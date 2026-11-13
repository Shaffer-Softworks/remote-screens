package tech.shaffersoftworks.remotescreens.server.data.energy

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId

private const val CONFIG_TTL_MS = 30 * 60_000L
private const val AREAS_TTL_MS = 60_000L

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
                homeAssistant.command(
                    "recorder/statistics_during_period",
                    buildJsonObject {
                        put("start_time", Instant.ofEpochMilli(startMs).toString())
                        put("end_time", Instant.ofEpochMilli(endMs).toString())
                        put("period", EnergyDashboardBuilder.recorderPeriod(ui))
                        put(
                            "statistic_ids",
                            buildJsonArray { statisticIds.forEach { add(JsonPrimitive(it)) } },
                        )
                        put(
                            "types",
                            buildJsonArray { add(JsonPrimitive("change")) },
                        )
                        put("units", buildJsonObject { put("energy", "kWh") })
                    },
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
            homeAssistant.command("energy/get_prefs") as? JsonObject
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
            homeAssistant.command(
                "energy/fossil_energy_consumption",
                buildJsonObject {
                    put("start_time", Instant.ofEpochMilli(startMs).toString())
                    put("end_time", Instant.ofEpochMilli(endMs).toString())
                    put(
                        "energy_statistic_ids",
                        buildJsonArray { statisticIds.forEach { add(JsonPrimitive(it)) } },
                    )
                    put("period", period)
                    put("co2_statistic_id", "sensor.electricity_maps_grid_fossil_fuel_percentage")
                },
            )
        }.onFailure { logger.debug("Fossil energy failed: {}", it.message) }.getOrNull()
        return EnergyDashboardParser.fossilKwh(response)
    }

    /** statistic_id → (areaId, areaName). */
    private suspend fun entityAreas(): Map<String, Pair<String, String>> {
        cachedAreas?.takeIf { System.currentTimeMillis() - it.second < AREAS_TTL_MS }?.let { return it.first }
        val value = runCatching { fetchEntityAreas() }
            .onFailure { logger.debug("Energy area map failed: {}", it.message) }
            .getOrElse { emptyMap() }
        cachedAreas = value to System.currentTimeMillis()
        return value
    }

    private suspend fun fetchEntityAreas(): Map<String, Pair<String, String>> {
        val areas = (homeAssistant.command("config/area_registry/list") as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val area = element as? JsonObject ?: return@mapNotNull null
                val areaId = area.text("area_id") ?: return@mapNotNull null
                areaId to (area.text("name") ?: areaId)
            }
            .toMap()
        val deviceAreas = (homeAssistant.command("config/device_registry/list") as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val device = element as? JsonObject ?: return@mapNotNull null
                val deviceId = device.text("id") ?: return@mapNotNull null
                device.text("area_id")?.let { deviceId to it }
            }
            .toMap()
        val registry = homeAssistant.command("config/entity_registry/list_for_display") as? JsonObject
        val result = HashMap<String, Pair<String, String>>()
        (registry?.get("entities") as? JsonArray).orEmpty().forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val entityId = entry.text("ei") ?: return@forEach
            val areaId = entry.text("ai") ?: entry.text("di")?.let(deviceAreas::get) ?: return@forEach
            val areaName = areas[areaId] ?: return@forEach
            result[entityId] = areaId to areaName
        }
        return result
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
