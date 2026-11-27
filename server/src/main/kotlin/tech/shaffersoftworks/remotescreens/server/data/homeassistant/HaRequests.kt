package tech.shaffersoftworks.remotescreens.server.data.homeassistant

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import tech.shaffersoftworks.remotescreens.server.assist.AssistPipelineCatalog

// The WebSocket commands the add-on sends. Each names its `type` and the type of its `result`.

data object GetConfig : HaRequest<HaConfigDto> {
    override val type: String get() = "get_config"
    override val resultSerializer: KSerializer<HaConfigDto> get() = HaConfigDto.serializer()
}

data object GetStates : HaRequest<List<EntityStateDto>> {
    override val type: String get() = "get_states"
    override val resultSerializer: KSerializer<List<EntityStateDto>>
        get() = SkippingListSerializer(EntityStateDto.serializer())
}

@Serializable
data class SubscribeEvents(@SerialName("event_type") val eventType: String) : HaRequest<Unit> {
    override val type: String get() = "subscribe_events"
    override val resultSerializer: KSerializer<Unit> get() = NoResult

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

/** Fires [eventType] on Home Assistant's event bus, with [eventData] for automations to read. */
@Serializable
data class FireEvent(
    @SerialName("event_type") val eventType: String,
    @SerialName("event_data") val eventData: JsonObject? = null,
) : HaRequest<Unit> {
    override val type: String get() = "fire_event"
    override val resultSerializer: KSerializer<Unit> get() = NoResult

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

/** The entities a service call acts on. */
@Serializable
data class ServiceTarget(@SerialName("entity_id") val entityIds: List<String>)

/** `call_service`; its result is the service's response when [returnResponse] is set. */
@Serializable
data class CallService(
    val domain: String,
    val service: String,
    @SerialName("service_data") val serviceData: JsonObject,
    val target: ServiceTarget? = null,
    @SerialName("return_response") val returnResponse: Boolean? = null,
) : HaRequest<JsonElement?> {
    override val type: String get() = "call_service"
    override val resultSerializer: KSerializer<JsonElement?> get() = JsonElement.serializer().nullable

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

data object AreaRegistryList : HaRequest<List<AreaEntry>> {
    override val type: String get() = "config/area_registry/list"
    override val resultSerializer: KSerializer<List<AreaEntry>> get() = SkippingListSerializer(AreaEntry.serializer())
}

data object DeviceRegistryList : HaRequest<List<DeviceEntry>> {
    override val type: String get() = "config/device_registry/list"
    override val resultSerializer: KSerializer<List<DeviceEntry>> get() = SkippingListSerializer(
        DeviceEntry.serializer(),
    )
}

data object EntityRegistryListForDisplay : HaRequest<DisplayEntityRegistry> {
    override val type: String get() = "config/entity_registry/list_for_display"
    override val resultSerializer: KSerializer<DisplayEntityRegistry> get() = DisplayEntityRegistry.serializer()
}

@Serializable
data class EntityRegistryGet(@SerialName("entity_id") val entityId: String) : HaRequest<EntityRegistryEntry> {
    override val type: String get() = "config/entity_registry/get"
    override val resultSerializer: KSerializer<EntityRegistryEntry> get() = EntityRegistryEntry.serializer()

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

/** A minimal history: per entity, rows of state `s` at `lu` (or `lc`) seconds. */
@Serializable
data class HistoryDuringPeriod(
    @SerialName("start_time") val startTime: String,
    @SerialName("entity_ids") val entityIds: List<String>,
    @SerialName("minimal_response") val minimalResponse: Boolean = true,
    @SerialName("no_attributes") val noAttributes: Boolean = true,
    @SerialName("significant_changes_only") val significantChangesOnly: Boolean = false,
) : HaRequest<Map<String, List<HistoryRow>>> {
    override val type: String get() = "history/history_during_period"
    override val resultSerializer: KSerializer<Map<String, List<HistoryRow>>>
        get() = HISTORY_RESULT

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

data object EnergyGetPrefs : HaRequest<EnergyPreferences> {
    override val type: String get() = "energy/get_prefs"
    override val resultSerializer: KSerializer<EnergyPreferences> get() = EnergyPreferences.serializer()
}

/** Recorder statistics per id: rows with a `start` in ms and the `change` over the period. */
@Serializable
data class StatisticsDuringPeriod(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val period: String,
    @SerialName("statistic_ids") val statisticIds: List<String>,
    val types: List<String>,
    val units: Map<String, String>,
) : HaRequest<Map<String, List<StatisticRow>>> {
    override val type: String get() = "recorder/statistics_during_period"
    override val resultSerializer: KSerializer<Map<String, List<StatisticRow>>>
        get() = STATISTICS_RESULT

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

/** Fossil energy per period start: the kWh of grid energy that came from fossil fuels. */
@Serializable
data class FossilEnergyConsumption(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("energy_statistic_ids") val energyStatisticIds: List<String>,
    val period: String,
    @SerialName("co2_statistic_id") val co2StatisticId: String,
) : HaRequest<Map<String, Double?>> {
    override val type: String get() = "energy/fossil_energy_consumption"
    override val resultSerializer: KSerializer<Map<String, Double?>> get() = FOSSIL_RESULT

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

@Serializable
data class BrowseMediaSource(
    @SerialName("media_content_id") val mediaContentId: String,
) : HaRequest<MediaSourceNode> {
    override val type: String get() = "media_source/browse_media"
    override val resultSerializer: KSerializer<MediaSourceNode> get() = MediaSourceNode.serializer()

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

@Serializable
data class ResolveMediaSource(
    @SerialName("media_content_id") val mediaContentId: String,
) : HaRequest<ResolvedMedia> {
    override val type: String get() = "media_source/resolve_media"
    override val resultSerializer: KSerializer<ResolvedMedia> get() = ResolvedMedia.serializer()

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

@Serializable
data class MediaSourceNode(
    @SerialName("media_content_id") val mediaContentId: String? = null,
    @SerialName("media_content_type") val mediaContentType: String? = null,
    @SerialName("media_class") val mediaClass: String? = null,
    @SerialName("can_expand") val canExpand: Boolean = false,
    val children: List<MediaSourceNode> = emptyList(),
)

@Serializable
data class ResolvedMedia(
    val url: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
)

@Serializable
data class BrowseMedia(@SerialName("entity_id") val entityId: String) : HaRequest<BrowseMediaResult> {
    override val type: String get() = "media_player/browse_media"
    override val resultSerializer: KSerializer<BrowseMediaResult> get() = BrowseMediaResult.serializer()

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

@Serializable
data class GetTranslations(val language: String, val category: String) : HaRequest<TranslationsResult> {
    override val type: String get() = "frontend/get_translations"
    override val resultSerializer: KSerializer<TranslationsResult> get() = TranslationsResult.serializer()

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

/** Starts the camera's stream; the answer is Home Assistant's own URL of it. */
@Serializable
data class CameraStream(
    @SerialName("entity_id") val entityId: String,
    val format: String = "hls",
) : HaRequest<CameraStreamResult> {
    override val type: String get() = "camera/stream"
    override val resultSerializer: KSerializer<CameraStreamResult> get() = CameraStreamResult.serializer()

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}

/** Assist pipelines configured in Home Assistant. A failure leaves the screen on the preferred pipeline. */
data object ListAssistPipelines : HaRequest<AssistPipelineCatalog> {
    override val type: String get() = "assist_pipeline/pipeline/list"
    override val resultSerializer: KSerializer<AssistPipelineCatalog> get() = AssistPipelineCatalog.serializer()
}

@Serializable
data class CalendarEventDelete(
    @SerialName("entity_id") val entityId: String,
    val uid: String,
    @SerialName("recurrence_id") val recurrenceId: String? = null,
) : HaRequest<Unit> {
    override val type: String get() = "calendar/event/delete"
    override val resultSerializer: KSerializer<Unit> get() = NoResult

    override fun payload(): JsonObject = encodePayload(serializer(), this)
}
