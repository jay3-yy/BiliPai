package com.android.purebilibili.feature.video.playback.audio

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.feature.video.playback.policy.resolveSpeedCompatibleAudioQualityPreference

fun resolveRequestedAudioQuality(
    defaultAudioQuality: Int,
    rememberedAudioQuality: Int
): Int {
    val resolvedPreference = if (defaultAudioQuality == AUDIO_QUALITY_FOLLOW_LAST_SELECTED) {
        rememberedAudioQuality
    } else {
        defaultAudioQuality
    }
    return normalizeAudioQualityPreference(resolvedPreference)
}

fun normalizeAudioQualityPreference(preferenceId: Int): Int {
    return if (preferenceId > 0 || preferenceId == AUDIO_QUALITY_AUTO) {
        preferenceId
    } else {
        AUDIO_QUALITY_AUTO
    }
}

fun collectAudioStreamCandidates(
    dash: Dash,
    isDolbyAudioSupported: Boolean = true,
    isDolbyAudioSoftwareDecoded: Boolean = false
): List<AudioStreamCandidate> {
    fun candidate(
        track: DashAudio,
        sourceKind: AudioStreamKind = AudioStreamKind.STANDARD
    ): AudioStreamCandidate? {
        if (track.id <= 0 || track.getValidUrl().isBlank()) return null
        val kind = when {
            track.codecs.isDolbyAtmosCodec() -> AudioStreamKind.DOLBY
            track.codecs.equals("flac", ignoreCase = true) -> AudioStreamKind.HI_RES
            sourceKind == AudioStreamKind.HI_RES -> AudioStreamKind.HI_RES
            track.codecs.isBlank() && track.id == AUDIO_QUALITY_HI_RES -> AudioStreamKind.HI_RES
            track.codecs.isBlank() &&
                (track.id == AUDIO_QUALITY_DOLBY || track.id == 30255) -> AudioStreamKind.DOLBY
            else -> AudioStreamKind.STANDARD
        }
        if (kind == AudioStreamKind.DOLBY && !isDolbyAudioSupported) return null
        val softwareDecoded = kind == AudioStreamKind.DOLBY && isDolbyAudioSoftwareDecoded
        val label = when (kind) {
            AudioStreamKind.HI_RES -> "Hi-Res 无损"
            AudioStreamKind.DOLBY -> if (softwareDecoded) "杜比音频" else "杜比全景声"
            AudioStreamKind.STANDARD -> when (track.id) {
                30216 -> "64K"
                30232 -> "132K"
                30280 -> "192K"
                else -> track.id.toString()
            }
        }
        return AudioStreamCandidate(
            preferenceId = track.id,
            kind = kind,
            label = label,
            track = track,
            isSoftwareDecoded = softwareDecoded
        )
    }

    val standard = dash.audio.orEmpty().mapNotNull { candidate(it) }
    val dolby = dash.dolby?.takeIf { it.type > 0 }?.audio.orEmpty()
        .mapNotNull { candidate(it, AudioStreamKind.DOLBY) }
    val hiRes = dash.flac?.audio?.let { candidate(it, AudioStreamKind.HI_RES) }
        ?.let(::listOf).orEmpty()

    return (hiRes + dolby + standard).distinctBy { candidate ->
        Triple(candidate.preferenceId, candidate.kind, candidate.track.getValidUrl())
    }
}

fun buildAvailableAudioQualityOptions(
    candidates: List<AudioStreamCandidate>
): List<AudioQualityOption> {
    if (candidates.isEmpty()) return emptyList()

    val playableOptions = candidates
        .groupBy { it.preferenceId }
        .mapNotNull { (_, groupedCandidates) ->
            groupedCandidates.maxByOrNull { it.track.bandwidth }
        }
        .sortedWith(
            compareBy<AudioStreamCandidate> { audioKindOrder(it.kind) }
                .thenByDescending { it.track.bandwidth }
        )
        .map { candidate ->
            AudioQualityOption(
                preferenceId = candidate.preferenceId,
                kind = candidate.kind,
                label = candidate.label,
                isHiRes = candidate.kind == AudioStreamKind.HI_RES,
                isDolby = candidate.kind == AudioStreamKind.DOLBY,
                isSoftwareDecoded = candidate.isSoftwareDecoded,
                codecDescription = candidate.track.codecs
            )
        }
    val automaticOption = if (candidates.any { it.kind == AudioStreamKind.STANDARD }) {
        listOf(AudioQualityOption(AUDIO_QUALITY_AUTO, kind = null, label = "自动"))
    } else {
        emptyList()
    }
    return automaticOption + playableOptions
}

fun resolveAudioStreamSelection(
    dash: Dash,
    requestedAudioQuality: Int,
    playbackSpeed: Float = 1.0f,
    isDolbyAudioSupported: Boolean = true,
    isDolbyAudioSoftwareDecoded: Boolean = false
): AudioSelectionDecision {
    val normalizedRequestedAudioQuality = normalizeAudioQualityPreference(requestedAudioQuality)
    val candidates = collectAudioStreamCandidates(
        dash = dash,
        isDolbyAudioSupported = isDolbyAudioSupported,
        isDolbyAudioSoftwareDecoded = isDolbyAudioSoftwareDecoded
    )
    val availableOptions = buildAvailableAudioQualityOptions(candidates)
    if (candidates.isEmpty()) {
        return AudioSelectionDecision(
            requestedPreferenceId = normalizedRequestedAudioQuality,
            effectivePreferenceId = AUDIO_QUALITY_AUTO,
            selected = null,
            availableOptions = emptyList(),
            fallbackReason = AudioFallbackReason.NO_PLAYABLE_AUDIO
        )
    }

    val requestedCandidate = candidates
        .filter { it.preferenceId == normalizedRequestedAudioQuality }
        .maxByOrNull { it.track.bandwidth }
    val effectivePreference = resolveSpeedCompatibleAudioQualityPreference(
        requestedAudioQuality = normalizedRequestedAudioQuality,
        playbackSpeed = playbackSpeed,
        requestedAudioKind = requestedCandidate?.kind
    )
    val bestStandard = candidates
        .filter { it.kind == AudioStreamKind.STANDARD }
        .maxByOrNull { it.track.bandwidth }

    val exactSelection = if (effectivePreference == AUDIO_QUALITY_AUTO) {
        bestStandard
    } else {
        requestedCandidate
    }
    val selected = exactSelection ?: bestStandard
    val fallbackReason = when {
        selected == null -> AudioFallbackReason.NO_PLAYABLE_AUDIO
        effectivePreference != normalizedRequestedAudioQuality ->
            AudioFallbackReason.SPEED_INCOMPATIBLE
        effectivePreference != AUDIO_QUALITY_AUTO && exactSelection == null ->
            AudioFallbackReason.REQUESTED_UNAVAILABLE
        else -> null
    }

    return AudioSelectionDecision(
        requestedPreferenceId = normalizedRequestedAudioQuality,
        effectivePreferenceId = effectivePreference,
        selected = selected,
        availableOptions = availableOptions,
        fallbackReason = fallbackReason
    )
}

fun resolveAudioQualityControlPresentation(
    options: List<AudioQualityOption>,
    selectedAudioQuality: Int
): AudioQualityControlPresentation {
    val selectedOption = options.firstOrNull { it.preferenceId == selectedAudioQuality }
    val label = when (selectedOption?.kind) {
        AudioStreamKind.HI_RES -> "Hi-Res"
        AudioStreamKind.DOLBY -> if (selectedOption.isSoftwareDecoded) "杜比音频" else "杜比"
        else -> selectedOption?.label?.takeIf { it.isNotBlank() } ?: "音质"
    }
    return AudioQualityControlPresentation(
        label = label,
        showHiResBadge = selectedOption?.isHiRes == true,
        showDolbyBadge = selectedOption?.isDolby == true,
        currentLabel = selectedOption?.takeIf { it.kind != null }?.label?.takeIf { it.isNotBlank() },
    )
}

private fun String.isDolbyAtmosCodec(): Boolean {
    val normalized = lowercase()
        .replace("_", "-")
        .replace(" ", "")
    return normalized.contains("ec-3") ||
        normalized.contains("e-ac-3") ||
        normalized.contains("eac3") ||
        normalized == "ec3"
}

private fun audioKindOrder(kind: AudioStreamKind): Int {
    return when (kind) {
        AudioStreamKind.HI_RES -> 0
        AudioStreamKind.DOLBY -> 1
        AudioStreamKind.STANDARD -> 2
    }
}
