package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DarkThemeScheduleTest {
    @Test
    fun overnightScheduleIncludesStartAndExcludesEnd() {
        val schedule = DarkThemeSchedule(enabled = true)
        assertFalse(isScheduledDarkTheme(schedule, 21 * 60 + 59))
        assertTrue(isScheduledDarkTheme(schedule, 22 * 60))
        assertTrue(isScheduledDarkTheme(schedule, 23 * 60 + 59))
        assertTrue(isScheduledDarkTheme(schedule, 0))
        assertTrue(isScheduledDarkTheme(schedule, 7 * 60 - 1))
        assertFalse(isScheduledDarkTheme(schedule, 7 * 60))
    }

    @Test
    fun daytimeAndDisabledSchedulesUseTheirOwnInterval() {
        val daytime = DarkThemeSchedule(true, 8 * 60 + 30, 17 * 60 + 15)
        assertFalse(isScheduledDarkTheme(daytime, 8 * 60 + 29))
        assertTrue(isScheduledDarkTheme(daytime, 8 * 60 + 30))
        assertFalse(isScheduledDarkTheme(daytime, 17 * 60 + 15))
        assertFalse(isScheduledDarkTheme(daytime.copy(enabled = false), 12 * 60))
        assertFalse(isScheduledDarkTheme(DarkThemeSchedule(true, 300, 300), 300))
    }

    @Test
    fun scheduleOverridesManualModeButDisablingItRestoresManualMode() {
        assertTrue(resolveThemePreferenceState(AppThemeMode.LIGHT, DarkThemeStyle.AMOLED, false, true).useAmoledDarkTheme)
        assertFalse(resolveThemePreferenceState(AppThemeMode.DARK, DarkThemeStyle.DEFAULT, true, false).useDarkTheme)
        assertTrue(resolveThemePreferenceState(AppThemeMode.DARK, DarkThemeStyle.DEFAULT, false).useDarkTheme)
    }

    @Test
    fun invalidImportedTimesNormalizeToSafeValues() {
        assertEquals(DarkThemeSchedule(), normalizeDarkThemeSchedule(DarkThemeSchedule(true, 30, 30)))
        assertEquals(DarkThemeSchedule(true, 0, 1439), normalizeDarkThemeSchedule(DarkThemeSchedule(true, -1, 9999)))
        assertEquals("07:05", formatThemeScheduleTime(7 * 60 + 5))
    }
}
