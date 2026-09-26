package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.navigation.animatePagerSelection

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.PlaylistPlay
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.feature.video.ui.components.shouldShowVideoCommentBackToTop
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.store.SettingsManager
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.core.store.TabletCommentPanelWidthPreset
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionSourceCornerDp
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.feature.video.ui.section.resolveAllowLivePlayerSharedElementForMorph
import com.android.purebilibili.feature.video.ui.section.resolveNavigationLiveSurfaceTextureEnabled
import com.android.purebilibili.feature.video.share.VideoSharePayload
import com.android.purebilibili.feature.video.share.VideoShareSheetHost
import com.android.purebilibili.feature.video.share.buildVideoSharePayload
import com.android.purebilibili.core.util.ShareUtils
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.buildVideoNoteShareText
import com.android.purebilibili.feature.video.note.shouldShowVideoNoteCard
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.ui.components.CommentSortHeader
import com.android.purebilibili.feature.video.ui.components.CommentSearchSheet
import com.android.purebilibili.feature.video.ui.components.BottomInputBar
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
import com.android.purebilibili.feature.video.ui.components.RelatedVideoItem
import com.android.purebilibili.feature.video.ui.components.RelatedVideoGridRow
import com.android.purebilibili.feature.video.ui.components.chunkRelatedVideosForHomeStyleGrid
import com.android.purebilibili.feature.video.ui.components.filterRelatedVideosByHiddenBvids
import com.android.purebilibili.feature.video.ui.components.rememberRelatedVideoCardLayout
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.VideoInlineSubReplyDetailContent
import com.android.purebilibili.feature.video.ui.components.rememberVideoCommentAppearance
import com.android.purebilibili.feature.video.ui.components.resolveReplyItemContentType
import com.android.purebilibili.feature.video.ui.components.shouldShowReplyTopAction
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.resolveDisplayBgmList
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionActions
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionState
import com.android.purebilibili.feature.video.ui.section.AiSummarySheet
import com.android.purebilibili.feature.video.ui.section.VideoNoteListSheet
import com.android.purebilibili.feature.video.ui.section.VideoSupplementEntryRow
import com.android.purebilibili.feature.video.ui.section.VideoNoteDeleteConfirmDialog
import com.android.purebilibili.feature.video.ui.section.VideoNoteEditorSheet
import com.android.purebilibili.feature.video.ui.section.shouldShowAiSummaryEntry
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.SponsorContributionUiState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.withEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TabletCinemaLayout(
    playerState: VideoPlayerState,
    uiState: VideoPlaybackUiState,
    commentState: CommentUiState,
    engagementState: VideoEngagementUiState,
    subReplyState: SubReplyUiState,
    downloadProgress: Float,
    tabletCommentPanelWidthPreset: TabletCommentPanelWidthPreset,
    commentMemberDecorationsEnabled: Boolean,
    videoAiSummaryEntryEnabled: Boolean,
    videoNoteEnabled: Boolean,
    videoNoteDefaultCollapsed: Boolean,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    configuration: Configuration,
    isVerticalVideo: Boolean,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    pbpProgressData: PbpProgressData?,
    bvid: String,
    coverUrl: String = "",
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    onBgmClick: (BgmInfo) -> Unit = {},
    onNavigateToAudioMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPortraitFullscreen: () -> Unit,
    isInPipMode: Boolean,
    onPipClick: () -> Unit,
    isPortraitFullscreen: Boolean = false,
    onHomeClick: () -> Unit,
    currentCodec: String = "hev1",
    onCodecChange: (String) -> Unit = {},
    currentSecondCodec: String = "avc1",
    onSecondCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    onAudioQualityChange: (Int) -> Unit = {},
    transitionEnabled: Boolean = false,
    danmakuHostActive: Boolean = true,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    showUpBadge: Boolean = true,
    onSearchKeywordClick: (String) -> Unit = {},
    onOpenBilibiliLink: ((String) -> Unit)? = null,
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode =
        com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
    onPlayModeClick: () -> Unit = {},
    forceCoverOnlyOnReturn: Boolean = false,
    predictiveBackCancelRecoveryGeneration: Int = 0,
    sponsorContributionState: SponsorContributionUiState = SponsorContributionUiState(),
    liveSurfaceCardTransitionEnabled: Boolean = true,
) {
    val appContext = LocalContext.current
    val secondaryDefaultTab by SettingsManager
        .getTabletSecondaryDefaultTab(appContext)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.TabletSecondaryDefaultTab.RELATED)
    val foldPosture = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.posture
    val policy = remember(configuration.screenWidthDp, tabletCommentPanelWidthPreset, foldPosture) {
        resolveTabletCinemaLayoutPolicy(
            widthDp = configuration.screenWidthDp,
            commentWidthPreset = tabletCommentPanelWidthPreset,
            foldPosture = foldPosture,
        )
    }
    val success = uiState as? VideoPlaybackUiState.Success
    val danmakuChrome = rememberTabletDanmakuChromeState(bvid)
    var pendingVideoShare by remember { mutableStateOf<VideoSharePayload?>(null) }
    val openVideoShareSheet: () -> Unit = {
        val info = (uiState as? VideoPlaybackUiState.Success)?.info
        if (info != null) {
            pendingVideoShare = buildVideoSharePayload(
                title = info.title,
                bvid = info.bvid,
                coverUrl = info.pic,
                upName = info.owner.name,
                playCountText = com.android.purebilibili.core.util.FormatUtils
                    .formatStat(info.stat.view.toLong()),
            )
        }
    }
    val initialCurtainState = remember(configuration.screenWidthDp) {
        resolveInitialCurtainState(configuration.screenWidthDp).name
    }
    var curtainStateName by rememberSaveable(bvid) { mutableStateOf(initialCurtainState) }
    val curtainState = remember(curtainStateName) {
        runCatching { TabletSideCurtainState.valueOf(curtainStateName) }
            .getOrDefault(resolveInitialCurtainState(configuration.screenWidthDp))
    }
    var selectedTab by rememberSaveable(bvid, secondaryDefaultTab) {
        mutableIntStateOf(resolveTabletCinemaDefaultTab(secondaryDefaultTab))
    }
    val curtainPagerState = rememberPagerState(
        initialPage = selectedTab,
        pageCount = { 2 }
    )
    val curtainWidth by animateDpAsState(
        targetValue = resolveCurtainWidthDp(curtainState, policy).dp,
        animationSpec = tween(durationMillis = 240),
        label = "cinemaCurtainWidth"
    )

    LaunchedEffect(success?.related?.size, commentState.replyCount, commentState.isRepliesLoading) {
        selectedTab = resolveCinemaSideCurtainSelectedTab(
            currentSelectedTab = selectedTab,
            replyCount = commentState.replyCount,
            isRepliesLoading = commentState.isRepliesLoading,
            hasRelatedVideos = !success?.related.isNullOrEmpty()
        )
    }
    LaunchedEffect(selectedTab) {
        if (curtainPagerState.currentPage != selectedTab) {
            animatePagerSelection(curtainPagerState, selectedTab)
        }
    }
    LaunchedEffect(curtainPagerState.currentPage) {
        if (selectedTab != curtainPagerState.currentPage) {
            selectedTab = curtainPagerState.currentPage
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        VideoShareSheetHost(
            payload = pendingVideoShare,
            onDismiss = { pendingVideoShare = null },
        )
        val padding = PaddingValues(
            top = max(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), policy.horizontalPaddingDp.dp),
            bottom = max(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), policy.horizontalPaddingDp.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = policy.horizontalPaddingDp.dp)
                .padding(padding)
                .consumeWindowInsets(padding),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CinemaStagePlayer(
                    playerState = playerState,
                    uiState = uiState,
                    playbackActions = playbackActions,
                    engagementActions = engagementActions,
                    engagementState = engagementState,
                    onBack = onBack,
                    onHomeClick = onHomeClick,
                    bvid = bvid,
                    coverUrl = coverUrl,
                    onNavigateToAudioMode = onNavigateToAudioMode,
                    onToggleFullscreen = onToggleFullscreen,
                    onPortraitFullscreen = onPortraitFullscreen,
                    isInPipMode = isInPipMode,
                    onPipClick = onPipClick,
                    sleepTimerMinutes = sleepTimerMinutes,
                    viewPoints = viewPoints,
                    pbpProgressData = pbpProgressData,
                    isVerticalVideo = isVerticalVideo,
                    isPortraitFullscreen = isPortraitFullscreen,
                    currentCodec = currentCodec,
                    onCodecChange = onCodecChange,
                    currentSecondCodec = currentSecondCodec,
                    onSecondCodecChange = onSecondCodecChange,
                    currentAudioQuality = currentAudioQuality,
                    onAudioQualityChange = onAudioQualityChange,
                    transitionEnabled = transitionEnabled,
                    danmakuHostActive = danmakuHostActive,
                    currentPlayMode = currentPlayMode,
                    onPlayModeClick = onPlayModeClick,
                    onRelatedVideoClick = onRelatedVideoClick,
                    playerMaxWidth = policy.playerMaxWidthDp.dp,
                    forceCoverOnlyOnReturn = forceCoverOnlyOnReturn,
                    predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                    sponsorContributionState = sponsorContributionState,
                    liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                )

                if (success != null) {
                    CinemaMetaPanel(
                        success = success.withEngagementUiState(engagementState),
                        engagement = engagementState,
                        downloadProgress = downloadProgress,
                        videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
                        videoNoteEnabled = videoNoteEnabled,
                        videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
                        modifier = Modifier.weight(1f),
                        danmakuEnabled = danmakuChrome.enabled,
                        onDanmakuSendClick = playbackActions.showDanmakuSendDialog,
                        onDanmakuToggle = danmakuChrome.onToggle,
                        onFollowClick = engagementActions.toggleFollow,
                        onUpClick = onUpClick,
                        onFavoriteClick = { engagementActions.onFavoriteAction(false) },
                        onFavoriteLongClick = { engagementActions.onFavoriteAction(true) },
                        onLikeClick = engagementActions.toggleLike,
                        onCoinClick = engagementActions.openCoinDialog,
                        onTripleClick = engagementActions.doTripleAction,
                        onDownloadClick = playbackActions.openDownloadDialog,
                        onWatchLaterClick = engagementActions.toggleWatchLater,
                        onOpenComments = {
                            selectedTab = 0
                            curtainStateName = TabletSideCurtainState.OPEN.name
                        },
                        onCollectionEpisodeClick = onRelatedVideoClick,
                        onPageSelect = playbackActions.switchPage,
                        onOpenBilibiliLink = onOpenBilibiliLink,
                        onBgmClick = onBgmClick,
                        onRelatedVideoClick = onRelatedVideoClick,
                        onSearchKeywordClick = onSearchKeywordClick,
                        onRetryAiSummary = playbackActions.retryAiSummary,
                        onCreateNoteDraftFromAiSummary = playbackActions.createVideoNoteDraftFromAiSummary,
                        onOpenVideoNoteEditor = playbackActions.openVideoNoteEditor,
                        onCloseVideoNoteEditor = playbackActions.closeVideoNoteEditor,
                        onVideoNoteDocumentChange = playbackActions.updateVideoNoteEditorDocument,
                        onInsertVideoNoteTimestamp = playbackActions.insertCurrentPlaybackTimestampIntoNote,
                        onVideoNoteTimestampClick = playbackActions.seekTo,
                        onSaveVideoNote = playbackActions.saveVideoNote,
                        onDeleteVideoNote = playbackActions.deleteVideoNote,
                        onRetryVideoNote = playbackActions.retryVideoNote,
                        onShareVideo = openVideoShareSheet
                    )
                } else {
                    AppSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = AppShapes.container(ContainerLevel.Floating),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AdaptiveLoadingIndicator()
                        }
                    }
                }
            }

            CinemaSideCurtain(
                state = curtainState,
                width = curtainWidth,
                selectedTab = selectedTab,
                pagerState = curtainPagerState,
                onToggle = {
                    curtainStateName = when (curtainState) {
                        TabletSideCurtainState.OPEN -> TabletSideCurtainState.PEEK.name
                        TabletSideCurtainState.PEEK -> TabletSideCurtainState.OPEN.name
                        TabletSideCurtainState.HIDDEN -> TabletSideCurtainState.PEEK.name
                    }
                },
                onTabSelected = { tab ->
                    selectedTab = tab
                    curtainStateName = TabletSideCurtainState.OPEN.name
                },
                success = success,
                commentState = commentState,
                engagementState = engagementState,
                subReplyState = subReplyState,
                playbackActions = playbackActions,
                engagementActions = engagementActions,
                commentActions = commentActions,
                playerState = playerState,
                onUpClick = onUpClick,
                onRelatedVideoClick = onRelatedVideoClick,
                context = appContext,
                showUpBadge = showUpBadge,
                showIdentityDecorations = commentMemberDecorationsEnabled,
                onSearchKeywordClick = onSearchKeywordClick,
                onOpenBilibiliLink = onOpenBilibiliLink,
                onShareVideo = openVideoShareSheet
            )
        }
    }
}

@Composable
private fun CinemaStagePlayer(
    playerState: VideoPlayerState,
    uiState: VideoPlaybackUiState,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    engagementState: VideoEngagementUiState,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    bvid: String,
    coverUrl: String,
    onNavigateToAudioMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPortraitFullscreen: () -> Unit,
    isInPipMode: Boolean,
    onPipClick: () -> Unit,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    pbpProgressData: PbpProgressData?,
    isVerticalVideo: Boolean,
    isPortraitFullscreen: Boolean,
    currentCodec: String,
    onCodecChange: (String) -> Unit,
    currentSecondCodec: String,
    onSecondCodecChange: (String) -> Unit,
    currentAudioQuality: Int,
    onAudioQualityChange: (Int) -> Unit,
    transitionEnabled: Boolean,
    danmakuHostActive: Boolean,
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode,
    onPlayModeClick: () -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    playerMaxWidth: Dp,
    forceCoverOnlyOnReturn: Boolean,
    predictiveBackCancelRecoveryGeneration: Int,
    sponsorContributionState: SponsorContributionUiState,
    liveSurfaceCardTransitionEnabled: Boolean = true,
) {
    val success = uiState as? VideoPlaybackUiState.Success
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val sharedCoverShape = remember(sourceRoute) {
        RoundedCornerShape(resolveVideoSharedTransitionSourceCornerDp(sourceRoute).dp)
    }
    val playerContainerModifier = if (
        shouldEnableVideoCoverSharedTransition(
            transitionEnabled = transitionEnabled,
            hasSharedTransitionScope = sharedTransitionScope != null,
            hasAnimatedVisibilityScope = animatedVisibilityScope != null
        ) && !forceCoverOnlyOnReturn
    ) {
        with(requireNotNull(sharedTransitionScope)) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = com.android.purebilibili.core.ui.transition.videoCoverSharedElementKey(bvid)),
                animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                resizeMode = com.android.purebilibili.core.ui.transition
                    .resolveVideoCardSharedBoundsResizeMode(),
                clipInOverlayDuringTransition = OverlayClip(sharedCoverShape)
            )
        }
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        val playerViewportWidthDp = resolveCinemaPlayerViewportWidthDp(
            availableWidthDp = maxWidth.value.toInt(),
            playerMaxWidthDp = playerMaxWidth.value.toInt(),
        )
        val playerWidth = playerViewportWidthDp.dp
        val videoHeight = if (forceCoverOnlyOnReturn) {
            playerWidth / VIDEO_SHARED_COVER_ASPECT_RATIO
        } else {
            playerWidth * 9f / 16f
        }
        AppSurface(
            modifier = playerContainerModifier
                .align(Alignment.Center)
                .width(playerWidth)
                .height(videoHeight)
                .aspectRatio(playerWidth / videoHeight),
            shape = AppShapes.container(ContainerLevel.Floating),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 4.dp
        ) {
            VideoPlayerSection(
                state = VideoPlayerSectionState(
                    playerState = playerState,
                    uiState = uiState,
                    isFullscreen = false,
                    isInPipMode = isInPipMode,
                    danmakuHostActive = danmakuHostActive,
                    useTextureSurfaceForNavigation = resolveNavigationLiveSurfaceTextureEnabled(
                        cardTransitionEnabled = transitionEnabled,
                        liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                    ),
                    allowLivePlayerSharedElement = resolveAllowLivePlayerSharedElementForMorph(
                        cardTransitionEnabled = transitionEnabled,
                        liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                    ),
                    predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                    bvid = bvid,
                    coverUrl = coverUrl,
                    currentCdnIndex = success?.currentCdnIndex ?: 0,
                    cdnCount = success?.cdnCount ?: 1,
                    cdnLineDiagnostics = success?.cdnLineDiagnostics.orEmpty(),
                    isCdnProbing = success?.isCdnProbing ?: false,
                    isAudioOnly = false,
                    sleepTimerMinutes = sleepTimerMinutes,
                    videoshotData = success?.videoshotData,
                    viewPoints = viewPoints,
                    pbpProgressData = pbpProgressData,
                    isVerticalVideo = isVerticalVideo,
                    isPortraitFullscreen = isPortraitFullscreen,
                    viewportWidthDpOverride = playerViewportWidthDp,
                    currentCodec = currentCodec,
                    currentSecondCodec = currentSecondCodec,
                    currentAudioQuality = currentAudioQuality,
                    currentPlayMode = currentPlayMode,
                    relatedVideos = success?.related ?: emptyList(),
                    forceCoverOnly = forceCoverOnlyOnReturn,
                    ugcSeason = success?.info?.ugc_season,
                    isFollowed = engagementState.isFollowing,
                    isLiked = engagementState.isLiked,
                    isCoined = engagementState.coinCount > 0,
                    isFavorited = engagementState.isFavorited,
                    sponsorContributionState = sponsorContributionState,
                ),
                actions = VideoPlayerSectionActions(
                    onToggleFullscreen = onToggleFullscreen,
                    onQualityChange = playbackActions.changeQuality,
                    onBack = onBack,
                    onHomeClick = onHomeClick,
                    onDoubleTapLike = engagementActions.toggleLike,
                    onReloadVideo = playbackActions.reloadVideo,
                    onSwitchCdn = playbackActions.switchCdn,
                    onSwitchCdnTo = playbackActions.switchCdnTo,
                    onProbeCdnCandidates = playbackActions.probeCdnCandidates,
                    onAudioOnlyToggle = {
                        playbackActions.setAudioMode(true)
                        onNavigateToAudioMode()
                    },
                    onSleepTimerChange = playbackActions.setSleepTimer,
                    onPortraitFullscreen = onPortraitFullscreen,
                    onPipClick = onPipClick,
                    onCodecChange = onCodecChange,
                    onSecondCodecChange = onSecondCodecChange,
                    onAudioQualityChange = onAudioQualityChange,
                    onPlaybackSpeedChange = playbackActions.applyPlaybackSpeed,
                    onSaveCover = playbackActions.saveCover,
                    onDownloadAudio = playbackActions.downloadAudio,
                    onPlayModeClick = onPlayModeClick,
                    onRelatedVideoClick = onRelatedVideoClick,
                    onToggleFollow = engagementActions.toggleFollow,
                    onToggleLike = engagementActions.toggleLike,
                    onDislike = playbackActions.markVideoNotInterested,
                    onCoin = engagementActions.openCoinDialog,
                    onToggleFavorite = { engagementActions.onFavoriteAction(false) },
                    onTriple = engagementActions.doTripleAction,
                    onSubtitleTrackSelected = playbackActions.selectSubtitleTrack,
                    onDanmakuInputClick = playbackActions.showDanmakuSendDialog,
                    onSponsorContributionMarkBoundary = playbackActions.markSponsorContributionBoundary,
                    onSponsorContributionCategoryChange = playbackActions.setSponsorContributionCategory,
                    onSponsorContributionActionTypeChange = playbackActions.setSponsorContributionActionType,
                    onSponsorContributionSubmit = playbackActions.submitSponsorContribution,
                    onSponsorContributionCancel = playbackActions.cancelSponsorContribution,
                    onLikeDanmaku = playbackActions.likeDanmaku,
                    onRecallDanmaku = playbackActions.recallDanmaku,
                ),
            )
        }
    }
}

@Composable
private fun CinemaMetaPanel(
    success: VideoPlaybackUiState.Success,
    engagement: VideoEngagementUiState,
    downloadProgress: Float,
    videoAiSummaryEntryEnabled: Boolean,
    videoNoteEnabled: Boolean,
    videoNoteDefaultCollapsed: Boolean,
    modifier: Modifier = Modifier,
    danmakuEnabled: Boolean,
    onDanmakuSendClick: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onFollowClick: () -> Unit,
    onUpClick: (Long) -> Unit,
    onFavoriteClick: () -> Unit,
    onFavoriteLongClick: () -> Unit = {},
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onOpenComments: () -> Unit,
    onCollectionEpisodeClick: (String, android.os.Bundle?) -> Unit,
    onPageSelect: (Int) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    onBgmClick: (BgmInfo) -> Unit = {},
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> },
    onSearchKeywordClick: (String) -> Unit = {},
    onRetryAiSummary: () -> Unit,
    onCreateNoteDraftFromAiSummary: () -> Unit,
    onOpenVideoNoteEditor: () -> Unit,
    onCloseVideoNoteEditor: () -> Unit,
    onVideoNoteDocumentChange: (VideoNoteEditorDocument) -> Unit,
    onInsertVideoNoteTimestamp: () -> Unit,
    onVideoNoteTimestampClick: (Long) -> Unit,
    onSaveVideoNote: (VideoNoteEditorDocument) -> Unit,
    onDeleteVideoNote: () -> Unit,
    onRetryVideoNote: () -> Unit,
    onShareVideo: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val currentPageIndex = remember(success.info.cid, success.info.pages) {
        success.info.pages.indexOfFirst { it.cid == success.info.cid }.coerceAtLeast(0)
    }
    var showCollectionSheet by rememberSaveable(success.info.bvid) { mutableStateOf(false) }
    var confirmDeleteNote by rememberSaveable(success.info.bvid) { mutableStateOf(false) }
    val onShareVideoNote: (VideoNoteEditorDocument, Boolean) -> Unit = { document, isDraft ->
        ShareUtils.shareText(
            context = context,
            subject = document.title.ifBlank { success.info.title },
            text = buildVideoNoteShareText(
                videoTitle = success.info.title,
                bvid = success.info.bvid,
                document = document,
                isDraft = isDraft
            ),
            chooserTitle = "分享视频笔记"
        )
    }

    success.info.ugc_season?.let { season ->
        if (showCollectionSheet) {
            CollectionSheet(
                ugcSeason = season,
                currentBvid = success.info.bvid,
                currentCid = success.info.cid,
                onDismiss = { showCollectionSheet = false },
                onEpisodeClick = { episode ->
                    showCollectionSheet = false
                    val navOptions = buildVideoNavigationOptions(targetCid = episode.cid)
                    onCollectionEpisodeClick(episode.bvid, navOptions)
                }
            )
        }
    }

    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 0.dp),
        shape = AppShapes.container(ContainerLevel.Floating),
        color = resolveCinemaMetaPanelContainerColor(
            isDarkTheme = isDarkTheme,
            surfaceColor = MaterialTheme.colorScheme.surface
        )
    ) {
        val metaBlocks = remember(
            success.info.owner.mid,
            success.info.owner.name,
            success.info.ugc_season,
            success.info.pages.size
        ) {
            resolveCinemaMetaPanelBlocks(
                hasCollection = success.info.ugc_season != null,
                hasMultiplePages = success.info.pages.size > 1
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = metaBlocks,
                key = { it.name }
            ) { block ->
                when (block) {
                    CinemaMetaPanelBlock.ACTIONS -> {
                        if (success.info.owner.mid > 0L || success.info.owner.name.isNotBlank()) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val isWide = maxWidth >= 600.dp

                                AnimatedContent(
                                    targetState = isWide,
                                    label = "ActionUpInfoTransition"
                                ) { targetIsWide ->
                                    if (targetIsWide) {
                                        // 宽度足够，横排
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            CinemaMetaUpInfo(
                                                success = success,
                                                isFollowing = engagement.isFollowing,
                                                onFollowClick = onFollowClick,
                                                onUpClick = onUpClick,
                                                danmakuEnabled = danmakuEnabled,
                                                onDanmakuSendClick = onDanmakuSendClick,
                                                onDanmakuToggle = onDanmakuToggle,
                                                modifier = Modifier.weight(1f)
                                            )
                                            CinemaMetaActions(
                                                success = success,
                                                engagement = engagement,
                                                downloadProgress = downloadProgress,
                                                context = context,
                                                onFavoriteClick = onFavoriteClick,
                                                onFavoriteLongClick = onFavoriteLongClick,
                                                onLikeClick = onLikeClick,
                                                onCoinClick = onCoinClick,
                                                onTripleClick = onTripleClick,
                                                onDownloadClick = onDownloadClick,
                                                onWatchLaterClick = onWatchLaterClick,
                                                onOpenComments = onOpenComments,
                                                onShareClick = onShareVideo,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    } else {
                                        // 宽度不足，竖排
                                        Column(Modifier.fillMaxWidth()) {
                                            CinemaMetaUpInfo(
                                                success = success,
                                                isFollowing = engagement.isFollowing,
                                                onFollowClick = onFollowClick,
                                                onUpClick = onUpClick,
                                                danmakuEnabled = danmakuEnabled,
                                                onDanmakuSendClick = onDanmakuSendClick,
                                                onDanmakuToggle = onDanmakuToggle,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            CinemaMetaActions(
                                                success = success,
                                                engagement = engagement,
                                                downloadProgress = downloadProgress,
                                                context = context,
                                                onFavoriteClick = onFavoriteClick,
                                                onFavoriteLongClick = onFavoriteLongClick,
                                                onLikeClick = onLikeClick,
                                                onCoinClick = onCoinClick,
                                                onTripleClick = onTripleClick,
                                                onDownloadClick = onDownloadClick,
                                                onWatchLaterClick = onWatchLaterClick,
                                                onOpenComments = onOpenComments,
                                                onShareClick = onShareVideo,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            CinemaMetaActions(
                                success = success,
                                engagement = engagement,
                                downloadProgress = downloadProgress,
                                context = context,
                                onFavoriteClick = onFavoriteClick,
                                onLikeClick = onLikeClick,
                                onCoinClick = onCoinClick,
                                onTripleClick = onTripleClick,
                                onDownloadClick = onDownloadClick,
                                onWatchLaterClick = onWatchLaterClick,
                                onOpenComments = onOpenComments,
                                onShareClick = onShareVideo,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    CinemaMetaPanelBlock.INTRO -> {
                        CinemaVideoIntroSection(
                            success = success,
                            videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
                            videoNoteEnabled = videoNoteEnabled,
                            videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
                            onOpenBilibiliLink = onOpenBilibiliLink,
                            onBgmClick = onBgmClick,
                            onRelatedVideoClick = onRelatedVideoClick,
                            onSearchKeywordClick = onSearchKeywordClick,
                            onRetryAiSummary = onRetryAiSummary,
                            onCreateNoteDraftFromAiSummary = onCreateNoteDraftFromAiSummary,
                            onOpenVideoNoteEditor = onOpenVideoNoteEditor,
                            onRetryVideoNote = onRetryVideoNote,
                            onDeleteVideoNoteClick = { confirmDeleteNote = true },
                            onShareVideoNote = { document -> onShareVideoNote(document, false) },
                            onPublicVideoNoteClick = { _, url ->
                                if (url.isNotBlank()) onOpenBilibiliLink?.invoke(url)
                            }
                        )
                    }
                    CinemaMetaPanelBlock.COLLECTION -> {
                        success.info.ugc_season?.let { season ->
                            CollectionRow(
                                ugcSeason = season,
                                currentBvid = success.info.bvid,
                                currentCid = success.info.cid,
                                onClick = { showCollectionSheet = true }
                            )
                        }
                    }
                    CinemaMetaPanelBlock.PAGES -> {
                        if (success.info.pages.size > 1) {
                            PagesSelector(
                                pages = success.info.pages,
                                currentPageIndex = currentPageIndex,
                                onPageSelect = onPageSelect,
                                forceGridMode = true
                            )
                        }
                    }
                }
            }
        }
    }

    VideoNoteEditorSheet(
        noteState = success.videoNoteState,
        onDismiss = onCloseVideoNoteEditor,
        onDocumentChange = onVideoNoteDocumentChange,
        onInsertTimestamp = onInsertVideoNoteTimestamp,
        onTimestampClick = onVideoNoteTimestampClick,
        onShare = { document -> onShareVideoNote(document, success.videoNoteState.editorFromAiSummary) },
        onSave = onSaveVideoNote
    )

    VideoNoteDeleteConfirmDialog(
        visible = confirmDeleteNote,
        deleting = success.videoNoteState.deleting,
        onConfirm = {
            confirmDeleteNote = false
            onDeleteVideoNote()
        },
        onDismiss = { confirmDeleteNote = false }
    )
}

@Composable
private fun CinemaMetaActions(
    success: VideoPlaybackUiState.Success,
    engagement: VideoEngagementUiState,
    downloadProgress: Float,
    context: android.content.Context,
    onFavoriteClick: () -> Unit,
    onFavoriteLongClick: () -> Unit = {},
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onOpenComments: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ActionButtonsRow(
        info = success.info,
        isFavorited = engagement.isFavorited,
        isLiked = engagement.isLiked,
        coinCount = engagement.coinCount,
        downloadProgress = downloadProgress,
        isInWatchLater = engagement.isInWatchLater,
        onFavoriteClick = onFavoriteClick,
        onFavoriteLongClick = onFavoriteLongClick,
        onLikeClick = onLikeClick,
        onCoinClick = onCoinClick,
        onTripleClick = onTripleClick,
        onDownloadClick = onDownloadClick,
        onWatchLaterClick = onWatchLaterClick,
        onCommentClick = onOpenComments,
        onShareClick = onShareClick,
        showCommentAction = false,
        modifier = modifier
    )
}

@Composable
private fun CinemaMetaUpInfo(
    success: VideoPlaybackUiState.Success,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onUpClick: (Long) -> Unit,
    danmakuEnabled: Boolean,
    onDanmakuSendClick: () -> Unit,
    onDanmakuToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    UpInfoSection(
        info = success.info,
        isFollowing = isFollowing,
        onFollowClick = onFollowClick,
        onUpClick = onUpClick,
        followerCount = success.ownerFollowerCount,
        videoCount = success.ownerVideoCount,
        modifier = modifier,
        trailingContent = {
            TabletSecondaryDanmakuActions(
                danmakuEnabled = danmakuEnabled,
                onDanmakuSendClick = onDanmakuSendClick,
                onDanmakuToggle = onDanmakuToggle,
            )
        },
    )
}

@Composable
private fun CinemaVideoIntroSection(
    success: VideoPlaybackUiState.Success,
    videoAiSummaryEntryEnabled: Boolean,
    videoNoteEnabled: Boolean,
    videoNoteDefaultCollapsed: Boolean,
    onBgmClick: (BgmInfo) -> Unit = {},
    onOpenBilibiliLink: ((String) -> Unit)? = null,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> },
    onSearchKeywordClick: (String) -> Unit = {},
    onRetryAiSummary: () -> Unit = {},
    onCreateNoteDraftFromAiSummary: () -> Unit = {},
    onOpenVideoNoteEditor: () -> Unit = {},
    onRetryVideoNote: () -> Unit = {},
    onDeleteVideoNoteClick: () -> Unit = {},
    onShareVideoNote: (VideoNoteEditorDocument) -> Unit = {},
    onPublicVideoNoteClick: (Long, String) -> Unit = { _, _ -> }
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = AppShapes.container(ContainerLevel.Card),
            color = resolveCinemaIntroCardContainerColor(
                isDarkTheme = isDarkTheme,
                surfaceContainerLowColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            VideoTitleWithDesc(
                info = success.info,
                videoTags = success.videoTags,
                onDescriptionUrlClick = onOpenBilibiliLink,
                bgmList = resolveDisplayBgmList(
                    bgmInfo = success.bgmInfo,
                    bgmInfoList = success.bgmInfoList
                ),
                onBgmClick = onBgmClick,
                onRelatedVideoClick = onRelatedVideoClick,
                onTagClick = onSearchKeywordClick
            )
        }
        val showAiSummaryEntry = videoAiSummaryEntryEnabled &&
            (shouldShowAiSummaryEntry(
                aiSummary = success.aiSummary,
                isAiSummaryEntryEnabled = true
            ) || success.aiSummaryPrompt != null)
        val showNoteEntry = shouldShowVideoNoteCard(videoNoteEnabled)
        var showAiSummarySheet by remember { mutableStateOf(false) }
        var showNoteListSheet by remember { mutableStateOf(false) }
        VideoSupplementEntryRow(
            showAiSummary = showAiSummaryEntry,
            showNote = showNoteEntry,
            onAiSummaryClick = { showAiSummarySheet = true },
            onNoteClick = { showNoteListSheet = true }
        )
        AiSummarySheet(
            visible = showAiSummarySheet,
            aiSummary = success.aiSummary,
            promptState = success.aiSummaryPrompt,
            onDismiss = { showAiSummarySheet = false },
            onTimestampClick = null,
            onRetry = onRetryAiSummary,
            onCreateNoteDraft = {
                showAiSummarySheet = false
                onCreateNoteDraftFromAiSummary()
            }
        )
        VideoNoteListSheet(
            visible = showNoteListSheet,
            noteState = success.videoNoteState,
            isLoggedIn = success.isLoggedIn,
            onDismiss = { showNoteListSheet = false },
            onCreateOrEditClick = {
                showNoteListSheet = false
                onOpenVideoNoteEditor()
            },
            onRetryClick = onRetryVideoNote,
            onDeleteClick = {
                showNoteListSheet = false
                onDeleteVideoNoteClick()
            },
            onShareClick = onShareVideoNote,
            onPublicNoteClick = onPublicVideoNoteClick
        )
    }
}

@Composable
private fun CinemaSideCurtain(
    state: TabletSideCurtainState,
    width: Dp,
    selectedTab: Int,
    pagerState: PagerState,
    onToggle: () -> Unit,
    onTabSelected: (Int) -> Unit,
    success: VideoPlaybackUiState.Success?,
    commentState: CommentUiState,
    engagementState: VideoEngagementUiState,
    subReplyState: SubReplyUiState,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    playerState: VideoPlayerState,
    onUpClick: (Long) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    context: android.content.Context,
    showUpBadge: Boolean,
    showIdentityDecorations: Boolean,
    onSearchKeywordClick: (String) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    onShareVideo: () -> Unit = {}
) {
    val transition = updateTransition(targetState = state, label = "SideCurtainAnimation")
    LaunchedEffect(subReplyState.visible) {
        if (subReplyState.visible) {
            onTabSelected(0)
        }
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            if (transition.currentState == TabletSideCurtainState.OPEN) {
                animatePagerSelection(pagerState, selectedTab)
            } else {    // 动画中或关闭状态停用动画，避免卡动画
                pagerState.scrollToPage(selectedTab)
            }
        }
    }
    transition.AnimatedContent(
        modifier = Modifier.fillMaxHeight()
    ) { targetState ->
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppSurface(
                onClick = onToggle,
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight(),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = if (targetState == TabletSideCurtainState.OPEN) {
                            Icons.Outlined.KeyboardDoubleArrowRight
                        } else {
                            Icons.Outlined.KeyboardDoubleArrowLeft
                        },
                        contentDescription = "切换侧栏",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (targetState != TabletSideCurtainState.HIDDEN) {
                AppSurface(
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .animateContentSize(),
                    shape = AppShapes.container(ContainerLevel.Floating),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    if (targetState == TabletSideCurtainState.PEEK) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AppIconButton(onClick = { onTabSelected(0) }) {
                                AppIcon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "comments"
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            AppIconButton(onClick = { onTabSelected(1) }) {
                                AppIcon(
                                    imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                                    contentDescription = "related videos"
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TabletSecondaryLiquidTabRow(
                                    labels = listOf("评论", "相关推荐"),
                                    selectedIndex = pagerState.currentPage,
                                    onSelected = onTabSelected,
                                    indicatorPositionProvider = {
                                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                                    },
                                    isScrollInProgressProvider = { pagerState.isScrollInProgress },
                                )
                            }

                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalPriorityHorizontalPagerSwipe(
                                        state = pagerState,
                                        enabled = shouldEnableVideoContentHorizontalPagerSwipe(
                                            currentPage = pagerState.currentPage,
                                            commentPageIndex = 0,
                                            isPagerScrollInProgress = pagerState.isScrollInProgress,
                                        ),
                                    )
                            ) { page ->
                                when {
                                    success == null -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AdaptiveLoadingIndicator()
                                        }
                                    }

                                    page == 0 -> {
                                        CinemaCommentsPane(
                                            success = success,
                                            commentState = commentState,
                                            engagementState = engagementState,
                                            subReplyState = subReplyState,
                                            playbackActions = playbackActions,
                                            engagementActions = engagementActions,
                                            commentActions = commentActions,
                                            playerState = playerState,
                                            onUpClick = onUpClick,
                                            context = context,
                                            onRelatedVideoClick = onRelatedVideoClick,
                                            showIdentityDecorations = showIdentityDecorations,
                                            onSearchKeywordClick = onSearchKeywordClick,
                                            onOpenBilibiliLink = onOpenBilibiliLink,
                                            onShareVideo = onShareVideo
                                        )
                                    }

                                    else -> {
                                        CinemaRelatedPane(
                                            success = success,
                                            onRelatedVideoClick = onRelatedVideoClick,
                                            context = context,
                                            showUpBadge = showUpBadge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaCommentsPane(
    success: VideoPlaybackUiState.Success,
    commentState: CommentUiState,
    engagementState: VideoEngagementUiState,
    subReplyState: SubReplyUiState,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    playerState: VideoPlayerState,
    onUpClick: (Long) -> Unit,
    context: android.content.Context,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    showIdentityDecorations: Boolean,
    onSearchKeywordClick: (String) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    onShareVideo: () -> Unit = {}
) {
    val commentAppearance = rememberVideoCommentAppearance()
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val openCommentUrl: (String) -> Unit = openCommentUrl@{ rawUrl ->
        val url = rawUrl.trim()
        if (url.isEmpty()) return@openCommentUrl
        if (onOpenBilibiliLink != null) {
            onOpenBilibiliLink(url)
            return@openCommentUrl
        }
        when (val target = resolveCommentUrlNavigationTarget(url)) {
            is CommentUrlNavigationTarget.Video -> {
                onRelatedVideoClick(target.videoId, null)
                return@openCommentUrl
            }

            is CommentUrlNavigationTarget.Search -> {
                onSearchKeywordClick(target.keyword)
                return@openCommentUrl
            }

            is CommentUrlNavigationTarget.Space -> {
                onUpClick(target.mid)
                return@openCommentUrl
            }

            null -> Unit
        }
        runCatching { uriHandler.openUri(url) }
    }
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var sourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3 && !commentState.isRepliesLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            commentActions.loadComments()
        }
    }

    if (showImagePreview && previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            sourceRect = sourceRect?.rect,
            sourceCornerRadiusDp = sourceRect?.cornerRadiusDp
                ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
            textContent = previewTextContent,
            onDismiss = {
                showImagePreview = false
                previewTextContent = null
            }
        )
    }

    if (subReplyState.visible && subReplyState.rootReply != null) {
        VideoInlineSubReplyDetailContent(
            state = subReplyState,
            commentState = commentState,
            emoteMap = success.emoteMap,
            maxTimestampMs = success.videoDurationMs.takeIf { it > 0L },
            onLoadMore = commentActions.loadMoreSubReplies,
            onSortModeChange = commentActions.setSubReplySortMode,
            onDismiss = commentActions.closeSubReply,
            onRootCommentClick = playbackActions.openRootCommentComposer,
            onTimestampClick = { positionMs ->
                seekPlayerFromUserAction(playerState.player, positionMs)
            },
            onImagePreview = { images, index, rect, textContent ->
                previewImages = images
                previewInitialIndex = index
                sourceRect = rect
                previewTextContent = textContent
                showImagePreview = true
            },
            onReplyClick = playbackActions.replyTo,
            onConversationClick = commentActions.openSubReplyConversation,
            onConversationBack = commentActions.closeSubReplyConversation,
            onDissolveStart = commentActions.startSubDissolve,
            onDeleteComment = commentActions.deleteSubComment,
            onCheckCommentFraud = commentActions.checkCommentFraud,
            onCommentLike = commentActions.likeComment,
            onCommentHate = commentActions.hateComment,
            onReportComment = commentActions.reportComment,
            onUrlClick = openCommentUrl,
            showIdentityDecorations = showIdentityDecorations,
            onAvatarClick = { mid -> mid.toLongOrNull()?.let(onUpClick) ?: Unit }
        )
    } else {
        val commentChromeBackdrop = rememberLayerBackdrop()
        var showCommentSearchSheet by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxSize()) {
            CommentSortHeader(
                count = commentState.replyCount,
                sortMode = commentState.sortMode,
                onSortModeChange = { mode ->
                    commentActions.setSortMode(mode)
                    scope.launch {
                        SettingsManager.setCommentDefaultSortMode(context, mode.apiMode)
                    }
                },
                onSearchClick = { showCommentSearchSheet = true },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(commentChromeBackdrop),
                contentPadding = PaddingValues(bottom = 112.dp)
            ) {
            items(
                items = commentState.replies,
                key = { "curtain_reply_${it.rpid}" },
                contentType = { resolveReplyItemContentType(it) }
            ) { reply ->
                ReplyItemView(
                    item = reply,
                    upMid = success.info.owner.mid,
                    showUpFlag = commentState.showUpFlag,
                    showIdentityDecorations = showIdentityDecorations,
                    isPinned = reply.rpid in commentState.pinnedReplyIds,
                    emoteMap = success.emoteMap,
                    onClick = {},
                    onSubClick = commentActions.openSubReply,
                    onTimestampClick = { positionMs ->
                        seekPlayerFromUserAction(playerState.player, positionMs)
                    },
                    maxTimestampMs = success.videoDurationMs.takeIf { it > 0L },
                    onImagePreview = { images, index, rect, textContent ->
                        previewImages = images
                        previewInitialIndex = index
                        sourceRect = rect
                        previewTextContent = textContent
                        showImagePreview = true
                    },
                    onLikeClick = { commentActions.likeComment(reply.rpid) },
                    onHateClick = { commentActions.hateComment(reply.rpid) },
                    isLiked = reply.action == 1 || reply.rpid in commentState.likedComments,
                    isHated = reply.action == 2 || reply.rpid in commentState.hatedComments,
                    onReplyClick = { playbackActions.replyTo(reply) },
                    onReportClick = { reason -> commentActions.reportComment(reply.rpid, reason) },
                    canToggleTop = shouldShowReplyTopAction(
                        currentMid = commentState.currentMid,
                        upMid = success.info.owner.mid,
                        item = reply
                    ),
                    onToggleTopClick = { commentActions.toggleTopComment(reply) },
                    onDeleteClick = if (
                        commentState.currentMid > 0 && reply.mid == commentState.currentMid
                    ) {
                        { commentActions.startDissolve(reply.rpid) }
                    } else {
                        null
                    },
                    onUrlClick = openCommentUrl,
                    onAvatarClick = { mid ->
                        mid.toLongOrNull()?.let(onUpClick)
                    }
                )
            }
            if (commentState.isRepliesLoading && commentState.replies.isEmpty()) {
                item(key = "cinema_comment_skeleton") {
                    com.android.purebilibili.core.ui.skeleton.CommentListColumnSkeleton()
                }
            } else if (commentState.isRepliesLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AdaptiveLoadingIndicator()
                    }
                }
            }
            if (commentState.replies.isEmpty() && !commentState.isRepliesLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            text = "还没有评论，先看看相关推荐",
                            style = MaterialTheme.typography.bodyMedium,
                            color = commentAppearance.secondaryTextColor
                        )
                    }
                }
            }
            }

            BottomInputBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                isLiked = engagementState.isLiked,
                isFavorited = engagementState.isFavorited,
                isCoined = engagementState.coinCount > 0,
                onLikeClick = engagementActions.toggleLike,
                onFavoriteClick = { engagementActions.onFavoriteAction(false) },
                onFavoriteLongClick = { engagementActions.onFavoriteAction(true) },
                onCoinClick = engagementActions.openCoinDialog,
                onShareClick = onShareVideo,
                onCommentClick = playbackActions.openRootCommentComposer,
                backdrop = commentChromeBackdrop,
                isScrollInProgressProvider = { listState.isScrollInProgress },
                scrollPositionProvider = {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                },
                showActionButtons = false,
            )

            if (showCommentSearchSheet) {
                CommentSearchSheet(
                    replies = commentState.replies,
                    upMid = success.info.owner.mid,
                    onCommentClick = { reply ->
                        playbackActions.replyTo(reply)
                    },
                    onSubReplyClick = { rootReply ->
                        commentActions.openSubReply(rootReply, 0L)
                    },
                    onDismiss = { showCommentSearchSheet = false },
                    miuixBackdrop = commentChromeBackdrop,
                )
            }

            }
        }
    }
}

@Composable
private fun CinemaRelatedPane(
    success: VideoPlaybackUiState.Success,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    context: android.content.Context,
    showUpBadge: Boolean
) {
    var hiddenRelatedBvids by remember(success.info.bvid) { mutableStateOf(emptySet<String>()) }
    val visibleRelatedVideos = remember(success.related, hiddenRelatedBvids) {
        filterRelatedVideosByHiddenBvids(success.related, hiddenRelatedBvids)
    }
    val relatedVideoCardLayout = rememberRelatedVideoCardLayout()
    val listState = rememberLazyListState()
    val showBackToTop by remember(listState) {
        derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }
    val backToTopButtonEnabled = rememberBackToTopButtonEnabled()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            val relatedRows = chunkRelatedVideosForHomeStyleGrid(visibleRelatedVideos)
            itemsIndexed(
                items = relatedRows,
                key = { rowIndex, row ->
                    val first = row.firstOrNull()
                    resolveIndexedVideoLazyKey(
                        namespace = "cinema_related_row",
                        index = rowIndex,
                        bvid = first?.bvid.orEmpty(),
                        aid = first?.aid ?: 0L,
                        cid = first?.cid ?: 0L
                    )
                }
            ) { _, row ->
                CompositionLocalProvider(
                    LocalVideoCardSharedElementSourceRoute provides "video/${success.info.bvid}"
                ) {
                    RelatedVideoGridRow(
                        videos = row,
                        cardLayout = relatedVideoCardLayout,
                        followingMids = success.followingMids,
                        showUpBadge = showUpBadge,
                        onVideoClick = { video ->
                            val navOptions = buildVideoNavigationOptions(
                                targetCid = video.cid,
                                coverUrl = video.pic,
                            ) ?: android.os.Bundle.EMPTY
                            onRelatedVideoClick(video.bvid, navOptions)
                        },
                        onVideoHidden = { video ->
                            hiddenRelatedBvids = hiddenRelatedBvids + video.bvid
                        }
                    )
                }
            }
            if (visibleRelatedVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            text = "暂时没有推荐视频",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AppLiquidGlassBackToTopButton(
            visible = backToTopButtonEnabled && showBackToTop,
            onClick = {
                scope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}
