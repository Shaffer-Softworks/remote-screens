package tech.shaffersoftworks.remotescreens.server.intercom

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalingRouteTest {
    @Test
    fun aBrowserAndATabletShareOneRoster() = testApplication {
        val signaling = SignalingService(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        install(WebSockets)
        routing { signalingRoutes(signaling) }
        val client = createClient { install(ClientWebSockets) }

        client.webSocket("/webrtc") {
            send(Frame.Text("""{"type":"register","clientId":"station-tablet","displayName":"Kitchen"}"""))
            incoming.receive()

            client.webSocket("/webrtc") {
                send(Frame.Text("""{"type":"register","clientId":"ha-browser","displayName":"Michael"}"""))
                var roster: JsonObject? = null
                while (roster == null) {
                    val frame = incoming.receive() as Frame.Text
                    val message = Json.parseToJsonElement(frame.readText()).jsonObject
                    if (message["type"]?.jsonPrimitive?.content == "clientsList") roster = message
                }
                val names = roster["clients"]!!.jsonArray.map {
                    it.jsonObject["displayName"]!!.jsonPrimitive.content
                }
                assertEquals(setOf("Kitchen", "Michael"), names.toSet())
                assertTrue(names.size == 2)
            }
        }
    }
}
