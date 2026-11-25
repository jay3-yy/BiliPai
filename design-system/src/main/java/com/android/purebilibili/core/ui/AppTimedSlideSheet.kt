package com.android.purebilibili.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 显式启用的底部整面板过渡。材质、内容和居中弹窗策略由宿主保留。 */
@Composable
internal fun AppTimedSlideSheet(
    visible: Boolean,
    onDismissStarted: () -> Unit,
    onDismissFinished: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    scrimColor: Color,
    dismissOnBackPress: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    windowInsets: WindowInsets,
    sheetSurfaceModifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val height = remember { intArrayOf(1) }
    val latestStarted by rememberUpdatedState(onDismissStarted)
    val latestFinished by rememberUpdatedState(onDismissFinished)
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val requestDismiss: () -> Unit = {
        if (!closing) {
            settleJob?.cancel()
            closing = true
            latestStarted()
        }
    }
    LaunchedEffect(visible) {
        if (visible) closing = false
    }
    LaunchedEffect(visible, closing, reduced) {
        val opening = visible && !closing
        progress.animateTo(
            if (opening) 1f else 0f,
            tween(
                if (reduced) { if (opening) 120 else 90 } else { if (opening) 250 else 200 },
                easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.M1Standard,
            ),
        )
        if (!opening) latestFinished()
    }
    val canDrag: () -> Boolean = { visible && !closing && progress.value >= 0.999f }
    val consumeDrag: (Float) -> Float = { delta ->
        if (canDrag()) {
            settleJob?.cancel()
            val old = dragOffset
            dragOffset = (old + delta).coerceAtLeast(0f)
            dragOffset - old
        } else 0f
    }
    val finishDrag: (Float) -> Unit = { velocity ->
        if (canDrag()) {
            settleJob?.cancel()
            if (dragOffset > height[0] * 0.3f || (dragOffset > 0f && velocity > 700f)) {
                requestDismiss()
            } else {
                settleJob = scope.launch {
                    animate(
                        dragOffset,
                        0f,
                        animationSpec = tween(
                            if (reduced) 0 else 150,
                            easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.M1Standard,
                        ),
                    ) { value, _ ->
                        dragOffset = value
                    }
                }
            }
        }
    }
    val latestConsumeDrag by rememberUpdatedState(consumeDrag)
    val latestFinishDrag by rememberUpdatedState(finishDrag)
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y < 0 && dragOffset > 0) {
                    Offset(0f, latestConsumeDrag(available.y))
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    Offset(0f, latestConsumeDrag(available.y))
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset <= 0f) return Velocity.Zero
                latestFinishDrag(available.y)
                return available
            }
        }
    }
    val predictiveBackEnabled = dismissOnBackPress && LocalPredictiveBackGestureEnabled.current
    Dialog(
        onDismissRequest = { if (dismissOnBackPress) requestDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissOnBackPress && !predictiveBackEnabled,
        ),
    ) {
        val back = remember { AppDialogBackState() }
        if (predictiveBackEnabled) {
            AppDialogPredictiveBackHandler(
                state = back,
                enabled = !closing,
                onBackCompleted = requestDismiss,
            )
        }
        ModalWindowBlurBehindEffect(progress = { (progress.value * (1f - back.progress)).coerceIn(0f, 1f) })
        AppPopupWindowHost(enableWindowMaterial = false) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = (progress.value * (1f - dragOffset / height[0]).coerceIn(0f, 1f) * (1f - back.progress))
                                .coerceIn(0f, 1f)
                        }
                        .background(scrimColor)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = requestDismiss,
                        ),
                )
                Surface(
                    modifier = modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(windowInsets)
                        .fillMaxWidth()
                        .onSizeChanged { height[0] = it.height.coerceAtLeast(1) }
                        .graphicsLayer {
                            val backProgress = back.progress.coerceIn(0f, 1f)
                            val baseTranslation = if (reduced) {
                                dragOffset
                            } else {
                                (size.height - dragOffset) * (1f - progress.value) + dragOffset
                            }
                            translationY = baseTranslation + size.height * 0.14f * backProgress
                            val backScale = 1f - 0.04f * backProgress
                            scaleX = backScale
                            scaleY = backScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                            alpha = if (reduced) progress.value else 1f
                        }
                        .nestedScroll(nestedScroll),
                    shape = shape,
                    color = containerColor,
                    contentColor = contentColor,
                ) {
                    Column(Modifier.then(sheetSurfaceModifier)) {
                        if (dragHandle != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .draggable(
                                        state = rememberDraggableState { consumeDrag(it) },
                                        orientation = Orientation.Vertical,
                                        onDragStopped = { finishDrag(it) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { dragHandle() }
                        }
                        content()
                    }
                }
            }
        }
    }
}
