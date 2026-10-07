package dev.remotescreens.player

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Turns a horizontal swipe across the pages into a page change, and lets everything else through to the document.
 * Any touch is reported first, for standby. The player view asks its parents not to intercept touches, so the swipe
 * is found while dispatching, and the document gets a cancel once it is one.
 */
class SwipeFrame(context: Context, private val touched: () -> Unit, private val swiped: (next: Boolean) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 3
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    var enabledSwipe = true

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touched()
                downX = e.x
                downY = e.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> if (!swiping && enabledSwipe && e.pointerCount == 1) {
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dx) > slop && abs(dx) > 2 * abs(dy)) {
                    swiping = true
                    val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
            }
        }
        if (!swiping) return super.dispatchTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val dx = e.x - downX
            if (abs(dx) > width / 8f) swiped(dx < 0)
            swiping = false
        } else if (e.actionMasked == MotionEvent.ACTION_CANCEL) {
            swiping = false
        }
        return true
    }
}
