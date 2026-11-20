package tech.shaffersoftworks.remotescreens.server.render.freeform

import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeSize
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LvglExportTest {
    private val words = I18n(
        language = "en",
        ownWords = I18n.load("en"),
        homeAssistantWords = emptyMap(),
        is24Hour = true,
    )

    private fun page(root: Node) = LvglPage(
        root = root,
        palette = Themes.RPI,
        widthPx = 800,
        heightPx = 480,
        screenId = "living",
        pageId = "home",
        sources = lvglSources(states = emptyMap(), i18n = words),
    )

    @Test
    fun aColumnOfTextAndAButtonBecomesFlexAndATap() {
        val document = LvglExport.render(
            page = page(
                root = Node(
                    id = "root",
                    type = "column",
                    size = NodeSize(width = "fill", height = "fill"),
                    children = listOf(
                        Node(id = "hello", type = "text", props = mapOf("text" to "Hello")),
                        Node(id = "go", type = "button", props = mapOf("text" to "Go")),
                        Node(id = "cam", type = "tile"),
                    ),
                ),
            ),
        )

        assertContains(document.yaml, "flex_flow: column")
        assertContains(document.yaml, "text: \"Hello\"")
        assertContains(document.yaml, "text: \"Go\"")
        assertContains(document.yaml, "service: remote_screens.panel_tap")
        assertContains(document.yaml, "node_id: \"go\"")
        assertContains(document.yaml, "screen_id: \"living\"")
        assertContains(document.payload, "\"k\":\"text\"")
        assertContains(document.payload, "\"screen\":\"living\"")
        assertContains(document.skipped, "tile cam stays on the tablet")
        assertTrue(document.yaml.contains("box is a column"))
    }

    @Test
    fun aRowFillsAndAWeightGrows() {
        val document = LvglExport.render(
            page = page(
                root = Node(
                    id = "root",
                    type = "row",
                    children = listOf(
                        Node(id = "wide", type = "text", size = NodeSize(width = "fill"), props = mapOf("text" to "A")),
                        Node(
                            id = "heavy",
                            type = "text",
                            size = NodeSize(width = "weight:2"),
                            props = mapOf("text" to "B"),
                        ),
                    ),
                ),
            ),
        )

        assertContains(document.yaml, "flex_flow: row")
        assertContains(document.yaml, "width: 100%")
        assertContains(document.yaml, "flex_grow: 2")
    }
}
