package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AlarmState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.CoverState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HvacMode
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.LockState
import tech.shaffersoftworks.remotescreens.server.render.widgets.Icons
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
        LockState.LOCKED to "lock",
        LockState.JAMMED to "lock-alert",
        LockState.LOCKING to "lock-clock",
        LockState.UNLOCKING to "lock-clock",
    )
    private val CLIMATE_ICONS = mapOf(
        HvacMode.COOL to "snowflake",
        HvacMode.HEAT to "fire",
        HvacMode.OFF to "thermostat-off",
    )
    private val ALARM_ICONS = mapOf(
        AlarmState.DISARMED to "shield-off",
        AlarmState.TRIGGERED to "bell-ring",
        AlarmState.ARMING to "shield-sync",
        AlarmState.PENDING to "shield-sync",
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
                name = nameOverride ?: "?",
                stateText = unavailable,
                value = "–",
                on = false,
                level = null,
                isAvailable = false,
                icon = iconOverride ?: Icons.FALLBACK,
            )
        }
        val spec = Catalogue.of(entityState.domain.value)
        val on = spec?.onStates?.contains(entityState.state) ?: entityState.isOn
        val word = i18n.state(
            entityState.domain.value,
            entityState.state,
            entityState.common.deviceClass,
        )
        val level = levelOf(entityState, on)
        val stateText = stateTextOf(entityState = entityState, i18n = i18n, on = on, level = level, word = word)
        val value = valueOf(entityState = entityState, i18n = i18n, stateText = stateText, word = word)
        val icon = iconOverride ?: entityState.common.icon ?: iconFor(entityState, on)
        return EntityView(
            name = nameOverride ?: entityState.name,
            stateText = stateText,
            value = value,
            on = on,
            level = level,
            isAvailable = entityState.isAvailable,
            icon = icon,
        )
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    @Suppress("CyclomaticComplexMethod")
    private fun levelOf(entityState: EntityState, on: Boolean): Float? = when (entityState.domain) {
        Domain.LIGHT -> if (on) {
            (entityState.light.brightness?.toFloat() ?: MAX_BRIGHTNESS) / MAX_BRIGHTNESS
        } else {
            0f
        }
        Domain.COVER -> (entityState.cover.currentPosition ?: entityState.cover.currentTiltPosition)
            ?.toFloat()
            ?.div(PERCENT)
        Domain.FAN -> if (on) (entityState.fan.percentage?.toFloat() ?: PERCENT) / PERCENT else 0f
        Domain.HUMIDIFIER -> {
            val humidity = entityState.humidifier.humidity ?: return null
            val min = entityState.humidifier.minHumidity ?: 0.0
            val max = entityState.humidifier.maxHumidity ?: DEFAULT_NUMBER_MAX
            if (max > min) ((humidity - min) / (max - min)).toFloat() else humidity.toFloat() / PERCENT
        }
        Domain.VALVE -> entityState.cover.currentPosition?.toFloat()?.div(PERCENT)
        Domain.WATER_HEATER -> {
            val temperature = entityState.climate.temperature ?: return null
            val min = entityState.climate.minTemp ?: 0.0
            val max = entityState.climate.maxTemp ?: DEFAULT_NUMBER_MAX
            if (max > min) ((temperature - min) / (max - min)).toFloat() else null
        }
        Domain.MEDIA_PLAYER -> entityState.mediaPlayer.volumeLevel?.toFloat()
        Domain.NUMBER, Domain.INPUT_NUMBER -> {
            val numeric = entityState.numericState()
            val min = entityState.input.min ?: 0.0
            val max = entityState.input.max ?: DEFAULT_NUMBER_MAX
            if (numeric != null && max > min) ((numeric - min) / (max - min)).toFloat() else null
        }
        Domain.VACUUM -> entityState.vacuum.batteryLevel?.toFloat()?.div(PERCENT)
        else -> null
    }

    // One branch per Home Assistant domain, as a lookup table would be.
    @Suppress("CyclomaticComplexMethod")
    private fun stateTextOf(entityState: EntityState, i18n: I18n, on: Boolean, level: Float?, word: String): String =
        when (entityState.domain) {
            Domain.LIGHT -> entityState.light.brightness
                ?.takeIf { on }
                ?.let { i18n.percent(((level ?: 0f) * PERCENT).roundToInt()) }
                ?: word
            Domain.COVER -> {
                val shade = entityState.cover.currentPosition
                val tilt = entityState.cover.currentTiltPosition
                when {
                    shade != null && entityState.coverState == CoverState.OPEN -> i18n.percent(shade.roundToInt())
                    shade == null && tilt != null -> i18n.percent(tilt.roundToInt())
                    else -> word
                }
            }
            Domain.FAN -> entityState.fan.percentage?.takeIf { on }?.let { i18n.percent(it.roundToInt()) } ?: word
            Domain.HUMIDIFIER -> entityState.humidifier.humidity
                ?.takeIf { on }
                ?.let { i18n.percent(it.roundToInt()) }
                ?: word
            Domain.CLIMATE -> entityState.climate.currentTemperature
                ?.let { "${i18n.number(it, 1)} ${entityState.climate.temperatureUnit ?: "°"} · $word" }
                ?: word
            Domain.MEDIA_PLAYER -> listOfNotNull(
                entityState.mediaPlayer.mediaTitle,
                entityState.mediaPlayer.mediaArtist,
            )
                .joinToString(" · ")
                .ifEmpty { word }
            Domain.SENSOR, Domain.NUMBER, Domain.INPUT_NUMBER -> numberWithUnit(
                entityState,
                i18n,
            ) ?: entityState.state
            Domain.WEATHER -> entityState.weather.temperature?.let { "${i18n.number(it, 0)}° · $word" } ?: word
            Domain.SCENE, Domain.BUTTON, Domain.INPUT_BUTTON -> ""
            Domain.SCRIPT -> if (on) word else ""
            Domain.VACUUM, Domain.LAWN_MOWER ->
                entityState.vacuum.batteryLevel?.let { "$word · ${i18n.percent(it.roundToInt())}" } ?: word
            Domain.INPUT_SELECT, Domain.SELECT, Domain.INPUT_TEXT, Domain.INPUT_DATETIME -> entityState.state
            Domain.CALENDAR -> entityState.calendar.message ?: word
            Domain.TODO, Domain.COUNTER -> entityState.state
            Domain.TIMER -> entityState.timer.remaining ?: word
            Domain.WATER_HEATER -> entityState.climate.temperature?.let { "${i18n.number(it, 0)}° · $word" } ?: word
            Domain.VALVE -> entityState.cover.currentPosition
                ?.takeIf { entityState.coverState == CoverState.OPEN }
                ?.let { i18n.percent(it.roundToInt()) }
                ?: word
            else -> word
        }

    @Suppress("CyclomaticComplexMethod")
    private fun valueOf(entityState: EntityState, i18n: I18n, stateText: String, word: String): String = when (entityState.domain) {
        Domain.SENSOR, Domain.NUMBER, Domain.INPUT_NUMBER, Domain.COUNTER, Domain.TODO ->
            entityState.numericState()?.let { numberOnly(
                entityState,
                it,
                i18n,
            ) } ?: entityState.state
        Domain.HUMIDIFIER -> entityState.humidifier.humidity?.let { i18n.percent(it.roundToInt()) } ?: word
        Domain.CLIMATE, Domain.WATER_HEATER ->
            entityState.climate.temperature?.let { i18n.number(it, if (it % 1.0 == 0.0) 0 else 1) + "°" } ?: word
        Domain.WEATHER -> entityState.weather.temperature?.let { i18n.number(it, 0) + "°" } ?: word
        Domain.TIMER -> entityState.timer.remaining ?: word
        Domain.CALENDAR -> entityState.calendar.message ?: word
        else -> stateText
    }

    private fun numberOnly(entityState: EntityState, v: Double, i18n: I18n): String {
        val unit = entityState.common.unitOfMeasurement.orEmpty()
        val (scaled, _) = scale(v, unit)
        val decimals = when {
            abs(scaled) >= LARGE_VALUE -> 0
            entityState.common.deviceClass in setOf("temperature", "humidity") || abs(scaled) >= MEDIUM_VALUE -> 1
            else -> if (scaled % 1.0 == 0.0) 0 else 1
        }
        return i18n.number(scaled, decimals)
    }

    fun numberWithUnit(entityState: EntityState, i18n: I18n): String? {
        val numeric = entityState.numericState() ?: return null
        val numberText = numberOnly(entityState, numeric, i18n)
        val (_, unit) = scale(numeric, entityState.common.unitOfMeasurement.orEmpty())
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
        val deviceClass = entityState.common.deviceClass.orEmpty()
        val spec = Catalogue.of(entityState.domain.value)
        fun Pair<String, String>.pick() = if (on) first else second
        return when (entityState.domain) {
            Domain.SENSOR -> SENSOR_ICONS[deviceClass] ?: "eye"
            Domain.BINARY_SENSOR -> (BINARY_SENSOR_ICONS[deviceClass] ?: BINARY_SENSOR_DEFAULT).pick()
            Domain.COVER -> (COVER_ICONS[deviceClass] ?: COVER_DEFAULT).pick()
            Domain.LOCK -> LOCK_ICONS[entityState.lockState] ?: "lock-open-variant"
            Domain.WEATHER -> weatherIcon(entityState.state)
            Domain.CLIMATE -> CLIMATE_ICONS[entityState.hvacMode] ?: "thermostat"
            Domain.ALARM_PANEL -> ALARM_ICONS[entityState.alarmState] ?: "shield-lock"
            Domain.PERSON -> if (on) "account" else "account-arrow-right"
            else -> (if (on) spec?.iconOn else null) ?: spec?.icon ?: Icons.FALLBACK
        }
    }

    fun weatherIcon(condition: String): String = WEATHER_ICONS[condition] ?: "weather-partly-cloudy"
}
