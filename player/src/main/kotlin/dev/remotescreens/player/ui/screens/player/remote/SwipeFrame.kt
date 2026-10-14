package dev.remotescreens.player.ui.screens.player.remote

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

private const val SWIPE_SLOP_MULTIPLIER = 3
private const val HORIZONTAL_DOMINANCE_RATIO = 2
private const val PAGE_CHANGE_WIDTH_FRACTION = 8f

/**
 * Turns a horizontal swipe across the pages into a page change, and lets everything else through to the document.
 * The player view asks its parents not to intercept touches, so the swipe is found while dispatching, and the
 * document gets a cancel once it is one.
 */
open class SwipeFrame(context: Context) : FrameLayout(context) {
    private val swipeSlop = ViewConfiguration.get(context).scaledTouchSlop * SWIPE_SLOP_MULTIPLIER
    private var downX = 0f
    private var downY = 0f
    private var isSwiping = false

    var isSwipeEnabled = true

    /** Called with `true` for a swipe to the next page, `false` for the previous one. */
    var onSwipe: (isNext: Boolean) -> Unit = {}

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isSwiping = false
            }
            MotionEvent.ACTION_MOVE -> if (!isSwiping && isSwipeEnabled && event.pointerCount == 1) {
                startSwipeIfHorizontal(event)
            }
        }
        if (!isSwiping) return super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - downX
                if (abs(deltaX) > width / PAGE_CHANGE_WIDTH_FRACTION) onSwipe(deltaX < 0)
                isSwiping = false
            }
            MotionEvent.ACTION_CANCEL -> isSwiping = false
        }
        return true
    }

    private fun startSwipeIfHorizontal(event: MotionEvent) {
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        if (abs(deltaX) > swipeSlop && abs(deltaX) > HORIZONTAL_DOMINANCE_RATIO * abs(deltaY)) {
            isSwiping = true
            val cancelEvent = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancelEvent)
            cancelEvent.recycle()
        }
    }
}
