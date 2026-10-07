package dev.remotescreens.player

import android.util.Log
import dev.remotescreens.shared.PlayerMessage
import dev.remotescreens.shared.Protocol
import dev.remotescreens.shared.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/** The WebSocket to the add-on: says hello, hands each message on, and reconnects with backoff. */
class Connection(
    private val scope: CoroutineScope,
    private val hello: () -> PlayerMessage.Hello,
    private val onMessage: suspend (ServerMessage) -> Unit,
) {
    enum class Status { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

    val client = HttpClient(OkHttp) { install(WebSockets) { pingIntervalMillis = 20_000 } }
    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status
    @Volatile private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null
    var server: String? = null
        private set

    fun connect(server: String) {
        this.server = server
        job?.cancel()
        job = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                _status.value = Status.CONNECTING
                try {
                    client.webSocket("ws://$server/player") {
                        session = this
                        send(Frame.Text(Protocol.encode(hello())))
                        _status.value = Status.CONNECTED
                        backoff = 1_000L
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val msg = Protocol.decodeServer(frame.readText()) ?: continue
                            onMessage(msg)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Connection to $server failed: ${e.message}")
                } finally {
                    session = null
                }
                _status.value = Status.DISCONNECTED
                delay(backoff)
                backoff = min(backoff * 2, 30_000L)
            }
        }
    }

    /** Says hello again, as after the screen turned: the add-on builds the documents for the new size. */
    fun reconnect() = server?.let { connect(it) }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = Status.IDLE
    }

    suspend fun send(msg: PlayerMessage) {
        val s = session ?: return
        runCatching { s.send(Frame.Text(Protocol.encode(msg))) }
    }

    companion object {
        private const val TAG = "Connection"
    }
}
