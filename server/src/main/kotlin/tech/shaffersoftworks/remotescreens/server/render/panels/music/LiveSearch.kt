package tech.shaffersoftworks.remotescreens.server.render.panels.music

/** Native Music Assistant search over [componentId] for [tileId] / [playerEntityId]. */
data class LiveSearch(
    val tileId: String,
    val playerEntityId: String,
    val componentId: Int,
    val xPx: Int = 0,
    val yPx: Int = 0,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    /** Document page this field belongs to. The player hides it on every other page. */
    val page: Int = 0,
) {
    companion object {
        /** Next to the map ids, so search, map, and video targets never share an id. */
        const val FIRST_COMPONENT_ID = 0x5E_0000
    }
}

/** Document-pixel rectangle for the native search field. */
data class SearchBoundsPx(val x: Int, val y: Int, val width: Int, val height: Int)
