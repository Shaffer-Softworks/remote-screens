@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.preview

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.WireBuffer
import androidx.compose.remote.core.operations.BitmapData
import androidx.compose.remote.core.operations.ColorConstant
import androidx.compose.remote.core.operations.FloatConstant
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.core.types.IntegerConstant
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.EntityState
import tech.shaffersoftworks.remotescreens.server.render.RenderedDoc
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.shared.VarValue

private const val COLOR_RADIX = 16
private const val ART_WIDTH = 320
private const val ART_HEIGHT = 180
private const val GRID_STEP = 40
private const val ART_BACKGROUND = 0x23414b
private const val ART_GRID = 0x305a64
private const val ART_FONT_SIZE = 18
private const val ART_LABEL_X = 20
private const val ART_LABEL_Y = 95

private val playerDefaults = listOf(
    VarValue.string(Vars.TIME, "10:08"),
    VarValue.string(Vars.DATE, "Monday, 2 November"),
    VarValue.string(Vars.DAY, "2 NOV 2026"),
    VarValue.string(Vars.BATTERY, "87%"),
    VarValue.string(Vars.WIFI, "4"),
    VarValue.int(Vars.CONNECTED, Vars.VISIBLE),
    VarValue.int(Vars.BATTERY_IDLE, Vars.VISIBLE),
    VarValue.int(Vars.BATTERY_CHARGING, Vars.GONE),
).associateBy { it.name }

/**
 * Bake the same named values the player would receive into the document's initial constants.
 * Work on flat wire operations: CoreDocument nests layout operations during inflation.
 * Production documents and the production renderer are never modified.
 */
fun snapshot(document: RenderedDoc, states: Map<String, EntityState>): ByteArray {
    val values = document.vars(states) + playerDefaults
    val operations = arrayListOf<Operation>()
    RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(document.bytes)).inflateFromBuffer(operations)
    val names = operations.filterIsInstance<NamedVariable>().associateBy { it.mVarId }
    val output = WireBuffer()
    operations.forEach { operation ->
        val name = names[operation.resourceId()]?.mVarName?.removePrefix("USER:")
        val value = values[name]?.value
        val replacement = when {
            operation is BitmapData -> imageSnapshot(operation, names, document)
            value != null -> operation.withValue(value)
            else -> operation
        }
        replacement.write(output)
    }
    return output.buffer.copyOf(output.size).also { bytes ->
        // Fail generation immediately on malformed documents, including unsupported operations.
        CoreDocument().initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
    }
}

private fun Operation.resourceId(): Int? = when (this) {
    is TextData -> mTextId
    is ColorConstant -> mColorId
    is FloatConstant -> mId
    is IntegerConstant -> mId
    else -> null
}

private fun Operation.withValue(value: String): Operation = when (this) {
    is TextData -> TextData(mTextId, value)
    is ColorConstant -> ColorConstant(mColorId, value.toUInt(COLOR_RADIX).toInt())
    is FloatConstant -> FloatConstant(mId, value.toFloat())
    is IntegerConstant -> IntegerConstant(mId, value.toInt())
    else -> this
}

private fun imageSnapshot(
    image: BitmapData,
    names: Map<Int, NamedVariable>,
    document: RenderedDoc,
): BitmapData {
    val named = names[image.mImageId]?.takeIf { it.mVarType == NamedVariable.IMAGE_TYPE } ?: return image
    val name = named.mVarName.removePrefix("USER:")
    val slot = document.docContext.pictures.firstOrNull { it.name == name }
    return slot?.let {
        BitmapData(image.mImageId, image.width, image.height, placeholder(it.source, image.width, image.height))
    } ?: image
}

private val placeholderCache = mutableMapOf<String, ByteArray>()

/** Local, visibly synthetic artwork; generation never fetches camera images or remote URLs. */
private fun placeholder(source: String, width: Int, height: Int): ByteArray =
    placeholderCache.getOrPut("$source:$width:$height") {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        drawPlaceholder(image, source)
        ByteArrayOutputStream().use { stream ->
            check(ImageIO.write(image, "png", stream))
            stream.toByteArray()
        }
    }

private fun drawPlaceholder(image: BufferedImage, source: String) {
    val graphics = image.createGraphics()
    try {
        graphics.scale(image.width.toDouble() / ART_WIDTH, image.height.toDouble() / ART_HEIGHT)
        graphics.color = Color(ART_BACKGROUND)
        graphics.fillRect(0, 0, ART_WIDTH, ART_HEIGHT)
        graphics.color = Color(ART_GRID)
        for (x in 0..ART_WIDTH step GRID_STEP) {
            graphics.drawLine(x, 0, x, ART_HEIGHT)
        }
        for (y in 0..ART_HEIGHT step GRID_STEP) {
            graphics.drawLine(0, y, ART_WIDTH, y)
        }
        graphics.color = Color.WHITE
        graphics.font = Font("SansSerif", Font.BOLD, ART_FONT_SIZE)
        val label = when {
            source.startsWith("map:") -> "Sample map"
            source.contains("camera") -> "Sample camera"
            else -> "Sample artwork"
        }
        graphics.drawString(label, ART_LABEL_X, ART_LABEL_Y)
    } finally {
        graphics.dispose()
    }
}
