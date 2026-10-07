package dev.remotescreens.player

import android.app.Application

class PlayerApp : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
}
