@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.tiles.bedside

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcColumnVerticalPositioning
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcRowHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.size
import androidx.compose.remote.creation.dsl.width
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.bindEntity
import tech.shaffersoftworks.remotescreens.server.render.bindTileColors
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.tapRef
import tech.shaffersoftworks.remotescreens.server.render.tiles.tileIcon
import tech.shaffersoftworks.remotescreens.server.render.widgets.numericLabel
import tech.shaffersoftworks.remotescreens.server.render.widgets.rounded
import tech.shaffersoftworks.remotescreens.server.render.widgets.spacer
import tech.shaffersoftworks.remotescreens.server.render.widgets.tap
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileChrome as C

private const val CLOCK_GLYPHS = 8

/** The bedside clock: the time as large as the glass allows and up to three round keys for the night. */
internal fun DocContext.bedsideTile(tile: Tile, box: TileBox) {
    val keys = tile.options["keys"].orEmpty().split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(C.BEDSIDE_KEYS_MAX)

    rcScope.Column(
        modifier = Modifier.fillMaxSize().padding(units.px(C.PAD_XXL_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcColumnVerticalPositioning.Center,
    ) {
        val doc = this@bedsideTile

        doc.numericLabel(
            text = doc.text(Vars.TIME),
            sp = minOf(box.w * C.BEDSIDE_TIME_W, box.h * C.BEDSIDE_TIME_H),
            color = doc.role(Role.INK_SOFT),
            weight = C.HEADER_TALL_MIN_DP,
            glyphs = CLOCK_GLYPHS,
        )

        doc.spacer(C.PAD_XXL_DP)
        doc.bedsideKeys(tile, keys)
    }
}

private fun DocContext.bedsideKeys(tile: Tile, keys: List<String>) {
    rcScope.Row(horizontal = RcRowHorizontalPositioning.Center) {
        val doc = this@bedsideKeys

        keys.forEachIndexed { index, entityId ->
            if (index > 0) {
                Spacer(Modifier.width(doc.units.px(C.LEVEL_BAR_HEIGHT_DP)))
            }

            doc.bedsideKey(tile = tile, entityId = entityId, index = index)
        }
    }
}

private fun DocContext.bedsideKey(tile: Tile, entityId: String, index: Int) {
    val spec = Catalogue.of(EntityId(entityId).domain.value)
    val keyTile = Tile(id = "${tile.id}.k$index", entityId = entityId)
    bindEntity(entityId)

    bindTileColors(
        tileId = keyTile.id,
        entityId = entityId,
        pastel = Pastel.forDomain(spec?.domain),
    )

    val ref = tapRef(
        tile = keyTile,
        action = TapAction(),
        spec = spec,
        pageIndex = { null },
        hold = false,
    )

    if (ref != null && spec?.predict?.containsKey(ref.verb) == true) {
        predict(ref, entityId)
    }

    rcScope.Box(
        modifier = tap(
            Modifier.size(units.px(C.BEDSIDE_KEY_DP), units.px(C.BEDSIDE_KEY_DP))
                .clip(rounded(C.BEDSIDE_KEY_DP / 2f))
                .background(color(Vars.tile(keyTile.id, Vars.KEY_BG))),
            ref,
        ),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        tileIcon(
            tile = keyTile,
            spec = spec,
            entityState = states[entityId],
            sizeDp = C.BEDSIDE_KEY_ICON_DP,
            colorKey = Vars.KEY_BG_INK,
        )
    }
}
