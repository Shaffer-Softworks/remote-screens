package tech.shaffersoftworks.remotescreens.server.data.alarm

import java.util.concurrent.ConcurrentHashMap

/** The dialog an alarm page shows over itself until OK is tapped, as RPI's `AlertDialog`. */
data class AlarmNotice(val title: String, val message: String)

/** What a full alarm page shows besides Home Assistant's states: the chosen zone and an open dialog. */
data class AlarmUiState(
    /** The zone entity the page shows and arms, or null for the tile's own entity (All Zones). */
    val selectedZone: String? = null,
    val notice: AlarmNotice? = null,
)

/** Session-wide alarm page UI state keyed by tile id. */
class AlarmUiStore {
    private val byTile = ConcurrentHashMap<String, AlarmUiState>()

    fun state(tileId: String): AlarmUiState = byTile[tileId] ?: AlarmUiState()

    fun snapshot(): Map<String, AlarmUiState> = HashMap(byTile)

    fun selectZone(tileId: String, zoneEntityId: String) {
        byTile[tileId] = state(tileId).copy(selectedZone = zoneEntityId)
    }

    fun showNotice(tileId: String, notice: AlarmNotice) {
        byTile[tileId] = state(tileId).copy(notice = notice)
    }

    fun dismissNotice(tileId: String) {
        byTile[tileId] = state(tileId).copy(notice = null)
    }
}
