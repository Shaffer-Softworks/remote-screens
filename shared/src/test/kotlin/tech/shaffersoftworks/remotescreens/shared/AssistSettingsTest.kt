package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistSettingsTest {
    @Test
    fun assistIsOffUntilAScreenTurnsItOn() {
        val settings = ScreenSettings()

        assertFalse(settings.assist)
        assertEquals(AssistWakeWords.OKAY_NABU, settings.assistWakeWord)
        assertNull(settings.assistPipeline)
    }

    @Test
    fun anUnknownWakeWordBecomesOkayNabu() {
        val settings = ScreenSettings(assist = true, assistWakeWord = "hey_house", assistPipeline = "  ")

        val validated = settings.validated()

        assertTrue(validated.assist)
        assertEquals(AssistWakeWords.OKAY_NABU, validated.assistWakeWord)
        assertNull(validated.assistPipeline)
    }

    @Test
    fun theManifestCutoffAndWindowAreWhatTheDetectorIsBuiltWith() {
        val manifest = AssistWakeWords.manifest(
            id = "okay_nabu",
            json = """
                {"wake_word":"Okay Nabu","micro":{"probability_cutoff":0.97,"sliding_window_size":5,"feature_step_size":10}}
            """.trimIndent(),
        )

        assertEquals(0.97f, manifest.probabilityCutoff)
        assertEquals(5, manifest.slidingWindowSize)
        assertEquals(10, manifest.featureStepSizeMs)
        assertEquals("Okay Nabu", manifest.phrase)
    }

    @Test
    fun anAssistPageRoundTrips() {
        val page = ServerMessage.AssistPage(
            slot = Slot.MAIN,
            rev = 3,
            page = 1,
            tileId = "as1",
            componentId = 0x62_0000,
            wakeWord = "Okay Nabu",
            pipelineLabel = "Preferred",
        )

        val decoded = Protocol.decodeServer(Protocol.encode(page))

        assertEquals(page, decoded)
    }

    @Test
    fun assistSocketMessagesRoundTrip() {
        val hello = AssistUplink.Hello(token = "secret", pipelineId = null, wakeWord = AssistWakeWords.HEY_JARVIS)
        val reply = AssistDownlink.Reply(text = "The kitchen lights are off", followUp = true)

        assertEquals(hello, AssistWire.decodeUplink(AssistWire.encodeUplink(hello)))
        assertEquals(reply, AssistWire.decodeDownlink(AssistWire.encodeDownlink(reply)))
    }
}
