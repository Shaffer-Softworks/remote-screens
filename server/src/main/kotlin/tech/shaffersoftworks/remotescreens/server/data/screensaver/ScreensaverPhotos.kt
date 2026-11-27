package tech.shaffersoftworks.remotescreens.server.data.screensaver

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.BrowseMediaSource
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.MediaSourceNode
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.ResolveMediaSource
import tech.shaffersoftworks.remotescreens.server.data.pictures.ImageEncoder
import tech.shaffersoftworks.remotescreens.shared.ScreensaverSettings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

private const val USER_AGENT = "RemoteScreens/1 (Home Assistant add-on)"
private const val API_KEY_HEADER = "x-api-key"
private const val MAX_LISTED = 500
private const val MAX_LONG_EDGE = 1920
private const val SEARCH_BODY = """{"type":"IMAGE","isArchived":false,"size":500}"""

/** One photo the screensaver can show. [id] changes when the bytes do. */
data class ScreensaverSlide(val id: String)

/**
 * Photo lists and JPEGs for the screensaver. Tablets never talk to Home Assistant or Immich.
 * A failure is an empty list or a null picture, and the clock stays up.
 */
class ScreensaverPhotos(
    private val homeAssistant: HomeAssistant,
    private val httpClient: HttpClient,
) {
    private val logger = LoggerFactory.getLogger(ScreensaverPhotos::class.java)

    suspend fun list(settings: ScreensaverSettings): List<ScreensaverSlide> = runCatching {
        val slides = when (settings.source) {
            ScreensaverSettings.SOURCE_IMMICH -> immichSlides(settings)
            else -> homeAssistantSlides(settings.mediaId)
        }

        slides.take(MAX_LISTED)
    }.onFailure { logger.warn("Screensaver list failed: {}", it.toString()) }.getOrElse { emptyList() }

    suspend fun jpeg(settings: ScreensaverSettings, slide: ScreensaverSlide, widthPx: Int, heightPx: Int): ByteArray? =
        runCatching {
            val bytes = when (settings.source) {
                ScreensaverSettings.SOURCE_IMMICH -> immichPreview(settings, slide.id)
                else -> homeAssistantImage(slide.id)
            } ?: return@runCatching null

            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@runCatching null
            val fitted = fittedSize(widthPx = widthPx, heightPx = heightPx)

            ImageEncoder.encode(
                image = ImageEncoder.fit(
                    source = image,
                    width = fitted.first,
                    height = fitted.second,
                    isCropped = true,
                ),
                isRound = false,
            )
        }.onFailure { logger.warn("Screensaver photo {} failed: {}", slide.id, it.toString()) }.getOrNull()

    private suspend fun homeAssistantSlides(mediaId: String?): List<ScreensaverSlide> {
        val rootId = mediaId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val root = homeAssistant.send(BrowseMediaSource(mediaContentId = rootId))

        val ids = buildList {
            addImages(root)

            for (child in root.children) {
                val folderId = child.mediaContentId

                if (ScreensaverCatalog.isFolder(child) && folderId != null) {
                    val folder = homeAssistant.send(BrowseMediaSource(mediaContentId = folderId))

                    addImages(folder)
                }
            }
        }

        return ids.distinct().map { ScreensaverSlide(id = it) }
    }

    private fun MutableList<String>.addImages(node: MediaSourceNode) {
        val ownId = node.mediaContentId

        if (ScreensaverCatalog.isImage(node) && ownId != null) add(ownId)

        for (child in node.children) {
            val childId = child.mediaContentId

            if (ScreensaverCatalog.isImage(child) && childId != null) add(childId)
        }
    }

    private suspend fun homeAssistantImage(contentId: String): ByteArray? {
        val resolved = homeAssistant.send(ResolveMediaSource(mediaContentId = contentId))
        val url = resolved.url ?: return null

        return if (url.startsWith("http")) fetchUrl(url = url, apiKey = null) else homeAssistant.fetch(url)
    }

    private suspend fun immichSlides(settings: ScreensaverSettings): List<ScreensaverSlide> {
        val server = settings.immichUrl?.trimEnd('/')
        val apiKey = settings.immichApiKey

        val body = if (server == null || apiKey == null) {
            null
        } else {
            immichBody(
                server = server,
                apiKey = apiKey,
                album = settings.immichAlbum?.takeIf { it.isNotBlank() },
            )
        }

        return ScreensaverCatalog.imageIds(body.orEmpty()).map { ScreensaverSlide(id = it) }
    }

    private suspend fun immichBody(server: String, apiKey: String, album: String?): String? = if (album == null) {
        postJson(url = "$server/api/search/metadata", apiKey = apiKey, json = SEARCH_BODY)
    } else {
        val albums = fetchText(url = "$server/api/albums", apiKey = apiKey)
        val albumId = albums?.let { ScreensaverCatalog.albumId(albumsBody = it, albumName = album) }

        albumId?.let { fetchText(url = "$server/api/albums/$it", apiKey = apiKey) }
    }

    private suspend fun immichPreview(settings: ScreensaverSettings, assetId: String): ByteArray? {
        val server = settings.immichUrl?.trimEnd('/')
        val apiKey = settings.immichApiKey

        return if (server == null || apiKey == null) {
            null
        } else {
            fetchUrl(url = "$server/api/assets/$assetId/thumbnail?size=preview", apiKey = apiKey)
        }
    }

    private suspend fun fetchText(url: String, apiKey: String): String? {
        val response = httpClient.get(url) {
            header("User-Agent", USER_AGENT)
            header(API_KEY_HEADER, apiKey)
        }

        return if (response.status.isSuccess()) response.bodyAsText() else null
    }

    private suspend fun postJson(url: String, apiKey: String, json: String): String? {
        val response = httpClient.post(url) {
            header("User-Agent", USER_AGENT)
            header(API_KEY_HEADER, apiKey)
            contentType(ContentType.Application.Json)
            setBody(json)
        }

        return if (response.status.isSuccess()) response.bodyAsText() else null
    }

    private suspend fun fetchUrl(url: String, apiKey: String?): ByteArray? {
        val response = httpClient.get(url) {
            header("User-Agent", USER_AGENT)

            if (apiKey != null) header(API_KEY_HEADER, apiKey)
        }

        return if (response.status.isSuccess()) response.readRawBytes() else null
    }

    private fun fittedSize(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        val safeWidth = widthPx.coerceAtLeast(1)
        val safeHeight = heightPx.coerceAtLeast(1)
        val longEdge = max(safeWidth, safeHeight)
        val scale = MAX_LONG_EDGE.toDouble() / longEdge.toDouble()
        val used = if (longEdge > MAX_LONG_EDGE) scale else 1.0

        return (safeWidth * used).roundToInt().coerceAtLeast(1) to (safeHeight * used).roundToInt().coerceAtLeast(1)
    }
}
