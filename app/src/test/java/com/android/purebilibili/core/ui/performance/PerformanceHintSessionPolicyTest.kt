package com.android.purebilibili.core.ui.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceHintSessionPolicyTest {

    @Test
    fun resolvesTargetFrameDurationFromDisplayRefreshRateAndSanitizesInvalidInput() {
        assertEquals(41_666_667L, resolveTargetFrameDurationNanos(24f))
        assertEquals(33_333_333L, resolveTargetFrameDurationNanos(30f))
        assertEquals(16_666_667L, resolveTargetFrameDurationNanos(60f))
        assertEquals(11_111_111L, resolveTargetFrameDurationNanos(90f))
        assertEquals(8_333_333L, resolveTargetFrameDurationNanos(120f))
        assertEquals(6_944_444L, resolveTargetFrameDurationNanos(144f))
        assertEquals(16_666_667L, resolveTargetFrameDurationNanos(0f))
        assertEquals(16_666_667L, resolveTargetFrameDurationNanos(Float.NaN))
        assertEquals(4_166_667L, resolveTargetFrameDurationNanos(500f))
    }

    @Test
    fun treatsNegativePreferredRateAsUnsupportedAndClampsZeroToMinimumFloor() {
        assertNull(resolveEffectiveAdpfUpdateRateNanos(-1L))
        assertEquals(MIN_ADPF_REPORT_INTERVAL_NANOS, resolveEffectiveAdpfUpdateRateNanos(0L))
        assertEquals(MIN_ADPF_REPORT_INTERVAL_NANOS, resolveEffectiveAdpfUpdateRateNanos(2_000_000L))
        assertEquals(16_000_000L, resolveEffectiveAdpfUpdateRateNanos(16_000_000L))
    }

    @Test
    fun quantizesObservedGlIntervalsIntoStableVideoCadenceBucketsAndIgnoresPauseGaps() {
        assertEquals(16_666_667L, resolveAdaptiveGlTargetFrameDurationNanos(16_500_000L))
        assertEquals(20_833_333L, resolveAdaptiveGlTargetFrameDurationNanos(21_000_000L))
        assertEquals(33_333_333L, resolveAdaptiveGlTargetFrameDurationNanos(33_400_000L))
        assertEquals(41_666_667L, resolveAdaptiveGlTargetFrameDurationNanos(41_700_000L))
        // Seek/pause gap (>66.7ms) falls back to default instead of stretching target indefinitely
        assertEquals(DEFAULT_ADPF_TARGET_FRAME_NANOS, resolveAdaptiveGlTargetFrameDurationNanos(500_000_000L))
        // Explicit video frame rate takes precedence
        assertEquals(41_666_667L, resolveAdaptiveGlTargetFrameDurationNanos(16_000_000L, explicitFrameRateHz = 24f))
    }

    @Test
    fun throttlesSamplesUsingExplicitHasReportedFlagEvenAtZeroOrNegativeClockOrigin() {
        assertFalse(
            shouldReportPerformanceHintSample(
                nowNanos = 0L,
                lastReportNanos = 0L,
                hasReported = false,
                preferredUpdateRateNanos = 16_000_000L,
                actualDurationNanos = 0L,
            )
        )
        assertTrue(
            shouldReportPerformanceHintSample(
                nowNanos = 0L,
                lastReportNanos = 0L,
                hasReported = false,
                preferredUpdateRateNanos = 16_000_000L,
                actualDurationNanos = 5_000_000L,
            )
        )
        assertFalse(
            shouldReportPerformanceHintSample(
                nowNanos = 10_000_000L,
                lastReportNanos = 0L,
                hasReported = true,
                preferredUpdateRateNanos = 16_000_000L,
                actualDurationNanos = 5_000_000L,
            )
        )
        assertTrue(
            shouldReportPerformanceHintSample(
                nowNanos = 16_000_000L,
                lastReportNanos = 0L,
                hasReported = true,
                preferredUpdateRateNanos = 16_000_000L,
                actualDurationNanos = 5_000_000L,
            )
        )
        // Negative monotonic clock timestamps still work accurately
        assertTrue(
            shouldReportPerformanceHintSample(
                nowNanos = -84_000_000L,
                lastReportNanos = -100_000_000L,
                hasReported = true,
                preferredUpdateRateNanos = 16_000_000L,
                actualDurationNanos = 5_000_000L,
            )
        )
        // Unsupported device (-1L) never reports after first check
        assertFalse(
            shouldReportPerformanceHintSample(
                nowNanos = 500_000_000L,
                lastReportNanos = 0L,
                hasReported = true,
                preferredUpdateRateNanos = -1L,
                actualDurationNanos = 5_000_000L,
            )
        )
    }
}
