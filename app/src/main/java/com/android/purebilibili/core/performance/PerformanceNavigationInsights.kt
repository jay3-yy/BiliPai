package com.android.purebilibili.core.performance

import java.util.Locale

internal data class PerformanceNavigationInsight(val windowId: Int, val record: PerformanceNavigationTracker.Record)

/** 单次运动独立排序，避免长时间播放把进出详情的长帧稀释到全局百分位之外。 */
internal fun rankPerformanceNavigation(entries: List<PerformanceNavigationInsight>): List<PerformanceNavigationInsight> =
    entries.filter { it.record.ui.frames > 0 || it.record.hwuiFrames > 0 }
        .sortedWith(compareByDescending<PerformanceNavigationInsight> { it.record.ui.longFrames100Ms }
            .thenByDescending { it.record.ui.maxDurationNanos }
            .thenByDescending { it.record.missedDeadlineFrames }
            .thenBy { it.windowId }.thenBy { it.record.id })
        .take(10)

internal data class PerformanceNavigationSummaryRow(
    val windowId: Int, val id: Long, val fromPage: String, val toPage: String,
    val kind: String, val outcome: String, val complete: Boolean,
    val frames: Long, val longFrames100Ms: Long?, val p95MsUpperBound: Int?, val maxUiMs: Double?,
)

internal fun PerformanceNavigationInsight.summaryRow() = record.let {
    PerformanceNavigationSummaryRow(windowId, it.id, it.fromPage, it.toPage, it.kind, it.outcome,
        it.startReason == "motion_boundary" && it.outcome in setOf("settled", "cancelled"),
        it.ui.frames, it.ui.longFrames100Ms, it.ui.percentileUpperBoundMs(.95),
        if (it.ui.frames == 0L) null else it.ui.maxDurationNanos / 1_000_000.0)
}

internal fun formatPerformanceNavigationSummary(rows: List<PerformanceNavigationSummaryRow>): String = buildString {
    appendLine()
    appendLine("单次导航转场（按长帧与最长 UI 帧排序）")
    appendLine("运动时长包含手势停留，不等同于页面加载耗时；边界不完整的记录不能直接比较整段转场。")
    if (rows.isEmpty()) appendLine("本次未采集到带帧数据的导航转场。")
    rows.forEachIndexed { index, row ->
        appendLine("${index + 1}. ${performancePageLabel(row.fromPage)} → ${performancePageLabel(row.toPage)} · 窗口 ${row.windowId} · 转场 ${row.id}")
        appendLine("${row.kind} · ${row.outcome} · ${if (row.complete) "边界完整" else "边界不完整"}")
        if (row.frames == 0L) appendLine("UI 帧：未采集")
        else {
            appendLine("UI 帧：${row.frames}；≥100ms 长帧：${row.longFrames100Ms ?: "未知"}")
            val p95 = when (row.p95MsUpperBound) {
                null -> "未采集"
                501 -> ">500ms"
                else -> "≤${row.p95MsUpperBound}ms"
            }
            appendLine("95% 的帧耗时：$p95；最长 ${String.format(Locale.ROOT, "%.1f", row.maxUiMs)}ms")
        }
    }
}
