package tech.shaffersoftworks.remotescreens.player.data.diagnostics

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tech.shaffersoftworks.remotescreens.player.BuildConfig
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionRepository
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionStatus
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Protocol
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

private const val WRITE_BUFFER_CAPACITY = 128
private const val MAX_LOG_MESSAGE = 4_000
private const val MAX_LOG_TAG = 100
private const val MAX_RETRY_SECONDS = 30L

/** Bounded durable queue. Timber callbacks enqueue writes without blocking the caller. */
@Singleton
class AppLog @Inject constructor(
    private val connection: ConnectionRepository,
    @IoScope private val scope: CoroutineScope,
    private val logs: LogDao,
) : Timber.Tree() {
    private var forwardingJob: Job? = null
    private var writerJob: Job? = null
    private val writes = Channel<StoredLog>(WRITE_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun start() {
        if (writerJob?.isActive != true) {
            writerJob = scope.launch {
                for (entry in writes) {
                    var retrySeconds = 1L
                    while (!persist(entry)) {
                        delay(retrySeconds.seconds)
                        retrySeconds = (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
                    }
                }
            }
        }
        if (forwardingJob?.isActive == true) return
        forwardingJob = scope.launch {
            // Keep the newest Welcome even if an old socket finishes emitting after replacement.
            val welcomes = connection.receivedMessages
                .filter { it.message is ServerMessage.Welcome }
                .runningFold<ConnectionRepository.ReceivedMessage, ConnectionRepository.ReceivedMessage?>(null) {
                        latest, incoming ->
                    if (latest == null || incoming.connectionId >= latest.connectionId) incoming else latest
                }
            combine(connection.connectionState, welcomes) { state, received ->
                val welcome = received?.message as? ServerMessage.Welcome
                state.connectionId?.takeIf {
                    state.status == ConnectionStatus.CONNECTED &&
                        it == received?.connectionId &&
                        welcome?.pairingCode == null &&
                        welcome != null && Protocol.CAPABILITY_APP_LOG_ACK in welcome.features
                }
            }.distinctUntilChanged().collectLatest { connectionId ->
                if (connectionId != null) sendLog(connectionId)
            }
        }
    }

    private suspend fun persist(entry: StoredLog): Boolean = try {
        logs.enqueue(entry)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        // Never feed storage errors back into the queue that failed.
        Log.e("AppLog", "Could not persist log entry", error)
        false
    }

    private suspend fun sendLog(connectionId: Long) {
        var retrySeconds = 1L
        while (true) {
            val delivered = try {
                val entry = logs.oldest().filterNotNull().first()
                if (sendAndAwaitAck(entry, connectionId)) {
                    logs.acknowledge(entry.eventId)
                    true
                } else {
                    false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e("AppLog", "Could not forward stored log", error)
                false
            }
            delay(if (delivered) 1.seconds else retrySeconds.seconds)
            retrySeconds = if (delivered) 1L else (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
        }
    }

    private suspend fun sendAndAwaitAck(entry: StoredLog, connectionId: Long): Boolean =
        withTimeoutOrNull(5.seconds) {
            coroutineScope {
                // Subscribe before sending so a fast acknowledgement cannot be missed.
                val ack = async(start = CoroutineStart.UNDISPATCHED) {
                    connection.receivedMessages.first {
                        it.connectionId == connectionId &&
                            (it.message as? ServerMessage.AppLogAck)?.eventId == entry.eventId
                    }
                }
                try {
                    if (connection.trySend(entry.toMessage(), connectionId)) {
                        ack.await()
                        true
                    } else {
                        false
                    }
                } finally {
                    ack.cancel()
                }
            }
        } ?: false

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Timber includes the throwable stack in message before invoking this method.
        // Debug builds also plant DebugTree, which already writes this message to Logcat.
        record(priority, tag ?: "Player", message, isEchoedToLogcat = !BuildConfig.DEBUG)
    }

    fun warning(tag: String, message: String) = record(Log.WARN, tag, message, isEchoedToLogcat = true)

    private fun record(priority: Int, tag: String, message: String, isEchoedToLogcat: Boolean) {
        val entry = entry(priority, tag, message)
        if (isEchoedToLogcat) Log.println(priority, entry.tag, entry.message)
        writes.trySend(entry)
    }

    /** Used before checkpointing recovered process exits; returns only after the database commit. */
    suspend fun persistWarning(tag: String, message: String, eventId: String) {
        val entry = entry(Log.WARN, tag, message).copy(eventId = eventId)
        Log.w(entry.tag, entry.message)
        logs.enqueue(entry)
    }

    private fun entry(priority: Int, tag: String, message: String) = StoredLog(
        eventId = UUID.randomUUID().toString(),
        timestampMs = System.currentTimeMillis(),
        level = if (priority >= Log.ERROR) "error" else "warning",
        tag = tag.take(MAX_LOG_TAG),
        message = message.take(MAX_LOG_MESSAGE),
    )
}
