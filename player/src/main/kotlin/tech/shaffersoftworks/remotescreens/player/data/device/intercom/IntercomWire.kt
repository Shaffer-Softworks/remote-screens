package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import tech.shaffersoftworks.remotescreens.shared.SignalingFrame

/** A [SignalingFrame] this station acts on, with the fields it needs present. */
internal sealed interface IntercomFrame {
    data object Registered : IntercomFrame
    data class Offer(val sdp: String, val from: String) : IntercomFrame
    data class Answer(val sdp: String, val from: String) : IntercomFrame
    data class Candidate(
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val from: String,
    ) : IntercomFrame
    data class Hangup(val from: String?) : IntercomFrame
    data class Unavailable(val targetId: String, val reason: String) : IntercomFrame
    data class Failed(val message: String) : IntercomFrame
    data object Replaced : IntercomFrame
    data class Roster(val stations: List<RosterStation>) : IntercomFrame
    data class Ping(val sentAt: Long) : IntercomFrame
}

internal data class RosterStation(
    val clientId: String,
    val displayName: String,
    val inCall: Boolean,
)

/** Encodes and decodes the intercom signaling frames. Kept off the call machine so either can be tested alone. */
internal object IntercomWire {
    fun register(clientId: String, displayName: String): String =
        SignalingFrame.encode(SignalingFrame.Register(clientId, displayName))

    fun offer(targetId: String, sdp: String): String = SignalingFrame.encode(SignalingFrame.Offer(sdp, to = targetId))

    fun answer(targetId: String, sdp: String): String = SignalingFrame.encode(SignalingFrame.Answer(sdp, to = targetId))

    fun candidate(targetId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int): String =
        SignalingFrame.encode(SignalingFrame.Candidate(candidate, sdpMid, sdpMLineIndex, to = targetId))

    fun hangup(targetId: String): String = SignalingFrame.encode(SignalingFrame.Hangup(to = targetId))

    fun getClients(): String = SignalingFrame.encode(SignalingFrame.GetClients)

    fun heartbeat(): String = SignalingFrame.encode(SignalingFrame.Heartbeat)

    fun pong(sentAt: Long): String = SignalingFrame.encode(SignalingFrame.Pong(sentAt))

    fun parse(text: String): IntercomFrame? = SignalingFrame.decode(text)?.let(::frame)

    private fun frame(frame: SignalingFrame): IntercomFrame? = when (frame) {
        is SignalingFrame.Registered -> IntercomFrame.Registered
        is SignalingFrame.Unavailable -> frame.targetId?.let { IntercomFrame.Unavailable(it, frame.reason) }
        is SignalingFrame.Error -> IntercomFrame.Failed(frame.message)
        is SignalingFrame.Replaced -> IntercomFrame.Replaced
        is SignalingFrame.ClientsList -> IntercomFrame.Roster(
            frame.clients.map { RosterStation(it.clientId, it.displayName ?: it.clientId, it.inCall) },
        )
        is SignalingFrame.Ping -> IntercomFrame.Ping(frame.ts)
        else -> callFrame(frame)
    }

    /** The offer, answer and candidates of a call, which are dropped without the station they came from. */
    private fun callFrame(frame: SignalingFrame): IntercomFrame? = when (frame) {
        is SignalingFrame.Offer -> frame.from?.let { IntercomFrame.Offer(frame.sdp, it) }
        is SignalingFrame.Answer -> frame.from?.let { IntercomFrame.Answer(frame.sdp, it) }
        is SignalingFrame.Candidate -> frame.candidate?.let { candidate ->
            frame.from?.let { IntercomFrame.Candidate(candidate, frame.sdpMid, frame.sdpMLineIndex, it) }
        }
        is SignalingFrame.Hangup -> IntercomFrame.Hangup(frame.from)
        else -> null
    }
}
