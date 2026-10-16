package tech.shaffersoftworks.remotescreens.player.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import androidx.core.content.IntentCompat
import tech.shaffersoftworks.remotescreens.player.MainActivity
import tech.shaffersoftworks.remotescreens.player.R
import timber.log.Timber

/**
 * Hears how an update install went. On Android 12+ same-signature self-updates usually succeed without a prompt;
 * older builds (or when the platform still asks) send [PackageInstaller.STATUS_PENDING_USER_ACTION] and we open
 * the confirm screen.
 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // BootReceiver also hears MY_PACKAGE_REPLACED; relaunch here too so a kiosk does not sit dark.
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            else -> {
                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Timber.w("Update not installed (%d): %s", status, statusMessage)
                Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_LONG).show()
            }
        }
    }
}
