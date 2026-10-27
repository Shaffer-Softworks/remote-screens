package tech.shaffersoftworks.remotescreens.player.data.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.receiver.LockTaskDeviceAdminReceiver
import tech.shaffersoftworks.remotescreens.player.receiver.ScreenOffAdminReceiver
import javax.inject.Inject
import javax.inject.Singleton

private const val WAKE_UP_HOLD_MS = 5_000L
private const val WAKE_LOCK_TAG = "RemoteScreens:"

/**
 * Turns the screen really off for standby, by locking the tablet as its device admin, and on again.
 * The activity shows over the lock screen, so waking up goes straight back to the screen.
 */
@Singleton
class ScreenPowerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    val adminComponent = ComponentName(context, ScreenOffAdminReceiver::class.java)
    private val deviceOwnerComponent = ComponentName(
        context,
        LockTaskDeviceAdminReceiver::class.java,
    )

    /** While the screen is off the CPU must keep running, or the add-on could not wake it again. */
    private val keepConnectedLock: PowerManager.WakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG + "standby")
            .apply { setReferenceCounted(false) }

    /**
     * Stored so the panel actually comes back on after [DevicePolicyManager.lockNow]. A bright wake
     * lock created and dropped on the sensor thread does not turn this PX30 back on.
     */
    @Suppress("DEPRECATION")
    private val wakeUpLock: PowerManager.WakeLock =
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG + "wake",
        ).apply { setReferenceCounted(false) }

    var isScreenOff: Boolean = false
        private set

    /** The device owner admin holds the same lock policy, so an enrolled kiosk can turn the screen off too. */
    fun canTurnScreenOff(): Boolean =
        devicePolicyManager.isAdminActive(
            adminComponent,
        ) || devicePolicyManager.isAdminActive(deviceOwnerComponent)

    fun turnScreenOff() {
        if (isScreenOff || !canTurnScreenOff()) return
        @Suppress("WakelockTimeout")
        keepConnectedLock.acquire()
        isScreenOff = true
        devicePolicyManager.lockNow()
    }

    fun turnScreenOn() {
        val panelIsOff = isScreenOff || !powerManager.isInteractive
        if (!panelIsOff) return
        isScreenOff = false
        wakeUpLock.acquire(WAKE_UP_HOLD_MS)
        if (keepConnectedLock.isHeld) keepConnectedLock.release()
    }
}
