package tech.shaffersoftworks.remotescreens.player.data.device.assist

import android.util.Log

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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
import kotlinx.coroutines.yield
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

private const val IDLE_POLL_MS = 250
private const val COOLDOWN_CHUNKS = 100
private const val MAX_TURNS = 12
private const val BYTES_PER_SAMPLE = 2
private const val BITS_PER_BYTE = 8
private const val SPEAKER_YOU = "You"
private const val SPEAKER_ASSIST = "Assist"
private const val LOG_TAG = "AssistCoordinator"

/**
 * Listens on one microphone until the bundled wake word, then opens one Assist turn.
 * The same warmed engine keeps listening through the reply. Saying the wake word again
 * cancels that turn and starts another. A new engine is warmed before the microphone opens.
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

    private val commandAudio = AtomicReference<Channel<ByteArray>?>(null)
    private var sessionConfig = AssistSessionConfig()
    private var onWake: () -> Unit = {}
    private var isMuted = false
    private var listenJob: Job? = null
    private var turnJob: Job? = null

    fun apply(config: AssistSessionConfig, onWake: () -> Unit) {
        val previous = sessionConfig
        sessionConfig = config
        this.onWake = onWake
        refreshForeground()

        val changed = previous.copy(isScreenOff = config.isScreenOff) != config
        val willRestart = changed || listenJob?.isActive != true

        if (willRestart) {
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

            detector.warmUp()
            _state.update { it.copy(phase = AssistPhases.LISTENING, wakeWord = detector.phrase, isMuted = false) }
            val session = microphone.open()

            try {
                coroutineScope {
                    try {
                        capture(session = session, detector = detector, scope = this)
                    } finally {
                        turnJob?.cancel()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(LOG_TAG, "Assist listening stopped", error)
                _state.update { it.copy(phase = AssistPhases.LISTENING, caption = null) }
            } finally {
                stopCommand()
                session.close()
                detector.close()
            }
        }
    }

    /** One capture feeds the wake word and, while a command is open, the pipeline. */
    private suspend fun capture(
        session: MicrophoneSession,
        detector: WakeWordDetector,
        scope: CoroutineScope,
    ) {
        var cooldownChunks = 0

        for (samples in session.samples) {
            if (!canListen(sessionConfig)) return

            commandAudio.get()?.trySend(pcmBytes(samples))
            val isCoolingDown = cooldownChunks > 0

            if (isCoolingDown) {
                cooldownChunks -= 1
            } else if (detector.processAudio(samples)) {
                detector.reset()
                cooldownChunks = COOLDOWN_CHUNKS
                beginTurn(scope = scope, config = sessionConfig)
                yield()
            }
        }
    }

    private suspend fun beginTurn(scope: CoroutineScope, config: AssistSessionConfig) {
        onWake()
        val previous = turnJob
        turnJob = null
        previous?.cancelAndJoin()
        stopCommand()

        val audio = Channel<ByteArray>(capacity = Channel.UNLIMITED)

        turnJob = scope.launch {
            try {
                playback.playListeningChime()
                commandAudio.set(audio)
                runTurn(pcm = audio, config = config)
            } finally {
                commandAudio.compareAndSet(audio, null)
                audio.close()
                dismissCaption()
            }
        }
    }

    private suspend fun runTurn(pcm: ReceiveChannel<ByteArray>, config: AssistSessionConfig) {
        var heard: String? = null
        var spoken: String? = null

        val events = object : AssistTurnEvents {
            override suspend fun phase(phase: String) {
                _state.update { it.copy(phase = phase) }

                if (phase == AssistPhases.THINKING || phase == AssistPhases.SPEAKING) stopCommand()
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
                stopCommand()
                _state.update { it.copy(phase = AssistPhases.SPEAKING, caption = spoken) }

                try {
                    playback.play(mimeType = mimeType, audio = audio)
                } finally {
                    commit(heard = heard, spoken = spoken)
                    heard = null
                    spoken = null
                }
            }

            override suspend fun ended() {
                _state.update { it.copy(phase = AssistPhases.LISTENING, caption = null) }
            }

            override suspend fun failed(message: String) {
                _state.update { it.copy(phase = AssistPhases.LISTENING, caption = null) }
            }

            override suspend fun followUpAudio(): ReceiveChannel<ByteArray> {
                val next = Channel<ByteArray>(capacity = Channel.UNLIMITED)
                commandAudio.getAndSet(next)?.close()

                return next
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
            stopCommand()
            dismissCaption()
        }
    }

    private fun stopCommand() {
        commandAudio.getAndSet(null)?.close()
    }

    /** Drops the snackbar once the turn is over, including when playback or the socket is cancelled. */
    private fun dismissCaption() {
        _state.update { current ->
            val isTurn = current.phase == AssistPhases.HEARING ||
                current.phase == AssistPhases.THINKING ||
                current.phase == AssistPhases.SPEAKING

            current.copy(
                phase = if (isTurn) AssistPhases.LISTENING else current.phase,
                caption = null,
            )
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
