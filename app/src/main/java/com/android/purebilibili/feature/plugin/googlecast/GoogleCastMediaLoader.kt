package com.android.purebilibili.feature.plugin.googlecast

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class GoogleCastMediaMetadataPolicy(val title: String, val subtitle: String?)

internal object GoogleCastMediaLoader {

    private const val DEFAULT_CONTENT_TYPE = "video/mp4"
    private const val FALLBACK_TITLE = "BiliPai Video"
    private const val SESSION_TIMEOUT_MS = 10_000L
    private const val SESSION_POLL_INTERVAL_MS = 100L

    internal fun resolveGoogleCastMediaMetadata(title: String, creator: String): GoogleCastMediaMetadataPolicy {
        return GoogleCastMediaMetadataPolicy(
            title = title.ifBlank { FALLBACK_TITLE },
            subtitle = if (creator.isNotBlank()) creator else null
        )
    }

    internal fun shouldContinueWaitingForSession(
        sessionExists: Boolean,
        remoteClientReady: Boolean,
        elapsedMs: Long,
        timeoutMs: Long = SESSION_TIMEOUT_MS
    ): Boolean {
        return elapsedMs < timeoutMs && (!sessionExists || !remoteClientReady)
    }

    fun buildMediaLoadRequest(
        url: String,
        title: String,
        creator: String = "",
        contentType: String = DEFAULT_CONTENT_TYPE,
        startPositionMs: Long = 0L,
        autoplay: Boolean = true
    ): MediaLoadRequestData {
        val policy = resolveGoogleCastMediaMetadata(title, creator)
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, policy.title)
            if (policy.subtitle != null) {
                putString(MediaMetadata.KEY_SUBTITLE, policy.subtitle)
            }
        }

        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()

        return MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(autoplay)
            .setCurrentTime(startPositionMs.coerceAtLeast(0L))
            .build()
    }

    suspend fun loadMedia(
        context: Context,
        routeId: String,
        url: String,
        title: String,
        creator: String = "",
        contentType: String = DEFAULT_CONTENT_TYPE,
        startPositionMs: Long = 0L,
        autoplay: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        try {
            val router = MediaRouter.getInstance(context)
            val cachedRoute = GoogleCastRouteManager.getCachedRoute(routeId)
            val mediaRoute = cachedRoute ?: router.routes.firstOrNull { it.id == routeId }
            if (mediaRoute == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("未找到 Google Cast 设备")
                )
            }

            mediaRoute.select()

            val castContext = GoogleCastRouteManager.awaitCastContext(context)
            val startMs = System.currentTimeMillis()
            val session = withTimeout(SESSION_TIMEOUT_MS) {
                var session = castContext.sessionManager.currentCastSession
                while (shouldContinueWaitingForSession(
                        sessionExists = session != null,
                        remoteClientReady = session?.remoteMediaClient != null,
                        elapsedMs = System.currentTimeMillis() - startMs
                    )) {
                    delay(SESSION_POLL_INTERVAL_MS)
                    session = castContext.sessionManager.currentCastSession
                }
                if (session == null || session.remoteMediaClient == null) {
                    throw IllegalStateException("无法获取远程媒体客户端")
                }
                session
            }

            val remoteMediaClient = session.remoteMediaClient!!
            val request = buildMediaLoadRequest(url, title, creator, contentType, startPositionMs, autoplay)
            remoteMediaClient.load(request)

            Result.success(Unit)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(IllegalStateException("Google Cast 会话连接超时"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
