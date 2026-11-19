package com.android.purebilibili.feature.bangumi

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.data.model.response.BangumiIndexConditionData
import com.android.purebilibili.data.model.response.BangumiItem
import com.android.purebilibili.data.model.response.BangumiSearchItem
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BangumiPagedState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

@Immutable
data class BangumiTimelineHubState(
    val days: List<TimelineDay> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@Immutable
data class BangumiHomeState(
    val recommendations: BangumiPagedState<BangumiItem> = BangumiPagedState(),
    val follows: BangumiPagedState<FollowBangumiItem> = BangumiPagedState(),
    val followTotal: Int? = null,
    val timeline: BangumiTimelineHubState = BangumiTimelineHubState(),
)

@Immutable
data class BangumiIndexState(
    val conditions: List<BangumiIndexFilterGroupUi> = emptyList(),
    val selectedParams: Map<String, String> = emptyMap(),
    val isConditionLoading: Boolean = false,
    val conditionError: String? = null,
    val isExpanded: Boolean = false,
    val results: BangumiPagedState<BangumiItem> = BangumiPagedState(),
)

@Immutable
data class BangumiFollowManagerState(
    val content: BangumiPagedState<FollowBangumiItem> = BangumiPagedState(),
    val selectedIds: Set<Long> = emptySet(),
    val isSelecting: Boolean = false,
    val isMutating: Boolean = false,
)

@Immutable
data class BangumiSearchHubState(
    val query: String = "",
    val category: BangumiIndexCategory = BangumiIndexCategory.BANGUMI,
    val results: BangumiPagedState<BangumiSearchItem> = BangumiPagedState(),
)

@Immutable
data class BangumiHubUiState(
    val isLoggedIn: Boolean = false,
    val accountGeneration: Long = TokenManager.accountIdentity.value.generation,
    val channel: BangumiChannel = BangumiChannel.BANGUMI,
    val page: BangumiHubPage = BangumiHubPage.HOME,
    val pageBeforeSearch: BangumiHubPage = BangumiHubPage.HOME,
    val homeStates: Map<BangumiChannel, BangumiHomeState> = emptyMap(),
    val indexCategory: BangumiIndexCategory = BangumiIndexCategory.BANGUMI,
    val indexCategories: Map<BangumiChannel, BangumiIndexCategory> = mapOf(
        BangumiChannel.BANGUMI to BangumiIndexCategory.BANGUMI,
        BangumiChannel.CINEMA to BangumiIndexCategory.CINEMA_ALL,
    ),
    val indexStates: Map<BangumiIndexCategory, BangumiIndexState> = emptyMap(),
    val followStatus: BangumiFollowStatus = BangumiFollowStatus.WATCHING,
    val followStates: Map<Pair<BangumiChannel, BangumiFollowStatus>, BangumiFollowManagerState> = emptyMap(),
    val search: BangumiSearchHubState = BangumiSearchHubState(),
    val showPgcTimeline: Boolean = true,
)

class BangumiHubViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BangumiHubUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var initialized = false
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private val homeJobs = mutableMapOf<String, Job>()
    private val conditionJobs = mutableMapOf<BangumiIndexCategory, Job>()
    private val indexJobs = mutableMapOf<BangumiIndexCategory, Job>()
    private val followJobs = mutableMapOf<Pair<BangumiChannel, BangumiFollowStatus>, Job>()
    private var searchJob: Job? = null

    init {
        // Timeline follow flags, follows, management and search belong to an account.
        // Public index and recommendation requests keep their ViewModel lifetime.
        accountRequests.observe {
            searchJob = null
            followJobs.clear()
            BangumiChannel.entries.forEach {
                homeJobs.remove("follows_${it.name}")
                homeJobs.remove("timeline_${it.name}")
            }
            val isLoggedIn = !TokenManager.sessDataCache.isNullOrBlank()
            _uiState.update { state ->
                val currentPage = if (state.page == BangumiHubPage.SEARCH) state.pageBeforeSearch else state.page
                state.copy(
                    isLoggedIn = isLoggedIn,
                    accountGeneration = accountRequests.identity.generation,
                    homeStates = state.homeStates.mapValues { (_, home) ->
                        home.copy(
                            follows = BangumiPagedState(),
                            followTotal = null,
                            timeline = BangumiTimelineHubState(),
                        )
                    },
                    followStates = emptyMap(),
                    search = BangumiSearchHubState(category = state.search.category),
                    page = if (!isLoggedIn && currentPage == BangumiHubPage.FOLLOW) {
                        BangumiHubPage.HOME
                    } else currentPage,
                )
            }
            if (initialized) {
                val state = _uiState.value
                loadHomeFollows(state.channel, reset = true)
                if (state.page == BangumiHubPage.HOME && state.showPgcTimeline) {
                    loadTimeline(state.channel)
                }
                if (state.page == BangumiHubPage.FOLLOW) {
                    loadFollowManager(state.channel, state.followStatus, reset = true)
                }
            }
        }
    }

    fun initialize(initialType: Int) {
        if (initialized) return
        initialized = true
        val channel = resolveBangumiChannel(initialType)
        _uiState.update {
            it.copy(
                isLoggedIn = !TokenManager.sessDataCache.isNullOrBlank(),
                channel = channel,
                indexCategory = resolveDefaultBangumiIndexCategory(channel),
            )
        }
        refreshHome(channel)
    }

    fun selectChannel(channel: BangumiChannel) {
        val current = _uiState.value
        if (current.channel == channel || followState(currentFollowKey()).isMutating) return
        if (current.page == BangumiHubPage.FOLLOW) clearFollowSelection()
        val category = current.indexCategories[channel] ?: resolveDefaultBangumiIndexCategory(channel)
        _uiState.update { it.copy(channel = channel, indexCategory = category) }
        when (current.page) {
            BangumiHubPage.HOME -> ensureHomeLoaded(channel)
            BangumiHubPage.INDEX -> ensureIndexLoaded(category)
            BangumiHubPage.FOLLOW -> ensureFollowLoaded(channel, current.followStatus)
            BangumiHubPage.SEARCH -> Unit
        }
    }

    fun setShowPgcTimeline(enabled: Boolean) {
        val current = _uiState.value
        if (current.showPgcTimeline == enabled) return
        _uiState.update { it.copy(showPgcTimeline = enabled) }
        if (enabled) {
            loadTimeline(current.channel)
        } else {
            homeJobs["timeline_${BangumiChannel.BANGUMI.name}"]?.cancel()
            updateHomeState(BangumiChannel.BANGUMI) { it.copy(timeline = BangumiTimelineHubState()) }
        }
    }

    fun refreshHome(channel: BangumiChannel = _uiState.value.channel) {
        loadHomeRecommendations(channel, reset = true)
        if (_uiState.value.isLoggedIn) loadHomeFollows(channel, reset = true)
        loadTimeline(channel)
    }

    fun loadMoreHomeRecommendations() {
        loadHomeRecommendations(_uiState.value.channel, reset = false)
    }

    fun loadMoreHomeFollows() {
        loadHomeFollows(_uiState.value.channel, reset = false)
    }

    fun refreshHomeFollows() {
        loadHomeFollows(_uiState.value.channel, reset = true)
    }

    fun retryTimeline() {
        loadTimeline(_uiState.value.channel)
    }

    fun openIndex() {
        val current = _uiState.value
        val category = current.indexCategories[current.channel]
            ?: resolveDefaultBangumiIndexCategory(current.channel)
        _uiState.update { it.copy(page = BangumiHubPage.INDEX, indexCategory = category) }
        ensureIndexLoaded(category)
    }

    fun selectIndexCategory(category: BangumiIndexCategory) {
        if (_uiState.value.indexCategory == category) return
        _uiState.update {
            it.copy(
                indexCategory = category,
                indexCategories = it.indexCategories + (it.channel to category),
            )
        }
        ensureIndexLoaded(category)
    }

    fun retryIndexConditions() {
        loadIndexConditions(_uiState.value.indexCategory)
    }

    fun selectIndexFilter(
        group: BangumiIndexFilterGroupUi,
        choice: BangumiIndexChoice,
    ) {
        val category = _uiState.value.indexCategory
        val current = indexState(category)
        val params = updateBangumiIndexParams(current.selectedParams, group, choice)
        updateIndexState(category) {
            it.copy(
                selectedParams = params,
                results = BangumiPagedState(),
            )
        }
        loadIndexResults(category, reset = true)
    }

    fun toggleIndexFiltersExpanded() {
        val category = _uiState.value.indexCategory
        updateIndexState(category) { it.copy(isExpanded = !it.isExpanded) }
    }

    fun retryIndexResults() = loadIndexResults(_uiState.value.indexCategory, reset = true)

    fun loadMoreIndexResults() = loadIndexResults(_uiState.value.indexCategory, reset = false)

    fun openFollowManager() {
        val current = _uiState.value
        val status = BangumiFollowStatus.WATCHING
        _uiState.update { it.copy(page = BangumiHubPage.FOLLOW, followStatus = status) }
        ensureFollowLoaded(current.channel, status)
    }

    fun selectFollowStatus(status: BangumiFollowStatus) {
        if (_uiState.value.followStatus == status || followState(currentFollowKey()).isMutating) return
        clearFollowSelection()
        _uiState.update { it.copy(followStatus = status) }
        ensureFollowLoaded(_uiState.value.channel, status)
    }

    fun refreshFollowManager() {
        val state = _uiState.value
        loadFollowManager(state.channel, state.followStatus, reset = true)
    }

    fun loadMoreFollowManager() {
        val state = _uiState.value
        loadFollowManager(state.channel, state.followStatus, reset = false)
    }

    fun toggleFollowSelection(seasonId: Long) {
        val key = currentFollowKey()
        if (seasonId <= 0L || followState(key).isMutating) return
        updateFollowState(key) {
            it.copy(
                isSelecting = true,
                selectedIds = updateBangumiSelection(it.selectedIds, seasonId),
            )
        }
    }

    fun selectAllFollowItems() {
        val key = currentFollowKey()
        if (followState(key).isMutating) return
        updateFollowState(key) { current ->
            val allIds = current.content.items.mapNotNull { it.seasonId.takeIf { id -> id > 0L } }.toSet()
            current.copy(selectedIds = if (current.selectedIds.containsAll(allIds)) emptySet() else allIds)
        }
    }

    fun clearFollowSelection() {
        updateFollowState(currentFollowKey()) { it.copy(selectedIds = emptySet(), isSelecting = false) }
    }

    fun moveSelectedFollowItems(target: BangumiFollowStatus) {
        if (!_uiState.value.isLoggedIn || !accountRequests.isCurrent()) return
        val key = currentFollowKey()
        val selected = followState(key).selectedIds
        if (selected.isEmpty() || target == key.second || followState(key).isMutating) return
        updateFollowState(key) { it.copy(isMutating = true) }
        accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.updateBangumiFollowStatuses(selected, target.value)
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = {
                    removeFollowItemsAfterMutation(key, selected, decrementTotal = false)
                    refreshFollowStatesAfterMove(key, target)
                    _messages.emit("已标记为${target.label}")
                },
                onFailure = { error ->
                    updateFollowState(key) { it.copy(isMutating = false) }
                    _messages.emit(error.message ?: "更新失败")
                },
            )
        }
    }

    fun updateSingleFollowItem(
        seasonId: Long,
        target: BangumiFollowStatus,
    ) {
        if (!_uiState.value.isLoggedIn || !accountRequests.isCurrent()) return
        val key = currentFollowKey()
        if (seasonId <= 0L || target == key.second || followState(key).isMutating) return
        updateFollowState(key) { it.copy(isMutating = true) }
        accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.updateBangumiFollowStatus(seasonId, target.value)
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = {
                    removeFollowItemsAfterMutation(key, setOf(seasonId), decrementTotal = false)
                    refreshFollowStatesAfterMove(key, target)
                    _messages.emit("已标记为${target.label}")
                },
                onFailure = { error ->
                    updateFollowState(key) { it.copy(isMutating = false) }
                    _messages.emit(error.message ?: "更新失败")
                },
            )
        }
    }

    fun unfollowSingleItem(seasonId: Long) {
        if (!_uiState.value.isLoggedIn || !accountRequests.isCurrent()) return
        val key = currentFollowKey()
        if (seasonId <= 0L || followState(key).isMutating) return
        updateFollowState(key) { it.copy(isMutating = true) }
        accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.unfollowBangumi(seasonId)
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = {
                    removeFollowItemsAfterMutation(key, setOf(seasonId), decrementTotal = true)
                    loadFollowManager(key.first, key.second, reset = true)
                    loadHomeFollows(key.first, reset = true)
                    _messages.emit(if (key.first == BangumiChannel.BANGUMI) "已取消追番" else "已取消追剧")
                },
                onFailure = { error ->
                    updateFollowState(key) { it.copy(isMutating = false) }
                    _messages.emit(error.message ?: "取消失败")
                },
            )
        }
    }

    fun openSearch() {
        val follow = followState(currentFollowKey())
        if (follow.isMutating || follow.isSelecting) return
        val page = _uiState.value.page
        _uiState.update {
            val category = resolveDefaultBangumiSearchCategory(
                channel = it.channel,
                preferred = it.indexCategories[it.channel],
            )
            it.copy(
                page = BangumiHubPage.SEARCH,
                pageBeforeSearch = if (page == BangumiHubPage.SEARCH) it.pageBeforeSearch else page,
                search = BangumiSearchHubState(category = category),
            )
        }
    }

    fun selectSearchCategory(category: BangumiIndexCategory) {
        val current = _uiState.value
        if (category !in resolveBangumiSearchCategories(current.channel)) return
        if (current.search.category == category) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                search = it.search.copy(
                    category = category,
                    results = BangumiPagedState(),
                ),
            )
        }
        current.search.query.takeIf(String::isNotBlank)?.let(::search)
    }

    fun search(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        val currentSearch = _uiState.value.search
        val category = currentSearch.category
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                search = it.search.copy(
                    query = normalized,
                    results = BangumiPagedState(isLoading = true),
                ),
            )
        }
        searchJob = accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.searchBangumi(
                keyword = normalized,
                seasonType = resolveBangumiSearchSeasonType(category),
                page = 1,
            )
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = { data ->
                    val latest = _uiState.value.search
                    if (latest.query != normalized || latest.category != category) return@fold
                    _uiState.update {
                        it.copy(
                            search = it.search.copy(
                                results = BangumiPagedState(
                                    items = filterBangumiSearchItems(
                                        items = data.result.orEmpty(),
                                        category = category,
                                    ).distinctBy(::resolveBangumiSearchItemBusinessKey),
                                    page = data.page.coerceAtLeast(1),
                                    hasMore = data.page < data.numPages,
                                ),
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    val latest = _uiState.value.search
                    if (latest.query != normalized || latest.category != category) return@fold
                    _uiState.update {
                        it.copy(search = it.search.copy(results = BangumiPagedState(error = error.message ?: "搜索失败")))
                    }
                },
            )
        }
    }

    fun loadMoreSearch() {
        val current = _uiState.value.search
        val results = current.results
        if (!results.hasMore || results.isLoading || results.isLoadingMore || current.query.isBlank()) return
        val nextPage = results.page + 1
        val category = current.category
        _uiState.update {
            it.copy(search = it.search.copy(results = results.copy(isLoadingMore = true, error = null)))
        }
        searchJob = accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.searchBangumi(
                keyword = current.query,
                seasonType = resolveBangumiSearchSeasonType(category),
                page = nextPage,
            )
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = { data ->
                    val latest = _uiState.value.search
                    if (latest.query != current.query || latest.category != category) return@fold
                    _uiState.update { state ->
                        val existing = state.search.results.items
                        state.copy(
                            search = state.search.copy(
                                results = state.search.results.copy(
                                    items = mergeBangumiPagedItems(
                                        existing = existing,
                                        incoming = filterBangumiSearchItems(
                                            items = data.result.orEmpty(),
                                            category = category,
                                        ),
                                        reset = false,
                                        keyOf = ::resolveBangumiSearchItemBusinessKey,
                                    ),
                                    page = nextPage,
                                    hasMore = data.page < data.numPages,
                                    isLoadingMore = false,
                                ),
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    val latest = _uiState.value.search
                    if (latest.query != current.query || latest.category != category) return@fold
                    _uiState.update {
                        it.copy(search = it.search.copy(results = it.search.results.copy(isLoadingMore = false, error = error.message)))
                    }
                },
            )
        }
    }

    fun consumeBack(): Boolean {
        val state = _uiState.value
        val isSelecting = state.page == BangumiHubPage.FOLLOW && followState(currentFollowKey()).isSelecting
        return when (resolveBangumiBackAction(state.page, isSelecting)) {
            BangumiBackAction.CLEAR_SELECTION -> {
                clearFollowSelection()
                true
            }
            BangumiBackAction.CLOSE_SEARCH -> {
                searchJob?.cancel()
                _uiState.update { it.copy(page = it.pageBeforeSearch, search = BangumiSearchHubState()) }
                true
            }
            BangumiBackAction.SHOW_HOME -> {
                _uiState.update { it.copy(page = BangumiHubPage.HOME) }
                ensureHomeLoaded(state.channel)
                true
            }
            BangumiBackAction.EXIT_SCREEN -> false
        }
    }

    fun prepareHomeChannel(channel: BangumiChannel) {
        if (initialized) ensureHomeLoaded(channel)
    }

    private fun ensureHomeLoaded(channel: BangumiChannel) {
        val home = homeState(channel)
        if (home.recommendations.page == 0 && !home.recommendations.isLoading) {
            refreshHome(channel)
            return
        }
        if (_uiState.value.isLoggedIn && home.follows.page == 0 && !home.follows.isLoading) {
            loadHomeFollows(channel, reset = true)
        }
        if (_uiState.value.showPgcTimeline && home.timeline.days.isEmpty() &&
            !home.timeline.isLoading && !home.timeline.isRefreshing
        ) {
            loadTimeline(channel)
        }
    }

    private fun loadHomeRecommendations(channel: BangumiChannel, reset: Boolean) {
        val current = homeState(channel).recommendations
        if (!reset && (!current.hasMore || current.isLoadingMore || current.isLoading || current.isRefreshing)) return
        if (reset && current.isRefreshing) return
        val nextPage = if (reset) 1 else current.page + 1
        val jobKey = "recommendations_${channel.name}"
        if (reset) homeJobs[jobKey]?.cancel()
        updateHomeState(channel) { home ->
            home.copy(
                recommendations = current.copy(
                    isLoading = reset && current.items.isEmpty(),
                    isRefreshing = reset && current.items.isNotEmpty(),
                    isLoadingMore = !reset,
                    error = null,
                ),
            )
        }
        homeJobs[jobKey] = viewModelScope.launch {
            val result = BangumiRepository.getBangumiIndexPage(
                seasonType = 1,
                indexType = if (channel == BangumiChannel.CINEMA) 102 else null,
                type = 1,
                page = nextPage,
                pageSize = 20,
                params = DEFAULT_HOME_INDEX_PARAMS,
            )
            coroutineContext.ensureActive()
            result.fold(
                onSuccess = { data ->
                    updateHomeState(channel) { home ->
                        val previous = if (reset) emptyList() else home.recommendations.items
                        home.copy(
                            recommendations = BangumiPagedState(
                                items = mergeBangumiPagedItems(previous, data.list.orEmpty(), reset = false) {
                                    resolveBangumiIndexItemBusinessKey(it)
                                },
                                page = nextPage,
                                hasMore = data.hasNext == 1,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    updateHomeState(channel) { home ->
                        home.copy(
                            recommendations = home.recommendations.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = error.message ?: "加载推荐失败",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun loadHomeFollows(channel: BangumiChannel, reset: Boolean) {
        if (!_uiState.value.isLoggedIn || !accountRequests.isCurrent()) return
        val current = homeState(channel).follows
        if (!reset && (!current.hasMore || current.isLoadingMore || current.isLoading || current.isRefreshing)) return
        if (reset && current.isRefreshing) return
        val nextPage = if (reset) 1 else current.page + 1
        val jobKey = "follows_${channel.name}"
        if (reset) homeJobs[jobKey]?.cancel()
        updateHomeState(channel) { home ->
            home.copy(
                follows = current.copy(
                    isLoading = reset && current.items.isEmpty(),
                    isRefreshing = reset && current.items.isNotEmpty(),
                    isLoadingMore = !reset,
                    error = null,
                ),
            )
        }
        homeJobs[jobKey] = accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.getMyFollowBangumi(
                type = channel.followType,
                page = nextPage,
            )
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = { data ->
                    updateHomeState(channel) { home ->
                        val previous = if (reset) emptyList() else home.follows.items
                        home.copy(
                            follows = BangumiPagedState(
                                items = mergeBangumiPagedItems(previous, data.list.orEmpty(), reset = false) {
                                    resolveMyFollowItemBusinessKey(it)
                                },
                                page = nextPage,
                                hasMore = data.hasNextPage(nextPage),
                            ),
                            followTotal = data.total ?: home.followTotal.takeUnless { reset },
                        )
                    }
                },
                onFailure = { error ->
                    updateHomeState(channel) { home ->
                        home.copy(
                            follows = home.follows.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = error.message ?: "加载追番失败",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun loadTimeline(channel: BangumiChannel) {
        if (channel != BangumiChannel.BANGUMI || !_uiState.value.showPgcTimeline ||
            !accountRequests.isCurrent()
        ) return
        val current = homeState(channel).timeline
        val jobKey = "timeline_${channel.name}"
        homeJobs[jobKey]?.cancel()
        updateHomeState(channel) { home ->
            home.copy(
                timeline = current.copy(
                    isLoading = current.days.isEmpty(),
                    isRefreshing = current.days.isNotEmpty(),
                    error = null,
                ),
            )
        }
        homeJobs[jobKey] = accountRequests.launch {
            val identity = accountRequests.identity
            val anime = async { BangumiRepository.getTimeline(type = 1, before = 6, after = 6) }
            val guochuang = async { BangumiRepository.getTimeline(type = 4, before = 6, after = 6) }
            val primaryResult = anime.await()
            val secondaryResult = guochuang.await()
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            val primaryDays = primaryResult.getOrNull()
            val secondaryDays = secondaryResult.getOrNull()
            if (primaryDays != null || secondaryDays != null) {
                updateHomeState(channel) { home ->
                    home.copy(
                        timeline = BangumiTimelineHubState(
                            days = mergeBangumiTimelineDays(primaryDays.orEmpty(), secondaryDays.orEmpty()),
                        ),
                    )
                }
            } else {
                val message = primaryResult.exceptionOrNull()?.message
                    ?: secondaryResult.exceptionOrNull()?.message
                    ?: "加载时间表失败"
                updateHomeState(channel) { home ->
                    home.copy(timeline = home.timeline.copy(isLoading = false, isRefreshing = false, error = message))
                }
            }
        }
    }

    private fun ensureIndexLoaded(category: BangumiIndexCategory) {
        val current = indexState(category)
        when {
            current.conditions.isEmpty() && !current.isConditionLoading -> loadIndexConditions(category)
            current.results.page == 0 && !current.results.isLoading -> loadIndexResults(category, reset = true)
        }
    }

    private fun loadIndexConditions(category: BangumiIndexCategory) {
        conditionJobs[category]?.cancel()
        updateIndexState(category) {
            it.copy(isConditionLoading = true, conditionError = null)
        }
        conditionJobs[category] = viewModelScope.launch {
            BangumiRepository.getBangumiIndexConditions(
                seasonType = category.seasonType,
                indexType = category.indexType,
                type = 0,
            ).fold(
                onSuccess = { data ->
                    val groups = buildBangumiIndexFilterGroups(data)
                    updateIndexState(category) {
                        it.copy(
                            conditions = groups,
                            selectedParams = buildDefaultBangumiIndexParams(groups),
                            isConditionLoading = false,
                        )
                    }
                    loadIndexResults(category, reset = true)
                },
                onFailure = { error ->
                    updateIndexState(category) {
                        it.copy(isConditionLoading = false, conditionError = error.message ?: "加载筛选失败")
                    }
                },
            )
        }
    }

    private fun loadIndexResults(category: BangumiIndexCategory, reset: Boolean) {
        val current = indexState(category)
        if (current.conditions.isEmpty()) return
        val results = current.results
        if (!reset && (!results.hasMore || results.isLoadingMore || results.isLoading || results.isRefreshing)) return
        val nextPage = if (reset) 1 else results.page + 1
        if (reset) indexJobs[category]?.cancel()
        updateIndexState(category) {
            it.copy(
                results = results.copy(
                    isLoading = reset && results.items.isEmpty(),
                    isRefreshing = reset && results.items.isNotEmpty(),
                    isLoadingMore = !reset,
                    error = null,
                ),
            )
        }
        indexJobs[category] = viewModelScope.launch {
            val result = BangumiRepository.getBangumiIndexPage(
                seasonType = category.seasonType,
                indexType = category.indexType,
                type = 0,
                page = nextPage,
                params = current.selectedParams,
            )
            coroutineContext.ensureActive()
            result.fold(
                onSuccess = { data ->
                    updateIndexState(category) { state ->
                        val previous = if (reset) emptyList() else state.results.items
                        state.copy(
                            results = BangumiPagedState(
                                items = mergeBangumiPagedItems(previous, data.list.orEmpty(), reset = false) {
                                    resolveBangumiIndexItemBusinessKey(it)
                                },
                                page = nextPage,
                                hasMore = data.hasNext == 1,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    updateIndexState(category) {
                        it.copy(
                            results = it.results.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = error.message ?: "加载索引失败",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun ensureFollowLoaded(channel: BangumiChannel, status: BangumiFollowStatus) {
        val current = followState(channel to status).content
        if (current.page == 0 && !current.isLoading) loadFollowManager(channel, status, reset = true)
    }

    private fun loadFollowManager(
        channel: BangumiChannel,
        status: BangumiFollowStatus,
        reset: Boolean,
    ) {
        if (!_uiState.value.isLoggedIn || !accountRequests.isCurrent()) return
        val key = channel to status
        val current = followState(key)
        val content = current.content
        if (current.isMutating) return
        if (!reset && (!content.hasMore || content.isLoadingMore || content.isLoading || content.isRefreshing)) return
        val nextPage = if (reset) 1 else content.page + 1
        if (reset) followJobs[key]?.cancel()
        updateFollowState(key) {
            it.copy(
                content = content.copy(
                    isLoading = reset && content.items.isEmpty(),
                    isRefreshing = reset && content.items.isNotEmpty(),
                    isLoadingMore = !reset,
                    error = null,
                ),
                selectedIds = if (reset) emptySet() else it.selectedIds,
                isSelecting = !reset && it.isSelecting,
            )
        }
        followJobs[key] = accountRequests.launch {
            val identity = accountRequests.identity
            val result = BangumiRepository.getMyFollowBangumi(
                type = channel.followType,
                followStatus = status.value,
                page = nextPage,
            )
            coroutineContext.ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = { data ->
                    updateFollowState(key) { state ->
                        val previous = if (reset) emptyList() else state.content.items
                        state.copy(
                            content = BangumiPagedState(
                                items = mergeBangumiPagedItems(previous, data.list.orEmpty(), reset = false) {
                                    resolveMyFollowItemBusinessKey(it)
                                },
                                page = nextPage,
                                hasMore = data.hasNextPage(nextPage),
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    updateFollowState(key) {
                        it.copy(
                            content = it.content.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = error.message ?: "加载追番列表失败",
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun removeFollowItemsAfterMutation(
        key: Pair<BangumiChannel, BangumiFollowStatus>,
        ids: Set<Long>,
        decrementTotal: Boolean,
    ) {
        updateFollowState(key) { state ->
            state.copy(
                content = state.content.copy(items = state.content.items.filterNot { it.seasonId in ids }),
                selectedIds = emptySet(),
                isSelecting = false,
                isMutating = false,
            )
        }
        if (decrementTotal) {
            updateHomeState(key.first) { home ->
                home.copy(
                    follows = home.follows.copy(items = home.follows.items.filterNot { it.seasonId in ids }),
                    followTotal = home.followTotal?.let { (it - ids.size).coerceAtLeast(0) },
                )
            }
        }
    }

    private fun refreshFollowStatesAfterMove(
        source: Pair<BangumiChannel, BangumiFollowStatus>,
        targetStatus: BangumiFollowStatus,
    ) {
        val target = source.first to targetStatus
        loadFollowManager(source.first, source.second, reset = true)
        loadFollowManager(target.first, target.second, reset = true)
        loadHomeFollows(source.first, reset = true)
    }

    private fun homeState(channel: BangumiChannel): BangumiHomeState =
        _uiState.value.homeStates[channel] ?: BangumiHomeState()

    private fun updateHomeState(
        channel: BangumiChannel,
        transform: (BangumiHomeState) -> BangumiHomeState,
    ) {
        _uiState.update { state ->
            state.copy(homeStates = state.homeStates + (channel to transform(state.homeStates[channel] ?: BangumiHomeState())))
        }
    }

    private fun indexState(category: BangumiIndexCategory): BangumiIndexState =
        _uiState.value.indexStates[category] ?: BangumiIndexState()

    private fun updateIndexState(
        category: BangumiIndexCategory,
        transform: (BangumiIndexState) -> BangumiIndexState,
    ) {
        _uiState.update { state ->
            state.copy(indexStates = state.indexStates + (category to transform(state.indexStates[category] ?: BangumiIndexState())))
        }
    }

    private fun currentFollowKey(): Pair<BangumiChannel, BangumiFollowStatus> =
        _uiState.value.channel to _uiState.value.followStatus

    private fun followState(key: Pair<BangumiChannel, BangumiFollowStatus>): BangumiFollowManagerState =
        _uiState.value.followStates[key] ?: BangumiFollowManagerState()

    private fun updateFollowState(
        key: Pair<BangumiChannel, BangumiFollowStatus>,
        transform: (BangumiFollowManagerState) -> BangumiFollowManagerState,
    ) {
        _uiState.update { state ->
            state.copy(followStates = state.followStates + (key to transform(state.followStates[key] ?: BangumiFollowManagerState())))
        }
    }

    private companion object {
        val DEFAULT_HOME_INDEX_PARAMS = mapOf(
            "order" to "3",
            "season_version" to "-1",
            "spoken_language_type" to "-1",
            "area" to "-1",
            "is_finish" to "-1",
            "copyright" to "-1",
            "season_status" to "-1",
            "season_month" to "-1",
            "year" to "-1",
            "style_id" to "-1",
            "sort" to "0",
        )
    }
}
