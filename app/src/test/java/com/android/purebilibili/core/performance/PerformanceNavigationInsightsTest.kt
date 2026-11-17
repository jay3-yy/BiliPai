package com.android.purebilibili.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceNavigationInsightsTest {
    @Test
    fun `single entry long frame remains visible without jank or a high p95`() {
        val tracker = PerformanceNavigationTracker("settings")
        tracker.pageChanged("appearance_settings", 1)
        tracker.motion(2, true, "push", null)
        tracker.motion(700_000_000, false, "push", null)
        repeat(86) { index ->
            val start = 1_000_000L + index * 5_000_000L
            tracker.uiFrame(start, start + 4_000_000, 4_000_000, false)
        }
        tracker.uiFrame(440_000_000, 574_819_692, 134_819_692, false)
        val first = PerformanceNavigationInsight(1, tracker.snapshot().single())
        val other = PerformanceNavigationTracker.Record(2, 500_000_000, "home", "video", "push").apply {
            ui.add(40_000_000, true)
        }
        val ranked = rankPerformanceNavigation(listOf(PerformanceNavigationInsight(2, other), first))
        assertEquals(first, ranked.first())
        assertEquals(4, first.record.ui.percentileUpperBoundMs(.95))
        assertEquals(1L, first.summaryRow().longFrames100Ms)
        val text = formatPerformanceNavigationSummary(ranked.map { it.summaryRow() })
        assertTrue(text.contains("设置 → 外观设置"))
        assertTrue(text.contains("≥100ms 长帧：1"))
        assertTrue(text.contains("边界不完整"))
    }
}
