package com.android.purebilibili.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterLiveTracePolicyTest {

    @Test
    fun sanitizeLiveTraceStage_collapsesWhitespaceAndLimitsLength() {
        val sanitized = sanitizeLiveTraceStage("  player   prepare   success  ")
        assertEquals("player_prepare_success", sanitized)

        val longInput = "a".repeat(120)
        assertEquals(80, sanitizeLiveTraceStage(longInput).length)
    }

    @Test
    fun shouldUpdateLiveTraceStage_onlyWhenStageChangesAndNotBlank() {
        assertFalse(shouldUpdateLiveTraceStage(lastStage = "prepare", nextStage = "prepare"))
        assertFalse(shouldUpdateLiveTraceStage(lastStage = "prepare", nextStage = "  "))
        assertTrue(shouldUpdateLiveTraceStage(lastStage = "prepare", nextStage = "playing"))
    }

    @Test
    fun liveSessionDurationMs_neverReturnsNegative() {
        assertEquals(0L, liveSessionDurationMs(nowElapsedMs = 100L, sessionStartElapsedMs = 120L))
        assertEquals(350L, liveSessionDurationMs(nowElapsedMs = 470L, sessionStartElapsedMs = 120L))
    }

    @Test
    fun shouldWriteCrashCustomKey_skipsRedundantWrites() {
        assertFalse(shouldWriteCrashCustomKey(previousValue = "same", nextValue = "same"))
        assertFalse(shouldWriteCrashCustomKey(previousValue = 12L, nextValue = 12L))
        assertTrue(shouldWriteCrashCustomKey(previousValue = "old", nextValue = "new"))
        assertTrue(shouldWriteCrashCustomKey(previousValue = null, nextValue = "first"))
    }

    @Test
    fun sensitiveCrashFieldsAndUserIdAreBlocked() {
        assertTrue(isSensitiveCrashCustomKey("video_bvid"))
        assertTrue(isSensitiveCrashCustomKey("danmaku_cid"))
        assertTrue(isSensitiveCrashCustomKey("live_room_id"))
        assertTrue(isSensitiveCrashCustomKey("live_room_title"))
        assertTrue(isSensitiveCrashCustomKey("live_anchor_name"))
        assertFalse(isSensitiveCrashCustomKey("video_error_type"))
        assertFalse(isSensitiveCrashCustomKey("live_last_stage"))

        assertEquals("", resolveCrashlyticsUserId(mid = 123456L))
        assertEquals("", resolveCrashlyticsUserId(mid = null))
    }
}
