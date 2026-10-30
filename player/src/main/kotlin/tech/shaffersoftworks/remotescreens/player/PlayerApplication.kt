package tech.shaffersoftworks.remotescreens.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import tech.shaffersoftworks.remotescreens.player.utils.UnCaughtExceptionHandler
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PlayerApplication : Application() {
    @Inject
    lateinit var kioskManager: KioskManager

    override fun onCreate() {
        super.onCreate()
        initializeLogging()
        initializeUncaughtExceptionHandler()

        // Before the player connects, so intercom and sensors do not wait on a permission dialog.
        kioskManager.grantRuntimePermissions()
        // A process restarted after a crash opens no activity, so nothing would lock the kiosk again.
        kioskManager.relaunchKioskIfUnlocked(reason = "process_start")
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initializeUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler() ?: return

        Thread.setDefaultUncaughtExceptionHandler(
            UnCaughtExceptionHandler(this, defaultHandler)
        )
    }
}
