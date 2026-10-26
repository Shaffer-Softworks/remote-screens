package tech.shaffersoftworks.remotescreens.server.render

import tech.shaffersoftworks.remotescreens.server.data.homeassistant.demo.DemoHome
import tech.shaffersoftworks.remotescreens.server.data.renderdata.RenderData
import tech.shaffersoftworks.remotescreens.server.i18n.I18n
import tech.shaffersoftworks.remotescreens.server.render.theme.Themes
import tech.shaffersoftworks.remotescreens.server.render.widgets.HaWeatherIcons
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TopBar
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderConditionIconTest {
    private val ha = DemoHome()
    private val tablet = Device(1280, 800, 1.5f)

    private fun input() = RenderInput(
        ha.states.value,
        I18n("en", I18n.load("en"), emptyMap(), true),
        ha.config.value,
        RenderData(),
        Themes.RPI,
    )

    @Test
    fun headerWeatherUsesTheConditionIcon() {
        val screen = Screen(
            id = "s",
            name = "S",
            pages = listOf(Page(id = "home", title = "Home")),
            topBar = TopBar(temperatureEntity = "weather.home", people = false),
        )
        val weatherDoc = ScreenRenderer.render(screen, tablet, input())
        val diskX = partlyCloudyDiskX(tablet.density)
        assertTrue(
            contains(weatherDoc.bytes, floatBytes(diskX)),
            "the partly cloudy sun disk is drawn",
        )
        assertTrue(
            contains(weatherDoc.bytes, colorBytes(HaWeatherIcons.SUN)),
            "the sun is the weather page's yellow",
        )
        val sunny = ha.states.value.getValue("weather.home").copy(state = "sunny")
        assertTrue(weatherDoc.structureChanged(ha.states.value + ("weather.home" to sunny)))

        val sensorDoc = ScreenRenderer.render(
            screen.copy(
                topBar = TopBar(temperatureEntity = "sensor.outdoor_temperature", people = false),
            ),
            tablet,
            input(),
        )
        assertFalse(
            contains(sensorDoc.bytes, floatBytes(diskX)),
            "a temperature sensor keeps the thermometer",
        )
        assertFalse(sensorDoc.docContext.structure.any { it.first == "sensor.outdoor_temperature" })
    }

    private fun contains(bytes: ByteArray, needle: List<Byte>): Boolean =
        bytes.asList().windowed(needle.size).any { it == needle }

    private fun colorBytes(argb: Int): List<Byte> = listOf(24, 16, 8, 0).map { (argb ushr it).toByte() }

    private fun floatBytes(value: Float): List<Byte> = colorBytes(java.lang.Float.floatToRawIntBits(value))

    /** Header weather icons are stored in pixels, so the 17-grid x is scaled like the renderer. */
    private fun partlyCloudyDiskX(density: Float): Float =
        PARTLY_CLOUDY_DISK_GRID_X * (HEADER_ICON_DP * density / HaWeatherIcons.VIEWPORT)

    companion object {
        /** First x of the partly-cloudy sun disk in `ha-weather-paths.json`. */
        private const val PARTLY_CLOUDY_DISK_GRID_X = 14.981f

        /** Same 26 dp as the header stat icon when the top bar is the default height. */
        private const val HEADER_ICON_DP = 26f
    }
}
