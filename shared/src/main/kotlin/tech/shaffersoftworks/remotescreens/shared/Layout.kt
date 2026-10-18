package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val SCHEMA_VERSION = 2
const val MAX_PAGES = 8
const val MAX_TILES_PER_SCREEN = 96
const val MAX_NODES_PER_PAGE = 400
const val MAX_NODE_DEPTH = 16

/** Everything the add-on stores. Written to `/data/layouts.json`. */
@Serializable
data class LayoutData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val screens: List<Screen> = emptyList(),
    val players: List<PlayerRecord> = emptyList(),
    val preferences: Preferences = Preferences(),
    /** Themes made in the editor; the built-in ones are not stored. */
    val themes: List<Theme> = emptyList(),
)

/**
 * A custom theme: colours laid over a built-in theme. Keys are the lowercase names of the server's roles (`card`,
 * `ink_soft`) and pastels (`teal`); values are `#RRGGBB` or `#AARRGGBB`. A key left out takes the [base]'s colour.
 */
@Serializable
data class Theme(
    val id: String,
    val name: String,
    val base: String = "rpi",
    val roles: Map<String, String> = emptyMap(),
    val pastels: Map<String, String> = emptyMap(),
) {
    fun validated(): Theme = copy(
        name = name.trim().take(MAX_NAME).ifEmpty { id },
        base = base.take(MAX_ID),
        roles = roles.filter { (key, value) -> key.length <= MAX_ID && HEX.matches(value) }
            .mapValues { it.value.uppercase() },
        pastels = pastels.filter { (key, value) -> key.length <= MAX_ID && HEX.matches(value) }
            .mapValues { it.value.uppercase() },
    )

    companion object {
        const val MAX_THEMES = 32
        const val MAX_ID = 40
        const val MAX_NAME = 60
        val HEX = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        val ID = Regex("^[a-z0-9][a-z0-9_-]{0,39}$")
    }
}

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
    val grid: GridSize = GridSize.DEFAULT,
    val pages: List<Page> = listOf(Page(id = "home", title = "Home")),
    val homePage: String = pages.firstOrNull()?.id ?: "home",
    val settings: ScreenSettings = ScreenSettings(),
    val topBar: TopBar = TopBar(),
    val navBar: NavBar = NavBar(),
    /** The size the editor draws the screen at until a tablet shows it; a tablet renders at its own size. */
    val designSize: DeviceSize? = null,
)

@Serializable
data class GridSize(val cols: Int, val rows: Int) {
    companion object {
        val DEFAULT = GridSize(3, 3)
    }
}

@Serializable
data class DeviceSize(val widthDp: Int, val heightDp: Int) {
    companion object {
        const val MIN = 200
        const val MAX = 4000
    }
}

@Serializable
data class Page(
    val id: String,
    val title: String,
    val tiles: List<Tile> = emptyList(),
    /** A hidden page has no button in the navigation bar and is opened from a navigation tile, with a Back key. */
    val hidden: Boolean = false,
    /** The icon of the page's button in the navigation bar, or null for the default. */
    val icon: String? = null,
    /** A free-form page: a tree of components laid out like Compose. When set, [tiles] are not drawn. */
    val root: Node? = null,
) {
    /** The grid's tiles, or the tiles a free-form page holds and the components it can be tapped through. */
    fun allTiles(): List<Tile> = root?.walk()?.mapNotNull { it.asTile() }?.toList() ?: tiles
}

/** The component types of a free-form page. Containers hold [Node.children]; the rest are leaves. */
object NodeTypes {
    val CONTAINERS = setOf("column", "row", "box", "flow", "tabs")
    val LEAVES = setOf(
        "text", "icon", "button", "spacer", "divider", "slider", "image", "tile",
        "clock", "badge", "avatar", "progress", "graph", "switch", "stepper", "choices",
        "gauge", "analog", "iconbutton", "keys", "swatches", "keypad",
        "dial", "countdown", "qr", "markdown",
        "timerkeys", "todolist", "events", "mediabar", "forecast", "presence", "led",
        "thermostat",
    )
    val ALL = CONTAINERS + LEAVES
}

/**
 * One component of a free-form page. Sizes are `wrap`, `fill`, `dp:N` or `weight:N`, as in Compose.
 * Props by type: `text` (text, fontSize, weight, align, color, lines), `icon` (icon, iconOn, size, color),
 * `button` (text, icon, color, ink), containers (arrangement, alignment, gap), `slider` (steps), `image` (fit),
 * `clock` (show: time, date or both; fontSize, color, align), `badge` (text, icon, color), `avatar` (text, size),
 * `progress` (min, max, color), `graph` (hours, color), `switch` (color), `stepper` (step), `choices` (max),
 * `gauge` (min, max, color, size, caption: words or `@name`), `analog` (size, seconds, numbers: all or quarters),
 * `iconbutton` (icon, iconOn, size, color), `keys` (size), `swatches` (show: colour or white), `keypad` (size),
 * `dial` (size, step), `countdown` (text), `qr` (text, size), `markdown` (text), `tabs` (labels: comma-separated),
 * `timerkeys` (timer Start/Pause/Cancel), `todolist` (max), `events` (calendars, max), `mediabar` (media strip),
 * `forecast` (days), `presence` (people), `led` (size, colour while on),
 * `thermostat` (the RPI climate page: dial, readings, Heat/Cool modes and Fan mode).
 * Columns and rows take `scroll`; a `flow` wraps its children onto new lines. Text takes `marquee`. A bound
 * component takes `showWhen` (on or off) and `animate` (fade, left, right, up, down or none) for how it comes and goes.
 * Any component takes `place`, its own alignment in its parent: start, center or end across a column, top, center or
 * bottom across a row, or a box position such as `top-end`.
 */
@Serializable
data class Node(
    val id: String,
    val type: String,
    val children: List<Node> = emptyList(),
    val props: Map<String, String> = emptyMap(),
    val size: NodeSize = NodeSize(),
    val style: NodeStyle = NodeStyle(),
    val bind: NodeBinding? = null,
    val tap: TapAction? = null,
    val hold: TapAction? = null,
    /** The card a `tile` component shows. */
    val tile: Tile? = null,
    /** Kept in the layout but not drawn. */
    val hidden: Boolean = false,
) {
    fun walk(): Sequence<Node> = sequence {
        yield(this@Node)
        for (child in children) yieldAll(child.walk())
    }

    fun depth(): Int = 1 + (children.maxOfOrNull { it.depth() } ?: 0)

    /**
     * What a tap on this component is about, as a tile, so taps go through the same router as the grid's:
     * the card of a `tile` component, or a stand-in for a component bound to an entity or given an action.
     * A graph stands in as a history tile, so its history is fetched; a switch toggles unless told otherwise.
     */
    fun asTile(): Tile? = when {
        type == "tile" -> tile
        bind?.entityId != null || tap != null || hold != null -> Tile(
            id = id,
            entityId = bind?.entityId,
            kind = if (type == "graph") "history" else null,
            tap = tap ?: if (type == "switch") TapAction(TapType.TOGGLE) else TapAction(),
            hold = hold ?: TapAction(TapType.NONE),
            options = if (type == "graph") {
                mapOf(
                    "hours" to (props["hours"] ?: "24"),
                )
            } else {
                emptyMap()
            },
        )
        else -> null
    }
}

@Serializable
data class NodeSize(val width: String = "wrap", val height: String = "wrap")

@Serializable
data class NodeStyle(
    val padding: Float = 0f,
    /** A theme role (`card`, `key`, `accent`) or a pastel key, or null for none. */
    val background: String? = null,
    val corner: Float = 0f,
    /** An outline this many dp wide, 0 for none. */
    val border: Float = 0f,
    /** The outline's colour, as [background]; the divider grey when null. */
    val borderColor: String? = null,
    /** One side's padding in dp, or null for [padding]. */
    val paddingStart: Float? = null,
    val paddingTop: Float? = null,
    val paddingEnd: Float? = null,
    val paddingBottom: Float? = null,
) {
    val start: Float get() = paddingStart ?: padding
    val top: Float get() = paddingTop ?: padding
    val end: Float get() = paddingEnd ?: padding
    val bottom: Float get() = paddingBottom ?: padding

    /** The padding across, start plus end, and down, top plus bottom. */
    val padX: Float get() = start + end
    val padY: Float get() = top + bottom
    val padded: Boolean get() = start > 0f || top > 0f || end > 0f || bottom > 0f
}

/** What a component shows of an entity: its `state`, `value`, `name`, or the attribute [attribute]. */
@Serializable
data class NodeBinding(
    val entityId: String,
    val format: String = "state",
    val attribute: String? = null,
    val prefix: String = "",
    val suffix: String = "",
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
    /** A pastel or role key for the card's edge, or null for the theme's `card_edge`. */
    val borderColor: String? = null,
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
    @SerialName("url") URL,
    @SerialName("event") EVENT,
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
    /** Absolute URL for [TapType.URL]. */
    val url: String? = null,
    /** Home Assistant event type for [TapType.EVENT]. */
    val event: String? = null,
)

@Serializable
data class ScreenSettings(
    val brightness: Int = 80,
    val darkMode: Boolean = false,
    val standbyMinutes: Int = 10,
    val standbyBrightness: Int = 0,
    /** Standby turns the screen off instead of dimming it, once the tablet allows the app to lock it. */
    val standbyScreenOff: Boolean = false,
    /** Wake from standby when the tablet's proximity sensor reports something near (a few cm on many devices). */
    val wakeOnProximity: Boolean = false,
    /** Wake from standby when the front camera sees a face (ignored without a usable front camera). */
    val wakeOnFace: Boolean = false,
    /** "HH:MM", both or neither. */
    val nightStart: String? = null,
    val nightEnd: String? = null,
    val nightBrightness: Int = 5,
    val nightDark: Boolean = true,
    /** Seconds before the screen returns to the home page; 0 is never. */
    val backToHomeSeconds: Int = 0,
    val swipe: Boolean = false,
    /** 0 follows the device, 1 locks portrait, 2 locks landscape. */
    val orientation: Int = 0,
    /** The theme the screen wears, built-in or custom. */
    val theme: String = "rpi",
    /** The theme it wears while dark (see [darkMode] and [nightDark]). */
    val darkTheme: String = "rpi",
    /** Keeps the player in front: lock task when it is the device owner, screen pinning otherwise. */
    val kiosk: Boolean = false,
    /** The digits that unlock the kiosk on the tablet; null leaves unlocking to Home Assistant. */
    val kioskPin: String? = null,
) {
    fun validated(): ScreenSettings = copy(
        kioskPin = kioskPin?.takeIf { KIOSK_PIN.matches(it) },
        theme = theme.takeIf { Theme.ID.matches(it) } ?: "rpi",
        darkTheme = darkTheme.takeIf { Theme.ID.matches(it) } ?: "rpi",
        brightness = brightness.coerceIn(1, MAX_PERCENT),
        standbyMinutes = standbyMinutes.coerceIn(0, MAX_STANDBY_MINUTES),
        standbyBrightness = standbyBrightness.coerceIn(0, MAX_PERCENT),
        nightBrightness = nightBrightness.coerceIn(0, MAX_PERCENT),
        backToHomeSeconds = backToHomeSeconds.coerceIn(0, MAX_BACK_TO_HOME_SECONDS),
        nightStart = nightStart?.takeIf { HHMM.matches(it) },
        nightEnd = nightEnd?.takeIf { HHMM.matches(it) },
        orientation = orientation.takeIf { it in setOf(0, 1, 2) } ?: 0,
    )

    companion object {
        const val MAX_PERCENT = 100
        const val MAX_STANDBY_MINUTES = 240
        const val MAX_BACK_TO_HOME_SECONDS = 3600
        private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val KIOSK_PIN = Regex("^\\d{4,8}$")
    }
}

@Serializable
data class TopBar(
    val show: Boolean = true,
    val clock: Boolean = true,
    /** The date beside the time; never on a narrow device. */
    val date: Boolean = true,
    val temperatureEntity: String? = null,
    val people: Boolean = true,
    /** The tablet's Wi‑Fi signal level in the header. */
    val wifi: Boolean = false,
    /** The tablet's battery level in the header. */
    val battery: Boolean = false,
    /** dp, or null for the default. Text and icons scale with it. */
    val height: Int? = null,
    /** The dark clock block's share of the screen width in percent, or null for the default. */
    val clockWidth: Int? = null,
    /** A theme role or pastel key, as [NodeStyle.background]; null is the theme's header colour. */
    val background: String? = null,
    val clockBackground: String? = null,
) {
    fun validated(): TopBar = copy(
        height = height?.coerceIn(MIN_HEIGHT, MAX_HEIGHT),
        clockWidth = clockWidth?.coerceIn(MIN_CLOCK_WIDTH, MAX_CLOCK_WIDTH),
        background = background?.takeIf { it.length <= Theme.MAX_ID },
        clockBackground = clockBackground?.takeIf { it.length <= Theme.MAX_ID },
    )

    companion object {
        const val MIN_HEIGHT = 32
        const val MAX_HEIGHT = 120
        const val MIN_CLOCK_WIDTH = 15
        const val MAX_CLOCK_WIDTH = 60
    }
}

/** The bottom navigation bar: one button per visible page, then Settings. */
@Serializable
data class NavBar(
    /** dp, or null for the default, which is smaller on a narrow device. Text and icons scale with it. */
    val height: Int? = null,
    /** `both`, `icons` or `text`. */
    val labels: String = "both",
    /** `spread` (square buttons spaced out), `center` (packed in the middle) or `fill` (stretched to the width). */
    val layout: String = "spread",
    /** Theme roles or pastel keys, as [NodeStyle.background]; null is the theme's own. */
    val background: String? = null,
    val line: String? = null,
    val active: String? = null,
) {
    fun validated(): NavBar = copy(
        height = height?.coerceIn(MIN_HEIGHT, MAX_HEIGHT),
        labels = labels.takeIf { it in LABELS } ?: "both",
        layout = layout.takeIf { it in LAYOUTS } ?: "spread",
        background = background?.takeIf { it.length <= Theme.MAX_ID },
        line = line?.takeIf { it.length <= Theme.MAX_ID },
        active = active?.takeIf { it.length <= Theme.MAX_ID },
    )

    companion object {
        const val MIN_HEIGHT = 48
        const val MAX_HEIGHT = 160
        val LABELS = setOf("both", "icons", "text")
        val LAYOUTS = setOf("spread", "center", "fill")
    }
}

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
