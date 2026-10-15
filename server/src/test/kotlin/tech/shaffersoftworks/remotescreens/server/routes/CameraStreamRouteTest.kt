package tech.shaffersoftworks.remotescreens.server.routes

import tech.shaffersoftworks.remotescreens.server.config.ServerConfig
import tech.shaffersoftworks.remotescreens.server.di.ServerDependencies
import tech.shaffersoftworks.remotescreens.server.player.PlayerTokens
import tech.shaffersoftworks.remotescreens.server.service.CameraStreamService
import tech.shaffersoftworks.remotescreens.server.service.StreamingHome
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PLAYER_TOKEN = "player-token"

class CameraStreamRouteTest {
    private val config = ServerConfig(
        isSupervisor = false,
        isDemo = true,
        homeAssistantWebSocketUrl = "",
        homeAssistantRestUrl = "",
        accessToken = "",
        dataDir = Files.createTempDirectory("data").toFile(),
        staticDir = null,
        apkDir = null,
        ingressHost = "127.0.0.1",
        ingressPort = 0,
        playerPort = 0,
        hostname = "localhost",
        version = "test",
    )
    private val dependencies =
        ServerDependencies(config, HttpClient(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    private val cameraStreamService = CameraStreamService(StreamingHome())

    init {
        val tablet = PlayerRecord("tablet", "Hallway", screenId = "home", tokenHash = PlayerTokens.hash(PLAYER_TOKEN))
        dependencies.layoutRepository.update { layout -> layout.copy(players = listOf(tablet)) }
    }

    private fun withRoutes(test: suspend (HttpClient) -> Unit) = testApplication {
        install(WebSockets)
        routing {
            playerRoutes(
                dependencies.playerHub,
                dependencies.playerApkRepository,
                dependencies.enrollmentService,
                cameraStreamService,
            )
        }
        test(client)
    }

    @Test
    fun anAdoptedTabletPlaysAStream() = withRoutes { client ->
        val path = checkNotNull(cameraStreamService.hlsPath("camera.front_door"))

        val response = client.get(path) { bearerAuth(PLAYER_TOKEN) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("#EXTM3U\n", response.bodyAsText())
        assertEquals("application/vnd.apple.mpegurl", response.headers[HttpHeaders.ContentType])
    }

    @Test
    fun aStrangerIsTurnedAway() = withRoutes { client ->
        val path = checkNotNull(cameraStreamService.hlsPath("camera.front_door"))

        assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(path) { bearerAuth("guess") }.status)
    }

    @Test
    fun anUnknownStreamIsNotFound() = withRoutes { client ->
        val response = client.get("${CameraStreamService.PLAYER_PATH}/unknown/master_playlist.m3u8") {
            bearerAuth(PLAYER_TOKEN)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
