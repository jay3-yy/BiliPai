package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcException
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resolve the requested reply itself: parent is a conversation edge, never a root hint. */
internal suspend fun readLocatedSubReplyPage(
    oid: Long,
    type: Int,
    rootId: Long,
    targetReplyId: Long,
    paginationOffset: String?,
    readReply: suspend (Long) -> ReplyResponse,
    readThread: suspend (Long) -> Result<ReplyData>,
): Result<ReplyData> {
    return try {
        currentCoroutineContext().ensureActive()
        require(oid > 0 && type > 0 && (rootId > 0 || targetReplyId > 0)) { "评论定位参数无效" }
        suspend fun active(result: Result<ReplyData>): Result<ReplyData> {
            currentCoroutineContext().ensureActive()
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            return result
        }
        // A cursor belongs to the already resolved root. Never re-resolve or restart it.
        if (!paginationOffset.isNullOrEmpty()) {
            require(rootId > 0L) { "回复分页缺少根评论信息" }
            return active(readThread(rootId))
        }
        var resolvedRoot = rootId
        var resolvedTarget = 0L
        var targetResponse: ReplyResponse? = null
        fun acceptLocation(id: Long, response: ReplyResponse) {
            if (response.code != 0) throw CommentReadException(response.code, response.message)
            val reply = response.data?.root ?: error("目标回复响应缺少数据，请重试")
            require(reply.rpid == id && reply.oid == oid && reply.replyType == type) {
                "目标回复与评论区不匹配，请重试"
            }
            require(reply.root >= 0 && (reply.root > 0 || reply.parent == 0L)) {
                "目标回复缺少根评论信息，请重试"
            }
            resolvedRoot = reply.root.takeIf { it > 0 } ?: reply.rpid
            resolvedTarget = reply.rpid.takeIf { it != resolvedRoot } ?: 0L
            targetResponse = response
        }
        suspend fun locate(id: Long) {
            val response = readReply(id)
            currentCoroutineContext().ensureActive()
            acceptLocation(id, response)
        }
        if (targetReplyId > 0) locate(targetReplyId)
        var result = active(readThread(resolvedRoot))
        // DetailList may return the requested child itself, auto-resolve its root,
        // or reject the child with 12006. All three must preserve the child anchor.
        if (targetReplyId <= 0) {
            val returnedData = result.getOrNull()
            val returnedRoot = returnedData?.root
            when {
                returnedRoot?.rpid == rootId && returnedRoot.root > 0L ->
                    acceptLocation(rootId, ReplyResponse(data = returnedData))
                (result.exceptionOrNull() as? BiliGrpcException)?.businessCode == 12006 ||
                    (returnedRoot != null && returnedRoot.rpid != rootId) -> locate(rootId)
            }
            // A child-addressed response may be a seek window even when it carries
            // the actual root. Start again with that root to retain earlier replies.
            if (resolvedRoot != rootId) {
                result = active(readThread(resolvedRoot))
            }
        }
        result = recoverUnavailableSubReplyTarget(
            oid, type, resolvedRoot, paginationOffset, resolvedTarget, result,
            readTarget = { targetResponse ?: readReply(resolvedTarget) },
        )
        result.map { data ->
            require(data.root?.let { it.rpid == resolvedRoot && it.root == 0L } != false) {
                "回复楼层不匹配，请重试"
            }
            data.copy(resolvedRootId = resolvedRoot, resolvedTargetReplyId = resolvedTarget)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
