package com.android.purebilibili.feature.video.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.android.purebilibili.core.ui.NavigationEntryTransition
import com.android.purebilibili.core.ui.transition.MiuixVideoCardTransitionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rememberVideoDetailEntryTransitionFinished(
    deferLoad: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    fallbackDurationMillis: Int,
    heroDriver: MiuixVideoCardTransitionState? = null,
    navigationTransitionScope: NavigationEntryTransition? = null,
): Boolean {
    // 在分支外保存 entry 生命周期锁存，defer/返回标志变化不能删除已完成的状态。
    var finished by remember { mutableStateOf(!deferLoad) }
    if (!deferLoad || finished) return true
    val fallbackTimeoutMillis = remember(fallbackDurationMillis) {
        resolveVideoDetailEntryTransitionFallbackTimeoutMillis(fallbackDurationMillis)
    }

    LaunchedEffect(
        deferLoad,
        sharedTransitionScope,
        animatedVisibilityScope,
        fallbackTimeoutMillis,
        heroDriver,
        navigationTransitionScope,
    ) {
        // 单向锁存：已加载父详情的返回预览/取消不能重新卸载正文或关闭准备门控。
        if (finished) return@LaunchedEffect

        if (navigationTransitionScope != null) {
            // 给宿主的入场 effect 一个发布 settle 的机会；无动画入口不必等待固定超时。
            withFrameNanos { }
            snapshotFlow {
                // 先读边界信号，仅在导航 idle 时求角色，避免订阅整段逐帧 depth。
                if (navigationTransitionScope.isRunning) return@snapshotFlow false
                if (!navigationTransitionScope.isTop) return@snapshotFlow false
                shouldFinishVideoDetailNavigationEntry(
                    isNavigationRunning = false,
                    isEntryTop = true,
                    isSharedTransitionActive = sharedTransitionScope?.isTransitionActive ?: false,
                    isLegacyNavRunning = animatedVisibilityScope?.transition?.isRunning ?: false,
                    isHeroSettled = heroDriver == null ||
                        (heroDriver.progressProvider() >= 0.999f &&
                            !heroDriver.isGestureInProgressProvider()),
                )
            }.first { it }
            finished = true
            return@LaunchedEffect
        }

        if (heroDriver != null) {
            snapshotFlow {
                heroDriver.progressProvider() >= 0.999f &&
                    !heroDriver.isGestureInProgressProvider()
            }.first { it }
            finished = true
            return@LaunchedEffect
        }

        var hasObservedActiveTransition = false
        var fallbackElapsed by mutableStateOf(false)
        val timeoutJob = launch {
            delay(fallbackTimeoutMillis.toLong())
            fallbackElapsed = true
        }
        try {
            snapshotFlow {
                val sharedActive = sharedTransitionScope?.isTransitionActive ?: false
                val navRunning = animatedVisibilityScope?.transition?.isRunning ?: false
                if (sharedActive || navRunning) hasObservedActiveTransition = true
                shouldMarkVideoDetailEntryTransitionFinished(
                    hasObservedActiveTransition = hasObservedActiveTransition || fallbackElapsed,
                    isSharedTransitionActive = sharedActive,
                    isNavEnterTransitionRunning = navRunning,
                )
            }.first { it }
            // 超时只兜底缺失的启动信号，不在明确活跃的动画中强行启动重任务。
            finished = true
        } finally {
            timeoutJob.cancel()
        }
    }

    return finished
}
