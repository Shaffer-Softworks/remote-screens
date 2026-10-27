package tech.shaffersoftworks.remotescreens.server.render.panels.lights

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.lights.LightDetailMode
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.server.render.widgets.Icons
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private val RGB_CAPABLE_MODES = setOf("rgb", "rgbw", "rgbww", "hs", "xy")
private const val EFFECT_FEATURE = 4
private const val ROW_ICON_FALLBACK = "toggle-switch"
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86_400L

/** RPI's `LightAttributes.supportsBrightness`: a brightness mode, or a brightness reported now. */
internal fun EntityState.supportsBrightness(): Boolean =
    "brightness" in list("supported_color_modes") || number("brightness") != null

internal fun EntityState.supportsColorTemp(): Boolean = "color_temp" in list("supported_color_modes")

internal fun EntityState.supportsRgbColor(): Boolean = list("supported_color_modes").any { it in RGB_CAPABLE_MODES }

internal fun EntityState.supportsEffects(): Boolean = list("effect_list").isNotEmpty() || supports(EFFECT_FEATURE)

/** RPI's `hasSlider`: a brightness slider for any light with brightness or colour temperature. */
internal fun EntityState.hasLightSlider(): Boolean = supportsBrightness() || supportsColorTemp()

internal fun EntityState.supportsLightDetail(): Boolean =
    supportsBrightness() || supportsColorTemp() || supportsRgbColor() || supportsEffects()

/** Whether RPI's row switch is on: `open` for a cover, `on` for the rest. */
internal fun EntityState?.isRowOn(): Boolean = when (this?.domain) {
    null -> false
    "cover" -> state == "open"
    else -> state == "on"
}

/** The light's `rgb_color` as opaque ARGB, or null when it reports none. */
internal fun EntityState.rgbColor(): Int? = readRgb()

/** RPI's `selectedColor`: the light's colour, else its teal. */
internal fun EntityState?.selectedColor(): Int = this?.rgbColor() ?: Rpi.LightsOn

/** RPI's `getIcon`: the entity's own mdi icon, or a toggle switch when it has none the set knows. */
internal fun EntityState.rowIcon(): String = attr("icon")?.takeIf(Icons::exists) ?: ROW_ICON_FALLBACK

/** Brightness 0–100 as the dialog shows it: from `brightness`, else full when on and nothing when off. */
internal fun EntityState?.brightnessPercent(): Int {
    val brightness = this?.number("brightness")
    return if (brightness != null) {
        (brightness / LightsMetrics.BRIGHTNESS_MAX * LightsMetrics.PERCENT).roundToInt()
            .coerceIn(0, LightsMetrics.PERCENT)
    } else if (this?.state == "on") {
        LightsMetrics.PERCENT
    } else {
        0
    }
}

/** The light's colour temperature range, warmest first. */
internal fun EntityState?.kelvinRange(): IntRange {
    val low = this?.number("min_color_temp_kelvin")?.toInt() ?: LightsDialogMetrics.MIN_KELVIN
    val high = this?.number("max_color_temp_kelvin")?.toInt() ?: LightsDialogMetrics.MAX_KELVIN
    return minOf(low, high)..maxOf(low, high)
}

internal fun EntityState?.kelvin(): Int {
    val range = kelvinRange()
    return this?.number("color_temp_kelvin")?.toInt()?.coerceIn(range) ?: ((range.first + range.last) / 2)
}

/** The dialog's first mode, as RPI's `LightDeviceDetailDialog` picks it. */
internal fun EntityState?.startMode(): LightDetailMode = when {
    this == null -> LightDetailMode.POWER
    attr("color_mode") == "color_temp" && supportsColorTemp() -> LightDetailMode.COLOR_TEMPERATURE
    supportsBrightness() -> LightDetailMode.BRIGHTNESS
    supportsColorTemp() -> LightDetailMode.COLOR_TEMPERATURE
    else -> LightDetailMode.POWER
}

/** How long ago Home Assistant last changed the light, as Android's relative time span (`16 minutes ago`). */
internal fun EntityState?.updatedLabel(i18n: I18n, now: Instant = Instant.now()): String {
    val then = this?.lastChanged?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "\u2014"
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        seconds < SECONDS_PER_MINUTE -> plural(i18n, "second", seconds)
        seconds < SECONDS_PER_HOUR -> plural(i18n, "minute", seconds / SECONDS_PER_MINUTE)
        seconds < SECONDS_PER_DAY -> plural(i18n, "hour", seconds / SECONDS_PER_HOUR)
        else -> plural(i18n, "day", seconds / SECONDS_PER_DAY)
    }
}

private fun plural(i18n: I18n, unit: String, count: Long): String =
    if (count == 1L) i18n.t("ui.lights_${unit}_ago") else i18n.t("ui.lights_${unit}s_ago", "count" to count)
