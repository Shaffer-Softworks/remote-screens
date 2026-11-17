package tech.shaffersoftworks.remotescreens.server.intercom

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import tech.shaffersoftworks.remotescreens.shared.SignalingClient
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** WebSocket sessions for one [SignalingHub], shared by the tablet port and the ingress panel. */
class SignalingService(
    private val scope: CoroutineScope,
) {
    private val hub = SignalingHub()
    private val sockets = ConcurrentHashMap<String, WebSocketSession>()

    fun start() {
        scope.launch {
            while (isActive) {
                delay(APP_PING_INTERVAL_MS.milliseconds)
                val ping = SignalingFrame.encode(SignalingFrame.Ping(System.currentTimeMillis()))
                for (socket in sockets.values) {
                    runCatching { socket.send(Frame.Text(ping)) }
                }
            }
        }
    }

    suspend fun serve(socket: WebSocketSession) {
        val sessionId = UUID.randomUUID().toString()
        sockets[sessionId] = socket
        try {
            for (frame in socket.incoming) {
                if (frame is Frame.Text) {
                    dispatch(hub.handleMessage(sessionId, frame.readText()))
                }
            }
        } finally {
            sockets.remove(sessionId, socket)
            dispatch(hub.handleDisconnect(sessionId))
        }
    }

    suspend fun clientsBody(): String {
        val (views, closeSessionIds) = hub.poll()
        for (sessionId in closeSessionIds) {
            closeSocket(sessionId)
        }

        return SignalingFrame.JSON.encodeToString(ClientsBody.serializer(), ClientsBody(views.map { it.toClient() }))
    }

    /** `/api/clients`: the roster without a `type`. */
    @Serializable
    private data class ClientsBody(val clients: List<SignalingClient>)

    private suspend fun dispatch(outcome: SignalingHub.Outcome) {
        for ((sessionId, json) in outcome.sends) {
            deliver(sessionId, json)
        }

        for (sessionId in outcome.closeSessionIds) {
            closeSocket(sessionId)
        }

        for ((sessionId, json, delayMs) in outcome.deferred) {
            scope.launch {
                delay(delayMs.milliseconds)
                deliver(sessionId, json)
            }
        }
    }

    private suspend fun deliver(sessionId: String, json: String) {
        val socket = sockets[sessionId] ?: return
        runCatching { socket.send(Frame.Text(json)) }
    }

    private suspend fun closeSocket(sessionId: String) {
        val socket = sockets.remove(sessionId) ?: return
        runCatching { socket.close() }
    }

    companion object {
        const val APP_PING_INTERVAL_MS = 25_000L
    }
}

/** `/webrtc` for stations, and `/api/clients` for the roster. */
fun Route.signalingRoutes(signaling: SignalingService) {
    webSocket("/webrtc") { signaling.serve(this) }
    get("/api/clients") {
        call.respondText(signaling.clientsBody(), ContentType.Application.Json)
    }
}
