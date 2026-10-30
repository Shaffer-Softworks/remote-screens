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
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.receiver.ScreenOffAdminReceiver
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.CalendarCreateForm
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.CalendarDialogState
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.CalendarDialogs
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteDocumentStack
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.RemoteScreenState
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote.SlotVideo
import tech.shaffersoftworks.remotescreens.player.ui.theme.Page
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
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
import tech.shaffersoftworks.remotescreens.shared.CalendarChoice
import tech.shaffersoftworks.remotescreens.shared.CalendarDialogMode
import tech.shaffersoftworks.remotescreens.shared.ServerMessage
import tech.shaffersoftworks.remotescreens.shared.Slot

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
        onEnterAddress = viewModel::openAddressDialog,
        onAllowUnrestrictedBattery = viewModel::onAllowUnrestrictedBatteryRequested,
        imageLoader = viewModel.haImageLoaderQualifier,
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
private fun PlayerScreenContent(
    viewState: PlayerViewModel.ViewState,
    onUserInteraction: () -> Unit = {},
    onDocumentAction: (Slot, Int, String) -> Unit = { _, _, _ -> },
    onVideoFailed: (Slot, SlotVideo, Int?) -> Unit = { _, _, _ -> },
    onSearchQuery: (String, String) -> Unit = { _, _ -> },
    onSearchPlay: (String, String) -> Unit = { _, _ -> },
    onSearchEnqueue: (String, String) -> Unit = { _, _ -> },
    onEnterAddress: () -> Unit = {},
    onAllowUnrestrictedBattery: () -> Unit = {},
    imageLoader: ImageLoader,
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
        RemoteScreen(
            state = viewState.remoteScreen,
            isVideoAllowed = viewState.isAwake && !viewState.isIntercomCall,
            onAction = onDocumentAction,
            onVideoFailed = onVideoFailed,
            onSearchQuery = onSearchQuery,
            onSearchPlay = onSearchPlay,
            onSearchEnqueue = onSearchEnqueue,
            imageLoader = imageLoader,
        )
        viewState.infoPanel?.let { infoPanel ->
            InfoPanel(
                state = infoPanel,
                onEnterAddress = onEnterAddress,
                // Lock task hides the system dialog the button opens.
                canOfferUnrestrictedBattery = viewState.isBatteryRestricted && viewState.isKioskWanted != true,
                onAllowUnrestrictedBattery = onAllowUnrestrictedBattery,
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
}

@Composable
private fun RemoteScreen(
    state: RemoteScreenState,
    isVideoAllowed: Boolean,
    onAction: (Slot, Int, String) -> Unit,
    onVideoFailed: (Slot, SlotVideo, Int?) -> Unit,
    onSearchQuery: (String, String) -> Unit,
    onSearchPlay: (String, String) -> Unit,
    onSearchEnqueue: (String, String) -> Unit,
    imageLoader: ImageLoader,
) {
    RemoteDocumentStack(
        state = state,
        isVideoAllowed = isVideoAllowed,
        onAction = onAction,
        onVideoFailed = onVideoFailed,
        onSearchQuery = onSearchQuery,
        onSearchPlay = onSearchPlay,
        onSearchEnqueue = onSearchEnqueue,
        imageLoader = imageLoader,
    )
}

@Composable
private fun PlayerDialogs(
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
    val prompts = remember { PermissionPrompts() }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
        prompts.finished()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onLocationPermissionResult(grants.values.any { it })
        prompts.finished()
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
        prompts.finished()
    }
    val locationAccuracyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onLocationAccuracyResult(result.resultCode == Activity.RESULT_OK) }
    prompts.show = { prompt ->
        when (prompt) {
            PermissionPrompt.Camera -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            PermissionPrompt.Microphone ->
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            PermissionPrompt.Location -> locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    LaunchedEffect(
        viewModel,
        lifecycleOwner,
        prompts,
        locationAccuracyLauncher,
    ) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    PlayerEvent.RequestCameraPermission -> prompts.request(PermissionPrompt.Camera)
                    PlayerEvent.RequestLocationPermission -> prompts.request(PermissionPrompt.Location)
                    PlayerEvent.RequestMicrophonePermission -> prompts.request(PermissionPrompt.Microphone)
                    is PlayerEvent.RequestLocationAccuracy -> runCatching {
                        locationAccuracyLauncher.launch(
                            IntentSenderRequest.Builder(event.intentSender).build(),
                        )
                    }.onFailure { viewModel.onLocationAccuracyResult(false) }
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
        PlayerEvent.RequestCameraPermission,
        PlayerEvent.RequestLocationPermission,
        PlayerEvent.RequestMicrophonePermission,
        -> Unit
        is PlayerEvent.RequestLocationAccuracy -> Unit
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
        RemoteScreen(
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

private fun screenOffAdminIntent(context: Context): Intent {
    val adminComponent = ComponentName(context, ScreenOffAdminReceiver::class.java)
    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.screen_off_explanation),
        )
}
