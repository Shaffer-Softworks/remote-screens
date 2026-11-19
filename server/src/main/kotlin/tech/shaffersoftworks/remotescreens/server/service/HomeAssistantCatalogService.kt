package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AreaRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.DeviceRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.DisplayEntityRegistry
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryListForDisplay
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.routes.model.AreaResponse
import tech.shaffersoftworks.remotescreens.server.routes.model.EntityResponse

/** The entities and areas the editor offers, from Home Assistant's registries. */
class HomeAssistantCatalogService(private val homeAssistant: HomeAssistant) {

    suspend fun areas(): List<AreaResponse> =
        runCatching { homeAssistant.send(AreaRegistryList) }.getOrNull().orEmpty()
            .map { area -> AreaResponse(area.areaId, area.displayName) }
            .sortedBy { it.name.lowercase() }

    /** Every entity, except the ones the registry hides or marks as an entity category (config, diagnostic). */
    suspend fun entities(): List<EntityResponse> {
        val registry = runCatching { homeAssistant.send(EntityRegistryListForDisplay) }.getOrNull()
            ?: DisplayEntityRegistry()

        val devices = runCatching { homeAssistant.send(DeviceRegistryList) }.getOrNull().orEmpty()
        // Own area first, else the device's area — same inheritance as the lights page rooms.
        val areaByEntity = registry.entityAreaIds(devices)
        // `ic` is the icon set on the entity in Home Assistant. State often omits it.
        val iconByEntity = registry.entities.mapNotNull { entry -> entry.icon?.let { entry.entityId to it } }.toMap()
        val hiddenEntities = registry.entities.filter { it.isHidden }.mapTo(HashSet()) { it.entityId }

        return homeAssistant.states.value.values
            .filter { entity -> entity.entityId !in hiddenEntities }
            .map { entity ->
                EntityResponse(
                    entityId = entity.entityId,
                    name = entity.name,
                    state = entity.state,
                    domain = entity.domain.value,
                    area = areaByEntity[entity.entityId],
                    icon = entity.common.icon ?: iconByEntity[entity.entityId],
                    unit = entity.common.unitOfMeasurement,
                    rgb = entity.readRgbHex(),
                )
            }
            .sortedBy { entity -> entity.name.lowercase() }
    }
}
