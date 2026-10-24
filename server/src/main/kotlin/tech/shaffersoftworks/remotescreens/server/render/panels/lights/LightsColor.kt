package tech.shaffersoftworks.remotescreens.server.render.panels.lights

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val CHANNEL_MAX = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE = 0xFF shl 24

/** Tanner Helland's blackbody fit, as RPI's `approximateColorForKelvin`. */
private object Blackbody {
    const val MIN_KELVIN = 1000.0
    const val MAX_KELVIN = 40_000.0
    const val SCALE = 100.0
    const val WARM_LIMIT = 66.0
    const val BLUE_LIMIT = 19.0
    const val GREEN_WARM_A = 99.4708025861
    const val GREEN_WARM_B = 161.1195681661
    const val BLUE_OFFSET = 10.0
    const val BLUE_A = 138.5177312231
    const val BLUE_B = 305.0447927307
    const val COOL_OFFSET = 60.0
    const val RED_COOL_A = 329.698727446
    const val RED_COOL_EXP = -0.1332047592
    const val GREEN_COOL_A = 288.1221695283
    const val GREEN_COOL_EXP = -0.0755148492
    const val FULL = 255.0
}

internal fun rgb(red: Int, green: Int, blue: Int): Int =
    OPAQUE or (red.coerceIn(0, CHANNEL_MAX) shl RED_SHIFT) or
        (green.coerceIn(0, CHANNEL_MAX) shl GREEN_SHIFT) or blue.coerceIn(0, CHANNEL_MAX)

internal fun Int.red(): Int = (this shr RED_SHIFT) and CHANNEL_MAX

internal fun Int.green(): Int = (this shr GREEN_SHIFT) and CHANNEL_MAX

internal fun Int.blue(): Int = this and CHANNEL_MAX

/** `r,g,b`, the argument of the catalogue's `rgb` verb. */
internal fun Int.rgbArg(): String = "${red()},${green()},${blue()}"

internal fun blackbodyColor(kelvin: Int): Int {
    val k = kelvin.toDouble().coerceIn(Blackbody.MIN_KELVIN, Blackbody.MAX_KELVIN) / Blackbody.SCALE
    val red: Double
    val green: Double
    val blue: Double
    if (k <= Blackbody.WARM_LIMIT) {
        red = Blackbody.FULL
        green = Blackbody.GREEN_WARM_A * ln(k) - Blackbody.GREEN_WARM_B
        blue = if (k <= Blackbody.BLUE_LIMIT) {
            0.0
        } else {
            Blackbody.BLUE_A * ln(k - Blackbody.BLUE_OFFSET) - Blackbody.BLUE_B
        }
    } else {
        red = Blackbody.RED_COOL_A * (k - Blackbody.COOL_OFFSET).pow(Blackbody.RED_COOL_EXP)
        green = Blackbody.GREEN_COOL_A * (k - Blackbody.COOL_OFFSET).pow(Blackbody.GREEN_COOL_EXP)
        blue = Blackbody.FULL
    }
    fun channel(value: Double) = value.coerceIn(0.0, Blackbody.FULL).roundToInt()
    return rgb(channel(red), channel(green), channel(blue))
}
