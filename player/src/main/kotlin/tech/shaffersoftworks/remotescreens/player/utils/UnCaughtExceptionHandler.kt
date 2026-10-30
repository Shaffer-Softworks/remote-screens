package tech.shaffersoftworks.remotescreens.player.utils

import android.content.Context
import android.content.Intent
import tech.shaffersoftworks.remotescreens.player.MainActivity
import timber.log.Timber

class UnCaughtExceptionHandler(
    val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            Timber.e(e, "Uncaught exception handled in thread: ${t.name}")

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (internalError: Throwable) {
            Timber.e(internalError, "Error within the custom UncaughtExceptionHandler itself.")
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }
}
