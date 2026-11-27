package com.android.purebilibili.core.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerDefaults
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import com.android.purebilibili.core.ui.components.ScrollBoundaryFeedback
import com.android.purebilibili.core.ui.components.appMiuixPagerGesture
import com.android.purebilibili.core.ui.components.rememberScrollBoundaryFeedback
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.semantics.scrollToIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sign

internal enum class PagerGestureDirection {
    UNDECIDED,
    HORIZONTAL,
    VERTICAL,
}

internal const val PAGER_HORIZONTAL_DOMINANCE_RATIO = 1.5f
internal const val PAGER_AMBIGUOUS_DIRECTION_SLOP_MULTIPLIER = 1.5f
internal const val HOME_PAGER_HORIZONTAL_LOCK_SLOP_MULTIPLIER = 1.5f
internal const val PAGER_RELEASE_POSITION_THRESHOLD = 0.2f
internal const val PAGER_RELEASE_MAX_POSITION_THRESHOLD_DP = 96f
internal const val PAGER_RELEASE_MIN_FLING_VELOCITY_DP = 300f
private val PiliPlusPagerSettleSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 438.65f,
)

internal fun resolveVerticalPriorityPagerGestureDirection(
    totalX: Float,
    totalY: Float,
    touchSlop: Float,
    horizontalDominanceRatio: Float = PAGER_HORIZONTAL_DOMINANCE_RATIO,
    ambiguousDirectionSlopMultiplier: Float = PAGER_AMBIGUOUS_DIRECTION_SLOP_MULTIPLIER,
    horizontalLockSlopMultiplier: Float = 1f,
): PagerGestureDirection {
    val systemTouchSlop = touchSlop.coerceAtLeast(0f)
    val totalDistanceSquared = totalX * totalX + totalY * totalY
    if (totalDistanceSquared == 0f || totalDistanceSquared < systemTouchSlop * systemTouchSlop) {
        return PagerGestureDirection.UNDECIDED
    }

    val horizontalDistance = abs(totalX)
    val verticalDistance = abs(totalY)
    if (horizontalDistance >= verticalDistance * horizontalDominanceRatio.coerceAtLeast(1f)) {
        val horizontalLockSlop = systemTouchSlop * horizontalLockSlopMultiplier.coerceAtLeast(1f)
        return if (horizontalDistance >= horizontalLockSlop) {
            PagerGestureDirection.HORIZONTAL
        } else {
            PagerGestureDirection.UNDECIDED
        }
    }

    // 竖向与斜向共用同一条意图宽限距离：手指落下时的自然竖向漂移若立即锁死
    // VERTICAL，横滑切页会在手势开头就被吞掉（不可逆），表现为「左右滑切不动
    // 评论」。超过宽限后，非横向主导的位移一律归属竖向内容。
    val ambiguousDirectionSlop = systemTouchSlop *
        ambiguousDirectionSlopMultiplier.coerceAtLeast(1f)
    return if (totalDistanceSquared < ambiguousDirectionSlop * ambiguousDirectionSlop) {
        PagerGestureDirection.UNDECIDED
    } else {
        PagerGestureDirection.VERTICAL
    }
}

internal fun resolvePagerInitialHorizontalDelta(
    totalX: Float,
    touchSlop: Float,
): Float {
    val consumedSlop = touchSlop.coerceAtLeast(0f).coerceAtMost(abs(totalX))
    return totalX - sign(totalX) * consumedSlop
}

internal fun resolvePagerReleaseTargetPage(
    startPage: Int,
    pageCount: Int,
    pageSizePx: Float,
    scrollDeltaPx: Float,
    scrollVelocityPxPerSecond: Float,
    positionalThresholdFraction: Float = PAGER_RELEASE_POSITION_THRESHOLD,
    maximumPositionThresholdPx: Float = Float.POSITIVE_INFINITY,
    minimumFlingVelocityPxPerSecond: Float,
): Int {
    if (pageCount <= 0) return 0
    val boundedStartPage = startPage.coerceIn(0, pageCount - 1)
    val isFastFling = scrollVelocityPxPerSecond != 0f &&
        abs(scrollVelocityPxPerSecond) >= minimumFlingVelocityPxPerSecond.coerceAtLeast(0f)
    val positionThresholdPx = minOf(
        pageSizePx * positionalThresholdFraction.coerceIn(0f, 1f),
        maximumPositionThresholdPx.coerceAtLeast(0f),
    )
    val crossedPositionThreshold = pageSizePx > 0f &&
        abs(scrollDeltaPx) >= positionThresholdPx
    val dragDirection = sign(scrollDeltaPx)
    val velocityDirection = sign(scrollVelocityPxPerSecond)
    if (isFastFling) {
        // 拖动方向与抬手速度相反表示回撤/取消，回弹到起始页，不越过起始页跳到反方向页面。
        if (dragDirection != 0f && velocityDirection != dragDirection) {
            return boundedStartPage
        }
        return (boundedStartPage + if (velocityDirection > 0f) 1 else -1)
            .coerceIn(0, pageCount - 1)
    }
    if (!crossedPositionThreshold || dragDirection == 0f) return boundedStartPage
    return (boundedStartPage + if (dragDirection > 0f) 1 else -1)
        .coerceIn(0, pageCount - 1)
}

/**
 * Gives a vertical child list priority over a surrounding [PagerState].
 *
 * The pager's built-in touch scrolling must be disabled. This modifier observes the complete
 * two-dimensional pointer stream without consuming it, then takes ownership only after the
 * accumulated gesture is clearly horizontal. Vertical and ambiguous gestures remain untouched so
 * the child LazyColumn/LazyGrid can continue handling them normally.
 *
 * [shouldYield] aborts this detector without consuming the pointer stream, so a nested
 * horizontal pager (such as the home hero carousel) can own the same gesture.
 */
internal fun shouldTriggerPagerBackwardEdgeSwipe(
    canScrollBackward: Boolean,
    initialScrollDelta: Float,
): Boolean = !canScrollBackward && initialScrollDelta < 0f

internal fun Modifier.verticalPriorityHorizontalPagerSwipe(
    state: PagerState,
    enabled: Boolean = true,
    reverseLayout: Boolean = false,
    horizontalLockSlopMultiplier: Float = 1f,
    forceVerticalPriority: Boolean = true,
    dragInteractionSource: MutableInteractionSource? = null,
    shouldYield: () -> Boolean = { false },
    settleAnimationSpec: AnimationSpec<Float>? = null,
    exposePageActions: Boolean = true,
    onEdgeBackwardSwipe: (() -> Unit)? = null,
): Modifier = composed {
    if (!enabled || (state.pageCount < 2 && onEdgeBackwardSwipe == null)) return@composed this
    // Non-list surfaces can retain Miuix's cross-axis takeover behavior.
    if (!forceVerticalPriority && LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        if (shouldYield()) return@composed this
        return@composed appMiuixPagerGesture(
            state = state,
            flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = PagerNavigationSpringSpec),
            reverseLayout = reverseLayout,
        )
    }

    val boundaryFeedback = rememberScrollBoundaryFeedback(state)
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val latestShouldYield = rememberUpdatedState(shouldYield)
    val latestOnEdgeBackwardSwipe = rememberUpdatedState(onEdgeBackwardSwipe)
    val latestDragInteractionSource = rememberUpdatedState(dragInteractionSource)
    val latestSettleAnimationSpec = rememberUpdatedState(
        settleAnimationSpec ?: if (!liquidGlassEnabled) PiliPlusPagerSettleSpringSpec else null
    )
    val pageActionScope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val minimumFlingVelocityPx = with(density) {
        PAGER_RELEASE_MIN_FLING_VELOCITY_DP.dp.toPx()
    }
    val edgeBackwardTriggerDistancePx = with(density) { 16.dp.toPx() }
    val maximumPositionThresholdPx = with(density) {
        PAGER_RELEASE_MAX_POSITION_THRESHOLD_DP.dp.toPx()
    }
    val reverseDirection = remember(layoutDirection, reverseLayout) {
        ScrollableDefaults.reverseDirection(
            layoutDirection = layoutDirection,
            orientation = Orientation.Horizontal,
            reverseScrolling = reverseLayout,
        )
    }

    val pageActions = if (exposePageActions) {
        Modifier.semantics {
            // The built-in pager input is disabled, so expose page navigation here.
            pageLeft {
                if (!state.canScrollBackward) false
                else {
                    pageActionScope.launch { state.animateScrollToPage(state.currentPage - 1) }
                    true
                }
            }
            pageRight {
                if (!state.canScrollForward) false
                else {
                    pageActionScope.launch { state.animateScrollToPage(state.currentPage + 1) }
                    true
                }
            }
            scrollToIndex { page ->
                require(page in 0 until state.pageCount) { "Page index out of bounds: $page" }
                pageActionScope.launch { state.scrollToPage(page) }
                true
            }
        }
    } else Modifier

    then(pageActions).pointerInput(
        state,
        boundaryFeedback,
        reverseDirection,
        minimumFlingVelocityPx,
        maximumPositionThresholdPx,
        horizontalLockSlopMultiplier,
    ) {
        val dragCoroutineScope = CoroutineScope(currentCoroutineContext())
        val velocityTracker = VelocityTracker()
        awaitEachGesture gesture@{
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            if (latestShouldYield.value()) return@gesture
            velocityTracker.resetTracking()
            velocityTracker.addPointerInputChange(down)

            var totalDrag = Offset.Zero
            var direction = PagerGestureDirection.UNDECIDED
            var trackedPointerId = down.id
            var horizontalLockChange: PointerInputChange? = null

            while (direction == PagerGestureDirection.UNDECIDED) {
                // Observe after descendants have had a chance to consume the gesture. This
                // allows nested horizontalScroll/LazyRow surfaces (for example the video
                // creator-team row) to keep ownership instead of being hijacked by the
                // surrounding content pager.
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (latestShouldYield.value() || event.changes.count { it.pressed } > 1) return@gesture
                val change = event.changes.firstOrNull { it.id == trackedPointerId }
                    ?: event.changes.firstOrNull { it.pressed }
                    ?: return@gesture
                if (change.id != trackedPointerId) {
                    trackedPointerId = change.id
                    velocityTracker.resetTracking()
                }

                if (!change.pressed) return@gesture
                val delta = change.positionChangeIgnoreConsumed()
                if (delta == Offset.Zero) continue
                if (change.isConsumed) return@gesture
                totalDrag += delta
                velocityTracker.addPointerInputChange(change)
                direction = resolveVerticalPriorityPagerGestureDirection(
                    totalX = totalDrag.x,
                    totalY = totalDrag.y,
                    touchSlop = viewConfiguration.touchSlop,
                    horizontalLockSlopMultiplier = horizontalLockSlopMultiplier,
                )
                if (direction == PagerGestureDirection.HORIZONTAL) {
                    horizontalLockChange = change
                }
            }

            if (direction != PagerGestureDirection.HORIZONTAL) return@gesture
            horizontalLockChange?.consume()
            val gestureStartPage = state.currentPage
            val initialPageOffsetPx = state.currentPageOffsetFraction * state.layoutInfo.pageSize

            val initialHorizontalDelta = resolvePagerInitialHorizontalDelta(
                totalX = totalDrag.x,
                touchSlop = viewConfiguration.touchSlop,
            )
            val scrollDirectionMultiplier = if (reverseDirection) -1f else 1f
            val initialScrollDelta = initialHorizontalDelta * scrollDirectionMultiplier
            val backwardEdgeSwipe = latestOnEdgeBackwardSwipe.value
            if (backwardEdgeSwipe != null &&
                shouldTriggerPagerBackwardEdgeSwipe(
                    canScrollBackward = state.canScrollBackward,
                    initialScrollDelta = initialScrollDelta,
                )
            ) {
                var accumulatedBackwardPx = -initialScrollDelta
                var triggered = accumulatedBackwardPx >= edgeBackwardTriggerDistancePx
                if (triggered) {
                    backwardEdgeSwipe()
                }
                var gestureCancelled = false
                var released = false
                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (latestShouldYield.value()) {
                        gestureCancelled = true
                        released = true
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == trackedPointerId }
                        ?: event.changes.firstOrNull { it.pressed }
                    if (change == null) {
                        gestureCancelled = true
                        released = true
                        continue
                    }
                    val pointerSwitched = change.id != trackedPointerId
                    if (pointerSwitched) {
                        trackedPointerId = change.id
                        velocityTracker.resetTracking()
                    }
                    velocityTracker.addPointerInputChange(change)
                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                        released = true
                    } else {
                        val horizontalDelta = if (pointerSwitched) 0f else change.positionChangeIgnoreConsumed().x
                        change.consume()
                        if (horizontalDelta != 0f) {
                            accumulatedBackwardPx += -(horizontalDelta * scrollDirectionMultiplier)
                            if (!triggered && accumulatedBackwardPx >= edgeBackwardTriggerDistancePx) {
                                triggered = true
                                backwardEdgeSwipe()
                            }
                        }
                    }
                }
                if (!triggered && !gestureCancelled) {
                    val maxFlingVelocity = viewConfiguration.maximumFlingVelocity
                    val releaseVelocity = velocityTracker.calculateVelocity(
                        Velocity(maxFlingVelocity, maxFlingVelocity)
                    ).x * scrollDirectionMultiplier
                    if (-releaseVelocity >= minimumFlingVelocityPx && accumulatedBackwardPx > 0f) {
                        backwardEdgeSwipe()
                    }
                }
                return@gesture
            }
            val dragSession = PagerDragScrollSession(boundaryFeedback)
            val scrollJob = dragCoroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                var release: PagerDragRelease? = null
                state.scroll(MutatePriority.UserInput) {
                    release = with(dragSession) {
                        awaitRelease(
                            initialDelta = initialHorizontalDelta * scrollDirectionMultiplier,
                        )
                    }
                }
                release?.let { dragRelease ->
                    val targetPage = resolvePagerReleaseTargetPage(
                        startPage = gestureStartPage,
                        pageCount = state.pageCount,
                        pageSizePx = state.layoutInfo.pageSize.toFloat(),
                        scrollDeltaPx = dragRelease.scrollDeltaPx,
                        scrollVelocityPxPerSecond = dragRelease.velocityPxPerSecond,
                        maximumPositionThresholdPx = maximumPositionThresholdPx,
                        minimumFlingVelocityPxPerSecond = minimumFlingVelocityPx,
                    )
                    val positionBeforeSettle = state.currentPage + state.currentPageOffsetFraction
                    boundaryFeedback.beforeScroll()
                    val animationSpec = latestSettleAnimationSpec.value
                    if (animationSpec == null) {
                        state.animateScrollToPage(targetPage)
                    } else {
                        val pageStridePx = (state.layoutInfo.pageSize + state.layoutInfo.pageSpacing).toFloat()
                        if (pageStridePx > 0f) {
                            state.scroll {
                                val targetOffsetPx =
                                    ((targetPage - state.currentPage) - state.currentPageOffsetFraction) * pageStridePx
                                var previousValue = 0f
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = targetOffsetPx,
                                    initialVelocity = dragRelease.velocityPxPerSecond,
                                    animationSpec = animationSpec,
                                ) { value, _ ->
                                    val clampedValue = if (targetOffsetPx >= 0f) {
                                        value.coerceAtMost(targetOffsetPx)
                                    } else {
                                        value.coerceAtLeast(targetOffsetPx)
                                    }
                                    val delta = clampedValue - previousValue
                                    previousValue = clampedValue
                                    if (delta != 0f) {
                                        scrollBy(delta)
                                    }
                                }
                            }
                        } else {
                            state.animateScrollToPage(targetPage, animationSpec = animationSpec)
                        }
                    }
                    val positionAfterSettle = state.currentPage + state.currentPageOffsetFraction
                    boundaryFeedback.afterScroll(positionAfterSettle - positionBeforeSettle, moved = true)
                }
            }

            var accumulatedScrollDelta = initialPageOffsetPx + initialHorizontalDelta * scrollDirectionMultiplier
            var releasedNormally = false
            var gestureCancelled = false
            // Custom state.scroll does not publish Pager drag interactions. Report the
            // pointer lifetime separately from the subsequent settle animation.
            val dragSource = latestDragInteractionSource.value
            val dragInteraction = DragInteraction.Start()
            dragSource?.tryEmit(dragInteraction)
            try {
                var released = false
                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (latestShouldYield.value()) {
                        gestureCancelled = true
                        released = true
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == trackedPointerId }
                        ?: event.changes.firstOrNull { it.pressed }
                    if (change == null) {
                        gestureCancelled = true
                        released = true
                        continue
                    }
                    val pointerSwitched = change.id != trackedPointerId
                    if (pointerSwitched) {
                        trackedPointerId = change.id
                        velocityTracker.resetTracking()
                    }
                    // addPointerInputChange 会纳入 historical 采样、忽略抬手瞬间的触控中心偏移，
                    // 并在停顿超过 40ms 后清空陈旧速度，避免抬手抖动制造反向弹跳。
                    velocityTracker.addPointerInputChange(change)

                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                        released = true
                    } else {
                        val horizontalDelta = if (pointerSwitched) 0f else change.positionChangeIgnoreConsumed().x
                        change.consume()
                        if (horizontalDelta != 0f) {
                            val scrollDelta = horizontalDelta * scrollDirectionMultiplier
                            accumulatedScrollDelta += scrollDelta
                            dragSession.dragBy(scrollDelta)
                        }
                    }
                }

                val maxFlingVelocity = viewConfiguration.maximumFlingVelocity
                val releaseVelocityX = if (gestureCancelled) {
                    0f
                } else {
                    velocityTracker.calculateVelocity(
                        Velocity(maxFlingVelocity, maxFlingVelocity)
                    ).x * scrollDirectionMultiplier
                }
                dragSession.release(
                    PagerDragRelease(
                        velocityPxPerSecond = releaseVelocityX,
                        scrollDeltaPx = if (gestureCancelled) 0f else accumulatedScrollDelta,
                    ),
                )
                releasedNormally = true
            } finally {
                dragSource?.tryEmit(
                    if (releasedNormally && !gestureCancelled) DragInteraction.Stop(dragInteraction)
                    else DragInteraction.Cancel(dragInteraction)
                )
                if (!releasedNormally) {
                    dragSession.cancel()
                    if (!scrollJob.isCompleted) scrollJob.cancel()
                }
            }
        }
    }
}

private data class PagerDragRelease(
    val velocityPxPerSecond: Float,
    val scrollDeltaPx: Float,
)

private class PagerDragScrollSession(private val boundaryFeedback: ScrollBoundaryFeedback) {
    private var scrollScope: ScrollScope? = null
    private var releaseContinuation: CancellableContinuation<PagerDragRelease>? = null

    suspend fun ScrollScope.awaitRelease(initialDelta: Float): PagerDragRelease =
        suspendCancellableCoroutine { continuation ->
            scrollScope = this
            releaseContinuation = continuation
            dragBy(initialDelta)
            continuation.invokeOnCancellation {
                scrollScope = null
                releaseContinuation = null
            }
        }

    fun dragBy(delta: Float) {
        val scope = scrollScope ?: return
        if (delta == 0f) return
        boundaryFeedback.beforeScroll()
        val consumed = scope.scrollBy(delta)
        boundaryFeedback.afterScroll(delta, moved = consumed != 0f)
    }

    fun release(release: PagerDragRelease) {
        val continuation = releaseContinuation ?: return
        releaseContinuation = null
        scrollScope = null
        if (continuation.isActive) continuation.resume(release)
    }

    fun cancel() {
        val continuation = releaseContinuation ?: return
        releaseContinuation = null
        scrollScope = null
        continuation.cancel()
    }
}
