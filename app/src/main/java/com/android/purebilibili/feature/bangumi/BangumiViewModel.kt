// 文件路径: feature/bangumi/BangumiViewModel.kt
package com.android.purebilibili.feature.bangumi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.*
import com.android.purebilibili.data.repository.BangumiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 番剧列表 UI 状态
 */
sealed class BangumiListState {
    object Loading : BangumiListState()
    data class Success(
        val items: List<BangumiItem>,
        val hasMore: Boolean = true
    ) : BangumiListState()
    data class Error(val message: String) : BangumiListState()
}

/**
 * 番剧详情 UI 状态
 */
sealed class BangumiDetailState {
    object Loading : BangumiDetailState()
    data class Success(val detail: BangumiDetail) : BangumiDetailState()
    data class Error(val message: String) : BangumiDetailState()
}

/**
 * 时间表 UI 状态
 */
sealed class TimelineState {
    object Loading : TimelineState()
    data class Success(val days: List<TimelineDay>) : TimelineState()
    data class Error(val message: String) : TimelineState()
}

/**
 * 番剧搜索 UI 状态
 */
sealed class BangumiSearchState {
    object Idle : BangumiSearchState()
    object Loading : BangumiSearchState()
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
    data class Success(
        val items: List<FollowBangumiItem>,
        val hasMore: Boolean = true,
        val total: Int = 0
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

/**
 * 番剧/影视 ViewModel
 */
class BangumiViewModel : ViewModel() {
    
    // 当前显示模式
    private val _displayMode = MutableStateFlow(BangumiDisplayMode.LIST)
    val displayMode: StateFlow<BangumiDisplayMode> = _displayMode.asStateFlow()
    
    // 当前选中的类型 (1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺)
    private val _selectedType = MutableStateFlow(1)
    val selectedType: StateFlow<Int> = _selectedType.asStateFlow()
    
    // 番剧列表状态
    private val _listState = MutableStateFlow<BangumiListState>(BangumiListState.Loading)
    val listState: StateFlow<BangumiListState> = _listState.asStateFlow()
    
    // 时间表状态
    private val _timelineState = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val timelineState: StateFlow<TimelineState> = _timelineState.asStateFlow()
    
    // 番剧详情状态
    private val _detailState = MutableStateFlow<BangumiDetailState>(BangumiDetailState.Loading)
    val detailState: StateFlow<BangumiDetailState> = _detailState.asStateFlow()
    
    //  新增：搜索状态
    private val _searchState = MutableStateFlow<BangumiSearchState>(BangumiSearchState.Idle)
    val searchState: StateFlow<BangumiSearchState> = _searchState.asStateFlow()
    
    //  新增：我的追番状态
    private val _myFollowState = MutableStateFlow<MyFollowState>(MyFollowState.Loading)
    val myFollowState: StateFlow<MyFollowState> = _myFollowState.asStateFlow()
    
    //  新增：筛选条件
    private val _filter = MutableStateFlow(BangumiFilter())
    val filter: StateFlow<BangumiFilter> = _filter.asStateFlow()
    
    //  新增：搜索关键词
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()
    
    // 分页
    private var currentPage = 1
    private var isLoadingMore = false
    private var searchPage = 1
    private var myFollowPage = 1
    
    //  [修复] 本地追番状态缓存
    // 由于 B站 PGC API 返回的 userStatus.follow 不可靠，我们使用本地缓存来覆盖
    // Key: seasonId, Value: 是否追番
    private val followStatusCache = mutableMapOf<Long, Boolean>()
    
    //  [修复] 预加载的已追番 seasonId 集合（从"我的追番"API 获取）
    private val followedSeasonIds = mutableSetOf<Long>()
    private var hasPreloadedFollowList = false
    
    init {
        loadBangumiList()
        //  预加载用户的追番列表以获取正确的追番状态
        preloadFollowedSeasons()
    }
    
    /**
     *  [新增] 预加载用户已追番的 seasonId 列表
     */
    private fun preloadFollowedSeasons() {
        viewModelScope.launch {
            // 加载追番 (type=1)
            BangumiRepository.getMyFollowBangumi(type = 1, page = 1, pageSize = 100).fold(
                onSuccess = { data ->
                    data.list?.forEach { item ->
                        followedSeasonIds.add(item.seasonId)
                    }
                    android.util.Log.d("BangumiVM", "📌 预加载追番列表: ${followedSeasonIds.size} 部")
                },
                onFailure = { }
            )
            // 加载追剧 (type=2)
            BangumiRepository.getMyFollowBangumi(type = 2, page = 1, pageSize = 100).fold(
                onSuccess = { data ->
                    data.list?.forEach { item ->
                        followedSeasonIds.add(item.seasonId)
                    }
                },
                onFailure = { }
            )
            hasPreloadedFollowList = true
        }
    }
    
    /**
     * 切换显示模式
     */
    fun setDisplayMode(mode: BangumiDisplayMode) {
        _displayMode.value = mode
        when (mode) {
            BangumiDisplayMode.TIMELINE -> {
                if (_timelineState.value is TimelineState.Loading) {
                    loadTimeline()
                }
            }
            BangumiDisplayMode.MY_FOLLOW -> {
                loadMyFollowBangumi()
            }
            else -> {}
        }
    }
    
    /**
     * 切换番剧类型
     */
    fun selectType(type: Int) {
        if (_selectedType.value != type) {
            _selectedType.value = type
            currentPage = 1
            loadBangumiList()
        }
    }
    
    /**
     *  更新筛选条件
     */
    fun updateFilter(newFilter: BangumiFilter) {
        _filter.value = newFilter
        currentPage = 1
        loadBangumiListWithFilter()
    }
    
    /**
     * 加载番剧列表
     */
    fun loadBangumiList() {
        viewModelScope.launch {
            _listState.value = BangumiListState.Loading
            currentPage = 1
            
            BangumiRepository.getBangumiIndex(
                seasonType = _selectedType.value,
                page = currentPage
            ).fold(
                onSuccess = { data ->
                    _listState.value = BangumiListState.Success(
                        items = data.list ?: emptyList(),
                        hasMore = data.hasNext == 1
                    )
                },
                onFailure = { error ->
                    _listState.value = BangumiListState.Error(error.message ?: "加载失败")
                }
            )
        }
    }
    
    /**
     *  带筛选条件加载番剧列表
     */
    private fun loadBangumiListWithFilter() {
        viewModelScope.launch {
            _listState.value = BangumiListState.Loading
            
            BangumiRepository.getBangumiIndexWithFilter(
                seasonType = _selectedType.value,
                page = currentPage,
                filter = _filter.value
            ).fold(
                onSuccess = { data ->
                    _listState.value = BangumiListState.Success(
                        items = data.list ?: emptyList(),
                        hasMore = data.hasNext == 1
                    )
                },
                onFailure = { error ->
                    _listState.value = BangumiListState.Error(error.message ?: "加载失败")
                }
            )
        }
    }
    
    /**
     * 加载更多
     */
    fun loadMore() {
        if (isLoadingMore) return
        val currentState = _listState.value
        if (currentState !is BangumiListState.Success || !currentState.hasMore) return
        
        isLoadingMore = true
        viewModelScope.launch {
            currentPage++
            
            BangumiRepository.getBangumiIndexWithFilter(
                seasonType = _selectedType.value,
                page = currentPage,
                filter = _filter.value
            ).fold(
                onSuccess = { data ->
                    val newItems = currentState.items + (data.list ?: emptyList())
                    _listState.value = BangumiListState.Success(
                        items = newItems,
                        hasMore = data.hasNext == 1
                    )
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
        viewModelScope.launch {
            _timelineState.value = TimelineState.Loading
            
            BangumiRepository.getTimeline(type).fold(
                onSuccess = { days ->
                    _timelineState.value = TimelineState.Success(days)
                },
                onFailure = { error ->
                    _timelineState.value = TimelineState.Error(error.message ?: "加载失败")
                }
            )
        }
    }
    
    /**
     * 加载番剧详情
     */
    fun loadSeasonDetail(seasonId: Long, epId: Long = 0) {
        viewModelScope.launch {
            _detailState.value = BangumiDetailState.Loading
            
            BangumiRepository.getSeasonDetail(seasonId, epId).fold(
                onSuccess = { detail ->
                    //  获取真实的 seasonId (如果传入的是 0 或错误的 ID，这里会纠正)
                    val realSeasonId = detail.seasonId
                    
                    //  [修复] 确定追番状态的优先级：
                    // 1. 本地缓存（用户在本次会话中点击追番/取消追番）
                    // 2. 预加载的追番列表（从"我的追番"API 获取）
                    // 3. API 返回的 userStatus.follow
                    val isFollowed = when {
                        followStatusCache.containsKey(realSeasonId) -> {
                            android.util.Log.d("BangumiVM", "📌 使用本地缓存状态: ${followStatusCache[realSeasonId]}")
                            followStatusCache[realSeasonId]!!
                        }
                        followedSeasonIds.contains(realSeasonId) -> {
                            android.util.Log.d("BangumiVM", "📌 从追番列表确认已追番: seasonId=$realSeasonId")
                            true
                        }
                        else -> {
                            detail.userStatus?.follow == 1
                        }
                    }
                    
                    val correctedDetail = detail.copy(
                        userStatus = detail.userStatus?.copy(
                            follow = if (isFollowed) 1 else 0
                        ) ?: com.android.purebilibili.data.model.response.UserStatus(
                            follow = if (isFollowed) 1 else 0
                        )
                    )
                    _detailState.value = BangumiDetailState.Success(correctedDetail)
                },
                onFailure = { error ->
                    _detailState.value = BangumiDetailState.Error(error.message ?: "加载失败")
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
        viewModelScope.launch {
            val result = if (isFollowing) {
                BangumiRepository.unfollowBangumi(seasonId)
            } else {
                BangumiRepository.followBangumi(seasonId)
            }
            
            result.fold(
                onSuccess = {
                    //  [修复] 成功后更新本地缓存和预加载列表
                    val newFollowStatus = !isFollowing
                    followStatusCache[seasonId] = newFollowStatus
                    if (newFollowStatus) {
                        followedSeasonIds.add(seasonId)
                    } else {
                        followedSeasonIds.remove(seasonId)
                    }
                    android.util.Log.d("BangumiVM", " ${if (isFollowing) "取消追番" else "追番"}成功，状态更新为: $newFollowStatus")
                },
                onFailure = { error ->
                    android.util.Log.e("BangumiVM", "Toggle follow failed: ${error.message}")
                    //  失败时清除缓存并重新加载详情，恢复正确状态
                    followStatusCache.remove(seasonId)
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
        
        _searchKeyword.value = keyword
        _displayMode.value = BangumiDisplayMode.SEARCH
        searchPage = 1
        
        viewModelScope.launch {
            _searchState.value = BangumiSearchState.Loading
            
            BangumiRepository.searchBangumi(
                keyword = keyword,
                page = searchPage
            ).fold(
                onSuccess = { data ->
                    _searchState.value = BangumiSearchState.Success(
                        items = data.result ?: emptyList(),
                        hasMore = data.page < data.numPages,
                        keyword = keyword
                    )
                },
                onFailure = { error ->
                    _searchState.value = BangumiSearchState.Error(error.message ?: "搜索失败")
                }
            )
        }
    }
    
    /**
     *  加载更多搜索结果
     */
    fun loadMoreSearchResults() {
        val currentState = _searchState.value
        if (currentState !is BangumiSearchState.Success || !currentState.hasMore || isLoadingMore) return
        
        isLoadingMore = true
        searchPage++
        
        viewModelScope.launch {
            BangumiRepository.searchBangumi(
                keyword = currentState.keyword,
                page = searchPage
            ).fold(
                onSuccess = { data ->
                    _searchState.value = BangumiSearchState.Success(
                        items = currentState.items + (data.result ?: emptyList()),
                        hasMore = data.page < data.numPages,
                        keyword = currentState.keyword
                    )
                },
                onFailure = {
                    searchPage--
                }
            )
            isLoadingMore = false
        }
    }
    
    /**
     *  清除搜索
     */
    fun clearSearch() {
        _searchKeyword.value = ""
        _searchState.value = BangumiSearchState.Idle
        _displayMode.value = BangumiDisplayMode.LIST
    }
    
    /**
     *  加载我的追番列表
     */
    fun loadMyFollowBangumi(type: Int = 1) {
        myFollowPage = 1
        
        viewModelScope.launch {
            _myFollowState.value = MyFollowState.Loading
            
            BangumiRepository.getMyFollowBangumi(
                type = type,
                page = myFollowPage
            ).fold(
                onSuccess = { data ->
                    _myFollowState.value = MyFollowState.Success(
                        items = data.list ?: emptyList(),
                        hasMore = (data.list?.size ?: 0) >= data.ps,
                        total = data.total
                    )
                },
                onFailure = { error ->
                    _myFollowState.value = MyFollowState.Error(error.message ?: "加载失败")
                }
            )
        }
    }
    
    /**
     *  加载更多追番
     */
    fun loadMoreMyFollow() {
        val currentState = _myFollowState.value
        if (currentState !is MyFollowState.Success || !currentState.hasMore || isLoadingMore) return
        
        isLoadingMore = true
        myFollowPage++
        
        viewModelScope.launch {
            BangumiRepository.getMyFollowBangumi(
                page = myFollowPage
            ).fold(
                onSuccess = { data ->
                    _myFollowState.value = MyFollowState.Success(
                        items = currentState.items + (data.list ?: emptyList()),
                        hasMore = (data.list?.size ?: 0) >= data.ps,
                        total = data.total
                    )
                },
                onFailure = {
                    myFollowPage--
                }
            )
            isLoadingMore = false
        }
    }
}
