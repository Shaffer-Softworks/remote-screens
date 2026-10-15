package tech.shaffersoftworks.remotescreens.server.routes.model

import tech.shaffersoftworks.remotescreens.server.player.PlayerStatus
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.ScreenSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoResponse(
    val version: String,
    @SerialName("demo") val isDemo: Boolean,
    @SerialName("connected") val isConnected: Boolean,
    val language: String,
    val haVersion: String,
    val locationName: String,
    /** The version of the player app the add-on can hand out, or null when it has none. */
    val playerApp: String? = null,
)

@Serializable
data class EntityResponse(
    val entityId: String,
    val name: String,
    val state: String,
    val domain: String,
    val area: String?,
    val icon: String?,
    val unit: String?,
)

@Serializable
data class AreaResponse(val id: String, val name: String)

@Serializable
data class CreateScreenRequest(
    val name: String,
    val grid: GridSize? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
)

@Serializable
data class AdoptPlayerRequest(val screenId: String, val name: String? = null, val code: String? = null)

@Serializable
data class RenamePlayerRequest(val name: String)

@Serializable
data class OpenPageRequest(val page: String, val targets: List<String> = emptyList())

@Serializable
data class TargetsRequest(val targets: List<String> = emptyList())

@Serializable
data class IntegrationScreenResponse(
    val id: String,
    val name: String,
    val settings: ScreenSettings,
    val pages: List<String>,
    val players: List<PlayerStatus>,
)

@Serializable
data class ErrorResponse(val error: String)

/** What the editor asks for a QR enrollment code. The Wi-Fi password only goes into the code; it is never stored. */
@Serializable
data class EnrollmentRequest(
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    /** `WPA`, `WEP` or `NONE`. */
    val wifiSecurity: String? = null,
    /** The add-on's address as tablets reach it, when the one it finds by itself is wrong. */
    val host: String? = null,
)

@Serializable
data class EnrollmentResponse(
    /** The JSON to put in the QR code. */
    val provisioning: String,
    val downloadUrl: String,
    /** Epoch milliseconds after which the code no longer downloads the app. */
    val expiresAt: Long,
)
