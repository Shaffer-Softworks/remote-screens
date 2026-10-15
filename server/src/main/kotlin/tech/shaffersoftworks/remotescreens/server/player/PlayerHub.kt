package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.layout.LayoutRepository
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private const val STATE_DEBOUNCE_MS = 80L
private const val TICK_INTERVAL_MS = 1000L
private const val MIN_CLOCK_DELAY_MS = 200L
private const val SECONDS_PER_MINUTE = 60
private const val MS_PER_SECOND = 1000L
private const val NANOS_PER_MS = 1_000_000
private const val IDENTIFY_SECONDS = 5

/**
 * Every connected player, and what each shows: the pages of its screen in the main slot, a card in the overlay
 * slot, an alert above both. Documents are built once and then kept up to date by sending only the values that
 * changed; a document is built again when something drawn into it changes.
 */
@Suppress("TooManyFunctions")
class PlayerHub(
    private val sessionContext: PlayerSessionContext,
    private val playerTokens: PlayerTokens = PlayerTokens(),
) {
    private val logger = LoggerFactory.getLogger(PlayerHub::class.java)
    private val homeAssistant: HomeAssistant = sessionContext.homeAssistant
    private val layoutRepository: LayoutRepository = sessionContext.layoutRepository
    private val screenClock: ScreenClock = sessionContext.screenClock
    private val scope: CoroutineScope = sessionContext.scope
    private val sessions = ConcurrentHashMap<String, PlayerSession>()

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            homeAssistant.states.debounce(STATE_DEBOUNCE_MS).collectLatest { states ->
                for (session in sessions.values) session.refresh(states)
            }
        }
        scope.launch {
            layoutRepository.layout.collectLatest { layout ->
                for (session in sessions.values) {
                    session.layoutChanged(layout.screens.firstOrNull { it.id == session.screenId })
                }
            }
        }
        scope.launch {
            homeAssistant.isConnected.collectLatest { for (session in sessions.values) session.sendConnected() }
        }
        scope.launch { runClock() }
        scope.launch { runTicks() }
        scope.launch { homeAssistant.events.collect { event -> onHomeAssistantEvent(event.type, event.data) } }
    }

    /** Serves one player's WebSocket until it closes. The first message must be its [PlayerMessage.Hello]. */
    suspend fun serve(socket: WebSocketSession, remoteAddress: String) {
        val hello = receiveHello(socket) ?: return
        val session = PlayerSession(socket, hello, sessionContext, ::newPairingCode)
        sessions.put(hello.deviceId, session)?.close()
        logger.info("Player {} ({}) connected from {}", hello.deviceName, hello.deviceId, remoteAddress)
        try {
            session.welcome()
            for (frame in socket.incoming) {
                (frame as? Frame.Text)?.let { Protocol.decodePlayer(it.readText()) }?.let { session.receive(it) }
            }
        } finally {
            sessions.remove(hello.deviceId, session)
            session.stop()
            layoutRepository.update { layout ->
                layout.copy(
                    players = layout.players.map { player ->
                        if (player.deviceId == hello.deviceId) {
                            player.copy(lastSeen = System.currentTimeMillis())
                        } else {
                            player
                        }
                    },
                )
            }
            logger.info("Player {} disconnected", hello.deviceName)
        }
    }

    private suspend fun receiveHello(socket: WebSocketSession): PlayerMessage.Hello? {
        val firstFrame = socket.incoming.receiveCatching().getOrNull() as? Frame.Text
        return firstFrame
            ?.let { Protocol.decodePlayer(it.readText()) as? PlayerMessage.Hello }
            ?.takeIf { it.protocol >= 1 }
    }

    fun status(): List<PlayerStatus> = layoutRepository.layout.value.players.map { record ->
        val session = sessions[record.deviceId]
        PlayerStatus(
            deviceId = record.deviceId,
            name = record.name,
            screenId = record.screenId,
            isOnline = session != null,
            pairingCode = session?.pairingCode,
            widthDp = record.widthDp,
            heightDp = record.heightDp,
            appVersion = record.appVersion,
            lastSeen = record.lastSeen,
            isAwake = session?.isAwake ?: false,
            page = session?.page ?: 0,
            battery = session?.battery,
            kiosk = session?.kiosk,
        )
    }

    /** Gives the player with [codeOrDeviceId] (its pairing code, or its device id) the screen [screenId]. */
    suspend fun adopt(codeOrDeviceId: String, screenId: String, name: String? = null): Boolean {
        val layout = layoutRepository.layout.value
        val session = sessions.values.firstOrNull { it.pairingCode == codeOrDeviceId } ?: sessions[codeOrDeviceId]
        val deviceId = session?.hello?.deviceId
            ?: layout.players.firstOrNull { it.deviceId == codeOrDeviceId }?.deviceId
        if (deviceId == null || layout.screens.none { it.id == screenId }) return false
        val token = if (session != null) playerTokens.newToken() else null
        layoutRepository.update { current ->
            current.copy(
                players = current.players.map { player ->
                    if (player.deviceId != deviceId) {
                        player
                    } else {
                        player.copy(
                            screenId = screenId,
                            name = name ?: player.name,
                            tokenHash = token?.let(PlayerTokens::hash) ?: player.tokenHash,
                        )
                    }
                },
            )
        }
        session?.adopted(token)
        return true
    }

    suspend fun forget(deviceId: String) {
        layoutRepository.update { layout ->
            layout.copy(players = layout.players.filterNot { it.deviceId == deviceId })
        }
        sessions[deviceId]?.welcome()
    }

    fun rename(deviceId: String, name: String) {
        layoutRepository.update { layout ->
            layout.copy(players = layout.players.map { if (it.deviceId == deviceId) it.copy(name = name) else it })
        }
    }

    suspend fun identify(deviceId: String) {
        sessions[deviceId]?.send(ServerMessage.Identify(IDENTIFY_SECONDS))
    }

    /** Shows [screen] (not saved) on one player, until its own layout is sent again. */
    suspend fun preview(deviceId: String, screen: Screen): Boolean {
        val session = sessions[deviceId] ?: return false
        session.showPreview(screen)
        return true
    }

    suspend fun showAlert(alert: Alert) {
        for (session in sessionsFor(alert.targets)) session.showAlert(alert)
    }

    suspend fun openPage(targets: List<String>, page: String) {
        for (session in sessionsFor(targets)) session.openPage(page)
    }

    suspend fun wake(targets: List<String>, isAwake: Boolean = true) {
        for (session in sessionsFor(targets)) session.send(ServerMessage.Wake(isAwake))
    }

    suspend fun closeOverlays(targets: List<String>) {
        for (session in sessionsFor(targets)) session.closeAll()
    }

    /** Whether [token] belongs to an adopted player; used for the APK download. */
    fun isTrustedToken(token: String?): Boolean {
        if (token == null) return false
        val tokenHash = PlayerTokens.hash(token)
        return layoutRepository.layout.value.players.any { it.tokenHash == tokenHash && it.screenId != null }
    }

    /** The adopted sessions among [targets] (screen or device ids); every adopted session when it is empty. */
    private fun sessionsFor(targets: List<String>): List<PlayerSession> = sessions.values.filter { session ->
        session.screenId != null &&
            (targets.isEmpty() || session.screenId in targets || session.hello.deviceId in targets)
    }

    private suspend fun onHomeAssistantEvent(type: String, data: JsonObject) {
        fun targets(): List<String> = when (val targets = data["targets"] ?: data["screens"]) {
            is JsonArray -> targets.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(targets.content)
            else -> emptyList()
        }
        when (type) {
            "remote_screens_alert" -> runCatching { Protocol.json.decodeFromJsonElement(Alert.serializer(), data) }
                .onSuccess { showAlert(it) }
                .onFailure { logger.warn("Bad alert event: {}", it.message) }
            "remote_screens_open_page" -> (data["page"] as? JsonPrimitive)?.content?.let { openPage(targets(), it) }
            "remote_screens_wake" -> wake(targets())
            "remote_screens_close" -> closeOverlays(targets())
        }
    }

    private fun newPairingCode(): String =
        playerTokens.newPairingCode(taken = sessions.values.mapNotNull { it.pairingCode }.toSet())

    /** Sends the time at the start of every minute, and switches themes as night begins or ends. */
    private suspend fun runClock() {
        while (scope.isActive) {
            val now = screenClock.now()
            val untilNextMinuteMs = (SECONDS_PER_MINUTE - now.second) * MS_PER_SECOND - now.nano / NANOS_PER_MS
            delay(untilNextMinuteMs.coerceAtLeast(MIN_CLOCK_DELAY_MS))
            val clockVars = screenClock.clockVars()
            for (session in sessions.values) {
                session.sendVars(clockVars)
                session.checkNight()
            }
        }
    }

    private suspend fun runTicks() {
        while (scope.isActive) {
            delay(TICK_INTERVAL_MS)
            val states = homeAssistant.states.value
            for (session in sessions.values) {
                runCatching { session.tick(states) }.onFailure { logger.debug("Tick failed: {}", it.message) }
            }
        }
    }
}
