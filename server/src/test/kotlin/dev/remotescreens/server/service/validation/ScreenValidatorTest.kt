package dev.remotescreens.server.service.validation

import dev.remotescreens.shared.DeviceSize
import dev.remotescreens.shared.LayoutData
import dev.remotescreens.shared.Screen
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
