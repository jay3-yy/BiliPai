package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * MainList's nested replies are a server-selected sample, not a full thread page. Its
 * request schema has no nested-preview count. Fill only a displayed row's short sample,
 * using one bounded reply-page request; keep the server's selected previews first.
 */
suspend fun loadCommentReplyPreview(
    item: ReplyItem,
    collapsedLimit: Int,
    loadedReplies: List<ReplyItem> = item.replies.orEmpty(),
    applyDisplayFilter: Boolean = true,
    loadPage: suspend (pageSize: Int) -> Result<ReplyData> = { pageSize ->
        CommentRepository.getSubCommentsForSubject(
            oid = item.oid,
            type = item.replyType,
            rootId = item.rpid,
            page = 1,
            ps = pageSize,
            applyDisplayFilter = applyDisplayFilter,
        )
    },
): List<ReplyItem> {
    val existing = loadedReplies.distinctBy { it.rpid }
    val total = maxOf(item.count, item.rcount, existing.size)
    val target = minOf(collapsedLimit.coerceIn(1, 10), total)
    if (existing.size >= target || item.oid <= 0L || item.rpid <= 0L || item.replyType <= 0) {
        return existing
    }
    val result = loadPage(target)
    currentCoroutineContext().ensureActive()
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    val fetched = result.getOrNull()?.replies.orEmpty()
    if (fetched.isEmpty()) return existing
    return (existing + fetched.filter { it.rpid != item.rpid })
        .distinctBy { it.rpid }
        .take(maxOf(existing.size, target))
}
