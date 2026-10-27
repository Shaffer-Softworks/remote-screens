package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class IntercomSessionTest {
    @Test
    fun aPingIsAnsweredAndThisTabletIsLeftOffTheRoster() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        harness.link.inbound.send("""{"type":"ping","ts":5}""")
        harness.link.inbound.send(ROSTER)
        runCurrent()

        assertTrue(harness.link.sent.any { it.contains(""""type":"pong"""") && it.contains(""""ts":5""") })
        val report = harness.reports.last()
        assertEquals(listOf("station-kitchen"), report.stations.map { it.clientId })
        assertEquals("Kitchen", report.stations.single().displayName)
        assertEquals("Online", report.status)
        harness.stop()
    }

    @Test
    fun anIncomingOfferWakesTheScreenAndAnswers() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        harness.link.inbound.send("""{"type":"offer","sdp":"their-offer","from":"station-kitchen"}""")
        runCurrent()

        assertEquals(1, harness.wakes)
        assertTrue(harness.audio.isStarted)
        assertTrue(harness.link.sent.any { it.contains(""""type":"answer"""") && it.contains("answer-sdp") })
        assertTrue(harness.link.sent.any { it.contains(HOST_CANDIDATE) })
        assertEquals("Incoming from Kitchen", harness.reports.last().status)
        assertTrue(harness.reports.last().busy)
        harness.audio.connect()
        runCurrent()
        assertEquals("Connected to Kitchen", harness.reports.last().status)
        assertEquals("station-kitchen", harness.reports.last().connectedId)
        harness.stop()
    }

    @Test
    fun theLowerClientIdKeepsItsOfferWhenBothCall() = runTest {
        val harness = harness(clientId = "station-aaa")
        harness.start()
        harness.registered()
        harness.session.command("call", "station-kitchen")
        runCurrent()
        harness.link.inbound.send("""{"type":"offer","sdp":"their-offer","from":"station-bbb"}""")
        runCurrent()

        assertFalse(harness.link.sent.any { it.contains(""""type":"answer"""") })
        assertTrue(harness.link.sent.any { it.contains(""""type":"offer"""") })
        assertTrue(harness.link.sent.any { it.contains(HOST_CANDIDATE) })
        harness.stop()
    }

    @Test
    fun aHigherClientIdRollsBackAndAnswers() = runTest {
        val harness = harness(clientId = "station-bbb")
        harness.start()
        harness.registered()
        harness.session.command("call", "station-aaa")
        runCurrent()
        harness.link.inbound.send("""{"type":"offer","sdp":"their-offer","from":"station-aaa"}""")
        runCurrent()

        assertTrue(harness.link.sent.any { it.contains("rollback-answer") })
        assertEquals("Incoming from station-aaa", harness.reports.last().status)
        harness.stop()
    }

    @Test
    fun aBusyStationHangsUpSoTheRosterDoesNotStick() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        harness.session.command("call", "station-kitchen")
        runCurrent()
        harness.link.inbound.send(
            """{"type":"unavailable","targetId":"station-kitchen","reason":"busy"}""",
        )
        runCurrent()

        assertTrue(harness.link.sent.any { it.contains(""""type":"hangup"""") })
        assertEquals("Kitchen is busy", harness.reports.last().status)
        assertFalse(harness.reports.last().busy)
        harness.stop()
    }

    @Test
    fun hangingUpStaysOnCallEndedWhenThePeerConnectionCloses() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        harness.session.command("call", "station-kitchen")
        runCurrent()
        harness.audio.connect()
        runCurrent()
        harness.session.command("hangup", "")
        runCurrent()

        assertEquals("Call ended", harness.reports.last().status)
        assertFalse(harness.reports.last().busy)
        assertTrue(harness.link.sent.any { it.contains(""""type":"hangup"""") })
        harness.audio.fail()
        runCurrent()
        assertEquals("Call ended", harness.reports.last().status)
        harness.stop()
    }

    @Test
    fun aCallThatNobodyAnswersHangsUp() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        harness.session.command("call", "station-kitchen")
        runCurrent()
        testScheduler.advanceTimeBy(CALL_TIMEOUT_MS.milliseconds)
        runCurrent()

        assertEquals("No answer", harness.reports.last().status)
        assertTrue(harness.link.sent.any { it.contains(""""type":"hangup"""") })
        harness.stop()
    }

    @Test
    fun aRebuiltScreenGetsTheRosterAgain() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        runCurrent()
        val reportsBefore = harness.reports.size

        harness.session.publishAgain()
        runCurrent()

        assertTrue(harness.reports.size > reportsBefore)
        val report = harness.reports.last()
        assertEquals("Online", report.status)
        assertEquals(listOf("station-kitchen"), report.stations.map { it.clientId })
        harness.stop()
    }

    @Test
    fun beingReplacedRegistersAgain() = runTest {
        val harness = harness()
        harness.start()
        harness.registered()
        harness.link.inbound.send("""{"type":"replaced"}""")
        runCurrent()
        testScheduler.advanceTimeBy(RECONNECT_MS.milliseconds)
        runCurrent()

        assertEquals(2, harness.link.opened)
        assertEquals(2, harness.link.sent.count { it.contains(""""type":"register"""") })
        harness.stop()
    }

    private fun TestScope.harness(clientId: String = "station-bbb"): Harness {
        val link = FakeLink()
        val audio = FakeAudio()
        val reports = mutableListOf<PlayerMessage.IntercomReport>()
        var wakes = 0
        val session = IntercomSession(
            link,
            audio,
            this,
            IntercomRegistration("ws://10.0.0.5:8766/webrtc", clientId, "Living Room"),
            onReport = { reports += it },
            onWake = { wakes += 1 },
        )
        return Harness(link, audio, session, reports, { wakes })
    }

    private class Harness(
        val link: FakeLink,
        val audio: FakeAudio,
        val session: IntercomSession,
        val reports: List<PlayerMessage.IntercomReport>,
        private val wakeCount: () -> Int,
    ) {
        val wakes: Int get() = wakeCount()

        fun start() = session.start()

        fun stop() = session.stop()

        suspend fun registered() {
            link.inbound.send("""{"type":"registered","clientId":"station-bbb"}""")
            link.inbound.send(ROSTER)
        }
    }

    private class FakeLink : SignalingLink {
        val sent = mutableListOf<String>()
        val inbound = Channel<String>(Channel.UNLIMITED)
        var opened = 0

        override suspend fun conversation(url: String, block: suspend SignalingConversation.() -> Unit) {
            opened += 1
            val conversation = object : SignalingConversation {
                override suspend fun send(text: String) {
                    sent += text
                }

                override suspend fun receive(): String? = inbound.receive()
            }
            conversation.block()
        }
    }

    private class FakeAudio : WebRtcAudio {
        var isStarted = false
        private var listener: AudioListener = IdleAudioListener

        override fun start(listener: AudioListener) {
            isStarted = true
            this.listener = listener
        }

        override suspend fun createOffer(): String {
            listener.onCandidate(HOST_CANDIDATE, "0", 0)
            return "offer-sdp"
        }

        override suspend fun acceptOffer(sdp: String): String {
            listener.onCandidate(HOST_CANDIDATE, "0", 0)
            return "answer-sdp"
        }

        override suspend fun acceptAnswer(sdp: String) = Unit

        override suspend fun rollbackAndAnswer(offer: String): String = "rollback-answer"

        override fun addCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) = Unit

        override fun setMuted(isMuted: Boolean) = Unit

        override fun stop() {
            if (!isStarted) return
            isStarted = false
            listener.onFailed()
        }

        fun connect() = listener.onConnected()

        fun fail() = listener.onFailed()
    }

    private companion object {
        const val CALL_TIMEOUT_MS = 30_000L
        const val RECONNECT_MS = 2_000L
        const val HOST_CANDIDATE = "candidate:1 1 udp 1 10.0.0.8 9 typ host"
        const val ROSTER = """{"type":"clientsList","clients":[
            {"clientId":"station-bbb","displayName":"Living Room","inCall":false},
            {"clientId":"station-kitchen","displayName":"Kitchen","inCall":true}
            ]}"""
    }
}
