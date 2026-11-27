package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverSettingsTest {
    @Test
    fun defaultsStayOffAndClampsRejectOutOfRangeValues() {
        val settings = ScreenSettings()

        assertFalse(settings.screensaver.enabled)
        assertEquals(ScreensaverSettings.DEFAULT_MINUTES, settings.screensaver.minutes)
        assertEquals(ScreensaverSettings.SOURCE_HA, settings.screensaver.source)
        assertFalse(settings.wantsScreenOff())

        val clamped = ScreensaverSettings(
            minutes = ScreensaverSettings.MAX_MINUTES + 1,
            seconds = 1,
            source = "plex",
            immichApiKey = "  ",
            weatherEntity = "",
        ).validated()

        assertEquals(ScreensaverSettings.MAX_MINUTES, clamped.minutes)
        assertEquals(ScreensaverSettings.MIN_SECONDS, clamped.seconds)
        assertEquals(ScreensaverSettings.SOURCE_HA, clamped.source)
        assertNull(clamped.immichApiKey)
        assertNull(clamped.weatherEntity)
    }

    @Test
    fun aScreensaverTimeoutWantsTheScreenOff() {
        val settings = ScreenSettings(
            screensaver = ScreensaverSettings(enabled = true, minutes = ScreensaverSettings.DEFAULT_MINUTES),
        )

        assertTrue(settings.wantsScreenOff())

        assertFalse(
            settings.copy(
                screensaver = settings.screensaver.copy(minutes = 0),
            ).wantsScreenOff(),
        )
    }

    @Test
    fun theTabletCopyDropsTheImmichKeyAndASaveKeepsTheStoredOne() {
        val stored = ScreenSettings(
            screensaver = ScreensaverSettings(
                enabled = true,
                source = ScreensaverSettings.SOURCE_IMMICH,
                immichApiKey = "secret-key",
            ),
        )

        val sent = stored.forPlayer()

        assertNull(sent.screensaver.immichApiKey)
        assertEquals("secret-key", stored.keepingSecrets(sent).screensaver.immichApiKey)

        assertEquals(
            "replacement",
            stored.keepingSecrets(
                sent.copy(screensaver = sent.screensaver.copy(immichApiKey = "replacement")),
            ).screensaver.immichApiKey,
        )
    }
}
