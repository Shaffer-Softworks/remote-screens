package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val NOTIFICATION_MAX_DURATION = 3600
private const val MAX_TITLE_LENGTH = 100
private const val MAX_MESSAGE_LENGTH = 500
private const val MAX_BUTTON_TITLE_LENGTH = 60

/** Immediate local delivery. Empty targets are invalid unless all is explicitly true. */
@Serializable
data class NotificationRequest(
    val title: String,
    val message: String = "",
    val targets: List<String> = emptyList(),
    val all: Boolean = false,
    val presentation: String = "banner",
    val level: String = "info",
    val timeout: Int? = null,
    val wake: Boolean = false,
    val icon: String? = null,
    val camera: String? = null,
    val buttons: List<NotificationButton> = emptyList(),
    val tag: String? = null,
) {
    val duration: Int get() = timeout ?: if (presentation == "banner") 10 else 30

    fun validate() {
        require(title.isNotBlank() && title.length <= MAX_TITLE_LENGTH) { "Title must contain 1–100 characters" }
        require(message.length <= MAX_MESSAGE_LENGTH) { "Message must be at most 500 characters" }
        require(presentation in setOf("banner", "alert")) { "Presentation must be banner or alert" }
        require(level in setOf("info", "warning", "danger")) { "Invalid level" }
        require(all.xor(targets.isNotEmpty())) { "Choose targets or explicitly select all" }
        require(targets.all { it.isNotBlank() }) { "Invalid target" }
        require(duration in 0..NOTIFICATION_MAX_DURATION && (presentation != "banner" || duration > 0)) {
            "Invalid timeout"
        }
        require(camera == null || (presentation == "alert" && camera.startsWith("camera."))) {
            "Cameras are supported only on alerts"
        }
        val validButtons = buttons.all { it.title.isNotBlank() && it.title.length <= MAX_BUTTON_TITLE_LENGTH }
        require(buttons.size <= 2 && validButtons) {
            "Provide up to two labeled buttons"
        }
        require(buttons.all { it.service == null || it.service.matches(Regex("[a-z0-9_]+\\.[a-z0-9_]+")) }) {
            "Button services must use domain.service"
        }
    }
}

@Serializable
data class NotificationButton(
    val title: String,
    val service: String? = null,
    val data: JsonObject? = null,
    val action: String? = null,
)

/** sent means dispatched to the connection, not displayed or read. */
@Serializable
data class NotificationDelivery(val deviceId: String, val name: String, val status: String)

@Serializable
data class NotificationResult(val devices: List<NotificationDelivery>)
