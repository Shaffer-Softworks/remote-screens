package dev.remotescreens.player.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import dev.remotescreens.player.R
import dev.remotescreens.player.ui.theme.IdentifyBadgeBackground

private val BADGE_TEXT_SIZE = 40.sp

/** "This is Kitchen", big over everything, so the screen can be found from Home Assistant. */
@Composable
fun IdentifyBadge(screenName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IdentifyBadgeBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.identify, screenName),
            color = Color.White,
            fontSize = BADGE_TEXT_SIZE,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun IdentifyBadgePreview() {
    IdentifyBadge(screenName = "Kitchen")
}
