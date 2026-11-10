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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tech.shaffersoftworks.remotescreens.player.BuildConfig
import tech.shaffersoftworks.remotescreens.player.data.connection.ConnectionRepository
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.shared.AppLogLevel
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
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
    private var forwardingConnectionId: Long? = null
    private var writerJob: Job? = null
    private val writes = Channel<StoredLog>(WRITE_BUFFER_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun start() {
        if (writerJob?.isActive == true) return
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

    /**
     * Sends the queue on [connectionId] until replaced, or stops sending when it is null. The view model
     * calls this from the main thread after a paired Welcome that accepts `app_log`, and with null when
     * the connection drops.
     */
    fun forwardOn(connectionId: Long?) {
        if (connectionId != null && connectionId == forwardingConnectionId && forwardingJob?.isActive == true) return
        forwardingJob?.cancel()
        forwardingConnectionId = connectionId
        forwardingJob = connectionId?.let { id -> scope.launch { sendLogs(id) } }
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

    private suspend fun sendLogs(connectionId: Long) {
        var retrySeconds = 1L
        while (true) {
            val isDelivered = try {
                val batch = logs.pending(Protocol.MAX_APP_LOG_BATCH).first { it.isNotEmpty() }
                val acknowledged = sendAndAwaitAck(batch, connectionId)
                // Entries left out of the acknowledgement stay queued and go in the next batch.
                if (acknowledged.isNotEmpty()) logs.acknowledge(acknowledged)
                acknowledged.isNotEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e("AppLog", "Could not forward stored logs", error)
                false
            }
            delay(if (isDelivered) 1.seconds else retrySeconds.seconds)
            retrySeconds = if (isDelivered) 1L else (retrySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
        }
    }

    /** Returns the event IDs in [batch] that the add-on acknowledged, or none on a failed send or timeout. */
    private suspend fun sendAndAwaitAck(batch: List<StoredLog>, connectionId: Long): List<String> {
        val eventIds = batch.mapTo(HashSet()) { it.eventId }
        return withTimeoutOrNull(5.seconds) {
            coroutineScope {
                // Subscribe before sending so a fast acknowledgement cannot be missed.
                val ack = async(start = CoroutineStart.UNDISPATCHED) {
                    connection.receivedMessages.mapNotNull { received ->
                        (received.message as? ServerMessage.AppLogAck)
                            ?.takeIf { received.connectionId == connectionId }
                            ?.eventIds?.filter { it in eventIds }
                            ?.takeIf { it.isNotEmpty() }
                    }.first()
                }
                try {
                    val message = PlayerMessage.AppLog(batch.map { it.toEntry() })
                    if (connection.trySend(message, connectionId)) ack.await() else emptyList()
                } finally {
                    ack.cancel()
                }
            }
        }.orEmpty()
    }

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
        level = if (priority >= Log.ERROR) AppLogLevel.ERROR else AppLogLevel.WARNING,
        tag = tag.take(MAX_LOG_TAG),
        message = message.take(MAX_LOG_MESSAGE),
    )
}
