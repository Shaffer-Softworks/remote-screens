package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteDocumentStack
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.SlotVideo
import androidx.compose.ui.unit.dp
import tech.shaffersoftworks.remotescreens.player.data.device.assist.AssistUiState
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.assist.AssistCaption
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.assist.AssistHeaderMic
import tech.shaffersoftworks.remotescreens.player.ui.theme.Page
import tech.shaffersoftworks.remotescreens.player.ui.views.IdentifyBadge
import tech.shaffersoftworks.remotescreens.player.ui.views.InfoPanel
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import tech.shaffersoftworks.remotescreens.shared.Slot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteScreenState
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme

private val HEADER_MIC_INSET = 12.dp
private val CAPTION_ABOVE_NAV = 72.dp

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = hiltViewModel()) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    PlayerEvents(viewModel)
    PlayerWindowEffects(viewState)
    BackHandler(onBack = viewModel::onBack)

    PlayerScreenContent(
        viewState = viewState,
        onUserInteraction = viewModel::onUserInteraction,
        onDocumentAction = viewModel::onDocumentAction,
        onVideoFailed = viewModel::onVideoFailed,
        onSearchQuery = viewModel::onSearchQuery,
        onSearchPlay = viewModel::onSearchPlay,
        onSearchEnqueue = viewModel::onSearchEnqueue,
        onEnergyAction = viewModel::onEnergyDashboardAction,
        onToggleAssistMute = viewModel::toggleAssistMute,
        onEnterAddress = viewModel::openAddressDialog,
        onAllowUnrestrictedBattery = viewModel::onAllowUnrestrictedBatteryRequested,
        onReconnect = viewModel::onReconnectRequested,
        imageLoader = viewModel.haImageLoaderQualifier,
        onNotificationAction = viewModel::dismissNotificationBanner,
    )

    PlayerDialogs(
        viewState = viewState,
        onForgetConfirmed = viewModel::onForgetConfirmed,
        onForgetCancelled = viewModel::onForgetCancelled,
        onBrightnessPreview = viewModel::onSettingsBrightnessPreview,
        onSettingsConfirmed = viewModel::onSettingsConfirmed,
        onSettingsDismissed = viewModel::onSettingsDismissed,
        onChangeServer = viewModel::onChangeServerRequested,
        onForget = viewModel::onForgetRequested,
        onAllowSelfStart = viewModel::onAllowSelfStartRequested,
        onAllowScreenOff = viewModel::onAllowScreenOffRequested,
        onAllowUnrestrictedBattery = viewModel::onAllowUnrestrictedBatteryRequested,
        onAllowCameraPermission = viewModel::onAllowCameraPermissionRequested,
        onAllowMicrophonePermission = viewModel::onAllowMicrophonePermissionRequested,
        onAllowLocationPermission = viewModel::onAllowLocationPermissionRequested,
        onUnlockKiosk = viewModel::onUnlockKioskRequested,
        onLockKioskAgain = viewModel::onLockKioskAgainRequested,
        onRemoveDeviceOwner = viewModel::onRemoveDeviceOwnerRequested,
        onKioskPinEntered = viewModel::onKioskPinEntered,
        onKioskPinDismissed = viewModel::onKioskPinDismissed,
        onAddressConfirmed = viewModel::onAddressConfirmed,
        onAddressDismissed = viewModel::onAddressDismissed,
        onCheckForUpdates = viewModel::onCheckForUpdatesRequested,
        onInstallUpdate = viewModel::onInstallUpdateConfirmed,
        onUpdateDismissed = viewModel::onUpdateDismissed,
        onCalendarDismiss = viewModel::dismissCalendarDialog,
        onCalendarCreate = viewModel::submitCalendarCreate,
        onCalendarDelete = viewModel::submitCalendarDelete,
    )
}

@Composable
internal fun PlayerScreenContent(
    viewState: PlayerViewModel.ViewState,
    onUserInteraction: () -> Unit = {},
    onDocumentAction: (Slot, Int, String) -> Unit = { _, _, _ -> },
    onVideoFailed: (Slot, SlotVideo, Int?) -> Unit = { _, _, _ -> },
    onSearchQuery: (String, String) -> Unit = { _, _ -> },
    onSearchPlay: (String, String) -> Unit = { _, _ -> },
    onSearchEnqueue: (String, String) -> Unit = { _, _ -> },
    onEnergyAction: (String, String) -> Unit = { _, _ -> },
    onToggleAssistMute: () -> Unit = {},
    onEnterAddress: () -> Unit = {},
    onAllowUnrestrictedBattery: () -> Unit = {},
    onReconnect: () -> Unit = {},
    imageLoader: ImageLoader,
    onNotificationAction: (Int?) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Page)
            .pointerInput(Unit) {
                // Any touch anywhere wakes the screen. Seen before the document, and never consumed.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        if (event.type == PointerEventType.Press) onUserInteraction()
                    }
                }
            },
    ) {
        PlayerNavigationDrawer(
            modifier = Modifier.fillMaxSize(),
            state = viewState.remoteScreen,
            isEnabled = viewState.remoteScreen.hasMainDocument && !viewState.remoteScreen.isOverlayShown &&
                !viewState.isAnyDialogVisible && viewState.isAwake,
            onAction = { metadata ->
                val revision = viewState.remoteScreen.documents.getValue(Slot.MAIN).revision
                onDocumentAction(Slot.MAIN, revision, metadata)
            },
        ) {
            RemoteDocumentStack(
                state = viewState.remoteScreen,
                isVideoAllowed = viewState.isAwake && !viewState.isIntercomCall,
                onAction = onDocumentAction,
                onVideoFailed = onVideoFailed,
                onSearchQuery = onSearchQuery,
                onSearchPlay = onSearchPlay,
                onSearchEnqueue = onSearchEnqueue,
                onEnergyAction = onEnergyAction,
                assist = viewState.assist,
                onToggleAssistMute = onToggleAssistMute,
                imageLoader = imageLoader,
            )
        }

        PlayerChromeOverlays(
            viewState = viewState,
            onEnterAddress = onEnterAddress,
            onAllowUnrestrictedBattery = onAllowUnrestrictedBattery,
            onReconnect = onReconnect,
            onNotificationAction = onNotificationAction,
            onToggleAssistMute = onToggleAssistMute,
        )
    }
}

@Composable
private fun PlayerChromeOverlays(
    viewState: PlayerViewModel.ViewState,
    onEnterAddress: () -> Unit,
    onAllowUnrestrictedBattery: () -> Unit,
    onReconnect: () -> Unit,
    onNotificationAction: (Int?) -> Unit,
    onToggleAssistMute: () -> Unit = {},
) {
    if (Slot.ALERT !in viewState.remoteScreen.documents && !viewState.isAnyDialogVisible) {
        viewState.notificationBanner?.let { banner ->
            tech.shaffersoftworks.remotescreens.player.ui.views.NotificationBanner(
                banner = banner,
                onAction = onNotificationAction,
            )
        }
    }

    viewState.infoPanel?.let { infoPanel ->
        val disconnectedOverDocument =
            infoPanel is InfoPanelState.Disconnected && viewState.remoteScreen.hasMainDocument

        if (disconnectedOverDocument && (viewState.isAnyDialogVisible || viewState.isBlackout)) {
            return@let
        }

        InfoPanel(
            state = infoPanel,
            onEnterAddress = onEnterAddress,
            // Lock task hides the system dialog the button opens.
            canOfferUnrestrictedBattery = viewState.isBatteryRestricted && viewState.isKioskWanted != true,
            onAllowUnrestrictedBattery = onAllowUnrestrictedBattery,
            asSnackbar = disconnectedOverDocument,
            onRetry = onReconnect,
        )
    }

    viewState.identifyScreenName?.let { screenName -> IdentifyBadge(screenName = screenName) }

    AssistPageChrome(viewState = viewState, onToggleAssistMute = onToggleAssistMute)

    if (viewState.isBlackout) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun AssistPageChrome(
    viewState: PlayerViewModel.ViewState,
    onToggleAssistMute: () -> Unit,
) {
    if (!viewState.screenSettings.assist || viewState.isBlackout) {
        return
    }

    Box(Modifier.fillMaxSize()) {
        AssistHeaderMic(
            assist = viewState.assist,
            onToggleMute = onToggleAssistMute,
            modifier = Modifier.align(Alignment.TopEnd).padding(HEADER_MIC_INSET),
        )

        val caption = viewState.assist.caption

        if (caption != null && viewState.remoteScreen.playingAssist == null) {
            AssistCaption(
                text = caption,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = CAPTION_ABOVE_NAV),
            )
        }
    }
}

@Preview(name = "Searching", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerScreenSearchingPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        PlayerScreenContent(
            viewState = PlayerViewModel.ViewState(infoPanel = InfoPanelState.Searching),
            imageLoader = previewLoader,
        )
    }
}

@Preview(name = "Playing", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerScreenPlayingPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        PlayerScreenContent(
            viewState = PlayerViewModel.ViewState(),
            imageLoader = previewLoader,
        )
    }
}

@Preview(name = "Identify", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerScreenIdentifyPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        PlayerScreenContent(
            viewState = PlayerViewModel.ViewState(identifyScreenName = "Kitchen"),
            imageLoader = previewLoader,
        )
    }
}

@Preview(name = "Blackout", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerScreenBlackoutPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        PlayerScreenContent(
            viewState = PlayerViewModel.ViewState(isAwake = false),
            imageLoader = previewLoader,
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun AssistPageChromePreview() {
    RemoteScreensTheme {
        AssistPageChrome(
            viewState = PlayerViewModel.ViewState(
                screenSettings = ScreenSettings(assist = true),
                assist = AssistUiState(
                    phase = AssistPhases.LISTENING,
                    caption = "Turn off the kitchen lights",
                ),
            ),
            onToggleAssistMute = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PlayerChromeOverlaysPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxSize()) {
            PlayerChromeOverlays(
                viewState = PlayerViewModel.ViewState(infoPanel = InfoPanelState.Searching),
                onEnterAddress = {},
                onAllowUnrestrictedBattery = {},
                onReconnect = {},
                onNotificationAction = {},
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun RemoteScreenPreview() {
    val previewLoader = ImageLoader.Builder(LocalContext.current).build()

    RemoteScreensTheme {
        RemoteDocumentStack(
            state = RemoteScreenState(),
            isVideoAllowed = true,
            onAction = { _, _, _ -> },
            onVideoFailed = { _, _, _ -> },
            onSearchQuery = { _, _ -> },
            onSearchPlay = { _, _ -> },
            onSearchEnqueue = { _, _ -> },
            imageLoader = previewLoader,
        )
    }
}
