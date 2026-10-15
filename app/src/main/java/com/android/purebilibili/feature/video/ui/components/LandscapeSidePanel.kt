package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableFloatState
import kotlinx.coroutines.Job
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
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
    enableControlCenterMotion: Boolean = false,
    content: @Composable (requestDismiss: () -> Unit) -> Unit,
) {
    // Only explicitly opted-in glass drawers receive group motion. Comment drawers
    // and non-glass MD3/Miuix retain the original drag limits and tween timings.
    val reduceMotion = rememberSystemReduceMotion()
    val groupMotionEnabled = enableControlCenterMotion && LocalAppThemeConfig.current.liquidGlassEnabled &&
        LocalComponentMotionEnabled.current && !reduceMotion
    val groupProgress = remember(closeProgress) { mutableFloatStateOf(closeProgress.floatValue) }
    val activeProgress = if (groupMotionEnabled) groupProgress else closeProgress
    fun updateProgress(value: Float) {
        activeProgress.floatValue = value
        // Layout reservations and scrims must never receive the negative over-pull.
        closeProgress.floatValue = value.coerceIn(0f, 1f)
    }
    val scope = rememberCoroutineScope()
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    val animationJob = remember { mutableStateOf<Job?>(null) }
    fun settleTo(target: Float, durationMillis: Int, dismiss: Boolean = false, velocity: Float = 0f) {
        animationJob.value?.cancel()
        animationJob.value = scope.launch {
            val spec: AnimationSpec<Float> = if (groupMotionEnabled) {
                ControlCenterMotionSpec.springSpec(closing = dismiss)
            } else {
                tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
            }
            animate(
                initialValue = activeProgress.floatValue,
                targetValue = target,
                initialVelocity = if (groupMotionEnabled) ControlCenterMotionSpec.releaseVelocity(velocity) else 0f,
                animationSpec = spec,
            ) { value, _ -> updateProgress(value) }
            updateProgress(target)
            if (dismiss) latestOnDismiss.value()
        }
    }
    val isSettlingToDismiss = remember { mutableStateOf(false) }
    val drawerWidthPx = with(LocalDensity.current) { width.toPx().coerceAtLeast(1f) }
    val edgeDirection = if (edge == LandscapeSidePanelEdge.End) 1f else -1f

    fun dismissWithVelocity(velocity: Float = 0f) {
        if (!isSettlingToDismiss.value) {
            isSettlingToDismiss.value = true
            settleTo(1f, 180, dismiss = true, velocity = velocity)
        }
    }
    val requestDismiss: () -> Unit = { dismissWithVelocity() }

    val dragState = rememberDraggableState { delta ->
        val change = edgeDirection * delta / drawerWidthPx
        val current = activeProgress.floatValue
        updateProgress(
            if (groupMotionEnabled) {
                1f - ControlCenterMotionSpec.dragProgress(1f - current, -change)
            } else (current + change).coerceIn(0f, 1f)
        )
    }

    LaunchedEffect(visible, groupMotionEnabled, closeProgress) {
        activeProgress.floatValue = closeProgress.floatValue
        isSettlingToDismiss.value = false
        if (visible) {
            settleTo(0f, 220)
        } else {
            animationJob.value?.cancel()
            updateProgress(1f)
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
                    val normalizedVelocity = closingVelocity / drawerWidthPx
                    val shouldDismiss = if (groupMotionEnabled) {
                        ControlCenterMotionSpec.shouldClose(1f - activeProgress.floatValue, -normalizedVelocity)
                    } else closeProgress.floatValue >= 0.42f || closingVelocity >= 900f
                    if (shouldDismiss) {
                        dismissWithVelocity(normalizedVelocity)
                    } else {
                        settleTo(0f, 180, velocity = closingVelocity / drawerWidthPx)
                    }
                },
            ),
    ) {
        if (groupMotionEnabled) {
            val groupMotion = remember(groupProgress, edgeDirection) {
                LandscapeControlCenterGroups(groupProgress, edgeDirection)
            }
            CompositionLocalProvider(LocalLandscapeControlCenterGroups provides groupMotion) {
                content(requestDismiss)
            }
        } else {
            content(requestDismiss)
        }
    }
}

private class LandscapeControlCenterGroups(val progress: MutableFloatState, val direction: Float)
private val LocalLandscapeControlCenterGroups = staticCompositionLocalOf<LandscapeControlCenterGroups?> { null }

/** Groups read the same drawer clock only in layout/draw, including during interrupted springs. */
@Composable
internal fun Modifier.landscapeControlCenterGroup(index: Int): Modifier {
    val motion = LocalLandscapeControlCenterGroups.current ?: return this
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val stretch = (-motion.progress.floatValue).coerceIn(0f, ControlCenterMotionSpec.MaxOverExpansion)
            val gap = (32.dp.toPx() * stretch).roundToInt()
            layout(placeable.width, (placeable.height + gap).coerceIn(constraints.minHeight, constraints.maxHeight)) {
                placeable.placeRelative(0, 0)
            }
        }
        .graphicsLayer {
            val close = motion.progress.floatValue.coerceIn(0f, 1f)
            val stretch = (-motion.progress.floatValue).coerceIn(0f, ControlCenterMotionSpec.MaxOverExpansion)
            alpha = (1f - close) * (1f - close)
            translationX = motion.direction * close * (8 * index).dp.toPx()
            scaleX = 1f + stretch * 0.12f
            scaleY = 1f - stretch * 0.08f
        }
}

/** Read the occupied drawer width during measurement so drag updates only relayout content. */
internal fun Modifier.landscapeEndDrawerPadding(widthProvider: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val inset = widthProvider().roundToPx().coerceIn(0, constraints.maxWidth)
        val placeable = measurable.measure(constraints.offset(horizontal = -inset))
        layout(placeable.width + inset, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
