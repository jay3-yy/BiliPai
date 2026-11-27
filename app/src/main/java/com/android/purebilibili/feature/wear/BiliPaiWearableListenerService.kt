@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.wear

import android.content.ComponentName
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.audio.player.AudioMediaLibrary
import com.android.purebilibili.feature.audio.player.BackgroundAudioService
import com.android.purebilibili.feature.audio.player.SystemMediaPreferences
import com.android.purebilibili.feature.audio.player.awaitBackgroundAudio
import com.android.purebilibili.feature.tile.isAudioTileControllerActivelyPlaying
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 手机端 Wear OS 腕上伴生遥控监听服务。
 * 优先调度活跃视频播放器（MiniPlayerManager），无活跃视频时无缝接管 :playback 后台听视频进程，
 * 严格遵循隐私锁边界与 Media3 主线程访问纪律。
 */
class BiliPaiWearableListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WEAR_PATH_COMMAND) {
            super.onMessageReceived(messageEvent)
            return
        }
        val command = WearRemoteCommand.fromWire(messageEvent.data.toString(Charsets.UTF_8)) ?: return
        val sourceNodeId = messageEvent.sourceNodeId
        serviceScope.launch {
            try {
                val snapshot = withTimeout(6_000L) {
                    executeRemoteCommand(command)
                }
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(sourceNodeId, WEAR_PATH_STATE, snapshot.toJsonBytes())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(TAG, "Wear remote command failed: ${command.wireValue}", error)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun executeRemoteCommand(command: WearRemoteCommand): WearPlaybackStateSnapshot {
        val appContext = applicationContext
        val mini = MiniPlayerManager.getInstanceOrNull()
        val localPlayer = mini?.player?.takeIf { it.currentMediaItem != null }
        if (localPlayer != null) {
            applyPlayerCommand(localPlayer, command)
            return snapshotFromPlayer(
                player = localPlayer,
                fallbackTitle = mini.currentTitle,
                fallbackAuthor = mini.currentOwner,
            )
        }

        val protected = SystemMediaPreferences.libraryProtected(appContext)
        val controllerFuture = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, BackgroundAudioService::class.java)),
        ).buildAsync()
        var controller: MediaController? = null
        return try {
            controller = controllerFuture.awaitBackgroundAudio()
            if (controller.currentMediaItem == null && protected) {
                return WearPlaybackStateSnapshot(locked = true)
            }
            if (controller.currentMediaItem == null && command == WearRemoteCommand.TOGGLE_PLAY) {
                if (AudioMediaLibrary(appContext).saved()?.current != null) {
                    controller.play()
                }
            } else if (controller.currentMediaItem != null) {
                applyPlayerCommand(controller, command)
            }
            snapshotFromPlayer(controller, fallbackTitle = null, fallbackAuthor = null)
        } finally {
            controller?.release()
            MediaController.releaseFuture(controllerFuture)
        }
    }

    private fun applyPlayerCommand(player: Player, command: WearRemoteCommand) {
        when (command) {
            WearRemoteCommand.TOGGLE_PLAY -> {
                val activelyPlaying = isAudioTileControllerActivelyPlaying(
                    playWhenReady = player.playWhenReady,
                    playbackState = player.playbackState,
                    hasCurrentMediaItem = player.currentMediaItem != null,
                )
                if (activelyPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                    }
                    player.play()
                }
            }
            WearRemoteCommand.SEEK_FORWARD_15S -> {
                val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                val target = (player.currentPosition + WEAR_SEEK_STEP_MS).coerceAtMost(duration)
                player.seekTo(target)
            }
            WearRemoteCommand.SEEK_BACKWARD_15S -> {
                val target = (player.currentPosition - WEAR_SEEK_STEP_MS).coerceAtLeast(0L)
                player.seekTo(target)
            }
            WearRemoteCommand.NEXT_ITEM -> {
                if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
                    player.seekToNextMediaItem()
                }
            }
            WearRemoteCommand.PREVIOUS_ITEM -> {
                if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
                    player.seekToPreviousMediaItem()
                }
            }
            WearRemoteCommand.CYCLE_SPEED -> {
                val nextSpeed = resolveNextWearPlaybackSpeed(player.playbackParameters.speed)
                player.setPlaybackSpeed(nextSpeed)
            }
            WearRemoteCommand.REQUEST_STATE -> Unit
        }
    }

    private fun snapshotFromPlayer(
        player: Player,
        fallbackTitle: String?,
        fallbackAuthor: String?,
    ): WearPlaybackStateSnapshot {
        val meta = player.currentMediaItem?.mediaMetadata
        val hasMedia = player.currentMediaItem != null
        val playing = isAudioTileControllerActivelyPlaying(
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            hasCurrentMediaItem = hasMedia,
        )
        return WearPlaybackStateSnapshot(
            title = meta?.title?.toString()?.takeIf { it.isNotBlank() } ?: fallbackTitle.orEmpty(),
            author = meta?.artist?.toString()?.takeIf { it.isNotBlank() } ?: fallbackAuthor.orEmpty(),
            playing = playing,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            speed = player.playbackParameters.speed,
            canPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
            canNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
            hasActiveMedia = hasMedia,
        )
    }

    companion object {
        private const val TAG = "BiliPaiWearService"
    }
}
