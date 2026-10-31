// 文件路径: feature/dynamic/DynamicViewModel.kt
package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.feature.comment.CommentPagingSession
import com.android.purebilibili.feature.comment.CommentPageRequest
import com.android.purebilibili.feature.dynamic.components.DynamicDisplayMode

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPagingSession
import com.android.purebilibili.core.network.DynamicDeleteRequest
import com.android.purebilibili.core.network.DynamicTopRequest
import com.android.purebilibili.core.network.DynamicVisibilityRequest
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.buildDynamicRepostRequest
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.appendDistinctByKey
import com.android.purebilibili.core.util.prependDistinctByKey
import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.FollowingUser
import com.android.purebilibili.data.model.response.LiveRoom
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyInteractionData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.BlockedUpRepository
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.DynamicCreateRepository
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.data.repository.LiveRepository
import com.android.purebilibili.data.repository.SessionRepository
import com.android.purebilibili.feature.dynamic.components.DynamicManageAction
import com.android.purebilibili.feature.dynamic.components.resolveDynamicFoldIds
import com.android.purebilibili.feature.dynamic.components.unfoldRelatedDynamicItems
import com.android.purebilibili.feature.dynamic.components.DynamicReserveAction
import com.android.purebilibili.feature.dynamic.components.DynamicReserveResult
import com.android.purebilibili.feature.dynamic.components.buildDynamicVisibilityObjectId
import com.android.purebilibili.feature.dynamic.components.resolveDynamicVisibilityAction
import com.android.purebilibili.feature.dynamic.notification.LiveReserveReminderScheduler
import com.android.purebilibili.feature.comment.SentCommentReplyStore
import com.android.purebilibili.feature.comment.SentCommentReplyStores
import com.android.purebilibili.feature.video.viewmodel.mergeSentSubReplies
import com.android.purebilibili.feature.video.viewmodel.withSentSubReply
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraft
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraftState
import com.android.purebilibili.feature.video.viewmodel.CommentMentionSearchUiState
import com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey
import com.android.purebilibili.feature.video.viewmodel.resolveRoutedCommentRootReply
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyLoadedTotalCount
import com.android.purebilibili.feature.video.viewmodel.isSortedSubReplyPageEnd
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.resetForSort
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyRemoteTotalCount
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyPageRequest
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyNextRequest
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal data class DynamicStartupLoadPlan(
    val refreshFeedImmediately: Boolean,
    val loadLiveStatusImmediately: Boolean,
    val loadFollowingsImmediately: Boolean,
    val followingsHydrationDelayMs: Long,
    val initialFollowingsPageLimit: Int
)

internal fun resolveDynamicStartupLoadPlan(): DynamicStartupLoadPlan {
    return DynamicStartupLoadPlan(
        refreshFeedImmediately = true,
        loadLiveStatusImmediately = true,
        loadFollowingsImmediately = false,
        followingsHydrationDelayMs = 1_200L,
        initialFollowingsPageLimit = 1
    )
}

internal fun resolveDynamicFollowingsPageLimit(isStartupHydration: Boolean): Int {
    return if (isStartupHydration) 1 else 3
}

internal fun hasLoadedAllDynamicFollowings(
    pageSize: Int,
    accumulatedCount: Int,
    reportedTotal: Int,
): Boolean {
    return pageSize < DYNAMIC_FOLLOWINGS_PAGE_SIZE ||
        (reportedTotal > 0 && accumulatedCount >= reportedTotal)
}

/**
 *  动态页面 ViewModel
 * 支持：动态列表、侧边栏关注用户、在线状态
 */
class DynamicViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>()
    private var activeAccountIdentity = TokenManager.accountIdentity.value
    private var accountScope = CoroutineScope(
        viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job])
    )
    private fun launchAccount(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        val requestedAccount = activeAccountIdentity
        return accountScope.launch(context) {
            if (requestedAccount != TokenManager.accountIdentity.value) return@launch
            block()
        }
    }
    private var cachePrefs = appContext.getSharedPreferences(
        dynamicAccountStorageName(PREFS_DYNAMIC_CACHE, activeAccountIdentity.mid), Context.MODE_PRIVATE
    )
    private var userPrefs = appContext.getSharedPreferences(
        dynamicAccountStorageName(PREFS_DYNAMIC_USERS, activeAccountIdentity.mid), Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val blockedUpRepository = BlockedUpRepository(appContext)

    private var cachedLiveRooms: List<LiveRoom> = emptyList()
    
    //  [新增] 缓存关注列表
    private var cachedFollowings: List<FollowingUser> = emptyList()
    private var incrementalTimelineRefreshEnabled: Boolean = false
    private var lastFollowingsLoadMs: Long = 0L
    private var isFollowingsLoading: Boolean = false
    private val _authorSearchLoading = MutableStateFlow(false)
    val authorSearchLoading: StateFlow<Boolean> = _authorSearchLoading.asStateFlow()
    private var followingsFullyLoaded: Boolean = false
    private var completeFollowingsLoadRequested: Boolean = false
    private var cacheSaveJob: Job? = null
    private var startupFollowingsHydrationScheduled: Boolean = false
    private var startupLoadsActivated: Boolean = false
    private val pageActivity = MutableStateFlow(false)
    private val isPageActive: Boolean get() = pageActivity.value
    private var followStateObserverStarted: Boolean = false

    private val _uiState = MutableStateFlow(createDynamicStartupUiState())
    val uiState: StateFlow<DynamicUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // 每个分类及作者拥有独立的 Paging 收集任务。
    private val activeTimelineRequestTokens = mutableMapOf<String, Long>()
    private val timelinePagingSessions = mutableMapOf<String, ForwardPagingSession<String, DynamicPageChunk>>()
    private val timelinePagingJobs = mutableMapOf<String, Job>()
    private val timelinePagingStates = mutableMapOf<String, CombinedLoadStates>()
    private val timelinePagingPages = mutableMapOf<String, List<DynamicPageChunk>>()
    private var userPagingSession: ForwardPagingSession<String, DynamicPageChunk>? = null
    private val isUserLoadingLocked: Boolean get() = _uiState.value.userIsLoading
    private var userDynamicsJob: Job? = null
    private var userSelectionJob: Job? = null
    private val removedDynamicIds = mutableSetOf<String>()
    private var activeUserDynamicsRequestToken: Long = 0L

    //  侧边栏相关状态
    private val _followedUsers = MutableStateFlow<List<SidebarUser>>(emptyList())
    val followedUsers: StateFlow<List<SidebarUser>> = _followedUsers.asStateFlow()
    private val _selfUser = MutableStateFlow<SidebarUser?>(null)
    val selfUser: StateFlow<SidebarUser?> = _selfUser.asStateFlow()
    private var selfProfileRefreshJob: Job? = null

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

    //  [新增] 显示模式状态
    private val _displayMode = MutableStateFlow(DynamicDisplayMode.SIDEBAR)
    val displayMode: StateFlow<DynamicDisplayMode> = _displayMode.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    init {
        loadUserPreferences()
        _uiState.value = createDynamicStartupUiState(_selectedTab.value)
        viewModelScope.launch {
            SettingsManager.getIncrementalTimelineRefresh(appContext).collect { enabled ->
                incrementalTimelineRefreshEnabled = enabled
            }
        }
        observeAccountChanges()
    }

    private fun observeAccountChanges() {
        viewModelScope.launch {
            // Comment and interaction state is declared below the constructor's init block.
            yield()
            TokenManager.accountIdentity.collect { identity ->
                if (identity == activeAccountIdentity) return@collect
                accountScope.cancel()
                closeCommentSheet()
                sentCommentDynamicId = null
                sentCommentTarget = null
                removedCommentIds.clear()
                commentCache.clear()
                _uiState.value = createDynamicStartupUiState()
                _followedUsers.value = emptyList()
                _selfUser.value = null
                _selectedUserId.value = null
                _likedDynamics.value = emptySet()
                _likeOverrides.value = emptyMap()
                accountScope.coroutineContext[Job]?.cancelAndJoin()
                if (identity != TokenManager.accountIdentity.value) return@collect
                activeAccountIdentity = identity
                accountScope = CoroutineScope(
                    viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job])
                )
                cachePrefs = appContext.getSharedPreferences(
                    dynamicAccountStorageName(PREFS_DYNAMIC_CACHE, identity.mid), Context.MODE_PRIVATE
                )
                userPrefs = appContext.getSharedPreferences(
                    dynamicAccountStorageName(PREFS_DYNAMIC_USERS, identity.mid), Context.MODE_PRIVATE
                )
                cachedLiveRooms = emptyList()
                cachedFollowings = emptyList()
                lastFollowingsLoadMs = 0L
                isFollowingsLoading = false
                _authorSearchLoading.value = false
                selfProfileRefreshJob = null
                followingsFullyLoaded = false
                completeFollowingsLoadRequested = false
                startupFollowingsHydrationScheduled = false
                activeTimelineRequestTokens.clear()
                timelinePagingSessions.clear()
                timelinePagingJobs.clear()
                timelinePagingStates.clear()
                timelinePagingPages.clear()
                removedDynamicIds.clear()
                userPagingSession = null
                activeUserDynamicsRequestToken++
                _selectedUserId.value = null
                _followedUsers.value = emptyList()
                _likedDynamics.value = emptySet()
                _likeOverrides.value = emptyMap()
                likeRequestGate = DynamicLikeRequestGate()
                _isRefreshing.value = false
                loadUserPreferences()
                _uiState.value = createDynamicStartupUiState(_selectedTab.value)
                startupLoadsActivated = false
                if (isPageActive) {
                    requestSelfProfileRefresh()
                    activateStartupLoads()
                }
            }
        }
    }

    fun setPageActive(active: Boolean) {
        val wasActive = pageActivity.value
        pageActivity.value = active
        if (active) {
            if (!wasActive) requestSelfProfileRefresh()
            activateStartupLoads()
        }
    }

    private fun requestSelfProfileRefresh() {
        if (selfProfileRefreshJob?.isActive == true) return
        val identity = activeAccountIdentity
        val mid = identity.mid ?: return
        selfProfileRefreshJob = launchAccount {
            try {
                val cachedFace = withContext(Dispatchers.IO) {
                    AccountSessionStore.getAccounts(appContext)
                        .firstOrNull { it.mid == mid }?.face.orEmpty()
                }
                if (identity != TokenManager.accountIdentity.value || identity != activeAccountIdentity) {
                    return@launchAccount
                }
                if (cachedFace.isNotBlank()) {
                    _selfUser.value = SidebarUser(uid = mid, name = "我", face = cachedFace)
                }
                val profile = SessionRepository.account().getOrNull() ?: return@launchAccount
                if (identity != TokenManager.accountIdentity.value ||
                    identity != activeAccountIdentity || profile.mid != mid
                ) {
                    return@launchAccount
                }
                withContext(Dispatchers.IO) {
                    AccountSessionStore.updateAccountAvatar(appContext, mid, profile.face)
                }
                if (identity != TokenManager.accountIdentity.value || identity != activeAccountIdentity) {
                    return@launchAccount
                }
                _selfUser.value = SidebarUser(
                    uid = mid,
                    name = "我",
                    face = profile.face.ifBlank { _selfUser.value?.face.orEmpty() }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun activateStartupLoads() {
        if (startupLoadsActivated) return
        startupLoadsActivated = true
        if (!followStateObserverStarted) {
            followStateObserverStarted = true
            observeFollowStateChanges()
        }
        hydrateStartupData()
    }

    private fun hydrateStartupData() {
        loadNotInterestedDynamicIds()
        val requestType = resolveDynamicFeedRequestType(_selectedTab.value)
        _uiState.value = updateDynamicTimelinePage(_uiState.value, requestType) { page ->
            if (page.items.isEmpty()) page.copy(isLoading = true, error = null) else page
        }
        launchAccount {
            pageActivity.first { it }
            loadCachedDynamics()
            pageActivity.first { it }
            rebuildFollowedUsers()
            loadUplistUpdates()
            refreshInBackground(resolveDynamicStartupLoadPlan())
        }
        if (_selectedTab.value == 0) {
            requestCompleteFollowingsLoad()
        }
    }

    private fun observeFollowStateChanges() {
        viewModelScope.launch {
            ActionRepository.followStateChanges.collect { change ->
                // Preferences/cache for a newly switched account are not hydrated until activation.
                if (!startupLoadsActivated) return@collect
                if (change.isFollowing) {
                    followingsFullyLoaded = false
                    lastFollowingsLoadMs = 0L
                    requestFollowingsRefreshIfStale()
                } else {
                    applyAuthorUnfollow(change.mid)
                }
            }
        }
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

        // 加载显示模式
        val modeName = userPrefs.getString(KEY_DISPLAY_MODE, DynamicDisplayMode.SIDEBAR.name)
        _displayMode.value = try {
            DynamicDisplayMode.valueOf(modeName ?: DynamicDisplayMode.SIDEBAR.name)
        } catch (e: Exception) {
            DynamicDisplayMode.SIDEBAR
        }
        val savedSelectedTab = if (userPrefs.contains(KEY_SELECTED_TAB)) {
            userPrefs.getInt(KEY_SELECTED_TAB, 0)
        } else {
            null
        }
        _selectedTab.value = resolveDynamicSelectedTab(
            savedTab = savedSelectedTab,
            tabCount = DYNAMIC_TOP_TAB_COUNT
        )
    }

    private fun loadNotInterestedDynamicIds() {
        val ids = normalizeDynamicNotInterestedIds(
            cachePrefs.getStringSet(KEY_NOT_INTERESTED_DYNAMIC_IDS, emptySet()).orEmpty(),
            MAX_NOT_INTERESTED_DYNAMIC_IDS,
        )
            .toImmutableSet()
        _uiState.value = _uiState.value.copy(tempBannedDynamicIds = ids)
    }

    private fun persistNotInterestedDynamicIds(ids: Set<String>) {
        cachePrefs.edit()
            .putStringSet(
                KEY_NOT_INTERESTED_DYNAMIC_IDS,
                normalizeDynamicNotInterestedIds(ids, MAX_NOT_INTERESTED_DYNAMIC_IDS),
            )
            .apply()
    }

    private fun saveUserPreferences(pinned: Set<Long>, hidden: Set<Long>) {
        userPrefs.edit()
            .putStringSet(KEY_PINNED_USERS, pinned.map { it.toString() }.toSet())
            .putStringSet(KEY_HIDDEN_USERS, hidden.map { it.toString() }.toSet())
            .apply()
    }

    private suspend fun loadCachedDynamics() {
        if (activeAccountIdentity.mid == null) return
        val cachedAccount = activeAccountIdentity
        val targetPrefs = cachePrefs
        val requestToken = activeTimelineRequestTokens["all"]
        val cachedJson = withContext(Dispatchers.IO) {
            targetPrefs.getString(KEY_DYNAMIC_CACHE, null)
        } ?: return
        val items = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<List<DynamicItem>>(cachedJson)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
        }
        pageActivity.first { it }
        if (!shouldApplyDeferredDynamicCache(
                sameAccount = cachedAccount == activeAccountIdentity &&
                    cachedAccount == TokenManager.accountIdentity.value,
                requestTokenBeforeRead = requestToken,
                currentRequestToken = activeTimelineRequestTokens["all"],
                hasTimelineItems = _uiState.value.timelinePage("all").items.isNotEmpty(),
            )
        ) return
        if (items.isNotEmpty()) {
            _uiState.value = updateDynamicTimelinePage(
                currentState = _uiState.value,
                requestType = "all"
            ) { page ->
                page.copy(
                    items = items.toImmutableList(),
                    isLoading = false,
                    error = null,
                    isCachePlaceholder = true
                )
            }
        }
    }

    private fun saveDynamicCache(items: List<DynamicItem>) {
        if (activeAccountIdentity.mid == null) return
        if (items.isEmpty()) {
            cacheSaveJob?.cancel()
            cachePrefs.edit()
                .remove(KEY_DYNAMIC_CACHE)
                .remove(KEY_DYNAMIC_CACHE_TIME)
                .apply()
            return
        }
        val snapshot = items.take(MAX_CACHE_ITEMS)
        val targetPrefs = cachePrefs
        cacheSaveJob?.cancel()
        cacheSaveJob = launchAccount(Dispatchers.Default) {
            val payload = json.encodeToString(snapshot)
            withContext(Dispatchers.IO) {
                targetPrefs.edit()
                    .putString(KEY_DYNAMIC_CACHE, payload)
                    .putLong(KEY_DYNAMIC_CACHE_TIME, System.currentTimeMillis())
                    .apply()
            }
        }
    }

    private fun refreshInBackground(
        startupPlan: DynamicStartupLoadPlan = resolveDynamicStartupLoadPlan()
    ) {
        launchAccount {
            pageActivity.first { it }
            refreshData(showRefreshIndicator = false)
            scheduleStartupFollowingsHydration(startupPlan)
        }
    }

    private suspend fun refreshData(
        showRefreshIndicator: Boolean,
        selectedTab: Int = _selectedTab.value
    ) {
        if (showRefreshIndicator) {
            _isRefreshing.value = true
            requestSelfProfileRefresh()
        }
        try {
            val requestType = resolveDynamicFeedRequestType(selectedTab)
            val refreshUserId = resolveDynamicRefreshUserId(
                selectedTab = selectedTab,
                selectedUserId = _selectedUserId.value
            )
            val userRequestToken = activeUserDynamicsRequestToken
            coroutineScope {
                val dynamicJob = async {
                    if (refreshUserId != null) {
                        loadUserDynamics(
                            uid = refreshUserId,
                            refresh = true,
                            requestToken = userRequestToken
                        )
                    } else {
                        loadDynamicFeedInternal(
                            refresh = true,
                            showLoading = _uiState.value.timelinePage(requestType).items.isEmpty(),
                            requestType = requestType
                        )
                    }
                }
                val liveJob = async { loadFollowedUsersInternal() }
                dynamicJob.await()
                liveJob.await()
            }
        } finally {
            if (showRefreshIndicator) {
                _isRefreshing.value = false
            }
        }
    }

    /**
     *  加载关注用户列表及其直播状态
     */
    fun loadFollowedUsers() {
        launchAccount { loadFollowedUsersInternal() }
    }

    private suspend fun loadFollowedUsersInternal() {
        LiveRepository.getFollowedLive(page = 1).onSuccess { liveRooms ->
            cachedLiveRooms = liveRooms
            rebuildFollowedUsers()
        }
    }
    
    /**
     *  [新增] 加载完整的关注列表
     */
    private suspend fun loadAllFollowings(
        force: Boolean = false,
        pageLimit: Int? = null,
    ) {
        if (isFollowingsLoading) return
        val now = System.currentTimeMillis()
        if (!force && !shouldReloadFollowings(nowMs = now, lastLoadMs = lastFollowingsLoadMs)) {
            return
        }
        isFollowingsLoading = true
        _authorSearchLoading.value = true
        try {
            // UID 来自当前账号身份；/nav 由本人资料刷新统一请求并更新头像缓存。
            val myMid = activeAccountIdentity.mid ?: return
            
            val maxPages = pageLimit?.coerceAtLeast(1) ?: Int.MAX_VALUE
            // 首轮可限制一页；进入 UP 模式后按接口 total 拉完整关注列表。
            val allFollowings = mutableListOf<FollowingUser>()
            var reachedEnd = false
            for (page in 1..maxPages) {
                val response = NetworkModule.api.getFollowings(
                    vmid = myMid,
                    pn = page,
                    ps = DYNAMIC_FOLLOWINGS_PAGE_SIZE,
                )
                val responseData = response.data ?: break
                val users = responseData.list ?: break
                allFollowings.addAll(users)
                if (hasLoadedAllDynamicFollowings(
                        pageSize = users.size,
                        accumulatedCount = allFollowings.size,
                        reportedTotal = responseData.total,
                    )
                ) {
                    reachedEnd = true
                    break
                }
            }
            
            cachedFollowings = allFollowings
            followingsFullyLoaded = reachedEnd
            lastFollowingsLoadMs = now
            rebuildFollowedUsers()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isFollowingsLoading = false
            _authorSearchLoading.value = false
            if (completeFollowingsLoadRequested && !followingsFullyLoaded) {
                completeFollowingsLoadRequested = false
                launchAccount {
                    loadAllFollowings(force = true, pageLimit = null)
                }
            }
        }
    }

    private fun requestFollowingsRefreshIfStale() {
        val now = System.currentTimeMillis()
        if (!shouldReloadFollowings(nowMs = now, lastLoadMs = lastFollowingsLoadMs)) return
        launchAccount {
            loadAllFollowings(force = true)
        }
    }

    fun prepareAuthorSearch() {
        requestCompleteFollowingsLoad()
    }

    private fun requestCompleteFollowingsLoad() {
        if (followingsFullyLoaded) return
        if (isFollowingsLoading) {
            completeFollowingsLoadRequested = true
            return
        }
        completeFollowingsLoadRequested = false
        launchAccount {
            loadAllFollowings(force = true, pageLimit = null)
        }
    }

    private fun scheduleStartupFollowingsHydration(startupPlan: DynamicStartupLoadPlan) {
        if (startupPlan.loadFollowingsImmediately || startupFollowingsHydrationScheduled) return
        startupFollowingsHydrationScheduled = true
        launchAccount {
            delay(startupPlan.followingsHydrationDelayMs.coerceAtLeast(0L))
            pageActivity.first { it }
            loadAllFollowings(
                force = false,
                pageLimit = startupPlan.initialFollowingsPageLimit
            )
        }
    }

    /**
     * 从动态列表提取用户（委托给纯策略，过滤合集/未关注/重复虚拟账号）
     */
    private fun extractUsersFromDynamics(items: List<DynamicItem>): List<SidebarUser> {
        return extractUsersFromDynamicItems(items)
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
        val mergedUsers = resolveMergedFollowedUsers(
            followingUsers = extractUsersFromFollowings(cachedFollowings),
            liveUsers = extractUsersFromLive(cachedLiveRooms),
            dynamicUsers = extractUsersFromDynamics(_uiState.value.timelinePage("all").items)
        )
        _followedUsers.value = applyUserPreferences(mergedUsers)
    }

    private fun applyAuthorUnfollow(authorMid: Long) {
        if (authorMid <= 0L) return
        cachedFollowings = cachedFollowings.filterNot { it.mid == authorMid }
        cachedLiveRooms = cachedLiveRooms.filterNot { it.uid == authorMid }
        _uiState.value = resolveDynamicStateAfterAuthorUnfollow(
            currentState = _uiState.value,
            authorMid = authorMid
        )
        if (_selectedUserId.value == authorMid) {
            selectUser(null)
        }
        _followedUsers.value = resolveFollowedUsersAfterAuthorUnfollow(
            users = _followedUsers.value,
            authorMid = authorMid
        )
        rebuildFollowedUsers()
        saveDynamicCache(_uiState.value.timelinePage("all").items)
    }
    
    /**
     *  [新增] 从关注列表转换为侧边栏用户
     */
    private fun extractUsersFromFollowings(followings: List<FollowingUser>): List<SidebarUser> {
        return followings.map { user ->
            SidebarUser(
                uid = user.mid,
                name = user.uname,
                face = user.face,
                isLive = false,
                lastActiveTs = 0  // 关注列表没有活跃时间，排序优先级最低
            )
        }
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
     *  [修改] 选择用户过滤动态 - 改为加载该用户的专属动态
     */
    fun selectUser(uid: Long?) {
        val previousUid = _selectedUserId.value
        _selectedUserId.value = uid
        
        if (uid != null) {
            //  [新增] 点击 UP 后清除其未读红点
            clearUplistUpdate(uid)
            val shouldReload = shouldReloadSelectedUserDynamics(
                previousUid = previousUid,
                nextUid = uid,
                currentItems = _uiState.value.userItems,
                userError = _uiState.value.userError,
            )
            if (previousUid != uid) {
                _uiState.value = _uiState.value.copy(
                    userItems = emptyList<DynamicItem>().toImmutableList(),
                    hasUserMore = true,
                    userIsLoading = false,
                    userError = null
                )
            }
            if (!shouldReload) return

            // 切换用户时废弃旧请求；同一用户失败重试时保留当前可见内容。
            userSelectionJob?.cancel()
            userDynamicsJob?.cancel()
            activeUserDynamicsRequestToken += 1L
            userPagingSession = null
            val requestToken = activeUserDynamicsRequestToken
            _uiState.value = _uiState.value.copy(userIsLoading = true)
            userSelectionJob = launchAccount {
                delay(USER_SELECTION_DEBOUNCE_MS)
                loadUserDynamics(uid = uid, refresh = true, requestToken = requestToken)
            }
        } else {
            // 清空用户动态
            userSelectionJob?.cancel()
            userDynamicsJob?.cancel()
            activeUserDynamicsRequestToken += 1L
            userPagingSession = null
            _uiState.value = _uiState.value.copy(
                userItems = emptyList<DynamicItem>().toImmutableList(),
                hasUserMore = true,
                userIsLoading = false,
                userError = null
            )
            // 可能从作者筛选直接进入了尚未加载的投稿/专栏页；清除筛选时补齐该分类时间线。
            val requestType = resolveDynamicFeedRequestType(_selectedTab.value)
            val page = _uiState.value.timelinePage(requestType)
            if (page.items.isEmpty() && page.hasMore && !page.isLoading && page.error == null) {
                loadDynamicFeed(refresh = true, requestType = requestType)
            }
        }
    }
    
    /**
     *  [新增] 加载指定用户的动态
     */
    private suspend fun loadUserDynamics(
        uid: Long,
        refresh: Boolean = false,
        requestToken: Long = activeUserDynamicsRequestToken
    ) {
        if (!shouldApplyUserDynamicsResult(
                selectedUid = _selectedUserId.value,
                requestUid = uid,
                activeRequestToken = activeUserDynamicsRequestToken,
                requestToken = requestToken
            )
        ) return
        if (!refresh && userPagingSession != null) {
            userPagingSession?.append()
            return
        }
        // 延迟选择任务只负责启动收集，刷新不会等待整个分页流结束。
        userDynamicsJob?.cancel()
        val identity = activeAccountIdentity
        val requestItems = _uiState.value.userItems
        var appliedPages = 0
        lateinit var session: ForwardPagingSession<String, DynamicPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                dynamicPagingSource { offset, _ ->
                    DynamicRepository.loadDynamicPage(hostMid = uid, offset = offset).getOrThrow()
                }
            },
            onChanged = { pages, states ->
                if (userPagingSession === session && identity == TokenManager.accountIdentity.value &&
                    shouldApplyUserDynamicsResult(_selectedUserId.value, uid, activeUserDynamicsRequestToken, requestToken)
                ) {
                    val failure = (states?.refresh as? LoadState.Error)?.error
                        ?: (states?.append as? LoadState.Error)?.error
                    _uiState.update { current ->
                        var items = current.userItems
                        if (states?.refresh is LoadState.NotLoading && pages.size > appliedPages) {
                            val incoming = reconcileDynamicPageItems(
                                pages.drop(appliedPages).flatMap { it.page.items },
                                requestItems, current.userItems, removedDynamicIds
                            )
                            items = (if (appliedPages == 0) incoming else appendDistinctByKey(
                                existing = items, incoming = incoming, keySelector = ::dynamicFeedItemKey
                            )).toImmutableList()
                        }
                        current.copy(
                            userItems = items,
                            userIsLoading = states?.refresh is LoadState.Loading || states?.append is LoadState.Loading,
                            userError = failure?.message ?: failure?.let { "加载失败" },
                            hasUserMore = states?.append?.endOfPaginationReached != true
                        )
                    }
                    if (states?.refresh is LoadState.NotLoading) appliedPages = pages.size
                }
            }
        )
        userPagingSession = session
        _uiState.update { it.copy(userIsLoading = true, userError = null) }
        userDynamicsJob = launchAccount { session.run() }
        session.awaitInitialLoad()
    }

    fun loadMoreUserDynamics() {
        if (_selectedUserId.value == null || !_uiState.value.hasUserMore ||
            _uiState.value.userIsLoading || _uiState.value.userError != null) return
        userPagingSession?.append()
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
                selectUser(null)
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
                selectUser(null)
            }
        }
        rebuildFollowedUsers()
    }

    /**
     *  [新增] 切换显示模式并保存
     */
    fun setDisplayMode(mode: DynamicDisplayMode) {
        _displayMode.value = mode
        userPrefs.edit()
            .putString(KEY_DISPLAY_MODE, mode.name)
            .apply()
    }

    fun setSelectedTab(tab: Int) {
        val resolvedTab = resolveDynamicSelectedTab(
            savedTab = tab,
            tabCount = DYNAMIC_TOP_TAB_COUNT
        )
        if (shouldShowDynamicUpPanel(resolvedTab)) {
            requestCompleteFollowingsLoad()
        }
        if (_selectedTab.value == resolvedTab) return
        _selectedTab.value = resolvedTab
        val requestType = resolveDynamicFeedRequestType(resolvedTab)
        _uiState.value = _uiState.value.selectTimelinePage(requestType)
        userPrefs.edit()
            .putInt(KEY_SELECTED_TAB, resolvedTab)
            .apply()
        // 作者选择在全部、投稿和专栏之间保留，番剧只读取公共分类时间线。
        if (!shouldUseSelectedUserDynamicFeed(resolvedTab, _selectedUserId.value)) {
            loadDynamicFeed(refresh = true, requestType = requestType)
        }
    }
    
    /**
     * 加载动态列表
     */
    fun loadDynamicFeed(
        refresh: Boolean = false,
        requestType: String = resolveDynamicFeedRequestType(_selectedTab.value)
    ) {
        val page = _uiState.value.timelinePage(requestType)
        if (!refresh && (page.isLoading || _isRefreshing.value || isTimelineLoading(requestType))) return
        launchAccount {
            loadDynamicFeedInternal(
                refresh = refresh,
                showLoading = refresh && page.items.isEmpty(),
                requestType = requestType
            )
        }
    }

    private suspend fun loadDynamicFeedInternal(
        refresh: Boolean,
        showLoading: Boolean = false,
        requestType: String
    ) {
        if (!refresh && timelinePagingSessions[requestType] != null) {
            timelinePagingSessions[requestType]?.append()
            return
        }
        timelinePagingJobs.remove(requestType)?.cancel()
        val requestToken = startTimelineRequest(requestType)
        val identity = activeAccountIdentity
        val requestPage = _uiState.value.timelinePage(requestType)
        val previousPages = timelinePagingPages[requestType].orEmpty()
        val baseline = previousPages.firstOrNull()?.page?.updateBaseline
            .orEmpty().takeIf { incrementalTimelineRefreshEnabled }.orEmpty()
        val previousTail = previousPages.lastOrNull()?.page
        val incrementalEnabled = incrementalTimelineRefreshEnabled
        var appliedPages = 0
        lateinit var session: ForwardPagingSession<String, DynamicPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                dynamicPagingSource(
                    updateBaseline = baseline,
                    previousTail = previousTail,
                    preserveTail = { result ->
                        resolveDynamicTimelinePageAfterSuccess(
                            requestPage, result.items, isRefresh = true,
                            incrementalRefreshEnabled = incrementalEnabled, hasMore = result.hasMore
                        ).incrementalPrependedCount > 0
                    },
                    loadPage = { offset, updateBaseline ->
                        check(!TokenManager.sessDataCache.isNullOrEmpty()) { "未登录，请先登录" }
                        DynamicRepository.loadDynamicPage(
                            type = requestType, offset = offset, updateBaseline = updateBaseline
                        ).getOrThrow()
                    }
                )
            },
            onChanged = { pages, states ->
                if (timelinePagingSessions[requestType] === session && identity == TokenManager.accountIdentity.value &&
                    shouldApplyTimelineFeedResult(activeTimelineRequestTokens, requestType, requestToken)
                ) {
                    if (states != null) timelinePagingStates[requestType] = states
                    val refreshError = (states?.refresh as? LoadState.Error)?.error
                    val appendError = (states?.append as? LoadState.Error)?.error
                    val hasNewPages = states?.refresh is LoadState.NotLoading && pages.size > appliedPages
                    _uiState.update { current ->
                        updateDynamicTimelinePage(current, requestType) { page ->
                            var next = page
                            if (hasNewPages) {
                                // 新一页动态到手就预热其中的视频卡判定，点击进详情时首帧即正确布局。
                                com.android.purebilibili.data.repository.VideoRepository
                                    .prefetchVerticalFlags(
                                        pages.drop(appliedPages)
                                            .flatMap { it.page.items }
                                            .mapNotNull { it.modules.module_dynamic?.major?.archive?.bvid }
                                    )
                                pages.drop(appliedPages).forEachIndexed { index, chunk ->
                                    next = resolveDynamicTimelinePageAfterSuccess(
                                        next, reconcileDynamicPageItems(
                                            chunk.page.items, requestPage.items, next.items, removedDynamicIds
                                        ),
                                        isRefresh = appliedPages == 0 && index == 0,
                                        incrementalRefreshEnabled = incrementalEnabled,
                                        hasMore = chunk.page.hasMore
                                    )
                                }
                            }
                            val failure = refreshError ?: appendError
                            if (failure != null) {
                                resolveDynamicTimelinePageAfterFailure(
                                    next, failure.message ?: "加载失败", refresh = refreshError != null
                                )
                            } else next.copy(
                                isLoading = (states?.refresh is LoadState.Loading && showLoading) ||
                                    states?.append is LoadState.Loading,
                                error = null,
                                errorSource = DynamicFeedErrorSource.NONE,
                                hasMore = states?.append?.endOfPaginationReached != true
                            )
                        }
                    }
                    if (states?.refresh is LoadState.NotLoading) {
                        appliedPages = pages.size
                        timelinePagingPages[requestType] = pages
                    }
                    if (hasNewPages && requestType == "all") {
                        saveDynamicCache(_uiState.value.timelinePage(requestType).items)
                        rebuildFollowedUsers()
                    }
                }
            }
        )
        timelinePagingSessions[requestType] = session
        timelinePagingStates.remove(requestType)
        _uiState.update { current ->
            updateDynamicTimelinePage(current, requestType) {
                resolveDynamicTimelinePageForLoadStart(it, refresh = true, showLoading = showLoading)
            }
        }
        timelinePagingJobs[requestType] = launchAccount { session.run() }
        session.awaitInitialLoad()
    }

    fun refresh(selectedTab: Int = _selectedTab.value) {
        val requestType = resolveDynamicFeedRequestType(selectedTab)
        val refreshUserId = resolveDynamicRefreshUserId(
            selectedTab = selectedTab,
            selectedUserId = _selectedUserId.value
        )
        val activeSourceLocked = if (refreshUserId != null) {
            isUserLoadingLocked
        } else {
            isTimelineLoading(requestType)
        }
        if (!shouldStartDynamicRefresh(_isRefreshing.value, activeSourceLocked)) return
        launchAccount {
            refreshData(
                showRefreshIndicator = true,
                selectedTab = selectedTab
            )
            loadUplistUpdates()
        }
    }

    //  [新增] 拉取关注 UP 列表未读标记（红点数据源，尽力而为）
    fun loadUplistUpdates() {
        launchAccount {
            pageActivity.first { it }
            try {
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) return@launchAccount
                val response = NetworkModule.dynamicApi.getDynamicUplist()
                if (response.code != 0) return@launchAccount
                val mids = response.data?.items
                    ?.filter { it.has_update == 1 }
                    ?.mapNotNull { it.user_profile?.info?.uid }
                    ?.filter { it > 0L }
                    .orEmpty()
                _uiState.value = _uiState.value.copy(
                    uplistUpdateMids = mids.toImmutableSet()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 红点为尽力而为，失败静默
            }
        }
    }

    //  [新增] 点击 UP 后清除该用户红点
    fun clearUplistUpdate(mid: Long) {
        if (mid <= 0L) return
        _uiState.value = _uiState.value.copy(
            uplistUpdateMids = (_uiState.value.uplistUpdateMids - mid).toImmutableSet()
        )
    }
    
    fun loadMore(selectedTab: Int = _selectedTab.value) {
        val requestType = resolveDynamicFeedRequestType(selectedTab)
        val page = _uiState.value.timelinePage(requestType)
        if (!page.hasMore || page.isLoading || page.error != null ||
            _isRefreshing.value || isTimelineLoading(requestType)) return
        loadDynamicFeed(refresh = false, requestType = requestType)
    }

    fun retryDynamicFeed(selectedTab: Int = _selectedTab.value) {
        val uid = resolveDynamicRefreshUserId(selectedTab, _selectedUserId.value)
        if (uid != null) {
            if (isUserLoadingLocked || _isRefreshing.value) return
            val session = userPagingSession
            if (session != null) session.retry() else launchAccount {
                loadUserDynamics(uid, refresh = true)
            }
            return
        }
        val requestType = resolveDynamicFeedRequestType(selectedTab)
        if (isTimelineLoading(requestType) || _isRefreshing.value) return
        val session = timelinePagingSessions[requestType]
        if (session != null) session.retry() else loadDynamicFeed(refresh = true, requestType = requestType)
    }

    private fun dynamicItemKey(item: DynamicItem): String {
        return dynamicFeedItemKey(item)
    }

    private fun startTimelineRequest(requestType: String): Long {
        val nextToken = (activeTimelineRequestTokens[requestType] ?: 0L) + 1L
        activeTimelineRequestTokens[requestType] = nextToken
        return nextToken
    }

    private fun isTimelineLoading(requestType: String): Boolean {
        val states = timelinePagingStates[requestType] ?: return false
        return states.refresh is LoadState.Loading || states.append is LoadState.Loading
    }

    override fun onCleared() {
        accountScope.cancel()
        cacheSaveJob?.cancel()
        userDynamicsJob?.cancel()
        userSelectionJob?.cancel()
        super.onCleared()
    }
    
    // ====================  动态评论/点赞/转发功能 ====================
    
    // 当前选中的动态（用于评论弹窗）
    private val _selectedDynamic = MutableStateFlow<DynamicItem?>(null)
    private val _selectedCommentTarget = MutableStateFlow<DynamicCommentTarget?>(null)
    val selectedDynamicId: StateFlow<String?> = _selectedDynamic
        .map { it?.id_str }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null
        )
    
    // 评论区单一状态源：所有字段一次发射，避免「评论已到但 loading 还没关」这类中间帧。
    private val _commentUiState = MutableStateFlow(DynamicCommentUiState())
    internal val commentUiState: StateFlow<DynamicCommentUiState> = _commentUiState.asStateFlow()

    private val _dynamicCommentSortMode = MutableStateFlow(CommentSortMode.HOT)
    val dynamicCommentSortMode: StateFlow<CommentSortMode> = _dynamicCommentSortMode.asStateFlow()

    private var subReplyLoadJob: Job? = null
    private var subCommentPagingSession: CommentPagingSession? = null
    private val removedCommentIds = mutableSetOf<Long>()
    private var sentCommentReplies = SentCommentReplyStore()
    private var sentCommentDynamicId: String? = null
    private var sentCommentTarget: DynamicCommentTarget? = null
    private var commentSubjectSession = 0L
    private var remoteSubReplies: List<ReplyItem> = emptyList()
    private var remoteMainReplies: List<ReplyItem> = emptyList()

    private fun selectCommentSubject(dynamicId: String, target: DynamicCommentTarget? = null) {
        if (sentCommentDynamicId != dynamicId ||
            (target != null && sentCommentTarget != null && sentCommentTarget != target)
        ) {
            if (sentCommentDynamicId == dynamicId) commentCache.clear()
            if (target == null) sentCommentReplies = SentCommentReplyStore()
            removedCommentIds.clear()
            remoteSubReplies = emptyList()
            remoteMainReplies = emptyList()
            sentCommentTarget = null
            commentSubjectSession++
            subReplyLoadRequestId++
            subCommentPagingSession = null
            subReplyLoadJob?.cancel()
            _subReplyState.value = SubReplyUiState()
        }
        sentCommentDynamicId = dynamicId
        if (target != null) {
            sentCommentTarget = target
            sentCommentReplies = SentCommentReplyStores.forSubject(target.oid, target.type)
        }
    }

    private fun applySentComment(reply: ReplyItem) {
        if (reply.rpid in removedCommentIds || reply.root in removedCommentIds ||
            sentCommentReplies.isReplyRemoved(reply.rpid) || sentCommentReplies.isRootRemoved(reply.rpid) ||
            sentCommentReplies.isRootRemoved(reply.root)
        ) return
        val comments = _commentUiState.value.comments
        val activeRoot = _subReplyState.value.rootReply?.takeIf { root ->
            root.rpid == reply.root && comments.none { it.rpid == root.rpid }
        }
        val isNew = sentCommentReplies.record(reply, if (activeRoot == null) comments else comments + activeRoot)
        updateCommentUiState { it.copy(
            comments = sentCommentReplies.merge(it.comments),
            totalCount = it.totalCount + if (isNew) 1 else 0,
        ) }
        if (isNew) {
            _subReplyState.update { it.withSentSubReply(reply) }
        }
        _subReplyState.update { state ->
            state.copy(rootReply = state.rootReply?.let { root ->
                sentCommentReplies.merge(listOf(root)).find { it.rpid == root.rpid }
            })
        }
        cacheCurrentComments(_commentUiState.value.comments)
    }
    private var subReplyLoadRequestId = 0L
    private var activeSubReplyRootId = 0L
    private val _subReplyState = MutableStateFlow(SubReplyUiState())
    val subReplyState: StateFlow<SubReplyUiState> = _subReplyState.asStateFlow()

    private val _commentReplyTarget = MutableStateFlow<DynamicCommentComposerTarget?>(null)
    internal val commentReplyTarget: StateFlow<DynamicCommentComposerTarget?> = _commentReplyTarget.asStateFlow()

    private val _commentComposerVisible = MutableStateFlow(false)
    internal val commentComposerVisible: StateFlow<Boolean> = _commentComposerVisible.asStateFlow()

    private val _commentComposerDrafts = MutableStateFlow(CommentComposerDraftState())
    internal val commentComposerDrafts: StateFlow<CommentComposerDraftState> =
        _commentComposerDrafts.asStateFlow()

    private val _isSendingComment = MutableStateFlow(false)
    internal val isSendingComment: StateFlow<Boolean> = _isSendingComment.asStateFlow()

    private val _commentMentionSearchState = MutableStateFlow(CommentMentionSearchUiState())
    val commentMentionSearchState: StateFlow<CommentMentionSearchUiState> =
        _commentMentionSearchState.asStateFlow()

    private var commentMentionSearchJob: Job? = null

    private fun resetCommentComposerState() {
        _commentComposerVisible.value = false
        _commentReplyTarget.value = null
        // 保留自增后的 sessionId，避免在途发送的成功回调误关掉重置后新开的输入会话。
        _commentComposerDrafts.value = CommentComposerDraftState(
            sessionId = _commentComposerDrafts.value.sessionId
        )
        clearCommentMentionSearch()
    }

    // 进程内首页评论缓存：重进同一条动态先秒出旧内容，再后台静默刷新。
    private val commentCache = DynamicCommentCache()

    private var commentGrpcNextOffset: String? = null
    private var commentContinuation: CommentMainListContinuation? = null
    private var commentLoadJob: Job? = null
    private var mainCommentPagingJob: Job? = null
    private var mainCommentPagingSession: CommentPagingSession? = null
    private var commentLoadRequestId = 0L

    private fun updateCommentUiState(
        transform: (DynamicCommentUiState) -> DynamicCommentUiState
    ) {
        _commentUiState.update(transform)
    }

    /**
     * 把当前评论列表同步进内存缓存，让「返回再进」不必重新拉取。
     * 只有在已经确定评论目标（oid/type）时才写，避免把不完整状态缓存下来。
     */
    private fun cacheCurrentComments(
        replies: List<ReplyItem>,
        sortModeApiMode: Int = _dynamicCommentSortMode.value.apiMode,
    ) {
        val dynamicId = _selectedDynamic.value?.id_str ?: return
        val target = _selectedCommentTarget.value ?: return
        if (replies.isEmpty()) return
        commentCache.put(
            dynamicId = dynamicId,
            sortModeApiMode = sortModeApiMode,
            snapshot = DynamicCommentCacheSnapshot(
                replies = replies,
                totalCount = _commentUiState.value.totalCount,
                nextPage = _commentUiState.value.nextPage,
                isEnd = !_commentUiState.value.hasMore,
                grpcNextOffset = commentGrpcNextOffset,
                mainListContinuation = commentContinuation,
                target = target,
                cachedAtMs = System.currentTimeMillis(),
                remoteReplies = remoteMainReplies,
            ),
        )
    }

    /** 评论列表的所有变更都走这里，保证状态与缓存一起更新。 */
    private fun updateCurrentComments(
        transform: (List<ReplyItem>) -> List<ReplyItem>
    ) {
        var updated: List<ReplyItem> = emptyList()
        updateCommentUiState { state ->
            updated = transform(state.comments)
            state.copy(comments = updated)
        }
        cacheCurrentComments(updated)
    }
    
    // 点赞状态缓存 (dynamicId -> isLiked)
    private val _likedDynamics = MutableStateFlow<Set<String>>(emptySet())
    val likedDynamics: StateFlow<Set<String>> = _likedDynamics.asStateFlow()
    private val _likeOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val likeOverrides: StateFlow<Map<String, Boolean>> = _likeOverrides.asStateFlow()
    private var likeRequestGate = DynamicLikeRequestGate()
    
    /**
     *  [修复] 根据动态ID获取动态对象 - 同时搜索 items 和 userItems
     */
    private fun findDynamicById(dynamicId: String): DynamicItem? {
        _selectedDynamic.value?.takeIf { it.id_str == dynamicId }?.let { return it }
        val timelineState = _uiState.value
        (timelineState.timelinePages.keys + timelineState.timelineRequestType).forEach { requestType ->
            timelineState.timelinePage(requestType).items
                .find { it.id_str == dynamicId }
                ?.let { return it }
        }
        // 再在用户专属动态中搜索
        return _uiState.value.userItems.find { it.id_str == dynamicId }
    }
    
    /**
     *  打开评论弹窗
     */
    fun openCommentSheet(dynamicId: String) {
        selectCommentSubject(dynamicId)
        commentSubjectSession++
        resetCommentComposerState()
        val item = findDynamicById(dynamicId)
        _selectedDynamic.value = item
        if (item != null) {
            loadCommentsForDynamic(item)
        }
    }

    fun openCommentSheet(
        item: DynamicItem,
        rootReplyId: Long = 0L,
        targetReplyId: Long = 0L
    ) {
        selectCommentSubject(item.id_str)
        commentSubjectSession++
        resetCommentComposerState()
        _selectedDynamic.value = item
        loadCommentsForDynamic(
            item = item,
            routedRootReplyId = rootReplyId,
            routedTargetReplyId = targetReplyId
        )
    }
    
    fun refreshComments() {
        val state = _commentUiState.value
        if (state.isRefreshing || (state.isLoading && state.comments.isEmpty())) return
        val item = _selectedDynamic.value ?: return
        updateCommentUiState { it.copy(isRefreshing = true, refreshError = null) }
        loadCommentsForDynamic(item = item, isRefresh = true)
    }


    /**
     *  关闭评论弹窗
     */
    fun closeCommentSheet() {
        commentSubjectSession++
        sentCommentReplies = SentCommentReplyStore()
        remoteSubReplies = emptyList()
        remoteMainReplies = emptyList()
        commentLoadRequestId++
        mainCommentPagingSession = null
        mainCommentPagingJob?.cancel()
        commentLoadJob?.cancel()
        commentLoadJob = null
        _selectedDynamic.value = null
        _selectedCommentTarget.value = null
        subReplyLoadRequestId++
        subCommentPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.value = SubReplyUiState()
        resetCommentComposerState()
        commentGrpcNextOffset = null
        commentContinuation = null
        _dynamicCommentSortMode.value = CommentSortMode.HOT
        // 一次发射清空，避免评论 / 计数 / loading 分多次生效留下中间帧。
        // 刻意保留 commentCache：返回再进同一条动态仍能秒出旧评论。
        _commentUiState.value = DynamicCommentUiState()
    }

    fun setDynamicCommentSortMode(mode: CommentSortMode) {
        if (mode != CommentSortMode.HOT && mode != CommentSortMode.NEWEST) return
        if (_dynamicCommentSortMode.value == mode) return
        _dynamicCommentSortMode.value = mode
        val item = _selectedDynamic.value ?: return
        updateCommentUiState { it.copy(comments = emptyList(), isLoadingMore = false, hasMore = false, nextPage = 1) }
        commentGrpcNextOffset = null
        commentContinuation = null
        loadCommentsForDynamic(item)
    }
    
    /**
     *  加载动态评论 (使用正确的 oid 和 type)
     */
    private fun loadCommentsForDynamic(
        item: DynamicItem,
        routedRootReplyId: Long = 0L,
        routedTargetReplyId: Long = 0L,
        isRefresh: Boolean = false,
    ) {
        if (_commentUiState.value.dynamicId != item.id_str) {
            subReplyLoadRequestId++
            subCommentPagingSession = null
            subReplyLoadJob?.cancel()
            _subReplyState.value = SubReplyUiState()
        }
        val requestId = ++commentLoadRequestId
        val routeIntentSession = subReplyLoadRequestId
        mainCommentPagingSession = null
        mainCommentPagingJob?.cancel()
        commentLoadJob?.cancel()
        commentLoadJob = launchAccount {
            val routeIntentActive = routeIntentSession == subReplyLoadRequestId
            val sortMode = _dynamicCommentSortMode.value
            val fallbackCount = item.modules.module_stat?.comment?.count ?: 0
            val dynamicId = item.id_str
            // 命中缓存先秒出旧评论，再后台静默刷新（stale-while-revalidate）。
            val cached = if (isRefresh) null else commentCache.get(dynamicId, sortMode.apiMode)

            if (cached != null) {
                selectCommentSubject(dynamicId, cached.target)
                remoteMainReplies = cached.remoteReplies
                _selectedCommentTarget.value = cached.target
                commentGrpcNextOffset = cached.grpcNextOffset
                commentContinuation = cached.mainListContinuation
                updateCommentUiState {
                    it.copy(
                        comments = sentCommentReplies.merge(cached.replies).filterNot { it.rpid in removedCommentIds },
                        hasMore = !cached.isEnd,
                        nextPage = cached.nextPage,
                        totalCount = maxOf(cached.totalCount, fallbackCount),
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        refreshError = null,
                        dynamicId = dynamicId,
                    )
                }
            } else {
                // 一次发射同时完成「切到新动态」和「进入加载态」。
                // 以前只重置分页、不清空 comments，切到另一条动态时会先闪一下上一条的评论。
                updateCommentUiState {
                    it.copy(
                        comments = if (isRefresh) it.comments else emptyList(),
                        hasMore = false,
                        nextPage = 1,
                        totalCount = if (isRefresh) it.totalCount else fallbackCount,
                        isLoading = !isRefresh,
                        isRefreshing = isRefresh,
                        isLoadingMore = false,
                        refreshError = null,
                        dynamicId = dynamicId,
                    )
                }
                if (!isRefresh) {
                    _selectedCommentTarget.value = sentCommentTarget.takeIf { sentCommentDynamicId == dynamicId }
                }
                commentGrpcNextOffset = null
                commentContinuation = null
            }
            // Cache binding may advance the thread session synchronously; only a later
            // user close or replacement cancels the unresolved route.
            val resolvedRouteSession = subReplyLoadRequestId

            var firstFailureMessage: String? = null
            try {
                var effectiveItem = item
                var targets = resolveDynamicCommentTargets(effectiveItem)
                if (targets.isEmpty() && effectiveItem.id_str.isNotBlank()) {
                    com.android.purebilibili.core.util.Logger.d(
                        "DynamicVM",
                        "未获取到评论参数，对齐 PiliPlus 动态详情拉取 basic 补全: dynamicId=${effectiveItem.id_str}"
                    )
                    DynamicRepository.getDynamicDetail(effectiveItem.id_str).getOrNull()?.let { fullDetail ->
                        if (requestId != commentLoadRequestId) return@launchAccount
                        if (_selectedDynamic.value?.id_str != item.id_str) return@launchAccount
                        effectiveItem = fullDetail
                        _selectedDynamic.value = fullDetail
                        targets = resolveDynamicCommentTargets(fullDetail)
                    }
                }
                if (targets.isEmpty()) {
                    com.android.purebilibili.core.util.Logger.e("DynamicVM", "无法获取评论参数: type=${effectiveItem.type}")
                    if (isRefresh && requestId == commentLoadRequestId &&
                        _selectedDynamic.value?.id_str == item.id_str
                    ) {
                        updateCommentUiState { it.copy(refreshError = "无法获取评论参数") }
                    }
                    return@launchAccount
                }

                // A thread read depends on the resolved subject, not on the main list succeeding.
                // Keep this routed read and its main list on the same subject; probing another
                // candidate must not replace the thread's identity after it has already opened.
                val routedTarget = targets.firstOrNull()?.takeIf {
                    routedRootReplyId > 0L && routeIntentActive && resolvedRouteSession == subReplyLoadRequestId &&
                        requestId == commentLoadRequestId && _selectedDynamic.value?.id_str == dynamicId &&
                        activeAccountIdentity == TokenManager.accountIdentity.value
                }
                if (routedTarget != null) {
                    selectCommentSubject(dynamicId, routedTarget)
                    _selectedCommentTarget.value = routedTarget
                    openSubReplyFromRoute(routedRootReplyId, routedTargetReplyId)
                }

                val candidateResults = loadDynamicCommentCandidates(
                    targets = routedTarget?.let { listOf(it) } ?: targets,
                    dynamicId = effectiveItem.id_str,
                    dynamicType = effectiveItem.type,
                    sortMode = sortMode,
                    fallbackCount = fallbackCount,
                )
                val attempts = candidateResults.mapNotNull { it.attempt }
                firstFailureMessage = candidateResults.firstNotNullOfOrNull { it.failureMessage }

                if (requestId != commentLoadRequestId) return@launchAccount
                if (_selectedDynamic.value?.id_str != item.id_str) return@launchAccount
                val selected = selectPreferredDynamicCommentAttempt(
                    attempts = attempts,
                    expectedCount = fallbackCount
                )
                if (selected != null) {
                    if (_dynamicCommentSortMode.value != sortMode) return@launchAccount
                    applyLoadedDynamicComments(
                        dynamicId = dynamicId,
                        sortMode = sortMode,
                        attempt = selected,
                    )
                } else if (isRefresh) {
                    updateCommentUiState {
                        it.copy(refreshError = firstFailureMessage ?: "评论刷新失败")
                    }
                } else if (cached == null) {
                    // 缓存命中的静默刷新失败时保留旧内容，不要退化成空列表。
                    updateCommentUiState {
                        it.copy(
                            comments = emptyList(),
                            hasMore = false,
                            nextPage = 1,
                            totalCount = fallbackCount,
                            dynamicId = dynamicId,
                            refreshError = firstFailureMessage ?: "评论加载失败，请重试",
                        )
                    }
                    commentGrpcNextOffset = null
                    commentContinuation = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("DynamicVM", "加载评论异常: ${e.message}")
                e.printStackTrace()
                if (isRefresh && requestId == commentLoadRequestId &&
                    _selectedDynamic.value?.id_str == item.id_str
                ) {
                    updateCommentUiState { it.copy(refreshError = e.message ?: "评论刷新失败") }
                }
            } finally {
                if (requestId == commentLoadRequestId) {
                    updateCommentUiState { it.copy(isLoading = false, isRefreshing = false) }
                }
            }
        }
    }

    private data class DynamicCommentCandidateResult(
        val attempt: DynamicCommentLoadAttempt?,
        val failureMessage: String? = null,
    )

    /**
     * 并行拉取所有候选目标的评论首页。
     *
     * 两点关键改动：
     * 1. 候选之间并行，不再让 N 个候选串行排队；
     * 2. 单个候选内部，计数请求与列表请求并发，计数不再串行占用关键路径耗时。
     *
     * 首选目标（index 0）先返回且带回了可用评论时立即采用并取消其余候选——这是真正的
     * 「先到先用」，同时避免候选陆续返回造成二次内容替换。首选目标不可用时才回退到
     * [selectPreferredDynamicCommentAttempt] 的全量择优。
     */
    private suspend fun loadDynamicCommentCandidates(
        targets: List<DynamicCommentTarget>,
        dynamicId: String,
        dynamicType: String,
        sortMode: CommentSortMode,
        fallbackCount: Int,
    ): List<DynamicCommentCandidateResult> = coroutineScope {
        val jobs = targets.mapIndexed { index, target ->
            async {
                loadDynamicCommentCandidate(
                    index = index,
                    target = target,
                    dynamicId = dynamicId,
                    dynamicType = dynamicType,
                    sortMode = sortMode,
                    fallbackCount = fallbackCount,
                )
            }
        }
        val collected = mutableListOf<DynamicCommentCandidateResult>()
        val primary = jobs.firstOrNull()
        if (primary != null) {
            val first = primary.await()
            if (first != null) {
                if (first.attempt?.replies?.isNotEmpty() == true) {
                    jobs.drop(1).forEach { it.cancel() }
                    return@coroutineScope listOf(first)
                }
                collected += first
            }
        }
        collected += jobs.drop(1).awaitAll()
        collected
    }

    private suspend fun loadDynamicCommentCandidate(
        index: Int,
        target: DynamicCommentTarget,
        dynamicId: String,
        dynamicType: String,
        sortMode: CommentSortMode,
        fallbackCount: Int,
    ): DynamicCommentCandidateResult = coroutineScope {
        com.android.purebilibili.core.util.Logger.d(
            "DynamicVM",
            "加载动态评论候选: oid=${target.oid}, type=${target.type}, dynamicId=$dynamicId, dynamicType=$dynamicType"
        )
        // 计数与列表并发发出，关键路径耗时从「计数 + 列表」降到 max(计数, 列表)。
        val exactCountDeferred = async {
            CommentRepository.getCommentCountForSubject(
                oid = target.oid,
                type = target.type
            ).getOrNull()
        }
        val result = CommentRepository.getMobileCommentsForSubject(
            oid = target.oid,
            type = target.type,
            page = 1,
            ps = DYNAMIC_COMMENT_PAGE_SIZE,
            mode = sortMode.apiMode,
            fallbackOnMissingLocation = target.type != 11
        )
        val exactCount = exactCountDeferred.await()
        result.fold(
            onSuccess = { data ->
                val payload = resolveDynamicCommentPayload(
                    data = data,
                    // 计数接口失败时回退到动态自带的评论数，而不是把总数归零。
                    fallbackCount = exactCount ?: fallbackCount,
                    includeHotReplies = sortMode == CommentSortMode.HOT
                )
                DynamicCommentCandidateResult(
                    attempt = DynamicCommentLoadAttempt(
                        target = target,
                        replies = payload.replies,
                        totalCount = payload.totalCount,
                        candidateIndex = index,
                        nextPage = 2,
                        isEnd = data.cursor.isEnd,
                        grpcNextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() },
                        mainListContinuation = data.mainListContinuation,
                    )
                )
            },
            onFailure = { error ->
                com.android.purebilibili.core.util.Logger.w(
                    "DynamicVM",
                    "动态评论候选失败: oid=${target.oid}, type=${target.type}, count=$exactCount, error=${error.message}"
                )
                DynamicCommentCandidateResult(
                    attempt = null,
                    failureMessage = error.message
                )
            }
        )
    }

    /**
     * 应用一次成功加载的首页评论。
     *
     * 状态一次发射；如果新内容与当前内容完全一致（StateFlow 按 equals 去重），
     * 则不会产生多余重组——这正是「静默刷新」不闪的原因。
     */
    private fun applyLoadedDynamicComments(
        dynamicId: String,
        sortMode: CommentSortMode,
        attempt: DynamicCommentLoadAttempt,
    ) {
        val previousState = _commentUiState.value
        // 缓存命中后的静默刷新可能晚于用户翻页返回：这时只把首页结果并回列表，
        // 不要把已经加载的第 2、3 页覆盖掉。
        val alreadyPagedFurther = previousState.dynamicId == dynamicId &&
            _selectedCommentTarget.value == attempt.target &&
            previousState.nextPage > attempt.nextPage
        if (!alreadyPagedFurther) {
            mainCommentPagingSession = null
            mainCommentPagingJob?.cancel()
        }
        val sameSubject = sentCommentDynamicId == dynamicId && sentCommentTarget == attempt.target
        selectCommentSubject(dynamicId, attempt.target)
        val loadedReplies = if (alreadyPagedFurther) {
            (attempt.replies + remoteMainReplies).distinctBy { it.rpid }
        } else {
            attempt.replies
        }
        remoteMainReplies = loadedReplies.filterNot {
            it.rpid in removedCommentIds || sentCommentReplies.isRootRemoved(it.rpid)
        }
        val appliedReplies = sentCommentReplies.merge(remoteMainReplies)
        _selectedCommentTarget.value = attempt.target
        if (!alreadyPagedFurther) {
            commentGrpcNextOffset = attempt.grpcNextOffset
            commentContinuation = attempt.mainListContinuation
        }
        updateCommentUiState {
            it.copy(
                comments = appliedReplies,
                hasMore = if (alreadyPagedFurther) it.hasMore else !attempt.isEnd,
                nextPage = if (alreadyPagedFurther) it.nextPage else attempt.nextPage,
                totalCount = if (sameSubject) maxOf(attempt.totalCount, it.totalCount) else attempt.totalCount,
                isLoading = false,
                isRefreshing = false,
                refreshError = null,
                dynamicId = dynamicId,
            )
        }
        cacheCurrentComments(appliedReplies, sortMode.apiMode)
    }

    fun loadMoreComments() {
        val target = _selectedCommentTarget.value ?: return
        val state = _commentUiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return
        mainCommentPagingSession?.let { it.loadMore(); return }
        val sortMode = _dynamicCommentSortMode.value
        val requestId = commentLoadRequestId
        lateinit var session: CommentPagingSession
        fun currentRequest() = mainCommentPagingSession === session && shouldApplyDynamicCommentPageResult(
            activeRequestId = commentLoadRequestId, requestId = requestId,
            activeTarget = _selectedCommentTarget.value, requestTarget = target,
            activeSortMode = _dynamicCommentSortMode.value, requestSortMode = sortMode
        )
        session = CommentPagingSession(
            initial = CommentPageRequest(state.nextPage, commentContinuation), mainList = true,
            load = { request -> CommentRepository.getMobileCommentsForSubject(
                oid = target.oid, type = target.type, page = request.page, ps = DYNAMIC_COMMENT_PAGE_SIZE,
                mode = sortMode.apiMode, continuation = request.continuation, fallbackOnMissingLocation = target.type != 11
            ).getOrThrow() },
            onPage = success@ { request, data ->
                if (!currentRequest()) return@success
                val pageToLoad = request.page
                    val newReplies = data.replies.orEmpty()
                    remoteMainReplies = (remoteMainReplies + newReplies).distinctBy { it.rpid }
                        .filterNot { it.rpid in removedCommentIds || sentCommentReplies.isRootRemoved(it.rpid) }
                    val mergedReplies = sentCommentReplies.merge(remoteMainReplies)
                    val totalCount = maxOf(
                        data.getAllCount(),
                        _commentUiState.value.totalCount,
                        mergedReplies.size
                    )
                    commentGrpcNextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() }
                    commentContinuation = data.mainListContinuation
                    updateCommentUiState {
                        it.copy(
                            comments = mergedReplies, totalCount = totalCount,
                            hasMore = !data.cursor.isEnd, nextPage = pageToLoad + 1,
                        )
                    }
                    cacheCurrentComments(mergedReplies)
            },
            onState = { loading, end, failure ->
                if (currentRequest()) updateCommentUiState { it.copy(
                    isLoadingMore = loading, hasMore = !end,
                    refreshError = failure?.let { error -> error.message ?: "评论加载失败，请重试" }
                ) }
            }
        )
        mainCommentPagingSession = session
        mainCommentPagingJob = launchAccount { session.run() }
    }

    /**
     *  加载评论 (兼容旧调用方式)
     */
    fun loadComments(dynamicId: String) {
        val item = _selectedDynamic.value?.takeIf { it.id_str == dynamicId } ?: return
        loadCommentsForDynamic(item)
    }

    fun openSubReply(rootReply: ReplyItem, targetReplyId: Long = 0L) {
        val target = _selectedCommentTarget.value ?: return
        if (activeAccountIdentity != TokenManager.accountIdentity.value) return
        val root = sentCommentReplies.merge(listOf(rootReply)).find { it.rpid == rootReply.rpid } ?: return
        activeSubReplyRootId = root.rpid
        subCommentPagingSession = null
        subReplyLoadJob?.cancel()
        remoteSubReplies = emptyList()
        _subReplyState.value = SubReplyUiState(
            visible = true,
            rootReply = root,
            upMid = _selectedDynamic.value?.modules?.module_author?.mid ?: 0L,
            targetReplyId = targetReplyId.takeIf { it != rootReply.rpid } ?: 0L,
            items = sentCommentReplies.repliesFor(rootReply.rpid).toImmutableList(),
            baseItems = sentCommentReplies.repliesFor(rootReply.rpid).toImmutableList(),
            totalCount = resolveSubReplyLoadedTotalCount(
                rootReply = root,
                loadedReplyCount = maxOf(root.replies.orEmpty().size, sentCommentReplies.repliesFor(root.rpid).size),
                remoteReplyCount = 0
            ),
            isLoading = true,
            page = 1
        )
        loadSubReplies(
            oid = target.oid,
            type = target.type,
            rootId = rootReply.rpid,
            page = 1
        )
    }

    fun openSubReplyFromRoute(rootReplyId: Long, targetReplyId: Long = 0L): Boolean {
        val target = _selectedCommentTarget.value ?: return false
        if (activeAccountIdentity != TokenManager.accountIdentity.value) return false
        if (rootReplyId <= 0L || sentCommentReplies.isRootRemoved(rootReplyId)) return false

        if (openLoadedRoutedSubReply(rootReplyId, targetReplyId)) return true
        activeSubReplyRootId = rootReplyId

        subCommentPagingSession = null
        subReplyLoadJob?.cancel()
        remoteSubReplies = emptyList()
        val localItems = sentCommentReplies.repliesFor(rootReplyId).toImmutableList()
        _subReplyState.value = SubReplyUiState(
            visible = true,
            items = localItems,
            baseItems = localItems,
            upMid = _selectedDynamic.value?.modules?.module_author?.mid ?: 0L,
            isLoading = true,
            targetReplyId = targetReplyId.takeIf { it != rootReplyId } ?: 0L
        )
        loadSubReplies(target.oid, target.type, rootReplyId, page = 1, paginationOffset = null)
        return true
    }

    private fun openLoadedRoutedSubReply(rootReplyId: Long, targetReplyId: Long): Boolean {
        (resolveRoutedCommentRootReply(
            loadedReplies = _commentUiState.value.comments,
            remoteData = null,
            rootReplyId = rootReplyId
        ) ?: sentCommentReplies.rootFor(rootReplyId))?.let { rootReply ->
            openSubReply(rootReply, targetReplyId)
            return true
        }
        return false
    }

    fun closeSubReply() {
        subReplyLoadRequestId++
        activeSubReplyRootId = 0L
        subCommentPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.value = _subReplyState.value.copy(
            visible = false,
            isLoading = false,
            isRefreshing = false
        )
    }

    fun setSubReplySortMode(mode: SubReplySortMode) {
        val state = _subReplyState.value
        val target = _selectedCommentTarget.value ?: return
        val rootId = state.rootReply?.rpid ?: activeSubReplyRootId
        if (!state.visible || state.sortMode == mode || rootId <= 0L) return
        subCommentPagingSession = null
        subReplyLoadJob?.cancel()
        remoteSubReplies = emptyList()
        val localItems = sentCommentReplies.repliesFor(rootId).toImmutableList()
        _subReplyState.update { it.resetForSort(mode).copy(items = localItems, baseItems = localItems) }
        loadSubReplies(target.oid, target.type, rootId, page = 1, paginationOffset = null)
    }

    fun refreshSubReplies() {
        val state = _subReplyState.value
        val target = _selectedCommentTarget.value ?: return
        val rootId = state.rootReply?.rpid ?: activeSubReplyRootId
        if (!state.visible || state.isRefreshing ||
            rootId <= 0L
        ) return
        _subReplyState.value = state.copy(targetReplyId = 0L)
        loadSubReplies(
            oid = target.oid,
            type = target.type,
            rootId = rootId,
            page = 1,
            paginationOffset = null,
            isRefresh = true
        )
    }

    fun loadMoreSubReplies() {
        val state = _subReplyState.value
        val target = _selectedCommentTarget.value ?: return
        if (!state.visible || state.isLoading || state.isRefreshing || (state.isEnd && state.error == null)) return
        if (subCommentPagingSession != null) { subCommentPagingSession?.loadMore(); return }
        val rootReply = state.rootReply ?: return
        val request = resolveSubReplyNextRequest(state)
        _subReplyState.value = state.copy(isLoading = true, error = null)
        loadSubReplies(
            oid = target.oid,
            type = target.type,
            rootId = rootReply.rpid,
            page = request.page,
            paginationOffset = request.offset
        )
    }

    private fun loadSubReplies(
        oid: Long,
        type: Int,
        rootId: Long,
        page: Int,
        paginationOffset: String? = _subReplyState.value.grpcNextOffset,
        isRefresh: Boolean = false,
    ) {
        if (page > 1 && subCommentPagingSession != null) { subCommentPagingSession?.loadMore(); return }
        val state = _subReplyState.value
        val sortMode = state.sortMode
        val account = activeAccountIdentity
        val store = sentCommentReplies
        val seedRoot = state.rootReply
        val targetReplyId = sentCommentReplies.remoteTargetReplyId(
            rootId, state.targetReplyId
        ).takeIf { page == 1 } ?: 0L
        val requestId = ++subReplyLoadRequestId
        subCommentPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.value = if (isRefresh) {
            state.copy(isLoading = true, isRefreshing = true, error = null)
        } else {
            state.copy(isLoading = true, isRefreshing = false, error = null)
        }
        var firstPageApplied = false
        lateinit var session: CommentPagingSession
        session = CommentPagingSession(
            initial = CommentPageRequest(page = page, offset = paginationOffset), mainList = false,
            load = { request ->
                val data = CommentRepository.getMobileSortedSubCommentsForSubject(
                    oid = oid, type = type, rootId = rootId, mode = sortMode.apiMode,
                    targetReplyId = targetReplyId.takeIf { request.page == 1 } ?: 0L, paginationOffset = request.offset
                ).getOrThrow()
                check(data.root?.let { it.rpid == rootId } != false) { "回复楼层不匹配，请重试" }
                check(resolveRoutedCommentRootReply(emptyList(), data, rootId) != null ||
                    _subReplyState.value.rootReply?.rpid == rootId || seedRoot != null
                ) { "回复可能已被删除或不可见" }
                data
            },
            onPage = success@ { request, data ->
                if (subCommentPagingSession !== session) return@success
                val page = request.page
                firstPageApplied = true
                    val current = _subReplyState.value
                    val target = _selectedCommentTarget.value
                    if (requestId != subReplyLoadRequestId || !current.visible ||
                        activeSubReplyRootId != rootId || target?.oid != oid ||
                        target?.type != type || current.sortMode != sortMode ||
                        TokenManager.accountIdentity.value != account
                    ) return@success
                    if (sentCommentReplies.isRootRemoved(rootId)) {
                        closeSubReply()
                        return@success
                    }
                    val resolvedRoot = resolveRoutedCommentRootReply(emptyList(), data, rootId)
                        ?: current.rootReply ?: seedRoot
                    val root = resolvedRoot?.let { store.merge(listOf(it)).firstOrNull { reply -> reply.rpid == rootId } }
                    val newItems = data.replies.orEmpty()
                    remoteSubReplies = (if (page == 1) newItems else {
                        (remoteSubReplies + newItems).distinctBy { it.rpid }
                    }).filterNot { it.rpid in removedCommentIds || sentCommentReplies.isReplyRemoved(it.rpid) }
                    val sentReplies = sentCommentReplies.repliesFor(rootId)
                    val updatedItems = mergeSentSubReplies(remoteSubReplies, sentReplies)
                    val remoteTotalCount = resolveSubReplyRemoteTotalCount(
                        data = data,
                        rootReply = root
                    )
                    val totalCount = resolveSubReplyLoadedTotalCount(
                        rootReply = root,
                        loadedReplyCount = updatedItems.size,
                        remoteReplyCount = remoteTotalCount,
                        previousTotalCount = if (isRefresh && page == 1) 0 else current.totalCount
                    )
                    val isEnd = isSortedSubReplyPageEnd(data.cursor.isEnd, data.grpcNextOffset)
                    _subReplyState.value = resolveDynamicSubReplyStateAfterSuccess(
                        currentState = current.copy(rootReply = root),
                        remoteItems = remoteSubReplies,
                        page = page,
                        isEnd = isEnd,
                        totalCount = totalCount,
                        grpcNextOffset = data.grpcNextOffset,
                        sentReplies = sentReplies,
                    )
            },
            onState = { loading, end, failure ->
                val current = _subReplyState.value
                val target = _selectedCommentTarget.value
                if (subCommentPagingSession === session && requestId == subReplyLoadRequestId && current.visible &&
                    activeSubReplyRootId == rootId && target?.oid == oid && target?.type == type &&
                    TokenManager.accountIdentity.value == account) {
                    _subReplyState.update { it.copy(
                        isLoading = loading, isRefreshing = isRefresh && !firstPageApplied && loading, isEnd = end, baseIsEnd = end,
                        error = failure?.let { error -> error.message ?: "回复加载失败" }
                    ) }
                }
            }
        )
        subCommentPagingSession = session
        subReplyLoadJob = launchAccount { session.run() }
    }

    /**
     *  打开根评论输入弹层
     */
    fun openCommentComposer() {
        _commentReplyTarget.value = null
        _commentComposerDrafts.update { it.open() }
        clearCommentMentionSearch()
        _commentComposerVisible.value = true
    }

    /**
     *  回复指定评论：统一打开共享输入弹层，不再聚焦内联输入框。
     */
    fun startCommentReply(reply: ReplyItem) {
        _commentReplyTarget.value = resolveDynamicCommentReplyTarget(reply)
        _commentComposerDrafts.update { it.open() }
        clearCommentMentionSearch()
        _commentComposerVisible.value = true
    }

    fun dismissCommentComposer() {
        _commentComposerVisible.value = false
        _commentReplyTarget.value = null
        clearCommentMentionSearch()
    }

    fun updateCommentDraft(
        sessionId: Long,
        replyRpid: Long?,
        text: String,
        imageUris: List<Uri>,
        syncToDynamic: Boolean,
    ) {
        _commentComposerDrafts.update { state ->
            if (state.sessionId != sessionId || sessionId <= state.completedSessionId) {
                state
            } else {
                state.update(
                    sessionId = sessionId,
                    replyRpid = replyRpid,
                    draft = CommentComposerDraft(text, imageUris, syncToDynamic),
                )
            }
        }
    }

    fun searchCommentMentionUsers(query: String) {
        if (_commentMentionSearchState.value.query == query && commentMentionSearchJob?.isActive == true) {
            return
        }
        commentMentionSearchJob?.cancel()
        _commentMentionSearchState.update {
            it.copy(query = query, isLoading = true, errorMessage = null)
        }
        commentMentionSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(250L)
            }
            CommentRepository.searchMentionUsers(query)
                .onSuccess { users ->
                    _commentMentionSearchState.update {
                        if (it.query == query) {
                            it.copy(users = users, isLoading = false, errorMessage = null)
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    _commentMentionSearchState.update {
                        if (it.query == query) {
                            it.copy(
                                users = emptyList(),
                                isLoading = false,
                                errorMessage = error.message ?: "搜索@好友失败"
                            )
                        } else {
                            it
                        }
                    }
                }
        }
    }

    fun clearCommentMentionSearch() {
        commentMentionSearchJob?.cancel()
        _commentMentionSearchState.value = CommentMentionSearchUiState()
    }

    /**
     *  发表评论
     */
    fun postComment(
        dynamicId: String,
        message: String,
        imageUris: List<Uri> = emptyList(),
        onResult: (Boolean, String) -> Unit,
    ) {
        if (_isSendingComment.value) return

        val draftSessionId = _commentComposerDrafts.value.sessionId
        val replyTarget = _commentReplyTarget.value
        val replyRpid = replyTarget?.parentRpid
        val subjectSession = commentSubjectSession
        val accountIdentity = activeAccountIdentity
        val selectedTarget = (_selectedCommentTarget.value ?: sentCommentTarget)
            .takeIf { _selectedDynamic.value?.id_str == dynamicId }
        val parentReply = replyRpid?.let { id ->
            _subReplyState.value.items.find { it.rpid == id }
                ?: _subReplyState.value.baseItems.find { it.rpid == id }
                ?: _commentUiState.value.comments.firstNotNullOfOrNull { findDynamicComment(it, id) }
        }
        val sentDraft = _commentComposerDrafts.value.drafts[commentComposerDraftKey(replyRpid)]
        _isSendingComment.value = true

        val job = launchAccount {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val item = findDynamicById(dynamicId)
                if (item == null) {
                    onResult(false, "动态不存在")
                    return@launchAccount
                }
                val target = selectedTarget ?: resolveDynamicCommentTargets(item).firstOrNull()
                if (target == null) {
                    onResult(false, "无法确定评论参数")
                    return@launchAccount
                }
                if (_selectedDynamic.value?.id_str != dynamicId || subjectSession != commentSubjectSession) {
                    return@launchAccount
                }
                selectCommentSubject(dynamicId, target)
                _selectedCommentTarget.value = target
                if (message.isBlank() && imageUris.isEmpty()) {
                    onResult(false, "请输入评论内容")
                    return@launchAccount
                }
                if (replyTarget != null && imageUris.isNotEmpty()) {
                    onResult(false, "回复暂不支持图片")
                    return@launchAccount
                }
                val pictures = uploadCommentPictures(imageUris)
                val response = CommentRepository.addCommentForSubject(
                    oid = target.oid,
                    type = target.type,
                    message = message,
                    root = replyTarget?.rootRpid ?: 0L,
                    parent = replyTarget?.parentRpid ?: 0L,
                    pictures = pictures,
                )
                if (accountIdentity != activeAccountIdentity || accountIdentity != TokenManager.accountIdentity.value ||
                    !shouldApplyDynamicSentComment(
                        expectedDynamicId = dynamicId, expectedTarget = target,
                        expectedSession = subjectSession, currentDynamicId = _selectedDynamic.value?.id_str,
                        currentTarget = _selectedCommentTarget.value, currentSession = commentSubjectSession,
                    )
                ) return@launchAccount
                if (response.isSuccess) {
                    if (
                        _commentComposerVisible.value &&
                        _commentComposerDrafts.value.sessionId == draftSessionId
                    ) {
                        _commentReplyTarget.value = null
                        _commentComposerVisible.value = false
                        clearCommentMentionSearch()
                    }
                    _commentComposerDrafts.update {
                        it.complete(draftSessionId, replyRpid, sentDraft)
                    }
                    val reply = response.getOrNull()
                    if (reply != null) {
                        applySentComment(resolveDynamicSentComment(reply, target, replyTarget, parentReply))
                    } else {
                        // A successful add may omit its reply; keep the current subject and refresh.
                        refreshComments()
                        if (_subReplyState.value.rootReply?.rpid == replyTarget?.rootRpid) refreshSubReplies()
                    }
                    onResult(true, "评论成功")
                } else {
                    onResult(false, response.exceptionOrNull()?.message ?: "评论失败")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (accountIdentity == activeAccountIdentity && accountIdentity == TokenManager.accountIdentity.value &&
                    subjectSession == commentSubjectSession && _selectedDynamic.value?.id_str == dynamicId
                ) onResult(false, e.message ?: "网络错误")
            }
        }
        job.invokeOnCompletion { _isSendingComment.value = false }
    }

    private suspend fun uploadCommentPictures(imageUris: List<Uri>): List<ReplyPicture> =
        if (imageUris.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            require(imageUris.size <= 9) { "最多选择 9 张图片" }
            imageUris.mapIndexed { index, uri ->
                val fileName = appContext.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                } ?: "comment_${System.currentTimeMillis()}_${index + 1}.jpg"
                // 流式上传:空/15MB 校验在 CommentRepository 内完成,不再整文件读入内存。
                CommentRepository.uploadCommentImage(
                    fileName = fileName,
                    mimeType = appContext.contentResolver.getType(uri) ?: "image/jpeg",
                    resolver = appContext.contentResolver,
                    uri = uri,
                ).getOrElse { throw it }
            }
        }

    fun likeComment(rpid: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (rpid <= 0L) return
        val current = _commentUiState.value.comments.firstOrNull { it.rpid == rpid }
            ?: _commentUiState.value.comments.firstNotNullOfOrNull { parent ->
                parent.replies.orEmpty().firstOrNull { it.rpid == rpid }
            }
            ?: _subReplyState.value.items.firstOrNull { it.rpid == rpid }
            ?: _subReplyState.value.rootReply?.takeIf { it.rpid == rpid }
        if (current == null) return
        val toLiked = !isDynamicCommentLiked(current)
        val target = _selectedCommentTarget.value
        if (target == null) {
            onResult(false, "无法确定评论参数")
            return
        }
        updateCurrentComments { applyDynamicCommentLikeInList(it, rpid, toLiked) }
        val subState = _subReplyState.value
        _subReplyState.value = subState.copy(
            rootReply = subState.rootReply?.let { root ->
                if (root.rpid == rpid) applyDynamicCommentLike(root, toLiked) else root
            },
            items = applyDynamicCommentLikeInList(subState.items, rpid, toLiked).toImmutableList()
        )
        launchAccount {
            CommentRepository.likeCommentForSubject(
                oid = target.oid,
                type = target.type,
                rpid = rpid,
                like = toLiked
            ).onFailure { error ->
                updateCurrentComments { applyDynamicCommentLikeInList(it, rpid, !toLiked) }
                val rollback = _subReplyState.value
                _subReplyState.value = rollback.copy(
                    rootReply = rollback.rootReply?.let { root ->
                        if (root.rpid == rpid) applyDynamicCommentLike(root, !toLiked) else root
                    },
                    items = applyDynamicCommentLikeInList(rollback.items, rpid, !toLiked).toImmutableList()
                )
                onResult(false, error.message ?: "操作失败")
            }.onSuccess {
                onResult(true, if (toLiked) "已点赞" else "已取消")
            }
        }
    }

    fun hateComment(rpid: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (rpid <= 0L) return
        val current = _commentUiState.value.comments.firstNotNullOfOrNull { reply ->
            findDynamicComment(reply, rpid)
        } ?: _subReplyState.value.items.firstOrNull { it.rpid == rpid }
            ?: _subReplyState.value.rootReply?.takeIf { it.rpid == rpid }
            ?: return
        val target = _selectedCommentTarget.value
        if (target == null) {
            onResult(false, "无法确定评论参数")
            return
        }
        val toHated = !isDynamicCommentHated(current)
        updateCurrentComments { applyDynamicCommentHateInList(it, rpid, toHated) }
        val subState = _subReplyState.value
        _subReplyState.value = subState.copy(
            rootReply = subState.rootReply?.let { root ->
                if (root.rpid == rpid) applyDynamicCommentHate(root, toHated) else root
            },
            items = applyDynamicCommentHateInList(subState.items, rpid, toHated).toImmutableList(),
        )
        launchAccount {
            CommentRepository.hateCommentForSubject(
                oid = target.oid,
                type = target.type,
                rpid = rpid,
                hate = toHated,
            ).fold(
                onSuccess = { onResult(true, if (toHated) "点踩成功" else "已取消点踩") },
                onFailure = { error ->
                    updateCurrentComments { replaceDynamicCommentInList(it, current) }
                    val rollback = _subReplyState.value
                    _subReplyState.value = rollback.copy(
                        rootReply = rollback.rootReply?.let { root ->
                            if (root.rpid == rpid) current else root
                        },
                        items = replaceDynamicCommentInList(rollback.items, current).toImmutableList(),
                    )
                    onResult(false, error.message ?: "点踩失败")
                },
            )
        }
    }

    private fun findDynamicComment(
        reply: com.android.purebilibili.data.model.response.ReplyItem,
        rpid: Long,
    ): com.android.purebilibili.data.model.response.ReplyItem? {
        if (reply.rpid == rpid) return reply
        return reply.replies.orEmpty().firstNotNullOfOrNull { findDynamicComment(it, rpid) }
    }

    fun deleteDynamicComment(rpid: Long, onResult: (Boolean, String) -> Unit) {
        val subjectSession = commentSubjectSession
        val dynamicId = _selectedDynamic.value?.id_str ?: return
        val accountIdentity = activeAccountIdentity
        val deletedReply = _subReplyState.value.items.find { it.rpid == rpid }
            ?: _commentUiState.value.comments.firstNotNullOfOrNull { findDynamicComment(it, rpid) }
        val rootId = deletedReply?.root?.takeIf { it > 0L }
            ?: _subReplyState.value.rootReply?.rpid?.takeIf { root ->
                _subReplyState.value.baseItems.any { it.rpid == rpid } && root != rpid
            } ?: 0L
        val target = _selectedCommentTarget.value
        if (target == null || rpid <= 0L) {
            onResult(false, "无法确定评论参数")
            return
        }
        launchAccount {
            CommentRepository.deleteCommentForSubject(
                oid = target.oid,
                type = target.type,
                rpid = rpid,
            ).fold(
                onSuccess = {
                    if (accountIdentity != activeAccountIdentity || accountIdentity != TokenManager.accountIdentity.value ||
                        !shouldApplyDynamicSentComment(
                            dynamicId, target, subjectSession, _selectedDynamic.value?.id_str,
                            _selectedCommentTarget.value, commentSubjectSession,
                        )
                    ) return@fold
                    if (sentCommentReplies.isReplyRemoved(rpid) || sentCommentReplies.isRootRemoved(rpid)) return@fold
                    if (!removedCommentIds.add(rpid)) return@fold
                    val subState = _subReplyState.value
                    if (rootId > 0L) sentCommentReplies.removeReply(rootId, rpid)
                    else sentCommentReplies.removeRoot(rpid)
                    remoteMainReplies = removeDynamicCommentFromList(remoteMainReplies, rpid, rootId)
                    remoteSubReplies = remoteSubReplies.filterNot { it.rpid == rpid }
                    updateCurrentComments {
                        sentCommentReplies.merge(removeDynamicCommentFromList(it, rpid, rootId))
                    }
                    _subReplyState.value = if (subState.rootReply?.rpid == rpid) {
                        SubReplyUiState()
                    } else {
                        subState.copy(
                            items = subState.items.filterNot { it.rpid == rpid }.toImmutableList(),
                            baseItems = subState.baseItems.filterNot { it.rpid == rpid }.toImmutableList(),
                            rootReply = subState.rootReply?.let { root ->
                                _commentUiState.value.comments.find { it.rpid == root.rpid }
                                    ?: removeDynamicCommentFromList(listOf(root), rpid, rootId).firstOrNull()
                            },
                            totalCount = (subState.totalCount - if (subState.rootReply?.rpid == rootId) 1 else 0)
                                .coerceAtLeast(0),
                        )
                    }
                    updateCommentUiState {
                        it.copy(totalCount = (it.totalCount - 1).coerceAtLeast(0))
                    }
                    commentCache.clear()
                    cacheCurrentComments(_commentUiState.value.comments)
                    onResult(true, "评论已删除")
                },
                onFailure = { onResult(false, it.message ?: "删除失败") },
            )
        }
    }

    fun toggleDynamicCommentTop(
        reply: com.android.purebilibili.data.model.response.ReplyItem,
        onResult: (Boolean, String) -> Unit,
    ) {
        val target = _selectedCommentTarget.value
        if (target == null || reply.rpid <= 0L) {
            onResult(false, "无法确定评论参数")
            return
        }
        launchAccount {
            CommentRepository.setCommentTopForSubject(
                oid = target.oid,
                type = target.type,
                rpid = reply.rpid,
                isCurrentlyTop = reply.replyControl?.isUpTop == true,
            ).fold(
                onSuccess = {
                    _selectedDynamic.value?.id_str?.takeIf(String::isNotBlank)?.let(::loadComments)
                    onResult(true, if (reply.replyControl?.isUpTop == true) "已取消置顶" else "已置顶")
                },
                onFailure = { onResult(false, it.message ?: "置顶操作失败") },
            )
        }
    }

    fun reportDynamicComment(
        rpid: Long,
        reason: Int,
        onResult: (Boolean, String) -> Unit,
    ) {
        val target = _selectedCommentTarget.value
        if (target == null || rpid <= 0L) {
            onResult(false, "无法确定评论参数")
            return
        }
        launchAccount {
            CommentRepository.reportCommentForSubject(
                oid = target.oid,
                type = target.type,
                rpid = rpid,
                reason = reason,
            ).fold(
                onSuccess = { onResult(true, "举报成功") },
                onFailure = { onResult(false, it.message ?: "举报失败") },
            )
        }
    }

    
    /**
     *  点赞动态
     */
    fun likeDynamic(
        dynamicId: String,
        knownIsLiked: Boolean? = null,
        onResult: (Boolean, String) -> Unit,
    ) {
        if (dynamicId.isBlank()) {
            onResult(false, "无法识别该动态")
            return
        }
        if (!likeRequestGate.tryAcquire(dynamicId)) {
            onResult(false, "操作进行中，请稍候")
            return
        }
        launchAccount {
            try {
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val serverIsLiked = knownIsLiked ?: findDynamicById(dynamicId)
                    ?.modules
                    ?.module_stat
                    ?.like
                    ?.status
                val isLiked = _likeOverrides.value[dynamicId]
                    ?: (_likedDynamics.value.contains(dynamicId) || serverIsLiked == true)
                val up = if (isLiked) 2 else 1  // 1=点赞, 2=取消
                
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi
                    .likeDynamic(
                        csrf = csrf,
                        body = com.android.purebilibili.core.network.DynamicThumbRequest(
                            dyn_id_str = dynamicId,
                            up = up
                        )
                    )
                if (response.code == 0) {
                    val toLiked = !isLiked
                    // 更新本地状态
                    _likedDynamics.value = if (toLiked) {
                        _likedDynamics.value + dynamicId
                    } else {
                        _likedDynamics.value - dynamicId
                    }
                    _likeOverrides.value = _likeOverrides.value + (dynamicId to toLiked)

                    val currentState = mapDynamicTimelineItems(_uiState.value) { items ->
                        applyDynamicLikeCountChange(
                            items = items,
                            dynamicId = dynamicId,
                            toLiked = toLiked
                        )
                    }
                    _uiState.value = currentState.copy(
                        userItems = applyDynamicLikeCountChange(
                            items = currentState.userItems,
                            dynamicId = dynamicId,
                            toLiked = toLiked
                        ).toImmutableList()
                    )

                    onResult(true, if (toLiked) "已点赞" else "已取消")
                } else {
                    onResult(false, response.message.ifBlank { "操作失败" })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            } finally {
                likeRequestGate.release(dynamicId)
            }
        }
    }

    fun addToWatchLater(aid: Long, onResult: (Boolean, String) -> Unit) {
        launchAccount {
            if (aid <= 0L) {
                onResult(false, "无法添加到稍后再看")
                return@launchAccount
            }

            val result = ActionRepository.toggleWatchLater(aid = aid, add = true)
            result
                .onSuccess { onResult(true, "已添加到稍后再看") }
                .onFailure { onResult(false, it.message ?: "添加失败") }
        }
    }

    fun checkDynamic(dynamicId: String, onResult: (Boolean, String) -> Unit) {
        if (dynamicId.isBlank()) {
            onResult(false, "无法识别该动态")
            return
        }
        val identity = activeAccountIdentity
        launchAccount {
            val status = com.android.purebilibili.feature.dynamic.visibility.DynamicVisibilityCheck.check(dynamicId)
            if (identity == TokenManager.accountIdentity.value) {
                onResult(status == com.android.purebilibili.feature.dynamic.visibility.DynamicVisibilityStatus.VISIBLE, status.message)
            }
        }
    }

    fun toggleDynamicReserve(
        action: DynamicReserveAction,
        onResult: (Result<DynamicReserveResult>) -> Unit,
    ) {
        if (action.dynamicId.isBlank() || action.reserveId <= 0L) {
            onResult(Result.failure(IllegalArgumentException("无法识别该预约")))
            return
        }
        launchAccount {
            val csrf = TokenManager.csrfCache
            if (csrf.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("请先登录")))
                return@launchAccount
            }
            try {
                val response = NetworkModule.dynamicApi.clickDynamicReserve(
                    csrf = csrf,
                    reserveId = action.reserveId,
                    currentButtonStatus = action.currentButtonStatus,
                    dynamicId = action.dynamicId,
                    reserveTotal = action.reserveTotal,
                )
                if (response.code != 0 || response.data == null) {
                    throw IllegalStateException(response.message.ifBlank { "预约操作失败" })
                }
                val data = requireNotNull(response.data)
                val result = DynamicReserveResult(
                    description = data.desc_update,
                    reserveTotal = data.reserve_update ?: action.reserveTotal,
                    buttonStatus = data.final_btn_status,
                )
                if (action.buttonType > 0 && result.buttonStatus == action.buttonType) {
                    LiveReserveReminderScheduler.schedule(getApplication(), action)
                } else {
                    LiveReserveReminderScheduler.cancel(getApplication(), action.reserveId)
                }
                onResult(Result.success(result))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onResult(Result.failure(error))
            }
        }
    }
    
    /**
     *  转发动态
     */
    fun repostDynamic(dynamicId: String, content: String = "", onResult: (Boolean, String) -> Unit) {
        repostDynamic(dynamicId = dynamicId, content = content, alsoComment = false, onResult = onResult)
    }

    fun repostDynamic(
        dynamicId: String,
        content: String = "",
        alsoComment: Boolean = false,
        richContents: List<com.android.purebilibili.core.network.DynamicRepostContentItem> = emptyList(),
        topic: com.android.purebilibili.data.model.response.DynamicCreateTopic? = null,
        onResult: (Boolean, String) -> Unit,
    ) {
        val identity = activeAccountIdentity
        launchAccount {
            try {
                if (dynamicId.isBlank()) {
                    onResult(false, "无法转发该动态")
                    return@launchAccount
                }
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val response = com.android.purebilibili.core.network.NetworkModule.dynamicApi
                    .repostDynamic(
                        csrf = csrf,
                        body = buildDynamicRepostRequest(
                            dynamicId = dynamicId,
                            content = content,
                            richContents = richContents,
                            topic = topic,
                        )
                    )
                currentCoroutineContext().ensureActive()
                if (identity != TokenManager.accountIdentity.value) return@launchAccount
                if (response.code == 0) {
                    val currentState = mapDynamicTimelineItems(_uiState.value) { items ->
                        applyDynamicForwardCountIncrement(items, dynamicId)
                    }
                    _uiState.value = currentState.copy(
                        userItems = applyDynamicForwardCountIncrement(
                            items = currentState.userItems,
                            dynamicId = dynamicId
                        ).toImmutableList()
                    )
                    var message = "转发成功"
                    if (alsoComment && content.isNotBlank()) {
                        val commentOk = postSourceDynamicComment(dynamicId, content)
                        message = if (commentOk) "转发成功，已同步评论" else "转发成功，评论同步失败"
                    }
                    if (identity != TokenManager.accountIdentity.value) return@launchAccount
                    onResult(true, message)
                    com.android.purebilibili.feature.dynamic.visibility.DynamicVisibilityCheck.afterPublication(
                        appContext,
                        com.android.purebilibili.data.model.response.resolveCreatedDynamicId(response.data),
                        identity,
                    )
                } else {
                    onResult(false, response.message.ifBlank { "转发失败" })
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    /** 转发时同步在原动态下发一条评论，失败不阻塞转发结果。 */
    private suspend fun postSourceDynamicComment(dynamicId: String, message: String): Boolean {
        return try {
            val item = findDynamicById(dynamicId) ?: return false
            val target = resolveDynamicCommentTargets(item).firstOrNull() ?: return false
            val subjectSession = commentSubjectSession
            val accountIdentity = activeAccountIdentity
            val response = CommentRepository.addCommentForSubject(
                oid = target.oid,
                type = target.type,
                message = message,
                root = 0L,
                parent = 0L
            )
            if (response.isSuccess && accountIdentity == activeAccountIdentity &&
                accountIdentity == TokenManager.accountIdentity.value &&
                shouldApplyDynamicSentComment(
                    dynamicId, target, subjectSession, _selectedDynamic.value?.id_str,
                    _selectedCommentTarget.value, commentSubjectSession,
                )
            ) {
                response.getOrNull()?.let { applySentComment(resolveDynamicSentComment(it, target, null, null)) }
                    ?: refreshComments()
            }
            response.isSuccess
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    //  [新增] 发布纯文本动态（对齐 BiliPai 动态页发布入口，成功后延迟校验防 shadow-ban）
    fun publishDynamic(content: String, onResult: (Boolean, String) -> Unit) {
        publishDynamic(
            draft = com.android.purebilibili.data.model.response.DynamicPublishDraft(text = content),
            context = null,
            onResult = onResult
        )
    }

    fun publishDynamic(
        draft: com.android.purebilibili.data.model.response.DynamicPublishDraft,
        context: android.content.Context?,
        onResult: (Boolean, String) -> Unit
    ) {
        if (draft.text.isBlank() && draft.imageUris.isEmpty() && draft.voteId <= 0L && draft.reserveId <= 0L) {
            onResult(false, "内容不能为空")
            return
        }
        val identity = activeAccountIdentity
        launchAccount {
            try {
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val createdId = if (context != null) {
                    DynamicCreateRepository.publish(context, draft).getOrElse {
                        if (it is CancellationException) throw it
                        if (identity != TokenManager.accountIdentity.value) return@launchAccount
                        onResult(false, it.message ?: "发布失败")
                        return@launchAccount
                    }
                } else {
                    val response = NetworkModule.dynamicApi.createDynamic(
                        content = draft.text.trim(),
                        csrf = csrf
                    )
                    if (response.code != 0) {
                        onResult(false, response.message.ifBlank { "发布失败" })
                        return@launchAccount
                    }
                    response.data?.dynamic_id_str.orEmpty()
                }
                currentCoroutineContext().ensureActive()
                if (identity != TokenManager.accountIdentity.value) return@launchAccount
                onResult(true, if (draft.publishAtSeconds != null) "定时发布已创建" else "发布成功")
                if (draft.publishAtSeconds == null) refresh(selectedTab = _selectedTab.value)
                com.android.purebilibili.feature.dynamic.visibility.DynamicVisibilityCheck.afterPublication(
                    appContext, createdId, identity,
                    privatePublication = draft.private,
                    scheduledPublication = draft.publishAtSeconds != null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    fun deleteDynamic(action: DynamicDeleteAction, onResult: (Boolean, String) -> Unit) {
        launchAccount {
            try {
                if (action.dynamicId.isBlank()) {
                    onResult(false, "无法删除该动态")
                    return@launchAccount
                }
                val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }

                val response = NetworkModule.dynamicApi.deleteDynamic(
                    csrf = csrf,
                    body = DynamicDeleteRequest(
                        dyn_id_str = action.dynamicId,
                        dyn_type = action.dynType,
                        rid_str = action.rid
                    )
                )
                if (response.code == 0) {
                    removeDynamicFromUiState(action.dynamicId)
                    onResult(true, "已删除动态")
                } else {
                    onResult(false, response.message.ifBlank { "删除失败" })
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    private val unfoldingDynamicIds = mutableSetOf<String>()

    fun unfoldRelatedDynamics(
        dynamicId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        val normalizedId = dynamicId.trim()
        if (normalizedId.isBlank()) return
        launchAccount {
            if (!unfoldingDynamicIds.add(normalizedId)) {
                onResult(false, "正在加载相关动态，请稍候")
                return@launchAccount
            }
            try {
                val snapshot = _uiState.value
                val cachedItems = snapshot.items + snapshot.userItems +
                    snapshot.timelinePages.values.flatMap { it.items }
                val anchor = cachedItems.firstOrNull {
                    it.id_str == normalizedId && it.modules.module_fold != null
                }
                if (anchor == null) {
                    onResult(false, "动态列表已更新，请刷新后重试")
                    return@launchAccount
                }
                val originalFold = anchor.modules.module_fold
                var resolvedFold = originalFold
                var ids = resolveDynamicFoldIds(anchor)
                if (ids.isEmpty()) {
                    onResult(false, "正在加载相关动态…")
                    val detail = DynamicRepository.getDynamicDetail(normalizedId)
                    coroutineContext.ensureActive()
                    resolvedFold = detail.getOrNull()?.modules?.module_fold
                    ids = detail.getOrNull()?.let(::resolveDynamicFoldIds).orEmpty()
                    if (ids.isEmpty()) {
                        onResult(false, "暂时无法获取相关动态，请稍后重试")
                        return@launchAccount
                    }
                }
                val cachedById = cachedItems.associateBy { it.id_str }
                val loadedItems = ids.mapNotNull(cachedById::get).toMutableList()
                val missingIds = ids.filterNot(cachedById::containsKey)
                if (missingIds.isNotEmpty()) {
                    onResult(false, "正在加载相关动态…")
                }
                for (id in missingIds) {
                    val result = DynamicRepository.getDynamicDetail(id)
                    coroutineContext.ensureActive()
                    result.getOrNull()?.takeIf { it.id_str == id }?.let(loadedItems::add)
                }
                // Apply against live state, never overwrite a newer refresh or interaction snapshot.
                _uiState.update { current ->
                    fun expand(items: List<DynamicItem>): List<DynamicItem> {
                        val currentAnchor = items.firstOrNull { it.id_str == normalizedId }
                        if (currentAnchor?.modules?.module_fold != originalFold) return items
                        return unfoldRelatedDynamicItems(items, normalizedId, loadedItems, resolvedFold)
                    }
                    val updated = mapDynamicTimelineItems(current, ::expand)
                    updated.copy(userItems = expand(updated.userItems).toImmutableList())
                }
                if (loadedItems.size < ids.size) {
                    onResult(false, if (loadedItems.isEmpty()) "相关动态加载失败，请重试"
                        else "部分相关动态加载失败，可点击重试")
                } else {
                    onResult(true, "")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onResult(false, "相关动态加载失败，请重试")
            } finally {
                unfoldingDynamicIds.remove(normalizedId)
            }
        }
    }

    private fun removeDynamicFromUiState(dynamicId: String) {
        removedDynamicIds += dynamicId
        val currentState = mapDynamicTimelineItems(_uiState.value) { items ->
            items.filterNot { it.id_str == dynamicId }
        }
        _uiState.value = currentState.copy(
            userItems = currentState.userItems.filterNot { it.id_str == dynamicId }.toImmutableList()
        )
    }

    //  [新增] 更多菜单管理动作分发：置顶 / 可见范围 / 评论互动 / 临时屏蔽
    fun handleManageAction(action: DynamicManageAction, onResult: (Boolean, String) -> Unit) {
        when (action) {
            is DynamicManageAction.NotInterested -> {
                if (action.dynamicId.isBlank()) {
                    onResult(false, "无法识别该动态")
                } else {
                    val updatedIds = normalizeDynamicNotInterestedIds(
                        _uiState.value.tempBannedDynamicIds + action.dynamicId,
                        MAX_NOT_INTERESTED_DYNAMIC_IDS,
                    )
                        .toImmutableSet()
                    _uiState.value = _uiState.value.copy(tempBannedDynamicIds = updatedIds)
                    persistNotInterestedDynamicIds(updatedIds)
                    onResult(true, "已标记为不感兴趣")
                }
            }
            is DynamicManageAction.ToggleTop -> toggleDynamicTop(action, onResult)
            is DynamicManageAction.SetVisibility -> setDynamicVisibility(action, onResult)
            is DynamicManageAction.SetReplySubject -> modifyReplySubject(action, onResult)
            is DynamicManageAction.BlockAuthor -> blockDynamicAuthor(action, onResult)
            is DynamicManageAction.Report -> Unit
            is DynamicManageAction.Edit -> Unit
        }
    }

    fun reportDynamic(
        action: DynamicManageAction.Report,
        reasonType: Int,
        reasonDesc: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        launchAccount {
            try {
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                if (action.dynamicId.isBlank() || action.authorMid <= 0L) {
                    onResult(false, "无法举报该动态")
                    return@launchAccount
                }
                val response = NetworkModule.dynamicApi.reportDynamic(
                    csrf = csrf,
                    accusedUid = action.authorMid,
                    dynamicId = action.dynamicId,
                    reasonType = reasonType,
                    reasonDesc = if (reasonType == 0) reasonDesc else null
                )
                if (response.code == 0) {
                    onResult(true, "已提交举报")
                } else {
                    onResult(false, response.message.ifBlank { "举报失败" })
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    fun editDynamic(
        context: android.content.Context,
        dynamicId: String,
        draft: com.android.purebilibili.data.model.response.DynamicPublishDraft,
        onResult: (Boolean, String) -> Unit,
    ) {
        launchAccount {
            com.android.purebilibili.data.repository.DynamicCreateRepository
                .edit(context = context, dynamicId = dynamicId, draft = draft)
                .fold(
                    onSuccess = {
                        onResult(true, "已更新动态")
                        refresh()
                    },
                    onFailure = { onResult(false, it.message ?: "编辑失败") },
                )
        }
    }

    fun toggleDynamicTop(action: DynamicManageAction.ToggleTop, onResult: (Boolean, String) -> Unit) {
        launchAccount {
            try {
                if (action.dynamicId.isBlank()) {
                    onResult(false, "无法操作该动态")
                    return@launchAccount
                }
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val response = if (action.isCurrentlyTop) {
                    NetworkModule.dynamicApi.removeDynamicTop(
                        csrf = csrf,
                        body = DynamicTopRequest(dyn_str = action.dynamicId)
                    )
                } else {
                    NetworkModule.dynamicApi.setDynamicTop(
                        csrf = csrf,
                        body = DynamicTopRequest(dyn_str = action.dynamicId)
                    )
                }
                if (response.code == 0) {
                    onResult(true, if (action.isCurrentlyTop) "已取消置顶" else "已置顶")
                } else {
                    onResult(false, response.message.ifBlank { "操作失败" })
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    fun setDynamicVisibility(action: DynamicManageAction.SetVisibility, onResult: (Boolean, String) -> Unit) {
        launchAccount {
            try {
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val response = NetworkModule.dynamicApi.setDynamicVisibility(
                    csrf = csrf,
                    body = DynamicVisibilityRequest(
                        object_id = buildDynamicVisibilityObjectId(action.dynamicId, action.dynType),
                        action = resolveDynamicVisibilityAction(action.isPrivate)
                    )
                )
                if (response.code == 0) {
                    onResult(true, if (action.isPrivate) "已设为仅自己可见" else "已设为公开")
                } else {
                    onResult(false, response.message.ifBlank { "设置失败" })
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    fun loadReplyInteractionStatus(oid: Long, type: Int, onLoaded: (ReplyInteractionData?) -> Unit) {
        launchAccount {
            try {
                val response = NetworkModule.dynamicApi.getReplyInteractionStatus(oid = oid, type = type)
                onLoaded(if (response.code == 0) response.data else null)
            } catch (e: Exception) {
                onLoaded(null)
            }
        }
    }

    fun modifyReplySubject(action: DynamicManageAction.SetReplySubject, onResult: (Boolean, String) -> Unit) {
        launchAccount {
            try {
                val csrf = TokenManager.csrfCache
                if (csrf.isNullOrEmpty()) {
                    onResult(false, "请先登录")
                    return@launchAccount
                }
                val response = NetworkModule.dynamicApi.modifyReplySubject(
                    oid = action.oid,
                    type = action.replyType,
                    action = action.action,
                    csrf = csrf
                )
                if (response.code == 0) {
                    onResult(true, "设置成功")
                } else {
                    onResult(false, response.message.ifBlank { "设置失败" })
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }
    }

    private fun blockDynamicAuthor(
        action: DynamicManageAction.BlockAuthor,
        onResult: (Boolean, String) -> Unit,
    ) {
        if (action.authorMid <= 0L) {
            onResult(false, "无法识别该用户")
            return
        }
        launchAccount {
            val result = runCatching {
                blockedUpRepository.blockUpWithBilibiliSync(
                    mid = action.authorMid,
                    name = action.authorName,
                    face = action.authorFace,
                )
            }.getOrElse { error ->
                onResult(false, error.message ?: "屏蔽失败")
                return@launchAccount
            }
            _uiState.value = mapDynamicTimelineItems(_uiState.value) { items ->
                items.filterNot { it.modules.module_author?.mid == action.authorMid }
            }.copy(
                userItems = _uiState.value.userItems
                    .filterNot { it.modules.module_author?.mid == action.authorMid }
                    .toImmutableList(),
            )
            onResult(result.localChanged, result.message)
        }
    }

    companion object {
        private const val USER_SELECTION_DEBOUNCE_MS = 120L
        private const val PREFS_DYNAMIC_CACHE = "dynamic_cache"
        private const val PREFS_DYNAMIC_USERS = "dynamic_user_prefs"
        //  发布动态后延迟校验时间（对齐 BiliPai checkCreatedDyn 的 5 秒）
        private const val KEY_DYNAMIC_CACHE = "dynamic_items_cache"
        private const val KEY_DYNAMIC_CACHE_TIME = "dynamic_cache_time"
        private const val KEY_NOT_INTERESTED_DYNAMIC_IDS = "not_interested_dynamic_ids_v1"
        private const val KEY_PINNED_USERS = "dynamic_pinned_users"
        private const val KEY_HIDDEN_USERS = "dynamic_hidden_users"
        private const val KEY_DISPLAY_MODE = "dynamic_display_mode"
        private const val KEY_SELECTED_TAB = "dynamic_selected_tab"
        private const val DYNAMIC_TOP_TAB_COUNT = 4
        private const val MAX_CACHE_ITEMS = 100
        private const val MAX_NOT_INTERESTED_DYNAMIC_IDS = 500
    }
}

private const val DYNAMIC_FOLLOWINGS_PAGE_SIZE = 50

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
    val items: ImmutableList<DynamicItem> = persistentListOf(),
    val userItems: ImmutableList<DynamicItem> = persistentListOf(), //  [新增] 选中 UP主的动态
    val timelineRequestType: String = "all",
    val isLoading: Boolean = false,
    val error: String? = null,
    val userIsLoading: Boolean = false,
    val userError: String? = null,
    val hasMore: Boolean = true,
    val hasUserMore: Boolean = true, //  [新增] UP主动态是否有更多
    val incrementalRefreshBoundaryKey: String? = null,
    val incrementalPrependedCount: Int = 0,
    val errorSource: DynamicFeedErrorSource = DynamicFeedErrorSource.NONE,
    val timelinePages: PersistentMap<String, DynamicTimelinePageState> = persistentMapOf(),
    // 用户明确标记不感兴趣的动态 id，持久化后刷新和重启仍会过滤。
    val tempBannedDynamicIds: ImmutableSet<String> = persistentSetOf(),
    //  [新增] 关注 UP 列表未读（有更新的 mid 集合，来自 uplist 接口）
    val uplistUpdateMids: ImmutableSet<Long> = persistentSetOf()
)

data class DynamicTimelinePageState(
    val items: ImmutableList<DynamicItem> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val incrementalRefreshBoundaryKey: String? = null,
    val incrementalPrependedCount: Int = 0,
    val errorSource: DynamicFeedErrorSource = DynamicFeedErrorSource.NONE,
    val isCachePlaceholder: Boolean = false
)
