package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Immutable
data class MenuScrollMetrics(
    val viewportFraction: Float,
    val scrollFraction: Float,
)

fun resolveMenuScrollMetrics(viewport: Int, maxScroll: Int, scroll: Int): MenuScrollMetrics? {
    if (viewport <= 0 || maxScroll <= 0 || maxScroll == Int.MAX_VALUE) return null
    return MenuScrollMetrics(
        viewportFraction = (viewport.toFloat() / (viewport.toFloat() + maxScroll.toFloat()))
            .coerceIn(0f, 1f),
        scrollFraction = (scroll.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f),
    )
}

fun resolveMenuScrollMetrics(state: ScrollState): MenuScrollMetrics? =
    resolveMenuScrollMetrics(state.viewportSize, state.maxValue, state.value)

fun resolveMenuScrollMetrics(state: LazyListState): MenuScrollMetrics? {
    val layout = state.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo
    if (total <= 0 || visible.isEmpty()) return null
    val viewportFraction = (visible.size.toFloat() / total).coerceIn(0f, 1f)
    if (viewportFraction >= 1f && !state.canScrollForward && !state.canScrollBackward) return null

    val averageItemSize = visible.sumOf { it.size }.toFloat() / visible.size
    val maxFirstIndex = (total - visible.size).coerceAtLeast(1)
    val rawScroll = if (averageItemSize > 0f) {
        (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / averageItemSize) /
            maxFirstIndex
    } else {
        state.firstVisibleItemIndex.toFloat() / maxFirstIndex
    }
    val scrollFraction = when {
        !state.canScrollForward -> 1f
        !state.canScrollBackward -> 0f
        else -> rawScroll.coerceIn(0f, 1f)
    }
    return MenuScrollMetrics(viewportFraction, scrollFraction)
}

@Stable
class MenuScrollHintState internal constructor(
    val overscrollPx: () -> Float,
    val nestedScrollConnection: NestedScrollConnection,
)

/**
 * Observes edge drag without consuming it. The hint shrinks while the user pulls
 * past an edge and springs back after the gesture settles.
 */
@Composable
fun rememberMenuScrollHintState(
    state: ScrollableState,
    maxOverscrollPx: () -> Float,
): MenuScrollHintState {
    var overscrollPx by remember(state) { mutableFloatStateOf(0f) }
    var inputGeneration by remember(state) { mutableIntStateOf(0) }
    val latestMaxOverscrollPx by rememberUpdatedState(maxOverscrollPx)
    val motionEnabled = LocalComponentMotionEnabled.current && !LocalSystemReduceMotion.current
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val max = latestMaxOverscrollPx()
                    .takeIf { it.isFinite() && it > 0f } ?: 1f
                val delta = when {
                    !state.canScrollBackward && available.y > 0f -> available.y
                    !state.canScrollForward && available.y < 0f -> available.y
                    else -> 0f
                }
                if (delta != 0f) {
                    overscrollPx = (overscrollPx + delta).coerceIn(-max, max)
                    inputGeneration++
                } else if (overscrollPx != 0f) {
                    inputGeneration++
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state, motionEnabled) {
        snapshotFlow { inputGeneration }.collectLatest { generation ->
            if (generation == 0) return@collectLatest
            if (!motionEnabled) {
                delay(80)
                overscrollPx = 0f
                return@collectLatest
            }
            delay(80)
            animate(
                initialValue = overscrollPx,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
            ) { value, _ -> overscrollPx = value }
        }
    }
    return remember(state, connection) {
        MenuScrollHintState(overscrollPx = { overscrollPx }, nestedScrollConnection = connection)
    }
}

fun DrawScope.drawMenuScrollHint(
    metrics: MenuScrollMetrics?,
    overscrollPx: Float,
    maxOverscrollPx: Float,
    color: Color,
    insetPx: Float,
    endInsetPx: Float,
    minLengthPx: Float,
    baseWidthPx: Float,
) {
    val trackLength = size.height - insetPx * 2f
    if (trackLength <= 0f) return
    val value = metrics ?: return
    val geometry = resolveMenuScrollHintGeometry(
        trackLength = trackLength,
        metrics = value,
        overscrollPx = overscrollPx,
        maxOverscrollPx = maxOverscrollPx,
        minLengthPx = minLengthPx,
        baseWidthPx = baseWidthPx,
    ) ?: return

    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - endInsetPx - geometry.width, insetPx + geometry.top),
        size = Size(geometry.width, geometry.length),
        cornerRadius = CornerRadius(geometry.width / 2f),
    )
}

@Immutable
data class MenuScrollHintGeometry(
    val top: Float,
    val length: Float,
    val width: Float,
)

fun resolveMenuScrollHintGeometry(
    trackLength: Float,
    metrics: MenuScrollMetrics,
    overscrollPx: Float,
    maxOverscrollPx: Float,
    minLengthPx: Float,
    baseWidthPx: Float,
): MenuScrollHintGeometry? {
    if (trackLength <= 0f) return null
    val safeOverscroll = overscrollPx.takeIf { it.isFinite() } ?: 0f
    val maxOverscroll = maxOverscrollPx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val force = (abs(safeOverscroll) / maxOverscroll).coerceIn(0f, 1f)
    val baseLength = (trackLength * metrics.viewportFraction)
        .coerceIn(minOf(minLengthPx, trackLength), trackLength)
    val length = (baseLength * (1f - 0.4f * force))
        .coerceAtLeast(minOf(minLengthPx, trackLength) * 0.6f)
    val width = baseWidthPx * (1f - 0.15f * force)
    val top = when {
        safeOverscroll > 0f -> 0f
        safeOverscroll < 0f -> trackLength - length
        else -> (trackLength - length) * metrics.scrollFraction
    }
    return MenuScrollHintGeometry(top = top, length = length, width = width)
}

@Composable
fun AppMenuScrollHint(
    metrics: () -> MenuScrollMetrics?,
    overscrollPx: () -> Float,
    maxOverscrollPx: () -> Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    inset: Dp = 12.dp,
    endInset: Dp = 5.dp,
    minLength: Dp = 28.dp,
    width: Dp = 3.dp,
) {
    val resolvedColor = color ?: AppContentColors.primaryText().copy(alpha = 0.28f)
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            drawMenuScrollHint(
                metrics = metrics(),
                overscrollPx = overscrollPx(),
                maxOverscrollPx = maxOverscrollPx(),
                color = resolvedColor,
                insetPx = inset.toPx(),
                endInsetPx = endInset.toPx(),
                minLengthPx = minLength.toPx(),
                baseWidthPx = width.toPx(),
            )
        }
    ) { }
}
