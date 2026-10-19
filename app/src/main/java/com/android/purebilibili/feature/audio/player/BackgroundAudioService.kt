@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.audio.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.SharedPlaybackRepository
import com.android.purebilibili.core.player.SharedPlaybackRequest
import com.android.purebilibili.core.store.MultiProcessPreferences
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.getBestAudio
import com.android.purebilibili.feature.video.player.PlayMode
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns only an audio renderer, a bounded transport queue and the system media session. */
class BackgroundAudioService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private var snapshot: BackgroundAudioSnapshot? = null
    private var preparing: SettableFuture<SessionResult>? = null
    private var nextLoad: Job? = null
    private var errorRecovery: Job? = null
    private var retriedMediaId: String? = null
    private var pauseRelease: Job? = null
    private var generation = 0L
    private var playedMs = 0L
    private var lastPlayTick = android.os.SystemClock.elapsedRealtime()
    private var wasPlaying = false
    private var historyStartedAt = System.currentTimeMillis() / 1000L
    private var committed = false
    private var nativeReleased = false
    private var restoring: Job? = null
    private val stateStore by lazy { MultiProcessPreferences.get(this, "background_audio") }
    private val snapshotKey = stringPreferencesKey("snapshot")

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpDataSource.Factory(NetworkModule.okHttpClient).setDefaultRequestProperties(
            mapOf("Referer" to "https://www.bilibili.com/")
        )
        player = ExoPlayer.Builder(this,
            com.android.purebilibili.core.player.HiResCompatibleRenderersFactory(this)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(10_000, 30_000, 1000, 2000)
                .setTargetBufferBytes(4 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false).build())
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                setHandleAudioBecomingNoisy(true)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                addListener(listener)
            }
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setCallback(callback).setSessionActivity(activity).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID).setChannelId(CHANNEL_ID).build())
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(5000L)
                if (nativeReleased) break
                updateWatchTime()
                if (committed) {
                    try {
                        persist()
                        if (++ticks % 6 == 0) reportHistory()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A temporary storage/network failure must not stop audio playback.
                    }
                }
            }
        }
        scope.launch {
            TokenManager.observeServiceSession(this@BackgroundAudioService) {
                val current = snapshot
                if (current != null && current.accountMid != TokenManager.midCache) {
                    scope.launch { closeSession() }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROMOTE || intent == null) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "后台音频", NotificationManager.IMPORTANCE_LOW))
            ServiceCompat.startForeground(this, NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("BiliPai").setContentText("正在准备后台音频")
                    .setSilent(true).setOngoing(true).build(),
                if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        }
        if (intent == null && snapshot == null) restoring = scope.launch {
            val saved = stateStore.data.first()[snapshotKey]?.let {
                runCatching { backgroundAudioJson.decodeFromString(BackgroundAudioSnapshot.serializer(), it) }.getOrNull()
            }
            if (saved == null || !saved.playing) closeSession() else {
                try {
                    prepare(saved.copy(audioUrl = ""), resume = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    closeSession()
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.uid != android.os.Process.myUid() && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            if (controller.uid == android.os.Process.myUid()) {
                listOf(AUDIO_PREPARE, AUDIO_COMMIT, AUDIO_RETURN, AUDIO_SNAPSHOT, AUDIO_CLOSE, AUDIO_TRANSPORT)
                    .forEach { commands.add(SessionCommand(it, Bundle.EMPTY)) }
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands.build()).build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            command: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            val result = SettableFuture.create<SessionResult>()
            scope.launch {
                try {
                    when (command.customAction) {
                        AUDIO_PREPARE -> {
                            val request = requireNotNull(args.audioSnapshot())
                            require(request.queue.size in 1..128 && request.current != null)
                            preparing?.set(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
                            preparing = result
                            prepare(request, resume = false)
                            return@launch
                        }
                        AUDIO_COMMIT -> {
                            check(snapshot != null && player.playbackState == Player.STATE_READY)
                            committed = true
                            player.seekTo(args.getLong("position_ms", player.currentPosition))
                            player.play()
                            persist()
                            prefetchNext()
                        }
                        AUDIO_RETURN -> {
                            restoring?.join()
                            val state = currentSnapshot()
                            committed = false
                            player.pause()
                            result.set(SessionResult(SessionResult.RESULT_SUCCESS, state?.toAudioBundle() ?: Bundle.EMPTY))
                            closeSession()
                            return@launch
                        }
                        AUDIO_SNAPSHOT -> {
                            restoring?.join()
                            result.set(SessionResult(SessionResult.RESULT_SUCCESS, currentSnapshot()?.toAudioBundle() ?: Bundle.EMPTY))
                            return@launch
                        }
                        AUDIO_TRANSPORT -> {
                            val current = checkNotNull(snapshot)
                            val mode = PlayMode.valueOf(checkNotNull(args.getString("mode")))
                            val queue = args.getString("queue")?.let {
                                backgroundAudioJson.decodeFromString(
                                    kotlinx.serialization.builtins.ListSerializer(
                                        com.android.purebilibili.feature.video.player.PlaylistItem.serializer()
                                    ), it
                                )
                            }?.takeIf { entries -> entries.size in 1..128 && entries.none { it.isBangumi || it.bvid.isBlank() } &&
                                entries.distinctBy { it.bvid }.size == entries.size }
                            val newIndex = queue?.indexOfFirst { it.bvid == current.current?.bvid }
                            val next = current.copy(playMode = mode, shuffle = args.getBoolean("shuffle"),
                                reportHistory = args.getBoolean("report_history", current.reportHistory),
                                queue = queue?.takeIf { newIndex != null && newIndex >= 0 } ?: current.queue,
                                index = newIndex?.takeIf { it >= 0 } ?: current.index)
                            snapshot = next
                            if (next.queue != current.queue || next.playMode != current.playMode || next.shuffle != current.shuffle) {
                                generation++
                                nextLoad?.cancel()
                                if (player.mediaItemCount > player.currentMediaItemIndex + 1) {
                                    player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
                                }
                                if (player.currentMediaItemIndex > 0) player.removeMediaItems(0, player.currentMediaItemIndex)
                                player.repeatMode = if (mode == PlayMode.REPEAT_ONE || next.queue.size == 1 &&
                                    mode == PlayMode.REPEAT_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                prefetchNext()
                            }
                            persist()
                        }
                        AUDIO_CLOSE -> closeSession()
                        else -> error("Unsupported audio command")
                    }
                    result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (e: CancellationException) {
                    result.cancel(false)
                    throw e
                } catch (e: Exception) {
                    preparing = null
                    result.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                    closeSession()
                }
            }
            return result
        }
    }

    private suspend fun prepare(request: BackgroundAudioSnapshot, resume: Boolean) {
        TokenManager.refreshServiceSession(this)
        check(request.accountMid == TokenManager.midCache)
        generation++
        nextLoad?.cancel()
        errorRecovery?.cancel()
        snapshot = request
        committed = resume
        val item = request.current ?: error("Missing audio entry")
        val media = if (request.audioUrl.isNotBlank()) mediaItem(request.index, item, request.audioUrl) else resolve(request.index)
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), request.handleAudioFocus)
        player.volume = request.volume.coerceIn(0f, 1f)
        player.setMediaItem(media, request.positionMs.coerceAtLeast(0L))
        player.setPlaybackSpeed(request.speed.coerceIn(0.25f, 4f))
        player.repeatMode = if (request.playMode == PlayMode.REPEAT_ONE ||
            request.queue.size == 1 && request.playMode == PlayMode.REPEAT_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.playWhenReady = resume
        player.prepare()
    }

    private fun mediaItem(index: Int, entry: com.android.purebilibili.feature.video.player.PlaylistItem, url: String): MediaItem =
        MediaItem.Builder().setMediaId("${entry.bvid}:${entry.cid}").setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).setArtist(entry.owner)
                .setExtras(Bundle().apply {
                    putInt("queue_index", index); putString("bvid", entry.bvid); putLong("cid", entry.cid)
                    putString("cover", entry.cover)
                }).build()).build()

    private suspend fun resolve(index: Int): MediaItem {
        val request = checkNotNull(snapshot)
        TokenManager.refreshServiceSession(this)
        check(request.accountMid == TokenManager.midCache)
        val entry = request.queue[index]
        val loaded = SharedPlaybackRepository.load(SharedPlaybackRequest(
            bvid = entry.bvid, cid = entry.cid, quality = request.quality,
            audioQuality = request.audioQuality, audioLanguage = request.audioLanguage
        ), allowTokenRefresh = false, usePlaybackAccount = false).getOrThrow()
        TokenManager.refreshServiceSession(this)
        check(request.accountMid == TokenManager.midCache)
        // Audio-only DASH is required; a video-only fallback is left to the foreground player.
        val audio = loaded.streams.dash?.getBestAudio(request.audioQuality)?.getValidUrl()
            ?.takeIf { it.startsWith("https://") } ?: error("Audio stream unavailable")
        return mediaItem(index, entry.copy(cid = loaded.info.cid), audio)
    }

    private fun prefetchNext() {
        val request = snapshot ?: return
        if (!committed || nextLoad?.isActive == true) return
        val next = nextBackgroundAudioIndex(request.queue.size, request.index, request.playMode, request.shuffle)
        val previous = (request.index - 1).takeIf {
            it >= 0 && player.currentMediaItemIndex == 0 && !request.shuffle && request.playMode != PlayMode.SHUFFLE
        }
        val needsNext = next != null && next != request.index && player.nextMediaItemIndex == C.INDEX_UNSET
        if (!needsNext && previous == null) return
        val ticket = generation
        nextLoad = scope.launch {
            try {
                if (previous != null) {
                    val media = try {
                        resolve(previous)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    if (ticket != generation || !committed) return@launch
                    if (media != null) player.addMediaItem(0, media)
                }
                if (next != null && next != request.index && player.nextMediaItemIndex == C.INDEX_UNSET) {
                    val media = resolve(next)
                    if (ticket == generation && committed) player.addMediaItem(media)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the current audio intact; a failed prefetch must not stop it.
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED && committed) {
                prefetchNext()
                onIsPlayingChanged(false)
            }
            if (state == Player.STATE_READY) {
                preparing?.set(SessionResult(SessionResult.RESULT_SUCCESS))
                preparing = null
                if (committed) prefetchNext()
            }
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            updateWatchTime()
            playedMs = 0L
            historyStartedAt = System.currentTimeMillis() / 1000L
            val media = item ?: return
            val current = snapshot ?: return
            val bvid = media.mediaMetadata.extras?.getString("bvid") ?: return
            val index = current.queue.indexOfFirst { it.bvid == bvid }
            if (index !in current.queue.indices) return
            val cid = media.mediaMetadata.extras?.getLong("cid") ?: 0L
            snapshot = current.copy(index = index, queue = current.queue.toMutableList().also {
                it[index] = it[index].copy(cid = cid)
            }, audioUrl = "")
            generation++
            nextLoad?.cancel()
            if (player.currentMediaItemIndex > 1) player.removeMediaItems(0, player.currentMediaItemIndex - 1)
            if (player.mediaItemCount > player.currentMediaItemIndex + 2) {
                player.removeMediaItems(player.currentMediaItemIndex + 2, player.mediaItemCount)
            }
            if (committed) prefetchNext()
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (committed && playWhenReady && player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
            onIsPlayingChanged(player.isPlaying)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateWatchTime()
            pauseRelease?.cancel()
            if (committed) scope.launch { persist() }
            if (committed && (!player.playWhenReady || player.playbackState == Player.STATE_ENDED)) pauseRelease = scope.launch {
                delay(30_000L)
                if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
                    persist()
                    player.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            preparing?.set(SessionResult(SessionResult.RESULT_ERROR_IO))
            preparing = null
            val current = currentSnapshot()
            val key = player.currentMediaItem?.mediaId
            if (!committed || current == null) {
                scope.launch { closeSession() }
                return
            }
            player.pause()
            if (key == null || retriedMediaId == key || errorRecovery?.isActive == true) return
            retriedMediaId = key
            val ticket = generation
            errorRecovery = scope.launch {
                try {
                    val media = resolve(current.index)
                    if (ticket != generation || !committed) return@launch
                    player.setMediaItem(media, current.positionMs)
                    player.playWhenReady = current.playing
                    player.prepare()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The session remains paused and can be reclaimed by the video page.
                    persist()
                }
            }
        }
    }

    private fun updateWatchTime() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (wasPlaying) playedMs += (now - lastPlayTick).coerceAtLeast(0L)
        lastPlayTick = now
        wasPlaying = committed && player.isPlaying
    }

    private suspend fun reportHistory() {
        val state = currentSnapshot() ?: return
        val item = state.current ?: return
        if (!state.reportHistory || !state.playing || state.accountMid == null) return
        TokenManager.refreshServiceSession(this)
        if (TokenManager.midCache != state.accountMid) return
        com.android.purebilibili.data.repository.HistoryRepository.reportPlayback(
            item.bvid, item.cid, state.positionMs / 1000L, playedMs / 1000L, historyStartedAt
        )
    }

    private fun currentSnapshot(): BackgroundAudioSnapshot? = snapshot?.copy(
        positionMs = player.currentPosition.coerceAtLeast(0L), playing = player.playWhenReady && committed && player.playbackState != Player.STATE_ENDED,
        speed = player.playbackParameters.speed, audioUrl = ""
    )

    private suspend fun persist() {
        val state = currentSnapshot() ?: return
        stateStore.edit { it[snapshotKey] = backgroundAudioJson.encodeToString(BackgroundAudioSnapshot.serializer(), state) }
    }

    private suspend fun closeSession() {
        if (nativeReleased) return
        committed = false
        generation++
        nextLoad?.cancel()
        errorRecovery?.cancel()
        snapshot = null
        player.stop()
        try {
            stateStore.edit { it.remove(snapshotKey) }
        } finally {
            releaseNativeResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun releaseNativeResources() {
        if (nativeReleased) return
        nativeReleased = true
        preparing?.set(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))
        preparing = null
        session.release()
        player.release()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseNativeResources()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PROMOTE = "bilipai.audio.PROMOTE"
        private const val NOTIFICATION_ID = 5101
        private const val CHANNEL_ID = "background_audio"
    }
}
