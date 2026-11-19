package tech.shaffersoftworks.remotescreens.server.data.homeassistant

/** Home Assistant `supported_color_modes` values. Compare these, not a fresh literal. */
object LightColorMode {
    const val ONOFF = "onoff"
    const val BRIGHTNESS = "brightness"
    const val COLOR_TEMP = "color_temp"
    const val HS = "hs"
    const val RGB = "rgb"
    const val RGBW = "rgbw"
    const val RGBWW = "rgbww"
    const val XY = "xy"

    /** Modes that carry a colour, as opposed to brightness or a plain on/off. */
    val RGB_CAPABLE = setOf(RGB, RGBW, RGBWW, HS, XY)
}
