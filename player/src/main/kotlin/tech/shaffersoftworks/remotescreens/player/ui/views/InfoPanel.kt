package tech.shaffersoftworks.remotescreens.player.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.ui.screens.player.InfoPanelState
import tech.shaffersoftworks.remotescreens.player.ui.theme.Accent
import tech.shaffersoftworks.remotescreens.player.ui.theme.Ink
import tech.shaffersoftworks.remotescreens.player.ui.theme.InkSoft
import tech.shaffersoftworks.remotescreens.player.ui.theme.Page
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme

private val PANEL_PADDING = 32.dp
private val CODE_VERTICAL_PADDING = 16.dp
private val TITLE_TEXT_SIZE = 28.sp
private val CODE_TEXT_SIZE = 72.sp
private val BODY_TEXT_SIZE = 18.sp
private val NOTE_TEXT_SIZE = 14.sp
private val CODE_LETTER_SPACING = 0.1.em

/** What the screen shows while there is no document yet: searching, connecting, or the code to pair with. */
@Composable
fun InfoPanel(
    state: InfoPanelState,
    onEnterAddress: () -> Unit,
    modifier: Modifier = Modifier,
    canOfferUnrestrictedBattery: Boolean = false,
    onAllowUnrestrictedBattery: () -> Unit = {},
) {
    val pairingCode = (state as? InfoPanelState.Pairing)?.code
    val title = if (pairingCode != null) {
        stringResource(
            R.string.pair_title,
        )
    } else {
        stringResource(R.string.app_name)
    }
    val body = when (state) {
        InfoPanelState.Searching -> stringResource(R.string.searching)
        is InfoPanelState.Connecting -> stringResource(R.string.connecting, state.server)
        InfoPanelState.Disconnected -> stringResource(R.string.disconnected)
        is InfoPanelState.Pairing -> stringResource(R.string.pair_body)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Page)
            .padding(PANEL_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, color = Ink, fontSize = TITLE_TEXT_SIZE, textAlign = TextAlign.Center)
        if (pairingCode != null) {
            Text(
                text = pairingCode,
                color = Accent,
                fontSize = CODE_TEXT_SIZE,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = CODE_LETTER_SPACING,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = CODE_VERTICAL_PADDING),
            )
        }
        Text(text = body, color = InkSoft, fontSize = BODY_TEXT_SIZE, textAlign = TextAlign.Center)
        if (pairingCode == null) {
            Spacer(Modifier.height(PANEL_PADDING))
            Button(onClick = onEnterAddress) { Text(stringResource(R.string.enter_address)) }
        }
        if (canOfferUnrestrictedBattery) {
            Spacer(Modifier.height(PANEL_PADDING))
            Text(
                text = stringResource(R.string.battery_restricted),
                color = InkSoft,
                fontSize = NOTE_TEXT_SIZE,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onAllowUnrestrictedBattery) {
                Text(stringResource(R.string.allow_unrestricted_battery))
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun InfoPanelPairingPreview() {
    RemoteScreensTheme { InfoPanel(state = InfoPanelState.Pairing("123 456"), onEnterAddress = {}) }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun InfoPanelSearchingPreview() {
    RemoteScreensTheme {
        InfoPanel(
            state = InfoPanelState.Searching,
            onEnterAddress = {},
            canOfferUnrestrictedBattery = true,
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun InfoPanelConnectingPreview() {
    RemoteScreensTheme {
        InfoPanel(
            state = InfoPanelState.Connecting(server = "192.168.1.10:8100"),
            onEnterAddress = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun InfoPanelDisconnectedPreview() {
    RemoteScreensTheme {
        InfoPanel(
            state = InfoPanelState.Disconnected,
            onEnterAddress = {},
        )
    }
}
