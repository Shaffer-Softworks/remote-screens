package dev.remotescreens.player.data.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PERCENT = 100

data class BatteryStatus(val levelPercent: Int?, val isCharging: Boolean?)

/** What the player can tell about the tablet it runs on. */
@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    val model: String = Build.MODEL

    val appVersionCode: Int
        get() {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        }

    fun batteryStatus(): BatteryStatus {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryStatus(levelPercent = null, isCharging = null)
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val levelPercent = if (level >= 0 && scale > 0) level * PERCENT / scale else null
        val isCharging = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return BatteryStatus(levelPercent, isCharging)
    }

    fun isHomeApp(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homeActivity = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return homeActivity?.activityInfo?.packageName == context.packageName
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Android only lets an app open itself (after a restart or an update) as the home app or with the
     * "display over other apps" permission.
     */
    fun canSelfStart(): Boolean = isHomeApp() || canDrawOverlays()

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()
}
