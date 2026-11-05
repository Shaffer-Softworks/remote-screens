package tech.shaffersoftworks.remotescreens.server.preview

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.render.AlertButton
import tech.shaffersoftworks.remotescreens.server.render.AlertRenderer
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.ScreenRenderer
import tech.shaffersoftworks.remotescreens.shared.Drawer
import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import tech.shaffersoftworks.remotescreens.shared.NodeStyle
import tech.shaffersoftworks.remotescreens.shared.NodeTypes
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Tile
import tech.shaffersoftworks.remotescreens.shared.TopBar

internal fun PreviewSession.screens() {
    for (typography in listOf(false, true)) {
        val renderInput = input()
        emit("Widgets", if (typography) "Typography" else "Controls", renderInput) {
            widgetGallery(wall, renderInput, typography)
        }
    }
    for ((pageIndex, demoPage) in demo.pages.withIndex()) {
        val renderInput = input()
        emit("Screens", demoPage.title, renderInput) {
            ScreenRenderer.render(screen = demo, device = wall, input = renderInput, initialPage = pageIndex)
        }
    }
    for (panel in extraPages + demo.pages.filter { it.tiles.any(Tile::full) }) {
        page(group = "Panels", label = panel.title, page = panel, canvas = PageCanvas(chrome = true))
    }
    page(
        group = "Portrait",
        label = "Portrait",
        page = demo.pages.first(),
        canvas = PageCanvas(chrome = true, device = Device(WALL_HEIGHT, WALL_WIDTH, 1f)),
    )
    page(
        group = "Large",
        label = "Large tablet",
        page = demo.pages.first(),
        canvas = PageCanvas(chrome = true, device = Device(LARGE_WIDTH, WALL_WIDTH, 1f)),
    )
    page("States", "Empty screen", Page("empty", "Empty"))
    val emptyInput = input(emptyMap(), RenderData())
    for (panel in extraPages + demo.pages.filter { it.tiles.any(Tile::full) }) {
        page(group = "States", label = "${panel.title} · no entities or data", page = panel, renderInput = emptyInput)
    }
}

/**
 * Only the menu key of the modal drawer: Layoutlib paints a single frame, and a state layout starting on
 * any state but the first still paints the first one then.
 */
internal fun PreviewSession.navigation() {
    val hidden = NavBar(show = false)
    val cases = listOf(
        "Menu key in the top bar" to demo.copy(drawer = Drawer(style = "modal")),
        "Menu key on the right, no bottom bar" to
            demo.copy(drawer = Drawer(style = "modal", side = "end"), navBar = hidden),
        "Menu key without the top bar" to
            demo.copy(drawer = Drawer(style = "modal"), topBar = TopBar(show = false), navBar = hidden),
    )
    for ((label, screen) in cases) {
        val renderInput = input()
        emit(group = "Navigation", label = label, renderInput = renderInput) {
            ScreenRenderer.render(screen = screen, device = wall, input = renderInput)
        }
    }
}

internal fun PreviewSession.components() {
    for (type in NodeTypes.ALL.sorted()) {
        val root = Node(
            id = "root",
            type = "box",
            children = listOf(sampleNode(type)),
            size = NodeSize("fill", "fill"),
            style = NodeStyle(padding = SAMPLE_PADDING),
        )
        page("Components", type, Page("preview", type, root = root))
        output.covered += "node:$type"
    }
}

internal fun PreviewSession.alerts() {
    for (level in listOf("info", "warning", "danger")) {
        for (camera in listOf(false, true)) {
            val renderInput = input()
            val alert = Alert(
                title = "Someone is at the front door",
                message = "A sample notification with enough text to check wrapping and spacing.",
                level = level,
                camera = if (camera) "camera.front_door" else null,
                buttons = listOf(AlertButton("Open door"), AlertButton("Turn on lights")),
            )
            emit("Alerts", "$level · camera=$camera", renderInput) {
                AlertRenderer.render(alert, wall, renderInput)
            }
        }
    }
}
