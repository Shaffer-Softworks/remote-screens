package tech.shaffersoftworks.remotescreens.server.data.alarm

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

/** The dialog an alarm page shows over itself until OK is tapped, as RPI's `AlertDialog`. */
data class AlarmNotice(val title: String, val message: String)

/** What a full alarm page shows besides Home Assistant's states: the chosen zone and an open dialog. */
data class AlarmUiState(
    /** The zone entity the page shows and arms, or null for the tile's own entity (All Zones). */
    val selectedZone: String? = null,
    val notice: AlarmNotice? = null,
)

/** Session-wide alarm page UI state keyed by tile id. */
class AlarmUiStore : TileUiStore<AlarmUiState>(AlarmUiState()) {
    fun selectZone(tileId: String, zoneEntityId: String) {
        update(tileId) { it.copy(selectedZone = zoneEntityId) }
    }

    fun showNotice(tileId: String, notice: AlarmNotice) {
        update(tileId) { it.copy(notice = notice) }
    }

    fun dismissNotice(tileId: String) {
        update(tileId) { it.copy(notice = null) }
    }
}
