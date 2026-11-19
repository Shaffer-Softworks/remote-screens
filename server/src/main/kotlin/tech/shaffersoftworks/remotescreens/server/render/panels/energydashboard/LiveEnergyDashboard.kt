package tech.shaffersoftworks.remotescreens.server.render.panels.energydashboard

/** A native Energy dashboard for [tileId] on document [page] over [componentId]. */
data class LiveEnergyDashboard(
    val tileId: String,
    val page: Int,
    val componentId: Int,
) {
    companion object {
        /** Next to the music-search ids. */
        const val FIRST_COMPONENT_ID = 0x5F_0000
    }
}
