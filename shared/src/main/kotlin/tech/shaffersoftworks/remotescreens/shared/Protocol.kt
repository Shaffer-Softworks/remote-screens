package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The messages between the add-on and a player over the `/player` WebSocket, one JSON object per text frame.
 *
 * Version 1. A new message or field is negotiated through [Hello.capabilities] and [Welcome.features]; an
 * existing one never changes meaning. Unknown messages are ignored on both sides.
 */
object Protocol {
    const val VERSION = 1
    const val PORT = 8100
    const val INGRESS_PORT = 8099
    const val MDNS_TYPE = "_remotescreens._tcp.local."

    /** A player that can play [ServerMessage.Video]. Only it is sent one, so no camera streams for one that can't. */
    const val CAPABILITY_VIDEO = "video"

    /** A player that can show an interactive [ServerMessage.LiveMap] over a map card. Others get a still bitmap. */
    const val CAPABILITY_MAP = "map"

    /** A player that can show a native Music Assistant search overlay ([ServerMessage.LiveSearch]). */
    const val CAPABILITY_MUSIC_SEARCH = "music_search"

    /** A player that can show the native calendar create and delete dialogs ([ServerMessage.CalendarDialog]). */
    const val CAPABILITY_CALENDAR_DIALOGS = "calendar_dialogs"

    /** A player that reports its sensors and diagnostics ([PlayerMessage.Telemetry]) once the add-on accepts them. */
    const val CAPABILITY_TELEMETRY = "telemetry"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
        explicitNulls = false
    }

    fun encode(message: ServerMessage): String = json.encodeToString(
        ServerMessage.serializer(),
        message,
    )
    fun encode(message: PlayerMessage): String = json.encodeToString(
        PlayerMessage.serializer(),
        message,
    )
    fun decodeServer(text: String): ServerMessage? =
        runCatching { json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull()

    fun decodePlayer(text: String): PlayerMessage? =
        runCatching { json.decodeFromString(PlayerMessage.serializer(), text) }.getOrNull()
}

/** Which document a message is about. The main document holds the pages; the overlay holds a card or an alert. */
@Serializable
enum class Slot {
    @SerialName("main") MAIN,
    @SerialName("overlay") OVERLAY,
    @SerialName("alert") ALERT,
}

@Serializable
enum class VarType {
    @SerialName("s") STRING,
    @SerialName("i") INT,
    @SerialName("f") FLOAT,
    @SerialName("c") COLOR,
}

/** A named remote variable. The document declares it as `USER:<name>`; the player sets it by [name]. */
@Serializable
data class VarValue(val name: String, val type: VarType, val value: String) {
    companion object {
        private const val HEX_RADIX = 16
        private const val ARGB_HEX_DIGITS = 8

        fun string(name: String, value: String) = VarValue(name, VarType.STRING, value)
        fun int(name: String, value: Int) = VarValue(name, VarType.INT, value.toString())
        fun float(name: String, value: Float) = VarValue(name, VarType.FLOAT, value.toString())
        fun color(name: String, argb: Int) =
            VarValue(
                name,
                VarType.COLOR,
                argb.toUInt().toString(HEX_RADIX).padStart(ARGB_HEX_DIGITS, '0'),
            )
    }

    fun asInt(): Int = value.toIntOrNull() ?: 0
    fun asFloat(): Float = value.toFloatOrNull() ?: 0f
    fun asColor(): Int = value.toUIntOrNull(HEX_RADIX)?.toInt() ?: 0
}

/**
 * What a tap asks for, carried as the metadata of a host action: `tile|verb|arg`.
 * Tile `_` is the page itself: `_|page|2`, `_|back|`, `_|close|`, `_|settings|`.
 */
data class ActionRef(val tile: String, val verb: String, val arg: String = "") {
    fun encode(): String = "$tile|$verb|$arg"

    /** Handled on the player without asking the add-on. */
    val local: Boolean get() = tile == "_" && verb in LOCAL_VERBS

    companion object {
        val LOCAL_VERBS = setOf("page", "next", "prev", "back", "close", "noop", "settings", "set", "url")

        fun decode(encoded: String?): ActionRef? {
            val parts = encoded?.split('|', limit = 3) ?: return null
            val isValid = parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()
            return if (isValid) ActionRef(parts[0], parts[1], parts.getOrElse(2) { "" }) else null
        }
    }
}

@Serializable
sealed interface PlayerMessage {
    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocol: Int = Protocol.VERSION,
        val deviceId: String,
        val deviceName: String,
        val token: String? = null,
        val widthDp: Int,
        val heightDp: Int,
        val density: Float,
        val appVersion: Int,
        val capabilities: List<String> = emptyList(),
        val darkSystem: Boolean = false,
    ) : PlayerMessage

    /**
     * A tap, from [ActionRef.encode].
     * [page] is the page the tablet is on when it taps, so a rebuild can bake that page in (older players omit it).
     */
    @Serializable
    @SerialName("action")
    data class Action(
        val slot: Slot,
        val rev: Int,
        val metadata: String,
        val page: Int? = null,
    ) : PlayerMessage

    @Serializable
    @SerialName("page")
    data class PageShown(val index: Int) : PlayerMessage

    /** A setting changed on the device itself, such as brightness from the settings sheet. */
    @Serializable
    @SerialName("settings")
    data class SettingsChanged(val settings: ScreenSettings) : PlayerMessage

    @Serializable
    @SerialName("state")
    data class DeviceState(
        val awake: Boolean,
        val page: Int,
        val battery: Int? = null,
        val charging: Boolean? = null,
        /** Wi‑Fi signal level 0–4, or null when not connected / unknown. */
        val wifi: Int? = null,
        /** `off`, `pinned` (screen pinning) or `locked` (lock task as the device owner); null from older players. */
        val kiosk: String? = null,
    ) : PlayerMessage

    /**
     * The tablet's sensors and diagnostics, for Home Assistant. Sent only to an add-on that lists
     * [Protocol.CAPABILITY_TELEMETRY] in its features, while [ScreenSettings.reportTelemetry] is on.
     */
    @Serializable
    @SerialName("telemetry")
    data class Telemetry(val telemetry: DeviceTelemetry) : PlayerMessage

    /**
     * The video over [componentId] of document [rev] in [slot] stopped, so the add-on can start the camera's stream
     * again. [httpCode] is the add-on's answer when it was an error, such as 404 once Home Assistant ended the stream.
     */
    @Serializable
    @SerialName("video_failed")
    data class VideoFailed(
        val slot: Slot,
        val rev: Int,
        val componentId: Int,
        val httpCode: Int? = null,
    ) : PlayerMessage

    /**
     * A search query typed in the Music Assistant overlay for [tileId]. The add-on answers with
     * [ServerMessage.SearchResults].
     */
    @Serializable
    @SerialName("search_query")
    data class SearchQuery(val tileId: String, val query: String) : PlayerMessage

    /** Create an event from the native calendar dialog. Dates are `yyyy-MM-dd`; times are `HH:mm` when not all-day. */
    @Serializable
    @SerialName("calendar_create")
    data class CalendarCreate(
        val tileId: String,
        val entityId: String,
        val summary: String,
        val location: String? = null,
        val allDay: Boolean = false,
        val startDate: String,
        val startTime: String? = null,
        val endDate: String,
        val endTime: String? = null,
    ) : PlayerMessage

    /** Delete the event the native calendar dialog confirmed. */
    @Serializable
    @SerialName("calendar_delete")
    data class CalendarDelete(
        val tileId: String,
        val entityId: String,
        val uid: String,
        val recurrenceId: String? = null,
    ) : PlayerMessage

    @Serializable
    @SerialName("ping")
    data object Ping : PlayerMessage
}

@Serializable
sealed interface ServerMessage {
    /** Answer to [PlayerMessage.Hello]. An unadopted player gets a [pairingCode] to show and nothing else. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val protocol: Int = Protocol.VERSION,
        val screenId: String? = null,
        val screenName: String? = null,
        val token: String? = null,
        val pairingCode: String? = null,
        val features: List<String> = emptyList(),
        val serverVersion: String = "",
        /** Home Assistant's time zone id, so analog clocks match the digital time the add-on sends. */
        val timeZone: String? = null,
    ) : ServerMessage

    /** A whole Remote Compose document, base64. [predictions] maps an action's metadata to what it will change. */
    @Serializable
    @SerialName("document")
    data class Document(
        val slot: Slot,
        val rev: Int,
        val data: String,
        val vars: List<VarValue> = emptyList(),
        val predictions: Map<String, List<VarValue>> = emptyMap(),
        val homePage: Int = 0,
        val pageCount: Int = 1,
        /** The pages a swipe moves through, in order; hidden pages are left out. Empty is every page. */
        val swipePages: List<Int> = emptyList(),
        /** Seconds before an alert closes by itself; 0 keeps it. */
        val timeout: Int = 0,
    ) : ServerMessage

    @Serializable
    @SerialName("vars")
    data class Vars(val vars: List<VarValue>, val predictions: Map<String, List<VarValue>> = emptyMap()) : ServerMessage

    /** A picture for a named bitmap of a document, PNG or JPEG, base64. */
    @Serializable
    @SerialName("bitmap")
    data class Bitmap(val name: String, val data: String) : ServerMessage

    /**
     * A camera's live video, played over the component with id [componentId] in document [rev] of [slot], where a
     * spinner or still shows until it plays. [path] is on the add-on's player port and wants the player's token.
     * [crop] fills the box and cuts the edges off; else the whole picture fits inside it.
     * Only for players with [Protocol.CAPABILITY_VIDEO]; it ends when the document goes.
     */
    @Serializable
    @SerialName("video")
    data class Video(
        val slot: Slot,
        val rev: Int,
        val componentId: Int,
        val path: String,
        val crop: Boolean,
        /** Page of the main document the video belongs to; it stops on the others. Older servers omit it (-1). */
        val page: Int = -1,
    ) : ServerMessage

    /**
     * An interactive OpenStreetMap over the component with id [componentId] in document [rev] of [slot].
     * [tileUrl] has `{z}`, `{x}` and `{y}` (the add-on's `map_tiles`). [markers] are people (or trackers) to pin.
     * Only for players with [Protocol.CAPABILITY_MAP]; it ends when the document goes.
     * Named [LiveMap] (not Map) so it does not clash with `kotlin.collections.Map` in this file.
     */
    @Serializable
    @SerialName("map")
    data class LiveMap(
        val slot: Slot,
        val rev: Int,
        val componentId: Int,
        val tileUrl: String,
        val dark: Boolean,
        val markers: List<MapMarker> = emptyList(),
    ) : ServerMessage

    /**
     * A native Music Assistant search field over the component with [componentId] in document [rev] of [slot].
     * Only for players with [Protocol.CAPABILITY_MUSIC_SEARCH]; it ends when the document goes.
     */
    @Serializable
    @SerialName("live_search")
    data class LiveSearch(
        val slot: Slot,
        val rev: Int,
        val componentId: Int,
        val tileId: String,
        val playerEntityId: String,
        /** Document pixels of the search slot. The player places the field here when the component has no size. */
        val x: Int = 0,
        val y: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
        /** Page the field belongs to. Older servers omit it; the player then keeps the field up. */
        val page: Int = -1,
    ) : ServerMessage

    /** Rows for an open [LiveSearch] after [PlayerMessage.SearchQuery]. */
    @Serializable
    @SerialName("search_results")
    data class SearchResults(
        val tileId: String,
        val rows: List<MusicSearchRow> = emptyList(),
    ) : ServerMessage

    @Serializable
    @SerialName("settings")
    data class Settings(val settings: ScreenSettings) : ServerMessage

    @Serializable
    @SerialName("open_page")
    data class OpenPage(val index: Int) : ServerMessage

    @Serializable
    @SerialName("close")
    data class Close(val slot: Slot) : ServerMessage

    @Serializable
    @SerialName("identify")
    data class Identify(val seconds: Int = 5) : ServerMessage

    @Serializable
    @SerialName("wake")
    data class Wake(val awake: Boolean) : ServerMessage

    @Serializable
    @SerialName("update")
    data class UpdateAvailable(val versionCode: Int, val versionName: String, val path: String) : ServerMessage

    /** A message to show for a moment, such as "Couldn't reach Home Assistant". */
    @Serializable
    @SerialName("toast")
    data class Toast(val text: String) : ServerMessage

    @Serializable
    @SerialName("pong")
    data object Pong : ServerMessage

    /**
     * Opens, updates, or closes the native calendar dialog.
     * [mode] is `create`, `delete`, `error` (leave it open), or `close`.
     */
    @Serializable
    @SerialName("calendar_dialog")
    data class CalendarDialog(
        val mode: String,
        val tileId: String,
        val calendars: List<CalendarChoice> = emptyList(),
        val event: CalendarDeleteTarget? = null,
        val error: String? = null,
    ) : ServerMessage
}

/** A calendar the create dialog can write to. */
@Serializable
data class CalendarChoice(
    val entityId: String,
    val name: String,
)

/** The event a delete dialog is confirming. */
@Serializable
data class CalendarDeleteTarget(
    val entityId: String,
    val uid: String,
    val recurrenceId: String? = null,
    val summary: String,
)

/** [ServerMessage.CalendarDialog.mode] values. */
object CalendarDialogMode {
    const val CREATE = "create"
    const val DELETE = "delete"
    const val ERROR = "error"
    const val CLOSE = "close"
}

/** A pin on [ServerMessage.LiveMap]. [picture] is a named bitmap the add-on already sent, or null for initials. */
@Serializable
data class MapMarker(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val picture: String? = null,
)

/** One hit in [ServerMessage.SearchResults]. */
@Serializable
data class MusicSearchRow(
    val title: String,
    val subtitle: String? = null,
    val uri: String,
    val imagePathOrUrl: String? = null,
)
