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
import com.android.purebilibili.plugin.sdk.FeedKind
import com.android.purebilibili.data.model.response.BangumiSearchItem
import com.android.purebilibili.data.model.response.LiveRoomSearchItem
import com.android.purebilibili.data.model.response.MatchContestInfo
import com.android.purebilibili.data.repository.SearchRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.SearchArticleCategory
import com.android.purebilibili.data.repository.SearchPhotoCategory
import com.android.purebilibili.data.repository.SearchOrderSort
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import com.android.purebilibili.data.repository.toggleSearchDurationSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    // Draft edits never change the keyword owning result pages and requests.
    val submittedQuery: String = query.trim(),
    val recordSearchHistory: Boolean = true,
    val globalPrivacyMode: Boolean = false,
    val isSearching: Boolean = false,
    val showResults: Boolean = false,
    val searchSessionId: Long = 0L,
    //  搜索类型
    val searchType: SearchType = SearchType.ALL,
    // 视频结果
    val searchResults: List<VideoItem> = emptyList(),
    //  UP主 结果
    val upResults: List<SearchUpItem> = emptyList(),
    //  [新增] 番剧结果
    val bangumiResults: List<BangumiSearchItem> = emptyList(),
    //  赛事结果（综合搜索 esports 块）
    val matchResults: List<MatchContestInfo> = emptyList(),
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
    val defaultSearchKeyword: String = "",
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
    val articleOrder: SearchOrder = SearchOrder.TOTALRANK,
    val articleCategory: SearchArticleCategory = SearchArticleCategory.ALL,
    val photoOrder: SearchOrder = SearchOrder.TOTALRANK,
    val photoCategory: SearchPhotoCategory = SearchPhotoCategory.ALL,
    //  搜索彩蛋消息
    val easterEggMessage: String? = null,
    //  [新增] 分页状态
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasMoreResults: Boolean = false,
    // 当前类型的 API 声明总数(-1 = 未知),以及各类型已加载过的计数,用于 Tab 标签展示。
    val totalResults: Int = -1,
    val searchTypeCounts: Map<SearchType, Int> = emptyMap(),
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
    val matchResults: List<MatchContestInfo> = emptyList(),
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
        query = submittedQuery,
        isSearching = isSearching,
        isLoadingMore = isLoadingMore,
        loadMoreError = loadMoreError,
        error = error,
        currentPage = currentPage,
        totalPages = totalPages,
        hasMoreResults = hasMoreResults,
        totalCount = when (searchType) {
            SearchType.ALL -> searchResults.size + upResults.size + bangumiResults.size + matchResults.size
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
        matchResults = matchResults,
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
        matchResults = pageState.matchResults,
        liveResults = pageState.liveResults,
        liveUserResults = pageState.liveUserResults,
        articleResults = pageState.articleResults,
        topicResults = pageState.topicResults,
        photoResults = pageState.photoResults,
        resultPages = resultPages + (type to pageState)
    )
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchUiState(
        recordSearchHistory = com.android.purebilibili.core.store.SettingsManager
            .isSearchHistoryRecordingEnabledSync(application),
        globalPrivacyMode = com.android.purebilibili.core.store.SettingsManager
            .isPrivacyModeEnabledSync(application),
    ))
    val uiState = _uiState.asStateFlow()

    private val searchDao = AppDatabase.getDatabase(application).searchHistoryDao()
    
    //  防抖任务
    private var discoverSectionEnabled = true
    private var searchSuggestionsEnabled = true
    private var discoverSettingsLoaded = false
    private var discoverRequestVersion = 0L
    private var suggestJob: Job? = null
    private val pagingSessions = mutableMapOf<SearchType, SearchPagingSession>()
    private val pagingGenerations = mutableMapOf<SearchType, Long>()
    private val pagingJobs = mutableMapOf<SearchType, Job>()
    private var activeSearchSessionId: Long = 0L
    private var blockedUpObserverStarted = false
    private var landingBootstrapStarted = false

    private val blockedUpRepository = com.android.purebilibili.data.repository.BlockedUpRepository(application)
    private var blockedMids: Set<Long> = emptySet()

    init {
        loadHistory()
        viewModelScope.launch {
            combine(
                com.android.purebilibili.core.store.SettingsManager.getSearchHistoryRecordingEnabled(application),
                com.android.purebilibili.core.store.SettingsManager.getPrivacyModeEnabled(application),
            ) { recording, globalPrivacy -> recording to globalPrivacy }
                .collect { (recording, globalPrivacy) ->
                    _uiState.update { it.copy(recordSearchHistory = recording, globalPrivacyMode = globalPrivacy) }
                }
        }
        viewModelScope.launch {
            combine(
                com.android.purebilibili.core.store.SettingsManager.getSearchDiscoverSectionEnabled(application),
                com.android.purebilibili.core.store.SettingsManager.getSearchSuggestionsEnabled(application)
            ) { visible, personalized -> visible to personalized }
                .collect { (visible, personalized) ->
                    val wasVisible = discoverSectionEnabled
                    val wasPersonalized = searchSuggestionsEnabled
                    val wasLoaded = discoverSettingsLoaded
                    discoverSectionEnabled = visible
                    searchSuggestionsEnabled = personalized
                    discoverSettingsLoaded = true
                    if (!wasLoaded || visible != wasVisible || personalized != wasPersonalized) {
                        discoverRequestVersion++
                        if (wasPersonalized != personalized) {
                            _uiState.update { it.copy(discoverList = emptyList(), discoverListError = null) }
                        }
                        if (landingBootstrapStarted && visible) {
                            launch { refreshDiscoverInternal() }
                        }
                    }
                }
        }
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
                                SearchType.ALL -> resolveSearchEmptyStateReason(
                                    rawResultCount = it.searchResults.size + it.upResults.size + it.bangumiResults.size,
                                    visibleResultCount = newVideos.size + newUps.size + it.bangumiResults.size
                                )
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
            if (discoverSettingsLoaded && discoverSectionEnabled) launch { refreshDiscoverInternal() }
        }
    }

    fun onQueryChange(newQuery: String) {
        if (newQuery.isBlank()) suggestJob?.cancel()
        _uiState.update { it.withEditedSearchQuery(newQuery) }
        if (newQuery.isNotBlank()) loadSuggestions(newQuery)
    }

    fun toggleSearchHistoryRecording() {
        if (_uiState.value.globalPrivacyMode) return
        val enabled = !_uiState.value.recordSearchHistory
        _uiState.update { it.copy(recordSearchHistory = enabled) }
        viewModelScope.launch {
            com.android.purebilibili.core.store.SettingsManager.setSearchHistoryRecordingEnabled(
                getApplication<android.app.Application>(), enabled,
            )
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
        pagingJobs.values.forEach { it.cancel() }
        pagingJobs.clear()
        pagingSessions.clear()
        pagingGenerations.clear()
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
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300) // 防抖 300ms
            val result = SearchRepository.getSuggest(trimmed)
            result.onSuccess { suggestions ->
                if (trimmed != _uiState.value.query.trim()) return@onSuccess
                _uiState.update {
                    it.copy(
                        suggestions = suggestions
                            .map { tag -> tag.toSearchSuggestionUiModel() }
                            .take(8)
                    )
                }
            }.onFailure {
                if (trimmed != _uiState.value.query.trim()) return@onFailure
                _uiState.update { it.copy(suggestions = emptyList()) }
            }
        }
    }
    
    //  切换搜索类型
    fun setSearchType(type: SearchType) {
        val beforeSwitch = _uiState.value
        if (beforeSwitch.searchType == type) return

        val normalizedQuery = beforeSwitch.submittedQuery
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
                .copy(totalResults = beforeSwitch.searchTypeCounts[type] ?: -1)
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
                matchResults = emptyList(),
                liveResults = emptyList(),
                liveUserResults = emptyList(),
                articleResults = emptyList(),
                topicResults = emptyList(),
                photoResults = emptyList()
            )

        if (normalizedQuery.isNotBlank() && beforeSwitch.showResults) {
            search(normalizedQuery, updateDraft = false)
        }
    }
    
    //  设置搜索排序
    fun setSearchOrder(order: SearchOrder) {
        if (_uiState.value.searchOrder == order) return
        _uiState.update { it.copy(searchOrder = order) }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }
    
    //  设置时长筛选；空集合表示“全部时长”，多个值会在仓库层分别请求后合并去重。
    fun toggleSearchDuration(duration: SearchDuration) {
        _uiState.update {
            it.copy(searchDurations = toggleSearchDurationSelection(it.searchDurations, duration))
        }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    /** BiliPai-style single duration selection for the filter sheet. */
    fun setSearchDuration(duration: SearchDuration) {
        _uiState.update {
            it.copy(searchDurations = resolveSearchDurationSelection(duration))
        }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun setVideoTid(tid: Int) {
        _uiState.update { it.copy(videoTid = tid) }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
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
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
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
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun setUpOrder(order: SearchUpOrder) {
        _uiState.update { it.copy(upOrder = order) }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun setUpOrderSort(orderSort: SearchOrderSort) {
        _uiState.update { it.copy(upOrderSort = orderSort) }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun setUpUserType(userType: SearchUserType) {
        _uiState.update { it.copy(upUserType = userType) }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun setLiveOrder(order: SearchLiveOrder) {
        _uiState.update { it.copy(liveOrder = order) }
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun setArticleOrder(order: SearchOrder) = updateSearchFilter { it.copy(articleOrder = order) }

    fun setArticleCategory(category: SearchArticleCategory) =
        updateSearchFilter { it.copy(articleCategory = category) }

    fun setPhotoOrder(order: SearchOrder) = updateSearchFilter { it.copy(photoOrder = order) }

    fun setPhotoCategory(category: SearchPhotoCategory) =
        updateSearchFilter { it.copy(photoCategory = category) }

    private fun updateSearchFilter(transform: (SearchUiState) -> SearchUiState) {
        _uiState.update(transform)
        if (_uiState.value.submittedQuery.isNotBlank() && _uiState.value.showResults) {
            search(_uiState.value.submittedQuery, updateDraft = false)
        }
    }

    fun search(keyword: String, updateDraft: Boolean = true) {
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
            _uiState.value.submittedQuery.trim() != normalizedKeyword
        val searchSessionId = activeSearchSessionId + 1L
        activeSearchSessionId = searchSessionId
        if (shouldClearCachedPages) {
            pagingJobs.values.forEach { it.cancel() }
            pagingJobs.clear()
            pagingSessions.clear()
            pagingGenerations.clear()
        }
        pagingJobs.remove(searchType)?.cancel()
        pagingGenerations[searchType] = searchSessionId

        _uiState.update {
            it.copy(
                query = if (updateDraft) normalizedKeyword else it.query,
                submittedQuery = normalizedKeyword,
                isSearching = true,
                showResults = true,
                searchSessionId = searchSessionId,
                searchTypeCounts = if (shouldClearCachedPages) emptyMap() else it.searchTypeCounts,
                totalResults = -1,
                suggestions = emptyList(),
                error = null,
                loadMoreError = null,
                easterEggMessage = easterEggMessage,
                currentPage = 1,
                hasMoreResults = false,
                isLoadingMore = false,
                emptyStateReason = SearchEmptyStateReason.NONE,
                searchResults = if (shouldClearCachedPages || searchType == SearchType.VIDEO) emptyList() else it.searchResults,
                upResults = if (shouldClearCachedPages) emptyList() else it.upResults,
                bangumiResults = if (shouldClearCachedPages) emptyList() else it.bangumiResults,
                matchResults = if (shouldClearCachedPages) emptyList() else it.matchResults,
                liveResults = if (shouldClearCachedPages) emptyList() else it.liveResults,
                liveUserResults = if (shouldClearCachedPages) emptyList() else it.liveUserResults,
                articleResults = if (shouldClearCachedPages) emptyList() else it.articleResults,
                topicResults = if (shouldClearCachedPages) emptyList() else it.topicResults,
                photoResults = if (shouldClearCachedPages) emptyList() else it.photoResults,
                resultPages = if (shouldClearCachedPages) emptyMap() else it.resultPages
            )
        }
        if (updateDraft) saveHistory(normalizedKeyword)
        com.android.purebilibili.core.util.AnalyticsHelper.logSearch(normalizedKeyword)

        val filters = _uiState.value
        val session = SearchPagingSession(
            loadPage = { page -> loadSearchChunk(filters, page) },
            onChanged = { pages, loads ->
                if (pagingGenerations[searchType] == searchSessionId &&
                    _uiState.value.submittedQuery == normalizedKeyword && _uiState.value.showResults
                ) {
                    val last = pages.lastOrNull()
                    val refresh = loads?.refresh
                    val append = loads?.append
                    val merged = mergeSearchChunks(pages).copy(
                        query = normalizedKeyword,
                        isSearching = refresh is androidx.paging.LoadState.Loading,
                        isLoadingMore = append is androidx.paging.LoadState.Loading,
                        error = if (refresh is androidx.paging.LoadState.Error) refresh.error.message ?: "搜索失败" else null,
                        loadMoreError = if (append is androidx.paging.LoadState.Error) append.error.message ?: "加载更多失败" else null,
                    )
                    _uiState.update { current ->
                        val cached = current.withSearchResultPage(searchType, merged)
                        val counted = if (last == null) cached else cached.copy(
                            searchTypeCounts = cached.searchTypeCounts + (searchType to last.info.totalResults))
                        if (current.searchType == searchType) counted.withSearchResultPageMirrored(searchType, merged)
                            .copy(totalResults = last?.info?.totalResults ?: -1)
                        else counted
                    }
                }
            },
        )
        pagingSessions[searchType] = session
        pagingJobs[searchType] = viewModelScope.launch { session.run() }
    }

    fun loadMoreResults() {
        ensureBlockedUpObserver()
        val state = _uiState.value
        if (!state.isSearching && !state.isLoadingMore && state.submittedQuery.isNotBlank()) {
            pagingSessions[state.searchType]?.loadMore()
        }
    }


    private suspend fun loadSearchChunk(filters: SearchUiState, number: Int): SearchPageChunk {
        val keyword = filters.submittedQuery
        val empty = SearchResultPageUiState(query = keyword)
        suspend fun videos(items: List<VideoItem>): List<VideoItem> {
            val native = items.filter { it.owner.mid !in blockedMids }
            val builtin = com.android.purebilibili.core.plugin.PluginManager.filterFeedItems(native, feedKind = FeedKind.SEARCH)
            return com.android.purebilibili.feature.plugin.filterVideoTagMatches(
                com.android.purebilibili.core.plugin.json.JsonPluginManager.filterVideos(builtin),
                bvid = { it.bvid },
                asVideoItem = { it },
            )
        }
        suspend fun ups(items: List<com.android.purebilibili.data.model.response.SearchUpItem>):
            List<com.android.purebilibili.data.model.response.SearchUpItem> {
            val visibleUps = items.filter { it.mid !in blockedMids }
            val nested = visibleUps.flatMap { it.res.orEmpty() }
            if (nested.isEmpty()) return visibleUps
            val visibleVideos = com.android.purebilibili.feature.plugin.filterVideoTagMatches(
                nested.filter { it.mid !in blockedMids },
                bvid = { it.bvid },
                asVideoItem = { it.toVideoItem() },
            )
            if (visibleVideos.size == nested.size) return visibleUps
            val allowed = visibleVideos.mapTo(hashSetOf()) { it.bvid }
            return visibleUps.map { up ->
                up.copy(res = up.res?.filter { it.mid !in blockedMids && it.bvid in allowed })
            }
        }
        fun chunk(page: SearchResultPageUiState, info: SearchRepository.SearchPageInfo, rawCount: Int): SearchPageChunk {
            val visible = page.searchResults.size + page.upResults.size + page.bangumiResults.size +
                page.matchResults.size + page.liveResults.size + page.liveUserResults.size + page.articleResults.size +
                page.topicResults.size + page.photoResults.size
            return SearchPageChunk(page.copy(currentPage = info.currentPage, totalPages = info.totalPages,
                hasMoreResults = info.hasMore, totalCount = visible,
                emptyStateReason = resolveSearchEmptyStateReason(rawCount, visible)), info)
        }
        return when (filters.searchType) {
            SearchType.ALL -> {
                val result = SearchRepository.searchAllPage(keyword = keyword, page = number,
                    order = filters.searchOrder, durations = filters.searchDurations, tids = filters.videoTid,
                    pubBegin = filters.pubBegin, pubEnd = filters.pubEnd).getOrThrow()
                // 综合搜索里的视频卡片同样要预热，否则点击仍会先铺横屏。
                VideoRepository.prefetchVerticalFlagsForVideos(result.videos)
                // UP 主卡片里嵌着「最近投稿」，这些视频也要预热：
                // 它们藏在 ups[].res 里，很容易漏掉。
                VideoRepository.prefetchVerticalFlagsForVideos(
                    result.ups.flatMap { up -> up.res.orEmpty().map { it.toVideoItem() } }
                )
                chunk(empty.copy(searchResults = videos(result.videos),
                    upResults = ups(result.ups),
                    bangumiResults = result.bangumi,
                    matchResults = result.matches), result.pageInfo,
                    result.videos.size + result.ups.size + result.bangumi.size + result.matches.size)
            }
            SearchType.VIDEO -> {
                val (items, info) = SearchRepository.searchWithDurations(keyword = keyword, page = number, order = filters.searchOrder, durations = filters.searchDurations, tids = filters.videoTid, pubBegin = filters.pubBegin, pubEnd = filters.pubEnd).getOrThrow()
                // 结果到手就预热横竖屏判定，点击进详情时首帧即进入正确布局。
                VideoRepository.prefetchVerticalFlagsForVideos(items)
                chunk(empty.copy(searchResults = videos(items)), info, items.size)
            }
            SearchType.UP -> {
                val (items, info) = SearchRepository.searchUp(keyword = keyword, page = number, order = filters.upOrder, orderSort = filters.upOrderSort, userType = filters.upUserType).getOrThrow()
                val filteredUps = ups(items)
                // UP 主卡片里嵌着「最近投稿」，这些视频同样要预热，
                // 否则点卡片里的竖屏视频仍会先铺横屏。
                VideoRepository.prefetchVerticalFlagsForVideos(
                    filteredUps.flatMap { up -> up.res.orEmpty().map { it.toVideoItem() } }
                )
                chunk(empty.copy(upResults = filteredUps), info, items.size)
            }
            SearchType.BANGUMI -> {
                val (items, info) = SearchRepository.searchBangumi(keyword, page = number).getOrThrow()
                chunk(empty.copy(bangumiResults = items), info, items.size)
            }
            SearchType.MEDIA_FT -> {
                val (items, info) = SearchRepository.searchMediaFt(keyword, page = number).getOrThrow()
                chunk(empty.copy(bangumiResults = items), info, items.size)
            }
            SearchType.LIVE -> {
                val (items, info) = SearchRepository.searchLive(keyword = keyword, page = number, order = filters.liveOrder).getOrThrow()
                chunk(empty.copy(liveResults = items.filter { it.uid !in blockedMids }), info, items.size)
            }
            SearchType.LIVE_USER -> {
                val (items, info) = SearchRepository.searchLiveUser(keyword = keyword, page = number).getOrThrow()
                chunk(empty.copy(liveUserResults = items.filter { it.uid !in blockedMids }), info, items.size)
            }
            SearchType.ARTICLE -> {
                val (items, info) = SearchRepository.searchArticle(keyword = keyword, page = number, order = filters.articleOrder, categoryId = filters.articleCategory.value).getOrThrow()
                chunk(empty.copy(articleResults = items), info, items.size)
            }
            SearchType.TOPIC -> {
                val (items, info) = SearchRepository.searchTopic(keyword = keyword, page = number).getOrThrow()
                chunk(empty.copy(topicResults = items), info, items.size)
            }
            SearchType.PHOTO -> {
                val (items, info) = SearchRepository.searchPhoto(keyword = keyword, page = number, order = filters.photoOrder, categoryId = filters.photoCategory.value).getOrThrow()
                chunk(empty.copy(photoResults = items), info, items.size)
            }
        }
    }

    private fun mergeSearchChunks(chunks: List<SearchPageChunk>): SearchResultPageUiState {
        val last = chunks.lastOrNull()?.page ?: return SearchResultPageUiState()
        val pages = chunks.map { it.page }
        val merged = last.copy(
            searchResults = pages.flatMap { it.searchResults }.distinctBy { it.bvid }.filter { it.owner.mid !in blockedMids },
            upResults = pages.flatMap { it.upResults }.distinctBy { it.mid }.filter { it.mid !in blockedMids },
            bangumiResults = pages.flatMap { it.bangumiResults }.distinctBy { it.seasonId },
            matchResults = pages.flatMap { it.matchResults }.distinctBy { it.id },
            liveResults = pages.flatMap { it.liveResults }.distinctBy { it.roomid }.filter { it.uid !in blockedMids },
            liveUserResults = pages.flatMap { it.liveUserResults }.distinctBy { it.uid }.filter { it.uid !in blockedMids },
            articleResults = pages.flatMap { it.articleResults }.distinctBy { it.id },
            topicResults = pages.flatMap { it.topicResults }.distinctBy { it.topicId },
            photoResults = pages.flatMap { it.photoResults }.distinctBy { it.id },
        )
        val visibleCount = merged.searchResults.size + merged.upResults.size + merged.bangumiResults.size +
            merged.matchResults.size + merged.liveResults.size + merged.liveUserResults.size + merged.articleResults.size +
            merged.topicResults.size + merged.photoResults.size
        return merged.copy(totalCount = visibleCount,
            emptyStateReason = if (visibleCount > 0) SearchEmptyStateReason.NONE else last.emptyStateReason)
    }

    private suspend fun loadDefaultSearchHintInternal() {
        SearchRepository.getDefaultSearchHint()
            .onSuccess { hint ->
                _uiState.update {
                    it.copy(defaultSearchHint = hint.hint, defaultSearchKeyword = hint.keyword)
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
        if (!discoverSectionEnabled || !discoverSettingsLoaded) return
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
        val requestVersion = ++discoverRequestVersion
        _uiState.update { it.copy(isRefreshingDiscoverList = true, discoverListError = null) }
        val result = SearchRepository.getSearchRecommend(personalizedEnabled = searchSuggestionsEnabled)
        if (requestVersion != discoverRequestVersion || !discoverSectionEnabled) return
        result.onSuccess { list ->
            _uiState.update {
                it.copy(
                    discoverList = list.map { item -> item.toSearchKeywordUiModel() },
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
        // Capture the choice at submission; enabling recording later cannot save an incognito query.
        if (!shouldRecordSearchHistory(_uiState.value.recordSearchHistory, _uiState.value.globalPrivacyMode)) return
        viewModelScope.launch {
            try {
                //  隐私无痕模式检查：如果启用则跳过保存搜索历史
                val context = getApplication<android.app.Application>()
                if (com.android.purebilibili.core.store.SettingsManager.isPrivacyModeEnabledSync(context) ||
                    !_uiState.value.recordSearchHistory
                ) {
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
