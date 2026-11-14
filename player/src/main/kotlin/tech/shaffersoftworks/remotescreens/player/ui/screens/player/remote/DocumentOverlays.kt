package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.energy.EnergyDashboardScreen
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.Slot

/** Overlays share their owning slot's stacking level, below the next document and its alerts. */
@Composable
internal fun DocumentOverlays(
    slot: Slot,
    state: RemoteScreenState,
    bounds: OverlayBounds,
    modifier: Modifier,
    isVideoAllowed: Boolean,
    onVideoFailed: (Slot, SlotVideo, Int?) -> Unit,
    onSearchQuery: (String, String) -> Unit,
    onSearchPlay: (String, String) -> Unit,
    onSearchEnqueue: (String, String) -> Unit,
    onEnergyAction: (tileId: String, verb: String) -> Unit = { _, _ -> },
    imageLoader: ImageLoader,
) {
    // A MAIN rebuild can briefly clear playingEnergyDashboard before the follow-up payload.
    // Keep the last dashboard for this page so the RC stub never shows through.
    var stickyEnergy by remember { mutableStateOf<SlotEnergyDashboard?>(null) }
    val playingEnergy = state.playingEnergyDashboard
    SideEffect {
        when {
            playingEnergy != null -> stickyEnergy = playingEnergy
            stickyEnergy?.page != state.page -> stickyEnergy = null
        }
    }
    val energyDashboard = when {
        slot != Slot.MAIN -> null
        playingEnergy != null -> playingEnergy
        else -> stickyEnergy?.takeIf { it.page == state.page }
    }

    Box(modifier) {
        state.playingVideo?.takeIf { it.first == slot && isVideoAllowed }?.second?.let { video ->
            key(slot, video) {
                DocumentOverlay(bounds.video) {
                    LiveVideoView(video) { failed, code -> onVideoFailed(slot, failed, code) }
                }
            }
        }
        state.playingMap?.takeIf { it.first == slot }?.second?.let { map ->
            key(slot, map.revision, map.componentId) {
                DocumentOverlay(bounds.map) { LiveMapView(map, state.bitmaps, Modifier) }
            }
        }
        state.playingSearch?.takeIf { it.first == slot }?.second?.let { search ->
            key(slot, search.tileId, search.revision, search.componentId) {
                DocumentOverlay(bounds.search) {
                    LiveSearchView(
                        search = search,
                        imageLoader = imageLoader,
                        onQueryChange = onSearchQuery,
                        onPlayUri = onSearchPlay,
                        onEnqueueUri = onSearchEnqueue,
                    )
                }
            }
        }
        energyDashboard?.let { dashboard ->
            key(dashboard.tileId, dashboard.page) {
                // Full-screen cover when the hole is not laid out yet (swap), so the RC stub stays hidden.
                if (bounds.energy != null) {
                    DocumentOverlay(bounds.energy) {
                        EnergyDashboardScreen(
                            message = dashboard.message,
                            onAction = { verb -> onEnergyAction(dashboard.tileId, verb) },
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        EnergyDashboardScreen(
                            message = dashboard.message,
                            onAction = { verb -> onEnergyAction(dashboard.tileId, verb) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Document overlays", widthDp = 800, heightDp = 480)
@Composable
private fun DocumentOverlaysPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        DocumentOverlays(
            slot = Slot.MAIN,
            state = RemoteScreenState(),
            bounds = OverlayBounds(),
            modifier = Modifier,
            isVideoAllowed = false,
            onVideoFailed = { _, _, _ -> },
            onSearchQuery = { _, _ -> },
            onSearchPlay = { _, _ -> },
            onSearchEnqueue = { _, _ -> },
            onEnergyAction = { _, _ -> },
            imageLoader = previewLoader,
        )
    }
}
