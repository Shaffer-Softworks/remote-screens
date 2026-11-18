@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.content.Context
import android.view.MotionEvent
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
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val root = if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            runCatching { document.document.rootLayoutComponent }.getOrNull()
        } else {
            null
        }
        val collapsed = root?.let(HiddenPanes::collapse).orEmpty()
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            HiddenPanes.restore(collapsed)
        }
    }
}

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
