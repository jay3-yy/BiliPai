package com.android.purebilibili.core.player

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import java.util.concurrent.atomic.AtomicBoolean
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

internal const val HI_RES_FLAC_MIN_CODEC_INPUT_SIZE_BYTES = 1024 * 1024

internal fun resolveHiResCodecMaxInputSize(
    defaultMaxInputSize: Int,
    sampleMimeTypes: List<String?>
): Int {
    val containsFlac = sampleMimeTypes.any { mimeType ->
        mimeType.equals(MimeTypes.AUDIO_FLAC, ignoreCase = true)
    }
    return if (containsFlac) {
        maxOf(defaultMaxInputSize, HI_RES_FLAC_MIN_CODEC_INPUT_SIZE_BYTES)
    } else {
        defaultMaxInputSize
    }
}

/**
 * 为部分系统 FLAC 解码器预留更大的输入缓冲区。
 *
 * 某些设备会给 `c2.android.flac.decoder` 分配 32 KiB 输入缓冲，但 B 站 Hi-Res
 * 音轨的单帧可能超过该大小，最终触发 Media3 `InsufficientCapacityException`。
 */
@OptIn(UnstableApi::class)
internal open class HiResCompatibleRenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        // 普通视频、竖屏连播、番剧和迷你条复用同一入口；关闭律动只做原样转发，
        // 不修改各播放器原有处理器和格式支持，PCM 会话内切换开关无需重建播放器。
        // 音频缓冲热路径禁止每次拿 SharedPreferences 锁，改为进程内 @Volatile 镜像 + 变更监听同步。
        ensureAudioSpectrumCacheBound(context.applicationContext)
        val waveformAudioSink = com.android.purebilibili.core.player.waveform.WaveformAudioSink(
            delegate = audioSink,
            enabled = {
                cachedAudioSpectrumEnabled &&
                    com.android.purebilibili.core.player.waveform.AudioWaveformSource.isAnalysisRequested()
            },
        )
        val firstAudioRendererIndex = out.size
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            waveformAudioSink,
            eventHandler,
            eventListener,
            out
        )
        val mediaCodecRendererIndex = (firstAudioRendererIndex until out.size)
            .firstOrNull { index -> out[index].javaClass == MediaCodecAudioRenderer::class.java }
            ?: return
        out[mediaCodecRendererIndex] = HiResCompatibleMediaCodecAudioRenderer(
            context = context,
            codecAdapterFactory = codecAdapterFactory,
            mediaCodecSelector = mediaCodecSelector,
            enableDecoderFallback = enableDecoderFallback,
            eventHandler = eventHandler,
            eventListener = eventListener,
            audioSink = waveformAudioSink
        )
    }

    private companion object {
        private const val MINI_PLAYER_PREFS = "mini_player"
        private const val KEY_AUDIO_SPECTRUM_ENABLED = "audio_spectrum_enabled"

        @Volatile
        private var cachedAudioSpectrumEnabled: Boolean = false
        private val spectrumListenerRegistered = AtomicBoolean(false)

        // Strong reference required because Android SharedPreferences stores listeners weakly.
        private val spectrumPreferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                if (key == null || key == KEY_AUDIO_SPECTRUM_ENABLED) {
                    cachedAudioSpectrumEnabled = prefs.getBoolean(KEY_AUDIO_SPECTRUM_ENABLED, false)
                }
            }

        private fun ensureAudioSpectrumCacheBound(appContext: Context) {
            if (!spectrumListenerRegistered.compareAndSet(false, true)) return
            val prefs = appContext.getSharedPreferences(MINI_PLAYER_PREFS, Context.MODE_PRIVATE)
            cachedAudioSpectrumEnabled = prefs.getBoolean(KEY_AUDIO_SPECTRUM_ENABLED, false)
            prefs.registerOnSharedPreferenceChangeListener(spectrumPreferenceListener)
        }
    }
}

@OptIn(UnstableApi::class)
private class HiResCompatibleMediaCodecAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
) {

    override fun getCodecMaxInputSize(
        codecInfo: MediaCodecInfo,
        format: Format,
        streamFormats: Array<Format>
    ): Int {
        return resolveHiResCodecMaxInputSize(
            defaultMaxInputSize = super.getCodecMaxInputSize(
                codecInfo,
                format,
                streamFormats
            ),
            sampleMimeTypes = listOf(format.sampleMimeType) +
                streamFormats.map { streamFormat -> streamFormat.sampleMimeType }
        )
    }
}
