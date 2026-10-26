package tech.shaffersoftworks.remotescreens.player.data.device.intercom

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** One signaling frame. The add-on's hub speaks the same messages as the standalone server on port 8765. */
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

internal data class RosterStation(val clientId: String, val displayName: String, val inCall: Boolean)

/** Encodes and decodes the intercom signaling frames. Kept off the call machine so either can be tested alone. */
internal object IntercomWire {
    private val json = Json { ignoreUnknownKeys = true }

    fun register(clientId: String, displayName: String): String = json.encodeToString(
        buildJsonObject {
            put("type", "register")
            put("clientId", clientId)
            put("displayName", displayName)
        },
    )

    fun offer(targetId: String, sdp: String): String = addressed("offer", targetId) { put("sdp", sdp) }

    fun answer(targetId: String, sdp: String): String = addressed("answer", targetId) { put("sdp", sdp) }

    fun candidate(targetId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int): String =
        addressed("candidate", targetId) {
            put("candidate", candidate)
            if (sdpMid != null) put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }

    fun hangup(targetId: String): String = addressed("hangup", targetId)

    fun getClients(): String = typed("getClients")

    fun heartbeat(): String = typed("heartbeat")

    fun pong(sentAt: Long): String = json.encodeToString(
        buildJsonObject {
            put("type", "pong")
            put("ts", sentAt)
        },
    )

    fun parse(text: String): IntercomFrame? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        return root?.let(::frame)
    }

    private fun frame(root: JsonObject): IntercomFrame? = when (root.string("type")) {
        "registered" -> IntercomFrame.Registered
        "offer" -> sdpFrame(root) { sdp, from -> IntercomFrame.Offer(sdp, from) }
        "answer" -> sdpFrame(root) { sdp, from -> IntercomFrame.Answer(sdp, from) }
        "candidate" -> candidateFrame(root)
        "hangup" -> IntercomFrame.Hangup(root.string("from"))
        "unavailable" -> unavailable(root)
        "error" -> IntercomFrame.Failed(root.string("message") ?: "")
        "replaced" -> IntercomFrame.Replaced
        "clientsList" -> IntercomFrame.Roster(roster(root))
        "ping" -> IntercomFrame.Ping(root.long("ts") ?: 0L)
        else -> null
    }

    private fun unavailable(root: JsonObject): IntercomFrame.Unavailable? {
        val targetId = root.string("targetId")
        return if (targetId == null) null else IntercomFrame.Unavailable(targetId, root.string("reason") ?: "offline")
    }

    private fun sdpFrame(root: JsonObject, make: (String, String) -> IntercomFrame): IntercomFrame? {
        val sdp = root.string("sdp")
        val from = root.string("from")
        return if (sdp != null && from != null) make(sdp, from) else null
    }

    private fun candidateFrame(root: JsonObject): IntercomFrame.Candidate? {
        val from = root.string("from")
        val candidate = root.string("candidate")
        val line = (root["sdpMLineIndex"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        return if (from != null && candidate != null) {
            IntercomFrame.Candidate(candidate, root.string("sdpMid"), line, from)
        } else {
            null
        }
    }

    private fun roster(root: JsonObject): List<RosterStation> {
        val clients = runCatching { root["clients"]?.jsonArray }.getOrNull() ?: return emptyList()
        return clients.mapNotNull { element ->
            val client = element as? JsonObject ?: return@mapNotNull null
            val clientId = client.string("clientId") ?: return@mapNotNull null
            RosterStation(
                clientId,
                client.string("displayName") ?: clientId,
                (client["inCall"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }
    }

    private fun addressed(
        type: String,
        targetId: String,
        extra: JsonObjectBuilder.() -> Unit = {},
    ): String = json.encodeToString(
        buildJsonObject {
            put("type", type)
            put("to", targetId)
            extra()
        },
    )

    private fun typed(type: String): String = json.encodeToString(buildJsonObject { put("type", type) })

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
