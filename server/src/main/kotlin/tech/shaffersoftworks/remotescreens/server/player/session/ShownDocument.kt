package tech.shaffersoftworks.remotescreens.server.player.session

import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.shared.Slot
import tech.shaffersoftworks.remotescreens.shared.VarValue

/** A document shown in [slot], with what was last sent for it. */
internal class ShownDocument(
    val slot: Slot,
    val renderedDoc: RenderedDoc,
    val rebuild: suspend () -> RenderedDoc?,
) {
    var revision = 0
    var sentVars: Map<String, VarValue> = emptyMap()
    val pictureKeys = HashMap<String, String?>()
    val pictureSentAtMs = HashMap<String, Long>()
    /** Component id to how many times its video was started again after the player said it stopped. */
    val videoRestarts = HashMap<Int, Int>()
}
