package tech.shaffersoftworks.remotescreens.server.data.vacuum

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

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
class VacuumUiStore : TileUiStore<VacuumUiState>(VacuumUiState()) {
    fun selectVacuum(tileId: String, entityId: String) {
        update(tileId) { current ->
            if (current.selectedVacuum == entityId) {
                current
            } else {
                current.copy(
                    selectedVacuum = entityId,
                    roomsDialogOpen = false,
                    selectedRooms = emptySet(),
                )
            }
        }
    }

    fun openRooms(tileId: String) {
        update(tileId) { it.copy(roomsDialogOpen = true) }
    }

    fun dismissRooms(tileId: String) {
        update(tileId) { it.copy(roomsDialogOpen = false) }
    }

    fun toggleRoom(tileId: String, roomId: String) {
        update(tileId) { current ->
            val rooms = current.selectedRooms
            current.copy(selectedRooms = if (roomId in rooms) rooms - roomId else rooms + roomId)
        }
    }

    /** Closes the dialog and clears the selection after a selective clean starts. */
    fun afterClean(tileId: String) {
        update(tileId) { it.copy(roomsDialogOpen = false, selectedRooms = emptySet()) }
    }
}
