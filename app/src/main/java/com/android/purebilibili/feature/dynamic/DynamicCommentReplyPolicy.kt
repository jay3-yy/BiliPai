package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.video.viewmodel.resolveSentSubReply
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState

internal fun canOpenDynamicSubReplies(reply: ReplyItem): Boolean {
    return resolveDynamicSubReplyCount(reply) > 0
}

internal fun shouldOpenDynamicCommentThreadOnTap(reply: ReplyItem): Boolean {
    return reply.rpid > 0L
}

internal enum class DynamicCommentSheetHostContent {
    MAIN_LIST,
    THREAD_DETAIL,
}

internal fun resolveDynamicCommentSheetHostContent(
    subReplyVisible: Boolean,
): DynamicCommentSheetHostContent {
    return if (subReplyVisible) {
        DynamicCommentSheetHostContent.THREAD_DETAIL
    } else {
        DynamicCommentSheetHostContent.MAIN_LIST
    }
}

/** 信息流点评论后进入动态详情内联评论区，不弹底部评论层。 */
internal fun shouldOpenDynamicCommentsInDetailPage(): Boolean = true

internal fun resolveDynamicSubReplyCount(reply: ReplyItem): Int {
    return maxOf(
        reply.rcount,
        reply.replies.orEmpty().size
    )
}

internal fun resolveDynamicCommentCountLabel(count: Int): String {
    return "${formatDynamicCommentCount(count.coerceAtLeast(0))}条回复"
}

internal fun resolveDynamicCommentEmptyLabel(): String = "还没有评论"

internal const val DYNAMIC_COMMENT_ROOT_INPUT_HINT = "发一条友善的评论"

internal fun resolveDynamicCommentLocationLabel(location: String?): String? {
    return location?.trim()?.takeIf { it.isNotEmpty() }
}

internal data class DynamicCommentComposerTarget(
    val rootRpid: Long,
    val parentRpid: Long,
    val uname: String
)

internal fun resolveDynamicCommentReplyTarget(reply: ReplyItem): DynamicCommentComposerTarget {
    val rootRpid = if (reply.root > 0L) reply.root else reply.rpid
    return DynamicCommentComposerTarget(
        rootRpid = rootRpid,
        parentRpid = reply.rpid,
        uname = reply.member.uname
    )
}

internal fun resolveDynamicSentComment(
    reply: ReplyItem,
    subject: DynamicCommentTarget,
    target: DynamicCommentComposerTarget?,
    parentReply: ReplyItem?,
): ReplyItem = resolveSentSubReply(
    reply = reply,
    rootId = target?.rootRpid ?: 0L,
    parentId = target?.parentRpid ?: 0L,
    target = parentReply,
    oid = subject.oid,
    replyType = subject.type,
)

internal fun shouldApplyDynamicSentComment(
    expectedDynamicId: String,
    expectedTarget: DynamicCommentTarget,
    expectedSession: Long,
    currentDynamicId: String?,
    currentTarget: DynamicCommentTarget?,
    currentSession: Long,
): Boolean = expectedSession == currentSession && expectedDynamicId == currentDynamicId &&
    expectedTarget == currentTarget

internal fun removeDynamicCommentFromList(
    comments: List<ReplyItem>,
    rpid: Long,
    rootId: Long,
): List<ReplyItem> = comments.mapNotNull { reply ->
    if (reply.rpid == rpid) null else {
        val removedChild = reply.rpid == rootId || reply.replies.orEmpty().any { it.rpid == rpid }
        reply.copy(
            replies = reply.replies?.filterNot { it.rpid == rpid },
            rcount = (reply.rcount - if (removedChild) 1 else 0).coerceAtLeast(0),
            count = (reply.count - if (removedChild) 1 else 0).coerceAtLeast(0),
        )
    }
}

internal fun isDynamicCommentLiked(reply: ReplyItem): Boolean = reply.action == 1

/** Both dynamic thread hosts must pass the same explicit state to the shared comment UI. */
internal fun resolveDynamicThreadReactionIds(state: SubReplyUiState, action: Int): Set<Long> = buildSet {
    state.rootReply?.takeIf { it.action == action }?.let { add(it.rpid) }
    state.items.forEach { if (it.action == action) add(it.rpid) }
}

internal fun updateDynamicCommentReaction(
    reply: ReplyItem,
    rpid: Long,
    action: Int,
    like: Int,
): ReplyItem {
    val updated = if (reply.rpid == rpid) reply.copy(action = action, like = like) else reply
    val nested = updated.replies
    return if (nested.isNullOrEmpty()) updated else updated.copy(
        replies = nested.map { updateDynamicCommentReaction(it, rpid, action, like) }
    )
}

internal fun applyDynamicCommentLike(
    reply: ReplyItem,
    toLiked: Boolean
): ReplyItem {
    val currentlyLiked = isDynamicCommentLiked(reply)
    if (currentlyLiked == toLiked) return reply
    val delta = if (toLiked) 1 else -1
    return reply.copy(
        action = if (toLiked) 1 else 0,
        like = (reply.like + delta).coerceAtLeast(0)
    )
}

internal fun isDynamicCommentHated(reply: ReplyItem): Boolean = reply.action == 2

internal fun applyDynamicCommentHate(
    reply: ReplyItem,
    toHated: Boolean,
): ReplyItem {
    val currentlyHated = isDynamicCommentHated(reply)
    if (currentlyHated == toHated) return reply
    return reply.copy(
        action = if (toHated) 2 else 0,
        like = if (toHated && reply.action == 1) (reply.like - 1).coerceAtLeast(0) else reply.like,
    )
}


internal fun applyDynamicCommentHateInList(
    comments: List<ReplyItem>,
    rpid: Long,
    toHated: Boolean,
): List<ReplyItem> {
    if (rpid <= 0L) return comments
    return comments.map { reply ->
        val updated = if (reply.rpid == rpid) {
            applyDynamicCommentHate(reply, toHated)
        } else {
            reply
        }
        val nested = updated.replies
        if (nested.isNullOrEmpty()) updated
        else updated.copy(replies = applyDynamicCommentHateInList(nested, rpid, toHated))
    }
}

internal fun replaceDynamicCommentInList(
    comments: List<ReplyItem>,
    replacement: ReplyItem,
): List<ReplyItem> = comments.map { reply ->
    if (reply.rpid == replacement.rpid) replacement
    else reply.copy(replies = reply.replies?.let { replaceDynamicCommentInList(it, replacement) })
}

private fun formatDynamicCommentCount(count: Int): String = FormatUtils.formatStat(count.toLong())
