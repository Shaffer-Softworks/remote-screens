package tech.shaffersoftworks.remotescreens.player.data.device.assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.shaffersoftworks.remotescreens.player.R
import javax.inject.Inject

private const val CHANNEL_ID = "assist"
private const val NOTIFICATION_ID = 8102

/** Holds the microphone while the panel is off. Some panels still drop it; that limit is documented. */
class AssistListenService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.assist_listening), NotificationManager.IMPORTANCE_LOW),
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.assist_listening))
            .setSmallIcon(R.drawable.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }
}

class AndroidAssistForeground @Inject constructor(
    @ApplicationContext private val context: Context,
) : AssistForeground {
    override fun setListeningWhileScreenOff(isEnabled: Boolean) {
        val intent = Intent(context, AssistListenService::class.java)

        if (isEnabled) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }
}
