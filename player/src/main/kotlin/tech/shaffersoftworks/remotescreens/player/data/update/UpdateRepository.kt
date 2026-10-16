package tech.shaffersoftworks.remotescreens.player.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.di.IoDispatcher
import tech.shaffersoftworks.remotescreens.player.receiver.InstallReceiver
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val UPDATE_FILE_PATH = "updates/remote-screens.apk"
private const val INSTALL_SESSION_NAME = "remote-screens"

/**
 * Installs a newer player app that the add-on offers, from the add-on itself.
 * On Android 12+ a same-signature self-update can skip the system confirm; the device owner can do that and
 * also start the install without a dialog in the player.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Downloads and commits the APK. When [silent] is true (device owner), Android 12+ is told not to ask;
     * older builds may still surface a confirm intent that [InstallReceiver] opens.
     */
    suspend fun install(server: String, path: String, token: String, silent: Boolean = false): Boolean =
        withContext(ioDispatcher) {
            val apkFile = File(context.cacheDir, UPDATE_FILE_PATH).apply { parentFile?.mkdirs() }
            try {
                val response = httpClient.get("http://$server$path?token=$token")
                if (!response.status.isSuccess()) return@withContext false
                response.bodyAsChannel().toInputStream().use { apkInputStream ->
                    apkFile.outputStream().use { apkOutputStream -> apkInputStream.copyTo(apkOutputStream) }
                }
                commitInstallSession(apkFile, silent)
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Timber.w("Update failed: %s", error.message)
                false
            } finally {
                apkFile.delete()
            }
        }

    @Throws(IOException::class)
    private fun commitInstallSession(apkFile: File, silent: Boolean) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // Same-signature self-update on Android 12+: skip the system confirm. [silent] is for callers that also skip
        // our own dialog (device owner); the flag still helps after a manual confirm on other tablets.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (silent) {
                Timber.i("Committing player update without a system confirm (Android 12+)")
            }
        }
        val sessionId = packageInstaller.createSession(sessionParams)
        packageInstaller.openSession(sessionId).use { installSession ->
            installSession.openWrite(INSTALL_SESSION_NAME, 0, apkFile.length()).use { apkOutputStream ->
                apkFile.inputStream().use { apkInputStream -> apkInputStream.copyTo(apkOutputStream) }
                installSession.fsync(apkOutputStream)
            }
            val statusIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            installSession.commit(statusIntent.intentSender)
        }
    }
}
