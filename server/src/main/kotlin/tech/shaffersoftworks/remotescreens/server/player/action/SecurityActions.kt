package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.data.security.SecuritySection

/** The verb of a full security page: choosing Overview, Locks or Doors & windows. */
class SecurityActions(
    private val renderData: RenderDataRepository?,
) {
    fun handle(arg: String, target: TapTarget): TapOutcome {
        val tileId = target.tile?.id
        val section = SecuritySection.of(arg)
        return if (tileId == null || renderData == null || section == null) {
            TapOutcome.Ignored
        } else {
            renderData.securityUi.selectSection(tileId, section)
            TapOutcome.Rebuild
        }
    }
}
