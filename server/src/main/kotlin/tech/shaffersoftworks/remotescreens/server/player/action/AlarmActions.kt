package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.ALARM_PIN_LENGTH
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.alarmZones
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.hasOpenSensors
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.selectedZone
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The verbs of a full alarm page, as RPI's `AlarmViewModel` handles them. */
class AlarmActions(
    private val homeAssistant: HomeAssistant,
    private val i18n: () -> I18n,
    private val renderData: RenderDataRepository?,
) {
    suspend fun handle(verb: String, arg: String, target: TapTarget, memory: TapMemory): TapOutcome {
        val tile = target.tile
        val entityId = target.entityId
        return if (tile == null || entityId == null || renderData == null) {
            TapOutcome.Ignored
        } else {
            when (verb) {
                "alarm_zone" -> selectZone(tile, entityId, arg, memory)
                "alarm_dismiss" -> {
                    renderData.alarmUi.dismissNotice(tile.id)
                    TapOutcome.Rebuild
                }
                else -> arm(tile, entityId, arg, memory)
            }
        }
    }

    /** Shows [zoneEntityId] and arms it from now on. The pin is cleared, as the page is built again. */
    private fun selectZone(tile: Tile, entityId: String, zoneEntityId: String, memory: TapMemory): TapOutcome {
        if (tile.alarmZones().none { it.entityId == zoneEntityId }) return TapOutcome.Ignored
        renderData?.alarmUi?.selectZone(tile.id, zoneEntityId)
        memory.codes.remove(entityId)
        return TapOutcome.Rebuild
    }

    /**
     * Arms or disarms the selected zone with the typed pin. An empty pin, one that is not four digits, or open
     * sensors show RPI's dialog instead. The pin is cleared either way.
     */
    private suspend fun arm(tile: Tile, entityId: String, verb: String, memory: TapMemory): TapOutcome {
        val action = Catalogue.of("alarm_control_panel")?.actions?.get(verb) ?: return TapOutcome.Ignored
        val code = memory.codes.remove(entityId).orEmpty()
        val selected = renderData?.alarmUi?.state(tile.id)?.selectedZone
        val zoneEntityId = tile.alarmZones().selectedZone(selected)?.entityId ?: entityId
        val zoneState = homeAssistant.states.value[zoneEntityId]
        val words = i18n()
        val notice = when {
            code.isEmpty() -> words.t("ui.alarm_pin_first_title") to words.t("ui.alarm_pin_first")
            code.length != ALARM_PIN_LENGTH -> words.t("ui.alarm_pin_invalid_title") to words.t("ui.alarm_pin_invalid")
            verb != "disarm" && zoneState.hasOpenSensors() ->
                words.t("ui.alarm_open_sensors_title") to words.t("ui.alarm_open_sensors")
            else -> null
        }
        return if (notice != null) {
            TapOutcome.AlarmNotice(tile.id, notice.first, notice.second)
        } else {
            val data = ServiceDataBuilder.build(action, verb, "", zoneState, code)
            val (domain, service) = action.service.split('.', limit = 2)
            homeAssistant.callService(domain, service, JsonObject(data + ("entity_id" to JsonPrimitive(zoneEntityId))))
            TapOutcome.Done
        }
    }
}
