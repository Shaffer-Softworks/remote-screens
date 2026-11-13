package tech.shaffersoftworks.remotescreens.server.data.lights

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.slf4j.LoggerFactory

private const val ROOMS_TTL_MS = 60_000L

/** The domains RPI's lights page lists in a room: its `LightAttributes`, `SwitchAttributes` and `CoverAttributes`. */
val LIGHTS_DOMAINS: Set<String> = setOf("light", "switch", "cover")

/** Covers only, for the blinds page. */
val BLINDS_DOMAINS: Set<String> = setOf("cover")

/** A Home Assistant area and the lights, switches and covers in it, in the order Home Assistant lists states. */
data class LightsRoom(val areaId: String, val name: String, val entityIds: List<String>)

/**
 * Reads the areas for a lights or blinds page from Home Assistant's registries. An entity is in its own area, or in
 * its device's area when it has none, as the `area_entities()` template RPI asks for.
 */
class LightsRoomsRepository(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(LightsRoomsRepository::class.java)

    @Volatile
    private var cachedByDomains: Map<String, Pair<List<LightsRoom>, Long>> = emptyMap()

    suspend fun rooms(domains: Set<String> = LIGHTS_DOMAINS): List<LightsRoom> {
        val cacheKey = domains.sorted().joinToString(",")
        cachedByDomains[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.second < ROOMS_TTL_MS }
            ?.let { return it.first }
        val rooms = runCatching { fetchRooms(domains) }
            .onFailure { logger.debug("Rooms failed ({}): {}", cacheKey, it.message) }
            .getOrElse { cachedByDomains[cacheKey]?.first.orEmpty() }
        cachedByDomains = cachedByDomains + (cacheKey to (rooms to System.currentTimeMillis()))
        return rooms
    }

    fun clear() {
        cachedByDomains = emptyMap()
    }

    private suspend fun fetchRooms(domains: Set<String>): List<LightsRoom> {
        val areas = (homeAssistant.command("config/area_registry/list") as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val area = element as? JsonObject ?: return@mapNotNull null
                val areaId = area.text("area_id") ?: return@mapNotNull null
                areaId to (area.text("name") ?: areaId)
            }
        val deviceAreas = (homeAssistant.command("config/device_registry/list") as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val device = element as? JsonObject ?: return@mapNotNull null
                val deviceId = device.text("id") ?: return@mapNotNull null
                device.text("area_id")?.let { id -> deviceId to id }
            }
            .toMap()
        val registry = homeAssistant.command("config/entity_registry/list_for_display") as? JsonObject
        val entityAreas = HashMap<String, String>()
        (registry?.get("entities") as? JsonArray).orEmpty().forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val entityId = entry.text("ei") ?: return@forEach
            val areaId = entry.text("ai") ?: entry.text("di")?.let(deviceAreas::get) ?: return@forEach
            entityAreas[entityId] = areaId
        }
        val ordered = homeAssistant.states.value.keys.filter { entityId ->
            entityId.substringBefore('.') in domains
        }
        val rooms = areas
            .map { (areaId, name) ->
                LightsRoom(
                    areaId = areaId,
                    name = name,
                    entityIds = ordered.filter { entityId -> entityAreas[entityId] == areaId },
                )
            }
            .sortedBy { room -> room.name }
        // Blinds only lists rooms that have a cover; lights keeps empty rooms like RPI.
        return if (domains == BLINDS_DOMAINS) {
            rooms.filter { room -> room.entityIds.isNotEmpty() }
        } else {
            rooms
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
