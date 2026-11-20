package com.android.purebilibili.feature.video.playback.audio

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.core.store.player.DEFAULT_AUDIO_QUALITY_FOLLOW_LAST

const val AUDIO_QUALITY_FOLLOW_LAST_SELECTED = DEFAULT_AUDIO_QUALITY_FOLLOW_LAST
const val AUDIO_QUALITY_AUTO = -1
const val AUDIO_QUALITY_DOLBY = 30250
const val AUDIO_QUALITY_HI_RES = 30251

enum class AudioStreamKind {
    STANDARD,
    DOLBY,
    HI_RES
}

enum class AudioFallbackReason {
    REQUESTED_UNAVAILABLE,
    SPEED_INCOMPATIBLE,
    DECODER_ERROR,
    NO_PLAYABLE_AUDIO
}

data class AudioStreamCandidate(
    val preferenceId: Int,
    val kind: AudioStreamKind,
    val label: String,
    val track: DashAudio,
    val isSoftwareDecoded: Boolean = false
)

data class AudioQualityOption(
    val preferenceId: Int,
    val kind: AudioStreamKind?,
    val label: String,
    val isHiRes: Boolean = false,
    val isDolby: Boolean = false,
    val isSoftwareDecoded: Boolean = false,
    val codecDescription: String = ""
)

data class AudioQualityControlPresentation(
    val label: String,
    val showHiResBadge: Boolean,
    val showDolbyBadge: Boolean,
    val currentLabel: String? = null,
)

data class AudioSelectionDecision(
    val requestedPreferenceId: Int,
    val effectivePreferenceId: Int,
    val selected: AudioStreamCandidate?,
    val availableOptions: List<AudioQualityOption>,
    val fallbackReason: AudioFallbackReason?
) {
    val selectedPreferenceId: Int
        get() = selected?.track?.id ?: AUDIO_QUALITY_AUTO
}
