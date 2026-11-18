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
