package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeSettingsTest {
    @Test
    fun wakeSourcesDefaultOff() {
        val settings = ScreenSettings()
        assertFalse(settings.wakeOnProximity)
        assertFalse(settings.wakeOnFace)
    }

    @Test
    fun wakeSourcesSurviveValidation() {
        val settings = ScreenSettings(wakeOnProximity = true, wakeOnFace = true).validated()
        assertTrue(settings.wakeOnProximity)
        assertTrue(settings.wakeOnFace)
    }
}
