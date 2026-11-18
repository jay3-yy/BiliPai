package com.android.purebilibili.feature.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.purebilibili.data.repository.LIVE_SEARCH_PAGE_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class LiveSearchResults<T> {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var total by mutableStateOf<Int?>(null)
        private set
    var nextPage by mutableIntStateOf(1)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var moreError by mutableStateOf<String?>(null)
        private set

    private val requests = LiveBrowseRequestPolicy()
    private var job: Job? = null

    fun clear() {
        requests.invalidate()
        job?.cancel()
        items = emptyList()
        total = null
        nextPage = 1
        hasMore = false
        loading = false
        loadingMore = false
        error = null
        moreError = null
    }

    fun load(
        scope: CoroutineScope,
        keyword: String,
        reset: Boolean,
        itemKey: (T) -> Long,
        fetch: suspend (String, Int) -> Result<Pair<List<T>, Int?>>,
    ) {
        if (keyword.isBlank()) return
        val request = (if (reset) requests.refresh() else requests.loadMore(hasMore)) ?: return
        if (reset) job?.cancel()
        loading = reset
        loadingMore = !reset
        error = null
        moreError = null
        job = scope.launch {
            val result = fetch(keyword, request.page)
            if (!requests.owns(request)) return@launch
            result
                .onSuccess { (newItems, count) ->
                    requests.succeed(request)
                    items = (if (reset) newItems else items + newItems).distinctBy(itemKey)
                    if (reset || count != null) total = count
                    nextPage = request.page + 1
                    hasMore = newItems.isNotEmpty() && (
                        total?.let { items.size < it }
                            ?: (newItems.size == LIVE_SEARCH_PAGE_SIZE)
                        )
                }
                .onFailure {
                    requests.fail(request)
                    if (reset) error = it.message ?: "直播搜索失败"
                    else moreError = it.message ?: "加载更多失败"
                }
            loading = false
            loadingMore = false
        }
    }
}
