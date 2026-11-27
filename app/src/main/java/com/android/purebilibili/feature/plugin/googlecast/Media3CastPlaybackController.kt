@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.plugin.googlecast

import android.content.Context
import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouter
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.plugin.sdk.CastPluginMediaRequest
import com.android.purebilibili.plugin.sdk.CastPluginPlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal const val MEDIA3_CAST_SESSION_TIMEOUT_MS = 10_000L
internal const val MEDIA3_CAST_SESSION_POLL_INTERVAL_MS = 100L
private const val MEDIA3_CAST_FALLBACK_TITLE = "BiliPai Video"
private const val MEDIA3_CAST_DEFAULT_MIME_TYPE = "video/mp4"

internal fun buildMedia3CastMediaItem(request: CastPluginMediaRequest): MediaItem {
    val safeTitle = request.title.trim().ifEmpty { MEDIA3_CAST_FALLBACK_TITLE }
    val safeCreator = request.creator.trim().takeIf { it.isNotEmpty() }
    val safeMime = request.contentType.trim().ifEmpty { MEDIA3_CAST_DEFAULT_MIME_TYPE }
    val metadata = MediaMetadata.Builder()
        .setTitle(safeTitle)
        .apply {
            if (safeCreator != null) {
                setArtist(safeCreator)
                setSubtitle(safeCreator)
            }
        }
        .build()
    return MediaItem.Builder()
        .setUri(Uri.parse(request.url))
        .setMimeType(safeMime)
        .setMediaMetadata(metadata)
        .build()
}

/** 保持既有 Cast 会话等待语义：10s 超时、100ms 轮询间隔。 */
internal fun shouldContinueWaitingForMedia3CastSession(
    remotePlaybackActive: Boolean,
    elapsedMs: Long,
    timeoutMs: Long = MEDIA3_CAST_SESSION_TIMEOUT_MS,
): Boolean = elapsedMs < timeoutMs && !remotePlaybackActive

/**
 * 依据 RemoteCastPlayer 真实会话可用性判定远端投屏是否活跃：
 * `RemoteCastPlayer.DEVICE_INFO_REMOTE_EMPTY` 在断连后仍恒定报告 `PLAYBACK_TYPE_REMOTE`，
 * 且旧 `currentMediaItem` 可能残留，因此必须以 `isCastSessionAvailable` 为前提。
 */
internal fun resolveMedia3CastPlaybackActive(
    isCastSessionAvailable: Boolean,
    hasCurrentMediaItem: Boolean,
    playbackState: Int,
): Boolean = isCastSessionAvailable && (hasCurrentMediaItem || playbackState != Player.STATE_IDLE)

/**
 * 基于 Jetpack Media3 `media3-cast:1.11.1` 的 Google Cast 播放控制器：
 * - 通过 RemoteCastPlayer 承担插件远端投屏会话、播控与进度同步；
 * - 提供 createUnifiedCastPlayer(context, localPlayer) 供 MediaSessionService 统一管理本地与远端 Cast 切换；
 * - 所有 Media3 Player 读写均严格在主线程（Dispatchers.Main.immediate）执行。
 */
internal object Media3CastPlaybackController {
    private const val TAG = "Media3CastController"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var remotePlayer: RemoteCastPlayer? = null
    private var positionSyncJob: Job? = null
    private var activeTitle: String = ""
    private var activeDeviceLabel: String = ""

    private val _playbackState = MutableStateFlow(CastPluginPlaybackState())
    val playbackState: StateFlow<CastPluginPlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            remotePlayer?.let(::publishState)
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            remotePlayer?.let(::publishState)
        }
    }

    private val sessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            remotePlayer?.let(::publishState)
        }

        override fun onCastSessionUnavailable() {
            handleSessionDisconnected()
        }
    }

    /**
     * 为 MediaSessionService 构造统一管理本地 ExoPlayer 与远端 Google Cast 的 Media3 CastPlayer。
     * 无 GMS Cast 运行环境时优雅回退返回原 localPlayer，不影响正常本地播放。
     */
    fun wrapSessionPlayerIfAvailable(context: Context, localPlayer: Player): Player {
        return runCatching {
            CastPlayer.Builder(context.applicationContext ?: context)
                .setLocalPlayer(localPlayer)
                .build()
        }.getOrElse { error ->
            Logger.d(TAG, "Media3 CastPlayer unavailable, keeping local player: ${error.message}")
            localPlayer
        }
    }

    suspend fun cast(
        context: Context,
        routeId: String,
        deviceLabel: String,
        media: CastPluginMediaRequest,
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        try {
            val appContext = context.applicationContext ?: context
            val router = MediaRouter.getInstance(appContext)
            val cachedRoute = GoogleCastRouteManager.getCachedRoute(routeId)
            val mediaRoute = cachedRoute ?: router.routes.firstOrNull { it.id == routeId }
                ?: return@withContext Result.failure(IllegalArgumentException("未找到 Google Cast 设备"))

            mediaRoute.select()

            // 等待底层 Cast 上下文与会话就绪（严格保留 10s 超时 / 100ms 轮询语义）。
            val castContext = GoogleCastRouteManager.awaitCastContext(appContext)
            val startMs = System.currentTimeMillis()
            withTimeout(MEDIA3_CAST_SESSION_TIMEOUT_MS) {
                var sessionReady = castContext.sessionManager.currentCastSession?.remoteMediaClient != null
                while (shouldContinueWaitingForMedia3CastSession(
                        remotePlaybackActive = sessionReady,
                        elapsedMs = System.currentTimeMillis() - startMs,
                    )
                ) {
                    delay(MEDIA3_CAST_SESSION_POLL_INTERVAL_MS)
                    sessionReady = castContext.sessionManager.currentCastSession?.remoteMediaClient != null
                }
                if (!sessionReady) {
                    throw IllegalStateException("无法获取 Media3 Cast 远端会话")
                }
            }

            val player = ensureRemotePlayer(appContext)
            activeTitle = media.title.trim().ifEmpty { MEDIA3_CAST_FALLBACK_TITLE }
            activeDeviceLabel = deviceLabel
            val item = buildMedia3CastMediaItem(media)
            player.setMediaItem(item, media.startPositionMs.coerceAtLeast(0L))
            player.playWhenReady = media.autoplay
            player.prepare()
            publishState(player)
            startPositionSync(player)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(IllegalStateException("Google Cast 会话连接超时"))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun play(): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val player = requireConnectedRemotePlayer()
            ?: return@withContext Result.failure(IllegalStateException("当前未连接 Google Cast 会话"))
        player.play()
        publishState(player)
        Result.success(Unit)
    }

    suspend fun pause(): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val player = requireConnectedRemotePlayer()
            ?: return@withContext Result.failure(IllegalStateException("当前未连接 Google Cast 会话"))
        player.pause()
        publishState(player)
        Result.success(Unit)
    }

    suspend fun seek(positionMs: Long): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val player = requireConnectedRemotePlayer()
            ?: return@withContext Result.failure(IllegalStateException("当前未连接 Google Cast 会话"))
        player.seekTo(positionMs.coerceAtLeast(0L))
        publishState(player)
        Result.success(Unit)
    }

    fun release() {
        positionSyncJob?.cancel()
        positionSyncJob = null
        remotePlayer?.let { player ->
            runCatching { player.setSessionAvailabilityListener(null) }
            runCatching { player.removeListener(playerListener) }
            runCatching { player.release() }
        }
        remotePlayer = null
        activeTitle = ""
        activeDeviceLabel = ""
        _playbackState.value = CastPluginPlaybackState()
    }

    private fun requireConnectedRemotePlayer(): RemoteCastPlayer? {
        val player = remotePlayer ?: return null
        if (!player.isCastSessionAvailable) {
            handleSessionDisconnected()
            return null
        }
        return player
    }

    private fun handleSessionDisconnected() {
        positionSyncJob?.cancel()
        positionSyncJob = null
        activeTitle = ""
        activeDeviceLabel = ""
        _playbackState.value = CastPluginPlaybackState()
    }

    private fun ensureRemotePlayer(appContext: Context): RemoteCastPlayer {
        remotePlayer?.let { return it }
        return RemoteCastPlayer.Builder(appContext)
            .build()
            .also { player ->
                player.addListener(playerListener)
                player.setSessionAvailabilityListener(sessionAvailabilityListener)
                remotePlayer = player
            }
    }

    private fun startPositionSync(player: RemoteCastPlayer) {
        positionSyncJob?.cancel()
        positionSyncJob = scope.launch {
            while (isActive && remotePlayer === player && player.isCastSessionAvailable) {
                publishState(player)
                delay(1_000L)
            }
            if (remotePlayer === player && !player.isCastSessionAvailable) {
                handleSessionDisconnected()
            }
        }
    }

    private fun publishState(player: RemoteCastPlayer) {
        if (!player.isCastSessionAvailable) {
            handleSessionDisconnected()
            return
        }
        val isRemoteActive = resolveMedia3CastPlaybackActive(
            isCastSessionAvailable = true,
            hasCurrentMediaItem = player.currentMediaItem != null,
            playbackState = player.playbackState,
        )
        val durationMs = player.duration.takeIf { it > 0L } ?: 0L
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        _playbackState.update {
            CastPluginPlaybackState(
                isActive = isRemoteActive,
                deviceLabel = activeDeviceLabel,
                title = activeTitle,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = positionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedMs,
                canSeek = player.isCurrentMediaItemSeekable || durationMs > 0L,
            )
        }
    }
}
