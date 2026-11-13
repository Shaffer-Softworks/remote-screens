package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository

/** Page-chrome verbs that only change session UI: blinds room, vacuum and camera selection. */
class PanelUiActions(
    renderData: RenderDataRepository?,
) {
    private val blindsActions = BlindsActions(renderData)
    private val vacuumActions = VacuumActions(renderData)
    private val camerasActions = CamerasActions(renderData)
    private val securityActions = SecurityActions(renderData)

    fun handle(verb: String, arg: String, target: TapTarget): TapOutcome = when (verb) {
        "blinds_room" -> blindsActions.handle(verb, arg, target)
        "vacuum_select" -> vacuumActions.handle(arg, target)
        "cameras_select" -> camerasActions.handle(arg, target)
        "security_section" -> securityActions.handle(arg, target)
        else -> TapOutcome.Ignored
    }
}
