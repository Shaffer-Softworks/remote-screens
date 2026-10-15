package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionRouterTest {
    private val ha = DemoHome()
    private val router = ActionRouter(ha) { I18n("en", I18n.load("en"), emptyMap(), true) }
    private val screen = DemoLayout.screen()

    private fun tap(meta: String, memory: TapMemory = TapMemory(), now: Long = 0) =
        runBlocking { router.handle(ActionRef.decode(meta)!!, screen, memory, now) }

    @Test
    fun aToggleTileTogglesItsEntity() {
        assertEquals(TapOutcome.Done, tap("t2|toggle|"))
        assertEquals("on", ha.states.value["light.table_lamp"]?.state)
    }

    @Test
    fun brightnessIsAPercentage() {
        tap("t1|brightness|40")
        val (domain, service, data) = ha.calls.last()
        assertEquals("light" to "turn_on", domain to service)
        assertEquals(40.0, (data["brightness_pct"] as JsonPrimitive).doubleOrNull)
    }

    @Test
    fun aStepCountsFromTheCurrentTarget() {
        tap("t4|temperature|+0.5")
        assertEquals(21.5, (ha.calls.last().third["temperature"] as JsonPrimitive).doubleOrNull)
    }

    @Test
    fun aStepStaysWithinTheLimits() {
        val climate = Catalogue.of("climate")!!.actions.getValue("temperature")
        val e = ha.states.value.getValue("climate.living_room")
        assertEquals("30", router.resolveArg(climate, "+50", e))
    }

    @Test
    fun volumeIsAFraction() {
        tap("t5|volume|+10")
        assertEquals(0.45, (ha.calls.last().third["volume_level"] as JsonPrimitive).doubleOrNull!!, 0.001)
    }

    @Test
    fun unlockingAsksTwice() {
        val memory = TapMemory()
        assertIs<TapOutcome.Confirm>(tap("t7|toggle|", memory, now = 1000))
        assertTrue(ha.calls.isEmpty())
        assertEquals(TapOutcome.Done, tap("t7|toggle|", memory, now = 2000))
        assertEquals("unlock", ha.calls.last().second)
    }

    @Test
    fun aLateSecondTapAsksAgain() {
        val memory = TapMemory()
        tap("t7|toggle|", memory, now = 1000)
        assertIs<TapOutcome.Confirm>(tap("t7|toggle|", memory, now = 1000 + ActionRouter.CONFIRM_MS + 1))
    }

    @Test
    fun theKeypadCodeGoesWithTheAlarm() {
        val memory = TapMemory()
        tap("e:alarm_control_panel.house|code_digit|1", memory)
        tap("e:alarm_control_panel.house|code_digit|2", memory)
        val shown = tap("e:alarm_control_panel.house|code_digit|3", memory)
        assertEquals(TapOutcome.Code("t.e:alarm_control_panel.house.code", "•••"), shown)
        tap("e:alarm_control_panel.house|arm_away|", memory)
        assertEquals("123", (ha.calls.last().third["code"] as JsonPrimitive).content)
    }

    @Test
    fun aLongPressOpensTheCard() {
        assertEquals(TapOutcome.Card("light.living_room", "light"), tap("t1|card|"))
    }

    @Test
    fun anActionTileCallsItsService() {
        tap("m8|run|")
        assertEquals("script" to "turn_on", ha.calls.last().first to ha.calls.last().second)
    }

    @Test
    fun aFavouritePlaysOnItsPlayer() {
        tap("m10|favourite|")
        val (_, service, data) = ha.calls.last()
        assertEquals("play_media", service)
        assertEquals("playlist:1", (data["media_content_id"] as JsonPrimitive).content)
    }

    @Test
    fun aToggleIsForeseen() {
        val lamp = ha.states.value.getValue("light.table_lamp")
        val ref = ActionRef("t2", "toggle")
        assertEquals("on", router.foresee(ref, router.target(ref, screen), lamp)?.state)
    }

    @Test
    fun anUnknownTileIsIgnored() {
        assertEquals(TapOutcome.Ignored, tap("nope|toggle|"))
    }
}
