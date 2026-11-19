package tech.shaffersoftworks.remotescreens.server.render.panels.calendar

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One calendar event, as Home Assistant puts it on a calendar entity's attributes. */
data class CalendarEvent(
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime?,
    val allDay: Boolean,
    val calendar: String,
    val location: String?,
)

private val HA_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** The next (or in-progress) event on [entityState], or null when the calendar is empty. */
fun calendarEvent(entityState: EntityState, zone: ZoneId): CalendarEvent? {
    fun time(text: String?) =
        text?.let {
            runCatching { LocalDateTime.parse(text, HA_LOCAL).atZone(zone) }.getOrNull()
        }

    val title = entityState.calendar.message
    val start = time(entityState.calendar.startTime)

    return if (title == null || start == null) {
        null
    } else {
        CalendarEvent(
            title = title,
            start = start,
            end = time(entityState.calendar.endTime),
            allDay = entityState.calendar.allDay == true,
            calendar = entityState.name,
            location = entityState.calendar.location,
        )
    }
}

/** The soonest upcoming event across [calendars], ignoring events that have already ended. */
fun nextCalendarEvent(
    calendars: List<String>,
    states: Map<String, EntityState>,
    zone: ZoneId,
    now: ZonedDateTime = ZonedDateTime.now(zone),
): CalendarEvent? =
    calendars.mapNotNull { id -> states[id]?.let { calendarEvent(it, zone) } }
        .filter { it.end == null || !it.end.isBefore(now) }
        .minByOrNull { it.start }

fun isToday(event: CalendarEvent, zone: ZoneId): Boolean =
    event.start.toLocalDate() == LocalDate.now(zone)
