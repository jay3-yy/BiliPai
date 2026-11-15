package com.android.purebilibili.core.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceReportInsightsTest {
    private fun frames(good: Int, janky: Int, slowNs: Long = 40_000_000) = PerformanceFrameSummary().apply {
        repeat(good) { add(8_000_000, false) }
        repeat(janky) { add(slowNs, true) }
    }

    @Test
    fun `page ranking includes idle jank without mixing windows`() {
        val scenes = listOf(
            PerformanceSceneInput(1, "home", "scroll", frames(10, 2)),
            PerformanceSceneInput(1, "home", "transition", frames(5, 3)),
            PerformanceSceneInput(2, "home", "scroll", frames(0, 1)),
            PerformanceSceneInput(1, "settings", "idle", frames(0, 20)),
        )
        val result = buildPerformanceReportInsights(scenes, emptyList(), emptyList())
        assertEquals(listOf("settings", "home", "home"), result.pages.map { it.page })
        assertEquals(listOf(1, 1, 2), result.pages.map { it.windowId })
        val home = result.pages[1]
        assertEquals(20L, home.frames.frames)
        assertEquals(5L, home.frames.jankyFrames)
        assertEquals(40, home.frames.percentileUpperBoundMs(.95))
        assertEquals(2L, scenes.first().frames.jankyFrames)
    }

    @Test
    fun `long frame remains ranked when system deadline reports no jank`() {
        val video = PerformanceFrameSummary().apply {
            repeat(1000) { add(3_000_000, false) }
            add(186_151_614, false)
        }
        val interval = PerformanceIntervalInsight(0.0, 5_000.0, 1, "video", 1001, 0, 90.0, 1001, 0,
            longFrames100Ms = 1)
        val result = buildPerformanceReportInsights(
            listOf(PerformanceSceneInput(1, "video", "idle", video)), listOf(interval), emptyList())
        assertEquals("video", result.pages.single().page)
        assertEquals(0L, result.pages.single().frames.jankyFrames)
        assertEquals(1L, result.pages.single().frames.longFrames100Ms)
        assertEquals(listOf(interval), result.intervals)
        assertTrue(result.summaryText.contains("≥100ms 长帧：1 帧"))
        assertTrue(result.summaryText.contains("页面停留"))
    }

    @Test
    fun `hardware missed deadline interval remains visible when UI reports no jank`() {
        val interval = PerformanceIntervalInsight(0.0, 5_000.0, 1, "home", 40, 0, 130.0, 40, 4)
        val result = buildPerformanceReportInsights(emptyList(), listOf(interval), emptyList())
        assertEquals(listOf(interval), result.intervals)
        assertTrue(result.summaryText.contains("4/40"))
    }

    @Test
    fun `markers overlap full intervals but exclude intervals that only touch the edge`() {
        fun interval(start: Double, end: Double) = PerformanceIntervalInsight(start, end, 1, "home", 20, 1, 50.0, 0, 0)
        val before = interval(0.0, 5_000.0)
        val inside = interval(5_000.0, 10_000.0)
        val after = interval(10_000.0, 15_000.0)
        val result = buildPerformanceReportInsights(emptyList(), listOf(before, inside, after),
            listOf(PerformanceMarkerInput(10_000.0, 5_000.0, 1), PerformanceMarkerInput(30_000.0, 20_000.0, 1)))
        assertEquals(listOf(inside), result.markers[0].intervals)
        assertTrue(result.markers[1].intervals.isEmpty())
        assertTrue(result.summaryText.contains("该时段没有采样记录"))
    }

    @Test
    fun `summary shows latency overflow and identifies an empty collection`() {
        val scene = PerformanceSceneInput(1, "home", "scroll", frames(0, 1, 900_000_000))
        assertTrue(buildPerformanceReportInsights(listOf(scene), emptyList(), emptyList()).summaryText.contains(">500ms"))
        val empty = buildPerformanceReportInsights(emptyList(), emptyList(), emptyList())
        assertTrue(empty.pages.isEmpty())
        assertTrue(empty.summaryText.contains("本次未记录到卡顿页面"))
        assertTrue(empty.summaryText.contains("本次未记录到卡顿采样时段"))
    }
}
