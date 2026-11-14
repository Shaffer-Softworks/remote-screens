package tech.shaffersoftworks.remotescreens.player.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.R
import tech.shaffersoftworks.remotescreens.player.ui.theme.DialogBackground
import tech.shaffersoftworks.remotescreens.player.ui.theme.DialogTeal
import tech.shaffersoftworks.remotescreens.player.ui.theme.DialogWhite
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.player.ui.theme.WarningAmber

private val SNACKBAR_HORIZONTAL = 16.dp
private val SNACKBAR_BOTTOM = 24.dp
private val SNACKBAR_RADIUS = 12.dp
private val WARNING_DOT = 10.dp

/** Small bar above the nav while a document stays up and the add-on link is down. */
@Composable
fun DisconnectedSnackbar(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .padding(start = SNACKBAR_HORIZONTAL, end = SNACKBAR_HORIZONTAL, bottom = SNACKBAR_BOTTOM)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(SNACKBAR_RADIUS),
            color = DialogBackground,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(WARNING_DOT)
                        .clip(CircleShape)
                        .background(WarningAmber),
                )
                Text(
                    text = stringResource(R.string.disconnected),
                    color = DialogWhite,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.retry),
                        color = DialogTeal,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun DisconnectedSnackbarPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxSize()) {
            DisconnectedSnackbar(onRetry = {})
        }
    }
}
