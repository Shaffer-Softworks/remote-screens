package tech.shaffersoftworks.remotescreens.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PlayerApplication : Application() {
    @Inject
    lateinit var kioskManager: KioskManager

    override fun onCreate() {
        super.onCreate()
        initializeLogging()
        // A process restarted after a crash opens no activity, so nothing would lock the kiosk again.
        kioskManager.relaunchKioskIfUnlocked(reason = "process_start")
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
