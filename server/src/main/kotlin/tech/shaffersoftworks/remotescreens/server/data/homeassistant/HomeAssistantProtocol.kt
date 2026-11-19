package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Home Assistant's WebSocket JSON: `type` tells messages apart, and an unset optional field is left out. */
val HA_JSON: Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    explicitNulls = false
    coerceInputValues = true
}

/** A payload sends every field, defaults included, and leaves out the ones that are null. */
private val PAYLOAD_JSON = Json {
    encodeDefaults = true
    explicitNulls = false
}

/** The fields of [value], to send as a command's payload or a service's data. */
fun <T> encodePayload(serializer: KSerializer<T>, value: T): JsonObject =
    PAYLOAD_JSON.encodeToJsonElement(serializer, value).jsonObject

/** A list that drops the entries it cannot read, as the hand-written parsers did, rather than failing whole. */
open class SkippingListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val list = ListSerializer(element)

    override val descriptor: SerialDescriptor = list.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as JsonDecoder
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()

        return array.mapNotNull { item -> runCatching { jsonDecoder.json.decodeFromJsonElement(
            element,
            item,
        ) }.getOrNull() }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = list.serialize(encoder, value)
}

/** What Home Assistant sends us over the WebSocket. */
@Serializable
sealed interface HaInbound {
    @Serializable
    @SerialName("auth_required")
    data class AuthRequired(@SerialName("ha_version") val haVersion: String? = null) : HaInbound

    @Serializable
    @SerialName("auth_ok")
    data class AuthOk(@SerialName("ha_version") val haVersion: String? = null) : HaInbound

    @Serializable
    @SerialName("auth_invalid")
    data class AuthInvalid(val message: String? = null) : HaInbound

    /** The answer to the command with [id]; [result] is read with that command's serializer. */
    @Serializable
    @SerialName("result")
    data class Result(
        val id: Int,
        val success: Boolean = false,
        val result: JsonElement? = null,
        val error: HaError? = null,
    ) : HaInbound

    @Serializable
    @SerialName("event")
    data class Event(val id: Int? = null, val event: HaEventEnvelope) : HaInbound

    @Serializable
    @SerialName("pong")
    data class Pong(val id: Int? = null) : HaInbound
}

@Serializable
data class HaError(val code: String = "error", val message: String = "failed")

/** One event Home Assistant fired. Its [data] depends on [eventType]; [StateChangedData] reads `state_changed`. */
@Serializable
data class HaEventEnvelope(
    @SerialName("event_type") val eventType: String,
    val data: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class StateChangedData(
    @SerialName("entity_id") val entityId: String,
    @SerialName("new_state") val newState: EntityStateDto? = null,
)

@Serializable
data class ComponentLoadedData(val component: String? = null)

/** An entity as `get_states` and `state_changed` report it. */
@Serializable
data class EntityStateDto(
    @SerialName("entity_id") val entityId: String,
    val state: String = Availability.UNKNOWN.wire,
    val attributes: JsonObject = JsonObject(emptyMap()),
    @SerialName("last_changed") val lastChanged: String = "",
) {
    fun toEntityState(): EntityState = EntityState(
        entityId = entityId,
        state = state,
        attributes = attributes,
        lastChanged = lastChanged,
    )
}

/** The answer to `get_config`. */
@Serializable
data class HaConfigDto(
    val language: String? = null,
    val country: String? = null,
    @SerialName("time_zone") val timeZone: String? = null,
    @SerialName("unit_system") val unitSystem: UnitSystem? = null,
    @SerialName("location_name") val locationName: String? = null,
    val version: String? = null,
    val components: List<String> = emptyList(),
) {
    @Serializable
    data class UnitSystem(val temperature: String? = null)

    /** [integrationDomain] is among the loaded [components]. */
    fun toConfig(integrationDomain: String): HomeAssistantConfig {
        val defaults = HomeAssistantConfig()

        return HomeAssistantConfig(
            language = language ?: defaults.language,
            country = country,
            timeZone = timeZone ?: defaults.timeZone,
            temperatureUnit = unitSystem?.temperature ?: defaults.temperatureUnit,
            locationName = locationName ?: defaults.locationName,
            version = version.orEmpty(),
            isIntegrationLoaded = integrationDomain in components,
        )
    }
}

/** The first message we send, of type `auth`: the long-lived access token. */
@Serializable
data class HaAuth(@SerialName("access_token") val accessToken: String)

/** For a command whose `result` is null or not read. */
object NoResult : KSerializer<Unit> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NoResult")

    override fun deserialize(decoder: Decoder) {
        (decoder as? JsonDecoder)?.decodeJsonElement()
    }

    override fun serialize(encoder: Encoder, value: Unit) {
        (encoder as JsonEncoder).encodeJsonElement(JsonNull)
    }
}

/**
 * A WebSocket command: its [type], its fields ([payload]) and how to read its `result` ([resultSerializer]).
 * A command with fields is a `@Serializable` class whose properties are those fields.
 */
interface HaRequest<R> {
    val type: String
    val resultSerializer: KSerializer<R>

    fun payload(): JsonObject = JsonObject(emptyMap())
}
