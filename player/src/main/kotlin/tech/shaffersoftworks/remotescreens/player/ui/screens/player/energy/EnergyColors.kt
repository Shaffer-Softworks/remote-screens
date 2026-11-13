package tech.shaffersoftworks.remotescreens.player.ui.screens.player.energy

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import tech.shaffersoftworks.remotescreens.shared.EnergyDashboardTheme

internal data class EnergyColors(
    val page: Color,
    val card: Color,
    val ink: Color,
    val inkDim: Color,
    val accent: Color,
    val track: Color,
)

/** Falls back to [EnergyDashboardTheme] defaults when an older add-on omits the theme. */
internal val LocalEnergyColors = staticCompositionLocalOf {
    EnergyDashboardTheme().toEnergyColors()
}

internal fun EnergyDashboardTheme.toEnergyColors(): EnergyColors = EnergyColors(
    page = Color(page.toInt()),
    card = Color(card.toInt()),
    ink = Color(ink.toInt()),
    inkDim = Color(inkDim.toInt()),
    accent = Color(accent.toInt()),
    track = Color(track.toInt()),
)
