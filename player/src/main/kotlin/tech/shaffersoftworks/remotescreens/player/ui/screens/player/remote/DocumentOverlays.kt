package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import android.graphics.Rect
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
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistUiState
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.assist.AssistScreen
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.energy.EnergyDashboardScreen
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
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
    assist: AssistUiState = AssistUiState(),
    onToggleAssistMute: () -> Unit = {},
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

        AssistPageOverlay(
            slot = slot,
            bounds = bounds.assist,
            page = state.playingAssist,
            assist = assist,
            onToggleMute = onToggleAssistMute,
        )
    }
}

@Composable
private fun AssistPageOverlay(
    slot: Slot,
    bounds: Rect?,
    page: SlotAssistPage?,
    assist: AssistUiState,
    onToggleMute: () -> Unit,
) {
    if (slot != Slot.MAIN || bounds == null || page == null) return

    key(page.tileId, page.page) {
        DocumentOverlay(bounds) {
            AssistScreen(
                page = page.message,
                assist = assist,
                onToggleMute = onToggleMute,
            )
        }
    }
}

@Preview(name = "Assist page overlay", widthDp = 800, heightDp = 480)
@Composable
private fun AssistPageOverlayPreview() {
    RemoteScreensTheme {
        AssistPageOverlay(
            slot = Slot.MAIN,
            bounds = Rect(0, PREVIEW_HEADER_TOP, PREVIEW_ASSIST_RIGHT, PREVIEW_ASSIST_BOTTOM),
            page = SlotAssistPage(
                revision = 1,
                page = 0,
                tileId = "assist",
                message = ServerMessage.AssistPage(
                    slot = Slot.MAIN,
                    rev = 1,
                    page = 0,
                    tileId = "assist",
                    componentId = 0x62_0000,
                    wakeWord = "Okay Nabu",
                    pipelineLabel = "Preferred",
                ),
            ),
            assist = AssistUiState(
                phase = AssistPhases.SPEAKING,
                caption = "The kitchen lights are off",
            ),
            onToggleMute = {},
        )
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

private const val PREVIEW_HEADER_TOP = 48
private const val PREVIEW_ASSIST_RIGHT = 800
private const val PREVIEW_ASSIST_BOTTOM = 400
