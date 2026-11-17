package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * What the add-on needs from Home Assistant. [HomeAssistantClient] talks to a real one, [demo.DemoHome] makes one
 * up for demo mode and the tests.
 */
interface HomeAssistant {
    val isConnected: StateFlow<Boolean>
    val states: StateFlow<Map<String, EntityState>>
    val events: SharedFlow<HomeAssistantEvent>
    val config: StateFlow<HomeAssistantConfig>

    /**
     * Calls [service] on [target], or on the `entity_id` in [data] when [target] is empty. Returns the service's
     * response when [returnResponse] is set. A fixed call the add-on makes itself goes through [call].
     */
    suspend fun callService(
        service: ServiceRef,
        target: List<EntityId> = emptyList(),
        data: ServiceData = ServiceData.EMPTY,
        returnResponse: Boolean = false,
    ): JsonElement?

    /** A WebSocket command, such as [AreaRegistryList]; returns its `result`. */
    suspend fun <R> send(request: HaRequest<R>): R

    /** A GET on the REST API or a path Home Assistant serves, such as `/api/camera_proxy/camera.door`. */
    suspend fun fetch(path: String): ByteArray?

    /**
     * A GET on a path Home Assistant serves, with its body handed to [onBody] as it arrives rather than read whole:
     * for a camera's video. [onBody] is only called on success. Returns the HTTP status.
     */
    suspend fun stream(path: String, onBody: suspend (contentType: String?, body: ByteReadChannel) -> Unit): Int
}
