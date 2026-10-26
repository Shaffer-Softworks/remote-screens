package tech.shaffersoftworks.remotescreens.server.player.action

import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmNotice
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.lights.LightDetailMode
import tech.shaffersoftworks.remotescreens.server.data.music.MusicUiState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AGENDA_FIELD_SEPARATOR
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.Vars
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.alarmDotVar
import tech.shaffersoftworks.remotescreens.server.render.theme.Rpi
import tech.shaffersoftworks.remotescreens.shared.ActionRef
import tech.shaffersoftworks.remotescreens.shared.CalendarDialogMode
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionRouterTest {
    private val tiltOnlyCoverFeatures =
        HaRenderFlags.COVER_OPEN_TILT_FLAG or HaRenderFlags.COVER_CLOSE_TILT_FLAG or
            HaRenderFlags.COVER_STOP_TILT_FLAG or HaRenderFlags.COVER_TILT_POSITION_FLAG
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
    fun aShadeCoverToggleStaysAToggle() {
        assertEquals(TapOutcome.Done, tap("r1|toggle|"))
        assertEquals("toggle", ha.calls.last().second)
    }

    @Test
    fun aTiltOnlyCoverToggleOpensTheSlats() {
        ha.set(
            "cover.living_room_curtains",
            "closed",
            mapOf("supported_features" to JsonPrimitive(tiltOnlyCoverFeatures)),
        )
        assertEquals(TapOutcome.Done, tap("r1|toggle|"))
        val (domain, service, _) = ha.calls.last()
        assertEquals("cover" to "open_cover_tilt", domain to service)
    }

    @Test
    fun aTiltOnlyCoverThatIsOpenClosesTheSlats() {
        ha.set(
            "cover.living_room_curtains",
            "open",
            mapOf("supported_features" to JsonPrimitive(tiltOnlyCoverFeatures)),
        )
        assertEquals(TapOutcome.Done, tap("r1|toggle|"))
        assertEquals("close_cover_tilt", ha.calls.last().second)
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

    private val alarmScreen = screen.copy(pages = screen.pages + DemoLayout.alarmPage())

    private fun alarmTap(meta: String, memory: TapMemory) = runBlocking {
        router.handle(checkNotNull(ActionRef.decode(meta)), alarmScreen, memory, 0)
    }

    private fun typePin(pin: String, memory: TapMemory) {
        for (digit in pin) alarmTap("a1|code_digit|$digit", memory)
    }

    @Test
    fun anAlarmPageAsksForAPinFirst() {
        val outcome = alarmTap("a1|alarm_arm|arm_away", TapMemory())
        assertEquals(TapOutcome.AlarmNotice("a1", "Set Pin First", "You Must Set A Pin First"), outcome)
        assertTrue(ha.calls.isEmpty())
    }

    @Test
    fun anAlarmPinMustBeFourDigits() {
        val memory = TapMemory()
        typePin("123", memory)
        val outcome = alarmTap("a1|alarm_arm|arm_home", memory)
        assertEquals(TapOutcome.AlarmNotice("a1", "Invalid Pin", "Pin Must Be 4 Digits"), outcome)
        assertTrue("alarm_control_panel.house" !in memory.codes, "the pin is cleared")
        assertTrue(ha.calls.isEmpty())
    }

    @Test
    fun alarmPageKeysLightThePinDots() {
        val memory = TapMemory()
        alarmTap("a1|code_digit|4", memory)
        val shown = assertIs<TapOutcome.Code>(alarmTap("a1|code_digit|2", memory))
        assertEquals(
            listOf(Vars.VISIBLE, Vars.VISIBLE, Vars.GONE, Vars.GONE).map { it.toString() },
            shown.extra.map { it.value },
        )
        assertEquals(alarmDotVar("alarm_control_panel.house", 0), shown.extra.first().name)
    }

    @Test
    fun anAlarmPageArmsTheChosenZoneWithThePin() {
        val memory = TapMemory()
        typePin("12", memory)
        assertEquals(TapOutcome.Rebuild, alarmTap("a1|alarm_zone|alarm_control_panel.basement", memory))
        assertEquals("alarm_control_panel.basement", renderData.alarmUi.state("a1").selectedZone)
        assertTrue("alarm_control_panel.house" !in memory.codes, "changing zone clears the pin")
        typePin("1234", memory)
        assertEquals(TapOutcome.Done, alarmTap("a1|alarm_arm|arm_home", memory))
        val (domain, service, data) = ha.calls.last()
        assertEquals("alarm_control_panel" to "alarm_arm_home", domain to service)
        assertEquals("alarm_control_panel.basement", (data["entity_id"] as JsonPrimitive).content)
        assertEquals("1234", (data["code"] as JsonPrimitive).content)
        val basement = ha.states.value.getValue("alarm_control_panel.basement")
        assertEquals("armed_home", basement.state)
        assertEquals("armed_home", basement.attr("arm_mode"))
    }

    @Test
    fun openSensorsStopArmingButNotDisarming() {
        val memory = TapMemory()
        alarmTap("a1|alarm_zone|alarm_control_panel.garage", memory)
        typePin("1234", memory)
        val refused = alarmTap("a1|alarm_arm|arm_away", memory)
        assertEquals(
            TapOutcome.AlarmNotice("a1", "Open Sensors!", "Check windows and doors are closed"),
            refused,
        )
        assertTrue(ha.calls.isEmpty())
        typePin("1234", memory)
        assertEquals(TapOutcome.Done, alarmTap("a1|alarm_arm|disarm", memory))
        assertEquals("alarm_disarm", ha.calls.last().second)
    }

    @Test
    fun anAlarmPageOnlyChoosesItsOwnZonesAndDismissesItsDialog() {
        val memory = TapMemory()
        assertEquals(TapOutcome.Ignored, alarmTap("a1|alarm_zone|lock.front_door", memory))
        renderData.alarmUi.showNotice("a1", AlarmNotice("Invalid Pin", "Pin Must Be 4 Digits"))
        assertEquals(TapOutcome.Rebuild, alarmTap("a1|alarm_dismiss|", memory))
        assertEquals(null, renderData.alarmUi.state("a1").notice)
    }

    private val lightsScreen = screen.copy(pages = screen.pages + DemoLayout.lightsPage())

    private fun lightsTap(meta: String) = runBlocking {
        router.handle(checkNotNull(ActionRef.decode(meta)), lightsScreen, TapMemory(), 0)
    }

    private val camerasScreen = screen.copy(pages = screen.pages + DemoLayout.camerasPage())

    private fun camerasTap(meta: String) = runBlocking {
        router.handle(checkNotNull(ActionRef.decode(meta)), camerasScreen, TapMemory(), 0)
    }

    @Test
    fun aCamerasPageChoosesOnlyACameraItLists() {
        assertEquals(null, renderData.camerasUi.state("cm1").selectedCamera)
        assertEquals(TapOutcome.Rebuild, camerasTap("cm1|cameras_select|camera.driveway"))
        assertEquals("camera.driveway", renderData.camerasUi.state("cm1").selectedCamera)
        assertEquals(TapOutcome.Ignored, camerasTap("cm1|cameras_select|camera.garage"))
        assertEquals(TapOutcome.Ignored, camerasTap("cm1|cameras_select|lock.front_door"))
        assertEquals("camera.driveway", renderData.camerasUi.state("cm1").selectedCamera)
        assertTrue(ha.calls.isEmpty())
    }

    @Test
    fun aLightsPageChoosesARoomAndOpensAndClosesALightsDialog() {
        assertEquals(TapOutcome.Rebuild, lightsTap("li1|lights_room|living_room"))
        assertEquals("living_room", renderData.lightsUi.state("li1").selectedAreaId)
        assertEquals(TapOutcome.Rebuild, lightsTap("li1|lights_detail|light.living_room"))
        assertEquals(TapOutcome.Rebuild, lightsTap("li1|lights_mode|wheel"))
        assertEquals(TapOutcome.Rebuild, lightsTap("li1|lights_effects|"))
        val open = renderData.lightsUi.state("li1")
        assertEquals("light.living_room", open.detailEntityId)
        assertEquals(LightDetailMode.COLOR_WHEEL, open.detailMode)
        assertTrue(open.isEffectMenuOpen)
        assertEquals(TapOutcome.Ignored, lightsTap("li1|lights_mode|sideways"))
        assertEquals(TapOutcome.Rebuild, lightsTap("li1|lights_dismiss|"))
        assertEquals(null, renderData.lightsUi.state("li1").detailEntityId)
        assertEquals(TapOutcome.Rebuild, lightsTap("li1|lights_room|kitchen"))
        assertEquals("kitchen", renderData.lightsUi.state("li1").selectedAreaId)
        assertTrue(ha.calls.isEmpty())
    }

    @Test
    fun aLightsSwatchAndEffectTurnTheOpenLightOn() {
        lightsTap("li1|lights_detail|light.living_room")
        lightsTap("li1|lights_palette|0")
        val (domain, service, data) = ha.calls.last()
        assertEquals("light" to "turn_on", domain to service)
        assertEquals("light.living_room", (data["entity_id"] as JsonPrimitive).content)
        val swatch = Rpi.LightDetailPalette[0]
        assertEquals(
            listOf((swatch shr 16) and 0xFF, (swatch shr 8) and 0xFF, swatch and 0xFF),
            data.getValue("rgb_color").jsonArray.map { it.jsonPrimitive.int },
        )
        assertEquals(0, renderData.lightsUi.state("li1").paletteIndex)
        lightsTap("li1|lights_effects|")
        lightsTap("li1|lights_effect|Rainbow")
        assertEquals("Rainbow", (ha.calls.last().third["effect"] as JsonPrimitive).content)
        assertTrue(!renderData.lightsUi.state("li1").isEffectMenuOpen, "picking an effect closes the menu")
    }

    @Test
    fun theLightDialogSendsColourTemperatureAndRgb() {
        tap("e:light.living_room|kelvin|3000")
        assertEquals(3000.0, (ha.calls.last().third["color_temp_kelvin"] as JsonPrimitive).doubleOrNull)
        tap("e:light.living_room|rgb|255,128,0")
        val (domain, service, data) = ha.calls.last()
        assertEquals("light" to "turn_on", domain to service)
        assertEquals(listOf(255, 128, 0), data.getValue("rgb_color").jsonArray.map { it.jsonPrimitive.int })
        tap("e:light.living_room|brightness|35")
        assertEquals(35.0, (ha.calls.last().third["brightness_pct"] as JsonPrimitive).doubleOrNull)
    }

    @Test
    fun aLightsPageCoverRowTiltsItsCover() {
        tap("e:cover.living_room_curtains|open_tilt|")
        assertEquals("cover" to "open_cover_tilt", ha.calls.last().first to ha.calls.last().second)
        tap("e:cover.living_room_curtains|close_tilt|")
        assertEquals("close_cover_tilt", ha.calls.last().second)
        tap("e:cover.living_room_curtains|tilt|42")
        assertEquals("set_cover_tilt_position", ha.calls.last().second)
        assertEquals(42.0, (ha.calls.last().third["tilt_position"] as JsonPrimitive).doubleOrNull)
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
