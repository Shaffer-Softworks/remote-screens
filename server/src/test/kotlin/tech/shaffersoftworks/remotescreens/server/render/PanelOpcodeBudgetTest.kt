package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.modifiers.ComponentModifiers
import tech.shaffersoftworks.remotescreens.shared.GridSize
import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TapAction
import tech.shaffersoftworks.remotescreens.shared.TapType
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TileKind
import tech.shaffersoftworks.remotescreens.shared.TopBar
import java.io.ByteArrayInputStream
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The operations a clock and a few entity tiles emit. The ESP32 player implements this set.
 * Shaders, flow, bitmaps and long-press stay out of the page.
 */
class PanelOpcodeBudgetTest : RenderFixture() {
    @Test
    fun aClockAndTilesStayInsideThePanelOpcodeSet() {
        val doc = ScreenRenderer.render(panelScreen(), panelDevice(), input())
        val document = CoreDocument()

        document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(doc.bytes)))

        val names = everyOperation(documentRoots(document)).map { operation ->
            "${operation.javaClass.simpleName}#${opcodeOf(operation)}"
        }.toSortedSet()

        val forbidden = names.filter { entry -> FORBIDDEN.any { prefix -> entry.startsWith(prefix) } }

        writePanelDocument(doc.bytes)
        writeWireOpcodes(doc.bytes)

        assertTrue(forbidden.isEmpty(), "panel page emitted ${forbidden.joinToString()}")

        assertTrue(
            doc.bytes.size < PANEL_BYTE_BUDGET,
            "panel document is ${doc.bytes.size} bytes",
        )

        assertEquals(PANEL_OPCODES, names)
    }

    @Test
    fun aRicherScreenDropsWhatThePanelCannotDraw() {
        val richer = panelScreen().copy(
            topBar = TopBar(show = true),
            navBar = NavBar(show = true),
            pages = listOf(
                panelScreen().pages.single().copy(
                    tiles = panelScreen().pages.single().tiles + listOf(
                        Tile(
                            id = "climate",
                            kind = TileKind.CLIMATE.wire,
                            col = 0,
                            row = 0,
                            full = true,
                            hold = TapAction(type = TapType.CARD),
                        ),
                        Tile(
                            id = "camera",
                            entityId = "camera.front_door",
                            col = 1,
                            row = 0,
                        ),
                        Tile(
                            id = "person",
                            kind = TileKind.PERSON.wire,
                            entityId = "person.michael",
                            col = 0,
                            row = 1,
                        ),
                    ),
                ),
            ),
        )

        val profile = richer.panelProfile()
        val tileIds = profile.pages.single().tiles.map { tile -> tile.id }

        assertEquals(listOf("home"), profile.pages.map { page -> page.id })
        assertEquals(true, profile.topBar.show)
        assertEquals(true, profile.navBar.show)
        assertEquals(listOf("clock", "lamp", "temp"), tileIds)
        assertTrue(profile.pages.single().tiles.all { tile -> tile.hold.type == TapType.NONE })

        val doc = ScreenRenderer.render(profile, panelDevice(), input())
        val document = CoreDocument()

        document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(doc.bytes)))

        val names = everyOperation(documentRoots(document)).map { operation ->
            "${operation.javaClass.simpleName}#${opcodeOf(operation)}"
        }.toSortedSet()

        val forbidden = names.filter { entry -> FORBIDDEN.any { prefix -> entry.startsWith(prefix) } }

        assertTrue(forbidden.isEmpty(), "panel profile emitted ${forbidden.joinToString()}")
        assertTrue(names.all { entry -> entry in PANEL_OPCODES }, "outside the budget: $names")
    }

    @Test
    fun aFreeFormPageKeepsTheLayoutTheEditorShows() {
        val designed = panelScreen().copy(
            pages = listOf(
                panelScreen().pages.single().copy(
                    root = Node(
                        id = "page",
                        type = "column",
                        children = listOf(
                            Node(id = "title", type = "text", props = mapOf("text" to "Kitchen")),
                        ),
                    ),
                ),
            ),
        )

        val profile = designed.panelProfile()

        assertEquals("column", profile.pages.single().root?.type)
        assertEquals("title", profile.pages.single().root?.children?.single()?.id)
        assertEquals(false, profile.topBar.show)
    }

    private fun panelDevice(): Device = Device(
        widthDp = PANEL_WIDTH_DP,
        heightDp = PANEL_HEIGHT_DP,
        density = PANEL_DENSITY,
    )

    private fun panelScreen(): Screen {
        val quiet = TapAction(type = TapType.NONE)

        return Screen(
            id = "panel",
            name = "Panel",
            grid = GridSize(cols = PANEL_COLUMNS, rows = PANEL_ROWS),
            topBar = TopBar(show = false),
            navBar = NavBar(show = false),
            pages = listOf(
                Page(
                    id = "home",
                    title = "Home",
                    tiles = listOf(
                        Tile(
                            id = "clock",
                            kind = TileKind.CLOCK.wire,
                            col = 0,
                            row = 0,
                            w = PANEL_COLUMNS,
                            h = 1,
                            tap = quiet,
                            hold = quiet,
                        ),
                        Tile(
                            id = "lamp",
                            entityId = "light.table_lamp",
                            col = 0,
                            row = 1,
                            tap = TapAction(type = TapType.TOGGLE),
                            hold = quiet,
                        ),
                        Tile(
                            id = "temp",
                            entityId = "sensor.kitchen_temperature",
                            col = 1,
                            row = 1,
                            tap = quiet,
                            hold = quiet,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun opcodeOf(operation: Operation): Int {
        val type = operation.javaClass

        val field = type.declaredFields.firstOrNull { it.name == "OP_CODE" }
            ?: type.fields.firstOrNull { it.name == "OP_CODE" }

        if (field != null) {
            field.isAccessible = true

            return field.getInt(null)
        }

        val method = type.methods.firstOrNull { it.name == "id" && it.parameterCount == 0 }

        return if (method != null) method.invoke(null) as Int else -1
    }

    private fun documentRoots(document: CoreDocument): List<Operation> {
        val operations = CoreDocument::class.java.getDeclaredField("mOperations")

        operations.isAccessible = true

        return (operations.get(document) as List<*>).filterIsInstance<Operation>()
    }

    private fun everyOperation(roots: List<Operation>): List<Operation> {
        val found = mutableListOf<Operation>()
        val seen = Collections.newSetFromMap(IdentityHashMap<Operation, Boolean>())

        fun visit(operation: Operation) {
            if (!seen.add(operation)) return

            found += operation

            val nested = when (operation) {
                is ComponentModifiers -> operation.list
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

    private fun writePanelDocument(bytes: ByteArray) {
        val out = File(PANEL_DOCUMENT)

        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
    }

    private fun writeWireOpcodes(bytes: ByteArray) {
        val flat = ArrayList<Operation>()

        RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)).inflateFromBuffer(flat)

        val lines = flat.map { operation -> "${operation.javaClass.simpleName}#${opcodeOf(operation)}" }
        val out = File(WIRE_OPCODE_LIST)

        out.parentFile?.mkdirs()
        out.writeText(lines.joinToString(separator = "\n"))
        assertEquals(WIRE_OPCODES, lines.toSortedSet())
    }

    private companion object {
        const val PANEL_WIDTH_DP = 320
        const val PANEL_HEIGHT_DP = 240
        const val PANEL_DENSITY = 1f
        const val PANEL_COLUMNS = 2
        const val PANEL_ROWS = 2
        const val PANEL_BYTE_BUDGET = 96 * 1024
        const val PANEL_DOCUMENT = "../firmware/esp32/testdata/panel.rc"
        const val WIRE_OPCODE_LIST = "build/panel-wire-opcodes.txt"

        val PANEL_OPCODES = setOf(
            "BackgroundModifierOperation#55",
            "BoxLayout#202",
            "CanvasLayout#205",
            "ClickModifierOperation#59",
            "CollapsibleColumnLayout#233",
            "ColorConstant#138",
            "ColumnLayout#204",
            "ComponentModifiers#-1",
            "ComponentVisibilityOperation#211",
            "CoreSemantics#-1",
            "CoreText#239",
            "DimensionConstraintsModifierOperation#243",
            "DrawPath#124",
            "DrawTextAnchored#133",
            "FitBoxLayout#176",
            "Header#0",
            "HeightModifierOperation#67",
            "HostActionMetadataOperation#216",
            "IntegerConstant#140",
            "MatrixRestore#131",
            "MatrixSave#130",
            "NamedVariable#137",
            "PaddingModifierOperation#58",
            "PaintData#40",
            "PathData#123",
            "RootLayoutComponent#200",
            "RoundedClipRectModifierOperation#54",
            "RowLayout#203",
            "StateLayout#217",
            "TextData#102",
            "WidthModifierOperation#16",
        )

        val WIRE_OPCODES = setOf(
            "BackgroundModifierOperation#55",
            "BoxLayout#202",
            "CanvasLayout#205",
            "ClickModifierOperation#59",
            "CollapsibleColumnLayout#233",
            "ColorConstant#138",
            "ColumnLayout#204",
            "ComponentVisibilityOperation#211",
            "ContainerEnd#214",
            "CoreSemantics#-1",
            "CoreText#239",
            "DimensionConstraintsModifierOperation#243",
            "DrawPath#124",
            "DrawTextAnchored#133",
            "FitBoxLayout#176",
            "Header#0",
            "HeightModifierOperation#67",
            "HostActionMetadataOperation#216",
            "IntegerConstant#140",
            "LayoutComponentContent#201",
            "MatrixRestore#131",
            "MatrixSave#130",
            "NamedVariable#137",
            "PaddingModifierOperation#58",
            "PaintData#40",
            "PathData#123",
            "RootLayoutComponent#200",
            "RoundedClipRectModifierOperation#54",
            "RowLayout#203",
            "StateLayout#217",
            "TextData#102",
            "WidthModifierOperation#16",
        )

        val FORBIDDEN = setOf(
            "ShaderData",
            "FlowLayout",
            "DrawBitmap",
            "DrawBitmapInt",
            "DrawBitmapScaled",
            "BitmapData",
            "MultiClickModifier",
            "ImageLayout",
        )
    }
}
