package com.android.purebilibili.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.SpaceVideoTag
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.UserContentRepository
import com.android.purebilibili.data.repository.asVideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SPACE_WEB_PAGE_SIZE = 30

internal data class SpaceWebVideosState(
    val items: List<VideoItem> = emptyList(),
    val count: Int? = null,
    val page: Int = 1,
    val tags: List<SpaceVideoTag> = emptyList(),
    val tag: SpaceVideoTag = SpaceVideoTag(name = "全部类型"),
    val order: String = "pubdate",
    val isLoading: Boolean = false,
    val error: String? = null,
    val listRevision: Int = 0,
) {
    val totalPages: Int? get() = count?.let { (it + SPACE_WEB_PAGE_SIZE - 1) / SPACE_WEB_PAGE_SIZE }
    val hasMore: Boolean get() = totalPages?.let { page < it } == true
}

internal class SpaceWebVideosViewModel : ViewModel() {
    private val _state = MutableStateFlow(SpaceWebVideosState())
    val state = _state.asStateFlow()
    private var identity: Triple<Long, String, Long>? = null
    private var accountIdentity: com.android.purebilibili.core.store.AccountSessionIdentity? = null
    private var ownerName = ""
    private var request: Job? = null
    private var allCount: Int? = null

    fun initialize(mid: Long, name: String, type: String, id: Long) {
        val next = Triple(mid, type, id)
        val account = com.android.purebilibili.core.store.TokenManager.accountIdentity.value
        if (identity == next && accountIdentity == account) return
        accountIdentity = account
        request?.cancel()
        identity = next
        ownerName = name
        allCount = null
        _state.value = SpaceWebVideosState(order = if (type.isEmpty()) "pubdate" else "desc")
        load(1)
    }

    fun refresh() = load(1)
    fun jump(page: Int) {
        val pages = _state.value.totalPages ?: return
        if (page in 1..pages) load(page)
    }
    fun sort(order: String) {
        if (_state.value.order == order) return
        _state.update { it.copy(order = order) }
        load(1)
    }
    fun filter(tag: SpaceVideoTag) {
        if (_state.value.tag.tid == tag.tid && _state.value.tag.specialType == tag.specialType) return
        _state.update { it.copy(tag = tag) }
        load(1)
    }
    fun append() {
        val state = _state.value
        if (!state.isLoading && state.error == null && state.hasMore) load(state.page + 1, append = true)
    }
    fun retry() = load(pendingPage, pendingAppend)
    private var pendingPage = 1
    private var pendingAppend = false

    private fun load(page: Int, append: Boolean = false) {
        val (mid, type, id) = identity ?: return
        request?.cancel()
        pendingPage = page
        pendingAppend = append
        val query = _state.value
        val requestAccount = accountIdentity
        _state.update { it.copy(
            isLoading = true, error = null,
            items = if (append) it.items else emptyList(),
            count = if (append) it.count else null,
        ) }
        request = viewModelScope.launch {
            try {
                val videos: List<VideoItem>
                val count: Int
                var tags = query.tags
                when (type) {
                    "season" -> {
                        val response = NetworkModule.spaceApi.getSeasonArchives(mid, id, page, SPACE_WEB_PAGE_SIZE, query.order == "asc",
                            webLocation = "333.1387", referer = "https://space.bilibili.com/$mid", origin = "https://space.bilibili.com")
                        check(response.code == 0) { response.message }
                        val data = checkNotNull(response.data) { "合集内容响应为空" }
                        videos = data.archives.map { mapSeasonArchiveToVideoItem(it, mid, ownerName) }
                        count = data.page.total
                    }
                    "series" -> {
                        val response = NetworkModule.spaceApi.getSeriesArchives(mid, id, page, SPACE_WEB_PAGE_SIZE, query.order,
                            webLocation = "333.1387", referer = "https://space.bilibili.com/$mid", origin = "https://space.bilibili.com")
                        check(response.code == 0) { response.message }
                        val data = checkNotNull(response.data) { "系列内容响应为空" }
                        videos = data.archives.map { mapSeriesArchiveToVideoItem(it, mid, ownerName) }
                        count = data.page.total
                    }
                    else -> {
                        val data = UserContentRepository.webVideos(mid, page, query.order, query.tag).getOrThrow()
                        videos = data.list.vlist.map { it.asVideoItem(mid) }
                        count = data.page.count
                        if (query.tag.tid == 0 && query.tag.specialType == null) allCount = count
                        if (data.list.tags.isNotEmpty()) tags = listOf(SpaceVideoTag(name = "全部类型", count = allCount)) + data.list.tags
                    }
                }
                ensureActive()
                if (com.android.purebilibili.core.store.TokenManager.accountIdentity.value != requestAccount) return@launch
                _state.update { it.copy(
                    items = if (append) it.items + videos else videos,
                    count = count, page = page, tags = tags, isLoading = false,
                    listRevision = if (append) it.listRevision else it.listRevision + 1,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, error = error.message ?: "加载投稿失败") }
            }
        }
    }
}
