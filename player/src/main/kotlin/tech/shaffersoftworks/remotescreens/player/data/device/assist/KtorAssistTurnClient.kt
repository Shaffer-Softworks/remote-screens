package tech.shaffersoftworks.remotescreens.player.data.device.assist

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.shared.AssistDownlink
import tech.shaffersoftworks.remotescreens.shared.AssistUplink
import tech.shaffersoftworks.remotescreens.shared.AssistWire
import tech.shaffersoftworks.remotescreens.shared.Protocol
import javax.inject.Inject
import javax.inject.Singleton

/** One websocket to the add-on for a command. PCM goes up; the spoken reply comes back as one binary frame. */
@Singleton
class KtorAssistTurnClient @Inject constructor() : AssistTurnClient {
    private val httpClient = HttpClient(CIO) { install(WebSockets) }

    override suspend fun run(
        request: AssistTurnRequest,
        pcm: ReceiveChannel<ByteArray>,
        events: AssistTurnEvents,
    ) {
        httpClient.webSocket(urlString = "ws://${request.server}${Protocol.ASSIST_PATH}") {
            sendHello(request)

            coroutineScope {
                val nextAudio = Channel<ReceiveChannel<ByteArray>>(capacity = 1)
                val forward = launch { forwardPcm(first = pcm, nextAudio = nextAudio) }

                readDownlink(events = events, nextAudio = nextAudio)
                nextAudio.close()
                forward.cancel()
                events.ended()
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendHello(request: AssistTurnRequest) {
        val hello = AssistUplink.Hello(
            token = request.token,
            pipelineId = request.pipelineId,
            wakeWord = request.wakeWord,
        )

        send(Frame.Text(AssistWire.encodeUplink(hello)))
    }

    private suspend fun DefaultClientWebSocketSession.forwardPcm(
        first: ReceiveChannel<ByteArray>,
        nextAudio: ReceiveChannel<ReceiveChannel<ByteArray>>,
    ) {
        var current = first

        while (true) {
            val chunk = current.receiveCatching().getOrNull()

            if (chunk != null) {
                send(Frame.Binary(fin = true, data = chunk))
            } else {
                current = nextAudio.receiveCatching().getOrNull() ?: return
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.readDownlink(
        events: AssistTurnEvents,
        nextAudio: SendChannel<ReceiveChannel<ByteArray>>,
    ) {
        val followUp = FollowUpFlag()

        for (frame in incoming) {
            val text = frame as? Frame.Text ?: continue

            if (!dispatch(text = text, events = events, nextAudio = nextAudio, followUp = followUp)) {
                return
            }
        }
    }

    private suspend fun DefaultClientWebSocketSession.dispatch(
        text: Frame.Text,
        events: AssistTurnEvents,
        nextAudio: SendChannel<ReceiveChannel<ByteArray>>,
        followUp: FollowUpFlag,
    ): Boolean {
        var isOpen = true

        when (val message = AssistWire.decodeDownlink(text.readText())) {
            is AssistDownlink.Phase -> events.phase(message.phase)
            is AssistDownlink.Transcript -> events.transcript(message.text)
            is AssistDownlink.Reply -> {
                followUp.isArmed = message.followUp
                events.reply(text = message.text, followUp = message.followUp)
            }
            is AssistDownlink.Speech -> deliverSpeech(
                message = message,
                events = events,
                nextAudio = nextAudio,
                followUp = followUp,
            )
            AssistDownlink.Ended -> isOpen = false
            is AssistDownlink.Failed -> {
                events.failed(message.message)
                isOpen = false
            }
        }

        return isOpen
    }

    private suspend fun DefaultClientWebSocketSession.deliverSpeech(
        message: AssistDownlink.Speech,
        events: AssistTurnEvents,
        nextAudio: SendChannel<ReceiveChannel<ByteArray>>,
        followUp: FollowUpFlag,
    ) {
        val audio = (incoming.receive() as? Frame.Binary)?.data ?: ByteArray(0)

        events.speech(mimeType = message.mimeType, audio = audio)

        if (!followUp.isArmed) {
            return
        }

        nextAudio.send(events.followUpAudio())
        send(Frame.Text(AssistWire.encodeUplink(AssistUplink.Ready)))
        followUp.isArmed = false
    }
}

private class FollowUpFlag {
    var isArmed: Boolean = false
}
