package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.render.cards.CardRenderer

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.NamedVariable
import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmNotice
import tech.shaffersoftworks.remotescreens.server.data.alarm.AlarmUiState
import tech.shaffersoftworks.remotescreens.server.data.cameras.CamerasUiState
import tech.shaffersoftworks.remotescreens.server.data.lights.LightDetailMode
import tech.shaffersoftworks.remotescreens.server.data.lights.LightsUiState
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.render.HaRenderFlags
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.ALARM_PIN_LENGTH
import tech.shaffersoftworks.remotescreens.server.render.panels.alarm.alarmDotVar
import tech.shaffersoftworks.remotescreens.server.render.theme.Palette
import tech.shaffersoftworks.remotescreens.server.render.theme.Pastel
import tech.shaffersoftworks.remotescreens.server.render.theme.Role
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.shared.Catalogue
import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Reflow
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.VarType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RenderTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)
    private val tablet = Device(1280, 800, 1.5f)

    private fun input(data: RenderData = RenderData(), palette: Palette = Themes.RPI) = RenderInput(
        ha.states.value,
        i18n,
        ha.config.value,
        data,
        palette,
    )
    private val Role.argb: Int get() = Themes.RPI[this]
    private val Pastel.argb: Int get() = Themes.RPI[this]

    private fun parse(bytes: ByteArray) = CoreDocument().also { it.initFromBuffer(
        RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)),
    ) }

    @Test
    fun everyPageOfTheDemoScreenIsOneDocument() {
        val screen = DemoLayout.screen()
        val data = runBlocking { RenderDataRepository(ha).forScreen(screen) }
        val doc = ScreenRenderer.render(screen, tablet, input(data))
        val core = parse(doc.bytes)
        assertContains(core.getNamedVariables(NamedVariable.INT_TYPE).toList(), "USER:page")
        assertEquals(screen.pages.map { it.id }, doc.pageIds)

        val vars = doc.vars(ha.states.value)
        assertEquals("Ceiling light", vars["e.light.living_room.name"]?.value)
        assertEquals(VarType.COLOR, vars["t.t1.line"]?.type)
        val declared = core.getNamedVariables(NamedVariable.STRING_TYPE).toSet() +
            core.getNamedVariables(
                NamedVariable.INT_TYPE,
            ) + core.getNamedVariables(
                NamedVariable.FLOAT_TYPE,
            ) + core.getNamedVariables(NamedVariable.COLOR_TYPE)
        val unread = vars.keys.filter { "USER:$it" !in declared && !it.startsWith("sys.") }
        assertTrue(unread.isEmpty(), "values sent but never declared: $unread")
    }

    @Test
    fun aCompressedDocumentIsSmallerAndReadsTheSame() {
        val screen = DemoLayout.screen()
        val doc = ScreenRenderer.render(
            screen,
            tablet,
            input(runBlocking { RenderDataRepository(ha).forScreen(screen) }),
        )
        val small = Rc.compress(doc.bytes)
        assertTrue(small.size * 2 < doc.bytes.size, "${small.size} of ${doc.bytes.size} bytes")
        val names = { b: ByteArray -> parse(
            b,
        ).getNamedVariables(NamedVariable.STRING_TYPE).toSet() }
        assertEquals(names(doc.bytes), names(small))
    }

    @Test
    fun everyDemoTileHasItsOwnCell() {
        val screen = DemoLayout.screen()
        for (page in screen.pages) {
            val placed = Reflow.place(page.tiles, screen.grid)
            assertEquals(
                page.tiles.map { it.id }.sorted(),
                placed.map { it.tile.id }.sorted(),
                "tiles dropped from ${page.id}",
            )
            for (placement in placed) assertEquals(
                placement.tile.col to placement.tile.row,
                placement.col to placement.row,
                "${placement.tile.id} moved on ${page.id}",
            )
        }
    }

    @Test
    fun aToggleIsForeseen() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val meta = "t2|toggle|"
        assertTrue(meta in doc.docContext.predicted, "the table lamp's toggle can be predicted")
    }

    @Test
    fun aTileLightsUpWhenItsEntityIsOn() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val states = ha.states.value
        val off = doc.vars(states)
        val on = doc.vars(
            states + ("light.table_lamp" to states.getValue("light.table_lamp").with("on")),
        )
        // Mockup domain tiles keep a soft teal header line while off; accent when on.
        assertEquals(Role.ACCENT_SOFT.argb, off["t.t2.line"]?.asColor())
        assertEquals(Pastel.YELLOW.argb, on["t.t2.line"]?.asColor())
        assertEquals(Pastel.YELLOW.argb, on["t.t2.icon"]?.asColor())
    }

    @Test
    fun aThemeColoursTheDocumentAndItsLiveColours() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input(palette = Themes.LIGHT))
        val states = ha.states.value
        val off = doc.vars(states)
        val on = doc.vars(
            states + ("light.table_lamp" to states.getValue("light.table_lamp").with("on")),
        )
        assertEquals(Themes.LIGHT[Role.ACCENT_SOFT], off["t.t2.line"]?.asColor())
        assertEquals(Themes.LIGHT[Pastel.YELLOW], on["t.t2.line"]?.asColor())
        val page = Themes.LIGHT[Role.PAGE]
        val bytes = byteArrayOf(
            (page ushr 24).toByte(),
            (page ushr 16).toByte(),
            (page ushr 8).toByte(),
            page.toByte(),
        )
        assertTrue(
            doc.bytes.asList().windowed(4).any { it == bytes.asList() },
            "the light page colour is in the document",
        )
        val dark = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        assertFalse(
            dark.bytes.asList().windowed(4).any { it == bytes.asList() },
            "the RPI document has no light page",
        )
    }

    @Test
    fun theHomePanelShowsTheNextEventAndWhoIsHome() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val vars = doc.vars(ha.states.value)
        assertEquals("Dentist appointment", vars["t.h1.ev.title"]?.value)
        assertEquals("Family · Main St", vars["t.h1.ev.meta"]?.value)
        assertEquals("HOME", vars["t.h1.where.person.sam"]?.value)
        assertEquals("AWAY", vars["t.h1.where.person.alex"]?.value)
        assertEquals("14°", vars["t.h1.temp"]?.value)
    }

    @Test
    fun aFullWeatherTileIsTheRpiWeatherPage() {
        val screen = DemoLayout.screen()
        val data = runBlocking { RenderDataRepository(ha).forScreen(screen) }
        val doc = ScreenRenderer.render(screen, tablet, input(data))
        val vars = doc.vars(ha.states.value)
        assertEquals("Wind 12 km/h", vars["t.w1.wind_speed"]?.value)
        assertEquals("Humidity 71%", vars["t.w1.humidity"]?.value)
        assertEquals("Pressure 1,013 hPa", vars["t.w1.pressure"]?.value)
        assertEquals("14°", vars["e.weather.home.value"]?.value)
    }

    @Test
    fun aFullClimateTileIsTheRpiThermostatPage() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val states = ha.states.value
        val vars = doc.vars(states)
        assertEquals("21.0°", vars["t.c1.current"]?.value)
        assertEquals("45%", vars["t.c1.humidity"]?.value)
        assertEquals("Cooling", vars["t.c1.action"]?.value)
        assertEquals(Role.BLUE.argb, vars["t.c1.dial"]?.asColor())
        assertEquals("18°", vars["t.c1.low"]?.value)
        assertEquals("24°", vars["t.c1.high"]?.value)
        assertEquals(Role.ACCENT_DARK.argb, vars["o.c1.seg.0.bg"]?.asColor(), "Heat/Cool is lit")
        assertTrue("c1|temperature_low|+0.5" in doc.docContext.predicted)
        assertTrue("c1|temperature_high|-0.5" in doc.docContext.predicted)
        assertTrue("c1|fan|on" in doc.docContext.predicted)
        assertEquals("Auto", vars["t.c1.fan"]?.value)
    }

    @Test
    fun aFullAlarmTileIsTheRpiAlarmPage() {
        val screen = DemoLayout.screen().let { it.copy(pages = it.pages + DemoLayout.alarmPage()) }
        val doc = ScreenRenderer.render(screen, tablet, input())
        val text = String(doc.bytes, Charsets.ISO_8859_1)
        for (word in listOf("Disarmed", "System not secure", "Select Zone", "All Zones", "Garage", "Enter code")) {
            assertContains(text, word)
        }
        for (meta in listOf(
            "a1|alarm_zone|alarm_control_panel.garage",
            "a1|alarm_arm|arm_night",
            "a1|alarm_arm|disarm",
            "a1|code_digit|7",
            "a1|code_back|",
        )) {
            assertContains(text, meta)
        }
        assertFalse("a1|alarm_dismiss|" in text, "no dialog until an arm is refused")
        assertFalse("a1|card|" in text, "the page itself does not open the alarm card")
        val vars = doc.vars(ha.states.value)
        for (index in 0 until ALARM_PIN_LENGTH) {
            assertEquals(Vars.GONE.toString(), vars[alarmDotVar("alarm_control_panel.house", index)]?.value)
        }
        assertTrue(doc.docContext.structure.any { it.first == "alarm_control_panel.house" })

        val core = parse(doc.bytes)
        val declared = listOf(
            NamedVariable.STRING_TYPE,
            NamedVariable.INT_TYPE,
            NamedVariable.FLOAT_TYPE,
            NamedVariable.COLOR_TYPE,
        ).flatMap { core.getNamedVariables(it).toList() }.toSet()
        val unread = vars.keys.filter { "USER:$it" !in declared && !it.startsWith("sys.") }
        assertTrue(unread.isEmpty(), "values sent but never declared: $unread")
    }

    private val lightsScreen = DemoLayout.screen().let { it.copy(pages = it.pages + DemoLayout.lightsPage()) }

    private fun renderLights(ui: LightsUiState? = null): Pair<RenderedDoc, String> {
        val data = runBlocking { RenderDataRepository(ha).forScreen(lightsScreen) }
        val withUi = if (ui == null) data else data.copy(lightsUi = mapOf("li1" to ui))
        val doc = ScreenRenderer.render(lightsScreen, tablet, input(withUi))
        return doc to String(doc.bytes, Charsets.ISO_8859_1)
    }

    /** The cameras page sits second, with pages after it, so a video's page is not just the last one drawn. */
    private val camerasScreen = DemoLayout.screen().let {
        it.copy(pages = it.pages.take(1) + DemoLayout.camerasPage() + it.pages.drop(1))
    }

    private fun renderCameras(ui: CamerasUiState? = null): Pair<RenderedDoc, String> {
        val data = runBlocking { RenderDataRepository(ha).forScreen(camerasScreen) }
        val withUi = if (ui == null) data else data.copy(camerasUi = mapOf("cm1" to ui))
        val doc = ScreenRenderer.render(camerasScreen, tablet, input(withUi))
        return doc to String(doc.bytes, Charsets.ISO_8859_1)
    }

    private fun assertEveryValueIsDeclared(doc: RenderedDoc) {
        val core = parse(doc.bytes)
        val declared = listOf(
            NamedVariable.STRING_TYPE,
            NamedVariable.INT_TYPE,
            NamedVariable.FLOAT_TYPE,
            NamedVariable.COLOR_TYPE,
        ).flatMap { core.getNamedVariables(it).toList() }.toSet()
        val unread = doc.vars(ha.states.value).keys.filter { "USER:$it" !in declared && !it.startsWith("sys.") }
        assertTrue(unread.isEmpty(), "values sent but never declared: $unread")
    }

    @Test
    fun aFullCamerasTileIsTheRpiCamerasPage() {
        val (doc, text) = renderCameras()
        for (word in listOf("CAMERAS", "Front Door", "Driveway camera")) assertContains(text, word)
        assertContains(text, "cm1|cameras_select|camera.front_door")
        assertContains(text, "cm1|cameras_select|camera.driveway")
        assertFalse("cm1|card|" in text, "the page itself opens no card")
        assertTrue(doc.docContext.videos.isEmpty(), "nothing plays until a camera is chosen")
        assertTrue(doc.docContext.pictures.any { it.name == "cam.list.camera.front_door" })
        assertEveryValueIsDeclared(doc)
    }

    @Test
    fun aChosenCameraPlaysItsLiveVideoOverASpinner() {
        val (doc, text) = renderCameras(CamerasUiState(selectedCamera = "camera.driveway"))
        val video = doc.docContext.videos.single()
        assertEquals("camera.driveway", video.entityId)
        assertFalse(video.crop, "the stream is fitted like the camera card's")
        assertEquals(1, video.page, "the video belongs to the cameras page")
        assertTrue(doc.docContext.declares("e.camera.driveway.cam_waiting"))
        assertTrue(doc.docContext.declares("e.camera.driveway.cam_still"))
        assertTrue(doc.docContext.pictures.any { it.name == "cam.card.camera.driveway" })
        assertContains(text, "Driveway camera")
        assertEveryValueIsDeclared(doc)
    }

    @Test
    fun aFullLightsTileIsTheRpiLightsPage() {
        val (doc, text) = renderLights()
        for (word in listOf("LIGHTING CONTROL", "Select a room to see its lights", "Bedroom", "Hallway", "Kitchen")) {
            assertContains(text, word)
        }
        assertContains(text, "li1|lights_room|hallway")
        assertFalse("li1|card|" in text, "the page itself opens no card")
        assertFalse("li1|lights_dismiss|" in text, "no dialog until a light is opened")
        assertEveryValueIsDeclared(doc)

        val states = ha.states.value
        val vars = doc.vars(states)
        assertEquals("1 Light is on", vars["t.li1.lightsOn.living_room"]?.value)
        assertEquals("All lights are off", vars["t.li1.lightsOn.hallway"]?.value, "a switch is not a light")
        val tableLamp = states.getValue("light.table_lamp")
        val bothOn = doc.vars(states + (tableLamp.entityId to tableLamp.with("on")))
        assertEquals("2 Lights are on", bothOn["t.li1.lightsOn.living_room"]?.value)
        assertTrue("light.table_lamp" in doc.entities, "the count follows the room's lights")
    }

    @Test
    fun aChosenLightsRoomListsItsLightsSwitchesAndCovers() {
        val (living, livingText) = renderLights(LightsUiState(selectedAreaId = "living_room"))
        for (meta in listOf(
            "li1|lights_detail|light.living_room",
            "e:light.living_room|brightness|50",
            "e:light.table_lamp|off|",
            "e:cover.living_room_curtains|open_tilt|",
            "e:cover.living_room_curtains|close_tilt|",
        )) {
            assertContains(livingText, meta)
        }
        val vars = living.vars(ha.states.value)
        assertTrue("e.light.table_lamp.lightsOn" in vars)
        assertFalse("e.media_player.living_room.lightsOn" in vars, "only lights, switches and covers are listed")
        assertEquals(0.5f, vars["e.cover.living_room_curtains.lightsLevel"]?.value?.toFloat())
        assertEveryValueIsDeclared(living)

        val (_, hallwayText) = renderLights(LightsUiState(selectedAreaId = "hallway"))
        assertContains(hallwayText, "Hallway light", message = "a switch takes its device's area")
        assertContains(hallwayText, "e:switch.hallway_light|off|")
    }

    @Test
    fun theLightDialogLoadsInEveryMode() {
        for (mode in LightDetailMode.entries) {
            val ui = LightsUiState(
                selectedAreaId = "living_room",
                detailEntityId = "light.living_room",
                detailMode = mode,
                paletteIndex = 2,
                isEffectMenuOpen = true,
            )
            val (doc, text) = renderLights(ui)
            assertContains(text, "li1|lights_dismiss|")
            assertContains(text, "li1|lights_mode|wheel")
            assertContains(text, "li1|lights_palette|7")
            assertContains(text, "e:light.living_room|toggle|")
            when (mode) {
                LightDetailMode.BRIGHTNESS -> assertContains(text, "e:light.living_room|brightness|100")
                LightDetailMode.COLOR_TEMPERATURE -> assertContains(text, "e:light.living_room|kelvin|")
                LightDetailMode.COLOR_WHEEL -> assertContains(text, "e:light.living_room|rgb|")
                LightDetailMode.POWER -> Unit
            }
            assertTrue(doc.docContext.structure.any { it.first == "light.living_room" })
            assertEveryValueIsDeclared(doc)
        }
    }

    @Test
    fun anAlarmPageShowsTheChosenZoneAndRpisDialog() {
        val screen = DemoLayout.screen().let { it.copy(pages = it.pages + DemoLayout.alarmPage()) }
        val ui = AlarmUiState(
            selectedZone = "alarm_control_panel.garage",
            notice = AlarmNotice("Invalid Pin", "Pin Must Be 4 Digits"),
        )
        val doc = ScreenRenderer.render(screen, tablet, input(RenderData(alarmUi = mapOf("a1" to ui))))
        val text = String(doc.bytes, Charsets.ISO_8859_1)
        assertContains(text, "a1|alarm_dismiss|")
        assertContains(text, "Pin Must Be 4 Digits")
        parse(doc.bytes)
        assertTrue(doc.docContext.structure.any { it.first == "alarm_control_panel.garage" })
    }

    @Test
    fun anArmedAlarmPageIsSecureAndEveryStateLoads() {
        val screen = DemoLayout.screen().let { it.copy(pages = it.pages + DemoLayout.alarmPage()) }
        val states = ha.states.value
        val house = states.getValue("alarm_control_panel.house")
        fun renderAs(state: String): String {
            val input = RenderInput(
                states + (house.entityId to house.with(state)),
                i18n,
                ha.config.value,
                RenderData(),
                Themes.RPI,
            )
            return String(ScreenRenderer.render(screen, tablet, input).bytes, Charsets.ISO_8859_1)
        }
        val armedText = renderAs("armed_away")
        assertContains(armedText, "Armed Away")
        assertContains(armedText, "System secure")
        assertContains(renderAs("arming"), "Arming")
        for (state in listOf("arming", "armed_away", "disarmed", "triggered")) {
            val input = RenderInput(
                states + (house.entityId to house.with(state)),
                i18n,
                ha.config.value,
                RenderData(),
                Themes.RPI,
            )
            parse(ScreenRenderer.render(screen, tablet, input).bytes)
        }
    }

    @Test
    fun aFullMusicTileIsTheMusicAssistantPage() {
        val screen = DemoLayout.screen()
        val data = runBlocking { RenderDataRepository(ha).forScreen(screen) }
        val doc = ScreenRenderer.render(screen, tablet, input(data))
        val vars = doc.vars(ha.states.value)
        assertTrue(vars.containsKey("e.media_player.living_room.title"))
        assertTrue(
            "ma1|play_pause|" in doc.docContext.predicted ||
                doc.docContext.predicted.keys.any { it.startsWith("ma1|play_pause") },
        )
        assertTrue(data.musicBrowse["ma1"].orEmpty().isNotEmpty())
        assertTrue(data.musicQueue["ma1"]?.rows.orEmpty().isNotEmpty())
        assertEquals("demo_music_assistant", data.musicConfigEntry["ma1"])
    }

    @Test
    fun heatModeUsesASingleSetpointNotDualControls() {
        val states = ha.states.value
        val heatStates = states + (
            "climate.living_room" to states.getValue("climate.living_room").with("heat")
            )
        val doc = ScreenRenderer.render(
            DemoLayout.screen(),
            tablet,
            RenderInput(heatStates, i18n, ha.config.value, RenderData(), Themes.RPI),
        )
        assertTrue("c1|temperature|+0.5" in doc.docContext.predicted)
        assertTrue("c1|temperature|-0.5" in doc.docContext.predicted)
        assertFalse(
            doc.docContext.predicted.keys.any { it.startsWith("c1|temperature_low|") },
            "Heat must not keep dual low/high keys",
        )
        assertFalse(doc.docContext.predicted.keys.any { it.startsWith("c1|temperature_high|") })
        val vars = doc.vars(heatStates)
        assertEquals("21°", vars["t.c1.target"]?.value)
        assertEquals(Role.ACCENT_DARK.argb, vars["o.c1.seg.1.bg"]?.asColor(), "Heat is lit")
        assertTrue(
            doc.structureChanged(states),
            "switching Heat/Cool ↔ Heat must rebuild the dial and keys",
        )
    }

    @Test
    fun aDocumentIsStaleOnlyWhenWhatIsDrawnChanges() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val states = ha.states.value
        assertFalse(doc.stale(states))
        assertFalse(doc.structureChanged(states))
        val lamp = states.getValue("light.table_lamp")
        assertFalse(
            doc.stale(states + ("light.table_lamp" to lamp.with("on"))),
            "a state change is a value, not a rebuild",
        )
    }

    @Test
    fun aRebuildKeepsThePageTheTabletIsOn() {
        val screen = DemoLayout.screen()
        val onHome = ScreenRenderer.render(screen, tablet, input(), initialPage = 0)
        val onLiving = ScreenRenderer.render(screen, tablet, input(), initialPage = 1)
        assertEquals(0, onHome.homePage)
        assertNotEquals(
            onHome.bytes.toList(),
            onLiving.bytes.toList(),
            "initial page is baked into the document so a rebuild does not flash home",
        )
    }

    @Test
    fun aTiltOnlyBlindCardSlidesItsPosition() {
        val shade = cardDocument("cover.living_room_curtains")
        val shadeText = String(shade.bytes, Charsets.ISO_8859_1)
        assertContains(shadeText, "e:cover.living_room_curtains|position|")
        assertFalse("e:cover.living_room_curtains|tilt|" in shadeText)

        ha.set(
            "cover.living_room_curtains",
            "closed",
            mapOf(
                "supported_features" to JsonPrimitive(
                    HaRenderFlags.COVER_OPEN_TILT_FLAG or HaRenderFlags.COVER_CLOSE_TILT_FLAG or
                        HaRenderFlags.COVER_STOP_TILT_FLAG or HaRenderFlags.COVER_TILT_POSITION_FLAG,
                ),
                "current_position" to JsonNull,
                "current_tilt_position" to JsonPrimitive(91),
            ),
        )
        val tilt = cardDocument("cover.living_room_curtains")
        val tiltText = String(tilt.bytes, Charsets.ISO_8859_1)
        assertContains(tiltText, "Position")
        assertContains(tiltText, "e:cover.living_room_curtains|tilt|")
        assertFalse("e:cover.living_room_curtains|position|" in tiltText)
        assertTrue(tilt.docContext.predicted.keys.any { it.startsWith("e:cover.living_room_curtains|tilt|") })
        val shown = tilt.vars(ha.states.value)["e.cover.living_room_curtains.tilt"]?.value?.toFloat()
        assertEquals(0.91f, assertNotNull(shown), 0.001f)
    }

    private fun cardDocument(entityId: String): RenderedDoc {
        val data = runBlocking { RenderDataRepository(ha).forCard(entityId, null) }
        return CardRenderer.render(entityId, null, tablet, input(data))
    }

    @Test
    fun everyCardBuilds() {
        val ids = ha.states.value.keys.filter { Catalogue.of(
            it.substringBefore('.'),
        )?.card?.let { c -> c != "none" } == true }
        for (id in ids) {
            val data = runBlocking { RenderDataRepository(ha).forCard(id, null) }
            val doc = CardRenderer.render(id, null, tablet, input(data))
            assertTrue(
                parse(doc.bytes).getNamedVariables(NamedVariable.STRING_TYPE).isNotEmpty(),
                id,
            )
        }
        val energy = runBlocking { RenderDataRepository(ha).forCard(null, "energy") }
        CardRenderer.render("card.energy", "energy", tablet, input(energy))
    }

    @Test
    fun aPortraitPhoneGetsTheSameScreen() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), Device(400, 860, 2.75f), input())
        assertContains(
            parse(doc.bytes).getNamedVariables(NamedVariable.STRING_TYPE).toList(),
            "USER:e.light.living_room.name",
        )
    }

    @Test
    fun barsTakeTheirOwnSizeLabelsLayoutAndColours() {
        val pink = Pastel.PINK.argb
        val bytes = listOf(24, 16, 8, 0).map { (pink ushr it).toByte() }
        val plain = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        assertFalse(
            plain.bytes.asList().windowed(4).any { it == bytes },
            "the demo screen has no pink",
        )
        for (labels in NavBar.LABELS) for (layout in NavBar.LAYOUTS) {
            val screen = DemoLayout.screen().let {
                it.copy(
                    topBar = it.topBar.copy(
                        height = 72,
                        clockWidth = 30,
                        date = false,
                        background = "pink",
                    ),
                    navBar = NavBar(height = 56, labels = labels, layout = layout, active = "pink"),
                )
            }
            val doc = ScreenRenderer.render(screen, Device(400, 860, 2.75f), input())
            assertTrue(
                doc.bytes.asList().windowed(4).any { it == bytes },
                "$labels/$layout: the pink header is drawn",
            )
        }
    }

    @Test
    fun theTopBarDeclaresWifiAndBatteryWhenEnabled() {
        val off = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val offNames = parse(off.bytes).getNamedVariables(NamedVariable.STRING_TYPE).toSet()
        assertFalse("USER:sys.wifi" in offNames)
        assertFalse("USER:sys.battery" in offNames)

        val on = ScreenRenderer.render(
            DemoLayout.screen().let { it.copy(
                topBar = it.topBar.copy(wifi = true, battery = true),
            ) },
            tablet,
            input(),
        )
        val strings = parse(on.bytes).getNamedVariables(NamedVariable.STRING_TYPE).toSet()
        val ints = parse(on.bytes).getNamedVariables(NamedVariable.INT_TYPE).toSet()
        assertContains(strings, "USER:sys.wifi")
        assertContains(strings, "USER:sys.battery")
        assertContains(ints, "USER:sys.battery_charging")
        assertContains(ints, "USER:sys.battery_idle")
    }

    @Test
    fun anAlertBuilds() {
        val doc = AlertRenderer.render(
            Alert(
                "Doorbell",
                "Someone is at the door",
                camera = "camera.front_door",
                buttons = listOf(AlertButton("Open")),
            ),
            tablet,
            input(),
        )
        assertEquals(1, doc.docContext.pictures.size)
    }

    @Test
    fun theDoorbellsVideoPlaysOverItsImageCroppedLikeIt() {
        val doc = AlertRenderer.render(
            Alert("Doorbell", camera = "camera.front_door"),
            tablet,
            input(),
        )

        val video = doc.docContext.videos.single()
        assertEquals(
            LiveVideo("camera.front_door", LiveVideo.FIRST_COMPONENT_ID, crop = true),
            video,
        )
        assertVideoTargetFound(doc, video.componentId)
    }

    @Test
    fun aCameraCardsVideoPlaysOverItsSpinnerFittedLikeIt() {
        val doc = CardRenderer.render("camera.front_door", null, tablet, input())

        assertEquals(1, doc.docContext.pictures.size)
        assertTrue(doc.docContext.declares(Vars.entity("camera.front_door", Vars.KEY_CAM_WAITING)))
        assertTrue(doc.docContext.declares(Vars.entity("camera.front_door", Vars.KEY_CAM_STILL)))
        val video = doc.docContext.videos.single()
        assertEquals(
            LiveVideo("camera.front_door", LiveVideo.FIRST_COMPONENT_ID, crop = false),
            video,
        )
        assertVideoTargetFound(doc, video.componentId)
    }

    @Test
    fun anAlertWithoutACameraHasNoVideo() {
        assertTrue(
            AlertRenderer.render(Alert("Washer done"), tablet, input()).docContext.videos.isEmpty(),
        )
    }

    @Test
    fun aPersonCardsMapTargetsItsBoxAndStillBitmap() {
        val doc = CardRenderer.render("person.sam", null, tablet, input())

        assertTrue(doc.docContext.pictures.any { it.name == "map.card" })
        val map = doc.docContext.maps.single()
        assertEquals(listOf("person.sam"), map.entityIds)
        assertEquals(LiveMap.FIRST_COMPONENT_ID, map.componentId)
        assertTrue(map.dark)
        assertVideoTargetFound(doc, map.componentId)
    }

    @Test
    fun aPageBackgroundIsAPictureInTheContentBox() {
        val token = "1700000000000"
        val withPhoto = Screen(
            "s",
            "Wall",
            pages = listOf(Page("home", "Home", backgroundImage = token)),
        )
        val picture = ScreenRenderer.render(withPhoto, tablet, input()).docContext.pictures
            .single { it.name == "bg.home" }
        assertEquals("file:s/home-$token", picture.source)
        assertTrue(picture.crop)
        val plain = ScreenRenderer.render(Screen("s", "Wall"), tablet, input())
        assertTrue(plain.docContext.pictures.none { it.name.startsWith("bg.") })
    }

    /** The player finds the target by its id once it has read the document, as it must to lay the video over it. */
    private fun assertVideoTargetFound(doc: RenderedDoc, componentId: Int) {
        val component = assertNotNull(
            parse(doc.bytes).getComponent(componentId),
            "component $componentId",
        )
        assertEquals(componentId, component.componentId)
    }
}
