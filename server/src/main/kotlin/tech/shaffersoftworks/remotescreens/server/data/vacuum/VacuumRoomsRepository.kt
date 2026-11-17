package tech.shaffersoftworks.remotescreens.server.data.vacuum

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AreaRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryGet
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
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
        val mapping = homeAssistant.send(EntityRegistryGet(entityId)).options.vacuum?.areaMapping.orEmpty()
        val areaIds = mapping.filterValues { segments -> segments.isNotEmpty() }.keys
        if (areaIds.isEmpty()) return emptyList()
        val names = homeAssistant.send(AreaRegistryList).associate { area -> area.areaId to area.displayName }
        return areaIds
            .map { areaId -> VacuumRoom(id = areaId, name = names[areaId] ?: areaId) }
            .sortedBy { room -> room.name }
    }
}
