package com.android.purebilibili.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.purebilibili.core.ui.AppPullRefreshIndicatorStyle
import com.android.purebilibili.core.ui.AppPullRefreshMotionStyle
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.LocalScrollFeedbackPreset
import com.android.purebilibili.core.ui.components.scrollFeedbackSpring
import com.android.purebilibili.feature.home.components.Md3ScreenshotRefreshIndicator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 距离只在协程中采样，位移 State 只供 graphicsLayer 读取，避免逐帧重组 Pager 页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberHomePullContentOffset(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    motionStyle: AppPullRefreshMotionStyle,
    indicatorStyle: AppPullRefreshIndicatorStyle,
    reduceMotion: Boolean,
    active: Boolean,
): State<Float> {
    val offset = remember(state) { Animatable(0f) }
    val preset = LocalScrollFeedbackPreset.current
    LaunchedEffect(state, isRefreshing, motionStyle, indicatorStyle, reduceMotion, active, preset) {
        // 原生 MD3 与 Miuix 自行处理位移，不订阅其逐帧距离。
        if (!active || indicatorStyle == AppPullRefreshIndicatorStyle.MATERIAL_DEFAULT ||
            indicatorStyle == AppPullRefreshIndicatorStyle.MIUIX_NATIVE
        ) {
            offset.snapTo(0f)
            return@LaunchedEffect
        }
        val spring = scrollFeedbackSpring(preset, damping = 0.96f, stiffness = 520f)
        snapshotFlow {
            val distance = state.distanceFraction
            val animating = state.isAnimating
            resolveStablePullContentOffsetFraction(
                distanceFraction = distance,
                isRefreshing = isRefreshing,
                isStateAnimating = animating,
                motionStyle = motionStyle,
                indicatorStyle = indicatorStyle,
            ) to (reduceMotion || shouldSnapPullOffsetToFinger(
                distanceFraction = distance,
                isRefreshing = isRefreshing,
                isStateAnimating = animating,
                indicatorStyle = indicatorStyle,
            ))
        }.collect { (target, snap) ->
            // 由 Animatable 接管旧动画，切换目标时继承速度；effect 取消时一并取消子任务。
            launch {
                if (snap) offset.snapTo(target) else offset.animateTo(target, spring)
            }
        }
    }
    return offset.asState()
}

/** 截图式指示器的尺寸读取有独立重启作用域，不使列表随手指距离重新执行。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.HomeScreenshotRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    topInset: Dp,
    calculateDragOffset: Density.() -> Float,
) {
    val progress = state.distanceFraction
    val height = resolveMd3ScreenshotRefreshIndicatorHeightDp(progress, isRefreshing).dp
    val totalHeight = resolveMd3ScreenshotRefreshIndicatorTotalHeightDp(
        indicatorHeightDp = height.value,
        hasHintText = progress > 0f || isRefreshing,
    ).dp
    Md3ScreenshotRefreshIndicator(
        state = state,
        isRefreshing = isRefreshing,
        indicatorHeight = height,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topInset)
            .zIndex(1f)
            .graphicsLayer {
                translationY = resolveMd3ScreenshotRefreshIndicatorTranslationY(
                    dragOffsetPx = calculateDragOffset(),
                    indicatorTotalHeightPx = totalHeight.toPx(),
                    minGapPx = AppSpacingTokens.Small.toPx(),
                )
            }
            .fillMaxWidth(),
    )
}
