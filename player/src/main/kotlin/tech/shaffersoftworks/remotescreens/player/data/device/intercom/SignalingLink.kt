package tech.shaffersoftworks.remotescreens.player.data.device.intercom

/** The open signaling socket. Production uses the player's Ktor client; tests feed frames by hand. */
interface SignalingLink {
    suspend fun conversation(url: String, block: suspend SignalingConversation.() -> Unit)
}

interface SignalingConversation {
    suspend fun send(text: String)
    suspend fun receive(): String?
}
