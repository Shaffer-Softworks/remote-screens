package tech.shaffersoftworks.remotescreens.server.render.cards.map

/** An interactive map of [entityIds] over the component with [componentId]; [dark] matches the theme. */
data class LiveMap(val entityIds: List<String>, val componentId: Int, val dark: Boolean) {
    companion object {
        /** Next to the camera video ids, so map and video targets never share an id. */
        const val FIRST_COMPONENT_ID = 0x5D_0000
    }
}
