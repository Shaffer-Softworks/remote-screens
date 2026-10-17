package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.layout.ClickModifierOperation
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.MultiClickModifier
import androidx.compose.remote.core.operations.layout.managers.CoreText
import androidx.compose.remote.core.operations.layout.managers.ImageLayout
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeBinding
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tappable free-form bits (camera images, bound text) must keep a click target so a tap reaches the add-on. */
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

    private fun hasText(root: Component?): Boolean {
        if (root == null) return false
        fun walk(c: Component): Boolean {
            if (c is CoreText) return true
            val kids = ArrayList<Component>()
            c.getComponents(kids)
            return kids.any(::walk)
        }
        return walk(root)
    }

    /** True when a CoreText itself carries a click modifier (the fragile path we wrap away). */
    private fun textItselfHasClick(root: Component?): Boolean {
        if (root == null) return false
        fun walk(c: Component): Boolean {
            if (c is CoreText) {
                val mods = c.componentModifiers?.modifiersList.orEmpty()
                if (mods.any { it is ClickModifierOperation || it is MultiClickModifier }) return true
            }
            val kids = ArrayList<Component>()
            c.getComponents(kids)
            return kids.any(::walk)
        }
        return walk(root)
    }

    /**
     * True when a Box that wraps only a CoreText (our free-form text pattern) carries a click.
     * Nav chrome also has CoreText children, but not as a sole child of a Box.
     */
    private fun textParentHasClick(root: Component?): Boolean {
        if (root == null) return false
        fun walk(c: Component): Boolean {
            val kids = ArrayList<Component>()
            c.getComponents(kids)
            if (kids.size == 1 && kids[0] is CoreText) {
                val mods = (c as? LayoutComponent)?.componentModifiers?.modifiersList.orEmpty()
                if (mods.any { it is ClickModifierOperation || it is MultiClickModifier }) return true
            }
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

    @Test
    fun freeFormBoundTextDocumentHasAClickTarget() {
        val page = Page(
            "one",
            "Labels",
            root = Node(
                "root",
                "column",
                size = NodeSize("fill", "fill"),
                children = listOf(
                    Node(
                        "label",
                        "text",
                        size = NodeSize("wrap", "wrap"),
                        props = mapOf("text" to "Lamp"),
                        bind = NodeBinding("light.living_room"),
                    ),
                ),
            ),
        )
        val doc = ScreenRenderer.render(Screen("s", "S", pages = listOf(page)), tablet, input())
        val core = parse(doc.bytes)
        assertTrue(hasText(core.rootLayoutComponent), "bound text is in the document")
        assertTrue(hasClickTarget(core.rootLayoutComponent, core.operations), "bound text must keep a click target")
        assertFalse(textItselfHasClick(core.rootLayoutComponent), "click must sit on a Box around the Text")
        assertTrue(textParentHasClick(core.rootLayoutComponent), "click must sit on the Text's Box")
    }

    @Test
    fun freeFormTextWithPageTapDocumentHasAClickTarget() {
        val page = Page(
            "one",
            "Home",
            root = Node(
                "root",
                "column",
                size = NodeSize("fill", "fill"),
                children = listOf(
                    Node(
                        "go",
                        "text",
                        size = NodeSize("wrap", "wrap"),
                        props = mapOf("text" to "Rooms"),
                        tap = TapAction(TapType.PAGE, page = "two"),
                    ),
                ),
            ),
        )
        val screen = Screen(
            "s",
            "S",
            pages = listOf(page, Page("two", "Rooms", tiles = emptyList())),
        )
        val doc = ScreenRenderer.render(screen, tablet, input())
        val core = parse(doc.bytes)
        assertTrue(hasText(core.rootLayoutComponent), "tappable text is in the document")
        assertTrue(hasClickTarget(core.rootLayoutComponent, core.operations), "page-tap text must keep a click target")
        assertFalse(textItselfHasClick(core.rootLayoutComponent), "click must sit on a Box around the Text")
        assertTrue(textParentHasClick(core.rootLayoutComponent), "click must sit on the Text's Box")
    }

    @Test
    fun freeFormPlainTextDoesNotPutClickOnItsBox() {
        val page = Page(
            "one",
            "Labels",
            root = Node(
                "root",
                "column",
                size = NodeSize("fill", "fill"),
                children = listOf(
                    Node("label", "text", size = NodeSize("wrap", "wrap"), props = mapOf("text" to "Hello")),
                ),
            ),
        )
        val doc = ScreenRenderer.render(Screen("s", "S", pages = listOf(page)), tablet, input())
        val core = parse(doc.bytes)
        assertTrue(hasText(core.rootLayoutComponent), "plain text is in the document")
        assertFalse(textItselfHasClick(core.rootLayoutComponent), "plain text must not carry a click")
        assertFalse(
            textParentHasClick(core.rootLayoutComponent),
            "plain text's Box must not carry a click (chrome clicks elsewhere are fine)",
        )
    }
}
