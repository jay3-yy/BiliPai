package com.android.purebilibili.core.performance

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceHistoryTest {
    @Test
    fun `missing scene remains in the time series but not in the numeric ranking`() {
        val a = parsePerformanceHistory(historyReportFixture())
        val missing = a.copy(id = "missing", startedWallMs = 2000, scopes = emptyMap())
        val b = a.copy(id = "later", startedWallMs = 3000)
        val entries = listOf(a, missing, b)
        assertEquals(listOf(a.id, missing.id, b.id), comparablePerformanceHistory(entries, a).map { it.id })
        assertEquals(setOf(a.id, b.id), rankPerformanceHistory(entries, a, PERFORMANCE_SCOPE_ALL,
            PerformanceHistoryMetric.P95).map { it.id }.toSet())
    }

    @Test
    fun `small measured rates do not display as a real zero`() {
        val measurement = PerformanceHistoryMeasurement(78709, 11, null, 5, 186.2, 39315, 43)
        assertEquals("0.014 %", measurement.formatted(PerformanceHistoryMetric.JANK_RATE))
        assertEquals("0.000 %", measurement.copy(jankyFrames = 0).formatted(PerformanceHistoryMetric.JANK_RATE))
    }
    @Test
    fun `legacy long count remains unknown while a measured zero remains zero`() {
        val legacy = parsePerformanceHistory(historyReportFixture(schema = 4))
        assertNull(legacy.scopes.getValue(PERFORMANCE_SCOPE_ALL).longFrames100Ms)
        assertNull(legacy.scopes.getValue(PERFORMANCE_SCOPE_ALL).value(PerformanceHistoryMetric.LONG_RATE))
        val report = historyReportFixture().apply { getJSONObject("frames").put("longFrames100Ms", 0) }
        assertEquals(0.0, parsePerformanceHistory(report).scopes.getValue(PERFORMANCE_SCOPE_ALL).value(PerformanceHistoryMetric.LONG_RATE))
    }

    @Test
    fun `repeated return comparisons keep the worst p95 rather than averaging percentiles`() {
        val report = historyReportFixture()
        fun motion(p95: Int, frames: Int, complete: Boolean) = JSONObject()
            .put("fromPage", "video").put("toPage", "home").put("kind", "gesture_back").put("outcome", "settled")
            .put("completeMotionBoundaries", complete)
            .put("ui", JSONObject().put("frames", frames).put("jankyFrames", 1).put("longFrames100Ms", 1)
                .put("p95UiMsUpperBound", p95).put("maxUiMs", 150.0))
            .put("hwui", JSONObject().put("deadlineAvailableFrames", frames).put("missedDeadlineFrames", 2))
        report.getJSONArray("windows").getJSONObject(0).put("navigation", JSONArray()
            .put(motion(68, 50, true)).put(motion(15, 200, true)).put(motion(400, 2, false)))
        val measure = parsePerformanceHistory(report).scopes.getValue("navigation:video/home/gesture_back/settled")
        assertEquals(250L, measure.frames)
        assertEquals(68, measure.p95Ms)
        assertEquals(2L, measure.longFrames100Ms)
    }

    @Test
    fun `comparison permits app version changes but rejects changed sampling conditions`() {
        val a = parsePerformanceHistory(historyReportFixture())
        val b = parsePerformanceHistory(historyReportFixture(2000).apply { getJSONObject("environment").put("version", "0.8.2") })
        assertTrue(performanceComparisonIssues(a, b).isEmpty())
        assertFalse(performanceComparisonIssues(a, b.copy(conditions = b.conditions.copy(liquidGlass = true))).isEmpty())
        assertFalse(performanceComparisonIssues(a, b.copy(conditions = b.conditions.copy(device = "vivo V2309A"))).isEmpty())
        assertFalse(performanceComparisonIssues(a, b.copy(conditions = b.conditions.copy(schema = 4))).isEmpty())
        assertFalse(performanceComparisonIssues(a, b.copy(conditions = b.conditions.copy(panelRates = listOf(60, 120)))).isEmpty())
        assertFalse(performanceComparisonIssues(a, b.copy(conditions = b.conditions.copy(panelRates = emptyList()))).isEmpty())
        assertFalse(performanceComparisonIssues(a, a).isEmpty())
    }

    @Test
    fun `ranking normalizes long counts excludes checkpoints and preserves p95 overflow`() {
        val a = parsePerformanceHistory(historyReportFixture())
        val measure = a.scopes.getValue(PERFORMANCE_SCOPE_ALL)
        val b = a.copy(id = "b", scopes = mapOf(PERFORMANCE_SCOPE_ALL to measure.copy(frames = 1000, longFrames100Ms = 5)))
        val incomplete = b.copy(id = "unfinished", stopReason = "checkpoint_unfinished")
        val foreign = b.copy(id = "foreign", conditions = b.conditions.copy(uiStyle = "MIUIX"))
        assertEquals(listOf(a.id, b.id), rankPerformanceHistory(listOf(b, incomplete, foreign, a), a, PERFORMANCE_SCOPE_ALL,
            PerformanceHistoryMetric.LONG_RATE).map { it.id })
        val overflow = b.copy(id = "overflow", scopes = mapOf(PERFORMANCE_SCOPE_ALL to measure.copy(p95Ms = 501)))
        assertEquals(overflow.id, rankPerformanceHistory(listOf(a, overflow), a, PERFORMANCE_SCOPE_ALL, PerformanceHistoryMetric.P95).first().id)
        assertNull(overflow.scopes.getValue(PERFORMANCE_SCOPE_ALL).value(PerformanceHistoryMetric.P95))
        assertEquals(">500 ms", overflow.scopes.getValue(PERFORMANCE_SCOPE_ALL).formatted(PerformanceHistoryMetric.P95))
    }
}
