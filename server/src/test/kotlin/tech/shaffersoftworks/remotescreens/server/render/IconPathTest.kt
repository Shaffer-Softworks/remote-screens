package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.MatrixScale
import androidx.compose.remote.core.operations.PathData
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.theme.Units
import tech.shaffersoftworks.remotescreens.server.render.widgets.icon
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Icons are drawn in pixels. A canvas scale of the 24-grid is a soft bitmap on Android 8. */
class IconPathTest {
    @Test
    fun pathCoordinatesAreMultipliedByTheScale() {
        val path = Rc.path("M 1 2 L 3 4 Z", scale = COORDINATE_SCALE)
        val coordinates = path.createFloatArray().filter { it.isFinite() }
        assertEquals(SCALED_END, coordinates.maxOrNull())
    }

    @Test
    fun anIconIsStoredInPixelsAndNotScaledOnTheCanvas() {
        val i18n = I18n("en", emptyMap(), emptyMap(), true)
        val (bytes, _) = DocContext.build(
            200,
            200,
            "icon",
            {
                DocContext(
                    Units(ICON_DENSITY),
                    Themes.RPI,
                    i18n,
                    emptyMap(),
                    HomeAssistantConfig(),
                    RenderData(),
                )
            },
        ) { doc ->
            doc.icon("home", ICON_SIZE_DP, doc.fixed(0xFFFFFFFF.toInt()))
        }
        val document = CoreDocument()
        document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
        val operations = everyOperation(documentRoots(document))
        assertTrue(operations.none { it is MatrixScale }, "a canvas scale stretches the 24-grid bitmap")
        val largest = operations.filterIsInstance<PathData>()
            .flatMap { it.floatPath.filter(Float::isFinite) }
            .maxOrNull()
        assertTrue(
            largest != null && largest > ICON_VIEWPORT,
            "path stays on the 24-grid (largest $largest)",
        )
    }

    private fun documentRoots(document: CoreDocument): List<Operation> {
        val operations = CoreDocument::class.java.getDeclaredField("mOperations")
        operations.isAccessible = true
        val roots = operations.get(document) as List<*>
        return roots.filterIsInstance<Operation>()
    }

    private fun everyOperation(roots: List<Operation>): List<Operation> {
        val found = mutableListOf<Operation>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Operation, Boolean>())
        fun visit(operation: Operation) {
            if (!seen.add(operation)) return
            found += operation
            val nested = when (operation) {
                is LayoutComponent -> listOfNotNull(
                    operation.canvasOperations,
                    operation.drawContentOperations,
                ).flatMap { it.list } + operation.list
                is Container -> operation.list
                is Component -> operation.list
                else -> emptyList()
            }
            nested.forEach { visit(it) }
        }
        roots.forEach { visit(it) }
        return found
    }

    private companion object {
        const val ICON_DENSITY = 2f
        const val ICON_SIZE_DP = 48f
        const val ICON_VIEWPORT = 24f
        const val COORDINATE_SCALE = 10f
        const val SCALED_END = 40f
    }
}
