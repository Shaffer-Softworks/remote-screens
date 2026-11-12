package tech.shaffersoftworks.remotescreens.server.data.energy

import tech.shaffersoftworks.remotescreens.shared.EnergyBarSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyDistribution
import tech.shaffersoftworks.remotescreens.shared.EnergyNamedSeries
import tech.shaffersoftworks.remotescreens.shared.EnergyRankBar
import tech.shaffersoftworks.remotescreens.shared.EnergySankey
import tech.shaffersoftworks.remotescreens.shared.EnergyTotalRow

/** Period shown on the Energy dashboard period bar. */
enum class EnergyDashboardPeriod {
    DAY,
    WEEK,
    MONTH,
}

/** Per-tile period UI for a full Energy dashboard page. */
data class EnergyDashboardUiState(
    val period: EnergyDashboardPeriod = EnergyDashboardPeriod.DAY,
    /** 0 = current period; negative = how many periods back. */
    val offset: Int = 0,
)

/** Energy statistic ids and pricing from `energy/get_prefs` for the dashboard (not live power). */
data class EnergyDashboardConfig(
    val gridEnergyFrom: String?,
    val gridPrice: Double?,
    val gridName: String,
    val homeName: String,
    val devices: List<EnergyDashboardDevice>,
)

data class EnergyDashboardDevice(
    val name: String,
    val statConsumption: String,
    val includedInStat: String? = null,
    val areaId: String? = null,
    val areaName: String? = null,
)

/** Assembled chart payload for [tech.shaffersoftworks.remotescreens.shared.ServerMessage.EnergyDashboard]. */
data class EnergyDashboardSnapshot(
    val periodLabel: String,
    val isNow: Boolean,
    val electricity: EnergyBarSeries,
    val totals: List<EnergyTotalRow>,
    val devicesDetail: List<EnergyNamedSeries>,
    val devicesRank: List<EnergyRankBar>,
    val distribution: EnergyDistribution,
    val lowCarbonPercent: Double,
    val sankey: EnergySankey,
)

/** One recorder statistics row (`change` over a bucket). */
data class EnergyStatPoint(
    val startMs: Long,
    val change: Double,
)
