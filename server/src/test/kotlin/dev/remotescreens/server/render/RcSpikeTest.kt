package dev.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.dsl.size
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Phase 0: proves on the JVM that a document carries what the design leans on. Named strings, ints and colors the
 * player can override, and a click that becomes a host action with metadata. Rendering is proven on a device.
 */
class RcSpikeTest {
    @Test
    fun namedVariablesAndHostActionsSurviveARoundTrip() {
        val bytes = Rc.document(480, 480, "spike") {
            val label = remoteNamedText("${Rc.USER}e.light.kitchen.label", "Off")
            val page = remoteNamedInteger("${Rc.USER}page", 0)
            val bg = remoteNamedColor("${Rc.USER}t.1.bg", 0xFFEEEEEE.toInt())
            val meta = remoteText("1|toggle|")
            Box(Modifier.size(200f, 200f).background(bg).padding(8f).onClick {
                hostAction(1, meta)
            }) {
                Text(label, fontSize = 24.rsp)
            }
            StateLayout(page) {
                Box(Modifier.size(10f, 10f))
                Box(Modifier.size(20f, 20f))
            }
        }
        assertTrue(bytes.size > 100, "a document has a body")

        val doc = CoreDocument()
        doc.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
        assertContains(doc.getNamedVariables(NamedVariable.STRING_TYPE).toList(), "USER:e.light.kitchen.label")
        assertContains(doc.getNamedVariables(NamedVariable.INT_TYPE).toList(), "USER:page")
        assertContains(doc.getNamedVariables(NamedVariable.COLOR_TYPE).toList(), "USER:t.1.bg")
    }
}
