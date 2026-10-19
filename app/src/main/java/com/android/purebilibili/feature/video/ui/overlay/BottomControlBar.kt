// File: feature/video/ui/overlay/BottomControlBar.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.player.resolveProgressFraction as resolveSharedProgressFraction
import com.android.purebilibili.core.ui.drawMediaProgressTrack
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.feature.video.progress.PbpRidgeDensity
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement
import com.android.purebilibili.feature.video.ui.components.resolveSeekPreviewBubbleHeightDp
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.DolbyBadge
import com.android.purebilibili.feature.video.ui.components.HiResBadge
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.subtitle.SubtitleTrackOption
import com.android.purebilibili.feature.video.subtitle.resolveSubtitleDisplayOptions
import com.android.purebilibili.feature.video.playback.policy.resolveDisplayedPlaybackTransitionPosition
import com.android.purebilibili.core.store.PlayerProgressPlacement
import com.android.purebilibili.feature.anime4k.Anime4KPreset
import com.android.purebilibili.feature.anime4k.DEFAULT_FSR_SHARPNESS
import com.android.purebilibili.feature.anime4k.FSR_SHARPNESS_SLIDER_STEPS
import com.android.purebilibili.feature.anime4k.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.anime4k.resolveAnime4KPresetLabel
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.plugin.skin.LocalUiSkinState
import com.android.purebilibili.core.plugin.skin.UiSkinAnimatedAsset
import com.android.purebilibili.core.plugin.skin.UiSkinSurface
import com.android.purebilibili.core.plugin.skin.assetPath
import com.android.purebilibili.core.plugin.skin.parseUiSkinColor

/**
 * Bottom Control Bar Component
 *
 * Fullscreen: [Play/Pause] [Time / Danmaku toggle / Settings / Send] [Tools] [Fullscreen]
 * Inline: [Play/Pause] [Time / Danmaku toggle / Settings] [Compact send] [Tools] [Fullscreen]
 */

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


internal fun shouldShowSubtitleButtonInControlBar(
    isFullscreen: Boolean,
    subtitleTrackAvailable: Boolean
): Boolean = isFullscreen && subtitleTrackAvailable

internal fun shouldShowPortraitSwitchButtonInControlBar(
    isFullscreen: Boolean
): Boolean = isFullscreen

internal fun shouldShowPortraitSwitchButtonInline(
    isFullscreen: Boolean,
    isVerticalVideo: Boolean,
    widthDp: Int
): Boolean {
    if (isFullscreen) return false
    // On tablets and large screens (widthDp >= 600), only show the portrait fullscreen button
    // if the video is actually vertical. Standard horizontal 16:9 videos should never
    // show "竖屏", which crowds out the regular fullscreen button.
    // On compact phones (widthDp < 600), preserve existing behavior.
    return if (widthDp >= 600) {
        isVerticalVideo
    } else {
        true
    }
}

internal fun shouldShowAudioQualityButtonInline(
    isFullscreen: Boolean,
    widthDp: Int,
    isSpecialAudio: Boolean
): Boolean {
    if (isFullscreen) return false
    // Special audio formats (Hi-Res / Dolby) always display their badge inline
    if (isSpecialAudio) return true
    // In inline non-fullscreen mode, standard audio (AAC) can be collapsed when space
    // is constrained (< 680dp on tablet split panes) to prioritize primary controls
    return widthDp >= 680 || widthDp < 480
}

internal fun shouldShowNextEpisodeButtonInControlBar(
    isFullscreen: Boolean,
    hasNextEpisode: Boolean
): Boolean = isFullscreen && hasNextEpisode

internal fun shouldShowEpisodeButtonInControlBar(
    isFullscreen: Boolean,
    hasEpisodeEntry: Boolean,
    widthDp: Int = Int.MAX_VALUE
): Boolean = isFullscreen && hasEpisodeEntry && widthDp >= 600

internal fun shouldShowEpisodeInMoreActions(
    isFullscreen: Boolean,
    hasEpisodeEntry: Boolean,
    showInlineEpisodeButton: Boolean
): Boolean = isFullscreen && hasEpisodeEntry && !showInlineEpisodeButton


/** Fullscreen always; tablet inline cinema (≥600dp) also needs a visible close toggle. */
internal fun shouldShowDanmakuToggleInControlBar(
    isFullscreen: Boolean,
    widthDp: Int
): Boolean = isFullscreen || widthDp >= 600

internal fun shouldShowPlaybackOrderLabelInControlBar(
    isFullscreen: Boolean,
    playbackOrderLabel: String
): Boolean = isFullscreen && playbackOrderLabel.isNotBlank()

internal fun shouldShowAspectRatioButtonInControlBar(
    isFullscreen: Boolean
): Boolean = isFullscreen

internal fun shouldShowMoreActionsButtonInControlBar(
    isFullscreen: Boolean,
    showEpisodeInMoreActions: Boolean = false,
    showNextEpisodeButton: Boolean,
    showPlaybackOrderLabel: Boolean,
    showAudioQualityButton: Boolean,
    showPortraitSwitchButton: Boolean,
    showAnime4KToggle: Boolean = false
): Boolean {
    return isFullscreen && (
        showEpisodeInMoreActions ||
            showNextEpisodeButton ||
            showPlaybackOrderLabel ||
            showAudioQualityButton ||
            showPortraitSwitchButton ||
            showAnime4KToggle
        )
}

internal fun shouldApplyNavigationBarPaddingToBottomControlBar(
    isFullscreen: Boolean
): Boolean = false

internal fun resolveFloatingControlPanelMinWidthDp(widthDp: Int): Int {
    return when {
        widthDp >= 840 -> 184
        widthDp >= 600 -> 176
        else -> 168
    }
}

internal fun resolveMoreActionItemMinWidthDp(widthDp: Int): Int {
    return when {
        widthDp >= 840 -> 112
        widthDp >= 600 -> 104
        else -> 96
    }
}

internal fun resolveMoreActionsButtonAnchorOffsetDp(widthDp: Int): Int {
    return when {
        widthDp >= 840 -> 28
        widthDp >= 600 -> 26
        else -> 24
    }
}

internal fun resolveMoreActionsPanelEndPaddingDp(
    horizontalPaddingDp: Int,
    fullscreenIconSizeDp: Int,
    rightActionSpacingDp: Int,
    moreButtonAnchorOffsetDp: Int
): Int {
    return horizontalPaddingDp +
        fullscreenIconSizeDp +
        rightActionSpacingDp +
        moreButtonAnchorOffsetDp
}

internal fun resolveFloatingPanelBottomOffsetDp(
    bottomPaddingDp: Int,
    controlRowHeightDp: Int,
    gapDp: Int
): Int {
    return bottomPaddingDp + controlRowHeightDp + gapDp
}

internal fun resolveFullscreenToggleTouchTargetDp(iconSizeDp: Int): Int {
    return maxOf(40, iconSizeDp + 16)
}

internal fun shouldConsumeBackgroundGesturesForFloatingPanels(
    showSubtitlePanel: Boolean,
    showMoreActionsPanel: Boolean
): Boolean = showSubtitlePanel || showMoreActionsPanel

internal fun resolveSubtitlePanelTrackOptions(
    trackOptions: List<SubtitleTrackOption>
): List<SubtitleTrackOption> {
    return trackOptions
        .filter { it.trackKey.isNotBlank() && it.label.isNotBlank() }
        .distinctBy { it.trackKey }
}

private fun Modifier.consumeTap(onTap: () -> Unit): Modifier {
    return pointerInput(onTap) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val up = waitForUpOrCancellation()
            if (up != null) {
                up.consume()
                onTap()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BottomControlBar(
    isPlaying: Boolean,
    progress: PlayerProgress = PlayerProgress(),
    isFullscreen: Boolean,
    currentSpeed: Float = 1.0f,
    currentRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    seekPositionMs: Long = progress.current,
    isSeekScrubbing: Boolean = false,
    onSpeedClick: () -> Unit = {},
    onRatioClick: () -> Unit = {},
    onNextEpisodeClick: () -> Unit = {},
    hasNextEpisode: Boolean = false,
    onEpisodeClick: () -> Unit = {},
    hasEpisodeEntry: Boolean = false,
    onToggleFullscreen: () -> Unit,
    viewportWidthDpOverride: Int? = null,

    // Danmaku
    sponsorContributionAvailable: Boolean = false,
    sponsorContributionMarking: Boolean = false,
    onSponsorContributionMarkBoundary: () -> Unit = {},
    onSponsorContributionMarkWholeVideo: () -> Unit = {},
    onSponsorContributionCancel: () -> Unit = {},
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    onDanmakuSettingsClick: () -> Unit = {},
    onDanmakuInputClick: () -> Unit = {},
    isLoggedIn: Boolean = true,
    subtitleControlState: SubtitleControlUiState = SubtitleControlUiState(),
    subtitleControlCallbacks: SubtitleControlCallbacks = SubtitleControlCallbacks(),
    anime4kEnabled: Boolean = false,
    anime4kAvailable: Boolean = false,
    videoEnhancementAlgorithm: VideoEnhancementAlgorithm = VideoEnhancementAlgorithm.ANIME4K,
    anime4kPreset: Anime4KPreset = Anime4KPreset.FAST,
    fsrSharpness: Float = DEFAULT_FSR_SHARPNESS,
    onAnime4kToggle: (Boolean) -> Unit = {},
    onVideoEnhancementAlgorithmChange: (VideoEnhancementAlgorithm) -> Unit = {},
    onAnime4kPresetChange: (Anime4KPreset) -> Unit = {},
    onFsrSharpnessChange: (Float) -> Unit = {},

    // Quality
    currentAudioQualityLabel: String = "音质",
    isHiResAudioSelected: Boolean = false,
    isDolbyAudioSelected: Boolean = false,
    onAudioQualityClick: () -> Unit = {},
    currentQualityLabel: String = "",
    onQualityClick: () -> Unit = {},

    // Features
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    viewPoints: List<com.android.purebilibili.data.model.response.ViewPoint> = emptyList(),
    sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
    pbpRidgeSamples: List<PbpRidgeSample> = emptyList(),
    currentChapter: String? = null,
    onChapterClick: () -> Unit = {},

    // Portrait controls (kept for compatibility, though less used in new design)
    isVerticalVideo: Boolean = false,
    onPortraitFullscreen: () -> Unit = {},
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode = com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
    onPlayModeClick: () -> Unit = {},
    playbackOrderLabel: String = "",
    onPlaybackOrderClick: () -> Unit = {},
    progressPlacement: PlayerProgressPlacement = PlayerProgressPlacement.ABOVE_CONTROLS,
    onPipClick: () -> Unit = {},
    cdnDiagnosticsAvailable: Boolean = false,
    onCdnDiagnosticsClick: () -> Unit = {},
    onPlayerSettingsClick: (() -> Unit)? = null,
    onFloatingPanelVisibilityChange: (Boolean) -> Unit = {},
    /** 紧凑布局：控制行更贴左右边缘，并更靠近进度条。 */
    compactPlayerChrome: Boolean = false,

    modifier: Modifier = Modifier,
    seekPositionProvider: (() -> Long)? = null,
    progressProvider: () -> PlayerProgress = { progress },
    currentChapterProvider: () -> String? = { currentChapter },
) {
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val subtitleTrackAvailable = subtitleControlState.trackAvailable
    val subtitlePrimaryAvailable = subtitleControlState.primaryAvailable
    val subtitleSecondaryAvailable = subtitleControlState.secondaryAvailable
    val subtitleEnabled = subtitleControlState.enabled
    val subtitleDisplayMode = subtitleControlState.displayMode
    val subtitlePrimaryLabel = subtitleControlState.primaryLabel
    val subtitleSecondaryLabel = subtitleControlState.secondaryLabel
    val subtitleTrackOptions = subtitleControlState.trackOptions
    val subtitleLargeTextEnabled = subtitleControlState.largeTextEnabled
    val subtitlePositionLocked = subtitleControlState.positionLocked
    val onSubtitleDisplayModeChange = subtitleControlCallbacks.onDisplayModeChange
    val onSubtitleTrackSelected = subtitleControlCallbacks.onTrackSelected
    val onSubtitleLargeTextChange = subtitleControlCallbacks.onLargeTextChange
    val onSubtitlePositionLockedChange = subtitleControlCallbacks.onPositionLockedChange

    val configuration = LocalConfiguration.current
    val uiLayoutWidthDp = remember(configuration.screenWidthDp, viewportWidthDpOverride) {
        (viewportWidthDpOverride ?: configuration.screenWidthDp).coerceAtLeast(1)
    }
    val layoutPolicy = remember(uiLayoutWidthDp, compactPlayerChrome, isFullscreen) {
        val base = resolveBottomControlBarLayoutPolicy(
            widthDp = uiLayoutWidthDp,
            compact = compactPlayerChrome,
        )
        if (isFullscreen) base.copy(
            progressSpacingDp = 4,
            horizontalPaddingDp = PlayerChromeLayout.EdgePaddingDp,
            playButtonSizeDp = 48,
            playIconSizeDp = 22,
            afterPlaySpacingDp = 0,
            timeFontSp = 12,
            afterTimeSpacingDp = 4,
            danmakuIconSizeDp = 22,
            danmakuSwitchToInputSpacingDp = 8,
            danmakuInputStartPaddingDp = 12,
            danmakuInputFontSp = 12,
            afterInputSpacingDp = 8,
            rightActionSpacingDp = 0,
            actionTextFontSp = 12,
            fullscreenIconSizeDp = 22,
        ) else base
    }
    val floatingPanelMinWidthDp = remember(uiLayoutWidthDp) {
        resolveFloatingControlPanelMinWidthDp(widthDp = uiLayoutWidthDp)
    }
    val moreActionItemMinWidthDp = remember(uiLayoutWidthDp) {
        resolveMoreActionItemMinWidthDp(widthDp = uiLayoutWidthDp)
    }
    val moreActionsPanelWidthDp = remember(moreActionItemMinWidthDp) {
        moreActionItemMinWidthDp * 2 + 32
    }
    val moreButtonAnchorOffsetDp = remember(uiLayoutWidthDp) {
        resolveMoreActionsButtonAnchorOffsetDp(widthDp = uiLayoutWidthDp)
    }
    val moreActionsPanelEndPaddingDp = remember(
        layoutPolicy.horizontalPaddingDp,
        layoutPolicy.fullscreenIconSizeDp,
        layoutPolicy.rightActionSpacingDp,
        moreButtonAnchorOffsetDp
    ) {
        resolveMoreActionsPanelEndPaddingDp(
            horizontalPaddingDp = layoutPolicy.horizontalPaddingDp,
            fullscreenIconSizeDp = layoutPolicy.fullscreenIconSizeDp,
            rightActionSpacingDp = layoutPolicy.rightActionSpacingDp,
            moreButtonAnchorOffsetDp = moreButtonAnchorOffsetDp
        )
    }
    val floatingPanelBottomOffsetDp = remember(
        layoutPolicy.bottomPaddingDp,
        layoutPolicy.playButtonSizeDp,
        layoutPolicy.danmakuInputHeightDp
    ) {
        resolveFloatingPanelBottomOffsetDp(
            bottomPaddingDp = layoutPolicy.bottomPaddingDp,
            controlRowHeightDp = maxOf(layoutPolicy.playButtonSizeDp, layoutPolicy.danmakuInputHeightDp),
            gapDp = 20
        )
    }
    val videoEnhancementPanelMaxHeightDp = remember(
        configuration.screenHeightDp,
        floatingPanelBottomOffsetDp
    ) {
        (configuration.screenHeightDp - floatingPanelBottomOffsetDp - 48).coerceAtLeast(120)
    }
    val progressLayoutPolicy = remember(uiLayoutWidthDp) {
        resolveVideoProgressBarLayoutPolicy(
            widthDp = uiLayoutWidthDp
        )
    }
    val fullscreenToggleTouchTargetDp = remember(layoutPolicy.fullscreenIconSizeDp, isFullscreen) {
        if (isFullscreen) PlayerChromeLayout.CircularButtonSizeDp
        else resolveFullscreenToggleTouchTargetDp(iconSizeDp = layoutPolicy.fullscreenIconSizeDp)
    }
    val showEpisodeButton = remember(isFullscreen, hasEpisodeEntry, uiLayoutWidthDp) {
        shouldShowEpisodeButtonInControlBar(
            isFullscreen = isFullscreen,
            hasEpisodeEntry = hasEpisodeEntry,
            widthDp = uiLayoutWidthDp
        )
    }
    val showEpisodeInMoreActions = remember(isFullscreen, hasEpisodeEntry, showEpisodeButton) {
        shouldShowEpisodeInMoreActions(
            isFullscreen = isFullscreen,
            hasEpisodeEntry = hasEpisodeEntry,
            showInlineEpisodeButton = showEpisodeButton
        )
    }
    val showCompactDanmakuSend = remember(isFullscreen, uiLayoutWidthDp) {
        !isFullscreen && com.android.purebilibili.feature.video.ui.components.shouldShowCompactDanmakuSendAction(
            isFullscreen = isFullscreen,
            widthDp = uiLayoutWidthDp
        )
    }
    var showMoreActionsPanel by remember { mutableStateOf(false) }
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var showVideoEnhancementPanel by remember { mutableStateOf(false) }
    val floatingPanelVisible = showMoreActionsPanel || showSubtitlePanel || showVideoEnhancementPanel
    val currentFloatingPanelVisibilityCallback = rememberUpdatedState(onFloatingPanelVisibilityChange)
    LaunchedEffect(floatingPanelVisible) {
        currentFloatingPanelVisibilityCallback.value(floatingPanelVisible)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentFloatingPanelVisibilityCallback.value(false)
        }
    }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            showMoreActionsPanel = false
            showSubtitlePanel = false
            showVideoEnhancementPanel = false
        }
    }
    val showPlaybackOrderLabel = remember(isFullscreen, playbackOrderLabel) {
        shouldShowPlaybackOrderLabelInControlBar(
            isFullscreen = isFullscreen,
            playbackOrderLabel = playbackOrderLabel
        )
    }
    val showAspectRatioButton = remember(isFullscreen) {
        shouldShowAspectRatioButtonInControlBar(
            isFullscreen = isFullscreen
        )
    }
    val showNextEpisodeButton = remember(isFullscreen, hasNextEpisode) {
        shouldShowNextEpisodeButtonInControlBar(
            isFullscreen = isFullscreen,
            hasNextEpisode = hasNextEpisode
        )
    }
    val showSubtitleButton = remember(isFullscreen, subtitleTrackAvailable) {
        shouldShowSubtitleButtonInControlBar(
            isFullscreen = isFullscreen,
            subtitleTrackAvailable = subtitleTrackAvailable
        )
    }
    val showPortraitSwitchButton = remember(isFullscreen) {
        shouldShowPortraitSwitchButtonInControlBar(
            isFullscreen = isFullscreen
        )
    }
    val isSpecialAudioSelected = isHiResAudioSelected || isDolbyAudioSelected
    val showAudioQualityButtonInline = remember(isFullscreen, uiLayoutWidthDp, isSpecialAudioSelected) {
        shouldShowAudioQualityButtonInline(
            isFullscreen = isFullscreen,
            widthDp = uiLayoutWidthDp,
            isSpecialAudio = isSpecialAudioSelected
        )
    }
    val showPortraitSwitchButtonInline = remember(isFullscreen, isVerticalVideo, uiLayoutWidthDp) {
        shouldShowPortraitSwitchButtonInline(
            isFullscreen = isFullscreen,
            isVerticalVideo = isVerticalVideo,
            widthDp = uiLayoutWidthDp
        )
    }
    val showMoreActionsButton = remember(
        isFullscreen,
        showEpisodeInMoreActions,
        showNextEpisodeButton,
        showPlaybackOrderLabel,
        showPortraitSwitchButton,
        anime4kAvailable
    ) {
        shouldShowMoreActionsButtonInControlBar(
            isFullscreen = isFullscreen,
            showEpisodeInMoreActions = showEpisodeInMoreActions,
            showNextEpisodeButton = showNextEpisodeButton,
            showPlaybackOrderLabel = showPlaybackOrderLabel,
            showAudioQualityButton = isFullscreen,
            showPortraitSwitchButton = showPortraitSwitchButton,
            showAnime4KToggle = anime4kAvailable
        )
    }
    val shouldConsumeFloatingPanelBackground = remember(showSubtitlePanel, showMoreActionsPanel) {
        shouldConsumeBackgroundGesturesForFloatingPanels(
            showSubtitlePanel = showSubtitlePanel,
            showMoreActionsPanel = showMoreActionsPanel
        )
    }
    val subtitleOptions = remember(
        subtitlePrimaryLabel,
        subtitleSecondaryLabel,
        subtitlePrimaryAvailable,
        subtitleSecondaryAvailable
    ) {
        resolveSubtitleDisplayOptions(
            primaryLabel = subtitlePrimaryLabel.ifBlank { "中文" },
            secondaryLabel = subtitleSecondaryLabel.ifBlank { "英文" },
            hasPrimaryTrack = subtitlePrimaryAvailable,
            hasSecondaryTrack = subtitleSecondaryAvailable
        )
    }
    val subtitlePanelTrackOptions = remember(subtitleTrackOptions) {
        resolveSubtitlePanelTrackOptions(subtitleTrackOptions)
    }

    val displayedPositionProvider: () -> Long = {
        (seekPositionProvider?.invoke() ?: seekPositionMs).coerceAtLeast(0L)
    }
    val resolvedBottomPaddingDp = remember(layoutPolicy.bottomPaddingDp, progressPlacement) {
        resolveBottomControlBarBottomPaddingDp(
            defaultBottomPaddingDp = layoutPolicy.bottomPaddingDp,
            progressPlacement = progressPlacement
        )
    }
    val inlineTimeGlassDock = !isFullscreen && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    // Fullscreen labels use a fixed font size, so the two-character gap is a fixed dp value.
    val actionSpacing = (layoutPolicy.actionTextFontSp * 2).dp
    val chromeDensity = LocalDensity.current
    val controlRowDensity = remember(chromeDensity, isFullscreen) {
        if (isFullscreen) Density(chromeDensity.density, fontScale = 1f) else chromeDensity
    }
    val glassProgressDock = isFullscreen && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val expandInlineActionDock = !isFullscreen && uiLayoutWidthDp < 600 && !showCompactDanmakuSend
    val showDirectTools = isFullscreen && uiLayoutWidthDp >= 840
    val dockProgressLayoutPolicy = if (glassProgressDock) {
        // The track's touch row matches the shell, keeping the track vertically centered.
        // Chapter chrome stays above that row, as does the separate seek preview area.
        val trackRowHeightDp = PlayerChromeLayout.CircularButtonSizeDp
        progressLayoutPolicy.copy(
            baseHeightWithoutChapterDp = trackRowHeightDp,
            baseHeightWithChapterDp = trackRowHeightDp +
                progressLayoutPolicy.baseHeightWithChapterDp - progressLayoutPolicy.touchContainerHeightDp,
            touchContainerHeightDp = trackRowHeightDp,
        )
    } else {
        progressLayoutPolicy
    }
    val progressBarContent: @Composable () -> Unit = {
        val renderProgress: @Composable (Modifier) -> Unit = { progressModifier ->
            PlayerControlProgressBar(
                progressProvider = progressProvider,
                displayPositionProvider = displayedPositionProvider,
                isSeekScrubbing = isSeekScrubbing,
                layoutPolicy = dockProgressLayoutPolicy,
                neutralGlassTrack = glassProgressDock,
                onSeek = onSeek,
                onSeekStart = onSeekStart,
                onSeekDragStart = onSeekDragStart,
                onSeekDragUpdate = onSeekDragUpdate,
                onSeekDragCancel = onSeekDragCancel,
                videoshotData = videoshotData,
                viewPoints = viewPoints,
                sponsorMarkers = sponsorMarkers,
                pbpRidgeSamples = pbpRidgeSamples,
                currentChapterProvider = currentChapterProvider,
                onChapterClick = onChapterClick,
                modifier = progressModifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("player_progress")
            )
        }
        if (glassProgressDock) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layoutPolicy.horizontalPaddingDp.dp)
                    .heightIn(min = 48.dp),
            ) {
                // Seeking adds a thumbnail preview above the track. Keep the shell pinned to
                // the 48dp track row so it never stretches around the preview image.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .playerControlGlassProgressDock(),
                )
                renderProgress(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        } else {
            renderProgress(Modifier.padding(horizontal = if (isFullscreen) layoutPolicy.horizontalPaddingDp.dp else 0.dp))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)) else Modifier)
            .padding(bottom = resolvedBottomPaddingDp.dp + if (isFullscreen && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) 4.dp else 0.dp)
            .then(
                if (shouldApplyNavigationBarPaddingToBottomControlBar(isFullscreen = isFullscreen)) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                }
            )
    ) {
        if (progressPlacement == PlayerProgressPlacement.ABOVE_CONTROLS) {
            progressBarContent()
            PlayerControlViewPointSegments(
                progressProvider = progressProvider,
                viewPoints = viewPoints,
                onSeek = onSeek,
                spacing = layoutPolicy.progressSpacingDp.dp,
                modifier = Modifier
                    .padding(horizontal = if (isFullscreen) layoutPolicy.horizontalPaddingDp.dp else 0.dp)
                    .testTag("player_viewpoint_segments"),
            )
        }

        // 2. Control Row
        // Fullscreen chrome keeps fixed label sizes so system font scaling cannot clip
        // fixed-width slots such as "更多"; inline controls still follow system font scale.
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides layoutPolicy.playButtonSizeDp.dp,
            LocalDensity provides controlRowDensity,
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_control_row")
                .padding(horizontal = layoutPolicy.horizontalPaddingDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fullscreen actions share one layout: content-width tools stay right-aligned with
            // fixed two-character gaps; glass-only width reserves are marked inline.
            if (isFullscreen) {
                PlayerPlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick,
                    sizeDp = layoutPolicy.playButtonSizeDp,
                    iconSizeDp = layoutPolicy.playIconSizeDp,
                )
                val showDanmakuToggle = shouldShowDanmakuToggleInControlBar(
                    isFullscreen = isFullscreen,
                    widthDp = uiLayoutWidthDp,
                )
                val danmakuActionWidthDp = if (isFullscreen) 40 else maxOf(40, layoutPolicy.playButtonSizeDp)
                val leftDockPaddingDp = if (isFullscreen) 8 else if (inlineTimeGlassDock) 20 else 0
                val timeEndSpacingDp = if (!inlineTimeGlassDock || showDanmakuToggle) layoutPolicy.afterTimeSpacingDp else 0
                // Only glass reserves a scrollable time viewport on narrow windows.
                // Native keeps its existing minimum width and adaptive layout.
                val minimumLeftWidthDp = leftDockPaddingDp + timeEndSpacingDp +
                    danmakuActionWidthDp * ((if (showDanmakuToggle) 2 else 0) + 1) +
                    (if (liquidGlassEnabled) layoutPolicy.playButtonSizeDp else 0)
                PlayerControlMiddleRow(
                    minimumLeftWidth = minimumLeftWidthDp.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    PlayerControlGlassDock(
                        enabled = true,
                        modifier = Modifier.then(
                            when {
                                isFullscreen -> Modifier.height(48.dp)
                                inlineTimeGlassDock -> Modifier.heightIn(min = 40.dp)
                                else -> Modifier
                            },
                        ),
                    ) {
                        Row(
                            modifier = when {
                                isFullscreen -> Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp)
                                inlineTimeGlassDock -> Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .heightIn(min = 28.dp)
                                else -> Modifier
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false)
                                    .horizontalScroll(rememberScrollState())
                                    .testTag("player_time_viewport"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isFullscreen && uiLayoutWidthDp >= 840 && showNextEpisodeButton) {
                                    AppIconButton(
                                        onClick = onNextEpisodeClick,
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = "下一集",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }

                                ProgressTimeText(
                                    positionProvider = displayedPositionProvider,
                                    durationProvider = { progressProvider().duration },
                                    fontSp = layoutPolicy.timeFontSp,
                                )
                            }

                            if (timeEndSpacingDp > 0) {
                                Spacer(modifier = Modifier.width(timeEndSpacingDp.dp))
                            }

                            if (showDanmakuToggle) {
                                val danmakuActiveColor = AppContentColors.selectionAccent(
                                    LocalAppThemeConfig.current.liquidGlassEnabled,
                                )
                                val danmakuInactiveColor = Color.White.copy(alpha = 0.74f)
                                NativeDanmakuToggleButton(
                                    enabled = danmakuEnabled,
                                    onToggle = onDanmakuToggle,
                                    activeTint = danmakuActiveColor,
                                    inactiveTint = danmakuInactiveColor,
                                    iconSize = layoutPolicy.danmakuIconSizeDp.dp,
                                    modifier = Modifier.width(danmakuActionWidthDp.dp)
                                        .then(if (isFullscreen) Modifier.height(48.dp) else Modifier)
                                        .testTag("player_danmaku_toggle"),
                                )

                                AppIconButton(
                                    onClick = onDanmakuSettingsClick,
                                    modifier = Modifier.width(danmakuActionWidthDp.dp)
                                        .then(if (isFullscreen) Modifier.height(48.dp) else Modifier)
                                        .testTag("player_danmaku_settings"),
                                ) {
                                    com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSettingsIcon(
                                        tint = Color.White.copy(alpha = 0.9f),
                                        size = if (isFullscreen) 22.dp else layoutPolicy.danmakuIconSizeDp.dp,
                                    )
                                }

                            }
                            AppIconButton(
                                onClick = onDanmakuInputClick,
                                modifier = Modifier.width(danmakuActionWidthDp.dp)
                                    .then(if (isFullscreen) Modifier.height(48.dp) else Modifier)
                                    .semantics {
                                        contentDescription = if (isLoggedIn) "发弹幕" else "登录发弹幕"
                                    }
                                    .testTag("player_danmaku_send"),
                            ) {
                                com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSendIcon(
                                    tint = Color.White.copy(alpha = if (isLoggedIn) 1f else 0.6f),
                                    size = layoutPolicy.danmakuIconSizeDp.dp,
                                )
                            }
                        }
                    }

                    // Right: Function Buttons
                    PlayerControlGlassDock(
                        enabled = true,
                        modifier = if (isFullscreen) Modifier.height(48.dp) else Modifier,
                    ) {
                        LandscapeDockActionRow(
                            spacing = actionSpacing,
                            modifier = if (isFullscreen) Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp)
                            else Modifier,
                        ) {
                            if (showAspectRatioButton) {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(onClick = onRatioClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText(
                                        text = currentRatio.displayName,
                                        color = Color.White,
                                        fontSize = layoutPolicy.actionTextFontSp.sp,
                                        lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else if (showAudioQualityButtonInline) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = layoutPolicy.playButtonSizeDp.dp)
                                        .clickable(onClick = onAudioQualityClick),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // The format badge already names the selected audio quality.
                                    when {
                                        isHiResAudioSelected -> HiResBadge()
                                        isDolbyAudioSelected -> DolbyBadge()
                                        else -> AppText(
                                            text = currentAudioQualityLabel.ifBlank { "音质" },
                                            color = Color.White,
                                            fontSize = layoutPolicy.actionTextFontSp.sp,
                                            lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Quality
                            if (currentQualityLabel.isNotEmpty()) {
                                AppText(
                                    text = currentQualityLabel,
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .then(if (isFullscreen) Modifier.heightIn(min = 48.dp) else Modifier)
                                        .clickable(onClick = onQualityClick)
                                        .then(if (isFullscreen) Modifier.wrapContentSize(Alignment.Center) else Modifier)
                                )
                            }

                            if (showEpisodeButton) {
                                AppText(
                                    text = "分集",
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .then(if (isFullscreen) Modifier.heightIn(min = 48.dp) else Modifier)
                                        .clickable(onClick = onEpisodeClick)
                                        .then(if (isFullscreen) Modifier.wrapContentSize(Alignment.Center) else Modifier)
                                )
                            }

                            // Speed
                            AppText(
                                text = if (currentSpeed == 1.0f) "倍速" else "${currentSpeed}x",
                                color = if (currentSpeed == 1.0f) Color.White else AppContentColors.accent(),
                                fontSize = layoutPolicy.actionTextFontSp.sp,
                                lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .then(if (isFullscreen) Modifier.heightIn(min = 48.dp) else Modifier)
                                    .clickable(onClick = onSpeedClick)
                                    .then(if (isFullscreen) Modifier.wrapContentSize(Alignment.Center) else Modifier)
                            )

                            if (isFullscreen && uiLayoutWidthDp >= 840 && showPlaybackOrderLabel) {
                                AppText(
                                    text = playbackOrderLabel,
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(onClick = onPlaybackOrderClick)
                                        .wrapContentSize(Alignment.Center),
                                )
                            }

                            if (showSubtitleButton) {
                                AppText(
                                    text = "字幕",
                                    color = if (subtitleEnabled) AppContentColors.accent() else Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            val nextShowSubtitlePanel = !showSubtitlePanel
                                            com.android.purebilibili.core.util.Logger.d(
                                                "BottomControlBar",
                                                "字幕按钮点击: nextShow=$nextShowSubtitlePanel, fullscreen=$isFullscreen, showMore=$showMoreActionsPanel, subtitleEnabled=$subtitleEnabled"
                                            )
                                            showSubtitlePanel = nextShowSubtitlePanel
                                            if (nextShowSubtitlePanel) {
                                                showMoreActionsPanel = false
                                                showVideoEnhancementPanel = false
                                            }
                                        }
                                        .wrapContentSize(Alignment.Center)
                                )
                            }

                            if (isFullscreen && uiLayoutWidthDp >= 840) {
                                LandscapeDockTextAction(
                                    text = "画中画",
                                    fontSp = layoutPolicy.actionTextFontSp,
                                    onClick = onPipClick,
                                )
                            }

                            if (showDirectTools) {
                                LandscapeDockTextAction(
                                    text = "音质",
                                    selected = isSpecialAudioSelected,
                                    fontSp = layoutPolicy.actionTextFontSp,
                                    onClick = onAudioQualityClick,
                                )
                                if (anime4kAvailable) {
                                    LandscapeDockTextAction(
                                        text = "画质增强",
                                        selected = anime4kEnabled,
                                        fontSp = layoutPolicy.actionTextFontSp,
                                        onClick = {
                                            showSubtitlePanel = false
                                            showMoreActionsPanel = false
                                            showVideoEnhancementPanel = true
                                        },
                                    )
                                }
                                LandscapeDockTextAction(
                                    text = "竖屏",
                                    fontSp = layoutPolicy.actionTextFontSp,
                                    onClick = onPortraitFullscreen,
                                )
                            }

                            if (showMoreActionsButton || sponsorContributionAvailable || isFullscreen) {
                                // The popup inherits the fixed control-row density; restore the system
                                // font scale for menu items and keep only the "更多" label fixed.
                                CompositionLocalProvider(LocalDensity provides chromeDensity) {
                                AppWindowActionMenu(
                                    // 其它文字项与右侧退全屏按钮都紧贴内容，而 IconButton 的默认
                                    // 最小槽位 40dp 会给“更多”两侧各留约 8dp，看起来比相邻项宽。
                                    // 这里把它收到文字宽（两个字 ≈ 2×字号）再加一点余量：
                                    // 视觉间距与相邻项一致，也不额外占用这一排的可用宽度。
                                    modifier = Modifier.width(
                                        (layoutPolicy.actionTextFontSp * 2 + 2).dp,
                                    ),
                                    groups = listOf(
                                        listOfNotNull(
                                            if (sponsorContributionAvailable) {
                                                AppWindowAction(
                                                    label = if (sponsorContributionMarking) "结束标记片段" else "标记片段起点",
                                                    selected = sponsorContributionMarking,
                                                    onClick = {
                                                        showMoreActionsPanel = false
                                                        onSponsorContributionMarkBoundary()
                                                    },
                                                )
                                            } else null,
                                            if (sponsorContributionAvailable && !sponsorContributionMarking) {
                                                AppWindowAction(label = "标记整段恰饭", onClick = {
                                                    showMoreActionsPanel = false
                                                    onSponsorContributionMarkWholeVideo()
                                                })
                                            } else null,
                                            if (sponsorContributionAvailable && sponsorContributionMarking) {
                                                AppWindowAction(label = "取消片段标记", onClick = {
                                                    showMoreActionsPanel = false
                                                    onSponsorContributionCancel()
                                                })
                                            } else null,
                                            if (showEpisodeInMoreActions) {
                                                AppWindowAction(label = "分集", onClick = {
                                                        showMoreActionsPanel = false
                                                        onEpisodeClick()
                                                    })
                                            } else null,
                                            if (showNextEpisodeButton && !(isFullscreen && uiLayoutWidthDp >= 840)) {
                                                AppWindowAction(label = "下集", onClick = {
                                                        showMoreActionsPanel = false
                                                        onNextEpisodeClick()
                                                    })
                                            } else null,
                                            if (showPlaybackOrderLabel && !(isFullscreen && uiLayoutWidthDp >= 840)) {
                                                AppWindowAction(label = playbackOrderLabel, selected = playbackOrderLabel != "自动连播", onClick = {
                                                        showMoreActionsPanel = false
                                                        onPlaybackOrderClick()
                                                    })
                                            } else null,
                                            if (!showDirectTools) AppWindowAction(
                                                label = if (currentAudioQualityLabel.isBlank() || currentAudioQualityLabel == "音质") {
                                                    "音质"
                                                } else {
                                                    "音质 · $currentAudioQualityLabel"
                                                },
                                                selected = isHiResAudioSelected || isDolbyAudioSelected,
                                                onClick = {
                                                    showMoreActionsPanel = false
                                                    onAudioQualityClick()
                                                }
                                            ) else null,
                                            if (showPortraitSwitchButton && !showDirectTools) {
                                                AppWindowAction(label = "竖屏", onClick = {
                                                        showMoreActionsPanel = false
                                                        onPortraitFullscreen()
                                                    })
                                            } else null,
                                            if (anime4kAvailable && !showDirectTools) {
                                                AppWindowAction(label = "画质增强", selected = anime4kEnabled, onClick = {
                                                        showMoreActionsPanel = false
                                                        showVideoEnhancementPanel = true
                                                    })
                                            } else null,
                                            if (cdnDiagnosticsAvailable) {
                                                AppWindowAction(label = "CDN 测速与线路", onClick = {
                                                    showMoreActionsPanel = false
                                                    onCdnDiagnosticsClick()
                                                })
                                            } else null,
                                            if (onPlayerSettingsClick != null) AppWindowAction(label = "播放器设置", onClick = {
                                                showMoreActionsPanel = false
                                                onPlayerSettingsClick()
                                            }) else null,
                                            if (viewPoints.isNotEmpty()) {
                                                AppWindowAction(label = "章节列表", onClick = {
                                                    showMoreActionsPanel = false
                                                    onChapterClick()
                                                })
                                            } else null
                                        )
                                    ),
                                    onExpandedChange = { expanded -> showMoreActionsPanel = expanded },
                                    content = {
                                        CompositionLocalProvider(LocalDensity provides controlRowDensity) {
                                        AppText(
                                            text = "更多",
                                            color = if (showMoreActionsPanel) AppContentColors.accent() else Color.White,
                                            fontSize = layoutPolicy.actionTextFontSp.sp,
                                            lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                            fontWeight = FontWeight.SemiBold,
                                            // Keep the label on one line so the overflow action is never
                                            // split as “更”/“多” in landscape.
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.padding(vertical = layoutPolicy.actionChipVerticalPaddingDp.dp)
                                        )
                                        }
                                    }
                                )
                                }
                            }

                            // 📱 [修复] 竖屏全屏按钮 - 仅在非全屏且有需要时显示，避免挤压平板控制栏
                            if (showPortraitSwitchButtonInline) {
                                AppText(
                                    text = "竖屏",
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .then(if (isFullscreen) Modifier.heightIn(min = 48.dp) else Modifier)
                                        .clickable(onClick = onPortraitFullscreen)
                                        .then(if (isFullscreen) Modifier.wrapContentSize(Alignment.Center) else Modifier)
                                )
                            }
                        }
                    }
                }
            } else {
                // Inline controls: play/pause stays at the bottom-left, then time and danmaku.
                Row(
                    modifier = Modifier
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerPlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPauseClick,
                        sizeDp = layoutPolicy.playButtonSizeDp,
                        iconSizeDp = layoutPolicy.playIconSizeDp,
                    )
                    Spacer(modifier = Modifier.width(layoutPolicy.afterPlaySpacingDp.dp))
                    val showDanmakuToggle = shouldShowDanmakuToggleInControlBar(
                        isFullscreen = isFullscreen,
                        widthDp = uiLayoutWidthDp
                    )
                    PlayerControlGlassDock(
                        enabled = true,
                        modifier = when {
                            inlineTimeGlassDock -> Modifier.heightIn(min = 40.dp)
                            else -> Modifier
                        },
                    ) {
                        Row(
                            modifier = when {
                                inlineTimeGlassDock -> Modifier
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .heightIn(min = 28.dp)
                                else -> Modifier
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProgressTimeText(
                                positionProvider = displayedPositionProvider,
                                durationProvider = { progressProvider().duration },
                                fontSp = layoutPolicy.timeFontSp,
                            )

                            if (!inlineTimeGlassDock || showDanmakuToggle) {
                                Spacer(modifier = Modifier.width(layoutPolicy.afterTimeSpacingDp.dp))
                            }

                            if (showDanmakuToggle) {
                                // 弹幕开启态跟随选中主题色：玻璃模式取玻璃强调色、原生模式取主题主色，
                                // 与竖屏底部栏的弹幕开关共用 selectionAccent 入口。
                                val danmakuActiveColor = AppContentColors.selectionAccent(
                                    LocalAppThemeConfig.current.liquidGlassEnabled,
                                )
                                val danmakuInactiveColor = Color.White.copy(alpha = 0.74f)
                                NativeDanmakuToggleButton(
                                    enabled = danmakuEnabled,
                                    onToggle = onDanmakuToggle,
                                    activeTint = danmakuActiveColor,
                                    inactiveTint = danmakuInactiveColor,
                                    iconSize = layoutPolicy.danmakuIconSizeDp.dp,
                                )

                                AppIconButton(
                                    onClick = onDanmakuSettingsClick,
                                    modifier = Modifier
                                        .testTag("player_danmaku_settings")
                                        .semantics { contentDescription = "弹幕设置" },
                                ) {
                                    com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSettingsIcon(
                                        tint = Color.White.copy(alpha = 0.9f),
                                        size = layoutPolicy.danmakuIconSizeDp.dp,
                                    )
                                }

                            }
                        }
                    }
                    if (showDanmakuToggle) {
                        if (showCompactDanmakuSend) {
                            Spacer(modifier = Modifier.width(layoutPolicy.danmakuSwitchToInputSpacingDp.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Color.White.copy(alpha = 0.16f),
                                    )
                                    .clickable(onClick = onDanmakuInputClick)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    text = if (isLoggedIn) "发弹幕" else "登录发弹幕",
                                    color = Color.White.copy(alpha = if (isLoggedIn) 0.62f else 0.44f),
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(layoutPolicy.afterInputSpacingDp.dp))
                            Spacer(modifier = Modifier.weight(1f))
                        } else if (!expandInlineActionDock) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else if (!expandInlineActionDock) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (expandInlineActionDock) {
                        Spacer(modifier = Modifier.width(PlayerChromeLayout.ActionGapDp.dp))
                    }

                    // Right: Function Buttons
                    PlayerControlGlassDock(
                        enabled = true,
                        modifier = if (expandInlineActionDock) Modifier.weight(1f) else Modifier,
                    ) {
                        InlineDockActionRow(
                            fillAvailableWidth = expandInlineActionDock,
                            modifier = if (expandInlineActionDock) Modifier
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 8.dp)
                            else Modifier,
                            minimumSpacing = when {
                                expandInlineActionDock -> 4.dp
                                else -> layoutPolicy.rightActionSpacingDp.dp
                            },
                        ) {
                            if (showAspectRatioButton) {
                                Box(
                                    modifier = Modifier
                                        .sizeIn(
                                            minWidth = InlineDockActionMinWidth,
                                            minHeight = 48.dp,
                                        )
                                        .clickable(onClick = onRatioClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText(
                                        text = currentRatio.displayName,
                                        color = Color.White,
                                        fontSize = layoutPolicy.actionTextFontSp.sp,
                                        lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            } else if (showAudioQualityButtonInline) {
                                Row(
                                    modifier = Modifier
                                        .heightIn(min = layoutPolicy.playButtonSizeDp.dp)
                                        .clickable(onClick = onAudioQualityClick),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // The format badge already names the selected audio quality.
                                    when {
                                        isHiResAudioSelected -> HiResBadge()
                                        isDolbyAudioSelected -> DolbyBadge()
                                        else -> AppText(
                                            text = currentAudioQualityLabel.ifBlank { "音质" },
                                            color = Color.White,
                                            fontSize = layoutPolicy.actionTextFontSp.sp,
                                            lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Quality
                            if (currentQualityLabel.isNotEmpty()) {
                                AppText(
                                    text = currentQualityLabel,
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable(onClick = onQualityClick)
                                )
                            }

                            if (showEpisodeButton) {
                                AppText(
                                    text = "分集",
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clickable(onClick = onEpisodeClick)
                                )
                            }

                            // Speed
                            AppText(
                                text = if (currentSpeed == 1.0f) "倍速" else "${currentSpeed}x",
                                color = if (currentSpeed == 1.0f) Color.White else AppContentColors.accent(),
                                fontSize = layoutPolicy.actionTextFontSp.sp,
                                lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .clickable(onClick = onSpeedClick)
                            )

                            if (showSubtitleButton) {
                                AppText(
                                    text = "字幕",
                                    color = if (subtitleEnabled) AppContentColors.accent() else Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .sizeIn(
                                            minWidth = InlineDockActionMinWidth,
                                            minHeight = 48.dp,
                                        )
                                        .clickable {
                                            val nextShowSubtitlePanel = !showSubtitlePanel
                                            com.android.purebilibili.core.util.Logger.d(
                                                "BottomControlBar",
                                                "字幕按钮点击: nextShow=$nextShowSubtitlePanel, fullscreen=$isFullscreen, showMore=$showMoreActionsPanel, subtitleEnabled=$subtitleEnabled"
                                            )
                                            showSubtitlePanel = nextShowSubtitlePanel
                                            if (nextShowSubtitlePanel) {
                                                showMoreActionsPanel = false
                                                showVideoEnhancementPanel = false
                                            }
                                        }
                                        .wrapContentSize(Alignment.Center)
                                )
                            }

                            if (showMoreActionsButton || sponsorContributionAvailable || isFullscreen) {
                                AppWindowActionMenu(
                                    groups = listOf(
                                        listOfNotNull(
                                            if (sponsorContributionAvailable) {
                                                AppWindowAction(
                                                    label = if (sponsorContributionMarking) "结束标记片段" else "标记片段起点",
                                                    selected = sponsorContributionMarking,
                                                    onClick = {
                                                        showMoreActionsPanel = false
                                                        onSponsorContributionMarkBoundary()
                                                    },
                                                )
                                            } else null,
                                            if (sponsorContributionAvailable && !sponsorContributionMarking) {
                                                AppWindowAction(label = "标记整段恰饭", onClick = {
                                                    showMoreActionsPanel = false
                                                    onSponsorContributionMarkWholeVideo()
                                                })
                                            } else null,
                                            if (sponsorContributionAvailable && sponsorContributionMarking) {
                                                AppWindowAction(label = "取消片段标记", onClick = {
                                                    showMoreActionsPanel = false
                                                    onSponsorContributionCancel()
                                                })
                                            } else null,
                                            if (showEpisodeInMoreActions) {
                                                AppWindowAction(label = "分集", onClick = {
                                                        showMoreActionsPanel = false
                                                        onEpisodeClick()
                                                    })
                                            } else null,
                                            if (showNextEpisodeButton && !(isFullscreen && uiLayoutWidthDp >= 840)) {
                                                AppWindowAction(label = "下集", onClick = {
                                                        showMoreActionsPanel = false
                                                        onNextEpisodeClick()
                                                    })
                                            } else null,
                                            if (showPlaybackOrderLabel) {
                                                AppWindowAction(label = playbackOrderLabel, selected = playbackOrderLabel != "自动连播", onClick = {
                                                        showMoreActionsPanel = false
                                                        onPlaybackOrderClick()
                                                    })
                                            } else null,
                                            if (!showDirectTools) AppWindowAction(
                                                label = if (currentAudioQualityLabel.isBlank() || currentAudioQualityLabel == "音质") {
                                                    "音质"
                                                } else {
                                                    "音质 · $currentAudioQualityLabel"
                                                },
                                                selected = isHiResAudioSelected || isDolbyAudioSelected,
                                                onClick = {
                                                    showMoreActionsPanel = false
                                                    onAudioQualityClick()
                                                }
                                            ) else null,
                                            if (showPortraitSwitchButton && !showDirectTools) {
                                                AppWindowAction(label = "竖屏", onClick = {
                                                        showMoreActionsPanel = false
                                                        onPortraitFullscreen()
                                                    })
                                            } else null,
                                            if (anime4kAvailable && !showDirectTools) {
                                                AppWindowAction(label = "画质增强", selected = anime4kEnabled, onClick = {
                                                        showMoreActionsPanel = false
                                                        showVideoEnhancementPanel = true
                                                    })
                                            } else null,
                                            if (cdnDiagnosticsAvailable) {
                                                AppWindowAction(label = "CDN 测速与线路", onClick = {
                                                    showMoreActionsPanel = false
                                                    onCdnDiagnosticsClick()
                                                })
                                            } else null,
                                            if (onPlayerSettingsClick != null) AppWindowAction(label = "播放器设置", onClick = {
                                                showMoreActionsPanel = false
                                                onPlayerSettingsClick()
                                            }) else null,
                                            if (viewPoints.isNotEmpty()) {
                                                AppWindowAction(label = "章节列表", onClick = {
                                                    showMoreActionsPanel = false
                                                    onChapterClick()
                                                })
                                            } else null
                                        )
                                    ),
                                    onExpandedChange = { expanded -> showMoreActionsPanel = expanded },
                                    content = {
                                        AppText(
                                            text = "更多",
                                            color = if (showMoreActionsPanel) AppContentColors.accent() else Color.White,
                                            fontSize = layoutPolicy.actionTextFontSp.sp,
                                            lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                            fontWeight = FontWeight.SemiBold,
                                            // Keep the label on one line so the overflow action is never
                                            // split as “更”/“多” in landscape.
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.padding(vertical = layoutPolicy.actionChipVerticalPaddingDp.dp)
                                        )
                                    }
                                )
                            }

                            // 📱 [修复] 竖屏全屏按钮 - 仅在非全屏且有需要时显示，避免挤压平板控制栏
                            if (showPortraitSwitchButtonInline) {
                                AppText(
                                    text = "竖屏",
                                    color = Color.White,
                                    fontSize = layoutPolicy.actionTextFontSp.sp,
                                    lineHeight = (layoutPolicy.actionTextFontSp + 2).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clickable(onClick = onPortraitFullscreen)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(if (isFullscreen) 8.dp else layoutPolicy.rightActionSpacingDp.dp))
            // Fullscreen
            PlayerControlGlassDock(enabled = true, shape = androidx.compose.foundation.shape.CircleShape) {
                Box(
                    modifier = Modifier
                        .size(fullscreenToggleTouchTargetDp.dp)
                        .testTag("player_fullscreen_toggle")
                        .consumeTap(onToggleFullscreen),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        contentDescription = if (isFullscreen) "退出横屏" else "横屏",
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.fullscreenIconSizeDp.dp)
                    )
                }
            }
        }
        }
        if (progressPlacement == PlayerProgressPlacement.BOTTOM_EDGE) {
            Spacer(modifier = Modifier.height(layoutPolicy.progressSpacingDp.dp))
            progressBarContent()
        }
    }

    if (showSubtitlePanel && showSubtitleButton && shouldConsumeFloatingPanelBackground) {
        FloatingControlPanelDialog(
            onDismissRequest = { showSubtitlePanel = false },
            panelModifier = Modifier
                .padding(
                    end = layoutPolicy.horizontalPaddingDp.dp,
                    bottom = floatingPanelBottomOffsetDp.dp
                )
        ) {
            AppSurface(
                color = Color.Black.copy(alpha = 0.76f),
                shape = AppShapes.container(ContainerLevel.Card),
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 140.dp, max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppText(
                        text = "字幕显示",
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                    subtitleOptions.forEach { option ->
                        SubtitlePanelOption(
                            label = option.label,
                            selected = subtitleDisplayMode == option.mode,
                            enabled = option.enabled,
                            minWidthDp = 80,
                            onClick = {
                                if (!option.enabled) return@SubtitlePanelOption
                                com.android.purebilibili.core.util.Logger.d(
                                    "BottomControlBar",
                                    "字幕选项点击: mode=${option.mode}, label=${option.label}"
                                )
                                showSubtitlePanel = false
                                onSubtitleDisplayModeChange(option.mode)
                            }
                        )
                    }
                    if (subtitlePanelTrackOptions.isNotEmpty()) {
                        AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        AppText(
                            text = "字幕轨道",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                        )
                        subtitlePanelTrackOptions.forEach { option ->
                            SubtitlePanelOption(
                                label = option.label,
                                selected = option.selected,
                                enabled = true,
                                minWidthDp = 96,
                                onClick = {
                                    showSubtitlePanel = false
                                    onSubtitleTrackSelected(option.trackKey)
                                    onSubtitleDisplayModeChange(SubtitleDisplayMode.PRIMARY_ONLY)
                                }
                            )
                        }
                    }
                    if (subtitleOptions.size > 1) {
                        AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            AppText(
                                text = "大字号",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            AppSwitch(
                                checked = subtitleLargeTextEnabled,
                                onCheckedChange = onSubtitleLargeTextChange,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        AppText(
                            text = "锁定位置",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        AppSwitch(
                            checked = subtitlePositionLocked,
                            onCheckedChange = onSubtitlePositionLockedChange,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }

    if (showVideoEnhancementPanel && anime4kAvailable) {
        FloatingControlPanelDialog(
            onDismissRequest = { showVideoEnhancementPanel = false },
            panelModifier = Modifier
                .padding(
                    end = moreActionsPanelEndPaddingDp.dp,
                    bottom = floatingPanelBottomOffsetDp.dp
                )
        ) {
            VideoEnhancementSettingsPanel(
                enabled = anime4kEnabled,
                algorithm = videoEnhancementAlgorithm,
                preset = anime4kPreset,
                fsrSharpness = fsrSharpness,
                minWidthDp = maxOf(220, floatingPanelMinWidthDp),
                maxHeightDp = videoEnhancementPanelMaxHeightDp,
                onCheckedChange = onAnime4kToggle,
                onAlgorithmChange = onVideoEnhancementAlgorithmChange,
                onPresetChange = onAnime4kPresetChange,
                onFsrSharpnessChange = onFsrSharpnessChange
            )
        }
    }
}

/**
 * Keep the two middle docks at their measured content widths. Unused width belongs between
 * them, not inside the action dock. Time and right actions scroll without moving the icon cluster.
 */
@Composable
private fun PlayerControlMiddleRow(
    modifier: Modifier = Modifier,
    minimumLeftWidth: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = 8.dp.roundToPx().coerceAtMost(constraints.maxWidth)
        val left = measurables[0].measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = maxOf((constraints.maxWidth * 0.4f).toInt(), minimumLeftWidth.roundToPx())
                    .coerceAtMost((constraints.maxWidth - gap).coerceAtLeast(0)),
            ),
        )
        val right = measurables[1].measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (constraints.maxWidth - left.width - gap).coerceAtLeast(0),
            ),
        )
        val height = constraints.constrainHeight(maxOf(left.height, right.height))
        layout(constraints.maxWidth, height) {
            left.placeRelative(0, (height - left.height) / 2)
            right.placeRelative(constraints.maxWidth - right.width, (height - right.height) / 2)
        }
    }
}

@Composable
private fun PlayerPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    sizeDp: Int,
    iconSizeDp: Int,
) {
    PlayerControlGlassDock(enabled = true, shape = CircleShape) {
        AppIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(sizeDp.dp)
                .testTag("player_play_pause"),
        ) {
            AppIcon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(iconSizeDp.dp),
            )
        }
    }
}

/**
 * 横屏功能组：两种材质共用两个中文字号的固定间隔，按内容宽度靠右显示，
 * 放不下时横向滚动，不再拉伸填满底栏。
 */
@Composable
private fun LandscapeDockActionRow(
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Preserve the existing compact inline action slots; fullscreen uses content-width actions. */
private val InlineDockActionMinWidth = 40.dp

@Composable
private fun InlineDockActionRow(
    fillAvailableWidth: Boolean,
    minimumSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val arrangement = remember(fillAvailableWidth, minimumSpacing) {
        if (fillAvailableWidth) {
            object : Arrangement.Horizontal {
                // Reserve the minimum gap when measuring overflowing content, then
                // distribute any remaining viewport width between the actions.
                override val spacing: Dp = minimumSpacing

                override fun Density.arrange(
                    totalSize: Int,
                    sizes: IntArray,
                    layoutDirection: LayoutDirection,
                    outPositions: IntArray,
                ) {
                    with(Arrangement.SpaceBetween) {
                        arrange(totalSize, sizes, layoutDirection, outPositions)
                    }
                }
            }
        } else Arrangement.spacedBy(minimumSpacing)
    }
    // 只修正 Miuix 液态玻璃胶囊：外层的最小高度可能高于文字行，
    // 让整行在胶囊内居中；关闭玻璃后的原生布局与 MD3 默认行为保持不变。
    val centerGlassActions = LocalAppUiStyle.current == AppUiStyle.MIUIX &&
        LocalAppThemeConfig.current.liquidGlassEnabled
    BoxWithConstraints(
        contentAlignment = if (centerGlassActions) Alignment.CenterStart else Alignment.TopStart,
        modifier = modifier
            .then(if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier)
            .then(if (fillAvailableWidth) Modifier.padding(horizontal = 2.dp) else Modifier),
    ) {
        Row(
            modifier = if (fillAvailableWidth) Modifier
                .horizontalScroll(rememberScrollState())
                // Scrolling removes the child's maximum width. Restore only the
                // viewport minimum so short rows fill it and long rows still scroll.
                .widthIn(min = maxWidth)
            else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = arrangement,
            content = content,
        )
    }
}

@Composable
private fun LandscapeDockTextAction(
    text: String,
    fontSp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    AppText(
        text = text,
        color = if (selected) AppContentColors.accent() else Color.White,
        fontSize = fontSp.sp,
        lineHeight = (fontSp + 2).sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .wrapContentSize(Alignment.Center),
    )
}

@Composable
private fun FloatingControlPanelDialog(
    onDismissRequest: () -> Unit,
    panelModifier: Modifier,
    panelAlignment: Alignment = Alignment.BottomEnd,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        ) {
            Box(
                modifier = Modifier
                    .align(panelAlignment)
                    .then(panelModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SubtitlePanelOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    minWidthDp: Int,
    onClick: () -> Unit
) {
    AppText(
        text = label,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.42f)
            selected -> AppContentColors.accent()
            else -> Color.White
        },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .widthIn(min = minWidthDp.dp)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun MoreActionTextButton(
    label: String,
    highlighted: Boolean = false,
    minWidthDp: Int,
    onClick: () -> Unit
) {
    AppText(
        text = label,
        color = if (highlighted) AppContentColors.accent() else Color.White,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .widthIn(min = minWidthDp.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun VideoEnhancementSettingsPanel(
    enabled: Boolean,
    algorithm: VideoEnhancementAlgorithm,
    preset: Anime4KPreset,
    fsrSharpness: Float,
    minWidthDp: Int,
    maxHeightDp: Int,
    onCheckedChange: (Boolean) -> Unit,
    onAlgorithmChange: (VideoEnhancementAlgorithm) -> Unit,
    onPresetChange: (Anime4KPreset) -> Unit,
    onFsrSharpnessChange: (Float) -> Unit
) {
    AppSurface(
        color = Color.Black.copy(alpha = 0.82f),
        shape = AppShapes.container(ContainerLevel.Card),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .width(minWidthDp.dp)
                .heightIn(max = maxHeightDp.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "画质增强",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    AppText(
                        text = if (enabled) "当前视频已开启" else "当前视频已关闭",
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                AppSwitch(
                    checked = enabled,
                    onCheckedChange = onCheckedChange
                )
            }

            AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            AppText(
                text = "增强算法",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoEnhancementAlgorithm.entries.forEach { option ->
                    VideoEnhancementChoice(
                        label = when (option) {
                            VideoEnhancementAlgorithm.ANIME4K -> "Anime4K\n动漫"
                            VideoEnhancementAlgorithm.FSR_1_0 -> "FSR 1.0\n通用"
                        },
                        selected = algorithm == option,
                        modifier = Modifier.weight(1f),
                        onClick = { onAlgorithmChange(option) }
                    )
                }
            }

            if (algorithm == VideoEnhancementAlgorithm.ANIME4K) {
                AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                AppText(
                    text = "Anime4K 模型",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Anime4KPreset.FAST, Anime4KPreset.QUALITY).forEach { option ->
                        VideoEnhancementChoice(
                            label = resolveAnime4KPresetLabel(option),
                            selected = preset == option,
                            modifier = Modifier.weight(1f),
                            onClick = { onPresetChange(option) }
                        )
                    }
                }
            } else {
                AppHorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "FSR 锐化",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    AppText(
                        text = "${(fsrSharpness.coerceIn(0f, 1f) * 100).roundToInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AppSlider(
                    value = fsrSharpness.coerceIn(0f, 1f),
                    onValueChange = onFsrSharpnessChange,
                    valueRange = 0f..1f,
                    steps = FSR_SHARPNESS_SLIDER_STEPS,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AppText(
                text = "算法与模型会沿用上次选择",
                color = Color.White.copy(alpha = 0.56f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun VideoEnhancementChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(AppShapes.container(ContainerLevel.Field))
            .background(
                if (selected) {
                    AppContentColors.accent().copy(alpha = 0.22f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = label,
            color = if (selected) AppContentColors.accent() else Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Reusing existing VideoProgressBar
 */
@Composable
fun VideoProgressBar(
    currentPosition: Long,
    displayPositionMs: Long,
    duration: Long,
    bufferedPosition: Long,
    isSeekScrubbing: Boolean,
    layoutPolicy: VideoProgressBarLayoutPolicy,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    viewPoints: List<com.android.purebilibili.data.model.response.ViewPoint> = emptyList(),
    sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
    pbpRidgeSamples: List<PbpRidgeSample> = emptyList(),
    currentChapter: String? = null,
    onChapterClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    displayPositionProvider: (() -> Long)? = null,
    neutralGlassTrack: Boolean = false,
) {
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var dragTargetPositionMs by remember { mutableLongStateOf(displayPositionMs.coerceAtLeast(0L)) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekDragStart by rememberUpdatedState(onSeekDragStart)
    val currentOnSeekDragUpdate by rememberUpdatedState(onSeekDragUpdate)
    val currentOnSeekDragCancel by rememberUpdatedState(onSeekDragCancel)
    LaunchedEffect(displayPositionMs, isSeekScrubbing) {
        if (!isSeekScrubbing) {
            dragTargetPositionMs = displayPositionMs.coerceAtLeast(0L)
        }
    }

    val uiSkinState = LocalUiSkinState.current
    val progressSkin = uiSkinState.activeSkin?.takeIf {
        uiSkinState.enabled && UiSkinSurface.PLAYER_PROGRESS in it.manifest.surfaces
    }
    val progressColors = progressSkin?.manifest?.colors
    val primaryColor = parseUiSkinColor(
        progressColors?.playerProgressActiveTint,
        // 与详情页选中态共用颜色入口，保留皮肤显式覆盖及原生主色。
        AppContentColors.selectionAccent(LocalAppThemeConfig.current.liquidGlassEnabled),
    )
    val bufferedTrackColor = if (neutralGlassTrack) Color.White.copy(alpha = 0.34f) else parseUiSkinColor(
        progressColors?.playerProgressBufferedTint,
        Color.White.copy(alpha = 0.42f),
    )
    val inactiveTrackColor = if (neutralGlassTrack) Color.White.copy(alpha = 0.26f) else parseUiSkinColor(
        progressColors?.playerProgressTrackTint,
        Color.White.copy(alpha = 0.24f),
    )
    val progressThumbPath = uiSkinState.assetPath(UiSkinSurface.PLAYER_PROGRESS) { assets ->
        if (isSeekScrubbing) {
            assets.playerProgressDraggingIcon ?: assets.playerProgressIcon ?: assets.playerProgressStaticIcon
        } else {
            assets.playerProgressIcon ?: assets.playerProgressStaticIcon
        }
    }
    val activePositionProvider: () -> Long = {
        resolveSeekPreviewTargetPositionMs(
            displayPositionMs = displayPositionProvider?.invoke() ?: displayPositionMs,
            dragTargetPositionMs = dragTargetPositionMs,
            isSeekScrubbing = isSeekScrubbing
        )
    }
    val bufferedProgress = resolveProgressFraction(
        positionMs = bufferedPosition,
        durationMs = duration
    )
    val resolvedSponsorMarkers = remember(duration, sponsorMarkers) {
        resolveSponsorProgressBarMarkers(
            durationMs = duration,
            markers = sponsorMarkers
        )
    }
    val baseHeightDp = if (currentChapter != null) {
        layoutPolicy.baseHeightWithChapterDp.dp
    } else {
        layoutPolicy.baseHeightWithoutChapterDp.dp
    }
    val previewImageHeightDp = if (videoshotData?.isValid == true) {
        resolveSeekPreviewBubbleHeightDp(LocalConfiguration.current.screenWidthDp)
    } else null
    val previewAreaHeightDp = if (isSeekScrubbing) {
        resolveVideoProgressPreviewAreaHeightDp(
            layoutPolicy = layoutPolicy,
            hasChapter = currentChapter != null,
            previewImageHeightDp = previewImageHeightDp
        ).dp
    } else 0.dp
    val thumbSizeDp = if (isSeekScrubbing) {
        layoutPolicy.thumbDraggingSizeDp.dp
    } else {
        layoutPolicy.thumbIdleSizeDp.dp
    }
    val thumbSizePx = with(LocalDensity.current) { thumbSizeDp.toPx() }
    val trackHeightPx = with(LocalDensity.current) { layoutPolicy.trackHeightDp.dp.toPx() }

    // Seek gestures must wrap chapter + track. A track-sibling pointerInput loses DOWN to the
    // chapter clickable (full-width + 48dp min touch), which is why chapter videos cannot scrub.
    val seekGestureHost = resolveVideoProgressBarSeekGestureHost(
        hasChapterLabel = currentChapter != null
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(baseHeightDp + previewAreaHeightDp)
    ) {
        if (isSeekScrubbing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewAreaHeightDp)
                    .padding(bottom = layoutPolicy.previewBottomPaddingDp.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                ProgressSeekPreview(
                    videoshotData = videoshotData,
                    positionProvider = activePositionProvider,
                    currentPosition = currentPosition,
                    duration = duration,
                    thumbCenterXPx = containerWidthPx * resolveProgressFraction(
                        positionMs = activePositionProvider(),
                        durationMs = duration
                    ),
                    containerWidthPx = containerWidthPx
                )

            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(baseHeightDp)
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .then(
                    if (seekGestureHost == VideoProgressBarSeekGestureHost.SharedOuterContainer) {
                        Modifier
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val targetPositionMs = resolveSeekPositionFromTouch(
                                        touchX = offset.x,
                                        containerWidthPx = size.width.toFloat(),
                                        durationMs = duration
                                    )
                                    dragTargetPositionMs = targetPositionMs
                                    currentOnSeekStart()
                                    currentOnSeekDragStart(targetPositionMs)
                                    currentOnSeekDragUpdate(targetPositionMs)
                                    currentOnSeek(targetPositionMs)
                                }
                            }
                            .pointerInput(duration) {
                                var dragInProgress = false
                                try {
                                    var dragStartPositionMs = displayPositionMs.coerceAtLeast(0L)
                                    var latestDragPositionMs = dragStartPositionMs
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val targetPositionMs = resolveSeekPositionFromTouch(
                                                touchX = offset.x,
                                                containerWidthPx = size.width.toFloat(),
                                                durationMs = duration
                                            )
                                            dragInProgress = true
                                            dragStartPositionMs = targetPositionMs
                                            latestDragPositionMs = targetPositionMs
                                            dragTargetPositionMs = targetPositionMs
                                            currentOnSeekStart()
                                            currentOnSeekDragStart(targetPositionMs)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val targetPositionMs = resolveSeekPositionFromTouch(
                                                touchX = change.position.x,
                                                containerWidthPx = size.width.toFloat(),
                                                durationMs = duration
                                            )
                                            latestDragPositionMs = targetPositionMs
                                            dragTargetPositionMs = targetPositionMs
                                            currentOnSeekDragUpdate(targetPositionMs)
                                        },
                                        onDragEnd = {
                                            val commitPositionMs = resolveSeekDragCommitPositionMs(
                                                dragStartPositionMs = dragStartPositionMs,
                                                latestDragPositionMs = latestDragPositionMs
                                            )
                                            dragInProgress = false
                                            currentOnSeek(commitPositionMs)
                                        },
                                        onDragCancel = {
                                            dragInProgress = false
                                            currentOnSeekDragCancel()
                                        }
                                    )
                                } finally {
                                    if (shouldCancelSeekDragOnPointerInputCompletion(dragInProgress)) {
                                        currentOnSeekDragCancel()
                                    }
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                if (currentChapter != null) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .pointerInput(currentChapter) {
                                    detectTapGestures { onChapterClick() }
                                }
                                .padding(
                                    bottom = layoutPolicy.chapterBottomPaddingDp.dp,
                                    start = layoutPolicy.chapterStartPaddingDp.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                Icons.Outlined.ViewList,
                                contentDescription = "Chapter",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(layoutPolicy.chapterIconSizeDp.dp)
                            )
                            Spacer(modifier = Modifier.width(layoutPolicy.chapterSpacingDp.dp))
                            AppText(
                                text = currentChapter,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = layoutPolicy.chapterFontSp.sp,
                                lineHeight = layoutPolicy.chapterFontSp.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layoutPolicy.touchContainerHeightDp.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Spacer(
                        Modifier.fillMaxWidth()
                            .height(layoutPolicy.touchContainerHeightDp.dp)
                            .drawWithCache {
                                val trackTop = ((size.height - trackHeightPx) / 2f).coerceAtLeast(0f)
                                val centerY = trackTop + trackHeightPx / 2f
                                val ridgeHeight = (size.height * 0.42f).coerceAtMost(18.dp.toPx())
                                val ridgePoints = pbpRidgeSamples.map { sample ->
                                    val intensity = when (sample.density) {
                                        PbpRidgeDensity.QUIET -> sample.intensity * 0.78f
                                        PbpRidgeDensity.NORMAL -> sample.intensity
                                        PbpRidgeDensity.HOT -> sample.intensity * 1.14f
                                    }.coerceIn(0f, 1f)
                                    Offset(size.width * sample.fraction.coerceIn(0f, 1f), centerY - ridgeHeight * intensity)
                                }
                                val ridgePath = Path().apply {
                                    moveTo(0f, centerY)
                                    ridgePoints.forEach { lineTo(it.x, it.y) }
                                    lineTo(size.width, centerY)
                                    close()
                                }
                                val ridgeLinePath = Path().apply {
                                    ridgePoints.forEachIndexed { index, point ->
                                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                    }
                                }
                                val hotSegments = (0 until (ridgePoints.size - 1).coerceAtLeast(0)).filter { index ->
                                    pbpRidgeSamples[index].density == PbpRidgeDensity.HOT ||
                                        pbpRidgeSamples[index + 1].density == PbpRidgeDensity.HOT
                                }
                                val ridgeBrush = Brush.verticalGradient(
                                    listOf(primaryColor.copy(alpha = 0.18f), primaryColor.copy(alpha = 0.03f)),
                                    startY = centerY - ridgeHeight, endY = centerY
                                )
                                val ridgeGlow = Stroke(trackHeightPx * 2.8f, cap = StrokeCap.Round)
                                val ridgeStroke = Stroke(trackHeightPx * 0.9f, cap = StrokeCap.Round)
                                val sponsorLines = resolvedSponsorMarkers.map { marker ->
                                    Triple(Offset(size.width * marker.startFraction, centerY),
                                        Offset(size.width * marker.endFraction, centerY), marker.color)
                                }
                                val chapterXs = if (duration > 0L) viewPoints.mapNotNull { point ->
                                    resolveProgressFraction(point.fromMs, duration)
                                        .takeIf { it in 0.01f..0.99f }?.let { size.width * it }
                                } else emptyList()
                                onDrawBehind {
                                    if (ridgePoints.size >= 2 && size.width > 0f) {
                                        drawPath(ridgePath, ridgeBrush)
                                        drawPath(ridgeLinePath, primaryColor.copy(alpha = 0.14f), style = ridgeGlow)
                                        drawPath(ridgeLinePath, primaryColor.copy(alpha = 0.46f), style = ridgeStroke)
                                        hotSegments.forEach { index ->
                                            drawLine(primaryColor.copy(alpha = 0.68f), ridgePoints[index],
                                                ridgePoints[index + 1], trackHeightPx * 1.25f, StrokeCap.Round)
                                        }
                                    }
                                    drawMediaProgressTrack(
                                        progressFraction = resolveProgressFraction(activePositionProvider(), duration),
                                        bufferedFraction = bufferedProgress,
                                        trackHeightPx = trackHeightPx,
                                        activeColor = primaryColor,
                                        bufferedColor = bufferedTrackColor,
                                        inactiveColor = inactiveTrackColor,
                                    )
                                    sponsorLines.forEach { (start, end, color) ->
                                        drawLine(color, start, end, trackHeightPx, StrokeCap.Round)
                                    }
                                    chapterXs.forEach { x ->
                                        drawLine(Color.White.copy(alpha = 0.85f), Offset(x, trackTop - 2f),
                                            Offset(x, trackTop + trackHeightPx + 2f),
                                            if (isSeekScrubbing) 2f else 1.5f)
                                    }
                                }
                            }
                    )

                    if (duration > 0L && containerWidthPx > 0f && (!neutralGlassTrack || isSeekScrubbing)) {
                        val thumbOffsetProvider: () -> Int = {
                            (containerWidthPx * resolveProgressFraction(activePositionProvider(), duration) - thumbSizePx / 2f)
                                .coerceIn(0f, (containerWidthPx - thumbSizePx).coerceAtLeast(0f))
                                .roundToInt()
                        }
                        if (progressThumbPath != null) {
                            UiSkinAnimatedAsset(
                                path = progressThumbPath,
                                size = thumbSizeDp,
                                iterations = if (isSeekScrubbing) Int.MAX_VALUE else 1,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset(thumbOffsetProvider(), 0) },
                                contentDescription = null,
                            )
                        } else if (LocalPlayerControlGlassEnabled.current) {
                            PlayerControlGlassThumb(
                                size = thumbSizeDp,
                                dragging = isSeekScrubbing,
                                modifier = Modifier.align(Alignment.CenterStart)
                                    .offset { IntOffset(thumbOffsetProvider(), 0) },
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset { IntOffset(thumbOffsetProvider(), 0) }
                                    .size(thumbSizeDp)
                                    .background(primaryColor, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressSeekPreview(
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData?,
    positionProvider: () -> Long,
    currentPosition: Long,
    duration: Long,
    thumbCenterXPx: Float,
    containerWidthPx: Float
) {
    val target = positionProvider()
    // 与竖屏 PortraitProgressBar 一致：预览跟随拖动 thumb；宽度未测量时回退居中。
    val placement = if (containerWidthPx > 0f) {
        SeekPreviewBubblePlacement.Anchored
    } else {
        SeekPreviewBubblePlacement.Centered
    }
    if (videoshotData != null && videoshotData.isValid) {
        SeekPreviewBubble(videoshotData = videoshotData, targetPositionMs = target,
            currentPositionMs = currentPosition, durationMs = duration, offsetX = thumbCenterXPx,
            containerWidth = containerWidthPx, placement = placement)
    } else {
        SeekPreviewBubbleSimple(targetPositionMs = target, currentPositionMs = currentPosition,
            offsetX = thumbCenterXPx, containerWidth = containerWidthPx, placement = placement)
    }
}
