package tech.shaffersoftworks.remotescreens.server.render

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
 * The built-in themes and the custom ones laid over them. The only file that holds hex colours (see [ThemesTest]).
 */
@Suppress("MatchingDeclarationName")
object Themes {
    const val DEFAULT = "rpi"

    /** The RPI wall panel's dark greys, teal and blue. */
    val RPI: Palette = palette(
        roles = mapOf(
            Role.PAGE to 0xFF494949, Role.CARD to 0xFF333333, Role.CARD_HEADER to 0xFF494949, Role.CARD_EDGE to 0xFF000000,
            Role.HEADER to 0xFF494949, Role.HEADER_DARK to 0xFF2F2E2E, Role.NAV to 0xFF2F2E2E, Role.NAV_ON to 0xFF14E1BA,
            Role.NAV_LINE to 0xFF03DAC5, Role.INK to 0xFFFFFFFF, Role.INK_SOFT to 0xFFCACACA, Role.INK_DIM to 0xFF939393,
            Role.INK_FAINT to 0xFF5C5C5C, Role.INK_DARK to 0xFF000000, Role.DIVIDER to 0xFF4A4A4A, Role.TRACK to 0xFF1B1B1B,
            Role.ACCENT to 0xFF14E1BA, Role.ACCENT_DARK to 0xFF1DA48C, Role.ACCENT_SOFT to 0xFF03DAC5, Role.BLUE to 0xFF0986DB,
            Role.HOME to 0xFF14E1BA, Role.HOME_STRIP to 0xFFFFFFFF, Role.WEATHER_TITLE to 0xFFFFBB33, Role.STRIP to 0xFF3B3B3B,
            Role.OK to 0xFF289907, Role.WARN to 0xFFF57C00, Role.DANGER to 0xFFA21C1C, Role.SCRIM to 0xCC000000,
            Role.KEY to 0xFF5C5C5C, Role.KEY_ON to 0xFF14E1BA, Role.KEY_ON_INK to 0xFF2F2E2E,
        ),
        pastels = mapOf(
            Pastel.YELLOW to 0xFFFFC94D, Pastel.ORANGE to 0xFFFF9800, Pastel.RED to 0xFFFF6E6E, Pastel.PINK to 0xFFF48FB1,
            Pastel.PURPLE to 0xFFB39DDB, Pastel.BLUE to 0xFF0986DB, Pastel.TEAL to 0xFF14E1BA, Pastel.GREEN to 0xFF5CCB5F,
            Pastel.GREY to 0xFFCACACA,
        ),
    )

    /** Warm light greys and white cards with dark ink; deeper accents so they read on white. */
    val LIGHT: Palette = RPI.over(
        roles = mapOf(
            Role.PAGE to 0xFFEDEBE7, Role.CARD to 0xFFFFFFFF, Role.CARD_HEADER to 0xFFF2F1EE, Role.CARD_EDGE to 0xFFD6D4CF,
            Role.HEADER to 0xFFFFFFFF, Role.HEADER_DARK to 0xFFE3E1DC, Role.NAV to 0xFFFFFFFF, Role.NAV_ON to 0xFF0F9E84,
            Role.NAV_LINE to 0xFF0F9E84, Role.INK to 0xFF1D1D1F, Role.INK_SOFT to 0xFF4A4A50, Role.INK_DIM to 0xFF75757A,
            Role.INK_FAINT to 0xFFB5B5B8, Role.INK_DARK to 0xFFFFFFFF, Role.DIVIDER to 0xFFDDDBD6, Role.TRACK to 0xFFE4E2DD,
            Role.ACCENT to 0xFF0F9E84, Role.ACCENT_DARK to 0xFF0B7F6A, Role.ACCENT_SOFT to 0xFF12A891, Role.BLUE to 0xFF0A6FC2,
            Role.HOME to 0xFF0F9E84, Role.HOME_STRIP to 0xFF1D1D1F, Role.WEATHER_TITLE to 0xFFC77700, Role.STRIP to 0xFFE9E7E2,
            Role.OK to 0xFF2E8B0F, Role.WARN to 0xFFD96A00, Role.DANGER to 0xFFB3261E, Role.SCRIM to 0x99000000,
            Role.KEY to 0xFFE6E4DF, Role.KEY_ON to 0xFF0F9E84, Role.KEY_ON_INK to 0xFF1D1D1F,
        ),
        pastels = mapOf(
            Pastel.YELLOW to 0xFFE0A800, Pastel.ORANGE to 0xFFF08000, Pastel.RED to 0xFFE5484D, Pastel.PINK to 0xFFD9578A,
            Pastel.PURPLE to 0xFF8E6CC9, Pastel.BLUE to 0xFF0A6FC2, Pastel.TEAL to 0xFF0F9E84, Pastel.GREEN to 0xFF3BA33F,
            Pastel.GREY to 0xFF8A8A8F,
        ),
    )

    /** RPI on true black, so an OLED panel lights only what is drawn. */
    val OLED: Palette = RPI.over(
        roles = mapOf(
            Role.PAGE to 0xFF000000, Role.CARD to 0xFF0E0E0E, Role.CARD_HEADER to 0xFF1A1A1A, Role.CARD_EDGE to 0xFF000000,
            Role.HEADER to 0xFF000000, Role.HEADER_DARK to 0xFF000000, Role.NAV to 0xFF000000, Role.DIVIDER to 0xFF262626,
            Role.TRACK to 0xFF141414, Role.STRIP to 0xFF121212, Role.KEY to 0xFF262626, Role.INK_FAINT to 0xFF4A4A4A,
        ),
        pastels = emptyMap(),
    )

    /** Black and white with saturated accents, for reading across a room. */
    val CONTRAST: Palette = RPI.over(
        roles = mapOf(
            Role.PAGE to 0xFF000000, Role.CARD to 0xFF000000, Role.CARD_HEADER to 0xFF1F1F1F, Role.CARD_EDGE to 0xFFFFFFFF,
            Role.HEADER to 0xFF000000, Role.HEADER_DARK to 0xFF000000, Role.NAV to 0xFF000000, Role.NAV_ON to 0xFF005FCC,
            Role.NAV_LINE to 0xFFFFFFFF, Role.INK to 0xFFFFFFFF, Role.INK_SOFT to 0xFFFFFFFF, Role.INK_DIM to 0xFFE0E0E0,
            Role.INK_FAINT to 0xFF9E9E9E, Role.DIVIDER to 0xFFFFFFFF, Role.TRACK to 0xFF333333, Role.ACCENT to 0xFF00FFD0,
            Role.ACCENT_DARK to 0xFF00C8A4, Role.ACCENT_SOFT to 0xFF00FFD0, Role.BLUE to 0xFF4DB2FF, Role.HOME to 0xFF00FFD0,
            Role.OK to 0xFF4CFF4C, Role.WARN to 0xFFFF9F1A, Role.DANGER to 0xFFFF4D4D, Role.KEY to 0xFF3A3A3A,
            Role.KEY_ON to 0xFFFFE600, Role.KEY_ON_INK to 0xFF000000,
        ),
        pastels = mapOf(
            Pastel.YELLOW to 0xFFFFE600, Pastel.ORANGE to 0xFFFF9F1A, Pastel.RED to 0xFFFF4D4D, Pastel.PINK to 0xFFFF66B3,
            Pastel.PURPLE to 0xFFC599FF, Pastel.BLUE to 0xFF4DB2FF, Pastel.TEAL to 0xFF00FFD0, Pastel.GREEN to 0xFF4CFF4C,
            Pastel.GREY to 0xFFFFFFFF,
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
        val roles = theme.roles.mapNotNull { (k, v) -> Role.of(k)?.let { r -> parse(v)?.let { r to it } } }.toMap()
        val pastels = theme.pastels.mapNotNull { (k, v) -> Pastel.of(k)?.let { p -> parse(v)?.let { p to it } } }.toMap()
        return base.with(roles, pastels)
    }

    /** Every theme, built-in first, as the editor draws them. */
    fun all(custom: List<Theme>): List<ResolvedTheme> =
        BUILTIN.map { (id, v) -> resolved(id, v.first, id, true, v.second, Theme(id, v.first)) } +
            custom.map { resolved(it.id, it.name, it.base, false, over(it), it) }

    private fun resolved(id: String, name: String, base: String, builtin: Boolean, p: Palette, own: Theme) = ResolvedTheme(
        id = id,
        name = name,
        base = base,
        builtin = builtin,
        dark = p.dark,
        roles = Role.entries.associate { it.key to hex(p[it]) },
        pastels = Pastel.entries.associate { it.key to hex(p[it]) },
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

    private fun palette(roles: Map<Role, Long>, pastels: Map<Pastel, Long>): Palette {
        require(roles.size == Role.entries.size && pastels.size == Pastel.entries.size) { "a built-in theme names every colour" }
        return Palette(IntArray(Role.entries.size) { roles.getValue(Role.entries[it]).toInt() }, IntArray(Pastel.entries.size) { pastels.getValue(Pastel.entries[it]).toInt() })
    }

    private fun Palette.over(roles: Map<Role, Long>, pastels: Map<Pastel, Long>): Palette =
        with(roles.mapValues { it.value.toInt() }, pastels.mapValues { it.value.toInt() })
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
