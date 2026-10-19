package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Applies Home Assistant service calls to demo entity state — one handler per service name.
 */
internal class DemoServiceHandlers(
    private val set: (entityId: String, state: String, attrs: Map<String, JsonElement>) -> Unit,
    private val completedTodos: MutableSet<String>,
) {
    fun apply(entityId: String, domain: String, service: String, data: JsonObject, current: EntityState) {
        handlers[service]?.invoke(ServiceCall(entityId, domain, service, data, current))
    }

    private fun ServiceCall.setStateFrom(key: String) {
        str(key)?.let { set(entityId, it, emptyMap()) }
    }

    private fun ServiceCall.setAttr(key: String) {
        num(key)?.let { value ->
            set(entityId, current.state, mapOf(key to JsonPrimitive(value)))
        } ?: str(key)?.let { value ->
            set(entityId, current.state, mapOf(key to JsonPrimitive(value)))
        }
    }

    private fun ServiceCall.setPosition() {
        num("position")?.let { position ->
            set(
                entityId,
                if (position > 0) "open" else "closed",
                mapOf("current_position" to JsonPrimitive(position.toInt())),
            )
        }
    }

    private fun ServiceCall.toggle() {
        val isOn = current.state in ON_STATES
        val next = when (current.domain) {
            "sun" -> if (isOn) "below_horizon" else "above_horizon"
            "person" -> if (isOn) "not_home" else "home"
            else -> if (isOn) off(current.domain) else on(current.domain)
        }
        set(entityId, next, emptyMap())
    }

    private fun ServiceCall.turnOn() {
        if (current.domain == "scene" || current.domain == "script") return
        val attrs = mutableMapOf<String, JsonElement>()
        num("brightness_pct")?.let {
            attrs["brightness"] = JsonPrimitive((it * BRIGHTNESS_PCT_SCALE).toInt())
        }
        num("percentage")?.let { attrs["percentage"] = JsonPrimitive(it.toInt()) }
        (data["hs_color"] as? JsonArray)?.let { attrs["hs_color"] = it }
        num("color_temp_kelvin")?.let { attrs["color_temp_kelvin"] = JsonPrimitive(it.toInt()) }
        str("effect")?.let { attrs["effect"] = JsonPrimitive(it) }
        set(entityId, "on", attrs)
    }

    private fun ServiceCall.setTemperature() {
        val attrs = mutableMapOf<String, JsonElement>()
        num("temperature")?.let { attrs["temperature"] = JsonPrimitive(it) }
        num("target_temp_high")?.let { attrs["target_temp_high"] = JsonPrimitive(it) }
        num("target_temp_low")?.let { attrs["target_temp_low"] = JsonPrimitive(it) }
        if (attrs.isNotEmpty()) set(entityId, current.state, attrs)
    }

    private fun ServiceCall.startActivity() {
        set(
            entityId,
            when (domain) {
                "lawn_mower" -> "mowing"
                "timer" -> "active"
                else -> "cleaning"
            },
            emptyMap(),
        )
    }

    private fun ServiceCall.stopActivity() {
        set(
            entityId,
            when (domain) {
                "vacuum", "lawn_mower" -> "idle"
                "timer" -> if (service == "pause") "paused" else "idle"
                else -> current.state
            },
            emptyMap(),
        )
    }

    private fun ServiceCall.updateTodoItem() {
        val uid = str("item") ?: return
        if (str("status") != "completed") return
        completedTodos += uid
        val open = (current.numericState()?.toInt() ?: DEFAULT_TODO_COUNT) - 1
        set(entityId, open.coerceAtLeast(0).toString(), emptyMap())
    }

    private val handlers: Map<String, (ServiceCall) -> Unit> = mapOf(
        "toggle" to { it.toggle() },
        "turn_on" to { it.turnOn() },
        "turn_off" to { set(it.entityId, "off", emptyMap()) },
        "open_cover" to {
            set(it.entityId, "open", mapOf("current_position" to JsonPrimitive(100)))
        },
        "close_cover" to {
            set(it.entityId, "closed", mapOf("current_position" to JsonPrimitive(0)))
        },
        "set_cover_position" to { it.setPosition() },
        "lock" to { set(it.entityId, "locked", emptyMap()) },
        "unlock" to { set(it.entityId, "unlocked", emptyMap()) },
        "set_temperature" to { it.setTemperature() },
        "set_hvac_mode" to { it.setStateFrom("hvac_mode") },
        "set_swing_mode" to { it.setAttr("swing_mode") },
        "set_preset_mode" to { it.setAttr("preset_mode") },
        "set_fan_mode" to { it.setAttr("fan_mode") },
        "set_humidity" to {
            it.num("humidity")?.let { humidity ->
                set(it.entityId, "on", mapOf("humidity" to JsonPrimitive(humidity.toInt())))
            }
        },
        "set_mode" to { it.setAttr("mode") },
        "oscillate" to {
            it.bool("oscillating")?.let { oscillating ->
                set(it.entityId, it.current.state, mapOf("oscillating" to JsonPrimitive(oscillating)))
            }
        },
        "set_direction" to { it.setAttr("direction") },
        "media_play_pause" to {
            set(
                it.entityId,
                if (it.current.state == "playing") "paused" else "playing",
                emptyMap(),
            )
        },
        "media_play" to { set(it.entityId, "playing", emptyMap()) },
        "media_pause" to { set(it.entityId, "paused", emptyMap()) },
        "volume_set" to { it.setAttr("volume_level") },
        "media_seek" to {
            it.num("seek_position")?.let { position ->
                set(it.entityId, it.current.state, mapOf("media_position" to JsonPrimitive(position)))
            }
        },
        "select_option" to { it.setStateFrom("option") },
        "start" to { it.startActivity() },
        "start_mowing" to { it.startActivity() },
        "return_to_base" to { set(it.entityId, "returning", emptyMap()) },
        "dock" to { set(it.entityId, "returning", emptyMap()) },
        "stop" to { it.stopActivity() },
        "pause" to { it.stopActivity() },
        "cancel" to { it.stopActivity() },
        "finish" to { set(it.entityId, "idle", emptyMap()) },
        "alarm_disarm" to { set(it.entityId, "disarmed", emptyMap()) },
        "alarm_arm_away" to { set(it.entityId, "armed_away", emptyMap()) },
        "alarm_arm_home" to { set(it.entityId, "armed_home", emptyMap()) },
        "alarm_arm_night" to { set(it.entityId, "armed_night", emptyMap()) },
        "set_percentage" to {
            it.num("percentage")?.let { percentage ->
                set(
                    it.entityId,
                    if (percentage > 0) "on" else "off",
                    mapOf("percentage" to JsonPrimitive(percentage.toInt())),
                )
            }
        },
        "press" to { set(it.entityId, java.time.Instant.now().toString(), emptyMap()) },
        "increment" to {
            set(
                it.entityId,
                ((it.current.numericState() ?: 0.0) + 1).toInt().toString(),
                emptyMap(),
            )
        },
        "decrement" to {
            set(
                it.entityId,
                ((it.current.numericState() ?: 0.0) - 1).toInt().toString(),
                emptyMap(),
            )
        },
        "reset" to { set(it.entityId, "0", emptyMap()) },
        "open_valve" to {
            set(it.entityId, "open", mapOf("current_position" to JsonPrimitive(100)))
        },
        "close_valve" to {
            set(it.entityId, "closed", mapOf("current_position" to JsonPrimitive(0)))
        },
        "set_valve_position" to { it.setPosition() },
        "set_operation_mode" to { it.setStateFrom("operation_mode") },
        "set_value" to { it.setStateFrom("value") },
        "set_datetime" to { it.setStateFrom("datetime") },
        "update_item" to { it.updateTodoItem() },
    )

    private companion object {
        val ON_STATES = setOf(
            "on",
            "open",
            "above_horizon",
            "home",
            "locked",
            "playing",
            "active",
        )
        const val BRIGHTNESS_PCT_SCALE = 2.55
        const val DEFAULT_TODO_COUNT = 2

        fun on(domain: String) = if (domain == "cover") "open" else "on"
        fun off(domain: String) = if (domain == "cover") "closed" else "off"
    }
}
