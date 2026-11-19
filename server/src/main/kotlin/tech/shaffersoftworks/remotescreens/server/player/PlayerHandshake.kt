package tech.shaffersoftworks.remotescreens.server.player

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import tech.shaffersoftworks.remotescreens.shared.Protocol

/**
 * The player's opening [PlayerMessage.Hello], or null when the socket closes first, the first frame is not text, or
 * it is another message or an unsupported protocol version.
 */
internal suspend fun WebSocketSession.receiveHello(): PlayerMessage.Hello? {
    val firstFrame = incoming.receiveCatching().getOrNull() as? Frame.Text

    return firstFrame
        ?.let { frame -> Protocol.decodePlayer(frame.readText()) as? PlayerMessage.Hello }
        ?.takeIf { hello -> hello.protocol >= 1 }
}
