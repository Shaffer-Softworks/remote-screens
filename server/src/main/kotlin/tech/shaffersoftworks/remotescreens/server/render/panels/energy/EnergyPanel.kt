@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.energy

import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.tiles.energyFlow
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import tech.shaffersoftworks.remotescreens.shared.Tile

/**
 * Full-page energy flow: solar, grid and battery around the house, from Home Assistant's energy
 * preferences. The same diagram as the 2×2 energy tile, drawn edge to edge without card chrome.
 */
fun DocContext.energyPanel(@Suppress("UNUSED_PARAMETER") tile: Tile, box: TileBox) {
    scope.Box(Modifier.fillMaxSize().background(role(Role.PAGE))) {
        val doc = this@energyPanel
        doc.energyFlow(
            wDp = box.w,
            hDp = box.h,
            compact = false,
        )
    }
}
