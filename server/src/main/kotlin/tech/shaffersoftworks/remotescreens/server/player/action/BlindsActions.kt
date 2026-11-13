package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.blinds.BlindsUiStore
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository

/** The verbs of a full blinds page: the chosen room. Cover changes go through the catalogue. */
class BlindsActions(
    private val renderData: RenderDataRepository?,
) {
    fun handle(
        verb: String,
        arg: String,
        target: TapTarget,
    ): TapOutcome {
        val tileId = target.tile?.id
        val store = renderData?.blindsUi
        val isHandled = tileId != null && store != null && changePage(
            store = store,
            tileId = tileId,
            verb = verb,
            arg = arg,
        )
        return if (isHandled) {
            TapOutcome.Rebuild
        } else {
            TapOutcome.Ignored
        }
    }

    private fun changePage(
        store: BlindsUiStore,
        tileId: String,
        verb: String,
        arg: String,
    ): Boolean = when (verb) {
        "blinds_room" -> {
            store.selectRoom(tileId, arg)
            true
        }
        else -> false
    }
}
