@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.player.view.RemoteComposePlayer
import kotlin.math.roundToInt

/** Places [overlay] at a document-pixel rectangle inside [player]. */
internal fun placeFixedOverlay(
    overlay: View,
    player: RemoteComposePlayer,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    if (width <= 0 || height <= 0) {
        overlay.visibility = View.INVISIBLE
        return
    }
    val layoutParams = overlay.layoutParams as FrameLayout.LayoutParams
    val left = player.left + x
    val top = player.top + y
    if (
        layoutParams.leftMargin != left ||
        layoutParams.topMargin != top ||
        layoutParams.width != width ||
        layoutParams.height != height
    ) {
        layoutParams.leftMargin = left
        layoutParams.topMargin = top
        layoutParams.width = width
        layoutParams.height = height
        overlay.layoutParams = layoutParams
    }
    overlay.translationZ = SEARCH_OVERLAY_Z
    overlay.visibility = View.VISIBLE
}

private const val SEARCH_OVERLAY_Z = 24f

/** Places a native overlay view over a Remote Compose component bound in [player]. */
internal fun placeRemoteOverlay(
    overlay: View,
    player: RemoteComposePlayer,
    componentId: Int,
) {
    val bounds = componentBounds(player, componentId)
    if (bounds == null) {
        overlay.visibility = View.INVISIBLE
        return
    }
    bounds.offset(player.left, player.top)
    val layoutParams = overlay.layoutParams as FrameLayout.LayoutParams
    val previousBounds = Rect(
        layoutParams.leftMargin,
        layoutParams.topMargin,
        layoutParams.leftMargin + layoutParams.width,
        layoutParams.topMargin + layoutParams.height,
    )
    if (previousBounds != bounds) {
        layoutParams.leftMargin = bounds.left
        layoutParams.topMargin = bounds.top
        layoutParams.width = bounds.width()
        layoutParams.height = bounds.height()
        overlay.layoutParams = layoutParams
    }
    overlay.visibility = View.VISIBLE
}

/**
 * Where component [componentId] sits in [player]'s document. Uses the library location when it works; never
 * fails closed on that path alone (that left the overlay invisible and only the spinner or still showed).
 */
private fun componentBounds(player: RemoteComposePlayer, componentId: Int): Rect? {
    val component = runCatching {
        player.document.document.getComponent(componentId)
    }.getOrNull()
    val width = component?.width?.roundToInt() ?: 0
    val height = component?.height?.roundToInt() ?: 0
    if (component == null || width <= 0 || height <= 0) return null
    val windowBounds = remoteContextOf(player)?.let { remoteContext ->
        val locationInWindow = floatArrayOf(0f, 0f)
        runCatching {
            component.getLocationInWindow(remoteContext, locationInWindow)
            val left = locationInWindow[0].roundToInt()
            val top = locationInWindow[1].roundToInt()
            Rect(left, top, left + width, top + height)
        }.getOrNull()
    }
    val semanticBounds = IntArray(SEMANTIC_BOUND_SIZE)
    val hasSemanticBounds = runCatching {
        component.getBoundsInSemanticParent(semanticBounds, null)
        true
    }.getOrDefault(false)
    val fromSemantic = if (hasSemanticBounds) {
        Rect(
            semanticBounds[BOUND_LEFT],
            semanticBounds[BOUND_TOP],
            semanticBounds[BOUND_RIGHT],
            semanticBounds[BOUND_BOTTOM],
        )
    } else {
        null
    }
    return windowBounds ?: fromSemantic
}

private fun remoteContextOf(player: RemoteComposePlayer): RemoteContext? = runCatching {
    RemoteComposePlayer::class.java.getDeclaredMethod("getRemoteContext").run {
        isAccessible = true
        invoke(player) as RemoteContext
    }
}.getOrNull()

/** [Component.getBoundsInSemanticParent] writes left, top, right, bottom into a length-4 array. */
private const val SEMANTIC_BOUND_SIZE = 4
private const val BOUND_LEFT = 0
private const val BOUND_TOP = 1
private const val BOUND_RIGHT = 2
private const val BOUND_BOTTOM = 3
