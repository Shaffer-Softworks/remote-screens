package tech.shaffersoftworks.remotescreens.server.render.tiles

import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

class TileBoxTest {
    @Test
    fun anActionGestureIsInTheRef() {
        val tile = Tile(
            id = "t",
            entityId = "script.good_night",
            tap = TapAction(TapType.ACTION, service = "script.turn_on"),
            hold = TapAction(TapType.ACTION, service = "light.turn_off"),
        )
        assertEquals(
            ActionRef("t", ActionRef.VERB_RUN, ActionRef.ARG_TAP),
            tapRef(tile, tile.tap, spec = null, pageIndex = { null }, hold = false),
        )
        assertEquals(
            ActionRef("t", ActionRef.VERB_RUN, ActionRef.ARG_HOLD),
            tapRef(tile, tile.hold, spec = null, pageIndex = { null }, hold = true),
        )
    }
}
