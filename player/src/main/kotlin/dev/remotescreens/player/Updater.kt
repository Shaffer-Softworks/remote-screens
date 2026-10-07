package dev.remotescreens.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Installs a newer player app that the add-on offers, from the add-on itself. Android asks the user to confirm. */
class Updater(private val context: Context, private val http: HttpClient) {
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun askPermission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun install(server: String, path: String, token: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "updates/remote-screens.apk").apply { parentFile?.mkdirs() }
        try {
            val r = http.get("http://$server$path?token=$token")
            if (!r.status.isSuccess()) return@withContext false
            r.bodyAsChannel().toInputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("remote-screens", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                val intent = PendingIntent.getBroadcast(context, id, Intent("dev.remotescreens.player.INSTALLED").setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(intent.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.w("Updater", "Update failed: ${e.message}")
            false
        } finally {
            file.delete()
        }
    }
}
