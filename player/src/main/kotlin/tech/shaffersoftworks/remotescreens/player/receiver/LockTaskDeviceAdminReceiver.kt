package tech.shaffersoftworks.remotescreens.player.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import timber.log.Timber
import javax.inject.Inject

/**
 * The device owner admin, set by QR enrollment or `scripts/enroll-device-owner.sh`. It lets the kiosk lock the tablet
 * to the player with lock task mode, and brings the player back if lock task ends without being asked to.
 */
@AndroidEntryPoint
class LockTaskDeviceAdminReceiver : DeviceAdminReceiver() {
    @Inject
    lateinit var kioskManager: KioskManager

    override fun onLockTaskModeEntering(context: Context, intent: Intent, packageName: String) {
        super.onLockTaskModeEntering(context, intent, packageName)
        Timber.i("onLockTaskModeEntering")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Timber.i("onLockTaskModeExiting")
        kioskManager.relaunchKioskIfUnlocked(reason = "lock_task_exiting")
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.i("LockTask: onEnabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.i("LockTask: onDisabled")
    }

    /** Android before 10 ends QR enrollment here instead of in the provisioning activities. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Timber.i("LockTask: onProfileProvisioningComplete")
        kioskManager.onProvisioned(intent)
        context.startActivity(KioskManager.mainActivityIntent(context))
    }
}
