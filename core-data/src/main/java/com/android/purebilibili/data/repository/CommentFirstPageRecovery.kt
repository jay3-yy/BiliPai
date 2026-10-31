package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.ReplyData
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 只恢复有数量依据却没有任何内容或说明的首屏；真实空态和后续分页不追加请求。 */
internal suspend fun recoverEmptyCommentFirstPage(
    page: Int,
    expectedCount: Int,
    read: suspend () -> Result<ReplyData>,
): Result<ReplyData> {
    fun unexpectedEmpty(result: Result<ReplyData>): Boolean {
        val data = result.getOrNull() ?: return false
        return page <= 1 && maxOf(expectedCount, data.getAllCount()) > 0 &&
            !hasRenderableCommentPayload(data)
    }

    suspend fun readActive(): Result<ReplyData> {
        val result = read()
        currentCoroutineContext().ensureActive()
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        return result
    }
    val first = readActive()
    if (!unexpectedEmpty(first)) return first
    delay(350L)
    val retry = readActive()
    return if (unexpectedEmpty(retry)) {
        Result.failure(IllegalStateException("评论暂时无法加载，请重试或切换排序"))
    } else retry
}
