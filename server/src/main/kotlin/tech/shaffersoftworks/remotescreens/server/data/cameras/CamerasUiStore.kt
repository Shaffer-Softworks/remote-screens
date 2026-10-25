package tech.shaffersoftworks.remotescreens.server.data.cameras

import java.util.concurrent.ConcurrentHashMap

/** What a full cameras page shows besides Home Assistant's states: the camera chosen in its list, if any. */
data class CamerasUiState(
    val selectedCamera: String? = null,
)

/** Session-wide cameras page UI state keyed by tile id. */
class CamerasUiStore {
    private val byTile = ConcurrentHashMap<String, CamerasUiState>()

    fun state(tileId: String): CamerasUiState = byTile[tileId] ?: CamerasUiState()

    fun snapshot(): Map<String, CamerasUiState> = HashMap(byTile)

    fun selectCamera(tileId: String, entityId: String) {
        byTile[tileId] = state(tileId).copy(selectedCamera = entityId)
    }
}
