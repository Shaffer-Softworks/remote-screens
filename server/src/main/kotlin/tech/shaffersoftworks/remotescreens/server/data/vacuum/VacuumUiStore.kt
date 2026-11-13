package tech.shaffersoftworks.remotescreens.server.data.vacuum

import java.util.concurrent.ConcurrentHashMap

/** What a full vacuum page shows besides Home Assistant's states: the vacuum chosen in its list, if any. */
data class VacuumUiState(
    val selectedVacuum: String? = null,
)

/** Session-wide vacuum page UI state keyed by tile id. */
class VacuumUiStore {
    private val byTile = ConcurrentHashMap<String, VacuumUiState>()

    fun state(tileId: String): VacuumUiState = byTile[tileId] ?: VacuumUiState()

    fun snapshot(): Map<String, VacuumUiState> = HashMap(byTile)

    fun selectVacuum(tileId: String, entityId: String) {
        byTile[tileId] = state(tileId).copy(selectedVacuum = entityId)
    }
}
