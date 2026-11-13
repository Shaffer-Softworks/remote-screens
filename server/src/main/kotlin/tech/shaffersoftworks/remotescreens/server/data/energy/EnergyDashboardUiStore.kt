package tech.shaffersoftworks.remotescreens.server.data.energy

import java.util.concurrent.ConcurrentHashMap

/** Session-wide Energy dashboard period UI keyed by tile id. */
class EnergyDashboardUiStore {
    private val byTile = ConcurrentHashMap<String, EnergyDashboardUiState>()

    fun state(tileId: String): EnergyDashboardUiState = byTile[tileId] ?: EnergyDashboardUiState()

    fun snapshot(): Map<String, EnergyDashboardUiState> = HashMap(byTile)

    fun shiftPeriod(tileId: String, delta: Int) {
        val current = state(tileId)
        byTile[tileId] = current.copy(offset = (current.offset + delta).coerceAtMost(0))
    }

    fun goToNow(tileId: String) {
        byTile[tileId] = state(tileId).copy(offset = 0)
    }

    fun cyclePeriod(tileId: String) {
        val current = state(tileId)
        val next = when (current.period) {
            EnergyDashboardPeriod.DAY -> EnergyDashboardPeriod.WEEK
            EnergyDashboardPeriod.WEEK -> EnergyDashboardPeriod.MONTH
            EnergyDashboardPeriod.MONTH -> EnergyDashboardPeriod.DAY
        }
        byTile[tileId] = current.copy(period = next, offset = 0)
    }
}
