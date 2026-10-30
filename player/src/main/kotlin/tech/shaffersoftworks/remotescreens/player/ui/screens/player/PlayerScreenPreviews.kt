package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil.ImageLoader
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.CalendarDialogState
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteDocumentStack
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteScreenState
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.CalendarChoice
import tech.shaffersoftworks.remotescreens.shared.CalendarDialogMode
import tech.shaffersoftworks.remotescreens.shared.ServerMessage

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

@Preview(name = "Settings", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDialogsSettingsPreview() {
    RemoteScreensTheme {
        PlayerDialogs(
            viewState = PlayerViewModel.ViewState(
                settingsDialog = SettingsDialogState(
                    server = "192.168.1.10:8100",
                    canOfferSelfStart = true,
                    canOfferScreenOff = true,
                    canOfferUnrestrictedBattery = true,
                    canOfferCameraPermission = true,
                    canOfferLocationPermission = true,
                    kioskAction = KioskAction.UNLOCK,
                ),
            ),
        )
    }
}

@Preview(name = "Forget", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDialogsForgetPreview() {
    RemoteScreensTheme {
        PlayerDialogs(
            viewState = PlayerViewModel.ViewState(
                settingsDialog = SettingsDialogState(
                    server = "192.168.1.10:8100",
                    canOfferSelfStart = false,
                    isForgetConfirmationVisible = true,
                ),
            ),
        )
    }
}

@Preview(name = "Kiosk PIN", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDialogsKioskPinPreview() {
    RemoteScreensTheme {
        PlayerDialogs(viewState = PlayerViewModel.ViewState(isKioskPinDialogVisible = true))
    }
}

@Preview(name = "Address", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDialogsAddressPreview() {
    RemoteScreensTheme {
        PlayerDialogs(
            viewState = PlayerViewModel.ViewState(
                addressDialog = AddressDialogState(initialAddress = "192.168.1.10:8100"),
            ),
        )
    }
}

@Preview(name = "Calendar", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDialogsCalendarPreview() {
    RemoteScreensTheme {
        PlayerDialogs(
            viewState = PlayerViewModel.ViewState(
                remoteScreen = RemoteScreenState(
                    calendarDialog = CalendarDialogState(
                        mode = CalendarDialogMode.CREATE,
                        tileId = "calendar",
                        calendars = listOf(CalendarChoice("calendar.family", "Family")),
                    ),
                ),
            ),
        )
    }
}

@Preview(name = "Update", widthDp = 800, heightDp = 480)
@Composable
private fun PlayerDialogsUpdatePreview() {
    RemoteScreensTheme {
        PlayerDialogs(
            viewState = PlayerViewModel.ViewState(
                availableUpdate = ServerMessage.UpdateAvailable(
                    versionCode = 2,
                    versionName = "0.1.3",
                    path = "remote-screens.apk",
                ),
            ),
        )
    }
}
