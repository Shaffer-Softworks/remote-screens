package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
) {
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
                    LiveSearchView(search, onSearchQuery, onSearchPlay, onSearchEnqueue)
                }
            }
        }
    }
}

@Preview(name = "Document overlays", widthDp = 800, heightDp = 480)
@Composable
private fun DocumentOverlaysPreview() {
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
        )
    }
}
