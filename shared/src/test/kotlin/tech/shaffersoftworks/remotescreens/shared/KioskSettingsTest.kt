package tech.shaffersoftworks.remotescreens.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KioskSettingsTest {
    @Test
    fun `the kiosk is off by default`() {
        assertFalse(ScreenSettings().kiosk)
        assertNull(ScreenSettings().kioskPin)
    }

    @Test
    fun `a pin of four to eight digits is kept`() {
        assertEquals("1234", ScreenSettings(kioskPin = "1234").validated().kioskPin)
        assertEquals("12345678", ScreenSettings(kioskPin = "12345678").validated().kioskPin)
    }

    @Test
    fun `a pin that is too short, too long or not digits is dropped`() {
        assertNull(ScreenSettings(kioskPin = "123").validated().kioskPin)
        assertNull(ScreenSettings(kioskPin = "123456789").validated().kioskPin)
        assertNull(ScreenSettings(kioskPin = "12a4").validated().kioskPin)
        assertNull(ScreenSettings(kioskPin = "").validated().kioskPin)
    }
}
