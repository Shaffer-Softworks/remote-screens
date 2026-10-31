package tech.shaffersoftworks.remotescreens.server.routes

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.withTimeout
import tech.shaffersoftworks.remotescreens.server.config.ServerConfig
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.di.ServerDependencies
import tech.shaffersoftworks.remotescreens.server.player.PlayerTokens
import tech.shaffersoftworks.remotescreens.server.plugins.configureErrorHandling
import tech.shaffersoftworks.remotescreens.server.plugins.configureSerialization
import tech.shaffersoftworks.remotescreens.shared.NotificationButton
import tech.shaffersoftworks.remotescreens.shared.NotificationRequest
import tech.shaffersoftworks.remotescreens.shared.NotificationResult
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.PlayerRecord
import tech.shaffersoftworks.remotescreens.shared.Protocol
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationRouteTest {
    private fun withHub(test: suspend (HttpClient, ServerDependencies) -> Unit) = testApplication {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val http = HttpClient()
        val data = Files.createTempDirectory("notifications").toFile()
        val config = ServerConfig(
            isSupervisor = false, isDemo = true, homeAssistantWebSocketUrl = "",
            homeAssistantRestUrl = "", accessToken = "", dataDir = data,
            staticDir = null, apkDir = null, ingressHost = "127.0.0.1",
            ingressPort = 0, playerPort = 0, hostname = "localhost", version = "test",
        )
        val dependencies = ServerDependencies(config, http, scope)
        dependencies.layoutRepository.update { layout ->
            layout.copy(
                screens = listOf(Screen("home", "Home")),
                players = listOf(
                    PlayerRecord("one", "Kitchen", screenId = "home", tokenHash = PlayerTokens.hash("token")),
                    PlayerRecord("two", "Hallway", screenId = "home"),
                    PlayerRecord("unpaired", "Waiting"),
                ),
            )
        }
        application {
            install(WebSockets)
            configureSerialization()
            configureErrorHandling()
            routing {
                route("/integration") { integrationRoutes(dependencies.screenService, dependencies.playerHub) }
                route("/api") { notificationRoutes(dependencies.playerHub) }
                webSocket("/player") { dependencies.playerHub.serve(this, "test") }
            }
        }
        try {
            test(createClient { install(ClientWebSockets) }, dependencies)
        } finally {
            scope.cancel()
            http.close()
        }
    }

    private suspend fun HttpClient.notify(request: NotificationRequest, path: String = "/integration/notifications"):
        NotificationResult {
        val response = post(path) {
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(NotificationRequest.serializer(), request))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Protocol.json.decodeFromString(NotificationResult.serializer(), response.bodyAsText())
    }

    private suspend fun DefaultWebSocketSession.hello(capable: Boolean = true) {
        send(Frame.Text(Protocol.encode(PlayerMessage.Hello(
            deviceId = "one",
            deviceName = "Kitchen",
            token = "token",
            widthDp = 800,
            heightDp = 480,
            density = 1f,
            appVersion = 1,
            capabilities = if (capable) listOf(Protocol.CAPABILITY_NOTIFICATIONS) else emptyList(),
        ))))
        untilMessage { it is ServerMessage.Document && it.slot == Slot.MAIN }
    }

    private suspend fun DefaultWebSocketSession.untilMessage(predicate: (ServerMessage) -> Boolean): ServerMessage =
        withTimeout(10_000) {
            var found: ServerMessage? = null
            while (found == null) {
                val frame = incoming.receive() as? Frame.Text
                val message = frame?.let { Protocol.decodeServer(it.readText()) }
                if (message != null && predicate(message)) found = message
            }
            found
        }

    @Test
    fun offlineRecipientsAreReportedAndUnpairedTabletsExcluded() = withHub { client, _ ->
        val result = client.notify(NotificationRequest("Hi", all = true))
        assertEquals(setOf("one", "two"), result.devices.map { it.deviceId }.toSet())
        assertTrue(result.devices.all { it.status == "offline" })
    }

    @Test
    fun exactTargetDoesNotReachOtherTabletsOnTheScreen() = withHub { client, _ ->
        client.webSocket("/player") {
            hello()
            val result = client.notify(NotificationRequest("Laundry", targets = listOf("one")))
            assertEquals(listOf("one"), result.devices.map { it.deviceId })
            assertEquals("sent", result.devices.single().status)
            val banner = untilMessage { it is ServerMessage.NotificationBanner } as ServerMessage.NotificationBanner
            assertEquals("Laundry", banner.title)
            assertEquals(10, banner.timeout)
        }
    }

    @Test
    fun overlappingTargetsAreDeduplicatedAndPartialResultsReturned() = withHub { client, _ ->
        client.webSocket("/player") {
            hello()
            val result = client.notify(
                NotificationRequest("Hi", targets = listOf("one", "home", "home")),
                "/api/notifications",
            )
            assertEquals(2, result.devices.size)
            assertEquals("sent", result.devices.first { it.deviceId == "one" }.status)
            assertEquals("offline", result.devices.first { it.deviceId == "two" }.status)
        }
    }

    @Test
    fun legacyPlayersSkipBannersButReceiveWakeEnabledAlerts() = withHub { client, _ ->
        client.webSocket("/player") {
            hello(capable = false)
            val banner = NotificationRequest("Hi", targets = listOf("one"))
            val alert = banner.copy(presentation = "alert")
            assertEquals("unsupported", client.notify(banner).devices.single().status)
            assertEquals("unsupported", client.notify(alert).devices.single().status)
            assertEquals("sent", client.notify(alert.copy(wake = true)).devices.single().status)
            assertTrue(untilMessage { it is ServerMessage.Wake } is ServerMessage.Wake)
            untilMessage { it is ServerMessage.Document && it.slot == Slot.ALERT }
        }
    }

    @Test
    fun silentAlertHasNoWakeAndRepeatedContentGetsANewRevision() = withHub { client, _ ->
        client.webSocket("/player") {
            hello()
            val request = NotificationRequest(
                "Check garage",
                targets = listOf("one"),
                presentation = "alert",
                timeout = 0,
            )
            client.notify(request)
            val received = untilMessage { it is ServerMessage.Document || it is ServerMessage.Wake }
            val first = received as ServerMessage.Document
            assertFalse(first.wakeOnShow)
            assertEquals(0, first.timeout)
            client.notify(request)
            val second = untilMessage { it is ServerMessage.Document } as ServerMessage.Document
            assertTrue(second.rev > first.rev)
        }
    }

    @Test
    fun staleOrRepeatedBannerActionsCannotInvokeServices() = withHub { client, dependencies ->
        client.webSocket("/player") {
            hello()
            val home = dependencies.homeAssistant as DemoHome
            val request = NotificationRequest(
                "Open?",
                targets = listOf("one"),
                buttons = listOf(NotificationButton("Open", "lock.open")),
            )
            client.notify(request)
            val first = untilMessage { it is ServerMessage.NotificationBanner } as ServerMessage.NotificationBanner
            client.notify(request)
            val second = untilMessage { it is ServerMessage.NotificationBanner } as ServerMessage.NotificationBanner
            val event = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(10_000) { home.events.first { it.type == "remote_screens_alert_action" } }
            }
            send(Frame.Text(Protocol.encode(PlayerMessage.NotificationAction(first.id, 0))))
            send(Frame.Text(Protocol.encode(PlayerMessage.NotificationAction(second.id, 0))))
            send(Frame.Text(Protocol.encode(PlayerMessage.NotificationAction(second.id, 0))))
            send(Frame.Text(Protocol.encode(PlayerMessage.Ping)))
            untilMessage { it == ServerMessage.Pong }
            assertEquals(1, home.calls.count { it.first == "lock" && it.second == "open" })
            assertEquals("one", event.await().data["device"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun invalidRequestsReturn400WithoutBroadcasting() = withHub { client, _ ->
        for (request in listOf(
            NotificationRequest("Hi"),
            NotificationRequest("Hi", targets = listOf("missing")),
            NotificationRequest("Hi", all = true, timeout = 0),
            NotificationRequest("Hi", all = true, camera = "camera.door"),
        )) {
            val response = client.post("/integration/notifications") {
                contentType(ContentType.Application.Json)
                setBody(Protocol.json.encodeToString(NotificationRequest.serializer(), request))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
