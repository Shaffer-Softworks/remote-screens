package tech.shaffersoftworks.remotescreens.server.data.energy

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergyPreferences
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergySource
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.StatisticRow

/** Reads Energy dashboard prefs and recorder statistics into the dashboard's models. */
object EnergyDashboardParser {
    private const val DEFAULT_HOME_NAME = "Home"
    private const val DEFAULT_GRID_NAME = "Grid"
    private const val FULL_FOSSIL = 1.0

    fun config(
        preferences: EnergyPreferences,
        entityAreas: Map<String, Pair<String, String>>,
        homeName: String = DEFAULT_HOME_NAME,
    ): EnergyDashboardConfig {
        val grid = preferences.energySources.firstOrNull { it.type == EnergySource.GRID }
        val devices = preferences.deviceConsumption.map { device ->
            val stat = device.statConsumption
            val area = entityAreas[stat]
            EnergyDashboardDevice(
                name = device.name ?: stat.substringAfterLast('.'),
                statConsumption = stat,
                includedInStat = device.includedInStat,
                areaId = area?.first,
                areaName = area?.second,
            )
        }
        return EnergyDashboardConfig(
            gridEnergyFrom = grid?.statEnergyFrom,
            gridPrice = grid?.numberEnergyPrice,
            gridName = DEFAULT_GRID_NAME,
            homeName = homeName,
            devices = devices,
        )
    }

    fun statistics(response: Map<String, List<StatisticRow>>?, statisticId: String): List<EnergyStatPoint> =
        response?.get(statisticId).orEmpty().map { row -> EnergyStatPoint(startMs = row.start, change = row.change) }

    /** Fossil energy share as 0..1 from `energy/fossil_energy_consumption`. */
    fun fossilFraction(response: Map<String, Double?>?): Double {
        val values = response?.values?.filterNotNull().orEmpty()
        return if (values.isEmpty()) FULL_FOSSIL else values.average().coerceIn(0.0, FULL_FOSSIL)
    }

    fun fossilKwh(response: Map<String, Double?>?): Double = response?.values?.filterNotNull()?.sum() ?: 0.0
}
