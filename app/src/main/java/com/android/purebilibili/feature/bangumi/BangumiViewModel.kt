// 文件路径: feature/bangumi/BangumiViewModel.kt
package com.android.purebilibili.feature.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.feature.bangumi.currentBangumiFollowSession
import com.android.purebilibili.feature.bangumi.BangumiFollowSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/**
 * 番剧列表 UI 状态
 */
sealed class BangumiListState {
    object Loading : BangumiListState()
    @androidx.compose.runtime.Immutable
    data class Success(
        val items: List<BangumiItem>,
        val hasMore: Boolean = true,
        val isRefreshing: Boolean = false
    ) : BangumiListState()
    data class Error(val message: String) : BangumiListState()
}

/**
 * 番剧详情 UI 状态
 */
sealed class BangumiDetailState {
    object Loading : BangumiDetailState()
    @androidx.compose.runtime.Immutable
    data class Success(val detail: BangumiDetail) : BangumiDetailState()
    data class Error(val message: String) : BangumiDetailState()
}

/**
 * 时间表 UI 状态
 */
sealed class TimelineState {
    object Loading : TimelineState()
    @androidx.compose.runtime.Immutable
    data class Success(val days: List<TimelineDay>) : TimelineState()
    data class Error(val message: String) : TimelineState()
}

/**
 * 番剧搜索 UI 状态
 */
sealed class BangumiSearchState {
    object Idle : BangumiSearchState()
    object Loading : BangumiSearchState()
    @androidx.compose.runtime.Immutable
    data class Success(
        val items: List<BangumiSearchItem>,
        val hasMore: Boolean = true,
        val keyword: String = ""
    ) : BangumiSearchState()
    data class Error(val message: String) : BangumiSearchState()
}

/**
 * 我的追番 UI 状态
 */
sealed class MyFollowState {
    object Loading : MyFollowState()
    @androidx.compose.runtime.Immutable
    data class Success(
        val items: List<FollowBangumiItem>,
        val hasMore: Boolean = true,
        val total: Int? = null
    ) : MyFollowState()
    data class Error(val message: String) : MyFollowState()
}

/**
 * 番剧页面显示模式
 */
enum class BangumiDisplayMode {
    LIST,       // 索引列表 (默认)
    TIMELINE,   // 时间表/新番日历
    MY_FOLLOW,  // 我的追番
    SEARCH      // 搜索结果
}

@androidx.compose.runtime.Immutable
data class BangumiUiState(
    val displayMode: BangumiDisplayMode = BangumiDisplayMode.LIST,
    val selectedType: Int = 1,
    val listState: BangumiListState = BangumiListState.Loading,
    val timelineState: TimelineState = TimelineState.Loading,
    val detailState: BangumiDetailState = BangumiDetailState.Loading,
    val searchState: BangumiSearchState = BangumiSearchState.Idle,
    val myFollowState: MyFollowState = MyFollowState.Loading,
    val myFollowType: Int = defaultMyFollowTypeForSeasonType(1),
    val myFollowStats: MyFollowStats = MyFollowStats(),
    val filter: BangumiFilter = BangumiFilter(),
    val searchKeyword: String = ""
)

/**
 * 番剧/影视 ViewModel
 */
class BangumiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BangumiUiState())
    val uiState = _uiState.asStateFlow()
    val displayMode: StateFlow<BangumiDisplayMode> = uiState.map { it.displayMode }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.displayMode)
    val selectedType: StateFlow<Int> = uiState.map { it.selectedType }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.selectedType)
    val listState: StateFlow<BangumiListState> = uiState.map { it.listState }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.listState)
    val timelineState: StateFlow<TimelineState> = uiState.map { it.timelineState }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.timelineState)
    val detailState: StateFlow<BangumiDetailState> = uiState.map { it.detailState }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.detailState)
    val searchState: StateFlow<BangumiSearchState> = uiState.map { it.searchState }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.searchState)
    val myFollowState: StateFlow<MyFollowState> = uiState.map { it.myFollowState }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.myFollowState)
    val myFollowType: StateFlow<Int> = uiState.map { it.myFollowType }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.myFollowType)
    val myFollowStats: StateFlow<MyFollowStats> = uiState.map { it.myFollowStats }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.myFollowStats)
    val filter: StateFlow<BangumiFilter> = uiState.map { it.filter }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.filter)
    val searchKeyword: StateFlow<String> = uiState.map { it.searchKeyword }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.searchKeyword)
    
    // 分页
    private var currentPage = 1
    private var isLoadingMore = false
    private var searchPage = 1
    private var searchGeneration = 0L
    private var listGeneration = 0L
    private var timelineGeneration = 0L
    private var myFollowPage = 1
    
    //  [修复] 本地追番状态缓存
    // 由于 B站 PGC API 返回的 userStatus.follow 不可靠，我们使用本地缓存来覆盖
    // Key: seasonId, Value: 是否追番
    private val followStatusCache = mutableMapOf<Long, Boolean>()
    private val followStatusValueCache = mutableMapOf<Long, Int>()
    
    //  [修复] 预加载的已追番 seasonId 集合（从"我的追番"API 获取）
    private val followedSeasonIds = mutableSetOf<Long>()
    private var followVerificationJob: Job? = null
    private var detailRequestGeneration = 0L
    private val loadedFollowStatsTypes = mutableSetOf<Int>()
    private var followStatsJob: Job? = null
    private var myFollowLoadJob: Job? = null
    private var myFollowGeneration = 0L
    private var myFollowLoadingType: Int? = null
    private var followCacheSession: BangumiFollowSession = currentBangumiFollowSession()

    private fun ensureFollowCacheSession(): BangumiFollowSession {
        val session = currentBangumiFollowSession()
        if (session != followCacheSession) {
            followCacheSession = session
            followStatusCache.clear()
            followStatusValueCache.clear()
            followedSeasonIds.clear()
            loadedFollowStatsTypes.clear()
            followVerificationJob?.cancel()
            followStatsJob?.cancel()
            myFollowLoadJob?.cancel()
            ++detailRequestGeneration
            ++myFollowGeneration
            myFollowPage = 1
            myFollowLoadingType = null
            _uiState.update {
                it.copy(
                    detailState = BangumiDetailState.Loading,
                    myFollowState = MyFollowState.Loading,
                    myFollowStats = MyFollowStats()
                )
            }
        }
        return session
    }

    private fun isActiveFollowRequest(session: BangumiFollowSession, generation: Long): Boolean =
        session == ensureFollowCacheSession() && generation == myFollowGeneration
    
    // 详情入口只加载当前剧集；分类、时间表与追番由各自页面显式发起。
    fun loadHomeFeed() {
        val shouldShowTimeline = _uiState.value.selectedType == BangumiType.ANIME.value ||
            _uiState.value.selectedType == BangumiType.GUOCHUANG.value
        if (shouldShowTimeline && _uiState.value.timelineState is TimelineState.Loading) {
            loadTimeline(timelineTypeForSelectedType(_uiState.value.selectedType))
        }
        if (_uiState.value.myFollowState is MyFollowState.Loading) {
            loadMyFollowBangumi(type = _uiState.value.myFollowType)
        }
    }
    
    private fun verifyDetailFollowStatus(detail: BangumiDetail, generation: Long) {
        val session = ensureFollowCacheSession()
        val seasonId = detail.seasonId
        if (followStatusCache.containsKey(seasonId) ||
            followStatusValueCache.containsKey(seasonId) ||
            followedSeasonIds.contains(seasonId) || isBangumiFollowed(detail.userStatus)) return
        if (session.session.isNullOrEmpty()) return
        followVerificationJob = viewModelScope.launch {
            val foundIds = mutableSetOf<Long>()
            val type = defaultMyFollowTypeForSeasonType(detail.seasonType)
            val result = preloadFollowedSeasonsForType(
                type = type,
                followedSeasonIds = foundIds,
                targetSeasonId = seasonId
            )
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (generation != detailRequestGeneration || session != currentBangumiFollowSession()) return@launch
            if (result.requestSucceeded) updateMyFollowStatsForType(type, result.total)
            followedSeasonIds.addAll(foundIds)
            if (seasonId !in foundIds) return@launch
            val current = (_uiState.value.detailState as? BangumiDetailState.Success)?.detail
                ?.takeIf { it.seasonId == seasonId } ?: return@launch
            val verifiedDetail = if (!followStatusCache.containsKey(seasonId) &&
                !followStatusValueCache.containsKey(seasonId) && result.targetFollowStatus != null) {
                current.copy(userStatus = (current.userStatus ?: UserStatus()).copy(
                    follow = 1, followStatus = result.targetFollowStatus
                ))
            } else current
            _uiState.update { it.copy(detailState = BangumiDetailState.Success(correctFollowStatus(verifiedDetail))) }
        }
    }

    private fun correctFollowStatus(detail: BangumiDetail): BangumiDetail {
        ensureFollowCacheSession()
        val merged = resolveBangumiMergedFollowStatus(
            detail.seasonId, detail.userStatus, followStatusCache,
            followStatusValueCache, followedSeasonIds
        )
        return detail.copy(userStatus = (detail.userStatus ?: UserStatus()).copy(
            follow = if (merged.isFollowing) 1 else 0,
            followStatus = merged.followStatus
        ))
    }
    
    /**
     * 切换显示模式
     */
    fun setDisplayMode(mode: BangumiDisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
        when (mode) {
            BangumiDisplayMode.TIMELINE -> {
                if (_uiState.value.timelineState is TimelineState.Loading) {
                    loadTimeline(timelineTypeForSelectedType(_uiState.value.selectedType))
                }
            }
            BangumiDisplayMode.MY_FOLLOW -> {
                loadMyFollowBangumi(type = _uiState.value.myFollowType)
            }
            else -> {}
        }
    }

    fun openMyFollowEntry() {
        val preferredType = defaultMyFollowTypeForSeasonType(_uiState.value.selectedType)
        _uiState.update { it.copy(myFollowType = preferredType, displayMode = BangumiDisplayMode.MY_FOLLOW) }
        loadMyFollowBangumi(type = preferredType)
    }
    
    /**
     * 切换番剧类型
     */
    fun selectType(type: Int) {
        if (_uiState.value.selectedType != type) {
            _uiState.update {
                it.copy(
                    selectedType = type,
                    filter = BangumiFilter(),
                    myFollowType = defaultMyFollowTypeForSeasonType(type),
                    listState = BangumiListState.Loading,
                    myFollowState = MyFollowState.Loading
                )
            }
            currentPage = 1
            loadBangumiList()
            loadMyFollowBangumi(type = _uiState.value.myFollowType)
            val shouldReloadTimeline = type == BangumiType.ANIME.value ||
                type == BangumiType.GUOCHUANG.value ||
                _uiState.value.displayMode == BangumiDisplayMode.TIMELINE
            if (shouldReloadTimeline) {
                loadTimeline(timelineTypeForSelectedType(type))
            }
        }
    }

    fun selectMyFollowType(type: Int) {
        if (_uiState.value.myFollowType == type) return
        _uiState.update { it.copy(myFollowType = type, myFollowState = MyFollowState.Loading) }
        if (_uiState.value.displayMode == BangumiDisplayMode.MY_FOLLOW) {
            loadMyFollowBangumi(type)
        }
    }

    private fun timelineTypeForSelectedType(seasonType: Int): Int {
        return when (seasonType) {
            BangumiType.MOVIE.value -> 3
            BangumiType.GUOCHUANG.value -> 4
            else -> 1
        }
    }
    
    /**
     *  更新筛选条件
     */
    fun updateFilter(newFilter: BangumiFilter) {
        _uiState.update { it.copy(filter = newFilter) }
        currentPage = 1
        loadBangumiListWithFilter()
    }
    
    /**
     * 加载番剧列表
     */
    fun loadBangumiList() {
        val session = ensureFollowCacheSession()
        val generation = ++listGeneration
        val requestState = _uiState.value
        _uiState.update {
            val previous = it.listState
            it.copy(listState = if (previous is BangumiListState.Success) {
                previous.copy(isRefreshing = true)
            } else BangumiListState.Loading)
        }
        viewModelScope.launch {
            BangumiRepository.getBangumiIndexWithFilter(
                seasonType = requestState.selectedType,
                page = 1,
                filter = requestState.filter
            ).fold(
                onSuccess = { data ->
                    if (generation != listGeneration || session != ensureFollowCacheSession()) return@fold
                    currentPage = 1
                    _uiState.update { it.copy(listState = BangumiListState.Success(
                        items = data.list ?: emptyList(),
                        hasMore = data.hasNext == 1
                    )) }
                },
                onFailure = { error ->
                    if (generation != listGeneration || session != ensureFollowCacheSession()) return@fold
                    _uiState.update { it.copy(listState = BangumiListState.Error(error.message ?: "加载失败")) }
                }
            )
        }
    }

    private fun loadBangumiListWithFilter() {
        loadBangumiList()
    }
    
    /**
     * 加载更多
     */
    fun loadMore() {
        if (isLoadingMore) return
        val currentState = _uiState.value.listState
        if (currentState !is BangumiListState.Success || !currentState.hasMore || currentState.isRefreshing) return

        isLoadingMore = true
        val session = ensureFollowCacheSession()
        val generation = listGeneration
        val requestedPage = currentPage + 1
        val requestState = _uiState.value
        viewModelScope.launch {
            BangumiRepository.getBangumiIndexWithFilter(
                seasonType = requestState.selectedType,
                page = requestedPage,
                filter = requestState.filter
            ).fold(
                onSuccess = { data ->
                    if (generation != listGeneration || session != ensureFollowCacheSession()) return@fold
                    val newItems = currentState.items + (data.list ?: emptyList())
                    _uiState.update { it.copy(listState = BangumiListState.Success(
                        items = newItems,
                        hasMore = data.hasNext == 1
                    )) }
                },
                onFailure = {
                    currentPage--
                }
            )
            isLoadingMore = false
        }
    }
    
    /**
     * 加载时间表
     */
    fun loadTimeline(type: Int = 1) {
        val session = ensureFollowCacheSession()
        val generation = ++timelineGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(timelineState = TimelineState.Loading) }
            
            BangumiRepository.getTimeline(type).fold(
                onSuccess = { days ->
                    if (generation != timelineGeneration || session != ensureFollowCacheSession()) return@fold
                    _uiState.update { it.copy(timelineState = TimelineState.Success(days)) }
                },
                onFailure = { error ->
                    if (generation != timelineGeneration || session != ensureFollowCacheSession()) return@fold
                    _uiState.update { it.copy(timelineState = TimelineState.Error(error.message ?: "加载失败")) }
                }
            )
        }
    }
    
    /**
     * 加载番剧详情
     */
    fun loadSeasonDetail(seasonId: Long, epId: Long = 0, mediaId: Long = 0) {
        val session = ensureFollowCacheSession()
        val generation = ++detailRequestGeneration
        followVerificationJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(detailState = BangumiDetailState.Loading) }
            
            val detailResult = if (mediaId > 0L && seasonId <= 0L && epId <= 0L) {
                BangumiRepository.getSeasonDetailByMediaId(mediaId)
            } else {
                BangumiRepository.getSeasonDetail(seasonId, epId)
            }
            if (generation != detailRequestGeneration || session != ensureFollowCacheSession()) return@launch
            detailResult.fold(
                onSuccess = { detail ->
                    //  获取真实的 seasonId (如果传入的是 0 或错误的 ID，这里会纠正)
                    val realSeasonId = detail.seasonId
                    // 先发布详情；不让追番分页校验阻塞内容。
                    
                    val mergedFollowStatus = resolveBangumiMergedFollowStatus(
                        seasonId = realSeasonId,
                        apiUserStatus = detail.userStatus,
                        cachedFollow = followStatusCache,
                        cachedStatus = followStatusValueCache,
                        followedSeasonIds = followedSeasonIds
                    )
                    
                    val correctedDetail = detail.copy(
                        userStatus = detail.userStatus?.copy(
                            follow = if (mergedFollowStatus.isFollowing) 1 else 0,
                            followStatus = mergedFollowStatus.followStatus
                        ) ?: com.android.purebilibili.data.model.response.UserStatus(
                            follow = if (mergedFollowStatus.isFollowing) 1 else 0,
                            followStatus = mergedFollowStatus.followStatus
                        )
                    )
                    _uiState.update { it.copy(detailState = BangumiDetailState.Success(correctedDetail)) }
                    verifyDetailFollowStatus(detail, generation)
                },
                onFailure = { error ->
                    if (generation != detailRequestGeneration || session != ensureFollowCacheSession()) return@fold
                    _uiState.update { it.copy(detailState = BangumiDetailState.Error(error.message ?: "加载失败")) }
                }
            )
        }
    }
    
    /**
     * 追番/取消追番
     *  [修复] 成功后不再重新加载详情（因为 API 可能有延迟返回错误的 follow 状态）
     * UI 层已经做了乐观更新，只有失败时才需要刷新以恢复正确状态
     */
    fun toggleFollow(seasonId: Long, isFollowing: Boolean) {
        val targetStatus = if (isFollowing) {
            BANGUMI_FOLLOW_STATUS_UNFOLLOW
        } else {
            BANGUMI_FOLLOW_STATUS_WATCHING
        }
        updateFollowStatus(seasonId, targetStatus)
    }

    fun updateFollowStatus(seasonId: Long, status: Int) {
        val session = ensureFollowCacheSession()
        val generation = ++detailRequestGeneration
        viewModelScope.launch {
            val currentDetail = (_uiState.value.detailState as? BangumiDetailState.Success)?.detail
                ?.takeIf { it.seasonId == seasonId }
            val wasFollowing = isBangumiFollowed(currentDetail?.userStatus) ||
                followStatusCache[seasonId] == true ||
                (followStatusValueCache[seasonId] ?: 0) > 0

            val result = when {
                status == BANGUMI_FOLLOW_STATUS_UNFOLLOW -> {
                    BangumiRepository.unfollowBangumi(seasonId)
                }
                wasFollowing -> {
                    BangumiRepository.updateBangumiFollowStatus(seasonId, status)
                }
                status == BANGUMI_FOLLOW_STATUS_WATCHING -> {
                    BangumiRepository.followBangumi(seasonId)
                }
                else -> {
                    val followResult = BangumiRepository.followBangumi(seasonId)
                    if (followResult.isSuccess && session == ensureFollowCacheSession()) {
                        BangumiRepository.updateBangumiFollowStatus(seasonId, status)
                    } else {
                        Result.failure(followResult.exceptionOrNull() ?: Exception("追番失败"))
                    }
                }
            }
            
            result.fold(
                onSuccess = {
                    if (generation != detailRequestGeneration || session != ensureFollowCacheSession()) return@fold
                    //  [修复] 成功后更新本地缓存和预加载列表
                    val isNowFollowing = status != BANGUMI_FOLLOW_STATUS_UNFOLLOW
                    followStatusCache[seasonId] = isNowFollowing
                    if (isNowFollowing) {
                        followedSeasonIds.add(seasonId)
                        followStatusValueCache[seasonId] = status
                    } else {
                        followedSeasonIds.remove(seasonId)
                        followStatusValueCache.remove(seasonId)
                    }
                    val currentState = _uiState.value.detailState as? BangumiDetailState.Success
                    if (currentState?.detail?.seasonId == seasonId) {
                        val updatedUserStatus = currentState.detail.userStatus?.copy(
                            follow = if (isNowFollowing) 1 else 0,
                            followStatus = if (isNowFollowing) status else 0
                        ) ?: UserStatus(
                            follow = if (isNowFollowing) 1 else 0,
                            followStatus = if (isNowFollowing) status else 0
                        )
                        _uiState.update { it.copy(detailState = BangumiDetailState.Success(
                            currentState.detail.copy(userStatus = updatedUserStatus)
                        )) }
                    }
                    android.util.Log.d("BangumiVM", "追番状态更新成功: seasonId=$seasonId, status=$status")
                },
                onFailure = { error ->
                    if (generation != detailRequestGeneration || session != ensureFollowCacheSession()) return@fold
                    android.util.Log.e("BangumiVM", "Toggle follow failed: ${error.message}")
                    //  失败时清除缓存并重新加载详情，恢复正确状态
                    followStatusCache.remove(seasonId)
                    followStatusValueCache.remove(seasonId)
                    loadSeasonDetail(seasonId)
                }
            )
        }
    }
    
    // ==========  新增功能 ==========
    
    /**
     *  搜索番剧
     */
    fun searchBangumi(keyword: String) {
        if (keyword.isBlank()) return
        
        _uiState.update {
            it.copy(searchKeyword = keyword, displayMode = BangumiDisplayMode.SEARCH,
                searchState = BangumiSearchState.Loading)
        }
        searchPage = 1
        val generation = ++searchGeneration
        val seasonType = _uiState.value.selectedType
        viewModelScope.launch {
            
            BangumiRepository.searchBangumi(
                keyword = keyword,
                seasonType = seasonType,
                page = 1
            ).fold(
                onSuccess = { data ->
                    if (generation != searchGeneration) return@fold
                    _uiState.update { it.copy(searchState = BangumiSearchState.Success(
                        items = data.result ?: emptyList(),
                        hasMore = data.page < data.numPages,
                        keyword = keyword
                    )) }
                },
                onFailure = { error ->
                    if (generation != searchGeneration) return@fold
                    _uiState.update { it.copy(searchState = BangumiSearchState.Error(error.message ?: "搜索失败")) }
                }
            )
        }
    }
    
    /**
     *  加载更多搜索结果
     */
    fun loadMoreSearchResults() {
        val currentState = _uiState.value.searchState
        if (currentState !is BangumiSearchState.Success || !currentState.hasMore || isLoadingMore) return
        
        isLoadingMore = true
        val requestedPage = searchPage + 1
        val generation = searchGeneration
        val seasonType = _uiState.value.selectedType
        viewModelScope.launch {
            BangumiRepository.searchBangumi(
                keyword = currentState.keyword,
                seasonType = seasonType,
                page = requestedPage
            ).fold(
                onSuccess = { data ->
                    if (generation != searchGeneration) return@fold
                    searchPage = requestedPage
                    _uiState.update { it.copy(searchState = BangumiSearchState.Success(
                        items = currentState.items + (data.result ?: emptyList()),
                        hasMore = data.page < data.numPages,
                        keyword = currentState.keyword
                    )) }
                },
                onFailure = {
                    // 页码仅在成功后提交。
                }
            )
            isLoadingMore = false
        }
    }
    
    /**
     *  清除搜索
     */
    fun clearSearch() {
        ++searchGeneration
        _uiState.update {
            it.copy(searchKeyword = "", searchState = BangumiSearchState.Idle,
                displayMode = BangumiDisplayMode.LIST)
        }
    }
    
    /**
     *  加载我的追番列表
     */
    fun loadMyFollowBangumi(type: Int? = null) {
        val session = ensureFollowCacheSession()
        val targetType = resolveMyFollowRequestType(type, _uiState.value.myFollowType)
        if (myFollowLoadJob?.isActive == true && myFollowLoadingType == targetType) return
        myFollowLoadingType = targetType
        _uiState.update { it.copy(myFollowType = targetType, myFollowState = MyFollowState.Loading) }
        val generation = ++myFollowGeneration
        myFollowLoadJob?.cancel()
        myFollowPage = 1
        
        myFollowLoadJob = viewModelScope.launch {
            BangumiRepository.getMyFollowBangumi(
                type = targetType,
                page = 1
            ).fold(
                onSuccess = { data ->
                    if (!isActiveFollowRequest(session, generation)) return@fold
                    followedSeasonIds.addAll(data.list.orEmpty().map { it.seasonId })
                    loadedFollowStatsTypes.add(targetType)
                    _uiState.update {
                        it.copy(
                            myFollowStats = statsForType(it.myFollowStats, targetType, data.total),
                            myFollowState = MyFollowState.Success(
                                items = data.list ?: emptyList(),
                                hasMore = data.hasNextPage(data.pn),
                                total = data.total
                            )
                        )
                    }
                    loadRemainingFollowStats(targetType)
                },
                onFailure = { error ->
                    if (!isActiveFollowRequest(session, generation)) return@fold
                    _uiState.update { it.copy(myFollowState = MyFollowState.Error(error.message ?: "加载失败")) }
                }
            )
        }
    }
    
    /**
     *  加载更多追番
     */
    fun loadMoreMyFollow() {
        val session = ensureFollowCacheSession()
        val currentState = _uiState.value.myFollowState
        if (currentState !is MyFollowState.Success || !currentState.hasMore || isLoadingMore) return
        
        isLoadingMore = true
        val generation = myFollowGeneration
        val targetType = _uiState.value.myFollowType
        val requestedPage = myFollowPage + 1
        
        viewModelScope.launch {
            BangumiRepository.getMyFollowBangumi(
                type = targetType,
                page = requestedPage
            ).fold(
                onSuccess = { data ->
                    if (!isActiveFollowRequest(session, generation)) return@fold
                    myFollowPage = requestedPage
                    followedSeasonIds.addAll(data.list.orEmpty().map { it.seasonId })
                    loadedFollowStatsTypes.add(targetType)
                    _uiState.update {
                        it.copy(
                            myFollowStats = statsForType(it.myFollowStats, targetType, data.total),
                            myFollowState = MyFollowState.Success(
                                items = currentState.items + (data.list ?: emptyList()),
                                hasMore = data.hasNextPage(data.pn),
                                total = data.total
                            )
                        )
                    }
                },
                onFailure = {
                    // 页码仅在成功后提交，失败重试仍请求同一页。
                }
            )
            isLoadingMore = false
        }
    }

    private fun loadRemainingFollowStats(displayedType: Int) {
        val session = ensureFollowCacheSession()
        val otherType = if (displayedType == MY_FOLLOW_TYPE_BANGUMI) {
            MY_FOLLOW_TYPE_CINEMA
        } else MY_FOLLOW_TYPE_BANGUMI
        if (otherType in loadedFollowStatsTypes || followStatsJob?.isActive == true) return
        followStatsJob = viewModelScope.launch {
            BangumiRepository.getMyFollowBangumi(type = otherType, page = 1).onSuccess { data ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (session != ensureFollowCacheSession()) return@onSuccess
                followedSeasonIds.addAll(data.list.orEmpty().map { it.seasonId })
                updateMyFollowStatsForType(otherType, data.total)
            }
        }
    }

    private fun updateMyFollowStatsForType(type: Int, total: Int?) {
        loadedFollowStatsTypes.add(type)
        _uiState.update { it.copy(myFollowStats = statsForType(it.myFollowStats, type, total)) }
    }

    private fun statsForType(current: MyFollowStats, type: Int, total: Int?): MyFollowStats {
        val normalized = total?.coerceAtLeast(0) ?: return current
        return if (type == MY_FOLLOW_TYPE_BANGUMI) {
            buildMyFollowStats(
                bangumiTotal = normalized,
                cinemaTotal = current.cinemaTotal
            )
        } else {
            buildMyFollowStats(
                bangumiTotal = current.bangumiTotal,
                cinemaTotal = normalized
            )
        }
    }
}
