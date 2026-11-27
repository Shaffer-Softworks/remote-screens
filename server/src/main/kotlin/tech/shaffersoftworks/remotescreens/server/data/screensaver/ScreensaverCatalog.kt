package tech.shaffersoftworks.remotescreens.server.data.screensaver

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaSourceNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

private const val IMAGE_TYPE = "IMAGE"
private const val MEDIA_CLASS_IMAGE = "image"
private const val MEDIA_CLASS_DIRECTORY = "directory"
private const val IMAGE_CONTENT_PREFIX = "image/"

private val CATALOG_JSON = Json { ignoreUnknownKeys = true }

/** Which Home Assistant media rows and Immich assets are still photos. */
object ScreensaverCatalog {
    fun isImage(node: MediaSourceNode): Boolean {
        val contentType = node.mediaContentType.orEmpty()

        return node.mediaClass == MEDIA_CLASS_IMAGE || contentType.startsWith(IMAGE_CONTENT_PREFIX)
    }

    fun isFolder(node: MediaSourceNode): Boolean =
        node.canExpand && node.mediaClass == MEDIA_CLASS_DIRECTORY && !node.mediaContentId.isNullOrBlank()

    /** Album id for [albumName], matched without case. Null when the list has no such album. */
    fun albumId(albumsBody: String, albumName: String): String? {
        val wanted = albumName.trim()
        val albums = runCatching { CATALOG_JSON.parseToJsonElement(albumsBody).jsonArray }.getOrNull() ?: return null

        return albums.firstNotNullOfOrNull { album ->
            val objectAlbum = album as? JsonObject ?: return@firstNotNullOfOrNull null
            val name = objectAlbum.text("albumName")

            if (name.equals(wanted, ignoreCase = true)) objectAlbum.text("id") else null
        }
    }

    /** Image asset ids from an Immich album body or a metadata search body. Videos are left out. */
    fun imageIds(body: String): List<String> {
        val root = runCatching { CATALOG_JSON.parseToJsonElement(body) }.getOrNull() ?: return emptyList()

        return imageIds(root)
    }

    fun imageIds(root: JsonElement): List<String> {
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> assetItems(root)
            else -> null
        } ?: return emptyList()

        return items.mapNotNull { item ->
            val asset = item as? JsonObject ?: return@mapNotNull null
            val type = asset.text("type")

            if (type != null && type != IMAGE_TYPE) {
                null
            } else {
                asset.text("id")
            }
        }
    }

    private fun assetItems(root: JsonObject): JsonArray? {
        val assets = root["assets"]

        return when (assets) {
            is JsonArray -> assets
            is JsonObject -> assets["items"] as? JsonArray
            else -> root["items"] as? JsonArray
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
