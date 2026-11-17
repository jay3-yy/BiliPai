package com.android.purebilibili.feature.home

private const val RETURN_ANIMATION_SUPPRESSION_BUFFER_MS = 40L
private const val RETURN_ANIMATION_SUPPRESSION_TABLET_EXTRA_MS = 40L

internal fun resolveReturnAnimationSuppressionDurationMs(
    isTabletLayout: Boolean,
    cardAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    sharedTransitionDurationMillis: Int =
        com.android.purebilibili.core.ui.transition.VIDEO_SHARED_TRANSITION_STANDARD_DURATION_MILLIS,
): Long {
    if (cardTransitionEnabled) {
        // 固定时长 tween 返回：保护窗 = 主时长 + 短 buffer，避免 clearReturning / 列表
        // 在 overlay 卸层瞬间抢跑重建封面请求 → 落位闪。
        return sharedTransitionDurationMillis.coerceAtLeast(0).toLong() +
            RETURN_ANIMATION_SUPPRESSION_BUFFER_MS +
            com.android.purebilibili.core.ui.transition.resolveVideoCardReturnSpringSettleBufferMs() +
            if (isTabletLayout) RETURN_ANIMATION_SUPPRESSION_TABLET_EXTRA_MS else 0L
    }
    if (!cardAnimationEnabled) return 220L
    return if (isTabletLayout) 220L else 240L
}

/** 首页可见期间，返回保护窗门控持续超过任何合法返回动画时长后的强制恢复期限。 */
internal const val HOME_STALE_RETURN_GATE_RECOVERY_MS = 2_000L

/**
 * 返回保护窗与卡片转场 settle 都依赖导航系统发布最终 Idle 才清除；独立播放窗口、
 * 画中画或 NavDisplay settle 丢失时（issue #1131），这些瞬态门控会一直挂着，把已挂载
 * 分类的数据收集冻结成「下拉刷新无新内容」。
 *
 * [elapsedMs] 由调用方用单调时钟累计，且只在首页处于顶层、没有进行中的返回手势时
 * 累计：预测性返回手势可以被用户按住远超恢复期限，那属于合法状态，不能强制恢复。
 */
internal fun shouldRecoverStaleHomeGate(
    active: Boolean,
    elapsedMs: Long,
    gestureInProgress: Boolean,
    timeoutMs: Long = HOME_STALE_RETURN_GATE_RECOVERY_MS,
): Boolean = active && !gestureInProgress && elapsedMs >= timeoutMs
