package dev.remotescreens.player

import android.content.Context
import java.util.UUID

/** What the player remembers between starts: who it is, where the add-on is, and the token it was given. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    val deviceId: String = p.getString("device_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }

    /** `host:port` of the add-on, found over mDNS or typed in. */
    var server: String?
        get() = p.getString("server", null)
        set(v) = p.edit().putString("server", v).apply()

    var token: String?
        get() = p.getString("token", null)
        set(v) = p.edit().putString("token", v).apply()

    var screenName: String?
        get() = p.getString("screen_name", null)
        set(v) = p.edit().putString("screen_name", v).apply()

    fun forget() {
        p.edit().remove("token").remove("screen_name").apply()
    }
}
