package tech.shaffersoftworks.remotescreens.player.data.device.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.data.device.DeviceRepository
import javax.inject.Inject

/** Reads actual grants each report, including changes made in Android settings. */
class PermissionReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
) {
    fun snapshot(): Map<String, Boolean> {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }.toMutableMap()
        // Special access uses app-ops rather than ordinary permission grants.
        permissions[Manifest.permission.SYSTEM_ALERT_WINDOW] = deviceRepository.canDrawOverlays()
        permissions[Manifest.permission.REQUEST_INSTALL_PACKAGES] = deviceRepository.canInstallPackages()
        permissions[Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] =
            deviceRepository.isIgnoringBatteryOptimizations()
        return permissions
    }
}
