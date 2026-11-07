package com.android.purebilibili.core.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.core.util.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** 可见阅读界面共用媒体时钟；暂停时只等待播放器事件，不持续轮询或创建逐帧任务。 */
@Composable
internal fun rememberPlaybackPosition(
    player: Player?,
    identity: Any,
    enabled: Boolean = true,
    timeline: PlaybackPositionTimeline? = null,
): State<Long> {
    // 字幕是播放内容，弹窗暂时取走窗口焦点时仍需跟随媒体时间。
    // 页面保留、退出前台与字幕关闭仍会撤销监听和定时等待。
    val active = LocalVisualWorkAllowed.current && enabled
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // produceState 的 key 只重启生产协程；内容切换还需重建 State，避免首帧沿用旧位置。
    return key(player, identity) {
        produceState(
            player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            active, lifecycle, timeline,
        ) {
            value = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            if (player == null || !active) return@produceState
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val wake = Channel<Unit>(Channel.CONFLATED)
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        // 拖动、暂停/恢复、缓冲结束、倍速与媒体切换均立即重新取时。
                        value = player.currentPosition.coerceAtLeast(0L)
                        wake.trySend(Unit)
                    }
                }
                player.addListener(listener)
                val startPosition = player.currentPosition
                var reportedAdvance = false
                Logger.d("PlaybackClock", "start identity=$identity position=$startPosition playing=${player.isPlaying} state=${player.playbackState}")
                try {
                    while (isActive) {
                        val position = player.currentPosition.coerceAtLeast(0L)
                        value = position
                        if (!reportedAdvance && position > startPosition) {
                            reportedAdvance = true
                            Logger.d("PlaybackClock", "advancing identity=$identity position=$position")
                        }
                        if (player.isPlaying) {
                            val timeout = timeline?.updateDelayMs(position, player.playbackParameters.speed) ?: 100L
                            withTimeoutOrNull(timeout) { wake.receive() }
                        } else {
                            wake.receive()
                        }
                    }
                } finally {
                    Logger.d("PlaybackClock", "stop identity=$identity position=${player.currentPosition} advanced=$reportedAdvance")
                    player.removeListener(listener)
                    wake.close()
                }
            }
        }
    }
}
