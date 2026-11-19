package tech.shaffersoftworks.remotescreens.server.render.cards.camera

/**
 * A camera's live video over the component with [componentId]; [crop] when the feed fills its box rather than fits.
 * [page] is the page being drawn, so a page's own video stops when the tablet leaves it.
 */
data class LiveVideo(val entityId: String, val componentId: Int, val crop: Boolean, val page: Int = 0) {
    companion object {
        /** Far above the ids the player gives components itself, so the video target is the only one with it. */
        const val FIRST_COMPONENT_ID = 0x5C_0000
    }
}
