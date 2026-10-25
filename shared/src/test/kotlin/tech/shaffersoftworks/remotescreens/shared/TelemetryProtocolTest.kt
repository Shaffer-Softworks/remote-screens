package tech.shaffersoftworks.remotescreens.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryProtocolTest {
    @Test
    fun telemetryRoundTrips() {
        val message = PlayerMessage.Telemetry(
            DeviceTelemetry(
                hardware = listOf(TelemetryHardware.LIGHT, TelemetryHardware.GPS),
                battery = BatteryTelemetry(health = "good", temperatureC = 31.5f, cycles = 12),
                network = NetworkTelemetry(ssid = "HomeNet", rssiDbm = -54),
                system = SystemTelemetry(storageFreeBytes = 11_000_000_000L, model = "Tab M10"),
                environment = EnvironmentTelemetry(illuminanceLux = 182f, isNear = false),
                motion = MotionTelemetry(isMoving = true, orientation = "landscape", lastMotionAt = 1L),
                location = LocationTelemetry(latitude = 52.1, longitude = 4.3, accuracyM = 20f, fixedAt = 2L),
            ),
        )

        val decoded = Protocol.decodePlayer(Protocol.encode(message))

        assertEquals(message, decoded)
    }

    @Test
    fun absentGroupsAreLeftOffTheWire() {
        val encoded = Protocol.encode(PlayerMessage.Telemetry(DeviceTelemetry(hardware = listOf("light"))))

        assertTrue(encoded.contains("\"type\":\"telemetry\""))
        assertFalse(encoded.contains("location"))
        assertFalse(encoded.contains("battery"))
    }

    @Test
    fun unknownTelemetryFieldsAreIgnored() {
        val text =
            """{"type":"telemetry","telemetry":{"hardware":[],"extra":1,"motion":{"isMoving":true,"x":1}}}"""

        val decoded = Protocol.decodePlayer(text) as PlayerMessage.Telemetry

        assertTrue(decoded.telemetry.motion?.isMoving == true)
    }

    @Test
    fun telemetryIsOnAndLocationOffByDefault() {
        val settings = ScreenSettings().validated()

        assertTrue(settings.reportTelemetry)
        assertFalse(settings.shareLocation)
    }
}
