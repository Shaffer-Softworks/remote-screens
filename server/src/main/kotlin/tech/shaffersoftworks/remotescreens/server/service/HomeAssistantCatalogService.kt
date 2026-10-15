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
        val registry = runCatching { homeAssistant.command("config/area_registry/list") }.getOrNull() as? JsonArray
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
        val areaByEntity = HashMap<String, String>()
        val hiddenEntities = HashSet<String>()
        (registry?.get("entities") as? JsonArray).orEmpty().forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val entityId = entry.text("ei") ?: return@forEach
            entry.text("ai")?.let { areaByEntity[entityId] = it }
            if (entry["hb"] != null || entry["ec"] != null) hiddenEntities += entityId
        }
        return homeAssistant.states.value.values
            .filter { it.entityId !in hiddenEntities }
            .map { entity ->
                EntityResponse(
                    entityId = entity.entityId,
                    name = entity.name,
                    state = entity.state,
                    domain = entity.domain,
                    area = areaByEntity[entity.entityId],
                    icon = entity.attr("icon"),
                    unit = entity.attr("unit_of_measurement"),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
