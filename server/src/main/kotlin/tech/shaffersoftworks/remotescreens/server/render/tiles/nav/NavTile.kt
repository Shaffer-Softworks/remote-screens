@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles.nav

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.shared.Tile

internal fun DocContext.navTile(tile: Tile, box: TileBox, named: Boolean) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))

    rcScope.Column(
        modifier = Modifier.fillMaxSize().padding(units.px(C.PAD_XL_DP)),
        vertical = if (named) RcColumnVerticalPositioning.Top else RcColumnVerticalPositioning.Center,
        horizontal = if (named) RcHorizontalPositioning.Start else RcHorizontalPositioning.Center,
    ) {
        this@navTile.icon(
            name = tile.icon ?: "arrow-right-circle",
            sizeDp = if (named) C.NAV_ICON_NAMED_DP else minOf(C.NAV_ICON_MAX_DP, box.h * C.NAV_ICON_SHARE),
            color = color(Vars.tile(tile.id, Vars.KEY_ICON)),
        )

        if (named) {
            Spacer(Modifier.verticalWeight(1f))

            this@navTile.label(
                text = tile.name.orEmpty(),
                sp = Type.TITLE,
                color = ink,
                weight = Weight.SEMI_BOLD,
                lines = 2,
            )
        }
    }
}
