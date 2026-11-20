package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import tech.shaffersoftworks.remotescreens.shared.SignalingClient
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** The Tessera screens registered on the intercom hub, one socket each. */
internal class TesseraStations(
    private val scope: CoroutineScope,
    private val http: HttpClient,
    private val port: Int,
    private val onFrame: suspend (String, SignalingFrame) -> Unit,
    private val onRoster: suspend (List<SignalingClient>) -> Unit,
) {
    private val names = ConcurrentHashMap<String, String>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val outbound = ConcurrentHashMap<String, Channel<String>>()
    private val ready = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    suspend fun reconcile(wanted: List<TesseraStation>) {
        val next = wanted.associate { station -> TesseraProtocol.clientId(station.node) to station.name }

        for (clientId in names.keys.filter { clientId -> clientId !in next }) close(clientId)

        for ((clientId, name) in next) {
            if (names[clientId] == name && jobs[clientId]?.isActive == true) continue

            if (names.containsKey(clientId)) close(clientId)

            open(clientId, name)
        }

        for (clientId in next.keys) awaitReady(clientId)
    }

    suspend fun send(clientId: String, frame: SignalingFrame): Boolean {
        val channel = outbound[clientId]

        if (channel == null || !awaitReady(clientId)) return false

        channel.send(SignalingFrame.encode(frame))

        return true
    }

    fun closeAll() {
        for (clientId in names.keys.toList()) {
            names.remove(clientId)
            jobs.remove(clientId)?.cancel()
            outbound.remove(clientId)?.close()
            ready.remove(clientId)?.cancel()
        }
    }

    private fun open(clientId: String, name: String) {
        val channel = Channel<String>(Channel.UNLIMITED)
        names[clientId] = name
        outbound[clientId] = channel
        arm(clientId)
        jobs[clientId] = scope.launch { run(clientId, name, channel) }
    }

    private suspend fun run(clientId: String, name: String, channel: Channel<String>) {
        while (scope.isActive && names[clientId] == name) {
            val connected = arm(clientId)

            try {
                http.webSocket(urlString = "ws://127.0.0.1:$port/webrtc") {
                    send(Frame.Text(SignalingFrame.encode(SignalingFrame.Register(clientId, name))))

                    val writer = launch {
                        for (text in channel) send(Frame.Text(text))
                    }

                    for (incoming in incoming) {
                        if (incoming is Frame.Text) onSocketFrame(clientId, incoming.readText(), connected)
                    }

                    writer.cancel()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!connected.isCompleted) connected.cancel()
            }

            if (names[clientId] == name) delay(RECONNECT_MS.milliseconds)
        }
    }

    private suspend fun onSocketFrame(
        clientId: String,
        text: String,
        connected: CompletableDeferred<Unit>,
    ) {
        val frame = SignalingFrame.decode(text) ?: return

        when (frame) {
            is SignalingFrame.Registered -> if (!connected.isCompleted) connected.complete(Unit)
            is SignalingFrame.Ping -> outbound[clientId]?.send(SignalingFrame.encode(SignalingFrame.Pong(frame.ts)))
            is SignalingFrame.ClientsList -> onRoster(frame.clients)
            else -> onFrame(clientId, frame)
        }
    }

    private fun arm(clientId: String): CompletableDeferred<Unit> {
        val existing = ready[clientId]

        if (existing != null && !existing.isCompleted && !existing.isCancelled) return existing

        val created = CompletableDeferred<Unit>()
        ready[clientId] = created

        return created
    }

    private suspend fun awaitReady(clientId: String): Boolean {
        val connected = ready[clientId] ?: return false

        return withTimeoutOrNull(READY_MS.milliseconds) { connected.await() } != null
    }

    private suspend fun close(clientId: String) {
        names.remove(clientId)
        outbound.remove(clientId)?.close()
        ready.remove(clientId)?.cancel()
        jobs.remove(clientId)?.cancelAndJoin()
    }

    @Serializable
    internal data class ClientsBody(val clients: List<SignalingClient> = emptyList())

    private companion object {
        const val READY_MS = 5_000L
        const val RECONNECT_MS = 1_000L
    }
}
