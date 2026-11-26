package com.android.purebilibili.feature.nearby

/** 准备阶段可停留在起点，也可正常前进；使用单调时间限制可接受的进度范围。 */
internal fun nearbyPlaybackPositionMatches(video: NearbyVideo, positionMs: Long, elapsedMs: Long): Boolean {
    if (video.isLive) return true
    if (positionMs < 0) return false
    val lower = (video.positionMs - 3_000L).coerceAtLeast(0L)
    val upper = video.positionMs.toDouble() + elapsedMs.coerceAtLeast(0L).toDouble() * video.speed + 3_000.0
    return positionMs >= lower && positionMs.toDouble() <= upper
}

/** 估算对端正在播放内容的展示时间（用于接力浮条与设备卡片展示）。 */
internal fun estimateNearbyPlaybackPositionMs(video: NearbyVideo, nowWallMs: Long): Long {
    if (video.isLive) return 0L
    if (!video.playing || video.updatedAtMs <= 0L) return video.positionMs.coerceAtLeast(0L)
    val deltaMs = (nowWallMs - video.updatedAtMs).coerceIn(0L, 60_000L)
    return (video.positionMs + (deltaMs * video.speed).toLong()).coerceAtLeast(0L)
}

internal fun formatNearbyDuration(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** 确定性主叫方向判定，避免两端同时发现彼此并同时发起 TLS 连接碰撞。 */
internal fun shouldInitiateNearbyAutoConnect(ownPin: String, peerPin: String): Boolean =
    ownPin.isNotEmpty() && peerPin.isNotEmpty() && ownPin < peerPin

/**
 * 接力完成后的弹幕、画质、音轨（杜比/Hi-Res/多语言）与倍速偏好差异提示：
 * - 当对端画质、音质/音轨或弹幕开关与本机当前实际状态不一致时生成非侵入式提示；
 * - 仅在本机当前内容明确支持对端画质/音质/音轨档位时提供一键切换动作，避免跨账号/会员权限差异导致误触；
 * - 弹幕开关差异始终提供一键同步动作，不强行覆盖各设备独立偏好；倍速已自动恢复时附带明确确认说明。
 */
internal data class NearbyPreferenceHint(
    val id: String,
    val peerName: String,
    val media: NearbyVideo,
    val headline: String,
    val detail: String,
    val targetDanmakuEnabled: Boolean? = null,
    val targetQualityId: Int? = null,
    val targetQualityLabel: String = "",
    val targetAudioQualityId: Int? = null,
    val targetAudioQualityLabel: String = "",
    val targetAudioLang: String? = null,
    val targetAudioLangLabel: String = "",
)

internal fun buildNearbyPreferenceHint(
    id: String,
    peerName: String,
    source: NearbyVideo,
    localActual: NearbyVideo?,
    localDanmakuEnabled: Boolean?,
    availableQualityIds: List<Int>,
    availableAudioQualityIds: List<Int> = emptyList(),
    availableAudioLangs: List<String> = emptyList(),
): NearbyPreferenceHint? {
    val sourceQualityLabel = source.effectiveQualityLabel
    val localQualityId = localActual?.qualityId ?: 0
    val localQualityLabel = localActual?.effectiveQualityLabel.orEmpty()
    val qualityDiffers = when {
        source.qualityId > 0 && localQualityId > 0 -> source.qualityId != localQualityId
        sourceQualityLabel.isNotBlank() && localQualityLabel.isNotBlank() -> sourceQualityLabel != localQualityLabel
        else -> false
    }

    val sourceAudioLabel = source.effectiveAudioQualityLabel
    val localAudioId = localActual?.audioQualityId ?: 0
    val localAudioLabel = localActual?.effectiveAudioQualityLabel.orEmpty()
    val audioQualityDiffers = source.audioQualityId > 0 &&
        localAudioId > 0 &&
        source.audioQualityId != localAudioId

    val sourceLang = source.audioLang.trim()
    val localLang = localActual?.audioLang?.trim().orEmpty()
    val audioLangDiffers = sourceLang.isNotBlank() && sourceLang != localLang

    val effectiveLocalDanmaku = localActual?.danmakuEnabled ?: localDanmakuEnabled
    val danmakuDiffers = source.danmakuEnabled != null &&
        effectiveLocalDanmaku != null &&
        source.danmakuEnabled != effectiveLocalDanmaku

    if (!qualityDiffers && !audioQualityDiffers && !audioLangDiffers && !danmakuDiffers) return null

    val remoteParts = buildList {
        if (qualityDiffers && sourceQualityLabel.isNotBlank()) add(sourceQualityLabel)
        if (audioQualityDiffers && sourceAudioLabel.isNotBlank()) add(sourceAudioLabel)
        if (audioLangDiffers && source.effectiveAudioLangLabel.isNotBlank()) add(source.effectiveAudioLangLabel)
        if (danmakuDiffers) add(if (source.danmakuEnabled == true) "弹幕开" else "弹幕关")
    }
    val localParts = buildList {
        if (qualityDiffers && localQualityLabel.isNotBlank()) add(localQualityLabel)
        if (audioQualityDiffers && localAudioLabel.isNotBlank()) add(localAudioLabel)
        if (audioLangDiffers) add(localActual?.effectiveAudioLangLabel?.ifBlank { "原声" } ?: "原声")
        if (danmakuDiffers) add(if (effectiveLocalDanmaku == true) "弹幕开" else "弹幕关")
    }
    val headline = buildString {
        if (peerName.isNotBlank()) {
            append(peerName)
            append(" 为 ")
        } else {
            append("对端为 ")
        }
        append(remoteParts.joinToString(" · "))
    }
    val speedNote = if (!source.isLive && kotlin.math.abs(source.speed - 1f) >= 0.01f) {
        "已同步 ${formatNearbySpeed(source.speed)}"
    } else ""
    val detail = buildList {
        if (localParts.isNotEmpty()) add("本机当前 ${localParts.joinToString(" · ")}")
        if (speedNote.isNotBlank()) add(speedNote)
    }.joinToString(" · ").ifBlank { "可按需同步当前播放偏好" }

    val switchableTargetQualityId = if (
        qualityDiffers &&
        source.qualityId > 0 &&
        source.qualityId in availableQualityIds
    ) {
        source.qualityId
    } else null

    val switchableTargetAudioQualityId = if (
        audioQualityDiffers &&
        source.audioQualityId > 0 &&
        source.audioQualityId in availableAudioQualityIds
    ) {
        source.audioQualityId
    } else null

    val switchableTargetAudioLang = if (
        audioLangDiffers &&
        sourceLang in availableAudioLangs
    ) {
        sourceLang
    } else null

    return NearbyPreferenceHint(
        id = id,
        peerName = peerName,
        media = source,
        headline = headline,
        detail = detail,
        targetDanmakuEnabled = if (danmakuDiffers) source.danmakuEnabled else null,
        targetQualityId = switchableTargetQualityId,
        targetQualityLabel = if (switchableTargetQualityId != null) sourceQualityLabel else "",
        targetAudioQualityId = switchableTargetAudioQualityId,
        targetAudioQualityLabel = if (switchableTargetAudioQualityId != null) sourceAudioLabel else "",
        targetAudioLang = switchableTargetAudioLang,
        targetAudioLangLabel = if (switchableTargetAudioLang != null) source.effectiveAudioLangLabel else "",
    )
}
