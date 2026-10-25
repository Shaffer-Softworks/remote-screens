package tech.shaffersoftworks.remotescreens.server.render.panels.cameras

/** Layout metrics of the cameras page, in dp and sp as RPI's `CamerasScreen` and `CameraListItemView` draw them. */
internal object CamerasMetrics {
    /** The size RPI's pages were drawn for; the page scales from it (same base as the alarm and lights pages). */
    const val BASE_W = 1280f
    const val BASE_H = 650f
    const val LAYOUT_SCALE_MIN = 0.4f
    const val LAYOUT_SCALE_MAX = 1.5f

    /** Floor for scaled words so they stay readable on 7" panels. */
    const val SMALL_LABEL_MIN_SP = 12f

    /** A line of text is about this much taller than its font size. */
    const val LINE_HEIGHT_RATIO = 1.3f

    /** `Modifier.width(350.dp).padding(top = 28.dp)` of the grey list column. */
    const val LIST_W_DP = 350f
    const val LIST_TOP_DP = 28f

    /** `HorizontalDivider(Modifier.width(120.dp).padding(start = 20.dp), thickness = 10.dp)`. */
    const val ACCENT_BAR_START_DP = 20f
    const val ACCENT_BAR_W_DP = 120f
    const val ACCENT_BAR_H_DP = 10f
    const val TITLE_GAP_DP = 10f
    const val TITLE_START_DP = 20f
    const val TITLE_SP = 15f

    /** `LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp))`. */
    const val LIST_PAD_DP = 20f
    const val ITEM_GAP_DP = 20f

    /** `CameraListItemView`: a 180dp row, its still `size(290.dp)`, and a 40dp teal bar at half alpha. */
    const val ITEM_H_DP = 180f
    const val ITEM_IMAGE_W_DP = 290f
    const val ITEM_BAR_H_DP = 40f
    const val ITEM_BAR_ALPHA = 0x80
    const val ITEM_LABEL_START_DP = 5f
    const val ITEM_LABEL_SP = 12f

    /** The right pane: `padding(28.dp)`, the camera's name at `padding(start = 20.dp)`, a 10dp gap, then video. */
    const val DETAIL_PAD_DP = 28f
    const val DETAIL_NAME_START_DP = 20f
    const val DETAIL_NAME_SP = 15f
    const val DETAIL_GAP_DP = 10f

    /** The spinner shown over the video until its first frame. */
    const val SPINNER_DP = 48f
}
