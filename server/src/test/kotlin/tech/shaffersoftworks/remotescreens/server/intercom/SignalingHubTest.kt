package tech.shaffersoftworks.remotescreens.server.intercom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalingHubTest {
    @Test
    fun aSecondRegisterClosesTheFirstSession() {
        val hub = SignalingHub()
        hub.handleMessage("s1", register("c1"))
        val second = hub.handleMessage("s2", register("c1"))
        assertTrue(second.closeSessionIds.contains("s1"))
        assertEquals(listOf("c1"), hub.poll().first.map { it.clientId })
    }

    @Test
    fun anIdleSessionIsClosedWhenTheRosterIsBuilt() {
        var clock = 1_000_000_000_000L
        val hub = SignalingHub(now = { clock })
        hub.handleMessage("old", register("a"))
        clock += SignalingHub.STALE_CLIENT_MS + 1_000L
        val joined = hub.handleMessage("new", register("b"))
        assertTrue(joined.closeSessionIds.contains("old"))
        assertEquals(listOf("b"), hub.poll().first.map { it.clientId })
    }

    @Test
    fun aPongFromARegisteredStationIsQuiet() {
        val hub = SignalingHub()
        hub.handleMessage("s1", register("c1"))
        val pong = hub.handleMessage("s1", """{"type":"pong"}""")
        assertTrue(pong.sends.isEmpty())
        assertEquals("c1", hub.poll().first.single().clientId)
    }

    @Test
    fun aPongFromAnUnknownSessionIsRejected() {
        val hub = SignalingHub()
        val pong = hub.handleMessage("unknown", """{"type":"pong"}""")
        val error = Json.parseToJsonElement(pong.sends.single().json).jsonObject
        assertEquals("error", error.field("type"))
        assertEquals("not_registered", error.field("message"))
    }

    @Test
    fun anOfflineTargetStaysOutOfACall() {
        val hub = SignalingHub()
        hub.handleMessage("s1", register("a", "Kitchen"))
        val offer = hub.handleMessage("s1", """{"type":"offer","sdp":"v=0","to":"missing"}""")
        val message = Json.parseToJsonElement(offer.sends.single().json).jsonObject
        assertEquals("unavailable", message.field("type"))
        assertEquals("offline", message.field("reason"))
        assertFalse(hub.poll().first.single().inCall)
    }

    @Test
    fun aBusyTargetIsRefused() {
        val hub = SignalingHub()
        hub.handleMessage("s1", register("a"))
        hub.handleMessage("s2", register("b"))
        hub.handleMessage("s3", register("c"))
        hub.handleMessage("s1", """{"type":"offer","sdp":"v=0","to":"b"}""")
        val refused = hub.handleMessage("s3", """{"type":"offer","sdp":"v=0","to":"b"}""")
        val message = Json.parseToJsonElement(refused.sends.single().json).jsonObject
        assertEquals("busy", message.field("reason"))
        assertEquals("s3", refused.sends.single().sessionId)
    }

    @Test
    fun getClientsAnswersOnlyTheStationThatAsked() {
        val hub = SignalingHub()
        hub.handleMessage("s1", register("a"))
        hub.handleMessage("s2", register("b"))
        val asked = hub.handleMessage("s1", """{"type":"getClients"}""")
        assertEquals(listOf("s1"), asked.sends.map { it.sessionId })
        val clients = Json.parseToJsonElement(asked.sends.single().json).jsonObject["clients"]!!.jsonArray
        assertEquals(2, clients.size)
        assertTrue(clients.all { !it.jsonObject["inCall"]!!.jsonPrimitive.boolean })
    }

    @Test
    fun aCallMarksBothStationsUntilHangup() {
        val hub = SignalingHub()
        hub.handleMessage("s1", register("a", "Kitchen"))
        hub.handleMessage("s2", register("b", "Hall"))
        val offer = hub.handleMessage("s1", """{"type":"offer","sdp":"v=0","to":"b"}""")
        assertTrue(offer.sends.any { it.sessionId == "s2" && it.json.contains("\"type\":\"offer\"") })
        assertTrue(hub.poll().first.all { it.inCall })
        hub.handleMessage("s1", """{"type":"hangup","to":"b"}""")
        assertTrue(hub.poll().first.none { it.inCall })
    }

    private fun register(clientId: String, displayName: String? = null): String = buildJsonObject {
        put("type", "register")
        put("clientId", clientId)
        if (displayName != null) put("displayName", displayName)
    }.toString()

    private fun kotlinx.serialization.json.JsonObject.field(key: String): String? =
        this[key]?.jsonPrimitive?.content
}
