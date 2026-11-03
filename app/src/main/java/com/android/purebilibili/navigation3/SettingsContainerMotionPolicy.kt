package com.android.purebilibili.navigation3

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import com.android.purebilibili.core.ui.motion.AppMotionEasing

internal const val SETTINGS_CONTAINER_DURATION_MILLIS = 300

internal fun containerReturnSpringSpec(stiffness: Float = 500f): SpringSpec<Float> =
    spring(dampingRatio = 0.82f, stiffness = stiffness, visibilityThreshold = 0.0001f)

internal data class SettingsContainerFrame(
    val bounds: Rect,
    val contentAlpha: Float,
    val cornerFraction: Float,
)

/** 导航深度是唯一时钟；程序返回、跟手及取消返回都沿同一几何路径。 */
internal fun resolveSettingsContainerFrame(
    source: Rect,
    destination: Rect,
    relativeDepth: Float,
    returning: Boolean,
    springDriven: Boolean = false,
): SettingsContainerFrame {
    val open = (1f + relativeDepth.coerceAtMost(0f)).coerceIn(0f, 1f)
    val geometry = if (springDriven) open else if (returning) {
        1f - AppMotionEasing.M1Standard.transform(1f - open)
    } else AppMotionEasing.M1Standard.transform(open)
    val fadeStart = if (returning) 0.6f else 0.2f
    // 官方导航弹簧越过终点时，只在来源尺寸附近收敛，避免用页面尺寸放大微小过冲。
    val landingScale = if (springDriven && returning) 1f + (1f + relativeDepth).coerceAtMost(0f) else 1f
    val bounds = lerp(source, destination, geometry)
    val halfSize = bounds.size * (landingScale.coerceIn(0.97f, 1f) / 2f)
    return SettingsContainerFrame(
        bounds = Rect(bounds.center - androidx.compose.ui.geometry.Offset(halfSize.width, halfSize.height),
            bounds.center + androidx.compose.ui.geometry.Offset(halfSize.width, halfSize.height)),
        contentAlpha = ((open - fadeStart) / 0.2f).coerceIn(0f, 1f),
        cornerFraction = 1f - geometry,
    )
}

/** 来源必须属于紧邻的父 entry，防止搜索或其他页面复用同名目的地的旧来源。 */
internal fun isSettingsContainerBoundary(
    enabled: Boolean,
    backStack: List<BiliPaiNavKey>,
    target: BiliPaiNavKey,
    owner: BiliPaiNavKey?,
): Boolean {
    if (!enabled || owner == null) return false
    val index = backStack.indexOf(target)
    return index > 0 && backStack[index - 1] == owner
}

/** 父页深度从 0 到 1 时逐渐模糊，前景页及非有限输入不参与。 */
internal fun resolveSettingsContainerBlurProgress(relativeDepth: Float): Float {
    if (!relativeDepth.isFinite()) return 0f
    val covered = relativeDepth.coerceIn(0f, 1f)
    return covered * covered
}
