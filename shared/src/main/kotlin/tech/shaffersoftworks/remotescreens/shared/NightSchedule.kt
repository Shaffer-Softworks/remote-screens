package tech.shaffersoftworks.remotescreens.shared

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

/** Whether the screen wears its dark theme at [time]: always in dark mode, else at night when it is set to. */
fun ScreenSettings.isDarkAt(time: LocalTime): Boolean = darkMode || (nightDark && isNightAt(time))

private fun parseTimeOrNull(text: String?): LocalTime? =
    text?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
