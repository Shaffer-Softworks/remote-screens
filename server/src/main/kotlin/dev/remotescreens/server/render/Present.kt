package dev.remotescreens.server.render

import dev.remotescreens.server.I18n
import dev.remotescreens.server.ha.EntityState
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
    val available: Boolean,
    val icon: String,
)

object Present {
    fun view(e: EntityState?, i18n: I18n, nameOverride: String? = null, iconOverride: String? = null): EntityView {
        if (e == null) return EntityView(nameOverride ?: "?", i18n.state("", "unavailable"), "–", false, null, false, iconOverride ?: Icons.FALLBACK)
        val spec = Catalogue.of(e.domain)
        val on = spec?.onStates?.contains(e.state) ?: (e.state == "on")
        val dc = e.attr("device_class")
        val word = i18n.state(e.domain, e.state, dc)
        val level: Float? = when (e.domain) {
            "light" -> if (on) (e.number("brightness") ?: 255.0).toFloat() / 255f else 0f
            "cover" -> e.number("current_position")?.toFloat()?.div(100f)
            "fan" -> if (on) (e.number("percentage") ?: 100.0).toFloat() / 100f else 0f
            "media_player" -> e.number("volume_level")?.toFloat()
            "number", "input_number" -> {
                val v = e.numericState(); val min = e.number("min") ?: 0.0; val max = e.number("max") ?: 100.0
                if (v != null && max > min) ((v - min) / (max - min)).toFloat() else null
            }
            "vacuum" -> e.number("battery_level")?.toFloat()?.div(100f)
            else -> null
        }
        val stateText = when (e.domain) {
            "light" -> if (on && e.number("brightness") != null) i18n.percent(((level ?: 0f) * 100).roundToInt()) else word
            "cover" -> e.number("current_position")?.takeIf { e.state == "open" }?.let { i18n.percent(it.roundToInt()) } ?: word
            "fan" -> if (on && e.number("percentage") != null) i18n.percent(e.number("percentage")!!.roundToInt()) else word
            "climate" -> e.number("current_temperature")?.let { "${i18n.number(it, 1)} ${e.attr("temperature_unit") ?: "°"} · $word" } ?: word
            "media_player" -> listOfNotNull(e.attr("media_title"), e.attr("media_artist")).joinToString(" · ").ifEmpty { word }
            "sensor", "number", "input_number" -> numberWithUnit(e, i18n) ?: e.state
            "weather" -> e.number("temperature")?.let { "${i18n.number(it, 0)}° · $word" } ?: word
            "scene", "button", "input_button" -> ""
            "script" -> if (on) word else ""
            "vacuum" -> e.number("battery_level")?.let { "$word · ${i18n.percent(it.roundToInt())}" } ?: word
            "input_select", "select" -> e.state
            else -> word
        }
        val value = when (e.domain) {
            "sensor", "number", "input_number" -> numberOnly(e, i18n) ?: e.state
            "climate" -> e.number("temperature")?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: word
            "weather" -> e.number("temperature")?.let { i18n.number(it, 0) + "°" } ?: word
            else -> stateText
        }
        val icon = iconOverride ?: e.attr("icon") ?: iconFor(e, on)
        return EntityView(nameOverride ?: e.name, stateText, value, on, level, e.available, icon)
    }

    private fun numberOnly(e: EntityState, i18n: I18n): String? {
        val v = e.numericState() ?: return null
        val unit = e.attr("unit_of_measurement").orEmpty()
        val (scaled, _) = scale(v, unit)
        val decimals = when {
            abs(scaled) >= 100 -> 0
            e.attr("device_class") in setOf("temperature", "humidity") || abs(scaled) >= 10 -> 1
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
        unit == "W" && abs(v) >= 1000 -> v / 1000 to "kW"
        unit == "Wh" && abs(v) >= 1000 -> v / 1000 to "kWh"
        else -> v to unit
    }

    /** Home Assistant's default icon for a state, the common cases of its `domain_icon`. */
    fun iconFor(e: EntityState, on: Boolean): String {
        val dc = e.attr("device_class")
        val spec = Catalogue.of(e.domain)
        return when (e.domain) {
            "sensor" -> when (dc) {
                "temperature" -> "thermometer"; "humidity" -> "water-percent"; "power" -> "flash"; "energy" -> "lightning-bolt"
                "battery" -> "battery"; "illuminance" -> "brightness-5"; "pressure" -> "gauge"; "co2" -> "molecule-co2"
                else -> "eye"
            }
            "binary_sensor" -> when (dc) {
                "door" -> if (on) "door-open" else "door-closed"
                "window" -> if (on) "window-open" else "window-closed"
                "motion", "occupancy", "presence" -> if (on) "motion-sensor" else "motion-sensor-off"
                "moisture" -> if (on) "water" else "water-off"
                "smoke" -> if (on) "smoke-detector-alert" else "smoke-detector"
                else -> if (on) "checkbox-marked-circle" else "checkbox-blank-circle-outline"
            }
            "cover" -> when (dc) {
                "garage" -> if (on) "garage-open" else "garage"
                "curtain" -> if (on) "curtains" else "curtains-closed"
                "blind" -> if (on) "blinds-open" else "blinds"
                "door" -> if (on) "door-open" else "door-closed"
                else -> if (on) "window-shutter-open" else "window-shutter"
            }
            "lock" -> when (e.state) { "locked" -> "lock"; "jammed" -> "lock-alert"; "locking", "unlocking" -> "lock-clock"; else -> "lock-open-variant" }
            "weather" -> weatherIcon(e.state)
            "climate" -> when (e.state) { "cool" -> "snowflake"; "heat" -> "fire"; "off" -> "thermostat-off"; else -> "thermostat" }
            "alarm_control_panel" -> when (e.state) { "disarmed" -> "shield-off"; "triggered" -> "bell-ring"; "arming", "pending" -> "shield-sync"; else -> "shield-lock" }
            "person" -> if (on) "account" else "account-arrow-right"
            else -> (if (on) spec?.iconOn else null) ?: spec?.icon ?: Icons.FALLBACK
        }
    }

    fun weatherIcon(condition: String): String = when (condition) {
        "sunny" -> "weather-sunny"; "clear-night" -> "weather-night"; "partlycloudy" -> "weather-partly-cloudy"
        "cloudy" -> "weather-cloudy"; "rainy" -> "weather-rainy"; "pouring" -> "weather-pouring"
        "snowy" -> "weather-snowy"; "snowy-rainy" -> "weather-snowy-rainy"; "fog" -> "weather-fog"
        "hail" -> "weather-hail"; "lightning" -> "weather-lightning"; "lightning-rainy" -> "weather-lightning-rainy"
        "windy", "windy-variant" -> "weather-windy"; "exceptional" -> "alert-circle-outline"
        else -> "weather-partly-cloudy"
    }
}
