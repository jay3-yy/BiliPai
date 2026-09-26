// 文件路径: feature/video/screen/TabletVideoLayout.kt
package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp // Add this back
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSplitLayout
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.feature.video.share.VideoSharePayload
import com.android.purebilibili.feature.video.share.VideoShareSheetHost
import com.android.purebilibili.feature.video.share.buildVideoSharePayload
import com.android.purebilibili.core.util.ShareUtils
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.components.*
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.note.buildVideoNoteShareText
import com.android.purebilibili.feature.video.note.shouldShowVideoNoteCard
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.AiSummarySheet
import com.android.purebilibili.feature.video.ui.section.VideoNoteListSheet
import com.android.purebilibili.feature.video.ui.section.VideoSupplementEntryRow
import com.android.purebilibili.feature.video.ui.section.VideoNoteDeleteConfirmDialog
import com.android.purebilibili.feature.video.ui.section.VideoNoteEditorSheet
import com.android.purebilibili.feature.video.ui.section.resolveDisplayBgmList
import com.android.purebilibili.feature.video.ui.section.shouldShowAiSummaryEntry
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionActions
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSectionState
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.resolveAllowLivePlayerSharedElementForMorph
import com.android.purebilibili.feature.video.ui.section.resolveNavigationLiveSurfaceTextureEnabled
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import kotlinx.coroutines.launch

//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoSharedTransitionSpeedSettings
import com.android.purebilibili.core.ui.transition.LocalVideoTransitionAdaptiveInfo
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO
import com.android.purebilibili.core.ui.transition.resolveVideoCardSharedTransitionMotionSpec
import com.android.purebilibili.core.ui.transition.resolveVideoSharedTransitionSourceCornerDp
import com.android.purebilibili.core.ui.transition.videoSharedElementBoundsTransformSpec
import com.android.purebilibili.feature.video.viewmodel.withEngagementUiState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

internal enum class TabletSecondaryTab(val label: String) {
    COMMENTS("评论"),
    INTRO("简介"),
    RELATED("相关推荐"),
    COLLECTION("合集"),
    OWNER_UPLOADS("UP 投稿")
}


@Composable
internal fun TabletSecondaryDanmakuActions(
    danmakuEnabled: Boolean,
    onDanmakuSendClick: () -> Unit,
    onDanmakuToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = configuration.screenWidthDp)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = layoutPolicy.sendLabel,
            fontSize = layoutPolicy.sendTextSizeSp.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            tapToCopyEnabled = false,
            modifier = Modifier
                .heightIn(min = layoutPolicy.sendMinHeightDp.dp)
                .wrapContentHeight(align = Alignment.CenterVertically)
                .clickable(onClick = onDanmakuSendClick),
        )
        NativeDanmakuToggleButton(
            enabled = danmakuEnabled,
            onToggle = onDanmakuToggle,
            activeTint = MaterialTheme.colorScheme.secondary,
            inactiveTint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(end = layoutPolicy.toggleTrailingPaddingDp.dp)
                .size(layoutPolicy.toggleButtonSizeDp.dp),
            iconSize = layoutPolicy.toggleIconSizeDp.dp,
        )
    }
}


@Composable
internal fun TabletSecondaryLiquidTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    indicatorPositionProvider: () -> Float,
    isScrollInProgressProvider: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    BoxWithConstraints(modifier = modifier) {
        val minimumScrollableWidth = (labels.size * 76).dp
        val needsHorizontalScroll = maxWidth < minimumScrollableWidth
        val scrollState = rememberScrollState()
        Box(
            modifier = if (needsHorizontalScroll) {
                Modifier
                    .horizontalScroll(scrollState)
                    .width(minimumScrollableWidth)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            BottomBarLiquidSegmentedControl(
                items = labels,
                selectedIndex = selectedIndex,
                onSelected = onSelected,
                modifier = Modifier.fillMaxWidth(),
                itemWidth = if (needsHorizontalScroll) 76.dp else null,
                height = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp,
                indicatorHeight = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp.dp,
                labelFontSize = 15.sp,
                liquidGlassEffectsEnabled = liquidGlassEnabled,
                equalizeMiuixNonGlassItemWidths = false,
                allowNativeLabelOverflow = true,
                forceEqualWidth = !needsHorizontalScroll,
                compactMiuixWhenTwoOptions = false,
                dragSelectionEnabled = true,
                tapPressRefractionEnabled = true,
                indicatorPositionProvider = indicatorPositionProvider,
                isScrollInProgressProvider = isScrollInProgressProvider,
                externalPagerMotionEffectsEnabled = true,
            )
        }
    }
}

/**
 * 🖥️ 平板端视频详情页布局
 * 
 * 左右分栏布局：
 * - 左侧：视频播放器 + 视频信息
 * - 右侧：评论 / 相关推荐（可切换）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TabletVideoLayout(
    playerState: VideoPlayerState,
    uiState: VideoPlaybackUiState,
    commentState: CommentUiState,
    engagementState: VideoEngagementUiState,
    subReplyState: SubReplyUiState,
    downloadProgress: Float,
    commentMemberDecorationsEnabled: Boolean,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    configuration: Configuration,
    isVerticalVideo: Boolean,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    pbpProgressData: PbpProgressData? = null,
    bvid: String,
    coverUrl: String = "",
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    onNavigateToAudioMode: () -> Unit,
    onToggleFullscreen: () -> Unit,  // 📺 全屏切换回调
    onPortraitFullscreen: () -> Unit,
    isInPipMode: Boolean,
    onPipClick: () -> Unit,
    isPortraitFullscreen: Boolean = false,
    onHomeClick: () -> Unit,

    // [New] Codec & Audio Params
    currentCodec: String = "hev1", 
    onCodecChange: (String) -> Unit = {},
    currentSecondCodec: String = "avc1",
    onSecondCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    onAudioQualityChange: (Int) -> Unit = {},
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onBgmClick: (BgmInfo) -> Unit = {},
    showUpBadge: Boolean = true,
    onSearchKeywordClick: (String) -> Unit = {},
    onOpenBilibiliLink: ((String) -> Unit)? = null,
    // 🔁 [新增] 播放模式
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode = com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
    onPlayModeClick: () -> Unit = {},
    forceCoverOnlyOnReturn: Boolean = false,
    predictiveBackCancelRecoveryGeneration: Int = 0,
    liveSurfaceCardTransitionEnabled: Boolean = true,
    paneControlsEnabled: Boolean = true,
    videoAiSummaryEntryEnabled: Boolean = true,
    videoNoteEnabled: Boolean = true,
    videoNoteDefaultCollapsed: Boolean = true,
    playerContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val adaptiveInfo = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current
    val foldHalfOpened = adaptiveInfo.posture == com.android.purebilibili.core.util.AppFoldPosture.Book ||
        adaptiveInfo.posture == com.android.purebilibili.core.util.AppFoldPosture.Tabletop
    val layoutPolicy = remember(configuration.screenWidthDp, adaptiveInfo.posture) {
        resolveTabletVideoLayoutPolicy(
            widthDp = configuration.screenWidthDp,
            foldPosture = adaptiveInfo.posture,
        )
    }
    var secondaryPaneModeName by rememberSaveable(bvid) {
        mutableStateOf(TabletSecondaryPaneMode.EXPANDED.name)
    }
    var requestedSecondaryTabName by rememberSaveable(bvid) { mutableStateOf<String?>(null) }
    val secondaryPaneMode = remember(secondaryPaneModeName) {
        runCatching { TabletSecondaryPaneMode.valueOf(secondaryPaneModeName) }
            .getOrDefault(TabletSecondaryPaneMode.EXPANDED)
    }
    val primaryRatio = resolveTabletPrimaryRatio(
        basePrimaryRatio = layoutPolicy.primaryRatio,
        secondaryPaneMode = secondaryPaneMode
    )
    val useThreePaneLayout = LocalWindowSizeClass.current.shouldUseThreePaneLayout &&
        !layoutPolicy.useTabletopLayout
    val secondaryPaneHidden = shouldHideTabletSecondaryPane(
        paneMode = secondaryPaneMode,
        useThreePaneLayout = useThreePaneLayout,
        useTabletopLayout = layoutPolicy.useTabletopLayout,
    )
    val danmakuChrome = rememberTabletDanmakuChromeState(bvid)
    var pendingVideoShare by remember { mutableStateOf<VideoSharePayload?>(null) }
    
    // 🖥️ [修复] 使用 LocalContext 获取 Activity，而非 playerState.context
    val context = LocalContext.current
    val secondaryDefaultTab by SettingsManager
        .getTabletSecondaryDefaultTab(context)
        .collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.TabletSecondaryDefaultTab.RELATED)
    val activity = remember(context) {
        (context as? android.app.Activity)
            ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
    }
    
    AppSplitLayout(
        secondaryPaneVisible = !secondaryPaneHidden,
        primaryContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                // 📹 左侧：播放器 + 视频信息（可滚动）
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                // 视频播放器（固定高度，不参与滚动）
                
                //  尝试获取共享元素作用域
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
                val sharedCoverShape = remember(sourceRoute) {
                    RoundedCornerShape(resolveVideoSharedTransitionSourceCornerDp(sourceRoute).dp)
                }
                val sharedTransitionSpeedSettings = LocalVideoSharedTransitionSpeedSettings.current
                val transitionAdaptiveInfo = LocalVideoTransitionAdaptiveInfo.current
                val sharedTransitionMotionSpec = remember(
                    sourceRoute,
                    transitionEnabled,
                    sharedTransitionSpeedSettings,
                    transitionAdaptiveInfo,
                ) {
                    resolveVideoCardSharedTransitionMotionSpec(
                        sourceRoute = sourceRoute,
                        transitionEnabled = transitionEnabled,
                        speedSettings = sharedTransitionSpeedSettings,
                        adaptiveInfo = transitionAdaptiveInfo,
                    )
                }
                
                //  为播放器容器添加共享元素标记（受开关控制）
                val playerContainerModifier = if (
                    transitionEnabled &&
                    !foldHalfOpened &&
                    sharedTransitionScope != null &&
                    animatedVisibilityScope != null &&
                    !forceCoverOnlyOnReturn
                ) {
                    with(requireNotNull(sharedTransitionScope)) {
                        Modifier
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = com.android.purebilibili.core.ui.transition.videoCoverSharedElementKey(bvid)),
                                animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                boundsTransform = { initialBounds, targetBounds ->
                                    if (sharedTransitionMotionSpec.enabled) {
                                        videoSharedElementBoundsTransformSpec(
                                            motion = sharedTransitionMotionSpec,
                                            initialBounds = initialBounds,
                                            targetBounds = targetBounds,
                                            durationMillis = sharedTransitionMotionSpec.durationMillis,
                                        )
                                    } else {
                                        com.android.purebilibili.core.ui.motion.AppMotionTokens.spatialSpec()
                                    }
                                },
                                clipInOverlayDuringTransition = OverlayClip(sharedCoverShape)
                            )
                    }
                } else {
                    Modifier
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val playerWidth = if (layoutPolicy.useTabletopLayout) {
                        minOf(maxWidth, maxHeight * 16f / 9f, layoutPolicy.playerMaxWidthDp.dp)
                    } else {
                        minOf(maxWidth, layoutPolicy.playerMaxWidthDp.dp)
                    }
                    val videoHeight = if (forceCoverOnlyOnReturn) {
                        playerWidth / VIDEO_SHARED_COVER_ASPECT_RATIO
                    } else {
                        playerWidth * 9f / 16f
                    }
                    Box(
                        modifier = playerContainerModifier
                            .width(playerWidth)
                            .height(videoHeight)
                            .align(Alignment.Center)
                            .background(MaterialTheme.colorScheme.scrim)
                    ) {
                        if (playerContent != null) {
                            playerContent(Modifier.fillMaxSize())
                        } else {
                            VideoPlayerSection(
                                state = VideoPlayerSectionState(
                                    playerState = playerState,
                                    uiState = uiState,
                                    isFullscreen = false,
                                    isInPipMode = isInPipMode,
                                    useTextureSurfaceForNavigation = resolveNavigationLiveSurfaceTextureEnabled(
                                        cardTransitionEnabled = transitionEnabled && !foldHalfOpened,
                                        liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                                    ),
                                    allowLivePlayerSharedElement = !foldHalfOpened &&
                                        resolveAllowLivePlayerSharedElementForMorph(
                                            cardTransitionEnabled = transitionEnabled,
                                            liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
                                        ),
                                    predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                                    bvid = bvid,
                                    coverUrl = coverUrl,
                                    cdnCount = (uiState as? VideoPlaybackUiState.Success)?.cdnCount ?: 1,
                                    cdnLineDiagnostics = (uiState as? VideoPlaybackUiState.Success)
                                        ?.cdnLineDiagnostics.orEmpty(),
                                    isCdnProbing = (uiState as? VideoPlaybackUiState.Success)?.isCdnProbing ?: false,
                                    isAudioOnly = false,
                                    sleepTimerMinutes = sleepTimerMinutes,
                                    videoshotData = (uiState as? VideoPlaybackUiState.Success)?.videoshotData,
                                    viewPoints = viewPoints,
                                    pbpProgressData = pbpProgressData,
                                    isVerticalVideo = isVerticalVideo,
                                    isPortraitFullscreen = isPortraitFullscreen,
                                    currentCodec = currentCodec,
                                    currentSecondCodec = currentSecondCodec,
                                    currentAudioQuality = currentAudioQuality,
                                    currentPlayMode = currentPlayMode,
                                    viewportWidthDpOverride = playerWidth.value.toInt(),
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
                                    onSubtitleTrackSelected = playbackActions.selectSubtitleTrack,
                                    onDanmakuInputClick = playbackActions.showDanmakuSendDialog,
                                    onLikeDanmaku = playbackActions.likeDanmaku,
                                    onRecallDanmaku = playbackActions.recallDanmaku,
                                ),
                            )
                        }
                    }
                }
                
                // Tabletop 把简介移到铰链下方；普通平板仍在播放器下方展示。
                if (uiState is VideoPlaybackUiState.Success && !layoutPolicy.useTabletopLayout) {
                    TabletVideoInfoPane(
                        success = uiState,
                        engagementState = engagementState,
                        downloadProgress = downloadProgress,
                        playbackActions = playbackActions,
                        engagementActions = engagementActions,
                        onBgmClick = onBgmClick,
                        onRelatedVideoClick = onRelatedVideoClick,
                        onOpenBilibiliLink = onOpenBilibiliLink,
                        danmakuEnabled = danmakuChrome.enabled,
                        onDanmakuSendClick = playbackActions.showDanmakuSendDialog,
                        onDanmakuToggle = danmakuChrome.onToggle,
                        onOwnerUploadsClick = {
                            requestedSecondaryTabName = TabletSecondaryTab.OWNER_UPLOADS.name
                            secondaryPaneModeName = TabletSecondaryPaneMode.EXPANDED.name
                        },
                        videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
                        videoNoteEnabled = videoNoteEnabled,
                        videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
                        showRelatedVideos = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = layoutPolicy.infoMaxWidthDp.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    },
        secondaryContent = {
            // 📝 右侧：评论 / 相关推荐
            if (uiState is VideoPlaybackUiState.Success) {
                val success = uiState
                Box(modifier = Modifier.fillMaxSize()) {
                    TabletSecondaryContent(
                        success = success,
                        commentState = commentState,
                        subReplyState = subReplyState,
                        playbackActions = playbackActions,
                        engagementState = engagementState,
                        engagementActions = engagementActions,
                        commentActions = commentActions,
                        playerState = playerState,
                        onUpClick = onUpClick,
                        paneMode = secondaryPaneMode,
                        onPaneModeChange = { secondaryPaneModeName = it.name },
                        onRelatedVideoClick = onRelatedVideoClick,
                        onSearchKeywordClick = onSearchKeywordClick,
                        showUpBadge = showUpBadge,
                        showIdentityDecorations = commentMemberDecorationsEnabled,
                        onOpenBilibiliLink = onOpenBilibiliLink,
                        requestedTabName = requestedSecondaryTabName,
                        onRequestedTabConsumed = { requestedSecondaryTabName = null },
                        danmakuEnabled = danmakuChrome.enabled,
                        onDanmakuSendClick = playbackActions.showDanmakuSendDialog,
                        onDanmakuToggle = danmakuChrome.onToggle,
                        fixedTab = if (useThreePaneLayout) TabletSecondaryTab.COMMENTS else null,
                        relatedTabFirst = secondaryDefaultTab ==
                            com.android.purebilibili.core.store.TabletSecondaryDefaultTab.RELATED,
                        introContent = if (layoutPolicy.useTabletopLayout) {
                            {
                                TabletVideoInfoPane(
                                    success = success,
                                    engagementState = engagementState,
                                    downloadProgress = downloadProgress,
                                    playbackActions = playbackActions,
                                    engagementActions = engagementActions,
                                    onBgmClick = onBgmClick,
                                    onRelatedVideoClick = onRelatedVideoClick,
                                    onOpenBilibiliLink = onOpenBilibiliLink,
                                    danmakuEnabled = danmakuChrome.enabled,
                                    onDanmakuSendClick = playbackActions.showDanmakuSendDialog,
                                    onDanmakuToggle = danmakuChrome.onToggle,
                                    onOwnerUploadsClick = {
                                        requestedSecondaryTabName = TabletSecondaryTab.OWNER_UPLOADS.name
                                    },
                                    videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
                                    videoNoteEnabled = videoNoteEnabled,
                                    videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else null,
                    )
                }
            }
        },
        tertiaryContent = if (useThreePaneLayout) {
            {
                if (uiState is VideoPlaybackUiState.Success) {
                    TabletSecondaryContent(
                        success = uiState,
                        commentState = commentState,
                        subReplyState = subReplyState,
                        playbackActions = playbackActions,
                        engagementState = engagementState,
                        engagementActions = engagementActions,
                        commentActions = commentActions,
                        playerState = playerState,
                        onUpClick = onUpClick,
                        paneMode = TabletSecondaryPaneMode.EXPANDED,
                        onPaneModeChange = {},
                        onRelatedVideoClick = onRelatedVideoClick,
                        onSearchKeywordClick = onSearchKeywordClick,
                        showUpBadge = showUpBadge,
                        showIdentityDecorations = commentMemberDecorationsEnabled,
                        onOpenBilibiliLink = onOpenBilibiliLink,
                        requestedTabName = null,
                        onRequestedTabConsumed = {},
                        fixedTab = TabletSecondaryTab.RELATED,
                        introContent = null,
                    )
                }
            }
        } else null,
        primaryRatio = primaryRatio
    )
}

@Composable
internal fun TabletVideoInfoPane(
    success: VideoPlaybackUiState.Success,
    engagementState: VideoEngagementUiState,
    downloadProgress: Float,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    onBgmClick: (BgmInfo) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    danmakuEnabled: Boolean,
    onDanmakuSendClick: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onOwnerUploadsClick: () -> Unit,
    videoAiSummaryEntryEnabled: Boolean = true,
    videoNoteEnabled: Boolean = true,
    videoNoteDefaultCollapsed: Boolean = true,
    modifier: Modifier = Modifier,
    showRelatedVideos: Boolean = true,
) {
    val context = LocalContext.current
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
    val engagementSuccess = success.withEngagementUiState(engagementState)
    val currentPageIndex = success.info.pages
        .indexOfFirst { it.cid == success.info.cid }
        .coerceAtLeast(0)
    var pendingVideoShare by remember { mutableStateOf<VideoSharePayload?>(null) }
    VideoShareSheetHost(
        payload = pendingVideoShare,
        onDismiss = { pendingVideoShare = null },
    )
    ScrollableVideoInfoSection(
        info = engagementSuccess.info,
        isFollowing = engagementState.isFollowing,
        isFavorited = engagementState.isFavorited,
        isLiked = engagementState.isLiked,
        coinCount = engagementState.coinCount,
        currentPageIndex = currentPageIndex,
        downloadProgress = downloadProgress,
        isInWatchLater = engagementState.isInWatchLater,
        onShareClick = {
            pendingVideoShare = buildVideoSharePayload(
                title = success.info.title,
                bvid = success.info.bvid,
                coverUrl = success.info.pic,
                upName = success.info.owner.name,
                playCountText = com.android.purebilibili.core.util.FormatUtils
                    .formatStat(success.info.stat.view.toLong()),
            )
        },
        videoTags = success.videoTags,
        ownerFollowerCount = success.ownerFollowerCount,
        ownerVideoCount = success.ownerVideoCount,
        bgmInfo = success.bgmInfo,
        bgmInfoList = success.bgmInfoList,
        onBgmClick = onBgmClick,
        relatedVideos = if (showRelatedVideos) success.related else emptyList(),
        showRelatedVideos = showRelatedVideos,
        onFollowClick = engagementActions.toggleFollow,
        onFavoriteClick = { engagementActions.onFavoriteAction(false) },
        onFavoriteLongClick = { engagementActions.onFavoriteAction(true) },
        onLikeClick = engagementActions.toggleLike,
        onCoinClick = engagementActions.openCoinDialog,
        onTripleClick = engagementActions.doTripleAction,
        onPageSelect = playbackActions.switchPage,
        onUpClick = { onOwnerUploadsClick() },
        onDownloadClick = playbackActions.openDownloadDialog,
        onWatchLaterClick = engagementActions.toggleWatchLater,
        onRelatedVideoClick = onRelatedVideoClick,
        onOpenBilibiliLink = onOpenBilibiliLink,
        aiSummary = success.aiSummary,
        aiSummaryPrompt = success.aiSummaryPrompt,
        videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
        onRetryAiSummary = playbackActions.retryAiSummary,
        onCreateNoteDraftFromAiSummary = playbackActions.createVideoNoteDraftFromAiSummary,
        onTimestampClick = { timestamp -> playbackActions.seekTo(timestamp) },
        videoNoteState = success.videoNoteState,
        isLoggedIn = success.isLoggedIn,
        videoNoteEnabled = videoNoteEnabled,
        videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
        onOpenVideoNoteEditor = playbackActions.openVideoNoteEditor,
        onRetryVideoNote = playbackActions.retryVideoNote,
        onDeleteVideoNoteClick = { confirmDeleteNote = true },
        onShareVideoNote = { document -> onShareVideoNote(document, false) },
        onPublicVideoNoteClick = { cvid, _ ->
            onOpenBilibiliLink?.invoke("https://www.bilibili.com/read/cv$cvid")
        },
        modifier = modifier,
    )

    VideoNoteEditorSheet(
        noteState = success.videoNoteState,
        onDismiss = playbackActions.closeVideoNoteEditor,
        onDocumentChange = playbackActions.updateVideoNoteEditorDocument,
        onInsertTimestamp = playbackActions.insertCurrentPlaybackTimestampIntoNote,
        onTimestampClick = { timestamp -> playbackActions.seekTo(timestamp) },
        onShare = { document -> onShareVideoNote(document, success.videoNoteState.editorFromAiSummary) },
        onSave = playbackActions.saveVideoNote
    )

    VideoNoteDeleteConfirmDialog(
        visible = confirmDeleteNote,
        deleting = success.videoNoteState.deleting,
        onConfirm = {
            confirmDeleteNote = false
            playbackActions.deleteVideoNote()
        },
        onDismiss = { confirmDeleteNote = false }
    )
}

/**
 * 📝 平板右侧内容区域（评论/推荐切换）
 */
@Composable
internal fun TabletSecondaryContent(
    success: VideoPlaybackUiState.Success,
    commentState: CommentUiState,
    subReplyState: SubReplyUiState,
    playbackActions: VideoDetailPlaybackActions,
    engagementState: VideoEngagementUiState,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    playerState: VideoPlayerState,
    onUpClick: (Long) -> Unit,
    paneMode: TabletSecondaryPaneMode,
    onPaneModeChange: (TabletSecondaryPaneMode) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    showUpBadge: Boolean,
    showIdentityDecorations: Boolean,
    onSearchKeywordClick: (String) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    requestedTabName: String?,
    onRequestedTabConsumed: () -> Unit,
    fixedTab: TabletSecondaryTab? = null,
    introContent: (@Composable () -> Unit)? = null,
    applyStatusBarPadding: Boolean = true,
    includeRelatedTab: Boolean = true,
    includeOwnerUploadsTab: Boolean = true,
    relatedTabFirst: Boolean = false,
    danmakuEnabled: Boolean = true,
    onDanmakuSendClick: () -> Unit = {},
    onDanmakuToggle: () -> Unit = {},
) {
    val commentAppearance = rememberVideoCommentAppearance()
    var pendingVideoShare by remember { mutableStateOf<VideoSharePayload?>(null) }
    val tabs = remember(
        success.info.ugc_season,
        success.info.owner.mid,
        fixedTab,
        introContent != null,
        includeRelatedTab,
        includeOwnerUploadsTab,
        relatedTabFirst,
    ) {
        if (fixedTab != null) {
            listOf(requireNotNull(fixedTab))
        } else {
            buildList {
                if (relatedTabFirst && includeRelatedTab) add(TabletSecondaryTab.RELATED)
                add(TabletSecondaryTab.COMMENTS)
                if (introContent != null) add(TabletSecondaryTab.INTRO)
                if (!relatedTabFirst && includeRelatedTab) add(TabletSecondaryTab.RELATED)
                if (success.info.ugc_season != null) add(TabletSecondaryTab.COLLECTION)
                if (includeOwnerUploadsTab && success.info.owner.mid > 0L) {
                    add(TabletSecondaryTab.OWNER_UPLOADS)
                }
            }
        }
    }
    val relatedTabIndex = tabs.indexOf(TabletSecondaryTab.RELATED).coerceAtLeast(0)
    var selectedTab by rememberSaveable(success.info.bvid, fixedTab, relatedTabFirst) {
        mutableIntStateOf(
            if (fixedTab != null) 0 else resolveTabletSecondaryDefaultTabIndex(
                tabs = tabs,
                preferRelated = relatedTabFirst,
            )
        )
    }
    val pagerState = rememberPagerState(
        initialPage = selectedTab,
        pageCount = { tabs.size }
    )
    
    // 评论图片预览状态
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var sourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val latestOnRequestedTabConsumed by rememberUpdatedState(onRequestedTabConsumed)
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            animatePagerSelection(pagerState, selectedTab)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) {
            selectedTab = pagerState.currentPage
        }
    }
    LaunchedEffect(requestedTabName, tabs) {
        if (fixedTab != null) return@LaunchedEffect
        val requestedTab = requestedTabName?.let { name ->
            TabletSecondaryTab.entries.firstOrNull { it.name == name }
        }
        if (requestedTab != null) {
            val index = tabs.indexOf(requestedTab)
            if (index >= 0) selectedTab = index
            latestOnRequestedTabConsumed()
        }
    }
    LaunchedEffect(subReplyState.visible) {
        if (subReplyState.visible && fixedTab != TabletSecondaryTab.RELATED) {
            resolveTabletCommentTabIndex(tabs)
                .takeIf { it >= 0 }
                ?.let { selectedTab = it }
            if (paneMode == TabletSecondaryPaneMode.COLLAPSED) {
                onPaneModeChange(TabletSecondaryPaneMode.COMPACT)
            }
        }
    }
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
    
    // 图片预览对话框
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

    if (fixedTab == null && paneMode == TabletSecondaryPaneMode.COLLAPSED) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppTextButton(onClick = { onPaneModeChange(TabletSecondaryPaneMode.COMPACT) }) {
                AppText("半开")
            }
            AppTextButton(onClick = { onPaneModeChange(TabletSecondaryPaneMode.EXPANDED) }) {
                AppText("展开")
            }
            Spacer(modifier = Modifier.height(8.dp))
            tabs.forEachIndexed { index, tab ->
                AppTextButton(onClick = {
                    selectedTab = index
                    onPaneModeChange(TabletSecondaryPaneMode.COMPACT)
                }) {
                    AppText(tab.label)
                }
            }
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (fixedTab == null && tabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabletSecondaryLiquidTabRow(
                    labels = tabs.map { it.label },
                    selectedIndex = pagerState.currentPage,
                    onSelected = { index ->
                        scope.launch { animatePagerSelection(pagerState, index) }
                    },
                    indicatorPositionProvider = {
                        pagerState.currentPage + pagerState.currentPageOffsetFraction
                    },
                    isScrollInProgressProvider = { pagerState.isScrollInProgress },
                    modifier = Modifier.weight(1f),
                )
                if (shouldShowTabletSecondaryDanmakuActions()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TabletSecondaryDanmakuActions(
                        danmakuEnabled = danmakuEnabled,
                        onDanmakuSendClick = onDanmakuSendClick,
                        onDanmakuToggle = onDanmakuToggle,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = requireNotNull(fixedTab).label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (shouldShowTabletSecondaryDanmakuActions()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TabletSecondaryDanmakuActions(
                        danmakuEnabled = danmakuEnabled,
                        onDanmakuSendClick = onDanmakuSendClick,
                        onDanmakuToggle = onDanmakuToggle,
                    )
                }
            }
        }
        
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalPriorityHorizontalPagerSwipe(
                    state = pagerState,
                    enabled = shouldEnableVideoContentHorizontalPagerSwipe(
                        currentPage = pagerState.currentPage,
                        commentPageIndex = 0,
                        isPagerScrollInProgress = pagerState.isScrollInProgress,
                    ),
                )
        ) { page ->
            when (tabs[page]) {
                TabletSecondaryTab.COMMENTS -> {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember(listState) {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3
                        }
                    }
                    LaunchedEffect(
                        shouldLoadMore,
                        commentState.isRepliesLoading,
                        commentState.isRepliesEnd,
                        commentState.replies.size,
                    ) {
                        if (
                            shouldLoadMore &&
                            !commentState.isRepliesLoading &&
                            !commentState.isRepliesEnd
                        ) {
                            commentActions.loadComments()
                        }
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
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setCommentDefaultSortMode(context, mode.apiMode)
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
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    top = 8.dp,
                                    end = 8.dp,
                                    bottom = 104.dp,
                                )
                            ) {
                            items(
                                items = commentState.replies,
                                key = { "reply_${it.rpid}" },
                                contentType = { resolveReplyItemContentType(it) }
                            ) { reply ->
                                com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard(
                                    isDissolving = reply.rpid in commentState.dissolvingIds,
                                    onDissolveComplete = { commentActions.deleteComment(reply.rpid) },
                                    cardId = "comment_${reply.rpid}",
                                    modifier = Modifier.padding(bottom = 1.dp)
                                ) {
                                    ReplyItemView(
                                        item = reply,
                                        emoteMap = success.emoteMap,
                                        upMid = success.info.owner.mid,
                                        showUpFlag = commentState.showUpFlag,
                                        showIdentityDecorations = showIdentityDecorations,
                                        isPinned = reply.rpid in commentState.pinnedReplyIds,
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
                                        onDeleteClick = if (commentState.currentMid > 0 && reply.mid == commentState.currentMid) {
                                            { commentActions.startDissolve(reply.rpid) }
                                        } else null,
                                        onCheckFraudClick = if (commentState.currentMid > 0 && reply.mid == commentState.currentMid) {
                                            { commentActions.checkCommentFraud(reply) }
                                        } else null,
                                        onUrlClick = openCommentUrl,
                                        onAvatarClick = { mid -> mid.toLongOrNull()?.let { onUpClick(it) } }
                                    )
                                }
                            }
                            if (commentState.isRepliesLoading && commentState.replies.isEmpty()) {
                                item(key = "tablet_comment_skeleton") {
                                    com.android.purebilibili.core.ui.skeleton.CommentListColumnSkeleton()
                                }
                            } else if (commentState.isRepliesLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AdaptiveLoadingIndicator()
                                    }
                                }
                            }
                            }

                        if (commentState.replies.isEmpty() && !commentState.isRepliesLoading) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppText(
                                    text = "暂无评论",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = commentAppearance.secondaryTextColor
                                )
                                if (includeRelatedTab) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    AppText(
                                        text = "先看看相关推荐",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = commentAppearance.secondaryTextColor
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AppTextButton(onClick = {
                                        scope.launch {
                                            animatePagerSelection(pagerState, relatedTabIndex)
                                        }
                                    }) {
                                        AppText("切换到相关推荐")
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
                            onShareClick = {
                                pendingVideoShare = buildVideoSharePayload(
                                    title = success.info.title,
                                    bvid = success.info.bvid,
                                    coverUrl = success.info.pic,
                                    upName = success.info.owner.name,
                                    playCountText = com.android.purebilibili.core.util.FormatUtils
                                        .formatStat(success.info.stat.view.toLong()),
                                )
                            },
                            onCommentClick = playbackActions.openRootCommentComposer,
                            backdrop = commentChromeBackdrop,
                            isScrollInProgressProvider = { listState.isScrollInProgress },
                            scrollPositionProvider = {
                                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                            },
                            showActionButtons = false,
                        )

                        VideoShareSheetHost(
                            payload = pendingVideoShare,
                            onDismiss = { pendingVideoShare = null },
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
                                liquidGlassEffectsEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
                            )
                        }

                           }
                        }
                    }
                }

                TabletSecondaryTab.INTRO -> {
                    introContent?.invoke()
                }

                TabletSecondaryTab.RELATED -> {
                    var hiddenRelatedBvids by remember(success.info.bvid) {
                        mutableStateOf(emptySet<String>())
                    }
                    val visibleRelatedVideos = remember(success.related, hiddenRelatedBvids) {
                        filterRelatedVideosByHiddenBvids(success.related, hiddenRelatedBvids)
                    }
                    val relatedVideoCardLayout = rememberRelatedVideoCardLayout()
                    val relatedListState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = relatedListState,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                            val relatedRows = chunkRelatedVideosForHomeStyleGrid(visibleRelatedVideos)
                            itemsIndexed(
                                items = relatedRows,
                                key = { rowIndex, row ->
                                    val first = row.firstOrNull()
                                    resolveIndexedVideoLazyKey(
                                        namespace = "tablet_related_row",
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
                    }
                }

                TabletSecondaryTab.COLLECTION -> {
                    val season = success.info.ugc_season
                    if (season != null) {
                        TabletCollectionPane(
                            ugcSeason = season,
                            currentBvid = success.info.bvid,
                            currentCid = success.info.cid,
                            onEpisodeClick = { episode ->
                                onRelatedVideoClick(
                                    episode.bvid,
                                    buildVideoNavigationOptions(targetCid = episode.cid)
                                )
                            }
                        )
                    }
                }

                TabletSecondaryTab.OWNER_UPLOADS -> {
                    TabletOwnerUploadsPane(
                        mid = success.info.owner.mid,
                        onVideoClick = onRelatedVideoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TabletCollectionPane(
    ugcSeason: com.android.purebilibili.data.model.response.UgcSeason,
    currentBvid: String,
    currentCid: Long,
    onEpisodeClick: (com.android.purebilibili.data.model.response.UgcEpisode) -> Unit
) {
    val episodes = remember(ugcSeason.sections) { ugcSeason.sections.flatMap { it.episodes } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AppText(
                text = ugcSeason.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppText(
                text = "共 ${episodes.size} 个视频",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(episodes, key = { it.id }) { episode ->
            val isCurrent = isCurrentUgcEpisode(currentBvid, currentCid, episode)
            AppSingleChoiceRow(
                selected = isCurrent,
                onClick = { if (!isCurrent) onEpisodeClick(episode) },
                shape = AppShapes.container(ContainerLevel.Card),
                modifier = Modifier.fillMaxWidth()
            ) {
                coil3.compose.AsyncImage(
                    model = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(episode.arc?.pic.orEmpty()),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(AppShapes.container(ContainerLevel.Chip))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = episode.title,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrent) AppText(text = "正在播放", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}


/**
 * 📊 平板视频信息区域（可滚动版）
 * 使用 LazyColumn 确保内容过多时可以滚动，避免布局冲突
 */
@Composable
private fun ScrollableVideoInfoSection(
    info: com.android.purebilibili.data.model.response.ViewInfo,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    currentPageIndex: Int,
    downloadProgress: Float?,
    isInWatchLater: Boolean,
    videoTags: List<com.android.purebilibili.data.model.response.VideoTag>,
    ownerFollowerCount: Int?,
    ownerVideoCount: Int?,
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    onBgmClick: (BgmInfo) -> Unit = {},
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onFavoriteLongClick: () -> Unit = {},
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onSearchKeywordClick: (String) -> Unit = {},
    onOpenBilibiliLink: ((String) -> Unit)?,
    aiSummary: AiSummaryData? = null,
    aiSummaryPrompt: AiSummaryPromptState? = null,
    videoAiSummaryEntryEnabled: Boolean = true,
    onRetryAiSummary: () -> Unit = {},
    onCreateNoteDraftFromAiSummary: () -> Unit = {},
    onTimestampClick: (Long) -> Unit = {},
    videoNoteState: VideoNoteUiState = VideoNoteUiState(),
    isLoggedIn: Boolean = false,
    videoNoteEnabled: Boolean = true,
    videoNoteDefaultCollapsed: Boolean = true,
    onOpenVideoNoteEditor: () -> Unit = {},
    onRetryVideoNote: () -> Unit = {},
    onDeleteVideoNoteClick: () -> Unit = {},
    onShareVideoNote: (VideoNoteEditorDocument) -> Unit = {},
    onPublicVideoNoteClick: (Long, String) -> Unit = { _, _ -> },
    onShareClick: () -> Unit = {},
    relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo> = emptyList(),
    showRelatedVideos: Boolean = true,
    modifier: Modifier = Modifier,
    ownerTrailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val windowSizeClass = LocalWindowSizeClass.current
    val adaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val systemReduceMotion = rememberSystemReduceMotion()
    val entranceSpec = remember(
        windowSizeClass.widthSizeClass,
        adaptiveInfo.posture,
        systemReduceMotion,
    ) {
        resolveTabletVideoInfoEntranceSpec(
            motionTier = resolveDeviceUiProfile(
                widthSizeClass = windowSizeClass.widthSizeClass,
                foldPosture = adaptiveInfo.posture,
            ).motionTier,
            systemReduceMotion = systemReduceMotion,
        )
    }
    var entranceVisible by remember(info.bvid) { mutableStateOf(false) }
    LaunchedEffect(info.bvid) {
        entranceVisible = true
    }
    var showAiSummarySheet by remember(info.bvid) { mutableStateOf(false) }
    var showNoteListSheet by remember(info.bvid) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp)
    ) {
        // 1. 视频标题
        item {
            TabletVideoInfoStaggeredItem(
                visible = entranceVisible,
                index = 0,
                spec = entranceSpec,
            ) {
                VideoTitleWithDesc(
                    info = info,
                    videoTags = videoTags,
                    bgmList = resolveDisplayBgmList(
                        bgmInfo = bgmInfo,
                        bgmInfoList = bgmInfoList
                    ),
                    onBgmClick = onBgmClick,
                    onRelatedVideoClick = onRelatedVideoClick,
                    onDescriptionUrlClick = onOpenBilibiliLink,
                    onTagClick = onSearchKeywordClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 2. UP主信息
        item {
            TabletVideoInfoStaggeredItem(
                visible = entranceVisible,
                index = 1,
                spec = entranceSpec,
            ) {
                UpInfoSection(
                    info = info,
                    isFollowing = isFollowing,
                    onFollowClick = onFollowClick,
                    onUpClick = onUpClick,
                    followerCount = ownerFollowerCount,
                    videoCount = ownerVideoCount,
                    horizontalPadding = 0.dp,
                    trailingContent = ownerTrailingContent,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 3. 互动按钮
        item {
            TabletVideoInfoStaggeredItem(
                visible = entranceVisible,
                index = 2,
                spec = entranceSpec,
            ) {
                ActionButtonsRow(
                    info = info,
                    isLiked = isLiked,
                    isFavorited = isFavorited,
                    coinCount = coinCount,
                    isInWatchLater = isInWatchLater,
                    onLikeClick = onLikeClick,
                    onCoinClick = onCoinClick,
                    onFavoriteClick = onFavoriteClick,
                    onFavoriteLongClick = onFavoriteLongClick,
                    onTripleClick = onTripleClick,
                    onDownloadClick = onDownloadClick,
                    onWatchLaterClick = onWatchLaterClick,
                    downloadProgress = downloadProgress ?: -1f,
                    onCommentClick = { /* 平板模式不需要跳转评论 */ },
                    showCommentAction = false,
                    onShareClick = onShareClick
                )
            }
        }

        // 4/5. AI 总结与视频笔记入口（内容在底部抽屉中展示）
        val showAiSummaryEntry = videoAiSummaryEntryEnabled &&
            (shouldShowAiSummaryEntry(
                aiSummary = aiSummary,
                isAiSummaryEntryEnabled = true
            ) || aiSummaryPrompt != null)
        val showNoteEntry = shouldShowVideoNoteCard(videoNoteEnabled)
        if (showAiSummaryEntry || showNoteEntry) {
            item {
                TabletVideoInfoStaggeredItem(
                    visible = entranceVisible,
                    index = 3,
                    spec = entranceSpec,
                ) {
                    VideoSupplementEntryRow(
                        showAiSummary = showAiSummaryEntry,
                        showNote = showNoteEntry,
                        onAiSummaryClick = { showAiSummarySheet = true },
                        onNoteClick = { showNoteListSheet = true },
                    )
                }
            }
        }

        // 6. 分P选择器（合集已移到右侧内容栏）
        item {
            if (info.pages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                PagesSelector(
                    pages = info.pages,
                    currentPageIndex = currentPageIndex,
                    onPageSelect = onPageSelect
                )
            }
        }


        // 6. 更多推荐 (水平滚动)。大屏右栏已有相关推荐 Tab 时不再重复。
        if (showRelatedVideos && relatedVideos.isNotEmpty()) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            AppText(
                text = "更多推荐",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (relatedVideos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    itemsIndexed(
                        items = relatedVideos.take(10),
                        key = { index, video ->
                            resolveIndexedVideoLazyKey(
                                namespace = "tablet_related_video",
                                index = index,
                                bvid = video.bvid,
                                aid = video.aid,
                                cid = video.cid,
                            )
                        },
                    ) { _, video ->
                        Column(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable {
                                    val activity = (context as? android.app.Activity) ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                                    val options = activity?.let {
                                        android.app.ActivityOptions.makeSceneTransitionAnimation(it).toBundle()
                                    }
                                    val navOptions = android.os.Bundle(options ?: android.os.Bundle.EMPTY)
                                    if (video.cid > 0L) {
                                        navOptions.putLong(VIDEO_NAV_TARGET_CID_KEY, video.cid)
                                    }
                                    onRelatedVideoClick(video.bvid, navOptions)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .clip(AppShapes.container(ContainerLevel.Chip))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                coil3.compose.AsyncImage(
                                    model = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(video.pic),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                com.android.purebilibili.feature.home.components.cards.VideoCardCoverDurationText(
                                    text = com.android.purebilibili.core.util.FormatUtils.formatDuration(video.duration),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            AppText(
                                text = video.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AppText(
                                text = video.owner.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f),
                            shape = AppShapes.container(ContainerLevel.Chip)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AppText(
                        text = "暂无更多推荐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            // 底部留白，防止被圆角遮挡
            Spacer(modifier = Modifier.height(24.dp))
        }
        }
    }

    AiSummarySheet(
        visible = showAiSummarySheet,
        aiSummary = aiSummary,
        promptState = aiSummaryPrompt,
        onDismiss = { showAiSummarySheet = false },
        onTimestampClick = onTimestampClick,
        onRetry = onRetryAiSummary,
        onCreateNoteDraft = {
            showAiSummarySheet = false
            onCreateNoteDraftFromAiSummary()
        }
    )

    VideoNoteListSheet(
        visible = showNoteListSheet,
        noteState = videoNoteState,
        isLoggedIn = isLoggedIn,
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

@Composable
private fun TabletVideoInfoStaggeredItem(
    visible: Boolean,
    index: Int,
    spec: TabletVideoInfoEntranceSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!spec.enabled) {
        Box(modifier = modifier) { content() }
        return
    }
    val delayMillis = index.coerceAtLeast(0) * spec.staggerDelayMillis
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = spec.durationMillis,
                delayMillis = delayMillis,
                easing = AppMotionEasing.EmphasizedEnter,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = spec.durationMillis,
                delayMillis = delayMillis,
                easing = AppMotionEasing.EmphasizedEnter,
            ),
            initialOffsetY = { fullHeight -> -fullHeight / spec.initialOffsetDivisor },
        ),
    ) {
        content()
    }
}
