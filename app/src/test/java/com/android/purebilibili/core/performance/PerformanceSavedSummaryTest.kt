package com.android.purebilibili.core.performance

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PerformanceSavedSummaryTest {
    @Test
    fun `saved summary preserves unflagged long frames and single navigation details`() {
        val ui = JSONObject().put("frames", 87).put("jankyFrames", 0).put("longFrames100Ms", 1)
            .put("p95UiMsUpperBound", 4).put("maxUiMs", 134.819692)
        val navigation = JSONObject().put("windowId", 1).put("id", 2)
            .put("fromPage", "settings").put("toPage", "appearance_settings")
            .put("kind", "push").put("outcome", "settled").put("completeMotionBoundaries", true)
            .put("ui", ui)
        val report = JSONObject().put("schema", 5).put("analysis", JSONObject()
            .put("pageRanking", JSONArray().put(JSONObject().put("windowId", 1).put("page", "appearance_settings")
                .put("interactions", JSONArray().put("transition")).put("ui", ui)))
            .put("problemIntervals", JSONArray()).put("markedWindows", JSONArray())
            .put("navigationRanking", JSONArray().put(navigation)))
        val text = savedPerformanceSummary(report)!!
        assertTrue(text.contains("≥100ms 长帧：1"))
        assertTrue(text.contains("设置 → 外观设置"))
        assertTrue(text.contains("边界完整"))
        assertTrue(text.contains("134.8ms"))
        assertEquals(0L, ui.getLong("jankyFrames"))
    }

    @Test
    fun `saved report uses recorded statistics with current wording without modifying stored text`() {
        val analysis = JSONObject().put("summaryText", "先前保存的摘要文本")
            .put("pageRanking", JSONArray().put(JSONObject().put("windowId", 1).put("page", "home")
                .put("interactions", JSONArray().put("scroll"))
                .put("ui", JSONObject().put("frames", 20).put("jankyFrames", 3)
                    .put("p95UiMsUpperBound", 40).put("maxUiMs", 75.0))))
            .put("problemIntervals", JSONArray()).put("markedWindows", JSONArray())
        val report = JSONObject().put("analysis", analysis)
        val text = savedPerformanceSummary(report)!!
        assertTrue(text.contains("首页"))
        assertTrue(text.contains("3/20"))
        assertTrue(text.contains("15.0%"))
        assertTrue(text.contains("≤40ms"))
        assertNotEquals(analysis.getString("summaryText"), text)
        assertEquals("先前保存的摘要文本", analysis.getString("summaryText"))
        assertEquals(3L, analysis.getJSONArray("pageRanking").getJSONObject(0).getJSONObject("ui").getLong("jankyFrames"))
    }

    @Test
    fun `uncollected hardware timing is shown alongside recorded UI jank`() {
        val interval = JSONObject().put("startOffsetMs", 0.0).put("endOffsetMs", 5_000.0)
            .put("windowIdAtEnd", JSONObject.NULL).put("pageAtEnd", JSONObject.NULL)
            .put("uiFrames", 20).put("jankyUiFrames", 1).put("cpuPercentOneCore", 20.0)
            .put("hwuiDeadlineFrames", 0).put("hwuiMissedDeadlines", JSONObject.NULL)
        val report = JSONObject().put("analysis", JSONObject()
            .put("pageRanking", JSONArray()).put("problemIntervals", JSONArray().put(interval))
            .put("markedWindows", JSONArray()))
        val text = savedPerformanceSummary(report)!!
        assertTrue(text.contains("界面卡顿：1/20 帧"))
        assertTrue(text.contains("渲染超期（HWUI）：未采集"))
        assertFalse(text.contains("0/0"))
    }
}
