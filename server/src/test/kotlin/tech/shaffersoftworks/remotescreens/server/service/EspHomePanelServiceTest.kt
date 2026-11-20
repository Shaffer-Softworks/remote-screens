package tech.shaffersoftworks.remotescreens.server.service

import kotlinx.coroutines.runBlocking
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.player.action.ActionRouter
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.shared.Node
import tech.shaffersoftworks.remotescreens.shared.NodeBinding
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import io.ktor.server.plugins.BadRequestException

class EspHomePanelServiceTest {
    private val ha = DemoHome()
    private val words = I18n(
        language = "en",
        ownWords = I18n.load("en"),
        homeAssistantWords = emptyMap(),
        is24Hour = true,
    )

    private fun service(screens: List<Screen>) = EspHomePanelService(
        homeAssistant = ha,
        screens = { screens },
        paletteOf = { themeId -> Themes.resolve(id = themeId, custom = emptyList()) },
        actions = ActionRouter(homeAssistant = ha, i18n = { words }, renderData = RenderDataRepository(ha)),
        words = { words },
    )

    @Test
    fun aBoundLabelUsesTheEntityName() {
        val document = service(screens = emptyList()).export(
            request = EspHomeExportRequest(
                root = Node(
                    id = "root",
                    type = "column",
                    children = listOf(
                        Node(
                            id = "label",
                            type = "text",
                            bind = NodeBinding(entityId = "light.living_room", format = "name"),
                        ),
                    ),
                ),
                widthPx = 800,
                heightPx = 480,
                screenId = "s",
                pageId = "home",
            ),
        )

        assertContains(document.yaml, "Ceiling light")
        assertContains(document.payload, "\"text\":\"Ceiling light\"")
    }

    @Test
    fun saveAndSendNamesTheDeviceService() = runBlocking {
        val service = service(screens = emptyList()).send(device = "kitchen_panel", payload = "{\"v\":1}")

        assertEquals("esphome.kitchen_panel_set_layout", service)
        assertEquals("esphome", ha.calls.last().first)
        assertEquals("kitchen_panel_set_layout", ha.calls.last().second)
        assertEquals("{\"v\":1}", (ha.calls.last().third["layout"] as JsonPrimitive).content)
    }

    @Test
    fun aPanelTapTogglesTheBoundLight() = runBlocking {
        val root = Node(
            id = "root",
            type = "column",
            children = listOf(Node(id = "sw", type = "switch", bind = NodeBinding(entityId = "light.living_room"))),
        )

        val screen = Screen(
            id = "s",
            name = "Panel",
            pages = listOf(Page(id = "home", title = "Home", root = root)),
        )

        assertEquals("on", ha.states.value["light.living_room"]?.state)

        assertEquals(
            "ran",
            service(screens = listOf(screen)).tap(screenId = "s", pageId = "home", nodeId = "sw", hold = false),
        )

        assertEquals("off", ha.states.value["light.living_room"]?.state)
    }

    @Test
    fun aDeviceNameWithASpaceIsRefused() {
        assertFailsWith<BadRequestException> {
            runBlocking { service(screens = emptyList()).send(device = "Kitchen Panel", payload = "{}") }
        }
    }
}
