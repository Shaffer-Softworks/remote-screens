package dev.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.size
import dev.remotescreens.server.I18n
import dev.remotescreens.server.ha.HaConfig
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** Why [DocContext.build] declares everything at the top: alpha20 loses what a layout component declares. */
class NestingProbeTest {
    private fun names(bytes: ByteArray): List<String> {
        val doc = CoreDocument()
        doc.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
        return doc.getNamedVariables(NamedVariable.STRING_TYPE).toList()
    }

    @Test
    fun aVariableDeclaredInsideABoxIsLost() {
        val bytes = Rc.document(100, 100, "nested") {
            Box(Modifier.size(50f, 50f)) { Text(remoteNamedText("USER:inner", "x")) }
        }
        assertEquals(emptyList(), names(bytes), "if this fails, alpha20's loss is fixed and DocContext.build may go back to one pass")
    }

    @Test
    fun buildDeclaresItAtTheTop() {
        val i18n = I18n("en", emptyMap(), emptyMap(), true)
        val (bytes, _) = DocContext.build(100, 100, "nested", { DocContext(Units(1f), false, i18n, emptyMap(), HaConfig(), RenderData()) }) { ctx ->
            Box(Modifier.size(50f, 50f)) { Text(ctx.text("inner")) }
        }
        assertEquals(listOf("USER:inner"), names(bytes))
    }
}
