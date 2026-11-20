package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.FireEvent
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.SubscribeEvents
import tech.shaffersoftworks.remotescreens.shared.SignalingClient
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Publishes Remote Screens stations to Tessera and answers Tessera screens on the intercom hub.
 * With no reply from Tessera's call hook it logs once and leaves the roster unchanged.
 */
internal class TesseraGateway(
    private val homeAssistant: HomeAssistant,
    private val port: Int,
    private val scope: CoroutineScope,
) {
    private val desk = TesseraCallDesk()
    private val deskLock = Mutex()
    private val steps = Channel<DeskResult>(Channel.UNLIMITED)
    private val calls = ConcurrentHashMap<String, TesseraMediaCall>()
    private var http: HttpClient? = null
    private var stations: TesseraStations? = null

    fun start() {
        val client = HttpClient(CIO) {
            install(WebSockets)

            engine { requestTimeout = NO_REQUEST_TIMEOUT_MS }
        }

        http = client

        stations = TesseraStations(
            scope = scope,
            http = client,
            port = port,
            onFrame = { clientId, frame -> submit { desk.onSignaling(clientId, frame) } },
            onRoster = { clients -> submit { desk.onRoster(clients) } },
        )

        scope.launch { executeSteps() }
        scope.launch { watchEvents() }
        scope.launch { watchConnection() }
        scope.launch { refreshScreens() }
        scope.launch { pollRoster(client) }
        logger.info("Tessera intercom bridge is watching port {}", port)
    }

    private suspend fun watchConnection() {
        homeAssistant.isConnected.collect { connected ->
            if (!connected) return@collect

            runCatching { homeAssistant.send(SubscribeEvents(TesseraProtocol.PEER_EVENT)) }
                .onFailure { error -> logger.warn("Tessera call events were not subscribed: {}", error.message) }
        }
    }

    private suspend fun watchEvents() {
        homeAssistant.events.collect { event ->
            if (event.type != TesseraProtocol.PEER_EVENT) return@collect

            val inbound = TesseraProtocol.parse(event.data) ?: return@collect

            submit { desk.onInbound(inbound) }
        }
    }

    private suspend fun refreshScreens() {
        while (scope.isActive) {
            delay(LIST_REFRESH_MS.milliseconds)
            submit { desk.onRefresh() }
        }
    }

    private suspend fun pollRoster(client: HttpClient) {
        while (scope.isActive) {
            val clients = runCatching { roster(client) }.getOrNull()

            if (clients != null) submit { desk.onRoster(clients) }

            delay(ROSTER_POLL_MS.milliseconds)
        }
    }

    private suspend fun roster(client: HttpClient): List<SignalingClient> {
        val body = client.get("http://127.0.0.1:$port/api/clients").bodyAsText()

        return JSON.decodeFromString(TesseraStations.ClientsBody.serializer(), body).clients
    }

    private suspend fun submit(block: () -> DeskResult) {
        val result = deskLock.withLock { block() }
        steps.send(result)
    }

    private suspend fun executeSteps() {
        for (result in steps) execute(result)
    }

    private suspend fun execute(result: DeskResult) {
        if (result.warnSilence) {
            logger.warn(
                "Tessera has no external peers; screen calls stay on Tessera until it answers {}",
                TesseraProtocol.PEERS_EVENT,
            )
        }

        for (fire in result.fires) {
            runCatching { homeAssistant.send(FireEvent(eventType = fire.eventType, eventData = fire.data)) }
                .onFailure { error -> logger.warn("Tessera event {} failed: {}", fire.eventType, error.message) }
        }

        result.stations?.let { wanted -> stations?.reconcile(wanted) }

        for (frame in result.frames) stations?.send(clientId = frame.fromClientId, frame = frame.frame)

        for (command in result.media) runMedia(command)
    }

    private suspend fun runMedia(command: MediaCommand) {
        when (command) {
            is MediaCommand.Invite -> invite(command)
            is MediaCommand.Answer -> answer(command)
            is MediaCommand.Accept -> call(command.stationId)?.accept(command.answerSdp)
            is MediaCommand.Attach -> call(command.stationId)?.attach(command.listenUrl, command.talkUrl)
            is MediaCommand.Candidate -> call(command.stationId)?.candidate(
                TesseraCandidate(
                    candidate = command.candidate,
                    sdpMid = command.sdpMid,
                    line = command.line,
                ),
            )
            is MediaCommand.Stop -> calls.remove(command.stationId)?.close()
        }
    }

    private suspend fun invite(command: MediaCommand.Invite) {
        val media = openCall(command.stationId, command.tabletId) ?: return

        val sdp = runCatching { media.offer() }.getOrElse { error ->
            fail(command.stationId, error.message ?: "offer failed")

            return
        }

        val sent = stations?.send(
            clientId = command.stationId,
            frame = SignalingFrame.Offer(sdp = sdp, to = command.tabletId),
        ) == true

        if (!sent) fail(command.stationId, "the station did not register")
    }

    private suspend fun answer(command: MediaCommand.Answer) {
        val media = openCall(command.stationId, command.tabletId) ?: return
        media.attach(listenUrl = command.listenUrl, talkUrl = command.talkUrl)

        val sdp = runCatching { media.answer(command.offerSdp) }.getOrElse { error ->
            fail(command.stationId, error.message ?: "answer failed")

            return
        }

        stations?.send(
            clientId = command.stationId,
            frame = SignalingFrame.Answer(sdp = sdp, to = command.tabletId),
        )
    }

    private fun openCall(stationId: String, tabletId: String): TesseraMediaCall? {
        calls[stationId]?.close()

        val media = http?.let { client -> startMedia(client, stationId, tabletId) }

        if (media != null) calls[stationId] = media

        return media
    }

    private fun startMedia(client: HttpClient, stationId: String, tabletId: String): TesseraMediaCall? =
        runCatching {
            TesseraMediaCall(
                scope = scope,
                http = client,
                onCandidate = { candidate ->
                    scope.launch {
                        stations?.send(
                            clientId = stationId,
                            frame = SignalingFrame.Candidate(
                                candidate = candidate.candidate,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.line,
                                to = tabletId,
                            ),
                        )
                    }
                },
                onFailed = { message -> scope.launch { fail(stationId, message) } },
            )
        }.getOrElse { error ->
            scope.launch { fail(stationId, error.message ?: "media did not start") }

            null
        }

    private fun call(stationId: String): TesseraMediaCall? = calls[stationId]

    private suspend fun fail(stationId: String, message: String) {
        logger.warn("Tessera call on {} failed: {}", stationId, message)
        calls.remove(stationId)?.close()
        submit { desk.onMediaFailed(stationId) }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        const val NO_REQUEST_TIMEOUT_MS = 0L
        const val ROSTER_POLL_MS = 5_000L
        const val LIST_REFRESH_MS = 30_000L
        private val logger = LoggerFactory.getLogger(TesseraGateway::class.java)
    }
}
