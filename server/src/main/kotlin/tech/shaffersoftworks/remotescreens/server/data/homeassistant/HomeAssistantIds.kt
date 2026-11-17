package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** An entity id such as `light.kitchen`. */
@Serializable
@JvmInline
value class EntityId(val value: String) {
    val domain: Domain get() = Domain(value.substringBefore('.'))
    val objectId: String get() = value.substringAfter('.')

    override fun toString(): String = value
}

/** A Home Assistant domain such as `light`. Open-ended: the catalogue and layouts may name any domain. */
@JvmInline
value class Domain(val value: String) {
    override fun toString(): String = value

    /** [entityId] is one of this domain's, such as `light.kitchen` for [LIGHT]. */
    fun owns(entityId: String): Boolean = entityId.startsWith("$value.")

    companion object {
        val ALARM_PANEL = Domain("alarm_control_panel")
        val BINARY_SENSOR = Domain("binary_sensor")
        val BUTTON = Domain("button")
        val CALENDAR = Domain("calendar")
        val CAMERA = Domain("camera")
        val CLIMATE = Domain("climate")
        val COUNTER = Domain("counter")
        val COVER = Domain("cover")
        val DEVICE_TRACKER = Domain("device_tracker")
        val FAN = Domain("fan")
        val HUMIDIFIER = Domain("humidifier")
        val INPUT_BOOLEAN = Domain("input_boolean")
        val INPUT_BUTTON = Domain("input_button")
        val INPUT_DATETIME = Domain("input_datetime")
        val INPUT_NUMBER = Domain("input_number")
        val INPUT_SELECT = Domain("input_select")
        val INPUT_TEXT = Domain("input_text")
        val LAWN_MOWER = Domain("lawn_mower")
        val LIGHT = Domain("light")
        val LOCK = Domain("lock")
        val MEDIA_PLAYER = Domain("media_player")
        val MUSIC_ASSISTANT = Domain("music_assistant")
        val NUMBER = Domain("number")
        val PERSON = Domain("person")
        val SCENE = Domain("scene")
        val SCRIPT = Domain("script")
        val SELECT = Domain("select")
        val SENSOR = Domain("sensor")
        val SIREN = Domain("siren")
        val SUN = Domain("sun")
        val SWITCH = Domain("switch")
        val TIMER = Domain("timer")
        val TODO = Domain("todo")
        val VACUUM = Domain("vacuum")
        val VALVE = Domain("valve")
        val WATER_HEATER = Domain("water_heater")
        val WEATHER = Domain("weather")
    }
}

/** A service such as `light.turn_on`. */
data class ServiceRef(val domain: Domain, val service: String) {
    override fun toString(): String = "$domain.$service"

    companion object {
        /** `light.turn_on` as a [ServiceRef]; null without a domain and a service. */
        fun parse(text: String): ServiceRef? {
            val domain = text.substringBefore('.', missingDelimiterValue = "")
            val service = text.substringAfter('.', missingDelimiterValue = "")
            return if (domain.isEmpty() || service.isEmpty()) null else ServiceRef(Domain(domain), service)
        }

        val CALENDAR_CREATE_EVENT = ServiceRef(Domain.CALENDAR, "create_event")
        val LIGHT_TURN_ON = ServiceRef(Domain.LIGHT, "turn_on")
        val MEDIA_PLAYER_CLEAR_PLAYLIST = ServiceRef(Domain.MEDIA_PLAYER, "clear_playlist")
        val MEDIA_PLAYER_PLAY_MEDIA = ServiceRef(Domain.MEDIA_PLAYER, "play_media")
        val MEDIA_PLAYER_VOLUME_DOWN = ServiceRef(Domain.MEDIA_PLAYER, "volume_down")
        val MEDIA_PLAYER_VOLUME_UP = ServiceRef(Domain.MEDIA_PLAYER, "volume_up")
        val TODO_GET_ITEMS = ServiceRef(Domain.TODO, "get_items")
        val VACUUM_CLEAN_AREA = ServiceRef(Domain.VACUUM, "clean_area")
        val VACUUM_SEND_COMMAND = ServiceRef(Domain.VACUUM, "send_command")
        val WEATHER_GET_FORECASTS = ServiceRef(Domain.WEATHER, "get_forecasts")
    }
}

/**
 * The `service_data` of a call. The catalogue and notification buttons may send any field a service takes, so this
 * stays an open map; fixed calls build it from a typed request instead.
 */
@JvmInline
value class ServiceData(val fields: JsonObject) {
    /** The `entity_id` the data names, one or many. */
    val entityIds: List<EntityId>
        get() = when (val target = fields[ENTITY_ID]) {
            is JsonPrimitive -> listOfNotNull(target.contentOrNull?.let(::EntityId))
            is JsonArray -> target.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::EntityId) }
            else -> emptyList()
        }

    operator fun get(key: String): JsonElement? = fields[key]

    /** The data without `entity_id`, which Home Assistant takes as the call's `target`. */
    fun withoutTarget(): ServiceData = ServiceData(JsonObject(fields - ENTITY_ID))

    companion object {
        const val ENTITY_ID = "entity_id"
        val EMPTY = ServiceData(JsonObject(emptyMap()))
    }
}
