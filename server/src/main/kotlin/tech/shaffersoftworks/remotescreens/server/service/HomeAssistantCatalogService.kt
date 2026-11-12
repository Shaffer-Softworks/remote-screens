package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.routes.model.AreaResponse
import tech.shaffersoftworks.remotescreens.server.routes.model.EntityResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The entities and areas the editor offers, from Home Assistant's registries. */
class HomeAssistantCatalogService(private val homeAssistant: HomeAssistant) {

    suspend fun areas(): List<AreaResponse> {
        val registry = runCatching { homeAssistant.command(
            "config/area_registry/list",
        ) }.getOrNull() as? JsonArray
        return registry.orEmpty()
            .mapNotNull { element ->
                val area = element as? JsonObject ?: return@mapNotNull null
                val areaId = area.text("area_id") ?: return@mapNotNull null
                AreaResponse(areaId, area.text("name") ?: areaId)
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Every entity, except the ones the registry hides or marks as an entity category (config, diagnostic). */
    suspend fun entities(): List<EntityResponse> {
        val registry = runCatching {
            homeAssistant.command("config/entity_registry/list_for_display") as? JsonObject
        }.getOrNull()
        val deviceAreas = deviceAreas()
        val areaByEntity = HashMap<String, String>()
        val iconByEntity = HashMap<String, String>()
        val hiddenEntities = HashSet<String>()
        (registry?.get("entities") as? JsonArray).orEmpty().forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val entityId = entry.text("ei") ?: return@forEach
            // Own area first, else the device's area — same inheritance as the lights page rooms.
            val areaId = entry.text("ai") ?: entry.text("di")?.let(deviceAreas::get)
            if (areaId != null) areaByEntity[entityId] = areaId
            // `ic` is the icon set on the entity in Home Assistant. State often omits it.
            entry.text("ic")?.let { iconByEntity[entityId] = it }
            if (entry["hb"] != null || entry["ec"] != null) hiddenEntities += entityId
        }
        return homeAssistant.states.value.values
            .filter { entity -> entity.entityId !in hiddenEntities }
            .map { entity ->
                EntityResponse(
                    entityId = entity.entityId,
                    name = entity.name,
                    state = entity.state,
                    domain = entity.domain,
                    area = areaByEntity[entity.entityId],
                    icon = entity.attr("icon") ?: iconByEntity[entity.entityId],
                    unit = entity.attr("unit_of_measurement"),
                    rgb = entity.readRgbHex(),
                )
            }
            .sortedBy { entity -> entity.name.lowercase() }
    }

    /** `device id` to its Home Assistant area, for entities that only have `di` in the registry. */
    private suspend fun deviceAreas(): Map<String, String> {
        val devices = runCatching {
            homeAssistant.command("config/device_registry/list") as? JsonArray
        }.getOrNull()
        return devices.orEmpty()
            .mapNotNull { element ->
                val device = element as? JsonObject ?: return@mapNotNull null
                val deviceId = device.text("id") ?: return@mapNotNull null
                device.text("area_id")?.let { areaId -> deviceId to areaId }
            }
            .toMap()
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
