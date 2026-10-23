package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarDialogProtocolTest {
    @Test
    fun aCreateDialogReadsBackAsSent() {
        val dialog = ServerMessage.CalendarDialog(
            mode = CalendarDialogMode.CREATE,
            tileId = "cal",
            calendars = listOf(CalendarChoice("calendar.family", "Family")),
        )
        assertEquals(dialog, Protocol.decodeServer(Protocol.encode(dialog)))
    }

    @Test
    fun aDeleteDialogAndTheRepliesRoundTrip() {
        val dialog = ServerMessage.CalendarDialog(
            mode = CalendarDialogMode.DELETE,
            tileId = "cal",
            event = CalendarDeleteTarget(
                entityId = "calendar.family",
                uid = "event-1",
                recurrenceId = "r1",
                summary = "Soccer practice",
            ),
        )
        assertEquals(dialog, Protocol.decodeServer(Protocol.encode(dialog)))

        val create = PlayerMessage.CalendarCreate(
            tileId = "cal",
            entityId = "calendar.family",
            summary = "Soccer practice",
            location = "Field 2",
            allDay = false,
            startDate = "2026-07-15",
            startTime = "17:30",
            endDate = "2026-07-15",
            endTime = "18:30",
        )
        assertEquals(create, Protocol.decodePlayer(Protocol.encode(create)))

        val delete = PlayerMessage.CalendarDelete(
            tileId = "cal",
            entityId = "calendar.family",
            uid = "event-1",
        )
        val decoded = Protocol.decodePlayer(Protocol.encode(delete)) as PlayerMessage.CalendarDelete
        assertEquals(delete, decoded)
        assertNull(decoded.recurrenceId)
    }
}
