package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.CommentMainListRoute
import com.android.purebilibili.data.model.response.ReplyData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun continueGoodsFilteredCommentPage(
    first: ReplyData,
    includeFeatured: Boolean,
    readNext: suspend (CommentMainListContinuation) -> Result<ReplyData>,
): Result<ReplyData> {
    var page = first
    val replies = first.replies.orEmpty().toMutableList()
    val hots = first.hots.orEmpty().toMutableList()
    val top = first.collectTopReplies().toMutableList()
    val seen = mutableSetOf<CommentMainListContinuation>()
    while (!page.cursor.isEnd && page.replies.orEmpty().all(::isGoodsReply) &&
        (!includeFeatured || page.collectTopReplies().all(::isGoodsReply))
    ) {
        val continuation = requireNotNull(page.mainListContinuation)
        if (!seen.add(continuation)) {
            return Result.failure(IllegalStateException("评论分页游标未前进，请重试"))
        }
        val next = readNext(continuation)
        currentCoroutineContext().ensureActive()
        page = next.getOrElse {
            if (it is CancellationException) throw it
            return Result.failure(it)
        }
        replies += page.replies.orEmpty()
        if (includeFeatured) {
            hots += page.hots.orEmpty()
            top += page.collectTopReplies()
        }
    }
    return Result.success(if (page === first) first else first.copy(
        replies = replies.distinctBy { it.rpid },
        hots = if (includeFeatured) hots.distinctBy { it.rpid } else first.hots,
        topReplies = if (includeFeatured) top.distinctBy { it.rpid } else first.topReplies,
        cursor = first.cursor.copy(isEnd = page.cursor.isEnd),
        grpcNextOffset = page.grpcNextOffset,
        mainListContinuation = page.mainListContinuation,
    ))
}

internal fun mobileRestCommentRoute(data: ReplyData): CommentMainListRoute =
    if (data.page.num > 0 || data.page.size > 0 || data.page.count > 0 &&
        data.cursor.allCount == 0 && data.cursor.next == 0 && !data.cursor.isEnd &&
        data.cursor.paginationReply == null
    ) CommentMainListRoute.REST_LEGACY else CommentMainListRoute.REST_MAIN

/** 仅供手机新分页入口使用，不改变 TV 消费的共享末页策略。 */
internal fun resolveMobileRestCommentPage(data: ReplyData, page: Int, pageSize: Int): ReplyData {
    val route = mobileRestCommentRoute(data)
    val offset = data.cursor.paginationReply?.nextOffset?.takeIf { it.isNotBlank() }
    val isEnd = when (route) {
        CommentMainListRoute.REST_LEGACY -> if (data.page.count > 0) {
            page.toLong() * (data.page.size.takeIf { it > 0 } ?: pageSize.coerceAtLeast(1)) >= data.page.count
        } else data.replies.orEmpty().isEmpty()
        else -> data.cursor.isEnd || (page <= 1 && data.getAllCount() == 0 &&
            !hasRenderableCommentPayload(data) && offset == null && data.cursor.next == 0)
    }
    val next = if (route == CommentMainListRoute.REST_LEGACY) page.toLong() + 1L else data.cursor.next.toLong()
    require(isEnd || route == CommentMainListRoute.REST_LEGACY || offset != null || next > 0) {
        "评论未加载完，但接口未返回后续游标，请重试"
    }
    return data.copy(
        cursor = data.cursor.copy(isEnd = isEnd),
        mainListContinuation = if (isEnd) null else CommentMainListContinuation(route, next, offset),
    )
}

internal fun requireAdvancingMobileCommentCursor(data: ReplyData, previous: CommentMainListContinuation?) {
    if (data.cursor.isEnd) return
    val next = requireNotNull(data.mainListContinuation) { "评论未加载完，但接口未返回后续游标，请重试" }
    val valid = if (next.route == CommentMainListRoute.GRPC) !next.offset.isNullOrBlank()
        else next.offset?.isNotBlank() == true || next.next > 0L
    require(valid) { "评论分页游标无效，请重试" }
    val repeats = previous != null && if (!next.offset.isNullOrBlank() && !previous.offset.isNullOrBlank()) {
        next.route == previous.route && next.offset == previous.offset
    } else next == previous
    require(!repeats) { "评论分页游标未前进，请重试" }
}

/** 空页可以继续；非末页必须有有效且前进的楼中楼/对话游标。 */
internal fun requireAdvancingSubReplyCursor(data: ReplyData, previousOffset: String?) {
    if (data.cursor.isEnd) return
    require(data.grpcNextOffset.isNotBlank()) { "回复未加载完，但接口未返回后续游标，请重试" }
    require(data.grpcNextOffset != previousOffset) { "回复分页游标未前进，请重试" }
}
