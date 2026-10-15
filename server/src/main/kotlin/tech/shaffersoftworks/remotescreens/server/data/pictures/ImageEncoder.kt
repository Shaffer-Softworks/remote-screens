package tech.shaffersoftworks.remotescreens.server.data.pictures

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val JPEG_QUALITY = 0.85f

/** Sizes pictures for a slot and encodes them for the tablet. */
object ImageEncoder {

    /** Scales [source] to cover (crop) or fit within [width]×[height]. */
    fun fit(source: BufferedImage, width: Int, height: Int, isCropped: Boolean): BufferedImage {
        if (source.width == width && source.height == height) return source
        val widthScale = width / source.width.toDouble()
        val heightScale = height / source.height.toDouble()
        val scale = if (isCropped) max(widthScale, heightScale) else min(widthScale, heightScale)
        val scaledWidth = (source.width * scale).roundToInt()
        val scaledHeight = (source.height * scale).roundToInt()
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = output.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(
            source,
            (width - scaledWidth) / 2,
            (height - scaledHeight) / 2,
            scaledWidth,
            scaledHeight,
            null,
        )
        graphics.dispose()
        return output
    }

    /** JPEG for photos; PNG where there is transparency to keep. */
    fun encode(image: BufferedImage, isRound: Boolean): ByteArray =
        if (isRound) encodeRoundPng(image) else encodeJpeg(image)

    private fun encodeRoundPng(image: BufferedImage): ByteArray {
        val masked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = masked.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.clip = Ellipse2D.Double(0.0, 0.0, image.width.toDouble(), image.height.toDouble())
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        val output = ByteArrayOutputStream()
        ImageIO.write(masked, "png", output)
        return output.toByteArray()
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply {
            drawImage(image, 0, 0, Color.BLACK, null)
            dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val parameters = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        val output = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(output).use { imageOutput ->
            writer.output = imageOutput
            writer.write(null, IIOImage(rgb, null, null), parameters)
        }
        writer.dispose()
        return output.toByteArray()
    }
}
