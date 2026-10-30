package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import androidx.compose.runtime.Composable
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.CalendarCreateForm
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.CalendarDialogs
import tech.shaffersoftworks.remotescreens.player.ui.views.AddressDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.ForgetScreenDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.KioskPinDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.ScreenSettingsDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.UpdateDialog

@Composable
internal fun PlayerDialogs(
    viewState: PlayerViewModel.ViewState,
    onForgetConfirmed: () -> Unit = {},
    onForgetCancelled: () -> Unit = {},
    onBrightnessPreview: (Int) -> Unit = {},
    onSettingsConfirmed: (brightnessPercent: Int, isDarkMode: Boolean, standbyMinutes: Int) -> Unit = { _, _, _ -> },
    onSettingsDismissed: () -> Unit = {},
    onChangeServer: () -> Unit = {},
    onForget: () -> Unit = {},
    onAllowSelfStart: () -> Unit = {},
    onAllowScreenOff: () -> Unit = {},
    onAllowUnrestrictedBattery: () -> Unit = {},
    onAllowCameraPermission: () -> Unit = {},
    onAllowLocationPermission: () -> Unit = {},
    onUnlockKiosk: () -> Unit = {},
    onLockKioskAgain: () -> Unit = {},
    onRemoveDeviceOwner: () -> Unit = {},
    onKioskPinEntered: (String) -> Unit = {},
    onKioskPinDismissed: () -> Unit = {},
    onAddressConfirmed: (String) -> Unit = {},
    onAddressDismissed: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onUpdateDismissed: () -> Unit = {},
    onCalendarDismiss: () -> Unit = {},
    onCalendarCreate: (CalendarCreateForm) -> Unit = {},
    onCalendarDelete: () -> Unit = {},
) {
    viewState.remoteScreen.calendarDialog?.let { calendarDialog ->
        CalendarDialogs(
            state = calendarDialog,
            onDismiss = onCalendarDismiss,
            onCreate = onCalendarCreate,
            onDelete = onCalendarDelete,
        )
    }
    viewState.settingsDialog?.let { settingsDialog ->
        if (settingsDialog.isForgetConfirmationVisible) {
            ForgetScreenDialog(
                onConfirm = onForgetConfirmed,
                onDismiss = onForgetCancelled,
            )
        } else {
            ScreenSettingsDialog(
                settings = viewState.screenSettings,
                server = settingsDialog.server,
                isCheckingForUpdates = settingsDialog.isCheckingForUpdates,
                onCheckForUpdates = onCheckForUpdates,
                canOfferSelfStart = settingsDialog.canOfferSelfStart,
                canOfferScreenOff = settingsDialog.canOfferScreenOff,
                canOfferUnrestrictedBattery = settingsDialog.canOfferUnrestrictedBattery,
                canOfferCameraPermission = settingsDialog.canOfferCameraPermission,
                canOfferLocationPermission = settingsDialog.canOfferLocationPermission,
                wakeOnFaceUnavailableNote = settingsDialog.wakeOnFaceUnavailableNote,
                kioskAction = settingsDialog.kioskAction,
                canRemoveDeviceOwner = settingsDialog.canRemoveDeviceOwner,
                onBrightnessPreview = onBrightnessPreview,
                onConfirm = onSettingsConfirmed,
                onDismiss = onSettingsDismissed,
                onChangeServer = onChangeServer,
                onForget = onForget,
                onAllowSelfStart = onAllowSelfStart,
                onAllowScreenOff = onAllowScreenOff,
                onAllowUnrestrictedBattery = onAllowUnrestrictedBattery,
                onAllowCameraPermission = onAllowCameraPermission,
                onAllowLocationPermission = onAllowLocationPermission,
                onUnlockKiosk = onUnlockKiosk,
                onLockKioskAgain = onLockKioskAgain,
                onRemoveDeviceOwner = onRemoveDeviceOwner,
            )
        }
    }
    if (viewState.isKioskPinDialogVisible) {
        KioskPinDialog(
            onConfirm = onKioskPinEntered,
            onDismiss = onKioskPinDismissed,
        )
    }
    viewState.addressDialog?.let { addressDialog ->
        AddressDialog(
            initialAddress = addressDialog.initialAddress,
            onConfirm = onAddressConfirmed,
            onDismiss = onAddressDismissed,
        )
    }
    viewState.availableUpdate?.let { update ->
        UpdateDialog(
            versionName = update.versionName,
            onInstall = onInstallUpdate,
            onDismiss = onUpdateDismissed,
        )
    }
}
