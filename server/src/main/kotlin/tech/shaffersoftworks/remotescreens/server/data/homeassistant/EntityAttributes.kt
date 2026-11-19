package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Integrations report attributes loosely: numbers as text, `null` for an empty list. Every field of a view has a
 * default, and [decodeAttributes] drops a value that still does not fit rather than failing the whole view.
 */
private val ATTRIBUTE_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** [attributes] as [serializer]'s view, keeping every attribute that decodes and leaving the rest at their defaults. */
internal fun <T> decodeAttributes(serializer: KSerializer<T>, attributes: JsonObject): T =
    runCatching { ATTRIBUTE_JSON.decodeFromJsonElement(serializer, attributes) }.getOrElse {
        val fitting = attributes.filter { (key, value) -> fits(serializer, key, value) }
        ATTRIBUTE_JSON.decodeFromJsonElement(serializer, JsonObject(fitting))
    }

private fun <T> fits(serializer: KSerializer<T>, key: String, value: JsonElement): Boolean =
    value != JsonNull &&
        runCatching { ATTRIBUTE_JSON.decodeFromJsonElement(serializer, JsonObject(mapOf(key to value))) }.isSuccess

/** Attributes any entity may have. */
@Serializable
data class CommonAttributes(
    @SerialName("friendly_name") val friendlyName: String? = null,
    val icon: String? = null,
    @SerialName("device_class") val deviceClass: String? = null,
    @SerialName("unit_of_measurement") val unitOfMeasurement: String? = null,
    @SerialName("supported_features") val supportedFeatureBits: Double? = null,
    @SerialName("entity_picture") val entityPicture: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val supportedFeatures: Int get() = supportedFeatureBits?.toInt() ?: 0
}

@Serializable
data class LightAttributes(
    val brightness: Double? = null,
    @SerialName("color_mode") val colorMode: String? = null,
    @SerialName("supported_color_modes") val supportedColorModes: List<String> = emptyList(),
    val effect: String? = null,
    @SerialName("effect_list") val effectList: List<String> = emptyList(),
    @SerialName("rgb_color") val rgbColor: List<Double> = emptyList(),
    @SerialName("color_temp_kelvin") val colorTempKelvin: Double? = null,
    @SerialName("min_color_temp_kelvin") val minColorTempKelvin: Double? = null,
    @SerialName("max_color_temp_kelvin") val maxColorTempKelvin: Double? = null,
)

/** A climate entity or a water heater. */
@Serializable
data class ClimateAttributes(
    val temperature: Double? = null,
    @SerialName("current_temperature") val currentTemperature: Double? = null,
    @SerialName("target_temp_low") val targetTempLow: Double? = null,
    @SerialName("target_temp_high") val targetTempHigh: Double? = null,
    @SerialName("target_temp_step") val targetTempStep: Double? = null,
    @SerialName("min_temp") val minTemp: Double? = null,
    @SerialName("max_temp") val maxTemp: Double? = null,
    @SerialName("temperature_unit") val temperatureUnit: String? = null,
    @SerialName("hvac_modes") val hvacModes: List<String> = emptyList(),
    @SerialName("hvac_action") val hvacAction: String? = null,
    @SerialName("fan_mode") val fanMode: String? = null,
    @SerialName("fan_modes") val fanModes: List<String> = emptyList(),
    @SerialName("preset_mode") val presetMode: String? = null,
    @SerialName("preset_modes") val presetModes: List<String> = emptyList(),
    @SerialName("swing_mode") val swingMode: String? = null,
    @SerialName("swing_modes") val swingModes: List<String> = emptyList(),
    @SerialName("current_humidity") val currentHumidity: Double? = null,
    @SerialName("operation_list") val operationList: List<String> = emptyList(),
) {
    val hasTemperatureRange: Boolean get() = targetTempLow != null && targetTempHigh != null
}

@Serializable
data class HumidifierAttributes(
    val humidity: Double? = null,
    @SerialName("current_humidity") val currentHumidity: Double? = null,
    @SerialName("min_humidity") val minHumidity: Double? = null,
    @SerialName("max_humidity") val maxHumidity: Double? = null,
    @SerialName("humidity_step") val humidityStep: Double? = null,
    val mode: String? = null,
    @SerialName("available_modes") val availableModes: List<String> = emptyList(),
)

/** A cover or a valve. */
@Serializable
data class CoverAttributes(
    @SerialName("current_position") val currentPosition: Double? = null,
    @SerialName("current_tilt_position") val currentTiltPosition: Double? = null,
)

@Serializable
data class FanAttributes(
    val percentage: Double? = null,
    @SerialName("preset_mode") val presetMode: String? = null,
    @SerialName("preset_modes") val presetModes: List<String> = emptyList(),
    val oscillating: Boolean? = null,
    val direction: String? = null,
)

@Serializable
data class MediaPlayerAttributes(
    @SerialName("media_title") val mediaTitle: String? = null,
    @SerialName("media_artist") val mediaArtist: String? = null,
    @SerialName("media_album_name") val mediaAlbumName: String? = null,
    @SerialName("media_duration") val mediaDuration: Double? = null,
    @SerialName("media_position") val mediaPosition: Double? = null,
    @SerialName("media_position_updated_at") val mediaPositionUpdatedAt: String? = null,
    @SerialName("volume_level") val volumeLevel: Double? = null,
    val source: String? = null,
    @SerialName("source_list") val sourceList: List<String> = emptyList(),
)

/** Numbers, selects and texts: `number`, `input_number`, `select`, `input_select`, `input_text`, `counter`. */
@Serializable
data class InputAttributes(
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<String> = emptyList(),
)

@Serializable
data class VacuumAttributes(
    @SerialName("battery_level") val batteryLevel: Double? = null,
    @SerialName("fan_speed") val fanSpeed: String? = null,
    @SerialName("fan_speed_list") val fanSpeedList: List<String> = emptyList(),
    @SerialName("room_list") val roomList: List<String> = emptyList(),
    @SerialName("room_ids") val roomIds: List<String> = emptyList(),
    @SerialName("options") val options: List<String> = emptyList()
)

/** A calendar's next event. */
@Serializable
data class CalendarAttributes(
    val message: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("all_day") val allDay: Boolean? = null,
    val location: String? = null,
)

/** An alarm panel; `arm_mode` and `open_sensors` are Alarmo's. */
@Serializable
data class AlarmAttributes(
    @SerialName("arm_mode") val armModeText: String? = null,
    @SerialName("code_format") val codeFormat: String? = null,
    /** Alarmo's open sensors: a map of sensor to state (a list on older versions), null when all are closed. */
    @SerialName("open_sensors") val openSensors: JsonElement? = null,
) {
    val armMode: AlarmState? get() = armModeText?.let { AlarmState.of(it) }

    val hasOpenSensors: Boolean
        get() = when (openSensors) {
            is JsonObject -> openSensors.isNotEmpty()
            is JsonArray -> openSensors.isNotEmpty()
            else -> false
        }
}

@Serializable
data class SirenAttributes(
    @SerialName("available_tones") val availableTones: List<String> = emptyList(),
    val tone: String? = null,
)

@Serializable
data class WeatherAttributes(
    val temperature: Double? = null,
    @SerialName("temperature_unit") val temperatureUnit: String? = null,
    val humidity: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_speed_unit") val windSpeedUnit: String? = null,
    val pressure: Double? = null,
    @SerialName("pressure_unit") val pressureUnit: String? = null,
)

@Serializable
data class TimerAttributes(
    val remaining: String? = null,
)
