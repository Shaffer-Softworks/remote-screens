package tech.shaffersoftworks.remotescreens.server.assist

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.AssistWakeWords
import kotlin.time.Duration.Companion.seconds

private val READY_TIMEOUT = 20.seconds

/** The tablet side of one Assist socket. Audio is PCM until [stopAudio]; speech bytes are the reply. */
interface AssistPlayerLink {
    suspend fun phase(phase: String)
    suspend fun transcript(text: String)
    suspend fun reply(text: String, followUp: Boolean)
    suspend fun speech(mimeType: String, audio: ByteArray)
    suspend fun ended()
    suspend fun failed(message: String)
    fun openAudio(): ReceiveChannel<ByteArray>
    fun stopAudio()

    /** Suspends until the tablet has finished playing the reply and is ready for a follow-up. */
    suspend fun awaitReady(): Boolean
}

/**
 * One command, then at most one follow-up. A follow-up starts again at speech-to-text with the same
 * conversation. After the socket ends, the tablet listens locally. This does not start a wake-word run.
 */
class AssistConversation(
    private val session: AssistPipelineSession,
    private val fetchSpeech: suspend (String) -> ByteArray?,
) {
    suspend fun talk(link: AssistPlayerLink, pipelineId: String?, wakeWordId: String) {
        val turn = TurnState()
        var isFirst = true
        var isContinuing = true

        while (isContinuing) {
            if (isFirst || awaitReady(link)) {
                isContinuing = speak(
                    link = link,
                    pipelineId = pipelineId,
                    wakeWordId = wakeWordId,
                    isFirst = isFirst,
                    turn = turn,
                )

                isFirst = false
            } else {
                isContinuing = false
            }
        }

        link.ended()
    }

    private suspend fun speak(
        link: AssistPlayerLink,
        pipelineId: String?,
        wakeWordId: String,
        isFirst: Boolean,
        turn: TurnState,
    ): Boolean {
        val phrase = if (isFirst) AssistWakeWords.phrase(wakeWordId) else null
        turn.isFollowUp = false
        turn.hasFailed = false
        link.phase(AssistPhases.HEARING)

        session.execute(
            pipelineId = pipelineId,
            conversationId = turn.conversationId,
            wakeWordPhrase = phrase,
            audio = link.openAudio(),
        ) { notice ->
            turn.apply(notice = notice, link = link, fetchSpeech = fetchSpeech)
        }

        return turn.isFollowUp && !turn.hasFailed
    }

    private suspend fun awaitReady(link: AssistPlayerLink): Boolean =
        withTimeoutOrNull(READY_TIMEOUT) { link.awaitReady() } == true
}

private class TurnState {
    var conversationId: String? = null
    var isFollowUp: Boolean = false
    var hasFailed: Boolean = false

    suspend fun apply(
        notice: PipelineNotice,
        link: AssistPlayerLink,
        fetchSpeech: suspend (String) -> ByteArray?,
    ) {
        when (notice) {
            is PipelineNotice.Handler, PipelineNotice.Ignored, PipelineNotice.Finished -> Unit
            PipelineNotice.Hearing -> link.phase(AssistPhases.HEARING)
            PipelineNotice.StopAudio -> {
                link.stopAudio()
                link.phase(AssistPhases.THINKING)
            }
            is PipelineNotice.Transcript -> link.transcript(notice.text)
            is PipelineNotice.Reply -> rememberReply(notice = notice, link = link)
            is PipelineNotice.Speech -> playSpeech(notice = notice, link = link, fetchSpeech = fetchSpeech)
            is PipelineNotice.Failed -> {
                hasFailed = true
                link.failed(notice.message)
            }
        }
    }

    private suspend fun rememberReply(notice: PipelineNotice.Reply, link: AssistPlayerLink) {
        conversationId = notice.conversationId ?: conversationId
        isFollowUp = notice.followUp
        link.reply(text = notice.text, followUp = notice.followUp)
    }

    private suspend fun playSpeech(
        notice: PipelineNotice.Speech,
        link: AssistPlayerLink,
        fetchSpeech: suspend (String) -> ByteArray?,
    ) {
        link.phase(AssistPhases.SPEAKING)
        val bytes = fetchedSpeech(path = notice.path, fetchSpeech = fetchSpeech)
        link.speech(mimeType = notice.mimeType, audio = bytes)
    }

    private suspend fun fetchedSpeech(path: String, fetchSpeech: suspend (String) -> ByteArray?): ByteArray {
        val audio = if (path.isEmpty()) null else fetchSpeech(path)

        return audio ?: ByteArray(0)
    }
}
