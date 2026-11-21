package tech.shaffersoftworks.remotescreens.server.render.panels.assist

/** Assist page registered while a document is drawn. The conversation itself is native. */
data class LiveAssist(
    val tileId: String,
    val page: Int,
    val componentId: Int,
) {
    companion object {
        const val FIRST_COMPONENT_ID = 0x62_0000
    }
}
