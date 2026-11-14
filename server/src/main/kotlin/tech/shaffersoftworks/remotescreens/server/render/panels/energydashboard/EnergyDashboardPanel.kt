@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.energydashboard

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.LiveEnergyDashboard
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.shared.Tile

/**
 * Thin Remote Compose stub for the Energy dashboard page. Charts are drawn natively on capable players
 * over [LiveEnergyDashboard.componentId]; older players see the update prompt.
 */
@Suppress("UnusedParameter")
fun DocContext.energyDashboardPanel(tile: Tile, box: TileBox) {
    val componentId = LiveEnergyDashboard.FIRST_COMPONENT_ID + energyDashboards.size
    energyDashboards += LiveEnergyDashboard(
        tileId = tile.id,
        page = renderingPage,
        componentId = componentId,
    )
    scope.Box(
        Modifier
            .fillMaxSize()
            .componentId(componentId)
            .background(role(Role.PAGE))
            .padding(units.px(EnergyDashboardMetrics.STUB_PAD_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        // Capable players cover this hole natively; the update prompt only matters for older APKs.
        // A blank PAGE-coloured hole also hides a one-frame swap when the overlay is briefly under RC.
        if (!supportsEnergyDashboard) {
            label(
                text = "Update the tablet app for the Energy dashboard",
                sp = Type.BODY,
                color = role(Role.INK_DIM),
            )
        }
    }
}
