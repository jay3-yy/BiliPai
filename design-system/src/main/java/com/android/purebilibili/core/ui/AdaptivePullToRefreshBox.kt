package com.android.purebilibili.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as rememberMiuixPullToRefreshState

/**
 * @param indicatorTopInset Distance from this box's top to where the indicator
 * should sit — height of **overlaid** chrome only. Scaffold-padded bodies use 0.
 * Custom [indicator] slots must apply the same inset themselves (e.g. home).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /**
     * Overlay chrome height above the visual content start. Applied to MIUIX
     * [contentPadding] and the default Material indicator.
     */
    indicatorTopInset: Dp = 0.dp,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    /** Disable refresh gestures without discarding the list's composition state. */
    enabled: Boolean = true,
    indicator: @Composable BoxScope.() -> Unit = {
        AdaptivePullToRefreshDefaultIndicator(
            isRefreshing = isRefreshing,
            state = state,
            indicatorTopInset = indicatorTopInset,
        )
    },
    /** Miuix 加载期间让列表继续滚动；完成后直接恢复下拉，不播放原生阻塞式完成动画。 */
    allowContentScrollWhileRefreshing: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    // 保留在 Pager/导航栈中的列表不能让不可见的刷新指示器继续提交帧。
    // 门控绑定稳定外壳，切换容器时不卸载列表；原生可见状态的参数保持不变。
    val refreshGate = rememberDecorativeAnimationGate(
        respectComponentMotion = false,
        respectSystemReduceMotion = false,
    )
    val reduceMotion = LocalSystemReduceMotion.current
    val contentHaptic = LocalHapticFeedback.current
    val refreshHaptic = rememberAppHapticFeedback(AppHapticScene.REFRESH)
    val layoutDirection = LocalLayoutDirection.current
    val mergedContentPadding = remember(contentPadding, indicatorTopInset, layoutDirection) {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + indicatorTopInset,
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding(),
        )
    }

    when (rememberPresetPrimitiveRenderer()) {
        PresetPrimitiveRenderer.MIUIX_BRIDGED -> {
            // MIUIX has no gesture-enabled flag. Move, rather than remount, the list
            // when bypassing its refresh container so comment row state is retained.
            val stableContent = remember {
                movableContentOf<Alignment, @Composable BoxScope.() -> Unit> { alignment, body ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = alignment,
                        content = body,
                    )
                }
            }
            Box(modifier = modifier.then(refreshGate.modifier), contentAlignment = contentAlignment) {
                if (enabled && refreshGate.active && !((allowContentScrollWhileRefreshing || reduceMotion) && isRefreshing)) {
                    // The native container owns its drag/refresh animation scope. Discard that
                    // state with the container when player expansion temporarily disables refresh;
                    // otherwise a cancelled Pulling/RefreshComplete session can consume later
                    // comment drags. movableContent below still preserves the list and its offset.
                    val miuixState = rememberMiuixPullToRefreshState()
                    CompositionLocalProvider(LocalHapticFeedback provides refreshHaptic) {
                        MiuixPullToRefresh(
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                            pullToRefreshState = miuixState,
                            contentPadding = mergedContentPadding,
                            color = AppSurfaceTokens.primary(),
                            refreshTexts = resolveMiuixPullToRefreshTexts(),
                            content = {
                                CompositionLocalProvider(LocalHapticFeedback provides contentHaptic) {
                                    stableContent(contentAlignment, content)
                                }
                            },
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = contentAlignment) {
                        stableContent(contentAlignment, content)
                        // 非阻塞刷新或减少动态效果时，静态提示与列表分开，避免保留旋转任务。
                        if (refreshGate.active && (allowContentScrollWhileRefreshing || reduceMotion) && isRefreshing) {
                            com.android.purebilibili.core.ui.components.AppText(
                                text = "正在刷新…",
                                color = AppSurfaceTokens.primary(),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                tapToCopyEnabled = false,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = indicatorTopInset + 8.dp),
                            )
                        }
                    }
                }
            }
        }
        PresetPrimitiveRenderer.MATERIAL3 -> {
            CompositionLocalProvider(LocalHapticFeedback provides refreshHaptic) {
                ComfortablePullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = modifier.then(refreshGate.modifier),
                    state = state,
                    contentAlignment = contentAlignment,
                    enabled = enabled && refreshGate.active,
                    indicator = {
                        if (refreshGate.active) {
                            if (reduceMotion && isRefreshing) {
                                com.android.purebilibili.core.ui.components.AppText(
                                    text = "正在刷新…",
                                    color = AppSurfaceTokens.primary(),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    tapToCopyEnabled = false,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = indicatorTopInset + 8.dp),
                                )
                            } else indicator()
                        }
                    },
                    content = {
                        CompositionLocalProvider(LocalHapticFeedback provides contentHaptic) { content() }
                    },
                )
            }
        }
    }
}

/**
 * Default pull-to-refresh chrome for [AdaptivePullToRefreshBox].
 *
 * - Material 3: official [PullToRefreshDefaults.Indicator] with circular progress.
 * - Miuix: unused — the Miuix branch mounts native [MiuixPullToRefresh] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.AdaptivePullToRefreshDefaultIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    indicatorTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val indicatorModifier = modifier
        .align(Alignment.TopCenter)
        .padding(top = indicatorTopInset)
    when (rememberPresetPrimitiveRenderer()) {
        PresetPrimitiveRenderer.MATERIAL3 -> {
            PullToRefreshDefaults.Indicator(
                modifier = indicatorModifier,
                isRefreshing = isRefreshing,
                state = state,
                color = AppContentColors.accent(),
            )
        }
        PresetPrimitiveRenderer.MIUIX_BRIDGED -> {
            PullToRefreshDefaults.Indicator(
                modifier = indicatorModifier,
                isRefreshing = isRefreshing,
                state = state,
            )
        }
    }
}
