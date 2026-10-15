package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionActions
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import kotlin.math.roundToInt

internal data class ContinuousPlayerHostLayout(
    val modifier: Modifier,
    val viewportWidth: Dp,
    val alpha: State<Float>,
    val scale: State<Float>,
    val isFullscreen: Boolean,
    val contentTopInset: Dp = 0.dp,
)

// 视口宽度 override 的量化步长：消费方策略全部断点式，量化后过渡期状态稳定。
private const val VIEWPORT_WIDTH_OVERRIDE_QUANTIZE_STEP_DP = 16

internal data class ContinuousPlayerFullscreenExtras(
    val danmakuComposerVisible: Boolean,
    val onDismissDanmakuComposer: () -> Unit,
    val onSendDanmakuComposer: (String, Int, Int, Int, Boolean) -> Unit,
    val isSendingDanmakuComposer: Boolean,
    val danmakuComposerInitialText: String,
    val danmakuComposerInitialAttentionCommand: Boolean,
    val danmakuComposerInitialColor: Int,
    val danmakuComposerInitialMode: Int,
    val danmakuComposerInitialFontSize: Int,
    val onDanmakuComposerDraftChange: (String, Boolean) -> Unit,
    val onDanmakuComposerSelectionChange: (Int, Int, Int) -> Unit,
    val currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode,
    val onPlayModeClick: () -> Unit,
    val onSaveCover: () -> Unit,
    val onDownloadAudio: () -> Unit,
    val relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo>,
    val ugcSeason: com.android.purebilibili.data.model.response.UgcSeason?,
    val isFollowed: Boolean,
    val followStatusKnown: Boolean,
    val isFollowSubmitting: Boolean,
    val isTripleSubmitting: Boolean,
    val isLiked: Boolean,
    val likeStatusKnown: Boolean,
    val isCoined: Boolean,
    val isFavorited: Boolean,
    val onToggleFollow: () -> Unit,
    val onFollow: () -> Unit,
    val onToggleLike: () -> Unit,
    val onDislike: () -> Unit,
    val onCoin: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onTriple: () -> Unit,
    val onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    val onPageSelect: (Int) -> Unit,
    val hasFavoritePlaylist: Boolean,
    val onFavoritePlaylistClick: () -> Unit,
    val onLandscapeCommentClick: () -> Unit,
    val landscapeCommentPanelVisible: Boolean,
    val landscapeCommentPanelOnLeft: Boolean,
    val landscapeCommentCloseProgressProvider: () -> Float = { 0f },
)

/**
 * Reads the frame-rate progress only during measurement, keeping the player composition stable
 * while the inline viewport grows into the landscape viewport.
 */
/**
 * @param preferLayoutWidthInline 普通视频竖屏详情播放器：展开态按**实际布局宽度**算高度，
 * 避免 `configuration.screenWidthDp` 与真机可用宽度不一致时出现左右黑边（vivo 等窄机更常见）。
 * 折叠/半折叠时必须仍尊重 [inlineHeight]（评论上滑缩小播放器），不能盖成固定展开比例。
 * @param inlineTopInset 沉浸状态栏额外高度；只加在 inline 高度上，不参与媒体比例本体。
 */
internal fun Modifier.continuousPlayerViewportHeight(
    progressProvider: () -> Float,
    inlineHeight: Dp,
    fullscreenHeight: Dp,
    enabled: Boolean,
    preferLayoutWidthInline: Boolean = false,
    inlineTopInset: Dp = 0.dp,
    removedInlineTopInsetProvider: () -> Dp = { 0.dp },
): Modifier {
    return layout { measurable, constraints ->
        val layoutWidth = constraints.maxWidth.coerceAtLeast(1)
        val removedInset = removedInlineTopInsetProvider()
        val insetPx = (inlineTopInset - removedInset).roundToPx().coerceAtLeast(0)
        val callerInlinePx = (inlineHeight - removedInset).roundToPx().coerceAtLeast(0)
        val inlinePx = resolveContinuousPlayerInlineHeightPx(
            layoutWidthPx = layoutWidth,
            preferLayoutWidthInline = preferLayoutWidthInline,
            callerInlineHeightPx = callerInlinePx,
            inlineTopInsetPx = insetPx,
        )
        val fraction = progressProvider().coerceIn(0f, 1f)
        val fullscreenPx = fullscreenHeight.toPx()
        val height = if (!enabled) {
            inlinePx
        } else {
            (inlinePx + (fullscreenPx - inlinePx) * fraction).roundToInt()
        }.coerceIn(
            minimumValue = constraints.minHeight,
            maximumValue = if (constraints.maxHeight == Constraints.Infinity) {
                Int.MAX_VALUE
            } else {
                constraints.maxHeight
            },
        )
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = layoutWidth,
                maxWidth = layoutWidth,
                minHeight = height,
                maxHeight = height,
            ),
        )
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}

/**
 * 解析 continuous player 的 inline 高度。
 *
 * - 默认：直接使用调用方传入的 [callerInlineHeightPx]（已含评论上滑折叠进度）。
 * - [preferLayoutWidthInline]：展开时用真实布局宽计算加载封面与播放画面的统一 16:9 高度，消除 screenWidthDp 黑边；
 *   折叠时取与调用方高度的较小值，避免盖掉「上滑缩小播放器」。
 */
internal fun resolveContinuousPlayerInlineHeightPx(
    layoutWidthPx: Int,
    preferLayoutWidthInline: Boolean,
    callerInlineHeightPx: Int,
    inlineTopInsetPx: Int,
): Int {
    val callerPx = callerInlineHeightPx.coerceAtLeast(0)
    if (!preferLayoutWidthInline) {
        return callerPx.coerceAtLeast(1)
    }
    val expandedFromLayoutPx =
        resolvePhoneDetailPlayerContentHeightPx(layoutWidthPx = layoutWidthPx) +
            inlineTopInsetPx.coerceAtLeast(0)
    // 展开：layout 宽计算的高度通常 ≤ screenWidthDp 估高，取 min 消黑边。
    // 折叠/半折叠：caller 更小，取 min 保留上滑缩小。
    return minOf(expandedFromLayoutPx, callerPx).coerceAtLeast(1)
}

@Composable
internal fun PortraitInlineVideoPlayerHost(
    modifier: Modifier,
    animatedViewportWidth: Dp,
    contentTopInset: Dp = 0.dp,
    inlinePlayerAlpha: State<Float>,
    inlinePlayerScale: State<Float>,
    isFullscreen: Boolean = false,
    playerState: VideoPlayerState,
    uiState: VideoPlaybackUiState,
    engagementState: VideoEngagementUiState,
    engagementActions: VideoDetailEngagementActions,
    isPipMode: Boolean,
    transitionEnabled: Boolean,
    transitionChromeAlphaProvider: () -> Float,
    danmakuHostActive: Boolean,
    onToggleFullscreen: () -> Unit,
    playbackActions: VideoDetailPlaybackActions,
    onDoubleTapLike: () -> Unit,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    endDrawerRequestKey: Int = 0,
    videoPlayerSectionTarget: VideoPlayerSectionTarget,
    sponsorSegment: com.android.purebilibili.data.model.response.SponsorSegment?,
    showSponsorSkipButton: Boolean,
    sponsorContributionState: com.android.purebilibili.feature.video.viewmodel.SponsorContributionUiState,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    pbpProgressData: PbpProgressData?,
    sponsorProgressMarkers: List<com.android.purebilibili.data.model.response.SponsorProgressMarker>,
    isVerticalVideo: Boolean,
    onPortraitFullscreen: () -> Unit,
    isPortraitFullscreen: Boolean,
    onPipClick: () -> Unit,
    codecPreference: String,
    secondCodecPreference: String,
    audioQualityPreference: Int,
    onNavigateToAudioMode: () -> Unit,
    forceCoverOnly: Boolean,
    preserveCurrentFrameOnFullscreenChange: Boolean,
    liveBackPreview: Boolean,
    useTextureSurfaceForNavigation: Boolean,
    predictiveBackCancelRecoveryGeneration: Int,
    allowLivePlayerSharedElement: Boolean,
    sourceRouteForSharedElement: String?,
    preserveSourceCardCornerDuringSharedReturn: Boolean = false,
    suppressSubtitleOverlay: Boolean,
    subtitleDisplayModePreferenceOverride: SubtitleDisplayMode?,
    onSubtitleDisplayModePreferenceOverrideChange: (SubtitleDisplayMode) -> Unit,
    fullscreenExtras: ContinuousPlayerFullscreenExtras? = null,
    residentCoverSource: VideoDetailResidentCoverSource? = null,
    suppressTransientOverlaysForTransition: Boolean = false,
    showPlaybackErrorNotice: Boolean = false,
) {
    val successState = uiState as? VideoPlaybackUiState.Success
    // 竖屏全屏由 PortraitVideoPager 接管播放面；内联 section 不得再合成。
    if (isPortraitFullscreen) {
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = inlinePlayerAlpha.value
                scaleX = inlinePlayerScale.value
                scaleY = inlinePlayerScale.value
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
    ) {
        VideoPlayerSection(
            state = VideoPlayerSectionState(
                playerState = playerState,
                uiState = uiState,
                isFullscreen = isFullscreen,
                isInPipMode = isPipMode,
                contentTopInset = contentTopInset,
                transitionEnabled = transitionEnabled,
                transitionChromeAlphaProvider = transitionChromeAlphaProvider,
                danmakuHostActive = danmakuHostActive,
                suppressTransientOverlaysForTransition = suppressTransientOverlaysForTransition,
                endDrawerRequestKey = endDrawerRequestKey,
                landscapeCommentPanelVisible = isFullscreen &&
                    fullscreenExtras?.landscapeCommentPanelVisible == true,
                landscapeCommentPanelOnLeft = fullscreenExtras?.landscapeCommentPanelOnLeft ?: true,
                landscapeCommentCloseProgressProvider = fullscreenExtras?.landscapeCommentCloseProgressProvider ?: { 0f },
                danmakuComposerVisible = isFullscreen &&
                    fullscreenExtras?.danmakuComposerVisible == true,
                isSendingDanmakuComposer = fullscreenExtras?.isSendingDanmakuComposer == true,
                danmakuComposerInitialText = fullscreenExtras?.danmakuComposerInitialText.orEmpty(),
                danmakuComposerInitialAttentionCommand =
                    fullscreenExtras?.danmakuComposerInitialAttentionCommand == true,
                danmakuComposerInitialColor = fullscreenExtras?.danmakuComposerInitialColor ?: 16777215,
                danmakuComposerInitialMode = fullscreenExtras?.danmakuComposerInitialMode ?: 1,
                danmakuComposerInitialFontSize = fullscreenExtras?.danmakuComposerInitialFontSize ?: 25,
                bvid = videoPlayerSectionTarget.bvid,
                coverUrl = videoPlayerSectionTarget.entryCoverUrl,
                stationaryListCoverUrl = residentCoverSource?.url.orEmpty(),
                stationaryListCoverCacheKey = residentCoverSource?.cacheKey.orEmpty(),
                stationaryListCoverDecodeWidthPx = residentCoverSource?.decodeWidthPx ?: 0,
                stationaryListCoverDecodeHeightPx = residentCoverSource?.decodeHeightPx ?: 0,
                sharedElementBvid = videoPlayerSectionTarget.sharedElementBvid,
                sponsorSegment = sponsorSegment,
                showSponsorSkipButton = showSponsorSkipButton,
                sponsorContributionState = sponsorContributionState,
                currentCdnIndex = successState?.currentCdnIndex ?: 0,
                cdnCount = successState?.cdnCount ?: 1,
                cdnLineDiagnostics = successState?.cdnLineDiagnostics.orEmpty(),
                isCdnProbing = successState?.isCdnProbing ?: false,
                isAudioOnly = false,
                sleepTimerMinutes = sleepTimerMinutes,
                videoshotData = successState?.videoshotData,
                viewPoints = viewPoints,
                pbpProgressData = pbpProgressData,
                sponsorMarkers = sponsorProgressMarkers,
                isVerticalVideo = isVerticalVideo,
                isPortraitFullscreen = isPortraitFullscreen,
                viewportWidthDpOverride = if (animatedViewportWidth.isSpecified) {
                    // 消费方（播放器 UI/控制栏布局策略）全部是断点式档位；
                    // 连续动画宽度若逐帧直传，会让 VideoPlayerSectionState 每帧
                    // 失去 equals，过渡期间整段 section 反复重组。量化成 16dp 桶，
                    // 档位切换点之外完全稳定。
                    ((animatedViewportWidth.value.roundToInt() /
                        VIEWPORT_WIDTH_OVERRIDE_QUANTIZE_STEP_DP) *
                        VIEWPORT_WIDTH_OVERRIDE_QUANTIZE_STEP_DP).coerceAtLeast(1)
                } else {
                    null
                },
                currentCodec = codecPreference,
                currentSecondCodec = secondCodecPreference,
                currentAudioQuality = audioQualityPreference,
                currentPlayMode = fullscreenExtras?.currentPlayMode
                    ?: com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
                relatedVideos = fullscreenExtras?.relatedVideos.orEmpty(),
                ugcSeason = fullscreenExtras?.ugcSeason,
                isFollowed = fullscreenExtras?.isFollowed ?: engagementState.isFollowing,
                followStatusKnown = fullscreenExtras?.followStatusKnown ?: engagementState.followStatusKnown,
                isFollowSubmitting = fullscreenExtras?.isFollowSubmitting ?: engagementState.isFollowSubmitting,
                isTripleSubmitting = fullscreenExtras?.isTripleSubmitting ?: engagementState.isTripleSubmitting,
                isLiked = fullscreenExtras?.isLiked ?: engagementState.isLiked,
                likeStatusKnown = fullscreenExtras?.likeStatusKnown ?: engagementState.likeStatusKnown,
                isCoined = fullscreenExtras?.isCoined ?: (engagementState.coinCount > 0),
                isFavorited = fullscreenExtras?.isFavorited ?: engagementState.isFavorited,
                hasFavoritePlaylist = fullscreenExtras?.hasFavoritePlaylist == true,
                forceCoverOnly = forceCoverOnly,
                preserveCurrentFrameOnFullscreenChange = preserveCurrentFrameOnFullscreenChange,
                liveBackPreview = liveBackPreview,
                useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
                predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                allowLivePlayerSharedElement = allowLivePlayerSharedElement,
                sourceRouteForSharedElement = sourceRouteForSharedElement,
                preserveSourceCardCornerDuringSharedReturn =
                    preserveSourceCardCornerDuringSharedReturn,
                suppressSubtitleOverlay = suppressSubtitleOverlay,
                subtitleDisplayModePreferenceOverride = subtitleDisplayModePreferenceOverride,
            ),
            actions = VideoPlayerSectionActions(
                    resolveNextWatch = playbackActions.resolveNextWatch,
                    playNextWatch = playbackActions.playNextWatch,
                onToggleFullscreen = onToggleFullscreen,
                onQualityChange = { qid -> playbackActions.changeQuality(qid) },
                onBack = onBack,
                onHomeClick = onHomeClick,
                onLandscapeCommentClick = if (isFullscreen) {
                    fullscreenExtras?.onLandscapeCommentClick ?: {}
                } else {
                    {}
                },
                onDanmakuInputClick = { playbackActions.showDanmakuSendDialog() },
                onDismissDanmakuComposer = fullscreenExtras?.onDismissDanmakuComposer ?: {},
                onSendDanmakuComposer = fullscreenExtras?.onSendDanmakuComposer
                    ?: { _, _, _, _, _ -> },
                onDanmakuComposerDraftChange = fullscreenExtras?.onDanmakuComposerDraftChange
                    ?: { _, _ -> },
                onDanmakuComposerSelectionChange = fullscreenExtras?.onDanmakuComposerSelectionChange
                    ?: { _, _, _ -> },
                onDoubleTapLike = onDoubleTapLike,
                onSponsorSkip = { playbackActions.skipSponsorSegment() },
                onSponsorDismiss = { playbackActions.dismissSponsorSkipButton() },
                onSponsorVote = { playbackActions.voteSponsorSegment(it) },
                onSponsorContributionMarkBoundary = { playbackActions.markSponsorContributionBoundary() },
                onSponsorContributionMarkWholeVideo = { playbackActions.markWholeVideoAsSponsor() },
                onSponsorContributionCategoryChange = { playbackActions.setSponsorContributionCategory(it) },
                onSponsorContributionActionTypeChange = { playbackActions.setSponsorContributionActionType(it) },
                onSponsorContributionSubmit = { playbackActions.submitSponsorContribution() },
                onSponsorContributionCancel = { playbackActions.cancelSponsorContribution() },
                onReloadVideo = { playbackActions.reloadVideo() },
                onSwitchCdn = { playbackActions.switchCdn() },
                onSwitchCdnTo = { playbackActions.switchCdnTo(it) },
                onProbeCdnCandidates = { playbackActions.probeCdnCandidates() },
                onAudioOnlyToggle = onNavigateToAudioMode,
                onSleepTimerChange = { playbackActions.setSleepTimer(it) },
                onUserSeek = { position -> playbackActions.notifyExplicitSeek(position) },
                onPortraitFullscreen = onPortraitFullscreen,
                onPipClick = onPipClick,
                onCodecChange = { playbackActions.setVideoCodec(it) },
                onSecondCodecChange = { playbackActions.setVideoSecondCodec(it) },
                onAudioQualityChange = { playbackActions.setAudioQuality(it) },
                onPlaybackSpeedChange = { playbackActions.applyPlaybackSpeed(it) },
                onAudioLangChange = { playbackActions.changeAudioLanguage(it) },
                onPlayModeClick = fullscreenExtras?.onPlayModeClick ?: {},
                onSaveCover = fullscreenExtras?.onSaveCover ?: { playbackActions.saveCover() },
                onDownloadAudio = fullscreenExtras?.onDownloadAudio
                    ?: { playbackActions.downloadAudio() },
                onRelatedVideoClick = fullscreenExtras?.onRelatedVideoClick ?: { _, _ -> },
                onToggleFollow = fullscreenExtras?.onToggleFollow ?: engagementActions.toggleFollow,
                onFollow = fullscreenExtras?.onFollow ?: engagementActions.followOwner,
                onToggleLike = fullscreenExtras?.onToggleLike ?: engagementActions.toggleLike,
                onDislike = fullscreenExtras?.onDislike ?: playbackActions.markVideoNotInterested,
                onCoin = fullscreenExtras?.onCoin ?: engagementActions.openCoinDialog,
                onToggleFavorite = fullscreenExtras?.onToggleFavorite ?: engagementActions.toggleFavorite,
                onTriple = fullscreenExtras?.onTriple ?: engagementActions.doTripleAction,
                onPageSelect = fullscreenExtras?.onPageSelect ?: {},
                onFavoritePlaylistClick = fullscreenExtras?.onFavoritePlaylistClick ?: {},
                onSubtitleDisplayModePreferenceOverrideChange =
                    onSubtitleDisplayModePreferenceOverrideChange,
                onSubtitleTrackSelected = playbackActions.selectSubtitleTrack,
                onLikeDanmaku = playbackActions.likeDanmaku,
                onLikeDanmakuToggle = playbackActions.likeDanmakuToggle,
                likedDanmakuIds = playbackActions.likedDanmakuIds,
                onReportDanmaku = playbackActions.reportDanmaku,
                onRecallDanmaku = playbackActions.recallDanmaku,
            ),
        )
        if (showPlaybackErrorNotice && uiState is VideoPlaybackUiState.Error && !suppressTransientOverlaysForTransition) {
            VideoDetailPlaybackErrorNotice(
                state = uiState,
                onRetry = playbackActions.retryPlayback,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
