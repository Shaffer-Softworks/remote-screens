package tech.shaffersoftworks.remotescreens.server.player

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.player.action.ActionRouter
import tech.shaffersoftworks.remotescreens.server.player.action.TapMemory
import tech.shaffersoftworks.remotescreens.server.player.action.TapOutcome
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.render.AlertButton
import tech.shaffersoftworks.remotescreens.server.render.AlertRenderer
import tech.shaffersoftworks.remotescreens.server.render.CardRenderer
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.LiveVideo
import tech.shaffersoftworks.remotescreens.server.render.Palette
import tech.shaffersoftworks.remotescreens.server.render.Rc
import tech.shaffersoftworks.remotescreens.server.render.RenderData
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.ScreenRenderer
import tech.shaffersoftworks.remotescreens.server.render.Themes
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarValue
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private const val MS_PER_SECOND = 1000L

/**
 * One connected player: the documents it shows in each [Slot], kept up to date by sending only the values that
 * changed, and its taps turned into actions.
 */
@Suppress("TooManyFunctions")
class PlayerSession(
    private val socket: WebSocketSession,
    val hello: PlayerMessage.Hello,
    private val context: PlayerSessionContext,
    /** A pairing code no other connected player shows. */
    private val newPairingCode: () -> String,
) {
    private val logger = LoggerFactory.getLogger(PlayerSession::class.java)
    private val homeAssistant = context.homeAssistant
    private val layoutRepository = context.layoutRepository
    private val sendMutex = Mutex()
    private val buildMutex = Mutex()
    private val device = Device(hello.widthDp, hello.heightDp, hello.density)
    private val supportsDeflate = Rc.DEFLATE in hello.capabilities
    private val supportsVideo = Protocol.CAPABILITY_VIDEO in hello.capabilities
    private val tapMemory = TapMemory()

    var pairingCode: String? = null
        private set
    var screenId: String? = null
        private set
    var isAwake = true
        private set
    var page = 0
        private set
    var battery: Int? = null
        private set
    var charging: Boolean? = null
        private set
    /** Wi‑Fi signal level 0–4, or null when unknown. */
    var wifi: Int? = null
        private set
    var kiosk: String? = null
        private set

    private var themeId = Themes.DEFAULT
    private var palette: Palette = Themes.RPI
    private var previewScreen: Screen? = null
    private var previewJob: Job? = null

    /** A document shown in [slot], with what was last sent for it. */
    private class ShownDocument(val slot: Slot, val renderedDoc: RenderedDoc, val rebuild: suspend () -> RenderedDoc?) {
        var revision = 0
        var sentVars: Map<String, VarValue> = emptyMap()
        val pictureKeys = HashMap<String, String?>()
        val pictureSentAtMs = HashMap<String, Long>()
        /** Component id to how many times its video was started again after the player said it stopped. */
        val videoRestarts = HashMap<Int, Int>()
    }

    private val shownDocuments = ConcurrentHashMap<Slot, ShownDocument>()
    private var lastRevision = 0
    private val jobs = mutableListOf<Job>()
    private var currentAlert: Alert? = null

    private fun screen(): Screen? =
        previewScreen ?: layoutRepository.layout.value.screens.firstOrNull { it.id == screenId }

    suspend fun send(message: ServerMessage) {
        val text = Protocol.encode(message)
        sendMutex.withLock { runCatching { socket.send(Frame.Text(text)) } }
    }

    suspend fun close() {
        runCatching { socket.close() }
    }

    fun stop() = jobs.forEach { it.cancel() }

    /** Greets the player: with its screen when it is adopted and its token matches, else with a pairing code. */
    suspend fun welcome() {
        val record = layoutRepository.layout.value.players.firstOrNull { it.deviceId == hello.deviceId }
        val token = hello.token
        val isTrusted = record?.tokenHash != null && token != null && PlayerTokens.hash(token) == record.tokenHash
        layoutRepository.update { layout ->
            val updated = (record ?: PlayerRecord(hello.deviceId, hello.deviceName)).copy(
                widthDp = hello.widthDp,
                heightDp = hello.heightDp,
                appVersion = hello.appVersion,
                lastSeen = System.currentTimeMillis(),
                screenId = if (isTrusted) record?.screenId else null,
                tokenHash = if (isTrusted) record?.tokenHash else null,
            )
            layout.copy(players = layout.players.filterNot { it.deviceId == hello.deviceId } + updated)
        }
        val screen = if (isTrusted) {
            layoutRepository.layout.value.screens.firstOrNull { it.id == record?.screenId }
        } else {
            null
        }
        if (screen == null) {
            screenId = null
            val code = pairingCode ?: newPairingCode()
            pairingCode = code
            shownDocuments.clear()
            send(ServerMessage.Welcome(pairingCode = code, serverVersion = context.serverVersion, features = FEATURES))
            return
        }
        pairingCode = null
        screenId = screen.id
        send(
            ServerMessage.Welcome(
                screenId = screen.id,
                screenName = screen.name,
                serverVersion = context.serverVersion,
                features = FEATURES,
            ),
        )
        begin(screen)
    }

    suspend fun adopted(token: String?) {
        pairingCode = null
        screenId = layoutRepository.layout.value.players.firstOrNull { it.deviceId == hello.deviceId }?.screenId
        val screen = screen() ?: return welcome()
        send(
            ServerMessage.Welcome(
                screenId = screen.id,
                screenName = screen.name,
                token = token,
                serverVersion = context.serverVersion,
                features = FEATURES,
            ),
        )
        begin(screen)
    }

    /** Picks the screen's theme for now, day or dark; true when it is another than before. */
    private fun applyTheme(settings: ScreenSettings): Boolean {
        val newThemeId = context.screenClock.themeIdFor(settings)
        val previousThemeId = themeId
        themeId = newThemeId
        palette = Themes.resolve(newThemeId, layoutRepository.layout.value.themes)
        return newThemeId != previousThemeId
    }

    private suspend fun begin(screen: Screen) {
        applyTheme(screen.settings)
        send(ServerMessage.Settings(screen.settings))
        showMain(screen)
        context.playerApkRepository.current()
            ?.takeIf { it.versionCode > hello.appVersion }
            ?.let { apk -> send(ServerMessage.UpdateAvailable(apk.versionCode, apk.versionName, "/player/apk")) }
    }

    private fun renderInput(renderData: RenderData) = RenderInput(
        states = homeAssistant.states.value,
        i18n = context.languageRepository.current(),
        homeAssistantConfig = homeAssistant.config.value,
        data = renderData,
        palette = palette,
    )

    private suspend fun renderScreen(screen: Screen): RenderedDoc =
        ScreenRenderer.render(screen, device, renderInput(context.renderDataRepository.forScreen(screen)))

    private suspend fun showMain(screen: Screen) {
        val rebuild: suspend () -> RenderedDoc? = { screen()?.let { renderScreen(it) } }
        val renderedDoc = buildMutex.withLock { renderScreen(screen) }
        show(Slot.MAIN, renderedDoc, rebuild)
    }

    private suspend fun show(
        slot: Slot,
        renderedDoc: RenderedDoc,
        rebuild: suspend () -> RenderedDoc?,
        timeoutSeconds: Int = 0,
    ) {
        val states = homeAssistant.states.value
        val shown = ShownDocument(slot, renderedDoc, rebuild).also { it.revision = ++lastRevision }
        val vars = renderedDoc.vars(states)
        shown.sentVars = vars
        shownDocuments[slot] = shown
        val allVars = vars.values + context.screenClock.clockVars() + connectedVar() + deviceVars()
        val bytes = if (supportsDeflate) Rc.compress(renderedDoc.bytes) else renderedDoc.bytes
        send(
            ServerMessage.Document(
                slot = slot,
                rev = shown.revision,
                data = Base64.getEncoder().encodeToString(bytes),
                vars = allVars,
                predictions = renderedDoc.predictions(states, ::foresee),
                homePage = renderedDoc.homePage,
                pageCount = renderedDoc.pageIds.size.coerceAtLeast(1),
                swipePages = renderedDoc.swipePages,
                timeout = timeoutSeconds,
            ),
        )
        sendPictures(shown, states, isForced = true)
        for (video in renderedDoc.docContext.videos) sendVideo(shown, video)
    }

    /** Starts [video]'s stream and tells the player where it is; without a stream, the image's picture stays. */
    private fun sendVideo(shown: ShownDocument, video: LiveVideo) {
        if (!supportsVideo) return
        context.scope.launch {
            val path = context.cameraStreamService.hlsPath(video.entityId) ?: return@launch
            if (shownDocuments[shown.slot] === shown) {
                send(ServerMessage.Video(shown.slot, shown.revision, video.componentId, path, video.crop))
            }
        }
    }

    private fun restartVideo(message: PlayerMessage.VideoFailed) {
        val shown = shownDocuments[message.slot]?.takeIf { it.revision == message.rev } ?: return
        val video = shown.renderedDoc.docContext.videos.firstOrNull { it.componentId == message.componentId } ?: return
        val restarts = shown.videoRestarts.getOrDefault(video.componentId, 0)
        if (restarts < MAX_VIDEO_RESTARTS) {
            shown.videoRestarts[video.componentId] = restarts + 1
            sendVideo(shown, video)
        } else {
            logger.info("Video of {} keeps stopping (HTTP {}); its picture stays", video.entityId, message.httpCode)
        }
    }

    private fun connectedVar(): VarValue = VarValue.int(Vars.CONNECTED, if (homeAssistant.isConnected.value) 1 else 0)

    /** Tablet battery and Wi‑Fi for the top bar; updated whenever the player sends [PlayerMessage.DeviceState]. */
    private fun deviceVars(): List<VarValue> {
        val i18n = context.languageRepository.current()
        val isCharging = charging == true
        return listOf(
            VarValue.string(Vars.BATTERY, battery?.let { i18n.percent(it) } ?: ""),
            VarValue.int(Vars.BATTERY_CHARGING, if (isCharging) Vars.VISIBLE else Vars.GONE),
            VarValue.int(Vars.BATTERY_IDLE, if (isCharging) Vars.GONE else Vars.VISIBLE),
            VarValue.string(Vars.WIFI, wifi?.toString() ?: ""),
        )
    }

    private fun foresee(ref: ActionRef, entity: EntityState): EntityState? =
        context.actionRouter.foresee(ref, context.actionRouter.target(ref, screen()), entity)

    /** Sends what changed in [states]; a document whose structure depends on a changed entity is built again. */
    suspend fun refresh(states: Map<String, EntityState>) {
        for (shown in shownDocuments.values.toList()) {
            if (shown.renderedDoc.stale(states)) {
                rebuild(shown)
                continue
            }
            val vars = shown.renderedDoc.vars(states)
            val changed = vars.values.filter { shown.sentVars[it.name] != it }
            shown.sentVars = vars
            if (changed.isNotEmpty()) send(ServerMessage.Vars(changed, shown.renderedDoc.predictions(states, ::foresee)))
            sendPictures(shown, states, isForced = false)
        }
    }

    private suspend fun rebuild(shown: ShownDocument) {
        val renderedDoc = buildMutex.withLock {
            runCatching { shown.rebuild() }
                .onFailure { logger.warn("Build failed: {}", it.message, it) }
                .getOrNull()
        } ?: return
        if (shownDocuments[shown.slot] === shown) show(shown.slot, renderedDoc, shown.rebuild)
    }

    suspend fun sendVars(vars: List<VarValue>) {
        if (screenId != null && vars.isNotEmpty()) send(ServerMessage.Vars(vars))
    }

    suspend fun sendConnected() = sendVars(listOf(connectedVar()))

    /** Sends every value again: after a tap whose prediction Home Assistant did not confirm. */
    private suspend fun resync() {
        val states = homeAssistant.states.value
        for (shown in shownDocuments.values) {
            val vars = shown.renderedDoc.vars(states)
            shown.sentVars = vars
            send(ServerMessage.Vars(vars.values.toList(), shown.renderedDoc.predictions(states, ::foresee)))
        }
    }

    private fun sendPictures(shown: ShownDocument, states: Map<String, EntityState>, isForced: Boolean) {
        val nowMs = System.currentTimeMillis()
        for (picture in shown.renderedDoc.docContext.pictures) {
            val sourceKey = context.pictureRepository.sourceKey(picture, states)
            val lastSentMs = shown.pictureSentAtMs[picture.name] ?: 0
            val isDue = picture.refreshSeconds > 0 && nowMs - lastSentMs >= picture.refreshSeconds * MS_PER_SECOND
            if (!isForced && !isDue && shown.pictureKeys[picture.name] == sourceKey) continue
            shown.pictureKeys[picture.name] = sourceKey
            shown.pictureSentAtMs[picture.name] = nowMs
            context.scope.launch {
                val bytes = context.pictureRepository.load(picture, states) ?: return@launch
                if (shownDocuments[shown.slot] === shown) {
                    send(ServerMessage.Bitmap(picture.name, Base64.getEncoder().encodeToString(bytes)))
                }
            }
        }
    }

    /** Once a second: rebuilds documents that refresh on a timer, and sends pictures that are due. */
    suspend fun tick(states: Map<String, EntityState>) {
        if (!isAwake) return
        for (shown in shownDocuments.values.toList()) {
            val docContext = shown.renderedDoc.docContext
            if (docContext.refreshSeconds > 0 && shown.renderedDoc.stale(states)) {
                rebuild(shown)
            } else if (docContext.pictures.any { it.refreshSeconds > 0 }) {
                sendPictures(shown, states, isForced = false)
            }
        }
    }

    /** Rebuilds every document when night switches the screen to or from its dark theme. */
    suspend fun checkNight() {
        val screen = screen() ?: return
        if (applyTheme(screen.settings)) for (shown in shownDocuments.values.toList()) rebuild(shown)
    }

    suspend fun layoutChanged(screen: Screen?) {
        endPreview()
        if (screenId != null && screen == null) {
            closeAll()
            return welcome()
        }
        if (screen == null) return
        val main = shownDocuments[Slot.MAIN]
        applyTheme(screen.settings)
        send(ServerMessage.Settings(screen.settings))
        if (main != null) rebuild(main) else showMain(screen)
    }

    suspend fun showPreview(screen: Screen) {
        endPreview()
        previewScreen = screen
        showWithSettings(screen)
        previewJob = context.scope.launch {
            delay(PREVIEW_MS)
            previewScreen = null
            screen()?.let { showWithSettings(it) }
        }.also { jobs += it }
    }

    private fun endPreview() {
        previewJob?.cancel()
        previewJob = null
        previewScreen = null
    }

    private suspend fun showWithSettings(screen: Screen) {
        applyTheme(screen.settings)
        send(ServerMessage.Settings(screen.settings))
        showMain(screen)
    }

    suspend fun receive(message: PlayerMessage) {
        when (message) {
            is PlayerMessage.Ping -> send(ServerMessage.Pong)
            is PlayerMessage.PageShown -> page = message.index
            is PlayerMessage.DeviceState -> {
                isAwake = message.awake
                page = message.page
                battery = message.battery
                charging = message.charging
                wifi = message.wifi
                kiosk = message.kiosk
                sendVars(deviceVars())
            }
            is PlayerMessage.SettingsChanged ->
                screenId?.let { id -> context.screenService.updateSettings(id) { message.settings } }
            is PlayerMessage.Action -> handleAction(message)
            is PlayerMessage.VideoFailed -> restartVideo(message)
            is PlayerMessage.Hello -> Unit
        }
    }

    private suspend fun handleAction(message: PlayerMessage.Action) {
        val ref = ActionRef.decode(message.metadata) ?: return
        val shown = shownDocuments[message.slot]
        if (shown == null || shown.revision != message.rev) {
            logger.debug("Tap on an old document ({} rev {}), ignored", message.slot, message.rev)
            return
        }
        when {
            ref.tile == "alert" -> handleAlertAction(ref)
            ref.local -> if (ref.verb == "close" && message.slot != Slot.MAIN) {
                shownDocuments.remove(message.slot)
                if (message.slot == Slot.ALERT) currentAlert = null
            }
            else -> handleOutcome(context.actionRouter.handle(ref, screen(), tapMemory))
        }
    }

    private suspend fun handleOutcome(outcome: TapOutcome) {
        when (outcome) {
            is TapOutcome.Card -> openCard(outcome.entityId, outcome.kind)
            is TapOutcome.Confirm -> {
                send(ServerMessage.Vars(listOf(VarValue.string(outcome.varName, outcome.text))))
                jobs += context.scope.launch {
                    delay(ActionRouter.CONFIRM_MS)
                    send(ServerMessage.Vars(listOf(VarValue.string(outcome.varName, ""))))
                }
            }
            is TapOutcome.Code -> send(ServerMessage.Vars(listOf(VarValue.string(outcome.varName, outcome.shown))))
            is TapOutcome.Failed -> {
                send(ServerMessage.Toast(outcome.message))
                resync()
            }
            TapOutcome.Done -> jobs += context.scope.launch {
                delay(RESYNC_MS)
                resync()
            }
            TapOutcome.Ignored -> Unit
        }
        jobs.removeAll { !it.isActive }
    }

    private suspend fun openCard(entityId: String?, kind: String?) {
        // Start the HLS stream while the card builds so the first frame is not waiting on camera/stream.
        warmCameraStream(entityId = entityId, kind = kind)
        val build: suspend () -> RenderedDoc? = {
            val renderData = context.renderDataRepository.forCard(entityId, kind)
            CardRenderer.render(entityId ?: "card.$kind", kind, device, renderInput(renderData))
        }
        val renderedDoc = buildMutex.withLock { build() } ?: return
        show(Slot.OVERLAY, renderedDoc, build)
    }

    suspend fun showAlert(alert: Alert) {
        currentAlert = alert
        warmCameraStream(camera = alert.camera)
        val build: suspend () -> RenderedDoc? = {
            currentAlert?.let { AlertRenderer.render(it, device, renderInput(RenderData())) }
        }
        val renderedDoc = buildMutex.withLock { build() } ?: return
        send(ServerMessage.Wake(true))
        show(Slot.ALERT, renderedDoc, build, timeoutSeconds = alert.timeout)
    }

    /** Asks Home Assistant for an HLS path in the background so [sendVideo] can reuse a warm stream. */
    private fun warmCameraStream(entityId: String? = null, kind: String? = null, camera: String? = null) {
        if (!supportsVideo) return
        val id = when {
            !camera.isNullOrBlank() -> camera
            entityId?.startsWith("camera.") == true -> entityId
            kind == "camera" && !entityId.isNullOrBlank() -> entityId
            else -> return
        }
        context.scope.launch {
            runCatching { context.cameraStreamService.hlsPath(id) }
        }
    }

    /** Closes the alert; for one of its buttons, also calls the button's service and tells Home Assistant. */
    private suspend fun handleAlertAction(ref: ActionRef) {
        val alert = currentAlert ?: return
        currentAlert = null
        shownDocuments.remove(Slot.ALERT)
        send(ServerMessage.Close(Slot.ALERT))
        if (ref.verb == "button") {
            alert.buttons.getOrNull(ref.arg.toIntOrNull() ?: -1)?.let { button -> pressAlertButton(alert, button) }
        }
    }

    private suspend fun pressAlertButton(alert: Alert, button: AlertButton) {
        button.service?.split('.', limit = 2)?.takeIf { it.size == 2 }?.let { (domain, service) ->
            runCatching { homeAssistant.callService(domain, service, button.data ?: JsonObject(emptyMap())) }
                .onFailure { send(ServerMessage.Toast(it.message ?: "Failed")) }
        }
        runCatching {
            val event = buildJsonObject {
                put("event_type", "remote_screens_alert_action")
                put(
                    "event_data",
                    buildJsonObject {
                        put("action", button.action ?: button.title)
                        alert.tag?.let { put("tag", it) }
                        put("screen", screenId)
                        put("device", hello.deviceId)
                    },
                )
            }
            homeAssistant.command("fire_event", event)
        }
    }

    suspend fun openPage(pageId: String) {
        val screen = screen() ?: return
        val pageIndex = screen.pages
            .indexOfFirst { it.id == pageId || it.title.equals(pageId, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: pageId.toIntOrNull()?.minus(1)
            ?: return
        send(ServerMessage.Wake(true))
        send(ServerMessage.OpenPage(pageIndex))
    }

    suspend fun closeAll() {
        for (slot in listOf(Slot.OVERLAY, Slot.ALERT)) {
            if (shownDocuments.remove(slot) != null) send(ServerMessage.Close(slot))
        }
        currentAlert = null
    }

    companion object {
        val FEATURES = listOf("predictions", "bitmaps", "alerts", "cards", "settings", Protocol.CAPABILITY_VIDEO)
        const val RESYNC_MS = 3000L
        const val MAX_VIDEO_RESTARTS = 3
        const val PREVIEW_MS = 5 * 60_000L
    }
}
