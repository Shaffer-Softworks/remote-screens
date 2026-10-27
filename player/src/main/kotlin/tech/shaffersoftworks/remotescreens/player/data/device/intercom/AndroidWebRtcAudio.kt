@file:Suppress("TooManyFunctions")

package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * An audio-only call on the speaker, with hardware echo cancellation and noise suppression when the tablet has them.
 * RPI's factory also builds a video encoder; a voice call does not need one.
 */
@Singleton
class AndroidWebRtcAudio @Inject constructor(
    @ApplicationContext private val context: Context,
) : WebRtcAudio {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var factory: PeerConnectionFactory? = null
    private var audioDevice: JavaAudioDeviceModule? = null
    private var peer: PeerConnection? = null
    private var localAudio: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var listener: AudioListener = IdleAudioListener
    private var callGeneration = 0
    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var hasRemoteDescription = false

    override fun start(listener: AudioListener) {
        callGeneration += 1
        val generation = callGeneration
        this.listener = listener
        closePeer()
        ensureFactory()
        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peer = factory?.createPeerConnection(
            configuration,
            WebRtcPeerObserver { activeListener(generation) },
        )
        addLocalAudio()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToSpeaker()
        takeAudioFocus()
    }

    override suspend fun createOffer(): String = createDescription { connection, callback ->
        connection.createOffer(callback, receiveAudio())
    }

    override suspend fun acceptOffer(sdp: String): String {
        setRemote(SessionDescription.Type.OFFER, sdp)
        return createDescription { connection, callback ->
            connection.createAnswer(callback, receiveAudio())
        }
    }

    override suspend fun acceptAnswer(sdp: String) {
        setRemote(SessionDescription.Type.ANSWER, sdp)
    }

    override suspend fun rollbackAndAnswer(offer: String): String? = runCatching {
        setLocal(SessionDescription(SessionDescription.Type.ROLLBACK, ""))
        setRemote(SessionDescription.Type.OFFER, offer)
        createDescription { connection, callback ->
            connection.createAnswer(callback, receiveAudio())
        }
    }.getOrNull()

    override fun addCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val ice = IceCandidate(sdpMid ?: "", sdpMLineIndex, candidate)
        if (!hasRemoteDescription) {
            pendingCandidates.add(ice)
            return
        }
        peer?.addIceCandidate(ice)
    }

    override fun setMuted(isMuted: Boolean) {
        localAudio?.setEnabled(!isMuted)
    }

    override fun stop() {
        callGeneration += 1
        listener = IdleAudioListener
        closePeer()
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
        focusRequest?.let { request -> audioManager.abandonAudioFocusRequest(request) }
        focusRequest = null
    }

    /** A granted microphone sends this tablet's voice. Without it the call still receives the other side. */
    private fun addLocalAudio() {
        if (!hasMicrophone()) {
            peer?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
            return
        }
        val source = factory?.createAudioSource(MediaConstraints())
        localAudio = factory?.createAudioTrack(LOCAL_TRACK_ID, source)
        localAudio?.setEnabled(true)
        peer?.addTrack(localAudio, listOf(LOCAL_STREAM_ID))
    }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        val module = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .createAudioDeviceModule()
        audioDevice = module
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(module)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    /** Events from a peer we already replaced or hung up stay on the idle listener. */
    private fun activeListener(generation: Int): AudioListener =
        if (generation == callGeneration) listener else IdleAudioListener

    /**
     * Drop the peer off the main thread. `close()` can block, and it reports CLOSED, which is not a failed call.
     * A generation check drops events from a peer that was already replaced or hung up.
     */
    private fun closePeer() {
        val closingPeer = peer
        val closingAudio = localAudio
        peer = null
        localAudio = null
        pendingCandidates.clear()
        hasRemoteDescription = false
        if (closingPeer == null && closingAudio == null) return
        closeScope.launch {
            closingAudio?.setEnabled(false)
            closingAudio?.dispose()
            closingPeer?.close()
        }
    }

    private fun routeToSpeaker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            return
        }
        val speaker = audioManager.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker != null) audioManager.setCommunicationDevice(speaker)
    }

    private fun takeAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private suspend fun createDescription(
        create: (PeerConnection, SdpObserver) -> Unit,
    ): String {
        val connection = peer ?: error("No intercom call is open")
        val description = suspendCancellableCoroutine { continuation ->
            create(
                connection,
                object : SdpObserver {
                    override fun onCreateSuccess(created: SessionDescription?) {
                        if (created == null) {
                            continuation.resumeWithException(IllegalStateException("Empty session description"))
                        } else {
                            continuation.resume(created)
                        }
                    }

                    override fun onCreateFailure(error: String?) {
                        continuation.resumeWithException(IllegalStateException(error ?: "create failed"))
                    }

                    override fun onSetSuccess() = Unit
                    override fun onSetFailure(error: String?) = Unit
                },
            )
        }
        setLocal(description)
        return description.description
    }

    private suspend fun setLocal(description: SessionDescription) {
        val connection = peer ?: error("No intercom call is open")
        suspendCancellableCoroutine { continuation ->
            connection.setLocalDescription(
                object : SdpObserver {
                    override fun onSetSuccess() = continuation.resume(Unit)
                    override fun onSetFailure(error: String?) {
                        continuation.resumeWithException(IllegalStateException(error ?: "set local failed"))
                    }

                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = Unit
                },
                description,
            )
        }
    }

    private suspend fun setRemote(type: SessionDescription.Type, sdp: String) {
        val connection = peer ?: error("No intercom call is open")
        suspendCancellableCoroutine { continuation ->
            connection.setRemoteDescription(
                object : SdpObserver {
                    override fun onSetSuccess() = continuation.resume(Unit)
                    override fun onSetFailure(error: String?) {
                        continuation.resumeWithException(IllegalStateException(error ?: "set remote failed"))
                    }

                    override fun onCreateSuccess(description: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String?) = Unit
                },
                SessionDescription(type, sdp),
            )
        }
        flushCandidates()
    }

    private fun flushCandidates() {
        hasRemoteDescription = true
        val queued = pendingCandidates.toList()
        pendingCandidates.clear()
        queued.forEach { candidate -> peer?.addIceCandidate(candidate) }
    }

    private fun receiveAudio(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    private companion object {
        const val LOCAL_TRACK_ID = "intercom_audio"
        const val LOCAL_STREAM_ID = "intercom"
    }
}
