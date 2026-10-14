package dev.remotescreens.server.data.homeassistant

import kotlinx.serialization.json.JsonObject

data class HomeAssistantEvent(val type: String, val data: JsonObject)

data class HomeAssistantConfig(
    val language: String = "en",
    val country: String? = null,
    val timeZone: String = "UTC",
    val temperatureUnit: String = "°C",
    val locationName: String = "Home",
    val version: String = "",
)

/** A command Home Assistant answered with `success: false`. */
class HomeAssistantException(val code: String, message: String) : Exception(message)
