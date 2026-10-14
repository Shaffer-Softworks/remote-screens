package dev.remotescreens.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
