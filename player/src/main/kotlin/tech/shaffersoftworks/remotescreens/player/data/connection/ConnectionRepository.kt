package tech.shaffersoftworks.remotescreens.player.data.connection

import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val CONNECTION_START_TIMEOUT_MS = 30_000L
private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L
private const val BACKOFF_MULTIPLIER = 2
private const val MESSAGE_BUFFER_CAPACITY = 64

/** The WebSocket to the add-on: says hello, hands each message on, and reconnects with backoff. */
@Singleton
class ConnectionRepository @Inject constructor(
    private val httpClient: HttpClient,
    @IoScope private val ioScope: CoroutineScope,
) {
    /** A lifecycle snapshot whose ID identifies a single socket attempt. */
    data class ConnectionState(val connectionId: Long?, val status: ConnectionStatus)
    private val _connectionState = MutableStateFlow(ConnectionState(null, ConnectionStatus.IDLE))
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _server = MutableStateFlow<String?>(null)
    val server: StateFlow<String?> = _server.asStateFlow()

    /** Failed connect attempts since the last successful session; resets on CONNECTED. */
    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()

    /**
     * Every message from the add-on, in order, tagged with the socket attempt it arrived on.
     * Collecting suspends the socket, so nothing is dropped.
     */
    data class ReceivedMessage(val connectionId: Long, val message: ServerMessage)
    private val _receivedMessages = MutableSharedFlow<ReceivedMessage>(extraBufferCapacity = MESSAGE_BUFFER_CAPACITY)
    val receivedMessages: SharedFlow<ReceivedMessage> = _receivedMessages.asSharedFlow()

    private data class ActiveConnection(val id: Long, val socket: DefaultClientWebSocketSession)
    private val session = AtomicReference<ActiveConnection?>(null)
    // Ownership, publication, and lifecycle updates share this lock. Never hold it over socket IO.
    private val connectionLock = Any()
    private var owner: Any? = null
    private var nextConnectionId = 0L
    private var connectionJob: Job? = null
    private var helloProvider: (suspend () -> PlayerMessage.Hello)? = null

    /** Connects to [server], replacing any connection. [buildHello] is asked again on every (re)connect. */
    fun connect(server: String, buildHello: suspend () -> PlayerMessage.Hello) {
        val job = synchronized(connectionLock) { newConnection(server, buildHello) }
        job.start()
    }

    /**
     * Called under connectionLock; lazy launch lets us publish ownership before running any IO.
     * Flows are set last: a Main.immediate collector can run inline here and call [connect] again.
     */
    private fun newConnection(server: String, buildHello: suspend () -> PlayerMessage.Hello): Job {
        val token = Any()
        owner = token
        helloProvider = buildHello
        session.set(null)
        connectionJob?.cancel()
        val job = ioScope.launch(start = CoroutineStart.LAZY) {
            runConnectionLoop(server, buildHello, token)
        }
        connectionJob = job
        _server.value = server
        _consecutiveFailures.value = 0
        updateState(null, ConnectionStatus.CONNECTING)
        return job
    }

    /** Says hello again, as after the screen turned: the add-on builds the documents for the new size. */
    fun reconnect() {
        val job = synchronized(connectionLock) {
            val server = _server.value ?: return
            val buildHello = helloProvider ?: return
            newConnection(server, buildHello)
        }
        job.start()
    }

    fun disconnect() {
        synchronized(connectionLock) {
            owner = null
            session.set(null)
            connectionJob?.cancel()
            connectionJob = null
            helloProvider = null
            _consecutiveFailures.value = 0
            updateState(null, ConnectionStatus.IDLE)
        }
    }

    /** Called only under connectionLock. */
    private fun updateState(id: Long?, status: ConnectionStatus) {
        _connectionState.value = ConnectionState(id, status)
    }

    suspend fun send(message: PlayerMessage) {
        val currentSession = session.get() ?: return
        try {
            currentSession.socket.send(Frame.Text(Protocol.encode(message)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Timber.w(error, "Could not send %s", message::class.simpleName)
        }
    }

    /** Best-effort transport without logging failures, optionally scoped to a particular socket. */
    suspend fun trySend(message: PlayerMessage, connectionId: Long? = null): Boolean {
        val currentSession = session.get()
            ?.takeIf { connectionId == null || it.id == connectionId } ?: return false
        return try {
            currentSession.socket.send(Frame.Text(Protocol.encode(message)))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            false
        }
    }

    private fun beginAttempt(token: Any): Long? = synchronized(connectionLock) {
        if (owner !== token) null else (++nextConnectionId).also { updateState(it, ConnectionStatus.CONNECTING) }
    }

    private fun publish(
        token: Any,
        activeConnection: ActiveConnection,
        context: kotlin.coroutines.CoroutineContext,
    ): Boolean = synchronized(connectionLock) {
        if (owner !== token || !context.isActive) {
            false
        } else {
            session.set(activeConnection)
            updateState(activeConnection.id, ConnectionStatus.CONNECTED)
            _consecutiveFailures.value = 0
            true
        }
    }

    /** Warnings are forwarded to the add-on, so retries during an outage must not fill its queue. */
    private fun logFailure(server: String, error: Exception) {
        if (_consecutiveFailures.value == 0) {
            Timber.w("Connection to %s failed: %s", server, error.message)
        } else {
            Timber.i("Connection to %s failed again: %s", server, error.message)
        }
    }

    private suspend fun runConnectionLoop(
        server: String,
        buildHello: suspend () -> PlayerMessage.Hello,
        token: Any,
    ) {
        var backoffMs = INITIAL_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            val id = beginAttempt(token) ?: return
            var ownedSession: ActiveConnection? = null
            try {
                coroutineScope {
                    // HTTP request timeouts do not bound an upgraded WebSocket waiting for its first reply.
                    // After an APK update the TCP session can look open while the add-on never answers Hello.
                    val startupWatchdog = launch {
                        delay(CONNECTION_START_TIMEOUT_MS.milliseconds)
                        throw IOException("Server did not respond within ${CONNECTION_START_TIMEOUT_MS}ms")
                    }
                    try {
                        httpClient.webSocket("ws://$server/player") {
                            val activeConnection = ActiveConnection(id, this)
                            send(Frame.Text(Protocol.encode(buildHello())))
                            if (!publish(token, activeConnection, currentCoroutineContext())) return@webSocket
                            ownedSession = activeConnection
                            backoffMs = INITIAL_BACKOFF_MS
                            for (frame in incoming) {
                                val textFrame = frame as? Frame.Text ?: continue
                                val message = Protocol.decodeServer(textFrame.readText())
                                if (message != null) {
                                    startupWatchdog.cancel()
                                    _receivedMessages.emit(ReceivedMessage(id, message))
                                }
                            }
                        }
                    } finally {
                        startupWatchdog.cancel()
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                logFailure(server, error)
            } finally {
                synchronized(connectionLock) {
                    if (owner === token) {
                        session.compareAndSet(ownedSession, null)
                        updateState(id, ConnectionStatus.DISCONNECTED)
                    }
                }
            }
            if (!currentCoroutineContext().isActive) break
            synchronized(connectionLock) {
                if (owner !== token) return
                _consecutiveFailures.value += 1
            }
            delay(backoffMs.milliseconds)
            backoffMs = min(backoffMs * BACKOFF_MULTIPLIER, MAX_BACKOFF_MS)
        }
    }
}
