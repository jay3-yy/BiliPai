package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox

/** Isolates the refresh indicator's composition-time inset read from the comment rows. */
@Composable
internal fun VideoCommentRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    refreshGestureListState: LazyListState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val latestEnabled by rememberUpdatedState(enabled)
    var refreshAllowedForGesture by remember(refreshGestureListState) { mutableStateOf(true) }
    val observeGestureStart = if (refreshGestureListState != null) {
        Modifier.pointerInput(refreshGestureListState) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // 已经从列表中开始的拖动交给原生回弹，松手后的惯性沿用同一次判断。
                refreshAllowedForGesture = latestEnabled && !refreshGestureListState.canScrollBackward
            }
        }
    } else Modifier
    // 播放器收起时禁用刷新；适配器保留列表组合状态，并在恢复时新建原生刷新手势状态。
    AdaptivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        enabled = enabled && refreshAllowedForGesture,
        allowContentScrollWhileRefreshing = true,
        indicatorTopInset = contentPadding.calculateTopPadding(),
        modifier = modifier.then(observeGestureStart),
        content = content,
    )
}
