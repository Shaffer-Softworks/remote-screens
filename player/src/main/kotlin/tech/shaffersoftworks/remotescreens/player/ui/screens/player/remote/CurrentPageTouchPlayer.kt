@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.managers.StateLayout
import androidx.compose.remote.player.view.RemoteComposePlayer

/**
 * Sends a press only to the page on screen.
 *
 * `StateLayout` routes clicks to its current pane but leaves touch-down to `Component`, which walks
 * every pane, last first. A page shown earlier keeps its bounds while hidden, so its list or tap
 * takes the press and the visible list under the finger never scrolls. Hidden panes are 0×0 for
 * the length of the down dispatch only; layout and page transitions never see the change.
 */
internal class CurrentPageTouchPlayer(context: Context) : RemoteComposePlayer(context) {
    private var earlyClick: EarlyClick? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> armEarlyClick(event)
            MotionEvent.ACTION_MOVE -> earlyClick?.cancelIfMoved(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> earlyClick?.disarmForUp()
        }

        val root = if (action == MotionEvent.ACTION_DOWN) {
            runCatching { document.document.rootLayoutComponent }.getOrNull()
        } else {
            null
        }

        val collapsed = root?.let(HiddenPanes::collapse).orEmpty()

        return try {
            super.dispatchTouchEvent(event)
        } finally {
            HiddenPanes.restore(collapsed)

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                earlyClick?.restore()
                earlyClick = null
            }
        }
    }

    /**
     * The document runs a click on finger-up. A press here is held well past a frame, so the button
     * feels late the whole time the finger is down. Run that click on the next frame unless the
     * finger has already moved far enough that this is a drag.
     */
    private fun armEarlyClick(event: MotionEvent) {
        val hostOnScreen = IntArray(COORDINATE_PAIR)
        val innerOnScreen = IntArray(COORDINATE_PAIR)

        getLocationOnScreen(hostOnScreen)
        mInner.getLocationOnScreen(innerOnScreen)

        val playerX = event.x
        val playerY = event.y
        val documentX = playerX + hostOnScreen[0] - innerOnScreen[0]
        val documentY = playerY + hostOnScreen[1] - innerOnScreen[1]

        val armed = EarlyClick(
            downX = playerX,
            downY = playerY,
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop,
            onFire = {
                fireDocumentClick(
                    playerX = playerX,
                    playerY = playerY,
                    documentX = documentX,
                    documentY = documentY,
                )
            },
        )

        earlyClick = armed
        postOnAnimation(armed.runnable)
    }

    private fun fireDocumentClick(playerX: Float, playerY: Float, documentX: Float, documentY: Float) {
        val clicked = runCatching {
            document.document.onClick(mInner.remoteContext, documentX, documentY)
        }.getOrDefault(false)

        if (!clicked) return

        // The view repeats the click on finger-up when this stays false. The field is the view's.
        val moved = mInner.javaClass.getDeclaredField("mHasMoved")

        moved.isAccessible = true
        moved.setBoolean(mInner, true)

        clickTargetAt(x = playerX, y = playerY)?.let { child ->
            child.isClickable = false
            earlyClick?.held = child
        }
    }
}

private class EarlyClick(
    private val downX: Float,
    private val downY: Float,
    private val touchSlop: Int,
    private val onFire: () -> Unit,
) {
    private var isFired = false
    private var isCancelled = false

    var held: View? = null

    val runnable = Runnable { fire() }

    fun cancelIfMoved(event: MotionEvent) {
        if (isFired || isCancelled) return

        val dx = event.x - downX
        val dy = event.y - downY
        val slop = touchSlop.toFloat()

        if (dx * dx + dy * dy > slop * slop) cancel()
    }

    fun disarmForUp() {
        if (!isFired) cancel()
    }

    fun restore() {
        held?.isClickable = true
        held?.isPressed = false
        held = null
    }

    private fun cancel() {
        isCancelled = true
    }

    private fun fire() {
        if (isCancelled || isFired) return

        isFired = true
        onFire()
    }
}

/** The deepest clickable child under ([x], [y]), in this view's coordinates. */
private fun View.clickTargetAt(x: Float, y: Float): View? {
    if (this is ViewGroup) {
        childClickTarget(scrolledX = x + scrollX, scrolledY = y + scrollY)?.let { return it }
    }

    val isButton = isClickable && hasOnClickListeners() && width > 0 && height > 0

    return if (isButton) this else null
}

private fun ViewGroup.childClickTarget(scrolledX: Float, scrolledY: Float): View? {
    for (index in childCount - 1 downTo 0) {
        val hit = getChildAt(index).hitClickTarget(scrolledX = scrolledX, scrolledY = scrolledY) ?: continue

        return hit
    }

    return null
}

private fun View.hitClickTarget(scrolledX: Float, scrolledY: Float): View? {
    val localX = scrolledX - left
    val localY = scrolledY - top

    val isInside = visibility == View.VISIBLE &&
        localX >= 0f &&
        localY >= 0f &&
        localX < width &&
        localY < height

    return if (isInside) clickTargetAt(x = localX, y = localY) else null
}

private const val COORDINATE_PAIR = 2

internal object HiddenPanes {
    class Collapsed(val pane: Component, val width: Float, val height: Float)

    /** Zero-sizes each settled `StateLayout` pane that is not current. */
    fun collapse(root: Component): List<Collapsed> {
        val collapsed = mutableListOf<Collapsed>()

        fun descend(component: Component) {
            // The root keeps its children in the operation list; layouts list theirs after measure.
            val children = (component as? LayoutComponent)?.childrenComponents
                ?: component.mList.filterIsInstance<Component>()

            val state = component as? StateLayout
            var paneIndex = 0

            for (child in children) {
                val isPane = state != null && child is LayoutComponent
                val isHidden = isPane && !state.inTransition && paneIndex != state.currentLayoutIndex

                if (isPane) paneIndex++

                if (isHidden) {
                    collapsed += Collapsed(pane = child, width = child.width, height = child.height)
                    child.width = 0f
                    child.height = 0f
                } else {
                    descend(child)
                }
            }
        }

        descend(root)

        return collapsed
    }

    fun restore(collapsed: List<Collapsed>) {
        for (entry in collapsed) {
            entry.pane.width = entry.width
            entry.pane.height = entry.height
        }
    }
}
