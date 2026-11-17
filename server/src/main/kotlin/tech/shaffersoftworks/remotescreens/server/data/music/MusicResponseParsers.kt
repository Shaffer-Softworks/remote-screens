package tech.shaffersoftworks.remotescreens.server.data.music

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HA_JSON
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.SkippingListSerializer

/** An artist as Music Assistant names it inside a media item. */
@Serializable
internal data class MaArtist(val name: String? = null)

@Serializable
internal data class MaMetadata(val images: List<JsonElement> = emptyList())

/** A track, album, artist, playlist or station. [image] is a URL or an object with a `path`. */
@Serializable
internal data class MaMediaItem(
    val uri: String? = null,
    val name: String? = null,
    val title: String? = null,
    @SerialName("sort_name") val sortName: String? = null,
    val artist: String? = null,
    @Serializable(with = MaArtists::class) val artists: List<MaArtist> = emptyList(),
    val image: JsonElement? = null,
    val metadata: MaMetadata? = null,
)

/** One entry of a player queue: the [mediaItem] it plays, under its own [name]. */
@Serializable
internal data class MaQueueItem(
    @SerialName("queue_item_id") val queueItemId: String? = null,
    val name: String? = null,
    val duration: Double? = null,
    @SerialName("stream_title") val streamTitle: String? = null,
    @SerialName("media_item") val mediaItem: MaMediaItem? = null,
    val image: JsonElement? = null,
)

/** `get_queue`: [items] is the whole list, or only how many there are beside [currentItem] and [nextItem]. */
@Serializable
internal data class MaQueue(
    val items: JsonElement? = null,
    @SerialName("current_index") val currentIndex: Int? = null,
    @SerialName("current_item") val currentItem: MaQueueItem? = null,
    @SerialName("next_item") val nextItem: MaQueueItem? = null,
)

internal object MaArtists : SkippingListSerializer<MaArtist>(MaArtist.serializer())

/** Parses Music Assistant `callService(..., returnResponse = true)` payloads. */
internal object MusicResponseParsers {
    fun parseSearch(element: JsonElement): List<MusicListRow> {
        val payload = unwrapServicePayload(element)
        return buildList {
            for ((first, second) in SEARCH_KINDS) {
                val items = payload[first] as? JsonArray ?: continue
                for (item in items) {
                    decode(MaMediaItem.serializer(), item)?.toRow(second)?.let(::add)
                }
            }
        }
    }

    fun parseLibrary(element: JsonElement): List<MusicListRow> {
        val payload = unwrapServicePayload(element)
        val items = payload["items"] as? JsonArray
        return items?.mapNotNull { decode(MaMediaItem.serializer(), it)?.toRow("Library") }.orEmpty()
    }

    fun parseQueue(element: JsonElement, playerEntityId: String): MusicQueueSnapshot {
        val payload = unwrapServicePayload(element)
        val queue = extractQueuePayload(payload, playerEntityId)
            ?.let { decode(MaQueue.serializer(), it) }
            ?: return MusicQueueSnapshot(emptyList(), 0)
        val itemsEl = queue.items
        val currentId = queue.currentItem?.queueItemId
        return when (itemsEl) {
            is JsonArray -> MusicQueueSnapshot(
                itemsEl.mapIndexedNotNull { index, item ->
                    queueRowFromArrayItem(
                        item = item,
                        index = index,
                        currentId = currentId,
                        currentIndex = queue.currentIndex,
                    )
                },
                moreCount = 0,
            )
            else -> compactQueueSnapshot(queue = queue, currentId = currentId)
        }
    }

    private fun queueRowFromArrayItem(
        item: JsonElement,
        index: Int,
        currentId: String?,
        currentIndex: Int?,
    ): MusicListRow? {
        val queueItem = decode(MaQueueItem.serializer(), item) ?: return null
        val id = queueItem.queueItemId
        val isCurrent = when {
            currentId != null && id != null -> id == currentId
            currentIndex != null -> index == currentIndex
            else -> false
        }
        return parseQueueItem(queueItem, isCurrent)
    }

    private fun compactQueueSnapshot(queue: MaQueue, currentId: String?): MusicQueueSnapshot {
        val rows = buildList {
            queue.currentItem?.let { parseQueueItem(it, true)?.let(::add) }
            val next = queue.nextItem
            val nextParsed = next?.let { parseQueueItem(it, false) }
            if (nextParsed != null) {
                val nextId = next.queueItemId
                if (nextId != null && nextId != currentId) add(nextParsed)
            }
        }
        val more = when (val totalInQueue = (queue.items as? JsonPrimitive)?.intOrNull) {
            null -> 0
            else -> (totalInQueue - rows.size).coerceAtLeast(0)
        }
        return MusicQueueSnapshot(rows, more)
    }

    private fun parseQueueItem(item: MaQueueItem, isCurrent: Boolean): MusicListRow? {
        val media = item.mediaItem
        val uri = media?.uri
        val title = media?.let { item.name ?: it.name }
        if (media == null || uri == null || title == null) return null
        val durationSec = item.duration?.toInt()
        val streamTitle = item.streamTitle
        val artist = media.artists.firstOrNull()?.name
        val subtitle = buildList {
            artist?.takeIf { it.isNotBlank() }?.let(::add)
            streamTitle?.takeIf { it.isNotBlank() }?.let(::add)
            durationSec?.takeIf { it > 0 }?.let { add(formatMmSs(it)) }
        }.joinToString(" · ").takeIf { it.isNotBlank() }
        val art = resolveMediaImage(media.image ?: item.image ?: media.metadata?.images?.firstOrNull())
        return MusicListRow(
            title = title,
            subtitle = subtitle,
            uri = uri,
            imagePathOrUrl = art,
            isCurrentPlayback = isCurrent,
        )
    }

    private fun MaMediaItem.toRow(kind: String): MusicListRow? {
        val primary = name ?: title ?: sortName
        if (uri == null || primary == null) return null
        return MusicListRow(
            title = "$kind · $primary",
            subtitle = artist?.takeIf { it.isNotBlank() } ?: artists.firstOrNull()?.name,
            uri = uri,
            imagePathOrUrl = resolveMediaImage(image),
        )
    }

    private fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T? =
        (element as? JsonObject)?.let { runCatching { HA_JSON.decodeFromJsonElement(serializer, it) }.getOrNull() }

    /**
     * HA websocket `call_service` + `return_response` wraps the payload as `{ context, response }`.
     * Some REST/demo bodies use `service_response` instead.
     */
    private fun unwrapServicePayload(element: JsonElement): JsonObject {
        var current = element as? JsonObject ?: return JsonObject(emptyMap())
        repeat(UNWRAP_DEPTH) {
            ((current["response"] ?: current["service_response"]) as? JsonObject)?.let { current = it }
            if (current.keys.none { it in LEAF_KEYS }) {
                current = current.values.singleOrNull() as? JsonObject ?: current
            }
        }
        return current
    }

    private fun extractQueuePayload(payload: JsonObject, entityId: String): JsonObject? {
        val byEntity = payload[entityId] as? JsonObject
        val sole = payload.values.singleOrNull() as? JsonObject
        val soleIsQueue = sole != null &&
            (sole.containsKey("queue_id") || sole.containsKey("current_item"))
        val scanned = payload.entries.firstOrNull { (_, value) ->
            val obj = value as? JsonObject ?: return@firstOrNull false
            obj.containsKey("queue_id") || obj.containsKey("current_item")
        }?.value as? JsonObject
        return when {
            payload.containsKey("queue_id") || payload.containsKey("current_item") -> payload
            byEntity != null -> byEntity
            soleIsQueue -> sole
            else -> scanned
        }
    }

    private fun resolveMediaImage(element: JsonElement?): String? {
        val fromPrimitive = (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        val fromObject = (element as? JsonObject)?.text("path")?.trim()?.takeIf { it.isNotBlank() }
        return fromPrimitive ?: fromObject
    }

    private fun formatMmSs(totalSec: Int): String {
        val safe = totalSec.coerceAtLeast(0)
        val minutes = safe / SECONDS_PER_MINUTE
        val seconds = safe % SECONDS_PER_MINUTE
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private const val UNWRAP_DEPTH = 8
    private const val SECONDS_PER_MINUTE = 60
    private val SEARCH_KINDS = listOf(
        "artists" to "Artist",
        "albums" to "Album",
        "tracks" to "Track",
        "playlists" to "Playlist",
        "radio" to "Radio",
    )
    private val SEARCH_KEYS = setOf(
        "tracks",
        "albums",
        "playlists",
        "artists",
        "radio",
        "audiobooks",
        "podcasts",
    )
    private val LEAF_KEYS = SEARCH_KEYS + setOf("queue_id", "current_item", "items")
}
