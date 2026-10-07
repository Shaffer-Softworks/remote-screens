package dev.remotescreens.server.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.NamedVariable
import dev.remotescreens.server.DataFetcher
import dev.remotescreens.server.I18n
import dev.remotescreens.server.ha.DemoHome
import dev.remotescreens.server.ha.DemoLayout
import dev.remotescreens.shared.Catalogue
import dev.remotescreens.shared.Reflow
import dev.remotescreens.shared.VarType
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)
    private val tablet = Device(1280, 800, 1.5f)

    private fun input(data: RenderData = RenderData(), dark: Boolean = false) = RenderInput(ha.states.value, i18n, ha.config.value, data, dark)

    private fun parse(bytes: ByteArray) = CoreDocument().also { it.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes))) }

    @Test
    fun everyPageOfTheDemoScreenIsOneDocument() {
        val screen = DemoLayout.screen()
        val data = runBlocking { DataFetcher(ha).forScreen(screen) }
        val doc = ScreenRenderer.render(screen, tablet, input(data))
        val core = parse(doc.bytes)
        assertContains(core.getNamedVariables(NamedVariable.INT_TYPE).toList(), "USER:page")
        assertEquals(screen.pages.map { it.id }, doc.pageIds)

        val vars = doc.vars(ha.states.value)
        assertEquals("Ceiling light", vars["e.light.living_room.name"]?.value)
        assertEquals(VarType.COLOR, vars["t.t1.line"]?.type)
        val declared = core.getNamedVariables(NamedVariable.STRING_TYPE).toSet() +
            core.getNamedVariables(NamedVariable.INT_TYPE) + core.getNamedVariables(NamedVariable.FLOAT_TYPE) + core.getNamedVariables(NamedVariable.COLOR_TYPE)
        val unread = vars.keys.filter { "USER:$it" !in declared && !it.startsWith("sys.") }
        assertTrue(unread.isEmpty(), "values sent but never declared: $unread")
    }

    @Test
    fun everyDemoTileHasItsOwnCell() {
        val screen = DemoLayout.screen()
        for (page in screen.pages) {
            val placed = Reflow.place(page.tiles, screen.grid)
            assertEquals(page.tiles.map { it.id }.sorted(), placed.map { it.tile.id }.sorted(), "tiles dropped from ${page.id}")
            for (p in placed) assertEquals(p.tile.col to p.tile.row, p.col to p.row, "${p.tile.id} moved on ${page.id}")
        }
    }

    @Test
    fun aToggleIsForeseen() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val meta = "t2|toggle|"
        assertTrue(meta in doc.ctx.predicted, "the table lamp's toggle can be predicted")
    }

    @Test
    fun aTileLightsUpWhenItsEntityIsOn() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val states = ha.states.value
        val off = doc.vars(states)
        val on = doc.vars(states + ("light.table_lamp" to states.getValue("light.table_lamp").with("on")))
        assertEquals(Role.CARD_HEADER.argb, off["t.t2.line"]?.asColor())
        assertEquals(Pastel.YELLOW.argb, on["t.t2.line"]?.asColor())
        assertEquals(Pastel.YELLOW.argb, on["t.t2.icon"]?.asColor())
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
        val data = runBlocking { DataFetcher(ha).forScreen(screen) }
        val doc = ScreenRenderer.render(screen, tablet, input(data))
        val vars = doc.vars(ha.states.value)
        assertEquals("Wind 12 km/h", vars["t.w1.wind_speed"]?.value)
        assertEquals("Humidity 71%", vars["t.w1.humidity"]?.value)
        assertEquals("Pressure 1,013 hPa", vars["t.w1.pressure"]?.value)
        assertEquals("14°", vars["e.weather.home.value"]?.value)
    }

    @Test
    fun aDocumentIsStaleOnlyWhenWhatIsDrawnChanges() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), tablet, input())
        val states = ha.states.value
        assertFalse(doc.stale(states))
        val lamp = states.getValue("light.table_lamp")
        assertFalse(doc.stale(states + ("light.table_lamp" to lamp.with("on"))), "a state change is a value, not a rebuild")
    }

    @Test
    fun everyCardBuilds() {
        val ids = ha.states.value.keys.filter { Catalogue.of(it.substringBefore('.'))?.card?.let { c -> c != "none" } == true }
        for (id in ids) {
            val data = runBlocking { DataFetcher(ha).forCard(id, null) }
            val doc = CardRenderer.render(id, null, tablet, input(data))
            assertTrue(parse(doc.bytes).getNamedVariables(NamedVariable.STRING_TYPE).isNotEmpty(), id)
        }
        val energy = runBlocking { DataFetcher(ha).forCard(null, "energy") }
        CardRenderer.render("card.energy", "energy", tablet, input(energy))
    }

    @Test
    fun aPortraitPhoneGetsTheSameScreen() {
        val doc = ScreenRenderer.render(DemoLayout.screen(), Device(400, 860, 2.75f), input())
        assertContains(parse(doc.bytes).getNamedVariables(NamedVariable.STRING_TYPE).toList(), "USER:e.light.living_room.name")
    }

    @Test
    fun anAlertBuilds() {
        val doc = AlertRenderer.render(Alert("Doorbell", "Someone is at the door", camera = "camera.front_door", buttons = listOf(AlertButton("Open"))), tablet, input())
        assertEquals(1, doc.ctx.pictures.size)
    }
}
