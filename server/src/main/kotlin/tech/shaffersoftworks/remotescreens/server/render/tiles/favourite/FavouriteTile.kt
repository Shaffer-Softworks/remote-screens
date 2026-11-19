@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles.favourite

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalWeight
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.Weight
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.shared.Tile

internal fun DocContext.favouriteTile(tile: Tile, box: TileBox, named: Boolean) {
    val ink = color(Vars.tile(tile.id, Vars.KEY_INK))
    val art = tile.options["image"]

    rcScope.Box(Modifier.fillMaxSize()) {
        val doc = this@favouriteTile

        if (art != null && box.w >= C.SHORT_HEIGHT_DP) {
            val w = doc.units.px(box.w).toInt(); val h = doc.units.px(box.h).toInt()

            doc.picture(
                PictureSlot(
                    name = "fav.${tile.id}",
                    source = art,
                    widthPx = w,
                    heightPx = h,
                    refreshSeconds = 0,
                ),
            )

            Image(
                image = doc.image(name = "fav.${tile.id}", width = w, height = h),
                modifier = Modifier.fillMaxSize(),
                contentScale = RcContentScale.Crop,
                alpha = C.NAV_ICON_SHARE,
            )
        }

        Column(Modifier.fillMaxSize().padding(doc.units.px(C.PAD_LG_DP))) {
            doc.icon(
                name = tile.icon ?: "playlist-music",
                sizeDp = C.LEVEL_BAR_HEIGHT_DP,
                color = doc.color(Vars.tile(tile.id, Vars.KEY_ICON)),
            )

            Spacer(Modifier.verticalWeight(1f))

            if (named) {
                doc.label(
                    text = tile.name ?: tile.options["title"].orEmpty(),
                    sp = Type.BODY + 1f,
                    color = ink,
                    weight = Weight.SEMI_BOLD,
                    lines = 2,
                )
            }
        }
    }
}
