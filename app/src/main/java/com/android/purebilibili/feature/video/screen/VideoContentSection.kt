// 文件路径: feature/video/screen/VideoContentSection.kt
package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import com.android.purebilibili.feature.home.components.biliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.feature.common.resolveIndexedVideoLazyKey
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.resolveDisplayBgmList
import com.android.purebilibili.feature.video.ui.section.shouldShowAiSummaryEntry
import com.android.purebilibili.feature.video.ui.section.resolveVideoDetailMotionBudget
import com.android.purebilibili.feature.video.ui.section.shouldAnimateVideoDetailLayout
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import com.android.purebilibili.feature.video.ui.components.RelatedVideoGridRow
import com.android.purebilibili.feature.video.ui.components.chunkRelatedVideosForHomeStyleGrid
import com.android.purebilibili.feature.video.ui.components.filterRelatedVideosByHiddenBvids
import com.android.purebilibili.feature.video.ui.components.rememberRelatedVideoCardLayout
import com.android.purebilibili.feature.video.ui.components.CollectionRow
import com.android.purebilibili.feature.video.ui.components.CollectionSheet
import com.android.purebilibili.feature.video.ui.components.PagesSelector
import com.android.purebilibili.feature.video.ui.components.CommentListHeader
import com.android.purebilibili.feature.video.ui.components.CommentSortHeader
import com.android.purebilibili.feature.video.ui.components.CommentSortFilterBar
import com.android.purebilibili.feature.video.ui.components.resolveCommentSortDockViewportOverflowDp
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.rememberVideoCommentAppearance
import com.android.purebilibili.feature.video.ui.components.resolveReplyItemContentType
import com.android.purebilibili.feature.video.ui.components.shouldShowReplyTopAction
import com.android.purebilibili.feature.video.ui.components.shouldShowVideoCommentBackToTop
import com.android.purebilibili.feature.video.ui.components.LandscapeSidePanel
import com.android.purebilibili.feature.video.ui.components.LandscapeSidePanelEdge
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.ui.section.AiSummaryCard
import com.android.purebilibili.feature.video.ui.section.AiSummaryPromptCard
import com.android.purebilibili.feature.video.ui.section.VideoNoteCard
import com.android.purebilibili.feature.video.ui.section.VideoNoteDeleteConfirmDialog
import com.android.purebilibili.feature.video.ui.section.VideoNoteEditorSheet
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.note.buildVideoNoteShareText
import com.android.purebilibili.feature.video.note.shouldShowVideoNoteCard
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
): Int = if (reusesLiquidGlassDock) 0 else containerHorizontalPaddingDp

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
): VideoContentTabSwitchAnimationSpec {
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

internal fun shouldEnableVideoContentTabBarCollapse(
    settingEnabled: Boolean,
    selectedTabIndex: Int,
    isPagerScrollInProgress: Boolean,
    commentPageIndex: Int = 1,
): Boolean = false

/**
 * 跟手折叠进度 0 = 全展开，1 = 全收起。
 * 由 [collapsePx] / [maxCollapsePx] 得到；列表已离开顶部时钳到 1，保证浏览评论时 chrome 收净。
 */
internal fun resolveVideoContentTabBarCollapseProgress(
    collapsePx: Float,
    maxCollapsePx: Float,
    selectedTabIndex: Int,
    listAtTop: Boolean,
    commentPageIndex: Int = 1,
): Float {
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
): Float {
    if (!enabled || maxCollapsePx <= 0f) return 0f
    if (!listAtTop) return maxCollapsePx
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
)

internal class VideoContentEngagementState(
    val isLoggedIn: Boolean,
    val isFollowing: Boolean,
    val isFavorited: Boolean,
    val isLiked: Boolean,
    val coinCount: Int,
    val currentPageIndex: Int,
    val downloadProgress: Float,
    val isInWatchLater: Boolean,
)

internal class VideoContentCommentState(
    val isRepliesLoading: Boolean,
    val isRepliesEnd: Boolean,
    val sortMode: CommentSortMode,
    val currentMid: Long,
    val showUpFlag: Boolean,
    val showIdentityDecorations: Boolean,
    val dissolvingIds: Set<Long>,
    val likedComments: Set<Long>,
    val hatedComments: Set<Long>,
)

internal class VideoContentNoteState(
    val aiSummary: AiSummaryData?,
    val aiSummaryPrompt: com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState?,
    val videoNoteState: VideoNoteUiState,
)

internal class VideoContentPresentationState(
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
)

internal class VideoContentPrimaryActions(
    val onFollowClick: () -> Unit,
    val onFavoriteClick: () -> Unit,
    val onLikeClick: () -> Unit,
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
    val onDeleteComment: (Long) -> Unit,
    val onDissolveStart: (Long) -> Unit,
    val onCommentLike: (Long) -> Unit,
    val onCommentHate: (Long) -> Unit,
    val onCommentUrlClick: (String) -> Unit,
    val onDescriptionUrlClick: ((String) -> Unit)?,
    val onSearchKeywordClick: (String) -> Unit,
    val onReportComment: (Long, Int) -> Unit,
    val onToggleTopComment: (ReplyItem) -> Unit,
)

internal class VideoContentNoteActions(
    val onRetryAiSummary: () -> Unit,
    val onCreateNoteDraftFromAiSummary: () -> Unit,
    val onOpenVideoNoteEditor: () -> Unit,
    val onCloseVideoNoteEditor: () -> Unit,
    val onVideoNoteDocumentChange: (VideoNoteEditorDocument) -> Unit,
    val onInsertVideoNoteTimestamp: () -> Unit,
    val onVideoNoteTimestampClick: (Long) -> Unit,
    val onSaveVideoNote: (VideoNoteEditorDocument) -> Unit,
    val onDeleteVideoNote: () -> Unit,
    val onRetryVideoNote: () -> Unit,
    val onPublicVideoNoteClick: (Long, String) -> Unit,
)

internal class VideoContentUiActions(
    val onSelectedTabChange: (Int) -> Unit,
    val onIntroScrollThresholdChange: (Boolean) -> Unit,
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
) {
    val info = data.info
    val introListState = data.introListState
    val commentListState = data.commentListState
    val pagerState = data.pagerState
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
    val isFavorited = engagementState.isFavorited
    val isLiked = engagementState.isLiked
    val coinCount = engagementState.coinCount
    val currentPageIndex = engagementState.currentPageIndex
    val downloadProgress = engagementState.downloadProgress
    val isInWatchLater = engagementState.isInWatchLater
    val isRepliesLoading = commentState.isRepliesLoading
    val isRepliesEnd = commentState.isRepliesEnd
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
    val onDeleteComment = commentActions.onDeleteComment
    val onDissolveStart = commentActions.onDissolveStart
    val onCommentLike = commentActions.onCommentLike
    val onCommentHate = commentActions.onCommentHate
    val onCommentUrlClick = commentActions.onCommentUrlClick
    val onDescriptionUrlClick = commentActions.onDescriptionUrlClick
    val onSearchKeywordClick = commentActions.onSearchKeywordClick
    val onReportComment = commentActions.onReportComment
    val onToggleTopComment = commentActions.onToggleTopComment
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
    val onPublicVideoNoteClick = noteActions.onPublicVideoNoteClick
    val onSelectedTabChange = uiActions.onSelectedTabChange
    val onIntroScrollThresholdChange = uiActions.onIntroScrollThresholdChange
    val onCommentScrollStateChange = uiActions.onCommentScrollStateChange
    val context = LocalContext.current
    val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
    val progressiveCommentHeaderEnabled = LocalAppThemeConfig.current.progressiveTopBlurEnabled
    val immersiveVideoContentChromeEnabled = progressiveCommentHeaderEnabled
    val tabs = listOf("简介", "评论")
    val scope = rememberCoroutineScope()
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
    val isIntroListScrolling by remember {
        derivedStateOf { introListState.isScrollInProgress }
    }
    val isCommentListScrolling by remember {
        derivedStateOf { commentListState.isScrollInProgress }
    }
    val videoDetailMotionBudget by remember {
        derivedStateOf {
            resolveVideoDetailMotionBudget(
                isTabSwitching = pagerState.isScrollInProgress,
                isContentScrolling = isIntroListScrolling || isCommentListScrolling
            )
        }
    }
    val animateVideoDetailLayout = shouldAnimateVideoDetailLayout(videoDetailMotionBudget)
    val lightweightCommentRendering by remember {
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
    var sourceRect by remember { mutableStateOf<Rect?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    
    // 合集展开状态
    var showCollectionSheet by remember { mutableStateOf(false) }
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
    val tabSwitchAnimationSpec = remember(playerChromeProfile.tabPresentation) {
        resolveVideoContentTabSwitchAnimationSpec(playerChromeProfile.tabPresentation)
    }
    val latestOnSelectedTabChange by rememberUpdatedState(onSelectedTabChange)

    val onTabSelected: (Int) -> Unit = { index ->
        scope.launch {
            if (pagerState.isScrollInProgress) {
                // 横向拖拽已经由 Pager 驱动时，不要再次启动一段从 currentPage
                // 出发的动画；否则 offset 会先归零回弹到左侧，再动画到目标页。
                pagerState.scrollToPage(index)
            } else {
                animatePagerSelection(
                    pagerState = pagerState,
                    targetPage = index,
                    animationSpec = tween(
                        durationMillis = tabSwitchAnimationSpec.durationMs,
                        easing = FastOutSlowInEasing
                    )
                )
            }
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
    LaunchedEffect(introListState) {
        snapshotFlow {
            isVideoDetailIntroScrollPastCollapseThreshold(
                firstVisibleItemIndex = introListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = introListState.firstVisibleItemScrollOffset
            )
        }
            .distinctUntilChanged()
            .collect(onIntroScrollThresholdChange)
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
    var tabBarMaxHeightPx by remember { mutableFloatStateOf(0f) }
    var tabBarCollapsePx by remember { mutableFloatStateOf(0f) }
    val commentListAtTop by remember {
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
    val backToTopButtonEnabled = rememberBackToTopButtonEnabled()
    // “简介 / 评论”与评论排序承担导航和操作职责，必须始终可见，不能随列表滚动收起。
    val tabBarCollapseEnabled = false
    // 离开评论列表顶部时钳到全收；回到简介 Tab 时复位展开。
    LaunchedEffect(tabBarCollapseEnabled, commentListAtTop, tabBarMaxHeightPx) {
        tabBarCollapsePx = resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
            collapsePx = tabBarCollapsePx,
            maxCollapsePx = tabBarMaxHeightPx,
            listAtTop = commentListAtTop,
            enabled = tabBarCollapseEnabled,
        )
    }
    val tabBarCollapseConnection = remember(
        tabBarCollapseEnabled,
        commentListAtTop,
        tabBarMaxHeightPx,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val update = reduceVideoContentTabBarCollapseOnPreScroll(
                    collapsePx = tabBarCollapsePx,
                    maxCollapsePx = tabBarMaxHeightPx,
                    availableY = available.y,
                    listAtTop = commentListAtTop,
                    enabled = tabBarCollapseEnabled,
                ) ?: return Offset.Zero
                tabBarCollapsePx = update.nextCollapsePx
                return Offset(0f, update.consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val update = reduceVideoContentTabBarCollapseOnPostScroll(
                    collapsePx = tabBarCollapsePx,
                    maxCollapsePx = tabBarMaxHeightPx,
                    availableY = available.y,
                    listAtTop = commentListAtTop,
                    enabled = tabBarCollapseEnabled,
                ) ?: return Offset.Zero
                tabBarCollapsePx = update.nextCollapsePx
                return Offset(0f, update.consumedY)
            }
        }
    }
    val tabBarCollapseProgress = resolveVideoContentTabBarCollapseProgress(
        collapsePx = tabBarCollapsePx,
        maxCollapsePx = tabBarMaxHeightPx,
        selectedTabIndex = pagerState.currentPage,
        listAtTop = commentListAtTop,
    )
    val tabBarVisibleHeightDp = with(density) {
        (tabBarMaxHeightPx - tabBarCollapsePx).coerceAtLeast(0f).toDp()
    }
    val commentSortDockLiftDp = remember {
        resolveCommentSortDockViewportOverflowDp(
            containerHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp,
            indicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp,
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
                beyondViewportPageCount = resolveVideoDetailBeyondViewportPageCount(
                    isVideoPlaying = isVideoPlaying,
                    selectedTabIndex = pagerState.currentPage
                ),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (immersiveVideoContentChromeEnabled) {
                            Modifier
                        } else {
                            Modifier.padding(top = tabBarVisibleHeightDp)
                        }
                    )
                    .verticalPriorityHorizontalPagerSwipe(
                        state = pagerState,
                        enabled = shouldEnableVideoContentHorizontalPagerSwipe(
                            currentPage = pagerState.currentPage,
                            commentPageIndex = 1,
                            isPagerScrollInProgress = pagerState.isScrollInProgress,
                        ),
                    )
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
                        isLoggedIn = isLoggedIn,
                        isVideoPlaying = isVideoPlaying,
                        onFollowClick = onFollowClick,
                        onFavoriteClick = onFavoriteClick,
                        onLikeClick = onLikeClick,
                        onCoinClick = onCoinClick,
                        onTripleClick = onTripleClick,
                        onCommentClick = { onTabSelected(1) },
                        onPageSelect = onPageSelect,
                        onUpClick = onUpClick,
                        onRelatedVideoClick = onRelatedVideoClick,
                        onOpenCollectionSheet = { showCollectionSheet = true },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onShareClick = onShareClick,
                        contentPadding = PaddingValues(
                            top = if (immersiveVideoContentChromeEnabled) tabBarVisibleHeightDp else 0.dp,
                            bottom = bottomContentPadding,
                        ),
                        transitionEnabled = transitionEnabled,
                        isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
                        sourceRouteForSharedElement = sourceRouteForSharedElement,
                        ownerFollowerCount = ownerFollowerCount,
                        ownerVideoCount = ownerVideoCount,
                        showUpBadge = showUpBadge,
                        onFavoriteLongClick = onFavoriteLongClick,
                        aiSummary = aiSummary,
                        aiSummaryPrompt = aiSummaryPrompt,
                        onRetryAiSummary = onRetryAiSummary,
                        onCreateNoteDraftFromAiSummary = onCreateNoteDraftFromAiSummary,
                        videoNoteState = videoNoteState,
                        onOpenVideoNoteEditor = onOpenVideoNoteEditor,
                        onRetryVideoNote = onRetryVideoNote,
                        onDeleteVideoNoteClick = { confirmDeleteNote = true },
                        onShareVideoNote = { document -> onShareVideoNote(document, false) },
                        onPublicVideoNoteClick = onPublicVideoNoteClick,
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
                        onUpClick = onUpClick,
                        onSubReplyClick = onSubReplyClick,
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
                        contentPadding = PaddingValues(
                            top = if (immersiveVideoContentChromeEnabled) tabBarVisibleHeightDp else 0.dp,
                            bottom = bottomContentPadding,
                        ),
                        currentMid = currentMid,
                        dissolvingIds = dissolvingIds,
                        onDeleteComment = onDeleteComment,
                        onDissolveStart = onDissolveStart,
                        onCommentLike = onCommentLike,
                        onCommentHate = onCommentHate,
                        likedComments = likedComments,
                        hatedComments = hatedComments,
                        onCommentUrlClick = onCommentUrlClick,
                        onReportComment = onReportComment,
                        onToggleTopComment = onToggleTopComment,
                        showIdentityDecorations = showIdentityDecorations,
                        lightweightCommentRendering = lightweightCommentRendering,
                        sortMode = sortMode,
                        onSortModeChange = onSortModeChange,
                        showNativeSortHeader = !liquidGlassEnabled,
                        showSortControlInHeader = true,
                        showHeader = !immersiveVideoContentChromeEnabled,
                        floatingHeaderContentPadding = if (immersiveVideoContentChromeEnabled) 46.dp else 0.dp,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tabBarMaxHeightPx <= 0f) {
                        // 首帧先按内容测量真实高度，再进入跟手折叠。
                        Modifier.wrapContentHeight()
                    } else {
                        Modifier
                            .height(tabBarVisibleHeightDp)
                            .graphicsLayer {
                                clip = tabBarCollapseProgress > 0.001f
                            }
                    }
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            if (immersiveVideoContentChromeEnabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .biliPaiProgressiveTopBlur(
                            backdrop = videoContentMiuixBackdrop,
                            enabled = true,
                            surfaceColor = Color.Transparent,
                        ),
                )
            }
            VideoContentTabBar(
                tabs = tabs,
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
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = tabBarMaxHeightPx > 0f)
                    .onSizeChanged { size ->
                        val measured = size.height.toFloat()
                        if (measured > 0f &&
                            (tabBarMaxHeightPx <= 0f || tabBarCollapsePx <= 0.5f)
                        ) {
                            tabBarMaxHeightPx = measured
                        }
                    }
                    .graphicsLayer {
                        val progress = tabBarCollapseProgress.coerceIn(0f, 1f)
                        alpha = 1f - progress
                        translationY = -tabBarMaxHeightPx * progress * 0.35f
                    },
                isPlayerCollapsed = isPlayerCollapsed,
                miuixBackdrop = videoContentMiuixBackdrop,
                indicatorPositionProvider = {
                    pagerState.currentPage + pagerState.currentPageOffsetFraction
                },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
            )
        }

        AnimatedVisibility(
            visible = pagerState.currentPage == 1 &&
                !pagerState.isScrollInProgress &&
                (liquidGlassEnabled || immersiveVideoContentChromeEnabled) &&
                (!tabBarCollapseEnabled || commentListAtTop),
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 90)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tabBarVisibleHeightDp)
                    .heightIn(min = 46.dp)
                    .graphicsLayer {
                        val progress = tabBarCollapseProgress.coerceIn(0f, 1f)
                        alpha = 1f - progress
                        translationY = -tabBarMaxHeightPx * progress * 0.35f
                    },
            ) {
                if (immersiveVideoContentChromeEnabled) {
                    AnimatedVisibility(
                        visible = commentListAtTop,
                        enter = fadeIn(animationSpec = tween(durationMillis = 120)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 90)),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        CommentListHeader(
                            count = replyCount,
                            title = "${sortMode.label}评论",
                        )
                    }
                }
                CommentSortFilterBar(
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange,
                    // The liquid dock reports its press/drag bloom as layout viewport. Lift that
                    // complete viewport so the resting 40dp shell aligns with the comment header.
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 16.dp)
                        .offset(y = (-commentSortDockLiftDp).dp),
                    miuixBackdrop = if (liquidGlassEnabled) videoContentMiuixBackdrop else null,
                    liquidGlassEffectsEnabled = liquidGlassEnabled,
                )
            }
        }

        AppLiquidGlassBackToTopButton(
            visible = pagerState.currentPage == 1 && backToTopButtonEnabled && showCommentBackToTop,
            onClick = {
                scope.launch {
                    commentListState.animateScrollToItem(0)
                    tabBarCollapsePx = 0f
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


        VideoNoteEditorSheet(
            noteState = videoNoteState,
            onDismiss = onCloseVideoNoteEditor,
            onDocumentChange = onVideoNoteDocumentChange,
            onInsertTimestamp = onInsertVideoNoteTimestamp,
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
    isLoggedIn: Boolean = false,
    isVideoPlaying: Boolean = false,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onCommentClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    onOpenCollectionSheet: () -> Unit,
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
    onRetryAiSummary: () -> Unit = {},
    onCreateNoteDraftFromAiSummary: () -> Unit = {},
    videoNoteState: VideoNoteUiState = VideoNoteUiState(),
    onOpenVideoNoteEditor: () -> Unit = {},
    onRetryVideoNote: () -> Unit = {},
    onDeleteVideoNoteClick: () -> Unit = {},
    onShareVideoNote: (VideoNoteEditorDocument) -> Unit = {},
    onPublicVideoNoteClick: (Long, String) -> Unit = { _, _ -> },
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    onTimestampClick: ((Long) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onlineCount: String = "",
    showOnlineCount: Boolean = true,
    showInteractionActions: Boolean = true,
    animateVideoDetailLayout: Boolean = true,
) {
    val hasPages = info.pages.size > 1
    var hiddenRelatedBvids by remember(info.bvid) { mutableStateOf(emptySet<String>()) }
    val visibleRelatedVideos = remember(relatedVideos, hiddenRelatedBvids) {
        filterRelatedVideosByHiddenBvids(relatedVideos, hiddenRelatedBvids)
    }
    val relatedVideoCardLayout = rememberRelatedVideoCardLayout()
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
                isLoggedIn = isLoggedIn,
                isVideoPlaying = isVideoPlaying,
                onFollowClick = onFollowClick,
                onFavoriteClick = onFavoriteClick,
                onLikeClick = onLikeClick,
                onCoinClick = onCoinClick,
                onTripleClick = onTripleClick,
                onCommentClick = onCommentClick,
                onUpClick = onUpClick,
                onOpenCollectionSheet = onOpenCollectionSheet,
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
                aiSummary = aiSummary,
                aiSummaryPrompt = aiSummaryPrompt,
                onRetryAiSummary = onRetryAiSummary,
                onCreateNoteDraftFromAiSummary = onCreateNoteDraftFromAiSummary,
                videoNoteState = videoNoteState,
                onOpenVideoNoteEditor = onOpenVideoNoteEditor,
                onRetryVideoNote = onRetryVideoNote,
                onDeleteVideoNoteClick = onDeleteVideoNoteClick,
                onShareVideoNote = onShareVideoNote,
                onPublicVideoNoteClick = onPublicVideoNoteClick,
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

        val relatedRows = chunkRelatedVideosForHomeStyleGrid(visibleRelatedVideos)
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
            }
        ) { _, row ->
            CompositionLocalProvider(
                LocalVideoCardSharedElementSourceRoute provides "video/${info.bvid}"
            ) {
                RelatedVideoGridRow(
                    videos = row,
                    cardLayout = relatedVideoCardLayout,
                    followingMids = followingMids,
                    showUpBadge = showUpBadge,
                    onVideoClick = { video ->
                        val navOptions = buildVideoNavigationOptions(
                            targetCid = video.cid,
                            coverUrl = video.pic
                        )
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

// ... VideoCommentTab signature ...
@Composable
internal fun VideoCommentTab(
    listState: LazyListState,
    modifier: Modifier,
    info: ViewInfo,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    isRepliesEnd: Boolean,
    videoTags: List<VideoTag>,
    onUpClick: (Long) -> Unit,
    onSubReplyClick: (ReplyItem, Long) -> Unit,
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
    onCommentHate: (Long) -> Unit,
    likedComments: Set<Long>,
    hatedComments: Set<Long>,
    onCommentUrlClick: (String) -> Unit,
    onReportComment: (Long, Int) -> Unit,
    onToggleTopComment: (ReplyItem) -> Unit,
    showIdentityDecorations: Boolean,
    lightweightCommentRendering: Boolean,
    sortMode: CommentSortMode = CommentSortMode.HOT,
    onSortModeChange: (CommentSortMode) -> Unit = {},
    showNativeSortHeader: Boolean = false,
    showSortControlInHeader: Boolean = false,
    showHeader: Boolean = true,
    floatingHeaderContentPadding: Dp = 0.dp,
) {
    val commentAppearance = rememberVideoCommentAppearance()
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
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
                // 置顶/热评会额外插入列表，已渲染条数不能推断服务端分页已结束。
                isEnd = isRepliesEnd
            )
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
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
                )
            } else {
                CommentListHeader(
                    count = replyCount,
                    title = "${sortMode.label}评论",
                )
            }
        } else if (showHeader) {
            CommentListHeader(
                count = replyCount,
                title = "${sortMode.label}评论",
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding() + floatingHeaderContentPadding,
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                )
            ) {
            if (isRepliesLoading && replies.isEmpty()) {
                item {
                    com.android.purebilibili.core.ui.skeleton.CommentListColumnSkeleton()
                }
            } else if (replies.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        // replyCount 来自详情/游标 all_count：>0 却列表空 = 最热链路空成功，勿误报「暂无」
                        AppText(
                            text = if (replyCount > 0) {
                                "评论暂时无法加载，可切换「最新」或稍后重试"
                            } else {
                                "暂无评论"
                            },
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
                            showIdentityDecorations = showIdentityDecorations,
                            onClick = {},
                            onSubClick = onSubReplyClick,
                            onTimestampClick = onTimestampClick,
                            maxTimestampMs = info.pages.firstOrNull { it.cid == info.cid }?.duration?.times(1000L)
                                ?: info.pages.firstOrNull()?.duration?.times(1000L),
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
                                upMid = info.owner.mid,
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
                            // [新增] URL 点击跳转
                            onUrlClick = onCommentUrlClick,
                            // [新增] 头像点击
                            onAvatarClick = { mid -> mid.toLongOrNull()?.let { onUpClick(it) } }
                        )
                    }
                }

                // 加载更多
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isRepliesLoading -> AdaptiveLoadingIndicator()
                            isRepliesEnd -> {
                                AppText("—— end ——", color = commentAppearance.secondaryTextColor, fontSize = 12.sp)
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
    isRepliesEnd: Boolean,
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
    onDeleteComment: (Long) -> Unit,
    onDissolveStart: (Long) -> Unit,
    onCommentLike: (Long) -> Unit,
    onCommentHate: (Long) -> Unit,
    onCommentUrlClick: (String) -> Unit,
    onReportComment: (Long, Int) -> Unit,
    onToggleTopComment: (ReplyItem) -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
    onSwitchSide: () -> Unit,
    isOnLeft: Boolean,
    drawerWidth: Dp,
    threadContent: (@Composable ((List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var previewImages by remember { mutableStateOf(emptyList<String>()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<Rect?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    var showImagePreview by remember { mutableStateOf(false) }
    val commentAppearance = rememberVideoCommentAppearance()

    LandscapeSidePanel(
        visible = true,
        edge = if (isOnLeft) LandscapeSidePanelEdge.Start else LandscapeSidePanelEdge.End,
        width = drawerWidth,
        onDismiss = onDismiss,
        modifier = modifier,
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
                    AppText("评论 $replyCount", style = MaterialTheme.typography.titleMedium)
                    CommentSortFilterBar(
                        sortMode = sortMode,
                        onSortModeChange = onSortModeChange,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppTextButton(
                        onClick = onSwitchSide,
                        modifier = Modifier.widthIn(min = 76.dp),
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
                    ) {
                        AppText(
                            text = "关闭",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
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
                        info = info,
                        replies = replies,
                        replyCount = replyCount,
                        emoteMap = emoteMap,
                        isRepliesLoading = isRepliesLoading,
                        isRepliesEnd = isRepliesEnd,
                        videoTags = videoTags,
                        onUpClick = onUpClick,
                        onSubReplyClick = onSubReplyClick,
                        onCommentReplyClick = onCommentReplyClick,
                        onLoadMoreReplies = onLoadMoreReplies,
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
                        onCommentUrlClick = onCommentUrlClick,
                        onReportComment = onReportComment,
                        onToggleTopComment = onToggleTopComment,
                        showIdentityDecorations = showIdentityDecorations,
                        lightweightCommentRendering = false,
                    )
                }
            }
        }
    }
    if (showImagePreview && previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            sourceRect = previewSourceRect,
            textContent = previewTextContent,
            onDismiss = { showImagePreview = false },
        )
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
    isLoggedIn: Boolean = false,
    isVideoPlaying: Boolean = false,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onCommentClick: () -> Unit,
    onUpClick: (Long) -> Unit,
    onOpenCollectionSheet: () -> Unit,
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
    onRetryAiSummary: () -> Unit = {},
    onCreateNoteDraftFromAiSummary: () -> Unit = {},
    videoNoteState: VideoNoteUiState = VideoNoteUiState(),
    onOpenVideoNoteEditor: () -> Unit = {},
    onRetryVideoNote: () -> Unit = {},
    onDeleteVideoNoteClick: () -> Unit = {},
    onShareVideoNote: (VideoNoteEditorDocument) -> Unit = {},
    onPublicVideoNoteClick: (Long, String) -> Unit = { _, _ -> },
    bgmInfo: BgmInfo? = null,
    bgmInfoList: List<BgmInfo> = emptyList(),
    relatedVideos: List<RelatedVideo> = emptyList(),
    onTimestampClick: ((Long) -> Unit)? = null,
    onBgmClick: (BgmInfo) -> Unit = {},
    onDescriptionUrlClick: ((String) -> Unit)? = null,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit = { _, _ -> },
    onSearchKeywordClick: (String) -> Unit = {},
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
    val videoNoteDefaultCollapsed by com.android.purebilibili.core.store.SettingsManager
        .getVideoNoteDefaultCollapsed(context)
        .collectAsStateWithLifecycle(initialValue = false
        )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface) // 🎨 [修复] 与 TabBar 统一使用容器背景色，消除割裂感
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
            transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
            isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
            sourceRouteForSharedElement = sourceRouteForSharedElement
        )

        VideoTitleWithDesc(
            info = info,
            videoTags = videoTags,
            transitionEnabled = transitionEnabled,  // 🔗 传递共享元素开关
            isQuickReturnLimitedForSharedElements = isQuickReturnLimitedForSharedElements,
            sourceRouteForSharedElement = sourceRouteForSharedElement,
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

        // [新增] AI Summary
        if (shouldShowAiSummaryEntry(
                aiSummary = aiSummary,
                isAiSummaryEntryEnabled = videoAiSummaryEntryEnabled
            )
        ) {
            AiSummaryCard(
                aiSummary = aiSummary,
                onTimestampClick = onTimestampClick,
                onCreateNoteDraftClick = onCreateNoteDraftFromAiSummary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else if (videoAiSummaryEntryEnabled && aiSummaryPrompt != null) {
            AiSummaryPromptCard(
                promptState = aiSummaryPrompt,
                onActionClick = onRetryAiSummary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (shouldShowVideoNoteCard(videoNoteEnabled)) {
            VideoNoteCard(
                noteState = videoNoteState,
                isLoggedIn = isLoggedIn,
                onCreateOrEditClick = onOpenVideoNoteEditor,
                onRetryClick = onRetryVideoNote,
                onDeleteClick = onDeleteVideoNoteClick,
                onShareClick = onShareVideoNote,
                onPublicNoteClick = onPublicVideoNoteClick,
                defaultCollapsed = videoNoteDefaultCollapsed,
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
    }

}

/**
 * Tab 栏组件
 */
@Composable
private fun VideoContentTabBar(
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
                    if (liquidChromeSpec.useTransparentTabRowBackground) {
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
            Box(
                modifier = Modifier.width(
                    (resolveVideoContentTabBarDockItemWidthDp(
                        liquidChromeSpec.labelFontSizeSp,
                    ) * tabs.size).dp,
                ),
                contentAlignment = Alignment.CenterStart,
            ) {
                AppThemeAdaptiveTabRow(
                    options = tabs.mapIndexed { index, label -> AppSegmentOption(index, label) },
                    selectedValue = selectedTabIndex,
                    onSelectionChange = onTabSelected,
                    modifier = Modifier.fillMaxWidth(),
                    height = liquidChromeSpec.segmentedControlHeightDp.dp,
                    indicatorHeight = liquidChromeSpec.segmentedControlIndicatorHeightDp.dp,
                    labelFontSize = liquidChromeSpec.labelFontSizeSp.sp,
                    // 该栏的指示器由 HorizontalPager 实时位置驱动，禁止自身再 settle 一次。
                    dragSelectionEnabled = true,
                    tapPressRefractionEnabled = true,
                    miuixBackdrop = miuixBackdrop,
                    indicatorPositionProvider = indicatorPositionProvider,
                    isScrollInProgressProvider = isScrollInProgressProvider,
                )
            }

            if (shouldShowVideoContentTabBarDanmakuActions(selectedTabIndex)) {
                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(
                    visible = shouldShowDanmakuSendInput(isPlayerCollapsed = isPlayerCollapsed),
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    AppText(
                        text = danmakuActionLayoutPolicy.sendLabel,
                        fontSize = danmakuActionLayoutPolicy.sendTextSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        tapToCopyEnabled = false,
                        modifier = Modifier
                            .sizeIn(
                                minWidth = AppChromeSizeTokens.MinimumTouchTarget,
                                minHeight = danmakuActionLayoutPolicy.sendMinHeightDp.dp,
                            )
                            .wrapContentHeight(align = Alignment.CenterVertically)
                            .clickable(onClick = onDanmakuSendClick),
                    )
                }

                NativeDanmakuToggleButton(
                    enabled = danmakuEnabled,
                    onToggle = onDanmakuToggle,
                    activeTint = MaterialTheme.colorScheme.secondary,
                    inactiveTint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(end = danmakuActionLayoutPolicy.toggleTrailingPaddingDp.dp)
                        .size(danmakuActionLayoutPolicy.toggleButtonSizeDp.dp),
                    iconSize = danmakuActionLayoutPolicy.toggleIconSizeDp.dp,
                )
            }
        }
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
        AppText(
            text = "相关推荐",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
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
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .copyOnLongPress(tagName, "标签")
        )
    }
}
