package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.lights.LightDetailMode
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsUiStore
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.blue
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.green
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.red
import tech.shaffersoftworks.remotescreens.server.render.panels.lights.supportsRgbColor
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The verbs of a full lights page: the chosen room and RPI's light dialog. Light changes go through the catalogue. */
class LightsActions(
    private val homeAssistant: HomeAssistant,
    private val renderData: RenderDataRepository?,
) {
    suspend fun handle(verb: String, arg: String, target: TapTarget): TapOutcome {
        val tileId = target.tile?.id
        val store = renderData?.lightsUi
        val isHandled = when {
            tileId == null || store == null -> false
            verb == "lights_palette" || verb == "lights_effect" -> changeLight(store, tileId, verb, arg)
            else -> changePage(store, tileId, verb, arg)
        }
        return if (isHandled) TapOutcome.Rebuild else TapOutcome.Ignored
    }

    private fun changePage(store: LightsUiStore, tileId: String, verb: String, arg: String): Boolean {
        val mode = LightDetailMode.of(arg)
        when (verb) {
            "lights_room" -> store.selectRoom(tileId, arg)
            "lights_detail" -> store.openDetail(tileId, arg)
            "lights_dismiss" -> store.dismissDetail(tileId)
            "lights_effects" -> store.setEffectMenuOpen(tileId, !store.state(tileId).isEffectMenuOpen)
            "lights_mode" -> if (mode != null) store.selectMode(tileId, mode)
        }
        return verb != "lights_mode" || mode != null
    }

    private suspend fun changeLight(store: LightsUiStore, tileId: String, verb: String, arg: String): Boolean {
        if (verb == "lights_effect") {
            store.setEffectMenuOpen(tileId, false)
            turnOnDetailLight(tileId, "effect" to JsonPrimitive(arg))
            return true
        }
        val index = arg.toIntOrNull()
        if (index != null) pickSwatch(tileId, index)
        return index != null
    }

    /** RPI's swatch tap: the swatch is ringed, and a light that takes colour turns to it. */
    private suspend fun pickSwatch(tileId: String, index: Int) {
        val colour = Rpi.LightDetailPalette.getOrNull(index) ?: return
        renderData?.lightsUi?.selectPalette(tileId, index)
        val entityId = renderData?.lightsUi?.state(tileId)?.detailEntityId
        if (entityId != null && homeAssistant.states.value[entityId]?.supportsRgbColor() == true) {
            val channels = listOf(colour.red(), colour.green(), colour.blue()).map { JsonPrimitive(it) }
            turnOnDetailLight(tileId, "rgb_color" to JsonArray(channels))
        }
    }

    private suspend fun turnOnDetailLight(tileId: String, field: Pair<String, JsonElement>) {
        val entityId = renderData?.lightsUi?.state(tileId)?.detailEntityId ?: return
        homeAssistant.callService("light", "turn_on", JsonObject(mapOf("entity_id" to JsonPrimitive(entityId), field)))
    }
}
