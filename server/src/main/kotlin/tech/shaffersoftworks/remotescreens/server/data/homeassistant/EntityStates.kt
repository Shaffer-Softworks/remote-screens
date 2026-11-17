package tech.shaffersoftworks.remotescreens.server.data.homeassistant

/** A state Home Assistant reports as text; [wire] is that text. Each enum ends in an entry for any other text. */
interface StateValue {
    val wire: String
}

/** The entry of [T] whose [StateValue.wire] is [wire], or [fallback]. */
inline fun <reified T> stateOf(wire: String?, fallback: T): T where T : Enum<T>, T : StateValue =
    enumValues<T>().firstOrNull { it.wire == wire } ?: fallback

/** Whether Home Assistant can reach the entity at all. */
enum class Availability(override val wire: String) : StateValue {
    AVAILABLE(""),
    UNAVAILABLE("unavailable"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun of(state: String): Availability = stateOf(state, AVAILABLE)
    }
}

/** On or off, for lights, switches, fans, binary sensors and the like. */
enum class SwitchState(override val wire: String) : StateValue {
    ON("on"),
    OFF("off"),
    OTHER(""),
    ;

    companion object {
        fun of(state: String): SwitchState = stateOf(state, OTHER)
    }
}

/** A cover or a valve. */
enum class CoverState(override val wire: String) : StateValue {
    OPEN("open"),
    OPENING("opening"),
    CLOSED("closed"),
    CLOSING("closing"),
    OTHER(""),
    ;

    val isOpenOrOpening: Boolean get() = this == OPEN || this == OPENING

    companion object {
        fun of(state: String): CoverState = stateOf(state, OTHER)
    }
}

enum class LockState(override val wire: String) : StateValue {
    LOCKED("locked"),
    UNLOCKED("unlocked"),
    LOCKING("locking"),
    UNLOCKING("unlocking"),
    JAMMED("jammed"),
    OPEN("open"),
    OPENING("opening"),
    OTHER(""),
    ;

    companion object {
        fun of(state: String): LockState = stateOf(state, OTHER)
    }
}

/** An alarm panel, including Alarmo's `arm_mode`. */
enum class AlarmState(override val wire: String) : StateValue {
    DISARMED("disarmed"),
    ARMED_HOME("armed_home"),
    ARMED_AWAY("armed_away"),
    ARMED_NIGHT("armed_night"),
    ARMED_VACATION("armed_vacation"),
    ARMED_CUSTOM_BYPASS("armed_custom_bypass"),
    ARMING("arming"),
    DISARMING("disarming"),
    PENDING("pending"),
    TRIGGERED("triggered"),
    OTHER(""),
    ;

    /** Counting down or sounding: the states a screen wakes and alerts for. */
    val isAlerting: Boolean get() = this == ARMING || this == PENDING || this == TRIGGERED

    companion object {
        fun of(state: String?): AlarmState = stateOf(state, OTHER)
    }
}

enum class MediaPlayerState(override val wire: String) : StateValue {
    OFF("off"),
    ON("on"),
    IDLE("idle"),
    PLAYING("playing"),
    PAUSED("paused"),
    STANDBY("standby"),
    BUFFERING("buffering"),
    OTHER(""),
    ;

    companion object {
        fun of(state: String): MediaPlayerState = stateOf(state, OTHER)
    }
}

/** A climate entity's state, and the entries of its `hvac_modes`. */
enum class HvacMode(override val wire: String) : StateValue {
    OFF("off"),
    HEAT("heat"),
    COOL("cool"),
    HEAT_COOL("heat_cool"),
    AUTO("auto"),
    DRY("dry"),
    FAN_ONLY("fan_only"),
    OTHER(""),
    ;

    companion object {
        fun of(state: String): HvacMode = stateOf(state, OTHER)
    }
}

enum class VacuumState(override val wire: String) : StateValue {
    CLEANING("cleaning"),
    DOCKED("docked"),
    IDLE("idle"),
    PAUSED("paused"),
    RETURNING("returning"),
    ERROR("error"),
    OTHER(""),
    ;

    companion object {
        fun of(state: String): VacuumState = stateOf(state, OTHER)
    }
}

enum class TimerState(override val wire: String) : StateValue {
    ACTIVE("active"),
    PAUSED("paused"),
    IDLE("idle"),
    OTHER(""),
    ;

    companion object {
        fun of(state: String): TimerState = stateOf(state, OTHER)
    }
}

/** A person or device tracker: home, away, or the name of another zone ([ZONE]). */
enum class PresenceState(override val wire: String) : StateValue {
    HOME("home"),
    NOT_HOME("not_home"),
    ZONE(""),
    ;

    companion object {
        fun of(state: String): PresenceState = stateOf(state, ZONE)
    }
}
