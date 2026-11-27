package com.android.purebilibili.feature.video.ui.feedback

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.rememberRichHapticFeedback

internal const val VIDEO_TRIPLE_HOLD_MILLIS = 900
private const val VIDEO_TRIPLE_CANCEL_MILLIS = 180

internal fun resolveVideoTripleHoldProgress(elapsedNanos: Long): Float =
    (elapsedNanos.coerceAtLeast(0L) / (VIDEO_TRIPLE_HOLD_MILLIS * 1_000_000f)).coerceIn(0f, 1f)

/** UI-only hold state, shared by detail/tablet actions and the portrait player. */
@Stable
internal class VideoTriplePressState {
    var isPressing by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var completed by mutableStateOf(false)
        private set

    fun start() {
        if (isPressing) return
        completed = false
        progress = 0f
        isPressing = true
    }

    fun release() {
        isPressing = false
    }

    internal fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    internal fun complete(): Boolean {
        if (!isPressing || completed || progress < 1f) return false
        completed = true
        isPressing = false
        return true
    }
}

@Composable
internal fun rememberVideoTriplePressState(
    identity: Any?,
    enabled: Boolean,
    onComplete: () -> Unit,
): VideoTriplePressState {
    val state = remember(identity) { VideoTriplePressState() }
    val latestOnComplete by rememberUpdatedState(onComplete)
    val richHaptics = rememberRichHapticFeedback(AppHapticScene.VIDEO_ACTIONS)
    val cadence = remember(identity) { TriplePressHapticCadenceTracker() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.release()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.release()
        }
    }
    LaunchedEffect(state, enabled, state.isPressing) {
        if (!enabled) state.release()
        if (enabled && state.isPressing) {
            cadence.reset()
            // This is an intentional hold, not a decorative animation. System animation
            // duration scaling must never turn a long press into an immediate submission.
            val startNanos = withFrameNanos { it }
            while (state.progress < 1f) {
                val nextProgress = withFrameNanos { resolveVideoTripleHoldProgress(it - startNanos) }
                state.updateProgress(nextProgress)
                val cue = cadence.onProgress(nextProgress)
                if (cue == TriplePressHapticCue.ChargeTick) {
                    performTriplePressRichHapticCue(richHaptics, cue, nextProgress)
                }
            }
            if (state.complete()) {
                performTriplePressRichHapticCue(richHaptics, TriplePressHapticCue.Complete, 1f)
                latestOnComplete()
            }
        } else {
            cadence.reset()
            animate(
                initialValue = state.progress,
                targetValue = 0f,
                animationSpec = tween(VIDEO_TRIPLE_CANCEL_MILLIS, easing = AppMotionEasing.M1Standard),
            ) { value, _ -> state.updateProgress(value) }
        }
    }
    return state
}
