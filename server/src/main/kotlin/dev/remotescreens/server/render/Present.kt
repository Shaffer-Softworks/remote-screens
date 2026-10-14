package dev.remotescreens.server.render

import dev.remotescreens.server.i18n.I18n
import dev.remotescreens.server.data.homeassistant.EntityState
import dev.remotescreens.shared.Catalogue
import kotlin.math.abs
import kotlin.math.roundToInt

/** How an entity reads on a tile: its name, the line under it, a big value, whether it is on, and a level. */
data class EntityView(
    val name: String,
    val stateText: String,
    val value: String,
    val on: Boolean,
    /** 0..1 for a bar (brightness, position, volume), or null when the entity has none. */
    val level: Float?,
    val isAvailable: Boolean,
    val icon: String,
)

object Present {
    private const val KILO = 1000
    private const val LARGE_VALUE = 100
    private const val MEDIUM_VALUE = 10
    private const val MAX_BRIGHTNESS = 255f
    private const val PERCENT = 100f
    private const val DEFAULT_NUMBER_MAX = 100.0

    private val SENSOR_ICONS = mapOf(
        "temperature" to "thermometer",
        "humidity" to "water-percent",
        "power" to "flash",
        "energy" to "lightning-bolt",
        "battery" to "battery",
        "illuminance" to "brightness-5",
        "pressure" to "gauge",
        "co2" to "molecule-co2",
    )

    /** Icons for a device class, on then off. */
    private val BINARY_SENSOR_ICONS = mapOf(
        "door" to ("door-open" to "door-closed"),
        "window" to ("window-open" to "window-closed"),
        "motion" to ("motion-sensor" to "motion-sensor-off"),
        "occupancy" to ("motion-sensor" to "motion-sensor-off"),
        "presence" to ("motion-sensor" to "motion-sensor-off"),
        "moisture" to ("water" to "water-off"),
        "smoke" to ("smoke-detector-alert" to "smoke-detector"),
    )
    private val BINARY_SENSOR_DEFAULT = "checkbox-marked-circle" to "checkbox-blank-circle-outline"

    private val COVER_ICONS = mapOf(
        "garage" to ("garage-open" to "garage"),
        "curtain" to ("curtains" to "curtains-closed"),
        "blind" to ("blinds-open" to "blinds"),
        "door" to ("door-open" to "door-closed"),
    )
    private val COVER_DEFAULT = "window-shutter-open" to "window-shutter"

    private val LOCK_ICONS = mapOf(
        "locked" to "lock",
        "jammed" to "lock-alert",
        "locking" to "lock-clock",
        "unlocking" to "lock-clock",
    )
    private val CLIMATE_ICONS = mapOf("cool" to "snowflake", "heat" to "fire", "off" to "thermostat-off")
    private val ALARM_ICONS = mapOf(
        "disarmed" to "shield-off",
        "triggered" to "bell-ring",
        "arming" to "shield-sync",
        "pending" to "shield-sync",
    )
    private val WEATHER_ICONS = mapOf(
        "sunny" to "weather-sunny",
        "clear-night" to "weather-night",
        "partlycloudy" to "weather-partly-cloudy",
        "cloudy" to "weather-cloudy",
        "rainy" to "weather-rainy",
        "pouring" to "weather-pouring",
        "snowy" to "weather-snowy",
        "snowy-rainy" to "weather-snowy-rainy",
        "fog" to "weather-fog",
        "hail" to "weather-hail",
        "lightning" to "weather-lightning",
        "lightning-rainy" to "weather-lightning-rainy",
        "windy" to "weather-windy",
        "windy-variant" to "weather-windy",
        "exceptional" to "alert-circle-outline",
    )

    fun view(e: EntityState?, i18n: I18n, nameOverride: String? = null, iconOverride: String? = null): EntityView {
        if (e == null) {
            val unavailable = i18n.state("", "unavailable")
            return EntityView(nameOverride ?: "?", unavailable, "–", false, null, false, iconOverride ?: Icons.FALLBACK)
        }
        val spec = Catalogue.of(e.domain)
        val on = spec?.onStates?.contains(e.state) ?: (e.state == "on")
        val word = i18n.state(e.domain, e.state, e.attr("device_class"))
        val level = levelOf(e, on)
        val stateText = stateTextOf(e, i18n, on, level, word)
        val value = valueOf(e, i18n, stateText, word)
        val icon = iconOverride ?: e.attr("icon") ?: iconFor(e, on)
        return EntityView(nameOverride ?: e.name, stateText, value, on, level, e.isAvailable, icon)
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    @Suppress("CyclomaticComplexMethod")
    private fun levelOf(e: EntityState, on: Boolean): Float? = when (e.domain) {
        "light" -> if (on) (e.number("brightness")?.toFloat() ?: MAX_BRIGHTNESS) / MAX_BRIGHTNESS else 0f
        "cover" -> e.number("current_position")?.toFloat()?.div(PERCENT)
        "fan" -> if (on) (e.number("percentage")?.toFloat() ?: PERCENT) / PERCENT else 0f
        "media_player" -> e.number("volume_level")?.toFloat()
        "number", "input_number" -> {
            val v = e.numericState()
            val min = e.number("min") ?: 0.0
            val max = e.number("max") ?: DEFAULT_NUMBER_MAX
            if (v != null && max > min) ((v - min) / (max - min)).toFloat() else null
        }
        "vacuum" -> e.number("battery_level")?.toFloat()?.div(PERCENT)
        else -> null
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    @Suppress("CyclomaticComplexMethod")
    private fun stateTextOf(e: EntityState, i18n: I18n, on: Boolean, level: Float?, word: String): String =
        when (e.domain) {
            "light" -> e.number("brightness")
                ?.takeIf { on }
                ?.let { i18n.percent(((level ?: 0f) * PERCENT).roundToInt()) }
                ?: word
            "cover" -> e.number("current_position")
                ?.takeIf { e.state == "open" }
                ?.let { i18n.percent(it.roundToInt()) }
                ?: word
            "fan" -> e.number("percentage")?.takeIf { on }?.let { i18n.percent(it.roundToInt()) } ?: word
            "climate" -> e.number("current_temperature")
                ?.let { "${i18n.number(it, 1)} ${e.attr("temperature_unit") ?: "°"} · $word" }
                ?: word
            "media_player" -> listOfNotNull(e.attr("media_title"), e.attr("media_artist"))
                .joinToString(" · ")
                .ifEmpty { word }
            "sensor", "number", "input_number" -> numberWithUnit(e, i18n) ?: e.state
            "weather" -> e.number("temperature")?.let { "${i18n.number(it, 0)}° · $word" } ?: word
            "scene", "button", "input_button" -> ""
            "script" -> if (on) word else ""
            "vacuum" -> e.number("battery_level")?.let { "$word · ${i18n.percent(it.roundToInt())}" } ?: word
            "input_select", "select" -> e.state
            else -> word
        }

    private fun valueOf(e: EntityState, i18n: I18n, stateText: String, word: String): String = when (e.domain) {
        "sensor", "number", "input_number" -> numberOnly(e, i18n) ?: e.state
        "climate" -> e.number("temperature")?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: word
        "weather" -> e.number("temperature")?.let { i18n.number(it, 0) + "°" } ?: word
        else -> stateText
    }

    private fun numberOnly(e: EntityState, i18n: I18n): String? {
        val v = e.numericState() ?: return null
        val unit = e.attr("unit_of_measurement").orEmpty()
        val (scaled, _) = scale(v, unit)
        val decimals = when {
            abs(scaled) >= LARGE_VALUE -> 0
            e.attr("device_class") in setOf("temperature", "humidity") || abs(scaled) >= MEDIUM_VALUE -> 1
            else -> if (scaled % 1.0 == 0.0) 0 else 1
        }
        return i18n.number(scaled, decimals)
    }

    fun numberWithUnit(e: EntityState, i18n: I18n): String? {
        val n = numberOnly(e, i18n) ?: return null
        val (_, unit) = scale(e.numericState()!!, e.attr("unit_of_measurement").orEmpty())
        return if (unit.isEmpty()) n else if (unit == "%") "$n %" else "$n $unit"
    }

    /** 5200 W reads as 5.2 kW, as Home Assistant's energy views do. */
    private fun scale(v: Double, unit: String): Pair<Double, String> = when {
        unit == "W" && abs(v) >= KILO -> v / KILO to "kW"
        unit == "Wh" && abs(v) >= KILO -> v / KILO to "kWh"
        else -> v to unit
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    /** Home Assistant's default icon for a state, the common cases of its `domain_icon`. */
    @Suppress("CyclomaticComplexMethod")
    fun iconFor(e: EntityState, on: Boolean): String {
        val dc = e.attr("device_class").orEmpty()
        val spec = Catalogue.of(e.domain)
        fun Pair<String, String>.pick() = if (on) first else second
        return when (e.domain) {
            "sensor" -> SENSOR_ICONS[dc] ?: "eye"
            "binary_sensor" -> (BINARY_SENSOR_ICONS[dc] ?: BINARY_SENSOR_DEFAULT).pick()
            "cover" -> (COVER_ICONS[dc] ?: COVER_DEFAULT).pick()
            "lock" -> LOCK_ICONS[e.state] ?: "lock-open-variant"
            "weather" -> weatherIcon(e.state)
            "climate" -> CLIMATE_ICONS[e.state] ?: "thermostat"
            "alarm_control_panel" -> ALARM_ICONS[e.state] ?: "shield-lock"
            "person" -> if (on) "account" else "account-arrow-right"
            else -> (if (on) spec?.iconOn else null) ?: spec?.icon ?: Icons.FALLBACK
        }
    }

    fun weatherIcon(condition: String): String = WEATHER_ICONS[condition] ?: "weather-partly-cloudy"
}
