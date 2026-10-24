package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.player.action.AgendaMutation
import tech.shaffersoftworks.remotescreens.server.player.action.CalendarMutations
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CalendarAgendaStoreTest {
    private val ha = DemoHome()

    @Test
    fun theDemoAgendaListsTheFamilyEventAndAHoliday() = runBlocking {
        val snapshot = CalendarAgendaStore(ha).snapshot(ha.states.value, ZoneOffset.UTC)
        assertEquals(null, snapshot.errorMessage)
        assertEquals("Dentist appointment", snapshot.events.first().summary)
        assertTrue(snapshot.events.any { it.summary == "School holiday" && it.isAllDay })
        assertTrue(snapshot.calendars.any { it.entityId == "calendar.family" && it.canCreate && it.canDelete })
        assertTrue(snapshot.calendars.any { it.entityId == "calendar.holidays" && !it.canDelete })
    }

    @Test
    fun createSendsTheRpiServiceBody() = runBlocking {
        val result = CalendarMutations(ha, CalendarAgendaStore(ha)).create(
            PlayerMessage.CalendarCreate(
                tileId = "cal",
                entityId = "calendar.family",
                summary = "Soccer practice",
                location = "Field 2",
                allDay = false,
                startDate = "2026-07-15",
                startTime = "17:30",
                endDate = "2026-07-15",
                endTime = "18:30",
            ),
        )
        assertIs<AgendaMutation.Accepted>(result)
        val (domain, service, data) = ha.calls.last()
        assertEquals("calendar", domain)
        assertEquals("create_event", service)
        assertEquals("Soccer practice", data.getValue("summary").jsonPrimitive.contentOrNull)
        assertEquals("Field 2", data.getValue("location").jsonPrimitive.contentOrNull)
        assertEquals("2026-07-15 17:30:00", data.getValue("start_date_time").jsonPrimitive.contentOrNull)
        assertEquals("2026-07-15 18:30:00", data.getValue("end_date_time").jsonPrimitive.contentOrNull)
    }

    @Test
    fun deleteSendsTheCalendarEventCommand() = runBlocking {
        val result = CalendarMutations(ha, CalendarAgendaStore(ha)).delete(
            PlayerMessage.CalendarDelete(
                tileId = "cal",
                entityId = "calendar.family",
                uid = "demo-dentist",
                recurrenceId = "r1",
            ),
        )
        assertIs<AgendaMutation.Accepted>(result)
        val (type, payload) = ha.commands.last()
        assertEquals("calendar/event/delete", type)
        assertEquals("calendar.family", payload.getValue("entity_id").jsonPrimitive.contentOrNull)
        assertEquals("demo-dentist", payload.getValue("uid").jsonPrimitive.contentOrNull)
        assertEquals("r1", payload.getValue("recurrence_id").jsonPrimitive.contentOrNull)
    }

    @Test
    fun aStatePulseDuringAgendaLoadDoesNotStoreTheCancellation() = runBlocking {
        withTimeout(PROBE_TIMEOUT_MS) {
            val home = HangingCalendarList()
            val store = CalendarAgendaStore(home)
            val pulses = MutableStateFlow(0)
            val job = launch {
                pulses.collectLatest {
                    store.snapshot(home.states.value, ZoneOffset.UTC)
                }
            }
            delay(PULSE_GAP_MS)
            pulses.value = 1
            delay(PULSE_GAP_MS)
            assertEquals(null, store.latest.errorMessage)
            job.cancel()
        }
    }
}

private const val PULSE_GAP_MS = 200L
private const val PROBE_TIMEOUT_MS = 5_000L
private const val HANG_MS = 60_000L

/** Holds the calendar list request open so a newer state pulse can cancel it. */
private class HangingCalendarList(private val inner: DemoHome = DemoHome()) : HomeAssistant by inner {
    override suspend fun fetch(path: String): ByteArray? {
        if (isCalendarListPath(path)) delay(HANG_MS)
        return inner.fetch(path)
    }
}
