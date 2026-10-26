package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.modifiers.ComponentModifiers
import androidx.compose.remote.core.operations.layout.modifiers.ScrollModifierOperation
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.size
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollKeepTest {
    @Test
    fun aScrollerKeepsItsComponentIdAcrossBuilds() {
        val name = "lights.tile.rooms"
        val id = scrollComponentId(name)
        assertTrue(id >= SCROLL_COMPONENT_BASE)
        assertTrue(id < SCROLL_COMPONENT_BASE + ID_SPACE)
        assertEquals(id, scrollComponentId(name))

        val first = scrollerIds(name)
        val second = scrollerIds(name)
        assertEquals(listOf(id), first)
        assertEquals(first, second)
    }

    @Test
    fun theSameScrollDocumentIsByteForByteTheSame() {
        val first = scrollDocument("calendar.agenda")
        val second = scrollDocument("calendar.agenda")
        assertTrue(first.contentEquals(second))
    }

    private fun scrollerIds(name: String): List<Int> {
        val document = CoreDocument()
        document.initFromBuffer(
            RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(scrollDocument(name))),
        )
        val found = mutableListOf<Int>()
        val root = document.rootLayoutComponent ?: return found
        walk(root) { component ->
            if (component.ownsVerticalScroll()) found += component.id
        }
        return found
    }

    private fun Component.ownsVerticalScroll(): Boolean {
        for (operation in mList) {
            val modifiers = operation as? ComponentModifiers ?: continue
            for (modifier in modifiers.list) {
                if (modifier is ScrollModifierOperation && modifier.isVerticalScroll()) return true
            }
        }
        return false
    }

    private fun walk(component: Component, visit: (Component) -> Unit) {
        visit(component)
        for (operation in component.mList) {
            val nested = operation as? Component ?: continue
            walk(nested, visit)
        }
        val laidOut = (component as? LayoutComponent)?.childrenComponents ?: return
        for (child in laidOut) walk(child, visit)
    }

    private fun scrollDocument(name: String): ByteArray = Rc.document(WIDTH, HEIGHT, "scroll") {
        Column(Modifier.size(LIST_SIZE, LIST_SIZE).keptVerticalScroll(name)) {}
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 200
        const val LIST_SIZE = 80f
        const val ID_SPACE = 0x1_0000
    }
}
