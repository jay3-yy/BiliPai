@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.state

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.android.purebilibili.core.lifecycle.PendingResource
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.player.HiResCompatibleRenderersFactory
import com.android.purebilibili.core.player.PlaybackHttpDataSourceFactory
import com.android.purebilibili.core.player.PlaybackMediaCache
import com.android.purebilibili.core.player.withDynamicScheduling
import com.android.purebilibili.core.player.withVideoDecoderPreference
import com.android.purebilibili.core.player.VideoDecoderDiagnostics
import com.android.purebilibili.core.store.DataSaverState
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.video.playback.policy.resolvePlaybackWakeMode
import com.android.purebilibili.feature.video.player.resolveHandleAudioFocusByPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 构造与页面接管分开；播放器操作及事件仍绑定主线程 Looper。 */
internal fun createVideoDetailPlayer(context: Context): ExoPlayer {
    val headers = mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    )
    val upstreamFactory = PlaybackHttpDataSourceFactory.create(
        context = context,
        fallbackClient = NetworkModule.playbackOkHttpClient,
        headers = headers,
    )
    val dataSourceFactory: DataSource.Factory =
        PlaybackMediaCache.buildCachedDataSourceFactory(context, upstreamFactory)

    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    //  [性能优化] 使用 PlayerSettingsCache 直接从内存读取，避免 I/O
    val seekFastEnabled = com.android.purebilibili.core.store.PlayerSettingsCache.isSeekFastEnabled(context)
    val miniPlayerMode = SettingsManager.getMiniPlayerModeSync(context)
    val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(context)
    val audioFocusEnabled = SettingsManager.getAudioFocusEnabledSync(context)
    val bufferPolicy = resolvePlayerBufferPolicy(
        isOnWifi = DataSaverState.get(context).isOnWifi,
        resilient = SettingsManager.getResilientVideoBufferSync(context),
    )
    Logger.d(
        "VideoPlayerState",
        "🎬 BufferPolicy: min=${bufferPolicy.minBufferMs}, max=${bufferPolicy.maxBufferMs}, " +
            "start=${bufferPolicy.bufferForPlaybackMs}, rebuffer=${bufferPolicy.bufferForPlaybackAfterRebufferMs}, " +
            "firstQuarterMax=${bufferPolicy.earlyPlaybackMaxBufferMs}"
    )

    // 构造和诊断共用同一偏好快照，后台准备期间的设置变化不改写本实例的记录。
    val preferHardware = com.android.purebilibili.core.store.PlayerSettingsCache.isHwDecodeEnabled(context)
    val renderersFactory = HiResCompatibleRenderersFactory(context)
        .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .withVideoDecoderPreference(preferHardware)

    return ExoPlayer.Builder(context)
        .setLooper(android.os.Looper.getMainLooper())
        .setRenderersFactory(renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .withDynamicScheduling()
        // 前 1/4 使用当前缓冲偏好，之后按常规阈值预缓冲。
        .setLoadControl(
            createVideoLoadControl(bufferPolicy)
        )
        //  [性能优化] 快速 Seek：跳转到最近的关键帧而非精确位置
        .setSeekParameters(
            if (seekFastEnabled) {
                androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
            } else {
                androidx.media3.exoplayer.SeekParameters.DEFAULT
            }
        )
        .setAudioAttributes(
            audioAttributes,
            resolveHandleAudioFocusByPolicy(audioFocusEnabled = audioFocusEnabled)
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(
            resolvePlaybackWakeMode(
                miniPlayerMode = miniPlayerMode,
                stopPlaybackOnExit = stopPlaybackOnExit
            )
        )
        .build()
        .let { VideoDecoderDiagnostics.attach(it, preferHardware) }
}

/** 调用方持有未交接资源；取消创建也等待构造结束并在主线程释放。 */
internal suspend fun prepareVideoDetailPlayer(context: Context): PendingResource<ExoPlayer> {
    var created: ExoPlayer? = null
    var handedOff = false
    try {
        withContext(Dispatchers.Default) {
            created = com.android.purebilibili.core.ui.transition.VideoCardTransitionDiagnostics
                .measureWork("player_construct") { createVideoDetailPlayer(context.applicationContext) }
        }
        val player = requireNotNull(created)
        val pending = PendingResource(player) { it.release() }
        handedOff = true
        return pending
    } finally {
        if (!handedOff) {
            withContext(NonCancellable + Dispatchers.Main.immediate) { created?.release() }
        }
    }
}
