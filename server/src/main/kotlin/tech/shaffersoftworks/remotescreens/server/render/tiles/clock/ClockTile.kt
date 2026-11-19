@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles.clock

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcTextAlign
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.server.render.widgets.numericLabel
import tech.shaffersoftworks.remotescreens.shared.Tile

private const val CLOCK_GLYPHS = 8

internal fun DocContext.clockTile(tile: Tile, box: TileBox) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val ink2 = color(Vars.tile(tile.id, Vars.KEY_INK2))
    val big = minOf(box.h * C.CLOCK_H_SHARE, box.w * C.CLOCK_W_SHARE).coerceAtLeast(Type.HEADLINE)

    rcScope.Column(
        modifier = Modifier.fillMaxSize().padding(units.px(C.PAD_LG_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        this@clockTile.numericLabel(
            text = this@clockTile.text(Vars.TIME),
            sp = big,
            color = ink,
            weight = Weight.LIGHT,
            glyphs = CLOCK_GLYPHS,
        )

        this@clockTile.label(
            text = this@clockTile.text(Vars.DATE),
            sp = Type.BODY,
            color = ink2,
            align = RcTextAlign.Center,
            lines = 2,
        )
    }
}
