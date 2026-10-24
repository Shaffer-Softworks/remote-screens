@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render.panels.thermostat

/** Layout and drawing metrics for the thermostat panel (RPI dial proportions). */
internal object ThermostatMetrics {
    const val DIAL_HEIGHT_SHARE = 0.55f
    /** Inner fill alpha at the dial centre (RPI `primaryColor.copy(0.45f)`). */
    const val DIAL_INNER_ALPHA = 0.45f
    /** Outer fill alpha at the dial rim (RPI `secondaryColor.copy(0.15f)`). */
    const val DIAL_OUTER_ALPHA = 0.15f
    /** Tick marks outside the ring (RPI unlit tick alpha). */
    const val DIAL_TICK_ALPHA = 0.3f
    /** Accent bar width on the RPI page (`HorizontalDivider` 120.dp). */
    const val ACCENT_BAR_WIDTH_DP = 120f
    /** Dial diameter the RPI `CustomCircularProgressIndicator` is drawn at (500.dp). */
    const val DIAL_BASE_DP = 500f
    const val ACCENT_BAR_HEIGHT_DP = 10f
    /** Knob radius as a share of the dial radius (RPI 20f on a 250f radius). */
    const val KNOB_RADIUS_SHARE = 20f / 250f
    /** Knob ring stroke as a share of the dial radius (RPI 5f on a 250f radius). */
    const val KNOB_RING_SHARE = 5f / 250f
    /** Ring thickness as a share of the dial diameter (RPI `width / 25`). */
    const val RING_THICKNESS_SHARE = 1f / 25f
    const val ADJUST_CAPTION_SP = 15f
    const val ACTION_LABEL_SP = 20f
    const val TARGET_LABEL_SP = 38f
    const val PANEL_PAD_DP = 20f
    const val MODE_ICON_DP = 22f
    const val MODE_LABEL_SP = 20f
    const val MODE_ICON_GAP_DP = 8f
    const val DIAL_SIDE_GAP_DP = 24f
    const val READING_CAPTION_SP = 25f
    const val READING_GAP_DP = 20f
    const val TOP_PAD_DP = 28f
    const val HEADER_GAP_DP = 10f
    const val MODE_ROW_MARGIN_DP = 40f
    const val MODE_ROW_GAP_DP = 12f
    const val SEG_HEIGHT_DP = 48f
    const val SEG_WIDTH_DP = 160f
    const val MODE_CORNER_DP = 10f
    const val MODE_BORDER_DP = 2f
    const val FAN_BUTTON_WIDTH_DP = 150f
    const val FAN_CAPTION_SP = 12f
    const val FAN_VALUE_SP = 16f
    const val FAN_ICON_DP = 28f
    const val FAN_PAD_DP = 12f
    const val READING_VALUE_SP = 70f
    /** +/− key size; Remote Compose cannot drag the dial as the RPI page does. */
    const val ADJUST_KEY_DP = 64f
    /** RPI tick gap outside the ring (`gap = 15f` on a 500.dp dial). */
    const val TICK_GAP_SHARE = 15f / 500f
    /** Both stops of the dial fill gradient are remote colour ids. */
    const val DIAL_GRADIENT_COLOR_MASK = 0b11
    const val OPAQUE_ALPHA = 0xFF
    const val ALPHA_SHIFT = 24
    const val RGB_MASK = 0xFFFFFF

    /** The size the RPI thermostat page was drawn for; the page scales from it. */
    const val BASE_W = 1280f
    const val BASE_H = 650f

    /** Tick marks around the dial (RPI default `maxValue - minValue` with 0…100). */
    const val TICKS = 100
    /** Floor for scaled captions so they stay readable on 7" panels. */
    const val SMALL_LABEL_MIN_SP = 12f
    const val ARC_START_DEG = 90f
    const val FULL_CIRCLE_DEG = 360.0
    const val DEFAULT_MIN_TEMP_C = 7.0
    const val DEFAULT_MAX_TEMP_C = 35.0
    const val TEMP_DECIMALS_F = 0
    const val TEMP_DECIMALS_C = 1
    const val TEMP_STEP_F = 1.0
    const val TEMP_STEP_C = 0.5
    const val LAYOUT_SCALE_MIN = 0.4f
    const val LAYOUT_SCALE_MAX = 1.5f
    /** Home Assistant `ClimateEntityFeature.TARGET_TEMPERATURE_RANGE`. */
    const val CLIMATE_TEMP_RANGE_FLAG = 2
    /** Gap in degrees between the heat and cool arcs on a dual dial. */
    const val DUAL_ARC_GAP_DEG = 8f
    const val DUAL_TEMP_GAP_DP = 16f
    const val KEY_STACK_GAP_DP = 10f

}
