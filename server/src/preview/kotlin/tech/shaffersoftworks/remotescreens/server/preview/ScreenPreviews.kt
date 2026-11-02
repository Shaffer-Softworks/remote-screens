package tech.shaffersoftworks.remotescreens.server.preview

import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.render.Alert
import tech.shaffersoftworks.remotescreens.server.render.AlertButton
import tech.shaffersoftworks.remotescreens.server.render.AlertRenderer
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.ScreenRenderer
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import tech.shaffersoftworks.remotescreens.shared.NodeStyle
import tech.shaffersoftworks.remotescreens.shared.NodeTypes
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Tile

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
            ScreenRenderer.render(demo, wall, renderInput, initialPage = pageIndex)
        }
    }
    for (panel in extraPages + demo.pages.filter { it.tiles.any(Tile::full) }) {
        page("Panels", panel.title, panel, canvas = PageCanvas(chrome = true))
    }
    page(
        "Portrait",
        "Portrait",
        demo.pages.first(),
        canvas = PageCanvas(chrome = true, device = Device(WALL_HEIGHT, WALL_WIDTH, 1f)),
    )
    page(
        "Large",
        "Large tablet",
        demo.pages.first(),
        canvas = PageCanvas(chrome = true, device = Device(LARGE_WIDTH, WALL_WIDTH, 1f)),
    )
    page("States", "Empty screen", Page("empty", "Empty"))
    val emptyInput = input(emptyMap(), RenderData())
    for (panel in extraPages + demo.pages.filter { it.tiles.any(Tile::full) }) {
        page("States", "${panel.title} · no entities or data", panel, emptyInput)
    }
}

internal fun PreviewSession.components() {
    for (type in NodeTypes.ALL.sorted()) {
        val root = Node(
            "root",
            "box",
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
                "Someone is at the front door",
                "A sample notification with enough text to check wrapping and spacing.",
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
