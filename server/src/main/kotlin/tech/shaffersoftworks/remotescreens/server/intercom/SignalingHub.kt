package tech.shaffersoftworks.remotescreens.server.intercom

import tech.shaffersoftworks.remotescreens.shared.SignalingClient
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame.Companion.ERROR_MISSING_CLIENT
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame.Companion.ERROR_MISSING_TO
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame.Companion.ERROR_NOT_REGISTERED
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame.Companion.REASON_BUSY
import tech.shaffersoftworks.remotescreens.shared.SignalingFrame.Companion.REASON_OFFLINE

/**
 * The intercom signaling router. Tablets on port 8766 and the Home Assistant panel on ingress share one instance.
 * The wire messages match the standalone signaling add-on, with two differences: a station is marked in a call only
 * when the other station is online and free, and `getClients` answers the station that asked.
 */
internal class SignalingHub(
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Outbound(val sessionId: String, val json: String)

    data class DeferredSend(
        val sessionId: String,
        val json: String,
        val delayMs: Long,
    )

    data class Outcome(
        val sends: List<Outbound> = emptyList(),
        val deferred: List<DeferredSend> = emptyList(),
        val closeSessionIds: List<String> = emptyList(),
    )

    data class ClientView(
        val clientId: String,
        val displayName: String?,
        val inCall: Boolean,
    ) {
        fun toClient(): SignalingClient = SignalingClient(
            clientId = clientId,
            displayName = displayName,
            inCall = inCall
        )
    }

    private data class SessionInfo(
        val clientId: String,
        val displayName: String?,
        var lastActivity: Long,
        var inCallWith: String?,
    )

    private val clientSessions = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, SessionInfo>()
    private val lock = Any()

    /** A frame of a `type` this hub does not know still marks a registered station active. */
    fun handleMessage(sessionId: String, text: String): Outcome = synchronized(lock) {
        val frame = SignalingFrame.decode(text)

        if (frame is SignalingFrame.Register) return register(sessionId, frame)

        if (frame == null && SignalingFrame.typeOf(text) == null) return Outcome()

        val info = sessions[sessionId] ?: return Outcome(sends = listOf(error(sessionId, ERROR_NOT_REGISTERED)))

        info.lastActivity = now()

        when (frame) {
            is SignalingFrame.Offer -> offer(sessionId = sessionId, info = info, frame = frame)
            is SignalingFrame.Answer -> forward(info, frame.to) { frame.copy(from = info.clientId) }
            is SignalingFrame.Candidate -> forward(info, frame.to) { frame.copy(from = info.clientId) }
            is SignalingFrame.Hangup -> hangup(info, frame)
            SignalingFrame.GetClients -> clientsFor(sessionId)
            else -> Outcome()
        }
    }

    fun handleDisconnect(sessionId: String): Outcome = synchronized(lock) {
        val info = sessions.remove(sessionId) ?: return Outcome()

        if (clientSessions[info.clientId] == sessionId) clientSessions.remove(info.clientId)

        val sends = mutableListOf<Outbound>()
        clearPeer(clientId = info.clientId, peerId = info.inCallWith, sends = sends)
        sends += rosterBroadcast()
        Outcome(sends = sends)
    }

    /** Drops idle sessions and returns who is still registered, plus the sockets to close. */
    fun poll(): Pair<List<ClientView>, List<String>> = synchronized(lock) {
        val closed = pruneStale()
        views() to closed
    }

    private fun register(sessionId: String, frame: SignalingFrame.Register): Outcome {
        val clientId = frame.clientId.trim()

        if (clientId.isEmpty()) return Outcome(sends = listOf(error(sessionId, ERROR_MISSING_CLIENT)))

        val displayName = frame.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val previousSessionId = clientSessions[clientId]
        val previous = previousSessionId?.let { sessions[it] }

        clientSessions[clientId] = sessionId

        sessions[sessionId] = SessionInfo(
            clientId = clientId,
            displayName = displayName,
            lastActivity = now(),
            inCallWith = null,
        )

        val sends = mutableListOf<Outbound>()
        val close = mutableListOf<String>()

        if (previousSessionId != null && previousSessionId != sessionId) {
            clearPeer(clientId = clientId, peerId = previous?.inCallWith, sends = sends)
            sessions.remove(previousSessionId)
            sends += Outbound(previousSessionId, SignalingFrame.encode(SignalingFrame.Replaced(bySession = sessionId)))
            close += previousSessionId
        }

        close += pruneStale()
        val registered = SignalingFrame.encode(SignalingFrame.Registered(clientId))
        sends += Outbound(sessionId, registered)
        sends += rosterBroadcast()

        val deferred = listOf(
            DeferredSend(sessionId = sessionId, json = registered, delayMs = REGISTERED_REPEAT_SHORT_MS),
            DeferredSend(sessionId = sessionId, json = registered, delayMs = REGISTERED_REPEAT_LONG_MS),
        )

        return Outcome(sends = sends, deferred = deferred, closeSessionIds = close)
    }

    private fun offer(
        sessionId: String,
        info: SessionInfo,
        frame: SignalingFrame.Offer,
    ): Outcome {
        val targetId = frame.to?.trim().orEmpty()
        val targetSessionId = clientSessions[targetId]
        val target = targetSessionId?.let { sessions[it] }

        val refused = when {
            targetId.isEmpty() -> error(sessionId, ERROR_MISSING_TO)
            targetSessionId == null || target == null -> unavailable(
                sessionId = sessionId,
                targetId = targetId,
                reason = REASON_OFFLINE
            )
            target.inCallWith != null -> unavailable(sessionId = sessionId, targetId = targetId, reason = REASON_BUSY)
            else -> null
        }

        if (refused != null) return Outcome(sends = listOf(refused))

        info.inCallWith = targetId
        target?.inCallWith = info.clientId
        val offer = SignalingFrame.encode(frame.copy(to = targetId, from = info.clientId))
        val delivered = targetSessionId?.let { Outbound(it, offer) }

        return Outcome(sends = listOfNotNull(delivered) + rosterBroadcast())
    }

    /** An answer or candidate, passed on as the sender wrote it, stamped [fromSender]. */
    private fun forward(
        info: SessionInfo,
        to: String?,
        fromSender: () -> SignalingFrame,
    ): Outcome {
        val targetId = to?.trim().orEmpty()
        val targetSessionId = clientSessions[targetId]
        val sender = clientSessions[info.clientId]

        val sends = if (targetId.isNotEmpty() && targetSessionId != null) {
            listOf(Outbound(targetSessionId, SignalingFrame.encode(fromSender())))
        } else if (sender != null) {
            listOf(unavailable(sessionId = sender, targetId = targetId, reason = REASON_OFFLINE))
        } else {
            emptyList()
        }

        return Outcome(sends = sends)
    }

    private fun hangup(info: SessionInfo, frame: SignalingFrame.Hangup): Outcome {
        val targetId = frame.to?.trim()?.takeIf { it.isNotEmpty() } ?: info.inCallWith
        val sends = mutableListOf<Outbound>()

        if (targetId != null) {
            clientSessions[targetId]?.let { targetSessionId ->
                val hangup = SignalingFrame.Hangup(to = targetId, from = info.clientId)
                sends += Outbound(targetSessionId, SignalingFrame.encode(hangup))
                sessions[targetSessionId]?.inCallWith = null
            }

            info.inCallWith = null
            sends += rosterBroadcast()
        }

        return Outcome(sends = sends)
    }

    private fun clientsFor(sessionId: String): Outcome {
        val closed = pruneStale()

        val sends = if (closed.isEmpty()) {
            listOf(Outbound(sessionId, rosterJson()))
        } else {
            rosterBroadcast()
        }

        return Outcome(sends = sends, closeSessionIds = closed)
    }

    private fun clearPeer(clientId: String, peerId: String?, sends: MutableList<Outbound>) {
        if (peerId == null) return

        val peerSessionId = clientSessions[peerId] ?: return
        sessions[peerSessionId]?.takeIf { it.inCallWith == clientId }?.inCallWith = null
        sends += Outbound(peerSessionId, SignalingFrame.encode(SignalingFrame.Hangup(from = clientId)))
    }

    private fun pruneStale(): List<String> {
        val expired = sessions.filter { now() - it.value.lastActivity > STALE_CLIENT_MS }.keys.toList()

        for (sessionId in expired) {
            val info = sessions.remove(sessionId) ?: continue

            if (clientSessions[info.clientId] == sessionId) clientSessions.remove(info.clientId)
        }

        return expired
    }

    private fun views(): List<ClientView> = clientSessions.mapNotNull { (clientId, sessionId) ->
        val info = sessions[sessionId] ?: return@mapNotNull null
        ClientView(clientId = clientId, displayName = info.displayName, inCall = info.inCallWith != null)
    }

    private fun rosterBroadcast(): List<Outbound> {
        val json = rosterJson()

        return clientSessions.values.distinct().map { Outbound(it, json) }
    }

    private fun rosterJson(): String = SignalingFrame.encode(SignalingFrame.ClientsList(views().map { it.toClient() }))

    companion object {
        const val STALE_CLIENT_MS = 90_000L
        private const val REGISTERED_REPEAT_SHORT_MS = 100L
        private const val REGISTERED_REPEAT_LONG_MS = 250L
        private fun error(sessionId: String, message: String): Outbound =
            Outbound(sessionId, SignalingFrame.encode(SignalingFrame.Error(message)))

        private fun unavailable(sessionId: String, targetId: String, reason: String): Outbound =
            Outbound(sessionId, SignalingFrame.encode(SignalingFrame.Unavailable(targetId, reason)))
    }
}
