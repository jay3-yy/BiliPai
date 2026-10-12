package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.resolveDanmakuSettingsScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.feature.bangumi.BangumiOverlayUnsupportedAction
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.feature.bangumi.resolveBangumiOverlayQualityLabel
import com.android.purebilibili.feature.bangumi.resolveBangumiOverlayShareTitle
import com.android.purebilibili.feature.bangumi.resolveBangumiOverlaySwitchableQualityIds
import com.android.purebilibili.feature.bangumi.resolveBangumiUnsupportedOverlayActionMessage
import com.android.purebilibili.feature.bangumi.shouldShowBangumiOverlayDislikeAction
import com.android.purebilibili.core.util.ShareUtils
import com.android.purebilibili.feature.anime4k.Anime4KBypassReason
import com.android.purebilibili.feature.anime4k.Anime4KPreset
import com.android.purebilibili.feature.anime4k.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.PlaybackDebugInfo
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlay
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayState
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayActions
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption

@Composable
internal fun BangumiPlayerOverlayHost(
    player: ExoPlayer,
    features: BangumiPlayerFeatures,
    seasonId: Long,
    epId: Long,
    title: String,
    subtitle: String,
    bvid: String,
    aid: Long,
    cid: Long,
    coverUrl: String,
    currentVideoUrl: String,
    currentAudioUrl: String,
    debugInfo: PlaybackDebugInfo,
    isVisible: Boolean,
    onToggleVisible: () -> Unit,
    isFullscreen: Boolean,
    isScreenLocked: Boolean,
    onLockToggle: () -> Unit,
    currentQuality: Int,
    acceptQuality: List<Int>,
    acceptDescription: List<String>,
    isLoggedIn: Boolean,
    isVip: Boolean,
    onQualityChange: (Int) -> Unit,
    requestedAudioQuality: Int,
    selectedAudioQuality: Int,
    availableAudioQualities: List<AudioQualityOption>,
    onAudioQualityChange: (Int) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    danmakuOpacity: Float,
    danmakuFontScale: Float,
    danmakuSpeed: Float,
    danmakuDisplayArea: Float,
    danmakuMergeDuplicates: Boolean,
    danmakuDuplicateMergeWindowMs: Int,
    danmakuDuplicateMergeCountThreshold: Int,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuFontScaleChange: (Float) -> Unit,
    onDanmakuSpeedChange: (Float) -> Unit,
    onDanmakuDisplayAreaChange: (Float) -> Unit,
    onDanmakuMergeDuplicatesChange: (Boolean) -> Unit,
    onDanmakuDuplicateMergeWindowMsChange: (Int) -> Unit,
    onDanmakuDuplicateMergeCountThresholdChange: (Int) -> Unit,
    currentAspectRatio: VideoAspectRatio,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    pages: List<Page>,
    currentPageIndex: Int,
    onPageSelect: (Int) -> Unit,
    isLiked: Boolean,
    coinCount: Int,
    onToggleLike: () -> Unit,
    onCoin: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onReloadVideo: () -> Unit,
    anime4kEnabled: Boolean,
    anime4kAvailable: Boolean,
    anime4kBypassReason: Anime4KBypassReason,
    videoEnhancementAlgorithm: VideoEnhancementAlgorithm,
    anime4kPreset: Anime4KPreset,
    fsrSharpness: Float,
    onAnime4kToggle: (Boolean) -> Unit,
    onVideoEnhancementAlgorithmChange: (VideoEnhancementAlgorithm) -> Unit,
    onAnime4kPresetChange: (Anime4KPreset) -> Unit,
    onFsrSharpnessChange: (Float) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    val danmakuSettingsScope = resolveDanmakuSettingsScope(isFullscreen)
    val settings by SettingsManager.getDanmakuSettings(context, danmakuSettingsScope)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val currentQualityLabel = resolveBangumiOverlayQualityLabel(
        currentQuality = currentQuality,
        acceptQuality = acceptQuality,
        acceptDescription = acceptDescription
    )
    val switchableQualityIds = resolveBangumiOverlaySwitchableQualityIds(acceptQuality)

    VideoPlayerOverlay(
        state = VideoPlayerOverlayState(
            player = player,
            title = title,
            isVisible = isVisible,
            isFullscreen = isFullscreen,
            currentQualityLabel = currentQualityLabel,
            qualityLabels = acceptDescription,
            qualityIds = acceptQuality,
            switchableQualityIds = switchableQualityIds,
            isLoggedIn = isLoggedIn,
            bvid = bvid,
            cid = cid,
            videoOwnerName = title,
            videoSharePlayCountText = "",
            videoDuration = player.duration.coerceAtLeast(0L),
            videoTitle = subtitle.ifBlank { title },
            currentAid = aid,
            currentQuality = currentQuality,
            currentVideoUrl = currentVideoUrl,
            currentAudioUrl = currentAudioUrl,
            debugInfo = debugInfo,
            isVip = isVip,
            currentAudioQuality = requestedAudioQuality,
            selectedAudioQuality = selectedAudioQuality,
            availableAudioQualities = availableAudioQualities,
            isLiked = isLiked,
            isCoined = coinCount > 0,
            coinCount = coinCount,
            isScreenLocked = isScreenLocked,
            danmakuEnabled = danmakuEnabled,
            danmakuOpacity = danmakuOpacity,
            danmakuFontScale = danmakuFontScale,
            danmakuSpeed = danmakuSpeed,
            danmakuDisplayArea = danmakuDisplayArea,
            danmakuFontWeight = settings.fontWeight,
            danmakuStrokeWidth = settings.strokeWidth,
            danmakuLineHeight = settings.lineHeight,
            danmakuScrollDurationSeconds = settings.scrollDurationSeconds,
            danmakuStaticDurationSeconds = settings.staticDurationSeconds,
            danmakuScrollFixedVelocity = settings.scrollFixedVelocity,
            danmakuStaticToScroll = settings.staticDanmakuToScroll,
            danmakuMassiveMode = settings.massiveMode,
            danmakuAllowScroll = settings.allowScroll,
            danmakuAllowTop = settings.allowTop,
            danmakuAllowBottom = settings.allowBottom,
            danmakuAllowColorful = settings.allowColorful,
            danmakuAllowSpecial = settings.allowSpecial,
            danmakuWeightFilterLevel = settings.weightFilterLevel,
            danmakuHideInteractiveCommands = settings.hideInteractiveCommands,
            danmakuBlockRulesRaw = settings.blockRulesRaw,
            danmakuSmartOcclusion = settings.smartOcclusion,
            danmakuFullscreenPanelWidthMode = settings.fullscreenPanelWidthMode,
            portraitDanmakuDisplayAreaMode = settings.portraitDisplayAreaMode,
            danmakuSettingsScope = danmakuSettingsScope,
            danmakuMergeDuplicates = danmakuMergeDuplicates,
            danmakuDuplicateMergeWindowMs = danmakuDuplicateMergeWindowMs,
            danmakuDuplicateMergeCountThreshold = danmakuDuplicateMergeCountThreshold,
            landscapeCommentPanelVisible = features.commentVisible,
            landscapeCommentViewportAlreadyReserved = true,
            landscapeCommentPanelOnLeft = features.commentOnLeft,
            landscapeCommentCloseProgressProvider = features.commentCloseProgressProvider,
            subtitleControlState = features.subtitle.controls,
            isAudioOnly = features.isAudioOnly,
            isFlippedHorizontal = features.isFlippedHorizontal,
            isFlippedVertical = features.isFlippedVertical,
            sleepTimerMinutes = features.sleepTimerMinutes,
            currentAspectRatio = currentAspectRatio,
            showDislikeAction = shouldShowBangumiOverlayDislikeAction(),
            coverUrl = coverUrl,
            anime4kEnabled = anime4kEnabled,
            anime4kAvailable = anime4kAvailable,
            anime4kBypassReason = anime4kBypassReason,
            videoEnhancementAlgorithm = videoEnhancementAlgorithm,
            anime4kPreset = anime4kPreset,
            fsrSharpness = fsrSharpness,
            pages = pages,
            currentPageIndex = currentPageIndex,
        ),
        actions = VideoPlayerOverlayActions(
            onToggleVisible = onToggleVisible,
            onQualitySelected = { index ->
                acceptQuality.getOrNull(index)?.let(onQualityChange)
            },
            onBack = onBack,
            onHomeClick = onBack,
            onToggleFullscreen = onToggleFullscreen,
            onAudioQualityChange = onAudioQualityChange,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            onLockToggle = onLockToggle,
            onDanmakuToggle = onDanmakuToggle,
            onDanmakuInputClick = features.onDanmakuInputClick,
            onLandscapeCommentClick = features.onCommentClick,
            onSleepTimerChange = features.onSleepTimerChange,
            onDanmakuOpacityChange = onDanmakuOpacityChange,
            onDanmakuFontScaleChange = onDanmakuFontScaleChange,
            onDanmakuSpeedChange = onDanmakuSpeedChange,
            onDanmakuDisplayAreaChange = onDanmakuDisplayAreaChange,
            onDanmakuFontWeightChange = { value -> settingsScope.launch { SettingsManager.setDanmakuFontWeight(context, value, danmakuSettingsScope) } },
            onDanmakuStrokeWidthChange = { value -> settingsScope.launch { SettingsManager.setDanmakuStrokeWidth(context, value, danmakuSettingsScope) } },
            onDanmakuLineHeightChange = { value -> settingsScope.launch { SettingsManager.setDanmakuLineHeight(context, value, danmakuSettingsScope) } },
            onDanmakuScrollDurationSecondsChange = { value -> settingsScope.launch { SettingsManager.setDanmakuScrollDurationSeconds(context, value, danmakuSettingsScope) } },
            onDanmakuStaticDurationSecondsChange = { value -> settingsScope.launch { SettingsManager.setDanmakuStaticDurationSeconds(context, value, danmakuSettingsScope) } },
            onDanmakuScrollFixedVelocityChange = { value -> settingsScope.launch { SettingsManager.setDanmakuScrollFixedVelocity(context, value, danmakuSettingsScope) } },
            onDanmakuStaticToScrollChange = { value -> settingsScope.launch { SettingsManager.setDanmakuStaticToScroll(context, value, danmakuSettingsScope) } },
            onDanmakuMassiveModeChange = { value -> settingsScope.launch { SettingsManager.setDanmakuMassiveMode(context, value, danmakuSettingsScope) } },
            onDanmakuAllowScrollChange = { value -> settingsScope.launch { SettingsManager.setDanmakuAllowScroll(context, value, danmakuSettingsScope) } },
            onDanmakuAllowTopChange = { value -> settingsScope.launch { SettingsManager.setDanmakuAllowTop(context, value, danmakuSettingsScope) } },
            onDanmakuAllowBottomChange = { value -> settingsScope.launch { SettingsManager.setDanmakuAllowBottom(context, value, danmakuSettingsScope) } },
            onDanmakuAllowColorfulChange = { value -> settingsScope.launch { SettingsManager.setDanmakuAllowColorful(context, value, danmakuSettingsScope) } },
            onDanmakuAllowSpecialChange = { value -> settingsScope.launch { SettingsManager.setDanmakuAllowSpecial(context, value, danmakuSettingsScope) } },
            onDanmakuWeightFilterLevelChange = { value -> settingsScope.launch { SettingsManager.setDanmakuWeightFilterLevel(context, value) } },
            onDanmakuHideInteractiveCommandsChange = { value -> settingsScope.launch { SettingsManager.setDanmakuHideInteractiveCommands(context, value) } },
            onDanmakuBlockRulesRawChange = { value -> settingsScope.launch { SettingsManager.setDanmakuBlockRulesRaw(context, value, danmakuSettingsScope) } },
            onDanmakuSmartOcclusionChange = { value -> settingsScope.launch { SettingsManager.setDanmakuSmartOcclusion(context, value, danmakuSettingsScope) } },
            onDanmakuFullscreenPanelWidthModeChange = { value -> settingsScope.launch { SettingsManager.setDanmakuFullscreenPanelWidthMode(context, value) } },
            onPortraitDanmakuDisplayAreaModeChange = { value -> settingsScope.launch { SettingsManager.setPortraitDanmakuDisplayAreaMode(context, value) } },
            onDoubleTapLike = { if (!isLiked) onToggleLike() },
            onDanmakuMergeDuplicatesChange = onDanmakuMergeDuplicatesChange,
            onDanmakuDuplicateMergeWindowMsChange = onDanmakuDuplicateMergeWindowMsChange,
            onDanmakuDuplicateMergeCountThresholdChange = onDanmakuDuplicateMergeCountThresholdChange,
            subtitleControlCallbacks = features.subtitle.callbacks,
            onAspectRatioChange = onAspectRatioChange,
            onShare = {
                ShareUtils.shareBangumi(
                    context = context,
                    title = resolveBangumiOverlayShareTitle(title = title, subtitle = subtitle),
                    seasonId = seasonId,
                    epId = epId.takeIf { it > 0L }
                )
            },
            onReloadVideo = onReloadVideo,
            onAnime4kToggle = onAnime4kToggle,
            onVideoEnhancementAlgorithmChange = onVideoEnhancementAlgorithmChange,
            onAnime4kPresetChange = onAnime4kPresetChange,
            onFsrSharpnessChange = onFsrSharpnessChange,
            onQualityChange = onQualityChange,
            onPipClick = features.onPipClick,
            onCaptureScreenshot = onCaptureScreenshot,
            onAudioOnlyToggle = features.onAudioOnlyToggle,
            onFlipHorizontal = features.onFlipHorizontal,
            onFlipVertical = features.onFlipVertical,
            onSaveCover = features.onSaveCover,
            onDownloadAudio = features.onDownloadAudio,
            onPageSelect = onPageSelect,
            onToggleLike = onToggleLike,
            onDislike = {
                onShowMessage(
                    resolveBangumiUnsupportedOverlayActionMessage(
                        BangumiOverlayUnsupportedAction.DISLIKE
                    )
                )
            },
            onCoin = onCoin,
        ),
    )
}
