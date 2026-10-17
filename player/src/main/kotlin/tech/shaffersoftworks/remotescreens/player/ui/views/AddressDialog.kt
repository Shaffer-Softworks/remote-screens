package tech.shaffersoftworks.remotescreens.player.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme

private val CONTENT_SPACING = 12.dp

/** Lets a person type the add-on's address when it cannot be found on the network. */
@Composable
fun AddressDialog(
    initialAddress: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.address_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CONTENT_SPACING)) {
                Text(stringResource(R.string.address_body))
                OutlinedTextField(
                    value = address,
                    onValueChange = { value -> address = value },
                    placeholder = { Text(stringResource(R.string.address_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(address) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(address) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun AddressDialogPreview() {
    RemoteScreensTheme { AddressDialog(initialAddress = "", onConfirm = {}, onDismiss = {}) }
}
