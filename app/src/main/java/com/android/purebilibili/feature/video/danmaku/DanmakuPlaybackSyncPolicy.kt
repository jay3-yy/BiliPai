package com.android.purebilibili.feature.video.danmaku

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val NORMAL_SYNC_INTERVAL_MS = 3200L
private const val NORMAL_SPEED_CORRECTION_INTERVAL_TICKS = 6
private const val NON_NORMAL_SPEED_CORRECTION_INTERVAL_TICKS = 3
private const val HIGH_SPEED_CORRECTION_INTERVAL_TICKS = 9
private const val EXPLICIT_SEEK_RESYNC_TOLERANCE_MS = 500L
private const val EXPLICIT_SEEK_RESYNC_WINDOW_MS = 1500L
private const val MIN_ENGINE_PLAYBACK_SPEED = 0.1f
private const val MAX_ENGINE_PLAYBACK_SPEED = 4.0f

internal enum class DanmakuSyncAction {
    None,
    PauseOnly,
    SoftResync,
    HardResync
}

internal fun normalizeDanmakuPlaybackSpeed(videoSpeed: Float): Float {
    if (videoSpeed.isNaN()) return 1.0f
    return videoSpeed.coerceIn(MIN_ENGINE_PLAYBACK_SPEED, MAX_ENGINE_PLAYBACK_SPEED)
}

internal fun resolveDanmakuEnginePlaySpeedPercent(videoSpeed: Float): Int {
    return (normalizeDanmakuPlaybackSpeed(videoSpeed) * 100f)
        .roundToInt()
        .coerceAtLeast(1)
}

internal fun resolveDanmakuPlaybackAdjustedDurationMillis(
    baseDurationMs: Long,
    videoSpeed: Float
): Long {
    if (baseDurationMs <= 0L) return 0L
    return (baseDurationMs / normalizeDanmakuPlaybackSpeed(videoSpeed))
        .roundToLong()
        .coerceAtLeast(1L)
}

internal fun resolveDanmakuDriftSyncIntervalMs(videoSpeed: Float): Long {
    val normalizedSpeed = normalizeDanmakuPlaybackSpeed(videoSpeed)
    return when {
        normalizedSpeed >= 1.75f -> 900L
        normalizedSpeed >= 1.25f -> 1200L
        normalizedSpeed > 1.02f -> 2000L
        normalizedSpeed <= 0.75f -> 3000L
        normalizedSpeed < 0.98f -> 3500L
        else -> NORMAL_SYNC_INTERVAL_MS
    }
}

internal fun shouldScheduleDanmakuDriftCorrection(videoSpeed: Float, tickCount: Int): Boolean {
    if (tickCount <= 0) return false
    val normalizedSpeed = normalizeDanmakuPlaybackSpeed(videoSpeed)
    val isNearNormalSpeed = abs(normalizedSpeed - 1.0f) <= 0.02f
    val interval = when {
        isNearNormalSpeed -> NORMAL_SPEED_CORRECTION_INTERVAL_TICKS
        normalizedSpeed >= 1.75f -> HIGH_SPEED_CORRECTION_INTERVAL_TICKS
        else -> NON_NORMAL_SPEED_CORRECTION_INTERVAL_TICKS
    }
    return tickCount % interval == 0
}

internal fun resolveDanmakuActionForIsPlayingChange(
    isPlayerPlaying: Boolean,
    danmakuEnabled: Boolean,
    hasData: Boolean
): DanmakuSyncAction {
    if (!isPlayerPlaying) return DanmakuSyncAction.PauseOnly
    // A normal pause/resume does not invalidate the loaded timeline. Re-anchoring the
    // engine clock is sufficient and preserves the currently visible render layers.
    // Explicit seeks, buffering recovery and player/view replacement keep their hard
    // resync paths because those events can invalidate the active window.
    return if (danmakuEnabled && hasData) DanmakuSyncAction.SoftResync else DanmakuSyncAction.None
}

/**
 * 弹幕数据就绪时的启动意图。
 *
 * 相关推荐/同页切集（含进入过渡动画）后，播放器往往先于弹幕网络请求开始播放：
 * `onIsPlayingChanged(true)` 可能在弹幕数据加载完成前到达，被 [resolveDanmakuActionForIsPlayingChange]
 * 的 None 分支（hasData=false）吃掉；若随后数据就绪时仅按 `isPlaying` 瞬时快照判断，
 * 引擎会停在 paused 且没有新的 isPlaying 事件恢复它（表现为弹幕开关显示「开」却无弹幕，
 * 需手动重开开关才显示）。因此数据就绪时应按「播放意图」（正在播放或即将播放）启动引擎，
 * 由后续 drift sync / buffering HardResync 校正缓冲造成的少量时间线偏差。
 */
internal fun shouldStartDanmakuOnDataReady(
    isPlaying: Boolean,
    playWhenReady: Boolean
): Boolean = isPlaying || playWhenReady

/**
 * Network loading is asynchronous, so the position captured when an episode request starts can
 * already be stale when its first segment commits. Always prefer the currently attached player.
 */
internal fun resolveDanmakuDataReadyPositionMs(
    currentPlayerPositionMs: Long?,
    requestedPositionMs: Long,
): Long = (currentPlayerPositionMs ?: requestedPositionMs).coerceAtLeast(0L)

/**
 * 新 DanmakuView attach 时是否要把已缓存弹幕时间线补到当前 controller。
 *
 * 相关推荐 push 新页时，load 常在旧 controller 完成；新 view 若跳过重放，
 * 开关显示「开」却无弹幕，只能手动重开开关才恢复。
 */
internal fun shouldReapplyDanmakuTimelineOnAttach(
    hasCachedList: Boolean,
    pendingTimelineResync: Boolean,
    previousControllerSameAsCurrent: Boolean,
    timelineAlreadySyncedToCurrent: Boolean,
): Boolean {
    if (!hasCachedList) return false
    if (pendingTimelineResync) return true
    if (previousControllerSameAsCurrent && timelineAlreadySyncedToCurrent) return false
    return true
}

internal fun resolveExplicitSeekStartedPlaybackAfterSyncAction(
    explicitSeekStartedPlayback: Boolean?,
    action: DanmakuSyncAction
): Boolean? {
    if (explicitSeekStartedPlayback == null) return null
    return if (action == DanmakuSyncAction.PauseOnly) {
        false
    } else {
        explicitSeekStartedPlayback
    }
}

internal fun resolveDanmakuActionForPlaybackState(
    playbackState: Int,
    isPlayerPlaying: Boolean,
    danmakuEnabled: Boolean,
    hasData: Boolean,
    resumedFromBuffering: Boolean
): DanmakuSyncAction {
    return when (playbackState) {
        androidx.media3.common.Player.STATE_BUFFERING -> DanmakuSyncAction.PauseOnly
        androidx.media3.common.Player.STATE_ENDED -> DanmakuSyncAction.PauseOnly
        androidx.media3.common.Player.STATE_READY ->
            if (resumedFromBuffering && isPlayerPlaying && danmakuEnabled && hasData) {
                DanmakuSyncAction.HardResync
            } else {
                DanmakuSyncAction.None
            }
        else -> DanmakuSyncAction.None
    }
}

internal fun resolveDanmakuActionForPositionDiscontinuity(
    reason: Int,
    hasData: Boolean
): DanmakuSyncAction {
    if (!hasData) return DanmakuSyncAction.None
    val isSeekDiscontinuity =
        reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK ||
            reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
    return if (isSeekDiscontinuity) DanmakuSyncAction.HardResync else DanmakuSyncAction.None
}

internal fun resolveDanmakuActionForForegroundRecovery(
    playWhenReady: Boolean,
    isPlayerPlaying: Boolean,
    playbackState: Int,
    danmakuEnabled: Boolean,
    hasData: Boolean,
    preserveTimeline: Boolean = false,
    timelineAlreadySynced: Boolean = false
): DanmakuSyncAction {
    if (!danmakuEnabled || !hasData) return DanmakuSyncAction.None
    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
        return DanmakuSyncAction.PauseOnly
    }
    return if (playWhenReady || isPlayerPlaying) {
        if (preserveTimeline && timelineAlreadySynced) {
            DanmakuSyncAction.SoftResync
        } else {
            DanmakuSyncAction.HardResync
        }
    } else {
        DanmakuSyncAction.None
    }
}

/**
 * Loading can finish before the ExoPlayer is attached to the danmaku session.
 * Rebind the cached timeline when the player arrives so an enabled session does
 * not require toggling the switch to become visible.
 */
internal fun shouldResyncDanmakuAfterPlayerAttach(
    danmakuEnabled: Boolean,
    hasData: Boolean,
    hasController: Boolean
): Boolean {
    return danmakuEnabled && hasData && hasController
}

internal fun resolveDanmakuGuardAction(
    videoSpeed: Float,
    tickCount: Int,
    danmakuEnabled: Boolean,
    isPlaying: Boolean,
    hasData: Boolean
): DanmakuSyncAction {
    if (!danmakuEnabled || !isPlaying || !hasData) return DanmakuSyncAction.None
    if (!shouldScheduleDanmakuDriftCorrection(videoSpeed, tickCount)) return DanmakuSyncAction.None
    // Periodic health checks must never clear the active render layers. Explicit seeks,
    // buffering recovery and view replacement still use HardResync at their event boundaries.
    return DanmakuSyncAction.SoftResync
}

internal inline fun executeExplicitDanmakuResync(
    pause: () -> Unit,
    clear: () -> Unit,
    setData: () -> Unit,
    start: () -> Unit
) {
    // 引擎 setData/start 均不清屏：先 clear 掉渲染队列里残留的旧弹幕，
    // 否则 seek 后旧时间线弹幕与新时间线叠加（重复且不跟随进度）。
    pause()
    clear()
    setData()
    start()
}

internal inline fun executeDanmakuSeekScrubStart(
    pause: () -> Unit,
    clear: () -> Unit
) {
    pause()
    clear()
}

internal fun shouldSuppressFollowupDanmakuHardResync(
    positionMs: Long,
    explicitSeekPositionMs: Long?,
    explicitSeekStartedPlayback: Boolean = true,
    nowElapsedRealtimeMs: Long,
    explicitSeekElapsedRealtimeMs: Long?,
    positionToleranceMs: Long = EXPLICIT_SEEK_RESYNC_TOLERANCE_MS,
    suppressionWindowMs: Long = EXPLICIT_SEEK_RESYNC_WINDOW_MS
): Boolean {
    val seekPosition = explicitSeekPositionMs ?: return false
    if (!explicitSeekStartedPlayback) return false
    val seekElapsedRealtimeMs = explicitSeekElapsedRealtimeMs ?: return false
    if (nowElapsedRealtimeMs < seekElapsedRealtimeMs) return false
    if (nowElapsedRealtimeMs - seekElapsedRealtimeMs > suppressionWindowMs) return false
    return abs(positionMs - seekPosition) <= positionToleranceMs
}
