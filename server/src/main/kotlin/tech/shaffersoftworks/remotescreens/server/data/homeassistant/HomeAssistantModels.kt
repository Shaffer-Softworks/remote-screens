package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

data class HomeAssistantEvent(val type: String, val data: JsonObject)

data class HomeAssistantConfig(
    val language: String = "en",
    val country: String? = null,
    val timeZone: String = "UTC",
    val temperatureUnit: String = "°C",
    val locationName: String = "Home",
    val version: String = "",
    /** True when Home Assistant has loaded the Remote Screens custom integration. */
    val isIntegrationLoaded: Boolean = false,
)

/** A command Home Assistant answered with `success: false`. */
class HomeAssistantException(val code: String, message: String) : Exception(message)

/**
 * One entity as Home Assistant reports it: its state and attributes. [attributes] is the raw map; read it through
 * the typed views ([common], [light], [climate], ...). An attribute named by the user's layout, unknown until run
 * time, goes through [attributeText], [attributeNumber] or [attributeList].
 */
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
    val lastChanged: String = "",
) {
    val id: EntityId get() = EntityId(entityId)

    val domain: Domain get() = id.domain

    val common: CommonAttributes by lazy { decodeAttributes(CommonAttributes.serializer(), attributes) }
    val light: LightAttributes by lazy { decodeAttributes(LightAttributes.serializer(), attributes) }
    val climate: ClimateAttributes by lazy { decodeAttributes(ClimateAttributes.serializer(), attributes) }
    val humidifier: HumidifierAttributes by lazy { decodeAttributes(HumidifierAttributes.serializer(), attributes) }
    val cover: CoverAttributes by lazy { decodeAttributes(CoverAttributes.serializer(), attributes) }
    val fan: FanAttributes by lazy { decodeAttributes(FanAttributes.serializer(), attributes) }
    val mediaPlayer: MediaPlayerAttributes by lazy {
        decodeAttributes(MediaPlayerAttributes.serializer(), attributes)
    }
    val input: InputAttributes by lazy { decodeAttributes(InputAttributes.serializer(), attributes) }
    val vacuum: VacuumAttributes by lazy { decodeAttributes(VacuumAttributes.serializer(), attributes) }
    val calendar: CalendarAttributes by lazy { decodeAttributes(CalendarAttributes.serializer(), attributes) }
    val alarm: AlarmAttributes by lazy { decodeAttributes(AlarmAttributes.serializer(), attributes) }
    val siren: SirenAttributes by lazy { decodeAttributes(SirenAttributes.serializer(), attributes) }
    val weather: WeatherAttributes by lazy { decodeAttributes(WeatherAttributes.serializer(), attributes) }
    val timer: TimerAttributes by lazy { decodeAttributes(TimerAttributes.serializer(), attributes) }

    val name: String
        get() = common.friendlyName
            ?: id.objectId
                .replace('_', ' ')
                .replaceFirstChar {
                    it.uppercase()
                }

    val availability: Availability get() = Availability.of(state)
    val isAvailable: Boolean get() = availability == Availability.AVAILABLE

    val switchState: SwitchState get() = SwitchState.of(state)
    val isOn: Boolean get() = switchState == SwitchState.ON
    val coverState: CoverState get() = CoverState.of(state)
    val lockState: LockState get() = LockState.of(state)
    val alarmState: AlarmState get() = AlarmState.of(state)
    val mediaPlayerState: MediaPlayerState get() = MediaPlayerState.of(state)
    val hvacMode: HvacMode get() = HvacMode.of(state)
    val vacuumState: VacuumState get() = VacuumState.of(state)
    val timerState: TimerState get() = TimerState.of(state)
    val presenceState: PresenceState get() = PresenceState.of(state)

    /** A text, number or boolean attribute the layout names, as text; null for a list, an object, or none. */
    fun attributeText(key: String): String? = (attributes[key] as? JsonPrimitive)
        ?.takeIf { it.isString || it.doubleOrNull != null || it.booleanOrNull != null }
        ?.content

    /** A numeric attribute the layout names. */
    fun attributeNumber(key: String): Double? = (attributes[key] as? JsonPrimitive)?.doubleOrNull

    /** A list attribute the layout names, each entry as text. */
    fun attributeList(key: String): List<String> =
        (attributes[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    fun features(): Int = common.supportedFeatures

    fun supports(featureBit: Int): Boolean = features() and featureBit != 0

    fun numericState(): Double? = state.toDoubleOrNull()

    /** Home Assistant's `rgb_color` as opaque ARGB, or null when the entity reports none. */
    fun readRgb(): Int? {
        val channels = light.rgbColor.map { it.roundToInt() }

        if (channels.size < RGB_CHANNELS) return null

        val red = channels[0].coerceIn(0, CHANNEL_MAX)
        val green = channels[1].coerceIn(0, CHANNEL_MAX)
        val blue = channels[2].coerceIn(0, CHANNEL_MAX)

        return (OPAQUE_ALPHA shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    /** [readRgb] as `#RRGGBB`, for the editor. */
    fun readRgbHex(): String? = readRgb()?.let { "#%06X".format(Locale.ROOT, it and RGB_MASK) }

    /** This entity with another state and some attributes replaced, as Home Assistant would report it. */
    fun with(state: String = this.state, attrs: Map<String, JsonElement> = emptyMap()): EntityState =
        copy(state = state, attributes = JsonObject(attributes + attrs))
}

private const val RGB_CHANNELS = 3
private const val CHANNEL_MAX = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE_ALPHA = 0xFF
private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0xFFFFFF

/** One demo service invocation: target entity, service name, and payload. */
data class ServiceCall(
    val entityId: String,
    val service: ServiceRef,
    val data: ServiceData,
    val current: EntityState,
) {
    val domain: Domain get() = service.domain

    fun num(key: String) = (data[key] as? JsonPrimitive)?.doubleOrNull
    fun str(key: String) = (data[key] as? JsonPrimitive)?.contentOrNull
    fun bool(key: String) = (data[key] as? JsonPrimitive)?.booleanOrNull
}
