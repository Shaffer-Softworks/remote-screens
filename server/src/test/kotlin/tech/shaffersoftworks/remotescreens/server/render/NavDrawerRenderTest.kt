package tech.shaffersoftworks.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.NamedVariable
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.shared.Drawer
import tech.shaffersoftworks.remotescreens.shared.NavBar
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavDrawerRenderTest {
    private val ha = DemoHome()
    private val tablet = Device(1280, 800, 1.5f)
    private val pink = listOf(24, 16, 8, 0).map { (Themes.RPI[Pastel.PINK] ushr it).toByte() }

    private fun input() = RenderInput(
        ha.states.value,
        I18n("en", I18n.load("en"), emptyMap(), true),
        ha.config.value,
        RenderData(),
        Themes.RPI,
    )

    private fun ints(doc: RenderedDoc) = CoreDocument().also {
        it.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(doc.bytes)))
    }.getNamedVariables(NamedVariable.INT_TYPE).toSet()

    private fun isLit(doc: RenderedDoc) = doc.bytes.asList().windowed(pink.size).any { it == pink }

    @Test
    fun aScreenWithoutADrawerHasNoDrawerSwitch() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        assertFalse("USER:drawer" in ints(doc))
        assertFalse(isLit(doc), "the demo screen has no pink")
    }

    @Test
    fun aModalDrawerOpensFromEitherSideWithOrWithoutTheTopBar() {
        for (side in Drawer.SIDES) for (topBar in listOf(true, false)) {
            val screen = DemoLayout.screen().let {
                it.copy(
                    topBar = it.topBar.copy(show = topBar),
                    navBar = NavBar(show = false),
                    drawer = Drawer(style = "modal", side = side, active = "pink"),
                )
            }
            val doc = ScreenRenderer.render(screen, tablet, input())
            assertFalse("USER:drawer" in ints(doc), "the panel is native, not a remote StateLayout")
            assertFalse(isLit(doc), "the drawer selection is drawn by Material 3")
            val drawer = kotlin.test.assertNotNull(doc.navigationDrawer)
            assertEquals(side, drawer.side)
            assertEquals(screen.name, drawer.title)
            assertEquals(
                screen.pages.withIndex().filter { !it.value.hidden }.map { it.index },
                drawer.items.mapNotNull { it.page },
            )
            assertEquals("Settings", drawer.items.last().title)
            assertTrue(drawer.items.all { it.iconPath.isNotEmpty() })
        }
    }

    @Test
    fun nativeDrawerChangesAreSentEvenWhenTheRemoteBytesMatch() {
        val screen = DemoLayout.screen().copy(drawer = Drawer(style = "modal", active = "pink"))
        val pinkDrawer = ScreenRenderer.render(screen, tablet, input())
        val otherDrawer = ScreenRenderer.render(
            screen.copy(drawer = screen.drawer.copy(active = "teal")),
            tablet,
            input(),
        )
        assertTrue(pinkDrawer.bytes.contentEquals(otherDrawer.bytes))
        assertFalse(pinkDrawer.hasSameContent(otherDrawer))
        assertTrue(pinkDrawer.hasSameContent(ScreenRenderer.render(screen, tablet, input())))
    }

}
