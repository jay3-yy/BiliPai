// 文件路径: feature/video/ui/overlay/ViewPointSegmentBar.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.ViewPoint

/**
 * 按章节跨度显示名称及已播放填充，点击章节跳到起点。
 * 填充只跟随播放或拖动位置，不创建独立动画任务。
 */
@Composable
fun ViewPointSegmentBar(
    viewPoints: List<ViewPoint>,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    positionProvider: (() -> Long)? = null,
) {
    val selectionHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_SEEK)
    val latestPosition = rememberUpdatedState(currentPositionMs)
    val latestPositionProvider = rememberUpdatedState(positionProvider)
    val displayedPosition = remember {
        { latestPositionProvider.value?.invoke() ?: latestPosition.value }
    }
    val segments = remember(viewPoints, durationMs) {
        normalizeViewPointSegments(viewPoints, durationMs)
    }
    if (segments.isEmpty() || durationMs <= 0L) return

    val activeSegment by remember(segments) {
        derivedStateOf { findViewPointSegmentAt(segments, displayedPosition()) }
    }
    val trackColor = Color.White.copy(alpha = 0.24f)
    val activeColor = AppContentColors.accent().copy(alpha = 0.6f)
    val separatorColor = Color.Black.copy(alpha = 0.55f)
    val density = LocalDensity.current
    val minChipWidthPx = with(density) { ViewPointSegmentBarMinChipWidth.toPx() }

    SubcomposeLayout(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor)
            .then(if (interactive) Modifier.pointerInput(segments, durationMs) {
                detectTapGestures { offset ->
                    val positionMs = (offset.x / size.width.toFloat() * durationMs)
                        .toLong().coerceIn(0L, durationMs)
                    findViewPointSegmentAt(segments, positionMs)?.let { segment ->
                        if (findViewPointSegmentAt(segments, displayedPosition()) != segment) selectionHaptic(HapticType.LIGHT)
                        onSeek(segment.fromMs)
                    }
                }
            } else Modifier),
    ) { constraints ->
        // 短章节抬到最小可读宽度；章节过碎时整体回退纯时长比例。
        val barWidthPx = constraints.maxWidth.toFloat()
        val floorShortChips = shouldFloorShortViewPointChips(
            segments = segments,
            durationMs = durationMs,
            barWidthPx = barWidthPx,
            minChipWidthPx = minChipWidthPx,
        )
        @Suppress("ComposableLambdaInMeasurePolicy")
        val placeable = subcompose("viewPointSegmentBar") {
            Row(modifier = Modifier.fillMaxSize()) {
                var cursorMs = 0L
                segments.forEachIndexed { index, segment ->
                    if (segment.fromMs > cursorMs) {
                        // 章节之间的空隙保持轨道底色
                        Box(
                            modifier = Modifier
                                .weight((segment.fromMs - cursorMs).toFloat() / durationMs)
                                .fillMaxHeight()
                        )
                    }
                    val isActive = segment === activeSegment
                    val chipFraction = (segment.toMs - segment.fromMs).toFloat() / durationMs
                    val chipSizingModifier = if (floorShortChips &&
                        chipFraction * barWidthPx < minChipWidthPx
                    ) {
                        Modifier.width(ViewPointSegmentBarMinChipWidth)
                    } else {
                        Modifier.weight(chipFraction)
                    }
                    Box(
                        modifier = chipSizingModifier
                            .fillMaxHeight()
                            .drawBehind {
                                val fill = viewPointSegmentFillFraction(segment, displayedPosition())
                                drawRect(activeColor, size = Size(size.width * fill, size.height))
                                if (index != 0) {
                                    drawRect(
                                        separatorColor,
                                        topLeft = Offset.Zero,
                                        size = Size(1.dp.toPx().coerceAtMost(size.width), size.height),
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            text = segment.content,
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                        )
                    }
                    cursorMs = segment.toMs
                }
            }
        }.first().measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
