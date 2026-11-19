@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.music

import tech.shaffersoftworks.remotescreens.server.render.DocContext

/** The component id for a native Music Assistant search overlay, placed at [boundsPx] in the document. */
internal fun DocContext.liveSearch(tileId: String, playerEntityId: String, boundsPx: SearchBoundsPx): Int {
    val componentId = LiveSearch.FIRST_COMPONENT_ID + searches.size

    searches += LiveSearch(
        tileId = tileId,
        playerEntityId = playerEntityId,
        componentId = componentId,
        xPx = boundsPx.x,
        yPx = boundsPx.y,
        widthPx = boundsPx.width,
        heightPx = boundsPx.height,
        page = renderingPage,
    )

    return componentId
}
