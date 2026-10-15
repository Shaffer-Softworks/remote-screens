package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.layout.ClickModifierOperation
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.MultiClickModifier
import androidx.compose.remote.core.operations.layout.managers.ImageLayout
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeBinding
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/** Camera images must keep a click target so a tap opens the card (and its live video). */
class CameraImageClickTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)
    private val tablet = Device(1280, 800, 1.5f)

    private fun input() = RenderInput(ha.states.value, i18n, ha.config.value, RenderData())

    private fun parse(bytes: ByteArray) =
        CoreDocument().also { it.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes))) }

    private fun ops(list: List<Operation>): List<Operation> =
        list.flatMap { op -> listOf(op) + if (op is Container) ops(op.list) else emptyList() }

    private fun hasClickTarget(root: Component?, operations: List<Operation>): Boolean {
        fun walk(c: Component): Boolean {
            val mods = (c as? LayoutComponent)?.componentModifiers?.modifiersList.orEmpty()
            if (mods.any { it is ClickModifierOperation || it is MultiClickModifier }) return true
            val kids = ArrayList<Component>()
            c.getComponents(kids)
            return kids.any(::walk)
        }
        return (root != null && walk(root)) ||
            ops(operations).any { it is ClickModifierOperation || it is MultiClickModifier }
    }

    private fun hasImage(root: Component?): Boolean {
        if (root == null) return false
        fun walk(c: Component): Boolean {
            if (c is ImageLayout) return true
            val kids = ArrayList<Component>()
            c.getComponents(kids)
            return kids.any(::walk)
        }
        return walk(root)
    }

    @Test
    fun freeFormCameraImageDocumentHasAClickTarget() {
        val page = Page(
            "one",
            "Cameras",
            root = Node(
                "root",
                "column",
                size = NodeSize("fill", "fill"),
                children = listOf(
                    Node("cam", "image", size = NodeSize("dp:160", "dp:90"), bind = NodeBinding("camera.front_door")),
                ),
            ),
        )
        val doc = ScreenRenderer.render(Screen("s", "S", pages = listOf(page)), tablet, input())
        val core = parse(doc.bytes)
        assertTrue(hasImage(core.rootLayoutComponent), "camera image is in the document")
        assertTrue(hasClickTarget(core.rootLayoutComponent, core.operations), "camera image must keep a click target")
    }

    @Test
    fun gridCameraTileDocumentHasAClickTarget() {
        val page = Page(
            "one",
            "Cameras",
            tiles = listOf(Tile("t1", entityId = "camera.front_door", w = 2, h = 2)),
        )
        val doc = ScreenRenderer.render(Screen("s", "S", pages = listOf(page)), tablet, input())
        val core = parse(doc.bytes)
        assertTrue(hasImage(core.rootLayoutComponent), "camera image is in the document")
        assertTrue(hasClickTarget(core.rootLayoutComponent, core.operations), "camera tile must keep a click target")
    }
}
