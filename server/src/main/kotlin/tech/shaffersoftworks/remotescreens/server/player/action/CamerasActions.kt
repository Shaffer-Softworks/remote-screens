package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.render.panels.cameras.cameraEntries

/** The verb of a full cameras page: choosing a camera from its list, as RPI's `CamerasViewModel.selectCamera`. */
class CamerasActions(
    private val renderData: RenderDataRepository?,
) {
    fun handle(arg: String, target: TapTarget): TapOutcome {
        val tile = target.tile
        val isListed = tile != null && tile.cameraEntries().any { it.entityId == arg }
        return if (tile == null || renderData == null || !isListed) {
            TapOutcome.Ignored
        } else {
            renderData.camerasUi.selectCamera(tile.id, arg)
            TapOutcome.Rebuild
        }
    }
}
