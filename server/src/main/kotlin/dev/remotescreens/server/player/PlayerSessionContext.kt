package dev.remotescreens.server.player

import dev.remotescreens.server.data.apk.PlayerApkRepository
import dev.remotescreens.server.data.homeassistant.HomeAssistant
import dev.remotescreens.server.data.layout.LayoutRepository
import dev.remotescreens.server.data.pictures.PictureRepository
import dev.remotescreens.server.data.renderdata.RenderDataRepository
import dev.remotescreens.server.i18n.LanguageRepository
import dev.remotescreens.server.player.action.ActionRouter
import dev.remotescreens.server.service.ScreenService
import kotlinx.coroutines.CoroutineScope

/** What every [PlayerSession] shares, handed down by the [PlayerHub]. */
class PlayerSessionContext(
    val serverVersion: String,
    val homeAssistant: HomeAssistant,
    val layoutRepository: LayoutRepository,
    val screenService: ScreenService,
    val renderDataRepository: RenderDataRepository,
    val pictureRepository: PictureRepository,
    val languageRepository: LanguageRepository,
    val playerApkRepository: PlayerApkRepository,
    val actionRouter: ActionRouter,
    val screenClock: ScreenClock,
    val scope: CoroutineScope,
)
