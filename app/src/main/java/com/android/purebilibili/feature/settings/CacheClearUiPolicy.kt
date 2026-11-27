package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.CacheClearTarget
import com.android.purebilibili.core.util.CacheUtils

internal data class CacheClearOptionUiModel(
    val target: CacheClearTarget,
    val title: String,
    val description: String,
    val defaultSelected: Boolean
)

internal fun resolveCacheClearOptions(): List<CacheClearOptionUiModel> {
    return listOf(
        CacheClearOptionUiModel(
            target = CacheClearTarget.PLAYBACK_QUALITY,
            title = "播放地址与画质协商缓存",
            description = "修复切画质后旧流复用、协商失败或冷却状态残留",
            defaultSelected = true
        ),
        CacheClearOptionUiModel(
            target = CacheClearTarget.NETWORK,
            title = "网络缓存",
            description = "清除旧接口响应与 HTTP 缓存，避免返回过期播放信息",
            defaultSelected = true
        ),
        CacheClearOptionUiModel(
            target = CacheClearTarget.IMAGE_PREVIEW,
            title = "图片与预览图缓存",
            description = "清除图片、封面和进度条预览图，下次会重新下载",
            defaultSelected = false
        ),
        CacheClearOptionUiModel(
            target = CacheClearTarget.SUBTITLE_DANMAKU,
            title = "字幕与弹幕缓存",
            description = "重新拉取字幕 cue 和弹幕数据，排查时间轴异常",
            defaultSelected = true
        ),
        CacheClearOptionUiModel(
            target = CacheClearTarget.TEMP_FILES_AND_LOGS,
            title = "临时文件与日志",
            description = "释放内部临时文件、外部缓存和诊断日志占用",
            defaultSelected = false
        ),
        CacheClearOptionUiModel(
            target = CacheClearTarget.APP_METADATA,
            title = "关注与签名元数据缓存",
            description = "清除 Following/WBI 等可重建元数据缓存",
            defaultSelected = false
        )
    )
}

internal fun resolveDefaultCacheClearTargets(): Set<CacheClearTarget> {
    return resolveCacheClearOptions()
        .filter { it.defaultSelected }
        .map { it.target }
        .toSet()
}

internal enum class CacheClearPreset {
    RECOMMENDED,
    SPACE_SAVING,
    ALL
}

internal enum class CacheClearDialogStage {
    SELECT,
    CLEARING,
    COMPLETE,
    ERROR
}

private val SPACE_SAVING_TARGETS = setOf(
    CacheClearTarget.NETWORK,
    CacheClearTarget.IMAGE_PREVIEW,
    CacheClearTarget.TEMP_FILES_AND_LOGS
)

internal fun resolvePresetTargets(preset: CacheClearPreset): Set<CacheClearTarget> = when (preset) {
    CacheClearPreset.RECOMMENDED -> resolveDefaultCacheClearTargets()
    CacheClearPreset.SPACE_SAVING -> SPACE_SAVING_TARGETS
    CacheClearPreset.ALL -> CacheClearTarget.entries.toSet()
}

internal fun resolveCacheClearPreset(selectedTargets: Set<CacheClearTarget>): CacheClearPreset? = when (selectedTargets) {
    resolveDefaultCacheClearTargets() -> CacheClearPreset.RECOMMENDED
    SPACE_SAVING_TARGETS -> CacheClearPreset.SPACE_SAVING
    CacheClearTarget.entries.toSet() -> CacheClearPreset.ALL
    else -> null
}

internal fun resolveAutoCacheClearStatusSummary(
    interval: SettingsManager.AutoCacheClearInterval,
    thresholdGb: Int,
    reclaimableDiskBytes: Long?,
    lastAutoClearAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): String = buildList {
    add(
        if (interval == SettingsManager.AutoCacheClearInterval.NEVER) {
            "自动清理未开启"
        } else {
            "自动清理：${interval.label}"
        }
    )
    val safeThresholdGb = thresholdGb.coerceAtLeast(1)
    val thresholdBytes = safeThresholdGb.toLong() * 1024L * 1024L * 1024L
    if (reclaimableDiskBytes != null) {
        val percentText = formatCacheClearPercent(reclaimableDiskBytes.coerceAtLeast(0L), thresholdBytes)
        add("上限 $safeThresholdGb GB（当前 $percentText）")
    } else {
        add("上限 $safeThresholdGb GB")
    }
    if (lastAutoClearAtMillis > 0L && nowMillis >= lastAutoClearAtMillis) {
        val elapsedMillis = nowMillis - lastAutoClearAtMillis
        val hourMillis = 60L * 60L * 1000L
        val dayMillis = 24L * hourMillis
        val timeText = when {
            elapsedMillis < hourMillis -> "刚刚自动清理"
            elapsedMillis < dayMillis -> "${elapsedMillis / hourMillis} 小时前自动清理"
            else -> "${elapsedMillis / dayMillis} 天前自动清理"
        }
        add(timeText)
    }
}.joinToString(" · ")

internal fun resolveCacheClearDeltaSummary(
    beforeDiskBytes: Long?,
    afterDiskBytes: Long?
): String? {
    if (beforeDiskBytes == null || afterDiskBytes == null) return null
    return "${formatCacheClearBytes(beforeDiskBytes.coerceAtLeast(0L))} → ${formatCacheClearBytes(afterDiskBytes.coerceAtLeast(0L))}"
}

internal fun resolveCacheClearConfirmationMessage(
    selectedTargets: Set<CacheClearTarget> = resolveDefaultCacheClearTargets()
): String {
    if (selectedTargets.isEmpty()) {
        return "请选择至少一项要清理的缓存。不会删除离线缓存、下载内容和播放记录。"
    }
    val selectedLabels = resolveCacheClearOptions()
        .filter { it.target in selectedTargets }
        .joinToString("、") { it.title }
    return "将清理：$selectedLabels。不会删除离线缓存、下载内容和播放记录。"
}

internal fun resolveSelectedCacheBytes(
    breakdown: CacheUtils.CacheBreakdown,
    selectedTargets: Set<CacheClearTarget>
): Long {
    return selectedTargets.sumOf { target ->
        when (target) {
            CacheClearTarget.PLAYBACK_QUALITY -> breakdown.playUrlMemoryCache
            CacheClearTarget.NETWORK -> breakdown.networkCache
            CacheClearTarget.IMAGE_PREVIEW -> breakdown.imageCache
            CacheClearTarget.SUBTITLE_DANMAKU -> breakdown.subtitleDanmakuMemoryCache
            CacheClearTarget.TEMP_FILES_AND_LOGS -> breakdown.otherCache
            CacheClearTarget.APP_METADATA -> 0L
        }
    }
}

internal fun resolveSelectedCacheSizeSummary(
    breakdown: CacheUtils.CacheBreakdown?,
    selectedTargets: Set<CacheClearTarget> = resolveDefaultCacheClearTargets()
): String {
    if (breakdown == null) return "已选缓存：计算中…"
    val selectedBytes = resolveSelectedCacheBytes(
        breakdown = breakdown,
        selectedTargets = selectedTargets
    )
    return "已选缓存：${formatCacheClearBytes(selectedBytes)}"
}

internal data class CacheClearDonutSegment(
    val target: CacheClearTarget,
    val title: String,
    val bytes: Long,
    val startAngle: Float,
    val sweepAngle: Float,
    val selected: Boolean,
    val colorIndex: Int,
    val percentLabel: String,
    val chartPercentLabel: String = percentLabel,
    val fraction: Float = 0f,
    val sizeKnown: Boolean = true
)

internal fun formatCacheClearBytes(bytes: Long): String {
    return if (bytes in 1L..1023L) "$bytes B"
    else CacheUtils.CacheBreakdown(otherCache = bytes.coerceAtLeast(0L)).format()
}

internal fun formatCacheClearPercent(bytes: Long, totalBytes: Long): String {
    if (totalBytes <= 0L || bytes <= 0L) return "0%"
    val percent = bytes.toDouble() / totalBytes.toDouble() * 100.0
    return if (percent < 1.0) "<1.0%" else "${percent.toInt()}%"
}

internal fun resolveCacheClearButtonLabel(
    breakdown: CacheUtils.CacheBreakdown?,
    selectedTargets: Set<CacheClearTarget>
): String {
    if (selectedTargets.isEmpty()) return "清理缓存"
    if (breakdown == null) return "清理缓存"
    val selectedBytes = resolveSelectedCacheBytes(breakdown, selectedTargets)
    if (selectedBytes <= 0L) {
        return if (selectedTargets.all { it == CacheClearTarget.APP_METADATA }) {
            "重置选中元数据"
        } else {
            "清理选中项"
        }
    }
    return "清理缓存 ${formatCacheClearBytes(selectedBytes)}"
}

internal fun resolveCacheClearDonutCenterSize(
    breakdown: CacheUtils.CacheBreakdown?,
    selectedTargets: Set<CacheClearTarget>
): String {
    if (breakdown == null) return "计算中"
    return formatCacheClearBytes(resolveSelectedCacheBytes(breakdown, selectedTargets))
}

internal fun resolveCacheClearDonutCenterCaption(
    breakdown: CacheUtils.CacheBreakdown?,
    selectedTargets: Set<CacheClearTarget>
): String = when {
    breakdown == null -> "正在统计"
    selectedTargets.isEmpty() -> "未选择"
    resolveSelectedCacheBytes(breakdown, selectedTargets) == 0L &&
        selectedTargets.all { it == CacheClearTarget.APP_METADATA } -> "待重置"
    else -> "已选释放"
}

internal fun resolveDeviceStorageSummary(totalBytes: Long, availableBytes: Long): String {
    val safeTotal = totalBytes.coerceAtLeast(0L)
    val safeAvailable = availableBytes.coerceAtLeast(0L)
    return "设备可用 ${formatCacheClearBytes(safeAvailable)} / 共 ${formatCacheClearBytes(safeTotal)}"
}

internal fun resolveCacheClearCompleteHeadline(
    releasedBytes: Long,
    clearedTargets: Set<CacheClearTarget>
): String {
    if (releasedBytes > 0L) {
        return "已释放 ${formatCacheClearBytes(releasedBytes)} 磁盘空间"
    }
    return if (clearedTargets.isNotEmpty()) {
        "已清理 ${clearedTargets.size} 项缓存与运行时状态"
    } else {
        "缓存清理已完成"
    }
}

internal fun resolveCacheClearDonutSegments(
    breakdown: CacheUtils.CacheBreakdown?,
    selectedTargets: Set<CacheClearTarget>,
    options: List<CacheClearOptionUiModel> = resolveCacheClearOptions()
): List<CacheClearDonutSegment> {
    val buckets = options.mapIndexed { index, option ->
        Triple(option, breakdown?.let { resolveSelectedCacheBytes(it, setOf(option.target)) } ?: 0L, index)
    }
    val selectedBytes = buckets.map { if (it.first.target in selectedTargets) it.second else 0L }
    val selectedTotal = selectedBytes.sum()
    val rowPercents = roundedCacheClearPercents(buckets.map { it.second })
    val chartPercents = roundedCacheClearPercents(selectedBytes)
    val visualFractions = cacheClearVisualFractions(selectedBytes)
    var currentStart = -90f
    return buckets.mapIndexed { index, (option, bytes, colorIndex) ->
        val selected = option.target in selectedTargets
        val fraction = if (selectedTotal > 0L) selectedBytes[index].toFloat() / selectedTotal else 0f
        val sweep = visualFractions[index] * 360f
        CacheClearDonutSegment(
            target = option.target,
            title = option.title,
            bytes = bytes,
            startAngle = currentStart,
            sweepAngle = sweep,
            selected = selected,
            colorIndex = colorIndex,
            percentLabel = when {
                breakdown == null -> "—"
                bytes > 0 && rowPercents[index] == 0 -> "<1%"
                else -> "${rowPercents[index]}%"
            },
            chartPercentLabel = "${chartPercents[index]}%",
            fraction = fraction,
            sizeKnown = breakdown != null && option.target != CacheClearTarget.APP_METADATA
        ).also { currentStart += sweep }
    }
}

/** 最大余数分配使显示百分比合计为 100，真实零值不会获得虚构份额。 */
internal fun roundedCacheClearPercents(bytes: List<Long>): List<Int> {
    val total = bytes.sumOf { it.coerceAtLeast(0L).toDouble() }
    if (total <= 0.0) return List(bytes.size) { 0 }
    val exact = bytes.map { it.coerceAtLeast(0L).toDouble() / total * 100.0 }
    val result = exact.map { it.toInt() }.toMutableList()
    val remainder = (100 - result.sum()).coerceIn(0, bytes.size)
    exact.indices.sortedWith(compareByDescending<Int> { exact[it] - result[it] }.thenByDescending { bytes[it] }.thenBy { it })
        .take(remainder).forEach { result[it]++ }
    return result
}

/** 小分类保留可命中的角度，百分比仍按真实容量显示。 */
internal fun cacheClearVisualFractions(bytes: List<Long>): List<Float> {
    val total = bytes.sumOf { it.coerceAtLeast(0L).toDouble() }
    if (total <= 0.0) return List(bytes.size) { 0f }
    val actual = bytes.map { it.coerceAtLeast(0L).toDouble() / total }
    val small = actual.count { it > 0.0 && it < .02 }
    val largeTotal = actual.filter { it >= .02 }.sum()
    return actual.map {
        when {
            it <= 0.0 -> 0f
            it < .02 -> .02f
            largeTotal > 0.0 -> (it / largeTotal * (1.0 - small * .02)).toFloat()
            else -> it.toFloat()
        }
    }
}

internal fun cacheClearCategoryTitle(target: CacheClearTarget): String = when (target) {
    CacheClearTarget.PLAYBACK_QUALITY -> "播放与画质"
    CacheClearTarget.NETWORK -> "网络与媒体分片"
    CacheClearTarget.IMAGE_PREVIEW -> "图片与预览图"
    CacheClearTarget.SUBTITLE_DANMAKU -> "字幕与弹幕"
    CacheClearTarget.TEMP_FILES_AND_LOGS -> "临时文件与日志"
    CacheClearTarget.APP_METADATA -> "关注与签名数据"
}

internal fun resolveCacheClearStageMessage(target: CacheClearTarget): String =
    "正在清理${cacheClearCategoryTitle(target)}"

internal fun cacheClearProgressFraction(completed: Long, total: Long): Float? =
    if (total > 0L) (completed.toDouble() / total).toFloat().coerceIn(0f, 1f) else null

internal fun canvasAngleDegrees(dx: Float, dy: Float): Float {
    var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0f) angle += 360f
    return angle
}

internal fun isAngleInDonutSweep(angle: Float, startAngle: Float, sweepAngle: Float): Boolean {
    if (sweepAngle <= 0f) return false
    if (sweepAngle >= 360f) return true
    val start = ((startAngle % 360f) + 360f) % 360f
    val end = (start + sweepAngle) % 360f
    val normalized = ((angle % 360f) + 360f) % 360f
    return if (start <= end) {
        normalized >= start && normalized < end
    } else {
        normalized >= start || normalized < end
    }
}

internal fun resolveCacheClearDonutHitTarget(
    segments: List<CacheClearDonutSegment>,
    dx: Float,
    dy: Float,
    innerRadius: Float,
    outerRadius: Float
): CacheClearTarget? {
    val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (distance < innerRadius || distance > outerRadius) return null
    val angle = canvasAngleDegrees(dx, dy)
    return segments.firstOrNull { segment ->
        isAngleInDonutSweep(angle, segment.startAngle, segment.sweepAngle)
    }?.target
}
