// Demo fixture data: the numbers are made-up entity values.
@file:Suppress("MagicNumber", "TooManyFunctions")

package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.AreaRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.BrowseMedia
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.CalendarEventDelete
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.DeviceRegistryList
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergyGetPrefs
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ListAssistPipelines
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergySource
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryGet
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryListForDisplay
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.FireEvent
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.FossilEnergyConsumption
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.GetTranslations
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HA_JSON
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HaRequest
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HistoryDuringPeriod
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantException
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.StatisticsDuringPeriod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.sin

/**
 * Handles Home Assistant WebSocket commands for demo mode — one handler per request class. Each is recorded in
 * [commands] as its type and payload.
 */
internal class DemoCommandHandlers(
    private val fire: (type: String, data: JsonObject) -> Unit,
    private val commands: MutableList<Pair<String, JsonObject>>,
) {
    /** Answers [request] the way Home Assistant would, read back through the request's own serializer. */
    fun <R> handle(request: HaRequest<R>): R {
        commands += request.type to request.payload()

        val result = registryAnswer(request) ?: when (request) {
            is FireEvent -> fireEvent(request)
            is HistoryDuringPeriod -> historyDuringPeriod(request.entityIds)
            EnergyGetPrefs -> energyGetPrefs()
            is StatisticsDuringPeriod -> energyStatistics()
            is FossilEnergyConsumption -> fossilEnergyConsumption()
            is BrowseMedia -> mediaPlayerBrowseMedia()
            is GetTranslations -> buildJsonObject { put("resources", buildJsonObject { }) }
            is CalendarEventDelete -> JsonNull
            ListAssistPipelines -> assistPipelines()
            else -> throw HomeAssistantException(code = "unknown_command", message = "Demo has no ${request.type}")
        }

        return HA_JSON.decodeFromJsonElement(request.resultSerializer, result)
    }

    private fun registryAnswer(request: HaRequest<*>): JsonElement? = when (request) {
        AreaRegistryList -> areaRegistryList()
        is EntityRegistryGet -> entityRegistryGet(request.entityId)
        DeviceRegistryList -> deviceRegistryList()
        EntityRegistryListForDisplay -> entityRegistryListForDisplay()
        else -> null
    }

    private fun assistPipelines(): JsonElement = buildJsonObject {
        put("preferred_pipeline", "preferred")

        put("pipelines", buildJsonArray {
            add(buildJsonObject {
                put("id", "preferred")
                put("name", "Home")
            })
        })
    }

    private fun fireEvent(request: FireEvent): JsonElement {
        fire(request.eventType, request.eventData ?: JsonObject(emptyMap()))

        return JsonNull
    }

    private fun areaRegistryList(): JsonElement = buildJsonArray {
        for ((id, name) in DemoHome.AREAS) {
            add(buildJsonObject {
                put("area_id", id)
                put("name", name)
            })
        }
    }

    private fun entityRegistryGet(entityId: String): JsonElement {
        return when (entityId) {
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
            else -> buildJsonObject { put("entity_id", entityId) }
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

    private fun historyDuringPeriod(ids: List<String>): JsonElement {
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
                        put("type", EnergySource.SOLAR)
                        put("stat_rate", "sensor.solar_power")
                    },
                )

                add(
                    buildJsonObject {
                        put("type", EnergySource.GRID)
                        put("stat_rate", "sensor.grid_power")
                        put("stat_energy_from", "sensor.grid_energy")
                        put("number_energy_price", 0.22)
                    },
                )

                add(
                    buildJsonObject {
                        put("type", EnergySource.BATTERY)
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
}
