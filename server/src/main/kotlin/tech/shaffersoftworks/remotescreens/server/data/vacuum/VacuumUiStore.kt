package tech.shaffersoftworks.remotescreens.server.data.vacuum

import java.util.concurrent.ConcurrentHashMap

/**
 * What a full vacuum page (or vacuum card) shows besides Home Assistant's states: the vacuum chosen in its
 * list, whether the rooms dialog is open, and which rooms are selected for a selective clean.
 */
data class VacuumUiState(
    val selectedVacuum: String? = null,
    val roomsDialogOpen: Boolean = false,
    val selectedRooms: Set<String> = emptySet(),
)

/** Session-wide vacuum UI state keyed by tile id (`va1`) or card entity tile (`e:vacuum.robot`). */
class VacuumUiStore {
    private val byTile = ConcurrentHashMap<String, VacuumUiState>()

    fun state(tileId: String): VacuumUiState = byTile[tileId] ?: VacuumUiState()

    fun snapshot(): Map<String, VacuumUiState> = HashMap(byTile)

    fun selectVacuum(tileId: String, entityId: String) {
        val current = state(tileId)
        byTile[tileId] = if (current.selectedVacuum == entityId) {
            current.copy(selectedVacuum = entityId)
        } else {
            current.copy(
                selectedVacuum = entityId,
                roomsDialogOpen = false,
                selectedRooms = emptySet(),
            )
        }
    }

    fun openRooms(tileId: String) {
        byTile[tileId] = state(tileId).copy(roomsDialogOpen = true)
    }

    fun dismissRooms(tileId: String) {
        byTile[tileId] = state(tileId).copy(roomsDialogOpen = false)
    }

    fun toggleRoom(tileId: String, roomId: String) {
        val current = state(tileId)
        val next = current.selectedRooms.toMutableSet()
        if (!next.add(roomId)) next.remove(roomId)
        byTile[tileId] = current.copy(selectedRooms = next)
    }

    /** Closes the dialog and clears the selection after a selective clean starts. */
    fun afterClean(tileId: String) {
        byTile[tileId] = state(tileId).copy(roomsDialogOpen = false, selectedRooms = emptySet())
    }
}
