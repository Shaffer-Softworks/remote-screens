package tech.shaffersoftworks.remotescreens.server.assist

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistPipelineTest {
    @Test
    fun theHandlerIdArrivesOnRunStart() {
        val message = """
            {"id":1,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":7}}}}
        """.trimIndent()

        assertEquals(PipelineNotice.Handler(7), parsePipelineMessage(message))

        assertEquals(
            PipelineNotice.Transcript("turn off the lights"),
            parsePipelineMessage(
                """{"type":"event","event":{"type":"stt-end","data":{"stt_output":{"text":"turn off the lights"}}}}""",
            ),
        )

        assertEquals(PipelineNotice.StopAudio, parsePipelineMessage(
            """{"type":"event","event":{"type":"stt-vad-end","data":{}}}""",
        ))

        assertEquals(PipelineNotice.Finished, parsePipelineMessage(
            """{"type":"event","event":{"type":"run-end","data":{}}}""",
        ))
    }

    @Test
    fun aRunStartsAtSpeechToTextAndReturnsToLocalListeningAfterRunEnd() = runTest {
        val socket = ScriptedAssistSocket()
        var closed = false

        val session = AssistPipelineSession(accessToken = "token") { block ->
            socket.block()
            closed = true
        }

        val audio = Channel<ByteArray>(capacity = 1)
        audio.trySend(byteArrayOf(1, 2, 3))
        val notices = mutableListOf<PipelineNotice>()

        session.execute(
            pipelineId = null,
            conversationId = null,
            wakeWordPhrase = "Okay Nabu",
            audio = audio,
        ) { notice ->
            notices += notice

            if (notice == PipelineNotice.StopAudio) audio.close()
        }

        val run = socket.sentText.first { it.contains("assist_pipeline/run") }
        assertTrue(run.contains("\"start_stage\":\"stt\""))
        assertTrue(run.contains("\"end_stage\":\"tts\""))
        assertFalse(run.contains("\"start_stage\":\"wake_word\""))
        assertEquals(7, socket.sentBinary.first().first().toInt())
        assertTrue(notices.contains(PipelineNotice.Finished))
        assertTrue(closed)
        assertEquals(1, socket.sentText.count { it.contains("assist_pipeline/run") })
    }

    @Test
    fun aFollowUpStartsAgainAtSpeechToTextWithTheSameConversation() = runTest {
        val socket = FollowUpSocket()

        val session = AssistPipelineSession(accessToken = "token") { block ->
            socket.greet()
            socket.block()
        }

        val link = ReadyLink()

        AssistConversation(session = session, fetchSpeech = { byteArrayOf(9) })
            .talk(link = link, pipelineId = null, wakeWordId = "okay_nabu")

        assertEquals(2, socket.runs.size)
        assertFalse(socket.runs[0].contains("conversation_id"))
        assertTrue(socket.runs[1].contains("\"conversation_id\":\"c1\""))
        assertFalse(socket.runs[1].contains("wake_word_phrase"))
        assertEquals(listOf("The kitchen lights are off", "Anything else?"), link.replies)
        assertTrue(link.ended)
    }

    @Test
    fun demoSpeaksTheSampleAndDoesNotOpenAPipeline() = runTest {
        val link = ReadyLink()

        DemoAssistRuns().converse(pipelineId = null, wakeWordId = "okay_nabu", link = link)

        assertEquals(listOf("Turn off the kitchen lights"), link.heard)
        assertEquals(listOf("The kitchen lights are off"), link.replies)
        assertTrue(link.speech.isEmpty() || link.speech.all { it.isEmpty() })
        assertTrue(link.ended)
    }
}

private class ScriptedAssistSocket : AssistHaSocket {
    val sentText = mutableListOf<String>()
    val sentBinary = mutableListOf<ByteArray>()
    private val incoming = Channel<String>(capacity = Channel.UNLIMITED)
    private var sentRunEvents = false

    init {
        incoming.trySend("""{"type":"auth_required","ha_version":"2026.11"}""")
    }

    override suspend fun sendText(text: String) {
        sentText += text

        if (text.contains("\"type\":\"auth\"")) incoming.trySend("""{"type":"auth_ok","ha_version":"2026.11"}""")

        if (text.contains("assist_pipeline/run")) {
            incoming.trySend(
                """{"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":7}}}}""",
            )
        }
    }

    override suspend fun sendBinary(bytes: ByteArray) {
        sentBinary += bytes

        if (!sentRunEvents && bytes.size > 1) {
            sentRunEvents = true
            incoming.trySend("""{"type":"event","event":{"type":"stt-vad-end","data":{}}}""")

            incoming.trySend(
                """{"type":"event","event":{"type":"stt-end","data":{"stt_output":{"text":"turn off the lights"}}}}""",
            )

            incoming.trySend("""{"type":"event","event":{"type":"run-end","data":{}}}""")
        }
    }

    override suspend fun receiveText(): String = incoming.receive()
}

private class FollowUpSocket : AssistHaSocket {
    val runs = mutableListOf<String>()
    private val incoming = Channel<String>(capacity = Channel.UNLIMITED)
    private var runIndex = 0

    fun greet() {
        incoming.trySend("""{"type":"auth_required"}""")
    }

    override suspend fun sendText(text: String) {
        if (text.contains("\"type\":\"auth\"")) incoming.trySend("""{"type":"auth_ok"}""")

        if (text.contains("assist_pipeline/run")) {
            runs += text
            runIndex += 1

            incoming.trySend(
                """{"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":3}}}}""",
            )
        }
    }

    override suspend fun sendBinary(bytes: ByteArray) {
        if (bytes.size <= 1) return

        val followUp = runIndex == 1
        val speech = if (followUp) "The kitchen lights are off" else "Anything else?"
        incoming.trySend("""{"type":"event","event":{"type":"stt-vad-end","data":{}}}""")

        incoming.trySend(
            """{"type":"event","event":{"type":"intent-end","data":{"intent_output":""" +
                """{"conversation_id":"c1","continue_conversation":$followUp,""" +
                """"response":{"speech":{"plain":{"speech":"$speech"}}}}}}}""",
        )

        incoming.trySend(
            """{"type":"event","event":{"type":"tts-end","data":""" +
                """{"tts_output":{"url":"/api/tts/1","mime_type":"audio/mpeg"}}}}""",
        )

        incoming.trySend("""{"type":"event","event":{"type":"run-end","data":{}}}""")
    }

    override suspend fun receiveText(): String = incoming.receive()
}

private class ReadyLink : AssistPlayerLink {
    val heard = mutableListOf<String>()
    val replies = mutableListOf<String>()
    val speech = mutableListOf<ByteArray>()
    var ended = false
    private var audio = Channel<ByteArray>(capacity = 1)
    private var audioOpen = true

    init {
        audio.trySend(byteArrayOf(4))
    }

    override suspend fun phase(phase: String) = Unit
    override suspend fun transcript(text: String) { heard += text }
    override suspend fun reply(text: String, followUp: Boolean) { replies += text }
    override suspend fun speech(mimeType: String, audio: ByteArray) { speech += audio }
    override suspend fun ended() { ended = true }
    override suspend fun failed(message: String) = Unit

    override fun openAudio(): ReceiveChannel<ByteArray> {
        if (!audioOpen) {
            audio = Channel(capacity = 1)
            audioOpen = true
            audio.trySend(byteArrayOf(5))
        }

        return audio
    }

    override fun stopAudio() {
        audioOpen = false
        audio.close()
    }

    override suspend fun awaitReady(): Boolean = true
}
