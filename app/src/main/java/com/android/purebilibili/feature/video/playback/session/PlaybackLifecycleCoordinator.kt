package com.android.purebilibili.feature.video.playback.session

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import com.android.purebilibili.feature.video.state.shouldRestorePlayerVolumeOnResume
import com.android.purebilibili.feature.video.state.shouldResumeAfterLifecyclePause

internal fun Activity?.isInMultiWindowPlaybackMode(): Boolean =
    this != null && isInMultiWindowMode && !isInPictureInPictureMode

/** A visible multi-window player can keep playing from STARTED until its window stops. */
internal class PlaybackWindowLifecycle {
    private var pauseDeferred = false
    private var pauseDelivered = false

    fun playbackEvent(event: Lifecycle.Event, isInMultiWindow: Boolean): Lifecycle.Event? =
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                pauseDeferred = isInMultiWindow
                if (pauseDeferred) null else {
                    pauseDelivered = true
                    event
                }
            }
            Lifecycle.Event.ON_STOP -> {
                if (pauseDeferred) {
                    pauseDeferred = false
                    pauseDelivered = true
                    Lifecycle.Event.ON_PAUSE
                } else {
                    event
                }
            }
            Lifecycle.Event.ON_START -> {
                // A split window can become visible and stop again without ever receiving RESUME.
                pauseDeferred = isInMultiWindow
                if (pauseDelivered && isInMultiWindow) {
                    pauseDelivered = false
                    Lifecycle.Event.ON_RESUME
                } else {
                    event
                }
            }
            Lifecycle.Event.ON_RESUME -> {
                pauseDeferred = false
                pauseDelivered = false
                event
            }
            else -> event
        }
}

internal data class PlaybackPauseDecision(
    val shouldContinuePlayback: Boolean,
    val shouldPausePlayback: Boolean,
    val shouldMarkBackgroundAudioSession: Boolean,
    val shouldPersistTransientResumeIntent: Boolean
)

internal data class PlaybackResumeDecision(
    val shouldResumePlayback: Boolean,
    val shouldRestoreVolume: Boolean
)

internal enum class PlaybackPauseOwner(val mask: Int) {
    LIFECYCLE(1),
    NAVIGATION(2),
}

/** Page-owned intent survives player recreation; automatic pauses must not overwrite it. */
internal class PlaybackReturnState {
    private var bvid = ""
    private var cid = 0L
    private var playWhenReady: Boolean? = null
    private var pauseOwners = 0

    private fun matchesMedia(bvid: String, cid: Long): Boolean =
        this.bvid == bvid && (this.cid <= 0L || cid <= 0L || this.cid == cid)

    fun recordPlaybackIntent(bvid: String, cid: Long, playing: Boolean) {
        if (pauseOwners == 0 && matchesMedia(bvid, cid)) playWhenReady = playing
    }

    fun suspend(bvid: String, cid: Long, playing: Boolean, owner: PlaybackPauseOwner): Boolean {
        if (pauseOwners == 0 || !matchesMedia(bvid, cid)) {
            this.bvid = bvid
            this.cid = cid
            playWhenReady = playing
        }
        pauseOwners = pauseOwners or owner.mask
        return playWhenReady == true
    }

    fun resume(owner: PlaybackPauseOwner) {
        pauseOwners = pauseOwners and owner.mask.inv()
    }

    fun resolveAutoPlay(bvid: String, cid: Long, initialPreference: Boolean): Boolean =
        if (matchesMedia(bvid, cid)) playWhenReady ?: initialPreference else initialPreference
}

internal fun resolveShouldContinuePlaybackDuringPause(
    isMiniMode: Boolean,
    isPip: Boolean,
    isInAudioMode: Boolean = false,
    isBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean
): Boolean {
    if (isMiniMode || isPip || isInAudioMode) return true
    return isBackgroundAudio
}

internal fun resolvePlaybackPauseDecision(
    isMiniMode: Boolean,
    isPip: Boolean,
    isInAudioMode: Boolean = false,
    isBackgroundAudio: Boolean,
    wasPlaybackActive: Boolean,
    hasRecentUserLeaveHint: Boolean,
    isLeavingByNavigation: Boolean = false
): PlaybackPauseDecision {
    val shouldContinuePlayback = resolveShouldContinuePlaybackDuringPause(
        isMiniMode = isMiniMode,
        isPip = isPip,
        isInAudioMode = isInAudioMode,
        isBackgroundAudio = isBackgroundAudio,
        wasPlaybackActive = wasPlaybackActive
    )
    return PlaybackPauseDecision(
        shouldContinuePlayback = shouldContinuePlayback,
        shouldPausePlayback = !shouldContinuePlayback,
        shouldMarkBackgroundAudioSession = isBackgroundAudio && hasRecentUserLeaveHint,
        shouldPersistTransientResumeIntent = wasPlaybackActive &&
            !shouldContinuePlayback &&
            !isLeavingByNavigation
    )
}

internal fun resolvePlaybackResumeDecision(
    wasPlaybackActive: Boolean,
    hasTransientResumeIntent: Boolean,
    hasForegroundResumeIntent: Boolean = false,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    currentVolume: Float,
    shouldEnsureAudibleOnForeground: Boolean,
    isLeavingByNavigation: Boolean
): PlaybackResumeDecision {
    if (isLeavingByNavigation) {
        return PlaybackResumeDecision(
            shouldResumePlayback = false,
            shouldRestoreVolume = false
        )
    }

    val shouldResumePlayback = hasTransientResumeIntent ||
        hasForegroundResumeIntent ||
        shouldKickPlaybackOnForegroundResume(
            playWhenReady = playWhenReady,
            isPlaying = isPlaying,
            playbackState = playbackState,
            shouldEnsureAudibleOnForeground = shouldEnsureAudibleOnForeground
        ) ||
        shouldResumeAfterLifecyclePause(
            wasPlaybackActive = wasPlaybackActive,
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState
        )
    return PlaybackResumeDecision(
        shouldResumePlayback = shouldResumePlayback,
        shouldRestoreVolume = shouldRestorePlayerVolumeOnResume(
            shouldResume = shouldResumePlayback,
            currentVolume = currentVolume,
            shouldEnsureAudible = shouldEnsureAudibleOnForeground
        )
    )
}

internal fun shouldKickPlaybackOnForegroundResume(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    shouldEnsureAudibleOnForeground: Boolean
): Boolean {
    return shouldEnsureAudibleOnForeground &&
        playWhenReady &&
        !isPlaying &&
        (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)
}
