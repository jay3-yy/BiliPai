package com.android.purebilibili.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import kotlin.math.roundToInt

@Composable
internal fun CompanionPaneDivider(
    state: CompanionPaneState,
    contentWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resizePointerIcon = androidx.compose.runtime.remember(context) {
        PointerIcon(
            android.view.PointerIcon.getSystemIcon(
                context,
                android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW,
            )
        )
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val widthPx = with(density) { contentWidth.toPx() }
    val gestureInsets = WindowInsets.systemGestures
    val startInset = if (direction > 0f) gestureInsets.getLeft(density, layoutDirection) else gestureInsets.getRight(density, layoutDirection)
    val endInset = if (direction > 0f) gestureInsets.getRight(density, layoutDirection) else gestureInsets.getLeft(density, layoutDirection)
    val description = stringResource(R.string.companion_resize)
    val restore = stringResource(R.string.companion_restore_split)
    val feed = stringResource(R.string.companion_show_feed)
    val video = stringResource(R.string.companion_show_video)
    val gripColor = MaterialTheme.colorScheme.onSurfaceVariant
    val handleInteractionSource = remember { MutableInteractionSource() }
    val handleFocused by handleInteractionSource.collectIsFocusedAsState()
    val handleBorderColor = if (handleFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback(
        com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION,
    )
    // 单栏折叠停靠时用贴边细条减少遮挡；拖动中或双栏态显示完整胶囊。
    val collapsed = state.mode != CompanionPaneMode.Split && !state.isDragging
    val collapsedAtStart = state.mode == CompanionPaneMode.Video
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset { IntOffset((state.layoutFeedFraction(contentWidth.value) * widthPx).roundToInt(), 0) }
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val handleWidth = with(density) { 48.dp.roundToPx() }
                    val x = if (collapsed) {
                        val edgeGap = with(density) { 6.dp.roundToPx() }
                        val safeStart = maxOf(edgeGap, startInset / 2)
                        val safeEnd = maxOf(edgeGap, endInset / 2)
                        if (collapsedAtStart) safeStart
                        else (widthPx.roundToInt() - handleWidth - safeEnd).coerceAtLeast(safeStart)
                    } else {
                        (state.layoutFeedFraction(contentWidth.value) * widthPx - handleWidth / 2f)
                            .roundToInt().coerceIn(startInset, (widthPx.roundToInt() - handleWidth - endInset).coerceAtLeast(startInset))
                    }
                    IntOffset(x, 0)
                }
                .size(48.dp, 96.dp)
                // 避免折叠态贴边把手或边缘拖动与 Android 系统侧滑返回冲突。
                .systemGestureExclusion()
                .focusable(interactionSource = handleInteractionSource)
                .pointerHoverIcon(resizePointerIcon)
                .testTag("companion_pane_divider")
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(state.committedFeedFraction(contentWidth.value), 0f..1f)
                    setProgress { state.selectFraction(it, contentWidth.value); true }
                    customActions = listOf(
                        CustomAccessibilityAction(feed) { state.selectFraction(1f, contentWidth.value); true },
                        CustomAccessibilityAction(video) { state.selectFraction(0f, contentWidth.value); true },
                        CustomAccessibilityAction(restore) { state.restoreSplit(); true },
                    )
                }
                .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            val step = if (it.key == Key.DirectionLeft) -0.1f else 0.1f
                            state.selectFraction(state.committedFeedFraction(contentWidth.value) + step * direction, contentWidth.value)
                            true
                        }
                        Key.MoveHome -> { state.selectFraction(0f, contentWidth.value); true }
                        Key.MoveEnd -> { state.selectFraction(1f, contentWidth.value); true }
                        else -> false
                    }
                }
                .pointerInput(state, contentWidth, direction) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                            state.beginDrag()
                        },
                        onDragEnd = {
                            val previousMode = state.mode
                            state.endDrag(contentWidth.value)
                            if (state.mode != previousMode) {
                                haptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                            }
                        },
                        onDragCancel = { state.cancelDrag() },
                    ) { change, delta ->
                        change.consume()
                        state.dragBy(delta * direction / widthPx)
                    }
                }
                .clickable(onClickLabel = restore) {
                    haptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                    state.restoreSplit()
                },
            contentAlignment = when {
                collapsed && collapsedAtStart -> Alignment.CenterStart
                collapsed -> Alignment.CenterEnd
                else -> Alignment.Center
            },
        ) {
            val handleShape = RoundedCornerShape(if (collapsed) 7.dp else 11.dp)
            Box(
                Modifier
                    .graphicsLayer {
                        val s = if (handleFocused) 1.08f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .size(if (collapsed) 14.dp else 22.dp, 64.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, handleShape)
                    .border(if (handleFocused) 2.dp else 0.75.dp, handleBorderColor, handleShape)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        val armX = 2.dp.toPx()
                        val armY = 5.dp.toPx()
                        if (collapsed) {
                            // 细条上的单箭头指向展开方向：列表收在起点时拖向终点展开，反之亦然。
                            val side = if (collapsedAtStart) direction else -direction
                            val apex = Offset(center.x + side * armX, center.y)
                            drawLine(gripColor, Offset(center.x - side * armX, center.y - armY), apex, stroke, StrokeCap.Round)
                            drawLine(gripColor, Offset(center.x - side * armX, center.y + armY), apex, stroke, StrokeCap.Round)
                        } else {
                            repeat(2) { index ->
                                val side = if (index == 0) -1f else 1f
                                val apex = Offset(center.x + side * 4.5.dp.toPx(), center.y)
                                drawLine(gripColor, Offset(apex.x - side * armX, center.y - armY), apex, stroke, StrokeCap.Round)
                                drawLine(gripColor, Offset(apex.x - side * armX, center.y + armY), apex, stroke, StrokeCap.Round)
                            }
                        }
                    },
            )
        }
    }
}
