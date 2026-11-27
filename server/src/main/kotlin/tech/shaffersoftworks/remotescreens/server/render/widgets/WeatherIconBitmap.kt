package tech.shaffersoftworks.remotescreens.server.render.widgets

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val ICON_PX = 128
private const val MOVE_TOKENS = 3
private const val CURVE_TOKENS = 7
private const val CURVE_CONTROL_2 = 3
private const val CURVE_END = 5

/** A small PNG of the coloured Home Assistant weather icon, or null when the condition has no artwork. */
fun weatherIconPng(condition: String, nightTime: Boolean = false): ByteArray? {
    val pieces = HaWeatherIcons.pieces(condition = condition, nightTime = nightTime) ?: return null
    val image = BufferedImage(ICON_PX, ICON_PX, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    val scale = ICON_PX / HaWeatherIcons.VIEWPORT

    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.scale(scale.toDouble(), scale.toDouble())

    for (piece in pieces) {
        graphics.color = Color(HaWeatherIcons.color(piece.layer), true)
        graphics.fill(weatherPath(piece.path))
    }

    graphics.dispose()
    val output = ByteArrayOutputStream()

    ImageIO.write(image, "png", output)

    return output.toByteArray()
}

/** Absolute `M` / `L` / `C` / `Z` paths, the same tokens [HaWeatherIcons] draws. */
internal fun weatherPath(source: String): GeneralPath {
    val path = GeneralPath(Path2D.WIND_NON_ZERO)
    val tokens = source.split(' ').filter { it.isNotEmpty() }
    var index = 0

    while (index < tokens.size) {
        when (tokens[index]) {
            "M" -> {
                path.moveTo(tokens.floatAt(index + 1), tokens.floatAt(index + 2))
                index += MOVE_TOKENS
            }
            "L" -> {
                path.lineTo(tokens.floatAt(index + 1), tokens.floatAt(index + 2))
                index += MOVE_TOKENS
            }
            "C" -> {
                path.curveTo(
                    tokens.floatAt(index + 1),
                    tokens.floatAt(index + 2),
                    tokens.floatAt(index + CURVE_CONTROL_2),
                    tokens.floatAt(index + CURVE_CONTROL_2 + 1),
                    tokens.floatAt(index + CURVE_END),
                    tokens.floatAt(index + CURVE_END + 1),
                )

                index += CURVE_TOKENS
            }
            "Z" -> {
                path.closePath()
                index += 1
            }
            else -> index += 1
        }
    }

    return path
}

private fun List<String>.floatAt(index: Int): Float = this.getOrNull(index)?.toFloatOrNull() ?: 0f
