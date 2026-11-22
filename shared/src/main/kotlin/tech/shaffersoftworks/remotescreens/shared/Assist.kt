package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DEFAULT_CUTOFF = 0.5f
private const val DEFAULT_WINDOW = 5
private const val DEFAULT_STEP_MS = 10

/** Header microphone drawn with the other status icons. The player swaps the two by these ints. */
object AssistLayout {
    const val MIC_VARIABLE = "sys.assist_mic"
    const val MUTED_VARIABLE = "sys.assist_muted"
    const val VISIBLE = 1
    const val GONE = 0
}

/** Wake word models bundled in the player. A phrase that exists only in openWakeWord is not heard. */
object AssistWakeWords {
    const val OKAY_NABU = "okay_nabu"
    const val HEY_JARVIS = "hey_jarvis"
    const val HEY_MYCROFT = "hey_mycroft"
    const val ALEXA = "alexa"

    val IDS = listOf(OKAY_NABU, HEY_JARVIS, HEY_MYCROFT, ALEXA)

    private val PHRASES = mapOf(
        OKAY_NABU to "Okay Nabu",
        HEY_JARVIS to "Hey Jarvis",
        HEY_MYCROFT to "Hey Mycroft",
        ALEXA to "Alexa",
    )

    fun validated(id: String): String = id.takeIf { it in IDS } ?: OKAY_NABU

    fun phrase(id: String): String = PHRASES.getValue(validated(id))

    /** Cutoff and window from the model's own manifest. Those values construct the detector. */
    fun manifest(id: String, json: String): WakeWordManifest {
        val file = runCatching { MANIFEST_JSON.decodeFromString(WakeWordFile.serializer(), json) }.getOrNull()
        val wakeWordId = validated(id)

        return WakeWordManifest(
            id = wakeWordId,
            phrase = file?.wakeWord?.takeIf { it.isNotBlank() } ?: phrase(wakeWordId),
            probabilityCutoff = file?.micro?.probabilityCutoff ?: DEFAULT_CUTOFF,
            slidingWindowSize = file?.micro?.slidingWindowSize ?: DEFAULT_WINDOW,
            featureStepSizeMs = file?.micro?.featureStepSize ?: DEFAULT_STEP_MS,
        )
    }

    private val MANIFEST_JSON = Json { ignoreUnknownKeys = true }
}

/** What one bundled model tells the detector to use. */
data class WakeWordManifest(
    val id: String,
    val phrase: String,
    val probabilityCutoff: Float,
    val slidingWindowSize: Int,
    val featureStepSizeMs: Int,
)

@Serializable
private data class WakeWordFile(
    @SerialName("wake_word") val wakeWord: String = "",
    val micro: WakeWordMicro = WakeWordMicro(),
)

@Serializable
private data class WakeWordMicro(
    @SerialName("probability_cutoff") val probabilityCutoff: Float = DEFAULT_CUTOFF,
    @SerialName("sliding_window_size") val slidingWindowSize: Int = DEFAULT_WINDOW,
    @SerialName("feature_step_size") val featureStepSize: Int = DEFAULT_STEP_MS,
)

/** What the tablet is doing with Assist. The header mic and the Assist page both read this. */
object AssistPhases {
    const val LISTENING = "listening"
    const val HEARING = "hearing"
    const val THINKING = "thinking"
    const val SPEAKING = "speaking"
    const val MUTED = "muted"
    const val UNAVAILABLE = "unavailable"
}

/**
 * Control messages on [Protocol.ASSIST_PATH]. Spoken audio is a binary frame, not one of these.
 * The socket opens for one command, including a follow-up, then closes.
 */
object AssistWire {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encodeUplink(message: AssistUplink): String = json.encodeToString(message)

    fun decodeUplink(text: String): AssistUplink = json.decodeFromString(text)

    fun encodeDownlink(message: AssistDownlink): String = json.encodeToString(message)

    fun decodeDownlink(text: String): AssistDownlink = json.decodeFromString(text)
}

/** Tablet to add-on on the Assist socket. */
@Serializable
sealed interface AssistUplink {
    @Serializable
    @SerialName("hello")
    data class Hello(
        val token: String,
        val pipelineId: String? = null,
        val wakeWord: String = AssistWakeWords.OKAY_NABU,
    ) : AssistUplink

    /** Playback of the reply finished, so a follow-up may start speech-to-text again. */
    @Serializable
    @SerialName("ready")
    data object Ready : AssistUplink
}

/** Add-on to tablet on the Assist socket. */
@Serializable
sealed interface AssistDownlink {
    @Serializable
    @SerialName("phase")
    data class Phase(val phase: String) : AssistDownlink

    @Serializable
    @SerialName("transcript")
    data class Transcript(val text: String) : AssistDownlink

    @Serializable
    @SerialName("reply")
    data class Reply(val text: String, val followUp: Boolean = false) : AssistDownlink

    /** The next binary frame is the spoken reply. */
    @Serializable
    @SerialName("speech")
    data class Speech(val mimeType: String) : AssistDownlink

    @Serializable
    @SerialName("ended")
    data object Ended : AssistDownlink

    @Serializable
    @SerialName("failed")
    data class Failed(val message: String) : AssistDownlink
}
