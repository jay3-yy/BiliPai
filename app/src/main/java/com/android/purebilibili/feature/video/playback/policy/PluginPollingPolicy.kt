package com.android.purebilibili.feature.video.playback.policy

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

internal fun resolvePluginPollingIntervalMs(
    hasPlugins: Boolean,
    isPlaying: Boolean,
): Long? = if (hasPlugins && isPlaying) 750L else null

internal fun resolvePlaybackSamplingIntervalMs(
    isPlaying: Boolean,
    isLoading: Boolean,
    analyticsEnabled: Boolean,
    cdnEnabled: Boolean,
): Long? = if ((isPlaying && analyticsEnabled) || (cdnEnabled && (isPlaying || isLoading))) {
    5_000L
} else null

/** 没有连续工作时只等待事件；恢复播放、seek 或配置变化会立即重新结算。 */
internal suspend fun runPlaybackTaskLoop(
    changes: ReceiveChannel<Unit>,
    pollingIntervalMs: () -> Long?,
    task: suspend () -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        task()
        val interval = pollingIntervalMs()
        if (interval == null) changes.receive()
        else withTimeoutOrNull(interval) { changes.receive() }
    }
}

internal fun shouldDispatchPluginPositionUpdate(
    lastDispatchedPositionMs: Long?,
    currentPositionMs: Long,
    minPositionDeltaMs: Long = 400L
): Boolean {
    val previous = lastDispatchedPositionMs ?: return true
    return currentPositionMs < previous || currentPositionMs - previous >= minPositionDeltaMs
}
