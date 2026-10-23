package tech.shaffersoftworks.remotescreens.server.data.homeassistant

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

    /** This entity with another state and some attributes replaced, as Home Assistant would report it. */
    fun with(state: String = this.state, attrs: Map<String, JsonElement> = emptyMap()): EntityState =
        copy(state = state, attributes = JsonObject(attributes + attrs))
}

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
