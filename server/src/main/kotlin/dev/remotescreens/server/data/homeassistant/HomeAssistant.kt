package dev.remotescreens.server.data.homeassistant

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What the add-on needs from Home Assistant. [HomeAssistantClient] talks to a real one, [demo.DemoHome] makes one
 * up for demo mode and the tests.
 */
interface HomeAssistant {
    val isConnected: StateFlow<Boolean>
    val states: StateFlow<Map<String, EntityState>>
    val events: SharedFlow<HomeAssistantEvent>
    val config: StateFlow<HomeAssistantConfig>

    suspend fun callService(
        domain: String,
        service: String,
        data: JsonObject,
        returnResponse: Boolean = false,
    ): JsonElement?

    /** Any WebSocket command, such as `config/area_registry/list`. Returns its `result`. */
    suspend fun command(type: String, payload: JsonObject = JsonObject(emptyMap())): JsonElement?

    /** A GET on the REST API or a path Home Assistant serves, such as `/api/camera_proxy/camera.door`. */
    suspend fun fetch(path: String): ByteArray?
}
