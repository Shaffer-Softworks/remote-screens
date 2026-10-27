package tech.shaffersoftworks.remotescreens.server.render.panels.weather

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoLayout
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderDataRepository
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.Device
import tech.shaffersoftworks.remotescreens.server.render.RenderInput
import tech.shaffersoftworks.remotescreens.server.render.ScreenRenderer
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Office keypad, 750×1334 px at 240 dpi. Content under the compact bars is about 500×775. */
private const val KEYPAD_DENSITY = 1.5f
private const val KEYPAD_W = 500
private const val KEYPAD_H = 889

/** A short portrait panel, where the day list has to scroll. */
private const val SHORT_W = 375
private const val SHORT_H = 667

class WeatherPanelTest {
    private val ha = DemoHome()
    private val i18n = I18n("en", I18n.load("en"), emptyMap(), true)

    @Test
    fun aLandscapePageKeepsTheDetailPhrase() {
        val vars = render(Device(1280, 800, KEYPAD_DENSITY))
        assertEquals("Wind 12 km/h", vars["t.w1.wind_speed"]?.value)
        assertEquals("Humidity 71%", vars["t.w1.humidity"]?.value)
        assertEquals("Pressure 1,013 hPa", vars["t.w1.pressure"]?.value)
    }

    @Test
    fun aKeypadListsEachDayAndSplitsTheReadings() {
        val doc = rendered(Device(KEYPAD_W, KEYPAD_H, KEYPAD_DENSITY))
        val vars = doc.vars(ha.states.value)
        assertEquals("12 km/h", vars["t.w1.wind_speed"]?.value)
        assertEquals("71%", vars["t.w1.humidity"]?.value)
        assertEquals("1,013 hPa", vars["t.w1.pressure"]?.value)
        val text = String(doc.bytes, Charsets.ISO_8859_1)
        assertContains(text, "Wind")
        assertContains(text, "Humidity")
        assertContains(text, "Pressure")
        assertContains(text, LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
    }

    @Test
    fun aShortPortraitPageStillShowsTheReadings() {
        val vars = render(Device(SHORT_W, SHORT_H, KEYPAD_DENSITY))
        assertEquals("12 km/h", vars["t.w1.wind_speed"]?.value)
        assertEquals("71%", vars["t.w1.humidity"]?.value)
    }

    private fun render(device: Device) = rendered(device).vars(ha.states.value)

    private fun rendered(device: Device) = ScreenRenderer.render(
        DemoLayout.screen(),
        device,
        RenderInput(
            ha.states.value,
            i18n,
            ha.config.value,
            runBlocking { RenderDataRepository(ha).forScreen(DemoLayout.screen()) },
        ),
    )
}
