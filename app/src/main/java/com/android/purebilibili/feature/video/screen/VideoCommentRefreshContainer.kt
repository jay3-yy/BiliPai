package com.android.purebilibili.feature.video.screen

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.motion.softLandingSpring
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Keeps the native refresh container mounted while a comment gesture reaches the top. */
@Composable
internal fun VideoCommentRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    refreshGestureListState: LazyListState? = null,
    allowContentScrollWhileRefreshing: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    AdaptivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        enabled = enabled,
        allowContentScrollWhileRefreshing = allowContentScrollWhileRefreshing,
        indicatorTopInset = contentPadding.calculateTopPadding(),
        modifier = modifier,
    ) {
        if (refreshGestureListState == null) {
            content()
        } else {
            VideoCommentTopRebound(refreshGestureListState, content)
        }
    }
}

@Composable
private fun VideoCommentTopRebound(
    listState: LazyListState,
    content: @Composable BoxScope.() -> Unit,
) {
    val motionGate = rememberDecorativeAnimationGate()
    val motionActive = motionGate.active
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val connection = remember(listState, scope, density, motionGate) {
        with(density) {
            VideoCommentTopReboundConnection(listState, scope, 28.dp.toPx(), 650.dp.toPx()) { motionGate.active }
        }
    }
    DisposableEffect(connection, motionActive) {
        onDispose { connection.resetMotion() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(motionGate.modifier)
            .pointerInput(connection) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    connection.beginGesture()
                }
            }
            .nestedScroll(connection),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { translationY = connection.offsetPx },
            content = content,
        )
    }
}

private class VideoCommentTopReboundConnection(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val pullLimitPx: Float,
    private val velocityLimitPxPerSecond: Float,
    private val motionAllowed: () -> Boolean,
) : NestedScrollConnection {
    private var returningToTop = false
    private var pullPx = 0f
    private var releaseJob: Job? = null

    var offsetPx by mutableFloatStateOf(0f)
        private set

    fun beginGesture() {
        returningToTop = listState.canScrollBackward
        if (returningToTop || releaseJob?.isActive != true) resetMotion()
    }

    fun resetMotion() {
        releaseJob?.cancel()
        updatePull(0f)
    }

    private fun updatePull(pixels: Float) {
        pullPx = pixels.coerceIn(0f, pullLimitPx * 4f)
        offsetPx = pullLimitPx * pullPx / (pullLimitPx * 2f + pullPx)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f || pullPx == 0f) {
            return Offset.Zero
        }
        val released = available.y.coerceAtLeast(-pullPx)
        updatePull(pullPx + released)
        return Offset(0f, released)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!returningToTop || listState.canScrollBackward || available.y <= 0f ||
            source != NestedScrollSource.UserInput
        ) return Offset.Zero

        // 先于原生刷新容器接收本次回顶拖动的余量。惯性余量由 onPostFling 接收。
        if (motionAllowed()) updatePull(pullPx + available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!returningToTop) return Velocity.Zero
        returningToTop = false
        if (listState.canScrollBackward) return Velocity.Zero
        val velocityY = available.y.coerceAtLeast(0f)
        if (motionAllowed() && (pullPx > 0f || velocityY > 0f)) {
            pullPx = 0f
            releaseJob = scope.launch {
                animate(
                    initialValue = offsetPx,
                    targetValue = 0f,
                    initialVelocity = (velocityY * 0.4f).coerceAtMost(velocityLimitPxPerSecond),
                    animationSpec = softLandingSpring(),
                ) { value, _ ->
                    offsetPx = value.coerceAtLeast(0f)
                }
                offsetPx = 0f
            }
        }
        return Velocity(0f, velocityY)
    }
}
