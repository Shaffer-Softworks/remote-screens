package tech.shaffersoftworks.remotescreens.player

import android.app.Application
import tech.shaffersoftworks.remotescreens.player.data.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import tech.shaffersoftworks.remotescreens.player.data.diagnostics.AppLog
import tech.shaffersoftworks.remotescreens.player.data.kiosk.KioskManager
import tech.shaffersoftworks.remotescreens.player.utils.UnCaughtExceptionHandler
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PlayerApplication : Application() {
    @Inject
    lateinit var kioskManager: KioskManager

    @Inject
    lateinit var crashReporter: CrashReporter

    @Inject
    lateinit var appLog: AppLog

    override fun onCreate() {
        super.onCreate()
        initializeLogging()
        appLog.start()
        initializeUncaughtExceptionHandler()
        crashReporter.start()

        // Start grants in the background; device-policy callbacks must not block the first app window.
        kioskManager.grantRuntimePermissions()
        // A process restarted after a crash opens no activity, so nothing would lock the kiosk again.
        kioskManager.relaunchKioskIfUnlocked(reason = "process_start")
    }

    private fun initializeLogging() {
        Timber.plant(appLog)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initializeUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler() ?: return

        Thread.setDefaultUncaughtExceptionHandler(
            UnCaughtExceptionHandler(defaultHandler),
        )
    }
}
