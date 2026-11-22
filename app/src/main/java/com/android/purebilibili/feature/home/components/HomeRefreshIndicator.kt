// 文件路径: feature/home/components/HomeRefreshIndicator.kt
package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppPullRefreshLoadingIndicator
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.feature.home.resolvePullRefreshHintText

/**
 * Renderer kind for [HomeRefreshIndicator]. iOS keeps its Cupertino spinner with
 * the rubber-band overshoot; MD3 uses the official morphing loading indicator;
 * the shared App indicator chooses the native loading control for each style
 * when this composable is still mounted (home Miuix uses native pull-to-refresh).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Md3ScreenshotRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    indicatorHeight: Dp,
    modifier: Modifier = Modifier
) {
    val progress = state.distanceFraction
    val hintText = resolvePullRefreshHintText(
        progress = progress,
        isRefreshing = isRefreshing,
        isStateAnimating = state.isAnimating
    )
    val alphaState = animateFloatAsState(
        targetValue = if (progress > 0.08f || isRefreshing) 1f else 0f,
        animationSpec = md3RefreshAlphaMotionSpec(),
        label = "md3_screenshot_pull_alpha"
    )
    val indicatorScaleState = animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            progress >= 1f && !state.isAnimating -> 1.04f
            else -> (0.86f + progress.coerceIn(0f, 1f) * 0.14f)
        },
        animationSpec = md3RefreshScaleMotionSpec(),
        label = "md3_screenshot_pull_scale"
    )
    val strokeColor = AppContentColors.accent().copy(alpha = 0.72f)
    val textColor = AppContentColors.secondaryText().copy(alpha = 0.74f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alphaState.value
                val scale = indicatorScaleState.value
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(top = AppSpacingTokens.Small + AppSpacingTokens.Micro, bottom = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isRefreshing) {
                // Official M3 morphing LoadingIndicator (dynamic primary).
                AdaptiveLoadingIndicator(
                    color = AppContentColors.accent(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = AppSpacingTokens.ExtraLarge + AppSpacingTokens.Micro, height = indicatorHeight)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(Color.Transparent)
                        .border(
                            width = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2,
                            color = strokeColor,
                            shape = AppShapes.container(ContainerLevel.Pill)
                        )
                )
            }

            if (hintText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small + AppSpacingTokens.Micro))
                AppText(
                    text = if (hintText == "松手刷新") "松开刷新" else hintText,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }
    }
}

/**
 *  iOS 风格下拉刷新指示器
 *
 * 特点：
 * - 下拉时显示"下拉刷新..."
 * - 达到阈值时显示"松手刷新"
 * - 刷新中显示 iOS 风格旋转动画
 * - 刷新完成显示"刷新成功"
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    //  进度值（0.0 ~ 1.0+）
    val progress = state.distanceFraction

    //  是否达到刷新阈值
    val isOverThreshold = progress >= 1f && !state.isAnimating

    //  提示文字
    val hintText = resolvePullRefreshHintText(
        progress = progress,
        isRefreshing = isRefreshing,
        isStateAnimating = state.isAnimating
    )

    //  箭头只表达阈值状态，使用高阻尼避免松手前后出现夸张回弹。
    val arrowRotationState = animateFloatAsState(
        targetValue = if (isOverThreshold) 180f else 0f,
        animationSpec = iosRefreshArrowMotionSpec(),
        label = "arrow_rotation"
    )

    //  透明度动画
    val alphaState = animateFloatAsState(
        targetValue = if (progress > 0.1f || isRefreshing) 1f else 0f,
        animationSpec = iosRefreshAlphaMotionSpec(),
        label = "alpha"
    )

    //  缩放跟随手势强度，阈值态只做轻强调，不制造果冻感。
    val scaleState = animateFloatAsState(
        targetValue = when {
            isRefreshing -> 1f
            isOverThreshold -> 1.03f
            else -> (progress.coerceIn(0f, 1f) * 0.28f + 0.72f).coerceAtMost(1f)
        },
        animationSpec = iosRefreshScaleMotionSpec(),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alphaState.value
                val scale = scaleState.value
                this.scaleX = scale
                this.scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppSpacingTokens.Medium)
        ) {
            if (isRefreshing) {
                AppPullRefreshLoadingIndicator()
            } else if (progress > 0.1f) {
                //  箭头图标（旋转表示状态变化）
                AppText(
                    text = "↓",
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = AppContentColors.accent(),
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotationState.value }
                )
            }

            if (hintText.isNotEmpty()) {
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))

                AppText(
                    text = hintText,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.secondaryText()
                )
            }
        }
    }
}
