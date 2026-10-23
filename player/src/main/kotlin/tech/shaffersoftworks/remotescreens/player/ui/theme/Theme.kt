package tech.shaffersoftworks.remotescreens.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

private val LIGHT_COLOR_SCHEME = lightColorScheme(
    primary = Accent,
    background = Page,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
)

private val DARK_COLOR_SCHEME = darkColorScheme(
    primary = Accent,
)

/** The theme of the player's own UI. The documents from the add-on bring their own theme. */
@Composable
fun RemoteScreensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DARK_COLOR_SCHEME else LIGHT_COLOR_SCHEME,
        content = content,
    )
}

@Preview(name = "Light", widthDp = 800, heightDp = 480)
@Composable
private fun RemoteScreensThemeLightPreview() {
    RemoteScreensTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Kitchen", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Preview(name = "Dark", widthDp = 800, heightDp = 480)
@Composable
private fun RemoteScreensThemeDarkPreview() {
    RemoteScreensTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Kitchen", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
