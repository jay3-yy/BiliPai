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
 * 返回动画保护窗与卡片转场 settle 都依赖导航系统发布最终 Idle 才清除；
 * 独立播放窗口、画中画或 NavDisplay settle 丢失时（issue #1131），
 * 这些瞬态门控会一直挂着，把已挂载分类的数据收集冻结成“下拉刷新无新内容”。
 * 首页可见期间门控持续超过恢复期限即强制按已恢复处理。
 */
internal fun shouldForceRecoverStaleReturnGate(
    homeVisible: Boolean,
    gateActive: Boolean,
    gateStartedAtMs: Long,
    nowMs: Long,
    timeoutMs: Long = HOME_STALE_RETURN_GATE_RECOVERY_MS,
): Boolean = homeVisible && gateActive && gateStartedAtMs > 0L && nowMs - gateStartedAtMs >= timeoutMs
