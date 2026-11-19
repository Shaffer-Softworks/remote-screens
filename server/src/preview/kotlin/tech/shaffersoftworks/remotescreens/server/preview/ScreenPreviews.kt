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

private fun PreviewSession.orientationPages() {
    page(
        group = "Portrait",
        label = "Portrait",
        page = demo.pages.first(),
        canvas = PageCanvas(
            chrome = true,
            device = Device(
                widthDp = WALL_HEIGHT,
                heightDp = WALL_WIDTH,
                density = 1f,
            ),
        ),
    )

    page(
        group = "Large",
        label = "Large tablet",
        page = demo.pages.first(),
        canvas = PageCanvas(
            chrome = true,
            device = Device(
                widthDp = LARGE_WIDTH,
                heightDp = WALL_WIDTH,
                density = 1f,
            ),
        ),
    )
}

internal fun PreviewSession.screens() {
    for (typography in listOf(false, true)) {
        val renderInput = input()

        emit(
            group = "Widgets",
            label = if (typography) "Typography" else "Controls",
            renderInput = renderInput,
        ) {
            widgetGallery(
                device = wall,
                input = renderInput,
                typography = typography,
            )
        }
    }

    for ((pageIndex, demoPage) in demo.pages.withIndex()) {
        val renderInput = input()

        emit(
            group = "Screens",
            label = demoPage.title,
            renderInput = renderInput,
        ) {
            ScreenRenderer.render(screen = demo, device = wall, input = renderInput, initialPage = pageIndex)
        }
    }

    for (panel in extraPages + demo.pages.filter { it.tiles.any(Tile::full) }) {
        page(group = "Panels", label = panel.title, page = panel, canvas = PageCanvas(chrome = true))
    }

    orientationPages()

    page(
        group = "States",
        label = "Empty screen",
        page = Page(id = "empty", title = "Empty"),
    )

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

        page(
            group = "Components",
            label = type,
            page = Page(
                id = "preview",
                title = type,
                root = root,
            ),
        )

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

            emit(
                group = "Alerts",
                label = "$level · camera=$camera",
                renderInput = renderInput,
            ) {
                AlertRenderer.render(
                    alert = alert,
                    device = wall,
                    input = renderInput,
                )
            }
        }
    }
}
