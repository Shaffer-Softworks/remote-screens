package tech.shaffersoftworks.remotescreens.server.service.validation

import tech.shaffersoftworks.remotescreens.shared.DeviceSize
import tech.shaffersoftworks.remotescreens.shared.LayoutData
import tech.shaffersoftworks.remotescreens.shared.NavBar
import tech.shaffersoftworks.remotescreens.shared.Screen
import tech.shaffersoftworks.remotescreens.shared.TopBar
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenValidatorTest {
    @Test
    fun aCustomSizeIsKeptWithinTheLimits() {
        val s = ScreenValidator.validate(Screen("s", "Wall", designSize = DeviceSize(50, 99999)))
        assertEquals(DeviceSize(DeviceSize.MIN, DeviceSize.MAX), s.designSize)
        val validated = ScreenValidator.validate(Screen("s", "Wall", designSize = DeviceSize(1100, 700)))
        assertEquals(DeviceSize(1100, 700), validated.designSize)
    }

    @Test
    fun barSettingsAreKeptInRange() {
        val s = ScreenValidator.validate(
            Screen(
                "s", "Wall",
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
    }

    @Test
    fun aNewScreenKeepsTheOrientationItWasDesignedFor() {
        assertEquals(1, ScreenValidator.orientationOf(DeviceSize(375, 667)))
        assertEquals(2, ScreenValidator.orientationOf(DeviceSize(1280, 800)))
        assertEquals(0, ScreenValidator.orientationOf(DeviceSize(480, 480)))
        assertEquals(0, ScreenValidator.orientationOf(null))
    }

    @Test
    fun aScreenSavedBeforeCustomSizesHasNone() {
        val json = """{"schemaVersion":2,"screens":[{"id":"s","name":"Old"}]}"""
        val data = Json { ignoreUnknownKeys = true }.decodeFromString(LayoutData.serializer(), json)
        assertNull(data.screens.single().designSize)
    }
}
