package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLogProtocolTest {
    @Test
    fun entryAndAcknowledgementRoundTrip() {
        val entry = PlayerMessage.AppLog(123L, "error", "Player", "failure", "event-1")
        assertEquals(entry, Protocol.decodePlayer(Protocol.encode(entry)))
        val ack = ServerMessage.AppLogAck("event-1")
        assertEquals(ack, Protocol.decodeServer(Protocol.encode(ack)))
    }

    @Test
    fun legacyEntryWithoutEventIdStillDecodes() {
        val entry = Protocol.decodePlayer(
            """{"type":"app_log","timestampMs":123,"level":"warning","tag":"Player","message":"old build"}""",
        ) as PlayerMessage.AppLog
        assertNull(entry.eventId)
    }
}
