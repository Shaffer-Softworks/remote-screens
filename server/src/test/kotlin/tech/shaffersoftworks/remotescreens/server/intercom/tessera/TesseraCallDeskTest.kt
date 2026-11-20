package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.shaffersoftworks.remotescreens.shared.SignalingClient
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TesseraCallDeskTest {
    private val desk = TesseraCallDesk()

    @Test
    fun peersAreTheStationsThatAreNotTesseraScreens() {
        val result = desk.onRoster(
            listOf(
                SignalingClient(clientId = "station-tablet", displayName = "Kitchen tablet"),
                SignalingClient(clientId = "tessera-kitchen", displayName = "Kitchen"),
                SignalingClient(clientId = "ha-user", displayName = "Michael"),
            ),
        )

        val peers = result.fires.first { fire -> fire.eventType == TesseraProtocol.PEERS_EVENT }
        val nodes = peers.data["peers"]!!.jsonArray.map { row -> row.jsonObject["node"]!!.jsonPrimitive.content }

        assertEquals(listOf("ha-user", "station-tablet"), nodes)
        assertEquals(TesseraProtocol.SCREEN_CALL_EVENT, result.fires.last().eventType)
        assertEquals("list", result.fires.last().data["action"]!!.jsonPrimitive.content)
        assertEquals("ha-user", result.fires.last().data["screen"]!!.jsonPrimitive.content)
    }

    @Test
    fun aTabletOfferDialsTheScreenWithSoundOnly() {
        val dial = desk.onSignaling(
            stationId = "tessera-kitchen",
            frame = SignalingFrame.Offer(sdp = "v=0", to = "tessera-kitchen", from = "station-tablet"),
        ).fires.single()

        assertEquals(TesseraProtocol.SCREEN_CALL_EVENT, dial.eventType)
        assertEquals("station-tablet", dial.data["screen"]!!.jsonPrimitive.content)
        assertEquals("dial", dial.data["action"]!!.jsonPrimitive.content)
        assertEquals("kitchen", dial.data["to"]!!.jsonPrimitive.content)
        assertEquals("0", dial.data["video"]!!.jsonPrimitive.content)
    }

    @Test
    fun aLiveReplyStartsTheAudioBridge() {
        desk.onSignaling(
            stationId = "tessera-kitchen",
            frame = SignalingFrame.Offer(sdp = "v=offer", to = "tessera-kitchen", from = "station-tablet"),
        )

        val live = desk.onInbound(
            TesseraInbound.Call(
                node = "station-tablet",
                peer = "kitchen",
                key = "ab12",
                state = TesseraProtocol.STATE_LIVE,
                name = "Kitchen",
                listenUrl = "http://screens/listen",
                talkUrl = "http://screens/talk",
            ),
        )

        val answer = assertIs<MediaCommand.Answer>(live.media.single())

        assertEquals("v=offer", answer.offerSdp)
        assertEquals("http://screens/listen", answer.listenUrl)
        assertEquals("http://screens/talk", answer.talkUrl)
    }

    @Test
    fun anEndFromTesseraHangsUpTheTablet() {
        desk.onSignaling(
            stationId = "tessera-kitchen",
            frame = SignalingFrame.Offer(sdp = "v=offer", from = "station-tablet"),
        )

        desk.onInbound(call(state = TesseraProtocol.STATE_OUT, key = "ab12"))
        val ended = desk.onInbound(call(state = TesseraProtocol.STATE_END, key = "ab12"))

        assertTrue(ended.fires.isEmpty())
        assertIs<SignalingFrame.Hangup>(ended.frames.single().frame)
        assertEquals("station-tablet", (ended.frames.single().frame as SignalingFrame.Hangup).to)
        assertIs<MediaCommand.Stop>(ended.media.single())
    }

    @Test
    fun aRingInvitesTheNamedTablet() {
        val ring = desk.onInbound(
            call(state = TesseraProtocol.STATE_RING, key = "cd34", peer = "kitchen", name = "Kitchen"),
        )

        val invite = assertIs<MediaCommand.Invite>(ring.media.single())

        assertEquals("tessera-kitchen", invite.stationId)
        assertEquals("station-tablet", invite.tabletId)
        assertEquals("Kitchen", ring.stations!!.single().name)
    }

    @Test
    fun theTabletsAnswerIsSentToTessera() {
        desk.onInbound(call(state = TesseraProtocol.STATE_RING, key = "cd34"))

        val answered = desk.onSignaling(
            stationId = "tessera-kitchen",
            frame = SignalingFrame.Answer(sdp = "v=answer", from = "station-tablet"),
        )

        val event = answered.fires.single()

        assertEquals("answer", event.data["action"]!!.jsonPrimitive.content)
        assertEquals("cd34", event.data["key"]!!.jsonPrimitive.content)
        assertEquals("station-tablet", event.data["screen"]!!.jsonPrimitive.content)
        assertIs<MediaCommand.Accept>(answered.media.single())
    }

    @Test
    fun aBusyScreenStaysRegisteredAfterItCouldBeCalled() {
        val first = desk.onInbound(
            TesseraInbound.Contacts(
                rows = listOf(
                    TesseraContact(id = "kitchen", name = "Kitchen", canSound = true, availability = "free"),
                    TesseraContact(id = "quiet", name = "Quiet", canSound = false, availability = "quiet"),
                ),
                more = false,
            ),
        )

        val second = desk.onInbound(
            TesseraInbound.Contacts(
                rows = listOf(
                    TesseraContact(id = "kitchen", name = "Kitchen", canSound = false, availability = "busy"),
                    TesseraContact(id = "quiet", name = "Quiet", canSound = false, availability = "quiet"),
                ),
                more = false,
            ),
        )

        assertEquals(listOf("kitchen"), first.stations!!.map { station -> station.node })
        assertEquals(listOf("kitchen"), second.stations!!.map { station -> station.node })
    }

    @Test
    fun silenceIsReportedOnceTheListHasBeenAskedForTwice() {
        desk.onRoster(listOf(SignalingClient(clientId = "station-tablet", displayName = "Kitchen tablet")))
        val again = desk.onRefresh()

        assertTrue(again.warnSilence)
        assertFalse(desk.onRefresh().warnSilence)
    }

    private fun call(
        state: String,
        key: String = "",
        peer: String = "kitchen",
        name: String = "Kitchen",
    ): TesseraInbound.Call = TesseraInbound.Call(
        node = "station-tablet",
        peer = peer,
        key = key,
        state = state,
        name = name,
        listenUrl = "",
        talkUrl = "",
    )
}
