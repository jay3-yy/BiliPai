package com.android.purebilibili.feature.comment

import androidx.paging.PagingSource
import com.android.purebilibili.core.paging.ForwardPage
import com.android.purebilibili.core.paging.ForwardPagingSource
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.feature.video.viewmodel.SubReplyPageRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class SubReplyPageChunk(val request: SubReplyPageRequest, val data: ReplyData)

internal fun subReplyPagingSource(
    initialRequest: SubReplyPageRequest = SubReplyPageRequest(1, null),
    loadPage: suspend (SubReplyPageRequest) -> ReplyData,
): PagingSource<SubReplyPageRequest, SubReplyPageChunk> {
    val offsets = mutableSetOf<String>()
    return ForwardPagingSource(initialRequest) { request ->
        val data = loadPage(request)
        currentCoroutineContext().ensureActive()
        val next = if (data.cursor.isEnd) null else data.grpcNextOffset.also {
            check(it.isNotBlank()) { "回复未加载完，但接口未返回后续游标，请重试" }
            check(it != request.offset && it !in offsets) { "回复分页游标未前进，请重试" }
        }
        // 只有成功的页才推进历史；异常游标保留为可重试错误，不伪装成完整末页。
        request.offset?.let(offsets::add)
        ForwardPage(listOf(SubReplyPageChunk(request, data)), next?.let { SubReplyPageRequest(request.page + 1, it) })
    }
}
