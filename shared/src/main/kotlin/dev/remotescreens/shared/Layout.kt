package dev.remotescreens.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val SCHEMA_VERSION = 1
const val MAX_PAGES = 8
const val MAX_TILES_PER_SCREEN = 96

/** Everything the add-on stores. Written to `/data/layouts.json`. */
@Serializable
data class LayoutData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val screens: List<Screen> = emptyList(),
    val players: List<PlayerRecord> = emptyList(),
    val preferences: Preferences = Preferences(),
)

@Serializable
data class Preferences(
    /** BCP 47 tag, or null to follow Home Assistant. */
    val language: String? = null,
    val clock24h: Boolean? = null,
)

/** A screen is a layout. One or more players (tablets) show it. */
@Serializable
data class Screen(
    val id: String,
    val name: String,
    val grid: GridSize = GridSize(3, 3),
    val pages: List<Page> = listOf(Page(id = "home", title = "Home")),
    val homePage: String = pages.firstOrNull()?.id ?: "home",
    val settings: ScreenSettings = ScreenSettings(),
    val topBar: TopBar = TopBar(),
)

@Serializable
data class GridSize(val cols: Int, val rows: Int)

@Serializable
data class Page(
    val id: String,
    val title: String,
    val tiles: List<Tile> = emptyList(),
    /** A hidden page has no page dot and is opened from a navigation tile, with a Back key. */
    val hidden: Boolean = false,
)

@Serializable
data class Tile(
    val id: String,
    /** The Home Assistant entity, or null for tiles that need none (clock, navigation). */
    val entityId: String? = null,
    /** Overrides the kind that follows from the entity's domain: `clock`, `nav`, `bedside`, `map`, `energy`. */
    val kind: String? = null,
    val col: Int = 0,
    val row: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
    /** Takes the whole page, whatever the grid. */
    val full: Boolean = false,
    val name: String? = null,
    val icon: String? = null,
    /** A key of the pastel palette (see the theme), or null for the default of the domain. */
    val color: String? = null,
    val tap: TapAction = TapAction(),
    val hold: TapAction = TapAction(type = TapType.CARD),
    val options: Map<String, String> = emptyMap(),
) {
    val domain: String? get() = entityId?.substringBefore('.', "")?.ifEmpty { null }
    fun effectiveKind(): String = kind ?: domain ?: "empty"
}

@Serializable
enum class TapType {
    @SerialName("default") DEFAULT,
    @SerialName("toggle") TOGGLE,
    @SerialName("card") CARD,
    @SerialName("action") ACTION,
    @SerialName("page") PAGE,
    @SerialName("none") NONE,
}

@Serializable
data class TapAction(
    val type: TapType = TapType.DEFAULT,
    /** `domain.service` for [TapType.ACTION]. */
    val service: String? = null,
    val data: JsonObject? = null,
    /** Page id for [TapType.PAGE]. */
    val page: String? = null,
)

@Serializable
data class ScreenSettings(
    val brightness: Int = 80,
    val darkMode: Boolean = false,
    val standbyMinutes: Int = 10,
    val standbyBrightness: Int = 0,
    /** "HH:MM", both or neither. */
    val nightStart: String? = null,
    val nightEnd: String? = null,
    val nightBrightness: Int = 5,
    val nightDark: Boolean = true,
    /** Seconds before the screen returns to the home page; 0 is never. */
    val backToHomeSeconds: Int = 0,
    val swipe: Boolean = true,
    /** 0 follows the device, else 90/180/270 or `portrait`/`landscape` locked via 1/2. */
    val orientation: Int = 0,
) {
    fun validated(): ScreenSettings = copy(
        brightness = brightness.coerceIn(1, 100),
        standbyMinutes = standbyMinutes.coerceIn(0, 240),
        standbyBrightness = standbyBrightness.coerceIn(0, 100),
        nightBrightness = nightBrightness.coerceIn(0, 100),
        backToHomeSeconds = backToHomeSeconds.coerceIn(0, 3600),
        nightStart = nightStart?.takeIf { HHMM.matches(it) },
        nightEnd = nightEnd?.takeIf { HHMM.matches(it) },
        orientation = orientation.takeIf { it in setOf(0, 1, 2) } ?: 0,
    )

    companion object {
        private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    }
}

@Serializable
data class TopBar(
    val show: Boolean = true,
    val clock: Boolean = true,
    val temperatureEntity: String? = null,
    val people: Boolean = true,
)

/** A tablet that has said hello. Adopted players have a screen and a token. */
@Serializable
data class PlayerRecord(
    val deviceId: String,
    val name: String,
    val screenId: String? = null,
    /** SHA-256 of the token the player holds; the token itself is never stored. */
    val tokenHash: String? = null,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val appVersion: Int = 0,
    val lastSeen: Long = 0,
)
