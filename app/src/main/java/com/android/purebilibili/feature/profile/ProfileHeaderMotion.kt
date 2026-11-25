/** 资料头部滚动收放和吸附策略。 */
package com.android.purebilibili.feature.profile

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion

internal val ProfileHeaderEaseOutQuint = com.android.purebilibili.core.ui.motion.AppMotionEasing.EaseOutQuint

internal fun resolveProfileHeaderExpandedFraction(offsetPx: Float, collapseRangePx: Float): Float =
    if (collapseRangePx <= 0f) 1f else (1f - offsetPx / collapseRangePx).coerceIn(0f, 1f)

/** Settle using the 60% remaining-height threshold and upward 1000px/s shortcut. */
internal fun resolveProfileHeaderSnapOffset(offsetPx: Float, rangePx: Float, velocity: Float): Float? {
    if (rangePx <= 0f || offsetPx <= 0f || offsetPx >= rangePx) return null
    val expanded = resolveProfileHeaderExpandedFraction(offsetPx, rangePx)
    return if (expanded < 0.6f || velocity > 1000f) rangePx else 0f
}

@Composable
internal fun rememberProfileHeaderFlingBehavior(
    listState: LazyListState,
    collapseRangeProvider: () -> Float,
): FlingBehavior {
    val defaultFling = ScrollableDefaults.flingBehavior()
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
    return remember(listState, collapseRangeProvider, defaultFling, motionEnabled) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (listState.firstVisibleItemIndex != 0) {
                    return with(defaultFling) { performFling(initialVelocity) }
                }
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                val target = resolveProfileHeaderSnapOffset(offset, collapseRangeProvider(), initialVelocity)
                    ?: return with(defaultFling) { performFling(initialVelocity) }
                val distance = target - offset
                if (!motionEnabled) {
                    scrollBy(distance)
                } else {
                    var previous = 0f
                    animate(0f, distance, animationSpec = tween(300, easing = ProfileHeaderEaseOutQuint)) { value, _ ->
                        scrollBy(value - previous)
                        previous = value
                    }
                    if (listState.firstVisibleItemIndex == 0) {
                        val remaining = target - listState.firstVisibleItemScrollOffset.toFloat()
                        if (remaining != 0f) scrollBy(remaining)
                    }
                }
                return 0f
            }
        }
    }
}
