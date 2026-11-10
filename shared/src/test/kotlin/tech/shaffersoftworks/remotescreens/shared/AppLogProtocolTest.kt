package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLogProtocolTest {
    private val entry = AppLogEntry(
        eventId = "event-1",
        timestampMs = 123L,
        level = AppLogLevel.ERROR,
        tag = "Player",
        message = "failure",
    )

    @Test
    fun batchAndAcknowledgementRoundTrip() {
        val batch = PlayerMessage.AppLog(listOf(entry, entry.copy(eventId = "event-2", level = AppLogLevel.WARNING)))
        assertEquals(batch, Protocol.decodePlayer(Protocol.encode(batch)))
        val ack = ServerMessage.AppLogAck(listOf("event-1", "event-2"))
        assertEquals(ack, Protocol.decodeServer(Protocol.encode(ack)))
    }

    @Test
    fun levelsUseLowercaseWireNames() {
        val text = Protocol.encode(PlayerMessage.AppLog(listOf(entry)))
        assertEquals(true, text.contains(""""level":"error""""))
    }

    @Test
    fun anEntryWithoutAnEventIdIsNotAValidBatch() {
        assertNull(
            Protocol.decodePlayer(
                """{"type":"app_log","entries":[{"timestampMs":1,"level":"warning","tag":"t","message":"m"}]}""",
            ),
        )
    }
}
