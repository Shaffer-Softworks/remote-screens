package tech.shaffersoftworks.remotescreens.server.assist

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ListAssistPipelines
import tech.shaffersoftworks.remotescreens.shared.AssistPhases

/** Lists pipelines and runs one conversation. Demo never opens a Home Assistant socket. */
interface AssistRuns {
    suspend fun listPipelines(): AssistPipelineCatalog
    suspend fun converse(pipelineId: String?, wakeWordId: String, link: AssistPlayerLink)
}

class HomeAssistRuns(
    private val homeAssistant: HomeAssistant,
    private val sockets: KtorAssistSockets,
) : AssistRuns {
    override suspend fun listPipelines(): AssistPipelineCatalog =
        runCatching { homeAssistant.send<AssistPipelineCatalog>(ListAssistPipelines) }
            .getOrDefault(AssistPipelineCatalog())

    override suspend fun converse(pipelineId: String?, wakeWordId: String, link: AssistPlayerLink) {
        AssistConversation(
            session = sockets.session(),
            fetchSpeech = { path -> homeAssistant.fetch(path) },
        ).talk(link = link, pipelineId = pipelineId, wakeWordId = wakeWordId)
    }
}

/** Sample conversation for the editor and for a tablet whose add-on is in demo mode. The microphone stays shut. */
class DemoAssistRuns : AssistRuns {
    override suspend fun listPipelines(): AssistPipelineCatalog = AssistPipelineCatalog(
        pipelines = listOf(AssistPipelineInfo(id = "demo", name = "Preferred")),
        preferredPipeline = "demo",
    )

    override suspend fun converse(pipelineId: String?, wakeWordId: String, link: AssistPlayerLink) {
        link.phase(AssistPhases.HEARING)
        link.openAudio()
        link.stopAudio()
        link.phase(AssistPhases.THINKING)
        link.transcript(SAMPLE_HEARD)
        link.reply(text = SAMPLE_REPLY, followUp = false)
        link.phase(AssistPhases.SPEAKING)
        link.speech(mimeType = "audio/mpeg", audio = ByteArray(0))
        link.ended()
    }

    private companion object {
        const val SAMPLE_HEARD = "Turn off the kitchen lights"
        const val SAMPLE_REPLY = "The kitchen lights are off"
    }
}
