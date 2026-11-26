package com.android.purebilibili.feature.audio.player

import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 管理单个播放器在定时关闭结束前的平滑音量淡出。
 * 所有 Player 音量读写均在 Main 线程执行；无论自然完成还是中途取消，都会恢复原始音量，
 * 避免下次播放停留在静音状态。
 */
internal class PlayerVolumeFadeController {
    private var activePlayer: Player? = null
    private var savedOriginalVolume: Float? = null

    fun originalVolumeFor(player: Player): Float? =
        savedOriginalVolume.takeIf { activePlayer === player }

    fun cancelAndRestore(player: Player? = null) {
        val target = activePlayer ?: return
        if (player != null && target !== player) return
        val original = savedOriginalVolume
        activePlayer = null
        savedOriginalVolume = null
        if (original != null) {
            target.volume = original
        }
    }

    suspend fun fadeOutAndFinish(
        player: Player,
        fadeWindowMs: Long,
        onFinish: () -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        if (fadeWindowMs <= 0L) {
            cancelAndRestore(player)
            onFinish()
            return@withContext
        }

        val originalVolume = savedOriginalVolume
            ?.takeIf { activePlayer === player }
            ?: player.volume.coerceIn(0f, 1f)
        activePlayer = player
        savedOriginalVolume = originalVolume

        val startMs = SystemClock.elapsedRealtime()
        try {
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - startMs
                if (elapsed >= fadeWindowMs) break
                val factor = computeSleepTimerFadeVolumeFactor(elapsed, fadeWindowMs)
                player.volume = (originalVolume * factor).coerceIn(0f, 1f)
                val nextDelay = minOf(SLEEP_TIMER_FADE_STEP_MS, (fadeWindowMs - elapsed).coerceAtLeast(16L))
                delay(nextDelay)
            }
            player.volume = 0f
            activePlayer = null
            savedOriginalVolume = null
            onFinish()
            player.volume = originalVolume
        } catch (cancelled: CancellationException) {
            if (activePlayer === player) {
                activePlayer = null
                savedOriginalVolume = null
                player.volume = originalVolume
            }
            throw cancelled
        }
    }
}
