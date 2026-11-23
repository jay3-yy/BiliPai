package com.android.purebilibili.feature.video.ui.overlay

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.feature.video.player.PlayerBackgroundPlaybackSessions
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Timer lifetime follows the playback session, not a transient settings/overlay composition. */
internal class PiliPlayerSleepTimer private constructor(player: Player, context: Context) {
    private val playerRef = WeakReference(player)
    private val appContext = context.applicationContext
    private var activityRef = WeakReference<Activity>(null)
    private val selectedMinutes = MutableStateFlow<Int?>(null)
    val minutes = selectedMinutes.asStateFlow()
    private val waitingForCurrentEnd = MutableStateFlow(false)
    val waitingForCurrentVideoEnd = waitingForCurrentEnd.asStateFlow()
    private var job: Job? = null
    private var waiting = false
    var exitApp: Boolean = false
    private val endedListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (waiting && shouldFinishWaitingForCurrentVideoEnd(
                    playbackState = player.playbackState,
                    hasError = player.playerError != null,
                )) finish()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // 单集循环不发送 ENDED；自动换集或循环回起点同样视为本集结束。
            if (waiting && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) finish()
        }
    }

    fun attachActivity(activity: Activity?) { activityRef = WeakReference(activity) }

    fun setTimer(minutes: Int?) {
        cancel()
        val duration = minutes?.takeIf { it > 0 } ?: return
        selectedMinutes.value = duration
        val deadline = SystemClock.elapsedRealtime() + duration * 60_000L
        job = scope.launch {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            if (playerRef.get() == null) { cancel(); return@launch }
            finish()
        }
    }

    /** 立即进入“播完当前视频”模式；不等待倒计时。 */
    fun waitForCurrentVideoEnd() {
        cancel()
        val player = playerRef.get() ?: return
        if (shouldFinishWaitingForCurrentVideoEnd(
                playbackState = player.playbackState,
                hasError = player.playerError != null,
            )) {
            finish()
            return
        }
        waiting = true
        waitingForCurrentEnd.value = true
        player.addListener(endedListener)
    }

    private fun cancel() {
        job?.cancel()
        job = null
        waiting = false
        playerRef.get()?.removeListener(endedListener)
        selectedMinutes.value = null
        waitingForCurrentEnd.value = false
    }

    private fun finish() {
        // 先清理等待监听，再暂停，避免播放状态回调重入 finish。
        val player = playerRef.get()
        cancel()
        val exoPlayer = player as? ExoPlayer
        if (exoPlayer != null) PlayerBackgroundPlaybackSessions.pauseForSleepTimer(exoPlayer)
        else player?.pause()
        if (exitApp) activityRef.get()?.finishAndRemoveTask()
        else Toast.makeText(appContext, "已暂停播放", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val sessions = WeakHashMap<Player, PiliPlayerSleepTimer>()
        fun forPlayer(player: Player, context: Context): PiliPlayerSleepTimer =
            sessions.getOrPut(player) { PiliPlayerSleepTimer(player, context) }
    }
}

/** 只有自然结束或播放错误才结束等待；用户暂停不触发退出。 */
internal fun shouldFinishWaitingForCurrentVideoEnd(
    playbackState: Int,
    hasError: Boolean,
): Boolean = hasError || playbackState == Player.STATE_ENDED
