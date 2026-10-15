package tech.shaffersoftworks.remotescreens.player.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import tech.shaffersoftworks.remotescreens.player.data.device.DisplayInfo

private const val PERCENT = 100f
private const val MIN_WINDOW_BRIGHTNESS = 0.01f
private const val MAX_WINDOW_BRIGHTNESS = 1f
private const val ORIENTATION_PORTRAIT = 1
private const val ORIENTATION_LANDSCAPE = 2

/** A wall screen has no status or navigation bar; a swipe from the edge shows them for a moment. */
fun Activity.hideSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** Standby can lock the tablet to turn the screen off; waking up then shows the screen, not the lock screen. */
fun Activity.showOverLockScreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
}

fun Activity.setWindowBrightness(brightnessPercent: Int) {
    window.attributes = window.attributes.apply {
        screenBrightness = (brightnessPercent.coerceAtLeast(1) / PERCENT)
            .coerceIn(MIN_WINDOW_BRIGHTNESS, MAX_WINDOW_BRIGHTNESS)
    }
}

/** 0 follows the device, 1 locks portrait, 2 locks landscape (see `ScreenSettings.orientation`). */
fun Activity.applyScreenOrientation(orientationSetting: Int) {
    requestedOrientation = when (orientationSetting) {
        ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }
}

/** The size of the whole window in dp, without the hidden system bars. */
fun Activity.measureDisplay(): DisplayInfo {
    val displayMetrics = resources.displayMetrics
    val (widthPx, heightPx) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let { bounds -> bounds.width() to bounds.height() }
    } else {
        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(realMetrics)
        realMetrics.widthPixels to realMetrics.heightPixels
    }
    val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return DisplayInfo(
        widthDp = (widthPx / displayMetrics.density).toInt(),
        heightDp = (heightPx / displayMetrics.density).toInt(),
        density = displayMetrics.density,
        isSystemDark = nightModeFlags == Configuration.UI_MODE_NIGHT_YES,
    )
}
