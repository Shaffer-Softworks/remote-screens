package tech.shaffersoftworks.remotescreens.server.data.blinds

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

/** What a full blinds page shows besides Home Assistant's states: the chosen room. */
data class BlindsUiState(
    val selectedAreaId: String? = null,
)

/** Session-wide blinds page UI state keyed by tile id. */
class BlindsUiStore : TileUiStore<BlindsUiState>(BlindsUiState()) {
    fun selectRoom(tileId: String, areaId: String) {
        update(tileId) { it.copy(selectedAreaId = areaId) }
    }
}
