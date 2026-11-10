package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.core.ui.CommentWindowNavigation
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubReplySheet(
    state: SubReplyUiState,
    showUpFlag: Boolean = false,
    emoteMap: Map<String, String>,
    onDismiss: () -> Unit,
    onRootCommentClick: (() -> Unit)? = null,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (SubReplySortMode) -> Unit,
    maxHeightFraction: Float = 1f,
    scrimAlpha: Float = 0.32f,
    onTimestampClick: ((Long) -> Unit)? = null,
    onImagePreview: ((List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit)? = null,
    onReplyClick: ((ReplyItem) -> Unit)? = null,
    onConversationClick: ((ReplyItem) -> Unit)? = null,
    onConversationBack: (() -> Unit)? = null,
    // [新增] 删除评论相关
    currentMid: Long = 0,
    onDissolveStart: ((Long) -> Unit)? = null,
    onDeleteComment: ((Long) -> Unit)? = null,
    // [新增] 点赞
    onCommentLike: ((Long) -> Unit)? = null,
    likedComments: Set<Long> = emptySet(),
    onCommentHate: ((Long) -> Unit)? = null,
    hatedComments: Set<Long> = emptySet(),
    onUrlClick: ((String) -> Unit)? = null,
    showIdentityDecorations: Boolean = true,
    onAvatarClick: ((String) -> Unit)? = null,
    onCoveredBlurProgressChange: ((Float) -> Unit)? = null,
) {
    if (state.visible) {
        val rootReply = state.rootReply
        val backState = rememberNavigationEventState(NavigationEventInfo.None)
        val transition = backState.transitionState as? NavigationEventTransitionState.InProgress
        val backProgress by animateFloatAsState(
            targetValue = transition?.latestEvent?.progress ?: 0f,
            animationSpec = if (transition != null) snap() else tween(180),
            label = "standalone_comment_predictive_back",
        )
        val threadDrag = rememberCommentThreadDrag(
            visible = state.visible,
            rootReplyId = state.threadRootId.takeIf { it > 0L },
            onDismiss = onDismiss,
        )
        val coveredBlurProgress = resolveCommentThreadCoveredBlurProgress(
            maxOf(backProgress, threadDrag.revealProgress)
        )
        SideEffect {
            onCoveredBlurProgressChange?.invoke(coveredBlurProgress)
        }
        DisposableEffect(Unit) {
            onDispose {
                onCoveredBlurProgressChange?.invoke(0f)
            }
        }
        val panelColor = rememberVideoCommentAppearance().panelColor
        com.android.purebilibili.core.ui.AppModalBottomSheet(
            onDismissRequest = onDismiss,
            dismissOnBackPress = false,
            // 外壳与内容安全区使用同一实色，避免底部露出下层页面材质。
            containerColor = panelColor,
            // 底部安全区由评论面板统一绘制，保留共享弹层与原生材质默认行为。
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
            sheetSurfaceModifier = Modifier
                .background(panelColor)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            modifier = Modifier.fillMaxHeight(maxHeightFraction)
                .then(threadDrag.containerModifier)
                .graphicsLayer {
                    translationY = threadDrag.offsetPx.value +
                        resolveCommentThreadPredictiveBackOffsetY(backProgress, size.height)
                },
            scrimColor = Color.Black.copy(alpha = scrimAlpha * (1f - maxOf(backProgress, threadDrag.revealProgress)))
        ) {
            CommentWindowNavigation {
                NavigationBackHandler(
                    state = backState,
                    isBackEnabled = true,
                    onBackCompleted = onDismiss,
                )
                if (rootReply == null && state.items.isEmpty()) {
                    SubReplyInitialContent(
                        isLoading = state.isLoading,
                        error = state.error,
                        onRetry = onRefresh,
                        onDismiss = onDismiss,
                    )
                } else {
                SubReplyDetailContent(
                    headerDragModifier = threadDrag.headerModifier,
                    rootReply = rootReply,
                    subReplies = state.items,
                    sortMode = state.sortMode,
                    error = state.error,
                    onSortModeChange = onSortModeChange,
                    remoteReplyCount = state.totalCount,
                    isLoading = state.isLoading,
                    isRefreshing = state.isRefreshing,
                    isEnd = state.isEnd,
                    paginationPage = state.page,
                    emoteMap = emoteMap,
                    onLoadMore = onLoadMore,
                    onRefresh = onRefresh,
                    onDismiss = onDismiss,
                    onRootCommentClick = onRootCommentClick,
                    onTimestampClick = onTimestampClick,
                    upMid = state.upMid,
                    showUpFlag = showUpFlag,
                    onImagePreview = onImagePreview,
                    onReplyClick = onReplyClick,
                    onConversationClick = onConversationClick,
                    onConversationBack = onConversationBack,
                    isConversationMode = state.conversationAnchor != null,
                    conversationAnchorId = state.conversationAnchor?.rpid ?: 0L,
                    // [新增] 消散动画相关
                    dissolvingIds = state.dissolvingIds,
                    currentMid = currentMid,
                    onDissolveStart = onDissolveStart,
                    onDeleteComment = onDeleteComment,
                    onCommentLike = onCommentLike,
                    likedComments = likedComments,
                    onCommentHate = onCommentHate,
                    hatedComments = hatedComments,
                    onUrlClick = onUrlClick,
                    showIdentityDecorations = showIdentityDecorations,
                    onAvatarClick = onAvatarClick,
                    targetReplyId = state.targetReplyId
                )
                }
            }
        }
    }
}
