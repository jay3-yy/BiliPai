package com.android.purebilibili.feature.comment

import kotlinx.coroutines.ensureActive
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPage
import com.android.purebilibili.core.paging.ForwardPagingSession
import com.android.purebilibili.core.paging.ForwardPagingSource
import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.ReplyData

internal data class CommentPageRequest(
    val page: Int = 1,
    val continuation: CommentMainListContinuation? = null,
    val offset: String? = null,
)
private data class CommentPage(val request: CommentPageRequest, val data: ReplyData)

/** 页面业务按新增包更新现有状态，加载、游标推进与失败重试统一由 Paging 管理。 */
internal class CommentPagingSession(
    initial: CommentPageRequest = CommentPageRequest(),
    private val mainList: Boolean,
    load: suspend (CommentPageRequest) -> ReplyData,
    onPage: (CommentPageRequest, ReplyData) -> Unit,
    onState: (loading: Boolean, end: Boolean, failure: Throwable?) -> Unit,
) {
    private var applied = 0
    private val session = ForwardPagingSession(
        config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
        sourceFactory = {
            val seen = mutableSetOf<Any>()
            ForwardPagingSource<CommentPageRequest, CommentPage>(initial) { request ->
                val data = load(request)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val nextToken: Any? = if (mainList) data.mainListContinuation else data.grpcNextOffset.takeIf { it.isNotBlank() }
                val requestToken: Any? = if (mainList) request.continuation else request.offset
                requestToken?.let(seen::add)
                val more = !data.cursor.isEnd && (nextToken == null && mainList || nextToken != null && nextToken !in seen)
                val next = if (more) CommentPageRequest(request.page + 1, data.mainListContinuation, data.grpcNextOffset.takeIf { it.isNotBlank() }) else null
                ForwardPage(listOf(CommentPage(request, data)), next)
            }
        },
        onChanged = { pages, states ->
            if (states != null) {
                if (states.refresh is LoadState.NotLoading && pages.size > applied) {
                    pages.drop(applied).forEach { onPage(it.request, it.data) }
                    applied = pages.size
                }
                val failure = (states.refresh as? LoadState.Error)?.error ?: (states.append as? LoadState.Error)?.error
                onState(
                    // Paging 初始化的 NotLoading 不代表首包已到达；先发布正文，再结束加载。
                    (applied == 0 && failure == null) ||
                        states.refresh is LoadState.Loading || states.append is LoadState.Loading,
                    states.append.endOfPaginationReached,
                    failure
                )
            }
        }
    )
    suspend fun run() = session.run()
    fun loadMore() = session.appendOrRetry()
}
