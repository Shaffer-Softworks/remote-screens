package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.shaffersoftworks.remotescreens.shared.IntercomStation
import tech.shaffersoftworks.remotescreens.shared.PlayerMessage

/**
 * One tablet's intercom: stay registered while the page is on the screen, place a call, and answer one that arrives.
 * A missed call, a busy reply, a timeout and a failed connection all hang up, so the roster does not stick on In Call.
 */
internal data class IntercomRegistration(
    val url: String,
    val clientId: String,
    val displayName: String,
)

internal class IntercomSession(
    private val link: SignalingLink,
    private val audio: WebRtcAudio,
    private val scope: CoroutineScope,
    private val registration: IntercomRegistration,
    onReport: (PlayerMessage.IntercomReport) -> Unit,
    private val onWake: () -> Unit,
    private val delayFor: suspend (Long) -> Unit = { delay(it) },
) {
    private var reportListener: (PlayerMessage.IntercomReport) -> Unit = onReport
    private val clientId = registration.clientId
    private val sendMutex = Mutex()
    private val names = HashMap<String, String>()
    private var stations = emptyList<IntercomStation>()
    private var conversation: SignalingConversation? = null
    private var phase = Phase.OFFLINE
    private var peerId: String? = null
    private var isMuted = false
    private var status = CONNECTING
    private var isRegistered = false
    private var isRunning = true
    private var job: Job? = null
    private var timeoutJob: Job? = null

    fun start() {
        job = scope.launch { run() }
    }

    fun stop() {
        isRunning = false
        timeoutJob?.cancel()
        job?.cancel()
        audio.stop()
    }

    /** Point later roster updates at the listener from the latest connection to the add-on. */
    fun attachReport(listener: (PlayerMessage.IntercomReport) -> Unit) {
        reportListener = listener
    }

    /** Send the current status and stations again. A rebuilt screen stays on Connecting until this arrives. */
    fun publishAgain() {
        scope.launch { publish() }
    }

    fun command(verb: String, targetId: String) {
        scope.launch {
            when (verb) {
                CALL -> placeCall(targetId)
                HANGUP -> if (peerId != null) endCall(notifyPeer = true, nextStatus = CALL_ENDED)
                MUTE -> toggleMute()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun run() {
        while (isRunning) {
            try {
                link.conversation(registration.url) { readFrames() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: NeedsReconnect) {
                dropCall()
            } catch (error: Exception) {
                Timber.d(error, "Intercom signaling dropped")
                dropCall()
            }
            if (isRunning) delayFor(RECONNECT_MS)
        }
    }

    private suspend fun SignalingConversation.readFrames() {
        conversation = this
        emit(IntercomWire.register(clientId, registration.displayName))
        val heartbeat = scope.launch {
            while (isRunning) {
                delayFor(HEARTBEAT_MS)
                emit(IntercomWire.heartbeat())
            }
        }
        try {
            while (isRunning) {
                val text = receive()
                val frame = text?.let(IntercomWire::parse)
                if (text == null) return
                if (frame != null) handle(frame)
            }
        } finally {
            heartbeat.cancel()
            conversation = null
        }
    }

    private suspend fun handle(frame: IntercomFrame) {
        when (frame) {
            IntercomFrame.Registered -> {
                isRegistered = true
                phase = Phase.IDLE
                status = ONLINE
                emit(IntercomWire.getClients())
                publish()
            }
            is IntercomFrame.Offer -> onOffer(frame)
            is IntercomFrame.Answer -> if (phase == Phase.OUTGOING) audio.acceptAnswer(frame.sdp)
            is IntercomFrame.Candidate -> audio.addCandidate(frame.candidate, frame.sdpMid, frame.sdpMLineIndex)
            is IntercomFrame.Hangup -> endCall(notifyPeer = false, nextStatus = CALL_ENDED)
            is IntercomFrame.Unavailable -> onUnavailable(frame)
            is IntercomFrame.Failed -> if (frame.message == NOT_REGISTERED) throw NeedsReconnect()
            IntercomFrame.Replaced -> throw NeedsReconnect()
            is IntercomFrame.Roster -> onRoster(frame)
            is IntercomFrame.Ping -> emit(IntercomWire.pong(frame.sentAt))
        }
    }

    private suspend fun onOffer(frame: IntercomFrame.Offer) {
        val answer = when {
            phase == Phase.IDLE -> {
                peerId = frame.from
                onWake()
                audio.start(audioListener)
                audio.acceptOffer(frame.sdp)
            }
            phase == Phase.OUTGOING && clientId >= frame.from -> {
                timeoutJob?.cancel()
                peerId = frame.from
                audio.rollbackAndAnswer(frame.sdp)
            }
            else -> null
        }
        if (answer == null) {
            if (phase == Phase.OUTGOING && clientId >= frame.from) {
                endCall(notifyPeer = true, nextStatus = CALL_FAILED)
            }
            return
        }
        peerId = frame.from
        phase = Phase.INCOMING
        emit(IntercomWire.answer(frame.from, answer))
        status = "Incoming from ${nameOf(frame.from)}"
        publish()
        armTimeout()
    }

    private suspend fun placeCall(targetId: String) {
        if (phase != Phase.IDLE || targetId.isBlank()) return
        peerId = targetId
        audio.start(audioListener)
        val sdp = runCatching { audio.createOffer() }.getOrElse {
            peerId = null
            audio.stop()
            return
        }
        phase = Phase.OUTGOING
        emit(IntercomWire.offer(targetId, sdp))
        status = "Calling ${nameOf(targetId)}"
        publish()
        armTimeout()
    }

    private suspend fun onUnavailable(frame: IntercomFrame.Unavailable) {
        if (peerId != frame.targetId) return
        val nextStatus = if (frame.reason == BUSY) "${nameOf(frame.targetId)} is busy" else NO_ANSWER
        endCall(notifyPeer = true, nextStatus = nextStatus)
    }

    private suspend fun endCall(notifyPeer: Boolean, nextStatus: String) {
        val target = peerId
        timeoutJob?.cancel()
        if (notifyPeer && target != null) emit(IntercomWire.hangup(target))
        dropCall()
        status = nextStatus
        publish()
    }

    private fun dropCall() {
        timeoutJob?.cancel()
        peerId = null
        phase = if (isRegistered) Phase.IDLE else Phase.OFFLINE
        audio.stop()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audio.setMuted(isMuted)
        publish()
    }

    private fun onRoster(frame: IntercomFrame.Roster) {
        stations = frame.stations
            .filter { it.clientId != clientId }
            .map { station ->
                names[station.clientId] = station.displayName
                IntercomStation(station.clientId, station.displayName, station.inCall)
            }
        if (phase == Phase.IDLE && (status == CONNECTING || status == ONLINE)) status = ONLINE
        publish()
    }

    private fun publish() {
        val connectedId = peerId.takeIf { phase == Phase.LIVE }
        reportListener(
            PlayerMessage.IntercomReport(
                status = status,
                muted = isMuted,
                busy = phase == Phase.OUTGOING || phase == Phase.INCOMING || phase == Phase.LIVE,
                connectedId = connectedId,
                stations = stations,
            ),
        )
    }

    private suspend fun emit(text: String) {
        val current = conversation ?: return
        sendMutex.withLock { current.send(text) }
    }

    private fun armTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delayFor(CALL_TIMEOUT_MS)
            endCall(notifyPeer = true, nextStatus = NO_ANSWER)
        }
    }

    private fun nameOf(targetId: String): String = names[targetId] ?: targetId

    private val audioListener = SessionAudioListener(
        scope = scope,
        peerId = { peerId },
        sendCandidate = { target, candidate, sdpMid, sdpMLineIndex ->
            emit(IntercomWire.candidate(target, candidate, sdpMid, sdpMLineIndex))
        },
        connected = connected@{
            val target = peerId ?: return@connected
            phase = Phase.LIVE
            timeoutJob?.cancel()
            status = "Connected to ${nameOf(target)}"
            publish()
        },
        failed = { endCall(notifyPeer = true, nextStatus = CALL_FAILED) },
    )

    private enum class Phase { OFFLINE, IDLE, OUTGOING, INCOMING, LIVE }

    private class NeedsReconnect : Exception()

    private companion object {
        const val HEARTBEAT_MS = 30_000L
        const val RECONNECT_MS = 2_000L
        const val CALL_TIMEOUT_MS = 30_000L
        const val CONNECTING = "Connecting…"
        const val ONLINE = "Online"
        const val CALL_ENDED = "Call ended"
        const val CALL_FAILED = "Call failed"
        const val NO_ANSWER = "No answer"
        const val BUSY = "busy"
        const val NOT_REGISTERED = "not_registered"
        const val CALL = "call"
        const val HANGUP = "hangup"
        const val MUTE = "mute"
    }
}
