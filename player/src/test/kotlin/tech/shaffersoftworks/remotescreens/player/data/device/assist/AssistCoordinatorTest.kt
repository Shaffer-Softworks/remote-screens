package tech.shaffersoftworks.remotescreens.player.data.device.assist

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.shaffersoftworks.remotescreens.shared.AssistPhases
import tech.shaffersoftworks.remotescreens.shared.AssistWakeWords

class AssistCoordinatorTest {
    @Test
    fun audioStaysOnTheTabletUntilTheWakeWordAndTheSameCaptureStaysOpen() = runTest {
        val microphone = ScriptedMicrophone()
        val detector = HitOnceDetector(phrase = "Okay Nabu")
        val client = RecordingTurnClient()
        val playback = RecordingPlayback(microphone)
        val foreground = RecordingForeground()
        var opened = 0

        val coordinator = AssistCoordinator(
            scope = this,
            microphone = microphone,
            detectors = WakeWordDetectors {
                opened += 1
                detector
            },
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
        assertEquals(1, detector.warmUps)
        assertEquals(1, opened)
        assertEquals(1, microphone.openCount)
        assertTrue(microphone.isOpen)
        assertTrue(playback.chimePlayed)
        assertTrue(playback.played)
        assertEquals(AssistPhases.LISTENING, coordinator.state.value.phase)
        assertNull(coordinator.state.value.caption)
        assertEquals("The kitchen lights are off", coordinator.state.value.turns.last().text)
        coordinator.stop()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun theSameEngineHearsTheNextWakeWord() = runTest {
        val microphone = ScriptedMicrophone()
        val detector = TwoHitDetector()
        val client = RecordingTurnClient()
        var opened = 0

        val coordinator = AssistCoordinator(
            scope = this,
            microphone = microphone,
            detectors = WakeWordDetectors {
                opened += 1
                detector
            },
            turns = client,
            playback = RecordingPlayback(microphone),
            foreground = RecordingForeground(),
        )

        coordinator.apply(
            config = AssistSessionConfig(
                isEnabled = true,
                server = "10.0.0.5:8100",
                token = "token",
            ),
            onWake = {},
        )

        testScheduler.advanceUntilIdle()
        assertEquals(1, client.runs)
        assertEquals(1, opened)

        microphone.push(EXTRA_CHUNKS)
        testScheduler.advanceUntilIdle()

        assertEquals(2, client.runs)
        assertEquals(1, opened)
        assertEquals(1, microphone.openCount)
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
    fun aSecondWakeWordCancelsTheReplyAndStartsAnotherTurn() = runTest {
        val microphone = ScriptedMicrophone()
        val detector = TwoHitDetector()
        val client = RecordingTurnClient()
        val playback = HangingPlayback()

        val coordinator = AssistCoordinator(
            scope = this,
            microphone = microphone,
            detectors = WakeWordDetectors { detector },
            turns = client,
            playback = playback,
            foreground = RecordingForeground(),
        )

        coordinator.apply(
            config = AssistSessionConfig(
                isEnabled = true,
                server = "10.0.0.5:8100",
                token = "token",
            ),
            onWake = {},
        )

        testScheduler.advanceUntilIdle()

        assertEquals(1, client.runs)
        assertEquals(1, microphone.openCount)

        microphone.push(EXTRA_CHUNKS)
        testScheduler.advanceUntilIdle()

        assertEquals(2, client.runs)
        assertEquals(2, playback.chimes)
        assertEquals(1, microphone.openCount)
        assertTrue(playback.wasCancelled)
        assertNull(coordinator.state.value.caption)
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
    var warmUps: Int = 0
    private var seen = 0

    override fun warmUp() {
        warmUps += 1
    }

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
    private var live: Channel<ShortArray>? = null

    override fun open(): MicrophoneSession {
        openCount += 1
        isOpen = true
        val samples = Channel<ShortArray>(capacity = Channel.UNLIMITED)
        repeat(CHUNK_COUNT) { index -> samples.trySend(shortArrayOf(index.toShort())) }
        live = samples

        return object : MicrophoneSession {
            override val samples: ReceiveChannel<ShortArray> = samples

            override fun close() {
                isOpen = false
                samples.close()
            }
        }
    }

    fun push(times: Int) {
        repeat(times) { live?.trySend(shortArrayOf(1)) }
    }

    private companion object {
        const val CHUNK_COUNT = 6
    }
}

private const val EXTRA_CHUNKS = 200

private class TwoHitDetector : WakeWordDetector {
    override val phrase: String = "Okay Nabu"
    override val isReady: Boolean = true
    private var seen: Int = 0
    private var hitsLeft: Int = 2

    override fun processAudio(samples: ShortArray): Boolean {
        if (hitsLeft == 0) return false

        seen += 1
        val hit = seen >= HITS_ON_CALL

        if (hit) {
            seen = 0
            hitsLeft -= 1
        }

        return hit
    }

    override fun reset() {
        seen = 0
    }

    override fun close() = Unit

    private companion object {
        const val HITS_ON_CALL = 3
    }
}

private class RecordingTurnClient : AssistTurnClient {
    val received = mutableListOf<ByteArray>()
    var runs: Int = 0

    override suspend fun run(
        request: AssistTurnRequest,
        pcm: ReceiveChannel<ByteArray>,
        events: AssistTurnEvents,
    ) {
        runs += 1
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
    var chimePlayed: Boolean = false

    override suspend fun playListeningChime() {
        chimePlayed = true
        check(microphone.isOpen)
    }

    override suspend fun play(mimeType: String, audio: ByteArray) {
        check(microphone.isOpen)
        played = true
    }
}

private class HangingPlayback : SpeechPlayback {
    var chimes: Int = 0
    var wasCancelled: Boolean = false
    private var plays: Int = 0

    override suspend fun playListeningChime() {
        chimes += 1
    }

    override suspend fun play(mimeType: String, audio: ByteArray) {
        plays += 1

        if (plays > 1) return

        try {
            awaitCancellation()
        } finally {
            wasCancelled = true
        }
    }
}

private class RecordingForeground : AssistForeground {
    override fun setListeningWhileScreenOff(isEnabled: Boolean) = Unit
}
