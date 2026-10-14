package dev.remotescreens.server.routes.model

import dev.remotescreens.server.player.PlayerStatus
import dev.remotescreens.shared.GridSize
import dev.remotescreens.shared.ScreenSettings
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
