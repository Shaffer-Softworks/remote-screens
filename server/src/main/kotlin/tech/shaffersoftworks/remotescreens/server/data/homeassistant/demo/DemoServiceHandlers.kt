package tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceCall
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceData
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceRef
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.TimerState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.VacuumState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.SwitchState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.CoverState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.PresenceState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaPlayerState

/**
 * Applies Home Assistant service calls to demo entity state — one handler per service name.
 */
internal class DemoServiceHandlers(
    private val set: (entityId: String, state: String, attrs: Map<String, JsonElement>) -> Unit,
    private val completedTodos: MutableSet<String>,
) {
    /** Handlers are keyed by the service's name, which means the same in every domain that has it. */
    fun apply(
        entityId: String,
        service: ServiceRef,
        data: ServiceData,
        current: EntityState,
    ) {
        handlers[service.service]?.invoke(
            ServiceCall(
                entityId = entityId,
                service = service,
                data = data,
                current = current,
            ),
        )
    }

    private fun ServiceCall.setStateFrom(key: String) {
        str(key)?.let {
            set(entityId, it, emptyMap())
        }
    }

    private fun ServiceCall.setAttr(key: String) {
        num(key)?.let { value ->
            set(entityId, current.state, mapOf(key to JsonPrimitive(value)))
        } ?: str(key)?.let { value ->
            set(entityId, current.state, mapOf(key to JsonPrimitive(value)))
        }
    }

    /** Alarmo panels report the mode in `arm_mode` as well as the state; keep both in step. */
    private fun ServiceCall.armAlarm(state: String) {
        val attrs = if ("arm_mode" in current.attributes) {
            mapOf("arm_mode" to JsonPrimitive(state))
        } else {
            emptyMap()
        }

        set(entityId, state, attrs)
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
            Domain.SUN -> if (isOn) "below_horizon" else "above_horizon"
            Domain.PERSON -> if (isOn) PresenceState.NOT_HOME.wire else PresenceState.HOME.wire
            else -> if (isOn) off(current.domain) else on(current.domain)
        }
        set(entityId, next, emptyMap())
    }

    private fun ServiceCall.turnOn() {
        if (current.domain == Domain.SCENE || current.domain == Domain.SCRIPT) return
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
                Domain.LAWN_MOWER -> "mowing"
                Domain.TIMER -> TimerState.ACTIVE.wire
                else -> VacuumState.CLEANING.wire
            },
            emptyMap(),
        )
    }

    private fun ServiceCall.stopActivity() {
        set(
            entityId,
            when (domain) {
                Domain.VACUUM, Domain.LAWN_MOWER -> VacuumState.IDLE.wire
                Domain.TIMER -> if (service.service == "pause") TimerState.PAUSED.wire else TimerState.IDLE.wire
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
                if (it.current.mediaPlayerState == MediaPlayerState.PLAYING) {
                    MediaPlayerState.PAUSED.wire
                } else {
                    MediaPlayerState.PLAYING.wire
                },
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
        "alarm_disarm" to { it.armAlarm("disarmed") },
        "alarm_arm_away" to { it.armAlarm("armed_away") },
        "alarm_arm_home" to { it.armAlarm("armed_home") },
        "alarm_arm_night" to { it.armAlarm("armed_night") },
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
        "set_fan_speed" to { it.setAttr("fan_speed") },
        "send_command" to { call ->
            if (call.str("command") == "app_segment_clean") {
                set(call.entityId, "cleaning", emptyMap())
            }
        },
        "clean_area" to { call -> set(call.entityId, "cleaning", emptyMap()) },
        "locate" to { /* demo: no state change */ },
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

        fun on(domain: Domain): String = if (domain == Domain.COVER) CoverState.OPEN.wire else SwitchState.ON.wire

        fun off(domain: Domain): String = if (domain == Domain.COVER) CoverState.CLOSED.wire else SwitchState.OFF.wire
    }
}
