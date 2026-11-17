package tech.shaffersoftworks.remotescreens.server.data.cameras

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

/** What a full cameras page shows besides Home Assistant's states: the camera chosen in its list, if any. */
data class CamerasUiState(
    val selectedCamera: String? = null,
)

/** Session-wide cameras page UI state keyed by tile id. */
class CamerasUiStore : TileUiStore<CamerasUiState>(CamerasUiState()) {
    fun selectCamera(tileId: String, entityId: String) {
        update(tileId) { it.copy(selectedCamera = entityId) }
    }
}
