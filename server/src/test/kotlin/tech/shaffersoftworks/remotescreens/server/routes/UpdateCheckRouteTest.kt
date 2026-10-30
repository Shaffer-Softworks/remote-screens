package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tech.shaffersoftworks.remotescreens.server.config.ServerConfig
import tech.shaffersoftworks.remotescreens.server.di.ServerDependencies
import tech.shaffersoftworks.remotescreens.server.player.PlayerTokens
import tech.shaffersoftworks.remotescreens.server.service.CameraStreamService
import tech.shaffersoftworks.remotescreens.server.service.StreamingHome
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PLAYER_TOKEN = "player-token"

class UpdateCheckRouteTest {
    private val config = ServerConfig(
        isSupervisor = false,
        isDemo = true,
        homeAssistantWebSocketUrl = "",
        homeAssistantRestUrl = "",
        accessToken = "",
        dataDir = Files.createTempDirectory("data").toFile(),
        staticDir = null,
        apkDir = Files.createTempDirectory("apk").toFile(),
        ingressHost = "127.0.0.1",
        ingressPort = 0,
        playerPort = 0,
        hostname = "localhost",
        version = "test",
    )
    private val dependencies =
        ServerDependencies(
            config,
            HttpClient(),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    private val cameraStreamService = CameraStreamService(StreamingHome())

    init {
        val tablet = PlayerRecord(
            "tablet",
            "Hallway",
            screenId = "home",
            tokenHash = PlayerTokens.hash(PLAYER_TOKEN),
        )
        dependencies.layoutRepository.update { layout -> layout.copy(players = listOf(tablet)) }
    }

    private fun withRoutes(test: suspend (HttpClient) -> Unit) = testApplication {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
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
    fun metadataRequiresAnAdoptedPlayerToken() = withRoutes { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/player/update").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/player/update?token=wrong").status)
    }

    @Test
    fun missingApkReturnsNoContent() = withRoutes { client ->
        assertEquals(HttpStatusCode.NoContent, client.get("/player/update?token=$PLAYER_TOKEN").status)
    }

    @Test
    fun metadataDescribesTheCurrentlyBundledApk() = withRoutes { client ->
        val apkDir = checkNotNull(config.apkDir)
        java.io.File(apkDir, "remote-screens.apk").writeText("apk")
        java.io.File(apkDir, "version.json").writeText(
            """{"versionCode":142,"versionName":"0.1.42"}""",
        )
        val response = client.get("/player/update?token=$PLAYER_TOKEN")
        val update = kotlinx.serialization.json.Json
            .decodeFromString<tech.shaffersoftworks.remotescreens.shared.ServerMessage.UpdateAvailable>(
                response.bodyAsText(),
            )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(142, update.versionCode)
        assertEquals("0.1.42", update.versionName)
        assertEquals("/player/apk", update.path)
    }
}
