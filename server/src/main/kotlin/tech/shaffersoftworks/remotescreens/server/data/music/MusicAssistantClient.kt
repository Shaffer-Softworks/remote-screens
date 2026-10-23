package tech.shaffersoftworks.remotescreens.server.data.music

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Music Assistant services on Home Assistant: library, search, queue, and play/enqueue.
 * Response shapes match RPI's MusicAssistantRepository parsers.
 */
class MusicAssistantClient(private val homeAssistant: HomeAssistant) {
    private val logger = LoggerFactory.getLogger(MusicAssistantClient::class.java)

    suspend fun configEntryIdForEntity(entityId: String): String? {
        if (entityId.isBlank()) return null
        val result = runCatching {
            homeAssistant.command(
                "config/entity_registry/get",
                buildJsonObject { put("entity_id", entityId) },
            )
        }.onFailure { logger.debug("entity registry get {}: {}", entityId, it.message) }
            .getOrNull() as? JsonObject
        return (result?.get("config_entry_id") as? JsonPrimitive)?.contentOrNull
    }

    suspend fun search(configEntryId: String, query: String, limit: Int = SEARCH_LIMIT): List<MusicListRow> {
        if (configEntryId.isBlank() || query.isBlank()) return emptyList()
        val result = callMa(
            "search",
            buildJsonObject {
                put("config_entry_id", configEntryId)
                put("name", query)
                put("limit", limit)
            },
        )
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
            "get_library",
            buildJsonObject {
                put("config_entry_id", configEntryId)
                put("media_type", mediaType)
                put("limit", limit)
                put("offset", offset)
            },
        )
        return result?.let(MusicResponseParsers::parseLibrary).orEmpty()
    }

    suspend fun getQueue(playerEntityId: String): MusicQueueSnapshot {
        if (playerEntityId.isBlank()) return MusicQueueSnapshot(emptyList(), 0)
        val result = callMa(
            "get_queue",
            buildJsonObject { put("entity_id", playerEntityId) },
        )
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
            homeAssistant.callService(
                "music_assistant",
                "play_media",
                buildJsonObject {
                    put("entity_id", playerEntityId)
                    put("media_id", uri)
                    put("enqueue", enqueue)
                },
            )
            true
        }.onFailure { logger.warn("play_media failed: {}", it.message) }.getOrDefault(false)
    }

    suspend fun clearPlaylist(playerEntityId: String): Boolean {
        if (playerEntityId.isBlank()) return false
        return runCatching {
            homeAssistant.callService(
                "media_player",
                "clear_playlist",
                buildJsonObject { put("entity_id", playerEntityId) },
            )
            true
        }.onFailure { logger.warn("clear_playlist failed: {}", it.message) }.getOrDefault(false)
    }

    private suspend fun callMa(service: String, data: JsonObject): JsonElement? =
        runCatching {
            homeAssistant.callService("music_assistant", service, data, returnResponse = true)
        }.onFailure { logger.debug("music_assistant.{}: {}", service, it.message) }.getOrNull()

    companion object {
        private const val SEARCH_LIMIT = 25
        const val LIBRARY_PAGE_SIZE = 40
    }
}
