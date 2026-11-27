@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.player

import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * 软件视频解码器内部优先级：优先多线程性能更好的 dav1d，其次现代 Codec2 (`c2.android.*`)，
 * 其余未知软件解码器保持相对顺序，旧版 `omx.google.*` 垫底。
 */
internal fun softwareVideoDecoderPriority(name: String): Int {
    if (name.isBlank()) return 2
    val lower = name.lowercase()
    return when {
        lower.contains("dav1d") -> 0
        lower.startsWith("c2.android.") -> 1
        lower.startsWith("omx.google.") -> 3
        else -> 2
    }
}

/**
 * 硬件与未知类型维持平台排序；软件解码器在同档内择优排序（dav1d > c2.android.* > 其他 > omx.google.*）。
 * 未知类型位于首选类型之后，仍保留为初始化失败的候选。
 */
internal fun <T> orderVideoDecoders(
    mimeType: String,
    decoders: List<T>,
    preferHardware: Boolean,
    isHardware: (T) -> Boolean,
    isSoftware: (T) -> Boolean,
    decoderName: (T) -> String = { "" },
): List<T> {
    if (!mimeType.startsWith("video/")) return decoders
    return decoders.sortedWith(
        compareBy<T> { decoder ->
            when {
                (if (preferHardware) isHardware(decoder) else isSoftware(decoder)) -> 0
                !isHardware(decoder) && !isSoftware(decoder) -> 1
                else -> 2
            }
        }.thenBy { decoder ->
            if (isSoftware(decoder)) softwareVideoDecoderPriority(decoderName(decoder)) else 0
        }
    )
}

/**
 * 只调整视频候选顺序，不删除候选，不改变音频排序或安全/隧道解码查询条件。
 * 保持 Media3 默认的异步 MediaCodec 缓冲队列策略（API 31+ 自动启用，旧系统保持稳定兼容）。
 * 由 Media3 继续判断具体格式支持；偏好不保证设备具备对应的可用解码器。
 */
fun DefaultRenderersFactory.withVideoDecoderPreference(preferHardware: Boolean): DefaultRenderersFactory =
    setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType, requiresSecureDecoder, requiresTunnelingDecoder
        )
        if (mimeType.startsWith("video/")) decoders.forEach {
            VideoDecoderCapabilities.record(it.name, it.hardwareAccelerated, it.softwareOnly)
        }
        orderVideoDecoders(
            mimeType = mimeType,
            decoders = decoders,
            preferHardware = preferHardware,
            isHardware = { it.hardwareAccelerated },
            isSoftware = { it.softwareOnly },
            decoderName = { it.name },
        )
    }
        .setEnableDecoderFallback(true)

/** 启用 Media3 动态调度，使播放循环按解码/渲染到期时间精准休眠以降低播放期 CPU 唤醒。 */
fun ExoPlayer.Builder.withDynamicScheduling(): ExoPlayer.Builder =
    experimentalSetDynamicSchedulingEnabled(true)
