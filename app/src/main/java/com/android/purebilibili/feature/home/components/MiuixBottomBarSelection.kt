package com.android.purebilibili.feature.home.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.animation.DampedDragAnimationState
import com.android.purebilibili.core.ui.animation.DampedDragTrackingMode
import com.android.purebilibili.core.ui.animation.rememberDampedDragAnimationState
import com.android.purebilibili.core.ui.animation.shouldEngageHorizontalDrag

@Composable
internal fun rememberMiuixBottomBarSelection(
    selectedIndex: Int,
    itemCount: Int,
    onSelected: (Int) -> Unit,
): MiuixBottomBarSelection {
    val currentIndex = rememberUpdatedState(selectedIndex)
    val dragState = rememberDampedDragAnimationState(
        initialIndex = selectedIndex,
        itemCount = itemCount,
        onIndexChanged = { index ->
            if (index != currentIndex.value) onSelected(index)
        },
        pressedScale = 1f,
        trackingMode = DampedDragTrackingMode.BILIPAI_SPRING,
        notifyIndexChangedOnReleaseStart = true,
    )
    LaunchedEffect(dragState, selectedIndex) {
        dragState.updateIndex(selectedIndex)
    }
    return remember(dragState, currentIndex, itemCount) {
        MiuixBottomBarSelection(dragState, currentIndex, itemCount)
    }
}

internal class MiuixBottomBarSelection(
    private val dragState: DampedDragAnimationState,
    private val selectedIndex: State<Int>,
    private val itemCount: Int,
) {
    @Composable
    fun itemModifier(index: Int, color: Color): Modifier {
        val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
        // Each item paints the portion of the capsule behind its own icon and label.
        val indicator = Modifier.drawBehind {
            val bounds = indicatorBounds(size, index, direction)
            if (!bounds.isEmpty && bounds.left < size.width && bounds.right > 0f) {
                clipRect {
                    drawRoundRect(
                        color = color,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        cornerRadius = CornerRadius(minOf(bounds.width, bounds.height) / 2f),
                    )
                }
            }
        }
        if (itemCount <= 1) return indicator
        return indicator.pointerInput(this, index, direction) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slotWidth = size.width.toFloat()
                val slotSize = Size(slotWidth, size.height.toFloat())
                if (dragState.isDragging || !indicatorBounds(slotSize, index, direction).contains(down.position)) {
                    return@awaitEachGesture
                }
                val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    val travel = change.position - down.position
                    if (dragState.isDragging || change.id != down.id ||
                        !shouldEngageHorizontalDrag(travel.x, travel.y)
                    ) {
                        return@awaitHorizontalTouchSlopOrCancellation
                    }
                    change.consume()
                    dragState.onDrag(travel.x * direction, slotWidth)
                } ?: return@awaitEachGesture

                var completed = false
                try {
                    while (true) {
                        val change = awaitHorizontalDragOrCancellation(start.id) ?: break
                        if (change.id != start.id) break
                        if (change.changedToUpIgnoreConsumed()) {
                            completed = true
                            break
                        }
                        val delta = change.position.x - change.previousPosition.x
                        change.consume()
                        dragState.onDrag(delta * direction, slotWidth)
                    }
                } finally {
                    dragState.onDragEnd(
                        velocityX = 0f,
                        itemWidthPx = slotWidth,
                        settleIndex = if (completed) null else selectedIndex.value,
                        notifyIndexChanged = completed,
                    )
                }
            }
        }
    }

    private fun Density.indicatorBounds(slot: Size, index: Int, direction: Float): Rect {
        val width = minOf(slot.width - 8.dp.toPx(), 88.dp.toPx()).coerceAtLeast(0f)
        val height = minOf(slot.height - 8.dp.toPx(), 50.dp.toPx()).coerceAtLeast(0f)
        val left = (slot.width - width) / 2f + (dragState.value - index) * slot.width * direction
        val top = (slot.height - height) / 2f
        return Rect(left, top, left + width, top + height)
    }
}
