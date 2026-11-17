@file:OptIn(ExperimentalSerializationApi::class)

package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One intercom signaling message on `/webrtc` ([Protocol.INTERCOM_PORT] and the ingress panel), `type` naming the
 * kind. The names match the standalone WebRTC signaling server app on 8765, which browsers and the Lovelace card
 * also speak, so a renamed field breaks them.
 *
 * Defaults are what a missing field decodes to. A field a peer always reads is [EncodeDefault], so it is written
 * even when it holds that default.
 */
@Serializable
sealed interface SignalingFrame {
    @Serializable
    @SerialName("register")
    data class Register(
        @EncodeDefault val clientId: String = "",
        val displayName: String? = null,
    ) : SignalingFrame

    @Serializable
    @SerialName("registered")
    data class Registered(val clientId: String? = null) : SignalingFrame

    @Serializable
    @SerialName("offer")
    data class Offer(
        @EncodeDefault val sdp: String = "",
        val to: String? = null,
        val from: String? = null,
    ) : SignalingFrame

    @Serializable
    @SerialName("answer")
    data class Answer(
        @EncodeDefault val sdp: String = "",
        val to: String? = null,
        val from: String? = null,
    ) : SignalingFrame

    @Serializable
    @SerialName("candidate")
    data class Candidate(
        val candidate: String? = null,
        val sdpMid: String? = null,
        @EncodeDefault val sdpMLineIndex: Int = 0,
        val to: String? = null,
        val from: String? = null,
    ) : SignalingFrame

    /** Without [to], the hub ends the sender's current call. */
    @Serializable
    @SerialName("hangup")
    data class Hangup(
        val to: String? = null,
        val from: String? = null,
    ) : SignalingFrame

    @Serializable
    @SerialName("getClients")
    data object GetClients : SignalingFrame

    @Serializable
    @SerialName("clientsList")
    data class ClientsList(@EncodeDefault val clients: List<SignalingClient> = emptyList()) : SignalingFrame

    @Serializable
    @SerialName("heartbeat")
    data object Heartbeat : SignalingFrame

    /** The hub's app-level ping, every 25s. [ts] is milliseconds since the epoch, echoed in [Pong]. */
    @Serializable
    @SerialName("ping")
    data class Ping(@EncodeDefault val ts: Long = 0) : SignalingFrame

    @Serializable
    @SerialName("pong")
    data class Pong(@EncodeDefault val ts: Long = 0) : SignalingFrame

    /** [reason] is [REASON_OFFLINE] or [REASON_BUSY]. */
    @Serializable
    @SerialName("unavailable")
    data class Unavailable(
        val targetId: String? = null,
        @EncodeDefault val reason: String = REASON_OFFLINE,
    ) : SignalingFrame

    @Serializable
    @SerialName("error")
    data class Error(@EncodeDefault val message: String = "") : SignalingFrame

    /** The same client id registered on another socket, [bySession]. This one is closed next. */
    @Serializable
    @SerialName("replaced")
    data class Replaced(val bySession: String? = null) : SignalingFrame

    companion object {
        const val REASON_OFFLINE = "offline"
        const val REASON_BUSY = "busy"
        const val ERROR_NOT_REGISTERED = "not_registered"
        const val ERROR_MISSING_CLIENT = "missing_client_id"
        const val ERROR_MISSING_TO = "missing_to_field"

        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            classDiscriminator = "type"
        }

        fun encode(frame: SignalingFrame): String = JSON.encodeToString(serializer(), frame)

        /** Null for text that is not a frame, including a `type` this side does not know ([typeOf] still reads it). */
        fun decode(text: String): SignalingFrame? = try {
            JSON.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            null
        }

        /** The `type` of a JSON object, for a frame [decode] does not know. */
        fun typeOf(text: String): String? = runCatching {
            ((JSON.parseToJsonElement(text) as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
    }
}

/** One station in [SignalingFrame.ClientsList] and `/api/clients`. A missing name is written as `null`. */
@Serializable
data class SignalingClient(
    val clientId: String,
    @EncodeDefault val displayName: String? = null,
    @EncodeDefault val inCall: Boolean = false,
)
