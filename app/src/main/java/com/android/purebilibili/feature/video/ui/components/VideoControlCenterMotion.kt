package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One gesture clock for all groups; native sheet dismissal/scrolling stays with its host. */
internal class VideoControlCenterMotion {
    var progress by mutableFloatStateOf(0f)
        private set
    var animationJob: Job? = null
    var closing = false
        private set

    fun beginDrag() {
        animationJob?.cancel()
        closing = false
    }

    fun dragBy(delta: Float) {
        progress = (progress + delta).coerceIn(0f, 1.5f)
    }

    suspend fun settle(target: Float, velocity: Float = 0f) {
        closing = target == 0f
        animate(
            initialValue = progress,
            targetValue = target,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = if (closing) 1f else 0.72f,
                stiffness = 300f,
                visibilityThreshold = 0.001f,
            ),
        ) { value, _ -> progress = value }
        progress = target
    }
}

internal fun shouldCloseVideoControlCenter(progress: Float, openingVelocity: Float): Boolean =
    when {
        openingVelocity <= -2.5f -> true
        openingVelocity >= 2.5f -> false
        else -> progress < 0.65f
    }

@Composable
internal fun rememberVideoControlCenterMotion(): VideoControlCenterMotion? {
    val reduceMotion = rememberSystemReduceMotion()
    // This is exclusively a glass interaction. MD3/Miuix without glass retain their
    // original handle, lazy item structure, spacing and native sheet gestures.
    val enabled = LocalAppThemeConfig.current.liquidGlassEnabled &&
        LocalComponentMotionEnabled.current && !reduceMotion
    if (!enabled) return null
    val motion = remember { VideoControlCenterMotion() }
    LaunchedEffect(motion) {
        motion.animationJob = launch { motion.settle(1f) }
    }
    return motion
}

/** Only the header owns this gesture, leaving switches, sliders and list scrolling alone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoControlCenterHandle(
    motion: VideoControlCenterMotion,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val latestDismiss = rememberUpdatedState(onDismiss)
    val dragRangePx = with(LocalDensity.current) { 240.dp.toPx() }
    fun settle(close: Boolean, velocity: Float = 0f) {
        motion.animationJob?.cancel()
        motion.animationJob = scope.launch {
            motion.settle(if (close) 0f else 1f, velocity)
            if (close) latestDismiss.value()
        }
    }
    Column(
        modifier = modifier.draggable(
            state = rememberDraggableState { delta -> motion.dragBy(-delta / dragRangePx) },
            orientation = Orientation.Vertical,
            onDragStarted = { motion.beginDrag() },
            onDragStopped = { velocity ->
                val openingVelocity = -velocity / dragRangePx
                settle(shouldCloseVideoControlCenter(motion.progress, openingVelocity), openingVelocity)
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetDefaults.DragHandle()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("播放设置", modifier = Modifier.weight(1f))
            AppTextButton(onClick = { if (!motion.closing) settle(close = true) }) {
                AppText("关闭")
            }
        }
    }
}

/** Built once per lazy DSL pass; late-composed items share the current clock, not a new entrance. */
internal class VideoControlCenterGroups(
    private val scope: LazyListScope,
    private val motion: VideoControlCenterMotion?,
) {
    private var groupIndex = 0

    fun item(content: @Composable () -> Unit) {
        val index = groupIndex++
        scope.item {
            if (motion == null) {
                content()
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val stretch = (motion.progress - 1f).coerceIn(0f, 0.5f)
                            val gap = (32.dp.toPx() * stretch).roundToInt()
                            layout(placeable.width, constraints.constrainHeight(placeable.height + gap)) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                        .graphicsLayer {
                            val progress = motion.progress
                            val visible = progress.coerceIn(0f, 1f)
                            val stretch = (progress - 1f).coerceIn(0f, 0.5f)
                            alpha = visible * visible
                            translationY = (1f - visible) * (24 + index.coerceAtMost(5) * 5).dp.toPx()
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            scaleX = 1f - stretch * 0.12f
                            scaleY = 1f + stretch * 0.16f
                        },
                ) { content() }
            }
        }
    }
}
