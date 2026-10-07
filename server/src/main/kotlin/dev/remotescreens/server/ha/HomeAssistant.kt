package dev.remotescreens.server.ha

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** What the add-on needs from Home Assistant. [HaClient] talks to a real one, [DemoHome] makes one up. */
interface HomeAssistant {
    val connected: StateFlow<Boolean>
    val states: StateFlow<Map<String, EntityState>>
    val events: SharedFlow<HaEvent>
    val config: StateFlow<HaConfig>

    suspend fun callService(domain: String, service: String, data: JsonObject, returnResponse: Boolean = false): JsonElement?

    /** Any WebSocket command, such as `config/area_registry/list`. Returns its `result`. */
    suspend fun command(type: String, payload: JsonObject = JsonObject(emptyMap())): JsonElement?

    /** A GET on the REST API or a path Home Assistant serves, such as `/api/camera_proxy/camera.door`. */
    suspend fun fetch(path: String): ByteArray?
}

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
    val lastChanged: String = "",
) {
    val domain: String get() = entityId.substringBefore('.')
    val name: String get() = attr("friendly_name") ?: entityId.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
    val available: Boolean get() = state != "unavailable" && state != "unknown"

    fun attr(key: String): String? = (attributes[key] as? JsonPrimitive)?.takeIf { it.isString || it.doubleOrNull != null || it.booleanOrNull != null }?.content
    fun number(key: String): Double? = (attributes[key] as? JsonPrimitive)?.doubleOrNull
    fun list(key: String): List<String> = runCatching { attributes[key]!!.jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
    fun features(): Int = number("supported_features")?.toInt() ?: 0
    fun supports(bit: Int): Boolean = features() and bit != 0
    fun numericState(): Double? = state.toDoubleOrNull()

    fun with(state: String = this.state, attrs: Map<String, JsonElement> = emptyMap()): EntityState =
        copy(state = state, attributes = JsonObject(attributes + attrs))
}

data class HaEvent(val type: String, val data: JsonObject)

data class HaConfig(
    val language: String = "en",
    val country: String? = null,
    val timeZone: String = "UTC",
    val temperatureUnit: String = "°C",
    val locationName: String = "Home",
    val version: String = "",
)
