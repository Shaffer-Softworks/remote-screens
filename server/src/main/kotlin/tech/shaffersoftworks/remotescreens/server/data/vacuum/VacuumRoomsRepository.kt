package tech.shaffersoftworks.remotescreens.server.data.vacuum

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private const val AREA_ROOMS_TTL_MS = 60_000L

/**
 * The rooms a vacuum's dialog lists. A vacuum with `CLEAN_AREA` lists the Home Assistant areas its entity settings
 * map to segments (`options.vacuum.area_mapping`); any other falls back to its `room_list` attributes.
 */
class VacuumRoomsRepository(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(VacuumRoomsRepository::class.java)
    private val areaRooms = ConcurrentHashMap<String, Pair<List<VacuumRoom>, Long>>()

    /** Null when the vacuum has no rooms to choose from. */
    suspend fun rooms(entityId: String): VacuumRooms? {
        val entityState = homeAssistant.states.value[entityId]
        val mapped = if (entityState?.supportsCleanArea() == true) mappedAreas(entityId) else emptyList()
        val rooms = if (mapped.isNotEmpty()) {
            VacuumRooms(VacuumCleanMethod.AREA, mapped)
        } else {
            VacuumRooms(VacuumCleanMethod.SEGMENT, entityState?.vacuumSegmentRooms().orEmpty())
        }
        return rooms.takeIf { it.rooms.isNotEmpty() }
    }

    fun clear() {
        areaRooms.clear()
    }

    private suspend fun mappedAreas(entityId: String): List<VacuumRoom> {
        areaRooms[entityId]
            ?.takeIf { System.currentTimeMillis() - it.second < AREA_ROOMS_TTL_MS }
            ?.let { return it.first }
        val rooms = runCatching { fetchMappedAreas(entityId) }
            .onFailure { logger.debug("Vacuum areas of {} failed: {}", entityId, it.message) }
            .getOrElse { areaRooms[entityId]?.first.orEmpty() }
        areaRooms[entityId] = rooms to System.currentTimeMillis()
        return rooms
    }

    private suspend fun fetchMappedAreas(entityId: String): List<VacuumRoom> {
        val entry = homeAssistant.command(
            "config/entity_registry/get",
            buildJsonObject { put("entity_id", entityId) },
        ) as? JsonObject
        val mapping = entry.objectAt("options")
            .objectAt("vacuum")
            .objectAt("area_mapping")
            .orEmpty()
        val areaIds = mapping.filterValues { segments -> (segments as? JsonArray).orEmpty().isNotEmpty() }.keys
        if (areaIds.isEmpty()) return emptyList()
        val names = (homeAssistant.command("config/area_registry/list") as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val area = element as? JsonObject
                val areaId = area?.text("area_id")
                areaId?.let { it to (area.text("name") ?: it) }
            }
            .toMap()
        return areaIds
            .map { areaId -> VacuumRoom(id = areaId, name = names[areaId] ?: areaId) }
            .sortedBy { room -> room.name }
    }

    private fun JsonObject?.objectAt(key: String): JsonObject? = this?.get(key) as? JsonObject

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
