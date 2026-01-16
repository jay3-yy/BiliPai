// 文件路径: feature/dynamic/DynamicViewModel.kt
package com.android.purebilibili.feature.dynamic

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.LiveRoom
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.data.repository.LiveRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

/**
 *  动态页面 ViewModel
 * 支持：动态列表、侧边栏关注用户、在线状态
 */
class DynamicViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()
    private val cachePrefs = appContext.getSharedPreferences(PREFS_DYNAMIC_CACHE, Context.MODE_PRIVATE)
    private val userPrefs = appContext.getSharedPreferences(PREFS_DYNAMIC_USERS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedLiveRooms: List<LiveRoom> = emptyList()

    private val _uiState = MutableStateFlow(DynamicUiState())
    val uiState: StateFlow<DynamicUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    //  侧边栏相关状态
    private val _followedUsers = MutableStateFlow<List<SidebarUser>>(emptyList())
    val followedUsers: StateFlow<List<SidebarUser>> = _followedUsers.asStateFlow()

    private val _selectedUserId = MutableStateFlow<Long?>(null)
    val selectedUserId: StateFlow<Long?> = _selectedUserId.asStateFlow()

    private val _isSidebarExpanded = MutableStateFlow(true)
    val isSidebarExpanded: StateFlow<Boolean> = _isSidebarExpanded.asStateFlow()

    private val _pinnedUserIds = MutableStateFlow<Set<Long>>(emptySet())
    val pinnedUserIds: StateFlow<Set<Long>> = _pinnedUserIds.asStateFlow()

    private val _hiddenUserIds = MutableStateFlow<Set<Long>>(emptySet())
    val hiddenUserIds: StateFlow<Set<Long>> = _hiddenUserIds.asStateFlow()

    private val _showHiddenUsers = MutableStateFlow(false)
    val showHiddenUsers: StateFlow<Boolean> = _showHiddenUsers.asStateFlow()

    init {
        loadUserPreferences()
        loadCachedDynamics()
        rebuildFollowedUsers()
        refreshInBackground()
    }
    
    private fun loadUserPreferences() {
        val pinned = userPrefs.getStringSet(KEY_PINNED_USERS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        val hidden = userPrefs.getStringSet(KEY_HIDDEN_USERS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        _pinnedUserIds.value = pinned
        _hiddenUserIds.value = hidden
    }

    private fun saveUserPreferences(pinned: Set<Long>, hidden: Set<Long>) {
        userPrefs.edit()
            .putStringSet(KEY_PINNED_USERS, pinned.map { it.toString() }.toSet())
            .putStringSet(KEY_HIDDEN_USERS, hidden.map { it.toString() }.toSet())
            .apply()
    }

    private fun loadCachedDynamics() {
        val cachedJson = cachePrefs.getString(KEY_DYNAMIC_CACHE, null) ?: return
        runCatching { json.decodeFromString<List<DynamicItem>>(cachedJson) }
            .onSuccess { items ->
                if (items.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        isLoading = false,
                        error = null
                    )
                }
            }
    }

    private fun saveDynamicCache(items: List<DynamicItem>) {
        if (items.isEmpty()) return
        val payload = json.encodeToString(items.take(MAX_CACHE_ITEMS))
        cachePrefs.edit()
            .putString(KEY_DYNAMIC_CACHE, payload)
            .putLong(KEY_DYNAMIC_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun refreshInBackground() {
        viewModelScope.launch { refreshData(showRefreshIndicator = false) }
    }

    private suspend fun refreshData(showRefreshIndicator: Boolean) {
        if (showRefreshIndicator) {
            _isRefreshing.value = true
        }
        coroutineScope {
            val dynamicJob = async {
                loadDynamicFeedInternal(refresh = true, showLoading = _uiState.value.items.isEmpty())
            }
            val liveJob = async { loadFollowedUsersInternal() }
            dynamicJob.await()
            liveJob.await()
        }
        if (showRefreshIndicator) {
            _isRefreshing.value = false
        }
    }

    /**
     *  加载关注用户列表及其直播状态
     */
    fun loadFollowedUsers() {
        viewModelScope.launch { loadFollowedUsersInternal() }
    }

    private suspend fun loadFollowedUsersInternal() {
        LiveRepository.getFollowedLive(page = 1).onSuccess { liveRooms ->
            cachedLiveRooms = liveRooms
            rebuildFollowedUsers()
        }
    }

    /**
     * 从动态列表提取用户
     */
    private fun extractUsersFromDynamics(items: List<DynamicItem>): List<SidebarUser> {
        val latestByUser = mutableMapOf<Long, SidebarUser>()
        items.mapNotNull { it.modules.module_author }.forEach { author ->
            if (author.mid <= 0 || author.name.isBlank()) return@forEach
            val lastActive = author.pub_ts.takeIf { it > 0 } ?: 0L
            val existing = latestByUser[author.mid]
            if (existing == null || lastActive > existing.lastActiveTs) {
                latestByUser[author.mid] = SidebarUser(
                    uid = author.mid,
                    name = author.name,
                    face = author.face,
                    isLive = false,
                    lastActiveTs = lastActive
                )
            }
        }
        return latestByUser.values.toList()
    }

    /**
     * 从直播列表提取用户（包含在线状态）
     */
    private fun extractUsersFromLive(rooms: List<LiveRoom>): List<SidebarUser> {
        val nowSeconds = System.currentTimeMillis() / 1000
        return rooms.map { room ->
            SidebarUser(
                uid = room.uid,
                name = room.uname,
                face = room.face,
                isLive = true,
                lastActiveTs = nowSeconds  // 直播中视作最近活跃
            )
        }
    }

    private fun rebuildFollowedUsers() {
        val mergedUsers = mergeUsers(
            extractUsersFromDynamics(_uiState.value.items),
            extractUsersFromLive(cachedLiveRooms)
        )
        _followedUsers.value = applyUserPreferences(mergedUsers)
    }

    private fun mergeUsers(
        dynamicUsers: List<SidebarUser>,
        liveUsers: List<SidebarUser>
    ): List<SidebarUser> {
        val merged = mutableMapOf<Long, SidebarUser>()
        (dynamicUsers + liveUsers).forEach { user ->
            val existing = merged[user.uid]
            if (existing == null) {
                merged[user.uid] = user
            } else {
                merged[user.uid] = existing.copy(
                    name = if (user.name.isNotBlank()) user.name else existing.name,
                    face = if (user.face.isNotBlank()) user.face else existing.face,
                    isLive = existing.isLive || user.isLive,
                    lastActiveTs = max(existing.lastActiveTs, user.lastActiveTs)
                )
            }
        }
        return merged.values.toList()
    }

    private fun applyUserPreferences(users: List<SidebarUser>): List<SidebarUser> {
        val pinned = _pinnedUserIds.value
        val hidden = _hiddenUserIds.value
        val showHidden = _showHiddenUsers.value
        return users
            .map { user ->
                user.copy(
                    isPinned = pinned.contains(user.uid),
                    isHidden = hidden.contains(user.uid)
                )
            }
            .filter { showHidden || !it.isHidden }
            .sortedWith(
                compareByDescending<SidebarUser> { it.isPinned }
                    .thenByDescending { it.isLive }
                    .thenByDescending { it.lastActiveTs }
                    .thenBy { it.name }
            )
    }
    
    /**
     * 选择用户过滤动态
     */
    fun selectUser(uid: Long?) {
        _selectedUserId.value = uid
    }
    
    /**
     * 切换侧边栏展开/收起
     */
    fun toggleSidebar() {
        _isSidebarExpanded.value = !_isSidebarExpanded.value
    }

    fun togglePinUser(uid: Long) {
        val pinned = _pinnedUserIds.value.toMutableSet()
        if (pinned.contains(uid)) {
            pinned.remove(uid)
        } else {
            pinned.add(uid)
        }
        _pinnedUserIds.value = pinned
        saveUserPreferences(pinned, _hiddenUserIds.value)
        rebuildFollowedUsers()
    }

    fun toggleHiddenUser(uid: Long) {
        val hidden = _hiddenUserIds.value.toMutableSet()
        val pinned = _pinnedUserIds.value.toMutableSet()
        val isNowHidden = if (hidden.contains(uid)) {
            hidden.remove(uid)
            false
        } else {
            hidden.add(uid)
            true
        }
        if (isNowHidden) {
            pinned.remove(uid)
            if (_selectedUserId.value == uid) {
                _selectedUserId.value = null
            }
        }
        _hiddenUserIds.value = hidden
        _pinnedUserIds.value = pinned
        saveUserPreferences(pinned, hidden)
        rebuildFollowedUsers()
    }

    fun toggleShowHiddenUsers() {
        val showHidden = !_showHiddenUsers.value
        _showHiddenUsers.value = showHidden
        if (!showHidden) {
            val selected = _selectedUserId.value
            if (selected != null && _hiddenUserIds.value.contains(selected)) {
                _selectedUserId.value = null
            }
        }
        rebuildFollowedUsers()
    }
    
    /**
     * 加载动态列表
     */
    fun loadDynamicFeed(refresh: Boolean = false) {
        if (!refresh && (_uiState.value.isLoading || _isRefreshing.value)) return
        viewModelScope.launch {
            loadDynamicFeedInternal(
                refresh = refresh,
                showLoading = refresh && _uiState.value.items.isEmpty()
            )
        }
    }

    private suspend fun loadDynamicFeedInternal(
        refresh: Boolean,
        showLoading: Boolean = false
    ) {
        if (refresh) {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = null)
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        }

        val result = DynamicRepository.getDynamicFeed(refresh)

        result.fold(
            onSuccess = { items ->
                val currentItems = if (refresh) emptyList() else _uiState.value.items
                val mergedItems = currentItems + items
                _uiState.value = _uiState.value.copy(
                    items = mergedItems,
                    isLoading = false,
                    error = null,
                    hasMore = DynamicRepository.hasMoreData()
                )
                saveDynamicCache(mergedItems)
                rebuildFollowedUsers()
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "加载失败"
                )
            }
        )
    }
    
    fun refresh() {
        viewModelScope.launch { refreshData(showRefreshIndicator = true) }
    }
    
    fun loadMore() {
        if (!_uiState.value.hasMore || _uiState.value.isLoading || _isRefreshing.value) return
        loadDynamicFeed(refresh = false)
    }
    
    // ====================  动态评论/点赞/转发功能 ====================
    
    // 当前选中的动态（用于评论弹窗）
    private val _selectedDynamic = MutableStateFlow<DynamicItem?>(null)
    val selectedDynamicId: StateFlow<String?> = _selectedDynamic.asStateFlow().let { flow ->
        MutableStateFlow<String?>(null).also { derived ->
            viewModelScope.launch {
                flow.collect { derived.value = it?.id_str }
            }
        }
    }
    
    // 评论列表
    private val _comments = MutableStateFlow<List<com.android.purebilibili.data.model.response.ReplyItem>>(emptyList())
    val comments: StateFlow<List<com.android.purebilibili.data.model.response.ReplyItem>> = _comments.asStateFlow()
    
    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()
    
    // 点赞状态缓存 (dynamicId -> isLiked)
    private val _likedDynamics = MutableStateFlow<Set<String>>(emptySet())
    val likedDynamics: StateFlow<Set<String>> = _likedDynamics.asStateFlow()
    
    /**
     *  根据动态类型获取评论 oid 和 type
     * - 视频动态: type=1, oid=aid
     * - 图片动态: type=11, oid=draw.id
     * - 文字动态: type=17, oid=id_str
     * - 转发动态: 使用原动态的信息
     */
    private fun getCommentParams(item: DynamicItem): Pair<Long, Int>? {
        val dynamicType = com.android.purebilibili.data.model.response.DynamicType.fromApiValue(item.type)
        return when (dynamicType) {
            com.android.purebilibili.data.model.response.DynamicType.VIDEO -> {
                // 视频动态: type=1, oid=aid
                val aid = item.modules.module_dynamic?.major?.archive?.aid?.toLongOrNull()
                if (aid != null) Pair(aid, 1) else null
            }
            com.android.purebilibili.data.model.response.DynamicType.DRAW -> {
                // 图片动态: type=11, oid=draw.id  
                val drawId = item.modules.module_dynamic?.major?.draw?.id
                if (drawId != null && drawId > 0) Pair(drawId, 11) else null
            }
            com.android.purebilibili.data.model.response.DynamicType.WORD -> {
                // 纯文字动态: type=17, oid=id_str
                val oid = item.id_str.toLongOrNull()
                if (oid != null) Pair(oid, 17) else null
            }
            com.android.purebilibili.data.model.response.DynamicType.FORWARD -> {
                // 转发动态: 使用原动态的信息
                item.orig?.let { getCommentParams(it) }
            }
            else -> null
        }
    }
    
    /**
     *  根据动态ID获取动态对象
     */
    private fun findDynamicById(dynamicId: String): DynamicItem? {
        return _uiState.value.items.find { it.id_str == dynamicId }
    }
    
    /**
     *  打开评论弹窗
     */
    fun openCommentSheet(dynamicId: String) {
        val item = findDynamicById(dynamicId)
        _selectedDynamic.value = item
        if (item != null) {
            loadCommentsForDynamic(item)
        }
    }
    
    /**
     *  关闭评论弹窗
     */
    fun closeCommentSheet() {
        _selectedDynamic.value = null
        _comments.value = emptyList()
    }
    
    /**
     *  加载动态评论 (使用正确的 oid 和 type)
     */
    private fun loadCommentsForDynamic(item: DynamicItem) {
        viewModelScope.launch {
            _commentsLoading.value = true
            try {
                val params = getCommentParams(item)
                if (params == null) {
                    com.android.purebilibili.core.util.Logger.e("DynamicVM", "无法获取评论参数: type=${item.type}")
                    return@launch
                }
                val (oid, type) = params
                com.android.purebilibili.core.util.Logger.d("DynamicVM", "加载评论: oid=$oid, type=$type, dynamicType=${item.type}")
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi
                    .getDynamicReplies(oid = oid, type = type)
                com.android.purebilibili.core.util.Logger.d("DynamicVM", "评论响应: code=${response.code}, message=${response.message}, replies=${response.data?.replies?.size}")
                if (response.code == 0 && response.data != null) {
                    _comments.value = response.data.replies ?: emptyList()
                } else {
                    com.android.purebilibili.core.util.Logger.e("DynamicVM", "评论加载失败: code=${response.code}, msg=${response.message}")
                }
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("DynamicVM", "加载评论异常: ${e.message}")
                e.printStackTrace()
            } finally {
                _commentsLoading.value = false
            }
        }
    }
    
    /**
     *  加载评论 (兼容旧调用方式)
     */
    fun loadComments(dynamicId: String) {
        val item = findDynamicById(dynamicId)
        if (item != null) {
            loadCommentsForDynamic(item)
        }
    }
    
    /**
     *  发表评论
     */
    fun postComment(dynamicId: String, message: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launch
                }
                val oid = dynamicId.toLongOrNull() ?: return@launch
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi
                    .addDynamicReply(oid = oid, type = 17, message = message, csrf = csrf)
                if (response.code == 0) {
                    onResult(true, "评论成功")
                    // 刷新评论列表
                    loadComments(dynamicId)
                } else {
                    onResult(false, response.message ?: "评论失败")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }
    
    /**
     *  点赞动态
     */
    fun likeDynamic(dynamicId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launch
                }
                val isLiked = _likedDynamics.value.contains(dynamicId)
                val up = if (isLiked) 2 else 1  // 1=点赞, 2=取消
                
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi
                    .likeDynamic(dynamicId = dynamicId, up = up, csrf = csrf)
                if (response.code == 0) {
                    // 更新本地状态
                    _likedDynamics.value = if (isLiked) {
                        _likedDynamics.value - dynamicId
                    } else {
                        _likedDynamics.value + dynamicId
                    }
                    onResult(true, if (isLiked) "已取消" else "已点赞")
                } else {
                    onResult(false, response.message ?: "操作失败")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }
    
    /**
     *  转发动态
     */
    fun repostDynamic(dynamicId: String, content: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launch
                }
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi
                    .repostDynamic(dynIdStr = dynamicId, content = content, csrf = csrf)
                if (response.code == 0) {
                    onResult(true, "转发成功")
                } else {
                    onResult(false, response.message ?: "转发失败")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    companion object {
        private const val PREFS_DYNAMIC_CACHE = "dynamic_cache"
        private const val PREFS_DYNAMIC_USERS = "dynamic_user_prefs"
        private const val KEY_DYNAMIC_CACHE = "dynamic_items_cache"
        private const val KEY_DYNAMIC_CACHE_TIME = "dynamic_cache_time"
        private const val KEY_PINNED_USERS = "dynamic_pinned_users"
        private const val KEY_HIDDEN_USERS = "dynamic_hidden_users"
        private const val MAX_CACHE_ITEMS = 100
    }
}

/**
 *  侧边栏用户数据
 */
data class SidebarUser(
    val uid: Long,
    val name: String,
    val face: String,
    val isLive: Boolean = false,
    val lastActiveTs: Long = 0L,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false
)

/**
 * 动态页面 UI 状态
 */
data class DynamicUiState(
    val items: List<DynamicItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true
)
