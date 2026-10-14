package dev.remotescreens.server.render

/**
 * The colours of the screens, by what they are for. Code asks for a role, never for a hex value; the values come from
 * the screen's theme (see [Themes]), and `ThemeTest` keeps hex colours out of the other files.
 */
enum class Role {
    PAGE,
    CARD,
    CARD_HEADER,
    CARD_EDGE,
    HEADER,
    HEADER_DARK,
    NAV,
    NAV_ON,
    NAV_LINE,
    INK,
    INK_SOFT,
    INK_DIM,
    INK_FAINT,
    /** Ink drawn on [INK], such as the words of a lit white key. */
    INK_DARK,
    DIVIDER,
    TRACK,
    ACCENT,
    ACCENT_DARK,
    ACCENT_SOFT,
    BLUE,
    HOME,
    HOME_STRIP,
    WEATHER_TITLE,
    STRIP,
    OK,
    WARN,
    DANGER,
    SCRIM,
    KEY,
    KEY_ON,
    KEY_ON_INK,
    ;

    /** The name layouts and custom themes use: `card_header`. */
    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String?): Role? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

/** The accent a tile lights up in when it is on. Keys are stored in layouts: never rename one. */
enum class Pastel(val key: String) {
    YELLOW("yellow"),
    ORANGE("orange"),
    RED("red"),
    PINK("pink"),
    PURPLE("purple"),
    BLUE("blue"),
    TEAL("teal"),
    GREEN("green"),
    GREY("grey"),
    ;

    companion object {
        fun of(key: String?): Pastel? = entries.firstOrNull { it.key == key }

        /** The colour a domain wears when the tile names none, as Home Assistant's own state colours lean. */
        fun forDomain(domain: String?): Pastel = when (domain) {
            "light" -> YELLOW
            "switch", "input_boolean" -> ORANGE
            "fan", "climate" -> TEAL
            "cover" -> PURPLE
            "media_player" -> PINK
            "lock", "alarm_control_panel" -> GREEN
            "scene", "script", "button" -> PURPLE
            "vacuum" -> TEAL
            else -> TEAL
        }
    }
}

/** Every role and pastel of one theme, as ARGB. */
class Palette(private val roles: IntArray, private val pastels: IntArray) {
    init {
        require(roles.size == Role.entries.size && pastels.size == Pastel.entries.size)
    }

    operator fun get(r: Role): Int = roles[r.ordinal]
    operator fun get(p: Pastel): Int = pastels[p.ordinal]

    /** Whether the page is dark, so pictures such as the map pick a matching style. */
    val dark: Boolean get() = luminance(get(Role.PAGE)) < 0.5f

    fun with(roles: Map<Role, Int>, pastels: Map<Pastel, Int>): Palette = Palette(
        IntArray(this.roles.size) { i -> roles[Role.entries[i]] ?: this.roles[i] },
        IntArray(this.pastels.size) { i -> pastels[Pastel.entries[i]] ?: this.pastels[i] },
    )

    private fun luminance(argb: Int): Float {
        val r = (argb shr RED_SHIFT) and CHANNEL_MASK
        val g = (argb shr GREEN_SHIFT) and CHANNEL_MASK
        val b = argb and CHANNEL_MASK
        return (RED_LUMA * r + GREEN_LUMA * g + BLUE_LUMA * b) / CHANNEL_MASK
    }

    private companion object {
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val CHANNEL_MASK = 0xFF

        /** ITU-R BT.601 luma weights. */
        const val RED_LUMA = 0.299f
        const val GREEN_LUMA = 0.587f
        const val BLUE_LUMA = 0.114f
    }
}

/** dp to the pixels of one device. Documents are built for the device that shows them. */
class Units(val density: Float) {
    fun px(dp: Float): Float = dp * density
    fun px(dp: Int): Float = dp * density
    fun sp(sp: Float): Float = sp * density
}
