package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.data.music.MusicListRow
import tech.shaffersoftworks.remotescreens.server.data.music.MusicQueueSnapshot
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState

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
)

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
