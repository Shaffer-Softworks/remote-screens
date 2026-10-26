package tech.shaffersoftworks.remotescreens.server.render.panels.intercom

/**
 * Layout of RPI's `IntercomScreen`, `StationView` and `ButtonViewV2`, in dp and sp.
 * The page uses those colours rather than the screen theme, so it stays the RPI intercom.
 */
internal object IntercomMetrics {
    const val BASE_W = 1280f
    const val BASE_H = 650f
    const val LAYOUT_SCALE_MAX = 1.5f
    const val SMALL_LABEL_MIN_SP = 12f

    /**
     * RPI draws the column at these dp values. A page that cannot hold the column and a named station
     * stacks the keys on top instead of shrinking them.
     */
    const val STATION_NAME_MIN_DP = 72f
    const val STACK_SCALE_MIN = 0.45f
    const val STACK_SCALE_MAX = 1f
    const val LABEL_OF_BUTTON = 0.4f
    const val UPDATE_STATUS_LINES = 3

    /** The grey column on the left. */
    const val COLUMN_W_DP = 350f
    const val COLUMN_TOP_DP = 28f
    const val ACCENT_START_DP = 20f
    const val ACCENT_W_DP = 120f
    const val ACCENT_H_DP = 10f
    const val TITLE_GAP_DP = 10f
    const val TITLE_START_DP = 20f
    const val TITLE_SP = 15f
    const val STATUS_SP = 20f
    const val BUTTON_TOP_DP = 10f
    const val BUTTON_W_DP = 150f
    const val BUTTON_H_DP = 50f
    const val BUTTON_GAP_DP = 10f
    const val BUTTON_CORNER_DP = 5f
    const val BUTTON_BORDER_DP = 1f
    const val BUTTON_ICON_DP = 32f
    const val BUTTON_SP = 14f
    const val ICON_TEXT_GAP_DP = 8f

    /** The station list, `StationView`. */
    const val LIST_START_DP = 15f
    const val LIST_TOP_DP = 28f
    const val LIST_END_DP = 15f
    const val LIST_BOTTOM_DP = 15f
    const val ROW_H_DP = 100f
    const val ROW_GAP_DP = 10f
    const val ROW_PAD_DP = 8f
    const val ROW_INSET_DP = 10f
    const val ROW_CORNER_DP = 8f
    const val ROW_BORDER_DP = 1f
    const val ROW_ICON_DP = 40f
    const val ROW_BAR_DP = 1f
    const val NAME_SP = 18f
    const val ROW_STATUS_SP = 14f
    const val CALL_H_DP = 60f

    /** The stacked header when the side column does not fit: the same pieces, in a band. */
    const val STACK_TOP_DP = 12f
    const val STACK_BOTTOM_DP = 12f
    const val STACK_SIDE_DP = 16f
    const val TITLE_LINE_DP = 24f
    const val STATUS_LINE_DP = 28f
    const val STACKED_WIDTH_DP = STACK_SIDE_DP * 2f + BUTTON_W_DP * 2f + BUTTON_GAP_DP
    const val LIST_MIN_W_DP =
        LIST_START_DP + LIST_END_DP + ROW_PAD_DP * 2f + ROW_INSET_DP * 3f + ROW_ICON_DP + BUTTON_W_DP

    /** Top padding, accent, title, one status line, and the two keys in the side column. */
    const val COLUMN_BODY_DP =
        COLUMN_TOP_DP + ACCENT_H_DP + TITLE_GAP_DP + TITLE_LINE_DP +
            STATUS_LINE_DP + BUTTON_TOP_DP + BUTTON_H_DP
}

/** Colours from RPI's theme and `ButtonViewV2`: page, column, card, accent, and the dark keys. */
internal object IntercomPaint {
    const val PAGE = 0xFF494949.toInt()
    const val COLUMN = 0xFF3B3B3B.toInt()
    const val CARD = 0xFF2F2E2E.toInt()
    const val ACCENT = 0xFF1DA48C.toInt()
    const val INK = 0xFFFFFFFF.toInt()
    const val BUTTON = 0xFF444444.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val MIC_LIVE = 0xFF00FF00.toInt()
    const val MIC_MUTED = 0xFFFF0000.toInt()
}
