// 文件路径: feature/video/screen/VideoContentSection.kt
package com.android.purebilibili.feature.video.screen

import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.core.ui.rememberAppSettingsIcon
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.shouldShowAiSummaryEntry
import com.android.purebilibili.feature.video.ui.section.resolveVideoDetailMotionBudget
import com.android.purebilibili.feature.video.ui.section.shouldAnimateVideoDetailLayout
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.RelatedVideoItem
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
import com.android.purebilibili.feature.video.ui.components.CommentSortFilterBar
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.rememberVideoCommentAppearance
import com.android.purebilibili.feature.video.ui.components.resolveReplyItemContentType
import com.android.purebilibili.feature.video.ui.components.shouldShowReplyTopAction
import com.android.purebilibili.feature.video.ui.components.shouldShowVideoCommentBackToTop
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.ui.section.AiSummaryCard
import com.android.purebilibili.feature.video.ui.section.AiSummaryPromptCard
import kotlin.math.abs

internal fun shouldShowDanmakuSendInput(isPlayerCollapsed: Boolean): Boolean = !isPlayerCollapsed

internal fun shouldShowVideoContentCommentDragHandle(
    selectedTabIndex: Int
): Boolean = selectedTabIndex == 1

internal data class VideoContentTabBarLayoutSpec(
    val tabsRowWeight: Float,
    val tabsRowScrollable: Boolean,
    val containerHorizontalPaddingDp: Int,
    val tabHorizontalPaddingDp: Int,
    val tabVerticalPaddingDp: Int,
    val tabSpacingDp: Int,
    val selectedTabFontSizeSp: Int,
    val unselectedTabFontSizeSp: Int,
    val indicatorWidthDp: Int,
    val segmentedControlHeightDp: Int,
    val segmentedControlIndicatorHeightDp: Int
)

internal fun hasVideoContentTabBarIndicatorScaleClearance(
    containerHeightDp: Int,
    indicatorHeightDp: Int
): Boolean {
    val bottomBarScale = 78f / 56f
    return containerHeightDp >= indicatorHeightDp * bottomBarScale + 2f
}

internal fun resolveVideoContentTabBarLayoutSpec(widthDp: Int): VideoContentTabBarLayoutSpec {
    return if (widthDp < 400) {
        VideoContentTabBarLayoutSpec(
            tabsRowWeight = 1f,
            tabsRowScrollable = true,
            containerHorizontalPaddingDp = 8,
            tabHorizontalPaddingDp = 8,
            tabVerticalPaddingDp = 9,
            tabSpacingDp = 10,
            selectedTabFontSizeSp = 16,
            unselectedTabFontSizeSp = 15,
            indicatorWidthDp = 28,
            segmentedControlHeightDp = 46,
            segmentedControlIndicatorHeightDp = 29
        )
    } else {
        VideoContentTabBarLayoutSpec(
            tabsRowWeight = 1f,
            tabsRowScrollable = true,
            containerHorizontalPaddingDp = 12,
            tabHorizontalPaddingDp = 12,
            tabVerticalPaddingDp = 10,
            tabSpacingDp = 16,
            selectedTabFontSizeSp = 17,
            unselectedTabFontSizeSp = 16,
            indicatorWidthDp = 32,
            segmentedControlHeightDp = 48,
            segmentedControlIndicatorHeightDp = 30
        )
    }
}

internal data class VideoContentTabBarDanmakuActionLayoutPolicy(
    val toggleIconSizeDp: Int,
    val toggleHorizontalPaddingDp: Int,
    val toggleVerticalPaddingDp: Int,
    val toggleTextSizeSp: Int,
    val toggleTrailingPaddingDp: Int,
    val sendHorizontalPaddingDp: Int,
    val sendVerticalPaddingDp: Int,
    val sendTextSizeSp: Int,
    val sendLabel: String,
    val settingsButtonSizeDp: Int,
    val settingsIconSizeDp: Int,
    val settingsLeadingPaddingDp: Int
)

internal fun resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp: Int): VideoContentTabBarDanmakuActionLayoutPolicy {
    return if (widthDp < 400) {
        VideoContentTabBarDanmakuActionLayoutPolicy(
            toggleIconSizeDp = 14,
            toggleHorizontalPaddingDp = 8,
            toggleVerticalPaddingDp = 5,
            toggleTextSizeSp = 10,
            toggleTrailingPaddingDp = 6,
            sendHorizontalPaddingDp = 10,
            sendVerticalPaddingDp = 7,
            sendTextSizeSp = 11,
            sendLabel = "发弹幕",
            settingsButtonSizeDp = 36,
            settingsIconSizeDp = 18,
            settingsLeadingPaddingDp = 4
        )
    } else {
        VideoContentTabBarDanmakuActionLayoutPolicy(
            toggleIconSizeDp = 16,
            toggleHorizontalPaddingDp = 10,
            toggleVerticalPaddingDp = 6,
            toggleTextSizeSp = 11,
            toggleTrailingPaddingDp = 8,
            sendHorizontalPaddingDp = 12,
            sendVerticalPaddingDp = 8,
            sendTextSizeSp = 12,
            sendLabel = "点我发弹幕",
            settingsButtonSizeDp = 40,
            settingsIconSizeDp = 20,
            settingsLeadingPaddingDp = 6
        )
    }
}

/**
 * 视频详情内容区域
 * 从 VideoDetailScreen.kt 提取出来，提高代码可维护性
 */
@Composable
fun VideoContentSection(
    info: ViewInfo,
    relatedVideos: List<RelatedVideo>,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    isRepliesEnd: Boolean = false,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    currentPageIndex: Int,
    downloadProgress: Float = -1f,
    isInWatchLater: Boolean = false,
    followingMids: Set<Long> = emptySet(),
    videoTags: List<VideoTag> = emptyList(),
    sortMode: CommentSortMode = CommentSortMode.HOT,
    upOnlyFilter: Boolean = false,
    onSortModeChange: (CommentSortMode) -> Unit = {},
    onUpOnlyToggle: () -> Unit = {},
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onSubReplyClick: (ReplyItem) -> Unit,
    onRootCommentClick: () -> Unit = {},
    onCommentReplyClick: (ReplyItem) -> Unit = {},
    onLoadMoreReplies: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onWatchLaterClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onTimestampClick: ((Long) -> Unit)? = null,
    onDanmakuSendClick: () -> Unit = {},
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    // [新增] 删除与动画参数
    currentMid: Long = 0,
    showUpFlag: Boolean = false,
    dissolvingIds: Set<Long> = emptySet(),
    onDeleteComment: (Long) -> Unit = {},
    onDissolveStart: (Long) -> Unit = {},
    // [新增] 点赞回调
    onCommentLike: (Long) -> Unit = {},
    // [新增] 已点赞的评论 ID 集合
    likedComments: Set<Long> = emptySet(),
    onCommentUrlClick: (String) -> Unit = {},
    onReportComment: (Long, Int) -> Unit = { _, _ -> },
    onToggleTopComment: (ReplyItem) -> Unit = {},
    // 🔗 [新增] 共享元素过渡开关
    transitionEnabled: Boolean = false,
    // [新增] 收藏夹相关参数
    onFavoriteLongClick: () -> Unit = {},
    favoriteFolderDialogVisible: Boolean = false,
    favoriteFolders: List<com.android.purebilibili.data.model.response.FavFolder> = emptyList(),
    isFavoriteFoldersLoading: Boolean = false,
    selectedFavoriteFolderIds: Set<Long> = emptySet(),
    isSavingFavoriteFolders: Boolean = false,
    onFavoriteFolderToggle: (com.android.purebilibili.data.model.response.FavFolder) -> Unit = {},
    onSaveFavoriteFolders: () -> Unit = {},
    onDismissFavoriteFolderDialog: () -> Unit = {},

    onCreateFavoriteFolder: (String, String, Boolean) -> Unit = { _, _, _ -> },
    // [新增] 恢复播放器 (音频模式 -> 视频模式)
    isPlayerCollapsed: Boolean = false,
    onRestorePlayer: () -> Unit = {},
    // [新增] AI Summary & BGM
    aiSummary: AiSummaryData? = null,
    aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState? = null,
    onRetryAiSummary: () -> Unit = {},
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    onBgmClick: (BgmInfo) -> Unit = {},
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    ownerFollowerCount: Int? = null,
    ownerVideoCount: Int? = null,
    showUpBadge: Boolean = true,
    showInteractionActions: Boolean = true,
    isVideoPlaying: Boolean = false,
    onSelectedTabChange: (Int) -> Unit = {},
    onIntroScrollStateChange: (Int, Int) -> Unit = { _, _ -> },
    onCommentScrollStateChange: (Int, Int) -> Unit = { _, _ -> },
    onCommentDragHandleDrag: (Float) -> Unit = {}
) {
    val tabs = listOf("简介", "评论 $replyCount")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val introListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    TrackJankStateFlag(
        stateName = "video_detail:tab_swipe",
        isActive = pagerState.isScrollInProgress
    )
    TrackScrollJank(
        scrollableState = introListState,
        stateName = "video_detail:intro_scroll"
    )
    TrackScrollJank(
        scrollableState = commentListState,
        stateName = "video_detail:comment_scroll"
    )
    val videoDetailMotionBudget = resolveVideoDetailMotionBudget(
        isTabSwitching = pagerState.isScrollInProgress,
        isContentScrolling = introListState.isScrollInProgress || commentListState.isScrollInProgress
    )
    val animateVideoDetailLayout = shouldAnimateVideoDetailLayout(videoDetailMotionBudget)
    val lightweightCommentRendering = remember(pagerState.currentPage, isVideoPlaying) {
        shouldUseLightweightCommentRendering(
            selectedTabIndex = pagerState.currentPage,
            isVideoPlaying = isVideoPlaying
        )
    }
    
    // 评论图片预览状态
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var sourceRect by remember { mutableStateOf<Rect?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    
    // 合集展开状态
    var showCollectionSheet by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }

    val onTabSelected: (Int) -> Unit = { index ->
        scope.launch { pagerState.animateScrollToPage(index) }
    }
    LaunchedEffect(pagerState.currentPage) {
        onSelectedTabChange(pagerState.currentPage)
    }
    LaunchedEffect(introListState) {
        snapshotFlow { introListState.firstVisibleItemIndex to introListState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { state: Pair<Int, Int> ->
                onIntroScrollStateChange(state.first, state.second)
            }
    }
    LaunchedEffect(commentListState) {
        snapshotFlow { commentListState.firstVisibleItemIndex to commentListState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { state: Pair<Int, Int> ->
                onCommentScrollStateChange(state.first, state.second)
            }
    }
    val bottomContentPadding = if (showInteractionActions) 84.dp else 12.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Inline 弹幕设置不是 Dialog，必须在详情内容之后绘制，避免被列表盖住。
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            VideoContentTabBar(
                tabs = tabs,
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = onTabSelected,
                onDanmakuSendClick = onDanmakuSendClick,
                danmakuEnabled = danmakuEnabled,
                onDanmakuToggle = onDanmakuToggle,
                onDanmakuSettingsClick = { showDanmakuSettings = true },
                modifier = Modifier,
                isPlayerCollapsed = isPlayerCollapsed,
                onRestorePlayer = onRestorePlayer,
                onCommentDragHandleDrag = onCommentDragHandleDrag,
                commentDragHandleVisible = shouldShowVideoContentCommentDragHandle(
                    selectedTabIndex = pagerState.currentPage
                )
            )

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = resolveVideoDetailBeyondViewportPageCount(
                    isVideoPlaying = isVideoPlaying
                ),
                userScrollEnabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> VideoIntroTab(
                        listState = introListState,
                        modifier = Modifier,
                        info = info,
                        relatedVideos = relatedVideos,
                        currentPageIndex = currentPageIndex,
                        followingMids = followingMids,
                        videoTags = videoTags,
                        isFollowing = isFollowing,
                        isFavorited = isFavorited,
                        isLiked = isLiked,
                        coinCount = coinCount,
                        downloadProgress = downloadProgress,
                        isInWatchLater = isInWatchLater,
                        onFollowClick = onFollowClick,
                        onFavoriteClick = onFavoriteClick,
                        onLikeClick = onLikeClick,
                        onCoinClick = onCoinClick,
                        onTripleClick = onTripleClick,
                        onPageSelect = onPageSelect,
                        onUpClick = onUpClick,
                        onRelatedVideoClick = onRelatedVideoClick,
                        onOpenCollectionSheet = { showCollectionSheet = true },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onShareClick = onShareClick,
                        contentPadding = PaddingValues(bottom = bottomContentPadding),
                        transitionEnabled = transitionEnabled,
                        ownerFollowerCount = ownerFollowerCount,
                        ownerVideoCount = ownerVideoCount,
                        showUpBadge = showUpBadge,
                        onFavoriteLongClick = onFavoriteLongClick,
                        aiSummary = aiSummary,
                        aiSummaryPrompt = aiSummaryPrompt,
                        onRetryAiSummary = onRetryAiSummary,
                        bgmInfo = bgmInfo,
                        bgmInfoList = bgmInfoList,
                        onlineCount = onlineCount,
                        showOnlineCount = showOnlineCount,
                        onTimestampClick = onTimestampClick,
                        onBgmClick = onBgmClick,
                        showInteractionActions = showInteractionActions,
                        animateVideoDetailLayout = animateVideoDetailLayout
                    )
                    1 -> VideoCommentTab(
                        listState = commentListState,
                        modifier = Modifier,
                        info = info,
                        replies = replies,
                        replyCount = replyCount,
                        emoteMap = emoteMap,
                        isRepliesLoading = isRepliesLoading,
                        isRepliesEnd = isRepliesEnd,
                        videoTags = videoTags,
                        sortMode = sortMode,
                        upOnlyFilter = upOnlyFilter,
                        onSortModeChange = onSortModeChange,
                        onUpOnlyToggle = onUpOnlyToggle,
                        onUpClick = onUpClick,
                        onSubReplyClick = onSubReplyClick,
                        onRootCommentClick = onRootCommentClick,
                        onCommentReplyClick = onCommentReplyClick,
                        onLoadMoreReplies = onLoadMoreReplies,
                        onImagePreview = { images, index, rect, textContent ->
                            previewImages = images
                            previewInitialIndex = index
                            sourceRect = rect
                            previewTextContent = textContent
                            showImagePreview = true
                        },
                        onTimestampClick = onTimestampClick,
                        showUpFlag = showUpFlag,
                        contentPadding = PaddingValues(bottom = bottomContentPadding),
                        currentMid = currentMid,
                        dissolvingIds = dissolvingIds,
                        onDeleteComment = onDeleteComment,
                        onDissolveStart = onDissolveStart,
                        onCommentLike = onCommentLike,
                        likedComments = likedComments,
                        onCommentUrlClick = onCommentUrlClick,
                        onReportComment = onReportComment,
                        onToggleTopComment = onToggleTopComment,
                        lightweightCommentRendering = lightweightCommentRendering
                    )
                }
            }
        }

        if (showImagePreview && previewImages.isNotEmpty()) {
            ImagePreviewDialog(
                images = previewImages,
                initialIndex = previewInitialIndex,
                sourceRect = sourceRect,
                textContent = previewTextContent,
                onDismiss = {
                    showImagePreview = false
                    previewTextContent = null
                }
            )
        }

        info.ugc_season?.let { season ->
            if (showCollectionSheet) {
                CollectionSheet(
                    ugcSeason = season,
                    currentBvid = info.bvid,
                    currentCid = info.cid,
                    onDismiss = { showCollectionSheet = false },
                    onEpisodeClick = { episode ->
                        showCollectionSheet = false
                        onRelatedVideoClick(
                            episode.bvid,
                            buildVideoNavigationOptions(targetCid = episode.cid)
                        )
                    }
                )
            }
        }

        if (showDanmakuSettings) {
            VideoDetailDanmakuSettingsPanel(
                onDismiss = { showDanmakuSettings = false }
            )
        }
    }
}

// ... VideoIntroTab signature ...
@Composable
private fun VideoIntroTab(
    listState: LazyListState,
    modifier: Modifier,
    info: ViewInfo,
    relatedVideos: List<RelatedVideo>,
    currentPageIndex: Int,
    followingMids: Set<Long>,
    videoTags: List<VideoTag>,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    downloadProgress: Float,
    isInWatchLater: Boolean,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onShareClick: () -> Unit = {},
    contentPadding: PaddingValues,
    transitionEnabled: Boolean = false,  // 🔗 共享元素过渡开关
    ownerFollowerCount: Int? = null,
    ownerVideoCount: Int? = null,
    showUpBadge: Boolean = true,
    onFavoriteLongClick: () -> Unit = {},
    aiSummary: AiSummaryData? = null,
    aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState? = null,
    onRetryAiSummary: () -> Unit = {},
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    onTimestampClick: ((Long) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    showInteractionActions: Boolean = true,
    animateVideoDetailLayout: Boolean = true
) {
    val hasPages = info.pages.size > 1
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        // 1. 移入的 Header 区域
        item {
            VideoHeaderContent(
                info = info,
                videoTags = videoTags,
                isFollowing = isFollowing,
                isFavorited = isFavorited,
                isLiked = isLiked,
                coinCount = coinCount,
                downloadProgress = downloadProgress,
                isInWatchLater = isInWatchLater,
                onFollowClick = onFollowClick,
                onFavoriteClick = onFavoriteClick,
                onLikeClick = onLikeClick,
                onCoinClick = onCoinClick,
                onTripleClick = onTripleClick,
                onUpClick = onUpClick,
                onOpenCollectionSheet = onOpenCollectionSheet,
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                onShareClick = onShareClick,

                onGloballyPositioned = { },
                transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
                ownerFollowerCount = ownerFollowerCount,
                ownerVideoCount = ownerVideoCount,
                onFavoriteLongClick = onFavoriteLongClick,
                aiSummary = aiSummary,
                aiSummaryPrompt = aiSummaryPrompt,
                onRetryAiSummary = onRetryAiSummary,
                bgmInfo = bgmInfo,
                bgmInfoList = bgmInfoList,
                onlineCount = onlineCount,
                showOnlineCount = showOnlineCount,
                onTimestampClick = onTimestampClick,
                onBgmClick = onBgmClick,
                showInteractionActions = showInteractionActions,
                animateVideoDetailLayout = animateVideoDetailLayout
            )
        }
        if (hasPages) {
            item {
                PagesSelector(
                    pages = info.pages,
                    currentPageIndex = currentPageIndex,
                    onPageSelect = onPageSelect
                )
            }
        }

        item {
            VideoRecommendationHeader()
        }

        itemsIndexed(items = relatedVideos, key = { _, item -> item.bvid }) { index, video ->
            val openRelatedVideo = {
                val navOptions = if (video.cid > 0L) {
                    android.os.Bundle().apply {
                        putLong(VIDEO_NAV_TARGET_CID_KEY, video.cid)
                    }
                } else {
                    null
                }
                onRelatedVideoClick(video.bvid, navOptions)
            }

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                RelatedVideoItem(
                    video = video,
                    isFollowed = video.owner.mid in followingMids,
                    transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
                    showUpBadge = showUpBadge,
                    onClick = openRelatedVideo
                )
            }
        }
    }
}

// ... VideoCommentTab signature ...
@Composable
private fun VideoCommentTab(
    listState: LazyListState,
    modifier: Modifier,
    info: ViewInfo,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    isRepliesEnd: Boolean,
    videoTags: List<VideoTag>,
    sortMode: CommentSortMode,
    upOnlyFilter: Boolean,
    onSortModeChange: (CommentSortMode) -> Unit,
    onUpOnlyToggle: () -> Unit,
    onUpClick: (Long) -> Unit,
    onSubReplyClick: (ReplyItem) -> Unit,
    onRootCommentClick: () -> Unit,
    onCommentReplyClick: (ReplyItem) -> Unit,
    onLoadMoreReplies: () -> Unit,
    onImagePreview: (List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    contentPadding: PaddingValues,
    // [新增] 参数
    currentMid: Long,
    showUpFlag: Boolean,
    dissolvingIds: Set<Long>,
    onDeleteComment: (Long) -> Unit,
    onDissolveStart: (Long) -> Unit,
    // [新增] 点赞回调
    onCommentLike: (Long) -> Unit,
    likedComments: Set<Long>,
    onCommentUrlClick: (String) -> Unit,
    onReportComment: (Long, Int) -> Unit,
    onToggleTopComment: (ReplyItem) -> Unit,
    lightweightCommentRendering: Boolean
) {
    val commentAppearance = rememberVideoCommentAppearance()
    val scope = rememberCoroutineScope()
    val shouldShowBackToTop by remember(listState) {
        derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            )
        }
    }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            item {
                CommentSortFilterBar(
                    count = replyCount,
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    upOnly = upOnlyFilter,
                    onUpOnlyToggle = onUpOnlyToggle
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = commentAppearance.composerHintBackgroundColor,
                    shape = RoundedCornerShape(16.dp),
                    onClick = onRootCommentClick
                ) {
                    Text(
                        text = "说点什么，直接评论 UP 主和大家",
                        color = commentAppearance.secondaryTextColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (isRepliesLoading && replies.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CupertinoActivityIndicator()
                    }
                }
            } else if (replies.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (upOnlyFilter) "这个视频没有 UP 主的评论" else "暂无评论",
                            color = commentAppearance.secondaryTextColor
                        )
                    }
                }
            } else {
                items(
                    items = replies,
                    key = { it.rpid },
                    contentType = { resolveReplyItemContentType(it) }
                ) { reply ->
                    // [新增] 使用 DissolvableVideoCard 包裹
                    com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard(
                        isDissolving = reply.rpid in dissolvingIds,
                        onDissolveComplete = { onDeleteComment(reply.rpid) },
                        cardId = "comment_${reply.rpid}",
                        modifier = Modifier.padding(bottom = 1.dp) // 小间距防止裁剪
                    ) {
                        ReplyItemView(
                            showUpFlag = showUpFlag,
                            item = reply,
                            upMid = info.owner.mid,
                            emoteMap = emoteMap,
                            lightweightMode = lightweightCommentRendering,
                            onClick = {},
                            onSubClick = { onSubReplyClick(reply) },
                            onTimestampClick = onTimestampClick,
                            maxTimestampMs = info.pages.firstOrNull { it.cid == info.cid }?.duration?.times(1000L)
                                ?: info.pages.firstOrNull()?.duration?.times(1000L),
                            onImagePreview = { images, index, rect, textContent ->
                                onImagePreview(images, index, rect, textContent)
                            },
                            // [新增] 点赞事件
                            onLikeClick = { onCommentLike(reply.rpid) },
                            onReplyClick = { onCommentReplyClick(reply) },
                            onReportClick = { reason -> onReportComment(reply.rpid, reason) },
                            canToggleTop = shouldShowReplyTopAction(
                                currentMid = currentMid,
                                upMid = info.owner.mid,
                                item = reply
                            ),
                            onToggleTopClick = { onToggleTopComment(reply) },
                            // [修复] 正确传递点赞状态 (API数据 或 本地乐观更新)
                            isLiked = reply.action == 1 || reply.rpid in likedComments,
                            // [新增] 仅当评论 mid 与当前登录用户 mid 一致时显示删除按钮
                            onDeleteClick = if (currentMid > 0 && reply.mid == currentMid) {
                                { onDissolveStart(reply.rpid) }
                            } else null,
                            // [新增] URL 点击跳转
                            onUrlClick = onCommentUrlClick,
                            // [新增] 头像点击
                            onAvatarClick = { mid -> mid.toLongOrNull()?.let { onUpClick(it) } }
                        )
                    }
                }

                // 加载更多
                item {
                    val shouldLoadMore by remember(
                        listState,
                        replies.size,
                        replyCount,
                        isRepliesLoading,
                        isRepliesEnd
                    ) {
                        derivedStateOf {
                            shouldLoadMoreVideoComments(
                                lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                                totalItemsCount = listState.layoutInfo.totalItemsCount,
                                isLoading = isRepliesLoading,
                                isEnd = isRepliesEnd || replies.isEmpty() || replyCount <= 0 || replies.size >= replyCount
                            )
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) {
                            onLoadMoreReplies()
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isRepliesLoading -> CupertinoActivityIndicator()
                            isRepliesEnd || replies.size >= replyCount -> {
                                Text("—— end ——", color = commentAppearance.secondaryTextColor, fontSize = 12.sp)
                            }
                            // 当 shouldLoadMore 为 true 时才显示加载指示器
                            shouldLoadMore -> CupertinoActivityIndicator()
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = shouldShowBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 12.dp
                ),
            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.92f)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = rememberAppChevronUpIcon(),
                    contentDescription = "回到顶部"
                )
            }
        }
    }
}

@Composable
private fun VideoHeaderContent(
    info: ViewInfo,
    videoTags: List<VideoTag>,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    downloadProgress: Float,
    isInWatchLater: Boolean,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onUpClick: (Long) -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onGloballyPositioned: (Float) -> Unit,
    transitionEnabled: Boolean = false,  // 🔗 共享元素过渡开关
    ownerFollowerCount: Int? = null,
    ownerVideoCount: Int? = null,
    onFavoriteLongClick: () -> Unit = {},
    aiSummary: AiSummaryData? = null,
    aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState? = null,
    onRetryAiSummary: () -> Unit = {},
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    onTimestampClick: ((Long) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    showInteractionActions: Boolean = true,
    animateVideoDetailLayout: Boolean = true
) {
    val context = LocalContext.current
    val videoAiSummaryEntryEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoAiSummaryEntryEnabled(context)
        .collectAsState(initial = true)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface) // 🎨 [修复] 与 TabBar 统一使用 Surface (通常为白色/深灰色)，消除割裂感
            .onGloballyPositioned { coordinates ->
                onGloballyPositioned(coordinates.size.height.toFloat())
            }
    ) {
        UpInfoSection(
            info = info,
            isFollowing = isFollowing,
            onFollowClick = onFollowClick,
            onUpClick = onUpClick,
            showOwnerAvatar = true,
            followerCount = ownerFollowerCount,
            videoCount = ownerVideoCount,
            transitionEnabled = transitionEnabled  // 🔗 传递共享元素开关
        )

        VideoTitleWithDesc(
            info = info,
            videoTags = videoTags,
            transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
            bgmInfo = bgmInfo,
            bgmInfoList = bgmInfoList,
            onlineCount = onlineCount,
            showOnlineCount = showOnlineCount,
            onBgmClick = onBgmClick,
            animateLayout = animateVideoDetailLayout
        )

        // [新增] AI Summary
        if (shouldShowAiSummaryEntry(
                aiSummary = aiSummary,
                isAiSummaryEntryEnabled = videoAiSummaryEntryEnabled
            )
        ) {
            AiSummaryCard(
                aiSummary = aiSummary,
                onTimestampClick = onTimestampClick,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else if (videoAiSummaryEntryEnabled && aiSummaryPrompt != null) {
            AiSummaryPromptCard(
                promptState = aiSummaryPrompt,
                onActionClick = onRetryAiSummary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (showInteractionActions) {
            ActionButtonsRow(
                info = info,
                isFavorited = isFavorited,
                isLiked = isLiked,
                coinCount = coinCount,
                downloadProgress = downloadProgress,
                isInWatchLater = isInWatchLater,
                onFavoriteClick = onFavoriteClick,
                onLikeClick = onLikeClick,
                onCoinClick = onCoinClick,
                onTripleClick = onTripleClick,
                onCommentClick = {},
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                onFavoriteLongClick = onFavoriteLongClick,
                onShareClick = onShareClick
            )
        }

        info.ugc_season?.let { season ->
            CollectionRow(
                ugcSeason = season,
                currentBvid = info.bvid,
                currentCid = info.cid,
                onClick = onOpenCollectionSheet
            )
        }
    }
}

@Composable
private fun VideoDetailDanmakuSettingsPanel(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val danmakuScope = com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT
    val danmakuSettings by SettingsManager
        .getDanmakuSettings(context, danmakuScope)
        .collectAsState(initial = DanmakuSettings())

    var localOpacity by remember(danmakuSettings.opacity) { mutableFloatStateOf(danmakuSettings.opacity) }
    var localFontScale by remember(danmakuSettings.fontScale) { mutableFloatStateOf(danmakuSettings.fontScale) }
    var localSpeed by remember(danmakuSettings.speed) { mutableFloatStateOf(danmakuSettings.speed) }
    var localDisplayArea by remember(danmakuSettings.displayArea) { mutableFloatStateOf(danmakuSettings.displayArea) }
    var localMergeDuplicates by remember(danmakuSettings.mergeDuplicates) { mutableStateOf(danmakuSettings.mergeDuplicates) }
    var localAllowScroll by remember(danmakuSettings.allowScroll) { mutableStateOf(danmakuSettings.allowScroll) }
    var localAllowTop by remember(danmakuSettings.allowTop) { mutableStateOf(danmakuSettings.allowTop) }
    var localAllowBottom by remember(danmakuSettings.allowBottom) { mutableStateOf(danmakuSettings.allowBottom) }
    var localAllowColorful by remember(danmakuSettings.allowColorful) { mutableStateOf(danmakuSettings.allowColorful) }
    var localAllowSpecial by remember(danmakuSettings.allowSpecial) { mutableStateOf(danmakuSettings.allowSpecial) }
    var localBlockRulesRaw by remember(danmakuSettings.blockRulesRaw) { mutableStateOf(danmakuSettings.blockRulesRaw) }

    DanmakuSettingsPanel(
        isFullscreen = false,
        settingsScope = danmakuScope,
        opacity = localOpacity,
        fontScale = localFontScale,
        speed = localSpeed,
        displayArea = localDisplayArea,
        mergeDuplicates = localMergeDuplicates,
        allowScroll = localAllowScroll,
        allowTop = localAllowTop,
        allowBottom = localAllowBottom,
        allowColorful = localAllowColorful,
        allowSpecial = localAllowSpecial,
        showBlockRuleEditor = true,
        showSmartOcclusionSection = false,
        blockRulesRaw = localBlockRulesRaw,
        smartOcclusion = false,
        onOpacityChange = {
            localOpacity = it
            scope.launch { SettingsManager.setDanmakuOpacity(context, it, danmakuScope) }
        },
        onFontScaleChange = {
            localFontScale = it
            scope.launch { SettingsManager.setDanmakuFontScale(context, it, danmakuScope) }
        },
        onSpeedChange = {
            localSpeed = it
            scope.launch { SettingsManager.setDanmakuSpeed(context, it, danmakuScope) }
        },
        onDisplayAreaChange = {
            localDisplayArea = it
            scope.launch { SettingsManager.setDanmakuArea(context, it, danmakuScope) }
        },
        onMergeDuplicatesChange = {
            localMergeDuplicates = it
            scope.launch { SettingsManager.setDanmakuMergeDuplicates(context, it, danmakuScope) }
        },
        onAllowScrollChange = {
            localAllowScroll = it
            scope.launch { SettingsManager.setDanmakuAllowScroll(context, it, danmakuScope) }
        },
        onAllowTopChange = {
            localAllowTop = it
            scope.launch { SettingsManager.setDanmakuAllowTop(context, it, danmakuScope) }
        },
        onAllowBottomChange = {
            localAllowBottom = it
            scope.launch { SettingsManager.setDanmakuAllowBottom(context, it, danmakuScope) }
        },
        onAllowColorfulChange = {
            localAllowColorful = it
            scope.launch { SettingsManager.setDanmakuAllowColorful(context, it, danmakuScope) }
        },
        onAllowSpecialChange = {
            localAllowSpecial = it
            scope.launch { SettingsManager.setDanmakuAllowSpecial(context, it, danmakuScope) }
        },
        onBlockRulesRawChange = {
            localBlockRulesRaw = it
            scope.launch { SettingsManager.setDanmakuBlockRulesRaw(context, it, danmakuScope) }
        },
        onDismiss = onDismiss
    )
}

/**
 * Tab 栏组件
 */
@Composable
private fun VideoContentTabBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onDanmakuSendClick: () -> Unit,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    onDanmakuSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayerCollapsed: Boolean = false,
    onRestorePlayer: () -> Unit = {},
    onCommentDragHandleDrag: (Float) -> Unit = {},
    commentDragHandleVisible: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val layoutSpec = remember(configuration.screenWidthDp) {
        resolveVideoContentTabBarLayoutSpec(widthDp = configuration.screenWidthDp)
    }
    val danmakuActionLayoutPolicy = remember(configuration.screenWidthDp) {
        resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = configuration.screenWidthDp)
    }
    Column(
        modifier = modifier
    ) {
        AnimatedVisibility(
            visible = commentDragHandleVisible,
            enter = fadeIn(animationSpec = tween(120)) + expandVertically(),
            exit = fadeOut(animationSpec = tween(90)) + shrinkVertically()
        ) {
            VideoContentCommentDragHandle(
                onDrag = onCommentDragHandleDrag
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = layoutSpec.containerHorizontalPaddingDp.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarLiquidSegmentedControl(
                items = tabs,
                selectedIndex = selectedTabIndex,
                onSelected = onTabSelected,
                modifier = Modifier
                    .weight(layoutSpec.tabsRowWeight)
                    .padding(start = 0.dp, top = 5.dp, end = 8.dp, bottom = 5.dp),
                height = layoutSpec.segmentedControlHeightDp.dp,
                indicatorHeight = layoutSpec.segmentedControlIndicatorHeightDp.dp,
                labelFontSize = layoutSpec.unselectedTabFontSizeSp.sp
            )

            // [新增] 恢复画面按钮 (仅在播放器折叠时显示)
            AnimatedVisibility(
                visible = isPlayerCollapsed,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onRestorePlayer() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = rememberAppPlayIcon(),
                        contentDescription = "恢复画面",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "恢复画面",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            val danmakuToggleInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val danmakuActiveColor = MaterialTheme.colorScheme.primary
            val danmakuInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = danmakuActionLayoutPolicy.toggleTrailingPaddingDp.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (danmakuEnabled) {
                            danmakuActiveColor.copy(alpha = 0.16f)
                        } else {
                            danmakuInactiveColor.copy(alpha = 0.12f)
                        }
                    )
                    .clickable(
                        interactionSource = danmakuToggleInteraction,
                        indication = null,
                        onClick = onDanmakuToggle
                    )
                    .padding(
                        horizontal = danmakuActionLayoutPolicy.toggleHorizontalPaddingDp.dp,
                        vertical = danmakuActionLayoutPolicy.toggleVerticalPaddingDp.dp
                    )
            ) {
                Icon(
                    imageVector = rememberAppCommentIcon(),
                    contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                    tint = if (danmakuEnabled) danmakuActiveColor else danmakuInactiveColor,
                    modifier = Modifier.size(danmakuActionLayoutPolicy.toggleIconSizeDp.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (danmakuEnabled) "开" else "关",
                    fontSize = danmakuActionLayoutPolicy.toggleTextSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (danmakuEnabled) danmakuActiveColor else danmakuInactiveColor
                )
            }

            AnimatedVisibility(
                visible = shouldShowDanmakuSendInput(isPlayerCollapsed = isPlayerCollapsed),
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(onClick = onDanmakuSendClick)
                        .padding(
                            horizontal = danmakuActionLayoutPolicy.sendHorizontalPaddingDp.dp,
                            vertical = danmakuActionLayoutPolicy.sendVerticalPaddingDp.dp
                        )
                ) {
                    Text(
                        text = danmakuActionLayoutPolicy.sendLabel,
                        fontSize = danmakuActionLayoutPolicy.sendTextSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .padding(start = danmakuActionLayoutPolicy.settingsLeadingPaddingDp.dp)
                    .size(danmakuActionLayoutPolicy.settingsButtonSizeDp.dp)
                    .clickable(onClick = onDanmakuSettingsClick),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = rememberAppSettingsIcon(),
                        contentDescription = "弹幕设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(danmakuActionLayoutPolicy.settingsIconSizeDp.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
}

/**
 * 推荐视频标题
 */
@Composable
private fun VideoRecommendationHeader() {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp) // 优化：减少底部间距，使视频卡片更紧凑
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "相关推荐",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VideoContentCommentDragHandle(
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(onDrag) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        )
    }
}

internal fun resolveFirstRelatedItemIndex(hasPages: Boolean): Int {
    return if (hasPages) 3 else 2
}

/**
 * 视频标签行
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoTagsRow(tags: List<VideoTag>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.take(10).forEach { tag ->
            VideoTagChip(tagName = tag.tag_name)
        }
    }
}

/**
 * 视频标签芯片
 */
@Composable
fun VideoTagChip(tagName: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = tagName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .copyOnLongPress(tagName, "标签")
        )
    }
}
