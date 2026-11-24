@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.util

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.android.purebilibili.core.store.PlayerSettingsCache
import com.android.purebilibili.data.model.response.DashVideo
import java.util.concurrent.ConcurrentHashMap

internal data class VideoTrackDecoderSupport(
    val supported: Boolean,
    val hardware: Boolean,
    val software: Boolean,
)

internal data class VideoTrackCapabilityCacheKey(
    val codecs: String,
    val width: Int,
    val height: Int,
    val frameRate: String,
)

internal fun resolveVideoTrackCapabilityCacheKey(track: DashVideo): VideoTrackCapabilityCacheKey =
    VideoTrackCapabilityCacheKey(
        codecs = track.codecs.trim(),
        width = track.width.coerceAtLeast(0),
        height = track.height.coerceAtLeast(0),
        frameRate = track.frameRate.trim(),
    )

/** 仅在同画质内优先匹配解码偏好，避免用低画质硬解替代用户选择的高画质。 */
internal fun selectPlayableVideoTracks(
    tracks: List<DashVideo>,
    preferHardware: Boolean,
    support: (DashVideo) -> VideoTrackDecoderSupport,
): List<DashVideo> = tracks
    .filter { it.getValidUrl().isNotBlank() }
    .map { it to support(it) }
    .filter { it.second.supported }
    .groupBy { it.first.id }
    .values
    .flatMap { atQuality ->
        val preferred = atQuality.filter { (_, capability) ->
            if (preferHardware) capability.hardware else capability.software
        }
        (preferred.ifEmpty { atQuality }).map { it.first }
    }

/** 保留完整 codecs 字符串，让 Media3 检查 profile、level、位深以及尺寸/帧率。 */
internal fun buildVideoTrackCapabilityFormat(track: DashVideo): Format {
    val frameRateParts = track.frameRate.split('/', limit = 2)
    val frameRate = if (frameRateParts.size == 2) {
        val numerator = frameRateParts[0].toFloatOrNull()
        val denominator = frameRateParts[1].toFloatOrNull()
        if (numerator != null && denominator != null && denominator > 0f) numerator / denominator else null
    } else track.frameRate.toFloatOrNull()
    return Format.Builder()
        // 接口的 mimeType 通常是容器 video/mp4；解码查询需要具体的样本 MIME。
        .setSampleMimeType(MimeTypes.getVideoMediaMimeType(track.codecs))
        .setCodecs(track.codecs)
        .setWidth(track.width.takeIf { it > 0 } ?: Format.NO_VALUE)
        .setHeight(track.height.takeIf { it > 0 } ?: Format.NO_VALUE)
        .setFrameRate(frameRate?.takeIf { it.isFinite() && it > 0f } ?: Format.NO_VALUE.toFloat())
        .build()
}

internal object VideoTrackCapabilities {
    private const val MAX_CACHE_ENTRIES = 256
    private val supportCache = ConcurrentHashMap<VideoTrackCapabilityCacheKey, VideoTrackDecoderSupport>()

    internal fun getOrQuerySupport(
        track: DashVideo,
        query: (DashVideo) -> VideoTrackDecoderSupport,
    ): VideoTrackDecoderSupport {
        val key = resolveVideoTrackCapabilityCacheKey(track)
        supportCache[key]?.let { return it }
        val resolved = query(track)
        if (supportCache.size >= MAX_CACHE_ENTRIES) {
            supportCache.clear()
        }
        supportCache[key] = resolved
        return resolved
    }

    internal fun clearCacheForTest() {
        supportCache.clear()
    }

    fun filterForPlayback(context: Context, tracks: List<DashVideo>): List<DashVideo> {
        val appContext = context.applicationContext
        return selectPlayableVideoTracks(tracks, PlayerSettingsCache.isHwDecodeEnabled(appContext)) { track ->
            getOrQuerySupport(track) { candidate ->
                val format = buildVideoTrackCapabilityFormat(candidate)
                if (format.sampleMimeType == null) {
                    VideoTrackDecoderSupport(supported = false, hardware = false, software = false)
                } else {
                    val decoders = MediaCodecUtil.getDecoderInfosSoftMatch(
                        MediaCodecSelector.DEFAULT, format, false, false,
                    ).filter { it.isFormatSupported(appContext, format) }
                    VideoTrackDecoderSupport(
                        supported = decoders.isNotEmpty(),
                        hardware = decoders.any { it.hardwareAccelerated },
                        software = decoders.any { it.softwareOnly },
                    )
                }
            }
        }
    }
}
