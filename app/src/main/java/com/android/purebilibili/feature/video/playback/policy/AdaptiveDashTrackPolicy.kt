package com.android.purebilibili.feature.video.playback.policy

import com.android.purebilibili.core.player.policy.PlaybackQualityMode

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.getBestVideo
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_AUTO
import com.android.purebilibili.feature.video.playback.audio.AudioStreamKind
import com.android.purebilibili.feature.video.playback.audio.collectAudioStreamCandidates
import com.android.purebilibili.feature.video.playback.audio.resolveAudioStreamSelection
import com.android.purebilibili.feature.video.viewmodel.normalizeCodecFamilyKey

data class AdaptiveDashTrackSet(
    val videoTracks: List<DashVideo>,
    val audioTracks: List<DashAudio>
)

const val PREMIUM_AUDIO_SPEED_COMPATIBILITY_THRESHOLD = 1.5f
private const val DOLBY_AUDIO_QUALITY_ID = 30250
private const val DOLBY_AUDIO_QUALITY_ALTERNATE_ID = 30255
private const val HI_RES_AUDIO_QUALITY_ID = 30251

fun isSpeedSensitivePremiumAudioQuality(audioQuality: Int): Boolean {
    return audioQuality == DOLBY_AUDIO_QUALITY_ID ||
        audioQuality == DOLBY_AUDIO_QUALITY_ALTERNATE_ID ||
        audioQuality == HI_RES_AUDIO_QUALITY_ID
}

fun resolveSpeedCompatibleAudioQualityPreference(
    requestedAudioQuality: Int,
    playbackSpeed: Float,
    speedThreshold: Float = PREMIUM_AUDIO_SPEED_COMPATIBILITY_THRESHOLD,
    requestedAudioKind: AudioStreamKind? = null
): Int {
    if (playbackSpeed <= speedThreshold) return requestedAudioQuality
    return if (isSpeedSensitivePremiumAudioQuality(requestedAudioQuality) ||
        requestedAudioKind == AudioStreamKind.DOLBY || requestedAudioKind == AudioStreamKind.HI_RES
    ) {
        -1
    } else {
        requestedAudioQuality
    }
}

fun buildAdaptiveDashTrackSet(
    dash: Dash,
    mode: PlaybackQualityMode,
    autoQualityCap: Int,
    preferredAudioQuality: Int,
    preferredVideoCodec: String,
    secondaryVideoCodec: String,
    isHevcSupported: Boolean,
    isAv1Supported: Boolean,
    isDolbyAudioSupported: Boolean = true
): AdaptiveDashTrackSet {
    val supportedVideos = dash.video
        .filter { it.getValidUrl().isNotBlank() }
        .filter { video ->
            when (normalizeCodecFamilyKey(video.codecs)) {
                null -> true
                "avc1" -> true
                "hev1" -> isHevcSupported
                "av01" -> isAv1Supported
                else -> false
            }
        }

    val candidateVideos = when (mode) {
        PlaybackQualityMode.AUTO -> {
            val capped = supportedVideos.filter { it.id <= autoQualityCap }
            if (capped.isNotEmpty()) capped else supportedVideos
        }
        is PlaybackQualityMode.LOCKED -> supportedVideos.filter { it.id == mode.qualityId }
    }

    val codecSelectedVideos = candidateVideos
        .groupBy { it.id }
        .values
        .flatMap { videosAtQuality ->
            // 与直接地址共用编码、次选编码和码率选择，避免清单再次选到另一条轨道。
            val selected = Dash(video = videosAtQuality).getBestVideo(
                targetQn = videosAtQuality.first().id,
                preferCodec = preferredVideoCodec,
                secondPreferCodec = secondaryVideoCodec,
                isHevcSupported = isHevcSupported,
                isAv1Supported = isAv1Supported,
            ) ?: return@flatMap emptyList()
            if (mode is PlaybackQualityMode.LOCKED) {
                listOf(selected)
            } else {
                // 自动画质保留同编码的多个码率，仍可随带宽自适应。
                val selectedCodec = normalizeCodecFamilyKey(selected.codecs)
                videosAtQuality.filter { normalizeCodecFamilyKey(it.codecs) == selectedCodec }
            }
        }

    val sortedVideos = codecSelectedVideos.sortedWith(
        compareByDescending<DashVideo> { it.id }
            .thenByDescending { it.bandwidth }
    )

    val sortedAudios = if (preferredAudioQuality == AUDIO_QUALITY_AUTO) {
        collectAudioStreamCandidates(dash)
            .filter { it.kind == AudioStreamKind.STANDARD }
            .map { it.track }
            .sortedByDescending { it.bandwidth }
    } else {
        resolveAudioStreamSelection(
            dash = dash,
            requestedAudioQuality = preferredAudioQuality,
            isDolbyAudioSupported = isDolbyAudioSupported
        ).selected?.track?.let(::listOf).orEmpty()
    }

    return AdaptiveDashTrackSet(
        videoTracks = sortedVideos,
        audioTracks = sortedAudios
    )
}
