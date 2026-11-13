// 文件路径: feature/video/ui/overlay/ViewPointSegmentBarPolicy.kt
package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.ViewPoint

/**
 * 分段章节条（view_points）的归一化与命中策略，对齐 PiliPlus
 * 的 ViewPointSegmentProgressBar 行为：按 from 排序、裁掉越界与重叠、
 * 间隙以透明占位呈现，点击命中所覆盖的章节并跳到其起点。
 */
internal data class NormalizedViewPointSegment(
    val fromMs: Long,
    val toMs: Long,
    val content: String
)

internal fun normalizeViewPointSegments(
    viewPoints: List<ViewPoint>,
    durationMs: Long
): List<NormalizedViewPointSegment> {
    if (durationMs <= 0L) return emptyList()
    return viewPoints
        .asSequence()
        .filter { it.content.isNotBlank() && it.toMs > it.fromMs }
        .sortedBy { it.fromMs }
        .fold(mutableListOf<NormalizedViewPointSegment>()) { acc, point ->
            val from = maxOf(point.fromMs, acc.lastOrNull()?.toMs ?: 0L)
                .coerceIn(0L, durationMs)
            val to = point.toMs.coerceIn(0L, durationMs)
            if (to > from) {
                acc += NormalizedViewPointSegment(fromMs = from, toMs = to, content = point.content)
            }
            acc
        }
        .toList()
}

/** 命中 positionMs 所在的章节（最后一段允许延伸到视频结尾） */
internal fun findViewPointSegmentAt(
    segments: List<NormalizedViewPointSegment>,
    positionMs: Long
): NormalizedViewPointSegment? {
    if (segments.isEmpty()) return null
    val containing = segments.lastOrNull { positionMs >= it.fromMs && positionMs < it.toMs }
    if (containing != null) return containing
    val last = segments.last()
    return last.takeIf { positionMs >= it.toMs }
}

/** 章节 chip 的最小可读宽度：约 4 个 10sp 字符，低于该宽度的章节抬到此宽度。 */
internal val ViewPointSegmentBarMinChipWidth = 44.dp

/**
 * 短章节保底是否可行：所有按比例宽不足 [minChipWidthPx] 的章节抬到最小宽度后，
 * 合计不超过条宽的 [flooredShareLimit]（默认 60%）才启用；章节多而碎时放弃保底，
 * 整体回退纯时长比例，避免挤压其余章节导致更普遍的不可读。
 */
internal fun shouldFloorShortViewPointChips(
    segments: List<NormalizedViewPointSegment>,
    durationMs: Long,
    barWidthPx: Float,
    minChipWidthPx: Float,
    flooredShareLimit: Float = 0.6f,
): Boolean {
    if (barWidthPx <= 0f || durationMs <= 0L) return false
    var flooredTotal = 0f
    for (segment in segments) {
        val idealWidth = (segment.toMs - segment.fromMs).toFloat() / durationMs * barWidthPx
        if (idealWidth < minChipWidthPx) flooredTotal += minChipWidthPx
    }
    return flooredTotal <= barWidthPx * flooredShareLimit
}

/** 已过章节填满，当前章节按播放位置填充；回拖时直接按新位置重算。 */
internal fun viewPointSegmentFillFraction(
    segment: NormalizedViewPointSegment,
    positionMs: Long,
): Float = when {
    positionMs <= segment.fromMs -> 0f
    positionMs >= segment.toMs -> 1f
    else -> (positionMs - segment.fromMs).toFloat() / (segment.toMs - segment.fromMs)
}
