package tech.shaffersoftworks.remotescreens.player.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
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

/** Installs a newer player app that the add-on offers, from the add-on itself. Android asks the user to confirm. */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun install(server: String, path: String, token: String): Boolean = withContext(ioDispatcher) {
        val apkFile = File(context.cacheDir, UPDATE_FILE_PATH).apply { parentFile?.mkdirs() }
        try {
            val response = httpClient.get("http://$server$path?token=$token")
            if (!response.status.isSuccess()) return@withContext false
            response.bodyAsChannel().toInputStream().use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }
            commitInstallSession(apkFile)
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
    private fun commitInstallSession(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(sessionParams)
        packageInstaller.openSession(sessionId).use { session ->
            session.openWrite(INSTALL_SESSION_NAME, 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            val statusIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(statusIntent.intentSender)
        }
    }
}
