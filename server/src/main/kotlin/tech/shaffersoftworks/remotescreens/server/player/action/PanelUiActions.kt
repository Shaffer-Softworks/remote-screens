package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository

/** Page-chrome verbs that only change session UI: blinds room, vacuum and camera selection. */
class PanelUiActions(
    homeAssistant: HomeAssistant,
    renderData: RenderDataRepository?,
) {
    private val blindsActions = BlindsActions(renderData)
    private val vacuumActions = VacuumActions(homeAssistant, renderData)
    private val camerasActions = CamerasActions(renderData)
    private val securityActions = SecurityActions(renderData)

    suspend fun handle(verb: String, arg: String, target: TapTarget): TapOutcome = when (verb) {
        "blinds_room" -> blindsActions.handle(verb, arg, target)
        "vacuum_select",
        "vacuum_rooms",
        "vacuum_rooms_dismiss",
        "vacuum_rooms_toggle",
        "vacuum_rooms_clean",
        -> vacuumActions.handle(verb, arg, target)
        "cameras_select" -> camerasActions.handle(arg, target)
        "security_section" -> securityActions.handle(arg, target)
        else -> TapOutcome.Ignored
    }
}
