package com.android.purebilibili.feature.video.screen

/** 共享元素 morph 标准时长之外的超时 buffer，防止极端设备上过渡信号丢失。 */
internal const val VIDEO_DETAIL_ENTRY_TRANSITION_FINISH_TIMEOUT_BUFFER_MS = 80

/** 正文跟随入场边界。复用播放器的起播许可由播放入口单独控制。 */
internal fun shouldDeferVideoDetailContentUntilEntryTransitionFinished(
    transitionEnabled: Boolean,
    detailShellSharedBoundsEnabled: Boolean,
    isReturningFromDetail: Boolean = false,
    hasNavigationTransitionScope: Boolean = false,
): Boolean {
    // 导航内只由该 entry 的完成锁存放行；全局返回标志不能豁免尚未完成入场的页面。
    if (hasNavigationTransitionScope) return true
    if (isReturningFromDetail) return false
    return transitionEnabled && detailShellSharedBoundsEnabled
}

internal fun isVideoDetailEntryPlaybackReady(
    reuseFromMiniPlayerAtEntry: Boolean,
    entryTransitionFinished: Boolean,
    entryWorkPhase: VideoDetailEntryWorkPhase,
): Boolean = reuseFromMiniPlayerAtEntry ||
    (entryTransitionFinished && entryWorkPhase >= VideoDetailEntryWorkPhase.Playback)

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

/** 已退出/被覆盖的 entry 不能在导航静止后误启动首次媒体准备。 */
internal fun shouldFinishVideoDetailNavigationEntry(
    isNavigationRunning: Boolean,
    isEntryTop: Boolean,
    isSharedTransitionActive: Boolean,
    isLegacyNavRunning: Boolean,
    isHeroSettled: Boolean,
): Boolean = !isNavigationRunning && isEntryTop &&
    !isSharedTransitionActive && !isLegacyNavRunning && isHeroSettled

/**
 * A fresh card entry can show its resident cover while media preparation is gated.
 * Keep an attached/live player mounted across return previews, cancellation and episode changes.
 */
internal fun shouldMountVideoDetailPlayerHost(
    alreadyMounted: Boolean,
    deferEntryLoad: Boolean,
    entryPlaybackReady: Boolean,
    hasResidentCover: Boolean,
    preserveLivePlayer: Boolean,
): Boolean = alreadyMounted || !deferEntryLoad || entryPlaybackReady ||
    !hasResidentCover || preserveLivePlayer
