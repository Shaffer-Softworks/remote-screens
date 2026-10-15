package dev.remotescreens.server.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the editor and the integration show about a player. */
@Serializable
data class PlayerStatus(
    val deviceId: String,
    val name: String,
    val screenId: String?,
    @SerialName("online") val isOnline: Boolean,
    val pairingCode: String?,
    val widthDp: Int,
    val heightDp: Int,
    val appVersion: Int,
    val lastSeen: Long,
    @SerialName("awake") val isAwake: Boolean,
    val page: Int,
    val battery: Int?,
    /** `off`, `pinned` or `locked`, as the player reports it; null while offline or from older players. */
    val kiosk: String? = null,
)
