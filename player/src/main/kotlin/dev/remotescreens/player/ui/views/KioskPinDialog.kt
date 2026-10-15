package dev.remotescreens.player.ui.views

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import dev.remotescreens.player.R
import dev.remotescreens.player.ui.theme.RemoteScreensTheme
import dev.remotescreens.shared.ScreenSettings

private const val MAX_KIOSK_PIN_LENGTH = 8

/** Asked before the settings of a locked kiosk open. */
@Composable
fun KioskPinDialog(onConfirm: (pin: String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kiosk_pin_title)) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { input -> pin = input.filter(Char::isDigit).take(MAX_KIOSK_PIN_LENGTH) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm(pin) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = ScreenSettings.KIOSK_PIN.matches(pin)) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview
@Composable
private fun KioskPinDialogPreview() {
    RemoteScreensTheme {
        KioskPinDialog(onConfirm = {}, onDismiss = {})
    }
}
