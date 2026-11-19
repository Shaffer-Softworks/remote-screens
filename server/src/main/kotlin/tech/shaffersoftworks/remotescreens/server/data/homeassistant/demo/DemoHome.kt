// Demo fixture data: the numbers are made-up entity values and the long lines are one entity each.
@file:Suppress("MagicNumber", "MaxLineLength")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.LightColorMode
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HaRequest
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceData
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceRef
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
import kotlinx.serialization.json.put
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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
    private val commandHandlers = DemoCommandHandlers(::fire, commands)

    fun set(
        entityId: String,
        state: String,
        attrs: Map<String, JsonElement> = emptyMap(),
    ) {
        _states.update { all ->
            val old = all[entityId] ?: EntityState(entityId, state)
            all + (entityId to old.with(state, attrs))
        }
    }

    fun fire(
        type: String,
        data: JsonObject,
    ) {
        _events.tryEmit(HomeAssistantEvent(type, data))
    }

    override suspend fun callService(
        service: ServiceRef,
        target: List<EntityId>,
        data: ServiceData,
        returnResponse: Boolean,
    ): JsonElement? {
        val ids = target.ifEmpty { data.entityIds }.map { it.value }
        calls += Triple(first = service.domain.value, second = service.service, third = recorded(target, data))

        return when {
            service == ServiceRef.WEATHER_GET_FORECASTS -> forecasts(ids)
            service == ServiceRef.TODO_GET_ITEMS -> todoItems(ids)
            service.domain == Domain.MUSIC_ASSISTANT -> musicAssistant(service.service, data)
            else -> {
                for (id in ids) {
                    _states.value[id]?.let { current ->
                        serviceHandlers.apply(entityId = id, service = service, data = data, current = current)
                    }
                }

                null
            }
        }
    }

    /** The call as Home Assistant's `service_data` would show it, with its target as `entity_id`. */
    private fun recorded(target: List<EntityId>, data: ServiceData): JsonObject = when {
        target.isEmpty() -> data.fields
        else -> JsonObject(
            data.fields + (
                ServiceData.ENTITY_ID to (
                    target.singleOrNull()?.let { JsonPrimitive(it.value) }
                        ?: JsonArray(target.map { JsonPrimitive(it.value) })
                    )
                ),
        )
    }

    override suspend fun <R> send(request: HaRequest<R>): R = commandHandlers.handle(request)

    private fun musicAssistant(
        service: String,
        data: ServiceData,
    ): JsonElement? = when (service) {
        "get_library" -> demoMusicLibrary()
        "search" -> demoMusicSearch((data["name"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        "get_queue" -> demoMusicQueue()
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
                                put("name", name.ifBlank { "Night Drive" })
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

    override suspend fun fetch(path: String): ByteArray {
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

        /** `vacuum.robot`'s area → segment mapping, used once it advertises `CLEAN_AREA`. Garden maps to none. */
        val VACUUM_AREA_SEGMENTS = listOf(
            "living_room" to "16",
            "kitchen" to "17",
            "hallway" to "18",
            "bedroom" to "19",
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

        /** An Alarmo area panel: `arm_mode` beside the state, and `open_sensors` null unless one is open. */
        private fun alarmoZone(
            entityId: String,
            name: String,
            openSensor: String? = null,
        ): EntityState {
            val base = attrs(
                "friendly_name" to name,
                "code_format" to "number",
                "supported_features" to 7,
                "arm_mode" to "disarmed",
                "delay" to 0,
            )

            val sensors = openSensor?.let { JsonObject(mapOf(it to JsonPrimitive("on"))) } ?: JsonNull

            return EntityState(
                entityId = entityId,
                state = "disarmed",
                attributes = JsonObject(base + ("open_sensors" to sensors))
            )
        }

        @Suppress("LongMethod")
        fun initialStates(): List<EntityState> = listOf(
            EntityState(
                entityId = "light.living_room",
                state = "on",
                attributes = attrs(
                    "friendly_name" to "Ceiling light",
                    "brightness" to 150,
                    "supported_color_modes" to listOf(LightColorMode.HS, LightColorMode.COLOR_TEMP),
                    "color_mode" to LightColorMode.HS,
                    "effect_list" to listOf("Rainbow", "Candle", "Pulse"),
                ),
            ),
            EntityState(
                entityId = "light.table_lamp",
                state = "off",
                attributes = attrs(
                    "friendly_name" to "Table lamp",
                    "supported_color_modes" to listOf(LightColorMode.BRIGHTNESS),
                ),
            ),
            EntityState(
                entityId = "light.kitchen",
                state = "on",
                attributes = attrs(
                    "friendly_name" to "Kitchen lights",
                    "brightness" to 255,
                    "supported_color_modes" to listOf(LightColorMode.ONOFF),
                ),
            ),
            EntityState(
                entityId = "light.bedroom",
                state = "off",
                attributes = attrs(
                    "friendly_name" to "Bedside lamp",
                    "supported_color_modes" to listOf(LightColorMode.BRIGHTNESS),
                ),
            ),
            EntityState(
                entityId = "light.garden",
                state = "off",
                attributes = attrs(
                    "friendly_name" to "Garden lights",
                    "supported_color_modes" to listOf(LightColorMode.ONOFF),
                ),
            ),
            EntityState(
                entityId = "switch.coffee_machine",
                state = "off",
                attributes = attrs("friendly_name" to "Coffee machine", "icon" to "mdi:coffee"),
            ),
            EntityState(
                entityId = "switch.hallway_light",
                state = "on",
                attributes = attrs("friendly_name" to "Hallway light", "icon" to "mdi:ceiling-light"),
            ),
            EntityState(entityId = "fan.bedroom", state = "on", attributes = attrs(
                "friendly_name" to "Bedroom fan",
                "percentage" to 50,
                "oscillating" to true,
                "direction" to "forward",
                "preset_modes" to listOf("auto", "breeze", "sleep"),
                "preset_mode" to "breeze",
                "supported_features" to 15,
            )),
            EntityState(entityId = "humidifier.bedroom", state = "on", attributes = attrs(
                "friendly_name" to "Bedroom humidifier",
                "humidity" to 45,
                "current_humidity" to 38,
                "min_humidity" to 30,
                "max_humidity" to 80,
                "available_modes" to listOf("normal", "eco", "away"),
                "mode" to "normal",
            )),
            EntityState(
                entityId = "sensor.kitchen_temperature",
                state = "21.4",
                attributes = attrs(
                    "friendly_name" to "Kitchen temperature",
                    "unit_of_measurement" to "°C",
                    "device_class" to "temperature",
                    "state_class" to "measurement",
                ),
            ),
            EntityState(
                entityId = "sensor.outdoor_temperature",
                state = "12.8",
                attributes = attrs(
                    "friendly_name" to "Outdoor",
                    "unit_of_measurement" to "°C",
                    "device_class" to "temperature",
                    "state_class" to "measurement",
                ),
            ),
            EntityState(
                entityId = "sensor.power",
                state = "432",
                attributes = attrs(
                    "friendly_name" to "Power usage",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                    "state_class" to "measurement",
                ),
            ),
            EntityState(
                entityId = "sensor.solar_power",
                state = "5200",
                attributes = attrs(
                    "friendly_name" to "Solar",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                entityId = "sensor.grid_power",
                state = "-1200",
                attributes = attrs(
                    "friendly_name" to "Grid",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                entityId = "sensor.battery_power",
                state = "1500",
                attributes = attrs(
                    "friendly_name" to "Battery",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                entityId = "sensor.heat_pump_power",
                state = "900",
                attributes = attrs(
                    "friendly_name" to "Heat pump",
                    "unit_of_measurement" to "W",
                    "device_class" to "power",
                ),
            ),
            EntityState(
                entityId = "sensor.heat_pump_energy",
                state = "6.2",
                attributes = attrs(
                    "friendly_name" to "Heat pump energy",
                    "unit_of_measurement" to "kWh",
                    "device_class" to "energy",
                ),
            ),
            EntityState(
                entityId = "binary_sensor.front_door",
                state = "off",
                attributes = attrs("friendly_name" to "Front door", "device_class" to "door"),
            ),
            EntityState(
                entityId = "binary_sensor.garage_side_door",
                state = "on",
                attributes = attrs("friendly_name" to "Garage side door", "device_class" to "door"),
            ),
            EntityState(
                entityId = "binary_sensor.living_room_window",
                state = "off",
                attributes = attrs("friendly_name" to "Living room window", "device_class" to "window"),
            ),
            EntityState(
                entityId = "scene.movie_night",
                state = "2026-01-01T00:00:00+00:00",
                attributes = attrs("friendly_name" to "Movie night", "icon" to "mdi:movie"),
            ),
            EntityState(
                entityId = "script.good_night",
                state = "off",
                attributes = attrs("friendly_name" to "Good night", "icon" to "mdi:weather-night"),
            ),
            EntityState(
                entityId = "cover.living_room_curtains",
                state = "open",
                attributes = attrs(
                    "friendly_name" to "Curtains",
                    "current_position" to 70,
                    "current_tilt_position" to 50,
                    "supported_features" to 15,
                    "device_class" to "curtain",
                ),
            ),
            EntityState(
                entityId = "lock.front_door",
                state = "locked",
                attributes = attrs("friendly_name" to "Front door", "supported_features" to 1),
            ),
            EntityState(entityId = "climate.living_room", state = "heat_cool", attributes = attrs(
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
            EntityState(entityId = "climate.bedroom", state = "heat_cool", attributes = attrs(
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
                entityId = "media_player.living_room",
                state = "playing",
                attributes = attrs(
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
            EntityState(entityId = "weather.home", state = "partlycloudy", attributes = attrs("friendly_name" to "Home", "temperature" to 14.2, "temperature_unit" to "°C", "humidity" to 71, "wind_speed" to 12, "wind_speed_unit" to "km/h",
                "pressure" to 1013, "pressure_unit" to "hPa", "supported_features" to 3,)),
            EntityState(
                entityId = "camera.front_door",
                state = "idle",
                attributes = attrs(
                    "friendly_name" to "Front door camera",
                    "entity_picture" to "/api/camera_proxy/camera.front_door",
                ),
            ),
            EntityState(
                entityId = "camera.driveway",
                state = "idle",
                attributes = attrs(
                    "friendly_name" to "Driveway camera",
                    "entity_picture" to "/api/camera_proxy/camera.driveway",
                ),
            ),
            EntityState(
                entityId = "alarm_control_panel.house",
                state = "disarmed",
                attributes = attrs(
                    "friendly_name" to "House alarm",
                    "code_format" to "number",
                    "supported_features" to 63,
                ),
            ),
            alarmoZone("alarm_control_panel.basement", "Basement alarm"),
            alarmoZone("alarm_control_panel.upstairs", "Upstairs alarm"),
            alarmoZone(
                entityId = "alarm_control_panel.garage",
                name = "Garage alarm",
                openSensor = "binary_sensor.garage_side_door"
            ),
            EntityState(
                entityId = "input_select.washing_program",
                state = "Cotton eco",
                attributes = attrs(
                    "friendly_name" to "Washing machine",
                    "options" to listOf("Cotton", "Cotton eco", "Synthetics", "Wool", "Quick 15'"),
                ),
            ),
            EntityState(
                entityId = "input_boolean.guest_mode",
                state = "off",
                attributes = attrs("friendly_name" to "Guest mode")
            ),
            EntityState(
                entityId = "vacuum.robot",
                state = "docked",
                attributes = attrs(
                    "friendly_name" to "Robot",
                    "battery_level" to 100,
                    "fan_speed" to "Standard",
                    "fan_speed_list" to listOf("Off", "Quiet", "Standard", "Turbo", "Max", "Max plus"),
                    "room_list" to listOf(
                        "Living Room",
                        "Kitchen",
                        "Hallway",
                        "Bedroom",
                        "Office",
                        "Bath",
                    ),
                    "room_ids" to listOf("16", "17", "18", "19", "20", "21"),
                    "supported_features" to 13180,
                ),
            ),
            EntityState(
                entityId = "select.robot_cleaning_mode",
                state = "vacuum",
                attributes = attrs(
                    "friendly_name" to "Robot Cleaning mode",
                    "options" to listOf("vac_and_mop", "vacuum", "mop", "customized"),
                ),
            ),
            EntityState(
                entityId = "person.sam",
                state = "home",
                attributes = attrs("friendly_name" to "Sam", "latitude" to 52.37, "longitude" to 4.89),
            ),
            EntityState(
                entityId = "person.alex",
                state = "not_home",
                attributes = attrs("friendly_name" to "Alex", "latitude" to 52.35, "longitude" to 4.92),
            ),
            EntityState(
                entityId = "button.doorbell_test",
                state = "unknown",
                attributes = attrs("friendly_name" to "Test doorbell"),
            ),
            EntityState(entityId = "calendar.family", state = "off", attributes = attrs(
                "friendly_name" to "Family",
                "message" to "Dentist appointment",
                "location" to "Main St",
                "all_day" to false,
                "start_time" to "${java.time.LocalDate.now().plusDays(1)} 19:30:00",
                "end_time" to "${java.time.LocalDate.now().plusDays(1)} 20:30:00",
                "supported_features" to 3,
            )),
            EntityState(entityId = "todo.shopping", state = "2", attributes = attrs("friendly_name" to "Shopping")),
            EntityState(
                entityId = "timer.kitchen",
                state = "idle",
                attributes = attrs(
                    "friendly_name" to "Kitchen timer",
                    "duration" to "00:10:00",
                    "remaining" to "0:10:00",
                ),
            ),
            EntityState(
                entityId = "counter.guests",
                state = "3",
                attributes = attrs("friendly_name" to "Guests", "initial" to 0, "step" to 1),
            ),
            EntityState(entityId = "water_heater.tank", state = "eco", attributes = attrs(
                "friendly_name" to "Hot water",
                "temperature" to 50,
                "min_temp" to 40,
                "max_temp" to 60,
                "operation_list" to listOf("off", "eco", "performance"),
                "operation_mode" to "eco",
            )),
            EntityState(
                entityId = "valve.garden",
                state = "open",
                attributes = attrs("friendly_name" to "Garden valve", "current_position" to 100),
            ),
            EntityState(
                entityId = "siren.alarm",
                state = "off",
                attributes = attrs("friendly_name" to "Siren", "available_tones" to listOf("fire", "burglar")),
            ),
            EntityState(
                entityId = "lawn_mower.yard",
                state = "docked",
                attributes = attrs("friendly_name" to "Mower", "battery_level" to 80),
            ),
            EntityState(
                entityId = "input_text.note",
                state = "Door code for Sam",
                attributes = attrs("friendly_name" to "Note", "max" to 100),
            ),
            EntityState(
                entityId = "input_datetime.quiet_hours",
                state = "22:00:00",
                attributes = attrs("friendly_name" to "Quiet hours", "has_date" to false, "has_time" to true),
            ),
            EntityState(
                entityId = "sun.sun",
                state = "above_horizon",
                attributes = attrs(
                    "friendly_name" to "Sun",
                    "next_rising" to "2026-10-08T07:45:00+00:00",
                    "next_setting" to "2026-10-07T18:40:00+00:00",
                ),
            ),
        )

        /** A soft gradient, standing in for a camera snapshot or an album cover. */
        fun picture(
            seed: Int,
            w: Int = 320,
            h: Int = 240,
        ): ByteArray {
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
