package tech.shaffersoftworks.remotescreens.server.data.lights

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AreaRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.DeviceRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryListForDisplay
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import org.slf4j.LoggerFactory

private const val ROOMS_TTL_MS = 60_000L

/** The domains RPI's lights page lists in a room: its `LightAttributes`, `SwitchAttributes` and `CoverAttributes`. */
val LIGHTS_DOMAINS: Set<Domain> = setOf(Domain.LIGHT, Domain.SWITCH, Domain.COVER)

/** Covers only, for the blinds page. */
val BLINDS_DOMAINS: Set<Domain> = setOf(Domain.COVER)

/** A Home Assistant area and the lights, switches and covers in it, in the order Home Assistant lists states. */
data class LightsRoom(
    val areaId: String,
    val name: String,
    val entityIds: List<String>,
)

/**
 * Reads the areas for a lights or blinds page from Home Assistant's registries. An entity is in its own area, or in
 * its device's area when it has none, as the `area_entities()` template RPI asks for.
 */
class LightsRoomsRepository(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(LightsRoomsRepository::class.java)

    @Volatile
    private var cachedByDomains: Map<String, Pair<List<LightsRoom>, Long>> = emptyMap()

    suspend fun rooms(domains: Set<Domain> = LIGHTS_DOMAINS): List<LightsRoom> {
        val cacheKey = domains.map { it.value }.sorted().joinToString(",")

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

    private suspend fun fetchRooms(domains: Set<Domain>): List<LightsRoom> {
        val areas = homeAssistant.send(AreaRegistryList)
        val devices = homeAssistant.send(DeviceRegistryList)
        val entityAreas = homeAssistant.send(EntityRegistryListForDisplay).entityAreaIds(devices)
        val ordered = homeAssistant.states.value.keys.filter { entityId -> EntityId(entityId).domain in domains }

        val rooms = areas
            .map { area ->
                val areaId = area.areaId

                LightsRoom(
                    areaId = areaId,
                    name = area.displayName,
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
}
