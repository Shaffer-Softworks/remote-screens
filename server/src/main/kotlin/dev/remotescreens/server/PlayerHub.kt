package dev.remotescreens.server

import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.server.ha.HomeAssistant
import dev.remotescreens.server.render.Alert
import dev.remotescreens.server.render.AlertRenderer
import dev.remotescreens.server.render.CardRenderer
import dev.remotescreens.server.render.Device
import dev.remotescreens.server.render.RenderInput
import dev.remotescreens.server.render.RenderedDoc
import dev.remotescreens.server.render.ScreenRenderer
import dev.remotescreens.server.render.V
import dev.remotescreens.server.store.LayoutStore
import dev.remotescreens.shared.ActionRef
import dev.remotescreens.shared.PlayerMessage
import dev.remotescreens.shared.PlayerRecord
import dev.remotescreens.shared.Protocol
import dev.remotescreens.shared.Screen
import dev.remotescreens.shared.ScreenSettings
import dev.remotescreens.shared.ServerMessage
import dev.remotescreens.shared.Slot
import dev.remotescreens.shared.VarValue
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** What the editor shows about a player. */
@Serializable
data class PlayerStatus(
    val deviceId: String,
    val name: String,
    val screenId: String?,
    val online: Boolean,
    val pairingCode: String?,
    val widthDp: Int,
    val heightDp: Int,
    val appVersion: Int,
    val lastSeen: Long,
    val awake: Boolean,
    val page: Int,
    val battery: Int?,
)

/** A newer player app, offered to tablets that run an older one. */
data class ApkInfo(val versionCode: Int, val versionName: String, val file: java.io.File)

/**
 * Every connected player, and what each shows: the pages of its screen in the main slot, a card in the overlay
 * slot, an alert above both. Documents are built once and then kept up to date by sending only the values that
 * changed; a document is built again when something drawn into it changes.
 */
class PlayerHub(
    private val config: Config,
    private val ha: HomeAssistant,
    private val store: LayoutStore,
    private val data: DataFetcher,
    private val pictures: Pictures,
    private val languages: Languages,
    private val scope: CoroutineScope,
    private val apk: () -> ApkInfo? = { null },
) {
    private val log = LoggerFactory.getLogger(PlayerHub::class.java)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val router = ActionRouter(ha) { languages.current() }
    private val random = SecureRandom()

    fun start() {
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            ha.states.debounce(80).collectLatest { states -> for (s in sessions.values) s.refresh(states) }
        }
        scope.launch {
            store.data.collectLatest { d -> for (s in sessions.values) s.layoutChanged(d.screens.firstOrNull { it.id == s.screenId }) }
        }
        scope.launch { ha.connected.collectLatest { for (s in sessions.values) s.sendVars(listOf(VarValue.int(V.CONNECTED, if (it) 1 else 0))) } }
        scope.launch { clock() }
        scope.launch { tick() }
        scope.launch { ha.events.collect { e -> onEvent(e.type, e.data) } }
    }

    // ---- Players ---------------------------------------------------------------------------------------------

    suspend fun serve(ws: WebSocketSession, remote: String) {
        val first = ws.incoming.receiveCatching().getOrNull() as? Frame.Text ?: return
        val hello = Protocol.decodePlayer(first.readText()) as? PlayerMessage.Hello ?: return
        if (hello.protocol < 1) return
        val session = Session(ws, hello)
        sessions.put(hello.deviceId, session)?.close()
        log.info("Player {} ({}) connected from {}", hello.deviceName, hello.deviceId, remote)
        try {
            session.welcome()
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val msg = Protocol.decodePlayer(frame.readText()) ?: continue
                session.receive(msg)
            }
        } finally {
            sessions.remove(hello.deviceId, session)
            session.stop()
            store.update { d -> d.copy(players = d.players.map { if (it.deviceId == hello.deviceId) it.copy(lastSeen = System.currentTimeMillis()) else it }) }
            log.info("Player {} disconnected", hello.deviceName)
        }
    }

    fun status(): List<PlayerStatus> {
        val records = store.data.value.players
        return records.map { r ->
            val s = sessions[r.deviceId]
            PlayerStatus(r.deviceId, r.name, r.screenId, s != null, s?.pairingCode, r.widthDp, r.heightDp, r.appVersion, r.lastSeen,
                s?.awake ?: false, s?.page ?: 0, s?.battery)
        }
    }

    /** Gives the player with [codeOrDevice] (its pairing code, or its device id) the screen [screenId]. */
    suspend fun adopt(codeOrDevice: String, screenId: String, name: String? = null): Boolean {
        val session = sessions.values.firstOrNull { it.pairingCode == codeOrDevice } ?: sessions[codeOrDevice]
        val deviceId = session?.hello?.deviceId ?: store.data.value.players.firstOrNull { it.deviceId == codeOrDevice }?.deviceId ?: return false
        if (store.data.value.screens.none { it.id == screenId }) return false
        val token = if (session != null) newToken() else null
        store.update { d ->
            d.copy(players = d.players.map {
                if (it.deviceId != deviceId) it
                else it.copy(screenId = screenId, name = name ?: it.name, tokenHash = token?.let(::hash) ?: it.tokenHash)
            })
        }
        session?.adopted(token)
        return true
    }

    suspend fun forget(deviceId: String) {
        store.update { d -> d.copy(players = d.players.filterNot { it.deviceId == deviceId }) }
        sessions[deviceId]?.welcome()
    }

    suspend fun rename(deviceId: String, name: String) {
        store.update { d -> d.copy(players = d.players.map { if (it.deviceId == deviceId) it.copy(name = name) else it }) }
    }

    suspend fun identify(deviceId: String) = sessions[deviceId]?.send(ServerMessage.Identify(5))

    /** Shows [screen] (not saved) on one player, until its own layout is sent again. */
    suspend fun preview(deviceId: String, screen: Screen): Boolean {
        val s = sessions[deviceId] ?: return false
        s.showPreview(screen)
        return true
    }

    suspend fun showAlert(alert: Alert) {
        for (s in targets(alert.targets)) s.showAlert(alert)
    }

    suspend fun openPage(targets: List<String>, page: String) {
        for (s in targets(targets)) s.openPage(page)
    }

    suspend fun wake(targets: List<String>, awake: Boolean = true) {
        for (s in targets(targets)) s.send(ServerMessage.Wake(awake))
    }

    suspend fun closeOverlays(targets: List<String>) {
        for (s in targets(targets)) s.closeAll()
    }

    /** Applies settings from Home Assistant (the integration's entities) to a screen and its players. */
    fun updateSettings(screenId: String, change: (ScreenSettings) -> ScreenSettings) {
        store.update { d -> d.copy(screens = d.screens.map { if (it.id == screenId) it.copy(settings = change(it.settings).validated()) else it }) }
    }

    private fun targets(ids: List<String>): List<Session> =
        if (ids.isEmpty()) sessions.values.filter { it.screenId != null }
        else sessions.values.filter { it.screenId != null && (it.screenId in ids || it.hello.deviceId in ids) }

    private suspend fun onEvent(type: String, data: JsonObject) {
        fun targets(): List<String> = when (val t = data["targets"] ?: data["screens"]) {
            is kotlinx.serialization.json.JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(t.content)
            else -> emptyList()
        }
        when (type) {
            "remote_screens_alert" -> runCatching { Protocol.json.decodeFromJsonElement(Alert.serializer(), data) }
                .onSuccess { showAlert(it) }.onFailure { log.warn("Bad alert event: {}", it.message) }
            "remote_screens_open_page" -> (data["page"] as? JsonPrimitive)?.content?.let { openPage(targets(), it) }
            "remote_screens_wake" -> wake(targets())
            "remote_screens_close" -> closeOverlays(targets())
        }
    }

    // ---- Timers ----------------------------------------------------------------------------------------------

    private fun zone(): ZoneId = runCatching { ZoneId.of(ha.config.value.timeZone) }.getOrDefault(ZoneId.systemDefault())

    fun clockVars(): List<VarValue> {
        val now = ZonedDateTime.now(zone())
        val i18n = languages.current()
        return listOf(VarValue.string(V.TIME, i18n.time(now)), VarValue.string(V.DATE, i18n.date(now)))
    }

    private suspend fun clock() {
        while (scope.isActive) {
            val now = ZonedDateTime.now(zone())
            delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(200))
            val vars = clockVars()
            for (s in sessions.values) {
                s.sendVars(vars)
                s.checkNight()
            }
        }
    }

    private suspend fun tick() {
        while (scope.isActive) {
            delay(1000)
            val states = ha.states.value
            for (s in sessions.values) runCatching { s.tick(states) }.onFailure { log.debug("Tick failed: {}", it.message) }
        }
    }

    // ---- One player ------------------------------------------------------------------------------------------

    private inner class Session(val ws: WebSocketSession, val hello: PlayerMessage.Hello) {
        private val sendLock = Mutex()
        private val buildLock = Mutex()
        val device = Device(hello.widthDp, hello.heightDp, hello.density)
        val memory = TapMemory()
        var pairingCode: String? = null
        var screenId: String? = null
        var awake = true
        var page = 0
        var battery: Int? = null
        private var dark = false
        private var preview: Screen? = null
        private var previewJob: Job? = null

        private inner class Shown(val slot: Slot, val doc: RenderedDoc, val rebuild: suspend () -> RenderedDoc?) {
            var rev = 0
            var sent: Map<String, VarValue> = emptyMap()
            val pictureKeys = HashMap<String, String?>()
            val pictureAt = HashMap<String, Long>()
        }

        private val shown = ConcurrentHashMap<Slot, Shown>()
        private var revs = 0
        private val jobs = mutableListOf<Job>()
        private var alert: Alert? = null

        fun screen(): Screen? = preview ?: store.data.value.screens.firstOrNull { it.id == screenId }

        suspend fun send(msg: ServerMessage) {
            val text = Protocol.encode(msg)
            sendLock.withLock { runCatching { ws.send(Frame.Text(text)) } }
        }

        suspend fun close() {
            runCatching { ws.close() }
        }

        fun stop() = jobs.forEach { it.cancel() }

        suspend fun welcome() {
            val record = store.data.value.players.firstOrNull { it.deviceId == hello.deviceId }
            val token = hello.token
            val trusted = record?.tokenHash != null && token != null && hash(token) == record.tokenHash
            store.update { d ->
                val updated = (record ?: PlayerRecord(hello.deviceId, hello.deviceName)).copy(
                    widthDp = hello.widthDp, heightDp = hello.heightDp, appVersion = hello.appVersion, lastSeen = System.currentTimeMillis(),
                    screenId = if (trusted) record?.screenId else null, tokenHash = if (trusted) record?.tokenHash else null,
                )
                d.copy(players = d.players.filterNot { it.deviceId == hello.deviceId } + updated)
            }
            val screen = if (trusted) store.data.value.screens.firstOrNull { it.id == record?.screenId } else null
            if (screen == null) {
                screenId = null
                pairingCode = pairingCode ?: newCode()
                shown.clear()
                send(ServerMessage.Welcome(pairingCode = pairingCode, serverVersion = config.version, features = FEATURES))
                return
            }
            pairingCode = null
            screenId = screen.id
            send(ServerMessage.Welcome(screenId = screen.id, screenName = screen.name, serverVersion = config.version, features = FEATURES))
            begin(screen)
        }

        suspend fun adopted(token: String?) {
            pairingCode = null
            screenId = store.data.value.players.firstOrNull { it.deviceId == hello.deviceId }?.screenId
            val screen = screen() ?: return welcome()
            send(ServerMessage.Welcome(screenId = screen.id, screenName = screen.name, token = token, serverVersion = config.version, features = FEATURES))
            begin(screen)
        }

        private suspend fun begin(screen: Screen) {
            dark = darkNow(screen.settings)
            send(ServerMessage.Settings(screen.settings))
            showMain(screen)
            apk()?.takeIf { it.versionCode > hello.appVersion }?.let { send(ServerMessage.UpdateAvailable(it.versionCode, it.versionName, "/player/apk")) }
        }

        private suspend fun input(renderData: dev.remotescreens.server.render.RenderData) =
            RenderInput(ha.states.value, languages.current(), ha.config.value, renderData, dark)

        private suspend fun showMain(screen: Screen) {
            val build: suspend () -> RenderedDoc? = {
                screen()?.let { s -> ScreenRenderer.render(s, device, input(data.forScreen(s))) }
            }
            val doc = buildLock.withLock { ScreenRenderer.render(screen, device, input(data.forScreen(screen))) }
            show(Slot.MAIN, doc, build)
        }

        private suspend fun show(slot: Slot, doc: RenderedDoc, rebuild: suspend () -> RenderedDoc?, timeout: Int = 0) {
            val states = ha.states.value
            val entry = Shown(slot, doc, rebuild).also { it.rev = ++revs }
            val vars = doc.vars(states)
            entry.sent = vars
            shown[slot] = entry
            val all = vars.values + clockVars() + VarValue.int(V.CONNECTED, if (ha.connected.value) 1 else 0)
            send(ServerMessage.Document(
                slot = slot, rev = entry.rev, data = Base64.getEncoder().encodeToString(doc.bytes), vars = all,
                predictions = doc.predictions(states, ::foresee), homePage = doc.homePage, pageCount = doc.pageIds.size.coerceAtLeast(1),
                swipePages = doc.swipePages, timeout = timeout,
            ))
            sendPictures(entry, states, force = true)
        }

        private fun foresee(ref: ActionRef, e: EntityState): EntityState? = router.foresee(ref, router.target(ref, screen()), e)

        suspend fun refresh(states: Map<String, EntityState>) {
            for (entry in shown.values.toList()) {
                if (entry.doc.stale(states)) {
                    rebuild(entry)
                    continue
                }
                val vars = entry.doc.vars(states)
                val changed = vars.values.filter { entry.sent[it.name] != it }
                entry.sent = vars
                if (changed.isNotEmpty()) send(ServerMessage.Vars(changed, entry.doc.predictions(states, ::foresee)))
                sendPictures(entry, states, force = false)
            }
        }

        private suspend fun rebuild(entry: Shown) {
            val doc = buildLock.withLock { runCatching { entry.rebuild() }.onFailure { log.warn("Build failed: {}", it.message, it) }.getOrNull() } ?: return
            if (shown[entry.slot] === entry) show(entry.slot, doc, entry.rebuild)
        }

        suspend fun sendVars(vars: List<VarValue>) {
            if (screenId != null && vars.isNotEmpty()) send(ServerMessage.Vars(vars))
        }

        /** Sends every value again: after a tap whose prediction Home Assistant did not confirm. */
        private suspend fun resync() {
            val states = ha.states.value
            for (entry in shown.values) {
                val vars = entry.doc.vars(states)
                entry.sent = vars
                send(ServerMessage.Vars(vars.values.toList(), entry.doc.predictions(states, ::foresee)))
            }
        }

        private suspend fun sendPictures(entry: Shown, states: Map<String, EntityState>, force: Boolean) {
            val now = System.currentTimeMillis()
            for (slot in entry.doc.ctx.pictures) {
                val key = pictures.sourceKey(slot, states)
                val due = slot.refreshSeconds > 0 && now - (entry.pictureAt[slot.name] ?: 0) >= slot.refreshSeconds * 1000L
                if (!force && !due && entry.pictureKeys[slot.name] == key) continue
                entry.pictureKeys[slot.name] = key
                entry.pictureAt[slot.name] = now
                scope.launch {
                    val bytes = pictures.load(slot, states) ?: return@launch
                    if (shown[entry.slot] === entry) send(ServerMessage.Bitmap(slot.name, Base64.getEncoder().encodeToString(bytes)))
                }
            }
        }

        suspend fun tick(states: Map<String, EntityState>) {
            if (!awake) return
            for (entry in shown.values.toList()) {
                if (entry.doc.ctx.refreshSeconds > 0 && entry.doc.stale(states)) rebuild(entry)
                else if (entry.doc.ctx.pictures.any { it.refreshSeconds > 0 }) sendPictures(entry, states, force = false)
            }
        }

        suspend fun checkNight() {
            val s = screen() ?: return
            val d = darkNow(s.settings)
            if (d != dark) {
                dark = d
                for (entry in shown.values.toList()) rebuild(entry)
            }
        }

        suspend fun layoutChanged(screen: Screen?) {
            endPreview()
            if (screenId != null && screen == null) {
                closeAll()
                return welcome()
            }
            if (screen == null) return
            val main = shown[Slot.MAIN]
            dark = darkNow(screen.settings)
            send(ServerMessage.Settings(screen.settings))
            if (main != null) rebuild(main) else showMain(screen)
        }

        suspend fun showPreview(screen: Screen) {
            endPreview()
            preview = screen
            showWithSettings(screen)
            previewJob = scope.launch {
                delay(PREVIEW_MS)
                preview = null
                screen()?.let { showWithSettings(it) }
            }.also { jobs += it }
        }

        private fun endPreview() {
            previewJob?.cancel()
            previewJob = null
            preview = null
        }

        private suspend fun showWithSettings(screen: Screen) {
            dark = darkNow(screen.settings)
            send(ServerMessage.Settings(screen.settings))
            showMain(screen)
        }

        suspend fun receive(msg: PlayerMessage) {
            when (msg) {
                is PlayerMessage.Ping -> send(ServerMessage.Pong)
                is PlayerMessage.PageShown -> page = msg.index
                is PlayerMessage.DeviceState -> {
                    awake = msg.awake; page = msg.page; battery = msg.battery
                }
                is PlayerMessage.SettingsChanged -> screenId?.let { id -> updateSettings(id) { msg.settings } }
                is PlayerMessage.Action -> action(msg)
                is PlayerMessage.Hello -> Unit
            }
        }

        private suspend fun action(msg: PlayerMessage.Action) {
            val ref = ActionRef.decode(msg.metadata) ?: return
            val entry = shown[msg.slot]
            if (entry == null || entry.rev != msg.rev) {
                log.debug("Tap on an old document ({} rev {}), ignored", msg.slot, msg.rev)
                return
            }
            if (ref.tile == "alert") return alertAction(ref)
            if (ref.local) {
                if (ref.verb == "close" && msg.slot != Slot.MAIN) shown.remove(msg.slot)
                return
            }
            when (val outcome = router.handle(ref, screen(), memory)) {
                is Outcome.Card -> openCard(outcome.entityId, outcome.kind)
                is Outcome.Confirm -> {
                    send(ServerMessage.Vars(listOf(VarValue.string(outcome.varName, outcome.text))))
                    jobs += scope.launch {
                        delay(ActionRouter.CONFIRM_MS)
                        send(ServerMessage.Vars(listOf(VarValue.string(outcome.varName, ""))))
                    }
                }
                is Outcome.Code -> send(ServerMessage.Vars(listOf(VarValue.string(outcome.varName, outcome.shown))))
                is Outcome.Failed -> {
                    send(ServerMessage.Toast(outcome.message))
                    resync()
                }
                Outcome.Done -> jobs += scope.launch {
                    delay(RESYNC_MS)
                    resync()
                }
                Outcome.Ignored -> Unit
            }
            jobs.removeAll { !it.isActive }
        }

        private suspend fun openCard(entityId: String?, kind: String?) {
            val build: suspend () -> RenderedDoc? = {
                CardRenderer.render(entityId ?: "card.$kind", kind, device, input(data.forCard(entityId, kind)))
            }
            val doc = buildLock.withLock { build() } ?: return
            show(Slot.OVERLAY, doc, build)
        }

        suspend fun showAlert(a: Alert) {
            alert = a
            val build: suspend () -> RenderedDoc? = { alert?.let { AlertRenderer.render(it, device, input(dev.remotescreens.server.render.RenderData())) } }
            val doc = buildLock.withLock { build() } ?: return
            send(ServerMessage.Wake(true))
            show(Slot.ALERT, doc, build, timeout = a.timeout)
        }

        private suspend fun alertAction(ref: ActionRef) {
            val a = alert ?: return
            alert = null
            shown.remove(Slot.ALERT)
            send(ServerMessage.Close(Slot.ALERT))
            if (ref.verb != "button") return
            val b = a.buttons.getOrNull(ref.arg.toIntOrNull() ?: -1) ?: return
            b.service?.split('.', limit = 2)?.takeIf { it.size == 2 }?.let { (d, s) ->
                runCatching { ha.callService(d, s, b.data ?: JsonObject(emptyMap())) }.onFailure { send(ServerMessage.Toast(it.message ?: "Failed")) }
            }
            runCatching {
                ha.command("fire_event", buildJsonObject {
                    put("event_type", "remote_screens_alert_action")
                    put("event_data", buildJsonObject {
                        put("action", b.action ?: b.title)
                        a.tag?.let { put("tag", it) }
                        put("screen", screenId)
                        put("device", hello.deviceId)
                    })
                })
            }
        }

        suspend fun openPage(pageId: String) {
            val s = screen() ?: return
            val i = s.pages.indexOfFirst { it.id == pageId || it.title.equals(pageId, ignoreCase = true) }
                .takeIf { it >= 0 } ?: pageId.toIntOrNull()?.minus(1) ?: return
            send(ServerMessage.Wake(true))
            send(ServerMessage.OpenPage(i))
        }

        suspend fun closeAll() {
            for (slot in listOf(Slot.OVERLAY, Slot.ALERT)) if (shown.remove(slot) != null) send(ServerMessage.Close(slot))
            alert = null
        }
    }

    private fun darkNow(s: ScreenSettings): Boolean {
        if (s.darkMode) return true
        val start = s.nightStart ?: return false
        val end = s.nightEnd ?: return false
        if (!s.nightDark) return false
        val now = LocalTime.now(zone())
        val a = LocalTime.parse(start)
        val b = LocalTime.parse(end)
        return if (a <= b) now >= a && now < b else now >= a || now < b
    }

    private fun newCode(): String {
        val taken = sessions.values.mapNotNull { it.pairingCode }.toSet()
        while (true) {
            val code = (100000 + random.nextInt(900000)).toString()
            if (code !in taken) return code
        }
    }

    private fun newToken(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    companion object {
        val FEATURES = listOf("predictions", "bitmaps", "alerts", "cards", "settings")
        const val RESYNC_MS = 3000L
        const val PREVIEW_MS = 5 * 60_000L

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Whether [token] belongs to an adopted player; used for the APK download. */
        fun trusted(store: LayoutStore, token: String?): Boolean =
            token != null && store.data.value.players.any { it.tokenHash == hash(token) && it.screenId != null }
    }
}

/** The language of the screens: the add-on's preference, else Home Assistant's, with Home Assistant's own words. */
class Languages(private val ha: HomeAssistant, private val store: LayoutStore) {
    @Volatile private var cached: I18n? = null
    private var key = ""

    fun current(): I18n {
        val prefs = store.data.value.preferences
        val lang = (prefs.language ?: ha.config.value.language).let { l -> if (l.substringBefore('-') in I18n.SUPPORTED) l else "en" }
        val clock = prefs.clock24h ?: I18n.default24h(lang)
        val k = "$lang|$clock"
        cached?.takeIf { key == k }?.let { return it }
        val built = I18n(lang, I18n.load(lang.substringBefore('-')), haWords[lang].orEmpty(), clock)
        cached = built
        key = k
        return built
    }

    private val haWords = ConcurrentHashMap<String, Map<String, String>>()

    /** Loads Home Assistant's words for states in the current language. */
    suspend fun loadHomeAssistantWords() {
        val lang = current().language
        if (haWords.containsKey(lang)) return
        val result = runCatching {
            ha.command("frontend/get_translations", buildJsonObject {
                put("language", lang)
                put("category", "entity_component")
            }) as? JsonObject
        }.getOrNull()
        haWords[lang] = I18n.haResources(result)
        cached = null
    }
}
