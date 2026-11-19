package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalingFrameTest {
    private fun wire(frame: SignalingFrame): JsonObject =
        SignalingFrame.JSON.parseToJsonElement(SignalingFrame.encode(frame)).jsonObject

    private fun json(text: String): JsonObject = SignalingFrame.JSON.parseToJsonElement(text).jsonObject

    @Test
    fun everyFrameRoundTrips() {
        val frames = listOf(
            SignalingFrame.Register("station-a", "Kitchen"),
            SignalingFrame.Registered("station-a"),
            SignalingFrame.Offer("v=0", to = "station-b", from = "station-a"),
            SignalingFrame.Answer("v=0", to = "station-a", from = "station-b"),
            SignalingFrame.Candidate("candidate:1", "0", sdpMLineIndex = 1, to = "station-b", from = "station-a"),
            SignalingFrame.Hangup(to = "station-b", from = "station-a"),
            SignalingFrame.GetClients,
            SignalingFrame.ClientsList(listOf(SignalingClient("station-a", "Kitchen", inCall = true))),
            SignalingFrame.Heartbeat,
            SignalingFrame.Ping(ts = 1_700_000_000_000),
            SignalingFrame.Pong(ts = 1_700_000_000_000),
            SignalingFrame.Unavailable("station-b", SignalingFrame.REASON_BUSY),
            SignalingFrame.Error(SignalingFrame.ERROR_NOT_REGISTERED),
            SignalingFrame.Replaced(bySession = "session-2"),
        )

        for (frame in frames) assertEquals(frame, SignalingFrame.decode(SignalingFrame.encode(frame)))
    }

    @Test
    fun theWireNamesMatchTheStandaloneServer() {
        assertEquals(json("""{"type":"getClients"}"""), wire(SignalingFrame.GetClients))

        assertEquals(
            json("""{"type":"offer","sdp":"v=0","to":"station-b"}"""),
            wire(SignalingFrame.Offer("v=0", to = "station-b")),
        )

        assertEquals(
            json("""{"type":"candidate","candidate":"c","sdpMLineIndex":0,"to":"station-b"}"""),
            wire(SignalingFrame.Candidate("c", sdpMLineIndex = 0, to = "station-b")),
        )

        assertEquals(
            json("""{"type":"unavailable","targetId":"station-b","reason":"offline"}"""),
            wire(SignalingFrame.Unavailable("station-b")),
        )

        assertEquals(json("""{"type":"ping","ts":0}"""), wire(SignalingFrame.Ping()))

        assertEquals(
            json("""{"type":"clientsList","clients":[{"clientId":"ha-1","displayName":null,"inCall":false}]}"""),
            wire(SignalingFrame.ClientsList(listOf(SignalingClient("ha-1")))),
        )
    }

    @Test
    fun aBrowserCandidateWithExtraFieldsDecodes() {
        val text = """{"type":"candidate","to":"station-a","candidate":"c","sdpMid":"0","sdpMLineIndex":0,
            |"usernameFragment":"x"}
        """.trimMargin()

        assertEquals(
            SignalingFrame.Candidate("c", "0", sdpMLineIndex = 0, to = "station-a"),
            SignalingFrame.decode(text),
        )
    }

    @Test
    fun missingFieldsTakeTheirDefaults() {
        assertEquals(SignalingFrame.Ping(ts = 0), SignalingFrame.decode("""{"type":"ping"}"""))
        assertEquals(SignalingFrame.Register(clientId = ""), SignalingFrame.decode("""{"type":"register"}"""))

        assertEquals(
            SignalingFrame.Unavailable("station-b", SignalingFrame.REASON_OFFLINE),
            SignalingFrame.decode("""{"type":"unavailable","targetId":"station-b"}"""),
        )
    }

    @Test
    fun anUnknownTypeIsNotAFrameButKeepsItsType() {
        val text = """{"type":"status","busy":true}"""

        assertNull(SignalingFrame.decode(text))
        assertEquals("status", SignalingFrame.typeOf(text))
        assertNull(SignalingFrame.decode("not json"))
        assertNull(SignalingFrame.typeOf("[1]"))
    }
}
