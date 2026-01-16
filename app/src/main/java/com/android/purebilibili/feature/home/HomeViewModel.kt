// 文件路径: feature/home/HomeViewModel.kt
package com.android.purebilibili.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.LiveRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 状态类已移至 HomeUiState.kt

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private var refreshIdx = 0
    private var popularPage = 1  //  热门视频分页
    private var livePage = 1     //  直播分页
    private var hasMoreLiveData = true  //  是否还有更多直播数据
    
    //  [新增] 会话级去重集合 (避免重复推荐)
    private val sessionSeenBvids = mutableSetOf<String>()

    init {
        loadData()
    }

    //  [新增] 切换分类
    fun switchCategory(category: HomeCategory) {
        if (_uiState.value.currentCategory == category) return
        
        //  [修复] 标记正在切换分类，避免入场动画产生收缩效果
        com.android.purebilibili.core.util.CardPositionManager.isSwitchingCategory = true
        
        viewModelScope.launch {
            //  [修复] 如果切换到直播分类，未登录用户默认显示热门
            val liveSubCategory = if (category == HomeCategory.LIVE) {
                val isLoggedIn = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
                if (isLoggedIn) _uiState.value.liveSubCategory else LiveSubCategory.POPULAR
            } else {
                _uiState.value.liveSubCategory
            }
            
            _uiState.value = _uiState.value.copy(
                currentCategory = category,
                liveSubCategory = liveSubCategory,
                videos = emptyList(),
                liveRooms = emptyList(),  //  清空直播列表
                isLoading = true,
                error = null,
                displayedTabIndex = category.ordinal  //  [新增] 同步更新标签页索引
            )
            refreshIdx = 0
            popularPage = 1
            livePage = 1
            livePage = 1
            hasMoreLiveData = true  //  重置分页标志
            sessionSeenBvids.clear() //  [新增] 切换分类时清空去重集合
            fetchData(isLoadMore = false)
        }
    }
    
    //  [新增] 更新显示的标签页索引（用于特殊分类，不改变内容只更新标签高亮）
    fun updateDisplayedTabIndex(index: Int) {
        _uiState.value = _uiState.value.copy(displayedTabIndex = index)
    }
    
    //  [新增] 开始消散动画（触发 UI 播放粒子动画）
    fun startVideoDissolve(bvid: String) {
        _uiState.value = _uiState.value.copy(
            dissolvingVideos = _uiState.value.dissolvingVideos + bvid
        )
    }
    
    //  [新增] 完成消散动画（从列表移除并记录到已过滤集合）
    fun completeVideoDissolve(bvid: String) {
        _uiState.value = _uiState.value.copy(
            dissolvingVideos = _uiState.value.dissolvingVideos - bvid,
            videos = _uiState.value.videos.filterNot { it.bvid == bvid }
        )
    }
    
    
    //  [新增] 切换直播子分类
    fun switchLiveSubCategory(subCategory: LiveSubCategory) {
        if (_uiState.value.liveSubCategory == subCategory) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                liveSubCategory = subCategory,
                liveRooms = emptyList(),
                isLoading = true,
                error = null
            )
            livePage = 1
            hasMoreLiveData = true  //  修复：切换分类时重置分页标志
            fetchLiveRooms(isLoadMore = false)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            fetchData(isLoadMore = false)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshIdx = 0
            popularPage = 1
            livePage = 1  //  修复：刷新时也要重置直播分页
            hasMoreLiveData = true  //  修复：刷新时重置分页标志
            fetchData(isLoadMore = false)
            //  数据加载完成后再更新 refreshKey，避免闪烁
            //  刷新成功后显示趣味提示
            val refreshMessage = com.android.purebilibili.core.util.EasterEggs.getRefreshMessage()
            _uiState.value = _uiState.value.copy(
                refreshKey = System.currentTimeMillis(),
                refreshMessage = refreshMessage
            )
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _isRefreshing.value) return
        
        //  修复：如果是直播分类且没有更多数据，不再加载
        if (_uiState.value.currentCategory == HomeCategory.LIVE && !hasMoreLiveData) {
            com.android.purebilibili.core.util.Logger.d("HomeVM", "🔴 No more live data, skipping loadMore")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            //  修复：先增加页码再获取数据（确保请求下一页）
            refreshIdx++
            popularPage++
            livePage++
            fetchData(isLoadMore = true)
        }
    }

    private suspend fun fetchData(isLoadMore: Boolean) {
        val currentCategory = _uiState.value.currentCategory
        
        //  直播分类单独处理
        if (currentCategory == HomeCategory.LIVE) {
            fetchLiveRooms(isLoadMore)
            return
        }
        
        //  关注动态分类单独处理
        if (currentCategory == HomeCategory.FOLLOW) {
            fetchFollowFeed(isLoadMore)
            return
        }
        
        //  [问题15修复] 保存旧视频列表，刷新失败时恢复
        val oldVideos = _uiState.value.videos
        
        //  视频类分类处理
        val videoResult = when (currentCategory) {
            HomeCategory.RECOMMEND -> VideoRepository.getHomeVideos(refreshIdx)
            HomeCategory.POPULAR -> VideoRepository.getPopularVideos(popularPage)
            else -> {
                //  Generic categories (Game, Tech, etc.)
                if (currentCategory.tid > 0) {
                     VideoRepository.getRegionVideos(tid = currentCategory.tid, page = refreshIdx + 1) // Using refreshIdx for pagination similar to Recommend
                } else {
                     Result.failure(Exception("Unknown category"))
                }
            }
        }
        
        // 仅在首次加载或刷新时获取用户信息
        if (!isLoadMore) {
            fetchUserInfo()
        }

        if (isLoadMore) delay(100)

        videoResult.onSuccess { videos ->
            val validVideos = videos.filter { it.bvid.isNotEmpty() && it.title.isNotEmpty() }
            
            //  应用原生 FeedPlugin 过滤器
            val nativeFiltered = validVideos.filter { video ->
                val plugins = PluginManager.getEnabledFeedPlugins()
                if (plugins.isEmpty()) return@filter true
                
                plugins.all { plugin ->
                    try {
                        plugin.shouldShowItem(video)
                    } catch (e: Exception) {
                        Logger.e("HomeVM", "Plugin ${plugin.name} filter failed", e)
                        true  // 过滤器失败时默认显示
                    }
                }
            }
            
            //  [新增] 应用 JSON 规则插件过滤器
            val filteredVideos = com.android.purebilibili.core.plugin.json.JsonPluginManager.filterVideos(nativeFiltered)
            
            if (filteredVideos.isNotEmpty()) {
                //  [修复] 全局会话级去重逻辑：过滤掉本会话已看过的视频
                //  如果是刷新 (isLoadMore=false)，我们仍然希望能看到新内容，所以保留去重
                //  如果是加载更多，更不能有重复
                val uniqueNewVideos = filteredVideos.filter { it.bvid !in sessionSeenBvids }
                
                if (uniqueNewVideos.size < filteredVideos.size) {
                    com.android.purebilibili.core.util.Logger.d("HomeVM", "Filtered ${filteredVideos.size - uniqueNewVideos.size} duplicate videos (session-level)")
                }
                
                //  将新视频加入去重集合
                sessionSeenBvids.addAll(uniqueNewVideos.map { it.bvid })
                
                // 如果去重后为空，且原本不为空，说明全是重复内容
                if (uniqueNewVideos.isEmpty() && filteredVideos.isNotEmpty()) {
                     com.android.purebilibili.core.util.Logger.d("HomeVM", "⚠️ All videos were filtered as duplicates! Fetching next page...")
                     // 可以在这里触发一次自动加载更多 (递归调用需谨慎) -> 简单处理：显示"没有更多新内容"或者直接不做任何操作(保留旧列表)
                     // 为防止空页面，如果是在刷新操作中全被过滤了，也许应该保留 oldVideos?
                }

                if (uniqueNewVideos.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        videos = if (isLoadMore) _uiState.value.videos + uniqueNewVideos else uniqueNewVideos,
                        liveRooms = emptyList(),  // 清空直播列表
                        isLoading = false,
                        error = null
                    )
                } else {
                     //  全被过滤掉了
                    _uiState.value = _uiState.value.copy(
                        videos = if (!isLoadMore && oldVideos.isNotEmpty()) oldVideos else _uiState.value.videos,
                        isLoading = false,
                        error = if (!isLoadMore && oldVideos.isEmpty()) "推荐内容重复，请稍后再试" else null
                    )
                }
            } else {
                //  [问题15修复] 刷新时如果没有获取到新数据，保留旧列表
                _uiState.value = _uiState.value.copy(
                    videos = if (!isLoadMore && oldVideos.isNotEmpty()) oldVideos else _uiState.value.videos,
                    isLoading = false,
                    error = if (!isLoadMore && oldVideos.isEmpty()) "没有更多内容了" else null
                )
            }
        }.onFailure { error ->
            //  [问题15修复] 刷新失败时保留旧视频列表，不清空
            _uiState.value = _uiState.value.copy(
                videos = if (!isLoadMore && oldVideos.isNotEmpty()) oldVideos else _uiState.value.videos,
                isLoading = false,
                error = if (!isLoadMore && oldVideos.isEmpty()) error.message ?: "网络错误" else null
            )
        }
    }
    
    //  [新增] 获取关注动态列表
    private suspend fun fetchFollowFeed(isLoadMore: Boolean) {
        if (!isLoadMore) {
            fetchUserInfo()
            com.android.purebilibili.data.repository.DynamicRepository.resetPagination()
        }
        
        val result = com.android.purebilibili.data.repository.DynamicRepository.getDynamicFeed(!isLoadMore)
        
        if (isLoadMore) delay(100)
        
        result.onSuccess { items ->
            //  将 DynamicItem 转换为 VideoItem（只保留视频类型）
            val videos = items.mapNotNull { item ->
                val archive = item.modules.module_dynamic?.major?.archive
                if (archive != null && archive.bvid.isNotEmpty()) {
                    com.android.purebilibili.data.model.response.VideoItem(
                        bvid = archive.bvid,
                        title = archive.title,
                        pic = archive.cover,
                        duration = parseDurationText(archive.duration_text),
                        owner = com.android.purebilibili.data.model.response.Owner(
                            mid = item.modules.module_author?.mid ?: 0,
                            name = item.modules.module_author?.name ?: "",
                            face = item.modules.module_author?.face ?: ""
                        ),
                        stat = com.android.purebilibili.data.model.response.Stat(
                            view = parseStatText(archive.stat.play)
                        )
                    )
                } else null
            }
            
            if (videos.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    videos = if (isLoadMore) _uiState.value.videos + videos else videos,
                    liveRooms = emptyList(),
                    isLoading = false,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (!isLoadMore && _uiState.value.videos.isEmpty()) "暂无关注动态，请先关注一些UP主" else null
                )
            }
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (!isLoadMore && _uiState.value.videos.isEmpty()) error.message ?: "请先登录" else null
            )
        }
    }
    
    //  解析时长文本 "10:24" -> 624 秒
    private fun parseDurationText(text: String): Int {
        val parts = text.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (e: Exception) { 0 }
    }
    
    //  解析统计文本 "123.4万" -> 1234000
    private fun parseStatText(text: String): Int {
        return try {
            if (text.contains("万")) {
                (text.replace("万", "").toFloat() * 10000).toInt()
            } else if (text.contains("亿")) {
                (text.replace("亿", "").toFloat() * 100000000).toInt()
            } else {
                text.toIntOrNull() ?: 0
            }
        } catch (e: Exception) { 0 }
    }
    
    //  🔴 [改进] 获取直播间列表（同时获取关注和热门）
    private suspend fun fetchLiveRooms(isLoadMore: Boolean) {
        val page = if (isLoadMore) livePage else 1
        
        com.android.purebilibili.core.util.Logger.d("HomeVM", "🔴 fetchLiveRooms: isLoadMore=$isLoadMore, page=$page")
        
        if (!isLoadMore) {
            fetchUserInfo()
            
            // 🔴 [改进] 首次加载时同时获取关注和热门直播
            val isLoggedIn = !com.android.purebilibili.core.store.TokenManager.sessDataCache.isNullOrEmpty()
            
            // 并行获取关注和热门直播
            val followedResult = if (isLoggedIn) LiveRepository.getFollowedLive(1) else Result.success(emptyList())
            val popularResult = LiveRepository.getLiveRooms(1)
            
            // 处理关注直播结果
            val followedRooms = followedResult.getOrDefault(emptyList())
            
            // 处理热门直播结果
            popularResult.onSuccess { rooms ->
                if (rooms.isNotEmpty() || followedRooms.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        followedLiveRooms = followedRooms,
                        liveRooms = rooms,
                        videos = emptyList(),
                        isLoading = false,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "暂无直播"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    followedLiveRooms = followedRooms,
                    isLoading = false,
                    error = if (followedRooms.isEmpty()) e.message ?: "网络错误" else null
                )
            }
        } else {
            // 加载更多时只加载热门直播（关注的主播数量有限，不需要分页）
            val result = LiveRepository.getLiveRooms(page)
            delay(100)
            
            result.onSuccess { rooms ->
                if (rooms.isNotEmpty()) {
                    val existingRoomIds = _uiState.value.liveRooms.map { it.roomid }.toSet()
                    val newRooms = rooms.filter { it.roomid !in existingRoomIds }
                    
                    if (newRooms.isEmpty()) {
                        hasMoreLiveData = false
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        return@onSuccess
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        liveRooms = _uiState.value.liveRooms + newRooms,
                        isLoading = false,
                        error = null
                    )
                } else {
                    hasMoreLiveData = false
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = null  // 加载更多失败不显示错误
                )
            }
        }
    }
    
    //  提取用户信息获取逻辑
    private suspend fun fetchUserInfo() {
        val navResult = VideoRepository.getNavInfo()
        navResult.onSuccess { navData ->
            if (navData.isLogin) {
                val isVip = navData.vip.status == 1
                com.android.purebilibili.core.store.TokenManager.isVipCache = isVip
                com.android.purebilibili.core.store.TokenManager.midCache = navData.mid
                _uiState.value = _uiState.value.copy(
                    user = UserState(
                        isLogin = true,
                        face = navData.face,
                        name = navData.uname,
                        mid = navData.mid,
                        level = navData.level_info.current_level,
                        coin = navData.money,
                        bcoin = navData.wallet.bcoin_balance,
                        isVip = isVip
                    )
                )
                
                //  获取关注列表（异步，不阻塞主流程）
                fetchFollowingList(navData.mid)
            } else {
                com.android.purebilibili.core.store.TokenManager.isVipCache = false
                com.android.purebilibili.core.store.TokenManager.midCache = null
                _uiState.value = _uiState.value.copy(
                    user = UserState(isLogin = false),
                    followingMids = emptySet()
                )
            }
        }
    }
    
    //  获取关注列表（并行分页获取，支持更多关注，带本地缓存）
    private suspend fun fetchFollowingList(mid: Long) {
        val context = getApplication<android.app.Application>()
        val prefs = context.getSharedPreferences("following_cache", android.content.Context.MODE_PRIVATE)
        val cacheKey = "following_mids_$mid"
        val cacheTimeKey = "following_time_$mid"
        
        //  检查缓存（1小时内有效）
        val cachedTime = prefs.getLong(cacheTimeKey, 0)
        val cacheValidDuration = 60 * 60 * 1000L  // 1小时
        if (System.currentTimeMillis() - cachedTime < cacheValidDuration) {
            val cachedMids = prefs.getStringSet(cacheKey, null)
            if (!cachedMids.isNullOrEmpty()) {
                val mids = cachedMids.mapNotNull { it.toLongOrNull() }.toSet()
                _uiState.value = _uiState.value.copy(followingMids = mids)
                com.android.purebilibili.core.util.Logger.d("HomeVM", " Loaded ${mids.size} following mids from cache")
                return
            }
        }
        
        //  动态获取所有关注列表（无上限）
        try {
            val allMids = mutableSetOf<Long>()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var page = 1
                while (true) {  //  无限循环，直到获取完所有关注
                    try {
                        val result = com.android.purebilibili.core.network.NetworkModule.api.getFollowings(mid, page, 50)
                        if (result.code == 0 && result.data != null) {
                            val list = result.data.list ?: break
                            if (list.isEmpty()) break
                            
                            list.forEach { user -> allMids.add(user.mid) }
                            
                            // 如果这一页不满50，说明已经获取完所有关注
                            if (list.size < 50) {
                                com.android.purebilibili.core.util.Logger.d("HomeVM", " Reached end at page $page, total: ${allMids.size}")
                                break
                            }
                            page++
                        } else {
                            break
                        }
                    } catch (e: Exception) {
                        com.android.purebilibili.core.util.Logger.e("HomeVM", " Error at page $page", e)
                        break
                    }
                }
            }
            
            //  保存到本地缓存
            prefs.edit()
                .putStringSet(cacheKey, allMids.map { it.toString() }.toSet())
                .putLong(cacheTimeKey, System.currentTimeMillis())
                .apply()
            
            _uiState.value = _uiState.value.copy(followingMids = allMids.toSet())
            com.android.purebilibili.core.util.Logger.d("HomeVM", " Total following mids fetched and cached: ${allMids.size}")
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("HomeVM", " Error fetching following list", e)
        }
    }
}