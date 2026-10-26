package tech.shaffersoftworks.remotescreens.server.render.panels.intercom

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.ScreenRenderer
import tech.shaffersoftworks.remotescreens.shared.IntercomStation
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntercomPanelTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)
    private val tablet = Device(1280, 800, 1.5f)
    private val screen = DemoLayout.screen().let { it.copy(pages = it.pages + DemoLayout.intercomPage()) }

    @Test
    fun thePageDrawsTheHeaderAndOneRowPerStation() {
        val view = IntercomView(
            supportsCalls = true,
            status = "Online",
            isBusy = false,
            connectedId = null,
            stations = listOf(
                IntercomStation("station-living", "Living Room", inCall = false),
                IntercomStation("station-kitchen", "Kitchen", inCall = true),
            ),
        )
        val text = render(view)

        for (word in listOf("INTERCOM", "Mute", "Hang Up", "Call", "Living Room", "Kitchen")) {
            assertContains(text, word)
        }
        assertContains(text, "ic1|intercom_call|station-living")
        assertFalse("ic1|intercom_call|station-kitchen" in text, "a station already in a call cannot be called")
        assertFalse("ic1|card|" in text)
        val vars = IntercomVars.values(view, i18n)
        assertEqualsStatus(vars, "ic.r0.status", "Online")
        assertEqualsStatus(vars, "ic.r1.status", "In Call")
    }

    @Test
    fun anOlderTabletIsToldToUpdateAndGetsNoCallButtons() {
        val text = render(IntercomView.from(null, supportsCalls = false, i18n))

        assertContains(text, "INTERCOM")
        assertContains(text, "Update the tablet app to use the intercom")
        assertFalse("intercom_call" in text)
        assertFalse("intercom_mute" in text)
        assertTrue(IntercomVars.values(IntercomView.from(null, supportsCalls = false, i18n), i18n).isEmpty())
    }

    private fun render(view: IntercomView): String {
        val input = RenderInput(ha.states.value, i18n, ha.config.value, RenderData(), intercom = view)
        return String(ScreenRenderer.render(screen, tablet, input).bytes, Charset.forName("ISO-8859-1"))
    }

    private fun assertEqualsStatus(
        vars: List<tech.shaffersoftworks.remotescreens.shared.VarValue>,
        name: String,
        expected: String,
    ) {
        kotlin.test.assertEquals(expected, vars.first { it.name == name }.value)
    }
}
