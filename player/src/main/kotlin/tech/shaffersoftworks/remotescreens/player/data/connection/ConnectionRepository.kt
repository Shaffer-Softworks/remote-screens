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
import kotlinx.coroutines.Job
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

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
    private val _status = MutableStateFlow(ConnectionStatus.IDLE)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _server = MutableStateFlow<String?>(null)
    val server: StateFlow<String?> = _server.asStateFlow()

    /** Failed connect attempts since the last successful session; resets on CONNECTED. */
    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures.asStateFlow()

    /** Every message from the add-on, in order. Collecting suspends the socket, so nothing is dropped. */
    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = MESSAGE_BUFFER_CAPACITY)
    val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var helloProvider: (suspend () -> PlayerMessage.Hello)? = null

    /** Connects to [server], replacing any connection. [buildHello] is asked again on every (re)connect. */
    fun connect(server: String, buildHello: suspend () -> PlayerMessage.Hello) {
        _server.value = server
        helloProvider = buildHello
        _consecutiveFailures.value = 0
        connectionJob?.cancel()
        connectionJob = ioScope.launch { runConnectionLoop(server, buildHello) }
    }

    /** Says hello again, as after the screen turned: the add-on builds the documents for the new size. */
    fun reconnect() {
        val currentServer = _server.value ?: return
        val buildHello = helloProvider ?: return
        connect(currentServer, buildHello)
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        helloProvider = null
        _consecutiveFailures.value = 0
        _status.value = ConnectionStatus.IDLE
    }

    suspend fun send(message: PlayerMessage) {
        val currentSession = session ?: return
        runCatching { currentSession.send(Frame.Text(Protocol.encode(message))) }
            .onFailure { error -> Timber.w(error, "Could not send %s", message::class.simpleName) }
    }

    private suspend fun runConnectionLoop(server: String, buildHello: suspend () -> PlayerMessage.Hello) {
        var backoffMs = INITIAL_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            _status.value = ConnectionStatus.CONNECTING
            try {
                httpClient.webSocket("ws://$server/player") {
                    session = this
                    send(Frame.Text(Protocol.encode(buildHello())))
                    _status.value = ConnectionStatus.CONNECTED
                    _consecutiveFailures.value = 0
                    backoffMs = INITIAL_BACKOFF_MS
                    for (frame in incoming) {
                        val textFrame = frame as? Frame.Text ?: continue
                        val message = Protocol.decodeServer(textFrame.readText())
                        if (message != null) _messages.emit(message)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Timber.w("Connection to %s failed: %s", server, error.message)
            } finally {
                session = null
            }
            if (!currentCoroutineContext().isActive) break
            _status.value = ConnectionStatus.DISCONNECTED
            _consecutiveFailures.value += 1
            delay(backoffMs)
            backoffMs = min(backoffMs * BACKOFF_MULTIPLIER, MAX_BACKOFF_MS)
        }
    }
}
