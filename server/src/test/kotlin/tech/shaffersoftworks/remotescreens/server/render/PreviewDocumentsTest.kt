@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.ColorConstant
import androidx.compose.remote.core.operations.FloatConstant
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.core.types.IntegerConstant
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.fillMaxSize
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import androidx.compose.remote.core.WireBuffer
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.preview.snapshot
import tech.shaffersoftworks.remotescreens.shared.VarValue

class PreviewDocumentsTest {
    private fun operations(bytes: ByteArray) = arrayListOf<Operation>().also {
        RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)).inflateFromBuffer(it)
    }

    @Test
    fun snapshotBakesAllValueTypesWithoutChangingLayoutOrProductionBytes() {
        val input = RenderInput(
            emptyMap(),
            I18n("en", I18n.load("en"), emptyMap(), true),
            HomeAssistantConfig(),
            RenderData(),
        )
        val values = listOf(
            VarValue.string("test.text", "Kitchen · 21 °C"),
            VarValue.color("test.color", 0xff12ab34.toInt()),
            VarValue.int("test.int", -7),
            VarValue.float("test.float", 0.625f),
        )
        val (bytes, context) = DocContext.build(
            800,
            480,
            "Preview test",
            { input.context(Device(800, 480, 1f)) },
        ) { doc ->
            doc.text("test.text")
            doc.color("test.color")
            doc.int("test.int")
            doc.float("test.float")
            doc.bind(null) { values }
            Box(Modifier.fillMaxSize())
        }
        val original = bytes.copyOf()
        val baked = snapshot(RenderedDoc(bytes, context), emptyMap())
        val before = operations(bytes)
        val after = operations(baked)
        assertContentEquals(original, bytes)
        assertEquals(before.map { it.javaClass }, after.map { it.javaClass })
        val names = after.filterIsInstance<NamedVariable>().associate { it.mVarName to it.mVarId }
        assertEquals("Kitchen · 21 °C", after.filterIsInstance<TextData>()
            .single { it.mTextId == names.getValue("USER:test.text") }.mText)
        assertEquals(0xff12ab34.toInt(), after.filterIsInstance<ColorConstant>()
            .single { it.mColorId == names.getValue("USER:test.color") }.mColor)
        fun wire(operation: Operation): ByteArray {
            val buffer = WireBuffer()
            operation.write(buffer)
            return buffer.buffer.copyOf(buffer.size)
        }
        val integer = after.filterIsInstance<IntegerConstant>()
            .single { it.mId == names.getValue("USER:test.int") }
        assertContentEquals(wire(IntegerConstant(names.getValue("USER:test.int"), -7)), wire(integer))
        assertEquals(0.625f, after.filterIsInstance<FloatConstant>()
            .single { it.mId == names.getValue("USER:test.float") }.mValue)
    }

    @Test
    fun playerClockValuesHaveVisiblePreviewDefaults() {
        val input = RenderInput(
            emptyMap(),
            I18n("en", I18n.load("en"), emptyMap(), true),
            HomeAssistantConfig(),
            RenderData(),
        )
        val (bytes, context) = DocContext.build(800, 480, "Clock", { input.context(Device(800, 480, 1f)) }) { doc ->
            doc.text(Vars.TIME)
            Box(Modifier.fillMaxSize())
        }
        val after = operations(snapshot(RenderedDoc(bytes, context), emptyMap()))
        val time = after.filterIsInstance<NamedVariable>().single { it.mVarName == "USER:" + Vars.TIME }
        assertEquals("10:08", after.filterIsInstance<TextData>().single { it.mTextId == time.mVarId }.mText)
    }
}
