package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal fun resolvePlayerProgressSnapshot(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    playbackState: Int,
): PlayerProgress {
    // 解码结束时最后一个媒体时间戳可能早于时间轴终点；仅在明确结束后补齐显示。
    val ended = playbackState == Player.STATE_ENDED && durationMs > 0L
    return PlayerProgress(
        current = if (ended) durationMs else currentPositionMs.coerceAtLeast(0L),
        duration = durationMs.coerceAtLeast(0L),
        buffered = if (ended) durationMs else bufferedPositionMs.coerceAtLeast(0L),
    )
}

/** Keeps the polling snapshot unread until a progress consumer needs it. */
@Composable
internal fun rememberVideoPlayerOverlayProgress(
    player: Player,
    bvid: String,
    cid: Long,
    videoDuration: Long,
    controlsVisible: Boolean,
    hostLifecycleStarted: Boolean,
    hasPendingSeekResume: Boolean,
    highFrequencyProgressActive: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
): State<PlayerProgress> {
    val currentOnPlayingChanged = rememberUpdatedState(onPlayingChanged)
    DisposableEffect(player, hasPendingSeekResume) {
        fun updatePlayingState() {
            currentOnPlayingChanged.value(
                resolveOverlayPlaybackButtonPlayingState(
                    isPlaying = player.isPlaying,
                    playWhenReady = player.playWhenReady,
                    playbackState = player.playbackState,
                    hasPendingSeekResume = hasPendingSeekResume,
                )
            )
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updatePlayingState()
            }
        }
        player.addListener(listener)
        updatePlayingState()
        onDispose { player.removeListener(listener) }
    }
    return produceState(
        initialValue = PlayerProgress(),
        player,
        bvid,
        cid,
        videoDuration,
        controlsVisible,
        hostLifecycleStarted,
        hasPendingSeekResume,
        highFrequencyProgressActive,
    ) {
        // Take one snapshot while stopped, and keep updating paused playback while started.
        // This also preserves progress immediately after rotating or seeking.
        do {
            value = resolvePlayerProgressSnapshot(
                currentPositionMs = player.currentPosition,
                durationMs = resolveSeekableDurationMs(
                    playbackDurationMs = player.duration,
                    fallbackDurationMs = videoDuration,
                ),
                bufferedPositionMs = player.bufferedPosition,
                playbackState = player.playbackState,
            )
            if (!shouldPollInlineVideoOverlayProgress(
                    playerExists = true,
                    hostLifecycleStarted = hostLifecycleStarted,
                )
            ) {
                return@produceState
            }
            delay(
                resolveInlineVideoOverlayProgressPollingIntervalMs(
                    controlsVisible = controlsVisible,
                    isPlaying = player.isPlaying,
                    highFrequencyProgressActive = highFrequencyProgressActive,
                )
            )
        } while (isActive)
    }
}
