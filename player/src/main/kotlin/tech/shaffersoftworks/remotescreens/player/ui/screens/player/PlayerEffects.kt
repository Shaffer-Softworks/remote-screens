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
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.receiver.ScreenOffAdminReceiver
import tech.shaffersoftworks.remotescreens.player.utils.applyScreenOrientation
import tech.shaffersoftworks.remotescreens.player.utils.hideSystemBars
import tech.shaffersoftworks.remotescreens.player.utils.setWindowBrightness

/** Brightness, orientation and hidden system bars belong to the window, so they are applied from here. */
@Composable
internal fun PlayerWindowEffects(viewState: PlayerViewModel.ViewState) {
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
internal fun PlayerEvents(viewModel: PlayerViewModel) {
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

private fun screenOffAdminIntent(context: Context): Intent {
    val adminComponent = ComponentName(context, ScreenOffAdminReceiver::class.java)
    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.screen_off_explanation),
        )
}
