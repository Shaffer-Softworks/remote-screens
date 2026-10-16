package tech.shaffersoftworks.remotescreens.server.render

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

private const val QUIET_ZONE_MODULES = 1
private const val BLACK_RGB = 0xFF000000.toInt()
private const val WHITE_RGB = 0xFFFFFFFF.toInt()

/** Encodes [text] as a square QR bitmap of [sizePx] pixels for a named document image. */
object QrBitmaps {
    fun encode(text: String, sizePx: Int): BufferedImage {
        val side = sizePx.coerceAtLeast(1)
        val matrix = QRCodeWriter().encode(
            text.ifBlank { " " },
            BarcodeFormat.QR_CODE,
            side,
            side,
            mapOf(EncodeHintType.MARGIN to QUIET_ZONE_MODULES),
        )
        val image = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until side) {
            for (x in 0 until side) {
                image.setRGB(x, y, if (matrix.get(x, y)) BLACK_RGB else WHITE_RGB)
            }
        }
        return image
    }
}
