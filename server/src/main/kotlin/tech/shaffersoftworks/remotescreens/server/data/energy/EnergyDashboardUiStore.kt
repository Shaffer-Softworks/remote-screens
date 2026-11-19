package tech.shaffersoftworks.remotescreens.server.data.energy

import tech.shaffersoftworks.remotescreens.server.data.ui.TileUiStore

/** Session-wide Energy dashboard period UI keyed by tile id. */
class EnergyDashboardUiStore : TileUiStore<EnergyDashboardUiState>(EnergyDashboardUiState()) {
    fun shiftPeriod(tileId: String, delta: Int) {
        update(tileId) { it.copy(offset = (it.offset + delta).coerceAtMost(0)) }
    }

    fun goToNow(tileId: String) {
        update(tileId) { it.copy(offset = 0) }
    }

    fun cyclePeriod(tileId: String) {
        update(tileId) { current ->
            val next = when (current.period) {
                EnergyDashboardPeriod.DAY -> EnergyDashboardPeriod.WEEK
                EnergyDashboardPeriod.WEEK -> EnergyDashboardPeriod.MONTH
                EnergyDashboardPeriod.MONTH -> EnergyDashboardPeriod.DAY
            }

            current.copy(period = next, offset = 0)
        }
    }
}
