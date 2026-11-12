package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.render.AlertRenderer
import tech.shaffersoftworks.remotescreens.server.render.cards.CardRenderer
import tech.shaffersoftworks.remotescreens.server.render.panels.intercom.IntercomVars
import tech.shaffersoftworks.remotescreens.server.render.panels.intercom.hasIntercomTile
import tech.shaffersoftworks.remotescreens.server.render.Rc
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.ScreenRenderer
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.Base64

/** Builds and publishes documents for each [Slot] on a [SessionHost]. */
internal class SessionDocument(
    private val host: SessionHost,
    private val media: SessionMedia,
) {
    private val logger = LoggerFactory.getLogger(SessionDocument::class.java)
    private val context get() = host.context
    private val homeAssistant get() = context.homeAssistant
    private val shownDocuments get() = host.shownDocuments
    private val buildMutex get() = host.buildMutex

    fun renderInput(renderData: RenderData) = RenderInput(
        states = homeAssistant.states.value,
        i18n = context.languageRepository.current(),
        homeAssistantConfig = homeAssistant.config.value,
        data = renderData,
        palette = host.palette,
        supportsCalendarDialogs = host.supportsCalendarDialogs,
        intercom = host.intercomView,
    )

    suspend fun renderScreen(screen: Screen): RenderedDoc =
        ScreenRenderer.render(
            screen = screen,
            device = host.device,
            input = renderInput(context.renderDataRepository.forScreen(screen)),
            initialPage = host.page,
        )

    suspend fun showMain(screen: Screen) {
        val rebuild: suspend () -> RenderedDoc? = { host.screen()?.let { renderScreen(it) } }
        val renderedDoc = buildMutex.withLock { renderScreen(screen) }
        show(Slot.MAIN, renderedDoc, rebuild)
    }

    suspend fun show(
        slot: Slot,
        renderedDoc: RenderedDoc,
        rebuild: suspend () -> RenderedDoc?,
        timeoutSeconds: Int = 0,
        wakeOnShow: Boolean = host.currentAlert?.wake ?: true,
        force: Boolean = false,
    ) {
        val states = homeAssistant.states.value
        val previous = shownDocuments[slot]
        if (!force && previous != null && previous.renderedDoc.hasSameContent(renderedDoc)) {
            reuseShownDocument(
                shown = previous,
                renderedDoc = renderedDoc,
                rebuild = rebuild,
                states = states,
            )
            return
        }
        val shown = adoptShownDocument(
            slot = slot,
            renderedDoc = renderedDoc,
            rebuild = rebuild,
            states = states,
        )
        sendDocumentMessage(
            shown = shown,
            states = states,
            timeoutSeconds = timeoutSeconds,
            wakeOnShow = wakeOnShow,
            restartTimeout = force,
        )
        sendLiveAttachments(
            shown = shown,
            states = states,
            isForced = true,
        )
    }

    /** Bytes match the document already on screen: keep the revision, push only changed vars and media. */
    private suspend fun reuseShownDocument(
        shown: ShownDocument,
        renderedDoc: RenderedDoc,
        rebuild: suspend () -> RenderedDoc?,
        states: Map<String, EntityState>,
    ) {
        shown.renderedDoc = renderedDoc
        shown.rebuild = rebuild
        sendChangedVars(shown = shown, renderedDoc = renderedDoc, states = states)
        sendLiveAttachments(shown = shown, states = states, isForced = false)
    }

    /** Installs a new [ShownDocument] for [slot] and bumps the session revision. */
    private fun adoptShownDocument(
        slot: Slot,
        renderedDoc: RenderedDoc,
        rebuild: suspend () -> RenderedDoc?,
        states: Map<String, EntityState>,
    ): ShownDocument {
        val shown = ShownDocument(
            slot = slot,
            renderedDoc = renderedDoc,
            rebuild = rebuild,
        ).also { document -> document.revision = ++host.lastRevision }
        shown.sentVars = renderedDoc.vars(states)
        shownDocuments[slot] = shown
        return shown
    }

    private suspend fun sendDocumentMessage(
        shown: ShownDocument,
        states: Map<String, EntityState>,
        timeoutSeconds: Int,
        wakeOnShow: Boolean,
        restartTimeout: Boolean,
    ) {
        val renderedDoc = shown.renderedDoc
        val bytes = if (host.supportsDeflate) {
            Rc.compress(renderedDoc.bytes)
        } else {
            renderedDoc.bytes
        }
        host.send(
            ServerMessage.Document(
                slot = shown.slot,
                rev = shown.revision,
                data = Base64.getEncoder().encodeToString(bytes),
                vars = documentVars(shown.slot, renderedDoc, states),
                predictions = renderedDoc.predictions(states, host::foresee),
                homePage = renderedDoc.homePage,
                pageCount = renderedDoc.pageIds.size.coerceAtLeast(1),
                swipePages = renderedDoc.swipePages,
                timeout = timeoutSeconds,
                wakeOnShow = wakeOnShow,
                restartTimeout = restartTimeout,
                navigationDrawer = renderedDoc.navigationDrawer,
            ),
        )
    }

    private fun documentVars(
        slot: Slot,
        renderedDoc: RenderedDoc,
        states: Map<String, EntityState>,
    ): List<VarValue> {
        val intercomVars = if (slot == Slot.MAIN && host.screen()?.hasIntercomTile() == true) {
            IntercomVars.values(host.intercomView, context.languageRepository.current())
        } else {
            emptyList()
        }
        return renderedDoc.vars(states).values +
            context.screenClock.clockVars() +
            host.connectedVar() +
            host.deviceVars() +
            intercomVars
    }

    private suspend fun sendChangedVars(
        shown: ShownDocument,
        renderedDoc: RenderedDoc,
        states: Map<String, EntityState>,
    ) {
        val vars = renderedDoc.vars(states)
        val changed = vars.values.filter { shown.sentVars[it.name] != it }
        shown.sentVars = vars
        if (changed.isNotEmpty()) {
            host.send(
                ServerMessage.Vars(
                    changed,
                    renderedDoc.predictions(states, host::foresee),
                ),
            )
        }
    }

    /**
     * Pictures and maps always; when [isForced], also warms and sends videos then search overlays
     * (new document path). Same-content updates skip video/search so a rebuild flash is avoided.
     */
    private suspend fun sendLiveAttachments(
        shown: ShownDocument,
        states: Map<String, EntityState>,
        isForced: Boolean,
    ) {
        media.sendPictures(shown, states, isForced = isForced)
        for (map in shown.renderedDoc.docContext.maps) {
            media.sendMap(shown, map, states)
        }
        if (!isForced) return
        for ((entityId) in shown.renderedDoc.docContext.videos) {
            media.warmCameraStream(entityId = entityId)
        }
        for (video in shown.renderedDoc.docContext.videos) {
            media.sendVideo(shown, video)
        }
        for (search in shown.renderedDoc.docContext.searches) {
            media.sendSearch(shown, search)
        }
    }

    /** Sends what changed in [states]; a document whose structure depends on a changed entity is built again. */
    suspend fun refresh(states: Map<String, EntityState>) {
        for (shown in shownDocuments.values.toList()) {
            if (shown.renderedDoc.stale(states)) {
                if (calendarStructureChanged(shown.renderedDoc, states)) {
                    context.renderDataRepository.calendarAgenda.invalidate()
                }
                rebuild(shown)
                continue
            }
            sendChangedVars(shown = shown, renderedDoc = shown.renderedDoc, states = states)
            sendLiveAttachments(shown = shown, states = states, isForced = false)
        }
    }

    /**
     * Recomputes `pos` / `progress` from the last `media_position` snapshot plus elapsed
     * time since `media_position_updated_at` (RPI `NowPlayingTrackTime`). A var update,
     * not a rebuild — Home Assistant does not publish that attribute on a timer.
     */
    suspend fun tickProgress(states: Map<String, EntityState>) {
        for (shown in shownDocuments.values.toList()) {
            val vars = shown.renderedDoc.vars(states)
            val changed = vars.values.filter { shown.sentVars[it.name] != it }
            if (changed.isEmpty()) continue
            shown.sentVars = vars
            host.send(
                ServerMessage.Vars(
                    changed,
                    shown.renderedDoc.predictions(states, host::foresee),
                ),
            )
        }
    }

    suspend fun rebuild(shown: ShownDocument) {
        val renderedDoc = buildMutex.withLock {
            runCatching { shown.rebuild() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    logger.warn("Build failed: {}", error.message, error)
                }
                .getOrNull()
        } ?: return
        if (shownDocuments[shown.slot] === shown) show(shown.slot, renderedDoc, shown.rebuild)
    }

    /** Sends every value again: after a tap whose prediction Home Assistant did not confirm. */
    suspend fun resync() {
        val states = homeAssistant.states.value
        for (shown in shownDocuments.values) {
            val vars = shown.renderedDoc.vars(states)
            shown.sentVars = vars
            host.send(
                ServerMessage.Vars(
                    vars.values.toList(),
                    shown.renderedDoc.predictions(states, host::foresee),
                ),
            )
        }
    }

    suspend fun openCard(entityId: String?, kind: String?) {
        // Start the HLS stream while the card builds so the first frame is not waiting on camera/stream.
        media.warmCameraStream(entityId = entityId, kind = kind)
        val build: suspend () -> RenderedDoc? = {
            val renderData = context.renderDataRepository.forCard(entityId, kind)
            CardRenderer.render(
                entityId = entityId ?: "card.$kind",
                kind = kind,
                device = host.device,
                input = renderInput(renderData),
            )
        }
        val renderedDoc = buildMutex.withLock { build() } ?: return
        show(Slot.OVERLAY, renderedDoc, build)
    }

    suspend fun showAlert(alert: Alert) {
        host.currentAlert = alert
        media.warmCameraStream(camera = alert.camera)
        val build: suspend () -> RenderedDoc? = {
            host.currentAlert?.let { AlertRenderer.render(
                it,
                host.device,
                renderInput(RenderData()),
            ) }
        }
        val renderedDoc = buildMutex.withLock { build() } ?: return
        if (alert.wake) host.send(ServerMessage.Wake(true))
        show(
            slot = Slot.ALERT,
            renderedDoc = renderedDoc,
            rebuild = build,
            timeoutSeconds = alert.timeout,
            wakeOnShow = alert.wake,
            force = true,
        )
    }
}

private fun calendarStructureChanged(rendered: RenderedDoc, states: Map<String, EntityState>): Boolean =
    rendered.docContext.structure.any { (entityId, what, was) ->
        entityId.startsWith("calendar.") && what(states[entityId]) != was
    }
