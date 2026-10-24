package tech.shaffersoftworks.remotescreens.server.render.panels.alarm

/** Layout metrics of the alarm page, in dp and sp as RPI's `AlarmScreenV2` and `KeypadPanel` draw them. */
internal object AlarmMetrics {
    /** The size the RPI alarm page was drawn for; the page scales from it (same base as the thermostat). */
    const val BASE_W = 1280f
    const val BASE_H = 650f
    const val LAYOUT_SCALE_MIN = 0.4f
    const val LAYOUT_SCALE_MAX = 1.5f
    /** Floor for scaled words so they stay readable on 7" panels. */
    const val SMALL_LABEL_MIN_SP = 12f
    /** A line of text is about this much taller than its font size. */
    const val LINE_HEIGHT_RATIO = 1.3f

    const val PAGE_PAD_H_DP = 32f
    const val PAGE_PAD_V_DP = 24f
    const val PANEL_GAP_DP = 28f
    const val ALARM_PANEL_WEIGHT = 1.45f
    const val KEYPAD_PANEL_WEIGHT = 0.85f
    const val CARD_CORNER_DP = 24f

    const val ALARM_PAD_DP = 24f
    const val STATUS_CIRCLE_DP = 138f
    const val STATUS_RING_DP = 2f
    const val STATUS_ICON_DP = 74f
    const val STATUS_TEXT_GAP_DP = 28f
    const val STATUS_SP = 38f
    const val SECURE_GAP_DP = 6f
    const val SECURE_SP = 20f
    const val ACCENT_BAR_W_DP = 4f
    const val ACCENT_BAR_H_DP = 150f
    const val ACCENT_BAR_CORNER_DP = 8f
    const val DIVIDER_TOP_GAP_DP = 18f
    const val DIVIDER_DP = 1f
    const val DIVIDER_BOTTOM_GAP_DP = 14f
    const val SELECT_ZONE_SP = 18f
    const val SELECT_ZONE_GAP_DP = 12f

    const val CHIP_GAP_DP = 14f
    const val CHIP_W_DP = 160f
    const val CHIP_H_DP = 70f
    const val CHIP_CORNER_DP = 14f
    /** Material 3 draws an unselected filter chip's outline 1dp wide and a selected one's not at all. */
    const val CHIP_BORDER_DP = 1f
    const val CHIP_PAD_START_DP = 30f
    const val CHIP_PAD_END_DP = 8f
    const val CHIP_ICON_DP = 20f
    const val CHIP_ICON_GAP_DP = 8f
    const val CHIP_LABEL_SP = 16f

    const val ACTION_GAP_DP = 14f
    const val ACTION_H_DP = 78f
    const val ACTION_CORNER_DP = 16f
    const val ACTION_ICON_DP = 24f
    const val ACTION_ICON_GAP_DP = 10f
    const val ACTION_SP = 17f
    /** Material 3 `ButtonDefaults.ContentPadding` horizontal. */
    const val ACTION_PAD_H_DP = 24f

    const val KEYPAD_PAD_DP = 28f
    const val ENTER_CODE_SP = 22f
    const val ENTER_CODE_GAP_DP = 22f
    const val DOT_DP = 18f
    const val DOT_GAP_DP = 18f
    const val DOTS_GAP_DP = 28f
    const val KEY_ROW_GAP_DP = 10f
    const val KEY_COL_GAP_DP = 14f
    const val KEY_ASPECT = 1.18f
    const val KEY_CORNER_DP = 16f
    const val KEY_SP = 22f
    const val KEYPAD_COLS = 3
    const val KEYPAD_ROWS = 4

    /** Material 3 `AlertDialog`: 28dp corners, 24dp padding, RPI's 90dp icon. */
    const val DIALOG_W_DP = 420f
    const val DIALOG_CORNER_DP = 28f
    const val DIALOG_PAD_DP = 24f
    const val DIALOG_ICON_DP = 90f
    const val DIALOG_GAP_DP = 16f
    const val DIALOG_TITLE_SP = 24f
    const val DIALOG_TEXT_SP = 14f
    const val DIALOG_BUTTON_SP = 14f
    const val DIALOG_BUTTON_H_DP = 40f
    const val DIALOG_BUTTON_GAP_DP = 8f

    /** RPI's `orange` is its warn colour at this alpha. */
    const val ARMING_ALPHA = 0x9A
    const val ALPHA_SHIFT = 24
    const val RGB_MASK = 0xFFFFFF
}
