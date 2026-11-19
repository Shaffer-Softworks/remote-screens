package tech.shaffersoftworks.remotescreens.server.data.homeassistant

/** Alarm-panel service verbs. The card, the page and the action handler share these. */
object AlarmVerb {
    const val DISARM = "disarm"
    const val ARM_HOME = "arm_home"
    const val ARM_AWAY = "arm_away"
    const val ARM_NIGHT = "arm_night"
    const val ARM_VACATION = "arm_vacation"
    const val ARM_PREFIX = "arm_"
    const val ARMED_PREFIX = "armed_"
}
