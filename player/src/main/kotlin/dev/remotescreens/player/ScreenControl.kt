package dev.remotescreens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import dev.remotescreens.shared.ScreenSettings
import java.time.LocalTime

/**
 * What a wall screen does by itself: stay on, dim after a while without a touch, follow a night schedule, go back
 * to the home page. The settings come from the add-on (the editor or the Home Assistant integration).
 */
class ScreenControl(
    private val activity: Activity,
    /** A black view over everything, shown when standby brightness is 0. */
    private val blackout: View,
    private val goHome: () -> Unit,
    private val awakeChanged: (Boolean) -> Unit,
) {
    var settings = ScreenSettings()
        private set
    var awake = true
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val sleep = Runnable { setAwake(false) }
    private val home = Runnable { goHome() }
    private val tick = object : Runnable {
        override fun run() {
            applyBrightness()
            handler.postDelayed(this, 60_000)
        }
    }

    fun start() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        handler.post(tick)
        touched()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
    }

    fun apply(s: ScreenSettings) {
        settings = s
        activity.requestedOrientation = when (s.orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        applyBrightness()
        touched()
    }

    /** Any touch: wake up, and start the standby and back-to-home clocks again. */
    fun touched() {
        if (!awake) setAwake(true)
        handler.removeCallbacks(sleep)
        handler.removeCallbacks(home)
        if (settings.standbyMinutes > 0) handler.postDelayed(sleep, settings.standbyMinutes * 60_000L)
        if (settings.backToHomeSeconds > 0) handler.postDelayed(home, settings.backToHomeSeconds * 1000L)
    }

    fun wake(on: Boolean = true) {
        if (on) touched() else setAwake(false)
    }

    private fun setAwake(on: Boolean) {
        if (awake == on) return
        awake = on
        applyBrightness()
        awakeChanged(on)
    }

    fun night(now: LocalTime = LocalTime.now()): Boolean {
        val start = settings.nightStart?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return false
        val end = settings.nightEnd?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return false
        return if (start <= end) now >= start && now < end else now >= start || now < end
    }

    private fun applyBrightness() {
        val percent = when {
            !awake -> settings.standbyBrightness
            night() -> settings.nightBrightness
            else -> settings.brightness
        }
        blackout.visibility = if (!awake && percent == 0) View.VISIBLE else View.GONE
        val lp = activity.window.attributes
        lp.screenBrightness = (percent.coerceAtLeast(1) / 100f).coerceIn(0.01f, 1f)
        activity.window.attributes = lp
    }

    @Suppress("DEPRECATION")
    fun immersive() {
        val w = activity.window
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            w.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            w.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }
}
