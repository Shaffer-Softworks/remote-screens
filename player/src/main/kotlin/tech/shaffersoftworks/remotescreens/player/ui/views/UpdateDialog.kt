package tech.shaffersoftworks.remotescreens.player.ui.views

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme

/** Offers a newer player app from the add-on. */
@Composable
fun UpdateDialog(
    versionName: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.update_ready, versionName)) },
        confirmButton = { TextButton(
            onClick = onInstall,
        ) { Text(stringResource(R.string.install)) } },
        dismissButton = { TextButton(
            onClick = onDismiss,
        ) { Text(stringResource(R.string.later)) } },
    )
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun UpdateDialogPreview() {
    RemoteScreensTheme { UpdateDialog(versionName = "0.1.3", onInstall = {}, onDismiss = {}) }
}
