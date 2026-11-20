package com.android.purebilibili.core.performance

import java.util.Locale

internal data class PerformanceSceneInput(
    val windowId: Int, val page: String, val interaction: String, val frames: PerformanceFrameSummary,
)
internal data class PerformancePageInsight(
    val windowId: Int, val page: String, val frames: PerformanceFrameSummary, val interactions: List<String>,
)
internal data class PerformanceIntervalInsight(
    val startOffsetMs: Double, val endOffsetMs: Double, val windowIdAtEnd: Int?, val pageAtEnd: String?,
    val frames: Long, val jankyFrames: Long, val cpuPercentOneCore: Double,
    val hwuiDeadlineFrames: Long, val hwuiMissedDeadlines: Long,
    val longFrames100Ms: Long? = null,
)
internal data class PerformanceMarkerInput(val offsetMs: Double, val reviewFromMs: Double, val windowId: Int?)
internal data class PerformanceMarkerInsight(val marker: PerformanceMarkerInput, val intervals: List<PerformanceIntervalInsight>)
internal data class PerformanceReportInsights(
    val pages: List<PerformancePageInsight>, val intervals: List<PerformanceIntervalInsight>,
    val markers: List<PerformanceMarkerInsight>, val summaryText: String,
)

/** 按窗口、页面和采样时段汇总报告。 */
internal fun buildPerformanceReportInsights(
    scenes: List<PerformanceSceneInput>, intervals: List<PerformanceIntervalInsight>, markers: List<PerformanceMarkerInput>,
): PerformanceReportInsights {
    val pages = scenes
        .groupBy { it.windowId to it.page }
        .map { (identity, entries) ->
            val total = PerformanceFrameSummary()
            entries.forEach { total.merge(it.frames) }
            PerformancePageInsight(identity.first, identity.second, total, entries.map { it.interaction }.distinct().sorted())
        }
        .filter { it.frames.jankyFrames > 0 || it.frames.longFrames100Ms > 0 }
        .sortedWith(compareByDescending<PerformancePageInsight> { it.frames.longFrames100Ms }
            .thenByDescending { it.frames.jankyFrames }
            .thenByDescending { it.frames.jankyFrames.toDouble() / it.frames.frames }
            .thenByDescending { it.frames.maxDurationNanos }
            .thenBy { it.windowId }.thenBy { it.page })
        .take(10)
    val problemIntervals = intervals.filter { it.jankyFrames > 0 || it.hwuiMissedDeadlines > 0 || (it.longFrames100Ms ?: 0) > 0 }
        .sortedWith(compareByDescending<PerformanceIntervalInsight> { it.longFrames100Ms ?: 0 }
            .thenByDescending { it.jankyFrames }
            .thenByDescending { it.hwuiMissedDeadlines }.thenBy { it.startOffsetMs })
        .take(10)
    val marked = markers.map { marker ->
        PerformanceMarkerInsight(marker, intervals.filter {
            it.startOffsetMs < marker.offsetMs && it.endOffsetMs > marker.reviewFromMs
        })
    }
    val text = formatPerformanceSummary(
        pages.map { page ->
            PerformancePageSummaryRow(page.windowId, page.page, page.frames.frames, page.frames.jankyFrames,
                page.frames.percentileUpperBoundMs(.95), page.frames.maxDurationNanos / 1_000_000.0, page.interactions,
                page.frames.longFrames100Ms)
        },
        problemIntervals,
        marked,
    )
    return PerformanceReportInsights(pages, problemIntervals, marked, text)
}

internal data class PerformancePageSummaryRow(
    val windowId: Int, val page: String, val frames: Long, val jankyFrames: Long,
    val p95MsUpperBound: Int?, val maxUiMs: Double, val interactions: List<String>,
    val longFrames100Ms: Long? = null,
)

internal fun formatPerformanceSummary(
    pages: List<PerformancePageSummaryRow>, intervals: List<PerformanceIntervalInsight>, markers: List<PerformanceMarkerInsight>,
): String = buildString {
    appendLine("页面性能摘要")
    appendLine()
    appendLine("卡顿页面")
    if (pages.isEmpty()) appendLine("本次未记录到卡顿页面。")
    pages.forEachIndexed { index, page ->
        appendLine("${index + 1}. ${performancePageLabel(page.page)} · 窗口 ${page.windowId}")
        appendLine("卡顿 ${page.jankyFrames}/${page.frames} 帧（${decimal(page.jankyFrames * 100.0 / page.frames)}%）")
        page.longFrames100Ms?.let { appendLine("≥100ms 长帧：$it 帧（独立于系统卡顿阈值）") }
        appendLine("95% 的帧耗时：${durationBucket(page.p95MsUpperBound)}；最长 ${decimal(page.maxUiMs)}ms")
        appendLine("操作：${page.interactions.joinToString("、") { interactionLabel(it) }}")
    }
    appendLine()
    appendLine("卡顿时段")
    if (intervals.isEmpty()) appendLine("本次未记录到卡顿采样时段。")
    intervals.forEachIndexed { index, interval ->
        val hardware = if (interval.hwuiDeadlineFrames == 0L) "未采集" else "${interval.hwuiMissedDeadlines}/${interval.hwuiDeadlineFrames} 帧"
        appendLine("${index + 1}. ${timeRange(interval.startOffsetMs, interval.endOffsetMs)}")
        appendLine(uiFrameText(interval.frames, interval.jankyFrames))
        interval.longFrames100Ms?.let { appendLine("≥100ms 长帧：$it 帧") }
        appendLine("渲染超期（HWUI）：$hardware")
        appendLine("CPU：${decimal(interval.cpuPercentOneCore)}%（单核为 100%）")
        val position = interval.pageAtEnd?.let(::performancePageLabel) ?: "页面未记录"
        val window = interval.windowIdAtEnd?.let { " · 窗口 $it" }.orEmpty()
        appendLine("采样结束时：$position$window")
    }
    appendLine()
    appendLine("问题标记")
    if (markers.isEmpty()) appendLine("本次未添加问题标记。")
    markers.forEachIndexed { index, item ->
        appendLine("${index + 1}. 标记 ${decimal(item.marker.offsetMs / 1_000)} 秒")
        appendLine("回看范围：${timeRange(item.marker.reviewFromMs, item.marker.offsetMs)}")
        if (item.intervals.isEmpty()) appendLine("该时段没有采样记录。")
        else {
            appendLine("采样范围：${timeRange(item.intervals.minOf { it.startOffsetMs }, item.intervals.maxOf { it.endOffsetMs })}，共 ${item.intervals.size} 段")
            appendLine(uiFrameText(item.intervals.sumOf { it.frames }, item.intervals.sumOf { it.jankyFrames }))
        }
    }
}

private fun uiFrameText(frames: Long, jankyFrames: Long) =
    if (frames == 0L) "界面帧：未采集" else "界面卡顿：$jankyFrames/$frames 帧"

private fun interactionLabel(value: String) = when (value) {
    "idle" -> "页面停留（未标记滚动或切换）"
    "scroll" -> "滚动"
    "transition" -> "页面切换"
    else -> value
}

internal fun performancePageLabel(value: String) = when (value) {
    "home" -> "首页"
    "dynamic" -> "动态"
    "profile" -> "个人中心"
    "settings", "settings_category" -> "设置"
    "search" -> "搜索"
    "search_trending" -> "热门搜索"
    "space" -> "UP 主空间"
    "video", "video_player" -> "视频详情"
    "history" -> "历史记录"
    "favorite" -> "收藏"
    "watch_later" -> "稍后再看"
    "listen_video" -> "听视频"
    "live_list" -> "直播列表"
    "appearance_settings" -> com.android.purebilibili.feature.settings.SettingsRootCategory.APPEARANCE_THEME.title
    "playback_settings" -> "播放设置"
    "animation_settings" -> com.android.purebilibili.feature.settings.SettingsRootCategory.ANIMATION_HAPTICS.title
    "home_settings" -> "首页与动态设置"
    "bottom_bar_settings" -> "导航布局"
    "unknown" -> "页面未记录"
    else -> value
}

private fun decimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)
private fun durationBucket(ms: Int?) = when (ms) {
    null -> "未采集"
    501 -> ">500ms"
    else -> "≤${ms}ms"
}
private fun timeRange(startMs: Double, endMs: Double) = "${decimal(startMs / 1_000)}～${decimal(endMs / 1_000)} 秒"
