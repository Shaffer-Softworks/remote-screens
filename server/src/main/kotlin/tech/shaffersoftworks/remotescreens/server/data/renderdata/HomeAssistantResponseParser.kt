package tech.shaffersoftworks.remotescreens.server.data.renderdata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.BrowseMediaResult
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.Domain
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergyPreferences
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EnergySource
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HA_JSON
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HistoryRow
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.SkippingListSerializer
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.SwitchState
import java.time.OffsetDateTime

private const val MS_PER_SECOND = 1000
private const val DEFAULT_CONTENT_TYPE = "music"
private const val NEEDS_ACTION = "needs_action"

/** One day of `weather.get_forecasts`. */
@Serializable
internal data class ForecastEntry(
    val datetime: String? = null,
    val condition: String? = null,
    val temperature: Double? = null,
    val templow: Double? = null,
    @SerialName("precipitation_probability") val precipitationProbability: Double? = null,
)

@Serializable
internal data class ForecastResponse(@Serializable(
    with = ForecastEntries::class,
) val forecast: List<ForecastEntry> = emptyList(),)

/** One item of `todo.get_items`. */
@Serializable
internal data class TodoEntry(val uid: String? = null, val summary: String? = null, val status: String? = null)

@Serializable
internal data class TodoResponse(@Serializable(with = TodoEntries::class) val items: List<TodoEntry> = emptyList())

internal object ForecastEntries : SkippingListSerializer<ForecastEntry>(ForecastEntry.serializer())

internal object TodoEntries : SkippingListSerializer<TodoEntry>(TodoEntry.serializer())

/** Reads the answers of Home Assistant's commands into what documents draw; anything unreadable is left out. */
internal object HomeAssistantResponseParser {

    /** The answer of `weather.get_forecasts` for [entityId], with or without its `response` wrapper. */
    fun forecasts(result: JsonElement?, entityId: String): List<Forecast> =
        serviceResponse(
            result = result,
            entityId = entityId,
            serializer = ForecastResponse.serializer()
        )?.forecast.orEmpty().mapNotNull(::forecast)

    /** The rows of a minimal `history/history_during_period` answer: `s` is the state, `lu` or `lc` its time. */
    fun history(result: Map<String, List<HistoryRow>>, entityId: String): List<HistoryPoint> {
        val isBinary = EntityId(entityId).domain == Domain.BINARY_SENSOR

        return result[entityId].orEmpty().mapNotNull { row -> historyPoint(row, isBinary) }
    }

    fun energy(preferences: EnergyPreferences): EnergyPrefs {
        fun rateOf(type: String) = preferences.energySources.firstOrNull { it.type == type }?.powerSensor

        val devices = preferences.deviceConsumption.mapNotNull { device ->
            device.powerSensor?.let { sensor -> (device.name ?: sensor) to sensor }
        }

        return EnergyPrefs(
            solar = rateOf(EnergySource.SOLAR),
            grid = rateOf(EnergySource.GRID),
            battery = rateOf(EnergySource.BATTERY),
            devices = devices,
        )
    }

    /** The children of a `media_player/browse_media` answer. */
    fun library(result: BrowseMediaResult): List<BrowseItem> = result.children.map { child ->
        BrowseItem(
            title = child.title,
            contentId = child.mediaContentId,
            contentType = child.mediaContentType ?: DEFAULT_CONTENT_TYPE,
            canPlay = child.canPlay,
            canExpand = child.canExpand,
            thumbnail = child.thumbnail,
        )
    }

    /** Incomplete items from `todo.get_items` for [entityId]. */
    fun todos(result: JsonElement?, entityId: String): List<TodoItem> =
        serviceResponse(
            result = result,
            entityId = entityId,
            serializer = TodoResponse.serializer()
        )?.items.orEmpty().mapNotNull { item ->
            val summary = item.summary

            when {
                item.status != null && item.status != NEEDS_ACTION -> null
                summary == null -> null
                else -> TodoItem(uid = item.uid ?: summary, summary = summary)
            }
        }

    /** [entityId]'s part of a service response, with or without its `response` wrapper. */
    private fun <T> serviceResponse(result: JsonElement?, entityId: String, serializer: KSerializer<T>): T? {
        val response = (result as? JsonObject)?.let { it["response"] as? JsonObject ?: it }
        val part = response?.get(entityId) as? JsonObject ?: return null

        return runCatching { HA_JSON.decodeFromJsonElement(serializer, part) }.getOrNull()
    }

    private fun forecast(forecast: ForecastEntry): Forecast? {
        val time = forecast.datetime
            ?.let { runCatching { OffsetDateTime.parse(it).toZonedDateTime() }.getOrNull() }
            ?: return null

        return Forecast(
            time = time,
            condition = forecast.condition.orEmpty(),
            high = forecast.temperature,
            low = forecast.templow,
            rain = forecast.precipitationProbability?.toInt(),
        )
    }

    private fun historyPoint(row: HistoryRow, isBinary: Boolean): HistoryPoint? {
        val state = row.state

        val value = if (isBinary) {
            state?.let { if (it == SwitchState.ON.wire) 1.0 else 0.0 }
        } else {
            state?.toDoubleOrNull()
        }

        val timeMs = (row.lastUpdated ?: row.lastChanged)?.let { (it * MS_PER_SECOND).toLong() }

        return if (value != null && timeMs != null) timeMs to value else null
    }
}
