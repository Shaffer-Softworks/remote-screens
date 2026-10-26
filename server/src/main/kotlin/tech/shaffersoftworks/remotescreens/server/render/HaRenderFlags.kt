package tech.shaffersoftworks.remotescreens.server.render

/**
 * Home Assistant supported-feature bits and level-bar step counts shared by tiles and cards.
 * Tile and card step densities differ on purpose (preserve existing visuals).
 */
object HaRenderFlags {
    const val FAN_PERCENTAGE_FLAG = 1
    const val COVER_OPEN_FLAG = 1
    const val COVER_CLOSE_FLAG = 2
    const val COVER_POSITION_FLAG = 4
    const val COVER_OPEN_TILT_FLAG = 16
    const val COVER_CLOSE_TILT_FLAG = 32
    const val COVER_STOP_TILT_FLAG = 64
    const val COVER_TILT_POSITION_FLAG = 128

    /** Segment count for entity level bars on grid tiles. */
    const val TILE_LEVEL_STEPS = 10

    /** Segment count for level bars on detail cards. */
    const val CARD_LEVEL_STEPS = 11
}
