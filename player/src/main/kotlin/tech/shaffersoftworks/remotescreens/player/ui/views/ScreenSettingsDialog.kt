package tech.shaffersoftworks.remotescreens.player.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.KioskAction
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import kotlin.math.roundToInt

private const val MIN_BRIGHTNESS_PERCENT = 1
private const val MAX_BRIGHTNESS_PERCENT = 100
private val STANDBY_MINUTE_CHOICES = listOf(0, 1, 5, 10, 30, 60)
private val SECTION_SPACING = 8.dp

/** The settings a person can change on the screen itself. Brightness changes are previewed while sliding. */
@Composable
fun ScreenSettingsDialog(
    settings: ScreenSettings,
    server: String?,
    canOfferSelfStart: Boolean,
    canOfferScreenOff: Boolean,
    canOfferUnrestrictedBattery: Boolean,
    canOfferCameraPermission: Boolean = false,
    wakeOnFaceUnavailableNote: Int? = null,
    kioskAction: KioskAction?,
    canRemoveDeviceOwner: Boolean,
    onBrightnessPreview: (Int) -> Unit,
    onConfirm: (brightnessPercent: Int, isDarkMode: Boolean, standbyMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
    onChangeServer: () -> Unit,
    onForget: () -> Unit,
    onAllowSelfStart: () -> Unit,
    onAllowScreenOff: () -> Unit,
    onAllowUnrestrictedBattery: () -> Unit,
    onAllowCameraPermission: () -> Unit = {},
    onUnlockKiosk: () -> Unit,
    onLockKioskAgain: () -> Unit,
    onRemoveDeviceOwner: () -> Unit,
) {
    var brightnessPercent by remember(settings) {
        mutableFloatStateOf(settings.brightness.coerceIn(MIN_BRIGHTNESS_PERCENT, MAX_BRIGHTNESS_PERCENT).toFloat())
    }
    var isDarkMode by remember(settings) { mutableStateOf(settings.darkMode) }
    var standbyChoiceIndex by remember(settings) {
        mutableIntStateOf(
            STANDBY_MINUTE_CHOICES.indexOfFirst { it >= settings.standbyMinutes }
                .takeIf { it >= 0 } ?: STANDBY_MINUTE_CHOICES.lastIndex,
        )
    }
    val standbyMinutes = STANDBY_MINUTE_CHOICES[standbyChoiceIndex]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            ) {
                Text(stringResource(R.string.brightness))
                Slider(
                    value = brightnessPercent,
                    onValueChange = { value ->
                        brightnessPercent = value
                        onBrightnessPreview(value.roundToInt())
                    },
                    valueRange = MIN_BRIGHTNESS_PERCENT.toFloat()..MAX_BRIGHTNESS_PERCENT.toFloat(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.dark_mode))
                    Switch(checked = isDarkMode, onCheckedChange = { enabled -> isDarkMode = enabled })
                }
                Text(stringResource(R.string.standby, standbyLabel(standbyMinutes)))
                Slider(
                    value = standbyChoiceIndex.toFloat(),
                    onValueChange = { value -> standbyChoiceIndex = value.roundToInt() },
                    valueRange = 0f..STANDBY_MINUTE_CHOICES.lastIndex.toFloat(),
                    steps = STANDBY_MINUTE_CHOICES.size - 2,
                )
                Text(
                    text = stringResource(R.string.server, server ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onChangeServer) { Text(stringResource(R.string.change_server)) }
                TextButton(onClick = onForget) { Text(stringResource(R.string.forget)) }
                if (canOfferSelfStart) {
                    TextButton(onClick = onAllowSelfStart) { Text(stringResource(R.string.allow_self_start)) }
                }
                if (canOfferScreenOff) {
                    TextButton(onClick = onAllowScreenOff) { Text(stringResource(R.string.allow_screen_off)) }
                }
                if (canOfferUnrestrictedBattery) {
                    TextButton(onClick = onAllowUnrestrictedBattery) {
                        Text(stringResource(R.string.allow_unrestricted_battery))
                    }
                }
                if (canOfferCameraPermission) {
                    TextButton(onClick = onAllowCameraPermission) {
                        Text(stringResource(R.string.allow_camera_for_face_wake))
                    }
                }
                wakeOnFaceUnavailableNote?.let { noteRes ->
                    Text(
                        text = stringResource(noteRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (kioskAction) {
                    KioskAction.UNLOCK -> TextButton(onClick = onUnlockKiosk) {
                        Text(stringResource(R.string.kiosk_unlock))
                    }
                    KioskAction.LOCK_AGAIN -> TextButton(onClick = onLockKioskAgain) {
                        Text(stringResource(R.string.kiosk_lock_again))
                    }
                    null -> Unit
                }
                if (canRemoveDeviceOwner) {
                    TextButton(onClick = onRemoveDeviceOwner) {
                        Text(stringResource(R.string.kiosk_remove_device_owner))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(brightnessPercent.roundToInt(), isDarkMode, standbyMinutes) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun standbyLabel(standbyMinutes: Int): String =
    if (standbyMinutes == 0) stringResource(R.string.never) else stringResource(R.string.minutes, standbyMinutes)

@Composable
fun ForgetScreenDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.forget_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.forget)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Preview
@Composable
private fun ScreenSettingsDialogPreview() {
    RemoteScreensTheme {
        ScreenSettingsDialog(
            settings = ScreenSettings(),
            server = "192.168.1.10:8100",
            canOfferSelfStart = true,
            canOfferScreenOff = true,
            canOfferUnrestrictedBattery = true,
            canOfferCameraPermission = true,
            kioskAction = KioskAction.UNLOCK,
            canRemoveDeviceOwner = false,
            onBrightnessPreview = {},
            onConfirm = { _, _, _ -> },
            onDismiss = {},
            onChangeServer = {},
            onForget = {},
            onAllowSelfStart = {},
            onAllowScreenOff = {},
            onAllowUnrestrictedBattery = {},
            onAllowCameraPermission = {},
            onUnlockKiosk = {},
            onLockKioskAgain = {},
            onRemoveDeviceOwner = {},
        )
    }
}
