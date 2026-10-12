package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableFloatState
import kotlinx.coroutines.Job
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The physical screen edge occupied by a landscape side panel. */
internal enum class LandscapeSidePanelEdge {
    Start,
    End,
}

/**
 * A fixed-width landscape panel that follows horizontal drags and settles open or closed.
 *
 * The close animation is intentionally owned here instead of [androidx.compose.animation.AnimatedVisibility]:
 * a new drag stops the in-flight animation immediately, so users can reverse direction without
 * waiting for the previous transition to finish.
 */
@Composable
internal fun LandscapeSidePanel(
    visible: Boolean,
    edge: LandscapeSidePanelEdge,
    width: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    closeProgress: MutableFloatState = remember { mutableFloatStateOf(1f) },
    content: @Composable (requestDismiss: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    val animationJob = remember { mutableStateOf<Job?>(null) }
    fun settleTo(target: Float, durationMillis: Int, dismiss: Boolean = false) {
        animationJob.value?.cancel()
        animationJob.value = scope.launch {
            animate(
                initialValue = closeProgress.floatValue,
                targetValue = target,
                animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            ) { value, _ -> closeProgress.floatValue = value }
            if (dismiss) latestOnDismiss.value()
        }
    }
    val isSettlingToDismiss = remember { mutableStateOf(false) }
    val drawerWidthPx = with(LocalDensity.current) { width.toPx().coerceAtLeast(1f) }
    val edgeDirection = if (edge == LandscapeSidePanelEdge.End) 1f else -1f

    val requestDismiss: () -> Unit = {
        if (!isSettlingToDismiss.value) {
            isSettlingToDismiss.value = true
            settleTo(1f, 180, dismiss = true)
        }
    }

    val dragState = rememberDraggableState { delta ->
        closeProgress.floatValue = (closeProgress.floatValue + edgeDirection * delta / drawerWidthPx)
            .coerceIn(0f, 1f)
    }

    LaunchedEffect(visible) {
        isSettlingToDismiss.value = false
        if (visible) {
            settleTo(0f, 220)
        } else {
            animationJob.value?.cancel()
            closeProgress.floatValue = 1f
        }
    }

    DisposableEffect(closeProgress) {
        onDispose { closeProgress.floatValue = 1f }
    }

    if (!visible) return

    Box(
        modifier = modifier
            .width(width)
            .offset {
                IntOffset(
                    x = (edgeDirection * closeProgress.floatValue * drawerWidthPx).roundToInt(),
                    y = 0,
                )
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    isSettlingToDismiss.value = false
                    animationJob.value?.cancel()
                },
                onDragStopped = { velocity ->
                    val closingVelocity = edgeDirection * velocity
                    if (closeProgress.floatValue >= 0.42f || closingVelocity >= 900f) {
                        requestDismiss()
                    } else {
                        settleTo(0f, 180)
                    }
                },
            ),
    ) {
        content(requestDismiss)
    }
}
