@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.android.purebilibili.R
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.toUserFriendlyMessage
import com.android.purebilibili.feature.audio.player.AudioMediaLibrary
import com.android.purebilibili.feature.audio.player.BackgroundAudioService
import com.android.purebilibili.feature.audio.player.SystemMediaPreferences
import com.android.purebilibili.feature.audio.player.awaitBackgroundAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 下拉控制中心「听视频续播」快捷磁贴。
 * 运行于 :playback 独立音频进程，点击时直接控制或冷启动续播最近音频队列，不拉起主界面进程。
 */
class BackgroundAudioTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val action = Runnable { toggleBackgroundAudio() }
        if (isLocked) {
            unlockAndRun(action)
        } else {
            action.run()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshTileState() {
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            val appContext = applicationContext
            val controllerFuture = MediaController.Builder(
                appContext,
                SessionToken(appContext, ComponentName(appContext, BackgroundAudioService::class.java)),
            ).buildAsync()
            var controller: MediaController? = null
            try {
                controller = withTimeout(3_000L) { controllerFuture.awaitBackgroundAudio() }
                val isPlaying = isAudioTileControllerActivelyPlaying(
                    playWhenReady = controller.playWhenReady,
                    playbackState = controller.playbackState,
                    hasCurrentMediaItem = controller.currentMediaItem != null,
                )
                val activeTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString()
                val protected = SystemMediaPreferences.libraryProtected(appContext)
                val savedTitle = if (activeTitle == null && !protected) {
                    AudioMediaLibrary(appContext).saved()?.current?.title
                } else {
                    null
                }
                applyPresentation(
                    isPlaying = isPlaying,
                    activeTitle = activeTitle,
                    savedTitle = savedTitle,
                    libraryProtected = protected,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.d(TAG, "Refresh audio tile fallback to snapshot: ${error.message}")
                val protected = SystemMediaPreferences.libraryProtected(appContext)
                val savedTitle = if (!protected) {
                    AudioMediaLibrary(appContext).saved()?.current?.title
                } else {
                    null
                }
                applyPresentation(
                    isPlaying = false,
                    activeTitle = null,
                    savedTitle = savedTitle,
                    libraryProtected = protected,
                )
            } finally {
                controller?.release()
                MediaController.releaseFuture(controllerFuture)
            }
        }
    }

    private fun toggleBackgroundAudio() {
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            val appContext = applicationContext
            val controllerFuture = MediaController.Builder(
                appContext,
                SessionToken(appContext, ComponentName(appContext, BackgroundAudioService::class.java)),
            ).buildAsync()
            var controller: MediaController? = null
            try {
                withTimeout(8_000L) {
                    controller = controllerFuture.awaitBackgroundAudio()
                    val active = requireNotNull(controller)
                    val currentlyPlaying = isAudioTileControllerActivelyPlaying(
                        playWhenReady = active.playWhenReady,
                        playbackState = active.playbackState,
                        hasCurrentMediaItem = active.currentMediaItem != null,
                    )
                    if (currentlyPlaying) {
                        active.pause()
                    } else {
                        if (active.currentMediaItem == null) {
                            check(!SystemMediaPreferences.libraryProtected(appContext)) {
                                appContext.getString(R.string.audio_library_locked)
                            }
                            check(AudioMediaLibrary(appContext).saved()?.current != null) {
                                appContext.getString(R.string.playback_widget_empty)
                            }
                        } else if (active.playbackState == Player.STATE_ENDED) {
                            active.seekToDefaultPosition()
                        }
                        active.play()
                        if (active.currentMediaItem == null) {
                            awaitPlaybackStart(active)
                        }
                    }
                    applyPresentation(
                        isPlaying = isAudioTileControllerActivelyPlaying(
                            playWhenReady = active.playWhenReady,
                            playbackState = active.playbackState,
                            hasCurrentMediaItem = active.currentMediaItem != null,
                        ),
                        activeTitle = active.currentMediaItem?.mediaMetadata?.title?.toString(),
                        savedTitle = null,
                        libraryProtected = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (timeout: TimeoutCancellationException) {
                Logger.w(TAG, "Toggle audio tile timed out", timeout)
                Toast.makeText(appContext, R.string.playback_widget_error, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Logger.w(TAG, "Toggle audio tile failed", error)
                Toast.makeText(
                    appContext,
                    error.toUserFriendlyMessage(appContext.getString(R.string.playback_widget_error)),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                controller?.release()
                MediaController.releaseFuture(controllerFuture)
            }
        }
    }

    private fun applyPresentation(
        isPlaying: Boolean,
        activeTitle: String?,
        savedTitle: String?,
        libraryProtected: Boolean,
    ) {
        val tile = qsTile ?: return
        val label = getString(R.string.qs_tile_audio_label)
        val presentation = resolveAudioTilePresentation(
            isPlaying = isPlaying,
            activeTitle = activeTitle,
            savedTitle = savedTitle,
            libraryProtected = libraryProtected,
            playingFallbackSubtitle = getString(R.string.qs_tile_audio_playing),
            emptySubtitle = getString(R.string.qs_tile_audio_empty),
            lockedSubtitle = getString(R.string.qs_tile_audio_locked),
            tileLabel = label,
        )
        tile.label = label
        tile.state = presentation.state
        tile.icon = Icon.createWithResource(this, R.drawable.ms_headphones_24)
        tile.contentDescription = presentation.contentDescription
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = presentation.subtitle
        }
        tile.updateTile()
    }

    private suspend fun awaitPlaybackStart(controller: MediaController) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val error = player.playerError
                    if (error != null) {
                        player.removeListener(this)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } else if (player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE) {
                        player.removeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            controller.addListener(listener)
            if (controller.currentMediaItem != null && controller.playbackState != Player.STATE_IDLE) {
                controller.removeListener(listener)
                continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { controller.removeListener(listener) }
        }

    companion object {
        private const val TAG = "BackgroundAudioTile"

        fun requestListeningState(context: Context) {
            val appContext = context.applicationContext ?: context
            TileService.requestListeningState(
                appContext,
                ComponentName(appContext, BackgroundAudioTileService::class.java),
            )
        }
    }
}
