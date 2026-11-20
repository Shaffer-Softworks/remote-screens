package tech.shaffersoftworks.remotescreens.server.intercom.tessera

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** The events the Tessera call hook and this add-on exchange. See docs/TESSERA_CALL_HOOK.md. */
internal object TesseraProtocol {
    const val PEERS_EVENT = "remote_screens_intercom_peers"
    const val PEER_EVENT = "esp_screens_call_peer"
    const val SCREEN_CALL_EVENT = "esphome.screen_call"

    const val CLIENT_PREFIX = "tessera-"
    const val NAME_LIMIT = 60

    const val ACTION_LIST = "list"
    const val ACTION_DIAL = "dial"
    const val ACTION_ANSWER = "answer"
    const val ACTION_DECLINE = "decline"
    const val ACTION_END = "end"

    const val STATE_RING = "ring"
    const val STATE_OUT = "out"
    const val STATE_LIVE = "live"
    const val STATE_END = "end"

    const val AVAILABILITY_OFFLINE = "offline"
    const val OP_CALL = "call"
    const val OP_CONTACTS = "contacts"

    fun clientId(node: String): String = CLIENT_PREFIX + node

    /** The Tessera node a station id names, or null when the station is a tablet or a dashboard card. */
    fun nodeOf(clientId: String): String? {
        if (!clientId.startsWith(CLIENT_PREFIX)) return null

        val node = clientId.removePrefix(CLIENT_PREFIX)

        return node.takeIf { it.isNotEmpty() }
    }

    fun peersPayload(peers: List<TesseraPeer>): JsonObject = buildJsonObject {
        put(
            "peers",
            buildJsonArray {
                for (peer in peers) {
                    add(peerPayload(peer))
                }
            },
        )
    }

    fun screenCall(
        screen: String,
        action: String,
        to: String = "",
        key: String = "",
    ): JsonObject = buildJsonObject {
        put("screen", screen)
        put("action", action)
        put("to", to)
        put("video", "0")
        put("key", key)
    }

    fun parse(data: JsonObject): TesseraInbound? = when (data.text("op")) {
        OP_CONTACTS -> TesseraInbound.Contacts(
            rows = contactRows(data["r"] as? JsonArray),
            more = data.enabled("more"),
        )
        OP_CALL -> TesseraInbound.Call(
            node = data.text("node"),
            peer = data.text("peer"),
            key = data.text("k"),
            state = data.text("s"),
            name = data.text("n"),
            listenUrl = data.text("l"),
            talkUrl = data.text("tk"),
        )
        else -> null
    }

    private fun peerPayload(peer: TesseraPeer): JsonObject = buildJsonObject {
        put("node", peer.node)
        put("name", peer.name.take(NAME_LIMIT))
        put("online", peer.online)
        put("hears", peer.hears)
        put("speaks", peer.speaks)
    }

    private fun contactRows(rows: JsonArray?): List<TesseraContact> {
        if (rows == null) return emptyList()

        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val id = row.text("id")

            if (id.isEmpty()) {
                null
            } else {
                TesseraContact(
                    id = id,
                    name = row.text("n"),
                    canSound = row.enabled("a"),
                    availability = row.text("s").ifEmpty { AVAILABILITY_OFFLINE },
                )
            }
        }
    }
}

internal data class TesseraPeer(
    val node: String,
    val name: String,
    val online: Boolean,
    val hears: Boolean,
    val speaks: Boolean,
)

internal data class TesseraContact(
    val id: String,
    val name: String,
    val canSound: Boolean,
    val availability: String,
)

internal data class TesseraStation(
    val node: String,
    val name: String,
)

internal sealed interface TesseraInbound {
    data class Contacts(
        val rows: List<TesseraContact>,
        val more: Boolean,
    ) : TesseraInbound

    data class Call(
        val node: String,
        val peer: String,
        val key: String,
        val state: String,
        val name: String,
        val listenUrl: String,
        val talkUrl: String,
    ) : TesseraInbound
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.enabled(key: String): Boolean {
    val value = this[key] as? JsonPrimitive ?: return false

    return value.intOrNull == 1 || value.booleanOrNull == true || value.contentOrNull == "1"
}
