@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.assist

import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.componentId
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import tech.shaffersoftworks.remotescreens.server.render.DocContext
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.tiles.TileBox
import tech.shaffersoftworks.remotescreens.server.render.widgets.Type
import tech.shaffersoftworks.remotescreens.server.render.widgets.label
import tech.shaffersoftworks.remotescreens.shared.Tile

private const val STUB_PAD_DP = 24

/**
 * Remote Compose hole for the Assist page. Capable players paint the conversation over [componentId].
 * Older players see the update prompt.
 */
@Suppress("UnusedParameter")
fun DocContext.assistPanel(tile: Tile, box: TileBox) {
    val componentId = LiveAssist.FIRST_COMPONENT_ID + assistPages.size

    assistPages += LiveAssist(
        tileId = tile.id,
        page = renderingPage,
        componentId = componentId,
    )

    rcScope.Box(
        modifier = Modifier
            .fillMaxSize()
            .componentId(componentId)
            .background(role(Role.PAGE))
            .padding(units.px(STUB_PAD_DP)),
        horizontal = RcHorizontalPositioning.Center,
        vertical = RcVerticalPositioning.Center,
    ) {
        if (!supportsAssist) {
            label(
                text = "Update the tablet app for Assist",
                sp = Type.BODY,
                color = role(Role.INK_DIM),
            )
        }
    }
}
