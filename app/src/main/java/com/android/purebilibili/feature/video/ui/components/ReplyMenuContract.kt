package com.android.purebilibili.feature.video.ui.components

import androidx.compose.runtime.Immutable
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.comment.CommentDetailViewModel
import com.android.purebilibili.feature.dynamic.DynamicViewModel
import com.android.purebilibili.feature.video.screen.VideoDetailCommentActions
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel

@Immutable
data class ReplyMenuCallbacks(
    val onReport: (ReplyItem, Int) -> Unit,
    val onCheckFraud: (ReplyItem) -> Unit,
    val onToggleTop: (ReplyItem) -> Unit,
)

internal data class ResolvedReplyMenuBindings(
    val isPinned: Boolean,
    val onReportClick: ((Int) -> Unit)?,
    val onCheckFraudClick: (() -> Unit)?,
    val canToggleTop: Boolean,
    val onToggleTopClick: () -> Unit,
)

internal fun resolveReplyMenuBindings(
    item: ReplyItem,
    currentMid: Long,
    upMid: Long,
    pinnedReplyIds: Set<Long>,
    callbacks: ReplyMenuCallbacks,
): ResolvedReplyMenuBindings {
    val authorMid = resolveReplyMemberMid(item)
    return ResolvedReplyMenuBindings(
        isPinned = item.rpid in pinnedReplyIds,
        onReportClick = { reason -> callbacks.onReport(item, reason) },
        onCheckFraudClick = if (currentMid > 0L && authorMid == currentMid) {
            { callbacks.onCheckFraud(item) }
        } else null,
        canToggleTop = shouldShowReplyTopAction(
            currentMid = currentMid,
            upMid = upMid,
            item = item,
        ),
        onToggleTopClick = { callbacks.onToggleTop(item) },
    )
}

internal fun VideoCommentViewModel.replyMenuCallbacks(): ReplyMenuCallbacks {
    return ReplyMenuCallbacks(
        onReport = { reply, reason -> reportComment(reply.rpid, reason) },
        onCheckFraud = ::checkCommentFraud,
        onToggleTop = ::toggleTopComment,
    )
}

internal fun VideoDetailCommentActions.replyMenuCallbacks(): ReplyMenuCallbacks {
    return ReplyMenuCallbacks(
        onReport = { reply, reason -> reportComment(reply.rpid, reason) },
        onCheckFraud = checkCommentFraud,
        onToggleTop = toggleTopComment,
    )
}

internal fun DynamicViewModel.replyMenuCallbacks(onMessage: (String) -> Unit): ReplyMenuCallbacks {
    return ReplyMenuCallbacks(
        onReport = { reply, reason -> reportDynamicComment(reply.rpid, reason) { _, msg -> onMessage(msg) } },
        onCheckFraud = { reply -> checkDynamicCommentFraud(reply, onMessage) },
        onToggleTop = { reply -> toggleDynamicCommentTop(reply) { _, msg -> onMessage(msg) } },
    )
}

internal fun CommentDetailViewModel.replyMenuCallbacks(onMessage: (String) -> Unit): ReplyMenuCallbacks {
    return ReplyMenuCallbacks(
        onReport = { reply, reason -> reportComment(reply.rpid, reason) { _, msg -> onMessage(msg) } },
        onCheckFraud = ::checkCommentFraud,
        onToggleTop = { reply -> toggleTopComment(reply) { _, msg -> onMessage(msg) } },
    )
}
