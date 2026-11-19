package tech.shaffersoftworks.remotescreens.server.data.homeassistant

/** Level verbs the card, the tile and free-form all send. One spelling, every call site. */
object ControlVerb {
    const val BRIGHTNESS = LightColorMode.BRIGHTNESS
    const val PERCENTAGE = "percentage"
    const val POSITION = "position"
    const val TILT = "tilt"
    const val VOLUME = "volume"
    const val VALUE = "value"
}
