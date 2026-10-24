package tech.shaffersoftworks.remotescreens.server.render.theme

import tech.shaffersoftworks.remotescreens.shared.Theme
import kotlinx.serialization.Serializable

private const val OPAQUE_ALPHA = 0xFF
private const val OPAQUE_ARGB_PREFIX = 0xFF000000L
private const val RGB_MASK = 0xFFFFFF
private const val ARGB_MASK = 0xFFFFFFFFL
private const val HEX_RGB_LENGTH = 7
private const val ALPHA_SHIFT = 24
private const val HEX_RADIX = 16

/**
 * The built-in themes and the custom ones laid over them.
 * Hex colour values live in Color.kt (see [ThemesTest]).
 */
object Themes {
    const val DEFAULT = "rpi"

    /** The RPI wall panel's dark greys, teal and blue. */
    val RPI: Palette = palette(
        roles = mapOf(
            Role.PAGE to Rpi.Page,
            Role.CARD to Rpi.Card,
            Role.CARD_HEADER to Rpi.CardHeader,
            Role.CARD_EDGE to Rpi.CardEdge,
            Role.HEADER to Rpi.Header,
            Role.HEADER_DARK to Rpi.HeaderDark,
            Role.NAV to Rpi.Nav,
            Role.NAV_ON to Rpi.NavOn,
            Role.NAV_LINE to Rpi.NavLine,
            Role.INK to Rpi.Ink,
            Role.INK_SOFT to Rpi.InkSoft,
            Role.INK_DIM to Rpi.InkDim,
            Role.INK_FAINT to Rpi.InkFaint,
            Role.INK_DARK to Rpi.InkDark,
            Role.DIVIDER to Rpi.Divider,
            Role.TRACK to Rpi.Track,
            Role.ACCENT to Rpi.Accent,
            Role.ACCENT_DARK to Rpi.AccentDark,
            Role.ACCENT_SOFT to Rpi.AccentSoft,
            Role.BLUE to Rpi.Blue,
            Role.HOME to Rpi.Home,
            Role.HOME_STRIP to Rpi.HomeStrip,
            Role.WEATHER_TITLE to Rpi.WeatherTitle,
            Role.STRIP to Rpi.Strip,
            Role.OK to Rpi.Ok,
            Role.WARN to Rpi.Warn,
            Role.DANGER to Rpi.Danger,
            Role.SCRIM to Rpi.Scrim,
            Role.KEY to Rpi.Key,
            Role.KEY_ON to Rpi.KeyOn,
            Role.KEY_ON_INK to Rpi.KeyOnInk,
        ),
        pastels = mapOf(
            Pastel.YELLOW to Rpi.PastelYellow,
            Pastel.ORANGE to Rpi.PastelOrange,
            Pastel.RED to Rpi.PastelRed,
            Pastel.PINK to Rpi.PastelPink,
            Pastel.PURPLE to Rpi.PastelPurple,
            Pastel.BLUE to Rpi.PastelBlue,
            Pastel.TEAL to Rpi.PastelTeal,
            Pastel.GREEN to Rpi.PastelGreen,
            Pastel.GREY to Rpi.PastelGrey,
        ),
    )

    /** Warm light greys and white cards with dark ink; deeper accents so they read on white. */
    val LIGHT: Palette = RPI.over(
        roles = mapOf(
            Role.PAGE to Light.Page,
            Role.CARD to Light.Card,
            Role.CARD_HEADER to Light.CardHeader,
            Role.CARD_EDGE to Light.CardEdge,
            Role.HEADER to Light.Header,
            Role.HEADER_DARK to Light.HeaderDark,
            Role.NAV to Light.Nav,
            Role.NAV_ON to Light.NavOn,
            Role.NAV_LINE to Light.NavLine,
            Role.INK to Light.Ink,
            Role.INK_SOFT to Light.InkSoft,
            Role.INK_DIM to Light.InkDim,
            Role.INK_FAINT to Light.InkFaint,
            Role.INK_DARK to Light.InkDark,
            Role.DIVIDER to Light.Divider,
            Role.TRACK to Light.Track,
            Role.ACCENT to Light.Accent,
            Role.ACCENT_DARK to Light.AccentDark,
            Role.ACCENT_SOFT to Light.AccentSoft,
            Role.BLUE to Light.Blue,
            Role.HOME to Light.Home,
            Role.HOME_STRIP to Light.HomeStrip,
            Role.WEATHER_TITLE to Light.WeatherTitle,
            Role.STRIP to Light.Strip,
            Role.OK to Light.Ok,
            Role.WARN to Light.Warn,
            Role.DANGER to Light.Danger,
            Role.SCRIM to Light.Scrim,
            Role.KEY to Light.Key,
            Role.KEY_ON to Light.KeyOn,
            Role.KEY_ON_INK to Light.KeyOnInk,
        ),
        pastels = mapOf(
            Pastel.YELLOW to Light.PastelYellow,
            Pastel.ORANGE to Light.PastelOrange,
            Pastel.RED to Light.PastelRed,
            Pastel.PINK to Light.PastelPink,
            Pastel.PURPLE to Light.PastelPurple,
            Pastel.BLUE to Light.PastelBlue,
            Pastel.TEAL to Light.PastelTeal,
            Pastel.GREEN to Light.PastelGreen,
            Pastel.GREY to Light.PastelGrey,
        ),
    )

    /** RPI on true black, so an OLED panel lights only what is drawn. */
    val OLED: Palette = RPI.over(
        roles = mapOf(
            Role.PAGE to Oled.Page,
            Role.CARD to Oled.Card,
            Role.CARD_HEADER to Oled.CardHeader,
            Role.CARD_EDGE to Oled.CardEdge,
            Role.HEADER to Oled.Header,
            Role.HEADER_DARK to Oled.HeaderDark,
            Role.NAV to Oled.Nav,
            Role.DIVIDER to Oled.Divider,
            Role.TRACK to Oled.Track,
            Role.STRIP to Oled.Strip,
            Role.KEY to Oled.Key,
            Role.INK_FAINT to Oled.InkFaint,
        ),
        pastels = emptyMap(),
    )

    /** Black and white with saturated accents, for reading across a room. */
    val CONTRAST: Palette = RPI.over(
        roles = mapOf(
            Role.PAGE to Contrast.Page,
            Role.CARD to Contrast.Card,
            Role.CARD_HEADER to Contrast.CardHeader,
            Role.CARD_EDGE to Contrast.CardEdge,
            Role.HEADER to Contrast.Header,
            Role.HEADER_DARK to Contrast.HeaderDark,
            Role.NAV to Contrast.Nav,
            Role.NAV_ON to Contrast.NavOn,
            Role.NAV_LINE to Contrast.NavLine,
            Role.INK to Contrast.Ink,
            Role.INK_SOFT to Contrast.InkSoft,
            Role.INK_DIM to Contrast.InkDim,
            Role.INK_FAINT to Contrast.InkFaint,
            Role.DIVIDER to Contrast.Divider,
            Role.TRACK to Contrast.Track,
            Role.ACCENT to Contrast.Accent,
            Role.ACCENT_DARK to Contrast.AccentDark,
            Role.ACCENT_SOFT to Contrast.AccentSoft,
            Role.BLUE to Contrast.Blue,
            Role.HOME to Contrast.Home,
            Role.OK to Contrast.Ok,
            Role.WARN to Contrast.Warn,
            Role.DANGER to Contrast.Danger,
            Role.KEY to Contrast.Key,
            Role.KEY_ON to Contrast.KeyOn,
            Role.KEY_ON_INK to Contrast.KeyOnInk,
        ),
        pastels = mapOf(
            Pastel.YELLOW to Contrast.PastelYellow,
            Pastel.ORANGE to Contrast.PastelOrange,
            Pastel.RED to Contrast.PastelRed,
            Pastel.PINK to Contrast.PastelPink,
            Pastel.PURPLE to Contrast.PastelPurple,
            Pastel.BLUE to Contrast.PastelBlue,
            Pastel.TEAL to Contrast.PastelTeal,
            Pastel.GREEN to Contrast.PastelGreen,
            Pastel.GREY to Contrast.PastelGrey,
        ),
    )

    /** Built-in id to its name and palette, in the order the editor lists them. */
    val BUILTIN: Map<String, Pair<String, Palette>> = linkedMapOf(
        DEFAULT to ("RPI dark" to RPI),
        "light" to ("Light" to LIGHT),
        "oled" to ("OLED black" to OLED),
        "contrast" to ("High contrast" to CONTRAST),
    )

    fun isBuiltin(id: String): Boolean = id in BUILTIN

    /** The palette of theme [id], a custom one laid over its base; the default for an id nobody knows. */
    fun resolve(id: String?, custom: List<Theme>): Palette =
        BUILTIN[id]?.second ?: custom.firstOrNull { it.id == id }?.let(::over) ?: RPI

    private fun over(theme: Theme): Palette {
        val base = BUILTIN[theme.base]?.second ?: RPI
        val roles = theme.roles.mapNotNull { (k, v) -> Role.of(
            k,
        )?.let { r -> parse(v)?.let { r to it } } }.toMap()
        val pastels = theme.pastels.mapNotNull { (k, v) -> Pastel.of(
            k,
        )?.let { p -> parse(v)?.let { p to it } } }.toMap()
        return base.with(roles, pastels)
    }

    /** Every theme, built-in first, as the editor draws them. */
    fun all(custom: List<Theme>): List<ResolvedTheme> =
        BUILTIN.map { (id, v) -> resolved(id, v.first, id, true, v.second, Theme(id, v.first)) } +
            custom.map { resolved(it.id, it.name, it.base, false, over(it), it) }

    private fun resolved(
        id: String,
        name: String,
        base: String,
        builtin: Boolean,
        palette: Palette,
        own: Theme,
    ): ResolvedTheme = ResolvedTheme(
        id = id,
        name = name,
        base = base,
        builtin = builtin,
        dark = palette.dark,
        roles = Role.entries.associate { it.key to hex(palette[it]) },
        pastels = Pastel.entries.associate { it.key to hex(palette[it]) },
        overrides = Overrides(own.roles, own.pastels),
    )

    /** `#RRGGBB` or `#AARRGGBB` to ARGB, or null for anything else. */
    fun parse(hex: String): Int? {
        if (!Theme.HEX.matches(hex)) return null
        val value = hex.substring(1).toLong(HEX_RADIX)
        return (if (hex.length == HEX_RGB_LENGTH) value or OPAQUE_ARGB_PREFIX else value).toInt()
    }

    fun hex(argb: Int): String {
        val alpha = (argb ushr ALPHA_SHIFT) and OPAQUE_ALPHA
        return if (alpha == OPAQUE_ALPHA) {
            "#%06X".format(argb and RGB_MASK)
        } else {
            "#%08X".format(argb.toLong() and ARGB_MASK)
        }
    }

    private fun palette(roles: Map<Role, Int>, pastels: Map<Pastel, Int>): Palette {
        require(
            roles.size == Role.entries.size && pastels.size == Pastel.entries.size,
        ) { "a built-in theme names every colour" }
        return Palette(
            IntArray(Role.entries.size) { roles.getValue(Role.entries[it]) },
            IntArray(Pastel.entries.size) { pastels.getValue(Pastel.entries[it]) },
        )
    }

    private fun Palette.over(roles: Map<Role, Int>, pastels: Map<Pastel, Int>): Palette =
        with(roles, pastels)
}

@Serializable
data class ResolvedTheme(
    val id: String,
    val name: String,
    val base: String,
    val builtin: Boolean,
    val dark: Boolean,
    val roles: Map<String, String>,
    val pastels: Map<String, String>,
    val overrides: Overrides,
)

@Serializable
data class Overrides(val roles: Map<String, String>, val pastels: Map<String, String>)
