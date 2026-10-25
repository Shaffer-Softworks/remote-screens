package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.shared.BatteryTelemetry
import tech.shaffersoftworks.remotescreens.shared.NetworkTelemetry
import tech.shaffersoftworks.remotescreens.shared.SystemTelemetry
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

private const val TENTHS_PER_DEGREE = 10f
private const val MICROAMPS_PER_MILLIAMP = 1_000
private const val MILLISECONDS_PER_SECOND = 1_000L
private const val UNKNOWN_SSID = "<unknown ssid>"
private const val HIDDEN_BSSID = "02:00:00:00:00:00"
private const val CYCLE_COUNT_EXTRA = "android.os.extra.CYCLE_COUNT"

/** `BatteryManager.BATTERY_PLUGGED_DOCK`, which only exists from Android 13. */
private const val BATTERY_PLUGGED_DOCK = 8

/** The diagnostics Android hands out on request: battery, Wi-Fi, storage, memory and versions. */
@Singleton
class DiagnosticsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    fun readBattery(): BatteryTelemetry? {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null
        val temperatureTenths = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val currentMicroamps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            batteryIntent.getIntExtra(CYCLE_COUNT_EXTRA, -1)
        } else {
            -1
        }
        return BatteryTelemetry(
            health = healthName(batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)),
            temperatureC = temperatureTenths.takeIf { it != Int.MIN_VALUE }?.let { it / TENTHS_PER_DEGREE },
            voltageMv = voltageMv.takeIf { it > 0 },
            currentMa = currentMicroamps?.takeIf { it != Int.MIN_VALUE && it != 0 }
                ?.let { it / MICROAMPS_PER_MILLIAMP },
            chargerType = chargerName(batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)),
            cycles = cycles.takeIf { it >= 0 },
        )
    }

    fun readNetwork(): NetworkTelemetry? {
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager?.takeIf { it.isWifiEnabled }?.connectionInfo?.takeIf { it.networkId != -1 }
        val ipAddress = runCatching {
            connectivityManager?.getLinkProperties(connectivityManager.activeNetwork)
                ?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()
        if (wifiInfo == null && ipAddress == null) return null
        return NetworkTelemetry(
            ssid = wifiInfo?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID },
            bssid = wifiInfo?.bssid?.takeIf { it != HIDDEN_BSSID },
            ipAddress = ipAddress,
            linkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 },
            frequencyMhz = wifiInfo?.frequency?.takeIf { it > 0 },
            rssiDbm = wifiInfo?.rssi,
        )
    }

    fun readSystem(): SystemTelemetry {
        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val appVersionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        return SystemTelemetry(
            storageFreeBytes = storage?.availableBytes,
            storageTotalBytes = storage?.totalBytes,
            memoryAvailableBytes = memoryInfo.availMem.takeIf { it > 0 },
            memoryTotalBytes = memoryInfo.totalMem.takeIf { it > 0 },
            uptimeSeconds = SystemClock.elapsedRealtime() / MILLISECONDS_PER_SECOND,
            osVersion = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
            appVersionName = appVersionName,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        )
    }

    private fun healthName(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
        else -> "unknown"
    }

    private fun chargerName(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "ac"
        BatteryManager.BATTERY_PLUGGED_USB -> "usb"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
        BATTERY_PLUGGED_DOCK -> "dock"
        else -> "none"
    }
}
