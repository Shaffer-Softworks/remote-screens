package tech.shaffersoftworks.remotescreens.server.data.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses Music Assistant `callService(..., returnResponse = true)` payloads. */
internal object MusicResponseParsers {
    fun parseSearch(element: JsonElement): List<MusicListRow> {
        val payload = unwrapServicePayload(element)
        return buildList {
            for (kind in SEARCH_KINDS) {
                val items = payload[kind.first] as? JsonArray ?: continue
                for (item in items) {
                    (item as? JsonObject)?.toRow(kind.second)?.let(::add)
                }
            }
        }
    }

    fun parseLibrary(element: JsonElement): List<MusicListRow> {
        val payload = unwrapServicePayload(element)
        val items = payload["items"] as? JsonArray
        return items?.mapNotNull { (it as? JsonObject)?.toRow("Library") }.orEmpty()
    }

    fun parseQueue(element: JsonElement, playerEntityId: String): MusicQueueSnapshot {
        val payload = unwrapServicePayload(element)
        val queue = extractQueuePayload(payload, playerEntityId)
        if (queue == null) return MusicQueueSnapshot(emptyList(), 0)
        val itemsEl = queue["items"]
        val currentIndex = queue["current_index"]?.jsonPrimitive?.intOrNull
        val currentObj = queue["current_item"] as? JsonObject
        val nextObj = queue["next_item"] as? JsonObject
        val currentId = currentObj?.text("queue_item_id")
        return when (itemsEl) {
            is JsonArray -> MusicQueueSnapshot(
                itemsEl.mapIndexedNotNull { index, item ->
                    queueRowFromArrayItem(item, index, currentId, currentIndex)
                },
                moreCount = 0,
            )
            else -> compactQueueSnapshot(currentObj, nextObj, currentId, itemsEl)
        }
    }

    private fun queueRowFromArrayItem(
        item: JsonElement,
        index: Int,
        currentId: String?,
        currentIndex: Int?,
    ): MusicListRow? {
        val obj = item as? JsonObject ?: return null
        val id = obj.text("queue_item_id")
        val isCurrent = when {
            currentId != null && id != null -> id == currentId
            currentIndex != null -> index == currentIndex
            else -> false
        }
        return parseQueueItem(obj, isCurrent)
    }

    private fun compactQueueSnapshot(
        currentObj: JsonObject?,
        nextObj: JsonObject?,
        currentId: String?,
        itemsEl: JsonElement?,
    ): MusicQueueSnapshot {
        val rows = buildList {
            currentObj?.let { parseQueueItem(it, true)?.let(::add) }
            val nextParsed = nextObj?.let { parseQueueItem(it, false) }
            if (nextParsed != null) {
                val nextId = nextObj.text("queue_item_id")
                if (nextId != null && nextId != currentId) add(nextParsed)
            }
        }
        val more = when (val totalInQueue = (itemsEl as? JsonPrimitive)?.intOrNull) {
            null -> 0
            else -> (totalInQueue - rows.size).coerceAtLeast(0)
        }
        return MusicQueueSnapshot(rows, more)
    }

    private fun parseQueueItem(obj: JsonObject, isCurrent: Boolean): MusicListRow? {
        val media = obj["media_item"] as? JsonObject
        val uri = media?.text("uri")
        val title = when {
            media == null -> null
            else -> obj.text("name") ?: media.text("name")
        }
        if (media == null || uri == null || title == null) return null
        val durationSec = obj["duration"]?.jsonPrimitive?.intOrNull
            ?: obj.text("duration")?.toIntOrNull()
        val streamTitle = obj.text("stream_title")
        val artist = media["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.text("name")
        val subtitle = buildList {
            artist?.takeIf { it.isNotBlank() }?.let(::add)
            streamTitle?.takeIf { it.isNotBlank() }?.let(::add)
            durationSec?.takeIf { it > 0 }?.let { add(formatMmSs(it)) }
        }.joinToString(" · ").takeIf { it.isNotBlank() }
        val art = resolveMediaImage(
            media["image"] ?: obj["image"]
                ?: media["metadata"]?.jsonObject?.get("images")?.jsonArray?.firstOrNull(),
        )
        return MusicListRow(title, subtitle, uri, art, isCurrent)
    }

    private fun JsonObject.toRow(kind: String): MusicListRow? {
        val uri = text("uri")
        val primary = text("name") ?: text("title") ?: text("sort_name")
        if (uri == null || primary == null) return null
        val artist = text("artist")?.takeIf { it.isNotBlank() }
            ?: this["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.text("name")
        return MusicListRow(
            title = "$kind · $primary",
            subtitle = artist,
            uri = uri,
            imagePathOrUrl = resolveMediaImage(this["image"]),
        )
    }

    /**
     * HA websocket `call_service` + `return_response` wraps the payload as `{ context, response }`.
     * Some REST/demo bodies use `service_response` instead.
     */
    private fun unwrapServicePayload(element: JsonElement): JsonObject {
        var current = element.jsonObject
        for (depth in 0 until UNWRAP_DEPTH) {
            val wrapped = current["response"] ?: current["service_response"]
            if (wrapped != null) {
                current = wrapped.jsonObject
            }
            val isLeaf = current.containsKey("queue_id") ||
                current.containsKey("current_item") ||
                current.keys.any { it in SEARCH_KEYS || it == "items" }
            val nested = current.values.singleOrNull() as? JsonObject
            if (isLeaf || nested == null || current.size != 1) {
                break
            }
            current = nested
        }
        return current
    }

    private fun extractQueuePayload(payload: JsonObject, entityId: String): JsonObject? {
        val byEntity = payload[entityId]?.jsonObject
        val sole = payload.values.singleOrNull() as? JsonObject
        val soleIsQueue = sole != null &&
            (sole.containsKey("queue_id") || sole.containsKey("current_item"))
        val scanned = payload.entries.firstOrNull { (_, value) ->
            val obj = value as? JsonObject ?: return@firstOrNull false
            obj.containsKey("queue_id") || obj.containsKey("current_item")
        }?.value?.jsonObject
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
}
