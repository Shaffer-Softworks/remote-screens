package tech.shaffersoftworks.remotescreens.server.render.cards

/**
 * Card the renderer draws. [wire] is the `card` string in catalogue yaml.
 * An unknown wire value draws [TOGGLE], which is what a missing card used to do.
 */
enum class CardType(val wire: String) {
    LIGHT("light"),
    FAN("fan"),
    HUMIDIFIER("humidifier"),
    COVER("cover"),
    LOCK("lock"),
    CLIMATE("climate"),
    MEDIA("media"),
    WEATHER("weather"),
    HISTORY("history"),
    CAMERA("camera"),
    ALARM("alarm"),
    SELECT("select"),
    NUMBER("number"),
    VACUUM("vacuum"),
    LAWN_MOWER("lawn_mower"),
    CALENDAR("calendar"),
    TODO("todo"),
    TIMER("timer"),
    COUNTER("counter"),
    WATER_HEATER("water_heater"),
    VALVE("valve"),
    SIREN("siren"),
    INPUT_TEXT("input_text"),
    INPUT_DATETIME("input_datetime"),
    MAP("map"),
    ENERGY("energy"),
    TOGGLE("toggle"),
    NONE("none"),
    ;

    companion object {
        private val byWire: Map<String, CardType> = entries.associateBy { it.wire }

        fun fromWire(wire: String?): CardType = byWire[wire] ?: TOGGLE
    }
}
