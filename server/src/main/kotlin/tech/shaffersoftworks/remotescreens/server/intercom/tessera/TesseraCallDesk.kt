package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import kotlinx.serialization.json.JsonObject
import tech.shaffersoftworks.remotescreens.shared.SignalingClient
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame

/** What the gateway should do after one roster, signaling, or Tessera message. */
internal data class DeskResult(
    val fires: List<TesseraFire> = emptyList(),
    val frames: List<TesseraFrame> = emptyList(),
    val stations: List<TesseraStation>? = null,
    val media: List<MediaCommand> = emptyList(),
    val warnSilence: Boolean = false,
)

internal data class TesseraFire(
    val eventType: String,
    val data: JsonObject,
)

internal data class TesseraFrame(
    val fromClientId: String,
    val frame: SignalingFrame,
)

/** One step of the WebRTC session that faces a tablet or a dashboard card. */
internal sealed interface MediaCommand {
    val stationId: String

    data class Answer(
        override val stationId: String,
        val tabletId: String,
        val offerSdp: String,
        val listenUrl: String,
        val talkUrl: String,
    ) : MediaCommand

    data class Invite(
        override val stationId: String,
        val tabletId: String,
    ) : MediaCommand

    data class Accept(
        override val stationId: String,
        val answerSdp: String,
    ) : MediaCommand

    data class Attach(
        override val stationId: String,
        val listenUrl: String,
        val talkUrl: String,
    ) : MediaCommand

    data class Candidate(
        override val stationId: String,
        val candidate: String,
        val sdpMid: String?,
        val line: Int,
    ) : MediaCommand

    data class Stop(override val stationId: String) : MediaCommand
}

/**
 * Turns the intercom roster and Tessera's call messages into events, signaling frames, and media steps.
 * It does not open a socket or a microphone.
 */
internal class TesseraCallDesk {
    private var published = emptyList<TesseraPeer>()
    private val callable = linkedSetOf<String>()
    private val contactRows = mutableListOf<TesseraContact>()
    private val stations = linkedMapOf<String, String>()
    private val legs = linkedMapOf<String, Leg>()
    private var askedList = false
    private var heardTessera = false
    private var warnedSilence = false

    fun onRoster(clients: List<SignalingClient>): DeskResult {
        val peers = TesseraRoster.peers(clients)

        if (peers == published) return DeskResult()

        published = peers
        val fires = mutableListOf(TesseraFire(TesseraProtocol.PEERS_EVENT, TesseraProtocol.peersPayload(peers)))
        val peer = peers.firstOrNull()

        if (peer != null) {
            askedList = true
            fires += listFire(peer.node)
        }

        return DeskResult(fires = fires)
    }

    /** Asks again for the screen list. The first time nothing has come back, the gateway says so. */
    fun onRefresh(): DeskResult {
        val peer = published.firstOrNull() ?: return DeskResult()
        val warn = !heardTessera && askedList && !warnedSilence

        if (warn) warnedSilence = true

        askedList = true

        return DeskResult(fires = listOf(listFire(peer.node)), warnSilence = warn)
    }

    fun onInbound(inbound: TesseraInbound): DeskResult {
        heardTessera = true

        return when (inbound) {
            is TesseraInbound.Contacts -> onContacts(inbound)
            is TesseraInbound.Call -> onCall(inbound)
        }
    }

    fun onSignaling(stationId: String, frame: SignalingFrame): DeskResult = when (frame) {
        is SignalingFrame.Offer -> onOffer(stationId, frame)
        is SignalingFrame.Answer -> onAnswer(stationId, frame)
        is SignalingFrame.Hangup -> drop(legForStation(stationId), tellTessera = true, tellTablet = false)
        is SignalingFrame.Candidate -> onCandidate(stationId, frame)
        is SignalingFrame.Unavailable -> onUnavailable(stationId, frame)
        else -> DeskResult()
    }

    fun onMediaFailed(stationId: String): DeskResult =
        drop(legForStation(stationId), tellTessera = true, tellTablet = true)

    private fun onContacts(inbound: TesseraInbound.Contacts): DeskResult {
        contactRows += inbound.rows

        if (inbound.more) return DeskResult()

        val rows = contactRows.toList()
        contactRows.clear()

        val next = TesseraRoster.stations(rows = rows, callable = callable).associate { station ->
            station.node to station.name
        }

        stations.clear()

        for ((node, name) in next) stations[node] = name

        for (leg in legs.values) {
            if (leg.screenNode !in stations) stations[leg.screenNode] = leg.screenNode
        }

        return DeskResult(stations = stationList())
    }

    private fun onCall(call: TesseraInbound.Call): DeskResult = when (call.state) {
        TesseraProtocol.STATE_RING -> onRing(call)
        TesseraProtocol.STATE_OUT -> onOut(call)
        TesseraProtocol.STATE_LIVE -> onLive(call)
        TesseraProtocol.STATE_END -> drop(legFor(call), tellTessera = false, tellTablet = true)
        else -> DeskResult()
    }

    private fun onRing(call: TesseraInbound.Call): DeskResult {
        if (call.node.isEmpty() || call.peer.isEmpty() || legs.containsKey(call.node)) return DeskResult()

        val stationId = TesseraProtocol.clientId(call.peer)

        legs[call.node] = Leg(
            tabletId = call.node,
            stationId = stationId,
            screenNode = call.peer,
            key = call.key,
            fromTablet = false,
            offerSdp = "",
        )

        if (call.peer !in stations) stations[call.peer] = call.name.ifEmpty { call.peer }

        return DeskResult(
            stations = stationList(),
            media = listOf(MediaCommand.Invite(stationId = stationId, tabletId = call.node)),
        )
    }

    private fun onOut(call: TesseraInbound.Call): DeskResult {
        val leg = legs[call.node]?.takeIf { item -> call.peer.isEmpty() || call.peer == item.screenNode }

        if (leg != null && call.key.isNotEmpty()) legs[call.node] = leg.copy(key = call.key)

        return DeskResult()
    }

    private fun onLive(call: TesseraInbound.Call): DeskResult {
        val leg = legs[call.node]?.takeIf { item -> call.peer.isEmpty() || call.peer == item.screenNode }
        val media = leg?.let { item -> liveCommand(item, call) }

        return DeskResult(media = listOfNotNull(media))
    }

    private fun liveCommand(leg: Leg, call: TesseraInbound.Call): MediaCommand {
        val keyed = leg.copy(key = call.key.ifEmpty { leg.key })
        legs[call.node] = keyed

        return if (keyed.fromTablet) {
            MediaCommand.Answer(
                stationId = keyed.stationId,
                tabletId = keyed.tabletId,
                offerSdp = keyed.offerSdp,
                listenUrl = call.listenUrl,
                talkUrl = call.talkUrl,
            )
        } else {
            MediaCommand.Attach(
                stationId = keyed.stationId,
                listenUrl = call.listenUrl,
                talkUrl = call.talkUrl,
            )
        }
    }

    private fun onOffer(stationId: String, frame: SignalingFrame.Offer): DeskResult {
        val tabletId = frame.from.orEmpty()
        val screenNode = TesseraProtocol.nodeOf(stationId)

        if (tabletId.isEmpty() || screenNode == null || legs.containsKey(tabletId)) return DeskResult()

        legs[tabletId] = Leg(
            tabletId = tabletId,
            stationId = stationId,
            screenNode = screenNode,
            key = "",
            fromTablet = true,
            offerSdp = frame.sdp,
        )

        return DeskResult(
            fires = listOf(
                TesseraFire(
                    eventType = TesseraProtocol.SCREEN_CALL_EVENT,
                    data = TesseraProtocol.screenCall(
                        screen = tabletId,
                        action = TesseraProtocol.ACTION_DIAL,
                        to = screenNode,
                    ),
                ),
            ),
        )
    }

    private fun onAnswer(stationId: String, frame: SignalingFrame.Answer): DeskResult {
        val leg = legForStation(stationId)?.takeIf { item -> !item.fromTablet }

        if (leg == null) return DeskResult()

        return DeskResult(
            fires = listOf(
                TesseraFire(
                    eventType = TesseraProtocol.SCREEN_CALL_EVENT,
                    data = TesseraProtocol.screenCall(
                        screen = leg.tabletId,
                        action = TesseraProtocol.ACTION_ANSWER,
                        key = leg.key,
                    ),
                ),
            ),
            media = listOf(MediaCommand.Accept(stationId = stationId, answerSdp = frame.sdp)),
        )
    }

    private fun onCandidate(stationId: String, frame: SignalingFrame.Candidate): DeskResult {
        val candidate = frame.candidate.orEmpty()

        if (candidate.isEmpty() || legForStation(stationId) == null) return DeskResult()

        return DeskResult(
            media = listOf(
                MediaCommand.Candidate(
                    stationId = stationId,
                    candidate = candidate,
                    sdpMid = frame.sdpMid,
                    line = frame.sdpMLineIndex,
                ),
            ),
        )
    }

    private fun onUnavailable(stationId: String, frame: SignalingFrame.Unavailable): DeskResult {
        val leg = legForStation(stationId)?.takeIf { item ->
            frame.targetId == null || frame.targetId == item.tabletId
        }

        return drop(leg, tellTessera = true, tellTablet = false)
    }

    private fun drop(leg: Leg?, tellTessera: Boolean, tellTablet: Boolean): DeskResult {
        if (leg == null) return DeskResult()

        legs.remove(leg.tabletId)

        val fires = if (tellTessera) {
            listOf(
                TesseraFire(
                    eventType = TesseraProtocol.SCREEN_CALL_EVENT,
                    data = TesseraProtocol.screenCall(
                        screen = leg.tabletId,
                        action = TesseraProtocol.ACTION_END,
                        key = leg.key,
                    ),
                ),
            )
        } else {
            emptyList()
        }

        val frames = if (tellTablet) {
            listOf(
                TesseraFrame(
                    fromClientId = leg.stationId,
                    frame = SignalingFrame.Hangup(to = leg.tabletId),
                ),
            )
        } else {
            emptyList()
        }

        return DeskResult(
            fires = fires,
            frames = frames,
            media = listOf(MediaCommand.Stop(stationId = leg.stationId)),
        )
    }

    private fun legFor(call: TesseraInbound.Call): Leg? = legs[call.node]
        ?: legs.values.find { leg -> call.key.isNotEmpty() && leg.key == call.key }

    private fun legForStation(stationId: String): Leg? = legs.values.find { leg -> leg.stationId == stationId }

    private fun stationList(): List<TesseraStation> = stations.map { (node, name) ->
        TesseraStation(node = node, name = name)
    }

    private fun listFire(node: String): TesseraFire = TesseraFire(
        eventType = TesseraProtocol.SCREEN_CALL_EVENT,
        data = TesseraProtocol.screenCall(screen = node, action = TesseraProtocol.ACTION_LIST),
    )

    private data class Leg(
        val tabletId: String,
        val stationId: String,
        val screenNode: String,
        val key: String,
        val fromTablet: Boolean,
        val offerSdp: String,
    )
}
