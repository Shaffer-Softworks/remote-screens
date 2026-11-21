package tech.shaffersoftworks.remotescreens.server.assist

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val AUTH = "auth"
private const val PIPELINE_RUN = "assist_pipeline/run"
private const val COMMAND_ID = 1

/** One Home Assistant websocket used only for an Assist run, so binary audio stays off the state socket. */
interface AssistHaSocket {
    suspend fun sendText(text: String)
    suspend fun sendBinary(bytes: ByteArray)
    suspend fun receiveText(): String
}

/** Opens a websocket, authenticates, and runs speech-to-text through to the spoken reply. */
class AssistPipelineSession(
    private val accessToken: String,
    private val open: suspend (suspend AssistHaSocket.() -> Unit) -> Unit,
) {
    suspend fun execute(
        pipelineId: String?,
        conversationId: String?,
        wakeWordPhrase: String?,
        audio: ReceiveChannel<ByteArray>,
        onNotice: suspend (PipelineNotice) -> Unit,
    ) {
        open {
            check(receiveText().contains("auth_required")) { "unexpected Assist greeting" }
            sendText(authMessage())
            check(receiveText().contains("auth_ok")) { "Assist authentication failed" }
            sendText(runMessage(pipelineId, conversationId, wakeWordPhrase))
            readRun(audio, onNotice)
        }
    }

    private suspend fun AssistHaSocket.readRun(
        audio: ReceiveChannel<ByteArray>,
        onNotice: suspend (PipelineNotice) -> Unit,
    ) {
        coroutineScope {
            var handlerId: Int? = null
            val pending = ArrayDeque<ByteArray>()
            var audioEnded = false

            val pump = launch {
                for (chunk in audio) {
                    val id = handlerId

                    if (id == null) pending.addLast(chunk) else sendPcm(id, chunk)
                }

                audioEnded = true
                val id = handlerId

                if (id != null) sendEnd(id)
            }

            while (true) {
                when (val notice = parsePipelineMessage(receiveText())) {
                    is PipelineNotice.Handler -> {
                        handlerId = notice.id
                        pending.forEach { chunk -> sendPcm(notice.id, chunk) }
                        pending.clear()

                        if (audioEnded) sendEnd(notice.id)
                    }
                    is PipelineNotice.Finished -> {
                        onNotice(notice)
                        pump.cancel()

                        return@coroutineScope
                    }
                    is PipelineNotice.Failed -> {
                        onNotice(notice)
                        pump.cancel()

                        return@coroutineScope
                    }
                    else -> onNotice(notice)
                }
            }
        }
    }

    private suspend fun AssistHaSocket.sendPcm(handlerId: Int, pcm: ByteArray) {
        val frame = ByteArray(1 + pcm.size)
        frame[0] = handlerId.toByte()
        pcm.copyInto(frame, destinationOffset = 1)
        sendBinary(frame)
    }

    private suspend fun AssistHaSocket.sendEnd(handlerId: Int) {
        sendBinary(byteArrayOf(handlerId.toByte()))
    }

    private fun authMessage(): String = buildJsonObject {
        put("type", AUTH)
        put("access_token", accessToken)
    }.toString()

    private fun runMessage(pipelineId: String?, conversationId: String?, wakeWordPhrase: String?): String {
        val input = buildJsonObject {
            put("sample_rate", ASSIST_SAMPLE_RATE)
            wakeWordPhrase?.let { put("wake_word_phrase", it) }
        }

        val payload = buildJsonObject {
            put("id", COMMAND_ID)
            put("type", PIPELINE_RUN)
            put("start_stage", ASSIST_START_STAGE)
            put("end_stage", ASSIST_END_STAGE)
            put("input", input)
            pipelineId?.let { put("pipeline", it) }
            conversationId?.let { put("conversation_id", it) }
        }

        return payload.toString()
    }
}

/** The real Home Assistant socket. Each run gets its own connection and closes it when the run returns. */
class KtorAssistSockets(
    private val webSocketUrl: String,
    private val accessToken: String,
) {
    private val httpClient = HttpClient(CIO) { install(WebSockets) }

    fun session(): AssistPipelineSession = AssistPipelineSession(accessToken) { block ->
        httpClient.webSocket(urlString = webSocketUrl) {
            val socket = object : AssistHaSocket {
                override suspend fun sendText(text: String) = send(Frame.Text(text))
                override suspend fun sendBinary(bytes: ByteArray) = send(Frame.Binary(fin = true, bytes))
                override suspend fun receiveText(): String = (incoming.receive() as Frame.Text).readText()
            }

            socket.block()
        }
    }
}
