package tech.shaffersoftworks.remotescreens.player.data.device.assist

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.AssistWakeWords

class AssistCoordinatorTest {
    @Test
    fun audioStaysOnTheTabletUntilTheWakeWordAndTheMicClosesForTheReply() = runTest {
        val microphone = ScriptedMicrophone()
        val detector = HitOnceDetector(phrase = "Okay Nabu")
        val client = RecordingTurnClient()
        val playback = RecordingPlayback(microphone)
        val foreground = RecordingForeground()

        val coordinator = AssistCoordinator(
            scope = this,
            microphone = microphone,
            detectors = WakeWordDetectors { detector },
            turns = client,
            playback = playback,
            foreground = foreground,
        )

        coordinator.apply(
            config = AssistSessionConfig(
                isEnabled = true,
                wakeWordId = AssistWakeWords.OKAY_NABU,
                server = "10.0.0.5:8100",
                token = "token",
            ),
            onWake = {},
        )

        testScheduler.advanceUntilIdle()

        assertTrue("audio is sent only after the detector reports a hit", client.received.isNotEmpty())
        assertTrue(detector.callsBeforeHit >= 2)
        assertTrue(playback.played)
        assertTrue(playback.micWasClosed)
        assertEquals(AssistPhases.LISTENING, coordinator.state.value.phase)
        assertEquals("The kitchen lights are off", coordinator.state.value.turns.last().text)
        coordinator.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun muteAndAnIntercomCallReleaseTheMicrophone() = runTest {
        val microphone = ScriptedMicrophone()

        val coordinator = AssistCoordinator(
            scope = this,
            microphone = microphone,
            detectors = WakeWordDetectors { HitOnceDetector(phrase = "Okay Nabu", hits = false) },
            turns = RecordingTurnClient(),
            playback = RecordingPlayback(microphone),
            foreground = RecordingForeground(),
        )

        val listening = AssistSessionConfig(
            isEnabled = true,
            server = "10.0.0.5:8100",
            token = "token",
        )

        coordinator.apply(config = listening, onWake = {})
        testScheduler.advanceUntilIdle()
        assertTrue(microphone.openCount >= 1)

        coordinator.toggleMute()
        testScheduler.advanceUntilIdle()
        assertEquals(AssistPhases.MUTED, coordinator.state.value.phase)
        assertFalse(microphone.isOpen)

        coordinator.toggleMute()
        coordinator.apply(config = listening.copy(isPaused = true), onWake = {})
        testScheduler.advanceUntilIdle()
        assertFalse(microphone.isOpen)
        coordinator.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun theDetectorIsBuiltFromTheManifestCutoff() {
        val seen = mutableListOf<String>()

        val detectors = WakeWordDetectors { id ->
            seen += id
            HitOnceDetector(phrase = id)
        }

        detectors.open(AssistWakeWords.HEY_JARVIS)

        assertEquals(listOf(AssistWakeWords.HEY_JARVIS), seen)
    }
}

private class HitOnceDetector(override val phrase: String, private val hits: Boolean = true) : WakeWordDetector {
    override val isReady: Boolean = true
    var callsBeforeHit: Int = 0
    private var seen = 0

    override fun processAudio(samples: ShortArray): Boolean {
        seen += 1

        if (!hits || seen < HITS_ON_CALL) {
            callsBeforeHit = seen

            return false
        }

        callsBeforeHit = seen - 1

        return seen == HITS_ON_CALL
    }

    override fun reset() = Unit
    override fun close() = Unit

    private companion object {
        const val HITS_ON_CALL = 3
    }
}

private class ScriptedMicrophone : Microphone {
    override val hasPermission: Boolean = true
    var openCount: Int = 0
    var isOpen: Boolean = false

    override fun open(): MicrophoneSession {
        openCount += 1
        isOpen = true
        val samples = Channel<ShortArray>(capacity = Channel.UNLIMITED)
        repeat(CHUNK_COUNT) { index -> samples.trySend(shortArrayOf(index.toShort())) }

        return object : MicrophoneSession {
            override val samples: ReceiveChannel<ShortArray> = samples

            override fun close() {
                isOpen = false
                samples.close()
            }
        }
    }

    private companion object {
        const val CHUNK_COUNT = 6
    }
}

private class RecordingTurnClient : AssistTurnClient {
    val received = mutableListOf<ByteArray>()

    override suspend fun run(
        request: AssistTurnRequest,
        pcm: ReceiveChannel<ByteArray>,
        events: AssistTurnEvents,
    ) {
        events.phase(AssistPhases.HEARING)
        received += pcm.receive()
        events.phase(AssistPhases.THINKING)

        while (true) {
            received += pcm.receiveCatching().getOrNull() ?: break
        }

        events.transcript("Turn off the kitchen lights")
        events.reply(text = "The kitchen lights are off", followUp = false)
        events.speech(mimeType = "audio/mpeg", audio = byteArrayOf(1))
        events.ended()
    }
}

private class RecordingPlayback(private val microphone: ScriptedMicrophone) : SpeechPlayback {
    var played: Boolean = false
    var micWasClosed: Boolean = false

    override suspend fun play(mimeType: String, audio: ByteArray) {
        micWasClosed = !microphone.isOpen
        played = true
    }
}

private class RecordingForeground : AssistForeground {
    override fun setListeningWhileScreenOff(isEnabled: Boolean) = Unit
}
