package tech.shaffersoftworks.remotescreens.server.data.screensaver

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaSourceNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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

        val albums = runCatching {
            CATALOG_JSON.decodeFromString(ListSerializer(ImmichAlbum.serializer()), albumsBody)
        }.getOrNull()

        return albums
            ?.firstOrNull { album -> album.albumName.equals(wanted, ignoreCase = true) }
            ?.id
            ?.takeIf { id -> id.isNotBlank() }
    }

    /** Image asset ids from an Immich album body or a metadata search body. Videos are left out. */
    fun imageIds(body: String): List<String> = decodedAssets(body)
        .filter { asset -> asset.type == null || asset.type == IMAGE_TYPE }
        .map { asset -> asset.id }
        .filter { id -> id.isNotBlank() }

    /** Metadata search body. A blank [albumId] searches the library and omits `albumIds`. */
    fun metadataSearch(size: Int, albumId: String? = null): String = CATALOG_JSON.encodeToString(
        serializer = ImmichMetadataSearch.serializer(),
        value = ImmichMetadataSearch(
            type = IMAGE_TYPE,
            isArchived = false,
            size = size,
            albumIds = listOfNotNull(albumId),
        ),
    )

    private fun decodedAssets(body: String): List<ImmichAsset> =
        searchAssets(body) ?: albumAssets(body) ?: listedAssets(body)

    private fun searchAssets(body: String): List<ImmichAsset>? = runCatching {
        CATALOG_JSON.decodeFromString(ImmichSearchResponse.serializer(), body)
    }.getOrNull()?.let { response -> response.assets?.items.orEmpty() + response.items }

    private fun albumAssets(body: String): List<ImmichAsset>? = runCatching {
        CATALOG_JSON.decodeFromString(ImmichAlbumAssets.serializer(), body)
    }.getOrNull()?.assets

    private fun listedAssets(body: String): List<ImmichAsset> = runCatching {
        CATALOG_JSON.decodeFromString(ListSerializer(ImmichAsset.serializer()), body)
    }.getOrNull().orEmpty()
}

@Serializable
internal data class ImmichMetadataSearch(
    val type: String,
    val isArchived: Boolean,
    val size: Int,
    val albumIds: List<String> = emptyList(),
)

@Serializable
private data class ImmichAlbum(val id: String = "", val albumName: String = "")

@Serializable
private data class ImmichAsset(val id: String = "", val type: String? = null)

@Serializable
private data class ImmichAssetPage(val items: List<ImmichAsset> = emptyList())

@Serializable
private data class ImmichSearchResponse(
    val assets: ImmichAssetPage? = null,
    val items: List<ImmichAsset> = emptyList(),
)

@Serializable
private data class ImmichAlbumAssets(val assets: List<ImmichAsset> = emptyList())
