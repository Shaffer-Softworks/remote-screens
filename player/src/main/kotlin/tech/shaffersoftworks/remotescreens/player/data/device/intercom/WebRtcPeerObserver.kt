package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver

/** Forwards the peer connection's audio events. Empty methods are the ones a voice call does not use. */
internal class WebRtcPeerObserver(
    private val listener: () -> AudioListener,
) : PeerConnection.Observer {
    override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate == null) return
        listener().onCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED,
            -> listener().onConnected()
            // CLOSED is how a hangup ends the connection. Only FAILED is a call that broke.
            PeerConnection.IceConnectionState.FAILED -> listener().onFailed()
            else -> Unit
        }
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        val track = receiver?.track()
        if (track is AudioTrack) track.setEnabled(true)
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
    override fun onAddStream(stream: MediaStream?) = Unit
    override fun onRemoveStream(stream: MediaStream?) = Unit
    override fun onDataChannel(channel: DataChannel?) = Unit
    override fun onRenegotiationNeeded() = Unit
}

internal object IdleAudioListener : AudioListener {
    override fun onCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) = Unit
    override fun onConnected() = Unit
    override fun onFailed() = Unit
}
