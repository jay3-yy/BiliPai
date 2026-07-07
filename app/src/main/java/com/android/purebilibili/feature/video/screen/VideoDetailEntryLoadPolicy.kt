package com.android.purebilibili.feature.video.screen

/** 共享元素 morph 标准时长之外的超时 buffer，防止极端设备上过渡信号丢失。 */
internal const val VIDEO_DETAIL_ENTRY_TRANSITION_FINISH_TIMEOUT_BUFFER_MS = 80

internal fun shouldDeferVideoDetailLoadUntilEntryTransitionFinished(
    transitionEnabled: Boolean,
    detailShellSharedBoundsEnabled: Boolean,
    reuseFromMiniPlayerAtEntry: Boolean,
): Boolean {
    if (!transitionEnabled) return false
    if (!detailShellSharedBoundsEnabled) return false
    if (reuseFromMiniPlayerAtEntry) return false
    return true
}

internal fun isVideoDetailEntryTransitionFinished(
    deferLoad: Boolean,
    isSharedTransitionActive: Boolean,
    isNavEnterTransitionRunning: Boolean,
): Boolean {
    if (!deferLoad) return true
    return !isSharedTransitionActive && !isNavEnterTransitionRunning
}

/**
 * 在已观测到至少一次活跃过渡后，双方信号均静止时才视为 morph 真正结束。
 * 避免进场首帧 isTransitionActive 尚未置 true 时被误判为已完成。
 */
internal fun shouldMarkVideoDetailEntryTransitionFinished(
    hasObservedActiveTransition: Boolean,
    isSharedTransitionActive: Boolean,
    isNavEnterTransitionRunning: Boolean,
): Boolean {
    if (isSharedTransitionActive || isNavEnterTransitionRunning) return false
    return hasObservedActiveTransition
}

internal fun resolveVideoDetailEntryTransitionFallbackTimeoutMillis(
    morphDurationMillis: Int,
    bufferMillis: Int = VIDEO_DETAIL_ENTRY_TRANSITION_FINISH_TIMEOUT_BUFFER_MS,
): Int {
    return morphDurationMillis.coerceAtLeast(0) + bufferMillis.coerceAtLeast(0)
}
