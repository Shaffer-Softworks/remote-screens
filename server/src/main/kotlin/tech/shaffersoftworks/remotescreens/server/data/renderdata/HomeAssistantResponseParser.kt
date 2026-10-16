package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.render.BrowseItem
import tech.shaffersoftworks.remotescreens.server.render.EnergyPrefs
import tech.shaffersoftworks.remotescreens.server.render.Forecast
import tech.shaffersoftworks.remotescreens.server.render.TodoItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.OffsetDateTime

private const val MS_PER_SECOND = 1000

/** Reads the answers of Home Assistant's commands into what documents draw; anything unreadable is left out. */
internal object HomeAssistantResponseParser {

    /** The answer of `weather.get_forecasts` for [entityId], with or without its `response` wrapper. */
    fun forecasts(result: Any?, entityId: String): List<Forecast> {
        val response = (result as? JsonObject)?.let { it["response"] as? JsonObject ?: it }
        val forecastList = (response?.get(entityId) as? JsonObject)?.get("forecast") as? JsonArray
        return forecastList.orEmpty().mapNotNull { (it as? JsonObject)?.let(::forecast) }
    }

    /** The rows of a minimal `history/history_during_period` answer: `s` is the state, `lu` or `lc` its time. */
    fun history(result: Any?, entityId: String): List<HistoryPoint> {
        val rows = (result as? JsonObject)?.get(entityId) as? JsonArray
        val isBinary = entityId.startsWith("binary_sensor.")
        return rows.orEmpty().mapNotNull { (it as? JsonObject)?.let { row -> historyPoint(row, isBinary) } }
    }

    fun energy(preferences: JsonObject): EnergyPrefs {
        val sources = (preferences["energy_sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun rateOf(type: String) = sources.firstOrNull { it.text("type") == type }?.powerSensor()
        val devices = (preferences["device_consumption"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { device -> device.powerSensor()?.let { sensor -> (device.text("name") ?: sensor) to sensor } }
        return EnergyPrefs(rateOf("solar"), rateOf("grid"), rateOf("battery"), devices)
    }

    /** The children of a `media_player/browse_media` answer. */
    fun library(result: Any?): List<BrowseItem> =
        ((result as? JsonObject)?.get("children") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::browseItem) }

    /** Incomplete items from `todo.get_items` for [entityId]. */
    fun todos(result: Any?, entityId: String): List<TodoItem> {
        val response = (result as? JsonObject)?.let { it["response"] as? JsonObject ?: it }
        val items = (response?.get(entityId) as? JsonObject)?.get("items") as? JsonArray
        return items.orEmpty().mapNotNull { (it as? JsonObject)?.let(::todoItem) }
    }

    private fun todoItem(item: JsonObject): TodoItem? {
        val status = item.text("status")
        val summary = item.text("summary")
        return when {
            status != null && status != "needs_action" -> null
            summary == null -> null
            else -> TodoItem(uid = item.text("uid") ?: summary, summary = summary)
        }
    }

    private fun forecast(forecast: JsonObject): Forecast? {
        val time = forecast.text("datetime")
            ?.let { runCatching { OffsetDateTime.parse(it).toZonedDateTime() }.getOrNull() }
            ?: return null
        return Forecast(
            time,
            forecast.text("condition").orEmpty(),
            forecast.number("temperature"),
            forecast.number("templow"),
            forecast.number("precipitation_probability")?.toInt(),
        )
    }

    private fun historyPoint(row: JsonObject, isBinary: Boolean): HistoryPoint? {
        val state = row.text("s")
        val value = if (isBinary) state?.let { if (it == "on") 1.0 else 0.0 } else state?.toDoubleOrNull()
        val timeMs = (row.number("lu") ?: row.number("lc"))?.let { (it * MS_PER_SECOND).toLong() }
        return if (value != null && timeMs != null) timeMs to value else null
    }

    private fun browseItem(child: JsonObject): BrowseItem? {
        val title = child.text("title")
        val contentId = child.text("media_content_id")
        if (title == null || contentId == null) return null
        return BrowseItem(
            title = title,
            contentId = contentId,
            contentType = child.text("media_content_type") ?: "music",
            canPlay = child.boolean("can_play"),
            canExpand = child.boolean("can_expand"),
            thumbnail = child.text("thumbnail"),
        )
    }

    private fun JsonObject.powerSensor(): String? = text("stat_rate") ?: text("stat_power")

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
}
