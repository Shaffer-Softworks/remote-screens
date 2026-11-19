package tech.shaffersoftworks.remotescreens.shared

/**
 * Tile kind the layout and the renderer agree on. [wire] is the `kind` stored on a tile and the
 * catalogue key for tiles that are not an entity domain (`clock`, `lights`, `home`).
 * A kind that is only an entity domain, such as `light`, is not listed here.
 */
enum class TileKind(val wire: String) {
    ALARM_PANEL("alarm_control_panel"),
    BEDSIDE("bedside"),
    BLINDS("blinds"),
    BUTTON("button"),
    CALENDAR("calendar"),
    CAMERA("camera"),
    CAMERAS("cameras"),
    CLIMATE("climate"),
    CLOCK("clock"),
    DEVICE_TRACKER("device_tracker"),
    ENERGY("energy"),
    ENERGY_DASHBOARD("energy_dashboard"),
    FAVOURITE("favourite"),
    HISTORY("history"),
    HOME("home"),
    INPUT_BUTTON("input_button"),
    INTERCOM("intercom"),
    LIGHTS("lights"),
    MAP("map"),
    MEDIA_PLAYER("media_player"),
    MUSIC("music"),
    NAV("nav"),
    PERSON("person"),
    SCENE("scene"),
    SCRIPT("script"),
    SECURITY("security"),
    SENSOR("sensor"),
    VACUUM("vacuum"),
    WEATHER("weather"),
    ;

    companion object {
        private val byWire: Map<String, TileKind> = entries.associateBy { it.wire }

        /** Pages that fill the screen. */
        val EDGE_PAGES: Set<TileKind> = setOf(
            HOME,
            WEATHER,
            CLIMATE,
            MUSIC,
            CALENDAR,
            ALARM_PANEL,
            LIGHTS,
            BLINDS,
            VACUUM,
            SECURITY,
            CAMERAS,
            INTERCOM,
            ENERGY,
            ENERGY_DASHBOARD,
        )

        fun fromWire(wire: String?): TileKind? = byWire[wire]
    }
}
