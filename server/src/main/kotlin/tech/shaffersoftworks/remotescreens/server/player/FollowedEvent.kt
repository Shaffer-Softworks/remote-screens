package tech.shaffersoftworks.remotescreens.server.player

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantEvent
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.shared.Protocol

/**
 * An event an automation fires for the screens, for setups without the custom integration. [targets] are screen or
 * device ids, from `targets` or `screens`; empty means every screen.
 */
sealed interface FollowedEvent {
    data class ShowAlert(val alert: Alert) : FollowedEvent

    data class OpenPage(val targets: List<String>, val page: String) : FollowedEvent

    data class Wake(val targets: List<String>) : FollowedEvent

    data class Close(val targets: List<String>) : FollowedEvent

    /** A followed event whose data could not be read, such as an alert without a message. */
    data class Unreadable(val type: String, val reason: String) : FollowedEvent

    companion object {
        const val ALERT = "remote_screens_alert"
        const val OPEN_PAGE = "remote_screens_open_page"
        const val WAKE = "remote_screens_wake"
        const val CLOSE = "remote_screens_close"

        val TYPES: List<String> = listOf(ALERT, OPEN_PAGE, WAKE, CLOSE)

        /** Fired back when an alert's button is pressed. */
        const val ALERT_ACTION = "remote_screens_alert_action"

        /** The screens' reading of [event], or null for an event they do not follow. */
        fun of(event: HomeAssistantEvent): FollowedEvent? {
            val data = event.data
            return when (event.type) {
                ALERT -> runCatching { Protocol.json.decodeFromJsonElement(Alert.serializer(), data) }
                    .fold(onSuccess = ::ShowAlert, onFailure = { Unreadable(ALERT, it.message.orEmpty()) })
                OPEN_PAGE -> (data["page"] as? JsonPrimitive)?.content
                    ?.let { OpenPage(targetsOf(data), it) }
                    ?: Unreadable(OPEN_PAGE, "no page")
                WAKE -> Wake(targetsOf(data))
                CLOSE -> Close(targetsOf(data))
                else -> null
            }
        }

        private fun targetsOf(data: JsonObject): List<String> = when (
            val targets = data["targets"] ?: data["screens"]
        ) {
            is JsonArray -> targets.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(targets.content)
            else -> emptyList()
        }
    }
}
