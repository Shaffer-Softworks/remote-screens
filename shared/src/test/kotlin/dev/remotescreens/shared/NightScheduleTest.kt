package dev.remotescreens.shared

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import java.time.LocalTime

class NightScheduleTest {
    @Test
    fun `no night without both times`() {
        assertFalse(ScreenSettings(nightStart = "22:00").isNightAt(LocalTime.of(23, 0)))
        assertFalse(ScreenSettings().isNightAt(LocalTime.MIDNIGHT))
    }

    @Test
    fun `a night window can wrap past midnight`() {
        val settings = ScreenSettings(nightStart = "22:00", nightEnd = "06:30")

        assertTrue(settings.isNightAt(LocalTime.of(23, 0)))
        assertTrue(settings.isNightAt(LocalTime.of(3, 0)))
        assertFalse(settings.isNightAt(LocalTime.of(6, 30)))
        assertFalse(settings.isNightAt(LocalTime.of(12, 0)))
    }

    @Test
    fun `a window within one day`() {
        val settings = ScreenSettings(nightStart = "01:00", nightEnd = "05:00")

        assertTrue(settings.isNightAt(LocalTime.of(1, 0)))
        assertFalse(settings.isNightAt(LocalTime.of(5, 0)))
        assertFalse(settings.isNightAt(LocalTime.of(23, 0)))
    }

    @Test
    fun `dark in dark mode, or at night when night is dark`() {
        val night = ScreenSettings(nightStart = "22:00", nightEnd = "06:00")

        assertTrue(ScreenSettings(darkMode = true).isDarkAt(LocalTime.NOON))
        assertTrue(night.isDarkAt(LocalTime.of(23, 0)))
        assertFalse(night.isDarkAt(LocalTime.NOON))
        assertFalse(night.copy(nightDark = false).isDarkAt(LocalTime.of(23, 0)))
    }

    @Test
    fun `unreadable times are no night`() {
        assertFalse(ScreenSettings(nightStart = "late", nightEnd = "06:00").isNightAt(LocalTime.of(3, 0)))
    }
}
