package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntercomProtocolTest {
    @Test
    fun anIntercomReportRoundTrips() {
        val message = PlayerMessage.IntercomReport(
            status = "Connected to Kitchen",
            muted = true,
            busy = true,
            connectedId = "station-kitchen",
            stations = listOf(IntercomStation("station-kitchen", "Kitchen", inCall = true)),
        )

        val decoded = Protocol.decodePlayer(Protocol.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun anIdleReportLeavesTheOptionalFieldsOffTheWire() {
        val encoded = Protocol.encode(PlayerMessage.IntercomReport(status = "Online"))

        assertFalse(encoded.contains("connectedId"))
        assertFalse(encoded.contains("stations"))
        assertFalse(encoded.contains("muted"))
    }

    @Test
    fun intercomConfigAndCommandRoundTrip() {
        val config = ServerMessage.IntercomConfig(
            enabled = true,
            displayName = "Living Room",
            pageIndexes = listOf(2),
        )
        val command = ServerMessage.IntercomCommand(verb = "call", clientId = "station-kitchen")

        assertEquals(config, Protocol.decodeServer(Protocol.encode(config)))
        assertEquals(command, Protocol.decodeServer(Protocol.encode(command)))
    }

    @Test
    fun anEmptyIntercomPageListStaysOffTheWire() {
        val encoded = Protocol.encode(ServerMessage.IntercomConfig(enabled = false, displayName = "Kitchen"))

        assertFalse(encoded.contains("pageIndexes"))
    }

    @Test
    fun anUnknownIntercomFieldIsIgnored() {
        val text = """{"type":"intercom_report","status":"Online","extra":1}"""

        val decoded = Protocol.decodePlayer(text) as PlayerMessage.IntercomReport

        assertEquals("Online", decoded.status)
        assertEquals(emptyList<IntercomStation>(), decoded.stations)
    }
}
