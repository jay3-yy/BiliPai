/** 下拉头像展开的手势、状态与收放动画。 */
package com.android.purebilibili.feature.profile

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.motion.interactiveSnapSpring
import com.android.purebilibili.core.ui.motion.scrollCompactExpansionSpring
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val PROFILE_AVATAR_PULL_THRESHOLD = 0.33f
internal val ProfileAvatarExpandEasing = AppMotionEasing.IosEaseInOut

internal fun profileAvatarExpandDuration(from: Float, to: Float, velocity: Float, density: Float): Int {
    val durationFactor = abs(velocity).coerceIn(1100f * density, 2000f * density) / (1100f * density)
    return (abs(to - from) * 250f / durationFactor).toInt().coerceAtLeast(1)
}

internal enum class ProfileAvatarPhotoLoadStatus { Loading, Ready, Failed }

/** UI-only state scoped to the account and window mode. No retained scope. */
@Stable
internal class ProfileAvatarExpansionState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var overPullPx by mutableFloatStateOf(0f)
        private set
    val visualOffsetPx: Float get() = (offsetPx + overPullPx).coerceAtLeast(0f)
    var viewportWidthPx by mutableIntStateOf(0)
    private var photoLoadResult by mutableStateOf<Pair<String, ProfileAvatarPhotoLoadStatus>?>(null)

    fun photoLoadStatus(url: String): ProfileAvatarPhotoLoadStatus =
        photoLoadResult?.takeIf { it.first == url }?.second ?: ProfileAvatarPhotoLoadStatus.Loading

    fun reportPhotoLoad(url: String, status: ProfileAvatarPhotoLoadStatus) {
        photoLoadResult = url to status
    }
    var entranceSourceSize by mutableStateOf<Dp?>(null)
        private set
    var isPulledDown by mutableStateOf(false)
        private set
    private var animation: Job? = null

    fun fraction(range: Float): Float = if (range > 0f) (offsetPx / range).coerceIn(0f, 1f) else 0f

    private fun stopAnimation() {
        val running = animation
        animation = null
        running?.cancel()
        entranceSourceSize = null
    }

    fun reset() {
        stopAnimation()
        offsetPx = 0f
        overPullPx = 0f
        isPulledDown = false
    }

    fun updateExpandedRange(range: Float) {
        if (!isPulledDown || animation != null) return
        offsetPx = range
        overPullPx = 0f
    }

    /** 完全展开前，拖动距离按一半阻尼换算。 */
    fun dragBy(delta: Float, range: Float): Float {
        animation?.cancel()
        if (isPulledDown && (overPullPx > 0f || offsetPx >= range && delta > 0f)) {
            val previous = overPullPx
            overPullPx = (previous + delta * 0.5f).coerceIn(0f, range.coerceAtLeast(0f) * PROFILE_AVATAR_PULL_THRESHOLD)
            if (overPullPx > 0f) return delta
            // First consume the elastic stretch, then allow this drag to close the photo.
            val remainder = delta + previous / 0.5f
            val oldOffset = offsetPx
            offsetPx = (oldOffset + remainder).coerceIn(0f, range.coerceAtLeast(0f))
            return -previous / 0.5f + offsetPx - oldOffset
        }
        overPullPx = 0f
        val damping = if (isPulledDown) 1f else 0.5f
        val previous = offsetPx
        offsetPx = (previous + delta * damping).coerceIn(0f, range.coerceAtLeast(0f))
        return (offsetPx - previous) / damping
    }

    suspend fun settle(
        expanded: Boolean,
        range: Float,
        velocity: Float,
        density: Float,
        motion: Boolean,
        entranceSourceSize: Dp? = null,
    ) {
        animation?.cancel()
        this.entranceSourceSize = entranceSourceSize
        isPulledDown = expanded && range > 0f
        val target = if (isPulledDown) range else 0f
        if (!motion || offsetPx == target && overPullPx == 0f) {
            offsetPx = target
            overPullPx = 0f
            this.entranceSourceSize = null
            return
        }
        val duration = profileAvatarExpandDuration(fraction(range), if (isPulledDown) 1f else 0f, velocity, density)
        coroutineScope {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                if (entranceSourceSize != null) {
                    animate(offsetPx, target, animationSpec = scrollCompactExpansionSpring()) { value, _ ->
                        offsetPx = value.coerceIn(0f, target)
                        // The photo already fills the width at 1. Extra travel stretches the header vertically.
                        overPullPx = (value - target).coerceAtLeast(0f)
                    }
                } else coroutineScope {
                    launch {
                        animate(offsetPx, target, animationSpec = tween(duration, easing = ProfileAvatarExpandEasing)) { value, _ ->
                            offsetPx = value
                        }
                    }
                    launch {
                        // Main expansion uses a tween. Only extra stretch uses
                        // the app's existing interactive spring, preserving interruption.
                        animate(overPullPx, 0f, animationSpec = interactiveSnapSpring()) { value, _ -> overPullPx = value }
                    }
                }
            }
            animation = job
            try {
                job.join()
            } finally {
                if (animation === job) {
                    animation = null
                    if (entranceSourceSize != null && !isActive) {
                        offsetPx = target
                        overPullPx = 0f
                    }
                    this@ProfileAvatarExpansionState.entranceSourceSize = null
                }
            }
        }
    }
}

@Composable
internal fun rememberProfileAvatarExpansionConnection(
    state: ProfileAvatarExpansionState,
    listState: LazyListState,
    enabled: Boolean,
    rangeProvider: () -> Float,
    photoUrl: String,
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val latestRange = rememberUpdatedState(rangeProvider)
    val latestPhotoUrl = rememberUpdatedState(photoUrl)
    val density = LocalDensity.current.density
    val view = LocalView.current
    val haptics = LocalAppThemeConfig.current.let { it.hapticFeedbackEnabled && it.hapticSettings.isEnabled(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION) }
    val motion = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion() &&
        !com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced()
    return remember(state, listState, enabled, density, motion, scope, view, haptics) {
        object : NestedScrollConnection {
            private fun settle(expanded: Boolean) {
                if (haptics) view.performHapticFeedback(
                    if (expanded) android.view.HapticFeedbackConstants.LONG_PRESS else android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                )
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    state.settle(expanded, latestRange.value(), 0f, density, motion)
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.UserInput || available.y >= 0f || state.offsetPx <= 0f) return Offset.Zero
                val consumed = state.dragBy(available.y, latestRange.value())
                if (state.isPulledDown && state.fraction(latestRange.value()) < PROFILE_AVATAR_PULL_THRESHOLD) settle(false)
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || state.photoLoadStatus(latestPhotoUrl.value) != ProfileAvatarPhotoLoadStatus.Ready || source != NestedScrollSource.UserInput || available.y <= 0f ||
                    listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0 || latestRange.value() <= 0f
                ) return Offset.Zero
                state.dragBy(available.y, latestRange.value())
                if (!state.isPulledDown && state.fraction(latestRange.value()) >= PROFILE_AVATAR_PULL_THRESHOLD) settle(true)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled || state.offsetPx <= 0f) return Velocity.Zero
                state.settle(state.isPulledDown, latestRange.value(), available.y, density, motion)
                return Velocity(0f, available.y)
            }
        }
    }
}
