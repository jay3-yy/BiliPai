package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

/** Miuix defaults to cross-axis recognition; nested ownership can opt into native gestures. */
@Composable
fun AppHorizontalPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSize: PageSize = PageSize.Fill,
    beyondViewportPageCount: Int = PagerDefaults.BeyondViewportPageCount,
    pageSpacing: Dp = 0.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    flingBehavior: TargetedFlingBehavior? = null,
    userScrollEnabled: Boolean = true,
    reverseLayout: Boolean = false,
    key: ((Int) -> Any)? = null,
    pageNestedScrollConnection: NestedScrollConnection? = null,
    snapPosition: SnapPosition = SnapPosition.Start,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    shouldYieldGesture: () -> Boolean = { false },
    /** Keep Foundation's recognizer attached when a nested pager changes gesture ownership. */
    useNativeGestures: Boolean = false,
    pageContent: @Composable PagerScope.(Int) -> Unit,
) {
    val miuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val useMiuixGestures = miuix && !useNativeGestures
    val enabled = userScrollEnabled && !shouldYieldGesture()
    val resolvedFling = flingBehavior ?: if (miuix) {
        PagerDefaults.flingBehavior(state, snapAnimationSpec = PagerNavigationSpringSpec)
    } else PagerDefaults.flingBehavior(state)
    val resolvedNestedScroll = pageNestedScrollConnection ?: if (useMiuixGestures) {
        PagerGestureNestedScrollConnection
    } else PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
    HorizontalPager(
        state = state,
        modifier = when {
            !enabled -> modifier
            useMiuixGestures -> modifier.appMiuixPagerGesture(state, resolvedFling, reverseLayout)
            else -> modifier.appScrollBoundaryHaptics(state, reverseLayout, Orientation.Horizontal)
        },
        contentPadding = contentPadding, pageSize = pageSize,
        beyondViewportPageCount = beyondViewportPageCount, pageSpacing = pageSpacing,
        verticalAlignment = verticalAlignment, flingBehavior = resolvedFling,
        userScrollEnabled = !useMiuixGestures && enabled, reverseLayout = reverseLayout, key = key,
        pageNestedScrollConnection = resolvedNestedScroll, snapPosition = snapPosition,
        overscrollEffect = overscrollEffect, pageContent = pageContent,
    )
}

/** Miuix 的触摸换页直接驱动 PagerState，需要在手势已由分页接管后观察位移。 */
@Composable
fun Modifier.appMiuixPagerGesture(
    state: PagerState,
    flingBehavior: FlingBehavior,
    reverseLayout: Boolean,
): Modifier {
    val feedback = rememberScrollBoundaryFeedback(state)
    val claimed = remember(state) { booleanArrayOf(false) }
    val reverseDirection = ScrollableDefaults.reverseDirection(
        LocalLayoutDirection.current, Orientation.Horizontal, reverseLayout,
    )
    return observeScrollBoundary(state, feedback, reverseDirection, Orientation.Horizontal)
        .pointerInput(state, feedback, reverseDirection) {
            coroutineScope {
                var settleObserver: Job? = null
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    settleObserver?.cancel()
                    claimed[0] = false
                    var position = state.currentPage + state.currentPageOffsetFraction
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val nextPosition = state.currentPage + state.currentPageOffsetFraction
                        if (claimed[0]) {
                            if (change.pressed) {
                                val delta = change.positionChangeIgnoreConsumed().x
                                feedback.afterScroll(
                                    if (reverseDirection) -delta else delta,
                                    nextPosition != position,
                                )
                            } else {
                                settleObserver = launch {
                                    // 手指抬起后，分页还可能吸附到首项或末项。
                                    withFrameNanos { }
                                    snapshotFlow { state.isScrollInProgress }.first { !it }
                                    val settledPosition = state.currentPage + state.currentPageOffsetFraction
                                    feedback.afterScroll(settledPosition - nextPosition, moved = true)
                                }
                            }
                        }
                        position = nextPosition
                    } while (change.pressed)
                }
            }
        }
        .pagerGestureOverride(
            pagerState = state,
            flingBehavior = flingBehavior,
            onTriggered = {
                feedback.beforeScroll()
                claimed[0] = true
            },
        )
}
