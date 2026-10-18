package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.player.PlayerTokens
import tech.shaffersoftworks.remotescreens.server.render.Themes
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Welcome, theme, preview, and layout-change lifecycle for a [SessionHost]. */
internal class SessionLifecycle(
    private val host: SessionHost,
    private val documents: SessionDocument,
) {
    private val context get() = host.context
    private val layoutRepository get() = context.layoutRepository
    private val shownDocuments get() = host.shownDocuments

    /** Greets the player: with its screen when it is adopted and its token matches, else with a pairing code. */
    suspend fun welcome() {
        val record = layoutRepository.layout.value.players.firstOrNull { it.deviceId == host.hello.deviceId }
        val token = host.hello.token
        val isTrusted = record?.tokenHash != null && token != null && PlayerTokens.hash(
            token,
        ) == record.tokenHash
        layoutRepository.update { layout ->
            val updated = (record ?: PlayerRecord(host.hello.deviceId, host.hello.deviceName)).copy(
                widthDp = host.hello.widthDp,
                heightDp = host.hello.heightDp,
                appVersion = host.hello.appVersion,
                lastSeen = System.currentTimeMillis(),
                screenId = if (isTrusted) record.screenId else null,
                tokenHash = if (isTrusted) record.tokenHash else null,
            )
            layout.copy(
                players = layout.players.filterNot { it.deviceId == host.hello.deviceId } + updated,
            )
        }
        val screen = if (isTrusted) {
            layoutRepository.layout.value.screens.firstOrNull { it.id == record.screenId }
        } else {
            null
        }
        if (screen == null) {
            host.screenId = null
            val code = host.pairingCode ?: host.newPairingCode()
            host.pairingCode = code
            shownDocuments.clear()
            host.send(
                ServerMessage.Welcome(
                    pairingCode = code,
                    serverVersion = context.serverVersion,
                    features = host.features,
                    timeZone = timeZoneId(),
                ),
            )
            return
        }
        host.pairingCode = null
        host.screenId = screen.id
        host.send(
            ServerMessage.Welcome(
                screenId = screen.id,
                screenName = screen.name,
                serverVersion = context.serverVersion,
                features = host.features,
                timeZone = timeZoneId(),
            ),
        )
        begin(screen)
    }

    suspend fun adopted(token: String?) {
        host.pairingCode = null
        host.screenId = layoutRepository.layout.value.players.firstOrNull { it.deviceId == host.hello.deviceId }?.screenId
        val screen = host.screen() ?: return welcome()
        host.send(
            ServerMessage.Welcome(
                screenId = screen.id,
                screenName = screen.name,
                token = token,
                serverVersion = context.serverVersion,
                features = host.features,
                timeZone = timeZoneId(),
            ),
        )
        begin(screen)
    }

    fun timeZoneId(): String = context.screenClock.zone().id

    /** Picks the screen's theme for now, day or dark; true when it is another than before. */
    fun applyTheme(settings: ScreenSettings): Boolean {
        val newThemeId = context.screenClock.themeIdFor(settings)
        val previousThemeId = host.themeId
        host.themeId = newThemeId
        host.palette = Themes.resolve(newThemeId, layoutRepository.layout.value.themes)
        return newThemeId != previousThemeId
    }

    suspend fun begin(screen: Screen) {
        applyTheme(screen.settings)
        host.send(ServerMessage.Settings(screen.settings))
        documents.showMain(screen)
        context.playerApkRepository.current()
            ?.takeIf { it.versionCode > host.hello.appVersion }
            ?.let { apk -> host.send(
                ServerMessage.UpdateAvailable(apk.versionCode, apk.versionName, "/player/apk"),
            ) }
    }

    /** Rebuilds every document when night switches the screen to or from its dark theme. */
    suspend fun checkNight() {
        val screen = host.screen() ?: return
        if (applyTheme(
                screen.settings,
            )) {
            for (shown in shownDocuments.values.toList()) documents.rebuild(shown)
        }
    }

    suspend fun layoutChanged(screen: Screen?) {
        endPreview()
        if (host.screenId != null && screen == null) {
            closeAll()
            return welcome()
        }
        if (screen == null) return
        val main = shownDocuments[Slot.MAIN]
        applyTheme(screen.settings)
        host.send(ServerMessage.Settings(screen.settings))
        if (main != null) documents.rebuild(main) else documents.showMain(screen)
    }

    suspend fun showPreview(screen: Screen) {
        endPreview()
        host.previewScreen = screen
        showWithSettings(screen)
        host.previewJob = context.scope.launch {
            delay(PREVIEW_MS)
            host.previewScreen = null
            host.screen()?.let { showWithSettings(it) }
        }.also { host.jobs += it }
    }

    fun endPreview() {
        host.previewJob?.cancel()
        host.previewJob = null
        host.previewScreen = null
    }

    suspend fun showWithSettings(screen: Screen) {
        applyTheme(screen.settings)
        host.send(ServerMessage.Settings(screen.settings))
        documents.showMain(screen)
    }

    suspend fun closeAll() {
        for (slot in listOf(Slot.OVERLAY, Slot.ALERT)) {
            if (shownDocuments.remove(slot) != null) host.send(ServerMessage.Close(slot))
        }
        host.currentAlert = null
    }

    companion object {
        const val PREVIEW_MS = 5 * 60_000L
    }
}
