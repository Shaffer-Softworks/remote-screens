package dev.remotescreens.server.render

/**
 * Every colour of the screens, in one table: the RPI wall panel's dark greys, teal and blue. Code asks for a role,
 * never for a hex value; `ThemeTest` keeps hex colours out of the other files.
 */
enum class Role(private val value: Long) {
    PAGE(0xFF494949),
    CARD(0xFF333333),
    CARD_HEADER(0xFF494949),
    CARD_EDGE(0xFF000000),
    HEADER(0xFF494949),
    HEADER_DARK(0xFF2F2E2E),
    NAV(0xFF2F2E2E),
    NAV_ON(0xFF14E1BA),
    NAV_LINE(0xFF03DAC5),
    INK(0xFFFFFFFF),
    INK_SOFT(0xFFCACACA),
    INK_DIM(0xFF939393),
    INK_FAINT(0xFF5C5C5C),
    INK_DARK(0xFF000000),
    DIVIDER(0xFF4A4A4A),
    TRACK(0xFF1B1B1B),
    ACCENT(0xFF14E1BA),
    ACCENT_DARK(0xFF1DA48C),
    ACCENT_SOFT(0xFF03DAC5),
    BLUE(0xFF0986DB),
    HOME(0xFF14E1BA),
    HOME_STRIP(0xFFFFFFFF),
    WEATHER_TITLE(0xFFFFBB33),
    STRIP(0xFF3B3B3B),
    OK(0xFF289907),
    WARN(0xFFF57C00),
    DANGER(0xFFA21C1C),
    SCRIM(0xCC000000),
    KEY(0xFF5C5C5C),
    KEY_ON(0xFF14E1BA),
    KEY_ON_INK(0xFF2F2E2E),
    ;

    val argb: Int get() = value.toInt()
}

/** The accent a tile lights up in when it is on. Keys are stored in layouts: never rename one. */
enum class Pastel(val key: String, private val value: Long) {
    YELLOW("yellow", 0xFFFFC94D),
    ORANGE("orange", 0xFFFF9800),
    RED("red", 0xFFFF6E6E),
    PINK("pink", 0xFFF48FB1),
    PURPLE("purple", 0xFFB39DDB),
    BLUE("blue", 0xFF0986DB),
    TEAL("teal", 0xFF14E1BA),
    GREEN("green", 0xFF5CCB5F),
    GREY("grey", 0xFFCACACA),
    ;

    val argb: Int get() = value.toInt()

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

/** dp to the pixels of one device. Documents are built for the device that shows them. */
class Units(val density: Float) {
    fun px(dp: Float): Float = dp * density
    fun px(dp: Int): Float = dp * density
    fun sp(sp: Float): Float = sp * density
}
