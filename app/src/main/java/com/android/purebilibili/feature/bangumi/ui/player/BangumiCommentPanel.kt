package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.topSolidProgressiveFade
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.home.components.biliPaiProgressiveTopBlur
import com.android.purebilibili.feature.video.screen.VideoCommentTab
import com.android.purebilibili.feature.video.screen.isVideoContentCommentListAtTop
import com.android.purebilibili.feature.video.screen.rememberVideoCommentHeaderScrollConnection
import com.android.purebilibili.feature.video.screen.resolveVideoContentTabBarCollapseProgress
import com.android.purebilibili.feature.video.screen.videoContentPadding
import com.android.purebilibili.feature.video.screen.videoContentHeight
import com.android.purebilibili.feature.video.screen.shouldUseLightweightCommentRendering
import com.android.purebilibili.feature.video.ui.components.BottomInputBar
import com.android.purebilibili.feature.video.ui.components.CommentFraudDetectingBanner
import com.android.purebilibili.feature.video.ui.components.CommentSearchSheet
import com.android.purebilibili.feature.video.ui.components.VideoCommentFloatingHeader
import com.android.purebilibili.feature.video.ui.components.resolveBottomInputBarContentBottomPadding
import com.android.purebilibili.feature.video.ui.components.shouldShowVideoCommentBackToTop
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** Episode wiring only; ordinary videos own the shared list, chrome and composer appearance. */
@Composable
internal fun BangumiCommentPanel(
    viewModel: VideoCommentViewModel,
    listState: LazyListState,
    showIdentityDecorations: Boolean,
    isVideoPlaying: Boolean,
    maxTimestampMs: Long?,
    onRootCommentClick: () -> Unit,
    onReplyClick: (ReplyItem) -> Unit,
    onUserClick: (Long) -> Unit,
    onCommentUrlClick: (String) -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    onImagePreview: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit,
    isLiked: Boolean,
    likeStatusKnown: Boolean,
    isFavorited: Boolean,
    isCoined: Boolean,
    onLikeClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCoinClick: () -> Unit,
    onShareClick: () -> Unit,
    showActionButtons: Boolean,
    topContentInsetPx: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val state by viewModel.commentState.collectAsStateWithLifecycle()
    val fullSearchState by viewModel.fullSearchState.collectAsStateWithLifecycle()
    val fullSearchReplies by viewModel.fullSearchReplies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val hazeState = remember { HazeState() }
    val themeConfig = LocalAppThemeConfig.current
    val liquidGlassEnabled = themeConfig.liquidGlassEnabled
    val progressiveHeaderEnabled = themeConfig.progressiveTopBlurEnabled && !themeConfig.headerBlurEnabled
    val solidHeaderEnabled = themeConfig.progressiveTopFadeEnabled && !themeConfig.headerBlurEnabled
    val floatingHeader = liquidGlassEnabled || progressiveHeaderEnabled || solidHeaderEnabled
    val density = LocalDensity.current
    val commentHeaderHeightPx = remember(density, liquidGlassEnabled) {
        mutableFloatStateOf(with(density) { 46.dp.toPx() })
    }
    val commentHeaderHeightProvider = remember(commentHeaderHeightPx) {
        { commentHeaderHeightPx.floatValue }
    }
    val hideSortDock by SettingsManager.getVideoDetailChromeScrollHideEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val headerCollapsePx = remember(listState) { mutableFloatStateOf(0f) }
    val headerScroll = rememberVideoCommentHeaderScrollConnection(
        listState = listState,
        collapsePx = headerCollapsePx,
        maxCollapsePx = commentHeaderHeightProvider,
        enabled = true,
    )
    val titleCollapseProgress = {
        resolveVideoContentTabBarCollapseProgress(
            collapsePx = headerCollapsePx.floatValue,
            maxCollapsePx = commentHeaderHeightPx.floatValue,
            selectedTabIndex = 1,
            listAtTop = isVideoContentCommentListAtTop(
                listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
            ),
        )
    }
    val visibleHeaderHeight = {
        val height = commentHeaderHeightPx.floatValue
        if (hideSortDock) (height - headerCollapsePx.floatValue).coerceAtLeast(0f) else height
    }
    val showBackToTop by remember(listState) {
        derivedStateOf {
            shouldShowVideoCommentBackToTop(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }
    val lightweightRendering by remember(listState, isVideoPlaying) {
        derivedStateOf {
            shouldUseLightweightCommentRendering(
                selectedTabIndex = 1,
                isVideoPlaying = isVideoPlaying,
                isCommentListScrolling = listState.isScrollInProgress,
            )
        }
    }
    var showSearch by remember { mutableStateOf(false) }
    val changeSort: (CommentSortMode) -> Unit = { mode ->
        viewModel.setSortMode(mode)
        scope.launch { SettingsManager.setCommentDefaultSortMode(context, mode.apiMode) }
    }
    val bottomPadding = resolveBottomInputBarContentBottomPadding(
        showBar = true,
        floatingLiquidGlass = liquidGlassEnabled,
        showActionButtonsFallback = false,
    ) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val listPadding = remember(density, bottomPadding, topContentInsetPx) {
        videoContentPadding(topContentInsetPx, bottomPadding, density)
    }

    Column(modifier = modifier) {
        CommentFraudDetectingBanner(isDetecting = state.isDetectingFraud)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Only the list is captured. Glass header/input/back-to-top are sibling overlays,
            // so no glass surface can recursively sample its own RenderNode.
            VideoCommentTab(
                listState = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .hazeSourceCompat(hazeState)
                    .background(MaterialTheme.colorScheme.surface)
                    .nestedScroll(headerScroll),
                upMid = state.upMid,
                maxTimestampMs = maxTimestampMs,
                replies = state.replies,
                pageControl = state.pageControl,
                replyCount = state.replyCount,
                emoteMap = emptyMap(),
                isRepliesLoading = state.isRepliesLoading,
                isRepliesRefreshing = state.isRepliesRefreshing,
                repliesError = state.repliesError,
                isRepliesEnd = state.isRepliesEnd,
                paginationPage = state.nextPage,
                voteCard = state.voteCard,
                onUpClick = onUserClick,
                onSubReplyClick = viewModel::openSubReply,
                onCommentReplyClick = onReplyClick,
                onLoadMoreReplies = viewModel::loadComments,
                onRefreshReplies = viewModel::refreshComments,
                onImagePreview = onImagePreview,
                onTimestampClick = onTimestampClick,
                contentPadding = listPadding,
                currentMid = state.currentMid,
                showUpFlag = state.showUpFlag,
                dissolvingIds = state.dissolvingIds,
                pinnedReplyIds = state.pinnedReplyIds,
                onDeleteComment = viewModel::deleteComment,
                onDissolveStart = viewModel::startDissolve,
                onCommentLike = viewModel::likeComment,
                onCommentHate = viewModel::hateComment,
                likedComments = state.likedComments,
                hatedComments = state.hatedComments,
                onCommentUrlClick = onCommentUrlClick,
                onReportComment = { rpid, reason -> viewModel.reportComment(rpid, reason) },
                onToggleTopComment = viewModel::toggleTopComment,
                onCheckCommentFraud = viewModel::checkCommentFraud,
                showIdentityDecorations = showIdentityDecorations,
                lightweightCommentRendering = lightweightRendering,
                sortMode = state.sortMode,
                onSortModeChange = changeSort,
                showNativeSortHeader = true,
                showSortControlInHeader = true,
                showHeader = false,
                compactSortHeader = true,
                floatingHeaderContentPaddingPx = visibleHeaderHeight,
                onSearchClick = { showSearch = true },
            )
            // 所有材质共用标题显隐；排序 dock 由视频详情滚动隐藏设置控制。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, topContentInsetPx().roundToInt()) }
                    .videoContentHeight(visibleHeaderHeight)
                    .graphicsLayer { clip = true }
                    .then(if (floatingHeader) Modifier else Modifier.background(MaterialTheme.colorScheme.surface)),
            ) {
                Box(
                    modifier = Modifier.matchParentSize()
                        .biliPaiProgressiveTopBlur(
                            backdrop = backdrop,
                            enabled = progressiveHeaderEnabled,
                            surfaceColor = androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .topSolidProgressiveFade(
                            surfaceColor = MaterialTheme.colorScheme.surface,
                            enabled = solidHeaderEnabled,
                        ),
                )
                VideoCommentFloatingHeader(
                    count = state.replyCount,
                    sortMode = state.sortMode,
                    onSortModeChange = changeSort,
                    onSearchClick = { showSearch = true },
                    backdrop = backdrop,
                    liquidGlassEnabled = liquidGlassEnabled,
                    compactTitleTypography = true,
                    titleCollapseProgress = titleCollapseProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true)
                        .onSizeChanged { size ->
                            if (size.height > 0 && commentHeaderHeightPx.floatValue != size.height.toFloat()) {
                                commentHeaderHeightPx.floatValue = size.height.toFloat()
                            }
                        },
                )
            }
            BottomInputBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                isLiked = isLiked,
                likeStatusKnown = likeStatusKnown,
                isFavorited = isFavorited,
                isCoined = isCoined,
                onLikeClick = onLikeClick,
                onFavoriteClick = onFavoriteClick,
                onCoinClick = onCoinClick,
                onShareClick = onShareClick,
                onCommentClick = onRootCommentClick,
                backdrop = if (liquidGlassEnabled) backdrop else null,
                hazeState = hazeState,
                isScrollInProgressProvider = { listState.isScrollInProgress },
                scrollPositionProvider = { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset },
                showActionButtons = showActionButtons,
            )
            AppLiquidGlassBackToTopButton(
                visible = rememberBackToTopButtonEnabled() && showBackToTop,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = bottomPadding),
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
            )
        }
    }
    if (showSearch) {
        CommentSearchSheet(
            replies = state.replies,
            fullReplies = fullSearchReplies,
            fullSearchState = fullSearchState,
            onLoadAllComments = viewModel::loadAllCommentsForSearch,
            upMid = state.upMid,
            onCommentClick = onReplyClick,
            onSubReplyClick = { viewModel.openSubReply(it) },
            onDismiss = { showSearch = false },
            miuixBackdrop = backdrop,
        )
    }
}
