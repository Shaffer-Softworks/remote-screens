package dev.remotescreens.server

import dev.remotescreens.server.ha.EntityState
import dev.remotescreens.server.ha.HomeAssistant
import dev.remotescreens.server.render.PictureSlot
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * The pictures documents show, fetched and sized here so a tablet never talks to Home Assistant or the internet:
 * camera frames and album art from Home Assistant, maps drawn from CARTO tiles with a marker for each person.
 */
class Pictures(private val ha: HomeAssistant, private val http: HttpClient) {
    private val log = LoggerFactory.getLogger(Pictures::class.java)
    private val tiles = ConcurrentHashMap<String, BufferedImage>()

    /** What the picture currently comes from: when it changes, the picture is sent again. */
    fun sourceKey(slot: PictureSlot, states: Map<String, EntityState>): String? = when {
        slot.source.startsWith("attr:") -> {
            val (_, id, attr) = slot.source.split(':', limit = 3)
            states[id]?.attr(attr)
        }
        slot.source.startsWith("map:") -> slot.source.substringAfter("map:").substringAfter(':').split(',')
            .joinToString(";") { id -> states[id]?.let { "${it.number("latitude")},${it.number("longitude")},${it.state}" } ?: "" }
        else -> slot.source
    }

    suspend fun load(slot: PictureSlot, states: Map<String, EntityState>): ByteArray? = runCatching {
        val image = when {
            slot.source.startsWith("map:") -> drawMap(slot, states)
            else -> {
                val url = if (slot.source.startsWith("attr:")) sourceKey(slot, states) ?: return null else slot.source
                val bytes = (if (url.startsWith("http")) fetchUrl(url) else ha.fetch(url)) ?: return null
                ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
            }
        }
        encode(fit(image, slot.widthPx, slot.heightPx, slot.crop), slot.round)
    }.onFailure { log.debug("Picture {} failed: {}", slot.name, it.message) }.getOrNull()

    private suspend fun fetchUrl(url: String): ByteArray? {
        val r = http.get(url) { header("User-Agent", USER_AGENT) }
        return if (r.status.isSuccess()) r.readRawBytes() else null
    }

    private suspend fun drawMap(slot: PictureSlot, states: Map<String, EntityState>): BufferedImage {
        val (style, ids) = slot.source.removePrefix("map:").split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val dark = style == "dark"
        val people = ids.split(',').mapNotNull { states[it] }.filter { it.number("latitude") != null && it.number("longitude") != null }
        val w = slot.widthPx
        val h = slot.heightPx
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = if (dark) Color(0x1E2226) else Color(0xEEF0EC)
        g.fillRect(0, 0, w, h)

        val lats = people.map { it.number("latitude")!! }
        val lons = people.map { it.number("longitude")!! }
        val centreLat = if (lats.isEmpty()) 52.37 else (lats.min() + lats.max()) / 2
        val centreLon = if (lons.isEmpty()) 4.89 else (lons.min() + lons.max()) / 2
        var zoom = 15
        while (zoom > 3 && people.size > 1) {
            val xs = lons.map { lonToX(it, zoom) }
            val ys = lats.map { latToY(it, zoom) }
            if (xs.max() - xs.min() < w * 0.7 && ys.max() - ys.min() < h * 0.7) break
            zoom--
        }
        val cx = lonToX(centreLon, zoom)
        val cy = latToY(centreLat, zoom)
        val left = cx - w / 2.0
        val top = cy - h / 2.0
        val layer = if (dark) "dark_all" else "light_all"
        for (tx in floor(left / 256).toInt()..floor((left + w) / 256).toInt()) {
            for (ty in floor(top / 256).toInt()..floor((top + h) / 256).toInt()) {
                val n = 1 shl zoom
                if (ty < 0 || ty >= n) continue
                val tile = tile(layer, zoom, ((tx % n) + n) % n, ty) ?: continue
                g.drawImage(tile, (tx * 256 - left).roundToInt(), (ty * 256 - top).roundToInt(), null)
            }
        }
        val r = max(14.0, min(w, h) / 14.0)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, (r * 0.8).roundToInt())
        for ((i, p) in people.withIndex()) {
            val x = lonToX(p.number("longitude")!!, zoom) - left
            val y = latToY(p.number("latitude")!!, zoom) - top
            val dot = Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)
            g.color = MARKERS[i % MARKERS.size]
            g.fill(dot)
            g.color = Color.WHITE
            g.stroke = BasicStroke((r / 6).toFloat())
            g.draw(dot)
            val letters = p.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
            val fm = g.fontMetrics
            g.drawString(letters, (x - fm.stringWidth(letters) / 2.0).toFloat(), (y + fm.ascent / 2.6).toFloat())
        }
        g.dispose()
        return img
    }

    private suspend fun tile(layer: String, z: Int, x: Int, y: Int): BufferedImage? {
        val key = "$layer/$z/$x/$y"
        tiles[key]?.let { return it }
        val bytes = runCatching { fetchUrl("https://basemaps.cartocdn.com/$layer/$z/$x/$y.png") }.getOrNull() ?: return null
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        if (tiles.size > 400) tiles.clear()
        tiles[key] = img
        return img
    }

    companion object {
        private const val USER_AGENT = "RemoteScreens/1 (Home Assistant add-on)"
        private val MARKERS = listOf(Color(0x3D7FD6), Color(0xD65A3D), Color(0x3DA866), Color(0x9B5BD6), Color(0xD6A23D))

        fun lonToX(lon: Double, z: Int) = (lon + 180) / 360 * 256 * 2.0.pow(z)
        fun latToY(lat: Double, z: Int): Double {
            val r = lat * PI / 180
            return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * 256 * 2.0.pow(z)
        }
        /** Scales [src] to cover (crop) or fit within w×h. */
        fun fit(src: BufferedImage, w: Int, h: Int, crop: Boolean): BufferedImage {
            if (src.width == w && src.height == h) return src
            val scale = if (crop) max(w / src.width.toDouble(), h / src.height.toDouble()) else min(w / src.width.toDouble(), h / src.height.toDouble())
            val sw = (src.width * scale).roundToInt()
            val sh = (src.height * scale).roundToInt()
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, null)
            g.dispose()
            return out
        }

        /** JPEG for photos; PNG where there is transparency to keep. */
        fun encode(img: BufferedImage, round: Boolean): ByteArray {
            val out = ByteArrayOutputStream()
            if (round) {
                val masked = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
                val g = masked.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.clip = Ellipse2D.Double(0.0, 0.0, img.width.toDouble(), img.height.toDouble())
                g.drawImage(img, 0, 0, null)
                g.dispose()
                ImageIO.write(masked, "png", out)
                return out.toByteArray()
            }
            val rgb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
            rgb.createGraphics().apply { drawImage(img, 0, 0, Color.BLACK, null); dispose() }
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            val params = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.85f }
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(rgb, null, null), params)
            }
            writer.dispose()
            return out.toByteArray()
        }
    }
}
