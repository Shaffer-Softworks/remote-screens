package tech.shaffersoftworks.remotescreens.server.render.panels.intercom

/** The page's scale, and its size in dp. */
internal class IntercomLayout(val scale: Float, val pageW: Float, val pageH: Float) {
    fun dp(value: Float): Float = value * scale
    fun sp(value: Float): Float = maxOf(value * scale, IntercomMetrics.SMALL_LABEL_MIN_SP)
    val columnW: Float get() = dp(IntercomMetrics.COLUMN_W_DP)
}
