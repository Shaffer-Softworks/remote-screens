package tech.shaffersoftworks.remotescreens.player.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.shaffersoftworks.remotescreens.player.MainActivity

/** Opens the screen when the tablet starts, or after an update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
