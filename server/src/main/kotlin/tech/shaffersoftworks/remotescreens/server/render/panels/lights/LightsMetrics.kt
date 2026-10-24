package tech.shaffersoftworks.remotescreens.server.render.panels.lights

/** Layout metrics of the lights page, in dp and sp as RPI's `LightsScreen` and its row and dialog views draw them. */
internal object LightsMetrics {
    /** The size RPI's pages were drawn for; the page scales from it (same base as the alarm page). */
    const val BASE_W = 1280f
    const val BASE_H = 650f
    const val LAYOUT_SCALE_MIN = 0.4f
    const val LAYOUT_SCALE_MAX = 1.5f
    /** Floor for scaled words so they stay readable on 7" panels. */
    const val SMALL_LABEL_MIN_SP = 12f
    /** A line of text is about this much taller than its font size. */
    const val LINE_HEIGHT_RATIO = 1.3f

    const val ROOMS_W_DP = 350f
    const val COLUMN_TOP_DP = 28f
    /** `HorizontalDivider(Modifier.width(120.dp).padding(start = 20.dp), thickness = 10.dp)`. */
    const val ACCENT_BAR_START_DP = 20f
    const val ACCENT_BAR_W_DP = 120f
    const val ACCENT_BAR_H_DP = 10f
    const val TITLE_GAP_DP = 10f
    const val TITLE_START_DP = 20f
    const val TITLE_SP = 15f
    const val SPINNER_DP = 80f
    const val SPINNER_STROKE_DP = 3f
    const val SPINNER_SWEEP_DEG = 270f
    const val SPINNER_START_DEG = -90f
    const val SPINNER_SCRIM_ALPHA = 0x80

    const val ROOM_H_DP = 100f
    const val ROOM_GAP_DP = 3f
    const val ROOM_PAD_START_DP = 20f
    const val ROOM_PAD_END_DP = 10f
    const val ROOM_SP = 30f
    const val ROOM_SUB_GAP_DP = 5f
    const val ROOM_SUB_SP = 15f

    const val DEVICES_PAD_DP = 15f
    const val ROOM_TITLE_START_DP = 8f
    const val ROOM_TITLE_SP = 20f
    const val DEVICE_GAP_DP = 10f
    const val DIVIDER_DP = 1f
    const val PROMPT_ICON_DP = 64f
    const val PROMPT_GAP_DP = 16f
    const val PROMPT_SP = 20f

    const val ROW_BOTTOM_DP = 10f
    const val ROW_PAD_H_DP = 10f
    const val ROW_HEADER_TOP_DP = 18f
    /** Iconics draws an icon at 24dp unless told otherwise. */
    const val ROW_ICON_DP = 24f
    const val ROW_ICON_GAP_DP = 10f
    /** Material 3 `bodyLarge`, the default for RPI's row title. */
    const val ROW_TITLE_SP = 16f
    const val SLIDER_TOP_DP = 18f
    const val SLIDER_END_DP = 80f
    const val SWITCH_TOP_DP = 10f

    /** Material 3 slider: a 44dp tall control, a 16dp track, and a 4dp thumb with a 6dp gap each side. */
    const val SLIDER_H_DP = 44f
    const val SLIDER_TRACK_DP = 16f
    const val SLIDER_THUMB_W_DP = 4f
    const val SLIDER_THUMB_GAP_DP = 6f
    const val SLIDER_INNER_CORNER_DP = 2f
    const val SLIDER_STOP_DP = 4f
    const val BRIGHTNESS_STEPS = 20
    /** RPI's tilt slider has `steps = 20`: 21 intervals from 0 to 100. */
    const val TILT_STEPS = 21
    const val PERCENT = 100

    /** Material 3 switch: a 52 by 32 track in a 48dp touch target; a 24dp thumb when on, 16dp with a 2dp edge off. */
    const val SWITCH_W_DP = 52f
    const val SWITCH_H_DP = 32f
    const val SWITCH_TOUCH_H_DP = 48f
    const val SWITCH_THUMB_ON_DP = 24f
    const val SWITCH_THUMB_OFF_DP = 16f
    const val SWITCH_BORDER_DP = 2f

    const val BRIGHTNESS_MAX = 255.0
}

/** Metrics of RPI's `LightControlDetailCard` dialog. */
internal object LightsDialogMetrics {
    /** RPI's dialog previews are 420dp wide; the platform dialog keeps that width on a wall panel. */
    const val CARD_W_DP = 420f
    const val DIALOG_PAD_H_DP = 20f
    const val DIALOG_PAD_V_DP = 24f
    const val CARD_CORNER_DP = 28f
    const val CARD_PAD_H_DP = 16f
    const val CARD_PAD_V_DP = 12f

    const val CLOSE_DP = 40f
    const val CLOSE_ICON_DP = 22f
    const val HEADER_TEXT_PAD_DP = 4f
    const val ROOM_SP = 13f
    const val DEVICE_SP = 17f

    const val HEADLINE_GAP_DP = 20f
    const val HEADLINE_SP = 44f
    const val UPDATED_TOP_DP = 4f
    const val UPDATED_SP = 14f
    const val CONTROL_GAP_DP = 24f
    const val CONTROL_H_DP = 280f
    const val SLIDER_W_DP = 72f
    const val SLIDER_CORNER_DP = 36f
    const val SLIDER_LINE_DP = 2f
    const val SLIDER_STEPS = 20
    const val DARK_LUMA = 0.08f
    const val DARK_LIFT = 0.25f
    const val LUMA_R = 0.2126f
    const val LUMA_G = 0.7152f
    const val LUMA_B = 0.0722f

    const val WHEEL_PAD_H_DP = 8f
    const val WHEEL_CELLS = 11
    const val WHEEL_RING_DP = 10f
    const val WHEEL_RING_STROKE_DP = 3f
    const val WHEEL_RING_EDGE_DP = 1f
    const val WHEEL_RING_EDGE_ALPHA = 0x66
    const val EYEDROPPER_DP = 44f
    const val EYEDROPPER_ICON_DP = 22f

    const val MODE_BAR_GAP_DP = 28f
    const val MODE_BAR_CORNER_DP = 28f
    const val MODE_BAR_PAD_DP = 8f
    const val MODE_BUTTON_DP = 48f
    const val MODE_BORDER_DP = 3f
    const val MODE_ICON_DP = 22f
    const val MODE_SUN_PLATE_DP = 26f
    const val MODE_SUN_DP = 16f
    const val MODE_CHIP_DP = 28f

    const val PALETTE_GAP_DP = 20f
    const val PALETTE_COLUMNS = 4
    const val PALETTE_ROW_GAP_DP = 12f
    const val SWATCH_DP = 44f
    const val SWATCH_BORDER_DP = 2f

    const val EFFECT_GAP_DP = 20f
    const val EFFECT_H_DP = 48f
    const val EFFECT_CORNER_DP = 14f
    const val EFFECT_ICON_DP = 20f
    const val EFFECT_ICON_GAP_DP = 8f
    const val EFFECT_SP = 16f
    const val MENU_MAX_H_DP = 272f
    const val MENU_ITEM_H_DP = 48f
    const val MENU_ITEM_PAD_H_DP = 12f
    const val MENU_DOT_DP = 6f
    const val MENU_DOT_GAP_DP = 10f
    const val MENU_SP = 15f
    const val MENU_CORNER_DP = 4f

    const val MIN_KELVIN = 2000
    const val MAX_KELVIN = 6500
    const val HUE_DEGREES = 360f
}
