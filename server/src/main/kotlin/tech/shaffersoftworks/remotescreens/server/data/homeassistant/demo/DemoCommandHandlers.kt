// Demo fixture data: the numbers are made-up entity values.
@file:Suppress("MagicNumber", "TooManyFunctions")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.sin

/**
 * Handles Home Assistant WebSocket commands for demo mode — one handler per command type.
 */
internal class DemoCommandHandlers(
    private val fire: (type: String, data: JsonObject) -> Unit,
    private val commands: MutableList<Pair<String, JsonObject>>,
) {
    fun handle(
        type: String,
        payload: JsonObject,
    ): JsonElement? = handlers[type]?.invoke(payload)

    private fun fireEvent(payload: JsonObject): JsonElement? {
        val eventType = payload["event_type"]?.jsonPrimitive?.contentOrNull ?: return null
        fire(eventType, (payload["event_data"] as? JsonObject) ?: JsonObject(emptyMap()))
        return JsonObject(emptyMap())
    }

    private fun areaRegistryList(): JsonElement = buildJsonArray {
        for ((id, name) in DemoHome.AREAS) {
            add(buildJsonObject {
                put("area_id", id)
                put("name", name)
            })
        }
    }

    private fun entityRegistryGet(payload: JsonObject): JsonElement? {
        return when (val entityId = payload["entity_id"]?.jsonPrimitive?.contentOrNull) {
            "media_player.living_room" -> buildJsonObject {
                put("entity_id", entityId)
                put("config_entry_id", "demo_music_assistant")
            }
            "vacuum.robot" -> buildJsonObject {
                put("entity_id", entityId)
                put(
                    "options",
                    buildJsonObject {
                        put(
                            "vacuum",
                            buildJsonObject {
                                put(
                                    "area_mapping",
                                    buildJsonObject {
                                        for ((area, segment) in DemoHome.VACUUM_AREA_SEGMENTS) {
                                            put(area, buildJsonArray { add(JsonPrimitive(segment)) })
                                        }
                                        put("garden", buildJsonArray { })
                                    },
                                )
                            },
                        )
                    },
                )
            }
            else -> null
        }
    }

    private fun deviceRegistryList(): JsonElement = buildJsonArray {
        for ((id, area) in DemoHome.DEVICE_AREAS) {
            add(buildJsonObject {
                put("id", id)
                put("area_id", area)
            })
        }
    }

    private fun entityRegistryListForDisplay(): JsonElement = buildJsonObject {
        put(
            "entities",
            buildJsonArray {
                for ((entity, area) in DemoHome.ENTITY_AREAS) {
                    add(buildJsonObject {
                        put("ei", entity)
                        put("ai", area)
                    })
                }
                for ((entity, device) in DemoHome.ENTITY_DEVICES) {
                    add(buildJsonObject {
                        put("ei", entity)
                        put("di", device)
                    })
                }
            },
        )
    }

    private fun historyDuringPeriod(payload: JsonObject): JsonElement {
        val ids = payload["entity_ids"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        return buildJsonObject {
            for (id in ids) {
                put(
                    id,
                    buildJsonArray {
                        val now = System.currentTimeMillis() / 1000.0
                        for (i in 0 until 48) {
                            add(
                                buildJsonObject {
                                    put(
                                        "s",
                                        "%.1f".format(
                                            java.util.Locale.ROOT,
                                            19.5 + 2.5 * sin(i / 7.0),
                                        ),
                                    )
                                    put("lu", now - (48 - i) * 1800)
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun energyGetPrefs(): JsonElement = buildJsonObject {
        put(
            "energy_sources",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "solar")
                        put("stat_rate", "sensor.solar_power")
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "grid")
                        put("stat_rate", "sensor.grid_power")
                        put("stat_energy_from", "sensor.grid_energy")
                        put("number_energy_price", 0.22)
                    },
                )
                add(
                    buildJsonObject {
                        put("type", "battery")
                        put("stat_rate", "sensor.battery_power")
                    },
                )
            },
        )
        put(
            "device_consumption",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("stat_consumption", "sensor.heat_pump_energy")
                        put("stat_rate", "sensor.heat_pump_power")
                        put("name", "Heat pump")
                    },
                )
            },
        )
    }

    private fun energyStatistics(): JsonObject {
        val now = System.currentTimeMillis()
        val hourMs = 3_600_000L
        fun series(seed: Double) = buildJsonArray {
            for (index in 0 until 24) {
                add(
                    buildJsonObject {
                        put("start", now - (24 - index) * hourMs)
                        put("change", seed + 0.15 * sin(index / 3.0))
                    },
                )
            }
        }
        return buildJsonObject {
            put("sensor.grid_energy", series(1.2))
            put("sensor.heat_pump_energy", series(0.4))
        }
    }

    private fun fossilEnergyConsumption(): JsonElement = buildJsonObject {
        put("sensor.grid_energy", 0.58)
    }

    private fun mediaPlayerBrowseMedia(): JsonElement = buildJsonObject {
        put("title", "Library")
        put("media_content_id", "")
        put(
            "children",
            buildJsonArray {
                for ((title, id) in listOf(
                    "Sunday Morning" to "playlist:1",
                    "Focus" to "playlist:2",
                    "Road Trip" to "album:3",
                )) {
                    add(
                        buildJsonObject {
                            put("title", title)
                            put("media_content_id", id)
                            put("media_content_type", "playlist")
                            put("can_play", true)
                            put("can_expand", false)
                        },
                    )
                }
            },
        )
    }

    private val handlers: Map<String, (JsonObject) -> JsonElement?> = mapOf(
        "fire_event" to { fireEvent(it) },
        "config/area_registry/list" to { areaRegistryList() },
        "config/entity_registry/get" to { entityRegistryGet(it) },
        "config/device_registry/list" to { deviceRegistryList() },
        "config/entity_registry/list_for_display" to { entityRegistryListForDisplay() },
        "history/history_during_period" to { historyDuringPeriod(it) },
        "energy/get_prefs" to { energyGetPrefs() },
        "recorder/statistics_during_period" to { energyStatistics() },
        "energy/fossil_energy_consumption" to { fossilEnergyConsumption() },
        "media_player/browse_media" to { mediaPlayerBrowseMedia() },
        "frontend/get_translations" to { buildJsonObject { put("resources", buildJsonObject { }) } },
        "calendar/event/delete" to { payload ->
            commands += "calendar/event/delete" to payload
            JsonObject(emptyMap())
        },
    )
}
