package tech.shaffersoftworks.remotescreens.server.data.pictures

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

private const val TILE_SIZE = 256
private const val MAX_ZOOM = 15
private const val MIN_ZOOM = 3
private const val FIT_FRACTION = 0.7
private const val DEFAULT_LATITUDE = 52.37
private const val DEFAULT_LONGITUDE = 4.89
private const val MIN_MARKER_RADIUS = 14.0
private const val MARKER_RADIUS_DIVISOR = 14.0
private const val MARKER_FONT_SCALE = 0.8
private const val MARKER_STROKE_DIVISOR = 6
private const val MARKER_TEXT_BASELINE_DIVISOR = 2.6
private const val INITIALS_COUNT = 2
private const val DARK_OVERLAY_ALPHA = 140
private val LIGHT_BACKGROUND = Color(0xEEF0EC)
private val DARK_BACKGROUND = Color(0x1E2226)
private val MARKER_COLOURS = listOf(
    Color(0x3D7FD6),
    Color(0xD65A3D),
    Color(0x3DA866),
    Color(0x9B5BD6),
    Color(0xD6A23D),
)

/** Draws a map of the people in a picture slot from map tiles, with a marker with their initials for each. */
class MapRenderer(private val tile: suspend (zoom: Int, x: Int, y: Int) -> BufferedImage?) {

    suspend fun draw(entities: List<EntityState>, width: Int, height: Int, isDark: Boolean): BufferedImage {
        val people = entities.mapNotNull { entity ->
            val latitude = entity.number("latitude")
            val longitude = entity.number("longitude")
            if (latitude != null && longitude != null) PlacedPerson(entity.name, latitude, longitude) else null
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.color = if (isDark) DARK_BACKGROUND else LIGHT_BACKGROUND
        graphics.fillRect(0, 0, width, height)

        val view = viewOf(people, width, height)
        drawTiles(graphics, view, width, height)
        if (isDark) {
            graphics.color = Color(0, 0, 0, DARK_OVERLAY_ALPHA)
            graphics.fillRect(0, 0, width, height)
        }
        drawMarkers(graphics, people, view, min(width, height))
        graphics.dispose()
        return image
    }

    private data class PlacedPerson(val name: String, val latitude: Double, val longitude: Double)

    /** The zoom level and the world pixel of the image's top-left corner. */
    private data class MapView(val zoom: Int, val left: Double, val top: Double)

    /** Centred on everyone, zoomed out until everyone fits. */
    private fun viewOf(people: List<PlacedPerson>, width: Int, height: Int): MapView {
        val latitudes = people.map { it.latitude }
        val longitudes = people.map { it.longitude }
        val centreLatitude = if (latitudes.isEmpty()) DEFAULT_LATITUDE else (latitudes.min() + latitudes.max()) / 2
        val centreLongitude = if (longitudes.isEmpty()) DEFAULT_LONGITUDE else (longitudes.min() + longitudes.max()) / 2
        var zoom = MAX_ZOOM
        while (zoom > MIN_ZOOM && people.size > 1) {
            val xs = longitudes.map { WebMercator.longitudeToX(it, zoom) }
            val ys = latitudes.map { WebMercator.latitudeToY(it, zoom) }
            val isFitting = xs.max() - xs.min() < width * FIT_FRACTION && ys.max() - ys.min() < height * FIT_FRACTION
            if (isFitting) break
            zoom--
        }
        val centreX = WebMercator.longitudeToX(centreLongitude, zoom)
        val centreY = WebMercator.latitudeToY(centreLatitude, zoom)
        return MapView(zoom, centreX - width / 2.0, centreY - height / 2.0)
    }

    private suspend fun drawTiles(graphics: Graphics2D, view: MapView, width: Int, height: Int) {
        val tileCount = 1 shl view.zoom
        val firstColumn = floor(view.left / TILE_SIZE).toInt()
        val lastColumn = floor((view.left + width) / TILE_SIZE).toInt()
        val firstRow = floor(view.top / TILE_SIZE).toInt().coerceAtLeast(0)
        val lastRow = floor((view.top + height) / TILE_SIZE).toInt().coerceAtMost(tileCount - 1)
        for (column in firstColumn..lastColumn) {
            for (row in firstRow..lastRow) {
                val wrappedColumn = ((column % tileCount) + tileCount) % tileCount
                tile(view.zoom, wrappedColumn, row)?.let { tileImage ->
                    val x = (column * TILE_SIZE - view.left).roundToInt()
                    val y = (row * TILE_SIZE - view.top).roundToInt()
                    graphics.drawImage(tileImage, x, y, null)
                }
            }
        }
    }

    private fun drawMarkers(graphics: Graphics2D, people: List<PlacedPerson>, view: MapView, shortestSide: Int) {
        val radius = max(MIN_MARKER_RADIUS, shortestSide / MARKER_RADIUS_DIVISOR)
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (radius * MARKER_FONT_SCALE).roundToInt())
        for ((index, person) in people.withIndex()) {
            val x = WebMercator.longitudeToX(person.longitude, view.zoom) - view.left
            val y = WebMercator.latitudeToY(person.latitude, view.zoom) - view.top
            val dot = Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius)
            graphics.color = MARKER_COLOURS[index % MARKER_COLOURS.size]
            graphics.fill(dot)
            graphics.color = Color.WHITE
            graphics.stroke = BasicStroke((radius / MARKER_STROKE_DIVISOR).toFloat())
            graphics.draw(dot)
            val initials = person.name.split(' ')
                .filter { it.isNotBlank() }
                .take(INITIALS_COUNT)
                .joinToString("") { it.take(1).uppercase() }
            val metrics = graphics.fontMetrics
            graphics.drawString(
                initials,
                (x - metrics.stringWidth(initials) / 2.0).toFloat(),
                (y + metrics.ascent / MARKER_TEXT_BASELINE_DIVISOR).toFloat(),
            )
        }
    }
}

/** The projection of web map tiles: world pixels at a zoom level, [TILE_SIZE] pixels per tile. */
object WebMercator {
    private const val HALF_TURN_DEGREES = 180.0
    private const val FULL_TURN_DEGREES = 360.0

    fun longitudeToX(longitude: Double, zoom: Int): Double =
        (longitude + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * worldSize(zoom)

    fun latitudeToY(latitude: Double, zoom: Int): Double {
        val radians = latitude * PI / HALF_TURN_DEGREES
        return (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * worldSize(zoom)
    }

    private fun worldSize(zoom: Int): Double = TILE_SIZE * 2.0.pow(zoom)
}
