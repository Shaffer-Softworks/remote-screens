package dev.remotescreens.player.utils

import dev.remotescreens.shared.ScreenSettings
import java.time.LocalTime

/** Whether [time] falls in the night window of these settings. A window may wrap past midnight. */
fun ScreenSettings.isNightAt(time: LocalTime): Boolean {
    val start = parseTimeOrNull(nightStart)
    val end = parseTimeOrNull(nightEnd)
    return when {
        start == null || end == null -> false
        start <= end -> time >= start && time < end
        else -> time >= start || time < end
    }
}

private fun parseTimeOrNull(text: String?): LocalTime? =
    text?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
