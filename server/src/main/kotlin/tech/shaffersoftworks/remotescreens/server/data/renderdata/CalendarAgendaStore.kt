package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Copy from RPI `strings.xml` and the calendar view model. */
object AgendaCopy {
    const val TITLE = "Upcoming events"
    const val LOADING = "Loading calendar…"
    const val EMPTY = "No events in the next two weeks"
    const val ERROR = "Could not load calendar"
    const val ALL_DAY = "All day"
    const val HOLD_SUFFIX = " · Hold to delete"
    const val META_SEPARATOR = " · "
    const val CREATED = "Event created"
    const val DELETED = "Event deleted"
    const val CANNOT_DELETE = "This event cannot be deleted"
    const val TITLE_REQUIRED = "Title is required"
    const val SELECT_CALENDAR = "Select a calendar"
    const val UNTITLED = "Untitled event"
}

private const val AGENDA_TTL_MS = 30_000L
private const val LOOKAHEAD_DAYS = 14L
private const val FEATURE_CREATE = 1
private const val FEATURE_DELETE = 2
private const val CALENDARS_PATH = "/api/calendars"

private val CALENDAR_JSON = Json { ignoreUnknownKeys = true }

/**
 * The 14-day agenda for a full calendar page, plus the short notice under its title.
 * Home Assistant's calendar entity only carries the next event, so the list comes from the REST API.
 */
class CalendarAgendaStore(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(CalendarAgendaStore::class.java)
    private val notices = ConcurrentHashMap<String, String>()
    private val cache = AtomicReference<Cached?>(null)

    @Volatile
    var latest: AgendaSnapshot = AgendaSnapshot()
        private set

    fun notice(tileId: String): String? = notices[tileId]

    fun notices(): Map<String, String> = notices.toMap()

    fun setNotice(tileId: String, message: String?) {
        if (message.isNullOrBlank()) notices.remove(tileId) else notices[tileId] = message
    }

    fun invalidate() {
        cache.set(null)
    }

    fun clear() {
        cache.set(null)
        notices.clear()
        latest = AgendaSnapshot()
    }

    suspend fun snapshot(states: Map<String, EntityState>, zone: ZoneId): AgendaSnapshot {
        cache.get()?.takeIf { it.isFresh() }?.let { return it.value }
        val loaded = runCatching { load(states, zone) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                logger.debug("Calendar agenda failed: {}", error.message)
            }
            .getOrElse { AgendaSnapshot(errorMessage = it.message ?: AgendaCopy.ERROR) }
        cache.set(Cached(loaded, System.currentTimeMillis()))
        latest = loaded
        return loaded
    }

    private suspend fun load(states: Map<String, EntityState>, zone: ZoneId): AgendaSnapshot {
        val listBytes = homeAssistant.fetch(CALENDARS_PATH)
        val listed = listBytes?.let(::parseCalendarList).orEmpty()
        return when {
            listBytes == null -> AgendaSnapshot(errorMessage = AgendaCopy.ERROR)
            listed.isEmpty() -> AgendaSnapshot()
            else -> eventsFor(listed, states, zone)
        }
    }

    private suspend fun eventsFor(
        listed: List<CalendarListItem>,
        states: Map<String, EntityState>,
        zone: ZoneId,
    ): AgendaSnapshot {
        val calendars = listed.map { item ->
            val entity = states[item.entityId]
            val name = item.name?.takeIf { it.isNotBlank() } ?: entity?.name ?: item.entityId
            AgendaCalendar(
                entityId = item.entityId,
                name = name,
                canCreate = entity?.supports(FEATURE_CREATE) == true,
                canDelete = entity?.supports(FEATURE_DELETE) == true,
            )
        }
        val now = Instant.now()
        val rangeEnd = now.plus(LOOKAHEAD_DAYS, ChronoUnit.DAYS)
        val events = mutableListOf<AgendaEvent>()
        var firstError: String? = null
        for (calendar in calendars) {
            val fetched = fetchEvents(calendar, zone, now, rangeEnd)
            if (fetched == null) {
                if (firstError == null) firstError = AgendaCopy.ERROR
            } else {
                events += fetched
            }
        }
        val sorted = events.sortedWith(compareBy({ it.start ?: Instant.MAX }, { it.summary }))
        val upcoming = sorted.filter { event -> event.end == null || !event.end.isBefore(now) }
        return if (events.isEmpty() && firstError != null) {
            AgendaSnapshot(errorMessage = firstError)
        } else {
            AgendaSnapshot(events = upcoming, calendars = calendars.sortedBy { it.name })
        }
    }

    private suspend fun fetchEvents(
        calendar: AgendaCalendar,
        zone: ZoneId,
        start: Instant,
        end: Instant,
    ): List<AgendaEvent>? {
        val startText = URLEncoder.encode(start.toString(), StandardCharsets.UTF_8)
        val endText = URLEncoder.encode(end.toString(), StandardCharsets.UTF_8)
        val entityPath = URLEncoder.encode(calendar.entityId, StandardCharsets.UTF_8)
        val bytes = homeAssistant.fetch("$CALENDARS_PATH/$entityPath?start=$startText&end=$endText") ?: return null
        return parseEvents(bytes, calendar, zone)
    }

    private data class Cached(val value: AgendaSnapshot, val fetchedAtMs: Long) {
        fun isFresh(): Boolean = System.currentTimeMillis() - fetchedAtMs < AGENDA_TTL_MS
    }
}

@Serializable
private data class CalendarListItem(
    @SerialName("entity_id") val entityId: String,
    val name: String? = null,
)

@Serializable
private data class CalendarEventBody(
    val summary: String? = null,
    val location: String? = null,
    val uid: String? = null,
    @SerialName("recurrence_id") val recurrenceId: String? = null,
    val start: CalendarWhen? = null,
    val end: CalendarWhen? = null,
)

@Serializable
private data class CalendarWhen(
    val date: String? = null,
    val dateTime: String? = null,
)

private fun parseCalendarList(bytes: ByteArray): List<CalendarListItem> =
    runCatching {
        CALENDAR_JSON.decodeFromString<List<CalendarListItem>>(bytes.toString(StandardCharsets.UTF_8))
    }.getOrDefault(emptyList())

private fun parseEvents(bytes: ByteArray, calendar: AgendaCalendar, zone: ZoneId): List<AgendaEvent> {
    val bodies = runCatching {
        CALENDAR_JSON.decodeFromString<List<CalendarEventBody>>(bytes.toString(StandardCharsets.UTF_8))
    }.getOrDefault(emptyList())
    return bodies.map { body ->
        val isAllDay = body.start?.dateTime.isNullOrBlank() && !body.start?.date.isNullOrBlank()
        AgendaEvent(
            uid = body.uid,
            recurrenceId = body.recurrenceId,
            entityId = calendar.entityId,
            calendarName = calendar.name,
            summary = body.summary?.takeIf { it.isNotBlank() } ?: AgendaCopy.UNTITLED,
            location = body.location?.takeIf { it.isNotBlank() },
            start = resolveInstant(body.start, zone),
            end = resolveInstant(body.end, zone),
            isAllDay = isAllDay,
            canDelete = calendar.canDelete && !body.uid.isNullOrBlank(),
        )
    }
}

private fun resolveInstant(whenValue: CalendarWhen?, zone: ZoneId): Instant? {
    val dateTime = whenValue?.dateTime
    val date = whenValue?.date
    return when {
        !dateTime.isNullOrBlank() -> parseDateTime(dateTime, zone)
        date.isNullOrBlank() -> null
        else -> runCatching { LocalDate.parse(date).atStartOfDay(zone).toInstant() }.getOrNull()
    }
}

private fun parseDateTime(text: String, zone: ZoneId): Instant? =
    runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(text).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull()

/** The entity id of `/api/calendars/{id}`, or null when [path] is the calendar list. */
internal fun calendarEntityId(path: String): String? {
    val bare = path.substringBefore('?')
    val isList = bare == CALENDARS_PATH || bare == "$CALENDARS_PATH/"
    val id = bare.removePrefix("$CALENDARS_PATH/")
    return if (isList || !bare.startsWith("$CALENDARS_PATH/")) {
        null
    } else {
        URLDecoder.decode(id, StandardCharsets.UTF_8).ifBlank { null }
    }
}

internal fun isCalendarListPath(path: String): Boolean {
    val bare = path.substringBefore('?')
    return bare == CALENDARS_PATH || bare == "$CALENDARS_PATH/"
}
