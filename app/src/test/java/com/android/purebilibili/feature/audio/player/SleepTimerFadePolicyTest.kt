package com.android.purebilibili.feature.audio.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepTimerFadePolicyTest {

    @Test
    fun `resolveSleepTimerFadeWindowMs clamps to remaining time and respects toggle`() {
        assertEquals(0L, resolveSleepTimerFadeWindowMs(remainingMs = 60_000L, fadeEnabled = false))
        assertEquals(0L, resolveSleepTimerFadeWindowMs(remainingMs = 0L, fadeEnabled = true))
        assertEquals(12_000L, resolveSleepTimerFadeWindowMs(remainingMs = 60_000L, fadeEnabled = true))
        assertEquals(30_000L, resolveSleepTimerFadeWindowMs(remainingMs = 60_000L, fadeEnabled = true, configuredFadeMs = 30_000L))
        assertEquals(5_000L, resolveSleepTimerFadeWindowMs(remainingMs = 5_000L, fadeEnabled = true))
    }

    @Test
    fun `normalize and parse custom fade duration enforces 3 to 120 second bounds`() {
        assertEquals(3, normalizeSleepTimerFadeDurationSec(0))
        assertEquals(120, normalizeSleepTimerFadeDurationSec(500))
        assertEquals(20, normalizeSleepTimerFadeDurationSec(20))
        assertEquals(15, parseSleepTimerFadeDurationInput(" 15 "))
        assertEquals(null, parseSleepTimerFadeDurationInput(""))
        assertEquals(null, parseSleepTimerFadeDurationInput("2"))
        assertEquals(null, parseSleepTimerFadeDurationInput("121"))
        assertEquals(null, parseSleepTimerFadeDurationInput("abc"))
    }

    @Test
    fun `computeSleepTimerFadeVolumeFactor decreases monotonically from one to zero`() {
        assertEquals(1f, computeSleepTimerFadeVolumeFactor(0L, 12_000L), 0.0001f)
        assertEquals(0f, computeSleepTimerFadeVolumeFactor(12_000L, 12_000L), 0.0001f)
        assertEquals(0f, computeSleepTimerFadeVolumeFactor(15_000L, 12_000L), 0.0001f)

        var previous = 1.01f
        for (elapsed in 0L..12_000L step 1_000L) {
            val factor = computeSleepTimerFadeVolumeFactor(elapsed, 12_000L)
            assertTrue(factor <= previous, "Fade factor must be monotonically non-increasing")
            previous = factor
        }
    }

    @Test
    fun `isLateNightSleepSuggestionHour covers 22_30 through 04_59 across midnight`() {
        assertFalse(isLateNightSleepSuggestionHour(22, 29))
        assertTrue(isLateNightSleepSuggestionHour(22, 30))
        assertTrue(isLateNightSleepSuggestionHour(23, 59))
        assertTrue(isLateNightSleepSuggestionHour(0, 0))
        assertTrue(isLateNightSleepSuggestionHour(4, 59))
        assertFalse(isLateNightSleepSuggestionHour(5, 0))
        assertFalse(isLateNightSleepSuggestionHour(14, 0))
    }

    @Test
    fun `resolveSleepTimerWindDownMessage distinguishes late night and after current`() {
        assertEquals("本集已播完，晚安好梦", resolveSleepTimerWindDownMessage(wasAfterCurrent = true, isLateNight = true))
        assertEquals("晚安，今晚就先到这里啦", resolveSleepTimerWindDownMessage(wasAfterCurrent = false, isLateNight = true))
        assertEquals("当前内容已播完，已暂停播放", resolveSleepTimerWindDownMessage(wasAfterCurrent = true, isLateNight = false))
        assertEquals("定时结束，已轻柔暂停播放", resolveSleepTimerWindDownMessage(wasAfterCurrent = false, isLateNight = false))
    }
}
