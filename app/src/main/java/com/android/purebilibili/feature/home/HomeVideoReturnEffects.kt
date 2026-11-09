package com.android.purebilibili.feature.home

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.purebilibili.core.util.CardPositionManager

/** 返回交互立即恢复；仅保留现有的卡片进场保护窗和结束通知。 */
@Composable
internal fun HomeVideoReturnEffects(
    state: HomeReturnState,
    pageOnScreen: Boolean,
    isReturningFromVideoDetail: Boolean,
    returnAnimationSuppressionDurationMs: Long,
    cardTransitionEnabled: Boolean,
    isQuickReturningFromVideoDetail: Boolean,
    isTabletLayout: Boolean,
    cardAnimationEnabled: Boolean,
    onReturnAnimationConsumed: () -> Unit,
) {
    val latestOnConsumed by rememberUpdatedState(onReturnAnimationConsumed)
    val windowLifecycle = (LocalActivity.current as? LifecycleOwner)?.lifecycle
        ?: LocalLifecycleOwner.current.lifecycle
    val lifecycleState by windowLifecycle.currentStateAsState()
    val sourceVisible = pageOnScreen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(state, sourceVisible) {
        state.onHomeVisibilityChanged(sourceVisible)
    }
    // 连续打开独立播放窗口时，返回标记可能一直为 true；重新曝光后必须重启完成通知。
    LaunchedEffect(state, sourceVisible, isReturningFromVideoDetail, returnAnimationSuppressionDurationMs) {
        if (!sourceVisible || !isReturningFromVideoDetail) return@LaunchedEffect
        val startElapsedMs = SystemClock.elapsedRealtime()
        state.finishVideoReturn(returnAnimationSuppressionDurationMs) {
            val actualDurationMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
            val isQuickReturn = isQuickReturningFromVideoDetail
            val sharedTransitionReady = cardTransitionEnabled &&
                CardPositionManager.lastClickedCardBounds != null && CardPositionManager.isCardFullyVisible
            latestOnConsumed()
            val builtinPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledCount()
            val playerPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledPlayerPlugins().size
            val feedPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledFeedPlugins().size
            val danmakuPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledDanmakuPlugins().size
            val jsonEnabledPlugins = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled }
            val jsonFeedPluginEnabledCount = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled && it.plugin.type == "feed" }
            val jsonDanmakuPluginEnabledCount = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled && it.plugin.type == "danmaku" }
            com.android.purebilibili.core.util.AnalyticsHelper.logHomeReturnAnimationPerformance(
                actualDurationMs = actualDurationMs,
                plannedSuppressionMs = returnAnimationSuppressionDurationMs,
                sharedTransitionEnabled = cardTransitionEnabled,
                sharedTransitionReady = sharedTransitionReady,
                isQuickReturn = isQuickReturn,
                isTabletLayout = isTabletLayout,
                cardAnimationEnabled = cardAnimationEnabled,
                builtinPluginEnabledCount = builtinPluginEnabledCount,
                playerPluginEnabledCount = playerPluginEnabledCount,
                feedPluginEnabledCount = feedPluginEnabledCount,
                danmakuPluginEnabledCount = danmakuPluginEnabledCount,
                jsonPluginEnabledCount = jsonEnabledPlugins,
                jsonFeedPluginEnabledCount = jsonFeedPluginEnabledCount,
                jsonDanmakuPluginEnabledCount = jsonDanmakuPluginEnabledCount
            )
        }
    }
}

/** 生命周期只恢复非视频入口的底栏，不再维护已取消的顶栏延迟任务。 */
@Composable
internal fun HomeReturnLifecycleEffect(
    state: HomeReturnState,
    useSideNavigation: Boolean,
    bottomBarVisible: Boolean,
    onBottomBarVisibleChange: (Boolean) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val visible by rememberUpdatedState(bottomBarVisible)
    val setVisible by rememberUpdatedState(onBottomBarVisibleChange)
    DisposableEffect(lifecycle, state, useSideNavigation) {
        if (useSideNavigation) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !visible && !state.videoNavigationInProgress) {
                setVisible(true)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
