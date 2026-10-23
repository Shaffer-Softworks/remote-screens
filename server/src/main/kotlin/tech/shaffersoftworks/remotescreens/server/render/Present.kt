package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.shared.Catalogue
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
    private val CLIMATE_ICONS = mapOf(
        "cool" to "snowflake",
        "heat" to "fire",
        "off" to "thermostat-off",
    )
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

    fun view(entityState: EntityState?, i18n: I18n, nameOverride: String? = null, iconOverride: String? = null): EntityView {
        if (entityState == null) {
            val unavailable = i18n.state("", "unavailable")
            return EntityView(
                nameOverride ?: "?",
                unavailable,
                "–",
                false,
                null,
                false,
                iconOverride ?: Icons.FALLBACK,
            )
        }
        val spec = Catalogue.of(entityState.domain)
        val on = spec?.onStates?.contains(entityState.state) ?: (entityState.state == "on")
        val word = i18n.state(
            entityState.domain,
            entityState.state,
            entityState.attr("device_class"),
        )
        val level = levelOf(entityState, on)
        val stateText = stateTextOf(entityState, i18n, on, level, word)
        val value = valueOf(entityState, i18n, stateText, word)
        val icon = iconOverride ?: entityState.attr("icon") ?: iconFor(entityState, on)
        return EntityView(
            nameOverride ?: entityState.name,
            stateText,
            value,
            on,
            level,
            entityState.isAvailable,
            icon,
        )
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    @Suppress("CyclomaticComplexMethod")
    private fun levelOf(entityState: EntityState, on: Boolean): Float? = when (entityState.domain) {
        "light" -> if (on) {
            (entityState.number(
                "brightness",
            )?.toFloat() ?: MAX_BRIGHTNESS) / MAX_BRIGHTNESS
        } else {
            0f
        }
        "cover" -> entityState.number("current_position")?.toFloat()?.div(PERCENT)
        "fan" -> if (on) (entityState.number("percentage")?.toFloat() ?: PERCENT) / PERCENT else 0f
        "humidifier" -> {
            val humidity = entityState.number("humidity") ?: return null
            val min = entityState.number("min_humidity") ?: 0.0
            val max = entityState.number("max_humidity") ?: DEFAULT_NUMBER_MAX
            if (max > min) ((humidity - min) / (max - min)).toFloat() else humidity.toFloat() / PERCENT
        }
        "valve" -> entityState.number("current_position")?.toFloat()?.div(PERCENT)
        "water_heater" -> {
            val temperature = entityState.number("temperature") ?: return null
            val min = entityState.number("min_temp") ?: 0.0
            val max = entityState.number("max_temp") ?: DEFAULT_NUMBER_MAX
            if (max > min) ((temperature - min) / (max - min)).toFloat() else null
        }
        "media_player" -> entityState.number("volume_level")?.toFloat()
        "number", "input_number" -> {
            val numeric = entityState.numericState()
            val min = entityState.number("min") ?: 0.0
            val max = entityState.number("max") ?: DEFAULT_NUMBER_MAX
            if (numeric != null && max > min) ((numeric - min) / (max - min)).toFloat() else null
        }
        "vacuum" -> entityState.number("battery_level")?.toFloat()?.div(PERCENT)
        else -> null
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    @Suppress("CyclomaticComplexMethod")
    private fun stateTextOf(entityState: EntityState, i18n: I18n, on: Boolean, level: Float?, word: String): String =
        when (entityState.domain) {
            "light" -> entityState.number("brightness")
                ?.takeIf { on }
                ?.let { i18n.percent(((level ?: 0f) * PERCENT).roundToInt()) }
                ?: word
            "cover" -> entityState.number("current_position")
                ?.takeIf { entityState.state == "open" }
                ?.let { i18n.percent(it.roundToInt()) }
                ?: word
            "fan" -> entityState.number(
                "percentage",
            )?.takeIf { on }?.let { i18n.percent(it.roundToInt()) } ?: word
            "humidifier" -> entityState.number(
                "humidity",
            )?.takeIf { on }?.let { i18n.percent(it.roundToInt()) } ?: word
            "climate" -> entityState.number("current_temperature")
                ?.let { "${i18n.number(
                    it,
                    1,
                )} ${entityState.attr("temperature_unit") ?: "°"} · $word" }
                ?: word
            "media_player" -> listOfNotNull(
                entityState.attr("media_title"),
                entityState.attr("media_artist"),
            )
                .joinToString(" · ")
                .ifEmpty { word }
            "sensor", "number", "input_number" -> numberWithUnit(
                entityState,
                i18n,
            ) ?: entityState.state
            "weather" -> entityState.number(
                "temperature",
            )?.let { "${i18n.number(it, 0)}° · $word" } ?: word
            "scene", "button", "input_button" -> ""
            "script" -> if (on) word else ""
            "vacuum", "lawn_mower" ->
                entityState.number(
                    "battery_level",
                )?.let { "$word · ${i18n.percent(it.roundToInt())}" } ?: word
            "input_select", "select", "input_text", "input_datetime" -> entityState.state
            "calendar" -> entityState.attr("message") ?: word
            "todo", "counter" -> entityState.state
            "timer" -> entityState.attr("remaining") ?: word
            "water_heater" -> entityState.number(
                "temperature",
            )?.let { "${i18n.number(it, 0)}° · $word" } ?: word
            "valve" -> entityState.number("current_position")
                ?.takeIf { entityState.state == "open" }
                ?.let { i18n.percent(it.roundToInt()) }
                ?: word
            else -> word
        }

    @Suppress("CyclomaticComplexMethod")
    private fun valueOf(entityState: EntityState, i18n: I18n, stateText: String, word: String): String = when (entityState.domain) {
        "sensor", "number", "input_number", "counter", "todo" ->
            entityState.numericState()?.let { numberOnly(
                entityState,
                it,
                i18n,
            ) } ?: entityState.state
        "humidifier" -> entityState.number(
            "humidity",
        )?.let { i18n.percent(it.roundToInt()) } ?: word
        "climate", "water_heater" ->
            entityState.number(
                "temperature",
            )?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: word
        "weather" -> entityState.number("temperature")?.let { i18n.number(it, 0) + "°" } ?: word
        "timer" -> entityState.attr("remaining") ?: word
        "calendar" -> entityState.attr("message") ?: word
        else -> stateText
    }

    private fun numberOnly(entityState: EntityState, v: Double, i18n: I18n): String {
        val unit = entityState.attr("unit_of_measurement").orEmpty()
        val (scaled, _) = scale(v, unit)
        val decimals = when {
            abs(scaled) >= LARGE_VALUE -> 0
            entityState.attr(
                "device_class",
            ) in setOf("temperature", "humidity") || abs(scaled) >= MEDIUM_VALUE -> 1
            else -> if (scaled % 1.0 == 0.0) 0 else 1
        }
        return i18n.number(scaled, decimals)
    }

    fun numberWithUnit(entityState: EntityState, i18n: I18n): String? {
        val numeric = entityState.numericState() ?: return null
        val numberText = numberOnly(entityState, numeric, i18n)
        val (_, unit) = scale(numeric, entityState.attr("unit_of_measurement").orEmpty())
        return if (unit.isEmpty()) {
            numberText
        } else if (unit == "%") {
            "$numberText %"
        } else {
            "$numberText $unit"
        }
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
    fun iconFor(entityState: EntityState, on: Boolean): String {
        val dc = entityState.attr("device_class").orEmpty()
        val spec = Catalogue.of(entityState.domain)
        fun Pair<String, String>.pick() = if (on) first else second
        return when (entityState.domain) {
            "sensor" -> SENSOR_ICONS[dc] ?: "eye"
            "binary_sensor" -> (BINARY_SENSOR_ICONS[dc] ?: BINARY_SENSOR_DEFAULT).pick()
            "cover" -> (COVER_ICONS[dc] ?: COVER_DEFAULT).pick()
            "lock" -> LOCK_ICONS[entityState.state] ?: "lock-open-variant"
            "weather" -> weatherIcon(entityState.state)
            "climate" -> CLIMATE_ICONS[entityState.state] ?: "thermostat"
            "alarm_control_panel" -> ALARM_ICONS[entityState.state] ?: "shield-lock"
            "person" -> if (on) "account" else "account-arrow-right"
            else -> (if (on) spec?.iconOn else null) ?: spec?.icon ?: Icons.FALLBACK
        }
    }

    fun weatherIcon(condition: String): String = WEATHER_ICONS[condition] ?: "weather-partly-cloudy"
}
