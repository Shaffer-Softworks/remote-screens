package tech.shaffersoftworks.remotescreens.server.service.validation

import tech.shaffersoftworks.remotescreens.shared.DeviceSize
import tech.shaffersoftworks.remotescreens.shared.Drawer
import tech.shaffersoftworks.remotescreens.shared.LayoutData
import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.Page
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TopBar
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenValidatorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aCustomSizeIsKeptWithinTheLimits() {
        val s = ScreenValidator.validate(Screen("s", "Wall", designSize = DeviceSize(50, 99999)))
        assertEquals(DeviceSize(DeviceSize.MIN, DeviceSize.MAX), s.designSize)
        val validated = ScreenValidator.validate(
            Screen("s", "Wall", designSize = DeviceSize(1100, 700)),
        )
        assertEquals(DeviceSize(1100, 700), validated.designSize)
    }

    @Test
    fun barSettingsAreKeptInRange() {
        val s = ScreenValidator.validate(
            Screen(
                "s",
                "Wall",
                topBar = TopBar(height = 999, clockWidth = 1, background = "x".repeat(99)),
                navBar = NavBar(height = 2, labels = "huge", layout = "diagonal", active = "pink"),
            ),
        )
        assertEquals(TopBar.MAX_HEIGHT, s.topBar.height)
        assertEquals(TopBar.MIN_CLOCK_WIDTH, s.topBar.clockWidth)
        assertNull(s.topBar.background)
        assertEquals(NavBar.MIN_HEIGHT, s.navBar.height)
        assertEquals("both", s.navBar.labels)
        assertEquals("spread", s.navBar.layout)
        assertEquals("pink", s.navBar.active)
        assertEquals(NavBar(), ScreenValidator.validate(Screen("s", "Wall")).navBar)
        val defaults = ScreenValidator.validate(Screen("s", "Wall")).topBar
        assertEquals(false, defaults.wifi)
        assertEquals(false, defaults.battery)
    }

    @Test
    fun drawerSettingsAreKeptInRange() {
        val screen = ScreenValidator.validate(
            Screen(
                "s",
                "Wall",
                navBar = NavBar(show = false),
                drawer = Drawer(style = "modal", side = "middle", width = 999),
            ),
        )
        assertEquals("modal", screen.drawer.style)
        assertEquals("start", screen.drawer.side)
        assertEquals(Drawer.MAX_MODAL_WIDTH, screen.drawer.width)
        assertFalse(screen.navBar.show)
        val modal = ScreenValidator.validate(Screen("s", "Wall", drawer = Drawer(style = "modal", width = 10)))
        assertEquals(Drawer.MIN_MODAL_WIDTH, modal.drawer.width)
        assertEquals(Drawer(), ScreenValidator.validate(Screen("s", "Wall")).drawer)
    }

    @Test
    fun theBottomBarStaysWithoutADrawer() {
        for (style in listOf("sideways", "rail")) {
            val screen = ScreenValidator.validate(
                Screen("s", "Wall", navBar = NavBar(show = false), drawer = Drawer(style = style)),
            )
            assertEquals("off", screen.drawer.style)
            assertTrue(screen.navBar.show)
        }
    }

    @Test
    fun aNewScreenKeepsTheOrientationItWasDesignedFor() {
        assertEquals(1, ScreenValidator.orientationOf(DeviceSize(375, 667)))
        assertEquals(2, ScreenValidator.orientationOf(DeviceSize(1280, 800)))
        assertEquals(0, ScreenValidator.orientationOf(DeviceSize(480, 480)))
        assertEquals(0, ScreenValidator.orientationOf(null))
    }

    @Test
    fun aBackgroundTokenMustBeDigits() {
        val kept = ScreenValidator.validate(
            Screen("s", "Wall", pages = listOf(Page("home", "Home", backgroundImage = "1700000000000"))),
        )
        assertEquals("1700000000000", kept.pages.single().backgroundImage)
        val dropped = ScreenValidator.validate(
            Screen("s", "Wall", pages = listOf(Page("home", "Home", backgroundImage = "../secret"))),
        )
        assertNull(dropped.pages.single().backgroundImage)
    }

    @Test
    fun aScreenSavedBeforeCustomSizesHasNone() {
        val payload = """{"schemaVersion":2,"screens":[{"id":"s","name":"Old"}]}"""
        val data = json.decodeFromString(LayoutData.serializer(), payload)
        assertNull(data.screens.single().designSize)
    }
}
