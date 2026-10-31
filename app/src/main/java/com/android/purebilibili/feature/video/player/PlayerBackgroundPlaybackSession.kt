package com.android.purebilibili.feature.video.player

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.store.ContentDisplayPreferences
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.video.screen.findActivity
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal class PlayerBackgroundPlaybackSession {
    private val mutableSelection = MutableStateFlow<Boolean?>(null)
    val selection = mutableSelection.asStateFlow()
    fun select(enabled: Boolean) { mutableSelection.value = enabled }
    fun clear() { mutableSelection.value = null }
    fun effective(default: Boolean): Boolean = mutableSelection.value ?: default

    suspend fun saveIfRemembered(context: Context, enabled: Boolean) {
        try {
            if (ContentDisplayPreferences.rememberPlayerAdjustments(context).first()) {
                SettingsManager.setBackgroundPlaybackEnabled(context, enabled)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Toast.makeText(context, "后台播放已临时调整，默认值保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }
}

/** 按播放器与内容隔离；后端同步读取同一份状态，不依赖菜单是否仍在组合中。 */
internal object PlayerBackgroundPlaybackSessions {
    private val owners = WeakHashMap<Any, LinkedHashMap<String, PlayerBackgroundPlaybackSession>>()

    @Synchronized
    fun forContent(owner: Any, key: String): PlayerBackgroundPlaybackSession {
        val entries = owners.getOrPut(owner) { LinkedHashMap() }
        return entries.getOrPut(key) { PlayerBackgroundPlaybackSession() }.also {
            while (entries.size > 16) entries.remove(entries.keys.first())
        }
    }

    @Synchronized
    fun selection(owner: Any?, key: String?): Boolean? =
        if (owner == null || key == null) null else owners[owner]?.get(key)?.selection?.value

    @Synchronized
    fun clear(owner: Any?) { owners[owner]?.values?.forEach { it.clear() } }

    @Synchronized
    fun effective(owner: Any?, key: String?, default: Boolean): Boolean =
        if (owner == null || key == null) default else owners[owner]?.get(key)?.effective(default) ?: default
}

@Composable
internal fun rememberPlayerBackgroundPlaybackSession(owner: Any?, key: String): PlayerBackgroundPlaybackSession =
    remember(owner, key) {
        if (owner == null) PlayerBackgroundPlaybackSession() else PlayerBackgroundPlaybackSessions.forContent(owner, key)
    }

/** 独立番剧播放器的生命周期规则：不允许后台时暂停，返回前台再恢复；画中画保持原路径。 */
@Composable
internal fun PlayerBackgroundPlaybackLifecycleEffect(player: ExoPlayer, key: String, isInPipMode: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestPip by rememberUpdatedState(isInPipMode)
    DisposableEffect(player, key, lifecycleOwner, activity) {
        var resumeOnForeground = false
        var applyingPause = false
        fun pauseIfNeeded() {
            val inPip = latestPip || activity?.isInPictureInPictureMode == true
            val enabled = PlayerBackgroundPlaybackSessions.effective(
                player, key, SettingsManager.getBackgroundPlaybackEnabledSync(context),
            )
            if (!inPip && !enabled) {
                resumeOnForeground = resumeOnForeground ||
                    (player.playWhenReady && player.playbackState != androidx.media3.common.Player.STATE_ENDED)
                applyingPause = true
                try { player.pause() } finally { applyingPause = false }
            }
        }
        val playerListener = object : androidx.media3.common.Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && !applyingPause && reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    resumeOnForeground = false
                }
                if (playWhenReady && !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) pauseIfNeeded()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> pauseIfNeeded()
                Lifecycle.Event.ON_START -> if (resumeOnForeground) {
                    resumeOnForeground = false
                    player.play()
                }
                else -> Unit
            }
        }
        player.addListener(playerListener)
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) pauseIfNeeded()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(playerListener)
        }
    }
}
