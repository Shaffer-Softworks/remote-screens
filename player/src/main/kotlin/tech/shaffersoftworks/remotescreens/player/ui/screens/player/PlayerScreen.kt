package tech.shaffersoftworks.remotescreens.player.ui.screens.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.receiver.ScreenOffAdminReceiver
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteDocumentView
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteScreenState
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.SlotVideo
import tech.shaffersoftworks.remotescreens.player.ui.theme.Page
import tech.shaffersoftworks.remotescreens.player.ui.views.AddressDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.ForgetScreenDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.IdentifyBadge
import tech.shaffersoftworks.remotescreens.player.ui.views.InfoPanel
import tech.shaffersoftworks.remotescreens.player.ui.views.KioskPinDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.ScreenSettingsDialog
import tech.shaffersoftworks.remotescreens.player.ui.views.UpdateDialog
import tech.shaffersoftworks.remotescreens.player.utils.applyScreenOrientation
import tech.shaffersoftworks.remotescreens.player.utils.hideSystemBars
import tech.shaffersoftworks.remotescreens.player.utils.setWindowBrightness
import tech.shaffersoftworks.remotescreens.shared.Slot

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = hiltViewModel()) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    PlayerEvents(viewModel)
    PlayerWindowEffects(viewState)
    BackHandler(onBack = viewModel::onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Page)
            .pointerInput(Unit) {
                // Any touch anywhere wakes the screen. Seen before the document, and never consumed.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) viewModel.onUserInteraction()
                    }
                }
            },
    ) {
        RemoteScreen(
            state = viewState.remoteScreen,
            isSwipeEnabled = viewState.isSwipeEnabled,
            isVideoAllowed = viewState.isAwake,
            onAction = viewModel::onDocumentAction,
            onSwipe = viewModel::onSwipe,
            onVideoFailed = viewModel::onVideoFailed,
        )
        viewState.infoPanel?.let { infoPanel ->
            InfoPanel(
                state = infoPanel,
                onEnterAddress = viewModel::openAddressDialog,
                // Lock task hides the system dialog the button opens.
                canOfferUnrestrictedBattery = viewState.isBatteryRestricted && viewState.isKioskWanted != true,
                onAllowUnrestrictedBattery = viewModel::onAllowUnrestrictedBatteryRequested,
            )
        }
        viewState.identifyScreenName?.let { screenName -> IdentifyBadge(screenName = screenName) }
        if (viewState.isBlackout) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }

    PlayerDialogs(viewState, viewModel)
}

@Composable
private fun RemoteScreen(
    state: RemoteScreenState,
    isSwipeEnabled: Boolean,
    isVideoAllowed: Boolean,
    onAction: (Slot, Int, String) -> Unit,
    onSwipe: (Boolean) -> Unit,
    onVideoFailed: (Slot, SlotVideo, Int?) -> Unit,
) {
    AndroidView(
        factory = { context -> RemoteDocumentView(context) },
        modifier = Modifier.fillMaxSize(),
        update = { remoteDocumentView ->
            remoteDocumentView.onAction = onAction
            remoteDocumentView.onSwipe = onSwipe
            remoteDocumentView.onVideoFailed = onVideoFailed
            remoteDocumentView.isSwipeEnabled = isSwipeEnabled
            remoteDocumentView.isVideoAllowed = isVideoAllowed
            remoteDocumentView.render(state)
        },
    )
}

@Composable
private fun PlayerDialogs(viewState: PlayerViewModel.ViewState, viewModel: PlayerViewModel) {
    viewState.settingsDialog?.let { settingsDialog ->
        if (settingsDialog.isForgetConfirmationVisible) {
            ForgetScreenDialog(
                onConfirm = viewModel::onForgetConfirmed,
                onDismiss = viewModel::onForgetCancelled,
            )
        } else {
            ScreenSettingsDialog(
                settings = viewState.screenSettings,
                server = settingsDialog.server,
                canOfferSelfStart = settingsDialog.canOfferSelfStart,
                canOfferScreenOff = settingsDialog.canOfferScreenOff,
                canOfferUnrestrictedBattery = settingsDialog.canOfferUnrestrictedBattery,
                canOfferCameraPermission = settingsDialog.canOfferCameraPermission,
                wakeOnFaceUnavailableNote = settingsDialog.wakeOnFaceUnavailableNote,
                kioskAction = settingsDialog.kioskAction,
                canRemoveDeviceOwner = settingsDialog.canRemoveDeviceOwner,
                onBrightnessPreview = viewModel::onSettingsBrightnessPreview,
                onConfirm = viewModel::onSettingsConfirmed,
                onDismiss = viewModel::onSettingsDismissed,
                onChangeServer = viewModel::onChangeServerRequested,
                onForget = viewModel::onForgetRequested,
                onAllowSelfStart = viewModel::onAllowSelfStartRequested,
                onAllowScreenOff = viewModel::onAllowScreenOffRequested,
                onAllowUnrestrictedBattery = viewModel::onAllowUnrestrictedBatteryRequested,
                onAllowCameraPermission = viewModel::onAllowCameraPermissionRequested,
                onUnlockKiosk = viewModel::onUnlockKioskRequested,
                onLockKioskAgain = viewModel::onLockKioskAgainRequested,
                onRemoveDeviceOwner = viewModel::onRemoveDeviceOwnerRequested,
            )
        }
    }
    if (viewState.isKioskPinDialogVisible) {
        KioskPinDialog(
            onConfirm = viewModel::onKioskPinEntered,
            onDismiss = viewModel::onKioskPinDismissed,
        )
    }
    viewState.addressDialog?.let { addressDialog ->
        AddressDialog(
            initialAddress = addressDialog.initialAddress,
            onConfirm = viewModel::onAddressConfirmed,
            onDismiss = viewModel::onAddressDismissed,
        )
    }
    viewState.availableUpdate?.let { update ->
        UpdateDialog(
            versionName = update.versionName,
            onInstall = viewModel::onInstallUpdateConfirmed,
            onDismiss = viewModel::onUpdateDismissed,
        )
    }
}

/** Brightness, orientation and hidden system bars belong to the window, so they are applied from here. */
@Composable
private fun PlayerWindowEffects(viewState: PlayerViewModel.ViewState) {
    val activity = LocalActivity.current ?: return
    LaunchedEffect(
        viewState.brightnessPercent,
    ) { activity.setWindowBrightness(viewState.brightnessPercent) }
    LaunchedEffect(viewState.screenSettings.orientation) {
        activity.applyScreenOrientation(viewState.screenSettings.orientation)
    }
    LaunchedEffect(viewState.isAnyDialogVisible) {
        if (!viewState.isAnyDialogVisible) activity.hideSystemBars()
    }
}

@Composable
private fun PlayerEvents(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onCameraPermissionResult(granted) }
    LaunchedEffect(viewModel, lifecycleOwner, cameraPermissionLauncher) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    PlayerEvent.RequestCameraPermission ->
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    else -> handleEvent(context, activity, event)
                }
            }
        }
    }
}

private fun handleEvent(context: Context, activity: Activity?, event: PlayerEvent) {
    when (event) {
        is PlayerEvent.ShowMessage -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
        is PlayerEvent.ShowMessageResource -> Toast.makeText(
            context,
            event.messageRes,
            Toast.LENGTH_LONG,
        ).show()
        is PlayerEvent.OpenUrl -> runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    event.url.toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        PlayerEvent.OpenInstallPermissionSettings -> context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ),
        )
        PlayerEvent.OpenOverlayPermissionSettings -> context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            ),
        )
        PlayerEvent.OpenScreenOffAdminSettings -> context.startActivity(
            screenOffAdminIntent(context),
        )
        PlayerEvent.RequestUnrestrictedBattery -> context.startActivity(
            unrestrictedBatteryIntent(context),
        )
        PlayerEvent.RequestCameraPermission -> Unit
        // Pinning comes back when the activity resumes after the confirmation.
        PlayerEvent.StopScreenPinning -> activity?.stopLockTask()
        PlayerEvent.ReapplyKiosk -> activity?.let { foregroundActivity ->
            runCatching { foregroundActivity.startLockTask() }
        }
    }
}

/** Google Play only allows asking directly when Doze breaks the app; the add-on's sideloaded build qualifies. */
@SuppressLint("BatteryLife")
internal fun unrestrictedBatteryIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri(),
    )

private fun screenOffAdminIntent(context: Context): Intent {
    val adminComponent = ComponentName(context, ScreenOffAdminReceiver::class.java)
    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.screen_off_explanation),
        )
}
