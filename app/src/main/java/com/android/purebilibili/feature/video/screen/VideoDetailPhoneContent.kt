package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.feature.video.viewmodel.CommentSubjectKey
import com.android.purebilibili.feature.video.viewmodel.resolveReplyCount
import com.android.purebilibili.core.ui.AppContentColors

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.share.VideoSharePayload
import com.android.purebilibili.feature.video.share.VideoShareSheet
import com.android.purebilibili.feature.video.share.buildVideoSharePayload
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.components.BottomInputBar
import com.android.purebilibili.feature.video.ui.components.VideoDetailSkeleton
import com.android.purebilibili.feature.video.ui.components.resolveBottomInputBarContentBottomPadding
import com.android.purebilibili.feature.video.ui.components.shouldUseFloatingLiquidBottomInputBar
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.withEngagementUiState
import com.android.purebilibili.feature.video.player.PlaylistItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeEffectCompat
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun VideoDetailPhoneSuccessContentLayer(
    success: VideoPlaybackUiState.Success,
    introListState: LazyListState,
    commentListState: LazyListState,
    videoContentPagerState: PagerState,
    commentStateHandle: androidx.compose.runtime.State<CommentUiState>,
    engagementState: VideoEngagementUiState,
    androidNativeLiquidGlassEnabled: Boolean,
    commentMemberDecorationsEnabled: Boolean,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    context: Context,
    sortPreferenceScope: CoroutineScope,
    playerState: VideoPlayerState,
    motionSpec: VideoDetailMotionSpec,
    hazeState: HazeState,
    isTransitionFinished: Boolean,
    relatedContentReady: Boolean = true,
    isLeaving: Boolean,
    rootTransitionOwnsContentAlpha: Boolean,
    keepContentVisibleAfterBackPreview: Boolean = false,
    shouldShowExternalPlaylistQueueBar: Boolean,
    selectedVideoContentTabIndex: Int,
    useTabletLayout: Boolean,
    isFullscreenMode: Boolean,
    isPortraitFullscreen: Boolean,
    showCommentInput: Boolean,
    isCommentThreadVisible: Boolean,
    showFavoriteFolderDialog: Boolean,
    downloadProgress: androidx.compose.runtime.State<Float>,
    danmakuEnabledForDetail: Boolean,
    /**
     * 详情页共享的弹幕会话。
     *
     * 必须由上层（与播放器、Overlay 同一份）传入，不能在正文层自行 acquire：
     * 自行 acquire 会以不同的 key 再开一个会话，导致此处切换弹幕开关改动的是另一个
     * manager 实例（功能失效），并在切集时产生额外 acquire/release 与渲染器重建。
     */
    danmakuManager: DanmakuManager,
    isQuickReturnLimitedForSharedElements: Boolean,
    transitionEnabled: Boolean,
    sourceRouteForSharedElement: String?,
    isPlayerCollapsed: Boolean,
    onBgmClick: (BgmInfo) -> Unit,
    homeUpBadgesVisible: Boolean,
    isVideoPlaying: Boolean,
    onSelectedTabChange: (Int) -> Unit,
    openFavoriteFolders: (VideoFavoriteEntryPoint, isLongPress: Boolean) -> Unit,
    navigateToUserSpaceFromVideo: (Long) -> Unit,
    navigateToRelatedVideo: (String, android.os.Bundle?) -> Unit,
    openCommentUrl: (String) -> Unit,
    onSearchKeywordClick: (String) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    externalPlaylistQueueTitle: String,
    playlistItems: List<PlaylistItem>,
    onShowExternalPlaylistQueueSheet: () -> Unit,
    commentThreadCoveredBlurProgressProvider: () -> Float = { 0f },
    commentPullToRefreshEnabled: Boolean = true,
    animateLateEntryContent: Boolean = false,
    deferFreshEntryContent: Boolean = false,
    navigationOwnsEntryMotion: Boolean = false,
    commentPagePreloadReady: Boolean = true,
) {
    val engagementSuccess = success.withEngagementUiState(engagementState)
    var pendingVideoShare by remember { mutableStateOf<VideoSharePayload?>(null) }
    val shareAnchorState = remember(success.info.bvid) { com.android.purebilibili.feature.video.share.VideoShareAnchorState() }
    val coveredBlurEffectCache = remember { BlurRenderEffectCache() }
    // Android 16 ART 曾拒绝校验 VideoDetailScreen 中捕获过多状态的匿名 Compose lambda。
    // 保持这个成功态为命名边界，避免 R8/Compose 再生成单个超大内容块。
    androidx.compose.runtime.CompositionLocalProvider(
        com.android.purebilibili.feature.video.share.LocalVideoShareAnchorState provides shareAnchorState,
    ) {
    key(success.info.bvid) {
        // 页面导航与卡片形变均持有正文的离场生命周期，避免正文先于页面淡空。
        val navigationOwnsContentVisibility = rootTransitionOwnsContentAlpha || navigationOwnsEntryMotion
        val detailContentVisible = shouldShowVideoDetailContent(
            isTransitionFinished = isTransitionFinished,
            isLeaving = isLeaving,
            rootTransitionOwnsContentAlpha = navigationOwnsContentVisibility,
            keepContentVisibleAfterBackPreview = keepContentVisibleAfterBackPreview,
            deferFreshEntryContent = deferFreshEntryContent,
        )
        // 正文被入场门控拦住时骨架垫底必须撑住（Loading 骨架此刻已卸载），
        // 否则骨架结束到正文放行之间是一段纯黑空窗。
        val holdSkeletonUnderlay = !detailContentVisible && !isLeaving
        val lateContentReveal = rememberVideoDetailLateContentReveal(
            animateOnMount = animateLateEntryContent,
            contentVisible = detailContentVisible,
        )
        val showLoadingUnderlay by remember(detailContentVisible, isLeaving) {
            derivedStateOf { holdSkeletonUnderlay || lateContentReveal.value < 1f }
        }
        Box(modifier = Modifier.fillMaxSize().videoInputDiagnostics("detail_body") {
            "fullscreen=$isFullscreenMode portraitFs=$isPortraitFullscreen " +
                "leaving=$isLeaving transitionFinished=$isTransitionFinished thread=$isCommentThreadVisible " +
                "commentInput=$showCommentInput favoriteDialog=$showFavoriteFolderDialog " +
                "tab=${videoContentPagerState.currentPage} introScrolling=${introListState.isScrollInProgress} " +
                "commentScrolling=${commentListState.isScrollInProgress} pagerScrolling=${videoContentPagerState.isScrollInProgress}"
        }) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Only late arrival adds an opacity handoff; root geometry and the
                // glass / MD3 / Miuix layouts stay unchanged. Drop the static underlay
                // once complete instead of leaving an invisible skeleton mounted.
                if (showLoadingUnderlay) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        alpha = if (holdSkeletonUnderlay) 1f else 1f - lateContentReveal.value
                    }) {
                        VideoDetailSkeleton(animated = false)
                    }
                }
                val detailContentRevealEnter = fadeIn(
                    tween(
                        motionSpec.contentRevealFadeDurationMillis,
                        easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.Continuity
                    )
                )
                val detailContentExitFade = fadeOut(
                    tween(
                        durationMillis = 180,
                        delayMillis = 60,
                        easing = com.android.purebilibili.core.ui.motion.AppMotionEasing.Continuity
                    )
                )
                AnimatedVisibility(
                    modifier = Modifier.graphicsLayer { alpha = lateContentReveal.value },
                    visible = detailContentVisible,
                    // 普通导航已负责整页入场，正文只保留一次骨架交接淡入。
                    enter = if (navigationOwnsContentVisibility) {
                        EnterTransition.None
                    } else {
                        detailContentRevealEnter
                    },
                    exit = if (navigationOwnsContentVisibility) {
                        ExitTransition.None
                    } else {
                        detailContentExitFade
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val floatingLiquidBottomInputBar = shouldUseFloatingLiquidBottomInputBar(
                            androidNativeLiquidGlassEnabled = androidNativeLiquidGlassEnabled
                        )
                        // Capture scrolling detail content only; BottomInputBar stays outside
                        // the source so neither blur nor backdrop samples the bar itself.
                        val bottomInputBarBackdrop = if (floatingLiquidBottomInputBar) {
                            rememberLayerBackdrop()
                        } else {
                            null
                        }
                        val showExternalPlaylistQueueBarOnCurrentTab =
                            shouldShowExternalPlaylistQueueBarOnContentTab(
                                queueAvailable = shouldShowExternalPlaylistQueueBar,
                                selectedTabIndex = selectedVideoContentTabIndex
                            )
                        val showFrozenCommentBar = shouldShowVideoDetailBottomInteractionBar(
                            useTabletLayout = useTabletLayout,
                            selectedTabIndex = selectedVideoContentTabIndex,
                            isFullscreenMode = isFullscreenMode,
                            isPortraitFullscreen = isPortraitFullscreen,
                            isCommentInputVisible = showCommentInput,
                            isCommentThreadVisible = isCommentThreadVisible,
                            isFavoriteFolderDialogVisible = showFavoriteFolderDialog,
                            isExternalPlaylistQueueBarVisible = showExternalPlaylistQueueBarOnCurrentTab
                        )
                        val showInteractionActions = shouldShowVideoDetailActionButtons()
                        val videoContentBottomPadding = resolveBottomInputBarContentBottomPadding(
                            showBar = showFrozenCommentBar,
                            floatingLiquidGlass = floatingLiquidBottomInputBar,
                            showActionButtonsFallback = showInteractionActions
                        )
                        val currentPageIndex = success.info.pages
                            .indexOfFirst { it.cid == success.info.cid }
                            .coerceAtLeast(0)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (floatingLiquidBottomInputBar && bottomInputBarBackdrop != null) {
                                        Modifier.layerBackdrop(bottomInputBarBackdrop)
                                    } else {
                                        Modifier
                                    }
                                )
                                .hazeSourceCompat(hazeState)
                                .graphicsLayer {
                                    // 在图层阶段读动画值，模糊动画不重新组合评论与简介。
                                    val coveredBlurProgress = if (isCommentThreadVisible) {
                                        commentThreadCoveredBlurProgressProvider()
                                    } else {
                                        0f
                                    }
                                    renderEffect = coveredBlurEffectCache.resolve(
                                        resolvePredictiveBackBlurFrame(
                                            progress = coveredBlurProgress,
                                        ).blurRadiusPx
                                    )
                                }
                        ) {
                            VideoDetailBodyUpdates(
                                comments = commentStateHandle,
                                downloadProgress = downloadProgress,
                            ) { commentState, currentDownloadProgress ->
                                VideoContentSection(
                                    commentUpdates = commentStateHandle,
                                    data = VideoContentData(
                                        info = engagementSuccess.info,
                                        introListState = introListState,
                                        commentListState = commentListState,
                                        pagerState = videoContentPagerState,
                                        relatedVideos = success.related,
                                        relatedLoadState = success.relatedLoadState,
                                        replies = commentState.replies,
                                        replyCount = commentState.resolveReplyCount(
                                            subject = CommentSubjectKey(engagementSuccess.info.aid),
                                            detailCount = engagementSuccess.info.stat.reply,
                                        ) ?: engagementSuccess.info.stat.reply,
                                        emoteMap = success.emoteMap,
                                        followingMids = engagementState.followingMids,
                                        videoTags = success.videoTags,
                                    ),
                                    engagementState = VideoContentEngagementState(
                                        isLoggedIn = success.isLoggedIn,
                                        isFollowing = engagementState.isFollowing,
                                        followStatusKnown = engagementState.followStatusKnown,
                                        isFavorited = engagementState.isFavorited,
                                        isLiked = engagementState.isLiked,
                                        likeStatusKnown = engagementState.likeStatusKnown,
                                        isDisliked = engagementState.isDisliked,
                                        coinCount = engagementState.coinCount,
                                        currentPageIndex = currentPageIndex,
                                        downloadProgress = currentDownloadProgress,
                                        isInWatchLater = engagementState.isInWatchLater,
                                    ),
                                    commentState = VideoContentCommentState(
                                        isRepliesLoading = commentState.isRepliesLoading,
                                        isRepliesRefreshing = commentState.isRepliesRefreshing,
                                        repliesError = commentState.repliesError,
                                        isRepliesEnd = commentState.isRepliesEnd,
                                        voteCard = commentState.voteCard,
                                        sortMode = commentState.sortMode,
                                        currentMid = commentState.currentMid,
                                        showUpFlag = commentState.showUpFlag,
                                        showIdentityDecorations = commentMemberDecorationsEnabled,
                                        dissolvingIds = commentState.dissolvingIds,
                                        likedComments = commentState.likedComments,
                                        hatedComments = commentState.hatedComments,
                                        nextPage = commentState.nextPage,
                                        pageControl = commentState.pageControl,
                                    ),
                                    noteState = VideoContentNoteState(
                                        aiSummary = success.aiSummary,
                                        aiSummaryPrompt = success.aiSummaryPrompt,
                                        videoNoteState = success.videoNoteState,
                                    ),
                                    presentationState = VideoContentPresentationState(
                                        sponsorVideoLabel = success.sponsorVideoLabel,
                                        danmakuEnabled = danmakuEnabledForDetail,
                                        transitionEnabled = transitionEnabled,
                                        isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
                                        sourceRouteForSharedElement = sourceRouteForSharedElement,
                                        isPlayerCollapsed = isPlayerCollapsed,
                                        onlineCount = success.onlineCount,
                                        showOnlineCount = true,
                                        ownerFollowerCount = success.ownerFollowerCount,
                                        ownerVideoCount = success.ownerVideoCount,
                                        showUpBadge = homeUpBadgesVisible,
                                        showInteractionActions = showInteractionActions,
                                        isVideoPlaying = isVideoPlaying,
                                        bottomContentPadding = videoContentBottomPadding,
                                        commentPullToRefreshEnabled = commentPullToRefreshEnabled,
                                        relatedContentReady = relatedContentReady,
                                        commentPagePreloadReady = commentPagePreloadReady,
                                    ),
                                    primaryActions = VideoContentPrimaryActions(
                                        onFollowClick = engagementActions.toggleFollow,
                                        onFavoriteClick = {
                                            openFavoriteFolders(VideoFavoriteEntryPoint.DetailActionRow, false)
                                        },
                                        onLikeClick = engagementActions.toggleLike,
                                        onDislikeClick = engagementActions.toggleDislike,
                                        onCoinClick = engagementActions.openCoinDialog,
                                        onTripleClick = engagementActions.doTripleAction,
                                        onPageSelect = playbackActions.switchPage,
                                        onUpClick = navigateToUserSpaceFromVideo,
                                        onRelatedVideoClick = navigateToRelatedVideo,
                                        onDownloadClick = playbackActions.openDownloadDialog,
                                        onWatchLaterClick = engagementActions.toggleWatchLater,
                                        onShareClick = {
                                            pendingVideoShare = buildVideoSharePayload(
                                                title = success.info.title,
                                                bvid = success.info.bvid,
                                                page = success.info.pages.indexOfFirst { it.cid == success.info.cid }.coerceAtLeast(0) + 1,
                                                positionMs = playerState.player.currentPosition.coerceAtLeast(0L),
                                                authorId = success.info.owner.mid,
                                                coverUrl = success.info.pic,
                                                upName = success.info.owner.name,
                                                playCountText = FormatUtils.formatStat(success.info.stat.view.toLong()),
                                            )
                                        },
                                        onTimestampClick = { positionMs -> seekPlayerFromUserAction(playerState.player, positionMs) },
                                        onDanmakuSendClick = {
                                            android.util.Log.d("VideoDetailScreen", "Danmaku send clicked")
                                            playbackActions.showDanmakuSendDialog()
                                        },
                                        onDanmakuToggle = {
                                            val newValue = !danmakuEnabledForDetail
                                            danmakuManager.isEnabled = newValue
                                            if (!newValue) danmakuManager.clear()
                                            sortPreferenceScope.launch {
                                                com.android.purebilibili.core.store.SettingsManager.setDanmakuEnabled(
                                                    context,
                                                    newValue,
                                                    com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT,
                                                )
                                            }
                                        },
                                        onFavoriteLongClick = {
                                            openFavoriteFolders(VideoFavoriteEntryPoint.DetailActionRow, true)
                                        },
                                        onBgmClick = onBgmClick,
                                    ),
                                    commentActions = VideoContentCommentActions(
                                        onSortModeChange = { mode ->
                                            commentActions.setSortMode(mode)
                                            sortPreferenceScope.launch {
                                                com.android.purebilibili.core.store.SettingsManager.setCommentDefaultSortMode(context, mode.apiMode)
                                            }
                                        },
                                        onSubReplyClick = commentActions.openSubReply,
                                        onCommentReplyClick = playbackActions.replyTo,
                                        onLoadMoreReplies = commentActions.loadComments,
                                        onRefreshReplies = commentActions.refreshComments,
                                        onDeleteComment = commentActions.deleteComment,
                                        onDissolveStart = commentActions.startDissolve,
                                        onCommentLike = commentActions.likeComment,
                                        onCommentHate = commentActions.hateComment,
                                        onCommentUrlClick = openCommentUrl,
                                        onDescriptionUrlClick = onOpenBilibiliLink,
                                        onSearchKeywordClick = onSearchKeywordClick,
                                        onReportComment = commentActions.reportComment,
                                        onToggleTopComment = commentActions.toggleTopComment,
                                        onCheckCommentFraud = commentActions.checkCommentFraud,
                                    ),
                                    noteActions = VideoContentNoteActions(
                                        onRetryAiSummary = playbackActions.retryAiSummary,
                                        onCreateNoteDraftFromAiSummary = playbackActions.createVideoNoteDraftFromAiSummary,
                                        onOpenVideoNoteEditor = playbackActions.openVideoNoteEditor,
                                        onCloseVideoNoteEditor = playbackActions.closeVideoNoteEditor,
                                        onVideoNoteDocumentChange = playbackActions.updateVideoNoteEditorDocument,
                                        onInsertVideoNoteTimestamp = playbackActions.currentVideoNoteTimestamp,
                                        onVideoNoteTimestampClick = playbackActions.seekTo,
                                        onSaveVideoNote = playbackActions.saveVideoNote,
                                        onDeleteVideoNote = playbackActions.deleteVideoNote,
                                        onRetryVideoNote = playbackActions.retryVideoNote,
                                        onLoadMorePublicVideoNotes = playbackActions.loadMorePublicVideoNotes,
                                        onPublicVideoNoteClick = { cvid, _ ->
                                            onOpenBilibiliLink?.invoke("https://www.bilibili.com/read/cv$cvid")
                                        },
                                    ),
                                    uiActions = VideoContentUiActions(
                                        onSelectedTabChange = onSelectedTabChange,
                                        onCommentScrollStateChange = { _, _ -> },
                                    ),
                                )
                            }
                        }

                        // 底栏可见度跟随翻页进度:滑动过程中连续淡入淡出,而不是过半时瞬间弹出。
                        val commentBarProgress = remember(videoContentPagerState) {
                            derivedStateOf {
                                resolveVideoDetailCommentBarProgress(
                                    pagerPosition = videoContentPagerState.currentPage +
                                        videoContentPagerState.currentPageOffsetFraction,
                                    commentTabIndex = VIDEO_CONTENT_COMMENT_TAB_INDEX,
                                )
                            }
                        }
                        val commentBarVisible by remember(commentBarProgress, showFrozenCommentBar) {
                            derivedStateOf { showFrozenCommentBar || commentBarProgress.value > 0f }
                        }
                        if (commentBarVisible) {
                            BottomInputBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .graphicsLayer {
                                        val progress = commentBarProgress.value
                                        alpha = progress
                                        translationY = (1f - progress) * 32f
                                    },
                                isLiked = engagementState.isLiked,
                                likeStatusKnown = engagementState.likeStatusKnown,
                                isFavorited = engagementState.isFavorited,
                                isCoined = engagementState.coinCount > 0,
                                onLikeClick = engagementActions.toggleLike,
                                onFavoriteClick = {
                                    openFavoriteFolders(VideoFavoriteEntryPoint.BottomInputBar, false)
                                },
                                onFavoriteLongClick = {
                                    openFavoriteFolders(VideoFavoriteEntryPoint.BottomInputBar, true)
                                },
                                onCoinClick = engagementActions.openCoinDialog,
                                onShareClick = {
                                    pendingVideoShare = buildVideoSharePayload(
                                        title = success.info.title,
                                        bvid = success.info.bvid,
                                        page = success.info.pages.indexOfFirst { it.cid == success.info.cid }.coerceAtLeast(0) + 1,
                                        positionMs = playerState.player.currentPosition.coerceAtLeast(0L),
                                        authorId = success.info.owner.mid,
                                        coverUrl = success.info.pic,
                                        upName = success.info.owner.name,
                                        playCountText = FormatUtils.formatStat(success.info.stat.view.toLong()),
                                    )
                                },
                                onCommentClick = {
                                    android.util.Log.d("VideoDetailScreen", "Comment input clicked")
                                    playbackActions.openRootCommentComposer()
                                },
                                backdrop = if (floatingLiquidBottomInputBar) {
                                    bottomInputBarBackdrop
                                } else {
                                    null
                                },
                                hazeState = hazeState,
                                isScrollInProgressProvider = {
                                    introListState.isScrollInProgress ||
                                        commentListState.isScrollInProgress ||
                                        videoContentPagerState.isScrollInProgress
                                },
                                scrollPositionProvider = {
                                    val state = if (videoContentPagerState.currentPage == 0) {
                                        introListState
                                    } else {
                                        commentListState
                                    }
                                    state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
                                },
                            )
                        }

                        if (showExternalPlaylistQueueBarOnCurrentTab) {
                            ExternalPlaylistQueueCollapsedBar(
                                title = externalPlaylistQueueTitle,
                                videoCount = playlistItems.size,
                                onClick = onShowExternalPlaylistQueueSheet,
                                hazeState = hazeState,
                                liquidGlassEnabled = floatingLiquidBottomInputBar,
                                backdrop = if (floatingLiquidBottomInputBar) bottomInputBarBackdrop else null,
                                isScrollInProgressProvider = {
                                    introListState.isScrollInProgress ||
                                        commentListState.isScrollInProgress ||
                                        videoContentPagerState.isScrollInProgress
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 24.dp,
                                    )
                            )
                        }
                    }
                }
            }

            pendingVideoShare?.let { sharePayload ->
                VideoShareSheet(
                    payload = sharePayload,
                    glassSource = shareAnchorState.source,
                    onDismiss = { pendingVideoShare = null },
                )
            }
        }
    }
    }
}

@Composable
private fun ExternalPlaylistQueueCollapsedBar(
    title: String,
    videoCount: Int,
    onClick: () -> Unit,
    hazeState: HazeState,
    liquidGlassEnabled: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    isScrollInProgressProvider: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val shape = AppShapes.container(ContainerLevel.Pill)
    // 队列入口按内容紧凑排版，两种材质共用最小高度，大字体可自然撑高。
    val minBarHeight = 40.dp
    if (liquidGlassEnabled) {
        BottomBarMatchedReusableLiquidDock(
            shape = shape,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minBarHeight),
            backdrop = backdrop,
            reuseEnabled = true,
            drawShellLens = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(minBarHeight.value),
            isScrollInProgressProvider = isScrollInProgressProvider,
        ) {
            ExternalPlaylistQueueCollapsedBarContent(
                title = title,
                videoCount = videoCount,
                onClick = onClick,
            )
        }
        return
    }
    val useHazeEffect = shouldAllowRuntimeShaderBackedHazeEffect(Build.VERSION.SDK_INT)
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minBarHeight)
            // 先 clip 再 haze：否则模糊层按矩形绘制，左/右端会露出直角（与展开 sheet 一致）。
            .clip(shape)
            .then(
                if (useHazeEffect) {
                    Modifier.hazeEffectCompat(
                        state = hazeState,
                        style = HazeMaterials.ultraThin()
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = AppSurfaceTokens.cardContainer().copy(alpha = 0.74f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        ExternalPlaylistQueueCollapsedBarContent(
            title = title,
            videoCount = videoCount,
            onClick = onClick,
        )
    }
}

@Composable
private fun ExternalPlaylistQueueCollapsedBarContent(
    title: String,
    videoCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = "${videoCount}个视频",
                color = AppContentColors.accent(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(4.dp))
            AppIcon(
                imageVector = rememberAppChevronUpIcon(),
                contentDescription = "展开${title}队列",
                tint = AppContentColors.secondaryText()
            )
        }
}
