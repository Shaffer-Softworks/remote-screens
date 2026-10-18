package tech.shaffersoftworks.remotescreens.server.render

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Home Assistant frontend weather state SVGs (`src/data/weather.ts` `getWeatherStateSVG`):
 * multi-path, multi-colour icons on a 17×17 viewBox — not the monochrome MDI set.
 * Paths in `ha-weather-paths.json` are normalised to absolute M/L/C/Z for [Rc.path].
 */
internal object HaWeatherIcons {
    const val VIEWPORT = 17f

    /** Default fills from HA `weatherSVGStyles`. */
    const val SUN = 0xFFFDD93C.toInt()
    const val MOON = 0xFFFCF497.toInt()
    const val CLOUD_BACK = 0xFFD4D4D4.toInt()
    const val CLOUD_FRONT = 0xFFF9F9F9.toInt()
    const val RAIN = 0xFF30B3FF.toInt()
    const val SNOW = 0xFFF9F9F9.toInt()

    enum class Layer { SUN, MOON, CLOUD_BACK, CLOUD_FRONT, RAIN, SNOW }

    data class Piece(val layer: Layer, val path: String)

    fun color(layer: Layer): Int = when (layer) {
        Layer.SUN -> SUN
        Layer.MOON -> MOON
        Layer.CLOUD_BACK -> CLOUD_BACK
        Layer.CLOUD_FRONT -> CLOUD_FRONT
        Layer.RAIN -> RAIN
        Layer.SNOW -> SNOW
    }

    /** Layered paths for [condition], or null when HA falls back to MDI (e.g. `exceptional`). */
    fun pieces(condition: String, nightTime: Boolean = false): List<Piece>? {
        if (condition !in SVG_STATES) return null
        return buildList {
            when {
                condition == "sunny" -> add(Piece(Layer.SUN, path("sunny")))
                condition == "clear-night" -> add(Piece(Layer.MOON, path("clear_night")))
                condition == "partlycloudy" && nightTime -> add(Piece(Layer.MOON, path("partly_disk")))
                condition == "partlycloudy" -> add(Piece(Layer.SUN, path("partly_disk")))
            }
            if (condition in CLOUDY) {
                add(Piece(Layer.CLOUD_BACK, path("cloud_back")))
                add(Piece(Layer.CLOUD_FRONT, path("cloud_front")))
            }
            if (condition in RAINY) {
                add(Piece(Layer.RAIN, path("rain_1")))
                add(Piece(Layer.RAIN, path("rain_2")))
                add(Piece(Layer.RAIN, path("rain_3")))
                add(Piece(Layer.RAIN, path("rain_4")))
            }
            if (condition == "pouring") {
                add(Piece(Layer.RAIN, path("pour_1")))
                add(Piece(Layer.RAIN, path("pour_2")))
            }
            if (condition in WINDY) {
                add(Piece(Layer.CLOUD_BACK, path("wind_1")))
                add(Piece(Layer.CLOUD_BACK, path("wind_2")))
            }
            if (condition in SNOWY) {
                add(Piece(Layer.SNOW, path("snow_1")))
                add(Piece(Layer.SNOW, path("snow_2")))
                add(Piece(Layer.SNOW, path("snow_3")))
            }
            if (condition in LIGHTNING) {
                add(Piece(Layer.SUN, path("bolt")))
            }
        }
    }

    private val paths by lazy {
        val stream = checkNotNull(
            HaWeatherIcons::class.java.getResourceAsStream("/ha-weather-paths.json"),
        ) { "ha-weather-paths.json is not bundled" }
        val text = stream.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    private fun path(key: String): String = checkNotNull(paths[key]) { "missing weather path $key" }

    private val SVG_STATES = setOf(
        "clear-night", "cloudy", "fog", "lightning", "lightning-rainy", "partlycloudy",
        "pouring", "rainy", "hail", "snowy", "snowy-rainy", "sunny", "windy", "windy-variant",
    )
    private val CLOUDY = setOf(
        "partlycloudy", "cloudy", "fog", "windy", "windy-variant", "hail", "rainy",
        "snowy", "snowy-rainy", "pouring", "lightning", "lightning-rainy",
    )
    private val RAINY = setOf("hail", "rainy", "pouring", "lightning-rainy")
    private val WINDY = setOf("windy", "windy-variant")
    private val SNOWY = setOf("snowy", "snowy-rainy")
    private val LIGHTNING = setOf("lightning", "lightning-rainy")
}
