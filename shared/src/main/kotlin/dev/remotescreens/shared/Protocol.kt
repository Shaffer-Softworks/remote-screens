package dev.remotescreens.shared

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

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
        explicitNulls = false
    }

    fun encode(msg: ServerMessage): String = json.encodeToString(ServerMessage.serializer(), msg)
    fun encode(msg: PlayerMessage): String = json.encodeToString(PlayerMessage.serializer(), msg)
    fun decodeServer(text: String): ServerMessage? = runCatching { json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull()
    fun decodePlayer(text: String): PlayerMessage? = runCatching { json.decodeFromString(PlayerMessage.serializer(), text) }.getOrNull()
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
        fun string(name: String, v: String) = VarValue(name, VarType.STRING, v)
        fun int(name: String, v: Int) = VarValue(name, VarType.INT, v.toString())
        fun float(name: String, v: Float) = VarValue(name, VarType.FLOAT, v.toString())
        fun color(name: String, argb: Int) = VarValue(name, VarType.COLOR, argb.toUInt().toString(16).padStart(8, '0'))
    }

    fun asInt(): Int = value.toIntOrNull() ?: 0
    fun asFloat(): Float = value.toFloatOrNull() ?: 0f
    fun asColor(): Int = value.toUIntOrNull(16)?.toInt() ?: 0
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
        val LOCAL_VERBS = setOf("page", "next", "prev", "back", "close", "noop", "settings")

        fun decode(s: String?): ActionRef? {
            if (s == null) return null
            val parts = s.split('|', limit = 3)
            if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
            return ActionRef(parts[0], parts[1], parts.getOrElse(2) { "" })
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

    /** A tap, from [ActionRef.encode]. */
    @Serializable
    @SerialName("action")
    data class Action(val slot: Slot, val rev: Int, val metadata: String) : PlayerMessage

    @Serializable
    @SerialName("page")
    data class PageShown(val index: Int) : PlayerMessage

    /** A setting changed on the device itself, such as brightness from the settings sheet. */
    @Serializable
    @SerialName("settings")
    data class SettingsChanged(val settings: ScreenSettings) : PlayerMessage

    @Serializable
    @SerialName("state")
    data class DeviceState(val awake: Boolean, val page: Int, val battery: Int? = null, val charging: Boolean? = null) : PlayerMessage

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
}
