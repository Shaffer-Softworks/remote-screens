package tech.shaffersoftworks.remotescreens.player.data.device.assist

import kotlinx.coroutines.channels.ReceiveChannel
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.AssistWakeWords
import java.io.Closeable

/** One bundled wake word. Not thread-safe; the capture loop is the only caller. */
interface WakeWordDetector {
    val isReady: Boolean
    val phrase: String
    fun processAudio(samples: ShortArray): Boolean
    fun reset()
    fun close()
}

fun interface WakeWordDetectors {
    fun open(wakeWordId: String): WakeWordDetector
}

interface Microphone {
    val hasPermission: Boolean
    fun open(): MicrophoneSession
}

interface MicrophoneSession : Closeable {
    val samples: ReceiveChannel<ShortArray>
}

/** Events the add-on sends during one turn. [speech] includes the bytes that follow the speech message. */
interface AssistTurnEvents {
    suspend fun phase(phase: String)
    suspend fun transcript(text: String)
    suspend fun reply(text: String, followUp: Boolean)
    suspend fun speech(mimeType: String, audio: ByteArray)
    suspend fun ended()
    suspend fun failed(message: String)

    /** Opens the microphone again after the reply, with no wake word, for one follow-up. */
    suspend fun followUpAudio(): ReceiveChannel<ByteArray>
}

data class AssistTurnRequest(
    val server: String,
    val token: String,
    val pipelineId: String?,
    val wakeWord: String,
)

interface AssistTurnClient {
    suspend fun run(
        request: AssistTurnRequest,
        pcm: ReceiveChannel<ByteArray>,
        events: AssistTurnEvents,
    )
}

interface SpeechPlayback {
    suspend fun play(mimeType: String, audio: ByteArray)
}

/** Keeps a microphone foreground service while the screen is off and Assist is listening. */
interface AssistForeground {
    fun setListeningWhileScreenOff(isEnabled: Boolean)
}

data class AssistSessionConfig(
    val isEnabled: Boolean = false,
    val wakeWordId: String = AssistWakeWords.OKAY_NABU,
    val pipelineId: String? = null,
    val isPaused: Boolean = false,
    val isScreenOff: Boolean = false,
    val server: String? = null,
    val token: String? = null,
)

data class AssistTurn(val speaker: String, val text: String)

data class AssistUiState(
    val phase: String = AssistPhases.UNAVAILABLE,
    val caption: String? = null,
    val turns: List<AssistTurn> = emptyList(),
    val wakeWord: String = AssistWakeWords.phrase(AssistWakeWords.OKAY_NABU),
    val isMuted: Boolean = false,
)
