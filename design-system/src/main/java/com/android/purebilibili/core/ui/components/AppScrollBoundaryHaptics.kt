package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.rememberAppHapticFeedback

/** 只观察当前滚动容器的边界，不消耗位移或替换原有回弹。 */
@Composable
fun Modifier.appScrollBoundaryHaptics(
    state: ScrollableState,
    reverseLayout: Boolean = false,
    orientation: Orientation = Orientation.Vertical,
): Modifier {
    val feedback = rememberScrollBoundaryFeedback(state)
    val reverseDirection = ScrollableDefaults.reverseDirection(
        LocalLayoutDirection.current, orientation, reverseLayout,
    )
    return observeScrollBoundary(state, feedback, reverseDirection, orientation)
}

@Composable
internal fun Modifier.observeScrollBoundary(
    state: ScrollableState,
    feedback: ScrollBoundaryFeedback,
    reverseDirection: Boolean,
    orientation: Orientation,
): Modifier {
    val connection = remember(state, feedback, reverseDirection, orientation) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (state.isScrollInProgress) feedback.beforeScroll()
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 滚动子列表时，静止的外层分页或列表不会重复反馈。
                if (!state.isScrollInProgress) return Offset.Zero
                val consumedDelta = consumed.alongAxis()
                val delta = consumedDelta + available.alongAxis()
                feedback.afterScroll(if (reverseDirection) -delta else delta, consumedDelta != 0f)
                return Offset.Zero
            }

            private fun Offset.alongAxis(): Float = if (orientation == Orientation.Horizontal) x else y
        }
    }
    return nestedScroll(connection)
}

@Composable
fun rememberScrollBoundaryFeedback(state: ScrollableState): ScrollBoundaryFeedback {
    val haptics = rememberAppHapticFeedback(AppHapticScene.REFRESH)
    return remember(state, haptics) { ScrollBoundaryFeedback(state, haptics) }
}

class ScrollBoundaryFeedback internal constructor(
    private val state: ScrollableState,
    private val haptics: HapticFeedback,
) {
    private var lastHitWasForward: Boolean? = null

    fun beforeScroll() {
        when (lastHitWasForward) {
            true -> if (state.canScrollForward) lastHitWasForward = null
            false -> if (state.canScrollBackward) lastHitWasForward = null
            null -> Unit
        }
    }

    /** 正值沿内容前进。即使抵达边界后位移为零，仍使用手势的剩余位移判断方向。 */
    fun afterScroll(delta: Float, moved: Boolean) {
        if (delta == 0f) return
        val forward = delta > 0f
        val canContinue = if (forward) state.canScrollForward else state.canScrollBackward
        val canMoveAway = if (forward) state.canScrollBackward else state.canScrollForward
        if (canContinue) {
            if (moved) lastHitWasForward = null
        } else if (canMoveAway && lastHitWasForward != forward) {
            lastHitWasForward = forward
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
}
