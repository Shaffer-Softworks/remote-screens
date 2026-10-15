package tech.shaffersoftworks.remotescreens.server.service

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantException
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private const val IDLE_STREAM_MINUTES = 5L
private const val MILLISECONDS_PER_MINUTE = 60_000L
private val HOME_ASSISTANT_HLS_URL = Regex("^/api/hls/([A-Za-z0-9_-]+)/([A-Za-z0-9_./-]+)$")
private val STREAM_FILE = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.[a-z0-9]+$")

/**
 * A camera's live video for the tablets, which never reach Home Assistant themselves: Home Assistant starts an HLS
 * stream behind a token, and the add-on relays its playlists and segments to the player port. Only tokens handed out
 * here are relayed, and each is forgotten once no tablet has asked for it for a while.
 */
class CameraStreamService(
    private val homeAssistant: HomeAssistant,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger(CameraStreamService::class.java)

    /** Token to the last time a tablet asked for it. */
    private val tokens = ConcurrentHashMap<String, Long>()

    /** The path on the player port that plays [entityId], or null when the camera cannot stream. */
    suspend fun hlsPath(entityId: String): String? {
        val (token, file) = startStream(entityId)?.let(HOME_ASSISTANT_HLS_URL::matchEntire)?.destructured ?: return null
        forgetIdle()
        tokens[token] = now()
        return "$PLAYER_PATH/$token/$file"
    }

    /** Home Assistant's own URL of the stream, `/api/hls/<token>/master_playlist.m3u8`. */
    private suspend fun startStream(entityId: String): String? {
        val payload = buildJsonObject {
            put("entity_id", entityId)
            put("format", "hls")
        }
        val result = try {
            homeAssistant.command("camera/stream", payload)
        } catch (e: HomeAssistantException) {
            logger.info("{} has no stream: {}", entityId, e.message)
            null
        } catch (e: IllegalStateException) {
            logger.info("No stream for {}: {}", entityId, e.message)
            null
        } catch (e: TimeoutCancellationException) {
            logger.info("Home Assistant did not start a stream for {}: {}", entityId, e.message)
            null
        }
        return (result as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Relays [file] of the stream behind [token], with the [query] low-latency HLS asks with. Returns Home Assistant's
     * HTTP status, or null when the token was not handed out here or the file is not one a stream serves.
     */
    suspend fun relay(
        token: String,
        file: String,
        query: String,
        onBody: suspend (contentType: String?, body: ByteReadChannel) -> Unit,
    ): Int? {
        forgetIdle()
        if (tokens.computeIfPresent(token) { _, _ -> now() } == null || !STREAM_FILE.matches(file)) return null
        val suffix = if (query.isEmpty()) "" else "?$query"
        return homeAssistant.stream("/api/hls/$token/$file$suffix", onBody)
    }

    private fun forgetIdle() {
        val oldest = now() - IDLE_STREAM_MINUTES * MILLISECONDS_PER_MINUTE
        tokens.entries.removeIf { (_, lastUsed) -> lastUsed < oldest }
    }

    companion object {
        /** Where the player port serves streams: `/stream/hls/<token>/<file>`. */
        const val PLAYER_PATH = "/stream/hls"
    }
}
