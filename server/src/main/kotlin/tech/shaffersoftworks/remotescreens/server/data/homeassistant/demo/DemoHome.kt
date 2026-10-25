// Demo fixture data: the numbers are made-up entity values and the long lines are one entity each.
@file:Suppress("MagicNumber", "MaxLineLength")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.calendarEntityId
import tech.shaffersoftworks.remotescreens.server.data.renderdata.isCalendarListPath
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantEvent
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
    private val _events = MutableSharedFlow<HomeAssistantEvent>(extraBufferCapacity = 64)
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)
    override val states: StateFlow<Map<String, EntityState>> = _states
    override val events: SharedFlow<HomeAssistantEvent> = _events
    override val config: StateFlow<HomeAssistantConfig> = MutableStateFlow(
        HomeAssistantConfig(locationName = "Demo home"),
    )

    val calls = mutableListOf<Triple<String, String, JsonObject>>()
    /** WebSocket commands the demo recorded, such as `calendar/event/delete`. */
    val commands = mutableListOf<Pair<String, JsonObject>>()
    /** Uids marked complete via `todo.update_item`, so `get_items` drops them. */
    private val completedTodos = mutableSetOf<String>()
    private val serviceHandlers = DemoServiceHandlers(::set, completedTodos)

    fun set(entityId: String, state: String, attrs: Map<String, JsonElement> = emptyMap()) {
        _states.update { all ->
            val old = all[entityId] ?: EntityState(entityId, state)
            all + (entityId to old.with(state, attrs))
        }
    }

    fun fire(type: String, data: JsonObject) {
        _events.tryEmit(HomeAssistantEvent(type, data))
    }

    override suspend fun callService(domain: String, service: String, data: JsonObject, returnResponse: Boolean): JsonElement? {
        calls += Triple(domain, service, data)
        val ids = when (val entityIdValue = data["entity_id"]) {
            is JsonPrimitive -> listOf(entityIdValue.content)
            is JsonArray -> entityIdValue.map { it.jsonPrimitive.content }
            else -> emptyList()
        }
        return when (domain) {
            "weather" if service == "get_forecasts" -> forecasts(ids)
            "todo" if service == "get_items" -> todoItems(ids)
            "music_assistant" -> musicAssistant(service, data)
            else -> {
                for (id in ids) apply(id, domain, service, data)
                null
            }
        }
    }

    private fun musicAssistant(service: String, data: JsonObject): JsonElement? = when (service) {
        "get_library" -> demoMusicLibrary()
        "search" -> demoMusicSearch(data["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
        "get_queue" -> demoMusicQueue()
        "play_media" -> null
        else -> null
    }

    private fun demoMusicLibrary(): JsonObject = buildJsonObject {
        put(
            "service_response",
            buildJsonObject {
                put(
                    "items",
                    buildJsonArray {
                        listOf(
                            "Neon Skyline" to "Synthwave Trio",
                            "Where I Find God" to "Larry Fleet",
                            "Phantom Grid" to "Summer Nights",
                        ).forEach { (title, artist) ->
                            add(
                                buildJsonObject {
                                    put("uri", "demo://track/${title.hashCode()}")
                                    put("name", title)
                                    put("artist", artist)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    private fun demoMusicSearch(name: String): JsonObject = buildJsonObject {
        put(
            "service_response",
            buildJsonObject {
                put(
                    "tracks",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("uri", "demo://search/${name.hashCode()}")
                                put("name", if (name.isBlank()) "Night Drive" else name)
                                put("artist", "Demo Artist")
                            },
                        )
                    },
                )
            },
        )
    }

    private fun demoMusicQueue(): JsonObject = buildJsonObject {
        put(
            "service_response",
            buildJsonObject {
                put(
                    "current_item",
                    buildJsonObject {
                        put("name", "Phantom Grid")
                        put("queue_item_id", "q1")
                        put(
                            "media_item",
                            buildJsonObject {
                                put("uri", "demo://queue/1")
                                put("name", "Phantom Grid")
                                put(
                                    "artists",
                                    buildJsonArray {
                                        add(buildJsonObject { put("name", "Summer Nights") })
                                    },
                                )
                            },
                        )
                    },
                )
                put(
                    "next_item",
                    buildJsonObject {
                        put("name", "Next Up")
                        put("queue_item_id", "q2")
                        put(
                            "media_item",
                            buildJsonObject {
                                put("uri", "demo://queue/2")
                                put("name", "Next Up")
                            },
                        )
                    },
                )
                put("items", 5)
            },
        )
    }

    private fun apply(id: String, domain: String, service: String, data: JsonObject) {
        val current = _states.value[id] ?: return
        serviceHandlers.apply(id, domain, service, data, current)
    }

    private fun todoItems(ids: List<String>): JsonObject = buildJsonObject {
        put("response", buildJsonObject {
            for (id in ids) {
                put(id, buildJsonObject {
                    put("items", buildJsonArray {
                        listOf("milk" to "Buy milk", "bins" to "Take out bins")
                            .filter { (uid, _) -> uid !in completedTodos }
                            .forEach { (uid, summary) ->
                                add(buildJsonObject {
                                    put("uid", uid)
                                    put("summary", summary)
                                    put("status", "needs_action")
                                })
                            }
                    })
                })
            }
        })
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun command(type: String, payload: JsonObject): JsonElement? = when (type) {
        "fire_event" -> {
            val eventType = payload["event_type"]?.jsonPrimitive?.contentOrNull ?: return null
            fire(eventType, payload["event_data"] as? JsonObject ?: JsonObject(emptyMap()))
            JsonObject(emptyMap())
        }
        "config/area_registry/list" -> buildJsonArray {
            for ((id, name) in AREAS) add(buildJsonObject { put("area_id", id); put("name", name) })
        }
        "config/entity_registry/get" -> {
            val entityId = payload["entity_id"]?.jsonPrimitive?.contentOrNull
            if (entityId == "media_player.living_room") {
                buildJsonObject {
                    put("entity_id", entityId)
                    put("config_entry_id", "demo_music_assistant")
                }
            } else {
                null
            }
        }
        "config/device_registry/list" -> buildJsonArray {
            for ((id, area) in DEVICE_AREAS) add(buildJsonObject { put("id", id); put("area_id", area) })
        }
        "config/entity_registry/list_for_display" -> buildJsonObject {
            put("entities", buildJsonArray {
                for ((entity, area) in ENTITY_AREAS) add(
                    buildJsonObject { put("ei", entity); put("ai", area) },
                )
                for ((entity, device) in ENTITY_DEVICES) add(
                    buildJsonObject { put("ei", entity); put("di", device) },
                )
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
                add(
                    buildJsonObject { put("type", "solar"); put("stat_rate", "sensor.solar_power") },
                )
                add(buildJsonObject { put("type", "grid"); put("stat_rate", "sensor.grid_power") })
                add(
                    buildJsonObject { put(
                        "type",
                        "battery",
                    ); put("stat_rate", "sensor.battery_power") },
                )
            })
            put("device_consumption", buildJsonArray {
                add(
                    buildJsonObject { put(
                        "stat_consumption",
                        "sensor.heat_pump_energy",
                    ); put("stat_rate", "sensor.heat_pump_power"); put("name", "Heat pump") },
                )
            })
        }
        "media_player/browse_media" -> buildJsonObject {
            put("title", "Library")
            put("media_content_id", "")
            put("children", buildJsonArray {
                for ((title, id) in listOf(
                    "Sunday Morning" to "playlist:1",
                    "Focus" to "playlist:2",
                    "Road Trip" to "album:3",
                )) {
                    add(buildJsonObject {
                        put(
                            "title",
                            title,
                        ); put("media_content_id", id); put("media_content_type", "playlist")
                        put("can_play", true); put("can_expand", false)
                    })
                }
            })
        }
        "frontend/get_translations" -> buildJsonObject { put("resources", buildJsonObject { }) }
        "calendar/event/delete" -> {
            commands += type to payload
            JsonObject(emptyMap())
        }
        else -> null
    }

    override suspend fun fetch(path: String): ByteArray? {
        calendarPayload(path)?.let { return it.toByteArray(Charsets.UTF_8) }
        return picture(path.hashCode())
    }

    private fun calendarPayload(path: String): String? {
        val entityId = calendarEntityId(path)
        return when {
            isCalendarListPath(path) -> calendarListJson()
            entityId != null -> calendarEventsJson(entityId)
            else -> null
        }
    }

    private fun calendarListJson(): String = buildJsonArray {
        add(buildJsonObject { put("entity_id", "calendar.family"); put("name", "Family") })
        add(buildJsonObject { put("entity_id", "calendar.holidays"); put("name", "Holidays") })
    }.toString()

    private fun calendarEventsJson(entityId: String): String {
        val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1)
        val later = day.plusDays(1)
        return when (entityId) {
            "calendar.family" -> buildJsonArray {
                add(buildJsonObject {
                    put("summary", "Dentist appointment")
                    put("location", "Main St")
                    put("uid", "demo-dentist")
                    put("start", buildJsonObject { put("dateTime", "${day}T17:30:00Z") })
                    put("end", buildJsonObject { put("dateTime", "${day}T18:30:00Z") })
                })
            }.toString()
            "calendar.holidays" -> buildJsonArray {
                add(buildJsonObject {
                    put("summary", "School holiday")
                    put("uid", "demo-holiday")
                    put("start", buildJsonObject { put("date", later.toString()) })
                    put("end", buildJsonObject { put("date", later.plusDays(1).toString()) })
                })
            }.toString()
            else -> "[]"
        }
    }

    /** The demo cameras have no video, so screens fall back to their snapshots. */
    override suspend fun stream(path: String, onBody: suspend (contentType: String?, body: ByteReadChannel) -> Unit): Int =
        HttpStatusCode.NotFound.value

    private fun forecasts(ids: List<String>): JsonElement = buildJsonObject {
        for (id in ids) put(id, buildJsonObject {
            put("forecast", buildJsonArray {
                val conditions = listOf(
                    "sunny",
                    "partlycloudy",
                    "rainy",
                    "cloudy",
                    "sunny",
                    "pouring",
                    "partlycloudy",
                )
                for (day in 0 until 7) add(buildJsonObject {
                    put(
                        "datetime",
                        java.time.LocalDate.now().plusDays(
                            day.toLong(),
                        ).toString() + "T12:00:00+00:00",
                    )
                    put("condition", conditions[day])
                    put("temperature", 18 + day % 4)
                    put("templow", 9 + day % 3)
                    put("precipitation_probability", listOf(0, 10, 80, 30, 0, 90, 20)[day])
                })
            })
        })
    }

    companion object {
        val AREAS = listOf(
            "living_room" to "Living room",
            "kitchen" to "Kitchen",
            "bedroom" to "Bedroom",
            "hallway" to "Hallway",
            "garden" to "Garden",
        )
        val ENTITY_AREAS = listOf(
            "light.living_room" to "living_room", "light.table_lamp" to "living_room", "media_player.living_room" to "living_room",
            "climate.living_room" to "living_room", "cover.living_room_curtains" to "living_room",
            "light.kitchen" to "kitchen", "switch.coffee_machine" to "kitchen", "sensor.kitchen_temperature" to "kitchen",
            "light.bedroom" to "bedroom", "fan.bedroom" to "bedroom",
            "lock.front_door" to "hallway", "camera.front_door" to "hallway", "alarm_control_panel.house" to "hallway",
            "light.garden" to "garden", "weather.home" to "garden",
        )
        /** Entities that take their area from their device, as most in a real home do. */
        val ENTITY_DEVICES = listOf("switch.hallway_light" to "hallway_switch")
        val DEVICE_AREAS = listOf("hallway_switch" to "hallway")

        private fun attrs(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) ->
            k to when (v) {
                null -> JsonPrimitive(null as String?)
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
        })

        /** An Alarmo area panel: `arm_mode` beside the state, and `open_sensors` null unless one is open. */
        private fun alarmoZone(entityId: String, name: String, openSensor: String? = null): EntityState {
            val base = attrs(
                "friendly_name" to name,
                "code_format" to "number",
                "supported_features" to 7,
                "arm_mode" to "disarmed",
                "delay" to 0,
            )
            val sensors = openSensor?.let { JsonObject(mapOf(it to JsonPrimitive("on"))) } ?: JsonNull
            return EntityState(entityId, "disarmed", JsonObject(base + ("open_sensors" to sensors)))
        }

        @Suppress("LongMethod")
        fun initialStates(): List<EntityState> = listOf(
            EntityState(
                "light.living_room",
                "on",
                attrs(
                    "friendly_name" to "Ceiling light",
                    "brightness" to 150,
                    "supported_color_modes" to listOf("hs", "color_temp"),
                    "color_mode" to "hs",
                    "effect_list" to listOf("Rainbow", "Candle", "Pulse"),
                ),
            ),
            EntityState(
                "light.table_lamp",
                "off",
                attrs(
                    "friendly_name" to "Table lamp",
                    "supported_color_modes" to listOf("brightness"),
                ),
            ),
            EntityState(
                "light.kitchen",
                "on",
                attrs(
                    "friendly_name" to "Kitchen lights",
                    "brightness" to 255,
                    "supported_color_modes" to listOf("onoff"),
                ),
            ),
            EntityState(
                "light.bedroom",
                "off",
                attrs(
                    "friendly_name" to "Bedside lamp",
                    "supported_color_modes" to listOf("brightness"),
                ),
            ),
            EntityState(
                "light.garden",
                "off",
                attrs(
                    "friendly_name" to "Garden lights",
                    "supported_color_modes" to listOf("onoff"),
                ),
            ),
            EntityState(
                "switch.coffee_machine",
                "off",
                attrs("friendly_name" to "Coffee machine", "icon" to "mdi:coffee"),
            ),
            EntityState(
                "switch.hallway_light",
                "on",
                attrs("friendly_name" to "Hallway light", "icon" to "mdi:ceiling-light"),
            ),
            EntityState("fan.bedroom", "on", attrs(
                "friendly_name" to "Bedroom fan",
                "percentage" to 50,
                "oscillating" to true,
                "direction" to "forward",
                "preset_modes" to listOf("auto", "breeze", "sleep"),
                "preset_mode" to "breeze",
                "supported_features" to 15,
            )),
            EntityState("humidifier.bedroom", "on", attrs(
                "friendly_name" to "Bedroom humidifier",
                "humidity" to 45,
                "current_humidity" to 38,
                "min_humidity" to 30,
                "max_humidity" to 80,
                "available_modes" to listOf("normal", "eco", "away"),
                "mode" to "normal",
            )),
            EntityState(
                "sensor.kitchen_temperature",
                "21.4",
                attrs(
                    "friendly_name" to "Kitchen temperature",
                    "unit_of_measurement" to "°C",
                    "device_class" to "temperature",
                    "state_class" to "measurement",
                ),
            ),
            EntityState(
                "sensor.outdoor_temperature",
                "12.8",
                attrs(
                    "friendly_name" to "Outdoor",
                    "unit_of_measurement" to "°C",
                    "device_class" to "temperature",
                    "state_class" to "measurement",
                ),
            ),
            EntityState(
                "sensor.power",
                "432",
                attrs(
                    "friendly_name" to "Power usage",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                    "state_class" to "measurement",
                ),
            ),
            EntityState(
                "sensor.solar_power",
                "5200",
                attrs(
                    "friendly_name" to "Solar",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                "sensor.grid_power",
                "-1200",
                attrs(
                    "friendly_name" to "Grid",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                "sensor.battery_power",
                "1500",
                attrs(
                    "friendly_name" to "Battery",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                "sensor.heat_pump_power",
                "900",
                attrs(
                    "friendly_name" to "Heat pump",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                "sensor.heat_pump_energy",
                "6.2",
                attrs(
                    "friendly_name" to "Heat pump energy",
                    "unit_of_measurement" to "kWh",
                    "device_class" to "energy",
                ),
            ),
            EntityState(
                "binary_sensor.front_door",
                "off",
                attrs("friendly_name" to "Front door", "device_class" to "door"),
            ),
            EntityState(
                "scene.movie_night",
                "2026-01-01T00:00:00+00:00",
                attrs("friendly_name" to "Movie night", "icon" to "mdi:movie"),
            ),
            EntityState(
                "script.good_night",
                "off",
                attrs("friendly_name" to "Good night", "icon" to "mdi:weather-night"),
            ),
            EntityState(
                "cover.living_room_curtains",
                "open",
                attrs(
                    "friendly_name" to "Curtains",
                    "current_position" to 70,
                    "current_tilt_position" to 50,
                    "supported_features" to 15,
                    "device_class" to "curtain",
                ),
            ),
            EntityState(
                "lock.front_door",
                "locked",
                attrs("friendly_name" to "Front door", "supported_features" to 1),
            ),
            EntityState("climate.living_room", "heat_cool", attrs(
                "friendly_name" to "Heating",
                "current_temperature" to 21.0,
                "current_humidity" to 45,
                "temperature" to 21.0,
                "target_temp_low" to 18.0,
                "target_temp_high" to 24.0,
                "min_temp" to 7,
                "max_temp" to 30,
                "target_temp_step" to 0.5,
                "hvac_modes" to listOf("off", "heat", "cool", "heat_cool"),
                "hvac_action" to "cooling",
                "fan_modes" to listOf("auto", "on"),
                "fan_mode" to "auto",
                "preset_modes" to listOf("home", "away", "sleep"),
                "preset_mode" to "home",
                "swing_modes" to listOf("off", "vertical", "horizontal", "both"),
                "swing_mode" to "off",
                // TARGET_TEMPERATURE | TARGET_TEMPERATURE_RANGE | FAN_MODE | PRESET_MODE | SWING_MODE
                "supported_features" to 59,
            )),
            EntityState("climate.bedroom", "heat_cool", attrs(
                "friendly_name" to "Bedroom climate",
                "current_temperature" to 21.0,
                "temperature" to 21.0,
                "target_temp_high" to 24.0,
                "target_temp_low" to 18.0,
                "min_temp" to 7,
                "max_temp" to 30,
                "target_temp_step" to 0.5,
                "hvac_modes" to listOf("off", "heat", "cool", "heat_cool"),
                "hvac_action" to "idle",
                "supported_features" to 3,
            )),
            EntityState(
                "media_player.living_room",
                "playing",
                attrs(
                    "friendly_name" to "Living room speaker",
                    "media_title" to "Clair de Lune",
                    "media_artist" to "Debussy",
                    "media_album_name" to "Suite bergamasque",
                    "volume_level" to 0.35,
                    "media_position" to 42.0,
                    "media_duration" to 195.0,
                    "supported_features" to 152463,
                    "entity_picture" to "/api/media_player_proxy/media_player.living_room",
                    "source_list" to listOf("Spotify", "Radio"),
                ),
            ),
            EntityState("weather.home", "partlycloudy", attrs("friendly_name" to "Home", "temperature" to 14.2, "temperature_unit" to "°C", "humidity" to 71, "wind_speed" to 12, "wind_speed_unit" to "km/h",
                "pressure" to 1013, "pressure_unit" to "hPa", "supported_features" to 3,)),
            EntityState(
                "camera.front_door",
                "idle",
                attrs(
                    "friendly_name" to "Front door camera",
                    "entity_picture" to "/api/camera_proxy/camera.front_door",
                ),
            ),
            EntityState(
                "camera.driveway",
                "idle",
                attrs(
                    "friendly_name" to "Driveway camera",
                    "entity_picture" to "/api/camera_proxy/camera.driveway",
                ),
            ),
            EntityState(
                "alarm_control_panel.house",
                "disarmed",
                attrs(
                    "friendly_name" to "House alarm",
                    "code_format" to "number",
                    "supported_features" to 63,
                ),
            ),
            alarmoZone("alarm_control_panel.basement", "Basement alarm"),
            alarmoZone("alarm_control_panel.upstairs", "Upstairs alarm"),
            alarmoZone("alarm_control_panel.garage", "Garage alarm", openSensor = "binary_sensor.garage_side_door"),
            EntityState(
                "input_select.washing_program",
                "Cotton eco",
                attrs(
                    "friendly_name" to "Washing machine",
                    "options" to listOf("Cotton", "Cotton eco", "Synthetics", "Wool", "Quick 15'"),
                ),
            ),
            EntityState("input_boolean.guest_mode", "off", attrs("friendly_name" to "Guest mode")),
            EntityState(
                "vacuum.robot",
                "docked",
                attrs(
                    "friendly_name" to "Robot",
                    "battery_level" to 100,
                    "fan_speed" to "Standard",
                    "fan_speed_list" to listOf("Quiet", "Standard", "Turbo", "Max"),
                    "supported_features" to 13180,
                ),
            ),
            EntityState(
                "person.sam",
                "home",
                attrs("friendly_name" to "Sam", "latitude" to 52.37, "longitude" to 4.89),
            ),
            EntityState(
                "person.alex",
                "not_home",
                attrs("friendly_name" to "Alex", "latitude" to 52.35, "longitude" to 4.92),
            ),
            EntityState(
                "button.doorbell_test",
                "unknown",
                attrs("friendly_name" to "Test doorbell"),
            ),
            EntityState("calendar.family", "off", attrs(
                "friendly_name" to "Family",
                "message" to "Dentist appointment",
                "location" to "Main St",
                "all_day" to false,
                "start_time" to "${java.time.LocalDate.now().plusDays(1)} 19:30:00",
                "end_time" to "${java.time.LocalDate.now().plusDays(1)} 20:30:00",
                "supported_features" to 3,
            )),
            EntityState("todo.shopping", "2", attrs("friendly_name" to "Shopping")),
            EntityState(
                "timer.kitchen",
                "idle",
                attrs(
                    "friendly_name" to "Kitchen timer",
                    "duration" to "00:10:00",
                    "remaining" to "0:10:00",
                ),
            ),
            EntityState(
                "counter.guests",
                "3",
                attrs("friendly_name" to "Guests", "initial" to 0, "step" to 1),
            ),
            EntityState("water_heater.tank", "eco", attrs(
                "friendly_name" to "Hot water",
                "temperature" to 50,
                "min_temp" to 40,
                "max_temp" to 60,
                "operation_list" to listOf("off", "eco", "performance"),
                "operation_mode" to "eco",
            )),
            EntityState(
                "valve.garden",
                "open",
                attrs("friendly_name" to "Garden valve", "current_position" to 100),
            ),
            EntityState(
                "siren.alarm",
                "off",
                attrs("friendly_name" to "Siren", "available_tones" to listOf("fire", "burglar")),
            ),
            EntityState(
                "lawn_mower.yard",
                "docked",
                attrs("friendly_name" to "Mower", "battery_level" to 80),
            ),
            EntityState(
                "input_text.note",
                "Door code for Sam",
                attrs("friendly_name" to "Note", "max" to 100),
            ),
            EntityState(
                "input_datetime.quiet_hours",
                "22:00:00",
                attrs("friendly_name" to "Quiet hours", "has_date" to false, "has_time" to true),
            ),
            EntityState(
                "sun.sun",
                "above_horizon",
                attrs(
                    "friendly_name" to "Sun",
                    "next_rising" to "2026-10-08T07:45:00+00:00",
                    "next_setting" to "2026-10-07T18:40:00+00:00",
                ),
            ),
        )

        /** A soft gradient, standing in for a camera snapshot or an album cover. */
        fun picture(seed: Int, w: Int = 320, h: Int = 240): ByteArray {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val graphics = img.createGraphics()
            val hue = (seed and 0xff) / 255f
            graphics.paint = GradientPaint(
                0f,
                0f,
                Color.getHSBColor(hue, 0.35f, 0.95f),
                w.toFloat(),
                h.toFloat(),
                Color.getHSBColor(hue + 0.15f, 0.55f, 0.6f),
            )
            graphics.fillRect(0, 0, w, h)
            graphics.dispose()
            return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        }
    }
}
