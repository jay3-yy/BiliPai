package com.android.purebilibili.feature.settings

/** Minutes in the device's current local day. Disabled by default; ordinary theme mode is retained. */
data class DarkThemeSchedule(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
)

internal fun isScheduledDarkTheme(schedule: DarkThemeSchedule, minuteOfDay: Int): Boolean {
    val start = schedule.startMinute.coerceIn(0, 1439)
    val end = schedule.endMinute.coerceIn(0, 1439)
    val now = minuteOfDay.coerceIn(0, 1439)
    if (!schedule.enabled || start == end) return false
    return if (start < end) now >= start && now < end else now >= start || now < end
}

internal fun formatThemeScheduleTime(minute: Int): String =
    java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

internal fun normalizeDarkThemeSchedule(schedule: DarkThemeSchedule): DarkThemeSchedule {
    val start = schedule.startMinute.coerceIn(0, 1439)
    val end = schedule.endMinute.coerceIn(0, 1439)
    return if (start == end) DarkThemeSchedule() else schedule.copy(startMinute = start, endMinute = end)
}
