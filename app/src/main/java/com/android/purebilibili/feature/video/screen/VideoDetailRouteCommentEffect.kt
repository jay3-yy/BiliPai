package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState

/** Comment list/loading updates restart this effect scope, not the detail transition renderer. */
@Composable
internal fun VideoDetailRouteCommentEffect(
    rootReplyId: Long,
    targetReplyId: Long,
    handled: Boolean,
    commentState: State<CommentUiState>,
    subReplyState: State<SubReplyUiState>,
    onOpenReply: (ReplyItem, Long) -> Unit,
    onOpenReplyFromRoute: (Long, Long) -> Boolean,
    onHandled: () -> Unit,
) {
    if ((rootReplyId <= 0L && targetReplyId <= 0L) || handled) return
    val comments by commentState
    val threadVisible by remember(subReplyState) {
        derivedStateOf { subReplyState.value.visible }
    }
    val openReply by rememberUpdatedState(onOpenReply)
    val openFromRoute by rememberUpdatedState(onOpenReplyFromRoute)
    val markHandled by rememberUpdatedState(onHandled)
    // Keep the list as a key: a failed fallback may become possible when comments arrive.
    LaunchedEffect(rootReplyId, targetReplyId, comments.replies, comments.isRepliesLoading, threadVisible) {
        if (threadVisible) return@LaunchedEffect
        val rootReply = comments.replies.firstOrNull { it.rpid == rootReplyId }
        if (rootReply != null) {
            openReply(rootReply, targetReplyId)
            markHandled()
        } else if (!comments.isRepliesLoading && openFromRoute(rootReplyId, targetReplyId)) {
            markHandled()
        }
    }
}
