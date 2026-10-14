package dev.remotescreens.server

import dev.remotescreens.shared.DeviceSize
import dev.remotescreens.shared.LayoutData
import dev.remotescreens.shared.Screen
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenSizeTest {
    @Test
    fun aCustomSizeIsKeptWithinTheLimits() {
        val s = validate(Screen("s", "Wall", designSize = DeviceSize(50, 99999)))
        assertEquals(DeviceSize(DeviceSize.MIN, DeviceSize.MAX), s.designSize)
        assertEquals(DeviceSize(1100, 700), validate(Screen("s", "Wall", designSize = DeviceSize(1100, 700))).designSize)
    }

    @Test
    fun aNewScreenKeepsTheOrientationItWasDesignedFor() {
        assertEquals(1, orientationOf(DeviceSize(375, 667)))
        assertEquals(2, orientationOf(DeviceSize(1280, 800)))
        assertEquals(0, orientationOf(DeviceSize(480, 480)))
        assertEquals(0, orientationOf(null))
    }

    @Test
    fun aScreenSavedBeforeCustomSizesHasNone() {
        val json = """{"schemaVersion":2,"screens":[{"id":"s","name":"Old"}]}"""
        val data = Json { ignoreUnknownKeys = true }.decodeFromString(LayoutData.serializer(), json)
        assertNull(data.screens.single().designSize)
    }
}
