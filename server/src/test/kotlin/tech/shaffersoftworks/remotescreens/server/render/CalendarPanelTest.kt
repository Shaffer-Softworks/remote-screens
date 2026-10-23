package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.HomeAssistantConfig
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaCalendar
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaEvent
import tech.shaffersoftworks.remotescreens.server.data.renderdata.AgendaSnapshot
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.Tile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class CalendarPanelTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)
    private val tablet = Device(1280, 800, 1.5f)

    @Test
    fun aFullCalendarTileDrawsTheRpiAgenda() {
        val screen = Screen(
            id = "s",
            name = "S",
            pages = listOf(
                Page(
                    id = "home",
                    title = "Home",
                    tiles = listOf(Tile(id = "cal", entityId = "calendar.family", full = true)),
                ),
            ),
        )
        val data = RenderData(
            agenda = AgendaSnapshot(
                events = listOf(
                    AgendaEvent(
                        uid = "1",
                        recurrenceId = null,
                        entityId = "calendar.family",
                        calendarName = "Family",
                        summary = "Soccer practice",
                        location = "Field 2",
                        start = Instant.parse("2026-07-15T17:30:00Z"),
                        end = Instant.parse("2026-07-15T18:30:00Z"),
                        isAllDay = false,
                        canDelete = true,
                    ),
                ),
                calendars = listOf(
                    AgendaCalendar("calendar.family", "Family", canCreate = true, canDelete = true),
                ),
            ),
        )
        val doc = ScreenRenderer.render(
            screen,
            tablet,
            RenderInput(
                ha.states.value,
                i18n,
                HomeAssistantConfig(timeZone = "UTC"),
                data,
                Themes.LIGHT,
                supportsCalendarDialogs = true,
            ),
        )
        val text = doc.bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("Upcoming events"))
        assertTrue(text.contains("Wed, Jul 15"))
        assertTrue(text.contains("5:30PM"))
        assertTrue(text.contains("Soccer practice"))
        assertTrue(text.contains("Family · Field 2 · Hold to delete"))
        assertTrue(text.contains("cal|calendar_create|"))
        assertTrue(doc.docContext.declares("#ff333333"), "page background")
        assertTrue(doc.docContext.declares("#ff03dac5"), "teal rule")
        assertTrue(doc.docContext.declares("#ff1da48c"), "day header")
        assertTrue(doc.docContext.declares("#ff3b3b3b"), "row")
    }
}
