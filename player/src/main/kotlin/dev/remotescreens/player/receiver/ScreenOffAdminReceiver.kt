package dev.remotescreens.player.receiver

import android.app.admin.DeviceAdminReceiver

/** Device admin with the one policy to lock the tablet, which is how an app turns the screen off. */
class ScreenOffAdminReceiver : DeviceAdminReceiver()
