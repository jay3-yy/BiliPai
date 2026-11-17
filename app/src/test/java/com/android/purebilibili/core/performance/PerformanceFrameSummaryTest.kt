package com.android.purebilibili.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PerformanceFrameSummaryTest {
    @Test
    fun `empty session has unknown percentiles rather than zero duration`() {
        val summary = PerformanceFrameSummary()
        assertNull(summary.percentileUpperBoundMs(.95))
        assertEquals(0L, summary.frames)
    }

    @Test
    fun `percentiles preserve tail latency and jank uses reported classification`() {
        val summary = PerformanceFrameSummary()
        repeat(94) { summary.add(8_500_000, false) }
        repeat(5) { summary.add(33_100_000, true) }
        summary.add(200_000_000, true)
        assertEquals(100L, summary.frames)
        assertEquals(6L, summary.jankyFrames)
        assertEquals(9, summary.percentileUpperBoundMs(.50))
        assertEquals(34, summary.percentileUpperBoundMs(.95))
        assertEquals(34, summary.percentileUpperBoundMs(.99))
        assertEquals(200_000_000L, summary.maxDurationNanos)
    }

    @Test
    fun `report snapshot stays unchanged while new frames arrive`() {
        val live = PerformanceFrameSummary()
        live.add(10_000_000, false)
        val report = live.snapshot()
        live.add(100_000_000, true)
        assertEquals(1L, report.frames)
        assertEquals(0L, report.jankyFrames)
        assertEquals(10_000_000L, report.totalDurationNanos)
        assertEquals(10, report.percentileUpperBoundMs(.95))
        assertEquals(2L, live.frames)
        assertEquals(0L, report.longFrames100Ms)
        assertEquals(1L, live.longFrames100Ms)
    }

    @Test
    fun `long frame count has an inclusive boundary independent from jank classification and merges`() {
        val summary = PerformanceFrameSummary().apply {
            add(99_999_999, true)
            add(100_000_000, false)
        }
        val merged = PerformanceFrameSummary().apply { merge(summary) }
        assertEquals(2L, merged.frames)
        assertEquals(1L, merged.jankyFrames)
        assertEquals(1L, merged.longFrames100Ms)
    }

    @Test
    fun `very slow frames retain maximum and use explicit percentile overflow bucket`() {
        val summary = PerformanceFrameSummary()
        summary.add(2_000_000_000, true)
        assertEquals(501, summary.percentileUpperBoundMs(.99))
        assertEquals(2_000_000_000L, summary.maxDurationNanos)
    }
}
