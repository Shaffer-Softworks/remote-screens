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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** BLU M8L content under the default bars: 1280×800 px and 800×1280 px at 240 dpi. */
private const val TABLET_DENSITY = 1.5f
private const val LANDSCAPE_SCREEN_W = 853
private const val LANDSCAPE_SCREEN_H = 533
private const val LANDSCAPE_CONTENT_W = 853f
private const val LANDSCAPE_CONTENT_H = 383f
private const val PORTRAIT_SCREEN_W = 533
private const val PORTRAIT_SCREEN_H = 853
private const val PORTRAIT_CONTENT_W = 533f
private const val PORTRAIT_CONTENT_H = 739f

/** Office keypad, 750×1334 px at 240 dpi, under the compact bars. */
private const val KEYPAD_CONTENT_W = 500f
private const val KEYPAD_CONTENT_H = 775f

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
        assertContains(text, "ic1|intercom_hangup|")
        assertFalse("ic1|intercom_call|station-kitchen" in text, "a station already in a call cannot be called")
        assertFalse("ic1|card|" in text)
        val vars = IntercomVars.values(view, i18n)
        assertEqualsStatus(vars, "ic.r0.status", "Online")
        assertEqualsStatus(vars, "ic.r1.status", "In Call")
    }

    @Test
    fun aWideLandscapePageKeepsTheSideColumn() {
        val layout = IntercomLayout.of(1280f, 650f, statusLines = 1)

        assertFalse(layout.isStacked)
        assertEquals(350f, layout.columnW, 0.01f)
        assertEquals(150f, layout.keyW, 0.01f)
        val shorterTablet = IntercomLayout.of(1024f, 450f, statusLines = 1)
        assertFalse(shorterTablet.isStacked)
        assertEquals(350f, shorterTablet.columnW, 0.01f)
        assertEquals(150f, shorterTablet.keyW, 0.01f)
    }

    @Test
    fun aNarrowPageStacksTheKeysAndKeepsThemOnScreen() {
        val pageW = 360f
        val pageH = 526f
        val layout = IntercomLayout.of(pageW, pageH, statusLines = 1)

        assertTrue(layout.isStacked)
        assertKeysFit(layout, pageW)
        assertEquals(pageH, layout.headerH + layout.listH, 0.01f)
        assertTrue(layout.listH > 0f)
        assertContains(render(stations(), Device(360, 740, 2.75f)), "Hang Up")
        assertContains(render(stations(), Device(480, 320, 1.5f)), "Living Room")
    }

    @Test
    fun aLandscapePageKeepsRpisColumnWhenItFits() {
        val layout = IntercomLayout.of(800f, 330f, statusLines = 1)

        assertFalse(layout.isStacked)
        assertEquals(350f, layout.columnW, 0.01f)
        assertEquals(150f, layout.keyW, 0.01f)
        assertContains(render(stations(), Device(800, 480, 1.5f)), "Kitchen")
    }

    @Test
    fun aPageShorterThanTheColumnStacks() {
        val layout = IntercomLayout.of(900f, 140f, statusLines = 1)

        assertTrue(layout.isStacked)
        assertKeysFit(layout, 900f)
    }

    @Test
    fun aTabletUsesTheSideColumnInLandscapeAndAHeaderInPortrait() {
        val landscape = IntercomLayout.of(LANDSCAPE_CONTENT_W, LANDSCAPE_CONTENT_H, statusLines = 1)
        assertFalse(landscape.isStacked)
        assertEquals(350f, landscape.columnW, 0.01f)
        assertEquals(150f, landscape.keyW, 0.01f)
        assertTrue(sideNameRoom(landscape) >= IntercomMetrics.STATION_NAME_MIN_DP)

        val portrait = IntercomLayout.of(PORTRAIT_CONTENT_W, PORTRAIT_CONTENT_H, statusLines = 1)
        assertStackedBand(portrait)
        assertContains(
            render(stations(), Device(LANDSCAPE_SCREEN_W, LANDSCAPE_SCREEN_H, TABLET_DENSITY)),
            "Living Room",
        )
        assertContains(
            render(stations(), Device(PORTRAIT_SCREEN_W, PORTRAIT_SCREEN_H, TABLET_DENSITY)),
            "Kitchen",
        )
    }

    @Test
    fun landscapePortraitAndTheKeypadEachShowTheKeysAndAStation() {
        val landscape = IntercomLayout.of(LANDSCAPE_CONTENT_W, LANDSCAPE_CONTENT_H, statusLines = 1)
        assertFalse(landscape.isStacked)
        assertTrue(sideNameRoom(landscape) >= IntercomMetrics.STATION_NAME_MIN_DP)

        val stacked = listOf(
            IntercomLayout.of(PORTRAIT_CONTENT_W, PORTRAIT_CONTENT_H, statusLines = 1),
            IntercomLayout.of(KEYPAD_CONTENT_W, KEYPAD_CONTENT_H, statusLines = 1),
        )
        for (layout in stacked) {
            assertStackedBand(layout)
            assertTrue(stationRoom(layout) >= layout.dp(IntercomMetrics.ROW_H_DP), "no full station row")
            assertTrue(nameRoom(layout) >= IntercomMetrics.STATION_NAME_MIN_DP, "the name is squeezed beside Call")
        }
    }

    @Test
    fun anOlderTabletIsToldToUpdateAndGetsNoCallButtons() {
        val text = render(IntercomView.from(null, supportsCalls = false, i18n))

        assertContains(text, "INTERCOM")
        assertContains(text, "Update the tablet app to use the intercom")
        assertFalse("intercom_call" in text)
        assertFalse("intercom_mute" in text)
        assertFalse("intercom_hangup" in text)
        assertTrue(IntercomVars.values(IntercomView.from(null, supportsCalls = false, i18n), i18n).isEmpty())
    }

    @Test
    fun theIntercomPageIndexPointsAtThePageWithTheTile() {
        val index = screen.intercomPage()

        assertTrue(index >= 0)
        assertEquals("intercom", screen.pages[index].allTiles().first().effectiveKind())
    }

    private fun render(view: IntercomView, device: Device = tablet): String {
        val input = RenderInput(ha.states.value, i18n, ha.config.value, RenderData(), intercom = view)
        return String(ScreenRenderer.render(screen, device, input).bytes, Charset.forName("ISO-8859-1"))
    }

    private fun stations(): IntercomView = IntercomView(
        supportsCalls = true,
        status = "Online",
        isBusy = false,
        connectedId = null,
        stations = listOf(
            IntercomStation("station-living", "Living Room", inCall = false),
            IntercomStation("station-kitchen", "Kitchen", inCall = true),
        ),
    )

    private fun assertStackedBand(layout: IntercomLayout) {
        assertTrue(layout.isStacked)
        assertTrue(layout.listH > layout.headerH, "header ${layout.headerH} leaves ${layout.listH} for stations")
        assertTrue(layout.listH >= layout.dp(IntercomMetrics.ROW_H_DP))
        assertKeysFit(layout, layout.pageW)
    }

    /** Height left under the header after the list's own top and bottom insets. */
    private fun stationRoom(layout: IntercomLayout): Float =
        layout.listH - layout.dp(IntercomMetrics.LIST_TOP_DP) - layout.dp(IntercomMetrics.LIST_BOTTOM_DP)

    /** Width left for the station name once the side column, the icon and the Call key are placed. */
    private fun sideNameRoom(layout: IntercomLayout): Float {
        val chrome = layout.dp(
            IntercomMetrics.LIST_START_DP + IntercomMetrics.LIST_END_DP +
                IntercomMetrics.ROW_PAD_DP * 2f + IntercomMetrics.ROW_INSET_DP * 3f +
                IntercomMetrics.ROW_ICON_DP,
        )
        return layout.pageW - layout.columnW - chrome - layout.keyW
    }

    /** Width left for the station name once the icon and the Call key are placed. */
    private fun nameRoom(layout: IntercomLayout): Float {
        val chrome = layout.dp(
            IntercomMetrics.LIST_START_DP + IntercomMetrics.LIST_END_DP +
                IntercomMetrics.ROW_PAD_DP * 2f + IntercomMetrics.ROW_INSET_DP * 3f +
                IntercomMetrics.ROW_ICON_DP,
        )
        return layout.pageW - chrome - layout.keyW
    }

    private fun assertKeysFit(layout: IntercomLayout, pageW: Float) {
        val pair = layout.keyW * 2f + layout.dp(IntercomMetrics.BUTTON_GAP_DP) +
            layout.dp(IntercomMetrics.STACK_SIDE_DP) * 2f
        assertTrue(pair <= pageW + 0.05f, "keys take $pair dp on a $pageW dp page")
    }

    private fun assertEqualsStatus(
        vars: List<tech.shaffersoftworks.remotescreens.shared.VarValue>,
        name: String,
        expected: String,
    ) {
        kotlin.test.assertEquals(expected, vars.first { it.name == name }.value)
    }
}
