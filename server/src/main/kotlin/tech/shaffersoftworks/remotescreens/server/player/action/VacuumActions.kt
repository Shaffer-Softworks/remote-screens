package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository

/** The verb of a full vacuum page: choosing a vacuum from its list. */
class VacuumActions(
    private val renderData: RenderDataRepository?,
) {
    fun handle(arg: String, target: TapTarget): TapOutcome {
        val tileId = target.tile?.id
        val isVacuum = arg.startsWith("vacuum.")
        return if (tileId == null || renderData == null || !isVacuum) {
            TapOutcome.Ignored
        } else {
            renderData.vacuumUi.selectVacuum(tileId, arg)
            TapOutcome.Rebuild
        }
    }
}
