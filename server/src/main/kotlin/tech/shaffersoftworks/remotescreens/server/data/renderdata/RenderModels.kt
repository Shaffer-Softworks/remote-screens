package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmUiState
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsRoom
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsUiState
import tech.shaffersoftworks.remotescreens.server.data.music.MusicListRow
import tech.shaffersoftworks.remotescreens.server.data.music.MusicQueueSnapshot
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState
import java.time.Instant

/** A picture the add-on fetches and sends as a `bitmap` message. */
data class PictureSlot(
    val name: String,
    /** A path Home Assistant serves (`/api/camera_proxy/camera.x`), or `map:<tile>` for a drawn map. */
    val source: String,
    val widthPx: Int,
    val heightPx: Int,
    /** 0 sends it once, and again when [source] changes. */
    val refreshSeconds: Int,
    val crop: Boolean = true,
    val round: Boolean = false,
)

/** Data a renderer draws into the document, fetched before it starts. */
data class RenderData(
    val forecasts: Map<String, List<Forecast>> = emptyMap(),
    val history: Map<String, List<Pair<Long, Double>>> = emptyMap(),
    val energy: EnergyPrefs? = null,
    val browse: Map<String, List<BrowseItem>> = emptyMap(),
    val todos: Map<String, List<TodoItem>> = emptyMap(),
    /** Music Assistant library rows keyed by music tile id. */
    val musicBrowse: Map<String, List<MusicListRow>> = emptyMap(),
    /** Music Assistant queues keyed by music tile id. */
    val musicQueue: Map<String, MusicQueueSnapshot> = emptyMap(),
    /** Resolved MA config entry ids keyed by music tile id. */
    val musicConfigEntry: Map<String, String> = emptyMap(),
    /** Per-tile browse/listen UI for music pages. */
    val musicUi: Map<String, MusicUiState> = emptyMap(),
    /** Whether browse has another page, keyed by music tile id. */
    val musicBrowseHasMore: Map<String, Boolean> = emptyMap(),
    /** The 14-day agenda for a full calendar page, or null when the screen has none. */
    val agenda: AgendaSnapshot? = null,
    /** Transient lines under the agenda title, keyed by tile id. */
    val agendaNotices: Map<String, String> = emptyMap(),
    /** Chosen zone and open dialog of each full alarm page, keyed by tile id. */
    val alarmUi: Map<String, AlarmUiState> = emptyMap(),
    /** Home Assistant's areas with their lights, switches and covers, when the screen has a lights page. */
    val lightsRooms: List<LightsRoom> = emptyList(),
    /** Chosen room and open light dialog of each lights page, keyed by tile id. */
    val lightsUi: Map<String, LightsUiState> = emptyMap(),
)

/** One calendar the agenda can read, and whether Home Assistant allows creating or deleting on it. */
data class AgendaCalendar(
    val entityId: String,
    val name: String,
    val canCreate: Boolean,
    val canDelete: Boolean,
)

/** One event in the 14-day agenda. Times are instants; the page formats them. */
data class AgendaEvent(
    val uid: String?,
    val recurrenceId: String?,
    val entityId: String,
    val calendarName: String,
    val summary: String,
    val location: String?,
    val start: Instant?,
    val end: Instant?,
    val isAllDay: Boolean,
    val canDelete: Boolean,
)

/** The agenda a full calendar page draws. [errorMessage] is set when the list could not be loaded. */
data class AgendaSnapshot(
    val events: List<AgendaEvent> = emptyList(),
    val calendars: List<AgendaCalendar> = emptyList(),
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

/** Joins the entity, uid, and recurrence id in a hold action. The separator does not appear in those fields. */
const val AGENDA_FIELD_SEPARATOR = "\u001f"

/** One incomplete to-do item, as `todo.get_items` returns it. */
data class TodoItem(
    val uid: String,
    val summary: String,
)

data class Forecast(
    val time: java.time.ZonedDateTime,
    val condition: String,
    val high: Double?,
    val low: Double?,
    val rain: Int?,
)

data class EnergyPrefs(
    val solar: String?,
    val grid: String?,
    val battery: String?,
    val devices: List<Pair<String, String>>,
)

data class BrowseItem(
    val title: String,
    val contentId: String,
    val contentType: String,
    val canPlay: Boolean,
    val canExpand: Boolean,
    val thumbnail: String?,
)
