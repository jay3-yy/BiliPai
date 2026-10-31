// 文件路径: feature/video/screen/VideoContentSection.kt
package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.feature.video.ui.components.commentSectionNotice
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.feature.video.ui.components.RelatedVideoList

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.material.icons.outlined.Edit

import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.common.copyOnLongPress
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.util.ShareUtils
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.components.biliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.blur.topSolidProgressiveFade
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyVoteCard
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.resolveDisplayBgmList
import com.android.purebilibili.feature.video.ui.section.resolveVideoDetailMotionBudget
import com.android.purebilibili.feature.video.ui.section.shouldAnimateVideoDetailLayout
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import com.android.purebilibili.feature.video.ui.components.RelatedVideoGridRow
import com.android.purebilibili.feature.video.ui.components.chunkRelatedVideosForHomeStyleGrid
import com.android.purebilibili.feature.video.ui.components.rememberRelatedVideoCardLayout
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
import com.android.purebilibili.feature.video.ui.components.CommentListHeader
import com.android.purebilibili.feature.video.ui.components.VideoCommentFloatingHeader
import com.android.purebilibili.feature.video.ui.components.CommentSortHeader
import com.android.purebilibili.feature.video.ui.components.CommentSortFilterBar
import com.android.purebilibili.feature.video.ui.components.CommentSearchSheet
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.VideoCommentVoteCard
import com.android.purebilibili.feature.video.ui.components.rememberVideoCommentAppearance
import com.android.purebilibili.feature.video.ui.components.resolveReplyItemContentType
import com.android.purebilibili.feature.video.ui.components.shouldShowReplyTopAction
import com.android.purebilibili.feature.video.ui.components.shouldShowVideoCommentBackToTop
import com.android.purebilibili.feature.video.ui.components.LandscapeSidePanel
import com.android.purebilibili.feature.video.ui.components.LandscapeSidePanelEdge
import com.android.purebilibili.feature.video.ui.components.videoCommentItemModifier
import com.android.purebilibili.feature.video.ui.components.videoCommentSkeletonItems
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.resolveVideoCommentSkeletonSlotCount
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.ui.section.AiSummarySheet
import com.android.purebilibili.feature.video.ui.section.VideoSupplementStatsActions
import com.android.purebilibili.feature.video.ui.section.VideoNoteListSheet
import com.android.purebilibili.feature.video.ui.section.VideoNoteDeleteConfirmDialog
import com.android.purebilibili.feature.video.ui.section.VideoNoteEditorSheet
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.note.buildVideoNoteShareText
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle

internal fun shouldShowDanmakuSendInput(isPlayerCollapsed: Boolean): Boolean = !isPlayerCollapsed

/** Trailing send/toggle chrome stays on both 简介 and 评论. */
internal fun shouldShowVideoContentTabBarDanmakuActions(
    selectedTabIndex: Int,
): Boolean = selectedTabIndex == 0 || selectedTabIndex == 1

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
    val geometry = com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry(
        dockHeightDp = containerHeightDp.toFloat(),
        indicatorHeightDp = indicatorHeightDp.toFloat(),
    )
    return geometry.pressedHeightDp > containerHeightDp
}


internal data class VideoContentTabBarLiquidChromeSpec(
    val reusesLiquidGlassDock: Boolean,
    val segmentedControlHeightDp: Int,
    val segmentedControlIndicatorHeightDp: Int,
    val labelFontSizeSp: Int,
    val itemWidthDp: Int?,
    val liquidGlassEffectsEnabled: Boolean,
    val useTransparentTabRowBackground: Boolean,
)

/** Two-character labels (简介/评论) plus dock item padding; matches comment-sort 13sp → 66dp. */
internal fun resolveVideoContentTabBarDockItemWidthDp(labelFontSizeSp: Int): Int {
    if (labelFontSizeSp <= 0) return 0
    return (labelFontSizeSp * 2) + 40
}

internal fun shouldReuseVideoContentTabBarLiquidGlassDock(
    androidNativeLiquidGlassEnabled: Boolean,
    hasBackdrop: Boolean,
): Boolean = androidNativeLiquidGlassEnabled && hasBackdrop

internal fun resolveVideoContentTabBarStartPaddingDp(
    reusesLiquidGlassDock: Boolean,
    containerHorizontalPaddingDp: Int,
): Int = if (reusesLiquidGlassDock) 16 else containerHorizontalPaddingDp

internal fun resolveVideoContentTabBarLiquidChromeSpec(
    androidNativeLiquidGlassEnabled: Boolean,
    hasBackdrop: Boolean,
    layoutSpec: VideoContentTabBarLayoutSpec,
): VideoContentTabBarLiquidChromeSpec {
    val reusesLiquidGlassDock = shouldReuseVideoContentTabBarLiquidGlassDock(
        androidNativeLiquidGlassEnabled = androidNativeLiquidGlassEnabled,
        hasBackdrop = hasBackdrop,
    )
    val labelFontSizeSp = layoutSpec.unselectedTabFontSizeSp
    return VideoContentTabBarLiquidChromeSpec(
        reusesLiquidGlassDock = reusesLiquidGlassDock,
        segmentedControlHeightDp = layoutSpec.segmentedControlHeightDp,
        segmentedControlIndicatorHeightDp = layoutSpec.segmentedControlIndicatorHeightDp,
        labelFontSizeSp = labelFontSizeSp,
        itemWidthDp = if (reusesLiquidGlassDock) {
            resolveVideoContentTabBarDockItemWidthDp(labelFontSizeSp)
        } else {
            null
        },
        liquidGlassEffectsEnabled = reusesLiquidGlassDock,
        useTransparentTabRowBackground = reusesLiquidGlassDock,
    )
}

internal fun resolveVideoContentTabBarLayoutSpec(widthDp: Int): VideoContentTabBarLayoutSpec {
    return if (widthDp < 400) {
        VideoContentTabBarLayoutSpec(
            tabsRowWeight = 1f,
            tabsRowScrollable = true,
            containerHorizontalPaddingDp = 8,
            tabHorizontalPaddingDp = 8,
            tabVerticalPaddingDp = 7,
            tabSpacingDp = 10,
            selectedTabFontSizeSp = 16,
            unselectedTabFontSizeSp = 15,
            indicatorWidthDp = 28,
            segmentedControlHeightDp = 40,
            segmentedControlIndicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp
        )
    } else {
        VideoContentTabBarLayoutSpec(
            tabsRowWeight = 1f,
            tabsRowScrollable = true,
            containerHorizontalPaddingDp = 12,
            tabHorizontalPaddingDp = 12,
            tabVerticalPaddingDp = 8,
            tabSpacingDp = 16,
            selectedTabFontSizeSp = 17,
            unselectedTabFontSizeSp = 16,
            indicatorWidthDp = 32,
            segmentedControlHeightDp = 40,
            segmentedControlIndicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp
        )
    }
}

internal data class VideoContentTabBarDanmakuActionLayoutPolicy(
    val toggleIconSizeDp: Int,
    val toggleButtonSizeDp: Int,
    val toggleTrailingPaddingDp: Int,
    val sendMinHeightDp: Int,
    val sendTextSizeSp: Int,
    val sendLabel: String,
)

/** Native PiliPlus tab-bar actions: plain “发弹幕” text + icon-only danmaku switch. */
internal fun resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp: Int): VideoContentTabBarDanmakuActionLayoutPolicy {
    val compact = widthDp < 400
    return VideoContentTabBarDanmakuActionLayoutPolicy(
        toggleIconSizeDp = 22,
        toggleButtonSizeDp = AppChromeSizeTokens.MinimumTouchTarget.value.toInt(),
        toggleTrailingPaddingDp = if (compact) 4 else 6,
        sendMinHeightDp = AppChromeSizeTokens.MinimumTouchTarget.value.toInt(),
        sendTextSizeSp = 12,
        sendLabel = "发弹幕",
    )
}

internal data class VideoContentTabSwitchAnimationSpec(
    val durationMs: Int
)

internal fun resolveVideoContentTabSwitchAnimationSpec(
    presentation: AppTopTabPresentation,
    liquidGlassEnabled: Boolean = false,
): VideoContentTabSwitchAnimationSpec {
    // Glass reuses the moving dock even when the underlying native style is underline.
    // Keep native MD3/Miuix timing unchanged when glass is disabled.
    if (liquidGlassEnabled) return VideoContentTabSwitchAnimationSpec(durationMs = 360)
    return when (presentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> VideoContentTabSwitchAnimationSpec(durationMs = 360)
        AppTopTabPresentation.MATERIAL_UNDERLINE,
        AppTopTabPresentation.TONAL_CAPSULE -> VideoContentTabSwitchAnimationSpec(durationMs = 240)
    }
}

internal fun resolveVideoContentEffectiveSelectedTabIndex(
    currentPage: Int,
    targetPage: Int,
    isScrollInProgress: Boolean,
    pageCount: Int
): Int {
    if (pageCount <= 0) return 0
    val current = currentPage.takeIf { it in 0 until pageCount } ?: 0
    return if (isScrollInProgress && targetPage in 0 until pageCount) {
        targetPage
    } else {
        current
    }
}

/** Extend the shared material with the visible comment page, never with scroll activity. */
internal fun resolveVideoContentCommentChromeHeightFraction(pagePosition: Float): Float =
    if (pagePosition.isFinite()) pagePosition.coerceIn(0f, 1f) else 0f

/** 简介与评论页之间始终支持横向分页，方向仲裁由共享的纵向优先手势门控处理。 */
internal fun shouldEnableVideoContentHorizontalPagerSwipe(
    currentPage: Int,
    commentPageIndex: Int,
    isPagerScrollInProgress: Boolean,
): Boolean = true

/**
 * 评论列表是否贴顶（仅贴顶时才允许上滑展开分段；浏览中上滑只滚列表）。
 */
internal fun isVideoContentCommentListAtTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean = firstVisibleItemIndex <= 0 && firstVisibleItemScrollOffset <= 0

internal fun shouldKeepVideoContentTabBarCollapse(
    settingEnabled: Boolean,
    selectedTabIndex: Int,
    commentPageIndex: Int = 1,
): Boolean = settingEnabled && selectedTabIndex == commentPageIndex

/**
 * 跟手折叠进度 0 = 全展开，1 = 全收起。
 * 由 [collapsePx] / [maxCollapsePx] 得到；列表已离开顶部时钳到 1，保证浏览评论时 chrome 收净。
 */
internal fun resolveVideoContentTabBarCollapseProgress(
    collapsePx: Float,
    maxCollapsePx: Float,
    selectedTabIndex: Int,
    listAtTop: Boolean,
    enabled: Boolean = true,
    commentPageIndex: Int = 1,
): Float {
    if (!enabled) return 0f
    if (selectedTabIndex != commentPageIndex) return 0f
    if (maxCollapsePx <= 0f) return 0f
    if (!listAtTop) return 1f
    return (collapsePx / maxCollapsePx).coerceIn(0f, 1f)
}

internal data class VideoContentTabBarCollapseScrollUpdate(
    val nextCollapsePx: Float,
    val consumedY: Float,
)

/**
 * Nested preScroll：评论 Tab 下先折叠/展开分段，再把剩余位移交给列表。
 * - availableY < 0（上滑内容）：先增加 collapse（收起），可随时反向打断
 * - availableY > 0 且列表贴顶：先减少 collapse（展开），可随时反向打断
 */
internal fun reduceVideoContentTabBarCollapseOnPreScroll(
    collapsePx: Float,
    maxCollapsePx: Float,
    availableY: Float,
    listAtTop: Boolean,
    enabled: Boolean,
): VideoContentTabBarCollapseScrollUpdate? {
    if (!enabled || maxCollapsePx <= 0f || availableY == 0f) return null
    val clampedCollapse = collapsePx.coerceIn(0f, maxCollapsePx)
    if (availableY < 0f) {
        val room = maxCollapsePx - clampedCollapse
        if (room <= 0f) return null
        val take = minOf(-availableY, room)
        if (take <= 0f) return null
        return VideoContentTabBarCollapseScrollUpdate(
            nextCollapsePx = clampedCollapse + take,
            consumedY = -take,
        )
    }
    if (!listAtTop || clampedCollapse <= 0f) return null
    val take = minOf(availableY, clampedCollapse)
    if (take <= 0f) return null
    return VideoContentTabBarCollapseScrollUpdate(
        nextCollapsePx = clampedCollapse - take,
        consumedY = take,
    )
}

/**
 * Nested postScroll：列表已贴顶后仍有未消费的上滑余量时，继续展开分段（fling 回顶可跟手展完）。
 */
internal fun reduceVideoContentTabBarCollapseOnPostScroll(
    collapsePx: Float,
    maxCollapsePx: Float,
    availableY: Float,
    listAtTop: Boolean,
    enabled: Boolean,
): VideoContentTabBarCollapseScrollUpdate? {
    if (!enabled || maxCollapsePx <= 0f || availableY <= 0f || !listAtTop) return null
    val clampedCollapse = collapsePx.coerceIn(0f, maxCollapsePx)
    if (clampedCollapse <= 0f) return null
    val take = minOf(availableY, clampedCollapse)
    if (take <= 0f) return null
    return VideoContentTabBarCollapseScrollUpdate(
        nextCollapsePx = clampedCollapse - take,
        consumedY = take,
    )
}

internal fun resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
    collapsePx: Float,
    maxCollapsePx: Float,
    listAtTop: Boolean,
    enabled: Boolean,
    returningToTop: Boolean = false,
): Float {
    if (!enabled || maxCollapsePx <= 0f) return 0f
    if (!listAtTop) return maxCollapsePx
    if (returningToTop) return 0f
    return collapsePx.coerceIn(0f, maxCollapsePx)
}


/**
 * 视频详情内容区域
 * 从 VideoDetailScreen.kt 提取出来，提高代码可维护性
 */
internal class VideoContentData(
    val info: ViewInfo,
    val introListState: LazyListState,
    val commentListState: LazyListState,
    val pagerState: PagerState,
    val relatedVideos: List<RelatedVideo>,
    val replies: List<ReplyItem>,
    val replyCount: Int,
    val emoteMap: Map<String, String>,
    val followingMids: Set<Long>,
    val videoTags: List<VideoTag>,
    val bgmInfo: BgmInfo?,
    val bgmInfoList: List<BgmInfo>,
    val relatedLoadState: VideoPlaybackUiState.RelatedLoadState = VideoPlaybackUiState.RelatedLoadState.READY,
)

internal class VideoContentEngagementState(
    val isLoggedIn: Boolean,
    val isFollowing: Boolean,
    val followStatusKnown: Boolean = false,
    val isFavorited: Boolean,
    val isLiked: Boolean,
    val likeStatusKnown: Boolean = false,
    val isDisliked: Boolean = false,
    val coinCount: Int,
    val currentPageIndex: Int,
    val downloadProgress: Float,
    val isInWatchLater: Boolean,
)

internal class VideoContentCommentState(
    val isRepliesLoading: Boolean,
    val isRepliesRefreshing: Boolean,
    val repliesError: String?,
    val isRepliesEnd: Boolean,
    val voteCard: ReplyVoteCard?,
    val sortMode: CommentSortMode,
    val currentMid: Long,
    val showUpFlag: Boolean,
    val showIdentityDecorations: Boolean,
    val dissolvingIds: Set<Long>,
    val likedComments: Set<Long>,
    val hatedComments: Set<Long>,
    val nextPage: Int,
    val pageControl: com.android.purebilibili.data.model.response.ReplyPageControl? = null,
)

internal class VideoContentNoteState(
    val aiSummary: AiSummaryData?,
    val aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState?,
    val videoNoteState: VideoNoteUiState,
)

internal class VideoContentPresentationState(
    val sponsorVideoLabel: String,
    val danmakuEnabled: Boolean,
    val transitionEnabled: Boolean,
    val isQuickReturnLimitedForSharedElements: Boolean,
    val sourceRouteForSharedElement: String?,
    val isPlayerCollapsed: Boolean,
    val onlineCount: String,
    val showOnlineCount: Boolean,
    val ownerFollowerCount: Int?,
    val ownerVideoCount: Int?,
    val showUpBadge: Boolean,
    val showInteractionActions: Boolean,
    val isVideoPlaying: Boolean,
    val bottomContentPadding: Dp,
    val commentPullToRefreshEnabled: Boolean = true,
    val relatedContentReady: Boolean = true,
    val commentPagePreloadReady: Boolean = true,
)

internal class VideoContentPrimaryActions(
    val onFollowClick: () -> Unit,
    val onFavoriteClick: () -> Unit,
    val onLikeClick: () -> Unit,
    val onDislikeClick: () -> Unit = {},
    val onCoinClick: () -> Unit,
    val onTripleClick: () -> Unit,
    val onPageSelect: (Int) -> Unit,
    val onUpClick: (Long) -> Unit,
    val onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    val onDownloadClick: () -> Unit,
    val onWatchLaterClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onTimestampClick: ((Long) -> Unit)?,
    val onDanmakuSendClick: () -> Unit,
    val onDanmakuToggle: () -> Unit,
    val onFavoriteLongClick: () -> Unit,
    val onBgmClick: (BgmInfo) -> Unit,
)

internal class VideoContentCommentActions(
    val onSortModeChange: (CommentSortMode) -> Unit,
    val onSubReplyClick: (ReplyItem, Long) -> Unit,
    val onCommentReplyClick: (ReplyItem) -> Unit,
    val onLoadMoreReplies: () -> Unit,
    val onRefreshReplies: () -> Unit,
    val onDeleteComment: (Long) -> Unit,
    val onDissolveStart: (Long) -> Unit,
    val onCommentLike: (Long) -> Unit,
    val onCommentHate: (Long) -> Unit,
    val onCommentUrlClick: (String) -> Unit,
    val onDescriptionUrlClick: ((String) -> Unit)?,
    val onSearchKeywordClick: (String) -> Unit,
    val onReportComment: (Long, Int) -> Unit,
    val onToggleTopComment: (ReplyItem) -> Unit,
    val onCheckCommentFraud: (ReplyItem) -> Unit,
)

internal class VideoContentNoteActions(
    val onRetryAiSummary: () -> Unit,
    val onCreateNoteDraftFromAiSummary: () -> Unit,
    val onOpenVideoNoteEditor: () -> Unit,
    val onCloseVideoNoteEditor: () -> Unit,
    val onVideoNoteDocumentChange: (VideoNoteEditorDocument) -> Unit,
    val onInsertVideoNoteTimestamp: () -> com.android.purebilibili.feature.video.note.VideoNoteBlock.Timestamp?,
    val onVideoNoteTimestampClick: (Long) -> Unit,
    val onSaveVideoNote: (VideoNoteEditorDocument) -> Unit,
    val onDeleteVideoNote: () -> Unit,
    val onRetryVideoNote: () -> Unit,
    val onLoadMorePublicVideoNotes: () -> Unit,
    val onPublicVideoNoteClick: (Long, String) -> Unit,
)

internal class VideoContentUiActions(
    val onSelectedTabChange: (Int) -> Unit,
    val onCommentScrollStateChange: (Int, Int) -> Unit,
)

@Composable
internal fun VideoContentSection(
    data: VideoContentData,
    engagementState: VideoContentEngagementState,
    commentState: VideoContentCommentState,
    noteState: VideoContentNoteState,
    presentationState: VideoContentPresentationState,
    primaryActions: VideoContentPrimaryActions,
    commentActions: VideoContentCommentActions,
    noteActions: VideoContentNoteActions,
    uiActions: VideoContentUiActions,
    // 手机正文传原始句柄，将分页/点赞读取限制在评论页；未接入的双栏入口沿用参数。
    commentUpdates: State<com.android.purebilibili.feature.video.viewmodel.CommentUiState>? = null,
) {
    val info = data.info
    val introListState = data.introListState
    val commentListState = data.commentListState
    val pagerState = data.pagerState
    val contentPagerInteractions = remember(pagerState) { MutableInteractionSource() }
    val nativePagerDragHeld by pagerState.interactionSource.collectIsDraggedAsState()
    val customPagerDragHeld by contentPagerInteractions.collectIsDraggedAsState()
    val relatedVideos = data.relatedVideos
    val replies = data.replies
    val replyCount = data.replyCount
    val emoteMap = data.emoteMap
    val followingMids = data.followingMids
    val videoTags = data.videoTags
    val bgmInfo = data.bgmInfo
    val bgmInfoList = data.bgmInfoList
    val isLoggedIn = engagementState.isLoggedIn
    val isFollowing = engagementState.isFollowing
    val followStatusKnown = engagementState.followStatusKnown
    val isFavorited = engagementState.isFavorited
    val isLiked = engagementState.isLiked
    val likeStatusKnown = engagementState.likeStatusKnown
    val isDisliked = engagementState.isDisliked
    val coinCount = engagementState.coinCount
    val currentPageIndex = engagementState.currentPageIndex
    val downloadProgress = engagementState.downloadProgress
    val isInWatchLater = engagementState.isInWatchLater
    val isRepliesLoading = commentState.isRepliesLoading
    val isRepliesRefreshing = commentState.isRepliesRefreshing
    val repliesError = commentState.repliesError
    val isRepliesEnd = commentState.isRepliesEnd
    val voteCard = commentState.voteCard
    val sortMode = commentState.sortMode
    val currentMid = commentState.currentMid
    val showUpFlag = commentState.showUpFlag
    val showIdentityDecorations = commentState.showIdentityDecorations
    val dissolvingIds = commentState.dissolvingIds
    val likedComments = commentState.likedComments
    val hatedComments = commentState.hatedComments
    val aiSummary = noteState.aiSummary
    val aiSummaryPrompt = noteState.aiSummaryPrompt
    val videoNoteState = noteState.videoNoteState
    val danmakuEnabled = presentationState.danmakuEnabled
    val transitionEnabled = presentationState.transitionEnabled
    val isQuickReturnLimitedForSharedElements = presentationState.isQuickReturnLimitedForSharedElements
    val sourceRouteForSharedElement = presentationState.sourceRouteForSharedElement
    val isPlayerCollapsed = presentationState.isPlayerCollapsed
    val commentPullToRefreshEnabled = presentationState.commentPullToRefreshEnabled
    val sponsorVideoLabel = presentationState.sponsorVideoLabel
    val onlineCount = presentationState.onlineCount
    val showOnlineCount = presentationState.showOnlineCount
    val ownerFollowerCount = presentationState.ownerFollowerCount
    val ownerVideoCount = presentationState.ownerVideoCount
    val showUpBadge = presentationState.showUpBadge
    val showInteractionActions = presentationState.showInteractionActions
    val isVideoPlaying = presentationState.isVideoPlaying
    val bottomContentPadding = presentationState.bottomContentPadding
    val onFollowClick = primaryActions.onFollowClick
    val onFavoriteClick = primaryActions.onFavoriteClick
    val onLikeClick = primaryActions.onLikeClick
    val onDislikeClick = primaryActions.onDislikeClick
    val onCoinClick = primaryActions.onCoinClick
    val onTripleClick = primaryActions.onTripleClick
    val onPageSelect = primaryActions.onPageSelect
    val onUpClick = primaryActions.onUpClick
    val onRelatedVideoClick = primaryActions.onRelatedVideoClick
    val onDownloadClick = primaryActions.onDownloadClick
    val onWatchLaterClick = primaryActions.onWatchLaterClick
    val onShareClick = primaryActions.onShareClick
    val onTimestampClick = primaryActions.onTimestampClick
    val onDanmakuSendClick = primaryActions.onDanmakuSendClick
    val onDanmakuToggle = primaryActions.onDanmakuToggle
    val onFavoriteLongClick = primaryActions.onFavoriteLongClick
    val onBgmClick = primaryActions.onBgmClick
    val onSortModeChange = commentActions.onSortModeChange
    val onSubReplyClick = commentActions.onSubReplyClick
    val onCommentReplyClick = commentActions.onCommentReplyClick
    val onLoadMoreReplies = commentActions.onLoadMoreReplies
    val onRefreshReplies = commentActions.onRefreshReplies
    val onDeleteComment = commentActions.onDeleteComment
    val onDissolveStart = commentActions.onDissolveStart
    val onCommentLike = commentActions.onCommentLike
    val onCommentHate = commentActions.onCommentHate
    val onCommentUrlClick = commentActions.onCommentUrlClick
    val onDescriptionUrlClick = commentActions.onDescriptionUrlClick
    val onSearchKeywordClick = commentActions.onSearchKeywordClick
    val onReportComment = commentActions.onReportComment
    val onToggleTopComment = commentActions.onToggleTopComment
    val onCheckCommentFraud = commentActions.onCheckCommentFraud
    val onRetryAiSummary = noteActions.onRetryAiSummary
    val onCreateNoteDraftFromAiSummary = noteActions.onCreateNoteDraftFromAiSummary
    val onOpenVideoNoteEditor = noteActions.onOpenVideoNoteEditor
    val onCloseVideoNoteEditor = noteActions.onCloseVideoNoteEditor
    val onVideoNoteDocumentChange = noteActions.onVideoNoteDocumentChange
    val onInsertVideoNoteTimestamp = noteActions.onInsertVideoNoteTimestamp
    val onVideoNoteTimestampClick = noteActions.onVideoNoteTimestampClick
    val onSaveVideoNote = noteActions.onSaveVideoNote
    val onDeleteVideoNote = noteActions.onDeleteVideoNote
    val onRetryVideoNote = noteActions.onRetryVideoNote
    val onLoadMorePublicVideoNotes = noteActions.onLoadMorePublicVideoNotes
    val onPublicVideoNoteClick = noteActions.onPublicVideoNoteClick
    val onSelectedTabChange = uiActions.onSelectedTabChange
    val onCommentScrollStateChange = uiActions.onCommentScrollStateChange
    val context = LocalContext.current
    val themeConfig = LocalAppThemeConfig.current
    val liquidGlassEnabled = themeConfig.liquidGlassEnabled
    val progressiveCommentHeaderEnabled = themeConfig.progressiveTopBlurEnabled &&
        !themeConfig.headerBlurEnabled
    val solidProgressiveCommentHeaderEnabled = themeConfig.progressiveTopFadeEnabled &&
        !themeConfig.headerBlurEnabled
    val immersiveVideoContentChromeEnabled = progressiveCommentHeaderEnabled ||
        solidProgressiveCommentHeaderEnabled
    val showVideoDetailCommentCount by remember(context) {
        SettingsManager.getShowVideoDetailCommentCount(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val tabs = remember(replyCount, showVideoDetailCommentCount) {
        listOf(
            "简介",
            if (showVideoDetailCommentCount) {
                "评论 ${FormatUtils.formatStat(replyCount.coerceAtLeast(0).toLong())}"
            } else {
                "评论"
            },
        )
    }
    val scope = rememberCoroutineScope()
    var showCommentSearchSheet by remember { mutableStateOf(false) }
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
    val isIntroListScrolling by remember(introListState) {
        derivedStateOf { introListState.isScrollInProgress }
    }
    val isCommentListScrolling by remember(commentListState) {
        derivedStateOf { commentListState.isScrollInProgress }
    }
    val videoDetailMotionBudget by remember(pagerState, introListState, commentListState) {
        derivedStateOf {
            resolveVideoDetailMotionBudget(
                isTabSwitching = pagerState.isScrollInProgress,
                isContentScrolling = isIntroListScrolling || isCommentListScrolling
            )
        }
    }
    val animateVideoDetailLayout = shouldAnimateVideoDetailLayout(videoDetailMotionBudget)
    val lightweightCommentRendering by remember(pagerState, isVideoPlaying, commentListState) {
        derivedStateOf {
            shouldUseLightweightCommentRendering(
                selectedTabIndex = pagerState.currentPage,
                isVideoPlaying = isVideoPlaying,
                isCommentListScrolling = isCommentListScrolling
            )
        }
    }

    // 评论图片预览状态
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var sourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }

    // 合集展开状态
    var showCollectionSheet by remember(info.bvid) { mutableStateOf(false) }
    var showPartsPanel by remember(info.bvid) { mutableStateOf(false) }
    var showAiSummarySheet by remember { mutableStateOf(false) }
    var showNoteListSheet by remember { mutableStateOf(false) }
    var confirmDeleteNote by remember { mutableStateOf(false) }
    val onShareVideoNote: (VideoNoteEditorDocument, Boolean) -> Unit = { document, isDraft ->
        ShareUtils.shareText(
            context = context,
            subject = document.title.ifBlank { info.title },
            text = buildVideoNoteShareText(
                videoTitle = info.title,
                bvid = info.bvid,
                document = document,
                isDraft = isDraft
            ),
            chooserTitle = "分享视频笔记"
        )
    }
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val tabSwitchAnimationSpec = remember(playerChromeProfile.tabPresentation, liquidGlassEnabled) {
        resolveVideoContentTabSwitchAnimationSpec(playerChromeProfile.tabPresentation, liquidGlassEnabled)
    }
    val componentMotionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val tabSelectionAnimationSpec = remember(tabSwitchAnimationSpec, liquidGlassEnabled, componentMotionEnabled) {
        if (liquidGlassEnabled && !componentMotionEnabled) snap<Float>() else tween<Float>(
            durationMillis = tabSwitchAnimationSpec.durationMs,
            easing = AppMotionEasing.M1Standard,
        )
    }
    // Only glass opts into shared tap/release timing. Native pagers keep their default fling.
    val glassTabFlingBehavior = if (liquidGlassEnabled) {
        PagerDefaults.flingBehavior(pagerState, snapAnimationSpec = tabSelectionAnimationSpec)
    } else null
    val latestOnSelectedTabChange by rememberUpdatedState(onSelectedTabChange)

    val onTabSelected: (Int) -> Unit = { index ->
        scope.launch {
            // Continue from the live page offset when a tap interrupts a swipe or another
            // tap. The helper takes over the scroll mutation without resetting the offset.
            // Native and glass retain their own timing and fling behavior above.
            animatePagerSelection(
                pagerState = pagerState,
                targetPage = index,
                animationSpec = tabSelectionAnimationSpec,
            )
        }
    }
    LaunchedEffect(pagerState, tabs.size) {
        snapshotFlow {
            resolveVideoContentEffectiveSelectedTabIndex(
                currentPage = pagerState.currentPage,
                targetPage = pagerState.targetPage,
                isScrollInProgress = pagerState.isScrollInProgress,
                pageCount = tabs.size
            )
        }
            .distinctUntilChanged()
            .collect { effectiveTabIndex ->
                latestOnSelectedTabChange(effectiveTabIndex)
            }
    }
    LaunchedEffect(commentListState) {
        snapshotFlow { commentListState.firstVisibleItemIndex to commentListState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { state: Pair<Int, Int> ->
                onCommentScrollStateChange(state.first, state.second)
            }
    }

    // 评论 Tab：「简介|评论」分段 nestedScroll 跟手折叠/展开，反向滑动立即打断。
    val density = LocalDensity.current
    // 首次测量前使用单行占位；之后背景与列表只读取同一份真实高度。
    val commentHeaderHeightPx = remember(density, liquidGlassEnabled) {
        mutableFloatStateOf(with(density) { 46.dp.toPx() })
    }
    val commentHeaderHeightProvider = remember(commentHeaderHeightPx) {
        { commentHeaderHeightPx.floatValue }
    }
    var tabBarMaxHeightPx by remember { mutableFloatStateOf(0f) }
    // 标签栏不再随滚动收起；滚动折叠机制整体转移到浮动评论栏：
    // 「最热评论」标题文字不设开关、下滑必隐回顶出现，排序/搜索行由设置开关控制。
    var commentHeaderCollapsePx by remember(commentListState) { mutableFloatStateOf(0f) }
    val commentListAtTop by remember(commentListState) {
        derivedStateOf {
            isVideoContentCommentListAtTop(
                firstVisibleItemIndex = commentListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = commentListState.firstVisibleItemScrollOffset,
            )
        }
    }
    val showCommentBackToTop by remember(commentListState) {
        derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = commentListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = commentListState.firstVisibleItemScrollOffset,
            )
        }
    }
    val showIntroBackToTop by remember(introListState) {
        derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = introListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = introListState.firstVisibleItemScrollOffset,
            )
        }
    }
    val backToTopButtonEnabled = rememberBackToTopButtonEnabled()
    val tabBarScrollHideEnabled by SettingsManager
        .getVideoDetailChromeScrollHideEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val commentHeaderCollapseEnabled by remember(pagerState) {
        derivedStateOf {
            shouldKeepVideoContentTabBarCollapse(
                settingEnabled = true,
                selectedTabIndex = pagerState.currentPage,
            )
        }
    }
    var commentListWasAtTop by remember(commentListState) { mutableStateOf(commentListAtTop) }
    // 离开顶部时收净；滚动、排序或刷新使列表重新贴顶后恢复，无需额外下拉。
    // 仍贴顶时保留当前跟手位移，避免打断正在进行的折叠手势。
    LaunchedEffect(commentListState, commentHeaderCollapseEnabled, commentListAtTop, commentHeaderHeightPx.floatValue) {
        commentHeaderCollapsePx = resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
            collapsePx = commentHeaderCollapsePx,
            maxCollapsePx = commentHeaderHeightPx.floatValue,
            listAtTop = commentListAtTop,
            enabled = commentHeaderCollapseEnabled,
            returningToTop = commentListAtTop && !commentListWasAtTop,
        )
        commentListWasAtTop = commentListAtTop
    }
    val tabBarCollapseConnection = remember(
        pagerState,
        commentListState,
        commentHeaderCollapseEnabled,
        commentListAtTop,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val update = reduceVideoContentTabBarCollapseOnPreScroll(
                    collapsePx = commentHeaderCollapsePx,
                    maxCollapsePx = commentHeaderHeightPx.floatValue,
                    availableY = available.y,
                    listAtTop = commentListAtTop,
                    enabled = commentHeaderCollapseEnabled && !pagerState.isScrollInProgress,
                ) ?: return Offset.Zero
                commentHeaderCollapsePx = update.nextCollapsePx
                return Offset(0f, update.consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val update = reduceVideoContentTabBarCollapseOnPostScroll(
                    collapsePx = commentHeaderCollapsePx,
                    maxCollapsePx = commentHeaderHeightPx.floatValue,
                    availableY = available.y,
                    listAtTop = commentListAtTop,
                    enabled = commentHeaderCollapseEnabled && !pagerState.isScrollInProgress,
                ) ?: return Offset.Zero
                commentHeaderCollapsePx = update.nextCollapsePx
                return Offset(0f, update.consumedY)
            }
        }
    }
    // Read the scroll-driven collapse only in measure/draw, not while composing the pages.
    val commentHeaderCollapseProgress = {
        resolveVideoContentTabBarCollapseProgress(
            collapsePx = commentHeaderCollapsePx,
            maxCollapsePx = commentHeaderHeightPx.floatValue,
            selectedTabIndex = pagerState.currentPage,
            listAtTop = commentListAtTop,
            enabled = true,
        )
    }
    // 标签栏始终按完整测量高度占位，不再消费折叠位移。
    val tabBarVisibleHeightPx = { tabBarMaxHeightPx }
    // 排序/搜索行仅在开关开启时随折叠收起；标题文字始终按进度隐藏。
    val commentHeaderVisibleHeightPx = {
        val fullHeight = commentHeaderHeightPx.floatValue
        if (tabBarScrollHideEnabled) {
            (fullHeight - commentHeaderCollapsePx).coerceAtLeast(0f)
        } else {
            fullHeight
        }
    }
    val pageContentPadding = remember(density, bottomContentPadding, immersiveVideoContentChromeEnabled, commentListState) {
        videoContentPadding(
            topPx = { if (immersiveVideoContentChromeEnabled) tabBarVisibleHeightPx() else 0f },
            bottom = bottomContentPadding,
            density = density,
        )
    }
    // Match the home bottom dock: one full-size content source, with liquid docks rendered as
    // overlay siblings outside that source. A source attached only to the LazyColumns starts
    // below the tab row, so sampling at the dock's coordinates resolves outside its bounds.
    val videoContentMiuixBackdrop = rememberMiuixLayerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(tabBarCollapseConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .miuixLayerBackdrop(videoContentMiuixBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface)
            )

            HorizontalPager(
                state = pagerState,
                flingBehavior = glassTabFlingBehavior,
                beyondViewportPageCount = resolveVideoDetailBeyondViewportPageCount(
                    selectedTabIndex = pagerState.currentPage,
                    commentPagePreloadReady = presentationState.commentPagePreloadReady,
                ),
                userScrollEnabled = shouldEnableVideoContentHorizontalPagerSwipe(
                            currentPage = pagerState.currentPage,
                            commentPageIndex = 1,
                            isPagerScrollInProgress = pagerState.isScrollInProgress,
                        ) && !liquidGlassEnabled,
                // Glass uses one child-first direction detector, rather than allowing the
                // native pager / Miuix cross-axis recognizer to race the comment list.
                // With glass off the native pager and its defaults are unchanged.
                modifier = Modifier
                    .fillMaxSize()
                    .verticalPriorityHorizontalPagerSwipe(
                        state = pagerState,
                        enabled = liquidGlassEnabled && shouldEnableVideoContentHorizontalPagerSwipe(
                            currentPage = pagerState.currentPage,
                            commentPageIndex = 1,
                            isPagerScrollInProgress = pagerState.isScrollInProgress,
                        ),
                        forceVerticalPriority = true,
                        dragInteractionSource = contentPagerInteractions,
                    )
                    .then(
                        if (immersiveVideoContentChromeEnabled) {
                            Modifier
                        } else {
                            Modifier.videoContentTopPadding(tabBarVisibleHeightPx)
                        }
                    )

            ) { page ->
                when (page) {
                    0 -> VideoIntroTab(
                        listState = introListState,
                        modifier = Modifier,
                        info = info,
                        relatedVideos = relatedVideos,
                        relatedLoadState = data.relatedLoadState,
                        relatedContentReady = presentationState.relatedContentReady,
                        currentPageIndex = currentPageIndex,
                        followingMids = followingMids,
                        videoTags = videoTags,
                        isFollowing = isFollowing,
                        followStatusKnown = followStatusKnown,
                        isFavorited = isFavorited,
                        isLiked = isLiked,
                        likeStatusKnown = likeStatusKnown,
                        isDisliked = isDisliked,
                        coinCount = coinCount,
                        downloadProgress = downloadProgress,
                        isInWatchLater = isInWatchLater,
                        isLoggedIn = isLoggedIn,
                        isVideoPlaying = isVideoPlaying,
                        onFollowClick = onFollowClick,
                        onFavoriteClick = onFavoriteClick,
                        onLikeClick = onLikeClick,
                        onDislikeClick = onDislikeClick,
                        onCoinClick = onCoinClick,
                        onTripleClick = onTripleClick,
                        onCommentClick = { onTabSelected(1) },
                        onPageSelect = onPageSelect,
                        onUpClick = onUpClick,
                        onRelatedVideoClick = onRelatedVideoClick,
                        onOpenCollectionSheet = { showCollectionSheet = true },
                        onOpenPartsPanel = { showPartsPanel = true },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onShareClick = onShareClick,
                        contentPadding = pageContentPadding,
                        transitionEnabled = transitionEnabled,
                        isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
                        sourceRouteForSharedElement = sourceRouteForSharedElement,
                        ownerFollowerCount = ownerFollowerCount,
                        ownerVideoCount = ownerVideoCount,
                        showUpBadge = showUpBadge,
                        onFavoriteLongClick = onFavoriteLongClick,
                        sponsorVideoLabel = sponsorVideoLabel,
                        aiSummary = aiSummary,
                        aiSummaryPrompt = aiSummaryPrompt,
                        onShowAiSummarySheet = { showAiSummarySheet = true },
                        onShowNoteListSheet = { showNoteListSheet = true },
                        bgmInfo = bgmInfo,
                        bgmInfoList = bgmInfoList,
                        onlineCount = onlineCount,
                        showOnlineCount = showOnlineCount,
                        onTimestampClick = onTimestampClick,
                        onBgmClick = onBgmClick,
                        onDescriptionUrlClick = onDescriptionUrlClick,
                        onSearchKeywordClick = onSearchKeywordClick,
                        showInteractionActions = showInteractionActions,
                        animateVideoDetailLayout = animateVideoDetailLayout
                    )
                    1 -> VideoDetailCommentUpdates(commentUpdates) { pageComments ->
                        VideoCommentTab(
                            listState = commentListState,
                            modifier = Modifier,
                            upMid = info.owner.mid,
                            maxTimestampMs = info.pages.firstOrNull { it.cid == info.cid }?.duration?.times(1000L)
                                ?: info.pages.firstOrNull()?.duration?.times(1000L),
                            replies = pageComments?.replies ?: replies,
                            replyCount = replyCount,
                            emoteMap = emoteMap,
                            isRepliesLoading = pageComments?.isRepliesLoading ?: isRepliesLoading,
                            isRepliesRefreshing = pageComments?.isRepliesRefreshing ?: isRepliesRefreshing,
                            repliesError = if (pageComments != null) pageComments.repliesError else repliesError,
                            isRepliesEnd = pageComments?.isRepliesEnd ?: isRepliesEnd,
                            paginationPage = pageComments?.nextPage ?: commentState.nextPage,
                            pageControl = pageComments?.pageControl ?: commentState.pageControl,
                            voteCard = if (pageComments != null) pageComments.voteCard else voteCard,
                            onUpClick = onUpClick,
                            onSubReplyClick = onSubReplyClick,
                            onCommentReplyClick = onCommentReplyClick,
                            onLoadMoreReplies = onLoadMoreReplies,
                            onRefreshReplies = onRefreshReplies,
                            pullToRefreshEnabled = commentPullToRefreshEnabled,
                            onImagePreview = { images, index, rect, textContent ->
                                previewImages = images
                                previewInitialIndex = index
                                sourceRect = rect
                                previewTextContent = textContent
                                showImagePreview = true
                            },
                            onTimestampClick = onTimestampClick,
                            showUpFlag = pageComments?.showUpFlag ?: showUpFlag,
                            contentPadding = pageContentPadding,
                            currentMid = pageComments?.currentMid ?: currentMid,
                            dissolvingIds = pageComments?.dissolvingIds ?: dissolvingIds,
                            onDeleteComment = onDeleteComment,
                            onDissolveStart = onDissolveStart,
                            onCommentLike = onCommentLike,
                            onCommentHate = onCommentHate,
                            likedComments = pageComments?.likedComments ?: likedComments,
                            hatedComments = pageComments?.hatedComments ?: hatedComments,
                            onCommentUrlClick = onCommentUrlClick,
                            onReportComment = onReportComment,
                            onToggleTopComment = onToggleTopComment,
                            onCheckCommentFraud = onCheckCommentFraud,
                            showIdentityDecorations = showIdentityDecorations,
                            lightweightCommentRendering = lightweightCommentRendering,
                            sortMode = sortMode,
                            onSortModeChange = onSortModeChange,
                            showNativeSortHeader = !liquidGlassEnabled,
                            showSortControlInHeader = true,
                            // 详情页评论标题在玻璃开/关时统一使用紧凑字阶；仅影响这一处调用。
                            compactSortHeader = true,
                            // Glass title and sort share the sibling overlay. Keep its space as list
                            // padding so hiding chrome cannot resize the list or move its anchors.
                            showHeader = !immersiveVideoContentChromeEnabled && !liquidGlassEnabled,
                            floatingHeaderContentPaddingPx = if (immersiveVideoContentChromeEnabled || liquidGlassEnabled) {
                                commentHeaderHeightProvider
                            } else null,
                            onSearchClick = { showCommentSearchSheet = true },
                        )
                    }
                }
            }
        }

        if (immersiveVideoContentChromeEnabled) {
            // 顶部标签与评论标题/排序共用同一张渐进模糊材质，避免两个独立渐变
            // 在相邻边界重新起算而形成断层。
            // 液态玻璃开启时，播放器折叠/隐藏后 chrome 仍对滚动中的评论内容做
            // 模糊采样；关闭时保持原有行为——折叠态使用内容页底色背景层。
            // 背景与评论栏共用实际可见高度：整行收起时同步缩短，保留排序控件时仍覆盖该行。
            // 在测量阶段读取连续高度，避免折叠结束后留下整行底色遮住评论。
            val chromeBlurActive = !isPlayerCollapsed || liquidGlassEnabled
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .videoContentHeight {
                        val commentFraction = resolveVideoContentCommentChromeHeightFraction(
                            pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        )
                        tabBarVisibleHeightPx() + commentHeaderVisibleHeightPx() * commentFraction
                    }
                    .background(
                        if (isPlayerCollapsed && !liquidGlassEnabled) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        }
                    )
                    .biliPaiProgressiveTopBlur(
                        backdrop = videoContentMiuixBackdrop,
                        enabled = progressiveCommentHeaderEnabled && chromeBlurActive,
                        surfaceColor = Color.Transparent,
                    )
                    .topSolidProgressiveFade(
                        surfaceColor = MaterialTheme.colorScheme.surface,
                        enabled = solidProgressiveCommentHeaderEnabled && chromeBlurActive,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.TopStart,
        ) {
            VideoContentTabBar(
                tabs = tabs,
                measurementLabels = listOf(
                    "简介",
                    if (showVideoDetailCommentCount) {
                        "评论 ${FormatUtils.formatStat(info.stat.reply.coerceAtLeast(0).toLong())}"
                    } else "评论",
                ),
                replyCount = replyCount,
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = onTabSelected,
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                onDanmakuSendClick = onDanmakuSendClick,
                danmakuEnabled = danmakuEnabled,
                onDanmakuToggle = onDanmakuToggle,
                tabSwipeModifier = Modifier.verticalPriorityHorizontalPagerSwipe(
                    state = pagerState,
                    enabled = shouldEnableVideoContentHorizontalPagerSwipe(
                        currentPage = pagerState.currentPage,
                        commentPageIndex = 1,
                        isPagerScrollInProgress = pagerState.isScrollInProgress,
                    ),
                    forceVerticalPriority = liquidGlassEnabled,
                    dragInteractionSource = contentPagerInteractions,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = tabBarMaxHeightPx > 0f)
                    .onSizeChanged { size ->
                        val measured = size.height.toFloat()
                        if (measured > 0f &&
                            (tabBarMaxHeightPx <= 0f || commentHeaderCollapsePx <= 0.5f)
                        ) {
                            tabBarMaxHeightPx = measured
                        }
                    },
                isPlayerCollapsed = isPlayerCollapsed,
                miuixBackdrop = videoContentMiuixBackdrop,
                indicatorPositionProvider = {
                    pagerState.tabIndicatorPosition
                },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
                isPagerDraggingProvider = { nativePagerDragHeld || customPagerDragHeld },
                pagerTargetPositionProvider = { pagerState.targetPage.toFloat() },
            )
        }

        // Keep the glass dock outside the backdrop capture subtree to avoid recursive RenderNode
        // sampling, while translating it with the comment page's actual pager position.
        if (liquidGlassEnabled || immersiveVideoContentChromeEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .videoContentTopPadding(tabBarVisibleHeightPx)
                    // 排序/搜索行由开关决定是否随折叠收起；关闭开关时整行保留，
                    // 仅标题文字按折叠进度隐藏。
                    .then(
                        if (tabBarScrollHideEnabled) {
                            Modifier
                                .videoContentHeight(commentHeaderVisibleHeightPx)
                                .graphicsLayer {
                                    clip = commentHeaderCollapseProgress() > 0.001f
                                }
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        translationX = pagerState.getOffsetDistanceInPages(1) * size.width
                    },
            ) {
                VideoCommentFloatingHeader(
                    count = replyCount,
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    onSearchClick = { showCommentSearchSheet = true },
                    backdrop = videoContentMiuixBackdrop,
                    liquidGlassEnabled = liquidGlassEnabled,
                    compactTitleTypography = true,
                    titleCollapseProgress = commentHeaderCollapseProgress,
                    modifier = Modifier
                        // 子项按自然高度测量、折叠容器只做高度裁剪：否则收起时
                        // 测量高度被同步压小，形成回环后回顶也无法恢复。
                        .wrapContentHeight(unbounded = true)
                        .onSizeChanged { size ->
                            if (size.height > 0 && commentHeaderHeightPx.floatValue != size.height.toFloat()) {
                                commentHeaderHeightPx.floatValue = size.height.toFloat()
                            }
                        },
                )
            }
        }

        val isBackToTopVisible = backToTopButtonEnabled && when (pagerState.currentPage) {
            0 -> showIntroBackToTop
            1 -> showCommentBackToTop
            else -> false
        }
        AppLiquidGlassBackToTopButton(
            visible = isBackToTopVisible,
            onClick = {
                scope.launch {
                    if (pagerState.currentPage == 0) {
                        introListState.animateScrollToItem(0)
                    } else {
                        commentListState.animateScrollToItem(0)
                    }
                    commentHeaderCollapsePx = 0f
                }
            },
            backdrop = videoContentMiuixBackdrop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = bottomContentPadding + 12.dp,
                ),
        )

        // Inline 弹幕设置不是 Dialog，必须在详情内容之后绘制，避免被列表盖住。
        if (showImagePreview && previewImages.isNotEmpty()) {
            ImagePreviewDialog(
                images = previewImages,
                initialIndex = previewInitialIndex,
                sourceRect = sourceRect?.rect,
                sourceRects = sourceRect?.galleryRects.orEmpty(),
                sourceCornerRadiusDp = sourceRect?.cornerRadiusDp
                    ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
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
                    currentVideoInfo = info,
                    currentBvid = info.bvid,
                    currentCid = info.cid,
                    inlinePanel = true,
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

        if (showPartsPanel && info.pages.size > 1) {
            com.android.purebilibili.feature.video.ui.components.PlayerEpisodeMenu(
                pages = info.pages,
                currentPageIndex = currentPageIndex,
                cover = info.pic,
                inlinePanel = true,
                onDismiss = { showPartsPanel = false },
                onPageSelect = onPageSelect,
            )
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
                confirmDeleteNote = true
            },
            onShareClick = { document -> onShareVideoNote(document, false) },
            onPublicNoteClick = onPublicVideoNoteClick,
            onAuthorClick = { mid ->
                if (mid > 0L) onDescriptionUrlClick?.invoke("https://space.bilibili.com/$mid")
            },
            onLoadMore = onLoadMorePublicVideoNotes,
            onOfficialEditorClick = {
                showNoteListSheet = false
                onDescriptionUrlClick?.invoke(
                    "https://www.bilibili.com/h5/note-app?oid=${info.aid}&pagefrom=ugcvideo"
                )
            }
        )

        VideoNoteEditorSheet(
            noteState = videoNoteState,
            onDismiss = onCloseVideoNoteEditor,
            onDocumentChange = onVideoNoteDocumentChange,
            currentTimestampProvider = onInsertVideoNoteTimestamp,
            onTimestampClick = onVideoNoteTimestampClick,
            onShare = { document -> onShareVideoNote(document, videoNoteState.editorFromAiSummary) },
            onSave = onSaveVideoNote
        )

        VideoNoteDeleteConfirmDialog(
            visible = confirmDeleteNote,
            deleting = videoNoteState.deleting,
            onConfirm = {
                confirmDeleteNote = false
                onDeleteVideoNote()
            },
            onDismiss = { confirmDeleteNote = false }
        )

        if (showCommentSearchSheet) {
            CommentSearchSheet(
                replies = commentUpdates?.value?.replies ?: replies,
                upMid = info.owner.mid,
                onCommentClick = { reply ->
                    onCommentReplyClick(reply)
                },
                onSubReplyClick = { rootReply ->
                    onSubReplyClick(rootReply, 0L)
                },
                onDismiss = { showCommentSearchSheet = false },
                miuixBackdrop = videoContentMiuixBackdrop,
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
    relatedLoadState: VideoPlaybackUiState.RelatedLoadState,
    relatedContentReady: Boolean = true,
    currentPageIndex: Int,
    followingMids: Set<Long>,
    videoTags: List<VideoTag>,
    isFollowing: Boolean,
    followStatusKnown: Boolean = false,
    isFavorited: Boolean,
    isLiked: Boolean,
    likeStatusKnown: Boolean = false,
    isDisliked: Boolean = false,
    coinCount: Int,
    downloadProgress: Float,
    isInWatchLater: Boolean,
    isLoggedIn: Boolean = false,
    isVideoPlaying: Boolean = false,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit = {},
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onCommentClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onOpenPartsPanel: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onDescriptionUrlClick: ((String) -> Unit)? = null,
    onSearchKeywordClick: (String) -> Unit = {},
    contentPadding: PaddingValues,
    transitionEnabled: Boolean = false,  // 🔗 共享元素过渡开关
    isQuickReturnLimitedForSharedElements: Boolean = false,
    sourceRouteForSharedElement: String? = null,
    ownerFollowerCount: Int? = null,
    ownerVideoCount: Int? = null,
    showUpBadge: Boolean = true,
    onFavoriteLongClick: () -> Unit = {},
    aiSummary: AiSummaryData? = null,
    aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState? = null,
    onShowAiSummarySheet: () -> Unit = {},
    onShowNoteListSheet: () -> Unit = {},
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    onTimestampClick: ((Long) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    sponsorVideoLabel: String = "",
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    showInteractionActions: Boolean = true,
    animateVideoDetailLayout: Boolean = true,
) {
    val visibleRelatedVideos = relatedVideos
    val relatedRows = remember(visibleRelatedVideos) {
        chunkRelatedVideosForHomeStyleGrid(visibleRelatedVideos)
    }
    val relatedVideoCardLayout = rememberRelatedVideoCardLayout()
    RelatedVideoList(
        cardLayout = relatedVideoCardLayout,
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) { cardPresentation ->
        // 1. 移入的 Header 区域
        item(key = "video_intro_header", contentType = "video_intro_header") {
            VideoHeaderContent(
                info = info,
                videoTags = videoTags,
                currentPageIndex = currentPageIndex,
                onPageSelect = onPageSelect,
                isFollowing = isFollowing,
                followStatusKnown = followStatusKnown,
                isFavorited = isFavorited,
                isLiked = isLiked,
                likeStatusKnown = likeStatusKnown,
                isDisliked = isDisliked,
                coinCount = coinCount,
                downloadProgress = downloadProgress,
                isInWatchLater = isInWatchLater,
                isLoggedIn = isLoggedIn,
                isVideoPlaying = isVideoPlaying,
                onFollowClick = onFollowClick,
                onFavoriteClick = onFavoriteClick,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick,
                onCoinClick = onCoinClick,
                onTripleClick = onTripleClick,
                onCommentClick = onCommentClick,
                onUpClick = onUpClick,
                onOpenCollectionSheet = onOpenCollectionSheet,
                onOpenPartsPanel = onOpenPartsPanel,
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                onShareClick = onShareClick,

                onGloballyPositioned = { },
                transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
                isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
                sourceRouteForSharedElement = sourceRouteForSharedElement,
                ownerFollowerCount = ownerFollowerCount,
                ownerVideoCount = ownerVideoCount,
                onFavoriteLongClick = onFavoriteLongClick,
                sponsorVideoLabel = sponsorVideoLabel,
                aiSummary = aiSummary,
                aiSummaryPrompt = aiSummaryPrompt,
                onShowAiSummarySheet = onShowAiSummarySheet,
                onShowNoteListSheet = onShowNoteListSheet,
                bgmInfo = bgmInfo,
                bgmInfoList = bgmInfoList,
                relatedVideos = relatedVideos,
                onlineCount = onlineCount,
                showOnlineCount = showOnlineCount,
                onTimestampClick = onTimestampClick,
                onBgmClick = onBgmClick,
                onDescriptionUrlClick = onDescriptionUrlClick,
                onRelatedVideoClick = onRelatedVideoClick,
                onSearchKeywordClick = onSearchKeywordClick,
                showInteractionActions = showInteractionActions,
                animateVideoDetailLayout = animateVideoDetailLayout
            )
        }

        // Keep loaded rows and their keys even while expensive images/shared bounds wait
        // for the entry handoff. Network loading uses the same card geometry per lazy row.
        if (visibleRelatedVideos.isEmpty() &&
            (!relatedContentReady || relatedLoadState == VideoPlaybackUiState.RelatedLoadState.LOADING)
        ) {
            items(count = com.android.purebilibili.feature.video.ui.components.RELATED_VIDEO_SKELETON_ROW_COUNT, key = { "video_related_loading_$it" }, contentType = { "video_related_loading" }) {
                com.android.purebilibili.feature.video.ui.components.RelatedVideoGridRowSkeleton(
                    cardLayout = relatedVideoCardLayout,
                    cardPresentation = cardPresentation,
                )
            }
        } else if (visibleRelatedVideos.isEmpty()) {
            item(key = "video_related_empty") {
                AppText(
                    if (relatedLoadState == VideoPlaybackUiState.RelatedLoadState.FAILED) "相关推荐加载失败" else "暂无相关推荐",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        itemsIndexed(
            items = relatedRows,
            key = { rowIndex, row ->
                val first = row.firstOrNull()
                resolveIndexedVideoLazyKey(
                    namespace = "video_related_row",
                    index = rowIndex,
                    bvid = first?.bvid.orEmpty(),
                    aid = first?.aid ?: 0L,
                    cid = first?.cid ?: 0L
                )
            },
            contentType = { _, _ -> "video_related_row" },
        ) { _, row ->
            if (!relatedContentReady) {
                com.android.purebilibili.feature.video.ui.components.RelatedVideoGridRowSkeleton(
                    cardLayout = relatedVideoCardLayout,
                    cardPresentation = cardPresentation,
                )
            } else CompositionLocalProvider(
                LocalVideoCardSharedElementSourceRoute provides
                    com.android.purebilibili.feature.video.ui.components.resolveRelatedVideoCardHostRoute(
                        LocalVideoCardSharedElementSourceRoute.current, info.bvid,
                    )
            ) {
                RelatedVideoGridRow(
                    videos = row,
                    cardLayout = relatedVideoCardLayout,
                    cardPresentation = cardPresentation,
                    followingMids = followingMids,
                    showUpBadge = showUpBadge,
                    onVideoClick = { video ->
                        val navOptions = buildVideoNavigationOptions(
                            targetCid = video.cid,
                            coverUrl = video.pic
                        )
                        onRelatedVideoClick(video.bvid, navOptions)
                    },
                    onUpClick = onUpClick,
                )
            }
        }
    }
}

// ... VideoCommentTab signature ...
@Composable
internal fun VideoCommentTab(
    listState: LazyListState,
    modifier: Modifier,
    upMid: Long,
    maxTimestampMs: Long?,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    isRepliesRefreshing: Boolean,
    repliesError: String?,
    isRepliesEnd: Boolean,
    voteCard: ReplyVoteCard?,
    onUpClick: (Long) -> Unit,
    onSubReplyClick: (ReplyItem, Long) -> Unit,
    onCommentReplyClick: (ReplyItem) -> Unit,
    onLoadMoreReplies: () -> Unit,
    onRefreshReplies: () -> Unit,
    onImagePreview: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit,
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
    onCommentHate: (Long) -> Unit,
    likedComments: Set<Long>,
    hatedComments: Set<Long>,
    onCommentUrlClick: (String) -> Unit,
    onReportComment: (Long, Int) -> Unit,
    onToggleTopComment: (ReplyItem) -> Unit,
    onCheckCommentFraud: (ReplyItem) -> Unit,
    showIdentityDecorations: Boolean,
    lightweightCommentRendering: Boolean,
    sortMode: CommentSortMode = CommentSortMode.HOT,
    onSortModeChange: (CommentSortMode) -> Unit = {},
    showNativeSortHeader: Boolean = false,
    showSortControlInHeader: Boolean = false,
    showHeader: Boolean = true,
    /** 详情页评论栏与切换后的浮动栏使用同一紧凑字阶；其他宿主保留原生默认。 */
    compactSortHeader: Boolean = false,
    floatingHeaderContentPadding: Dp = 0.dp,
    onSearchClick: (() -> Unit)? = null,
    pullToRefreshEnabled: Boolean = true,
    pinnedReplyIds: Set<Long> = emptySet(),
    paginationPage: Int = 1,
    floatingHeaderContentPaddingPx: (() -> Float)? = null,
    pageControl: com.android.purebilibili.data.model.response.ReplyPageControl? = null,
) {
    val commentAppearance = rememberVideoCommentAppearance()
    val avatarPushEnabled = com.android.purebilibili.feature.video.ui.components.rememberCommentAvatarPushEnabled()
    // 首屏骨架按视口高度铺够槽位：少铺会让列表在数据到达时被撑长、整体跳一下。
    val commentSkeletonSlotCount = resolveVideoCommentSkeletonSlotCount(
        LocalConfiguration.current.screenHeightDp
    )
    val shouldLoadMore by remember(
        listState,
        replies.size,
        replyCount,
        isRepliesLoading,
        isRepliesRefreshing,
        repliesError,
        isRepliesEnd
    ) {
        derivedStateOf {
            shouldLoadMoreVideoComments(
                lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                isLoading = isRepliesLoading || isRepliesRefreshing || repliesError != null,
                // 置顶/热评会额外插入列表，已渲染条数不能推断服务端分页已结束。
                isEnd = isRepliesEnd
            )
        }
    }
    LaunchedEffect(shouldLoadMore, isRepliesRefreshing, repliesError, paginationPage) {
        if (shouldLoadMore && !isRepliesRefreshing && repliesError == null) {
            onLoadMoreReplies()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (showHeader && showSortControlInHeader) {
            if (showNativeSortHeader) {
                CommentSortHeader(
                    count = replyCount,
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    onSearchClick = onSearchClick,
                    compactTypography = compactSortHeader,
                )
            } else {
                CommentListHeader(
                    count = replyCount,
                    title = "${sortMode.label}评论",
                    compactTypography = compactSortHeader,
                )
            }
        } else if (showHeader) {
            CommentListHeader(
                count = replyCount,
                title = "${sortMode.label}评论",
                compactTypography = compactSortHeader,
            )
        }
        val contentDensity = LocalDensity.current
        val commentContentPadding = remember(
            contentPadding, floatingHeaderContentPadding, floatingHeaderContentPaddingPx, contentDensity,
        ) {
            if (floatingHeaderContentPaddingPx != null) {
                videoContentAdditionalTopPadding(contentPadding, floatingHeaderContentPaddingPx, contentDensity)
            } else {
                videoContentAdditionalTopPadding(contentPadding, floatingHeaderContentPadding)
            }
        }
        VideoCommentRefreshContainer(
            isRefreshing = isRepliesRefreshing,
            onRefresh = onRefreshReplies,
            enabled = pullToRefreshEnabled,
            contentPadding = commentContentPadding,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = commentContentPadding
            ) {
            commentSectionNotice(pageControl)
            if (repliesError != null) {
                item(key = "video_comment_refresh_error", contentType = "video_comment_error") {
                    com.android.purebilibili.feature.video.ui.components.VideoCommentLoadError(
                        message = repliesError,
                        onRetry = { if (replies.isNotEmpty() && !isRepliesEnd) onLoadMoreReplies() else onRefreshReplies() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (replies.isEmpty() && voteCard == null) Modifier.fillParentMaxHeight(0.7f) else Modifier),
                    )
                }
            }
            voteCard?.let { card ->
                item(key = "inline_vote_${card.voteId}", contentType = "video_comment_vote") {
                    VideoCommentVoteCard(
                        card = card,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
            if (isRepliesLoading && !isRepliesRefreshing && replies.isEmpty()) {
                // 骨架拆成多个独立 lazy item（而不是 1 个 item 里塞 5 行）：数据到达时是
                // 一个槽位换一个槽位，列表锚点不失效，不会整体跳一下。
                videoCommentSkeletonItems(
                    slotCount = commentSkeletonSlotCount,
                    keyPrefix = "video_comment_skeleton_",
                )
            } else if (replies.isEmpty() && voteCard == null && repliesError == null) {
                item(key = "video_comment_empty", contentType = "video_comment_empty") {
                    val emptyMessage = pageControl?.emptyMessage()?.takeIf { it.isNotBlank() }
                    if (replyCount > 0 && emptyMessage == null) {
                        com.android.purebilibili.feature.video.ui.components.VideoCommentLoadError(
                            message = "评论暂时无法加载，请重试或切换「最新」",
                            onRetry = onRefreshReplies,
                            modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.7f),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            AppText(
                                text = emptyMessage ?: "暂无评论",
                                color = commentAppearance.secondaryTextColor
                            )
                        }
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
                        // 小间距防止裁剪；animateItem 放在最前，保证位移/淡入淡出作用于整个槽位。
                        modifier = videoCommentItemModifier().padding(bottom = 1.dp)
                    ) {
                        ReplyItemView(
                            showUpFlag = showUpFlag,
                            item = reply,
                            isPinned = reply.rpid in pinnedReplyIds,
                            upMid = upMid,
                            emoteMap = emoteMap,
                            avatarListState = listState.takeIf { avatarPushEnabled },
                            lightweightMode = lightweightCommentRendering,
                            showIdentityDecorations = showIdentityDecorations,
                            onClick = {},
                            onSubClick = onSubReplyClick,
                            onTimestampClick = onTimestampClick,
                            maxTimestampMs = maxTimestampMs,
                            onImagePreview = { images, index, rect, textContent ->
                                onImagePreview(images, index, rect, textContent)
                            },
                            // [新增] 点赞事件
                            onLikeClick = { onCommentLike(reply.rpid) },
                            onHateClick = { onCommentHate(reply.rpid) },
                            onReplyClick = { onCommentReplyClick(reply) },
                            onReportClick = { reason -> onReportComment(reply.rpid, reason) },
                            canToggleTop = shouldShowReplyTopAction(
                                currentMid = currentMid,
                                upMid = upMid,
                                item = reply
                            ),
                            onToggleTopClick = { onToggleTopComment(reply) },
                            // [修复] 正确传递点赞状态 (API数据 或 本地乐观更新)
                            isLiked = reply.action == 1 || reply.rpid in likedComments,
                            isHated = reply.action == 2 || reply.rpid in hatedComments,
                            // [新增] 仅当评论 mid 与当前登录用户 mid 一致时显示删除按钮
                            onDeleteClick = if (currentMid > 0 && reply.mid == currentMid) {
                                { onDissolveStart(reply.rpid) }
                            } else null,
                            onCheckFraudClick = if (currentMid > 0 && reply.mid == currentMid) {
                                { onCheckCommentFraud(reply) }
                            } else null,
                            // [新增] URL 点击跳转
                            onUrlClick = onCommentUrlClick,
                            // [新增] 头像点击
                            onAvatarClick = { mid -> mid.toLongOrNull()?.let { onUpClick(it) } }
                        )
                    }
                }

                // 加载更多
                item(key = "video_comment_footer", contentType = "video_comment_footer") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isRepliesRefreshing -> Unit
                            isRepliesLoading -> AdaptiveLoadingIndicator()
                            isRepliesEnd -> {
                                AppText("没有更多了", color = commentAppearance.secondaryTextColor, style = MaterialTheme.typography.bodySmall)
                            }
                            // 当 shouldLoadMore 为 true 时才显示加载指示器
                            shouldLoadMore -> AdaptiveLoadingIndicator()
                        }
                    }
                }
            }
            }

        }
    }
}

@Composable
internal fun LandscapeCommentPanel(
    info: ViewInfo,
    listState: LazyListState,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    isRepliesRefreshing: Boolean,
    repliesError: String?,
    isRepliesEnd: Boolean,
    voteCard: ReplyVoteCard?,
    videoTags: List<VideoTag>,
    sortMode: CommentSortMode,
    currentMid: Long,
    showUpFlag: Boolean,
    showIdentityDecorations: Boolean,
    dissolvingIds: Set<Long>,
    likedComments: Set<Long>,
    hatedComments: Set<Long>,
    onSortModeChange: (CommentSortMode) -> Unit,
    onUpClick: (Long) -> Unit,
    onSubReplyClick: (ReplyItem, Long) -> Unit,
    onCommentReplyClick: (ReplyItem) -> Unit,
    onLoadMoreReplies: () -> Unit,
    onRefreshReplies: () -> Unit,
    onDeleteComment: (Long) -> Unit,
    onDissolveStart: (Long) -> Unit,
    onCommentLike: (Long) -> Unit,
    onCommentHate: (Long) -> Unit,
    onCommentUrlClick: (String) -> Unit,
    onReportComment: (Long, Int) -> Unit,
    onToggleTopComment: (ReplyItem) -> Unit,
    onCheckCommentFraud: (ReplyItem) -> Unit = {},
    onTimestampClick: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
    onSwitchSide: () -> Unit,
    isOnLeft: Boolean,
    drawerWidth: Dp,
    closeProgress: androidx.compose.runtime.MutableFloatState,
    threadContent: (@Composable ((List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
    paginationPage: Int = 1,
    pageControl: com.android.purebilibili.data.model.response.ReplyPageControl? = null,
) {
    var previewImages by remember { mutableStateOf(emptyList<String>()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    var showImagePreview by remember { mutableStateOf(false) }
    var showCommentSearchSheet by remember { mutableStateOf(false) }
    val commentAppearance = rememberVideoCommentAppearance()

    com.android.purebilibili.core.ui.adaptive.AppHingeSafeSidePanel(
        isStart = isOnLeft,
        modifier = modifier.fillMaxSize(),
    ) {
        LandscapeSidePanel(
            visible = true,
            closeProgress = closeProgress,
            edge = if (isOnLeft) LandscapeSidePanelEdge.Start else LandscapeSidePanelEdge.End,
            width = drawerWidth.coerceAtMost(maxWidth),
            onDismiss = onDismiss,
            modifier = Modifier
                .align(if (isOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight(),
        ) { requestDismiss ->
            AppSurface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            "评论 $replyCount",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppTextButton(
                            onClick = onSwitchSide,
                            modifier = Modifier.widthIn(min = 76.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            AppText(
                                text = if (isOnLeft) "移至右侧" else "移至左侧",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                        AppTextButton(
                            onClick = requestDismiss,
                            modifier = Modifier.widthIn(min = 56.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            AppText(
                                text = "关闭",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                    // 操作组跟随面板靠向屏幕外侧；玻璃与原生控件保留各自尺寸和触控范围。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = if (isOnLeft) Arrangement.Start else Arrangement.End,
                    ) {
                        CommentSortFilterBar(
                            sortMode = sortMode,
                            onSortModeChange = { mode ->
                                if (mode != sortMode) {
                                    // 新排序从首条展示；取消旧滚动锚点对新评论列表的保位。
                                    listState.requestScrollToItem(0)
                                    onSortModeChange(mode)
                                }
                            },
                            onSearchClick = { showCommentSearchSheet = true },
                        )
                    }
                    AppHorizontalDivider(color = commentAppearance.secondaryTextColor.copy(alpha = 0.18f))
                    if (threadContent != null) {
                        threadContent { images, index, rect, textContent ->
                            previewImages = images
                            previewInitialIndex = index
                            previewSourceRect = rect
                            previewTextContent = textContent
                            showImagePreview = true
                        }
                    } else {
                        VideoCommentTab(
                            listState = listState,
                            modifier = Modifier.weight(1f),
                            upMid = info.owner.mid,
                            maxTimestampMs = info.pages.firstOrNull { it.cid == info.cid }?.duration?.times(1000L)
                                ?: info.pages.firstOrNull()?.duration?.times(1000L),
                            replies = replies,
                            pageControl = pageControl,
                            replyCount = replyCount,
                            emoteMap = emoteMap,
                            isRepliesLoading = isRepliesLoading,
                            isRepliesRefreshing = isRepliesRefreshing,
                            repliesError = repliesError,
                            isRepliesEnd = isRepliesEnd,
                            paginationPage = paginationPage,
                            voteCard = voteCard,
                            onUpClick = onUpClick,
                            onSubReplyClick = onSubReplyClick,
                            onCommentReplyClick = onCommentReplyClick,
                            onLoadMoreReplies = onLoadMoreReplies,
                            onRefreshReplies = onRefreshReplies,
                            onImagePreview = { images, index, rect, textContent ->
                                previewImages = images
                                previewInitialIndex = index
                                previewSourceRect = rect
                                previewTextContent = textContent
                                showImagePreview = true
                            },
                            onTimestampClick = onTimestampClick,
                            contentPadding = PaddingValues(bottom = 16.dp),
                            currentMid = currentMid,
                            showUpFlag = showUpFlag,
                            dissolvingIds = dissolvingIds,
                            onDeleteComment = onDeleteComment,
                            onDissolveStart = onDissolveStart,
                            onCommentLike = onCommentLike,
                            onCommentHate = onCommentHate,
                            likedComments = likedComments,
                            hatedComments = hatedComments,
                            sortMode = sortMode,
                            onSortModeChange = onSortModeChange,
                            onCommentUrlClick = onCommentUrlClick,
                            onReportComment = onReportComment,
                            onToggleTopComment = onToggleTopComment,
                            onCheckCommentFraud = onCheckCommentFraud,
                            showIdentityDecorations = showIdentityDecorations,
                            lightweightCommentRendering = false,
                            compactSortHeader = true,
                        )
                    }
                }
            }
        }
    }
    if (showImagePreview && previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            sourceRect = previewSourceRect?.rect,
            sourceRects = previewSourceRect?.galleryRects.orEmpty(),
            sourceCornerRadiusDp = previewSourceRect?.cornerRadiusDp
                ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
            textContent = previewTextContent,
            onDismiss = { showImagePreview = false },
        )
    }
    if (showCommentSearchSheet) {
        CommentSearchSheet(
            replies = replies,
            upMid = info.owner.mid,
            onCommentClick = { reply ->
                onCommentReplyClick(reply)
            },
            onSubReplyClick = { rootReply ->
                onSubReplyClick(rootReply, 0L)
            },
            onDismiss = { showCommentSearchSheet = false },
        )
    }
}

@Composable
private fun VideoHeaderContent(
    info: ViewInfo,
    videoTags: List<VideoTag>,
    currentPageIndex: Int,
    onPageSelect: (Int) -> Unit,
    isFollowing: Boolean,
    followStatusKnown: Boolean = false,
    isFavorited: Boolean,
    isLiked: Boolean,
    likeStatusKnown: Boolean = false,
    isDisliked: Boolean = false,
    coinCount: Int,
    downloadProgress: Float,
    isInWatchLater: Boolean,
    isLoggedIn: Boolean = false,
    isVideoPlaying: Boolean = false,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit = {},
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onCommentClick: () -> Unit,
    onUpClick: (Long) -> Unit,
    onOpenCollectionSheet: () -> Unit,
    onOpenPartsPanel: () -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onGloballyPositioned: (Float) -> Unit,
    transitionEnabled: Boolean = false,  // 🔗 共享元素过渡开关
    isQuickReturnLimitedForSharedElements: Boolean = false,
    sourceRouteForSharedElement: String? = null,
    ownerFollowerCount: Int? = null,
    ownerVideoCount: Int? = null,
    onFavoriteLongClick: () -> Unit = {},
    aiSummary: AiSummaryData? = null,
    aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState? = null,
    onShowAiSummarySheet: () -> Unit = {},
    onShowNoteListSheet: () -> Unit = {},
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    relatedVideos: List<RelatedVideo> = emptyList(),
    onTimestampClick: ((Long) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onDescriptionUrlClick: ((String) -> Unit)? = null,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> },
    onSearchKeywordClick: (String) -> Unit = {},
    sponsorVideoLabel: String = "",
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    showInteractionActions: Boolean = true,
    animateVideoDetailLayout: Boolean = true
) {
    val context = LocalContext.current
    val videoAiSummaryEntryEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoAiSummaryEntryEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val videoNoteEnabled by com.android.purebilibili.core.store.SettingsManager
        .getVideoNoteEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val uiStyle = LocalAppUiStyle.current
    val sectionSpacing = 4.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface) // 🎨 [修复] 与 TabBar 统一使用容器背景色，消除割裂感
            .onGloballyPositioned { coordinates ->
                onGloballyPositioned(coordinates.size.height.toFloat())
            },
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        UpInfoSection(
            info = info,
            onOpenLiveRoom = onDescriptionUrlClick,
            isFollowing = isFollowing,
            followStatusKnown = followStatusKnown,
            onFollowClick = onFollowClick,
            onUpClick = onUpClick,
            showOwnerAvatar = true,
            followerCount = ownerFollowerCount,
            videoCount = ownerVideoCount,
            horizontalPadding = if (uiStyle == AppUiStyle.MATERIAL3) 16.dp else 12.dp,
            transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
            isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
            sourceRouteForSharedElement = sourceRouteForSharedElement
        )

        VideoTitleWithDesc(
            info = info,
            videoTags = videoTags,
            sponsorLabel = sponsorVideoLabel,
            transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
            isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
            sourceRouteForSharedElement = sourceRouteForSharedElement,
            trailingStatsContent = {
                // PiliPlus 式：信息行右端的 AI 总结 / 视频笔记小图标，内容在底部抽屉展示
                VideoSupplementStatsActions(
                    showAiSummary = videoAiSummaryEntryEnabled,
                    showNote = videoNoteEnabled,
                    onAiSummaryClick = onShowAiSummarySheet,
                    onNoteClick = onShowNoteListSheet,
                )
            },
            bgmList = resolveDisplayBgmList(
                bgmInfo = bgmInfo,
                bgmInfoList = bgmInfoList
            ),
            onlineCount = onlineCount,
            showOnlineCount = showOnlineCount,
            onBgmClick = onBgmClick,
            onDescriptionUrlClick = onDescriptionUrlClick,
            onRelatedVideoClick = onRelatedVideoClick,
            animateLayout = animateVideoDetailLayout,
            onTagClick = onSearchKeywordClick
        )

        if (showInteractionActions) {
            ActionButtonsRow(
                info = info,
                isFavorited = isFavorited,
                isLiked = isLiked,
                likeStatusKnown = likeStatusKnown,
                isDisliked = isDisliked,
                coinCount = coinCount,
                downloadProgress = downloadProgress,
                isInWatchLater = isInWatchLater,
                onFavoriteClick = onFavoriteClick,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick,
                onCoinClick = onCoinClick,
                onTripleClick = onTripleClick,
                onCommentClick = onCommentClick,
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                onFavoriteLongClick = onFavoriteLongClick,
                onShareClick = onShareClick,
                showCommentAction = false,
            )
        }

        info.ugc_season?.let { season ->
            CollectionRow(
                ugcSeason = season,
                currentBvid = info.bvid,
                currentCid = info.cid,
                isPlaying = isVideoPlaying,
                onClick = onOpenCollectionSheet
            )
        }

        if (info.pages.size > 1) {
            PagesSelector(
                pages = info.pages,
                coverUrl = info.pic,
                currentPageIndex = currentPageIndex,
                isPlaying = isVideoPlaying,
                onPageSelect = onPageSelect,
                onOpenExpanded = onOpenPartsPanel,
            )
        }

    }

}

/**
 * Tab 栏组件
 */
@Composable
internal fun VideoContentTabBar(
    tabs: List<String>,
    replyCount: Int,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    sortMode: CommentSortMode,
    onSortModeChange: (CommentSortMode) -> Unit,
    onDanmakuSendClick: () -> Unit,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tabSwipeModifier: Modifier = Modifier,
    isPlayerCollapsed: Boolean = false,
    miuixBackdrop: MiuixBackdrop? = null,
    indicatorPositionProvider: (() -> Float)? = null,
    isScrollInProgressProvider: () -> Boolean = { false },
    isPagerDraggingProvider: (() -> Boolean)? = null,
    pagerTargetPositionProvider: (() -> Float)? = null,
    showDanmakuActions: Boolean = true,
    measurementLabels: List<String>? = null,
    liquidGlassTabsOffsetX: Dp = 0.dp,
    liquidGlassAdaptIndicatorToLabel: Boolean = false,
) {
    val liquidGlassEnabledForTabBar = LocalAppThemeConfig.current.liquidGlassEnabled
    val configuration = LocalConfiguration.current
    val layoutSpec = remember(configuration.screenWidthDp) {
        resolveVideoContentTabBarLayoutSpec(widthDp = configuration.screenWidthDp)
    }
    val danmakuActionLayoutPolicy = remember(configuration.screenWidthDp) {
        resolveVideoContentTabBarDanmakuActionLayoutPolicy(widthDp = configuration.screenWidthDp)
    }
    val liquidChromeSpec = remember(
        liquidGlassEnabledForTabBar,
        LocalAppUiStyle.current,
        miuixBackdrop,
        layoutSpec
    ) {
        resolveVideoContentTabBarLiquidChromeSpec(
            androidNativeLiquidGlassEnabled = liquidGlassEnabledForTabBar,
            hasBackdrop = miuixBackdrop != null,
            layoutSpec = layoutSpec,
        )
    }
    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (liquidChromeSpec.useTransparentTabRowBackground &&
                        (!isPlayerCollapsed || liquidGlassEnabledForTabBar)
                    ) {
                        // 透明 + 上层渐进模糊材质；液态玻璃开启时折叠/隐藏播放器
                        // 后同样成立，采样对象是滚动中的评论内容。
                        Modifier
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    }
                )
                .padding(
                    start = resolveVideoContentTabBarStartPaddingDp(
                        reusesLiquidGlassDock = liquidChromeSpec.reusesLiquidGlassDock,
                        containerHorizontalPaddingDp = layoutSpec.containerHorizontalPaddingDp,
                    ).dp,
                    end = layoutSpec.containerHorizontalPaddingDp.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (liquidChromeSpec.reusesLiquidGlassDock) {
                Arrangement.spacedBy(8.dp)
            } else {
                Arrangement.Start
            }
        ) {
            AppThemeAdaptiveTabRow(
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                centerContent = false,
                options = tabs.mapIndexed { index, label ->
                    AppSegmentOption(index, label, measurementLabel = measurementLabels?.getOrNull(index))
                },
                // Counts have a metadata reservation before comment loading starts. Data arrival
                // updates text only, rather than starting a second width spring during entry.
                liquidGlassAnimateLabelWidthChanges = false,
                // Callers opt into content-sized glass indicators; native geometry is unchanged.
                liquidGlassLabelHorizontalPadding = 10.dp,
                liquidGlassAdaptIndicatorToLabel = liquidGlassAdaptIndicatorToLabel,
                selectedValue = selectedTabIndex,
                onSelectionChange = onTabSelected,
                // 仅移动页面显式指定的玻璃 dock；原生布局及右侧弹幕操作保持原位。
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (liquidChromeSpec.reusesLiquidGlassDock) {
                            Modifier.offset(x = liquidGlassTabsOffsetX)
                        } else Modifier
                    ),
                scrollable = true,
                compactMiuixWhenTwoOptions = false,
                height = liquidChromeSpec.segmentedControlHeightDp.dp,
                indicatorHeight = liquidChromeSpec.segmentedControlIndicatorHeightDp.dp,
                labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
                dragSelectionEnabled = true,
                tapPressRefractionEnabled = true,
                miuixBackdrop = miuixBackdrop,
                indicatorPositionProvider = indicatorPositionProvider,
                isScrollInProgressProvider = isScrollInProgressProvider,
                isPagerDraggingProvider = isPagerDraggingProvider,
                pagerTargetPositionProvider = pagerTargetPositionProvider,
            )

            if (showDanmakuActions && shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = shouldShowDanmakuSendInput(isPlayerCollapsed = isPlayerCollapsed),
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    ) {
                        com.android.purebilibili.core.ui.components.AppIconButton(
                            onClick = onDanmakuSendClick,
                            modifier = Modifier
                                .width(40.dp)
                                .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget),
                        ) {
                            com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSendIcon()
                        }
                    }

                    NativeDanmakuToggleButton(
                        enabled = danmakuEnabled,
                        onToggle = onDanmakuToggle,
                        activeTint = AppContentColors.selectionAccent(
                            com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        ),
                        inactiveTint = AppContentColors.secondaryText(),
                        modifier = Modifier
                            .padding(end = danmakuActionLayoutPolicy.toggleTrailingPaddingDp.dp)
                            .width(40.dp)
                            .height(AppChromeSizeTokens.MinimumTouchTarget),
                        iconSize = danmakuActionLayoutPolicy.toggleIconSizeDp.dp,
                    )
                }
            }
        }
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
fun VideoTagsRow(
    tags: List<VideoTag>,
    onTagClick: (String) -> Unit = {}
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.take(10).forEach { tag ->
            VideoTagChip(
                tagName = tag.tag_name,
                onClick = onTagClick
            )
        }
    }
}

/**
 * 视频标签芯片
 */
@Composable
fun VideoTagChip(
    tagName: String,
    onClick: (String) -> Unit = {}
) {
    AppSurface(
        onClick = { onClick(tagName) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        shape = AppShapes.container(ContainerLevel.Dialog)
    ) {
        AppText(
            text = tagName,
            style = MaterialTheme.typography.labelMedium,
            color = AppContentColors.secondaryText(),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .copyOnLongPress(tagName, "标签")
        )
    }
}
