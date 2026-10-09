package dev.remotescreens.server

import dev.remotescreens.server.ha.HomeAssistant
import dev.remotescreens.server.render.BrowseItem
import dev.remotescreens.server.render.EnergyPrefs
import dev.remotescreens.server.render.Forecast
import dev.remotescreens.server.render.RenderData
import dev.remotescreens.shared.Screen
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Fetches what documents draw that is not an entity's state: forecasts, history, the energy setup, a player's
 * library. Each is kept for a while, so a page of five weather tiles asks Home Assistant once.
 */
class DataFetcher(private val ha: HomeAssistant) {
    private val log = LoggerFactory.getLogger(DataFetcher::class.java)
    private data class Cached<T>(val value: T, val at: Long)

    private val forecasts = HashMap<String, Cached<List<Forecast>>>()
    private val history = HashMap<String, Cached<List<Pair<Long, Double>>>>()
    private val browse = HashMap<String, Cached<List<BrowseItem>>>()
    private var energy: Cached<EnergyPrefs?>? = null

    /** What the screen needs, for its tiles and for any card that may be opened from them. */
    suspend fun forScreen(screen: Screen): RenderData {
        val tiles = screen.pages.flatMap { it.allTiles() }
        val weather = tiles.mapNotNull { it.entityId }.filter { it.startsWith("weather.") }.distinct()
        val graphs = tiles.filter { it.effectiveKind() == "history" || (it.effectiveKind() == "sensor" && it.options["graph"] == "true") }
            .mapNotNull { it.entityId }.distinct()
        val players = tiles.filter { it.effectiveKind() == "media_player" }.mapNotNull { it.entityId }.distinct()
        val needsEnergy = tiles.any { it.effectiveKind() == "energy" }
        val hours = tiles.firstOrNull { it.effectiveKind() == "history" }?.options?.get("hours")?.toIntOrNull() ?: 24
        return RenderData(
            forecasts = weather.associateWith { forecast(it) },
            history = graphs.associateWith { history(it, hours) },
            energy = if (needsEnergy) energy() else null,
            browse = players.associateWith { browse(it) },
        )
    }

    suspend fun forCard(entityId: String?, kind: String?): RenderData {
        if (entityId == null) return RenderData(energy = if (kind == "energy") energy() else null)
        return when {
            entityId.startsWith("weather.") -> RenderData(forecasts = mapOf(entityId to forecast(entityId)))
            kind == "history" || entityId.startsWith("sensor.") || entityId.startsWith("binary_sensor.") -> RenderData(history = mapOf(entityId to history(entityId, 24)))
            entityId.startsWith("media_player.") -> RenderData(browse = mapOf(entityId to browse(entityId)))
            else -> RenderData()
        }
    }

    suspend fun forecast(entityId: String): List<Forecast> = cached(forecasts, entityId, 15 * 60_000L) {
        val result = ha.callService("weather", "get_forecasts", buildJsonObject {
            put("entity_id", entityId)
            put("type", "daily")
        }, returnResponse = true) ?: return@cached emptyList()
        val root = (result as? JsonObject)?.let { it["response"] as? JsonObject ?: it } ?: return@cached emptyList()
        val list = (root[entityId] as? JsonObject)?.get("forecast") as? JsonArray ?: return@cached emptyList()
        list.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val time = o.str("datetime")?.let { runCatching { OffsetDateTime.parse(it).toZonedDateTime() }.getOrNull() } ?: return@mapNotNull null
            Forecast(time, o.str("condition") ?: "", o.num("temperature"), o.num("templow"), o.num("precipitation_probability")?.toInt())
        }
    }

    suspend fun history(entityId: String, hours: Int): List<Pair<Long, Double>> = cached(history, "$entityId@$hours", 5 * 60_000L) {
        val start = Instant.now().minusSeconds(hours * 3600L)
        val result = ha.command("history/history_during_period", buildJsonObject {
            put("start_time", start.toString())
            put("entity_ids", buildJsonArray { add(JsonPrimitive(entityId)) })
            put("minimal_response", true)
            put("no_attributes", true)
            put("significant_changes_only", false)
        }) as? JsonObject ?: return@cached emptyList()
        val rows = result[entityId] as? JsonArray ?: return@cached emptyList()
        val binary = entityId.startsWith("binary_sensor.")
        rows.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val s = o.str("s") ?: return@mapNotNull null
            val v = if (binary) (if (s == "on") 1.0 else 0.0) else s.toDoubleOrNull() ?: return@mapNotNull null
            val at = (o.num("lu") ?: o.num("lc"))?.let { (it * 1000).toLong() } ?: return@mapNotNull null
            at to v
        }
    }

    suspend fun energy(): EnergyPrefs? {
        energy?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000L }?.let { return it.value }
        val value = runCatching {
            val prefs = ha.command("energy/get_prefs") as? JsonObject
            prefs?.let { p ->
                val sources = (p["energy_sources"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                fun rate(type: String) = sources.firstOrNull { it.str("type") == type }?.let { it.str("stat_rate") ?: it.str("stat_power") }
                val devices = (p["device_consumption"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { d ->
                    val sensor = d.str("stat_rate") ?: d.str("stat_power") ?: return@mapNotNull null
                    (d.str("name") ?: sensor) to sensor
                }
                EnergyPrefs(rate("solar"), rate("grid"), rate("battery"), devices)
            }
        }.onFailure { log.debug("No energy preferences: {}", it.message) }.getOrNull()
        energy = Cached(value, System.currentTimeMillis())
        return value
    }

    suspend fun browse(entityId: String): List<BrowseItem> = cached(browse, entityId, 10 * 60_000L) {
        val result = ha.command("media_player/browse_media", buildJsonObject { put("entity_id", entityId) }) as? JsonObject
            ?: return@cached emptyList()
        (result["children"] as? JsonArray).orEmpty().mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            BrowseItem(
                title = o.str("title") ?: return@mapNotNull null,
                contentId = o.str("media_content_id") ?: return@mapNotNull null,
                contentType = o.str("media_content_type") ?: "music",
                canPlay = (o["can_play"] as? JsonPrimitive)?.booleanOrNull ?: false,
                canExpand = (o["can_expand"] as? JsonPrimitive)?.booleanOrNull ?: false,
                thumbnail = o.str("thumbnail"),
            )
        }
    }

    fun forget() {
        forecasts.clear(); history.clear(); browse.clear(); energy = null
    }

    private suspend fun <T> cached(map: HashMap<String, Cached<List<T>>>, key: String, ttl: Long, load: suspend () -> List<T>): List<T> {
        map[key]?.takeIf { System.currentTimeMillis() - it.at < ttl }?.let { return it.value }
        val value = runCatching { load() }.onFailure { log.debug("Fetch {} failed: {}", key, it.message) }.getOrDefault(map[key]?.value ?: emptyList())
        map[key] = Cached(value, System.currentTimeMillis())
        return value
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
}