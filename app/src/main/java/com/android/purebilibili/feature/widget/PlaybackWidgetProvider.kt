@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.audio.player.AudioMediaLibrary
import com.android.purebilibili.feature.audio.player.BackgroundAudioService
import com.android.purebilibili.feature.audio.player.SystemMediaPreferences
import com.android.purebilibili.feature.audio.player.awaitBackgroundAudio
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.resolveActivePlaybackLaunchIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException

/** 桌面控件只响应媒体事件和用户操作，不轮询进度或启动逐帧更新。 */
class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runPending(context) {
            val current = MiniPlayerManager.getInstanceOrNull()?.mediaSession?.player
            if (current?.currentMediaItem != null) {
                val owner = MiniPlayerManager.getInstanceOrNull()
                publish(context, current, owner?.currentTitle, owner?.currentBvid)
            }
            else {
                val future = MediaController.Builder(context, SessionToken(context,
                    ComponentName(context, BackgroundAudioService::class.java))).buildAsync()
                try {
                    val remote = future.awaitBackgroundAudio()
                    if (remote.currentMediaItem != null) publish(context, remote)
                    else {
                        val saved = AudioMediaLibrary(context).saved()
                        render(context, saved?.current?.title, saved?.current?.bvid, false, false, false)
                    }
                } finally { MediaController.releaseFuture(future) }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONTROL) {
            super.onReceive(context, intent)
            return
        }
        val command = intent.getStringExtra("command") ?: return
        if (command !in listOf("toggle", "previous", "next")) return
        runPending(context) {
            val controllers = mutableListOf<MediaController>()
            try {
                suspend fun connect(service: Class<*>): MediaController? {
                    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, service))).buildAsync()
                    return try { future.awaitBackgroundAudio().also(controllers::add) }
                    catch (cancelled: CancellationException) { MediaController.releaseFuture(future); throw cancelled }
                    catch (error: Exception) {
                        MediaController.releaseFuture(future)
                        Logger.d("PlaybackWidget", "No session in ${service.simpleName}: ${error.message}")
                        null
                    }
                }
                val local = MiniPlayerManager.getInstanceOrNull()?.mediaSession?.player?.takeIf { it.currentMediaItem != null }
                val active = local ?: connect(BackgroundAudioService::class.java)
                checkNotNull(active) { context.getString(R.string.playback_widget_empty) }
                when (command) {
                    "toggle" -> if (active.playWhenReady) active.pause() else {
                        if (active.currentMediaItem == null) {
                            check(!SystemMediaPreferences.libraryProtected(context)) { context.getString(R.string.audio_library_locked) }
                            check(AudioMediaLibrary(context).saved()?.current != null) { context.getString(R.string.playback_widget_empty) }
                        }
                        active.play()
                        if (active is MediaController && active.currentMediaItem == null) awaitPlaybackStart(active)
                    }
                    "previous" -> if (active.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) active.seekToPreviousMediaItem()
                    "next" -> if (active.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) active.seekToNextMediaItem()
                }
                // 实际状态随后由服务的 Player 事件更新。
                if (active.currentMediaItem != null) publish(context, active)
            } finally { controllers.forEach { it.release() } }
        }
    }

    private suspend fun awaitPlaybackStart(controller: MediaController) = suspendCancellableCoroutine<Unit> { continuation ->
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

    private fun runPending(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            try { withTimeout(8_000L) { block() } }
            catch (timeout: TimeoutCancellationException) {
                Logger.w("PlaybackWidget", "Widget operation timed out", timeout)
                Toast.makeText(context, R.string.playback_widget_error, Toast.LENGTH_SHORT).show()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Logger.w("PlaybackWidget", "Widget operation failed", error)
                Toast.makeText(context, error.message ?: context.getString(R.string.playback_widget_error), Toast.LENGTH_SHORT).show()
            } finally { pending.finish(); scope.cancel() }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(Intent(context, PlaybackWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }

        private const val ACTION_CONTROL = "com.android.purebilibili.widget.CONTROL"

        fun publish(context: Context, player: Player, title: String? = null, contentBvid: String? = null) {
            val media = player.currentMediaItem
            val bvid = contentBvid ?: media?.mediaMetadata?.extras?.getString("bvid")
                ?: media?.mediaId?.substringBefore(':')?.takeIf { it.startsWith("BV") }
            render(context, title ?: media?.mediaMetadata?.title?.toString(), bvid, player.playWhenReady,
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        }

        private fun render(context: Context, title: String?, bvid: String?, playing: Boolean, previous: Boolean, next: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_playback)
            views.setTextViewText(R.id.widget_title, title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.playback_widget_empty))
            views.setImageViewResource(R.id.widget_toggle, if (playing) R.drawable.ms_pause_24 else R.drawable.ms_play_arrow_24)
            views.setContentDescription(R.id.widget_toggle, context.getString(if (playing) R.string.playback_widget_pause else R.string.playback_widget_play))
            fun control(viewId: Int, command: String, enabled: Boolean = true) {
                val intent = Intent(context, PlaybackWidgetProvider::class.java).setAction(ACTION_CONTROL)
                    .setData(Uri.parse("bilipai-widget://control/$command")).putExtra("command", command)
                views.setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setBoolean(viewId, "setEnabled", enabled)
                views.setFloat(viewId, "setAlpha", if (enabled) 1f else 0.35f)
            }
            control(R.id.widget_toggle, "toggle")
            control(R.id.widget_previous, "previous", previous)
            control(R.id.widget_next, "next", next)
            views.setOnClickPendingIntent(R.id.widget_title, PendingIntent.getActivity(context, 10,
                resolveActivePlaybackLaunchIntent(context, bvid), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            fun shortcut(viewId: Int, target: String, request: Int) {
                views.setOnClickPendingIntent(viewId, PendingIntent.getActivity(context, request,
                    Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("bilipai://$target")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            shortcut(R.id.widget_later, "watch_later", 11)
            shortcut(R.id.widget_search, "search", 12)
            manager.updateAppWidget(ids, views)
        }
    }
}
