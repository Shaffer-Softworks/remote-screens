package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import tech.shaffersoftworks.remotescreens.server.config.ServerConfig
import tech.shaffersoftworks.remotescreens.server.di.ServerDependencies
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.player.PlayerStatus
import tech.shaffersoftworks.remotescreens.server.player.PlayerTokens
import tech.shaffersoftworks.remotescreens.server.plugins.ApiJson
import tech.shaffersoftworks.remotescreens.server.plugins.configureSerialization
import tech.shaffersoftworks.remotescreens.server.service.CameraStreamService
import tech.shaffersoftworks.remotescreens.server.service.StreamingHome
import tech.shaffersoftworks.remotescreens.shared.DeviceTelemetry
import tech.shaffersoftworks.remotescreens.shared.EnvironmentTelemetry
import tech.shaffersoftworks.remotescreens.shared.LocationTelemetry
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private const val TABLET_TOKEN = "tablet-token"
private const val POLL_ATTEMPTS = 50
private const val POLL_DELAY_MS = 20L

class IntegrationPlayersRouteTest {
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
    private val dependencies = ServerDependencies(
        config,
        HttpClient(),
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    init {
        val tablets = listOf(
            PlayerRecord("tablet", "Hallway", screenId = "home", tokenHash = PlayerTokens.hash(TABLET_TOKEN)),
            PlayerRecord("spare", "Spare tablet", screenId = null),
        )
        dependencies.layoutRepository.update { layout -> layout.copy(players = tablets) }
    }

    private fun withRoutes(test: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        install(WebSockets)
        application { configureSerialization() }
        routing {
            playerRoutes(
                dependencies.playerHub,
                dependencies.playerApkRepository,
                dependencies.enrollmentService,
                CameraStreamService(StreamingHome()),
            )
            route("/integration") { integrationRoutes(dependencies.screenService, dependencies.playerHub) }
        }
        test(createClient { install(ClientWebSockets) })
    }

    private suspend fun players(client: HttpClient): List<PlayerStatus> {
        val response = client.get("/integration/players")
        assertEquals(HttpStatusCode.OK, response.status)
        return ApiJson.decodeFromString(ListSerializer(PlayerStatus.serializer()), response.bodyAsText())
    }

    @Test
    fun everyTabletIsListedEvenWithoutAScreen() = withRoutes { client ->
        val tablets = players(client)

        assertEquals(setOf("tablet", "spare"), tablets.map { it.deviceId }.toSet())
        assertTrue(tablets.none { it.isOnline })
        assertTrue(tablets.all { it.telemetry == null })
    }

    @Test
    fun aConnectedTabletsTelemetryIsServed() = withRoutes { client ->
        val telemetry = DeviceTelemetry(
            hardware = listOf("light"),
            environment = EnvironmentTelemetry(illuminanceLux = 182f),
            location = LocationTelemetry(latitude = 52.1, longitude = 4.3, fixedAt = 1L),
        )
        client.webSocket("/player") {
            val hello = PlayerMessage.Hello(
                deviceId = "tablet",
                deviceName = "Hallway",
                token = TABLET_TOKEN,
                widthDp = 800,
                heightDp = 480,
                density = 1f,
                appVersion = 1,
                capabilities = listOf(Protocol.CAPABILITY_TELEMETRY),
            )
            send(Frame.Text(Protocol.encode(hello)))
            send(Frame.Text(Protocol.encode(PlayerMessage.Telemetry(telemetry))))

            var reported = players(client).first { it.deviceId == "tablet" }
            var attempts = 0
            while (reported.telemetry == null && attempts++ < POLL_ATTEMPTS) {
                delay(POLL_DELAY_MS.milliseconds)
                reported = players(client).first { it.deviceId == "tablet" }
            }

            assertTrue(reported.isOnline)
            assertEquals(telemetry, reported.telemetry)
            assertNotNull(reported.telemetryAt)
            assertNull(players(client).first { it.deviceId == "spare" }.telemetry)
        }
    }

    @Test
    fun invalidCredentialsDoNotEraseAPairedTablet() = withRoutes { client ->
        client.webSocket("/player") {
            send(Frame.Text(Protocol.encode(hello(deviceId = "tablet", token = "wrong"))))
        }

        val record = dependencies.layoutRepository.layout.value.players.first { it.deviceId == "tablet" }
        assertEquals("home", record.screenId)
        assertEquals(PlayerTokens.hash(TABLET_TOKEN), record.tokenHash)
        assertTrue(players(client).none { it.deviceId == "tablet" && it.isOnline })
    }

    @Test
    fun invalidCredentialsDoNotDisplaceAnActiveSession() = withRoutes { client ->
        client.webSocket("/player") {
            send(Frame.Text(Protocol.encode(hello(deviceId = "tablet", token = TABLET_TOKEN))))
            incoming.receive()
            var reported = players(client).first { it.deviceId == "tablet" }
            var attempts = 0
            while (!reported.isOnline && attempts++ < POLL_ATTEMPTS) {
                delay(POLL_DELAY_MS.milliseconds)
                reported = players(client).first { it.deviceId == "tablet" }
            }
            assertTrue(reported.isOnline)

            client.webSocket("/player") {
                send(Frame.Text(Protocol.encode(hello(deviceId = "tablet", token = "wrong"))))
            }

            assertTrue(players(client).first { it.deviceId == "tablet" }.isOnline)
            send(Frame.Text(Protocol.encode(PlayerMessage.Ping)))
            val pong = incoming.receive() as Frame.Text
            assertEquals(ServerMessage.Pong, Protocol.decodeServer(pong.readText()))
        }
    }

    @Test
    fun anUnpairedClientCannotCreateCalendarEvents() = withRoutes { client ->
        val home = dependencies.homeAssistant as DemoHome
        val callsBefore = home.calls.size
        client.webSocket("/player") {
            send(Frame.Text(Protocol.encode(hello(deviceId = "intruder", token = null))))
            incoming.receive()
            send(
                Frame.Text(
                    Protocol.encode(
                        PlayerMessage.CalendarCreate(
                            tileId = "cal",
                            entityId = "calendar.family",
                            summary = "Pwned",
                            startDate = "2026-07-15",
                            endDate = "2026-07-15",
                        ),
                    ),
                ),
            )
            delay(POLL_DELAY_MS.milliseconds)
        }
        assertTrue(home.calls.drop(callsBefore).none { it.first == "calendar" })
    }

    private fun hello(deviceId: String, token: String?): PlayerMessage.Hello = PlayerMessage.Hello(
        deviceId = deviceId,
        deviceName = deviceId,
        token = token,
        widthDp = 800,
        heightDp = 480,
        density = 1f,
        appVersion = 1,
    )
}
