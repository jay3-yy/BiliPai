package com.android.purebilibili.navigation3

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavSettlePhase
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

internal const val SETTINGS_CONTAINER_DURATION_MILLIS = 300

internal fun containerReturnSpringSpec(stiffness: Float = 500f): SpringSpec<Float> =
    spring(dampingRatio = 0.82f, stiffness = stiffness, visibilityThreshold = 0.0001f)

internal data class SettingsContainerFrame(
    val bounds: Rect,
    val contentAlpha: Float,
    val cornerFraction: Float,
)

/** 已展开容器的跟手、取消和提交都直接消费导航深度，阶段切换不再重映射几何。 */
internal fun resolveSettingsContainerFrame(
    source: Rect,
    destination: Rect,
    scope: NavTransitionScope,
    hasOpened: Boolean,
    videoCardMotionEnabled: Boolean,
): SettingsContainerFrame = resolveSettingsContainerFrame(
    source = source,
    destination = destination,
    relativeDepth = scope.relativeDepth,
    returning = !videoCardMotionEnabled && hasOpened && (
        scope.change == NavChange.Pop || scope.change is NavChange.MultiPop || scope.gesture != null ||
            scope.settle?.phase == NavSettlePhase.Cancel || scope.settle?.phase == NavSettlePhase.Commit
        ),
    // 玻璃与原生共用；视频卡片保留原有单时钟行为，普通容器取消时不再二次缓动。
    springDriven = videoCardMotionEnabled || hasOpened,
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
    // 官方导航弹簧越过终点时，只在来源尺寸附近收敛，避免用页面尺寸放大微小过冲。
    val landingScale = if (springDriven && returning) 1f + (1f + relativeDepth).coerceAtMost(0f) else 1f
    val bounds = lerp(source, destination, geometry)
    val halfSize = bounds.size * (landingScale.coerceIn(0.97f, 1f) / 2f)
    return SettingsContainerFrame(
        bounds = Rect(bounds.center - androidx.compose.ui.geometry.Offset(halfSize.width, halfSize.height),
            bounds.center + androidx.compose.ui.geometry.Offset(halfSize.width, halfSize.height)),
        contentAlpha = resolveSettingsContainerContentAlpha(open, returning),
        cornerFraction = 1f - geometry,
    )
}

internal fun resolveSettingsContainerContentAlpha(open: Float, returning: Boolean): Float {
    val start = if (returning) 0.45f else 0.22f
    val span = if (returning) 0.30f else 0.28f
    val u = ((open - start) / span).coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
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

/** 父页深度从 0 到 1 时快速建立模糊，前景页及非有限输入不参与。 */
internal fun resolveSettingsContainerBlurProgress(relativeDepth: Float): Float {
    if (!relativeDepth.isFinite()) return 0f
    val covered = relativeDepth.coerceIn(0f, 1f)
    val remaining = 1f - covered
    return 1f - remaining * remaining
}
