package tech.shaffersoftworks.remotescreens.server.assist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HA_JSON

const val ASSIST_SAMPLE_RATE = 16_000
const val ASSIST_START_STAGE = "stt"
const val ASSIST_END_STAGE = "tts"

/** One Assist pipeline the editor can offer. Blank on the screen means Home Assistant's preferred one. */
@Serializable
data class AssistPipelineInfo(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class AssistPipelineCatalog(
    val pipelines: List<AssistPipelineInfo> = emptyList(),
    @SerialName("preferred_pipeline") val preferredPipeline: String? = null,
)

/** What a pipeline run tells the tablet bridge. Binary audio is not one of these. */
sealed interface PipelineNotice {
    data class Handler(val id: Int) : PipelineNotice
    data object Hearing : PipelineNotice
    data object StopAudio : PipelineNotice
    data class Transcript(val text: String) : PipelineNotice
    data class Reply(val text: String, val conversationId: String?, val followUp: Boolean) : PipelineNotice
    data class Speech(val path: String, val mimeType: String) : PipelineNotice
    data class Failed(val message: String) : PipelineNotice
    data object Finished : PipelineNotice
    data object Ignored : PipelineNotice
}

/**
 * Reads one Home Assistant websocket message from an `assist_pipeline/run` subscription.
 * The handler id arrives on `run-start`, not in the empty result that only confirms the subscription.
 */
fun parsePipelineMessage(text: String): PipelineNotice {
    val root = runCatching { HA_JSON.parseToJsonElement(text).jsonObjectOrNull() }.getOrNull()

    return if (root == null) PipelineNotice.Ignored else noticeFor(root)
}

private fun noticeFor(root: JsonObject): PipelineNotice {
    val type = root.string("type")
    val isFailedResult = root["success"]?.jsonPrimitive?.contentOrNull == "false"

    return when {
        type == null -> PipelineNotice.Ignored
        type == "result" && isFailedResult -> PipelineNotice.Failed(root.errorMessage())
        type != "event" -> PipelineNotice.Ignored
        else -> eventNotice(root)
    }
}

private fun eventNotice(root: JsonObject): PipelineNotice {
    val event = root["event"].jsonObjectOrNull()

    return if (event == null) PipelineNotice.Ignored else typedFrom(event)
}

private fun typedFrom(event: JsonObject): PipelineNotice {
    val eventType = event.string("type")
    val data = event["data"].jsonObjectOrNull() ?: JsonObject(emptyMap())

    return if (eventType == null) PipelineNotice.Ignored else typedNotice(eventType = eventType, data = data)
}

private fun typedNotice(eventType: String, data: JsonObject): PipelineNotice = when (eventType) {
    "run-start" -> handlerNotice(data)
    "stt-start", "stt-vad-start" -> PipelineNotice.Hearing
    "stt-vad-end" -> PipelineNotice.StopAudio
    "stt-end" -> PipelineNotice.Transcript(data.obj("stt_output").string("text").orEmpty())
    "intent-end" -> intentNotice(data)
    "tts-end" -> ttsNotice(data)
    "error" -> PipelineNotice.Failed(errorText(data))
    "run-end" -> PipelineNotice.Finished
    else -> PipelineNotice.Ignored
}

private fun handlerNotice(data: JsonObject): PipelineNotice =
    data.obj("runner_data").int("stt_binary_handler_id")?.let(PipelineNotice::Handler)
        ?: PipelineNotice.Ignored

private fun errorText(data: JsonObject): String = data.string("message") ?: data.string("code") ?: "Assist failed"

private fun intentNotice(data: JsonObject): PipelineNotice {
    val output = data.obj("intent_output")
    val speech = output.obj("response").obj("speech").obj("plain").string("speech").orEmpty()

    return PipelineNotice.Reply(
        text = speech,
        conversationId = output.string("conversation_id"),
        followUp = output["continue_conversation"]?.jsonPrimitive?.contentOrNull == "true",
    )
}

private fun ttsNotice(data: JsonObject): PipelineNotice {
    val output = data.obj("tts_output")

    return PipelineNotice.Speech(
        path = output.string("url").orEmpty(),
        mimeType = output.string("mime_type") ?: "audio/mpeg",
    )
}

private fun JsonObject.errorMessage(): String =
    this["error"].jsonObjectOrNull()?.string("message") ?: "Assist pipeline failed"

/** Home Assistant sends JSON null for an absent object. [JsonElement.jsonObject] throws on that. */
private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject.obj(name: String): JsonObject = this[name].jsonObjectOrNull() ?: JsonObject(emptyMap())

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
