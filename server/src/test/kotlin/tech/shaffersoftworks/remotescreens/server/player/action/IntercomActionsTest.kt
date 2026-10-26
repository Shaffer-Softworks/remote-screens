package tech.shaffersoftworks.remotescreens.server.player.action

import kotlinx.coroutines.runBlocking
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntercomActionsTest {
    private val ha = DemoHome()
    private val router = ActionRouter(ha, { I18n("en", I18n.load("en"), emptyMap(), true) }, RenderDataRepository(ha))
    private val screen = DemoLayout.screen().let { it.copy(pages = it.pages + DemoLayout.intercomPage()) }

    @Test
    fun callMuteAndHangUpStayOnTheTablet() {
        assertEquals(TapOutcome.Intercom("call", "station-kitchen"), tap("ic1|intercom_call|station-kitchen"))
        assertEquals(TapOutcome.Intercom("mute", ""), tap("ic1|intercom_mute|"))
        assertEquals(TapOutcome.Intercom("hangup", ""), tap("ic1|intercom_hangup|"))
        assertEquals(TapOutcome.Ignored, tap("ic1|intercom_call|"))
        assertTrue(ha.calls.isEmpty())
    }

    private fun tap(metadata: String): TapOutcome = runBlocking {
        router.handle(checkNotNull(ActionRef.decode(metadata)), screen, TapMemory(), 0)
    }
}
