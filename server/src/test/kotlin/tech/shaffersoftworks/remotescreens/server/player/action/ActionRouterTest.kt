package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AGENDA_FIELD_SEPARATOR
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.CalendarDialogMode
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionRouterTest {
    private val ha = DemoHome()
    private val renderData = RenderDataRepository(ha)
    private val router = ActionRouter(ha, { I18n("en", I18n.load("en"), emptyMap(), true) }, renderData)
    private val screen = DemoLayout.screen()

    private fun tap(meta: String, memory: TapMemory = TapMemory(), now: Long = 0) =
        runBlocking {
            val action = checkNotNull(ActionRef.decode(meta))
            router.handle(action, screen, memory, now)
        }

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
        // Living room is heat_cool — a single temperature step becomes a range shift.
        tap("t4|temperature|+0.5")
        val data = ha.calls.last().third
        assertEquals(18.5, (data["target_temp_low"] as JsonPrimitive).doubleOrNull)
        assertEquals(24.5, (data["target_temp_high"] as JsonPrimitive).doubleOrNull)
        assertTrue("temperature" !in data)
    }

    @Test
    fun aHeatCoolLowStepSendsBothRangeTargets() {
        tap("c1|temperature_low|+0.5")
        val data = ha.calls.last().third
        assertEquals("set_temperature", ha.calls.last().second)
        assertEquals(18.5, (data["target_temp_low"] as JsonPrimitive).doubleOrNull)
        assertEquals(24.0, (data["target_temp_high"] as JsonPrimitive).doubleOrNull)
    }

    @Test
    fun aHeatCoolHighStepSendsBothRangeTargets() {
        tap("c1|temperature_high|-0.5")
        val data = ha.calls.last().third
        assertEquals(18.0, (data["target_temp_low"] as JsonPrimitive).doubleOrNull)
        assertEquals(23.5, (data["target_temp_high"] as JsonPrimitive).doubleOrNull)
    }

    @Test
    fun aHeatCoolLowStepThatCrossesHighRaisesHigh() {
        tap("c1|temperature_low|+10")
        val data = ha.calls.last().third
        assertEquals(28.0, (data["target_temp_low"] as JsonPrimitive).doubleOrNull)
        assertEquals(28.0, (data["target_temp_high"] as JsonPrimitive).doubleOrNull)
    }

    @Test
    fun aStepStaysWithinTheLimits() {
        val climate = checkNotNull(Catalogue.of("climate")).actions.getValue("temperature")
        val entityState = ha.states.value.getValue("climate.living_room")
        assertEquals("30", router.resolveArg(climate, "+50", entityState))
    }

    @Test
    fun volumeIsAFraction() {
        tap("t5|volume|+10")
        assertEquals(
            0.45,
            checkNotNull((ha.calls.last().third["volume_level"] as JsonPrimitive).doubleOrNull),
            0.001,
        )
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
        assertIs<TapOutcome.Confirm>(
            tap("t7|toggle|", memory, now = 1000 + ActionRouter.CONFIRM_MS + 1),
        )
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

    @Test
    fun musicBrowseTypeRebuilds() {
        assertEquals(TapOutcome.Rebuild, tap("ma1|browse_type|album"))
        assertEquals("album", renderData.musicUi.state("ma1").browseMediaType)
    }

    @Test
    fun musicListenModeRebuilds() {
        assertEquals(TapOutcome.Rebuild, tap("ma1|listen_mode|search"))
        assertEquals(MusicUiState.LISTEN_SEARCH, renderData.musicUi.state("ma1").listenMode)
    }

    @Test
    fun musicPrimaryTabRebuilds() {
        assertEquals(TapOutcome.Rebuild, tap("ma1|primary_tab|listen"))
        assertEquals(MusicUiState.PRIMARY_LISTEN, renderData.musicUi.state("ma1").primarySection)
    }

    @Test
    fun musicPlayUriCallsMusicAssistant() {
        assertEquals(TapOutcome.Rebuild, tap("ma1|play_uri|demo://track/1"))
        val (domain, service, data) = ha.calls.last()
        assertEquals("music_assistant" to "play_media", domain to service)
        assertEquals("demo://track/1", (data["media_id"] as JsonPrimitive).content)
        assertEquals("play", (data["enqueue"] as JsonPrimitive).content)
    }

    @Test
    fun musicClearQueueCallsMediaPlayer() {
        assertEquals(TapOutcome.Rebuild, tap("ma1|clear_queue|"))
        val (domain, service, _) = ha.calls.last()
        assertEquals("media_player" to "clear_playlist", domain to service)
    }

    @Test
    fun aCalendarPageOpensCreateAndDeleteDialogs() = runBlocking {
        val calendarScreen = Screen(
            id = "s",
            name = "S",
            pages = listOf(
                Page(
                    "home",
                    "Home",
                    listOf(Tile(id = "cal", entityId = "calendar.family", full = true)),
                ),
            ),
        )
        renderData.forScreen(calendarScreen)
        val create = router.handle(ActionRef("cal", "calendar_create"), calendarScreen, TapMemory())
        val opened = assertIs<TapOutcome.OpenCalendar>(create)
        assertEquals(CalendarDialogMode.CREATE, opened.mode)
        assertTrue(opened.calendars.any { it.entityId == "calendar.family" && it.canCreate })

        val arg = listOf("calendar.family", "demo-dentist", "").joinToString(AGENDA_FIELD_SEPARATOR)
        val delete = router.handle(ActionRef("cal", "calendar_delete", arg), calendarScreen, TapMemory())
        val confirm = assertIs<TapOutcome.OpenCalendar>(delete)
        assertEquals(CalendarDialogMode.DELETE, confirm.mode)
        assertEquals("Dentist appointment", confirm.event?.summary)
    }
}
