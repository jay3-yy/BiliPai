// 文件路径: feature/dynamic/components/DynamicCommentSheet.kt
package com.android.purebilibili.feature.dynamic.components
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.skeleton.CommentListColumnSkeleton

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.android.purebilibili.core.ui.transition.resolvePredictiveBackBlurFrame
import com.android.purebilibili.core.ui.transition.BlurRenderEffectCache
import com.android.purebilibili.feature.video.ui.components.resolveCommentThreadCoveredBlurProgress
import com.android.purebilibili.feature.video.ui.components.resolveCommentThreadPredictiveBackOffsetY
import com.android.purebilibili.feature.video.ui.components.rememberCommentThreadDrag
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.resolveUpNameColor
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.DynamicViewModel
import com.android.purebilibili.feature.dynamic.DynamicStatusPalette
import com.android.purebilibili.feature.dynamic.canOpenDynamicSubReplies
import com.android.purebilibili.feature.dynamic.isDynamicCommentLiked
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentComposerHint
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentCountLabel
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentEmptyLabel
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentLocationLabel
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentImeSubmission
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentSheetTotalCount
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentSheetHostContent
import com.android.purebilibili.feature.dynamic.resolveDynamicSubReplyCount
import com.android.purebilibili.feature.dynamic.shouldOpenDynamicCommentThreadOnTap
import com.android.purebilibili.feature.dynamic.DynamicCommentSheetHostContent
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import com.android.purebilibili.feature.video.ui.components.CommentPictures
import com.android.purebilibili.feature.video.ui.components.RichCommentText
import com.android.purebilibili.feature.video.ui.components.ReplyMemberAvatar
import com.android.purebilibili.feature.video.ui.components.ReplyItemView
import com.android.purebilibili.feature.video.ui.components.VideoCommentTypographyTokens
import com.android.purebilibili.feature.video.ui.components.FanGroupDecorationBadge
import com.android.purebilibili.feature.video.ui.components.resolveFanGroupDecorationCardBgs
import com.android.purebilibili.feature.video.ui.components.resolveFanGroupVisualFromMemberAndSailing
import com.android.purebilibili.feature.video.ui.components.resolveInlineSubReplyToggleLabel
import com.android.purebilibili.feature.video.ui.components.resolveReplyItemLayoutPolicy
import com.android.purebilibili.feature.video.ui.components.resolveReplyPreviewTextContent
import com.android.purebilibili.feature.video.ui.components.resolveVisibleSubReplies
import com.android.purebilibili.feature.video.ui.components.shouldShowInlineSubReplyToggle
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppLikeFilledIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.store.TokenManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Image
import coil3.compose.AsyncImage
import com.android.purebilibili.core.util.PickMultipleGalleryVisualMedia
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.ui.LocalDetailedCommentTimeEnabled
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.android.purebilibili.core.ui.CommentWindowNavigation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun DynamicCommentOverlayHost(
    viewModel: DynamicViewModel,
    primaryItems: List<DynamicItem>,
    secondaryItems: List<DynamicItem> = emptyList(),
    toastContext: Context,
    onUserClick: (Long) -> Unit,
) {
    val selectedDynamicId by viewModel.selectedDynamicId.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentsLoading by viewModel.commentsLoading.collectAsStateWithLifecycle()
    val commentsRefreshing by viewModel.commentsRefreshing.collectAsStateWithLifecycle()
    val commentsRefreshError by viewModel.commentsRefreshError.collectAsStateWithLifecycle()
    val commentsLoadingMore by viewModel.commentsLoadingMore.collectAsStateWithLifecycle()
    val subReplyState by viewModel.subReplyState.collectAsStateWithLifecycle()
    val liveCommentCount by viewModel.commentTotalCount.collectAsStateWithLifecycle()
    val sortMode by viewModel.dynamicCommentSortMode.collectAsStateWithLifecycle()
    val replyTarget by viewModel.commentReplyTarget.collectAsStateWithLifecycle()
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
            onDismiss = { viewModel.closeCommentSheet() },
            onRefresh = viewModel::refreshComments,
            onSortModeChange = { viewModel.setDynamicCommentSortMode(it) },
            onPostComment = { message, images, onResult ->
                viewModel.postComment(dynamicId, message, images) { success, msg ->
                    if (!inspectionMode) {
                        android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onResult(success)
                }
            },
            onViewReplies = { reply -> viewModel.openSubReply(reply) },
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
            replyTargetUname = replyTarget?.uname,
            onClearReplyTarget = { viewModel.clearCommentReplyTarget() },
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
    onPostComment: (String, List<Uri>, (Boolean) -> Unit) -> Unit,
    onViewReplies: (ReplyItem) -> Unit = {},
    onReply: (ReplyItem) -> Unit = {},
    onLike: (ReplyItem) -> Unit = {},
    onHate: (ReplyItem) -> Unit = {},
    dynamicAuthorMid: Long = 0L,
    currentUserMid: Long? = null,
    onDelete: (ReplyItem) -> Unit = {},
    onToggleTop: (ReplyItem) -> Unit = {},
    onReport: (ReplyItem, Int) -> Unit = { _, _ -> },
    replyTargetUname: String? = null,
    onClearReplyTarget: () -> Unit = {},
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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(replyTargetUname) {
        if (!replyTargetUname.isNullOrBlank()) {
            delay(50L)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }
    val canLoadMore = comments.size < totalCount && !isLoading && !isRefreshing && !hasRefreshError && !isLoadingMore
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

    LaunchedEffect(listState, comments.size, totalCount, isLoading, isRefreshing, hasRefreshError, isLoadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { lastVisibleIndex ->
                val itemCount = listState.layoutInfo.totalItemsCount
                itemCount > 0 && lastVisibleIndex >= itemCount - 4
            }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (canLoadMore) onLoadMore()
            }
    }
    val emoteCatalogSessionKey = DynamicEmoteCatalog.currentSessionKey()
    var emoteMap by remember(emoteCatalogSessionKey) {
        mutableStateOf(DynamicEmoteCatalog.snapshot())
    }
    LaunchedEffect(emoteCatalogSessionKey) {
        emoteMap = DynamicEmoteCatalog.ensureLoaded()
    }
    val hostContent = resolveDynamicCommentSheetHostContent(subReplyState.visible)
    val likedThreadComments = remember(subReplyState) {
        buildSet {
            subReplyState.rootReply?.takeIf { isDynamicCommentLiked(it) }?.let { add(it.rpid) }
            subReplyState.items.forEach { reply ->
                if (isDynamicCommentLiked(reply)) add(reply.rpid)
            }
        }
    }
    
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dismissOnBackPress = false,
        dragHandle = null
    ) {
        CommentWindowNavigation {
            val commentBackState = rememberNavigationEventState(NavigationEventInfo.None)
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
                rootReplyId = subReplyState.rootReply?.rpid,
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
                                isLoading && comments.isEmpty() -> item(
                                    key = "dynamic_comment_initial_skeleton",
                                    contentType = "dynamic_comment_initial_skeleton",
                                ) {
                                    Box(modifier = Modifier.fillParentMaxSize()) {
                                        CommentListColumnSkeleton(itemCount = 6)
                                    }
                                }

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
                                                    tint = MaterialTheme.colorScheme.primary,
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
                                    items(comments, key = { it.rpid }) { reply ->
                                        ReplyItemView(
                                            item = reply,
                                            onClick = { onViewReplies(reply) },
                                            onSubClick = { root, _ -> onViewReplies(root) },
                                            onReplyClick = { onReply(reply) },
                                            onLikeClick = { onLike(reply) },
                                            isLiked = isDynamicCommentLiked(reply),
                                            onHateClick = { onHate(reply) },
                                            isHated = reply.action == 2,
                                            onDeleteClick = { onDelete(reply) },
                                            onReportClick = { reason -> onReport(reply, reason) },
                                            canToggleTop = dynamicAuthorMid > 0L,
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
                    visible = hostContent == DynamicCommentSheetHostContent.THREAD_DETAIL &&
                        subReplyState.rootReply != null,
                    enter = fadeIn(animationSpec = tween(220)) +
                        slideInVertically(animationSpec = tween(260)) { height -> height },
                    exit = if (threadBackCompleted) androidx.compose.animation.ExitTransition.None else fadeOut(animationSpec = tween(200)) +
                        slideOutVertically(animationSpec = tween(240)) { height -> height },
                ) {
                    val rootReply = subReplyState.rootReply
                    if (rootReply != null) {
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
                                subReplies = subReplyState.items,
                                sortMode = subReplyState.sortMode,
                                error = subReplyState.error,
                                onSortModeChange = onSubReplySortModeChange,
                                remoteReplyCount = subReplyState.totalCount,
                                isLoading = subReplyState.isLoading,
                                isRefreshing = subReplyState.isRefreshing,
                                isEnd = subReplyState.isEnd,
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
                                onAvatarClick = { mid -> mid.toLongOrNull()?.let(onUserClick) },
                                targetReplyId = subReplyState.targetReplyId,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                }

                DynamicCommentComposer(
                    value = commentText,
                    onValueChange = { commentText = it },
                    onSubmit = { message, images, onResult ->
                        onPostComment(message, images) { success ->
                            if (success) {
                                commentText = ""
                                if (!replyTargetUname.isNullOrBlank()) onClearReplyTarget()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            onResult(success)
                        }
                    },
                    hint = resolveDynamicCommentComposerHint(replyTargetUname),
                    onClearReplyTarget = if (replyTargetUname.isNullOrBlank()) null else onClearReplyTarget,
                    focusRequester = focusRequester,
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
    val spec = remember(items.size) {
        resolveDynamicCommentSortControlSpec(itemCount = items.size)
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

internal fun resolveDynamicCommentSortControlSpec(itemCount: Int) = DynamicCommentSortControlSpec(
    // Keep the beta.36 dynamic comment layout: two-option sorting tabs are 66dp each.
    itemWidthDp = if (itemCount >= 4) 56 else 66,
    heightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp,
    indicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp,
)

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium),
        verticalAlignment = Alignment.CenterVertically,
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onShowReactions)
                        .padding(vertical = 6.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            DynamicCommentSortControl(
                items = sortModeLabels,
                selectedIndex = sortModes.indexOf(sortMode).coerceAtLeast(0),
                onSelected = { index ->
                    sortModes.getOrNull(index)?.let(onSortModeChange)
                },
                modifier = Modifier.align(Alignment.CenterEnd),
                // Null deliberately selects the shared control's mounted local source. Do not
                // manufacture an unrecorded Backdrop here or sample the LazyColumn containing
                // this header, which would be invalid/recursive on Xiaomi's native renderer.
                miuixBackdrop = miuixBackdrop,
            )
        }
    }
    AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

fun LazyListScope.dynamicInlineCommentItems(
    comments: List<ReplyItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onViewReplies: (ReplyItem) -> Unit,
    onReply: (ReplyItem) -> Unit = {},
    onLike: (ReplyItem) -> Unit = {},
    onHate: (ReplyItem) -> Unit = {},
    dynamicAuthorMid: Long = 0L,
    currentUserMid: Long? = null,
    onDelete: (ReplyItem) -> Unit = {},
    onToggleTop: (ReplyItem) -> Unit = {},
    onReport: (ReplyItem, Int) -> Unit = { _, _ -> },
    onUserClick: (Long) -> Unit,
    onImagePreview: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit,
) {
    when {
        isLoading && comments.isEmpty() -> item(key = "dynamic_inline_comment_skeleton") {
            CommentListColumnSkeleton(itemCount = 4)
        }

        comments.isEmpty() -> item(key = "dynamic_inline_comment_empty") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacingTokens.DoubleExtraLarge),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    resolveDynamicCommentEmptyLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        else -> items(comments, key = { it.rpid }) { reply ->
            ReplyItemView(
                item = reply,
                onClick = { onViewReplies(reply) },
                onSubClick = { root, _ -> onViewReplies(root) },
                onReplyClick = { onReply(reply) },
                onLikeClick = { onLike(reply) },
                isLiked = isDynamicCommentLiked(reply),
                onHateClick = { onHate(reply) },
                isHated = reply.action == 2,
                onDeleteClick = { onDelete(reply) },
                onReportClick = { reason -> onReport(reply, reason) },
                canToggleTop = dynamicAuthorMid > 0L,
                onToggleTopClick = { onToggleTop(reply) },
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

@Composable
fun DynamicInlineCommentComposer(
    onPostComment: (String, List<Uri>, (Boolean) -> Unit) -> Unit,
    replyTargetUname: String? = null,
    onClearReplyTarget: () -> Unit = {},
    liquidGlassEnabled: Boolean = false,
    backdrop: MiuixBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    var commentText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(replyTargetUname) {
        if (!replyTargetUname.isNullOrBlank()) {
            delay(50L)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    DynamicCommentComposer(
        value = commentText,
        onValueChange = { commentText = it },
        onSubmit = { message, images, onResult ->
            onPostComment(message, images) { success ->
                if (success) {
                    commentText = ""
                    if (!replyTargetUname.isNullOrBlank()) onClearReplyTarget()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                onResult(success)
            }
        },
        hint = resolveDynamicCommentComposerHint(replyTargetUname),
        onClearReplyTarget = if (replyTargetUname.isNullOrBlank()) null else onClearReplyTarget,
        liquidGlassEnabled = liquidGlassEnabled,
        backdrop = backdrop,
        focusRequester = focusRequester,
        modifier = modifier,
    )
}

@Composable
private fun DynamicCommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String, List<Uri>, (Boolean) -> Unit) -> Unit,
    hint: String = resolveDynamicCommentComposerHint(),
    onClearReplyTarget: (() -> Unit)? = null,
    liquidGlassEnabled: Boolean = false,
    backdrop: MiuixBackdrop? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickMultipleGalleryVisualMedia(maxItems = 9)) { uris ->
        if (!isSending && onClearReplyTarget == null) {
            selectedImages = (selectedImages + uris).distinct().take(9)
        }
    }
    LaunchedEffect(onClearReplyTarget != null) {
        if (onClearReplyTarget != null) selectedImages = emptyList()
    }
    val canSend = !isSending && (value.isNotBlank() || selectedImages.isNotEmpty())
    fun submit() {
        if (!canSend) return
        isSending = true
        onSubmit(value.trim(), selectedImages) { success ->
            if (success) selectedImages = emptyList()
            isSending = false
        }
    }
    val useMiuixNonGlassInput = isMiuixNonGlassEnabled()
    val dockShape = resolveSharedBottomBarCapsuleShape()
    val composerHeight = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small
    val composerLensIntensity = resolveFloatingDockGeometryScale(composerHeight.value)
    Column(
        modifier = modifier,
    ) {
        if (selectedImages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpacingTokens.Small),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                items(selectedImages, key = { it.toString() }) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = "已选图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(AppShapes.container(ContainerLevel.Chip)),
                        )
                        AppIconButton(
                            onClick = { selectedImages = selectedImages - uri },
                            enabled = !isSending,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(AppChromeSizeTokens.MinimumTouchTarget),
                        ) {
                            AppIcon(rememberAppClearIcon(), contentDescription = "移除图片")
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val commentFieldContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            BottomBarMatchedReusableLiquidDock(
                shape = dockShape,
                modifier = Modifier
                    .weight(1f)
                    .height(composerHeight)
                    .then(
                        if (!liquidGlassEnabled) {
                            Modifier
                                .clip(dockShape)
                                .background(commentFieldContainerColor)
                        } else {
                            Modifier
                        }
                    ),
                reuseEnabled = liquidGlassEnabled,
                backdrop = backdrop,
                drawShellLens = true,
                shellLensIntensity = composerLensIntensity,
            ) { liquidChromeActive ->
                val fieldColor = if (liquidChromeActive) Color.Transparent else commentFieldContainerColor
                val fieldTextColor = MaterialTheme.colorScheme.onSurface
                val placeholderColor = if (liquidChromeActive) {
                    fieldTextColor.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                val keyboardActions = KeyboardActions(
                    onSend = {
                        if (resolveDynamicCommentImeSubmission(value) != null || selectedImages.isNotEmpty()) submit()
                    },
                )
                if (useMiuixNonGlassInput) {
                    AppOutlinedTextField(
                        value = value,
                        onValueChange = { if (!isSending) onValueChange(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                        placeholderText = hint,
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        shape = dockShape,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = fieldTextColor
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = fieldColor,
                            unfocusedContainerColor = fieldColor,
                            disabledContainerColor = fieldColor,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedTextColor = fieldTextColor,
                            unfocusedTextColor = fieldTextColor,
                            disabledTextColor = fieldTextColor.copy(alpha = 0.72f),
                            focusedPlaceholderColor = placeholderColor,
                            unfocusedPlaceholderColor = placeholderColor,
                            disabledPlaceholderColor = placeholderColor,
                            cursorColor = fieldTextColor,
                        ),
                    )
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { if (!isSending) onValueChange(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                        placeholder = {
                            AppText(
                                text = hint,
                                color = placeholderColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        shape = dockShape,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = fieldTextColor
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = fieldColor,
                            unfocusedContainerColor = fieldColor,
                            disabledContainerColor = fieldColor,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedTextColor = fieldTextColor,
                            unfocusedTextColor = fieldTextColor,
                            disabledTextColor = fieldTextColor.copy(alpha = 0.72f),
                            focusedPlaceholderColor = placeholderColor,
                            unfocusedPlaceholderColor = placeholderColor,
                            disabledPlaceholderColor = placeholderColor,
                            cursorColor = fieldTextColor,
                        ),
                    )
                }
            }
            if (onClearReplyTarget == null) {
                AppIconButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !isSending && selectedImages.size < 9,
                    modifier = Modifier.size(AppChromeSizeTokens.MinimumTouchTarget),
                ) {
                    AppIcon(
                        Icons.Outlined.Image,
                        contentDescription = "添加图片，已选 ${selectedImages.size}/9 张",
                    )
                }
            }
            AppTextButton(
                onClick = ::submit,
                enabled = canSend,
                modifier = Modifier.heightIn(min = AppChromeSizeTokens.MinimumTouchTarget),
            ) { AppText("发送") }
            if (onClearReplyTarget != null) {
                AppIconButton(onClick = onClearReplyTarget, enabled = !isSending) {
                    AppIcon(
                        rememberAppClearIcon(),
                        contentDescription = "取消回复",
                        modifier = Modifier.size(AppSpacingTokens.Large)
                    )
                }
            }
        }
    }
}

/**
 *  单条评论项
 */
@Composable
private fun CommentItem(
    reply: ReplyItem,
    onViewReplies: (ReplyItem) -> Unit,
    onReply: (ReplyItem) -> Unit = {},
    onLike: (ReplyItem) -> Unit = {},
    dynamicAuthorMid: Long = 0L,
    currentUserMid: Long? = null,
    onDelete: (ReplyItem) -> Unit = {},
    onToggleTop: (ReplyItem) -> Unit = {},
    onReport: (ReplyItem, Int) -> Unit = { _, _ -> },
    onUserClick: (Long) -> Unit,
    onImagePreview: (List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit,
    subReplyState: SubReplyUiState = SubReplyUiState(),
    modifier: Modifier = Modifier,
) {
    val detailedCommentTimeEnabled = LocalDetailedCommentTimeEnabled.current
    val queryAicu = com.android.purebilibili.feature.aicu.LocalAicuNavigation.current
    val member = reply.member
    val actionCapabilities = remember(reply, dynamicAuthorMid, currentUserMid) {
        resolveDynamicCommentActionCapabilities(reply, dynamicAuthorMid, currentUserMid)
    }
    var showActions by remember(reply.rpid) { mutableStateOf(false) }
    var showReportReasons by remember(reply.rpid) { mutableStateOf(false) }
    var confirmDelete by remember(reply.rpid) { mutableStateOf(false) }
    if (confirmDelete) {
        AppAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { AppText("删除评论") },
            text = { AppText("删除后无法恢复，确定删除这条评论吗？") },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        confirmDelete = false
                        onDelete(reply)
                    },
                ) { AppText("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                AppDialogAction(onClick = { confirmDelete = false }) { AppText("取消") }
            },
        )
    }
    var isSubPreviewExpanded by remember(reply.rpid) { mutableStateOf(false) }
    val loadedSubReplies = if (subReplyState.visible && subReplyState.rootReply?.rpid == reply.rpid) {
        subReplyState.items
    } else {
        reply.replies.orEmpty()
    }
    val visibleSubReplies = remember(loadedSubReplies, isSubPreviewExpanded) {
        resolveVisibleSubReplies(
            replies = loadedSubReplies,
            expanded = isSubPreviewExpanded
        )
    }
    val showInlineToggle = remember(loadedSubReplies) {
        shouldShowInlineSubReplyToggle(loadedSubReplies.size)
    }
    val fanGroupVisual = remember(member) {
        resolveFanGroupVisualFromMemberAndSailing(
            member = member,
            cardBgs = resolveFanGroupDecorationCardBgs(member)
        )
    }
    // 收藏集装饰必须 TopEnd 叠层；内联在名字行会在右侧留下空洞。
    val decorationEndReserve = if (fanGroupVisual != null) {
        resolveReplyItemLayoutPolicy().decorationMinWidthDp.dp
    } else {
        AppSpacingTokens.None
    }

    val locationLabel = remember(reply.replyControl?.location) {
        resolveDynamicCommentLocationLabel(reply.replyControl?.location)
    }
    val liked = isDynamicCommentLiked(reply)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (shouldOpenDynamicCommentThreadOnTap(reply)) {
                    Modifier.clickable { onViewReplies(reply) }
                } else {
                    Modifier
                }
            )
    ) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val memberMid = remember(member.mid, reply.mid) {
            member.mid.toLongOrNull()?.takeIf { it > 0L }
                ?: reply.mid.takeIf { it > 0L }
        }
        ReplyMemberAvatar(
            member = member,
            placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
            lightweightMode = false,
            modifier = Modifier.size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall),
            onClick = memberMid?.let { mid -> { onUserClick(mid) } }
        )
        
        Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = decorationEndReserve)
        ) {
            // 用户名 + 时间（装饰独立右置顶，不占用此行）
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = member.uname,
                    fontSize = VideoCommentTypographyTokens.author,
                    fontWeight = FontWeight.Medium,
                    color = resolveUpNameColor(
                        vipStatus = member.vip?.vipStatus ?: 0,
                        vipType = member.vip?.vipType ?: 0,
                        onSurface = MaterialTheme.colorScheme.onSurface,
                        secondary = MaterialTheme.colorScheme.secondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (memberMid != null) {
                                Modifier.clickable { onUserClick(memberMid) }
                            } else {
                                Modifier
                            }
                        )
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                AppText(
                    text = FormatUtils.formatCommentTime(
                        timestampSeconds = reply.ctime,
                        detailedTimeEnabled = detailedCommentTimeEnabled
                    ),
                    fontSize = VideoCommentTypographyTokens.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
                if (locationLabel != null) {
                    AppText(
                        text = " • $locationLabel",
                        fontSize = VideoCommentTypographyTokens.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (
                    actionCapabilities.canDelete ||
                    actionCapabilities.canReport ||
                    actionCapabilities.canToggleTop
                ) {
                    Box {
                        AppIconButton(onClick = { showActions = true }) {
                            AppIcon(
                                rememberAppMoreIcon(),
                                contentDescription = "评论操作",
                                modifier = Modifier.size(AppSpacingTokens.Large),
                            )
                        }
                        AppDropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false },
                        ) {
                            val authorUid = member.mid.toLongOrNull()?.takeIf { it > 0 } ?: reply.mid
                            if (queryAicu != null && authorUid > 0) {
                                AppDropdownMenuItem(
                                    text = { AppText("查询作者历史") },
                                    onClick = { showActions = false; queryAicu(authorUid) },
                                )
                            }
                            if (actionCapabilities.canToggleTop) {
                                AppDropdownMenuItem(
                                    text = {
                                        AppText(if (actionCapabilities.isCurrentlyTop) "取消置顶" else "置顶评论")
                                    },
                                    onClick = {
                                        showActions = false
                                        onToggleTop(reply)
                                    },
                                )
                            }
                            if (actionCapabilities.canReport) {
                                AppDropdownMenuItem(
                                    text = { AppText("举报评论") },
                                    onClick = {
                                        showActions = false
                                        showReportReasons = true
                                    },
                                )
                            }
                            if (actionCapabilities.canDelete) {
                                AppDropdownMenuItem(
                                    text = { AppText("删除评论", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showActions = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                        AppDropdownMenu(
                            expanded = showReportReasons,
                            onDismissRequest = { showReportReasons = false },
                        ) {
                            resolveDynamicCommentReportReasons().forEach { reason ->
                                AppDropdownMenuItem(
                                    text = { AppText(reason.label) },
                                    onClick = {
                                        showReportReasons = false
                                        onReport(reply, reason.type)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
            
            // 评论内容 - 使用 RichCommentText 渲染表情
            val emoteMap = remember(reply.content.emote) {
                reply.content.emote?.mapValues { it.value.url } ?: emptyMap()
            }
            RichCommentText(
                text = reply.content.message,
                fontSize = VideoCommentTypographyTokens.body,
                color = MaterialTheme.colorScheme.onSurface,
                emoteMap = emoteMap,
                content = reply.content,
                onUserClick = onUserClick,
            )

            // 评论图片
            if (!reply.content.pictures.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                CommentPictures(
                    pictures = reply.content.pictures.orEmpty(),
                    onImageClick = { images, index, rect ->
                        onImagePreview(
                            images,
                            index,
                            rect,
                            resolveReplyPreviewTextContent(
                                item = reply,
                                detailedTimeEnabled = detailedCommentTimeEnabled
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onReply(reply) }
                ) {
                    AppIcon(
                        Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = "回复",
                        modifier = Modifier.size(AppSpacingTokens.Large),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = "回复",
                        fontSize = VideoCommentTypographyTokens.action,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLike(reply) }
                ) {
                    AppIcon(
                        if (liked) rememberAppLikeFilledIcon() else rememberAppLikeIcon(),
                        contentDescription = if (liked) "已赞" else "点赞",
                        modifier = Modifier.size(AppSpacingTokens.Medium + AppSpacingTokens.Micro),
                        tint = if (liked) {
                            DynamicStatusPalette.liked()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        }
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = "${reply.like}",
                        fontSize = VideoCommentTypographyTokens.actionCount,
                        color = if (liked) {
                            DynamicStatusPalette.liked()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        }
                    )
                }
            }

            if (canOpenDynamicSubReplies(reply) || loadedSubReplies.isNotEmpty()) {
                if (visibleSubReplies.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Small + AppSpacingTokens.Micro))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.container(ContainerLevel.Dialog))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .padding(horizontal = AppSpacingTokens.Small + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Small),
                        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
                    ) {
                        visibleSubReplies.forEach { subReply ->
                            val subEmoteMap = remember(subReply.content.emote) {
                                subReply.content.emote?.mapValues { it.value.url } ?: emptyMap()
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                                ) {
                                    AppText(
                                        text = "${subReply.member.uname}:",
                                        fontSize = VideoCommentTypographyTokens.subReply,
                                        fontWeight = FontWeight.SemiBold,
                                        color = resolveUpNameColor(
                                            vipStatus = subReply.member.vip?.vipStatus ?: 0,
                                            vipType = subReply.member.vip?.vipType ?: 0,
                                            onSurface = MaterialTheme.colorScheme.onSurface,
                                            secondary = MaterialTheme.colorScheme.secondary,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        RichCommentText(
                                            text = subReply.content.message,
                                            fontSize = VideoCommentTypographyTokens.subReply,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            emoteMap = subEmoteMap,
                                            content = subReply.content,
                                            maxLines = 2,
                                            onUserClick = onUserClick,
                                        )
                                    }
                                }
                                if (!subReply.content.pictures.isNullOrEmpty()) {
                                    CommentPictures(
                                        pictures = subReply.content.pictures.orEmpty(),
                                        onImageClick = { images, index, rect ->
                                            onImagePreview(
                                                images,
                                                index,
                                                rect,
                                                resolveReplyPreviewTextContent(
                                                    item = subReply,
                                                    detailedTimeEnabled = detailedCommentTimeEnabled
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (showInlineToggle) {
                            AppText(
                                text = resolveInlineSubReplyToggleLabel(expanded = isSubPreviewExpanded),
                                fontSize = VideoCommentTypographyTokens.subReply,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { isSubPreviewExpanded = !isSubPreviewExpanded }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                AppTextButton(
                    onClick = { onViewReplies(reply) },
                    contentPadding = PaddingValues(horizontal = AppSpacingTokens.None, vertical = AppSpacingTokens.None)
                ) {
                    AppText(
                        text = if (subReplyState.isLoading) {
                            "加载回复中…"
                        } else {
                            "查看回复(${maxOf(resolveDynamicSubReplyCount(reply), subReplyState.totalCount)})"
                        },
                        fontSize = VideoCommentTypographyTokens.subReply,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

        if (fanGroupVisual != null) {
            FanGroupDecorationBadge(
                visual = fanGroupVisual,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = AppSpacingTokens.None)
            )
        }
    }
}
