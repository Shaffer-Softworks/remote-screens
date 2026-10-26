package tech.shaffersoftworks.remotescreens.server.intercom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The intercom signaling router. Tablets on port 8766 and the Home Assistant panel on ingress share one instance.
 * The wire messages match the standalone signaling add-on, with two differences: a station is marked in a call only
 * when the other station is online and free, and `getClients` answers the station that asked.
 */
internal class SignalingHub(
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Outbound(val sessionId: String, val json: String)

    data class DeferredSend(val sessionId: String, val json: String, val delayMs: Long)

    data class Outcome(
        val sends: List<Outbound> = emptyList(),
        val deferred: List<DeferredSend> = emptyList(),
        val closeSessionIds: List<String> = emptyList(),
    )

    data class ClientView(
        val clientId: String,
        val displayName: String?,
        val inCall: Boolean,
    )

    private data class SessionInfo(
        val clientId: String,
        val displayName: String?,
        var lastActivity: Long,
        var inCallWith: String?,
    )

    private val clientSessions = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, SessionInfo>()
    private val lock = Any()

    fun handleMessage(sessionId: String, text: String): Outcome = synchronized(lock) {
        val data = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return Outcome()
        val type = data.field("type") ?: return Outcome()
        if (type == TYPE_REGISTER) return register(sessionId, data)
        val info = sessions[sessionId]
        if (info == null) {
            return Outcome(sends = listOf(error(sessionId, ERROR_NOT_REGISTERED)))
        }
        info.lastActivity = now()
        when (type) {
            TYPE_OFFER -> offer(sessionId, info, data)
            TYPE_ANSWER -> forward(info, data, TYPE_ANSWER)
            TYPE_CANDIDATE -> forward(info, data, TYPE_CANDIDATE)
            TYPE_HANGUP -> hangup(info, data)
            TYPE_GET_CLIENTS -> clientsFor(sessionId)
            TYPE_HEARTBEAT, TYPE_PONG -> Outcome()
            else -> Outcome()
        }
    }

    fun handleDisconnect(sessionId: String): Outcome = synchronized(lock) {
        val info = sessions.remove(sessionId) ?: return Outcome()
        if (clientSessions[info.clientId] == sessionId) clientSessions.remove(info.clientId)
        val sends = mutableListOf<Outbound>()
        clearPeer(info.clientId, info.inCallWith, sends)
        sends += rosterBroadcast()
        Outcome(sends = sends)
    }

    /** Drops idle sessions and returns who is still registered, plus the sockets to close. */
    fun poll(): Pair<List<ClientView>, List<String>> = synchronized(lock) {
        val closed = pruneStale()
        views() to closed
    }

    private fun register(sessionId: String, data: JsonObject): Outcome {
        val clientId = data.field("clientId")?.trim().orEmpty()
        if (clientId.isEmpty()) return Outcome(sends = listOf(error(sessionId, ERROR_MISSING_CLIENT)))
        val displayName = data.field("displayName")?.trim()?.takeIf { it.isNotEmpty() }
        val previousSessionId = clientSessions[clientId]
        val previous = previousSessionId?.let { sessions[it] }
        clientSessions[clientId] = sessionId
        sessions[sessionId] = SessionInfo(clientId, displayName, now(), null)
        val sends = mutableListOf<Outbound>()
        val close = mutableListOf<String>()
        if (previousSessionId != null && previousSessionId != sessionId) {
            clearPeer(clientId, previous?.inCallWith, sends)
            sessions.remove(previousSessionId)
            sends += Outbound(previousSessionId, json(TYPE_REPLACED, "bySession" to sessionId))
            close += previousSessionId
        }
        close += pruneStale()
        val registered = json(TYPE_REGISTERED, "clientId" to clientId)
        sends += Outbound(sessionId, registered)
        sends += rosterBroadcast()
        val deferred = listOf(
            DeferredSend(sessionId, registered, REGISTERED_REPEAT_SHORT_MS),
            DeferredSend(sessionId, registered, REGISTERED_REPEAT_LONG_MS),
        )
        return Outcome(sends, deferred, close)
    }

    private fun offer(sessionId: String, info: SessionInfo, data: JsonObject): Outcome {
        val targetId = data.field("to")?.trim().orEmpty()
        val targetSessionId = clientSessions[targetId]
        val target = targetSessionId?.let { sessions[it] }
        val refused = when {
            targetId.isEmpty() -> error(sessionId, ERROR_MISSING_TO)
            targetSessionId == null || target == null -> unavailable(sessionId, targetId, REASON_OFFLINE)
            target.inCallWith != null -> unavailable(sessionId, targetId, REASON_BUSY)
            else -> null
        }
        if (refused != null) return Outcome(sends = listOf(refused))
        info.inCallWith = targetId
        target?.inCallWith = info.clientId
        val offer = buildJsonObject {
            put("type", TYPE_OFFER)
            put("sdp", data["sdp"] ?: JsonPrimitive(""))
            put("to", targetId)
            put("from", info.clientId)
        }.toString()
        val delivered = targetSessionId?.let { Outbound(it, offer) }
        return Outcome(sends = listOfNotNull(delivered) + rosterBroadcast())
    }

    private fun forward(info: SessionInfo, data: JsonObject, type: String): Outcome {
        val targetId = data.field("to")?.trim().orEmpty()
        val targetSessionId = clientSessions[targetId]
        val sender = clientSessions[info.clientId]
        val sends = if (targetId.isNotEmpty() && targetSessionId != null) {
            val forwarded = JsonObject(
                data + mapOf(
                    "type" to JsonPrimitive(type),
                    "from" to JsonPrimitive(info.clientId),
                ),
            ).toString()
            listOf(Outbound(targetSessionId, forwarded))
        } else if (sender != null) {
            listOf(unavailable(sender, targetId, REASON_OFFLINE))
        } else {
            emptyList()
        }
        return Outcome(sends = sends)
    }

    private fun hangup(info: SessionInfo, data: JsonObject): Outcome {
        val targetId = data.field("to")?.trim()?.takeIf { it.isNotEmpty() } ?: info.inCallWith
        val sends = mutableListOf<Outbound>()
        if (targetId != null) {
            clientSessions[targetId]?.let { targetSessionId ->
                sends += Outbound(targetSessionId, json(TYPE_HANGUP, "to" to targetId, "from" to info.clientId))
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
        sends += Outbound(peerSessionId, json(TYPE_HANGUP, "from" to clientId))
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
        ClientView(clientId, info.displayName, info.inCallWith != null)
    }

    private fun rosterBroadcast(): List<Outbound> {
        val json = rosterJson()
        return clientSessions.values.distinct().map { Outbound(it, json) }
    }

    private fun rosterJson(): String {
        val clients = buildJsonArray {
            for (view in views()) {
                add(
                    buildJsonObject {
                        put("clientId", view.clientId)
                        put("displayName", view.displayName?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("inCall", view.inCall)
                    },
                )
            }
        }
        return buildJsonObject {
            put("type", TYPE_CLIENTS_LIST)
            put("clients", clients)
        }.toString()
    }

    companion object {
        const val STALE_CLIENT_MS = 90_000L
        private const val REGISTERED_REPEAT_SHORT_MS = 100L
        private const val REGISTERED_REPEAT_LONG_MS = 250L
        private const val TYPE_REGISTER = "register"
        private const val TYPE_REGISTERED = "registered"
        private const val TYPE_OFFER = "offer"
        private const val TYPE_ANSWER = "answer"
        private const val TYPE_CANDIDATE = "candidate"
        private const val TYPE_HANGUP = "hangup"
        private const val TYPE_GET_CLIENTS = "getClients"
        private const val TYPE_CLIENTS_LIST = "clientsList"
        private const val TYPE_HEARTBEAT = "heartbeat"
        private const val TYPE_PONG = "pong"
        private const val TYPE_REPLACED = "replaced"
        private const val ERROR_NOT_REGISTERED = "not_registered"
        private const val ERROR_MISSING_CLIENT = "missing_client_id"
        private const val ERROR_MISSING_TO = "missing_to_field"
        private const val REASON_OFFLINE = "offline"
        private const val REASON_BUSY = "busy"

        private fun JsonObject.field(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun error(sessionId: String, message: String): Outbound =
            Outbound(sessionId, json("error", "message" to message))

        private fun unavailable(sessionId: String, targetId: String, reason: String): Outbound =
            Outbound(sessionId, json("unavailable", "targetId" to targetId, "reason" to reason))

        private fun json(type: String, vararg fields: Pair<String, String>): String = buildJsonObject {
            put("type", type)
            for ((key, value) in fields) put(key, value)
        }.toString()
    }
}
