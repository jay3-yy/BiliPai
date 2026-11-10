// 文件路径: feature/dynamic/components/DynamicCommentSheet.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.LocalAppThemeConfig

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.skeleton.CommentListItemSkeleton

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import com.android.purebilibili.feature.video.ui.components.resolveCommentThreadCoveredBlurProgress
import com.android.purebilibili.feature.video.ui.components.resolveCommentThreadPredictiveBackOffsetY
import com.android.purebilibili.feature.video.ui.components.rememberCommentThreadDrag
import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.DynamicViewModel
import com.android.purebilibili.feature.dynamic.isDynamicCommentLiked
import com.android.purebilibili.feature.dynamic.resolveDynamicThreadReactionIds
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentCountLabel
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentEmptyLabel
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentSheetTotalCount
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentSheetHostContent
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentSkeletonSlotCount
import com.android.purebilibili.feature.dynamic.DYNAMIC_COMMENT_SKELETON_MIN_SLOTS
import com.android.purebilibili.feature.dynamic.DynamicCommentSheetHostContent
import com.android.purebilibili.feature.video.ui.components.rememberCommentAvatarPushEnabled
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.SubReplyDetailContent
import com.android.purebilibili.feature.video.ui.components.resolveVideoCommentPredictiveBackProgress
import com.android.purebilibili.feature.video.ui.components.resolveVideoCommentPredictiveBackTarget
import com.android.purebilibili.feature.video.ui.components.VideoCommentPredictiveBackTarget
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppModalBottomSheet
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.core.ui.CommentWindowNavigation

@Composable
fun DynamicCommentOverlayHost(
    viewModel: DynamicViewModel,
    primaryItems: List<DynamicItem>,
    secondaryItems: List<DynamicItem> = emptyList(),
    toastContext: Context,
    onUserClick: (Long) -> Unit,
) {
    val selectedDynamicId by viewModel.selectedDynamicId.collectAsStateWithLifecycle()
    // 评论区一次性读取单一状态，避免 5 个独立 flow 分别发射造成中间帧。
    val commentUiState by viewModel.commentUiState.collectAsStateWithLifecycle()
    val comments = commentUiState.comments
    val commentsLoading = commentUiState.isLoading
    val commentsRefreshing = commentUiState.isRefreshing
    val commentsRefreshError = commentUiState.refreshError
    val commentsLoadingMore = commentUiState.isLoadingMore
    val liveCommentCount = commentUiState.totalCount
    val subReplyState by viewModel.subReplyState.collectAsStateWithLifecycle()
    val sortMode by viewModel.dynamicCommentSortMode.collectAsStateWithLifecycle()
    val inspectionMode = LocalInspectionMode.current

    LaunchedEffect(commentsRefreshError, inspectionMode) {
        commentsRefreshError?.let { message ->
            if (!inspectionMode) {
                android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (!selectedDynamicId.isNullOrBlank()) {
        val dynamicId = requireNotNull(selectedDynamicId)
        val dynamicItem = remember(dynamicId, primaryItems, secondaryItems) {
            primaryItems.find { it.id_str == dynamicId }
                ?: secondaryItems.find { it.id_str == dynamicId }
        }
        val fallbackCount = dynamicItem?.modules?.module_stat?.comment?.count ?: 0
        val totalCount = remember(liveCommentCount, fallbackCount) {
            resolveDynamicCommentSheetTotalCount(
                liveCount = liveCommentCount,
                fallbackCount = fallbackCount
            )
        }
        DynamicCommentSheet(
            comments = comments,
            totalCount = totalCount,
            sortMode = sortMode,
            isLoading = commentsLoading,
            isRefreshing = commentsRefreshing,
            hasRefreshError = commentsRefreshError != null,
            isLoadingMore = commentsLoadingMore,
            hasMore = commentUiState.hasMore,
            nextPage = commentUiState.nextPage,
            onDismiss = { viewModel.closeCommentSheet() },
            onRefresh = viewModel::refreshComments,
            onSortModeChange = { viewModel.setDynamicCommentSortMode(it) },
            onOpenComposer = viewModel::openCommentComposer,
            onViewReplies = viewModel::openSubReply,
            onReply = { reply -> viewModel.startCommentReply(reply) },
            onLike = { reply -> viewModel.likeComment(reply.rpid) },
            onHate = { reply ->
                viewModel.hateComment(reply.rpid) { _, message ->
                    if (!inspectionMode) android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            dynamicAuthorMid = dynamicItem?.modules?.module_author?.mid ?: 0L,
            currentUserMid = TokenManager.midCache,
            onDelete = { reply ->
                viewModel.deleteDynamicComment(reply.rpid) { _, message ->
                    if (!inspectionMode) android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onToggleTop = { reply ->
                viewModel.toggleDynamicCommentTop(reply) { _, message ->
                    if (!inspectionMode) android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onReport = { reply, reason ->
                viewModel.reportDynamicComment(reply.rpid, reason) { _, message ->
                    if (!inspectionMode) android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onLoadMore = { viewModel.loadMoreComments() },
            onUserClick = onUserClick,
            subReplyState = subReplyState,
            onCloseSubReply = { viewModel.closeSubReply() },
            onLoadMoreSubReplies = { viewModel.loadMoreSubReplies() },
            onRefreshSubReplies = { viewModel.refreshSubReplies() },
            onSubReplySortModeChange = { viewModel.setSubReplySortMode(it) },
            onThreadCommentLike = { rpid -> viewModel.likeComment(rpid) },
            onThreadCommentHate = { rpid ->
                viewModel.hateComment(rpid) { _, message ->
                    if (!inspectionMode) android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onThreadCommentDelete = { rpid ->
                viewModel.deleteDynamicComment(rpid) { _, message ->
                    if (!inspectionMode) {
                        android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onThreadCommentReport = { rpid, reason ->
                viewModel.reportDynamicComment(rpid, reason) { _, message ->
                    if (!inspectionMode) {
                        android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        DynamicCommentInputHost(
            draftKeyPrefix = "dynamic-sheet:$dynamicId",
            viewModel = viewModel,
        ) { message, images, onResult ->
            viewModel.postComment(dynamicId, message, images) { success, msg ->
                if (!inspectionMode) {
                    android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
                onResult(success)
            }
        }
    }
}

/**
 *  动态评论底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun DynamicCommentSheet(
    comments: List<ReplyItem>,
    totalCount: Int,  //  [新增] 总评论数
    sortMode: CommentSortMode = CommentSortMode.HOT,
    isLoading: Boolean,
    isRefreshing: Boolean,
    hasRefreshError: Boolean,
    isLoadingMore: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (CommentSortMode) -> Unit = {},
    onOpenComposer: () -> Unit,
    onViewReplies: (ReplyItem, Long) -> Unit = { _, _ -> },
    onReply: (ReplyItem) -> Unit = {},
    onLike: (ReplyItem) -> Unit = {},
    onHate: (ReplyItem) -> Unit = {},
    dynamicAuthorMid: Long = 0L,
    currentUserMid: Long? = null,
    onDelete: (ReplyItem) -> Unit = {},
    onToggleTop: (ReplyItem) -> Unit = {},
    onReport: (ReplyItem, Int) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
    onUserClick: (Long) -> Unit,
    subReplyState: SubReplyUiState = SubReplyUiState(),
    onCloseSubReply: () -> Unit = {},
    onLoadMoreSubReplies: () -> Unit = {},
    onRefreshSubReplies: () -> Unit,
    onSubReplySortModeChange: (SubReplySortMode) -> Unit = {},
    onThreadCommentLike: (Long) -> Unit = {},
    onThreadCommentHate: (Long) -> Unit = {},
    onThreadCommentDelete: (Long) -> Unit = {},
    onThreadCommentReport: (Long, Int) -> Unit = { _, _ -> },
    hasMore: Boolean = false,
    nextPage: Int = 1,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val avatarPushEnabled = rememberCommentAvatarPushEnabled()
    // 骨架槽位数按可视高度估算：铺满首屏，数据到达时列表不会被撑长或缩短。
    val commentSkeletonSlotCount = resolveDynamicCommentSkeletonSlotCount(
        viewportHeightDp = LocalConfiguration.current.screenHeightDp,
    )

    // 服务端分页状态与显示计数分离；最新值由稳定的触底监听读取。
    val canLoadMore by rememberUpdatedState(hasMore && !isLoading && !isRefreshing && !hasRefreshError && !isLoadingMore)
    val latestNextPage by rememberUpdatedState(nextPage)
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var previewSourceRect by remember { mutableStateOf<ImagePreviewSourceAnchor?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    val sortModes = remember { listOf(CommentSortMode.HOT, CommentSortMode.NEWEST) }
    val sortModeLabels = remember(sortModes) { sortModes.map { it.label } }

    if (showImagePreview && previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            sourceRect = previewSourceRect?.rect,
            sourceRects = previewSourceRect?.galleryRects.orEmpty(),
            sourceCornerRadiusDp = previewSourceRect?.cornerRadiusDp
                ?: AppShapes.containerCornerDp(ContainerLevel.Field).value,
            textContent = previewTextContent,
            onDismiss = {
                showImagePreview = false
                previewTextContent = null
            }
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val itemCount = listState.layoutInfo.totalItemsCount
            // 返回页码，使空页/重复页后仍可消费已前进的游标，不依赖评论条数改变。
            if (canLoadMore && itemCount > 0 && lastVisibleIndex >= itemCount - 4) latestNextPage else null
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { latestOnLoadMore() }
    }
    val emoteCatalogSessionKey = DynamicEmoteCatalog.currentSessionKey()
    var emoteMap by remember(emoteCatalogSessionKey) {
        mutableStateOf(DynamicEmoteCatalog.snapshot())
    }
    LaunchedEffect(emoteCatalogSessionKey) {
        emoteMap = DynamicEmoteCatalog.ensureLoaded()
    }
    val hostContent = resolveDynamicCommentSheetHostContent(subReplyState.visible)
    val likedThreadComments = remember(subReplyState.rootReply, subReplyState.items) {
        resolveDynamicThreadReactionIds(subReplyState, action = 1)
    }
    val hatedThreadComments = remember(subReplyState.rootReply, subReplyState.items) {
        resolveDynamicThreadReactionIds(subReplyState, action = 2)
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dismissOnBackPress = false,
        dragHandle = null,
        // 内容自己绘制底部安全区，避免弹层默认底色在评论页下方露出一条异色带。
        // 同时覆盖 MD3/Miuix，玻璃开关不改变安全区归属。
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        sheetSurfaceModifier = Modifier
            .background(AppSurfaceTokens.surfaceContainer())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        CommentWindowNavigation {
            val commentBackState = rememberNavigationEventState(NavigationEventInfo.None)
            val threadSheetMotion = com.android.purebilibili.core.ui.rememberAppBottomSheetMotion()
            var threadBackCompleted by remember { mutableStateOf(false) }
            val rawThreadBackProgress = resolveVideoCommentPredictiveBackProgress(
                inProgress = commentBackState.transitionState is NavigationEventTransitionState.InProgress &&
                    hostContent == DynamicCommentSheetHostContent.THREAD_DETAIL,
                progress = (commentBackState.transitionState as? NavigationEventTransitionState.InProgress)
                    ?.latestEvent
                    ?.progress
                    ?: 0f,
            )
            val threadBackProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = rawThreadBackProgress,
                animationSpec = if (commentBackState.transitionState is NavigationEventTransitionState.InProgress) {
                    androidx.compose.animation.core.snap()
                } else {
                    tween(180)
                },
                label = "comment_thread_predictive_back",
            )
            LaunchedEffect(subReplyState.visible) {
                if (subReplyState.visible) threadBackCompleted = false
            }
            NavigationBackHandler(
                state = commentBackState,
                isBackEnabled = true,
                onBackCompleted = {
                    when (
                        resolveVideoCommentPredictiveBackTarget(
                            subReplyVisible = subReplyState.visible,
                            conversationActive = subReplyState.conversationAnchor != null,
                        )
                    ) {
                        VideoCommentPredictiveBackTarget.CLOSE_CONVERSATION -> onCloseSubReply()
                        VideoCommentPredictiveBackTarget.CLOSE_THREAD -> {
                            threadBackCompleted = rawThreadBackProgress > 0f
                            onCloseSubReply()
                        }
                        VideoCommentPredictiveBackTarget.DISMISS_SHEET -> onDismiss()
                    }
                },
            )
            val threadDrag = rememberCommentThreadDrag(
                visible = subReplyState.visible,
                rootReplyId = subReplyState.threadRootId.takeIf { it > 0L },
                onDismiss = onCloseSubReply,
            )
            val commentChromeBackdrop = rememberLayerBackdrop()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .layerBackdrop(commentChromeBackdrop)
                        .background(AppSurfaceTokens.background())
                )
                Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val coveredBlurProgress = if (
                    hostContent == DynamicCommentSheetHostContent.THREAD_DETAIL
                ) {
                    resolveCommentThreadCoveredBlurProgress(maxOf(threadBackProgress, threadDrag.revealProgress))
                } else {
                    0f
                }
                val coveredBlurEffectCache = remember { BlurRenderEffectCache() }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect = null
                            if (coveredBlurProgress > 0f &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ) {
                                val blurFrame = resolvePredictiveBackBlurFrame(
                                    progress = coveredBlurProgress,
                                )
                                renderEffect = if (blurFrame.blurRadiusPx > 0.5f) {
                                    coveredBlurEffectCache.resolve(blurFrame.blurRadiusPx)
                                } else {
                                    null
                                }
                            }
                        },
                ) {
                // 标题、数量和排序保持在同一视觉层级，关闭按钮保留 48dp 触控区。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppSpacingTokens.Large,
                            top = AppSpacingTokens.Small,
                            end = AppSpacingTokens.Small,
                            bottom = AppSpacingTokens.Medium,
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        AppText(
                            text = "评论",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AppText(
                            text = resolveDynamicCommentCountLabel(totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppSurfaceTokens.onSurfaceVariantActions(),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    DynamicCommentSortControl(
                        items = sortModeLabels,
                        selectedIndex = sortModes.indexOf(sortMode).coerceAtLeast(0),
                        onSelected = { index ->
                            sortModes.getOrNull(index)?.let(onSortModeChange)
                        },
                        miuixBackdrop = commentChromeBackdrop,
                    )
                    AppIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge),
                    ) {
                        AppIcon(
                            rememberAppClearIcon(),
                            contentDescription = "关闭",
                            modifier = Modifier.size(AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall)
                        )
                    }
                }

                // 评论列表
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AdaptivePullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = AppSpacingTokens.Large,
                                vertical = AppSpacingTokens.Small,
                            ),
                            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
                        ) {
                            when {
                                isLoading && comments.isEmpty() -> dynamicCommentSkeletonItems(
                                    slotCount = commentSkeletonSlotCount,
                                    keyPrefix = "dynamic_comment_skeleton_",
                                )

                                comments.isEmpty() -> item(
                                    key = "dynamic_comment_empty",
                                    contentType = "dynamic_comment_empty",
                                ) {
                                    Box(
                                        modifier = Modifier.fillParentMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = AppSpacingTokens.ExtraLarge),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large)
                                                    .clip(AppShapes.container(ContainerLevel.Pill))
                                                    .background(AppSurfaceTokens.surfaceContainerHigh()),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AppIcon(
                                                    rememberAppCommentIcon(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(AppSpacingTokens.DoubleExtraLarge),
                                                    tint = AppContentColors.accent(),
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(AppSpacingTokens.Large))
                                            AppText(
                                                text = resolveDynamicCommentEmptyLabel(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                                            AppText(
                                                text = "来聊聊你对这条动态的看法",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AppSurfaceTokens.onSurfaceVariantActions(),
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    items(
                                        items = comments,
                                        key = { it.rpid },
                                        contentType = { DYNAMIC_COMMENT_ITEM_CONTENT_TYPE },
                                    ) { reply ->
                                        val actionCapabilities = resolveDynamicCommentActionCapabilities(
                                            reply, dynamicAuthorMid, currentUserMid,
                                        )
                                        ReplyItemView(
                                            modifier = dynamicCommentItemModifier(),
                                            avatarListState = listState.takeIf { avatarPushEnabled },
                                            item = reply,
                                            upMid = dynamicAuthorMid,
                                            onClick = { onViewReplies(reply, 0L) },
                                            onSubClick = onViewReplies,
                                            onReplyClick = { onReply(reply) },
                                            onLikeClick = { onLike(reply) },
                                            isLiked = isDynamicCommentLiked(reply),
                                            onHateClick = { onHate(reply) },
                                            isHated = reply.action == 2,
                                            onDeleteClick = if (actionCapabilities.canDelete) ({ onDelete(reply) }) else null,
                                            onReportClick = { reason -> onReport(reply, reason) },
                                            canToggleTop = actionCapabilities.canToggleTop,
                                            onToggleTopClick = { onToggleTop(reply) },
                                            onAvatarClick = { mid -> mid.toLongOrNull()?.let(onUserClick) },
                                            onImagePreview = { images, index, rect, textContent ->
                                                previewImages = images
                                                previewInitialIndex = index
                                                previewSourceRect = rect
                                                previewTextContent = textContent
                                                showImagePreview = true
                                            },
                                        )
                                    }
                                    if (isLoadingMore) {
                                        item(key = "dynamic_comment_loading_more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = AppSpacingTokens.Small),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AdaptiveLoadingIndicator(size = AppSpacingTokens.ExtraLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = hostContent == DynamicCommentSheetHostContent.THREAD_DETAIL,
                    enter = threadSheetMotion.contentEnter,
                    exit = if (threadBackCompleted) androidx.compose.animation.ExitTransition.None else threadSheetMotion.contentExit,
                ) {
                    val rootReply = subReplyState.rootReply
                    if (rootReply != null || subReplyState.items.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(threadDrag.containerModifier)
                                .graphicsLayer {
                                    translationY = threadDrag.offsetPx.value + resolveCommentThreadPredictiveBackOffsetY(
                                        progress = threadBackProgress,
                                        heightPx = size.height,
                                    )
                                }
                                .background(AppSurfaceTokens.background()),
                        ) {
                            SubReplyDetailContent(
                                headerDragModifier = threadDrag.headerModifier,
                                rootReply = rootReply,
                                upMid = dynamicAuthorMid,
                                subReplies = subReplyState.items,
                                sortMode = subReplyState.sortMode,
                                error = subReplyState.error,
                                onSortModeChange = onSubReplySortModeChange,
                                remoteReplyCount = subReplyState.totalCount,
                                isLoading = subReplyState.isLoading,
                                isRefreshing = subReplyState.isRefreshing,
                                isEnd = subReplyState.isEnd,
                                paginationPage = subReplyState.page,
                                emoteMap = emoteMap,
                                onLoadMore = onLoadMoreSubReplies,
                                onRefresh = onRefreshSubReplies,
                                onDismiss = onCloseSubReply,
                                applyStatusBarPadding = false,
                                onImagePreview = { images, index, rect, textContent ->
                                    previewImages = images
                                    previewInitialIndex = index
                                    previewSourceRect = rect
                                    previewTextContent = textContent
                                    showImagePreview = true
                                },
                                onReplyClick = { reply -> onReply(reply) },
                                dissolvingIds = subReplyState.dissolvingIds,
                                currentMid = currentUserMid ?: 0L,
                                onDeleteComment = onThreadCommentDelete,
                                onCommentLike = onThreadCommentLike,
                                onCommentHate = onThreadCommentHate,
                                onReportComment = onThreadCommentReport,
                                likedComments = likedThreadComments,
                                hatedComments = hatedThreadComments,
                                onAvatarClick = { mid -> mid.toLongOrNull()?.let(onUserClick) },
                                targetReplyId = subReplyState.targetReplyId,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        com.android.purebilibili.feature.video.ui.components.SubReplyInitialContent(
                            isLoading = subReplyState.isLoading,
                            error = subReplyState.error,
                            onRetry = onLoadMoreSubReplies,
                            onDismiss = onCloseSubReply,
                        )
                    }
                }
                }

                val liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled
                DynamicCommentEntryBar(
                    onClick = onOpenComposer,
                    liquidGlassEnabled = liquidGlassEnabled,
                    backdrop = commentChromeBackdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppSurfaceTokens.surfaceContainer())
                        .padding(
                            horizontal = AppSpacingTokens.Large,
                            vertical = AppSpacingTokens.Medium,
                        ),
                )
                }
            }
        }
    }
}

@Composable
private fun DynamicCommentSortControl(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    miuixBackdrop: MiuixBackdrop? = null,
) {
    if (items.isEmpty()) return
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val spec = remember(items.size, liquidGlassEnabled) {
        resolveDynamicCommentSortControlSpec(items.size, liquidGlassEnabled)
    }
    Box(
        modifier = modifier.requiredWidth((spec.itemWidthDp * items.size).dp),
    ) {
        DynamicAdaptiveSegmentedControl(
            items = items,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            itemWidth = spec.itemWidthDp.dp,
            height = spec.heightDp.dp,
            indicatorHeight = spec.indicatorHeightDp.dp,
            labelFontSize = MaterialTheme.typography.bodySmall.fontSize,
            // Keep the renderer's fillMaxWidth() inside the fixed-width outer box so
            // the whole latest/hottest control remains aligned to the header's end.
            modifier = Modifier.fillMaxWidth(),
            backdrop = miuixBackdrop,
        )
    }
}

internal data class DynamicCommentSortControlSpec(
    val itemWidthDp: Int,
    val heightDp: Int,
    val indicatorHeightDp: Int,
)

internal fun resolveDynamicCommentSortControlSpec(
    itemCount: Int,
    liquidGlassEnabled: Boolean = false,
): DynamicCommentSortControlSpec {
    // Share the video comment policy so outer/inner widths cannot drift between comment hosts.
    // Disabled materials retain the original 66dp slots through the shared default.
    val shared = com.android.purebilibili.feature.video.ui.components
        .resolveCommentSortSegmentedControlSpec(itemCount, liquidGlassEnabled)
    return DynamicCommentSortControlSpec(shared.itemWidthDp, shared.heightDp, shared.indicatorHeightDp)
}

internal fun hasDynamicCommentSortIndicatorScaleClearance(
    containerHeightDp: Int,
    indicatorHeightDp: Int,
): Boolean {
    val geometry = com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry(
        dockHeightDp = containerHeightDp.toFloat(),
        indicatorHeightDp = indicatorHeightDp.toFloat(),
    )
    return geometry.pressedHeightDp > containerHeightDp
}

/** Inline comments for dynamic detail, rendered by the detail screen's LazyColumn. */
@Composable
fun DynamicInlineCommentHeader(
    totalCount: Int,
    sortMode: CommentSortMode,
    onSortModeChange: (CommentSortMode) -> Unit,
    miuixBackdrop: MiuixBackdrop? = null,
    onShowReactions: (() -> Unit)? = null,
) {
    val sortModes = remember { listOf(CommentSortMode.HOT, CommentSortMode.NEWEST) }
    val sortModeLabels = remember(sortModes) { sortModes.map { it.label } }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            AppText(
                text = resolveDynamicCommentCountLabel(totalCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (onShowReactions != null) {
                AppText(
                    text = "赞和转发 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.accent(),
                    modifier = Modifier
                        .appActionClickable(onClick = onShowReactions)
                        .padding(vertical = 6.dp),
                )
            }
        }
        DynamicCommentSortControl(
            items = sortModeLabels,
            selectedIndex = sortModes.indexOf(sortMode).coerceAtLeast(0),
            onSelected = { index ->
                sortModes.getOrNull(index)?.let(onSortModeChange)
            },
            // Null deliberately selects the shared control's mounted local source. Do not
            // manufacture an unrecorded Backdrop here or sample the LazyColumn containing
            // this header, which would be invalid/recursive on Xiaomi's native renderer.
            miuixBackdrop = miuixBackdrop,
        )
    }
    AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** 真实评论项的复用类型；临时骨架使用独立类型。 */
internal const val DYNAMIC_COMMENT_ITEM_CONTENT_TYPE = "dynamic_comment_item"

private const val DYNAMIC_COMMENT_FADE_IN_MILLIS = 220
private const val DYNAMIC_COMMENT_FADE_OUT_MILLIS = 160
private const val DYNAMIC_COMMENT_PLACEMENT_DAMPING = 0.9f
private const val DYNAMIC_COMMENT_PLACEMENT_STIFFNESS = 220f

/**
 * 真实评论使用淡入、淡出和位移动画；临时骨架不参与离场动画。
 */
@Composable
internal fun LazyItemScope.dynamicCommentItemModifier(): Modifier =
    if (LocalAppThemeConfig.current.uiEntranceAnimationEnabled) {
        Modifier.animateItem(
            fadeInSpec = tween(durationMillis = DYNAMIC_COMMENT_FADE_IN_MILLIS),
            placementSpec = spring(
                dampingRatio = DYNAMIC_COMMENT_PLACEMENT_DAMPING,
                stiffness = DYNAMIC_COMMENT_PLACEMENT_STIFFNESS,
            ),
            fadeOutSpec = tween(durationMillis = DYNAMIC_COMMENT_FADE_OUT_MILLIS),
        )
    } else {
        Modifier
    }

/**
 * 骨架「稳定槽位」：拆成多个独立 lazy item，而不是以前那种「1 个 item 里塞 N 行」。
 *
 * 骨架按独立条目测量，加载结束立即撤掉全部占位绘制，不保留淡出图层。
 */
internal fun LazyListScope.dynamicCommentSkeletonItems(
    slotCount: Int,
    keyPrefix: String,
) {
    val slots = List(slotCount.coerceAtLeast(1)) { it }
    items(
        items = slots,
        key = { "$keyPrefix$it" },
        contentType = { "dynamic_comment_skeleton" },
    ) { slotIndex ->
        CommentListItemSkeleton(
            // 不给临时骨架保留离场图层；真实评论仍保留原有进场和位移动画。
            modifier = Modifier,
            slotIndex = slotIndex,
        )
    }
}

fun LazyListScope.dynamicInlineCommentItems(
    comments: List<ReplyItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onViewReplies: (ReplyItem, Long) -> Unit,
    onReply: (ReplyItem) -> Unit = {},
    onLike: (ReplyItem) -> Unit = {},
    onHate: (ReplyItem) -> Unit = {},
    dynamicAuthorMid: Long = 0L,
    currentUserMid: Long? = null,
    onDelete: (ReplyItem) -> Unit = {},
    onToggleTop: (ReplyItem) -> Unit = {},
    onReport: (ReplyItem, Int) -> Unit = { _, _ -> },
    onUserClick: (Long) -> Unit,
    onUrlClick: ((String) -> Unit)? = null,
    onImagePreview: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit,
    avatarListState: LazyListState? = null,
    skeletonSlotCount: Int = DYNAMIC_COMMENT_SKELETON_MIN_SLOTS,
) {
    when {
        isLoading && comments.isEmpty() -> dynamicCommentSkeletonItems(
            slotCount = skeletonSlotCount,
            keyPrefix = "dynamic_inline_comment_skeleton_",
        )

        comments.isEmpty() -> item(key = "dynamic_inline_comment_empty") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacingTokens.DoubleExtraLarge),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    resolveDynamicCommentEmptyLabel(),
                    color = AppContentColors.secondaryText()
                )
            }
        }

        else -> items(
            items = comments,
            key = { it.rpid },
            contentType = { DYNAMIC_COMMENT_ITEM_CONTENT_TYPE },
        ) { reply ->
            val actionCapabilities = resolveDynamicCommentActionCapabilities(
                reply, dynamicAuthorMid, currentUserMid,
            )
            ReplyItemView(
                modifier = dynamicCommentItemModifier(),
                avatarListState = avatarListState,
                item = reply,
                upMid = dynamicAuthorMid,
                onClick = { onViewReplies(reply, 0L) },
                onSubClick = onViewReplies,
                onReplyClick = { onReply(reply) },
                onLikeClick = { onLike(reply) },
                isLiked = isDynamicCommentLiked(reply),
                onHateClick = { onHate(reply) },
                isHated = reply.action == 2,
                onDeleteClick = if (actionCapabilities.canDelete) ({ onDelete(reply) }) else null,
                onReportClick = { reason -> onReport(reply, reason) },
                canToggleTop = actionCapabilities.canToggleTop,
                onToggleTopClick = { onToggleTop(reply) },
                onUrlClick = onUrlClick,
                onAvatarClick = { mid -> mid.toLongOrNull()?.let(onUserClick) },
                onImagePreview = onImagePreview,
            )
        }
    }
    if (isLoadingMore) {
        item(key = "dynamic_inline_comment_loading_more") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacingTokens.Medium),
                contentAlignment = Alignment.Center,
            ) {
                AdaptiveLoadingIndicator(size = AppSpacingTokens.ExtraLarge)
            }
        }
    }
}
