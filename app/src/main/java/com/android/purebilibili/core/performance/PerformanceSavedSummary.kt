package com.android.purebilibili.core.performance

import org.json.JSONArray
import org.json.JSONObject

/** 直接用已保存的统计值生成展示文案，预览和导出共用；保留报告中的原始数值。 */
internal fun savedPerformanceSummary(report: JSONObject): String? {
    if (!report.has("analysis")) return null
    val analysis = report.getJSONObject("analysis")
    val pages = analysis.getJSONArray("pageRanking").objects().map { page ->
        val ui = page.getJSONObject("ui")
        PerformancePageSummaryRow(
            page.getInt("windowId"), page.getString("page"), ui.getLong("frames"), ui.getLong("jankyFrames"),
            if (ui.isNull("p95UiMsUpperBound")) null else ui.getInt("p95UiMsUpperBound"), ui.getDouble("maxUiMs"),
            page.getJSONArray("interactions").let { values -> (0 until values.length()).map { values.getString(it) } },
            if (ui.has("longFrames100Ms")) ui.getLong("longFrames100Ms") else null,
        )
    }
    val intervals = analysis.getJSONArray("problemIntervals").objects().map(::savedInterval)
    val markers = analysis.getJSONArray("markedWindows").objects().map { item ->
        PerformanceMarkerInsight(
            PerformanceMarkerInput(item.getDouble("markerOffsetMs"), item.getDouble("reviewFromOffsetMs"),
                if (item.isNull("markerWindowId")) null else item.getInt("markerWindowId")),
            item.getJSONArray("overlappingIntervals").objects().map(::savedInterval),
        )
    }
    val navigation = analysis.optJSONArray("navigationRanking")?.objects()?.map { item ->
        val ui = item.getJSONObject("ui")
        PerformanceNavigationSummaryRow(
            item.getInt("windowId"), item.getLong("id"), item.getString("fromPage"), item.getString("toPage"),
            item.getString("kind"), item.getString("outcome"), item.getBoolean("completeMotionBoundaries"),
            ui.getLong("frames"), if (ui.isNull("longFrames100Ms")) null else ui.getLong("longFrames100Ms"),
            if (ui.isNull("p95UiMsUpperBound")) null else ui.getInt("p95UiMsUpperBound"),
            if (ui.isNull("maxUiMs")) null else ui.getDouble("maxUiMs"),
        )
    }
    return formatPerformanceSummary(pages, intervals, markers) +
        (navigation?.let(::formatPerformanceNavigationSummary) ?: "")
}

private fun savedInterval(item: JSONObject): PerformanceIntervalInsight {
    val deadlineFrames = item.getLong("hwuiDeadlineFrames")
    return PerformanceIntervalInsight(
        item.getDouble("startOffsetMs"), item.getDouble("endOffsetMs"),
        if (item.isNull("windowIdAtEnd")) null else item.getInt("windowIdAtEnd"),
        if (item.isNull("pageAtEnd")) null else item.getString("pageAtEnd"),
        item.getLong("uiFrames"), item.getLong("jankyUiFrames"), item.getDouble("cpuPercentOneCore"),
        deadlineFrames, if (deadlineFrames == 0L) 0 else item.getLong("hwuiMissedDeadlines"),
        if (item.isNull("longUiFrames100Ms")) null else item.getLong("longUiFrames100Ms"),
    )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
