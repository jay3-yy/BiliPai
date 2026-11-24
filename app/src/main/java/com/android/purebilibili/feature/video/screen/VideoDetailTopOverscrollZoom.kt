package com.android.purebilibili.feature.video.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val VIDEO_DETAIL_TOP_OVERSCROLL_MAX_SCALE_DELTA = 0.055f
internal const val VIDEO_DETAIL_TOP_OVERSCROLL_RESISTANCE_DP = 220f
internal const val VIDEO_DETAIL_TOP_FLING_MAX_SCALE_DELTA = 0.028f
private const val VIDEO_DETAIL_TOP_OVERSCROLL_HAPTIC_THRESHOLD_DP = 64f
private const val VIDEO_DETAIL_TOP_FLING_MIN_VELOCITY_DP_PER_SEC = 420f
private const val VIDEO_DETAIL_TOP_FLING_REFERENCE_VELOCITY_DP_PER_SEC = 2800f

/**
 * 顶部越界下拉阻尼映射：随下拉距离渐近收敛到 `1 + maxScaleDelta`，
 * 避免线性放大在长距离拖拽时失真或过度裁切画面。
 */
internal fun resolveVideoDetailTopOverscrollScale(
    overscrollPx: Float,
    resistancePx: Float,
    maxScaleDelta: Float = VIDEO_DETAIL_TOP_OVERSCROLL_MAX_SCALE_DELTA,
): Float {
    if (!overscrollPx.isFinite() || overscrollPx <= 0f) return 1f
    val safeResistance = resistancePx.coerceAtLeast(1f)
    val fraction = 1f - 1f / (1f + overscrollPx / safeResistance)
    return 1f + maxScaleDelta.coerceAtLeast(0f) * fraction.coerceIn(0f, 1f)
}

/**
 * 惯性快速滑回顶部撞击边界时的短促弹性放大目标，速度低于阈值不触发。
 */
internal fun resolveVideoDetailTopFlingImpulseScale(
    velocityYPxPerSec: Float,
    density: Float,
): Float {
    if (!velocityYPxPerSec.isFinite() || !density.isFinite() || density <= 0f) return 1f
    val velocityDpPerSec = velocityYPxPerSec / density
    if (velocityDpPerSec <= VIDEO_DETAIL_TOP_FLING_MIN_VELOCITY_DP_PER_SEC) return 1f
    val normalized = (
        (velocityDpPerSec - VIDEO_DETAIL_TOP_FLING_MIN_VELOCITY_DP_PER_SEC) /
            (VIDEO_DETAIL_TOP_FLING_REFERENCE_VELOCITY_DP_PER_SEC - VIDEO_DETAIL_TOP_FLING_MIN_VELOCITY_DP_PER_SEC)
        ).coerceIn(0f, 1f)
    val eased = 1f - (1f - normalized) * (1f - normalized)
    return 1f + VIDEO_DETAIL_TOP_FLING_MAX_SCALE_DELTA * eased
}

/**
 * 详情页顶部封面/播放器越界物理放大状态。
 * 缩放值仅在 graphicsLayer 阶段读取，不触发组合阶段重组。
 */
@Stable
internal class VideoDetailTopOverscrollZoomState(
    private val scope: CoroutineScope,
    private val density: Float,
    private val haptic: HapticFeedback? = null,
) {
    private val settleAnimatable = Animatable(1f)
    private var dragScale by mutableFloatStateOf(1f)
    private var dragging by mutableFloatStateOf(0f)
    private var dragOverscrollPx: Float = 0f
    private var hapticTriggeredInGesture: Boolean = false
    private var settleJob: Job? = null

    val scale: Float
        get() = if (dragging > 0f) dragScale else settleAnimatable.value

    val isZoomed: Boolean
        get() = scale > 1.0001f

    private val resistancePx: Float
        get() = VIDEO_DETAIL_TOP_OVERSCROLL_RESISTANCE_DP * density.coerceAtLeast(1f)

    private val hapticThresholdPx: Float
        get() = VIDEO_DETAIL_TOP_OVERSCROLL_HAPTIC_THRESHOLD_DP * density.coerceAtLeast(1f)

    /** 反向向上推时优先回收已产生的越界拉伸，保持手指跟手。 */
    fun consumeReverseDrag(deltaY: Float, source: NestedScrollSource): Float {
        if (source != NestedScrollSource.UserInput || deltaY >= 0f || dragOverscrollPx <= 0f) return 0f
        settleJob?.cancel()
        val consumed = (-deltaY).coerceAtMost(dragOverscrollPx)
        dragOverscrollPx = (dragOverscrollPx - consumed).coerceAtLeast(0f)
        if (dragOverscrollPx <= 0f) hapticTriggeredInGesture = false
        dragScale = resolveVideoDetailTopOverscrollScale(dragOverscrollPx, resistancePx)
        dragging = if (dragOverscrollPx > 0f) 1f else 0f
        return -consumed
    }

    /** 列表已到顶且继续向下拉时，将剩余越界距离映射为轻微阻尼放大（同步写入，零协程开销）。 */
    fun accumulateTopOverscroll(availableY: Float, source: NestedScrollSource) {
        if (source != NestedScrollSource.UserInput || availableY <= 0f) return
        settleJob?.cancel()
        dragOverscrollPx += availableY
        if (!hapticTriggeredInGesture && dragOverscrollPx >= hapticThresholdPx) {
            hapticTriggeredInGesture = true
            haptic?.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        dragScale = resolveVideoDetailTopOverscrollScale(dragOverscrollPx, resistancePx)
        dragging = 1f
    }

    /** 松手或惯性回顶撞击顶部边界时执行弹簧回弹 / 短促物理冲量反馈。 */
    fun onFlingAtTop(remainingVelocityY: Float, allowFlingImpulse: Boolean) {
        if (dragOverscrollPx > 0f || scale > 1.0001f) {
            releaseDragIfActive()
            return
        }
        if (!allowFlingImpulse) return
        val peakScale = resolveVideoDetailTopFlingImpulseScale(remainingVelocityY, density)
        if (peakScale <= 1.0005f) return
        haptic?.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        settleJob?.cancel()
        settleJob = scope.launch {
            settleAnimatable.snapTo(1f)
            settleAnimatable.animateTo(
                targetValue = peakScale,
                animationSpec = tween(durationMillis = 85),
            )
            settleAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    fun releaseDragIfActive() {
        val current = scale
        hapticTriggeredInGesture = false
        if (dragOverscrollPx <= 0f && current <= 1.0001f) return
        dragOverscrollPx = 0f
        settleJob?.cancel()
        settleJob = scope.launch {
            settleAnimatable.snapTo(current)
            dragging = 0f
            dragScale = 1f
            settleAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    fun resetImmediate() {
        settleJob?.cancel()
        dragOverscrollPx = 0f
        hapticTriggeredInGesture = false
        dragging = 0f
        dragScale = 1f
        if (settleAnimatable.value != 1f) {
            settleJob = scope.launch { settleAnimatable.snapTo(1f) }
        }
    }
}

@Composable
internal fun rememberVideoDetailTopOverscrollZoomState(
    enabled: Boolean,
    density: Float,
): VideoDetailTopOverscrollZoomState {
    val scope = rememberCoroutineScope()
    val haptic = rememberAppHapticFeedback(AppHapticScene.REFRESH)
    val state = remember(scope, density, haptic) {
        VideoDetailTopOverscrollZoomState(scope = scope, density = density, haptic = haptic)
    }
    LaunchedEffect(state, enabled) {
        if (!enabled) state.resetImmediate()
    }
    return state
}

/** 在绘制层对封面/播放器施加轻微越界放大，并在外层裁切到播放器视口内，零组合重组。 */
internal fun Modifier.videoDetailTopOverscrollZoom(
    state: VideoDetailTopOverscrollZoomState,
): Modifier = this
    .graphicsLayer {
        clip = state.isZoomed
    }
    .graphicsLayer {
        val currentScale = state.scale
        if (currentScale > 1.0001f) {
            scaleX = currentScale
            scaleY = currentScale
            transformOrigin = TransformOrigin.Center
        }
    }
