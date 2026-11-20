package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.rememberAppHapticFeedback

/** 在纵向滚动容器外观察本次滚动是否抵达边界，不消耗位移或替换原有回弹。 */
@Composable
fun Modifier.appScrollBoundaryHaptics(
    state: ScrollableState,
    reverseLayout: Boolean = false,
): Modifier {
    val haptics = rememberAppHapticFeedback(AppHapticScene.REFRESH)
    val connection = remember(state, reverseLayout, haptics) {
        object : NestedScrollConnection {
            private var lastHitWasForward: Boolean? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 也处理列表在两次手势之间被滚动到别处的情况。
                when (lastHitWasForward) {
                    true -> if (state.canScrollForward) lastHitWasForward = null
                    false -> if (state.canScrollBackward) lastHitWasForward = null
                    null -> Unit
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 边缘可能吃不下这一步位移，仍要识别手势撞到边界的方向。
                val deltaY = consumed.y + available.y
                if (deltaY == 0f) return Offset.Zero
                // 默认布局向上划动前进，反向布局则相反。
                val forward = (deltaY < 0f) != reverseLayout
                val canContinue = if (forward) state.canScrollForward else state.canScrollBackward
                val canMoveAway = if (forward) state.canScrollBackward else state.canScrollForward
                if (canContinue) {
                    // 回拉的小位移可能已被接收，但还没达到一个布局像素。
                    if (consumed.y != 0f) lastHitWasForward = null
                } else if (canMoveAway && lastHitWasForward != forward) {
                    lastHitWasForward = forward
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection)
}
