package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
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

/** 播放时定时采样；暂停后只等待 seek、缓冲、结束等播放器事件。 */
internal suspend fun observeVideoPlayerProgress(
    player: Player,
    videoDuration: Long,
    pollingIntervalMs: Long,
    pollWhilePaused: Boolean = false,
    onProgress: (PlayerProgress) -> Unit,
) {
    val events = Channel<Unit>(Channel.CONFLATED)
    val listener = object : Player.Listener {
        override fun onEvents(player: Player, playerEvents: Player.Events) {
            events.trySend(Unit)
        }
    }
    player.addListener(listener)
    try {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            onProgress(
                resolvePlayerProgressSnapshot(
                    currentPositionMs = player.currentPosition,
                    durationMs = resolveSeekableDurationMs(player.duration, videoDuration),
                    bufferedPositionMs = player.bufferedPosition,
                    playbackState = player.playbackState,
                )
            )
            if (player.isPlaying || pollWhilePaused) {
                withTimeoutOrNull(pollingIntervalMs) { events.receive() }
            } else {
                events.receive()
            }
        }
    } finally {
        player.removeListener(listener)
        events.close()
    }
}

/** 页面持有快照时由两种材质共用；不可见时不保留定时器或播放器监听。 */
@Composable
internal fun rememberVideoPlayerOverlayProgress(
    player: Player,
    bvid: String,
    cid: Long,
    videoDuration: Long,
    controlsVisible: Boolean,
    samplingActive: Boolean,
    hasPendingSeekResume: Boolean,
    highFrequencyProgressActive: Boolean,
    onProgress: (PlayerProgress) -> Unit = {},
): State<PlayerProgress> {
    val currentOnProgress = rememberUpdatedState(onProgress)
    return key(player, bvid, cid) {
        produceState(
            initialValue = PlayerProgress(),
            player, bvid, cid, videoDuration, controlsVisible, samplingActive,
            hasPendingSeekResume, highFrequencyProgressActive,
        ) {
            if (!samplingActive) return@produceState
            observeVideoPlayerProgress(
                player = player,
                videoDuration = videoDuration,
                // seek 尚未落位时保留短期采样，以便现有恢复与超时策略推进。
                pollWhilePaused = hasPendingSeekResume,
                pollingIntervalMs = resolveInlineVideoOverlayProgressPollingIntervalMs(
                    controlsVisible = controlsVisible,
                    isPlaying = true,
                    highFrequencyProgressActive = highFrequencyProgressActive,
                ),
            ) { progress ->
                value = progress
                currentOnProgress.value(progress)
            }
        }
    }
}
