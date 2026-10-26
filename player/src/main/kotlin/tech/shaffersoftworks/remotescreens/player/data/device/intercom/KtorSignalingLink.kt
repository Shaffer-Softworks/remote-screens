package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KtorSignalingLink @Inject constructor(
    private val httpClient: HttpClient,
) : SignalingLink {
    override suspend fun conversation(url: String, block: suspend SignalingConversation.() -> Unit) {
        httpClient.webSocket(url) {
            val socket = this
            val conversation = object : SignalingConversation {
                override suspend fun send(text: String) {
                    socket.send(Frame.Text(text))
                }

                override suspend fun receive(): String? {
                    while (true) {
                        val frame = incoming.receiveCatching().getOrNull() ?: return null
                        if (frame is Frame.Text) return frame.readText()
                    }
                }
            }
            conversation.block()
        }
    }
}
