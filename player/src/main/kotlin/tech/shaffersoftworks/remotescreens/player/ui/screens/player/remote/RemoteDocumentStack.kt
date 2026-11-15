package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.Slot

/** Compose owns slot order; the controller only manages Remote Compose document renderers. */
@Composable
internal fun RemoteDocumentStack(
    state: RemoteScreenState,
    isVideoAllowed: Boolean,
    onAction: (Slot, Int, String) -> Unit,
    onVideoFailed: (Slot, SlotVideo, Int?) -> Unit,
    onSearchQuery: (String, String) -> Unit,
    onSearchPlay: (String, String) -> Unit,
    onSearchEnqueue: (String, String) -> Unit,
    onEnergyAction: (tileId: String, verb: String) -> Unit = { _, _ -> },
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current
    val controller = remember(context) { RemoteDocumentController(context) }
    val host = LocalView.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val currentState by rememberUpdatedState(state)
    var bounds by remember { mutableStateOf(OverlayBounds()) }
    SideEffect {
        controller.onAction = onAction
        // This composition recorded the new stacking. Hide the old buffer before the frame draws.
        controller.hideReplacedMainPlayer()
    }
    LaunchedEffect(controller, state) {
        controller.render(state)
    }
    DisposableEffect(controller, host) {
        val observer = host.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            controller.beforeDraw()
            val measured = overlayBounds(controller, currentState)
            // Keep the last Energy hole while the dashboard is still playing. A MAIN swap measures
            // the incoming buffer before layout and would otherwise shrink the overlay to 0×0.
            bounds = measured.copy(
                energy = measured.energy
                    ?: bounds.energy.takeIf { currentState.playingEnergyDashboard != null },
            )
            true
        }
        observer.addOnPreDrawListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            controller.dispose()
        }
    }
    Box(
        Modifier.fillMaxSize().deferDocumentUpdatesDuringTouch(controller),
    ) {
        // Remote Compose AndroidViews first, then Compose overlays. Interleaving let a MAIN buffer
        // swap draw the Energy stub over the native dashboard for a frame.
        Slot.entries.forEach { slot ->
            val base = slot.ordinal * SLOT_LAYER_SPACING
            if (slot == Slot.MAIN) {
                AndroidView(
                    factory = { controller.mainBackPlayer },
                    modifier = Modifier.fillMaxSize().zIndex(
                        base + if (controller.mainFrontIsPrimary) -1f else 0f,
                    ),
                )
            }
            AndroidView(
                factory = { controller.primaryPlayer(slot) },
                modifier = Modifier.fillMaxSize().zIndex(
                    base + if (slot == Slot.MAIN && !controller.mainFrontIsPrimary) -1f else 0f,
                ),
            )
        }
        if (active) {
            Slot.entries.forEach { slot ->
                val base = slot.ordinal * SLOT_LAYER_SPACING
                DocumentOverlays(
                    slot = slot,
                    state = state,
                    bounds = bounds,
                    modifier = Modifier.zIndex(base + OVERLAY_ABOVE_DOCUMENTS),
                    isVideoAllowed = isVideoAllowed,
                    onVideoFailed = onVideoFailed,
                    onSearchQuery = onSearchQuery,
                    onSearchPlay = onSearchPlay,
                    onSearchEnqueue = onSearchEnqueue,
                    onEnergyAction = onEnergyAction,
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

/** Observe without consuming: Remote Compose still owns clicks and scrolling. */
private fun Modifier.deferDocumentUpdatesDuringTouch(controller: RemoteDocumentController): Modifier =
    pointerInput(controller) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    controller.setTouchActive(event.changes.any { it.pressed })
                }
            }
        } finally {
            controller.setTouchActive(false)
        }
    }

private const val SLOT_LAYER_SPACING = 10f
/** Keep Compose overlays above every Remote Compose AndroidView in the stack. */
private const val OVERLAY_ABOVE_DOCUMENTS = 5f

internal data class OverlayBounds(
    val video: Rect? = null,
    val map: Rect? = null,
    val search: Rect? = null,
    val energy: Rect? = null,
)

private fun overlayBounds(controller: RemoteDocumentController, state: RemoteScreenState): OverlayBounds {
    fun bounds(slot: Slot, id: Int): Rect? = componentBounds(controller.documentPlayer(slot), id)
    // Visible MAIN buffer first during a swap: documentPlayer is the incoming one and may not
    // have laid out the Energy hole yet (null bounds → 0×0 overlay flash / RC stub).
    fun energyBounds(id: Int): Rect? {
        val front = controller.primaryPlayer(Slot.MAIN)
        val back = controller.mainBackPlayer
        val visible = if (controller.mainFrontIsPrimary) front else back
        return componentBounds(visible, id)
            ?: componentBounds(controller.documentPlayer(Slot.MAIN), id)
    }
    val search = state.playingSearch
    val energy = state.playingEnergyDashboard
    return OverlayBounds(
        video = state.playingVideo?.let { (slot, video) -> bounds(slot, video.componentId) },
        map = state.playingMap?.let { (slot, map) -> bounds(slot, map.componentId) },
        search = search?.let { (slot, field) ->
            if (field.width > 0 && field.height > 0) {
                Rect(field.x, field.y, field.x + field.width, field.y + field.height)
            } else {
                bounds(slot, field.componentId)
            }
        },
        energy = energy?.let { dashboard -> energyBounds(dashboard.message.componentId) },
    )
}

/** Document bounds are physical pixels, so measure and place without a density conversion. */
@Composable
internal fun DocumentOverlay(bounds: Rect?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = bounds?.width()?.coerceAtLeast(0) ?: 0
        val height = bounds?.height()?.coerceAtLeast(0) ?: 0
        val children = measurables.map { it.measure(Constraints.fixed(width, height)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (bounds != null && width > 0 && height > 0) {
                children.forEach { it.place(bounds.left, bounds.top) }
            }
        }
    }
}

@Preview(name = "Document stack", widthDp = 800, heightDp = 480)
@Composable
private fun RemoteDocumentStackPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        RemoteDocumentStack(
            state = RemoteScreenState(),
            isVideoAllowed = false,
            onAction = { _, _, _ -> },
            onVideoFailed = { _, _, _ -> },
            onSearchQuery = { _, _ -> },
            onSearchPlay = { _, _ -> },
            onSearchEnqueue = { _, _ -> },
            imageLoader = previewLoader,
        )
        DocumentOverlay(Rect(0, 0, 1, 1)) { }
    }
}
