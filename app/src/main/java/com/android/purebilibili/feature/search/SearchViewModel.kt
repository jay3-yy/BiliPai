package com.android.purebilibili.feature.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.database.AppDatabase
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.data.model.response.SearchArticleItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import com.android.purebilibili.data.model.response.SearchPhotoItem
import com.android.purebilibili.data.model.response.SearchTopicItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.SearchUpItem
import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.core.plugin.FeedKind
import com.android.purebilibili.data.model.response.BangumiSearchItem
import com.android.purebilibili.data.model.response.LiveRoomSearchItem
import com.android.purebilibili.data.repository.SearchRepository
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.mergeSearchPageResults
import com.android.purebilibili.data.repository.SearchOrderSort
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import com.android.purebilibili.data.repository.shouldApplySearchResult
import com.android.purebilibili.data.repository.toggleSearchDurationSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val showResults: Boolean = false,
    val searchSessionId: Long = 0L,
    //  搜索类型
    val searchType: SearchType = SearchType.VIDEO,
    // 视频结果
    val searchResults: List<VideoItem> = emptyList(),
    //  UP主 结果
    val upResults: List<SearchUpItem> = emptyList(),
    //  [新增] 番剧结果
    val bangumiResults: List<BangumiSearchItem> = emptyList(),
    //  [新增] 直播结果
    val liveResults: List<LiveRoomSearchItem> = emptyList(),
    val liveUserResults: List<SearchLiveUserItem> = emptyList(),
    val articleResults: List<SearchArticleItem> = emptyList(),
    val topicResults: List<SearchTopicItem> = emptyList(),
    val photoResults: List<SearchPhotoItem> = emptyList(),
    val hotList: List<SearchKeywordUiModel> = emptyList(),
    val historyList: List<SearchHistory> = emptyList(),
    //  搜索建议
    val suggestions: List<SearchSuggestionUiModel> = emptyList(),
    // 默认搜索占位词（来自 API-collect: /wbi/search/default）
    val defaultSearchHint: String = "",
    //  搜索发现 / 猜你想搜
    val discoverList: List<SearchKeywordUiModel> = emptyList(),
    val discoverTitle: String = "搜索发现",
    val isRefreshingHotList: Boolean = false,
    val isRefreshingDiscoverList: Boolean = false,
    val hotListError: String? = null,
    val discoverListError: String? = null,
    val error: String? = null,
    //  搜索过滤条件
    val searchOrder: SearchOrder = SearchOrder.TOTALRANK,
    val searchDurations: Set<SearchDuration> = emptySet(),
    val videoTid: Int = 0,
    val pubTimeType: SearchVideoPubTimeType = SearchVideoPubTimeType.ALL,
    val pubBegin: Long? = null,
    val pubEnd: Long? = null,
    val upOrder: SearchUpOrder = SearchUpOrder.DEFAULT,
    val upOrderSort: SearchOrderSort = SearchOrderSort.DESC,
    val upUserType: SearchUserType = SearchUserType.ALL,
    val liveOrder: SearchLiveOrder = SearchLiveOrder.ONLINE,
    //  搜索彩蛋消息
    val easterEggMessage: String? = null,
    //  [新增] 分页状态
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasMoreResults: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val emptyStateReason: SearchEmptyStateReason = SearchEmptyStateReason.NONE,
    val resultPages: Map<SearchType, SearchResultPageUiState> = emptyMap()
)

data class SearchResultPageUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val hasMoreResults: Boolean = false,
    val totalCount: Int = 0,
    val emptyStateReason: SearchEmptyStateReason = SearchEmptyStateReason.NONE,
    val searchResults: List<VideoItem> = emptyList(),
    val upResults: List<SearchUpItem> = emptyList(),
    val bangumiResults: List<BangumiSearchItem> = emptyList(),
    val liveResults: List<LiveRoomSearchItem> = emptyList(),
    val liveUserResults: List<SearchLiveUserItem> = emptyList(),
    val articleResults: List<SearchArticleItem> = emptyList(),
    val topicResults: List<SearchTopicItem> = emptyList(),
    val photoResults: List<SearchPhotoItem> = emptyList()
) {
    val hasLoaded: Boolean
        get() = query.isNotBlank() && currentPage > 0 && !isSearching
}

internal fun SearchUiState.toCurrentSearchResultPage(): SearchResultPageUiState {
    return SearchResultPageUiState(
        query = query.trim(),
        isSearching = isSearching,
        isLoadingMore = isLoadingMore,
        loadMoreError = loadMoreError,
        error = error,
        currentPage = currentPage,
        totalPages = totalPages,
        hasMoreResults = hasMoreResults,
        totalCount = when (searchType) {
            SearchType.VIDEO -> searchResults.size
            SearchType.UP -> upResults.size
            SearchType.BANGUMI, SearchType.MEDIA_FT -> bangumiResults.size
            SearchType.LIVE -> liveResults.size
            SearchType.LIVE_USER -> liveUserResults.size
            SearchType.ARTICLE -> articleResults.size
            SearchType.TOPIC -> topicResults.size
            SearchType.PHOTO -> photoResults.size
        },
        emptyStateReason = emptyStateReason,
        searchResults = searchResults,
        upResults = upResults,
        bangumiResults = bangumiResults,
        liveResults = liveResults,
        liveUserResults = liveUserResults,
        articleResults = articleResults,
        topicResults = topicResults,
        photoResults = photoResults
    )
}

internal fun SearchUiState.withSearchResultPage(
    type: SearchType,
    pageState: SearchResultPageUiState
): SearchUiState {
    return copy(
        resultPages = resultPages + (type to pageState)
    )
}

internal fun SearchUiState.withCurrentSearchResultPageCached(): SearchUiState {
    return withSearchResultPage(
        type = searchType,
        pageState = toCurrentSearchResultPage()
    )
}

internal fun SearchUiState.withSearchResultPageMirrored(
    type: SearchType,
    pageState: SearchResultPageUiState
): SearchUiState {
    return copy(
        searchType = type,
        isSearching = pageState.isSearching,
        isLoadingMore = pageState.isLoadingMore,
        loadMoreError = pageState.loadMoreError,
        error = pageState.error,
        currentPage = pageState.currentPage.coerceAtLeast(1),
        totalPages = pageState.totalPages,
        hasMoreResults = pageState.hasMoreResults,
        emptyStateReason = pageState.emptyStateReason,
        searchResults = pageState.searchResults,
        upResults = pageState.upResults,
        bangumiResults = pageState.bangumiResults,
        liveResults = pageState.liveResults,
        liveUserResults = pageState.liveUserResults,
        articleResults = pageState.articleResults,
        topicResults = pageState.topicResults,
        photoResults = pageState.photoResults,
        resultPages = resultPages + (type to pageState)
    )
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val searchDao = AppDatabase.getDatabase(application).searchHistoryDao()
    
    //  防抖任务
    private var suggestJob: Job? = null
    private var activeSearchJob: Job? = null
    private var activeLoadMoreJob: Job? = null
    private var activeSearchSessionId: Long = 0L
    private var blockedUpObserverStarted = false
    private var landingBootstrapStarted = false

    private val blockedUpRepository = com.android.purebilibili.data.repository.BlockedUpRepository(application)
    private var blockedMids: Set<Long> = emptySet()

    init {
        loadHistory()
    }

    private fun ensureBlockedUpObserver() {
        if (blockedUpObserverStarted) return
        blockedUpObserverStarted = true
        viewModelScope.launch {
            blockedUpRepository.getAllBlockedUps().collect { list ->
                blockedMids = list.map { it.mid }.toSet()
                val currentState = _uiState.value
                val newVideos = currentState.searchResults.filter { it.owner.mid !in blockedMids }
                val newUps = currentState.upResults.filter { it.mid !in blockedMids }
                val newLives = currentState.liveResults.filter { it.uid !in blockedMids }
                val newLiveUsers = currentState.liveUserResults.filter { it.uid !in blockedMids }

                if (newVideos.size != currentState.searchResults.size ||
                    newUps.size != currentState.upResults.size ||
                    newLives.size != currentState.liveResults.size ||
                    newLiveUsers.size != currentState.liveUserResults.size
                ) {
                    _uiState.update {
                        it.copy(
                            searchResults = newVideos,
                            upResults = newUps,
                            liveResults = newLives,
                            liveUserResults = newLiveUsers,
                            emptyStateReason = when (it.searchType) {
                                SearchType.VIDEO -> resolveSearchEmptyStateReason(
                                    rawResultCount = it.searchResults.size,
                                    visibleResultCount = newVideos.size
                                )
                                SearchType.UP -> resolveSearchEmptyStateReason(
                                    rawResultCount = it.upResults.size,
                                    visibleResultCount = newUps.size
                                )
                                SearchType.LIVE -> resolveSearchEmptyStateReason(
                                    rawResultCount = it.liveResults.size,
                                    visibleResultCount = newLives.size
                                )
                                SearchType.LIVE_USER -> resolveSearchEmptyStateReason(
                                    rawResultCount = it.liveUserResults.size,
                                    visibleResultCount = newLiveUsers.size
                                )
                                else -> it.emptyStateReason
                            }
                        )
                    }
                }
            }
        }
    }

    fun ensureLandingBootstrap() {
        if (landingBootstrapStarted) return
        landingBootstrapStarted = true
        viewModelScope.launch {
            launch { loadDefaultSearchHintInternal() }
            launch { refreshHotSearchInternal() }
            launch { refreshDiscoverInternal() }
        }
    }

    fun onQueryChange(newQuery: String) {
        val trimmedQuery = newQuery.trim()
        val currentState = _uiState.value
        val shouldReturnToLanding = currentState.showResults && trimmedQuery != currentState.query.trim()

        _uiState.update {
            it.copy(
                query = newQuery,
                showResults = if (newQuery.isEmpty()) false else if (shouldReturnToLanding) false else it.showResults,
                error = if (newQuery.isEmpty() || shouldReturnToLanding) null else it.error,
                loadMoreError = if (newQuery.isEmpty() || shouldReturnToLanding) null else it.loadMoreError,
                emptyStateReason = if (newQuery.isEmpty() || shouldReturnToLanding) {
                    SearchEmptyStateReason.NONE
                } else {
                    it.emptyStateReason
                }
            )
        }
        if (newQuery.isEmpty()) {
            _uiState.update {
                it.copy(
                    showResults = false,
                    suggestions = emptyList(),
                    error = null,
                    loadMoreError = null,
                    emptyStateReason = SearchEmptyStateReason.NONE,
                    resultPages = emptyMap()
                )
            }
        } else {
            //  触发搜索建议（防抖 300ms）
            loadSuggestions(newQuery)
        }
    }

    /** Hides the suggestion layer without leaving the search page. */
    fun dismissSuggestions() {
        suggestJob?.cancel()
        _uiState.update { it.copy(suggestions = emptyList()) }
    }

    /**
     * Leave the result list and return to the landing surface while keeping the query.
     * Does not clear history or filters; focus/keyboard is owned by the screen.
     */
    fun exitResultsToLanding() {
        suggestJob?.cancel()
        activeSearchJob?.cancel()
        activeLoadMoreJob?.cancel()
        _uiState.update {
            it.copy(
                showResults = false,
                isSearching = false,
                suggestions = emptyList(),
                error = null,
                loadMoreError = null,
                isLoadingMore = false,
                emptyStateReason = SearchEmptyStateReason.NONE
            )
        }
    }
    
    //  防抖加载搜索建议
    private fun loadSuggestions(keyword: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300) // 防抖 300ms
            val result = SearchRepository.getSuggest(keyword)
            result.onSuccess { suggestions ->
                if (keyword != _uiState.value.query) return@onSuccess
                _uiState.update {
                    it.copy(
                        suggestions = suggestions
                            .map { tag -> tag.toSearchSuggestionUiModel() }
                            .take(8)
                    )
                }
            }.onFailure {
                if (keyword != _uiState.value.query) return@onFailure
                _uiState.update { it.copy(suggestions = emptyList()) }
            }
        }
    }
    
    //  切换搜索类型
    fun setSearchType(type: SearchType) {
        val beforeSwitch = _uiState.value
        if (beforeSwitch.searchType == type) return

        val normalizedQuery = beforeSwitch.query.trim()
        val cachedState = beforeSwitch.resultPages[type]
        if (
            beforeSwitch.showResults &&
            cachedState != null &&
            cachedState.query == normalizedQuery &&
            cachedState.hasLoaded
        ) {
            _uiState.value = beforeSwitch
                .withCurrentSearchResultPageCached()
                .withSearchResultPageMirrored(type, cachedState)
            return
        }

        _uiState.value = beforeSwitch
            .withCurrentSearchResultPageCached()
            .copy(
                searchType = type,
                isSearching = beforeSwitch.showResults && normalizedQuery.isNotBlank(),
                isLoadingMore = false,
                loadMoreError = null,
                error = null,
                currentPage = 1,
                totalPages = 1,
                hasMoreResults = false,
                emptyStateReason = SearchEmptyStateReason.NONE,
                searchResults = emptyList(),
                upResults = emptyList(),
                bangumiResults = emptyList(),
                liveResults = emptyList(),
                liveUserResults = emptyList(),
                articleResults = emptyList(),
                topicResults = emptyList(),
                photoResults = emptyList()
            )

        if (normalizedQuery.isNotBlank() && beforeSwitch.showResults) {
            search(normalizedQuery)
        }
    }
    
    //  设置搜索排序
    fun setSearchOrder(order: SearchOrder) {
        _uiState.update { it.copy(searchOrder = order) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }
    
    //  设置时长筛选；空集合表示“全部时长”，多个值会在仓库层分别请求后合并去重。
    fun toggleSearchDuration(duration: SearchDuration) {
        _uiState.update {
            it.copy(searchDurations = toggleSearchDurationSelection(it.searchDurations, duration))
        }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    /** PiliPlus-style single duration selection for the filter sheet. */
    fun setSearchDuration(duration: SearchDuration) {
        _uiState.update {
            it.copy(searchDurations = resolveSearchDurationSelection(duration))
        }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setVideoTid(tid: Int) {
        _uiState.update { it.copy(videoTid = tid) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setPubTimeType(type: SearchVideoPubTimeType) {
        val range = resolveSearchPubTimeRange(type = type)
        _uiState.update {
            it.copy(
                pubTimeType = type,
                pubBegin = range.beginEpochSeconds,
                pubEnd = range.endEpochSeconds
            )
        }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setCustomPubTimeRange(beginEpochSeconds: Long, endEpochSeconds: Long) {
        val begin = minOf(beginEpochSeconds, endEpochSeconds)
        val end = maxOf(beginEpochSeconds, endEpochSeconds)
        _uiState.update {
            it.copy(
                pubTimeType = SearchVideoPubTimeType.CUSTOM,
                pubBegin = begin,
                pubEnd = end
            )
        }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setUpOrder(order: SearchUpOrder) {
        _uiState.update { it.copy(upOrder = order) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setUpOrderSort(orderSort: SearchOrderSort) {
        _uiState.update { it.copy(upOrderSort = orderSort) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setUpUserType(userType: SearchUserType) {
        _uiState.update { it.copy(upUserType = userType) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun setLiveOrder(order: SearchLiveOrder) {
        _uiState.update { it.copy(liveOrder = order) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.query)
        }
    }

    fun search(keyword: String) {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return
        ensureBlockedUpObserver()

        val context = getApplication<android.app.Application>()
        val easterEggEnabled = com.android.purebilibili.core.store.SettingsManager.isEasterEggEnabledSync(context)
        val easterEggMessage = if (easterEggEnabled) {
            com.android.purebilibili.core.util.EasterEggs.checkSearchEasterEgg(normalizedKeyword)
        } else null
        val searchType = _uiState.value.searchType
        val shouldClearCachedPages = !_uiState.value.showResults ||
            _uiState.value.query.trim() != normalizedKeyword
        val searchSessionId = activeSearchSessionId + 1L
        activeSearchSessionId = searchSessionId
        activeSearchJob?.cancel()
        activeLoadMoreJob?.cancel()

        _uiState.update {
            it.copy(
                query = normalizedKeyword,
                isSearching = true,
                showResults = true,
                searchSessionId = searchSessionId,
                suggestions = emptyList(),
                error = null,
                loadMoreError = null,
                easterEggMessage = easterEggMessage,
                currentPage = 1,
                hasMoreResults = false,
                isLoadingMore = false,
                emptyStateReason = SearchEmptyStateReason.NONE,
                searchResults = if (shouldClearCachedPages) emptyList() else it.searchResults,
                upResults = if (shouldClearCachedPages) emptyList() else it.upResults,
                bangumiResults = if (shouldClearCachedPages) emptyList() else it.bangumiResults,
                liveResults = if (shouldClearCachedPages) emptyList() else it.liveResults,
                liveUserResults = if (shouldClearCachedPages) emptyList() else it.liveUserResults,
                articleResults = if (shouldClearCachedPages) emptyList() else it.articleResults,
                topicResults = if (shouldClearCachedPages) emptyList() else it.topicResults,
                photoResults = if (shouldClearCachedPages) emptyList() else it.photoResults,
                resultPages = if (shouldClearCachedPages) emptyMap() else it.resultPages
            )
        }
        saveHistory(normalizedKeyword)
        com.android.purebilibili.core.util.AnalyticsHelper.logSearch(normalizedKeyword)

        activeSearchJob = viewModelScope.launch {
            when (searchType) {
                SearchType.VIDEO -> {
                    val order = _uiState.value.searchOrder
                    val durations = _uiState.value.searchDurations
                    val videoTid = _uiState.value.videoTid
                    val pubBegin = _uiState.value.pubBegin
                    val pubEnd = _uiState.value.pubEnd
                    val result = SearchRepository.searchWithDurations(
                        keyword = normalizedKeyword,
                        order = order,
                        durations = durations,
                        tids = videoTid,
                        page = 1,
                        pubBegin = pubBegin,
                        pubEnd = pubEnd
                    )
                    result.onSuccess { (videos, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        val nativeFiltered = videos.filter { it.owner.mid !in blockedMids }
                        val builtinFiltered = com.android.purebilibili.core.plugin.PluginManager.filterFeedItems(nativeFiltered, feedKind = FeedKind.SEARCH)
                        val filteredVideos = com.android.purebilibili.core.plugin.json.JsonPluginManager.filterVideos(builtinFiltered)
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                searchResults = filteredVideos,
                                upResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = videos.size,
                                    visibleResultCount = filteredVideos.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.UP -> {
                    val result = SearchRepository.searchUp(
                        keyword = normalizedKeyword,
                        page = 1,
                        order = _uiState.value.upOrder,
                        orderSort = _uiState.value.upOrderSort,
                        userType = _uiState.value.upUserType
                    )
                    result.onSuccess { (ups, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        val filteredUps = ups.filter { it.mid !in blockedMids }
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                upResults = filteredUps,
                                searchResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = ups.size,
                                    visibleResultCount = filteredUps.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.BANGUMI -> {
                    val result = SearchRepository.searchBangumi(normalizedKeyword, page = 1)
                    result.onSuccess { (bangumis, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                bangumiResults = bangumis,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = bangumis.size,
                                    visibleResultCount = bangumis.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.MEDIA_FT -> {
                    val result = SearchRepository.searchMediaFt(normalizedKeyword, page = 1)
                    result.onSuccess { (items, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                bangumiResults = items,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = items.size,
                                    visibleResultCount = items.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.LIVE -> {
                    val result = SearchRepository.searchLive(
                        keyword = normalizedKeyword,
                        page = 1,
                        order = _uiState.value.liveOrder
                    )
                    result.onSuccess { (liveRooms, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        val filteredLive = liveRooms.filter { it.uid !in blockedMids }
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                liveResults = filteredLive,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = liveRooms.size,
                                    visibleResultCount = filteredLive.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.LIVE_USER -> {
                    val result = SearchRepository.searchLiveUser(
                        keyword = normalizedKeyword,
                        page = 1
                    )
                    result.onSuccess { (liveUsers, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        val filteredLiveUsers = liveUsers.filter { it.uid !in blockedMids }
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                liveUserResults = filteredLiveUsers,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = liveUsers.size,
                                    visibleResultCount = filteredLiveUsers.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.ARTICLE -> {
                    val result = SearchRepository.searchArticle(keyword = normalizedKeyword, page = 1)
                    result.onSuccess { (articles, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                articleResults = articles,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                topicResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = articles.size,
                                    visibleResultCount = articles.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.TOPIC -> {
                    val result = SearchRepository.searchTopic(keyword = normalizedKeyword, page = 1)
                    result.onSuccess { (topics, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                topicResults = topics,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                photoResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = topics.size,
                                    visibleResultCount = topics.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
                SearchType.PHOTO -> {
                    val result = SearchRepository.searchPhoto(keyword = normalizedKeyword, page = 1)
                    result.onSuccess { (photos, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = null,
                                loadMoreError = null,
                                photoResults = photos,
                                searchResults = emptyList(),
                                upResults = emptyList(),
                                bangumiResults = emptyList(),
                                liveResults = emptyList(),
                                liveUserResults = emptyList(),
                                articleResults = emptyList(),
                                topicResults = emptyList(),
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore,
                                emptyStateReason = resolveSearchEmptyStateReason(
                                    rawResultCount = photos.size,
                                    visibleResultCount = photos.size
                                )
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, normalizedKeyword, _uiState.value.query, searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = e.message ?: "搜索失败",
                                emptyStateReason = SearchEmptyStateReason.NONE
                            )
                        }
                    }
                }
            }
        }
    }
    
    //  [新增] 加载更多搜索结果
    fun loadMoreResults() {
        val state = _uiState.value
        ensureBlockedUpObserver()
        
        if (!state.hasMoreResults || state.isLoadingMore || state.isSearching || state.query.isBlank()) {
            return
        }
        
        _uiState.update { it.withLoadMoreStarted() }
        val searchSessionId = activeSearchSessionId
        val nextPage = state.currentPage + 1
        
        activeLoadMoreJob?.cancel()
        activeLoadMoreJob = viewModelScope.launch {
            when (state.searchType) {
                SearchType.VIDEO -> {
                    val result = SearchRepository.searchWithDurations(
                        keyword = state.query,
                        order = state.searchOrder,
                        durations = state.searchDurations,
                        tids = state.videoTid,
                        page = nextPage,
                        pubBegin = state.pubBegin,
                        pubEnd = state.pubEnd
                    )
                    result.onSuccess { (videos, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        val nativeFiltered = videos.filter { it.owner.mid !in blockedMids }
                        val builtinFiltered = com.android.purebilibili.core.plugin.PluginManager
                            .filterFeedItems(nativeFiltered, feedKind = FeedKind.SEARCH)
                        val filteredVideos = com.android.purebilibili.core.plugin.json.JsonPluginManager
                            .filterVideos(builtinFiltered)

                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                searchResults = mergeSearchPageResults(it.searchResults, filteredVideos) { video -> video.bvid },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.UP -> {
                    val result = SearchRepository.searchUp(
                        keyword = state.query,
                        page = nextPage,
                        order = state.upOrder,
                        orderSort = state.upOrderSort,
                        userType = state.upUserType
                    )
                    result.onSuccess { (ups, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        val filteredUps = ups.filter { it.mid !in blockedMids }
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                upResults = mergeSearchPageResults(it.upResults, filteredUps) { up -> up.mid },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.BANGUMI -> {
                    val result = SearchRepository.searchBangumi(state.query, page = nextPage)
                    result.onSuccess { (bangumis, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                bangumiResults = mergeSearchPageResults(it.bangumiResults, bangumis) { item -> item.seasonId },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.MEDIA_FT -> {
                    val result = SearchRepository.searchMediaFt(state.query, page = nextPage)
                    result.onSuccess { (items, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                bangumiResults = mergeSearchPageResults(it.bangumiResults, items) { item -> item.seasonId },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.LIVE -> {
                    val result = SearchRepository.searchLive(
                        keyword = state.query,
                        page = nextPage,
                        order = state.liveOrder
                    )
                    result.onSuccess { (liveRooms, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        val filteredLive = liveRooms.filter { it.uid !in blockedMids }
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                liveResults = mergeSearchPageResults(it.liveResults, filteredLive) { room -> room.roomid },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.LIVE_USER -> {
                    val result = SearchRepository.searchLiveUser(
                        keyword = state.query,
                        page = nextPage
                    )
                    result.onSuccess { (liveUsers, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        val filteredLiveUsers = liveUsers.filter { it.uid !in blockedMids }
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                liveUserResults = mergeSearchPageResults(it.liveUserResults, filteredLiveUsers) { user -> user.uid },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.ARTICLE -> {
                    val result = SearchRepository.searchArticle(
                        keyword = state.query,
                        page = nextPage
                    )
                    result.onSuccess { (articles, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                articleResults = mergeSearchPageResults(it.articleResults, articles) { article -> article.id },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.TOPIC -> {
                    val result = SearchRepository.searchTopic(
                        keyword = state.query,
                        page = nextPage
                    )
                    result.onSuccess { (topics, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                topicResults = mergeSearchPageResults(it.topicResults, topics) { topic -> topic.topicId },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
                SearchType.PHOTO -> {
                    val result = SearchRepository.searchPhoto(
                        keyword = state.query,
                        page = nextPage
                    )
                    result.onSuccess { (photos, pageInfo) ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onSuccess
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                loadMoreError = null,
                                photoResults = mergeSearchPageResults(it.photoResults, photos) { photo -> photo.id },
                                currentPage = pageInfo.currentPage,
                                totalPages = pageInfo.totalPages,
                                hasMoreResults = pageInfo.hasMore
                            )
                        }
                    }.onFailure { e ->
                        if (!shouldApplySearchResult(searchSessionId, activeSearchSessionId, state.query, _uiState.value.query, state.searchType, _uiState.value.searchType)) return@onFailure
                        _uiState.update { it.withLoadMoreFailure("加载更多失败: ${e.message}") }
                    }
                }
            }
        }
    }

    private suspend fun loadDefaultSearchHintInternal() {
        SearchRepository.getDefaultSearchHint()
            .onSuccess { hint ->
                if (hint.isNotBlank()) {
                    _uiState.update { it.copy(defaultSearchHint = hint) }
                }
            }
    }

    fun refreshHotSearch() {
        if (!landingBootstrapStarted) {
            ensureLandingBootstrap()
            return
        }
        viewModelScope.launch { refreshHotSearchInternal() }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            searchDao.getAll()
                .catch { e ->
                    com.android.purebilibili.core.util.Logger.e("SearchVM", "Failed to load search history", e)
                    _uiState.update { it.copy(historyList = emptyList()) }
                }
                .collect { history ->
                    _uiState.update { it.copy(historyList = history) }
                }
        }
    }

    fun refreshDiscover() {
        if (!landingBootstrapStarted) {
            ensureLandingBootstrap()
            return
        }
        viewModelScope.launch { refreshDiscoverInternal() }
    }

    private suspend fun refreshHotSearchInternal() {
        _uiState.update { it.copy(isRefreshingHotList = true, hotListError = null) }
        val result = SearchRepository.getTrendingKeywords(limit = 10)
        result.onSuccess { bundle ->
            _uiState.update {
                it.copy(
                    hotList = bundle.allItems
                        .map { item -> item.toSearchKeywordUiModel() }
                        .take(10),
                    isRefreshingHotList = false,
                    hotListError = null
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    isRefreshingHotList = false,
                    hotListError = it.message ?: "热榜加载失败"
                )
            }
        }
    }

    private suspend fun refreshDiscoverInternal() {
        val historyKeywords = _uiState.value.historyList.map { it.keyword }
        _uiState.update { it.copy(isRefreshingDiscoverList = true, discoverListError = null) }
        val result = SearchRepository.getSearchRecommend(historyKeywords)

        result.onSuccess { list ->
            _uiState.update {
                it.copy(
                    discoverList = list
                        .map { item -> item.toSearchKeywordUiModel() }
                        .take(10),
                    isRefreshingDiscoverList = false,
                    discoverListError = null
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    isRefreshingDiscoverList = false,
                    discoverListError = it.message ?: "搜索发现加载失败"
                )
            }
        }
    }

    private fun saveHistory(keyword: String) {
        viewModelScope.launch {
            try {
                //  隐私无痕模式检查：如果启用则跳过保存搜索历史
                val context = getApplication<android.app.Application>()
                if (com.android.purebilibili.core.store.SettingsManager.isPrivacyModeEnabledSync(context)) {
                    com.android.purebilibili.core.util.Logger.d("SearchVM", " Privacy mode enabled, skipping search history save")
                    return@launch
                }

                //  使用 keyword 主键，重复搜索自动更新时间戳
                searchDao.insert(SearchHistory(keyword = keyword, timestamp = System.currentTimeMillis()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.android.purebilibili.core.util.Logger.e("SearchVM", "Failed to save search history", e)
            }
        }
    }

    fun deleteHistory(history: SearchHistory) {
        viewModelScope.launch {
            try {
                searchDao.delete(history)
                refreshDiscover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.android.purebilibili.core.util.Logger.e("SearchVM", "Failed to delete search history", e)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                searchDao.clearAll()
                refreshDiscover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                com.android.purebilibili.core.util.Logger.e("SearchVM", "Failed to clear search history", e)
            }
        }
    }
    
    //  [新增] 添加到稍后再看
    fun addToWatchLater(bvid: String, aid: Long) {
        viewModelScope.launch {
            val result = com.android.purebilibili.data.repository.ActionRepository.toggleWatchLater(aid, true)
            result.onSuccess {
                android.widget.Toast.makeText(getApplication(), "已添加到稍后再看", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                android.widget.Toast.makeText(getApplication(), e.message ?: "添加失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
