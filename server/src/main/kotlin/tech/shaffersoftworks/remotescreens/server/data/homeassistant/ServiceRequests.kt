package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A service call the add-on makes itself: a fixed [service] and typed fields. Its `entity_id` field becomes the
 * call's `target`. A service named by a layout or the catalogue goes through [HomeAssistant.callService] instead.
 */
interface ServiceRequest {
    val service: ServiceRef

    fun data(): ServiceData
}

/** Calls [request]; the service's response when [returnResponse] is set. */
suspend fun HomeAssistant.call(request: ServiceRequest, returnResponse: Boolean = false): JsonElement? =
    callService(service = request.service, data = request.data(), returnResponse = returnResponse)

/** RPI's light dialog: an effect or a colour. */
@Serializable
data class LightTurnOn(
    @SerialName("entity_id") val entityId: EntityId,
    val effect: String? = null,
    @SerialName("rgb_color") val rgbColor: List<Int>? = null,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.LIGHT_TURN_ON

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** Cleans Home Assistant areas, for a vacuum whose segments are mapped to them. */
@Serializable
data class VacuumCleanArea(
    @SerialName("entity_id") val entityId: EntityId,
    @SerialName("cleaning_area_id") val areaIds: List<String>,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.VACUUM_CLEAN_AREA

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** Roborock's `app_segment_clean` through `vacuum.send_command`; [params] are the segment ids. */
@Serializable
data class VacuumSegmentClean(
    @SerialName("entity_id") val entityId: EntityId,
    val params: List<JsonPrimitive>,
    val command: String = APP_SEGMENT_CLEAN,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.VACUUM_SEND_COMMAND

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))

    companion object {
        const val APP_SEGMENT_CLEAN = "app_segment_clean"

        /** Numeric segment ids are sent as numbers, as Roborock expects. */
        fun of(entityId: EntityId, segmentIds: List<String>): VacuumSegmentClean = VacuumSegmentClean(
            entityId = entityId,
            params = segmentIds.map { id -> id.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(id) },
        )
    }
}

/** An all-day event takes dates; any other takes local date-times (`2026-11-17 09:30:00`). */
@Serializable
data class CalendarCreateEvent(
    @SerialName("entity_id") val entityId: EntityId,
    val summary: String,
    val location: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("start_date_time") val startDateTime: String? = null,
    @SerialName("end_date_time") val endDateTime: String? = null,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.CALENDAR_CREATE_EVENT

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** One step of a media player's volume, up or down. */
@Serializable
data class MediaPlayerVolumeStep(
    @SerialName("entity_id") val entityId: EntityId,
    @Transient val isUp: Boolean = true,
) : ServiceRequest {
    override val service: ServiceRef
        get() = if (isUp) ServiceRef.MEDIA_PLAYER_VOLUME_UP else ServiceRef.MEDIA_PLAYER_VOLUME_DOWN

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

@Serializable
data class MediaPlayerPlayMedia(
    @SerialName("entity_id") val entityId: EntityId,
    @SerialName("media_content_id") val mediaContentId: String,
    @SerialName("media_content_type") val mediaContentType: String,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.MEDIA_PLAYER_PLAY_MEDIA

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

@Serializable
data class MediaPlayerClearPlaylist(@SerialName("entity_id") val entityId: EntityId) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.MEDIA_PLAYER_CLEAR_PLAYLIST

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** The daily forecast; call it with a response. */
@Serializable
data class WeatherGetForecasts(
    @SerialName("entity_id") val entityId: EntityId,
    val type: String = DAILY,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.WEATHER_GET_FORECASTS

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))

    companion object {
        const val DAILY = "daily"
    }
}

/** A to-do list's items; call it with a response. */
@Serializable
data class TodoGetItems(@SerialName("entity_id") val entityId: EntityId) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef.TODO_GET_ITEMS

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** Music Assistant's `play_media`: a Music Assistant URI, played now or queued as [enqueue] says. */
@Serializable
data class MusicAssistantPlayMedia(
    @SerialName("entity_id") val entityId: EntityId,
    @SerialName("media_id") val mediaId: String,
    val enqueue: String,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef(Domain.MUSIC_ASSISTANT, "play_media")

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** Call with a response. */
@Serializable
data class MusicAssistantSearch(
    @SerialName("config_entry_id") val configEntryId: String,
    val name: String,
    val limit: Int,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef(Domain.MUSIC_ASSISTANT, "search")

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** Call with a response. */
@Serializable
data class MusicAssistantGetLibrary(
    @SerialName("config_entry_id") val configEntryId: String,
    @SerialName("media_type") val mediaType: String,
    val limit: Int,
    val offset: Int,
) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef(Domain.MUSIC_ASSISTANT, "get_library")

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}

/** Call with a response. */
@Serializable
data class MusicAssistantGetQueue(@SerialName("entity_id") val entityId: EntityId) : ServiceRequest {
    override val service: ServiceRef get() = ServiceRef(Domain.MUSIC_ASSISTANT, "get_queue")

    override fun data(): ServiceData = ServiceData(encodePayload(serializer(), this))
}
