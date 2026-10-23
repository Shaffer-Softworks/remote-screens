package tech.shaffersoftworks.remotescreens.server.data.renderdata

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.music.MusicAssistantClient
import tech.shaffersoftworks.remotescreens.server.data.music.MusicListRow
import tech.shaffersoftworks.remotescreens.server.data.music.MusicQueueSnapshot
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiStore
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private const val MS_PER_MINUTE = 60_000L
private const val SECONDS_PER_HOUR = 3600L
private const val FORECAST_TTL_MS = 15 * MS_PER_MINUTE
private const val HISTORY_TTL_MS = 5 * MS_PER_MINUTE
private const val BROWSE_TTL_MS = 10 * MS_PER_MINUTE
private const val ENERGY_TTL_MS = 30 * MS_PER_MINUTE
private const val MUSIC_TTL_MS = 30_000L
private const val DEFAULT_HISTORY_HOURS = 24

/** A sample of an entity's history: when, in epoch milliseconds, and its value. */
typealias HistoryPoint = Pair<Long, Double>

/**
 * Fetches what documents draw that is not an entity's state: forecasts, history, the energy setup, a player's
 * library, Music Assistant browse/queue. Each is kept for a while, so a page of five weather tiles asks once.
 */
class RenderDataRepository(
    private val homeAssistant: HomeAssistant,
    private val musicClient: MusicAssistantClient = MusicAssistantClient(homeAssistant),
    private val musicUiStore: MusicUiStore = MusicUiStore(),
    val calendarAgenda: CalendarAgendaStore = CalendarAgendaStore(homeAssistant),
) {
    private val logger = LoggerFactory.getLogger(RenderDataRepository::class.java)

    private data class Cached<T>(val value: T, val fetchedAtMs: Long) {
        fun isFresh(ttlMs: Long): Boolean = System.currentTimeMillis() - fetchedAtMs < ttlMs
    }

    private val forecasts = ConcurrentHashMap<String, Cached<List<Forecast>>>()
    private val histories = ConcurrentHashMap<String, Cached<List<HistoryPoint>>>()
    private val libraries = ConcurrentHashMap<String, Cached<List<BrowseItem>>>()
    private val todos = ConcurrentHashMap<String, Cached<List<TodoItem>>>()
    private val musicQueueCache = ConcurrentHashMap<String, Cached<MusicQueueSnapshot>>()
    private val configEntryCache = ConcurrentHashMap<String, Cached<String>>()

    val musicUi: MusicUiStore get() = musicUiStore
    val music: MusicAssistantClient get() = musicClient

    /** What the screen needs, for its tiles and for any card that may be opened from them. */
    suspend fun forScreen(screen: Screen): RenderData {
        val tiles = screen.pages.flatMap { it.allTiles() }
        val weatherEntities = tiles.mapNotNull { it.entityId }.filter { it.startsWith(
            "weather.",
        ) }.distinct()
        val todoEntities = tiles.mapNotNull { it.entityId }.filter { it.startsWith(
            "todo.",
        ) }.distinct()
        val graphedEntities = tiles.filter(::isGraphed).mapNotNull { it.entityId }.distinct()
        val mediaPlayers = tiles.filter { it.effectiveKind() == "media_player" }.mapNotNull { it.entityId }.distinct()
        val musicTiles = tiles.filter { it.effectiveKind() == "music" }
        val needsAgenda = tiles.any { it.full && it.effectiveKind() == "calendar" }
        val needsEnergy = tiles.any { it.effectiveKind() == "energy" }
        val historyHours = tiles.firstOrNull { it.effectiveKind() == "history" }
            ?.options?.get("hours")?.toIntOrNull()
            ?: DEFAULT_HISTORY_HOURS
        // Drop cached to-dos so a complete on the page is visible after the next rebuild.
        for (entityId in todoEntities) todos.remove(entityId)
        val musicPayload = musicPayload(musicTiles)
        val zone = runCatching { ZoneId.of(homeAssistant.config.value.timeZone) }.getOrDefault(ZoneId.of("UTC"))
        val agenda = if (needsAgenda) calendarAgenda.snapshot(homeAssistant.states.value, zone) else null
        return RenderData(
            forecasts = weatherEntities.associateWith { forecast(it) },
            history = graphedEntities.associateWith { history(it, historyHours) },
            energy = if (needsEnergy) energy() else null,
            browse = mediaPlayers.associateWith { browse(it) },
            todos = todoEntities.associateWith { todoItems(it) },
            musicBrowse = musicPayload.browse,
            musicQueue = musicPayload.queue,
            musicConfigEntry = musicPayload.configEntries,
            musicUi = musicPayload.ui,
            musicBrowseHasMore = musicPayload.hasMore,
            agenda = agenda,
            agendaNotices = calendarAgenda.notices(),
        )
    }

    /** What the card of [entityId], or of a [kind] with no entity such as energy, needs. */
    suspend fun forCard(entityId: String?, kind: String?): RenderData = when {
        entityId == null -> RenderData(energy = if (kind == "energy") energy() else null)
        entityId.startsWith(
            "weather.",
        ) -> RenderData(forecasts = mapOf(entityId to forecast(entityId)))
        kind == "history" || entityId.startsWith(
            "sensor.",
        ) || entityId.startsWith("binary_sensor.") ->
            RenderData(history = mapOf(entityId to history(entityId, DEFAULT_HISTORY_HOURS)))
        entityId.startsWith(
            "media_player.",
        ) -> RenderData(browse = mapOf(entityId to browse(entityId)))
        entityId.startsWith("todo.") -> RenderData(todos = mapOf(entityId to todoItems(entityId)))
        else -> RenderData()
    }

    fun invalidateMusic(tileId: String? = null, playerEntityId: String? = null) {
        if (tileId != null) musicUiStore.clearBrowse(tileId)
        if (playerEntityId != null) {
            musicQueueCache.remove(playerEntityId)
        } else {
            musicQueueCache.clear()
        }
    }

    suspend fun forecast(entityId: String): List<Forecast> = cached(
        forecasts,
        entityId,
        FORECAST_TTL_MS,
    ) {
        val request = buildJsonObject {
            put("entity_id", entityId)
            put("type", "daily")
        }
        val result = homeAssistant.callService(
            "weather",
            "get_forecasts",
            request,
            returnResponse = true,
        )
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
            (homeAssistant.command(
                "energy/get_prefs",
            ) as? JsonObject)?.let(HomeAssistantResponseParser::energy)
        }.onFailure { logger.debug("No energy preferences: {}", it.message) }.getOrNull()
        energyPreferences = Cached(value, System.currentTimeMillis())
        return value
    }

    suspend fun browse(entityId: String): List<BrowseItem> = cached(
        libraries,
        entityId,
        BROWSE_TTL_MS,
    ) {
        val request = buildJsonObject { put("entity_id", entityId) }
        HomeAssistantResponseParser.library(
            homeAssistant.command("media_player/browse_media", request),
        )
    }

    suspend fun todoItems(entityId: String): List<TodoItem> = cached(
        todos,
        entityId,
        BROWSE_TTL_MS,
    ) {
        val request = buildJsonObject { put("entity_id", entityId) }
        val result = homeAssistant.callService("todo", "get_items", request, returnResponse = true)
        HomeAssistantResponseParser.todos(result, entityId)
    }

    fun clear() {
        forecasts.clear()
        histories.clear()
        libraries.clear()
        todos.clear()
        musicQueueCache.clear()
        configEntryCache.clear()
        energyPreferences = null
        calendarAgenda.clear()
    }

    private data class MusicPayload(
        val browse: Map<String, List<MusicListRow>>,
        val queue: Map<String, MusicQueueSnapshot>,
        val configEntries: Map<String, String>,
        val ui: Map<String, MusicUiState>,
        val hasMore: Map<String, Boolean>,
    )

    private suspend fun musicPayload(musicTiles: List<Tile>): MusicPayload {
        val browse = linkedMapOf<String, List<MusicListRow>>()
        val queue = linkedMapOf<String, MusicQueueSnapshot>()
        val configEntries = linkedMapOf<String, String>()
        val ui = linkedMapOf<String, MusicUiState>()
        val hasMore = linkedMapOf<String, Boolean>()
        for (tile in musicTiles) {
            val playerEntityId = tile.entityId ?: continue
            val state = musicUiStore.state(tile.id)
            ui[tile.id] = state
            val configEntryId = resolveConfigEntry(tile, playerEntityId)
            if (configEntryId != null) {
                configEntries[tile.id] = configEntryId
                ensureBrowseLoaded(tile.id, configEntryId, state)
                browse[tile.id] = musicUiStore.browseItems(tile.id)
                hasMore[tile.id] = musicUiStore.browseHasMore(tile.id)
                queue[tile.id] = musicQueue(playerEntityId)
            }
        }
        return MusicPayload(browse, queue, configEntries, ui, hasMore)
    }

    private suspend fun resolveConfigEntry(tile: Tile, playerEntityId: String): String? {
        val fromOptions = tile.options["config_entry_id"]?.takeIf { it.isNotBlank() }
        val cached = configEntryCache[playerEntityId]?.takeIf { it.isFresh(BROWSE_TTL_MS) }?.value
        val resolved = when {
            fromOptions != null -> fromOptions
            cached != null -> cached
            else -> {
                val fetched = musicClient.configEntryIdForEntity(playerEntityId)
                if (fetched != null) {
                    configEntryCache[playerEntityId] = Cached(fetched, System.currentTimeMillis())
                }
                fetched
            }
        }
        return resolved
    }

    /** Loads the first library page into [musicUiStore] when the tile has no rows yet. */
    suspend fun ensureBrowseLoaded(tileId: String, configEntryId: String, state: MusicUiState) {
        if (musicUiStore.browseItems(tileId).isNotEmpty()) return
        val page = musicClient.getLibrary(
            configEntryId = configEntryId,
            mediaType = state.browseMediaType,
            offset = 0,
            limit = MusicAssistantClient.LIBRARY_PAGE_SIZE,
        )
        musicUiStore.setBrowseItems(
            tileId,
            page,
            hasMore = page.size >= MusicAssistantClient.LIBRARY_PAGE_SIZE,
        )
        musicUiStore.setBrowseOffset(tileId, 0)
    }

    /** Fetches the next browse page for [tileId] and updates the store. */
    suspend fun loadMoreBrowse(tileId: String, configEntryId: String): Boolean {
        val state = musicUiStore.state(tileId)
        if (!musicUiStore.browseHasMore(tileId)) return false
        val offset = musicUiStore.browseItems(tileId).size
        musicUiStore.setBrowseOffset(tileId, offset)
        val page = musicClient.getLibrary(
            configEntryId = configEntryId,
            mediaType = state.browseMediaType,
            offset = offset,
            limit = MusicAssistantClient.LIBRARY_PAGE_SIZE,
        )
        musicUiStore.setBrowseItems(
            tileId,
            musicUiStore.browseItems(tileId) + page,
            hasMore = page.size >= MusicAssistantClient.LIBRARY_PAGE_SIZE,
        )
        return page.isNotEmpty()
    }

    private suspend fun musicQueue(playerEntityId: String): MusicQueueSnapshot {
        musicQueueCache[playerEntityId]?.takeIf { it.isFresh(MUSIC_TTL_MS) }?.let { return it.value }
        val snapshot = musicClient.getQueue(playerEntityId)
        musicQueueCache[playerEntityId] = Cached(snapshot, System.currentTimeMillis())
        return snapshot
    }

    private fun isGraphed(tile: Tile): Boolean =
        tile.effectiveKind() == "history" || (tile.effectiveKind() == "sensor" && tile.options["graph"] == "true")

    @Volatile
    private var energyPreferences: Cached<EnergyPrefs?>? = null

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
