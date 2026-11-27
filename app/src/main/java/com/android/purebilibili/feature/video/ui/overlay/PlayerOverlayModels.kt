package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PlaybackDebugInfo(
    val resolution: String = "",
    val videoBitrate: String = "",
    val audioBitrate: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val frameRate: String = "",
    val videoDecoder: String = "",
    val videoDecoderType: String = "",
    val videoDecoderPreference: String = "",
    val videoDecoderFallbackReason: String = "",
    val videoDecoderDropWarning: String = "",
    val audioDecoder: String = "",
    val playbackState: String = "",
    val playWhenReady: String = "",
    val isPlaying: String = "",
    val firstFrame: String = "",
    val droppedFrames: String = "",
    val bandwidthEstimate: String = "",
    val playerViewport: String = "",
    val cdnHost: String = "",
    val cdnIndex: String = "",
    val networkType: String = "",
    val forwardBuffer: String = "",
    val lastLoadError: String = "",
    val lastVideoEvent: String = "",
    val lastAudioEvent: String = ""
)

data class DebugStatRow(
    val label: String,
    val value: String
)

internal enum class PlaybackInsightLevel {
    LIVE,
    ATTENTION,
    UNAVAILABLE
}

internal enum class PlaybackInsightSection(val title: String) {
    OVERVIEW("概览"),
    VIDEO("视频"),
    AUDIO("音频"),
    RUNTIME("播放"),
    EVENTS("事件")
}

internal data class PlaybackInsightPresentation(
    val summary: String,
    val level: PlaybackInsightLevel,
    val statusText: String,
    val sections: Map<PlaybackInsightSection, List<DebugStatRow>>
)

internal data class PlaybackInsightPanelLayoutPolicy(
    val widthDp: Int,
    val maxHeightDp: Int,
    val edgePaddingDp: Int
)

internal enum class CenterLoadingReason {
    QUALITY_SWITCH,
    SEEK_BUFFERING
}

internal data class CenterLoadingUiState(
    val reason: CenterLoadingReason,
    val primaryText: String,
    val secondaryText: String? = null
)

internal data class CenterLoadingVisualState(
    val indicatorColor: Color,
    val primaryTextColor: Color,
    val secondaryTextColor: Color
)

data class ProgressBarMarkerUiState(
    val segmentId: String,
    val category: String,
    val startFraction: Float,
    val endFraction: Float,
    val color: Color
)

internal enum class PlaybackIssueType {
    STUTTER,
    BLACK_SCREEN,
    NO_RESPONSE
}

enum class PlaybackUserActionType {
    PLAY,
    PAUSE
}

internal data class PlaybackIssueSignal(
    val type: PlaybackIssueType,
    val title: String,
    val message: String
)

private const val PLAYBACK_STUTTER_THRESHOLD_MS = 10_000L
private const val PLAYBACK_BLACK_SCREEN_THRESHOLD_MS = 5_000L
private const val PLAYBACK_NO_RESPONSE_THRESHOLD_MS = 2_000L

internal fun resolvePlaybackDebugRows(
    info: PlaybackDebugInfo
): List<DebugStatRow> {
    val candidates = listOf(
        DebugStatRow("Resolution", info.resolution),
        DebugStatRow("Video bitrate", info.videoBitrate),
        DebugStatRow("Audio bitrate", info.audioBitrate),
        DebugStatRow("Video codec", info.videoCodec),
        DebugStatRow("Audio codec", info.audioCodec),
        DebugStatRow("Frame rate", info.frameRate),
        DebugStatRow("Video decoder", info.videoDecoder),
        DebugStatRow("Video decoder type", info.videoDecoderType),
        DebugStatRow("Video decoder preference", info.videoDecoderPreference),
        DebugStatRow("解码方式说明", info.videoDecoderFallbackReason),
        DebugStatRow("掉帧诊断", info.videoDecoderDropWarning),
        DebugStatRow("Audio decoder", info.audioDecoder),
        DebugStatRow("Playback state", info.playbackState),
        DebugStatRow("Play when ready", info.playWhenReady),
        DebugStatRow("Is playing", info.isPlaying),
        DebugStatRow("First frame", info.firstFrame),
        DebugStatRow("Dropped frames", info.droppedFrames),
        DebugStatRow("Bandwidth", info.bandwidthEstimate),
        DebugStatRow("Player viewport", info.playerViewport),
        DebugStatRow("CDN host", info.cdnHost),
        DebugStatRow("CDN index", info.cdnIndex),
        DebugStatRow("Network type", info.networkType),
        DebugStatRow("Forward buffer", info.forwardBuffer),
        DebugStatRow("Last load error", info.lastLoadError),
        DebugStatRow("Last video event", info.lastVideoEvent),
        DebugStatRow("Last audio event", info.lastAudioEvent)
    )
    return candidates.filter { it.value.isNotBlank() }
}

/** Cheap visibility projection; does not build formatted diagnostic rows or sections. */
internal fun resolvePlaybackInsightLevel(info: PlaybackDebugInfo): PlaybackInsightLevel {
    val decoder = info.videoDecoder.lowercase(Locale.ROOT)
    return when {
        (info.droppedFrames.toIntOrNull() ?: 0) > 0 || info.videoDecoderType == "软件解码" || (info.videoDecoderType.isBlank() &&
            (decoder.contains("software") || decoder.contains("omx.google") || decoder.contains("c2.android"))) -> PlaybackInsightLevel.ATTENTION
        info == PlaybackDebugInfo() -> PlaybackInsightLevel.UNAVAILABLE
        else -> PlaybackInsightLevel.LIVE
    }
}

internal fun resolvePlaybackInsightPresentation(
    info: PlaybackDebugInfo
): PlaybackInsightPresentation {
    val summaryParts = listOf(info.resolution, info.videoCodec, info.frameRate)
        .filter { it.isNotBlank() }
        .take(3)
    val droppedFrames = info.droppedFrames.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val usesSoftwareVideoDecoder = info.videoDecoderType == "软件解码" ||
        (info.videoDecoderType.isBlank() && info.videoDecoder.lowercase(Locale.ROOT).let { decoder ->
            decoder.contains("software") || decoder.contains("omx.google") || decoder.contains("c2.android")
        })
    val rows = resolvePlaybackDebugRows(info)
    val (level, statusText) = when {
        rows.isEmpty() -> PlaybackInsightLevel.UNAVAILABLE to "等待播放器数据"
        info.videoDecoderDropWarning.isNotBlank() -> PlaybackInsightLevel.ATTENTION to info.videoDecoderDropWarning
        droppedFrames > 0 -> PlaybackInsightLevel.ATTENTION to "已记录 $droppedFrames 个掉帧"
        usesSoftwareVideoDecoder -> PlaybackInsightLevel.ATTENTION to "当前使用软件视频解码"
        else -> PlaybackInsightLevel.LIVE to "实时数据"
    }
    val sections = linkedMapOf(
        PlaybackInsightSection.OVERVIEW to listOf(
            DebugStatRow("分辨率", info.resolution),
            DebugStatRow("视频码率", info.videoBitrate),
            DebugStatRow("音频码率", info.audioBitrate),
            DebugStatRow("带宽估算", info.bandwidthEstimate),
            DebugStatRow("播放器视口", info.playerViewport),
            DebugStatRow("CDN", listOf(info.cdnHost, info.cdnIndex).filter { it.isNotBlank() }.joinToString(" · ")),
            DebugStatRow("网络", info.networkType),
            DebugStatRow("前向缓冲", info.forwardBuffer)
        ),
        PlaybackInsightSection.VIDEO to listOf(
            DebugStatRow("视频编码", info.videoCodec),
            DebugStatRow("帧率", info.frameRate),
            DebugStatRow("视频解码器", info.videoDecoder),
            DebugStatRow("实际解码类型", info.videoDecoderType),
            DebugStatRow("创建时解码偏好", info.videoDecoderPreference),
            DebugStatRow("解码方式说明", info.videoDecoderFallbackReason),
            DebugStatRow("掉帧诊断", info.videoDecoderDropWarning),
            DebugStatRow("掉帧", info.droppedFrames)
        ),
        PlaybackInsightSection.AUDIO to listOf(
            DebugStatRow("音频编码", info.audioCodec),
            DebugStatRow("音频解码器", info.audioDecoder)
        ),
        PlaybackInsightSection.RUNTIME to listOf(
            DebugStatRow("播放状态", info.playbackState),
            DebugStatRow("准备播放", info.playWhenReady),
            DebugStatRow("正在播放", info.isPlaying),
            DebugStatRow("首帧", info.firstFrame)
        ),
        PlaybackInsightSection.EVENTS to listOf(
            DebugStatRow("最近加载错误", info.lastLoadError),
            DebugStatRow("最近视频事件", info.lastVideoEvent),
            DebugStatRow("最近音频事件", info.lastAudioEvent)
        )
    ).mapValues { (_, sectionRows) -> sectionRows.filter { it.value.isNotBlank() } }
        .filterValues { it.isNotEmpty() }

    return PlaybackInsightPresentation(
        summary = summaryParts.joinToString(" · ").ifBlank {
            info.playbackState.ifBlank { "等待播放器数据" }
        },
        level = level,
        statusText = statusText,
        sections = sections
    )
}

internal fun shouldShowPlaybackInsightHud(
    mode: PlayerSettingsStore.PlayerInsightMode,
    hasMeasuredData: Boolean,
    controlsVisible: Boolean,
    screenLocked: Boolean,
    level: PlaybackInsightLevel
): Boolean {
    if (mode == PlayerSettingsStore.PlayerInsightMode.OFF ||
        !hasMeasuredData ||
        screenLocked
    ) {
        return false
    }
    return controlsVisible ||
        mode == PlayerSettingsStore.PlayerInsightMode.ALWAYS ||
        level == PlaybackInsightLevel.ATTENTION
}

internal fun resolvePlaybackInsightPanelLayoutPolicy(
    screenWidthDp: Int,
    screenHeightDp: Int
): PlaybackInsightPanelLayoutPolicy {
    val edgePaddingDp = 16
    val availableWidthDp = (screenWidthDp - edgePaddingDp * 2).coerceAtLeast(0)
    val availableHeightDp = (screenHeightDp - edgePaddingDp * 2).coerceAtLeast(0)
    val isLandscape = screenWidthDp > screenHeightDp
    val targetWidthDp = if (isLandscape) {
        (screenWidthDp * 0.42f).toInt().coerceIn(320, 440)
    } else {
        availableWidthDp.coerceAtMost(440)
    }
    val targetMaxHeightDp = if (isLandscape) {
        availableHeightDp.coerceAtMost(360)
    } else {
        (screenHeightDp * 0.68f).toInt().coerceAtMost(520)
    }
    return PlaybackInsightPanelLayoutPolicy(
        widthDp = targetWidthDp.coerceAtMost(availableWidthDp),
        maxHeightDp = targetMaxHeightDp.coerceAtMost(availableHeightDp),
        edgePaddingDp = edgePaddingDp
    )
}

internal fun appendPlaybackDiagnosticEvent(
    current: List<String>,
    event: String,
    maxEntries: Int = 40
): List<String> {
    val normalizedEvent = event.trim()
    if (normalizedEvent.isBlank()) return current

    val next = buildList {
        addAll(current.takeLast((maxEntries - 1).coerceAtLeast(0)))
        if (current.lastOrNull() != normalizedEvent) {
            add(normalizedEvent)
        }
    }
    return next.takeLast(maxEntries)
}

internal fun resolvePlaybackDiagnosticEvents(
    current: List<String>,
    event: String,
    diagnosticsEnabled: Boolean,
    maxEntries: Int = 40
): List<String> {
    if (!diagnosticsEnabled) return current
    return appendPlaybackDiagnosticEvent(
        current = current,
        event = event,
        maxEntries = maxEntries
    )
}

internal fun shouldMonitorPlaybackIssues(
    diagnosticsEnabled: Boolean,
    bufferingStartedAtMs: Long,
    waitingFirstFrameStartedAtMs: Long,
    hasPendingUserAction: Boolean
): Boolean {
    return diagnosticsEnabled &&
        (bufferingStartedAtMs > 0L || waitingFirstFrameStartedAtMs > 0L || hasPendingUserAction)
}

internal fun buildPlaybackDiagnosticReport(
    title: String,
    bvid: String,
    cid: Long,
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    debugInfo: PlaybackDebugInfo,
    recentEvents: List<String>,
    generatedAtMillis: Long = System.currentTimeMillis()
): String {
    val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        .format(Date(generatedAtMillis))
    val debugRows = resolvePlaybackDebugRows(debugInfo)

    return buildString {
        appendLine("BiliPai Player Diagnostics")
        appendLine("Generated at: $generatedAt")
        if (title.isNotBlank()) appendLine("Title: $title")
        if (bvid.isNotBlank()) appendLine("BVID: $bvid")
        if (cid > 0L) appendLine("CID: $cid")
        appendLine("Position: ${FormatUtils.formatDuration(currentPositionMs)}")
        appendLine("Buffered: ${FormatUtils.formatDuration(bufferedPositionMs)}")
        if (debugRows.isNotEmpty()) {
            appendLine()
            appendLine("Playback stats:")
            debugRows.forEach { row ->
                appendLine("${row.label}: ${row.value}")
            }
        }
        if (recentEvents.isNotEmpty()) {
            appendLine()
            appendLine("Recent events:")
            recentEvents.forEach { event ->
                appendLine("- $event")
            }
        }
    }.trim()
}

internal fun resolvePlaybackIssueSignal(
    playbackState: Int,
    playWhenReady: Boolean,
    firstFrameRendered: Boolean,
    bufferingDurationMs: Long,
    waitingFirstFrameDurationMs: Long
): PlaybackIssueSignal? {
    if (!playWhenReady) return null

    if (playbackState == androidx.media3.common.Player.STATE_BUFFERING &&
        bufferingDurationMs >= PLAYBACK_STUTTER_THRESHOLD_MS
    ) {
        return PlaybackIssueSignal(
            type = PlaybackIssueType.STUTTER,
            title = "检测到播放卡顿",
            message = "播放器长时间处于缓冲状态，可以一键导出诊断日志。"
        )
    }

    if (playbackState == androidx.media3.common.Player.STATE_READY &&
        !firstFrameRendered &&
        waitingFirstFrameDurationMs >= PLAYBACK_BLACK_SCREEN_THRESHOLD_MS
    ) {
        return PlaybackIssueSignal(
            type = PlaybackIssueType.BLACK_SCREEN,
            title = "检测到疑似黑屏",
            message = "播放器已经准备完成但首帧迟迟未出现，可以一键导出诊断日志。"
        )
    }

    return null
}

internal fun resolvePlaybackActionNoResponseSignal(
    actionType: PlaybackUserActionType,
    actionAgeMs: Long,
    hasPlayerResponded: Boolean
): PlaybackIssueSignal? {
    if (hasPlayerResponded || actionAgeMs < PLAYBACK_NO_RESPONSE_THRESHOLD_MS) return null

    val actionLabel = when (actionType) {
        PlaybackUserActionType.PLAY -> "播放"
        PlaybackUserActionType.PAUSE -> "暂停"
    }
    return PlaybackIssueSignal(
        type = PlaybackIssueType.NO_RESPONSE,
        title = "检测到点击无响应",
        message = "用户点击${actionLabel}后播放器状态没有及时变化，可以一键导出诊断日志。"
    )
}

internal fun resolveCenterLoadingUiState(
    isBuffering: Boolean,
    isQualitySwitching: Boolean,
    isSeekTransitionPending: Boolean,
    playWhenReady: Boolean,
    isPlaying: Boolean,
    bandwidthEstimate: String,
    playbackState: Int = androidx.media3.common.Player.STATE_READY,
): CenterLoadingUiState? {
    if (playbackState == androidx.media3.common.Player.STATE_ENDED) return null
    val bandwidthLabel = bandwidthEstimate.trim().ifBlank { null }
    return when {
        isBuffering && isQualitySwitching -> {
            CenterLoadingUiState(
                reason = CenterLoadingReason.QUALITY_SWITCH,
                primaryText = bandwidthLabel ?: "正在切换清晰度...",
                secondaryText = bandwidthLabel?.let { "正在切换清晰度..." }
            )
        }
        isSeekTransitionPending && playWhenReady && !isPlaying -> {
            CenterLoadingUiState(
                reason = CenterLoadingReason.SEEK_BUFFERING,
                primaryText = if (isBuffering) {
                    bandwidthLabel ?: "正在缓冲..."
                } else {
                    "正在恢复播放..."
                },
                secondaryText = if (isBuffering) {
                    "正在定位新进度..."
                } else {
                    "正在等待视频重新跟上进度..."
                }
            )
        }
        else -> null
    }
}

internal fun resolveCenterLoadingVisualState(
    themePrimary: Color
): CenterLoadingVisualState {
    return CenterLoadingVisualState(
        indicatorColor = themePrimary,
        primaryTextColor = themePrimary,
        secondaryTextColor = Color.White.copy(alpha = 0.76f)
    )
}

internal fun resolveSponsorProgressBarMarkers(
    durationMs: Long,
    markers: List<SponsorProgressMarker>
): List<ProgressBarMarkerUiState> {
    if (durationMs <= 0L || markers.isEmpty()) return emptyList()
    return markers.mapNotNull { marker ->
        val startFraction = (marker.startTimeMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val endFraction = (marker.endTimeMs.toFloat() / durationMs).coerceIn(0f, 1f)
        if (endFraction <= startFraction) {
            null
        } else {
            ProgressBarMarkerUiState(
                segmentId = marker.segmentId,
                category = marker.category,
                startFraction = startFraction,
                endFraction = endFraction,
                color = resolveSponsorProgressMarkerColor(marker.category, marker.colorHex)
            )
        }
    }
}

private fun resolveSponsorProgressMarkerColor(category: String, colorHex: String?): Color {
    colorHex?.let { hex ->
        runCatching { return Color(android.graphics.Color.parseColor(hex)) }
    }
    return if (category == SponsorCategory.SPONSOR) {
        Color(0xFFFF8A65)
    } else {
        Color(0xFFFDE68A).copy(alpha = 0.72f)
    }
}
