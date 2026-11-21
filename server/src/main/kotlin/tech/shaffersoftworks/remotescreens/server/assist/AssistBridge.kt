package tech.shaffersoftworks.remotescreens.server.assist

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import tech.shaffersoftworks.remotescreens.server.player.PlayerHub
import tech.shaffersoftworks.remotescreens.shared.AssistDownlink
import tech.shaffersoftworks.remotescreens.shared.AssistUplink
import tech.shaffersoftworks.remotescreens.shared.AssistWire

/** Speaks Assist for one adopted tablet. The socket opens for the command, including one follow-up, then closes. */
class AssistBridge(
    private val runs: AssistRuns,
    private val playerHub: PlayerHub,
) {
    suspend fun serve(session: WebSocketServerSession) {
        val hello = session.incoming.receive() as? Frame.Text ?: return session.close()
        val uplink = runCatching { AssistWire.decodeUplink(hello.readText()) }.getOrNull()

        if (uplink !is AssistUplink.Hello || !playerHub.isTrustedToken(uplink.token)) {
            session.close()

            return
        }

        WebSocketPlayerLink(session).exchange { link ->
            runs.converse(pipelineId = uplink.pipelineId, wakeWordId = uplink.wakeWord, link = link)
        }
    }
}

private class WebSocketPlayerLink(private val session: WebSocketServerSession) : AssistPlayerLink, AutoCloseable {
    private var audio = Channel<ByteArray>(capacity = Channel.BUFFERED)
    private var audioOpen = true
    private val ready = Channel<Unit>(capacity = 1)
    private var reader: Job? = null

    suspend fun exchange(block: suspend (AssistPlayerLink) -> Unit) {
        coroutineScope {
            reader = launch { readFrames() }
            block(this@WebSocketPlayerLink)
            reader?.cancel()
        }
    }

    override suspend fun phase(phase: String) = send(AssistWire.encodeDownlink(AssistDownlink.Phase(phase)))

    override suspend fun transcript(text: String) = send(AssistWire.encodeDownlink(AssistDownlink.Transcript(text)))

    override suspend fun reply(text: String, followUp: Boolean) = send(
        AssistWire.encodeDownlink(AssistDownlink.Reply(text = text, followUp = followUp)),
    )

    override suspend fun speech(mimeType: String, audio: ByteArray) {
        send(AssistWire.encodeDownlink(AssistDownlink.Speech(mimeType)))
        session.send(Frame.Binary(fin = true, audio))
    }

    override suspend fun ended() = send(AssistWire.encodeDownlink(AssistDownlink.Ended))

    override suspend fun failed(message: String) = send(
        AssistWire.encodeDownlink(AssistDownlink.Failed(message)),
    )

    override fun openAudio(): ReceiveChannel<ByteArray> {
        if (!audioOpen) {
            audio = Channel(capacity = Channel.BUFFERED)
            audioOpen = true
        }

        return audio
    }

    override fun stopAudio() {
        audioOpen = false
        audio.close()
    }

    override suspend fun awaitReady(): Boolean = ready.receiveCatching().isSuccess

    override fun close() {
        audio.close()
        reader?.cancel()
    }

    private suspend fun readFrames() {
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Binary -> if (audioOpen) audio.send(frame.data)
                is Frame.Text -> {
                    val message = runCatching { AssistWire.decodeUplink(frame.readText()) }.getOrNull()

                    if (message is AssistUplink.Ready) ready.trySend(Unit)
                }
                else -> Unit
            }
        }
    }

    private suspend fun send(text: String) {
        session.send(Frame.Text(text))
    }
}
