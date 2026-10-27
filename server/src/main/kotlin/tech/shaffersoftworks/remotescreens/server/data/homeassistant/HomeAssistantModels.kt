package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class HomeAssistantEvent(val type: String, val data: JsonObject)

data class HomeAssistantConfig(
    val language: String = "en",
    val country: String? = null,
    val timeZone: String = "UTC",
    val temperatureUnit: String = "°C",
    val locationName: String = "Home",
    val version: String = "",
)

/** A command Home Assistant answered with `success: false`. */
class HomeAssistantException(val code: String, message: String) : Exception(message)

/** One entity as Home Assistant reports it: its state and attributes. */
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
    val lastChanged: String = "",
) {
    val domain: String get() = entityId.substringBefore('.')

    val name: String
        get() = attr("friendly_name")
            ?: entityId.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

    val isAvailable: Boolean get() = state != "unavailable" && state != "unknown"

    /** A text, number or boolean attribute as text; null for a list, an object, or a missing attribute. */
    fun attr(key: String): String? = (attributes[key] as? JsonPrimitive)
        ?.takeIf { it.isString || it.doubleOrNull != null || it.booleanOrNull != null }
        ?.content

    fun number(key: String): Double? = (attributes[key] as? JsonPrimitive)?.doubleOrNull

    fun list(key: String): List<String> =
        runCatching { attributes.getValue(
            key,
        ).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())

    fun features(): Int = number("supported_features")?.toInt() ?: 0

    fun supports(featureBit: Int): Boolean = features() and featureBit != 0

    fun numericState(): Double? = state.toDoubleOrNull()

    /** Home Assistant's `rgb_color` as opaque ARGB, or null when the entity reports none. */
    fun readRgb(): Int? {
        val channels = list("rgb_color").mapNotNull { it.toDoubleOrNull()?.roundToInt() }
        if (channels.size < RGB_CHANNELS) return null
        val red = channels[0].coerceIn(0, CHANNEL_MAX)
        val green = channels[1].coerceIn(0, CHANNEL_MAX)
        val blue = channels[2].coerceIn(0, CHANNEL_MAX)
        return (OPAQUE_ALPHA shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    /** [readRgb] as `#RRGGBB`, for the editor. */
    fun readRgbHex(): String? = readRgb()?.let { "#%06X".format(it and RGB_MASK) }

    /** This entity with another state and some attributes replaced, as Home Assistant would report it. */
    fun with(state: String = this.state, attrs: Map<String, JsonElement> = emptyMap()): EntityState =
        copy(state = state, attributes = JsonObject(attributes + attrs))
}

private const val RGB_CHANNELS = 3
private const val CHANNEL_MAX = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE_ALPHA = 0xFF
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFF

/** One demo service invocation: target entity, service name, and payload. */
data class ServiceCall(
    val entityId: String,
    val domain: String,
    val service: String,
    val data: JsonObject,
    val current: EntityState,
) {
    fun num(key: String) = (data[key] as? JsonPrimitive)?.doubleOrNull
    fun str(key: String) = (data[key] as? JsonPrimitive)?.contentOrNull
    fun bool(key: String) = (data[key] as? JsonPrimitive)?.booleanOrNull
}
