package tech.shaffersoftworks.remotescreens.server.data.music

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.call
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ServiceRequest
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MusicAssistantSearch
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MusicAssistantPlayMedia
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MusicAssistantGetQueue
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MusicAssistantGetLibrary
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaPlayerClearPlaylist
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityRegistryGet
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityId

/**
 * Music Assistant services on Home Assistant: library, search, queue, and play/enqueue.
 * Response shapes match RPI's MusicAssistantRepository parsers.
 */
class MusicAssistantClient(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(MusicAssistantClient::class.java)

    suspend fun configEntryIdForEntity(entityId: String): String? {
        if (entityId.isBlank()) return null
        return runCatching { homeAssistant.send(EntityRegistryGet(entityId)) }
            .onFailure { logger.debug("entity registry get {}: {}", entityId, it.message) }
            .getOrNull()
            ?.configEntryId
    }

    suspend fun search(
        configEntryId: String,
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): List<MusicListRow> {
        if (configEntryId.isBlank() || query.isBlank()) return emptyList()
        val result = callMa(MusicAssistantSearch(configEntryId = configEntryId, name = query, limit = limit))
        return result?.let(MusicResponseParsers::parseSearch).orEmpty()
    }

    suspend fun getLibrary(
        configEntryId: String,
        mediaType: String,
        offset: Int,
        limit: Int = LIBRARY_PAGE_SIZE,
    ): List<MusicListRow> {
        if (configEntryId.isBlank()) return emptyList()
        val result = callMa(
            MusicAssistantGetLibrary(
                configEntryId = configEntryId,
                mediaType = mediaType,
                limit = limit,
                offset = offset,
            ),
        )
        return result?.let(MusicResponseParsers::parseLibrary).orEmpty()
    }

    suspend fun getQueue(playerEntityId: String): MusicQueueSnapshot {
        if (playerEntityId.isBlank()) return MusicQueueSnapshot(emptyList(), 0)
        val result = callMa(MusicAssistantGetQueue(EntityId(playerEntityId)))
        return result?.let { MusicResponseParsers.parseQueue(it, playerEntityId) }
            ?: MusicQueueSnapshot(emptyList(), 0)
    }

    suspend fun playMediaUri(
        playerEntityId: String,
        uri: String,
        enqueue: String = "play",
    ): Boolean {
        if (playerEntityId.isBlank() || uri.isBlank()) return false
        return runCatching {
            homeAssistant.call(MusicAssistantPlayMedia(EntityId(playerEntityId), mediaId = uri, enqueue = enqueue))
            true
        }.onFailure { logger.warn("play_media failed: {}", it.message) }.getOrDefault(false)
    }

    suspend fun clearPlaylist(playerEntityId: String): Boolean {
        if (playerEntityId.isBlank()) return false
        return runCatching {
            homeAssistant.call(MediaPlayerClearPlaylist(EntityId(playerEntityId)))
            true
        }.onFailure { logger.warn("clear_playlist failed: {}", it.message) }.getOrDefault(false)
    }

    private suspend fun callMa(request: ServiceRequest): JsonElement? =
        runCatching { homeAssistant.call(request, returnResponse = true) }
            .onFailure { logger.debug("{}: {}", request.service, it.message) }
            .getOrNull()

    companion object {
        private const val SEARCH_LIMIT = 25
        const val LIBRARY_PAGE_SIZE = 40
    }
}
