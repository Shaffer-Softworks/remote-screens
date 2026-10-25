package tech.shaffersoftworks.remotescreens.shared

import kotlinx.serialization.Serializable

/**
 * What a tablet reports about itself in [PlayerMessage.Telemetry]. Every group and field is optional: a tablet
 * without the hardware, or without the permission to read it, leaves it out.
 */
@Serializable
data class DeviceTelemetry(
    /** The sensors the tablet has, from [TelemetryHardware]; Home Assistant only creates entities for these. */
    val hardware: List<String> = emptyList(),
    val battery: BatteryTelemetry? = null,
    val network: NetworkTelemetry? = null,
    val system: SystemTelemetry? = null,
    val environment: EnvironmentTelemetry? = null,
    val motion: MotionTelemetry? = null,
    /** Only while [ScreenSettings.shareLocation] is on and the tablet has a fix. */
    val location: LocationTelemetry? = null,
)

/** [DeviceTelemetry.hardware] values. */
object TelemetryHardware {
    const val LIGHT = "light"
    const val PRESSURE = "pressure"
    const val AMBIENT_TEMPERATURE = "ambient_temp"
    const val HUMIDITY = "humidity"
    const val PROXIMITY = "proximity"
    const val ACCELEROMETER = "accelerometer"
    const val SIGNIFICANT_MOTION = "significant_motion"
    const val GPS = "gps"
}

@Serializable
data class BatteryTelemetry(
    /** `good`, `overheat`, `dead`, `over_voltage`, `cold`, `failure` or `unknown`. */
    val health: String? = null,
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    /** Positive while charging, negative while discharging. */
    val currentMa: Int? = null,
    /** `ac`, `usb`, `wireless`, `dock` or `none`. */
    val chargerType: String? = null,
    val cycles: Int? = null,
)

@Serializable
data class NetworkTelemetry(
    /** Needs location permission on Android 8 and later, else null. */
    val ssid: String? = null,
    val bssid: String? = null,
    val ipAddress: String? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val rssiDbm: Int? = null,
)

@Serializable
data class SystemTelemetry(
    val storageFreeBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val memoryAvailableBytes: Long? = null,
    val memoryTotalBytes: Long? = null,
    val uptimeSeconds: Long? = null,
    val osVersion: String? = null,
    val securityPatch: String? = null,
    val appVersionName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
)

@Serializable
data class EnvironmentTelemetry(
    val illuminanceLux: Float? = null,
    val pressureHpa: Float? = null,
    val temperatureC: Float? = null,
    val humidityPercent: Float? = null,
    /** Something within the proximity sensor's range (a few cm on most tablets). */
    val isNear: Boolean? = null,
)

@Serializable
data class MotionTelemetry(
    /** Stays true for a while after the last movement, so a poll does not miss it. */
    val isMoving: Boolean = false,
    /** `portrait` or `landscape`. */
    val orientation: String? = null,
    /** Epoch milliseconds of the last movement, or null when none was seen since the player started. */
    val lastMotionAt: Long? = null,
)

@Serializable
data class LocationTelemetry(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float? = null,
    val altitudeM: Double? = null,
    /** Epoch milliseconds of the fix. */
    val fixedAt: Long,
)
