package tech.shaffersoftworks.remotescreens.player.data.device.assist

import android.util.Log

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

private const val IDLE_POLL_MS = 250
private const val MAX_TURNS = 12
private const val BYTES_PER_SAMPLE = 2
private const val BITS_PER_BYTE = 8
private const val SPEAKER_YOU = "You"
private const val SPEAKER_ASSIST = "Assist"
private const val LOG_TAG = "AssistCoordinator"

/**
 * Listens on the tablet until the bundled wake word, then opens one Assist turn.
 * The microphone stays closed for the whole of the spoken reply.
 */
@Singleton
class AssistCoordinator @Inject constructor(
    @IoScope private val scope: CoroutineScope,
    private val microphone: Microphone,
    private val detectors: WakeWordDetectors,
    private val turns: AssistTurnClient,
    private val playback: SpeechPlayback,
    private val foreground: AssistForeground,
) {
    private val _state = MutableStateFlow(AssistUiState())
    val state: StateFlow<AssistUiState> = _state.asStateFlow()

    private var sessionConfig = AssistSessionConfig()
    private var onWake: () -> Unit = {}
    private var isMuted = false
    private var listenJob: Job? = null

    fun apply(config: AssistSessionConfig, onWake: () -> Unit) {
        val previous = sessionConfig
        sessionConfig = config
        this.onWake = onWake
        refreshForeground()

        val changed = previous.copy(isScreenOff = config.isScreenOff) != config

        if (changed || listenJob?.isActive != true) {
            listenJob?.cancel()
            listenJob = scope.launch { listen() }
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        _state.update { it.copy(isMuted = isMuted, phase = if (isMuted) AssistPhases.MUTED else it.phase) }
        refreshForeground()
        listenJob?.cancel()
        listenJob = scope.launch { listen() }
    }

    fun stop() {
        listenJob?.cancel()
        foreground.setListeningWhileScreenOff(false)
    }

    private fun refreshForeground() {
        foreground.setListeningWhileScreenOff(
            isEnabled = sessionConfig.isEnabled && sessionConfig.isScreenOff && !isMuted && !sessionConfig.isPaused,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun listen() {
        while (scope.isActive) {
            val config = sessionConfig

            if (!canListen(config)) {
                _state.update {
                    it.copy(
                        phase = when {
                            isMuted -> AssistPhases.MUTED
                            config.isEnabled -> AssistPhases.LISTENING
                            else -> AssistPhases.UNAVAILABLE
                        },
                        isMuted = isMuted,
                        caption = null,
                    )
                }

                awaitCancellation()
            }

            val detector = detectors.open(config.wakeWordId)

            if (!detector.isReady) {
                detector.close()
                _state.update { it.copy(phase = AssistPhases.UNAVAILABLE, wakeWord = detector.phrase) }
                delay(IDLE_POLL_MS.milliseconds)
                continue
            }

            _state.update { it.copy(phase = AssistPhases.LISTENING, wakeWord = detector.phrase, isMuted = false) }
            val session = microphone.open()

            try {
                detectAndTalk(session = session, detector = detector, config = config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(LOG_TAG, "Assist listening stopped", error)
                _state.update { it.copy(phase = AssistPhases.LISTENING, caption = null) }
            } finally {
                session.close()
                detector.close()
            }
        }
    }

    private suspend fun detectAndTalk(
        session: MicrophoneSession,
        detector: WakeWordDetector,
        config: AssistSessionConfig,
    ) {
        for (samples in session.samples) {
            if (!canListen(sessionConfig)) return

            if (!detector.processAudio(samples)) continue

            detector.reset()
            onWake()
            runTurn(session = session, config = config)

            return
        }
    }

    private suspend fun runTurn(session: MicrophoneSession, config: AssistSessionConfig) {
        val pcm = Channel<ByteArray>(capacity = Channel.UNLIMITED)
        var heard: String? = null
        var spoken: String? = null
        var activeSession: MicrophoneSession = session
        var followUpAudio: Channel<ByteArray>? = null

        coroutineScope {
            val pump = launch {
                for (samples in session.samples) {
                    if (pcm.trySend(pcmBytes(samples)).isClosed) break
                }
            }

            val events = object : AssistTurnEvents {
                override suspend fun phase(phase: String) {
                    _state.update { it.copy(phase = phase) }

                    if (phase == AssistPhases.THINKING || phase == AssistPhases.SPEAKING) {
                        pcm.close()
                        followUpAudio?.close()
                        activeSession.close()
                    }
                }

                override suspend fun transcript(text: String) {
                    heard = text
                    _state.update { it.copy(caption = text, phase = AssistPhases.HEARING) }
                }

                override suspend fun reply(text: String, followUp: Boolean) {
                    spoken = text
                    _state.update { it.copy(caption = text) }
                }

                override suspend fun speech(mimeType: String, audio: ByteArray) {
                    pcm.close()
                    followUpAudio?.close()
                    activeSession.close()
                    _state.update { it.copy(phase = AssistPhases.SPEAKING, caption = spoken) }
                    playback.play(mimeType = mimeType, audio = audio)
                    commit(heard = heard, spoken = spoken)
                    heard = null
                    spoken = null
                }

                override suspend fun ended() {
                    _state.update { it.copy(phase = AssistPhases.LISTENING, caption = null) }
                }

                override suspend fun failed(message: String) {
                    _state.update { it.copy(phase = AssistPhases.LISTENING, caption = null) }
                }

                override suspend fun followUpAudio(): ReceiveChannel<ByteArray> {
                    val next = microphone.open()
                    val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
                    activeSession = next
                    followUpAudio = channel

                    launch {
                        try {
                            for (samples in next.samples) {
                                if (channel.trySend(pcmBytes(samples)).isClosed) break
                            }
                        } finally {
                            next.close()
                            channel.close()
                        }
                    }

                    return channel
                }
            }

            try {
                turns.run(
                    request = AssistTurnRequest(
                        server = config.server.orEmpty(),
                        token = config.token.orEmpty(),
                        pipelineId = config.pipelineId,
                        wakeWord = config.wakeWordId,
                    ),
                    pcm = pcm,
                    events = events,
                )
            } finally {
                pcm.close()
                followUpAudio?.close()
                pump.cancel()
            }
        }
    }

    private fun commit(heard: String?, spoken: String?) {
        _state.update { current ->
            val added = buildList {
                addAll(current.turns)
                heard?.let { add(AssistTurn(speaker = SPEAKER_YOU, text = it)) }
                spoken?.let { add(AssistTurn(speaker = SPEAKER_ASSIST, text = it)) }
            }

            current.copy(turns = added.takeLast(MAX_TURNS), caption = null)
        }
    }

    private fun canListen(config: AssistSessionConfig): Boolean =
        config.isEnabled &&
            !isMuted &&
            !config.isPaused &&
            microphone.hasPermission &&
            !config.server.isNullOrBlank() &&
            !config.token.isNullOrBlank()
}

internal fun pcmBytes(samples: ShortArray): ByteArray {
    val bytes = ByteArray(samples.size * BYTES_PER_SAMPLE)
    var index = 0

    for (sample in samples) {
        bytes[index] = sample.toByte()
        bytes[index + 1] = (sample.toInt() shr BITS_PER_BYTE).toByte()
        index += BYTES_PER_SAMPLE
    }

    return bytes
}
