package com.android.purebilibili.feature.video.ui.overlay

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.player.resolveProgressFraction as resolveSharedProgressFraction
import com.android.purebilibili.data.model.response.AiAudioInfo
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.feature.anime4k.Anime4KPreset
import com.android.purebilibili.feature.anime4k.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.video.playback.policy.resolveDisplayedPlaybackTransitionPosition
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import com.android.purebilibili.feature.video.ui.components.PlayerListPopupPlacement
import com.android.purebilibili.feature.video.ui.components.PlayerSelectionItem
import com.android.purebilibili.feature.video.ui.components.PlayerSelectionPopup
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import kotlin.math.roundToInt

data class PlayerProgress(
    val current: Long = 0L,
    val duration: Long = 0L,
    val buffered: Long = 0L
)

internal fun resolveSeekableDurationMs(
    playbackDurationMs: Long,
    fallbackDurationMs: Long
): Long {
    return if (playbackDurationMs > 0L) {
        playbackDurationMs
    } else {
        fallbackDurationMs.coerceAtLeast(0L)
    }
}

internal fun resolveDisplayedPlayerProgress(
    progress: PlayerProgress,
    previewPositionMs: Long?,
    previewActive: Boolean,
    playbackTransitionPositionMs: Long? = null
): PlayerProgress {
    val safeDuration = progress.duration.coerceAtLeast(0L)
    if (previewActive && previewPositionMs != null) {
        val resolvedCurrent = if (safeDuration > 0L) {
            previewPositionMs.coerceIn(0L, safeDuration)
        } else {
            previewPositionMs.coerceAtLeast(0L)
        }
        return progress.copy(current = resolvedCurrent)
    }

    val heldCurrent = resolveDisplayedPlaybackTransitionPosition(
        playerPositionMs = progress.current,
        transitionPositionMs = playbackTransitionPositionMs
    )
    val resolvedCurrent = if (safeDuration > 0L) {
        heldCurrent.coerceIn(0L, safeDuration)
    } else {
        heldCurrent.coerceAtLeast(0L)
    }
    return progress.copy(current = resolvedCurrent)
}

internal fun resolveDisplayedPlayerProgressWithOverride(
    progress: PlayerProgress,
    overridePositionMs: Long?
): PlayerProgress {
    val resolvedCurrent = overridePositionMs ?: return progress
    val safeDuration = progress.duration.coerceAtLeast(0L)
    val clampedCurrent = if (safeDuration > 0L) {
        resolvedCurrent.coerceIn(0L, safeDuration)
    } else {
        resolvedCurrent.coerceAtLeast(0L)
    }
    return progress.copy(current = clampedCurrent)
}

internal fun resolveSeekPreviewTargetPositionMs(
    displayPositionMs: Long,
    dragTargetPositionMs: Long,
    isSeekScrubbing: Boolean
): Long {
    return if (isSeekScrubbing) {
        dragTargetPositionMs.coerceAtLeast(0L)
    } else {
        displayPositionMs.coerceAtLeast(0L)
    }
}

internal fun resolveSeekDragCommitPositionMs(
    dragStartPositionMs: Long,
    latestDragPositionMs: Long
): Long {
    return if (latestDragPositionMs >= 0L) {
        latestDragPositionMs
    } else {
        dragStartPositionMs.coerceAtLeast(0L)
    }
}

internal fun resolveProgressFraction(
    positionMs: Long,
    durationMs: Long
): Float {
    return resolveSharedProgressFraction(positionMs, durationMs)
}

internal fun resolveSeekPositionFromTouch(
    touchX: Float,
    containerWidthPx: Float,
    durationMs: Long
): Long {
    if (durationMs <= 0L || containerWidthPx <= 0f) return 0L
    val fraction = (touchX / containerWidthPx).coerceIn(0f, 1f)
    return (durationMs.toFloat() * fraction).roundToInt().toLong().coerceIn(0L, durationMs)
}

internal fun shouldCancelSeekDragOnPointerInputCompletion(
    dragInProgress: Boolean
): Boolean = dragInProgress

/**
 * Seek tap/drag must live on a shared outer container that wraps both the chapter label and the
 * track. Placing gestures on a track sibling loses pointer DOWN to the chapter [clickable].
 */
enum class VideoProgressBarSeekGestureHost {
    SharedOuterContainer
}

internal fun resolveVideoProgressBarSeekGestureHost(
    @Suppress("UNUSED_PARAMETER") hasChapterLabel: Boolean
): VideoProgressBarSeekGestureHost {
    // Always use a shared outer host. Chapter videos previously broke scrubbing when seek
    // gestures lived on a track sibling under the chapter label's clickable hit target.
    return VideoProgressBarSeekGestureHost.SharedOuterContainer
}





internal fun resolveSubtitlePanelTrackOptions(trackOptions: List<SubtitleTrackOption>): List<SubtitleTrackOption> =
    trackOptions.filter { it.trackKey.isNotBlank() && it.label.isNotBlank() }.distinctBy { it.trackKey }

/**
 * PiliPlus' single bottom layout in all four modes. Optional entries follow content capabilities,
 * not material. Only ratio/quality have the reference fullscreen-or-500dp visibility gate.
 */
@Composable
fun BottomControlBar(
    isPlaying: Boolean,
    progress: PlayerProgress = PlayerProgress(),
    isFullscreen: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    viewportWidthDpOverride: Int? = null,
    viewportIsLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
    currentSpeed: Float = 1f,
    currentRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    currentQualityLabel: String = "",
    onSpeedClick: () -> Unit = {},
    onRatioClick: () -> Unit = {},
    onQualityClick: () -> Unit = {},
    hasEpisodeEntry: Boolean = false,
    onEpisodeClick: () -> Unit = {},
    onPreviousEpisodeClick: () -> Unit = {},
    onNextEpisodeClick: () -> Unit = {},
    onActionBoundsChanged: (PlayerControlAnchor, IntRect) -> Unit = { _, _ -> },
    subtitleControlState: SubtitleControlUiState = SubtitleControlUiState(),
    subtitleControlCallbacks: SubtitleControlCallbacks = SubtitleControlCallbacks(),
    aiAudioInfo: AiAudioInfo? = null,
    currentAudioLang: String? = null,
    onAudioLangChange: (String) -> Unit = {},
    anime4kAvailable: Boolean = false,
    anime4kEnabled: Boolean = false,
    videoEnhancementAlgorithm: VideoEnhancementAlgorithm = VideoEnhancementAlgorithm.ANIME4K,
    anime4kPreset: Anime4KPreset = Anime4KPreset.FAST,
    onAnime4kToggle: (Boolean) -> Unit = {},
    onVideoEnhancementAlgorithmChange: (VideoEnhancementAlgorithm) -> Unit = {},
    onAnime4kPresetChange: (Anime4KPreset) -> Unit = {},
    heatmapEnabled: Boolean = true,
    onHeatmapToggle: () -> Unit = {},
    showViewPoints: Boolean = true,
    onViewPointsToggle: () -> Unit = {},
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    viewPoints: List<com.android.purebilibili.data.model.response.ViewPoint> = emptyList(),
    sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
    pbpRidgeSamples: List<PbpRidgeSample> = emptyList(),
    onChapterClick: () -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    seekPositionMs: Long = progress.current,
    isSeekScrubbing: Boolean = false,
    seekPositionProvider: (() -> Long)? = null,
    progressProvider: () -> PlayerProgress = { progress },
    onFloatingPanelVisibilityChange: (Boolean) -> Unit = {},
) {
    val widthDp = viewportWidthDpOverride ?: LocalConfiguration.current.screenWidthDp
    val slotWidth = if (viewportIsLandscape && isFullscreen) 42.dp else 35.dp
    val showExtendedOptions = isFullscreen || widthDp >= 500
    val displayPosition = { (seekPositionProvider?.invoke() ?: seekPositionMs).coerceAtLeast(0L) }
    val progressLayout = remember(widthDp) {
        resolveVideoProgressBarLayoutPolicy(widthDp).copy(
            baseHeightWithoutChapterDp = 14,
            baseHeightWithChapterDp = 14,
            touchContainerHeightDp = 14,
            trackHeightDp = 3.5f,
            thumbIdleSizeDp = 14,
            thumbDraggingSizeDp = 14,
        )
    }
    val bounds = remember(isFullscreen, viewportIsLandscape) {
        mutableStateMapOf<PlayerControlAnchor, IntRect>()
    }
    var localMenu by remember(isFullscreen, viewportIsLandscape) { mutableStateOf<PlayerControlAnchor?>(null) }
    val latestVisibility by rememberUpdatedState(onFloatingPanelVisibilityChange)
    LaunchedEffect(localMenu) { latestVisibility(localMenu != null) }
    DisposableEffect(Unit) { onDispose { latestVisibility(false) } }
    fun anchor(action: PlayerControlAnchor, tag: String): Modifier =
        Modifier.testTag(tag).playerActionBounds {
            bounds[action] = it
            onActionBoundsChanged(action, it)
        }
    val density = LocalDensity.current
    // Reference player chrome uses fixed 13sp labels; menus still respect system font scaling.
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Column(
            modifier = modifier.fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .windowInsetsPadding(playerControlHorizontalInsets(viewportIsLandscape, isFullscreen))
                .padding(vertical = PILI_PLAYER_BOTTOM_VERTICAL_PADDING_DP.dp),
        ) {
            val seekInsets = WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal).union(
                WindowInsets(left = 20.dp, right = 20.dp),
            )
            val hasSegments = showViewPoints && viewPoints.isNotEmpty()
            Box(Modifier.windowInsetsPadding(seekInsets).padding(bottom = PILI_PLAYER_PROGRESS_GAP_DP.dp)
                .height((if (hasSegments) PILI_PLAYER_CHAPTER_PROGRESS_HEIGHT_DP else PILI_PLAYER_PROGRESS_HEIGHT_DP).dp)) {
                PlayerControlProgressBar(
                    progressProvider = progressProvider,
                    displayPositionProvider = displayPosition,
                    isSeekScrubbing = isSeekScrubbing,
                    layoutPolicy = progressLayout,
                    seekAreaHeight = PILI_PLAYER_PROGRESS_HEIGHT_DP.dp,
                    onSeek = onSeek,
                    onSeekStart = onSeekStart,
                    onSeekDragStart = onSeekDragStart,
                    onSeekDragUpdate = onSeekDragUpdate,
                    onSeekDragCancel = onSeekDragCancel,
                    videoshotData = videoshotData,
                    viewPoints = if (showViewPoints) viewPoints else emptyList(),
                    sponsorMarkers = sponsorMarkers,
                    pbpRidgeSamples = if (heatmapEnabled) pbpRidgeSamples else emptyList(),
                    currentChapterProvider = { null },
                    onChapterClick = onChapterClick,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).testTag("player_progress"),
                )
                PlayerControlViewPointSegments(
                    progressProvider = progressProvider,
                    viewPoints = if (showViewPoints) viewPoints else emptyList(),
                    onSeek = onSeek,
                    spacing = 0.dp,
                    interactive = false,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).height(PILI_PLAYER_PROGRESS_HEIGHT_DP.dp).testTag("player_viewpoint_segments"),
                )
            }
            PiliPlayerBar(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(PILI_PLAYER_BOTTOM_ROW_HEIGHT_DP.dp)
                    .testTag("player_control_row"),
                left = {
                    PlayerPlayPauseButton(isPlaying, onPlayPauseClick, width = slotWidth)
                    ProgressTimeText(
                        positionProvider = displayPosition,
                        durationProvider = { progressProvider().duration },
                        modifier = Modifier.padding(horizontal = 8.dp).testTag("player_time_viewport"),
                    )
                    if (hasEpisodeEntry) {
                        PlayerControlIconAction(
                            icon = Icons.Filled.SkipPrevious, description = "上一集",
                            width = slotWidth, height = 30.dp, onClick = onPreviousEpisodeClick,
                            modifier = Modifier.testTag("player_previous_episode"),
                        )
                        PlayerControlIconAction(
                            icon = Icons.Filled.SkipNext, description = "下一集",
                            width = slotWidth, height = 30.dp, onClick = onNextEpisodeClick,
                            modifier = Modifier.testTag("player_next_episode"),
                        )
                    }
                },
                right = {
                    if (pbpRidgeSamples.isNotEmpty()) {
                        PlayerControlIconAction(
                            icon = Icons.Filled.ShowChart, description = "高能进度条",
                            width = slotWidth, height = 30.dp, onClick = onHeatmapToggle,
                            selected = heatmapEnabled, dimmed = !heatmapEnabled,
                            modifier = Modifier.testTag("player_heatmap_action"),
                        )
                    }
                    if (anime4kAvailable) {
                        PlayerControlTextAction(
                            text = if (!anime4kEnabled) "禁用" else if (anime4kPreset == Anime4KPreset.FAST) "效率" else "画质",
                            description = "超分辨率",
                            modifier = anchor(PlayerControlAnchor.ENHANCEMENT, "player_enhancement_action"),
                            onClick = { localMenu = PlayerControlAnchor.ENHANCEMENT },
                        )
                    }
                    if (viewPoints.isNotEmpty()) {
                        PlayerControlIconAction(
                            icon = Icons.AutoMirrored.Filled.ViewList, description = "分段信息",
                            width = slotWidth, height = 30.dp, onClick = onChapterClick,
                            onLongClick = onViewPointsToggle,
                            dimmed = !showViewPoints, selected = showViewPoints,
                            modifier = anchor(PlayerControlAnchor.CHAPTERS, "player_chapter_action"),
                        )
                    }
                    if (hasEpisodeEntry) {
                        PlayerControlIconAction(
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay, description = "选集",
                            width = slotWidth, height = 30.dp, onClick = onEpisodeClick,
                            modifier = anchor(PlayerControlAnchor.EPISODES, "player_episode_action"),
                        )
                    }
                    if (showExtendedOptions) {
                        PlayerControlTextAction(
                            text = currentRatio.displayName, description = "画面比例", onClick = onRatioClick,
                            modifier = anchor(PlayerControlAnchor.RATIO, "player_ratio_action"),
                        )
                    }
                    if (!aiAudioInfo?.items.isNullOrEmpty()) {
                        PlayerControlIconAction(
                            icon = Icons.Filled.Translate, description = "翻译",
                            width = slotWidth, height = 30.dp, iconSize = 18.dp,
                            onClick = { localMenu = PlayerControlAnchor.TRANSLATION },
                            modifier = anchor(PlayerControlAnchor.TRANSLATION, "player_translation_action"),
                        )
                    }
                    if (subtitleControlState.trackAvailable) {
                        PlayerControlIconAction(
                            icon = if (subtitleControlState.enabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                            description = "字幕", width = slotWidth, height = 30.dp,
                            selected = subtitleControlState.enabled,
                            onClick = { localMenu = PlayerControlAnchor.SUBTITLE },
                            modifier = anchor(PlayerControlAnchor.SUBTITLE, "player_subtitle_action"),
                        )
                    }
                    PlayerControlTextAction(
                        text = "${currentSpeed}X", description = "倍速", onClick = onSpeedClick,
                        modifier = anchor(PlayerControlAnchor.SPEED, "player_speed_action"),
                    )
                    if (showExtendedOptions && currentQualityLabel.isNotEmpty()) {
                        PlayerControlTextAction(
                            text = currentQualityLabel, description = "画质", onClick = onQualityClick,
                            modifier = anchor(PlayerControlAnchor.QUALITY, "player_quality_action"),
                        )
                    }
                    PlayerControlIconAction(
                        icon = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        description = if (isFullscreen) "退出全屏" else "全屏",
                        width = slotWidth, height = 30.dp, iconSize = 24.dp,
                        onClick = onToggleFullscreen, modifier = Modifier.testTag("player_fullscreen_toggle"),
                    )
                },
            )
        }
    }
    localMenu?.let { menu ->
        PlayerSelectionPopup(
            title = when (menu) {
                PlayerControlAnchor.SUBTITLE -> "字幕"
                PlayerControlAnchor.TRANSLATION -> "翻译"
                else -> "超分辨率"
            },
            showTitle = false,
            placement = PlayerListPopupPlacement.ANCHOR_END,
            anchorBounds = bounds[menu],
            onDismissRequest = { localMenu = null },
        ) {
            when (menu) {
                PlayerControlAnchor.SUBTITLE -> {
                    val tracks = resolveSubtitlePanelTrackOptions(subtitleControlState.trackOptions)
                    PlayerSelectionItem(
                        index = 0, optionCount = tracks.size + 1,
                        text = "关闭字幕", selected = !subtitleControlState.enabled,
                        onClick = {
                            localMenu = null
                            subtitleControlCallbacks.onDisplayModeChange(SubtitleDisplayMode.OFF)
                        },
                    )
                    tracks.forEachIndexed { index, track ->
                        PlayerSelectionItem(
                            index = index + 1, optionCount = tracks.size + 1,
                            text = track.label, selected = subtitleControlState.enabled && track.selected,
                            onClick = {
                                localMenu = null
                                subtitleControlCallbacks.onTrackSelected(track.trackKey)
                                subtitleControlCallbacks.onDisplayModeChange(SubtitleDisplayMode.PRIMARY_ONLY)
                            },
                        )
                    }
                }
                PlayerControlAnchor.TRANSLATION -> {
                    val languages = aiAudioInfo?.items.orEmpty()
                    PlayerSelectionItem(
                        index = 0, optionCount = languages.size + 1,
                        text = "关闭翻译", selected = currentAudioLang.isNullOrBlank(),
                        onClick = { localMenu = null; onAudioLangChange("") },
                    )
                    languages.forEachIndexed { index, language ->
                        PlayerSelectionItem(
                            index = index + 1, optionCount = languages.size + 1,
                            text = language.langDoc,
                            selected = currentAudioLang == language.langCode,
                            onClick = { localMenu = null; onAudioLangChange(language.langCode) },
                        )
                    }
                }
                PlayerControlAnchor.ENHANCEMENT -> {
                    listOf("禁用", "效率", "画质").forEachIndexed { index, label ->
                        PlayerSelectionItem(
                            index = index, optionCount = 3,
                            text = label,
                            selected = if (index == 0) !anime4kEnabled else anime4kEnabled &&
                                videoEnhancementAlgorithm == VideoEnhancementAlgorithm.ANIME4K &&
                                anime4kPreset == if (index == 1) Anime4KPreset.FAST else Anime4KPreset.QUALITY,
                            onClick = {
                                localMenu = null
                                if (index == 0) {
                                    onAnime4kToggle(false)
                                } else {
                                    onVideoEnhancementAlgorithmChange(VideoEnhancementAlgorithm.ANIME4K)
                                    onAnime4kPresetChange(if (index == 1) Anime4KPreset.FAST else Anime4KPreset.QUALITY)
                                    onAnime4kToggle(true)
                                }
                            },
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
