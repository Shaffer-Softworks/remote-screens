package dev.remotescreens.server.ha

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.sin

/**
 * A made-up home, for demo mode (no Home Assistant), the tests and screenshots. Services change its states the way
 * Home Assistant would, so a tap on a demo screen does what it does at home.
 */
class DemoHome : HomeAssistant {
    private val _states = MutableStateFlow(initialStates().associateBy { it.entityId })
    private val _events = MutableSharedFlow<HaEvent>(extraBufferCapacity = 64)
    override val connected: StateFlow<Boolean> = MutableStateFlow(true)
    override val states: StateFlow<Map<String, EntityState>> = _states
    override val events: SharedFlow<HaEvent> = _events
    override val config: StateFlow<HaConfig> = MutableStateFlow(HaConfig(locationName = "Demo home"))

    val calls = mutableListOf<Triple<String, String, JsonObject>>()

    fun set(entityId: String, state: String, attrs: Map<String, JsonElement> = emptyMap()) {
        _states.update { all ->
            val old = all[entityId] ?: EntityState(entityId, state)
            all + (entityId to old.with(state, attrs))
        }
    }

    fun fire(type: String, data: JsonObject) {
        _events.tryEmit(HaEvent(type, data))
    }

    override suspend fun callService(domain: String, service: String, data: JsonObject, returnResponse: Boolean): JsonElement? {
        calls += Triple(domain, service, data)
        val ids = when (val e = data["entity_id"]) {
            is JsonPrimitive -> listOf(e.content)
            is JsonArray -> e.map { it.jsonPrimitive.content }
            else -> emptyList()
        }
        if (domain == "weather" && service == "get_forecasts") return forecasts(ids)
        for (id in ids) apply(id, domain, service, data)
        return null
    }

    private fun apply(id: String, domain: String, service: String, data: JsonObject) {
        val current = _states.value[id] ?: return
        fun num(key: String) = (data[key] as? JsonPrimitive)?.doubleOrNull
        fun str(key: String) = (data[key] as? JsonPrimitive)?.contentOrNull
        when (service) {
            "toggle" -> set(id, if (current.state == "on" || current.state == "open") off(current.domain) else on(current.domain))
            "turn_on" -> {
                val attrs = mutableMapOf<String, JsonElement>()
                num("brightness_pct")?.let { attrs["brightness"] = JsonPrimitive((it * 2.55).toInt()) }
                num("percentage")?.let { attrs["percentage"] = JsonPrimitive(it.toInt()) }
                (data["hs_color"] as? JsonArray)?.let { attrs["hs_color"] = it }
                num("color_temp_kelvin")?.let { attrs["color_temp_kelvin"] = JsonPrimitive(it.toInt()) }
                str("effect")?.let { attrs["effect"] = JsonPrimitive(it) }
                if (current.domain == "scene" || current.domain == "script") return
                set(id, "on", attrs)
            }
            "turn_off" -> set(id, "off")
            "open_cover" -> set(id, "open", mapOf("current_position" to JsonPrimitive(100)))
            "close_cover" -> set(id, "closed", mapOf("current_position" to JsonPrimitive(0)))
            "set_cover_position" -> num("position")?.let { set(id, if (it > 0) "open" else "closed", mapOf("current_position" to JsonPrimitive(it.toInt()))) }
            "lock" -> set(id, "locked")
            "unlock" -> set(id, "unlocked")
            "set_temperature" -> num("temperature")?.let { set(id, current.state, mapOf("temperature" to JsonPrimitive(it))) }
            "set_hvac_mode" -> str("hvac_mode")?.let { set(id, it) }
            "media_play_pause" -> set(id, if (current.state == "playing") "paused" else "playing")
            "media_play" -> set(id, "playing")
            "media_pause" -> set(id, "paused")
            "volume_set" -> num("volume_level")?.let { set(id, current.state, mapOf("volume_level" to JsonPrimitive(it))) }
            "select_option" -> str("option")?.let { set(id, it) }
            "start" -> set(id, "cleaning")
            "return_to_base" -> set(id, "returning")
            "stop", "pause" -> set(id, if (domain == "vacuum") "idle" else current.state)
            "alarm_disarm" -> set(id, "disarmed")
            "alarm_arm_away" -> set(id, "armed_away")
            "alarm_arm_home" -> set(id, "armed_home")
            "alarm_arm_night" -> set(id, "armed_night")
            "set_percentage" -> num("percentage")?.let { set(id, if (it > 0) "on" else "off", mapOf("percentage" to JsonPrimitive(it.toInt()))) }
            "press" -> set(id, java.time.Instant.now().toString())
        }
    }

    private fun on(domain: String) = if (domain == "cover") "open" else "on"
    private fun off(domain: String) = if (domain == "cover") "closed" else "off"

    override suspend fun command(type: String, payload: JsonObject): JsonElement? = when (type) {
        "config/area_registry/list" -> buildJsonArray {
            for ((id, name) in AREAS) add(buildJsonObject { put("area_id", id); put("name", name) })
        }
        "config/entity_registry/list_for_display" -> buildJsonObject {
            put("entities", buildJsonArray {
                for ((entity, area) in ENTITY_AREAS) add(buildJsonObject { put("ei", entity); put("ai", area) })
            })
        }
        "history/history_during_period" -> {
            val ids = payload["entity_ids"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            buildJsonObject {
                for (id in ids) put(id, buildJsonArray {
                    val now = System.currentTimeMillis() / 1000.0
                    for (i in 0 until 48) add(buildJsonObject {
                        put("s", "%.1f".format(java.util.Locale.ROOT, 19.5 + 2.5 * sin(i / 7.0)))
                        put("lu", now - (48 - i) * 1800)
                    })
                })
            }
        }
        "energy/get_prefs" -> buildJsonObject {
            put("energy_sources", buildJsonArray {
                add(buildJsonObject { put("type", "solar"); put("stat_rate", "sensor.solar_power") })
                add(buildJsonObject { put("type", "grid"); put("stat_rate", "sensor.grid_power") })
                add(buildJsonObject { put("type", "battery"); put("stat_rate", "sensor.battery_power") })
            })
            put("device_consumption", buildJsonArray {
                add(buildJsonObject { put("stat_consumption", "sensor.heat_pump_energy"); put("stat_rate", "sensor.heat_pump_power"); put("name", "Heat pump") })
            })
        }
        "media_player/browse_media" -> buildJsonObject {
            put("title", "Library")
            put("media_content_id", "")
            put("children", buildJsonArray {
                for ((title, id) in listOf("Sunday Morning" to "playlist:1", "Focus" to "playlist:2", "Road Trip" to "album:3")) {
                    add(buildJsonObject {
                        put("title", title); put("media_content_id", id); put("media_content_type", "playlist")
                        put("can_play", true); put("can_expand", false)
                    })
                }
            })
        }
        "frontend/get_translations" -> buildJsonObject { put("resources", buildJsonObject { }) }
        else -> null
    }

    override suspend fun fetch(path: String): ByteArray? = picture(path.hashCode())

    private fun forecasts(ids: List<String>): JsonElement = buildJsonObject {
        for (id in ids) put(id, buildJsonObject {
            put("forecast", buildJsonArray {
                val conditions = listOf("sunny", "partlycloudy", "rainy", "cloudy", "sunny", "pouring", "partlycloudy")
                for (d in 0 until 7) add(buildJsonObject {
                    put("datetime", java.time.LocalDate.now().plusDays(d.toLong()).toString() + "T12:00:00+00:00")
                    put("condition", conditions[d])
                    put("temperature", 18 + d % 4)
                    put("templow", 9 + d % 3)
                    put("precipitation_probability", listOf(0, 10, 80, 30, 0, 90, 20)[d])
                })
            })
        })
    }

    companion object {
        val AREAS = listOf("living_room" to "Living room", "kitchen" to "Kitchen", "bedroom" to "Bedroom", "hallway" to "Hallway", "garden" to "Garden")
        val ENTITY_AREAS = listOf(
            "light.living_room" to "living_room", "light.table_lamp" to "living_room", "media_player.living_room" to "living_room",
            "climate.living_room" to "living_room", "cover.living_room_curtains" to "living_room",
            "light.kitchen" to "kitchen", "switch.coffee_machine" to "kitchen", "sensor.kitchen_temperature" to "kitchen",
            "light.bedroom" to "bedroom", "fan.bedroom" to "bedroom",
            "lock.front_door" to "hallway", "camera.front_door" to "hallway", "alarm_control_panel.house" to "hallway",
            "light.garden" to "garden", "weather.home" to "garden",
        )

        private fun attrs(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) ->
            k to when (v) {
                null -> JsonPrimitive(null as String?)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
        })

        fun initialStates(): List<EntityState> = listOf(
            EntityState("light.living_room", "on", attrs("friendly_name" to "Ceiling light", "brightness" to 150, "supported_color_modes" to listOf("hs", "color_temp"), "color_mode" to "hs", "effect_list" to listOf("Rainbow", "Candle", "Pulse"))),
            EntityState("light.table_lamp", "off", attrs("friendly_name" to "Table lamp", "supported_color_modes" to listOf("brightness"))),
            EntityState("light.kitchen", "on", attrs("friendly_name" to "Kitchen lights", "brightness" to 255, "supported_color_modes" to listOf("onoff"))),
            EntityState("light.bedroom", "off", attrs("friendly_name" to "Bedside lamp", "supported_color_modes" to listOf("brightness"))),
            EntityState("light.garden", "off", attrs("friendly_name" to "Garden lights", "supported_color_modes" to listOf("onoff"))),
            EntityState("switch.coffee_machine", "off", attrs("friendly_name" to "Coffee machine", "icon" to "mdi:coffee")),
            EntityState("fan.bedroom", "on", attrs("friendly_name" to "Bedroom fan", "percentage" to 50, "supported_features" to 1)),
            EntityState("sensor.kitchen_temperature", "21.4", attrs("friendly_name" to "Kitchen temperature", "unit_of_measurement" to "°C", "device_class" to "temperature", "state_class" to "measurement")),
            EntityState("sensor.outdoor_temperature", "12.8", attrs("friendly_name" to "Outdoor", "unit_of_measurement" to "°C", "device_class" to "temperature", "state_class" to "measurement")),
            EntityState("sensor.power", "432", attrs("friendly_name" to "Power usage", "unit_of_measurement" to "W", "device_class" to "power", "state_class" to "measurement")),
            EntityState("sensor.solar_power", "5200", attrs("friendly_name" to "Solar", "unit_of_measurement" to "W", "device_class" to "power")),
            EntityState("sensor.grid_power", "-1200", attrs("friendly_name" to "Grid", "unit_of_measurement" to "W", "device_class" to "power")),
            EntityState("sensor.battery_power", "1500", attrs("friendly_name" to "Battery", "unit_of_measurement" to "W", "device_class" to "power")),
            EntityState("sensor.heat_pump_power", "900", attrs("friendly_name" to "Heat pump", "unit_of_measurement" to "W", "device_class" to "power")),
            EntityState("sensor.heat_pump_energy", "6.2", attrs("friendly_name" to "Heat pump energy", "unit_of_measurement" to "kWh", "device_class" to "energy")),
            EntityState("binary_sensor.front_door", "off", attrs("friendly_name" to "Front door", "device_class" to "door")),
            EntityState("scene.movie_night", "2026-01-01T00:00:00+00:00", attrs("friendly_name" to "Movie night", "icon" to "mdi:movie")),
            EntityState("script.good_night", "off", attrs("friendly_name" to "Good night", "icon" to "mdi:weather-night")),
            EntityState("cover.living_room_curtains", "open", attrs("friendly_name" to "Curtains", "current_position" to 70, "supported_features" to 15, "device_class" to "curtain")),
            EntityState("lock.front_door", "locked", attrs("friendly_name" to "Front door", "supported_features" to 1)),
            EntityState("climate.living_room", "heat", attrs("friendly_name" to "Heating", "current_temperature" to 20.5, "temperature" to 21.0, "min_temp" to 7, "max_temp" to 30, "target_temp_step" to 0.5, "hvac_modes" to listOf("off", "heat", "auto"), "hvac_action" to "heating", "supported_features" to 1)),
            EntityState("media_player.living_room", "playing", attrs("friendly_name" to "Living room speaker", "media_title" to "Clair de Lune", "media_artist" to "Debussy", "media_album_name" to "Suite bergamasque", "volume_level" to 0.35, "supported_features" to 152463, "entity_picture" to "/api/media_player_proxy/media_player.living_room", "source_list" to listOf("Spotify", "Radio"))),
            EntityState("weather.home", "partlycloudy", attrs("friendly_name" to "Home", "temperature" to 14.2, "temperature_unit" to "°C", "humidity" to 71, "wind_speed" to 12, "wind_speed_unit" to "km/h",
                "pressure" to 1013, "pressure_unit" to "hPa", "supported_features" to 3)),
            EntityState("camera.front_door", "idle", attrs("friendly_name" to "Front door camera", "entity_picture" to "/api/camera_proxy/camera.front_door")),
            EntityState("alarm_control_panel.house", "disarmed", attrs("friendly_name" to "House alarm", "code_format" to "number", "supported_features" to 63)),
            EntityState("input_select.washing_program", "Cotton eco", attrs("friendly_name" to "Washing machine", "options" to listOf("Cotton", "Cotton eco", "Synthetics", "Wool", "Quick 15'"))),
            EntityState("input_boolean.guest_mode", "off", attrs("friendly_name" to "Guest mode")),
            EntityState("vacuum.robot", "docked", attrs("friendly_name" to "Robot", "battery_level" to 100, "fan_speed" to "Standard", "fan_speed_list" to listOf("Quiet", "Standard", "Turbo", "Max"), "supported_features" to 13180)),
            EntityState("person.sam", "home", attrs("friendly_name" to "Sam", "latitude" to 52.37, "longitude" to 4.89)),
            EntityState("person.alex", "not_home", attrs("friendly_name" to "Alex", "latitude" to 52.35, "longitude" to 4.92)),
            EntityState("button.doorbell_test", "unknown", attrs("friendly_name" to "Test doorbell")),
            EntityState("calendar.family", "off", attrs("friendly_name" to "Family", "message" to "Dentist appointment", "location" to "Main St",
                "all_day" to false, "start_time" to "${java.time.LocalDate.now().plusDays(1)} 19:30:00", "end_time" to "${java.time.LocalDate.now().plusDays(1)} 20:30:00")),
            EntityState("sun.sun", "above_horizon", attrs("friendly_name" to "Sun", "next_rising" to "2026-10-08T07:45:00+00:00", "next_setting" to "2026-10-07T18:40:00+00:00")),
        )

        /** A soft gradient, standing in for a camera snapshot or an album cover. */
        fun picture(seed: Int, w: Int = 320, h: Int = 240): ByteArray {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            val hue = (seed and 0xff) / 255f
            g.paint = GradientPaint(0f, 0f, Color.getHSBColor(hue, 0.35f, 0.95f), w.toFloat(), h.toFloat(), Color.getHSBColor(hue + 0.15f, 0.55f, 0.6f))
            g.fillRect(0, 0, w, h)
            g.dispose()
            return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        }
    }
}
