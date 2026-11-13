package tech.shaffersoftworks.remotescreens.server.data.blinds

import java.util.concurrent.ConcurrentHashMap

/** What a full blinds page shows besides Home Assistant's states: the chosen room. */
data class BlindsUiState(
    val selectedAreaId: String? = null,
)

/** Session-wide blinds page UI state keyed by tile id. */
class BlindsUiStore {
    private val byTile = ConcurrentHashMap<String, BlindsUiState>()

    fun state(tileId: String): BlindsUiState = byTile[tileId] ?: BlindsUiState()

    fun snapshot(): Map<String, BlindsUiState> = HashMap(byTile)

    fun selectRoom(tileId: String, areaId: String) {
        byTile[tileId] = state(tileId).copy(selectedAreaId = areaId)
    }
}
