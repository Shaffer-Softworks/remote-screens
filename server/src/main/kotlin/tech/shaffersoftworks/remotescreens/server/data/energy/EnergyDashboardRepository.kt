package tech.shaffersoftworks.remotescreens.server.data.energy

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AreaRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.DeviceRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergyGetPrefs
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryListForDisplay
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.FossilEnergyConsumption
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.StatisticsDuringPeriod
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId

private const val CONFIG_TTL_MS = 30 * 60_000L
private const val AREAS_TTL_MS = 60_000L
private const val STATISTIC_CHANGE = "change"
private const val ENERGY_UNIT = "energy"
private const val KILOWATT_HOURS = "kWh"
private const val FOSSIL_SHARE_SENSOR = "sensor.electricity_maps_grid_fossil_fuel_percentage"

/** Loads Energy dashboard prefs, recorder statistics, and fossil share for a period. */
class EnergyDashboardRepository(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(EnergyDashboardRepository::class.java)

    @Volatile
    private var cachedConfig: Pair<EnergyDashboardConfig?, Long>? = null

    @Volatile
    private var cachedAreas: Pair<Map<String, Pair<String, String>>, Long>? = null

    suspend fun snapshot(
        ui: EnergyDashboardUiState,
        zone: ZoneId,
        homeName: String = "Home",
    ): EnergyDashboardSnapshot? {
        val config = config(homeName) ?: return null
        val (startMs, endMs) = EnergyDashboardBuilder.window(ui = ui, zone = zone)
        val statisticIds = buildList {
            config.gridEnergyFrom?.let(::add)
            config.devices.forEach { add(it.statConsumption) }
        }.distinct()

        val statsResponse = if (statisticIds.isEmpty()) {
            null
        } else {
            runCatching {
                homeAssistant.send(
                    StatisticsDuringPeriod(
                        startTime = Instant.ofEpochMilli(startMs).toString(),
                        endTime = Instant.ofEpochMilli(endMs).toString(),
                        period = EnergyDashboardBuilder.recorderPeriod(ui),
                        statisticIds = statisticIds,
                        types = listOf(STATISTIC_CHANGE),
                        units = mapOf(ENERGY_UNIT to KILOWATT_HOURS),
                    ),
                )
            }.onFailure { logger.debug("Energy statistics failed: {}", it.message) }.getOrNull()
        }
        val gridPoints = config.gridEnergyFrom
            ?.let { EnergyDashboardParser.statistics(statsResponse, it) }
            .orEmpty()
        val devicePoints = config.devices.associate { device ->
            device.statConsumption to EnergyDashboardParser.statistics(statsResponse, device.statConsumption)
        }
        val fossilFraction = fossilFraction(
            statisticIds = listOfNotNull(config.gridEnergyFrom),
            startMs = startMs,
            endMs = endMs,
            period = EnergyDashboardBuilder.recorderPeriod(ui),
        )
        return EnergyDashboardBuilder.build(
            config = config,
            ui = ui,
            zone = zone,
            gridPoints = gridPoints,
            devicePoints = devicePoints,
            fossilKwh = fossilFraction,
        )
    }

    suspend fun config(homeName: String = "Home"): EnergyDashboardConfig? {
        cachedConfig?.takeIf { System.currentTimeMillis() - it.second < CONFIG_TTL_MS }?.let { return it.first }
        val prefs = runCatching {
            homeAssistant.send(EnergyGetPrefs)
        }.onFailure { logger.debug("Energy dashboard prefs failed: {}", it.message) }.getOrNull()
        val value = prefs?.let { EnergyDashboardParser.config(it, entityAreas(), homeName) }
        cachedConfig = value to System.currentTimeMillis()
        return value
    }

    fun clear() {
        cachedConfig = null
        cachedAreas = null
    }

    private suspend fun fossilFraction(
        statisticIds: List<String>,
        startMs: Long,
        endMs: Long,
        period: String,
    ): Double {
        if (statisticIds.isEmpty()) return 1.0
        val response = runCatching {
            homeAssistant.send(
                FossilEnergyConsumption(
                    startTime = Instant.ofEpochMilli(startMs).toString(),
                    endTime = Instant.ofEpochMilli(endMs).toString(),
                    energyStatisticIds = statisticIds,
                    period = period,
                    co2StatisticId = FOSSIL_SHARE_SENSOR,
                ),
            )
        }.onFailure { logger.debug("Fossil energy failed: {}", it.message) }.getOrNull()
        return EnergyDashboardParser.fossilKwh(response)
    }

    /** statistic_id → (areaId, areaName). */
    private suspend fun entityAreas(): Map<String, Pair<String, String>> {
        cachedAreas?.takeIf {
            System.currentTimeMillis() - it.second < AREAS_TTL_MS
        }?.let { return it.first }

        val value = runCatching { fetchEntityAreas() }
            .onFailure { logger.debug("Energy area map failed: {}", it.message) }
            .getOrElse { emptyMap() }
        cachedAreas = value to System.currentTimeMillis()
        return value
    }

    private suspend fun fetchEntityAreas(): Map<String, Pair<String, String>> {
        val areaNames = homeAssistant.send(AreaRegistryList).associate { area -> area.areaId to area.displayName }
        val devices = homeAssistant.send(DeviceRegistryList)
        return homeAssistant.send(EntityRegistryListForDisplay).entityAreaIds(devices)
            .mapNotNull { (entityId, areaId) -> areaNames[areaId]?.let { entityId to (areaId to it) } }
            .toMap()
    }
}
