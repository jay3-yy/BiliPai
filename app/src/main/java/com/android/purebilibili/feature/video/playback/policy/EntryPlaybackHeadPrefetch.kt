package com.android.purebilibili.feature.video.playback.policy

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.PlaybackMediaCache
import com.android.purebilibili.core.player.buildPlaybackCacheKey
import com.android.purebilibili.core.player.shouldUsePlaybackMediaCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal const val ENTRY_VIDEO_HEAD_PREFETCH_BYTES = 512L * 1024L
internal const val ENTRY_AUDIO_HEAD_PREFETCH_BYTES = 128L * 1024L

internal data class EntryPlaybackHeadTarget(
    val url: String,
    val cacheKey: String,
    val lengthBytes: Long,
)

/**
 * 进场转场等待起播许可期间，为尚未启动 provisional 预缓冲的 HTTP/HTTPS 音视频轨
 * 规划头部字节预取目标，与播放器实际使用的 CDN cacheKey 保持一致。
 */
internal fun resolveEntryPlaybackHeadPrefetchTargets(
    videoUrl: String,
    audioUrl: String?,
    cdnCacheKeysByUrl: Map<String, String>,
    hasProvisionalPlayer: Boolean,
    playbackStartAllowed: Boolean,
    isDataSaverActive: Boolean,
    isAudioOnlyMode: Boolean,
): List<EntryPlaybackHeadTarget> {
    if (hasProvisionalPlayer || playbackStartAllowed || isDataSaverActive) return emptyList()
    return buildList {
        val trimmedVideo = videoUrl.trim()
        if (!isAudioOnlyMode && trimmedVideo.isNotEmpty() && shouldUsePlaybackMediaCache(trimmedVideo)) {
            add(
                EntryPlaybackHeadTarget(
                    url = trimmedVideo,
                    cacheKey = cdnCacheKeysByUrl[trimmedVideo]?.takeIf { it.isNotBlank() }
                        ?: buildPlaybackCacheKey(rawUri = trimmedVideo, explicitKey = null),
                    lengthBytes = ENTRY_VIDEO_HEAD_PREFETCH_BYTES,
                )
            )
        }
        val trimmedAudio = audioUrl?.trim().orEmpty()
        if (trimmedAudio.isNotEmpty() && shouldUsePlaybackMediaCache(trimmedAudio)) {
            add(
                EntryPlaybackHeadTarget(
                    url = trimmedAudio,
                    cacheKey = cdnCacheKeysByUrl[trimmedAudio]?.takeIf { it.isNotBlank() }
                        ?: buildPlaybackCacheKey(rawUri = trimmedAudio, explicitKey = null),
                    lengthBytes = ENTRY_AUDIO_HEAD_PREFETCH_BYTES,
                )
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
internal suspend fun prefetchEntryPlaybackHead(
    context: Context,
    targets: List<EntryPlaybackHeadTarget>,
) {
    if (targets.isEmpty()) return
    val headers = mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    )
    val upstreamFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
        .setDefaultRequestProperties(headers)
    coroutineScope {
        targets.map { target ->
            async(Dispatchers.IO) {
                try {
                    PlaybackMediaCache.prefetchRange(
                        context = context.applicationContext,
                        upstreamFactory = upstreamFactory,
                        url = Uri.parse(target.url),
                        cacheKey = target.cacheKey,
                        position = 0L,
                        length = target.lengthBytes,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 进场预拉属于纯加速项，失败不阻塞正常起播。
                }
            }
        }.awaitAll()
    }
}
