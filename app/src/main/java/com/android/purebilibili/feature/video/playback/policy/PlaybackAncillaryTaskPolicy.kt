package com.android.purebilibili.feature.video.playback.policy

internal data class PlaybackHeartbeatSnapshot(
    val playedTimeSec: Long,
    val realPlayedTimeSec: Long
)

internal fun shouldRefreshOnlineCount(
    showOnlineCountEnabled: Boolean,
    isInBackground: Boolean,
    currentBvid: String,
    currentCid: Long
): Boolean {
    return showOnlineCountEnabled &&
        !isInBackground &&
        currentBvid.isNotBlank() &&
        currentCid > 0L
}

internal fun resolveOnlineCountPollingDelayMs(isInBackground: Boolean): Long {
    return if (isInBackground) 90_000L else 30_000L
}

internal fun shouldSendPlaybackHeartbeat(
    isPlaying: Boolean,
    isInBackground: Boolean,
    currentBvid: String,
    currentCid: Long
): Boolean {
    return isPlaying &&
        !isInBackground &&
        currentBvid.isNotBlank() &&
        currentCid > 0L
}

internal fun resolvePlaybackHeartbeatSessionStartTsSec(
    existingStartTsSec: Long,
    nowEpochSec: Long
): Long {
    return if (existingStartTsSec > 0L) {
        existingStartTsSec
    } else {
        nowEpochSec.coerceAtLeast(0L)
    }
}

internal fun resolvePlaybackHeartbeatSnapshot(
    currentPositionMs: Long,
    accumulatedPlayMs: Long,
    activePlayStartElapsedMs: Long?,
    nowElapsedMs: Long
): PlaybackHeartbeatSnapshot {
    val safeAccumulatedPlayMs = accumulatedPlayMs.coerceAtLeast(0L)
    val activePlayMs = if (activePlayStartElapsedMs != null && activePlayStartElapsedMs > 0L) {
        (nowElapsedMs - activePlayStartElapsedMs).coerceAtLeast(0L)
    } else {
        0L
    }

    return PlaybackHeartbeatSnapshot(
        playedTimeSec = currentPositionMs.coerceAtLeast(0L) / 1000L,
        realPlayedTimeSec = (safeAccumulatedPlayMs + activePlayMs) / 1000L
    )
}

internal fun shouldFlushPlaybackHeartbeatSnapshot(
    currentBvid: String,
    currentCid: Long,
    snapshot: PlaybackHeartbeatSnapshot,
    lastReportedSnapshot: PlaybackHeartbeatSnapshot?
): Boolean {
    if (currentBvid.isBlank() || currentCid <= 0L || snapshot.playedTimeSec <= 0L) {
        return false
    }

    return lastReportedSnapshot == null ||
        snapshot.playedTimeSec > lastReportedSnapshot.playedTimeSec ||
        snapshot.realPlayedTimeSec > lastReportedSnapshot.realPlayedTimeSec
}
