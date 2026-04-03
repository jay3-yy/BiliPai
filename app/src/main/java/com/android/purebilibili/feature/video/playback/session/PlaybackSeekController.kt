package com.android.purebilibili.feature.video.playback.session

import com.android.purebilibili.feature.video.playback.policy.shouldHoldPlaybackTransitionPosition
import kotlin.math.abs

private const val DEFAULT_PLAYBACK_SEEK_SETTLED_TOLERANCE = 0.01f
private const val DEFAULT_PLAYBACK_SEEK_PENDING_TOLERANCE_MS = 500L

internal data class PlaybackSeekSessionState(
    val playbackPositionMs: Long = 0L,
    val sliderPositionMs: Long = 0L,
    val sliderTempPositionMs: Long = 0L,
    val isSliderMoving: Boolean = false,
    val pendingSeekPositionMs: Long? = null,
    val shouldResumePlayback: Boolean? = null
)

internal data class PlaybackSeekSessionCommitResult(
    val state: PlaybackSeekSessionState,
    val committedPositionMs: Long,
    val shouldResumePlayback: Boolean
)

internal fun syncPlaybackSeekSession(
    state: PlaybackSeekSessionState,
    playbackPositionMs: Long,
    toleranceMs: Long = DEFAULT_PLAYBACK_SEEK_PENDING_TOLERANCE_MS
): PlaybackSeekSessionState {
    val safePlaybackPositionMs = playbackPositionMs.coerceAtLeast(0L)
    val syncedState = state.copy(playbackPositionMs = safePlaybackPositionMs)
    if (syncedState.isSliderMoving) {
        return syncedState
    }
    if (
        shouldHoldPlaybackTransitionPosition(
            playerPositionMs = safePlaybackPositionMs,
            transitionPositionMs = syncedState.pendingSeekPositionMs,
            toleranceMs = toleranceMs
        )
    ) {
        return syncedState
    }
    return syncedState.copy(
        sliderPositionMs = safePlaybackPositionMs,
        sliderTempPositionMs = safePlaybackPositionMs,
        pendingSeekPositionMs = null,
        shouldResumePlayback = null
    )
}

internal fun startPlaybackSeekInteraction(
    state: PlaybackSeekSessionState,
    positionMs: Long = state.sliderPositionMs,
    shouldResumePlayback: Boolean? = state.shouldResumePlayback
): PlaybackSeekSessionState {
    val safePositionMs = positionMs.coerceAtLeast(0L)
    return state.copy(
        sliderPositionMs = safePositionMs,
        sliderTempPositionMs = safePositionMs,
        isSliderMoving = true,
        pendingSeekPositionMs = null,
        shouldResumePlayback = shouldResumePlayback
    )
}

internal fun updatePlaybackSeekInteraction(
    state: PlaybackSeekSessionState,
    positionMs: Long
): PlaybackSeekSessionState {
    val safePositionMs = positionMs.coerceAtLeast(0L)
    return state.copy(
        sliderPositionMs = safePositionMs,
        sliderTempPositionMs = safePositionMs,
        isSliderMoving = true
    )
}

internal fun finishPlaybackSeekInteraction(
    state: PlaybackSeekSessionState
): PlaybackSeekSessionCommitResult {
    val committedPositionMs = state.sliderPositionMs.coerceAtLeast(0L)
    return PlaybackSeekSessionCommitResult(
        state = state.copy(
            sliderPositionMs = committedPositionMs,
            sliderTempPositionMs = committedPositionMs,
            isSliderMoving = false,
            pendingSeekPositionMs = committedPositionMs
        ),
        committedPositionMs = committedPositionMs,
        shouldResumePlayback = state.shouldResumePlayback == true
    )
}

internal fun cancelPlaybackSeekInteraction(
    state: PlaybackSeekSessionState
): PlaybackSeekSessionState {
    val restoredPositionMs = state.playbackPositionMs.coerceAtLeast(0L)
    return state.copy(
        sliderPositionMs = restoredPositionMs,
        sliderTempPositionMs = restoredPositionMs,
        isSliderMoving = false,
        pendingSeekPositionMs = null,
        shouldResumePlayback = null
    )
}

internal fun shouldUsePlaybackSeekSessionPosition(
    state: PlaybackSeekSessionState,
    toleranceMs: Long = DEFAULT_PLAYBACK_SEEK_PENDING_TOLERANCE_MS
): Boolean {
    return state.isSliderMoving ||
        shouldHoldPlaybackTransitionPosition(
            playerPositionMs = state.playbackPositionMs,
            transitionPositionMs = state.pendingSeekPositionMs,
            toleranceMs = toleranceMs
        )
}

internal data class PlaybackSeekUiState(
    val isScrubbing: Boolean = false,
    val dragProgress: Float = 0f,
    val pendingSettledProgress: Float? = null
)

internal data class PlaybackSeekFinishResult(
    val state: PlaybackSeekUiState,
    val committedProgress: Float
)

internal fun startPlaybackSeekSession(progress: Float): PlaybackSeekUiState {
    return PlaybackSeekUiState(
        isScrubbing = true,
        dragProgress = progress.coerceIn(0f, 1f),
        pendingSettledProgress = null
    )
}

internal fun updatePlaybackSeekSession(
    state: PlaybackSeekUiState,
    progress: Float
): PlaybackSeekUiState {
    return state.copy(
        dragProgress = progress.coerceIn(0f, 1f)
    )
}

internal fun finishPlaybackSeekSession(
    state: PlaybackSeekUiState
): PlaybackSeekFinishResult {
    val committedProgress = state.dragProgress.coerceIn(0f, 1f)
    return PlaybackSeekFinishResult(
        state = state.copy(
            isScrubbing = false,
            dragProgress = committedProgress,
            pendingSettledProgress = committedProgress
        ),
        committedProgress = committedProgress
    )
}

internal fun cancelPlaybackSeekSession(
    state: PlaybackSeekUiState
): PlaybackSeekUiState {
    return state.copy(
        isScrubbing = false,
        pendingSettledProgress = null
    )
}

internal fun shouldHoldPlaybackSeekSettledProgress(
    playbackProgress: Float,
    pendingSettledProgress: Float?,
    tolerance: Float = DEFAULT_PLAYBACK_SEEK_SETTLED_TOLERANCE
): Boolean {
    val settledProgress = pendingSettledProgress ?: return false
    return abs(playbackProgress - settledProgress) > tolerance
}

internal fun settlePlaybackSeekSession(
    state: PlaybackSeekUiState,
    playbackProgress: Float,
    tolerance: Float = DEFAULT_PLAYBACK_SEEK_SETTLED_TOLERANCE
): PlaybackSeekUiState {
    if (state.isScrubbing) return state
    if (shouldHoldPlaybackSeekSettledProgress(playbackProgress, state.pendingSettledProgress, tolerance)) {
        return state
    }
    return state.copy(
        dragProgress = playbackProgress.coerceIn(0f, 1f),
        pendingSettledProgress = null
    )
}

internal fun resolvePlaybackSeekDisplayProgress(
    playbackProgress: Float,
    state: PlaybackSeekUiState,
    tolerance: Float = DEFAULT_PLAYBACK_SEEK_SETTLED_TOLERANCE
): Float {
    return when {
        state.isScrubbing -> state.dragProgress
        shouldHoldPlaybackSeekSettledProgress(
            playbackProgress = playbackProgress,
            pendingSettledProgress = state.pendingSettledProgress,
            tolerance = tolerance
        ) -> state.pendingSettledProgress ?: playbackProgress
        else -> playbackProgress
    }.coerceIn(0f, 1f)
}
