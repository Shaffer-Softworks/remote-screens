package tech.shaffersoftworks.remotescreens.server.data.energy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Parses Energy dashboard prefs and recorder statistics responses. */
object EnergyDashboardParser {
    private const val DEFAULT_HOME_NAME = "Home"
    private const val DEFAULT_GRID_NAME = "Grid"
    private const val FULL_FOSSIL = 1.0

    fun config(
        preferences: JsonObject,
        entityAreas: Map<String, Pair<String, String>>,
        homeName: String = DEFAULT_HOME_NAME,
    ): EnergyDashboardConfig {
        val sources = (preferences["energy_sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val grid = sources.firstOrNull { it.text("type") == "grid" }
        val devices = (preferences["device_consumption"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { device ->
                val stat = device.text("stat_consumption") ?: return@mapNotNull null
                val area = entityAreas[stat]
                EnergyDashboardDevice(
                    name = device.text("name") ?: stat.substringAfterLast('.'),
                    statConsumption = stat,
                    includedInStat = device.text("included_in_stat"),
                    areaId = area?.first,
                    areaName = area?.second,
                )
            }
        return EnergyDashboardConfig(
            gridEnergyFrom = grid?.text("stat_energy_from"),
            gridPrice = grid?.number("number_energy_price"),
            gridName = DEFAULT_GRID_NAME,
            homeName = homeName,
            devices = devices,
        )
    }

    fun statistics(response: JsonElement?, statisticId: String): List<EnergyStatPoint> {
        val rows = (response as? JsonObject)?.get(statisticId) as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val start = row.long("start") ?: return@mapNotNull null
            val change = row.number("change") ?: return@mapNotNull null
            EnergyStatPoint(startMs = start, change = change)
        }
    }

    /** Fossil energy share as 0..1 from `energy/fossil_energy_consumption`. */
    fun fossilFraction(response: JsonElement?): Double {
        val values = (response as? JsonObject)
            ?.values
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
            .orEmpty()
        return if (values.isEmpty()) FULL_FOSSIL else values.average().coerceIn(0.0, FULL_FOSSIL)
    }

    fun fossilKwh(response: JsonElement?): Double =
        (response as? JsonObject)
            ?.values
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
            ?.sum()
            ?: 0.0

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
