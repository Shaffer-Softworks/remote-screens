package tech.shaffersoftworks.remotescreens.server.render.panels.intercom

/**
 * The page's scale, and its size in dp.
 *
 * RPI's `IntercomScreen` keeps a 350dp column at full size. A page that cannot hold that column and a
 * station name stacks the column as a header, which is the keypad page.
 */
internal class IntercomLayout private constructor(
    val scale: Float,
    val pageW: Float,
    val pageH: Float,
    val isStacked: Boolean,
    val headerH: Float,
) {
    fun dp(value: Float): Float = value * scale

    /** Captions stay at 12sp while the key is tall enough to hold them, and shrink with the key when it is not. */
    fun sp(value: Float): Float {
        val scaled = value * scale
        val fitted = minOf(
            IntercomMetrics.SMALL_LABEL_MIN_SP,
            dp(IntercomMetrics.BUTTON_H_DP) * IntercomMetrics.LABEL_OF_BUTTON,
        )
        return maxOf(scaled, fitted)
    }

    val columnW: Float
        get() = if (isStacked) pageW else dp(IntercomMetrics.COLUMN_W_DP)

    val listH: Float
        get() = (pageH - headerH).coerceAtLeast(0f)

    /** Mute and Hang Up, or Call. Never wider than the page. */
    val keyW: Float
        get() {
            val designed = dp(IntercomMetrics.BUTTON_W_DP)
            val side = dp(IntercomMetrics.STACK_SIDE_DP)
            val gap = dp(IntercomMetrics.BUTTON_GAP_DP)
            val room = (pageW - side * 2f - gap) / 2f
            return if (isStacked) minOf(designed, room) else designed
        }

    companion object {
        fun of(pageW: Float, pageH: Float, statusLines: Int): IntercomLayout {
            val stacked = !sideBySide(pageW, pageH, statusLines)
            val headerBody = headerBody(statusLines)
            val scale = if (stacked) stackScale(pageW, pageH, headerBody) else sideScale(pageW, pageH)
            val headerH = if (stacked) (headerBody * scale).coerceAtMost(pageH) else 0f
            return IntercomLayout(scale, pageW, pageH, stacked, headerH)
        }

        private fun sideBySide(pageW: Float, pageH: Float, statusLines: Int): Boolean {
            val scale = sideScale(pageW, pageH)
            val column = IntercomMetrics.COLUMN_W_DP * scale
            val keys = (IntercomMetrics.BUTTON_W_DP * 2f + IntercomMetrics.BUTTON_GAP_DP) * scale
            val listMin = (IntercomMetrics.LIST_MIN_W_DP + IntercomMetrics.STATION_NAME_MIN_DP) * scale
            val extraLines = (statusLines - 1).coerceAtLeast(0)
            val minH = (IntercomMetrics.COLUMN_BODY_DP + IntercomMetrics.STATUS_LINE_DP * extraLines) * scale
            return pageW - column >= listMin && column >= keys && pageH >= minH
        }

        /** RPI does not shrink the column. Scale up only when the page is larger than 1280×650. */
        private fun sideScale(pageW: Float, pageH: Float): Float =
            minOf(pageW / IntercomMetrics.BASE_W, pageH / IntercomMetrics.BASE_H)
                .coerceIn(1f, IntercomMetrics.LAYOUT_SCALE_MAX)

        private fun stackScale(pageW: Float, pageH: Float, header: Float): Float {
            val widthScale = pageW / IntercomMetrics.STACKED_WIDTH_DP
            val heightScale = pageH / (header + IntercomMetrics.ROW_H_DP)
            val raw = minOf(widthScale, heightScale).coerceIn(0f, IntercomMetrics.STACK_SCALE_MAX)
            val raised = raw.coerceAtLeast(IntercomMetrics.STACK_SCALE_MIN)
            val fits = IntercomMetrics.STACKED_WIDTH_DP * raised <= pageW &&
                (header + IntercomMetrics.ROW_H_DP) * raised <= pageH
            return if (fits) raised else raw
        }

        private fun headerBody(statusLines: Int): Float =
            IntercomMetrics.STACK_TOP_DP + IntercomMetrics.ACCENT_H_DP + IntercomMetrics.TITLE_GAP_DP +
                IntercomMetrics.TITLE_LINE_DP + IntercomMetrics.STATUS_LINE_DP * statusLines +
                IntercomMetrics.BUTTON_TOP_DP + IntercomMetrics.BUTTON_H_DP + IntercomMetrics.STACK_BOTTOM_DP
    }
}
