package tech.shaffersoftworks.remotescreens.player.data.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PersistableBundle
import android.os.UserManager
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.MainActivity
import tech.shaffersoftworks.remotescreens.player.data.device.DeviceRepository
import tech.shaffersoftworks.remotescreens.player.data.preferences.PlayerPreferencesRepository
import tech.shaffersoftworks.remotescreens.player.di.IoScope
import tech.shaffersoftworks.remotescreens.player.receiver.LockTaskDeviceAdminReceiver
import tech.shaffersoftworks.remotescreens.player.utils.normalizeServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** How firmly the player is held in front. */
enum class KioskState {
    OFF,

    /** Screen pinning: Android asked the user, and holding Back and Overview gets out. */
    PINNED,

    /** Lock task mode as the device owner: there is no way out on the tablet. */
    LOCKED,
}

private val KIOSK_USER_RESTRICTIONS = listOf(
    UserManager.DISALLOW_SAFE_BOOT,
    UserManager.DISALLOW_FACTORY_RESET,
    UserManager.DISALLOW_ADD_USER,
)

/**
 * Keeps the player in front. As the device owner it uses lock task mode with the kiosk policies (home app, no status
 * bar, no lock screen); otherwise `startLockTask` falls back to screen pinning. Every policy set here is undone by
 * [stopLockTaskMode].
 */
@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PlayerPreferencesRepository,
    private val deviceRepository: DeviceRepository,
    @IoScope private val ioScope: CoroutineScope,
) {
    private val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    val adminComponent = ComponentName(context, LockTaskDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(context.packageName)

    fun lockTaskState(): KioskState = when (activityManager.lockTaskModeState) {
        ActivityManager.LOCK_TASK_MODE_LOCKED -> KioskState.LOCKED
        ActivityManager.LOCK_TASK_MODE_PINNED -> KioskState.PINNED
        else -> KioskState.OFF
    }

    /** Must be called while [activity] is in front. */
    fun startLockTaskMode(activity: Activity) {
        if (isDeviceOwner()) setKioskPolicies(enable = true)
        runCatching { activity.startLockTask() }
            .onSuccess { Timber.i("Lock task started (device owner: %b)", isDeviceOwner()) }
            .onFailure { exception -> Timber.e(exception, "startLockTask failed") }
    }

    fun stopLockTaskMode(activity: Activity) {
        if (isDeviceOwner()) setKioskPolicies(enable = false)
        runCatching { activity.stopLockTask() }
            .onSuccess { Timber.i("Lock task stopped") }
            .onFailure { exception -> Timber.e(exception, "stopLockTask failed") }
    }

    /** Applies [isKioskWanted] to [activity], doing nothing when it already holds. */
    fun applyKiosk(activity: Activity, isKioskWanted: Boolean) {
        val currentState = lockTaskState()
        when {
            isKioskWanted && currentState == KioskState.OFF -> startLockTaskMode(activity)
            !isKioskWanted && currentState != KioskState.OFF -> stopLockTaskMode(activity)
        }
    }

    /**
     * Opens the player again when the kiosk should be locked but is not: after the process restarted on its own (a
     * crash or an out-of-memory kill opens no activity) or after lock task ended without being asked to.
     */
    fun relaunchKioskIfUnlocked(reason: String) {
        ioScope.launch {
            val isKioskWanted = preferencesRepository.isKioskEnabled.first() &&
                !preferencesRepository.isKioskSuppressed.first()
            if (!isKioskWanted || !isDeviceOwner() || lockTaskState() != KioskState.OFF) return@launch
            Timber.i("Relaunching the kiosk (%s)", reason)
            context.startActivity(mainActivityIntent(context))
        }
    }

    /**
     * QR enrollment finished: the tablet locks from its first start, before it is added to a screen, and connects to
     * the add-on named in the code's admin extras without searching for it.
     */
    fun onProvisioned(provisioningIntent: Intent) {
        val adminExtras = IntentCompat.getParcelableExtra(
            provisioningIntent,
            DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
            PersistableBundle::class.java,
        )
        val server = adminExtras?.getString(EXTRA_SERVER)?.let(::normalizeServerAddress)
        ioScope.launch {
            // Before the kiosk turns on, so the first lock already sees it.
            preferencesRepository.setBatteryPromptPending(true)
            preferencesRepository.setKioskEnabled(true)
            if (server != null) preferencesRepository.setServer(server)
        }
    }

    /**
     * Whether to show the battery optimization dialog before locking. It is offered only once after QR enrollment,
     * so a "Deny" does not keep the kiosk from locking.
     */
    suspend fun consumeBatteryPrompt(): Boolean {
        if (!preferencesRepository.isBatteryPromptPending.first()) return false
        preferencesRepository.setBatteryPromptPending(false)
        return isDeviceOwner() && !deviceRepository.isIgnoringBatteryOptimizations()
    }

    /**
     * Gives up being the device owner, so the tablet can be used normally again without a factory reset. Only offered
     * while unlocked, so there is no lock task to stop first.
     */
    fun removeDeviceOwner() {
        if (!isDeviceOwner()) return
        setKioskPolicies(enable = false)
        // Still the only API that drops device owner without a factory wipe; deprecated as "test-only".
        @Suppress("DEPRECATION")
        runCatching { devicePolicyManager.clearDeviceOwnerApp(context.packageName) }
            .onSuccess { Timber.i("Device owner removed") }
            .onFailure { exception -> Timber.e(exception, "clearDeviceOwnerApp failed") }
    }

    private fun setKioskPolicies(enable: Boolean) {
        try {
            KIOSK_USER_RESTRICTIONS.forEach { restriction -> setUserRestriction(
                restriction,
                enable,
            ) }
            devicePolicyManager.setStatusBarDisabled(adminComponent, enable)
            devicePolicyManager.setKeyguardDisabled(adminComponent, enable)
            setAsHomeApp(enable)
            if (enable) {
                devicePolicyManager.setLockTaskPackages(
                    adminComponent,
                    arrayOf(context.packageName),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    devicePolicyManager.setLockTaskFeatures(
                        adminComponent,
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                    )
                }
            } else {
                devicePolicyManager.setLockTaskPackages(adminComponent, emptyArray())
            }
        } catch (exception: SecurityException) {
            Timber.e(exception, "Failed to set kiosk policies. Is this app a device owner?")
        }
    }

    /**
     * The location-accuracy prompt is a Play Services screen. Lock task would block it, so that package is allowed
     * only while the prompt is up.
     */
    fun setLocationAccuracyDialogAllowed(isAllowed: Boolean) {
        if (!isDeviceOwner() || lockTaskState() != KioskState.LOCKED) return
        val packages = if (isAllowed) {
            arrayOf(context.packageName, GOOGLE_PLAY_SERVICES_PACKAGE)
        } else {
            arrayOf(context.packageName)
        }
        runCatching { devicePolicyManager.setLockTaskPackages(adminComponent, packages) }
            .onFailure { exception ->
                Timber.w(exception, "Could not update lock task packages for location accuracy")
            }
    }

    private fun setUserRestriction(restriction: String, isDisallowed: Boolean) {
        if (isDisallowed) {
            devicePolicyManager.addUserRestriction(adminComponent, restriction)
        } else {
            devicePolicyManager.clearUserRestriction(adminComponent, restriction)
        }
    }

    private fun setAsHomeApp(enable: Boolean) {
        if (enable) {
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            devicePolicyManager.addPersistentPreferredActivity(
                adminComponent,
                homeFilter,
                ComponentName(context, MainActivity::class.java),
            )
        } else {
            devicePolicyManager.clearPackagePersistentPreferredActivities(
                adminComponent,
                context.packageName,
            )
        }
    }

    companion object {
        /** The admin extra the add-on's enrollment code puts its `host:port` in. */
        private const val EXTRA_SERVER = "server"
        private const val GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms"

        fun mainActivityIntent(context: Context): Intent = Intent(
            context,
            MainActivity::class.java,
        ).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }
}
