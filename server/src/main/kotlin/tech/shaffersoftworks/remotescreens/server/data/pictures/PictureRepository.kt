package tech.shaffersoftworks.remotescreens.server.data.pictures

import tech.shaffersoftworks.remotescreens.server.config.ServerConfig
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistant
import tech.shaffersoftworks.remotescreens.server.data.renderdata.PictureSlot
import tech.shaffersoftworks.remotescreens.server.render.QrBitmaps
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

private const val USER_AGENT = "RemoteScreens/1 (Home Assistant add-on)"
private const val MAX_CACHED_TILES = 400
private const val ATTRIBUTE_SOURCE_PREFIX = "attr:"
private const val MAP_SOURCE_PREFIX = "map:"
private const val QR_SOURCE_PREFIX = "qr:"

/**
 * The pictures documents show, fetched and sized here so a tablet never talks to Home Assistant or the internet:
 * camera frames and album art from Home Assistant, maps drawn from OpenStreetMap tiles with a marker for each person.
 */
class PictureRepository(
    private val homeAssistant: HomeAssistant,
    private val httpClient: HttpClient,
    private val tileUrl: String = ServerConfig.OPENSTREETMAP_TILE_URL,
) {
    private val logger = LoggerFactory.getLogger(PictureRepository::class.java)
    private val tileCache = ConcurrentHashMap<String, BufferedImage>()
    private val mapRenderer = MapRenderer(::tile)

    /** What the picture currently comes from: when it changes, the picture is sent again. */
    fun sourceKey(slot: PictureSlot, states: Map<String, EntityState>): String? = when {
        slot.source.startsWith(ATTRIBUTE_SOURCE_PREFIX) -> {
            val (_, entityId, attribute) = slot.source.split(':', limit = 3)
            states[entityId]?.attr(attribute)
        }
        slot.source.startsWith(MAP_SOURCE_PREFIX) -> slot.source.removePrefix(MAP_SOURCE_PREFIX)
            .substringAfter(':')
            .split(',')
            .joinToString(";") { entityId ->
            states[entityId]?.let { "${it.number("latitude")},${it.number("longitude")},${it.state}" }.orEmpty()
        }
        slot.source.startsWith(QR_SOURCE_PREFIX) -> qrPayload(slot.source, states)
        else -> slot.source
    }

    /** The picture of [slot] at its size, as JPEG or (when round) PNG; null when it cannot be had. */
    suspend fun load(slot: PictureSlot, states: Map<String, EntityState>): ByteArray? = runCatching {
        val image = when {
            slot.source.startsWith(MAP_SOURCE_PREFIX) -> {
                val style = slot.source.removePrefix(MAP_SOURCE_PREFIX).substringBefore(':')
                val people = mapEntityIds(slot).mapNotNull { states[it] }
                mapRenderer.draw(people, slot.widthPx, slot.heightPx, isDark = style == "dark")
            }
            slot.source.startsWith(QR_SOURCE_PREFIX) ->
                QrBitmaps.encode(qrPayload(slot.source, states), maxOf(slot.widthPx, slot.heightPx))
            else -> fetchImage(slot, states)
        }
        image?.let { ImageEncoder.encode(ImageEncoder.fit(it, slot.widthPx, slot.heightPx, slot.crop), slot.round) }
    }.onFailure { logger.warn("Picture {} failed: {}", slot.name, it.toString()) }.getOrNull()

    private suspend fun fetchImage(slot: PictureSlot, states: Map<String, EntityState>): BufferedImage? {
        val url = if (slot.source.startsWith(ATTRIBUTE_SOURCE_PREFIX)) sourceKey(slot, states) else slot.source
        val bytes = url?.let { if (it.startsWith("http")) fetchUrl(it) else homeAssistant.fetch(it) }
        return bytes?.let { ImageIO.read(ByteArrayInputStream(it)) }
    }

    private fun mapEntityIds(slot: PictureSlot): List<String> =
        slot.source.removePrefix(MAP_SOURCE_PREFIX).substringAfter(':', "").split(',')

    /** Static text after `qr:`, or an entity's state when the source is `qr:@entity.id`. */
    private fun qrPayload(source: String, states: Map<String, EntityState>): String {
        val payload = source.removePrefix(QR_SOURCE_PREFIX)
        return if (payload.startsWith("@")) {
            states[payload.removePrefix("@")]?.state.orEmpty()
        } else {
            payload
        }
    }

    private suspend fun fetchUrl(url: String): ByteArray? {
        val response = httpClient.get(url) { header("User-Agent", USER_AGENT) }
        return if (response.status.isSuccess()) response.readRawBytes() else null
    }

    /** OpenStreetMap's own tiles by default: free for light use with a named User-Agent and caching. */
    private suspend fun tile(zoom: Int, x: Int, y: Int): BufferedImage? {
        val key = "$zoom/$x/$y"
        tileCache[key]?.let { return it }
        val url = tileUrl.replace("{z}", "$zoom").replace("{x}", "$x").replace("{y}", "$y")
        val image = runCatching { fetchUrl(url) }.getOrNull()?.let { ImageIO.read(ByteArrayInputStream(it)) }
        if (image != null) {
            if (tileCache.size > MAX_CACHED_TILES) tileCache.clear()
            tileCache[key] = image
        }
        return image
    }
}
