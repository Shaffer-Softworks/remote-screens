package dev.remotescreens.server.di

import dev.remotescreens.server.config.ServerConfig
import dev.remotescreens.server.data.apk.PlayerApkRepository
import dev.remotescreens.server.data.homeassistant.HomeAssistant
import dev.remotescreens.server.data.homeassistant.HomeAssistantClient
import dev.remotescreens.server.data.homeassistant.demo.DemoHome
import dev.remotescreens.server.data.homeassistant.demo.DemoLayout
import dev.remotescreens.server.data.layout.LayoutRepository
import dev.remotescreens.server.data.pictures.PictureRepository
import dev.remotescreens.server.data.renderdata.RenderDataRepository
import dev.remotescreens.server.data.security.ApiKeyRepository
import dev.remotescreens.server.i18n.LanguageRepository
import dev.remotescreens.server.player.PlayerHub
import dev.remotescreens.server.player.PlayerSessionContext
import dev.remotescreens.server.player.ScreenClock
import dev.remotescreens.server.player.action.ActionRouter
import dev.remotescreens.server.service.HomeAssistantCatalogService
import dev.remotescreens.server.service.ScreenService
import dev.remotescreens.server.service.ThemeService
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The composition root: every long-lived object of the server, built once and wired by hand. */
class ServerDependencies(
    val config: ServerConfig,
    httpClient: HttpClient,
    private val scope: CoroutineScope,
) {
    private val homeAssistantClient: HomeAssistantClient? = if (config.isDemo) {
        null
    } else {
        HomeAssistantClient(
            webSocketUrl = config.homeAssistantWebSocketUrl,
            restBaseUrl = config.homeAssistantRestUrl,
            accessToken = config.accessToken,
            scope = scope,
        )
    }
    val homeAssistant: HomeAssistant = homeAssistantClient ?: DemoHome()

    val layoutRepository = LayoutRepository(config.dataDir)
    val playerApkRepository = PlayerApkRepository(config.apkDir)
    val apiKeyRepository = ApiKeyRepository(config.dataDir)
    private val renderDataRepository = RenderDataRepository(homeAssistant)
    private val pictureRepository = PictureRepository(homeAssistant, httpClient, config.mapTileUrl)
    private val languageRepository = LanguageRepository(homeAssistant, layoutRepository)

    val screenService = ScreenService(layoutRepository)
    val themeService = ThemeService(layoutRepository)
    val homeAssistantCatalogService = HomeAssistantCatalogService(homeAssistant)

    val playerHub = PlayerHub(
        PlayerSessionContext(
            serverVersion = config.version,
            homeAssistant = homeAssistant,
            layoutRepository = layoutRepository,
            screenService = screenService,
            renderDataRepository = renderDataRepository,
            pictureRepository = pictureRepository,
            languageRepository = languageRepository,
            playerApkRepository = playerApkRepository,
            actionRouter = ActionRouter(homeAssistant) { languageRepository.current() },
            screenClock = ScreenClock(homeAssistant, languageRepository),
            scope = scope,
        ),
    )

    fun start() {
        homeAssistantClient?.start()
        if (config.isDemo && layoutRepository.layout.value.screens.isEmpty()) {
            layoutRepository.update { it.copy(screens = listOf(DemoLayout.screen())) }
        }
        playerHub.start()
        scope.launch {
            homeAssistant.isConnected.collect { isConnected ->
                if (isConnected) languageRepository.loadHomeAssistantWords()
            }
        }
    }
}
