package dev.remotescreens.server.render

/**
 * Every colour of the screens, in one table. Each role has a light and a dark value. Code asks for a role, never for
 * a hex value; `ThemeTest` keeps hex colours out of the other files.
 */
enum class Role(val light: Long, val dark: Long) {
    PAGE(0xFFF3F1EC, 0xFF0E0F11),
    CARD(0xFFFFFFFF, 0xFF1C1E22),
    CARD_OFF(0xFFFFFFFF, 0xFF1C1E22),
    INK(0xFF1D1D1F, 0xFFECECEF),
    INK_SOFT(0xFF6E6E73, 0xFF9A9AA2),
    INK_FAINT(0xFFB8B8BD, 0xFF4A4C52),
    DIVIDER(0xFFE6E3DC, 0xFF2A2C31),
    ACCENT(0xFF3A6FF7, 0xFF7BA2FF),
    OK(0xFF2E9E5B, 0xFF5CCB88),
    WARN(0xFFE08A1E, 0xFFF0AE55),
    DANGER(0xFFD64545, 0xFFFF7A7A),
    SCRIM(0x99000000, 0xCC000000),
    KEY(0xFFF0EEE9, 0xFF2A2C31),
    KEY_ON(0xFF1D1D1F, 0xFFECECEF),
    KEY_ON_INK(0xFFFFFFFF, 0xFF111214),
    ;

    fun argb(dark: Boolean): Int = (if (dark) this.dark else light).toInt()
}

/** The pastel a tile wears when it is on. Keys are stored in layouts: never rename one. */
enum class Pastel(val key: String, val light: Long, val dark: Long, val inkLight: Long, val inkDark: Long) {
    YELLOW("yellow", 0xFFFFE9A8, 0xFF5C4A12, 0xFF3D2F00, 0xFFFFE9A8),
    ORANGE("orange", 0xFFFFD3AE, 0xFF5E3414, 0xFF4A2200, 0xFFFFD3AE),
    RED("red", 0xFFFFC4C0, 0xFF5E1F1C, 0xFF4D0A06, 0xFFFFC4C0),
    PINK("pink", 0xFFFAC8E3, 0xFF5A2140, 0xFF4A0B2D, 0xFFFAC8E3),
    PURPLE("purple", 0xFFDCCBFA, 0xFF3D2B63, 0xFF2A1458, 0xFFDCCBFA),
    BLUE("blue", 0xFFC4DBFF, 0xFF1E3A66, 0xFF0B2A5C, 0xFFC4DBFF),
    TEAL("teal", 0xFFBDEBE4, 0xFF184C46, 0xFF053D36, 0xFFBDEBE4),
    GREEN("green", 0xFFCDEDC0, 0xFF2A4B1F, 0xFF173D0A, 0xFFCDEDC0),
    GREY("grey", 0xFFE3E3E6, 0xFF3A3C42, 0xFF26272B, 0xFFE3E3E6),
    ;

    fun bg(dark: Boolean): Int = (if (dark) this.dark else light).toInt()
    fun ink(dark: Boolean): Int = (if (dark) inkDark else inkLight).toInt()

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
            else -> BLUE
        }
    }
}

/** dp to the pixels of one device. Documents are built for the device that shows them. */
class Units(val density: Float) {
    fun px(dp: Float): Float = dp * density
    fun px(dp: Int): Float = dp * density
    fun sp(sp: Float): Float = sp * density
}
