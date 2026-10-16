package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.render.BrowseItem
import tech.shaffersoftworks.remotescreens.server.render.EnergyPrefs
import tech.shaffersoftworks.remotescreens.server.render.Forecast
import tech.shaffersoftworks.remotescreens.server.render.RenderData
import tech.shaffersoftworks.remotescreens.server.render.TodoItem
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private const val MS_PER_MINUTE = 60_000L
private const val SECONDS_PER_HOUR = 3600L
private const val FORECAST_TTL_MS = 15 * MS_PER_MINUTE
private const val HISTORY_TTL_MS = 5 * MS_PER_MINUTE
private const val BROWSE_TTL_MS = 10 * MS_PER_MINUTE
private const val ENERGY_TTL_MS = 30 * MS_PER_MINUTE
private const val DEFAULT_HISTORY_HOURS = 24

/** A sample of an entity's history: when, in epoch milliseconds, and its value. */
typealias HistoryPoint = Pair<Long, Double>

/**
 * Fetches what documents draw that is not an entity's state: forecasts, history, the energy setup, a player's
 * library. Each is kept for a while, so a page of five weather tiles asks Home Assistant once.
 */
class RenderDataRepository(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(RenderDataRepository::class.java)

    private data class Cached<T>(val value: T, val fetchedAtMs: Long) {
        fun isFresh(ttlMs: Long): Boolean = System.currentTimeMillis() - fetchedAtMs < ttlMs
    }

    private val forecasts = ConcurrentHashMap<String, Cached<List<Forecast>>>()
    private val histories = ConcurrentHashMap<String, Cached<List<HistoryPoint>>>()
    private val libraries = ConcurrentHashMap<String, Cached<List<BrowseItem>>>()
    private val todos = ConcurrentHashMap<String, Cached<List<TodoItem>>>()

    @Volatile
    private var energyPreferences: Cached<EnergyPrefs?>? = null

    /** What the screen needs, for its tiles and for any card that may be opened from them. */
    suspend fun forScreen(screen: Screen): RenderData {
        val tiles = screen.pages.flatMap { it.allTiles() }
        val weatherEntities = tiles.mapNotNull { it.entityId }.filter { it.startsWith("weather.") }.distinct()
        val graphedEntities = tiles.filter(::isGraphed).mapNotNull { it.entityId }.distinct()
        val mediaPlayers = tiles.filter { it.effectiveKind() == "media_player" }.mapNotNull { it.entityId }.distinct()
        val needsEnergy = tiles.any { it.effectiveKind() == "energy" }
        val historyHours = tiles.firstOrNull { it.effectiveKind() == "history" }
            ?.options?.get("hours")?.toIntOrNull()
            ?: DEFAULT_HISTORY_HOURS
        return RenderData(
            forecasts = weatherEntities.associateWith { forecast(it) },
            history = graphedEntities.associateWith { history(it, historyHours) },
            energy = if (needsEnergy) energy() else null,
            browse = mediaPlayers.associateWith { browse(it) },
        )
    }

    /** What the card of [entityId], or of a [kind] with no entity such as energy, needs. */
    suspend fun forCard(entityId: String?, kind: String?): RenderData = when {
        entityId == null -> RenderData(energy = if (kind == "energy") energy() else null)
        entityId.startsWith("weather.") -> RenderData(forecasts = mapOf(entityId to forecast(entityId)))
        kind == "history" || entityId.startsWith("sensor.") || entityId.startsWith("binary_sensor.") ->
            RenderData(history = mapOf(entityId to history(entityId, DEFAULT_HISTORY_HOURS)))
        entityId.startsWith("media_player.") -> RenderData(browse = mapOf(entityId to browse(entityId)))
        entityId.startsWith("todo.") -> RenderData(todos = mapOf(entityId to todoItems(entityId)))
        else -> RenderData()
    }

    suspend fun forecast(entityId: String): List<Forecast> = cached(forecasts, entityId, FORECAST_TTL_MS) {
        val request = buildJsonObject {
            put("entity_id", entityId)
            put("type", "daily")
        }
        val result = homeAssistant.callService("weather", "get_forecasts", request, returnResponse = true)
        HomeAssistantResponseParser.forecasts(result, entityId)
    }

    suspend fun history(entityId: String, hours: Int): List<HistoryPoint> =
        cached(histories, "$entityId@$hours", HISTORY_TTL_MS) {
            val request = buildJsonObject {
                put("start_time", Instant.now().minusSeconds(hours * SECONDS_PER_HOUR).toString())
                put("entity_ids", buildJsonArray { add(JsonPrimitive(entityId)) })
                put("minimal_response", true)
                put("no_attributes", true)
                put("significant_changes_only", false)
            }
            val response = homeAssistant.command("history/history_during_period", request)
            HomeAssistantResponseParser.history(response, entityId)
        }

    suspend fun energy(): EnergyPrefs? {
        energyPreferences?.takeIf { it.isFresh(ENERGY_TTL_MS) }?.let { return it.value }
        val value = runCatching {
            (homeAssistant.command("energy/get_prefs") as? JsonObject)?.let(HomeAssistantResponseParser::energy)
        }.onFailure { logger.debug("No energy preferences: {}", it.message) }.getOrNull()
        energyPreferences = Cached(value, System.currentTimeMillis())
        return value
    }

    suspend fun browse(entityId: String): List<BrowseItem> = cached(libraries, entityId, BROWSE_TTL_MS) {
        val request = buildJsonObject { put("entity_id", entityId) }
        HomeAssistantResponseParser.library(homeAssistant.command("media_player/browse_media", request))
    }

    suspend fun todoItems(entityId: String): List<TodoItem> = cached(todos, entityId, BROWSE_TTL_MS) {
        val request = buildJsonObject { put("entity_id", entityId) }
        val result = homeAssistant.callService("todo", "get_items", request, returnResponse = true)
        HomeAssistantResponseParser.todos(result, entityId)
    }

    fun clear() {
        forecasts.clear()
        histories.clear()
        libraries.clear()
        todos.clear()
        energyPreferences = null
    }

    private fun isGraphed(tile: Tile): Boolean =
        tile.effectiveKind() == "history" || (tile.effectiveKind() == "sensor" && tile.options["graph"] == "true")

    private suspend fun <T> cached(
        cache: ConcurrentHashMap<String, Cached<List<T>>>,
        key: String,
        ttlMs: Long,
        load: suspend () -> List<T>,
    ): List<T> {
        cache[key]?.takeIf { it.isFresh(ttlMs) }?.let { return it.value }
        val value = runCatching { load() }
            .onFailure { logger.debug("Fetch {} failed: {}", key, it.message) }
            .getOrDefault(cache[key]?.value.orEmpty())
        cache[key] = Cached(value, System.currentTimeMillis())
        return value
    }
}
