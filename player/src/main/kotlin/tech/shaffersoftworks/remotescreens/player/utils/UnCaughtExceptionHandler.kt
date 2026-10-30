package tech.shaffersoftworks.remotescreens.player.utils

import timber.log.Timber

@Suppress("TooGenericExceptionCaught")
class UnCaughtExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            Timber.e(e, "Uncaught exception handled in thread: ${t.name}")
        } catch (internalError: Throwable) {
            Timber.e(internalError, "Error within the custom UncaughtExceptionHandler itself.")
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }
}
