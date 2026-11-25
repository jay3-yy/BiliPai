// 文件路径: feature/list/ListViewModel.kt
package com.android.purebilibili.feature.list

import com.android.purebilibili.core.player.PlaybackProgressManager

import android.app.Application
import android.content.Context
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPage
import com.android.purebilibili.core.paging.ForwardPagingSource
import com.android.purebilibili.core.paging.ForwardPagingSession
import kotlinx.coroutines.Job
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.refresh.HistoryRefreshBus
import com.android.purebilibili.core.refresh.FavoriteSubscriptionBus
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.FavoriteFolderCoverCache
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// 通用的 UI 状态
@androidx.compose.runtime.Immutable
data class ListUiState(
    val title: String = "",
    val items: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val canRemoveItems: Boolean = true,
    val loadMoreError: String? = null,
)

// 基类 ViewModel
abstract class BaseListViewModel(application: Application, private val pageTitle: String) : AndroidViewModel(application) {
    protected val _uiState = MutableStateFlow(ListUiState(title = pageTitle, isLoading = true))
    val uiState = _uiState.asStateFlow()
    protected val _actionEvents = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val actionEvents: kotlinx.coroutines.flow.Flow<String> = _actionEvents.receiveAsFlow()
    internal val accountRequests = AccountScopedRequests(viewModelScope)

    init {
        accountRequests.observe {
            _uiState.value = ListUiState(title = pageTitle)
            onAccountChanged()
        }
    }

    protected open fun onAccountChanged() {
        loadData()
    }

    // 应当在子类初始化完成后调用
    open fun loadData(showLoading: Boolean = true) {
        accountRequests.launch {
            val shouldShowLoading = showLoading || _uiState.value.items.isEmpty()
            if (shouldShowLoading) {
                _uiState.update { it.copy(isLoading = true, error = null, loadMoreError = null) }
            } else {
                _uiState.update { it.copy(error = null, loadMoreError = null) }
            }
            try {
                val items = fetchItems()
                _uiState.update { it.copy(isLoading = false, items = items, error = null, loadMoreError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    // 子类必须实现此方法来提供数据
    abstract suspend fun fetchItems(): List<VideoItem>
}

class LikedVideosViewModel(
    application: Application,
    private val targetMid: Long? = null,
    ownerName: String? = null,
    private val isCoinArchive: Boolean = false,
) : BaseListViewModel(
    application,
    ownerName?.takeIf { it.isNotBlank() }?.let { "$it 的${if (isCoinArchive) "投币" else "点赞"}" }
        ?: if (targetMid != null && targetMid > 0L) {
            "最近${if (isCoinArchive) "投币" else "点赞"}"
        } else {
            "我的${if (isCoinArchive) "投币" else "点赞"}"
        },
) {
    val isOwnLikedVideos: Boolean = targetMid == null && !isCoinArchive
    private val _isManagingLikes = MutableStateFlow(false)
    val isManagingLikes = _isManagingLikes.asStateFlow()
    private val _likedFavoriteFolders = MutableStateFlow<List<FavFolder>>(emptyList())
    val likedFavoriteFolders = _likedFavoriteFolders.asStateFlow()
    private val _isLoadingLikedFolders = MutableStateFlow(false)
    val isLoadingLikedFolders = _isLoadingLikedFolders.asStateFlow()

    fun loadLikedFavoriteFolders() {
        if (_isLoadingLikedFolders.value) return
        _likedFavoriteFolders.value = emptyList()
        _isLoadingLikedFolders.value = true
        accountRequests.launch {
            try {
                val mid = accountRequests.identity.mid
                if (mid == null) {
                    android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                com.android.purebilibili.data.repository.FavoriteRepository.getFavFolders(mid).fold(
                    onSuccess = { _likedFavoriteFolders.value = it },
                    onFailure = { android.widget.Toast.makeText(getApplication(), it.message ?: "加载收藏夹失败", android.widget.Toast.LENGTH_SHORT).show() },
                )
            } finally {
                _isLoadingLikedFolders.value = false
            }
        }
    }

    fun manageLikedVideos(aids: List<Long>, removeLike: Boolean, folderId: Long? = null, onFinished: () -> Unit) {
        if (_isManagingLikes.value || aids.isEmpty()) return
        _isManagingLikes.value = true
        accountRequests.launch {
            try {
                var succeeded = 0
                for (aid in aids.distinct()) {
                    val result = if (removeLike) {
                        com.android.purebilibili.data.repository.ActionRepository.likeVideo(aid, false)
                    } else {
                        com.android.purebilibili.data.repository.ActionRepository.favoriteVideo(aid, true, folderId = requireNotNull(folderId), showSuccessFeedback = false)
                    }
                    if (result.isSuccess) succeeded++
                }
                android.widget.Toast.makeText(getApplication(), "成功 $succeeded / ${aids.distinct().size}", android.widget.Toast.LENGTH_SHORT).show()
                onFinished()
                if (removeLike && succeeded > 0) loadData()
            } finally {
                _isManagingLikes.value = false
            }
        }
    }
    private val pageSize = 20
    private var pagingSession: ForwardPagingSession<Int, VideoItem>? = null
    private var pagingJob: Job? = null
    private val _isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMoreState = _isLoadingMoreState.asStateFlow()
    private val _hasMoreState = MutableStateFlow(true)
    val hasMoreState = _hasMoreState.asStateFlow()

    override fun onAccountChanged() {
        _isManagingLikes.value = false
        _isLoadingLikedFolders.value = false
        _likedFavoriteFolders.value = emptyList()
        loadData()
    }

    override fun loadData(showLoading: Boolean) {
        pagingJob?.cancel()
        _uiState.update { it.copy(isLoading = showLoading || it.items.isEmpty(), error = null, loadMoreError = null) }
        _isLoadingMoreState.value = false
        _hasMoreState.value = true
        val identity = accountRequests.identity
        lateinit var session: ForwardPagingSession<Int, VideoItem>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = pageSize, initialLoadSize = pageSize, enablePlaceholders = false),
            sourceFactory = {
                var resolvedMid = targetMid?.takeIf { it > 0L }
                InteractionVideosPagingSource(pageSize) { number ->
                    val mid = resolvedMid ?: NetworkModule.api.getNavInfo().data?.mid
                    check(mid != null && mid > 0L) { "请先登录" }
                    resolvedMid = mid
                    fetchInteractionPage(mid, number)
                }
            },
            onChanged = { items, states ->
                if (pagingSession === session && accountRequests.isCurrent(identity) && states != null) {
                    val refreshError = (states.refresh as? LoadState.Error)?.error
                    val appendError = (states.append as? LoadState.Error)?.error
                    val refreshing = states.refresh is LoadState.Loading
                    _uiState.update { current ->
                        current.copy(
                            items = if (refreshing || refreshError != null) current.items else items,
                            isLoading = refreshing && (showLoading || current.items.isEmpty()),
                            error = refreshError?.message ?: if (refreshError != null) "加载失败" else null,
                            loadMoreError = appendError?.message ?: if (appendError != null) "加载更多失败" else null,
                        )
                    }
                    _isLoadingMoreState.value = states.append is LoadState.Loading
                    _hasMoreState.value = !states.append.endOfPaginationReached
                }
            },
        )
        pagingSession = session
        pagingJob = accountRequests.launch { session.run() }
    }

    // 保留基类接口；实际首屏、追加和失败重试均由当前 Paging 会话负责。
    override suspend fun fetchItems(): List<VideoItem> = _uiState.value.items

    fun loadMore(retry: Boolean = false) {
        if (!accountRequests.isCurrent()) return
        if (retry) pagingSession?.retry() else pagingSession?.append()
    }

    private suspend fun fetchInteractionPage(
        mid: Long,
        page: Int,
    ): com.android.purebilibili.data.repository.LikedVideosRepository.Page {
        return if (isCoinArchive) {
            com.android.purebilibili.data.repository.LikedVideosRepository
                .getCoinVideos(mid = mid, page = page, pageSize = pageSize)
                .getOrThrow()
        } else {
            com.android.purebilibili.data.repository.LikedVideosRepository
                .getLikedVideos(mid = mid, page = page, pageSize = pageSize)
                .getOrThrow()
        }
    }

    init {
        loadData()
    }
}

class LikedVideosViewModelFactory(
    private val application: Application,
    private val targetMid: Long? = null,
    private val ownerName: String? = null,
    private val isCoinArchive: Boolean = false,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LikedVideosViewModel(
            application = application,
            targetMid = targetMid,
            ownerName = ownerName,
            isCoinArchive = isCoinArchive,
        ) as T
    }
}

// --- 历史记录 ViewModel (支持游标分页加载) ---
class HistoryViewModel(application: Application) : BaseListViewModel(application, "历史记录") {
    private var isPageActive = false
    private var startupLoadsActivated = false
    private var browsingSessionStarted = false
    private var browsingSessionKey: Any? = null

    /** A tab visit survives playback; a standalone history entry uses its ViewModel lifetime. */
    fun enterBrowsingSession(sessionKey: Any? = null) {
        if (browsingSessionStarted && browsingSessionKey === sessionKey) return
        browsingSessionStarted = true
        browsingSessionKey = sessionKey
        loadData(showLoading = _uiState.value.items.isEmpty())
    }

    fun setPageActive(active: Boolean) {
        isPageActive = active
        if (active) updateCachedHistoryProgress()
        if (active && !startupLoadsActivated) {
            startupLoadsActivated = true
            loadHistoryPauseState()
        }
    }
    override fun onAccountChanged() {
        startupLoadsActivated = false
        browsingSessionStarted = false
        recapSnapshots.clear()
        historySearchQuery = ""
        historyPagingJob?.cancel()
        historyPagingSession = null
        presentedHistory = emptyList()
        historyEdits.clear()
        historyCleared = false
        historyClearInProgress = false
        _isLoadingMoreState.value = false
        _hasMoreState.value = true
        _historyItemsMap.clear()
        _historyItemsByRenderKey.clear()
        _deleteSession.value = null
        _isHistoryPausedState.value = false
        _isHistoryManagementBusyState.value = false
        if (isPageActive) {
            setPageActive(true)
            loadData()
        }
    }


    internal val recapSnapshots = mutableMapOf<PersonalRecapWindow, HistoryRecapSnapshot>()
    private var historySearchQuery: String = ""
    private var historyListType: String? = null
    
    private val progressManager by lazy {
        com.android.purebilibili.core.player.PlaybackProgressManager.getInstance(
            getApplication<Application>()
        )
    }
    
    private var historyPagingSession: ForwardPagingSession<HistoryPageKey, HistoryPageChunk>? = null
    private var historyPagingJob: Job? = null
    private var presentedHistory = emptyList<com.android.purebilibili.data.model.response.HistoryItem>()
    private val historyEdits = HistoryPresentationEdits()
    private var historyCleared = false
    private var historyClearInProgress = false
    private var historyClearRevision = 0L

    //  暴露加载更多状态
    private val _isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMoreState = _isLoadingMoreState.asStateFlow()
    
    private val _hasMoreState = MutableStateFlow(true)
    val hasMoreState = _hasMoreState.asStateFlow()
    
    // [新增] 保存完整的历史记录项（包含导航信息）
    private val _historyItemsMap = mutableMapOf<String, com.android.purebilibili.data.model.response.HistoryItem>()
    private val _historyItemsByRenderKey = mutableMapOf<String, com.android.purebilibili.data.model.response.HistoryItem>()

    private val _deleteSession = MutableStateFlow<HistoryDeleteSession?>(null)
    internal val deleteSession = _deleteSession.asStateFlow()

    private val _isHistoryPausedState = MutableStateFlow(false)
    val isHistoryPausedState = _isHistoryPausedState.asStateFlow()

    private val _isHistoryManagementBusyState = MutableStateFlow(false)
    val isHistoryManagementBusyState = _isHistoryManagementBusyState.asStateFlow()

    private data class HistoryClearSnapshot(val cleared: Boolean, val hasMore: Boolean)

    /**
     * 根据 bvid 获取历史记录项的导航信息
     */
    fun getHistoryItem(lookupKey: String): com.android.purebilibili.data.model.response.HistoryItem? {
        val normalizedKey = lookupKey.trim()
        if (normalizedKey.isEmpty()) return null
        return _historyItemsMap[normalizedKey] ?: _historyItemsByRenderKey[normalizedKey]
    }

    fun resolveHistoryRenderKey(video: VideoItem): String {
        val bvid = video.bvid.trim()
        if (bvid.isNotEmpty()) return bvid
        val matched = _historyItemsByRenderKey.entries.firstOrNull { (_, item) ->
            item.videoItem.id == video.id &&
                item.videoItem.cid == video.cid &&
                item.videoItem.title == video.title
        }?.key
        if (!matched.isNullOrBlank()) return matched
        return "unknown_${video.id.coerceAtLeast(0L)}"
    }

    fun resolveHistoryLookupKey(video: VideoItem): String {
        val bvid = video.bvid.trim()
        if (bvid.isNotEmpty()) return bvid
        return resolveHistoryRenderKey(video)
    }

    fun setHistoryListType(type: String?) {
        val normalized = type?.trim()?.takeIf { it.isNotEmpty() && !it.equals("all", ignoreCase = true) }
        if (historyListType == normalized) return
        historyListType = normalized
        if (historySearchQuery.isNotBlank()) return
        loadData(showLoading = true)
    }

    fun searchHistory(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty() && historySearchQuery.isEmpty()) return
        historySearchQuery = normalized
        loadData(showLoading = true)
    }

    fun startVideoDissolve(renderKey: String) {
        startDeleteSession(setOf(renderKey))
    }

    fun startVideosDissolve(renderKeys: Set<String>, visibleKeys: Set<String>) {
        startDeleteSession(renderKeys, visibleKeys)
    }

    private fun startDeleteSession(renderKeys: Set<String>, visibleKeys: Set<String> = renderKeys) {
        if (_deleteSession.value != null) return
        val session = createHistoryDeleteSession(
            targetKeys = renderKeys,
            dissolveAnimationSafe = isThanosEffectSupported(getApplication<Application>()),
            visibleKeys = visibleKeys,
        ) ?: return
        if (session.animationMode == HistoryDeleteAnimationMode.DIRECT_DELETE ||
            shouldFinalizeHistoryDeleteSession(session)
        ) {
            deleteHistoryItems(session.targetKeys)
        } else {
            _deleteSession.value = session
            accountRequests.launch {
                // Also cover navigation/viewport changes before a lazy row ever mounts.
                // The normal per-card watchdog completes earlier; this bounds the whole session.
                delay(6_000L)
                var keysToDelete: Set<String>? = null
                _deleteSession.update { current ->
                    if (current != null && current.targetKeys === session.targetKeys) {
                        keysToDelete = current.targetKeys
                        null
                    } else {
                        keysToDelete = null
                        current
                    }
                }
                keysToDelete?.let(::deleteHistoryItems)
            }
        }
    }

    fun completeVideoDissolve(renderKey: String) {
        val key = renderKey.trim()
        if (key.isEmpty()) return
        var keysToDelete: Set<String>? = null
        _deleteSession.update { currentSession ->
            keysToDelete = null
            if (currentSession == null || key !in currentSession.targetKeys) {
                return@update currentSession
            }
            val nextSession = reduceHistoryDeleteSessionOnAnimationComplete(currentSession, key)
            if (shouldFinalizeHistoryDeleteSession(nextSession)) {
                keysToDelete = nextSession.targetKeys
                null
            } else {
                nextSession
            }
        }
        keysToDelete?.let(::deleteHistoryItems)
    }

    private fun enrichHistoryProgress(
        historyItems: List<com.android.purebilibili.data.model.response.HistoryItem>
    ): List<com.android.purebilibili.data.model.response.HistoryItem> {
        return historyItems.map { item ->
            val video = item.videoItem
            if (video.bvid.isBlank()) return@map item

            val cid = (item.cid.takeIf { it > 0L } ?: video.cid).coerceAtLeast(0L)
            val localCachedMs = progressManager.getCachedPosition(video.bvid, cid)
            val resolvedProgress = resolveHistoryDisplayProgress(
                serverProgressSec = item.progress,
                durationSec = video.duration,
                localPositionMs = localCachedMs
            )

            if (resolvedProgress == item.progress && resolvedProgress == video.progress) {
                item
            } else {
                item.copy(
                    progress = resolvedProgress,
                    videoItem = video.copy(progress = resolvedProgress)
                )
            }
        }
    }

    /** Refresh progress in place so watching a video cannot reorder the current list. */
    private fun updateCachedHistoryProgress() = publishHistoryItems()

    private fun publishHistoryItems() {
        val visible = if (historyCleared) emptyList() else historyEdits.visible(enrichHistoryProgress(presentedHistory))
        _historyItemsMap.clear()
        _historyItemsByRenderKey.clear()
        cacheHistoryItems(visible)
        _uiState.update { it.copy(items = visible.map { entry -> entry.videoItem }) }
    }

    override fun loadData(showLoading: Boolean) {
        if (historyClearInProgress) return
        historyPagingJob?.cancel()
        val identity = accountRequests.identity
        val query = historySearchQuery
        val clearRevision = historyClearRevision
        val reloadingClearedHistory = historyCleared
        val type = historyListType
        _uiState.update { it.copy(isLoading = showLoading || it.items.isEmpty(), error = null, loadMoreError = null) }
        _isLoadingMoreState.value = false
        var firstPageApplied = false
        lateinit var session: ForwardPagingSession<HistoryPageKey, HistoryPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                HistoryPagingSource(searching = query.isNotEmpty()) { key ->
                    if (query.isNotEmpty()) {
                        com.android.purebilibili.data.repository.HistoryRepository.searchHistory(key.page, query)
                    } else {
                        com.android.purebilibili.data.repository.HistoryRepository.getHistoryList(
                            ps = 30, max = key.max, viewAt = key.viewAt, business = key.business, type = type,
                        )
                    }.getOrThrow()
                }
            },
            onChanged = { pages, states ->
                if (historyPagingSession === session && accountRequests.isCurrent(identity) && states != null) {
                    val refreshError = (states.refresh as? LoadState.Error)?.error
                    val appendError = (states.append as? LoadState.Error)?.error
                    if (states.refresh is LoadState.NotLoading && pages.isNotEmpty()) {
                        if (!firstPageApplied) {
                            firstPageApplied = true
                            if (!historyClearInProgress && clearRevision == historyClearRevision) historyCleared = false
                            _deleteSession.value = null
                        }
                        presentedHistory = pages.flatMap { it.items }
                            .distinctBy { com.android.purebilibili.feature.list.resolveHistoryRenderKey(it) }
                        // 历史列表到手就预热横竖屏判定，点击进详情时首帧即进入正确布局。
                        com.android.purebilibili.data.repository.VideoRepository
                            .prefetchVerticalFlags(presentedHistory.map { it.videoItem.bvid })
                        publishHistoryItems()
                    }
                    if (!historyCleared || (reloadingClearedHistory && clearRevision == historyClearRevision)) {
                        _uiState.update { it.copy(
                            isLoading = states.refresh is LoadState.Loading && (showLoading || it.items.isEmpty()),
                            error = refreshError?.let { error -> error.message ?: "加载历史失败" },
                            loadMoreError = appendError?.let { error -> error.message ?: "加载更多历史失败" },
                        ) }
                        _hasMoreState.value = !historyCleared && !states.append.endOfPaginationReached
                        _isLoadingMoreState.value = !historyCleared && states.append is LoadState.Loading
                    }
                }
            },
        )
        historyPagingSession = session
        historyPagingJob = accountRequests.launch { session.run() }
    }

    override suspend fun fetchItems(): List<VideoItem> = _uiState.value.items

    fun loadMore(retry: Boolean = false) {
        if (historyCleared || historyClearInProgress || !accountRequests.isCurrent()) return
        if (retry) historyPagingSession?.retry() else historyPagingSession?.append()
    }

    fun retryHistory() {
        if (historyCleared || historyPagingSession == null) loadData() else historyPagingSession?.retry()
    }

    fun deleteHistoryItems(renderKeys: Set<String>) {
        if (renderKeys.isEmpty() || !accountRequests.isCurrent()) return
        val requestedIdentity = accountRequests.identity
        val csrf = TokenManager.csrfCache.orEmpty()
        val sessData = TokenManager.sessDataCache
        val isSessionCurrent = {
            accountRequests.isCurrent(requestedIdentity) && TokenManager.csrfCache.orEmpty() == csrf &&
                TokenManager.sessDataCache == sessData
        }
        val targets = renderKeys.mapNotNull { _historyItemsByRenderKey[it] }
        if (targets.isEmpty()) return
        val batch = historyEdits.hide(targets)
        publishHistoryItems()
        AppScope.ioScope.launch {
            try {
                if (!isSessionCurrent()) return@launch
                check(csrf.isNotBlank()) { "请先登录" }
                val succeeded = deleteHistoryItemsInBackground(batch.toList(), csrf, isSessionCurrent)
                withContext(Dispatchers.Main.immediate) {
                    if (!isSessionCurrent()) return@withContext
                    historyEdits.restore(batch, succeeded)
                    publishHistoryItems()
                    val message = if (succeeded.size == batch.size) {
                        if (succeeded.size == 1) "已删除历史记录" else "已删除 ${succeeded.size} 条历史记录"
                    } else "批量删除完成：成功 ${succeeded.size} / ${batch.size}"
                    android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    if (!isSessionCurrent()) return@withContext
                    historyEdits.restore(batch)
                    publishHistoryItems()
                    android.widget.Toast.makeText(getApplication(), "删除历史失败: ${error.message ?: "请稍后重试"}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun clearAllHistory() {
        if (_isHistoryManagementBusyState.value) return
        val snapshot = captureHistoryClearSnapshot()
        historyClearInProgress = true
        historyClearRevision += 1
        applyHistoryClearOptimisticState()

        accountRequests.launch {
            val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache.orEmpty()
            if (csrf.isBlank()) {
                restoreHistoryClearSnapshot(snapshot)
                historyClearInProgress = false
                _isHistoryManagementBusyState.value = false
                android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            val result = com.android.purebilibili.data.repository.HistoryRepository.clearHistory(csrf)
            if (result.isSuccess) {
                android.widget.Toast.makeText(getApplication(), "已清空历史记录", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                restoreHistoryClearSnapshot(snapshot)
                android.widget.Toast.makeText(
                    getApplication(),
                    "清空历史失败: ${result.exceptionOrNull()?.message ?: "请稍后重试"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            historyClearInProgress = false
            _isHistoryManagementBusyState.value = false
        }
    }

    fun deleteViewedHistory() {
        val viewedKeys = _historyItemsByRenderKey
            .filterValues { item -> item.progress == -1 }
            .keys
            .toSet()
        if (viewedKeys.isEmpty()) {
            android.widget.Toast.makeText(
                getApplication(),
                "无已看记录",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        deleteHistoryItems(viewedKeys)
    }

    fun addToWatchLater(item: com.android.purebilibili.data.model.response.HistoryItem) {
        if (!canAddHistoryToWatchLater(item)) {
            android.widget.Toast.makeText(
                getApplication(),
                "该内容无法加入稍后再看",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        accountRequests.launch {
            val result = com.android.purebilibili.data.repository.ActionRepository.toggleWatchLater(
                aid = item.videoItem.id,
                add = true,
            )
            android.widget.Toast.makeText(
                getApplication(),
                result.fold(
                    onSuccess = { "已添加到稍后再看" },
                    onFailure = { it.message ?: "添加失败" },
                ),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun captureHistoryClearSnapshot() = HistoryClearSnapshot(historyCleared, _hasMoreState.value)

    private fun applyHistoryClearOptimisticState() {
        _isHistoryManagementBusyState.value = true
        _deleteSession.value = null
        historyCleared = true
        _uiState.update { it.copy(isLoading = false, error = null, loadMoreError = null) }
        publishHistoryItems()
        _hasMoreState.value = false
        _isLoadingMoreState.value = false
    }

    private fun restoreHistoryClearSnapshot(snapshot: HistoryClearSnapshot) {
        historyCleared = snapshot.cleared
        publishHistoryItems()
        _hasMoreState.value = snapshot.hasMore
    }

    fun loadHistoryPauseState() {
        accountRequests.launch {
            val result = com.android.purebilibili.data.repository.HistoryRepository.getHistoryPaused()
            result.getOrNull()?.let { paused ->
                _isHistoryPausedState.value = paused
            }
        }
    }

    fun toggleHistoryPause() {
        if (_isHistoryManagementBusyState.value) return
        val currentPaused = _isHistoryPausedState.value
        val nextPaused = !currentPaused
        val csrf = com.android.purebilibili.core.store.TokenManager.csrfCache.orEmpty()
        if (csrf.isBlank()) {
            android.widget.Toast.makeText(getApplication(), "请先登录", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        _isHistoryPausedState.value = nextPaused
        _isHistoryManagementBusyState.value = true
        accountRequests.launch {
            val result = com.android.purebilibili.data.repository.HistoryRepository.setHistoryPaused(
                paused = nextPaused,
                csrf = csrf
            )
            if (result.isSuccess) {
                android.widget.Toast.makeText(
                    getApplication(),
                    resolveHistoryPauseSuccessMessage(nextPaused),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                _isHistoryPausedState.value = currentPaused
                android.widget.Toast.makeText(
                    getApplication(),
                    "设置历史记录失败: ${result.exceptionOrNull()?.message ?: "请稍后重试"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            _isHistoryManagementBusyState.value = false
        }
    }

    private suspend fun deleteHistoryItemsInBackground(
        targetEntries: List<Pair<String, com.android.purebilibili.data.model.response.HistoryItem>>,
        csrf: String,
        isSessionCurrent: () -> Boolean,
    ): Set<String> = supervisorScope {
        val semaphore = Semaphore(resolveDeleteBatchParallelism(targetEntries.size))
        targetEntries.map { (key, item) ->
            async {
                val kid = resolveHistoryDeleteKid(item).orEmpty()
                if (kid.isBlank()) return@async null
                semaphore.withPermit {
                    if (deleteHistoryItemWithRetry(kid, csrf, isSessionCurrent).isSuccess) {
                        key
                    } else {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    private fun cacheHistoryItems(historyItems: List<com.android.purebilibili.data.model.response.HistoryItem>) {
        historyItems.forEach { item ->
            _historyItemsMap[resolveHistoryLookupKey(item)] = item
            _historyItemsByRenderKey[resolveHistoryRenderKey(item)] = item
        }
    }

    private suspend fun deleteHistoryItemWithRetry(
        kid: String,
        csrf: String,
        isSessionCurrent: () -> Boolean,
    ): Result<Unit> {
        repeat(HISTORY_DELETE_MAX_ATTEMPTS) { attempt ->
            if (!isSessionCurrent()) throw CancellationException("账号已切换")
            val result = com.android.purebilibili.data.repository.HistoryRepository.deleteHistoryItem(
                kid = kid,
                csrf = csrf
            )
            if (result.isSuccess) return Result.success(Unit)
            if (attempt >= HISTORY_DELETE_MAX_ATTEMPTS - 1) {
                return result
            }
            val backoffMs = HISTORY_DELETE_RETRY_BASE_DELAY_MS * (attempt + 1)
            kotlinx.coroutines.delay(backoffMs)
        }
        return Result.failure(Exception("删除历史失败"))
    }

    companion object {
        private const val HISTORY_DELETE_MAX_ATTEMPTS = 3
        private const val HISTORY_DELETE_RETRY_BASE_DELAY_MS = 300L
    }

    init {
        observeHistoryRefresh()
    }

    private fun observeHistoryRefresh() {
        viewModelScope.launch {
            HistoryRefreshBus.changes.collect {
                // Server ordering is loaded on the next browsing session or explicit refresh.
                // A heartbeat or return from playback only updates existing progress in place.
                if (isPageActive) updateCachedHistoryProgress()
            }
        }
    }
}

// --- 收藏 ViewModel (支持分页加载所有收藏夹) ---
class FavoriteViewModel(application: Application) : BaseListViewModel(application, "我的收藏") {
    private var startupLoadsActivated = false
    private var isPageActive = false

    fun setPageActive(active: Boolean) {
        isPageActive = active
        if (!active || startupLoadsActivated) return
        startupLoadsActivated = true
        loadData()
    }

    override fun onAccountChanged() {
        startupLoadsActivated = false
        currentUserMid = 0L
        currentFolderIndex = 0
        subscribedPagingJob?.cancel()
        subscribedPagingSession = null
        allFolderIds = emptyList()
        _folders.value = emptyList()
        _subscribedFolders.value = emptyList()
        _subscribedFolderActions.value = emptyMap()
        _folderStates.values.forEach { it.value = ListUiState(isLoading = true) }
        _folderStates.clear()
        cancelFolderPaging()
        folderRequestGenerations.clear()
        folderLoadedOrders.clear()
        coverResolvedMediaIds.clear()
        _selectedFolderIndex.value = 0
        _subscribedFolderProgressState.value = SubscribedFolderProgressState()
        _isLoadingMoreState.value = false
        _hasMoreState.value = true
        _isFavoriteManagingState.value = false
        searchGeneration += 1
        _searchUiState.value = ListUiState(title = "收藏搜索")
        if (isPageActive) setPageActive(true)
    }

    private val _searchUiState = MutableStateFlow(ListUiState(title = "收藏搜索"))
    val searchUiState = _searchUiState.asStateFlow()
    private var searchGeneration = 0L
    
    // 分页状态
    private var allFolderIds: List<Long> = emptyList()  //  自建收藏夹 media_id
    private var currentFolderIndex = 0  //  当前正在加载的收藏夹索引
    private var currentUserMid: Long = 0L
    private var subscribedPagingSession: ForwardPagingSession<Int, SubscribedFolderPageChunk>? = null
    private var subscribedPagingJob: Job? = null
    // collected/list 允许较大 ps；示例与常见客户端用 20，避免过大页触发风控
    private val subscribedPageSize = 20
    
    //  暴露加载更多状态
    private val _isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMoreState = _isLoadingMoreState.asStateFlow()
    
    private val _hasMoreState = MutableStateFlow(true)
    val hasMoreState = _hasMoreState.asStateFlow()
    
    // 📁 [新增] 自建收藏夹列表
    private val _folders = MutableStateFlow<List<com.android.purebilibili.data.model.response.FavFolder>>(emptyList())
    val folders = _folders.asStateFlow()

    init {
        viewModelScope.launch {
            com.android.purebilibili.core.refresh.FavoriteFolderUpdateBus.changes.collect { update ->
                val accountMid = TokenManager.midCache
                if (accountMid == null || accountMid <= 0L || accountMid != update.ownerMid) return@collect
                _folders.update { currentFolders ->
                    val folderIndex = currentFolders.indexOfFirst { folder ->
                        resolveFavoriteFolderMediaId(folder) == update.mediaId
                    }
                    if (folderIndex < 0) {
                        currentFolders
                    } else {
                        val updatedFolders = currentFolders.toMutableList()
                        updatedFolders[folderIndex] = updatedFolders[folderIndex].copy(
                            title = update.info.title,
                            intro = update.info.intro,
                            cover = update.info.cover,
                            attr = update.info.attr,
                        )
                        updatedFolders
                    }
                }
            }
        }
    }

    // 📁 [新增] 订阅收藏夹列表
    private val _subscribedFolders = MutableStateFlow<List<com.android.purebilibili.data.model.response.FavFolder>>(emptyList())
    val subscribedFolders = _subscribedFolders.asStateFlow()

    data class SubscribedFolderProgressState(
        val loadedCount: Int = 0,
        val totalCount: Int = 0,
        val currentPage: Int = 1,
        val lastAddedCount: Int = 0,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
    )

    private val _subscribedFolderProgressState = MutableStateFlow(SubscribedFolderProgressState())
    val subscribedFolderProgressState = _subscribedFolderProgressState.asStateFlow()

    data class SubscribedFolderActionState(
        val isSubscribed: Boolean = true,
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _subscribedFolderActions =
        MutableStateFlow<Map<String, SubscribedFolderActionState>>(emptyMap())
    val subscribedFolderActions = _subscribedFolderActions.asStateFlow()
    
    // 📁 [新增] 当前选中的收藏夹索引
    private val _selectedFolderIndex = MutableStateFlow(0)
    val selectedFolderIndex = _selectedFolderIndex.asStateFlow()

    private val _favoriteOrderState = MutableStateFlow(FavoriteResourceOrder.FAVORITE_TIME)
    internal val favoriteOrderState = _favoriteOrderState.asStateFlow()

    private val _isFavoriteManagingState = MutableStateFlow(false)
    internal val isFavoriteManagingState = _isFavoriteManagingState.asStateFlow()
    
    /**
     * 📁 [新增] 切换收藏夹
     */
    // 📁 [新增] 多文件夹状态管理
    private val _folderStates = mutableMapOf<Int, MutableStateFlow<ListUiState>>()
    // 收藏夹内容各自拥有分页会话，排序与目录变更时取消旧会话。
    private data class FolderPage(val items: List<VideoItem>)
    private val folderPagingSessions = mutableMapOf<Int, ForwardPagingSession<Int, FolderPage>>()
    private val folderPagingJobs = mutableMapOf<Int, Job>()
    private val folderPagingStates = mutableMapOf<Int, CommonListPaginationSnapshot>()
    private val completeFolderLoads = mutableSetOf<Int>()
    private val removedFolderItems = mutableMapOf<Long, MutableSet<Long>>()
    private val folderCatalogMutex = Mutex()
    private val folderContentSemaphore = Semaphore(1)
    private val folderRequestGenerations = mutableMapOf<Int, Long>()
    private val folderLoadedOrders = mutableMapOf<Int, String>()

    // 🖼 收藏夹预览封面：串行化补全流程，并用会话级集合避免重复请求同一个收藏夹
    private val coverPrefetchMutex = Mutex()
    private val coverResolvedMediaIds: MutableSet<Long> =
        java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    
    /**
     * 获取指定文件夹的 UI 状态
     */
    fun getFolderUiState(index: Int): kotlinx.coroutines.flow.StateFlow<ListUiState> {
        return _folderStates.getOrPut(index) {
             // 默认状态: isLoading = true to show skeleton initially
            MutableStateFlow(ListUiState(title = "文件夹$index", isLoading = true))
        }.asStateFlow()
    }

    /**
     * 📁 切换收藏夹 (仅更新索引，不再强制刷新)
     */
    fun switchFolder(index: Int) {
        if (index < 0 || index >= allFolderIds.size) return
        currentFolderIndex = index
        _selectedFolderIndex.value = index
        publishSelectedFolderPagingState()
    }

    private fun publishSelectedFolderPagingState() {
        val state = folderPagingStates[_selectedFolderIndex.value]
        _hasMoreState.value = state?.hasMore ?: true
        _isLoadingMoreState.value = state?.isLoadingMore ?: false
    }

    private fun cancelFolderPaging() {
        folderRequestGenerations.keys.toList().forEach(::invalidateFolderRequest)
        folderPagingJobs.values.forEach { it.cancel() }
        folderPagingJobs.clear()
        folderPagingSessions.clear()
        folderPagingStates.clear()
        completeFolderLoads.clear()
        removedFolderItems.clear()
    }

    fun searchVideos(
        keyword: String,
        scope: com.android.purebilibili.data.model.response.FavoriteSearchScope,
    ) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            searchGeneration += 1
            _searchUiState.value = ListUiState(title = "收藏搜索")
            return
        }
        val generation = ++searchGeneration
        _searchUiState.update { it.copy(isLoading = true, error = null) }
        accountRequests.launch {
            try {
                fetchFolders()
                val mediaId = allFolderIds.getOrNull(_selectedFolderIndex.value)
                    ?: allFolderIds.firstOrNull()
                    ?: error("没有可搜索的收藏夹")
                val result = com.android.purebilibili.data.repository.FavoriteRepository.getFavoriteList(
                    mediaId = mediaId,
                    pn = 1,
                    ps = 20,
                    keyword = normalized,
                    order = _favoriteOrderState.value.apiValue,
                    type = resolveFavoriteSearchApiType(scope),
                )
                if (generation != searchGeneration) return@launch
                _searchUiState.value = result.fold(
                    onSuccess = { data ->
                        ListUiState(
                            title = "收藏搜索",
                            items = data.medias.orEmpty().map { it.toVideoItem() },
                        )
                    },
                    onFailure = { error ->
                        ListUiState(title = "收藏搜索", error = error.message ?: "搜索失败")
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == searchGeneration) {
                    _searchUiState.value = ListUiState(
                        title = "收藏搜索",
                        error = e.message ?: "搜索失败",
                    )
                }
            }
        }
    }
    
    /**
     * 加载指定文件夹的数据
     */
    fun loadFolder(index: Int) {
        if (index < 0) return
        val order = _favoriteOrderState.value.apiValue
        if (folderLoadedOrders[index] == order &&
            (folderPagingJobs[index]?.isActive == true || index in completeFolderLoads)
        ) return
        folderPagingJobs.remove(index)?.cancel()
        val generation = nextFolderRequestGeneration(index)
        val identity = accountRequests.identity
        val state = _folderStates.getOrPut(index) { MutableStateFlow(ListUiState(isLoading = true)) }
        state.update { it.copy(isLoading = true, error = null, loadMoreError = null) }
        folderLoadedOrders[index] = order
        folderPagingJobs[index] = accountRequests.launch {
            try {
                fetchFolders()
                val mediaId = allFolderIds.getOrNull(index) ?: error("没有找到收藏夹")
                val folder = _folders.value.getOrNull(index)
                lateinit var session: ForwardPagingSession<Int, FolderPage>
                session = ForwardPagingSession(
                    config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
                    sourceFactory = {
                        val seen = mutableSetOf<Long>()
                        ForwardPagingSource<Int, FolderPage>(1) { number ->
                            val result = folderContentSemaphore.withPermit {
                                requestFavoriteFolderWithRetry {
                                    com.android.purebilibili.data.repository.FavoriteRepository.getFavoriteList(
                                        mediaId = mediaId, pn = number, ps = resolveFavoriteFolderContentPageSize(), order = order,
                                    ).mapCatching { data ->
                                        val items = resolveFavoriteFolderItems(
                                            expectedItemCount = if (number == 1) folder?.media_count ?: 0 else 0,
                                            resources = data.medias,
                                        ).getOrThrow()
                                        items to data.has_more
                                    }
                                }
                            }.getOrThrow()
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val fresh = result.first.filter { seen.add(it.id) }
                            ForwardPage(listOf(FolderPage(fresh)), (number + 1).takeIf { result.second && fresh.isNotEmpty() })
                        }
                    },
                    onChanged = { pages, states ->
                        if (folderPagingSessions[index] === session && accountRequests.isCurrent(identity) &&
                            isCurrentFolderRequest(index, generation, mediaId, order) && states != null
                        ) {
                            val refreshError = (states.refresh as? LoadState.Error)?.error
                            val appendError = (states.append as? LoadState.Error)?.error
                            val refreshReady = states.refresh is LoadState.NotLoading && pages.isNotEmpty()
                            val refreshedItems = if (refreshReady) pages.flatMap { it.items }
                                .filter { it.id !in removedFolderItems[mediaId].orEmpty() } else null
                            state.update { it.copy(
                                title = folder?.title ?: it.title,
                                items = refreshedItems ?: it.items,
                                isLoading = states.refresh is LoadState.Loading,
                                error = refreshError?.let(::resolveFavoriteErrorMessage),
                                loadMoreError = appendError?.let(::resolveFavoriteErrorMessage),
                                canRemoveItems = folder?.source != com.android.purebilibili.data.model.response.FavFolderSource.SUBSCRIBED,
                            ) }
                            folderPagingStates[index] = CommonListPaginationSnapshot(
                                hasMore = !states.append.endOfPaginationReached,
                                isLoadingMore = states.append is LoadState.Loading,
                            )
                            if (_selectedFolderIndex.value == index) publishSelectedFolderPagingState()
                        }
                    },
                )
                if (!isCurrentFolderRequest(index, generation, mediaId, order)) return@launch
                folderPagingSessions[index] = session
                session.run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (accountRequests.isCurrent(identity) && folderRequestGenerations[index] == generation) {
                    state.update { it.copy(isLoading = false, error = resolveFavoriteErrorMessage(failure)) }
                }
            }
        }
    }

    private suspend fun fetchFolders() {
        folderCatalogMutex.withLock {
            val mid = ensureCurrentUserMid()
            check(mid > 0L) { "请先登录" }

            if (_folders.value.isEmpty()) {
                val ownedFolders = requestFavoriteFolderWithRetry {
                    com.android.purebilibili.data.repository.FavoriteRepository.getFavFolders(mid)
                }
                    .getOrThrow()
                _folders.value = ownedFolders
                allFolderIds = ownedFolders.map(::resolveFavoriteFolderMediaId)
                // 收藏夹列表刷新后封面会重新变为空，重置会话级去重集合并先用缓存回填。
                coverResolvedMediaIds.clear()
                applyCachedFavoriteFolderCovers(ownedFolders)
                // 首屏先主动补齐一小批，避免卡片进入视口后才开始请求封面。
                ensureFavoriteFolderCovers(
                    ownedFolders.take(FAVORITE_FOLDER_COVER_INITIAL_WINDOW)
                )
            }
        }
    }

    /**
     * 进入收藏页时先用本地缓存补齐封面（不发起网络请求）。
     *
     * 未命中缓存的收藏夹保持空封面，交给列表滚动到可见前沿时按需补全，
     * 避免打开页面就一次性发起大量 fav/resource/list 请求。
     */
    private suspend fun applyCachedFavoriteFolderCovers(folders: List<FavFolder>) {
        if (folders.isEmpty()) return
        val lookups = folders.mapNotNull { folder ->
            val mediaId = resolveFavoriteFolderMediaId(folder)
            if (mediaId <= 0L || folder.cover.isNotBlank() || folder.media_count <= 0) {
                null
            } else {
                FavoriteFolderCoverCache.Lookup(mediaId = mediaId, mediaCount = folder.media_count)
            }
        }
        if (lookups.isEmpty()) return
        val covers = FavoriteFolderCoverCache.lookupAll(getApplication(), lookups)
        if (covers.isEmpty()) return
        coverResolvedMediaIds.addAll(covers.keys)
        applyFavoriteFolderCovers(covers)
    }

    /**
     * 按需补齐收藏夹封面：命中缓存直接填充，未命中的才发起 fav/resource/list 请求。
     *
     * 由收藏夹列表滚动到可见前沿时调用，[candidates] 为当前需要保证封面的收藏夹前缀。
     * 缓存以 media_count 失效，因此命中缓存即代表封面仍是最新收藏的视频封面。
     */
    fun ensureFavoriteFolderCovers(candidates: List<FavFolder>) {
        if (candidates.isEmpty()) return
        accountRequests.launch {
            coverPrefetchMutex.withLock {
                val context = getApplication<Application>()
                val pending = resolveFavoriteFolderCoverFetchTargets(
                    folders = candidates,
                    alreadyResolved = coverResolvedMediaIds,
                )
                if (pending.isEmpty()) return@withLock

                // 先吃缓存：命中的不占请求额度，也标记为已解析避免下次重复查询。
                val cached = FavoriteFolderCoverCache.lookupAll(
                    context = context,
                    lookups = pending.map { target ->
                        FavoriteFolderCoverCache.Lookup(
                            mediaId = target.mediaId,
                            mediaCount = target.mediaCount,
                        )
                    },
                )
                if (cached.isNotEmpty()) {
                    coverResolvedMediaIds.addAll(cached.keys)
                    applyFavoriteFolderCovers(cached)
                }

                val remaining = pending.filter { it.mediaId !in cached }
                if (remaining.isEmpty()) return@withLock
                // 先登记再请求，避免滚动过程中同一收藏夹被重复发起。
                coverResolvedMediaIds.addAll(remaining.map { it.mediaId })
                fetchFavoriteFolderCovers(context, remaining)
            }
        }
    }

    private suspend fun fetchFavoriteFolderCovers(
        context: Context,
        targets: List<FavoriteFolderCoverTarget>,
    ) {
        if (targets.isEmpty()) return
        val semaphore = Semaphore(FAVORITE_FOLDER_COVER_CONCURRENCY)
        val resolved = supervisorScope {
            targets.map { target ->
                async {
                    semaphore.withPermit {
                        val cover = try {
                            com.android.purebilibili.data.repository.FavoriteRepository.getFavoriteList(
                                mediaId = target.mediaId,
                                pn = 1,
                                ps = 1,
                            ).getOrNull()?.medias
                                ?.firstOrNull { it.cover.isNotBlank() }
                                ?.cover
                                .orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ""
                        }
                        FavoriteFolderCoverCache.Resolved(
                            mediaId = target.mediaId,
                            mediaCount = target.mediaCount,
                            cover = cover,
                        )
                    }
                }
            }.awaitAll()
        }.filter { it.cover.isNotBlank() }
        if (resolved.isEmpty()) return

        FavoriteFolderCoverCache.store(context, resolved)
        applyFavoriteFolderCovers(resolved.associate { it.mediaId to it.cover })
    }

    /** 把封面回填到收藏夹列表，只补空白封面，避免覆盖接口已返回的值。 */
    private suspend fun applyFavoriteFolderCovers(coversByMediaId: Map<Long, String>) {
        if (coversByMediaId.isEmpty()) return
        // 用 CAS 更新，避免多个补封面批次并发回填时互相覆盖。
        _folders.update { folders ->
            folders.map { folder ->
                if (folder.cover.isNotBlank()) return@map folder
                val cover = coversByMediaId[resolveFavoriteFolderMediaId(folder)]?.trim().orEmpty()
                if (cover.isBlank()) folder else folder.copy(cover = cover)
            }
        }
    }

    fun ensureSubscribedFoldersLoaded() {
        launchInitialSubscribedFoldersLoad()
    }

    private fun launchInitialSubscribedFoldersLoad() {
        if (subscribedPagingSession != null || _subscribedFolderProgressState.value.error != null) return
        startSubscribedFolderPaging(preserveRows = false)
    }

    private suspend fun ensureCurrentUserMid(): Long {
        val requestedIdentity = accountRequests.identity
        if (!accountRequests.isCurrent(requestedIdentity)) throw CancellationException("账号已切换")
        requestedIdentity.mid?.let {
            currentUserMid = it
            return it
        }
        if (currentUserMid > 0L) return currentUserMid
        val navResp = NetworkModule.api.getNavInfo()
        if (!accountRequests.isCurrent(requestedIdentity)) throw CancellationException("账号已切换")
        currentUserMid = navResp.data?.mid ?: 0L
        return currentUserMid
    }

    private fun startSubscribedFolderPaging(preserveRows: Boolean) {
        subscribedPagingJob?.cancel()
        val identity = accountRequests.identity
        val retained = if (preserveRows) _subscribedFolders.value else emptyList()
        _subscribedFolderProgressState.update { it.copy(isLoadingMore = true, error = null) }
        lateinit var session: ForwardPagingSession<Int, SubscribedFolderPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                subscribedFolderPagingSource(subscribedPageSize) { number ->
                    val mid = ensureCurrentUserMid()
                    check(mid > 0L) { "请先登录" }
                    requestFavoriteFolderWithRetry {
                        com.android.purebilibili.data.repository.FavoriteRepository.getCollectedFavFolders(
                            mid = mid, pn = number, ps = subscribedPageSize, platform = "web",
                        )
                    }.getOrThrow()
                }
            },
            onChanged = { pages, states ->
                if (subscribedPagingSession === session && accountRequests.isCurrent(identity) && states != null) {
                    val failure = (states.refresh as? LoadState.Error)?.error ?: (states.append as? LoadState.Error)?.error
                    val refreshedFolders = if (states.refresh is LoadState.NotLoading && pages.isNotEmpty()) {
                        (retained + pages.flatMap { it.folders })
                            .distinctBy(::resolveSubscribedFavoriteFolderKey)
                    } else {
                        null
                    }
                    if (refreshedFolders != null) {
                        _subscribedFolders.value = refreshedFolders
                    }
                    val loadedCount = (refreshedFolders ?: _subscribedFolders.value).size
                    val last = pages.lastOrNull()
                    _subscribedFolderProgressState.update { current ->
                        SubscribedFolderProgressState(
                            loadedCount = loadedCount,
                            totalCount = last?.totalCount ?: current.totalCount,
                            currentPage = last?.number ?: 1,
                            lastAddedCount = last?.folders?.size ?: 0,
                            hasMore = !states.append.endOfPaginationReached,
                            isLoadingMore = states.refresh is LoadState.Loading || states.append is LoadState.Loading,
                            error = failure?.let(::resolveFavoriteErrorMessage),
                        )
                    }
                }
            },
        )
        subscribedPagingSession = session
        subscribedPagingJob = accountRequests.launch { session.run() }
    }

    fun loadMoreSubscribedFolders() {
        if (!accountRequests.isCurrent() || _subscribedFolderProgressState.value.error != null) return
        if (subscribedPagingSession == null) launchInitialSubscribedFoldersLoad() else subscribedPagingSession?.append()
    }

    fun retrySubscribedFolders() {
        if (!accountRequests.isCurrent()) return
        if (subscribedPagingSession == null) startSubscribedFolderPaging(preserveRows = true) else subscribedPagingSession?.retry()
    }

    fun toggleSubscribedFolderSubscription(folder: FavFolder) {
        val requestedIdentity = accountRequests.identity
        if (!accountRequests.isCurrent(requestedIdentity)) return
        val key = resolveSubscribedFavoriteFolderKey(folder)
        // 出现在 collected/list 就是已订阅事实，不能用不可靠的 fav_state 推断。
        val current = _subscribedFolderActions.value[key] ?: SubscribedFolderActionState()
        if (current.isLoading) return
        val subscribe = !current.isSubscribed
        _subscribedFolderActions.update {
            it + (key to current.copy(isLoading = true, error = null))
        }
        accountRequests.launch {
            try {
                val subscribed = if (folder.type == 21) {
                    com.android.purebilibili.data.repository.ActionRepository
                        .setCollectionSubscription(seasonId = folder.id, subscribe = subscribe)
                } else {
                    com.android.purebilibili.data.repository.FavoriteRepository
                        .setFolderSubscription(
                            mediaId = resolveFavoriteFolderMediaId(folder),
                            subscribe = subscribe,
                        )
                }.getOrThrow()
                if (!accountRequests.isCurrent(requestedIdentity)) return@launch
                applySubscribedFolderSubscription(folder, subscribed, isLoading = true)
                FavoriteSubscriptionBus.notifyChanged(requestedIdentity, folder, subscribed)
                if (!accountRequests.isCurrent(requestedIdentity)) return@launch
                if (folder.type == 21) {
                    SettingsManager.setCollectionSubscription(
                        getApplication(), folder.id, subscribed
                    )
                }
                if (!accountRequests.isCurrent(requestedIdentity)) return@launch
                _subscribedFolderActions.update {
                    it + (key to it.getValue(key).copy(isLoading = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!accountRequests.isCurrent(requestedIdentity)) return@launch
                _subscribedFolderActions.update {
                    val state = it[key] ?: current
                    it + (key to state.copy(
                        isLoading = false,
                        error = e.message?.takeIf(String::isNotBlank) ?: "订阅操作失败，请重试",
                    ))
                }
            }
        }
    }

    private fun applySubscribedFolderSubscription(folder: FavFolder, subscribed: Boolean, isLoading: Boolean) {
        val key = resolveSubscribedFavoriteFolderKey(folder)
        _subscribedFolderActions.update {
            it + (key to SubscribedFolderActionState(isSubscribed = subscribed, isLoading = isLoading))
        }
        _subscribedFolders.update { current ->
            if (current.none { resolveSubscribedFavoriteFolderKey(it) == key }) current + folder else current
        }
        // 订阅改变服务端页码偏移；保留撤销行，用新的 Paging 源从第一页重新校准。
        startSubscribedFolderPaging(preserveRows = true)
    }

    fun loadMoreForFolder(index: Int, retry: Boolean = false) {
        if (!accountRequests.isCurrent() || index !in allFolderIds.indices) return
        val session = folderPagingSessions[index] ?: return
        if (retry) session.retry() else session.append()
    }

    fun loadAllForPlayback(index: Int, onLoaded: (List<VideoItem>) -> Unit) {
        if (index < 0 || index >= allFolderIds.size) return

        accountRequests.launch {
            val mediaId = allFolderIds[index]
            val requestedOrder = _favoriteOrderState.value.apiValue
            val requestGeneration = folderRequestGenerations[index] ?: 0L
            val items = mutableListOf<VideoItem>()
            var page = 1
            var hasMore = true

            try {
                folderContentSemaphore.withPermit {
                    while (hasMore) {
                        val data = requestFavoriteFolderWithRetry {
                            com.android.purebilibili.data.repository.FavoriteRepository.getFavoriteList(
                                mediaId = mediaId,
                                pn = page,
                                ps = resolveFavoriteFolderContentPageSize(),
                                order = requestedOrder
                            )
                        }.getOrThrow()
                        items += data.medias.orEmpty().map { it.toVideoItem() }
                        hasMore = shouldLoadNextFavoritePlaybackPage(
                            hasMore = data.has_more,
                            pageItemCount = data.medias.orEmpty().size
                        )
                        page += 1
                    }
                }
                if (isCurrentFolderRequest(index, requestGeneration, mediaId, requestedOrder)) {
                    folderPagingJobs.remove(index)?.cancel()
                    folderPagingSessions.remove(index)
                    completeFolderLoads.add(index)
                    folderPagingStates[index] = CommonListPaginationSnapshot(hasMore = false, isLoadingMore = false)
                    if (_selectedFolderIndex.value == index) publishSelectedFolderPagingState()
                    val uniqueItems = items.distinctBy { it.id }.filter { it.id !in removedFolderItems[mediaId].orEmpty() }
                    val stateFlow = _folderStates[index] ?: return@launch
                    stateFlow.update {
                        it.copy(items = uniqueItems, isLoading = false, error = null, loadMoreError = null)
                    }
                    onLoaded(uniqueItems)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentFolderRequest(index, requestGeneration, mediaId, requestedOrder)) return@launch
                _folderStates[index]?.update {
                    it.copy(
                        error = e.message?.takeIf(String::isNotBlank) ?: "加载收藏夹失败"
                    )
                }
            }
        }
    }

    // 保持 BaseListViewModel 兼容性 (Redirect to current folder)
    override suspend fun fetchItems(): List<VideoItem> {
        // Folder contents have independent observable states; the base list initializes the catalog.
        try {
            launchInitialSubscribedFoldersLoad()
            fetchFolders()
            if (allFolderIds.isNotEmpty()) {
                 loadFolder(0)
                 // Sync base UI state with first folder? 
                 // Actually CommonListScreen should observe getFolderUiState if it's FavoriteVM
                 return _folderStates[0]?.value?.items ?: emptyList()
            }
            return emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(resolveFavoriteErrorMessage(e), e)
        }
    }
    
    //  加载更多
    //  加载更多 (重定向到当前文件夹)
    fun loadMore() {
        loadMoreForFolder(currentFolderIndex)
    }


    internal fun cleanInvalidFavoriteFolders(mediaIds: Set<Long>, onComplete: () -> Unit) {
        manageFavoriteFolders(mediaIds, delete = false, onComplete = onComplete)
    }

    internal fun deleteFavoriteFolders(mediaIds: Set<Long>, onComplete: () -> Unit) {
        manageFavoriteFolders(mediaIds, delete = true, onComplete = onComplete)
    }

    private fun manageFavoriteFolders(
        mediaIds: Set<Long>,
        delete: Boolean,
        onComplete: () -> Unit,
    ) {
        if (_isFavoriteManagingState.value || mediaIds.isEmpty()) return
        val selected = _folders.value.filter { resolveFavoriteFolderMediaId(it) in mediaIds }
        if (selected.size != mediaIds.size) return
        val permissions = favoriteFolderBatchPermissions(
            selected,
            _folders.value.firstOrNull()?.let(::resolveFavoriteFolderMediaId),
            com.android.purebilibili.core.store.TokenManager.midCache,
        )
        if (if (delete) !permissions.canDelete else !permissions.canClean) return
        _isFavoriteManagingState.value = true
        accountRequests.launch {
            var succeeded = 0
            val failures = mutableListOf<String>()
            try {
                for (folder in selected) {
                    val mediaId = resolveFavoriteFolderMediaId(folder)
                    // Recheck server ownership immediately before each destructive request.
                    val result = try {
                        val info = com.android.purebilibili.data.repository.FavoriteRepository
                            .getFolderInfo(mediaId).getOrThrow()
                        val owner = info.upper?.mid?.takeIf { it > 0L } ?: info.mid
                        check(owner > 0L && owner == com.android.purebilibili.core.store.TokenManager.midCache) {
                            "无权管理此收藏夹"
                        }
                        if (delete) {
                            com.android.purebilibili.data.repository.FavoriteRepository.deleteFolder(mediaId)
                        } else {
                            com.android.purebilibili.data.repository.FavoriteRepository
                                .cleanInvalidResources(mediaId).map { Unit }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    result.fold(
                        onSuccess = { succeeded++ },
                        onFailure = { failures += "${folder.title}：${it.message ?: "操作失败"}" },
                    )
                }
                val operation = if (delete) "删除" else "清理"
                finishFolderMutation(
                    if (succeeded > 0) Result.success(Unit)
                    else Result.failure(IllegalStateException(failures.joinToString("\n"))),
                    "已${operation} $succeeded 个收藏夹" +
                        if (failures.isEmpty()) "" else "\n${failures.joinToString("\n")}",
                )
                onComplete()
            } finally {
                _isFavoriteManagingState.value = false
            }
        }
    }

    internal fun createFavoriteFolder(
        title: String,
        intro: String,
        isPrivate: Boolean,
    ) {
        if (_isFavoriteManagingState.value || title.isBlank()) return
        _isFavoriteManagingState.value = true
        accountRequests.launch {
            try {
                val result = com.android.purebilibili.data.repository.ActionRepository.createFavFolder(
                    title = title.trim(),
                    intro = intro.trim(),
                    isPrivate = isPrivate,
                )
                finishFolderMutation(result.map { Unit }, "已创建收藏夹")
            } finally {
                _isFavoriteManagingState.value = false
            }
        }
    }



    private suspend fun finishFolderMutation(result: Result<Unit>, successMessage: String) {
        android.widget.Toast.makeText(
            getApplication(),
            result.fold(
                onSuccess = { successMessage },
                onFailure = { it.message ?: "操作失败" },
            ),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        try {
            if (result.isSuccess) {
                _folders.value = emptyList()
                allFolderIds = emptyList()
                _folderStates.clear()
                cancelFolderPaging()
                folderLoadedOrders.clear()
                fetchFolders()
                currentFolderIndex = currentFolderIndex.coerceIn(0, (allFolderIds.size - 1).coerceAtLeast(0))
                _selectedFolderIndex.value = currentFolderIndex
                if (allFolderIds.isNotEmpty()) loadFolder(currentFolderIndex)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                getApplication(),
                e.message ?: "刷新收藏夹失败",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } finally {
            _isFavoriteManagingState.value = false
        }
    }

    internal fun deleteSelectedFavoriteResources(resourceIds: Set<Long>) {
        manageSelectedFavoriteResources(resourceIds = resourceIds, targetMediaId = null, copy = false)
    }

    internal fun copyOrMoveSelectedFavoriteResources(
        resourceIds: Set<Long>,
        targetMediaId: Long,
        copy: Boolean,
    ) {
        manageSelectedFavoriteResources(
            resourceIds = resourceIds,
            targetMediaId = targetMediaId,
            copy = copy,
        )
    }

    private fun manageSelectedFavoriteResources(
        resourceIds: Set<Long>,
        targetMediaId: Long?,
        copy: Boolean,
    ) {
        if (_isFavoriteManagingState.value || resourceIds.isEmpty()) return
        val folderIndex = _selectedFolderIndex.value
        val sourceMediaId = allFolderIds.getOrNull(folderIndex) ?: return
        _isFavoriteManagingState.value = true
        accountRequests.launch {
            try {
                val result = if (targetMediaId == null) {
                    com.android.purebilibili.data.repository.FavoriteRepository.removeResources(
                        mediaId = sourceMediaId,
                        resourceIds = resourceIds,
                    )
                } else {
                    com.android.purebilibili.data.repository.FavoriteRepository.copyOrMoveResources(
                        sourceMediaId = sourceMediaId,
                        targetMediaId = targetMediaId,
                        mid = currentUserMid,
                        resourceIds = resourceIds,
                        copy = copy,
                    )
                }
                android.widget.Toast.makeText(
                    getApplication(),
                    result.fold(
                        onSuccess = {
                            when {
                                targetMediaId == null -> "已删除 ${resourceIds.size} 个内容"
                                copy -> "已复制 ${resourceIds.size} 个内容"
                                else -> "已移动 ${resourceIds.size} 个内容"
                            }
                        },
                        onFailure = { it.message ?: "操作失败" },
                    ),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                if (result.isSuccess && (targetMediaId == null || !copy)) {
                    reloadFavoriteFolder(folderIndex)
                }
            } finally {
                _isFavoriteManagingState.value = false
            }
        }
    }

    private fun reloadFavoriteFolder(index: Int) {
        if (index < 0) return
        folderPagingJobs.remove(index)?.cancel()
        folderPagingSessions.remove(index)
        folderPagingStates.remove(index)
        completeFolderLoads.remove(index)
        allFolderIds.getOrNull(index)?.let { removedFolderItems.remove(it) }
        folderLoadedOrders.remove(index)
        invalidateFolderRequest(index)
        val stateFlow = _folderStates.getOrPut(index) { MutableStateFlow(ListUiState(isLoading = true)) }
        stateFlow.update {
            it.copy(
                isLoading = true,
                error = null
            )
        }
        loadFolder(index)
    }

    fun retryFolder(index: Int) {
        if (folderPagingSessions[index] != null) loadMoreForFolder(index, retry = true)
        else reloadFavoriteFolder(index)
    }

    fun retrySelectedFolder() {
        retryFolder(_selectedFolderIndex.value)
    }

    private fun nextFolderRequestGeneration(index: Int): Long {
        val next = (folderRequestGenerations[index] ?: 0L) + 1L
        folderRequestGenerations[index] = next
        return next
    }

    private fun invalidateFolderRequest(index: Int) {
        nextFolderRequestGeneration(index)
    }

    private fun isCurrentFolderRequest(
        index: Int,
        requestGeneration: Long,
        mediaId: Long,
        order: String
    ): Boolean {
        return shouldApplyFavoriteFolderResult(
            requestGeneration = requestGeneration,
            currentGeneration = folderRequestGenerations[index] ?: 0L,
            requestedMediaId = mediaId,
            currentMediaId = allFolderIds.getOrNull(index),
            requestedOrder = order,
            currentOrder = _favoriteOrderState.value.apiValue
        )
    }

    //  [新增] 移除收藏
    fun removeVideo(video: VideoItem) {
        // aid 作为 resourceId
        val resourceId = video.aid 
        if (resourceId == 0L || allFolderIds.isEmpty()) return

        val folderIndex = _selectedFolderIndex.value
        if (folderIndex < 0 || folderIndex >= allFolderIds.size) return
        currentFolderIndex = folderIndex

        val currentMediaId = allFolderIds[folderIndex]
        val stateFlow = _folderStates.getOrPut(folderIndex) {
            MutableStateFlow(ListUiState(isLoading = false))
        }
        
        accountRequests.launch {
            val originalState = stateFlow.value
            val removedIndex = originalState.items.indexOfFirst { it.id == video.id }
            val removedItem = originalState.items.getOrNull(removedIndex)
            if (removedItem == null) return@launch
            fun restoreFailedRemoval(message: String) {
                if (allFolderIds.getOrNull(folderIndex) != currentMediaId) return
                fun restore(items: List<VideoItem>): List<VideoItem> {
                    if (items.any { it.id == removedItem.id }) return items
                    return items.toMutableList().apply {
                        add(removedIndex.coerceIn(0, size), removedItem)
                    }
                }
                removedFolderItems[currentMediaId]?.remove(video.id)
                stateFlow.update { it.copy(items = restore(it.items)) }
                if (_selectedFolderIndex.value == folderIndex) {
                    _uiState.update { it.copy(items = restore(it.items)) }
                }
                _actionEvents.trySend(message)
            }
            try {
                removedFolderItems.getOrPut(currentMediaId) { mutableSetOf() }.add(video.id)
                stateFlow.update { current ->
                    current.copy(items = current.items.filter { it.id != video.id }, error = null)
                }
                if (_selectedFolderIndex.value == folderIndex && _uiState.value.items.isNotEmpty()) {
                    _uiState.update { current ->
                        current.copy(items = current.items.filter { it.id != video.id })
                    }
                }
                
                val result = com.android.purebilibili.data.repository.FavoriteRepository.removeResource(currentMediaId, resourceId)
                if (result.isFailure) {
                    val error = "取消收藏失败: ${result.exceptionOrNull()?.message}"
                    restoreFailedRemoval(error)
                } else {
                    // 删除改变服务端页码偏移，从第一页重新校准。
                    if (allFolderIds.getOrNull(folderIndex) == currentMediaId) reloadFavoriteFolder(folderIndex)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                 e.printStackTrace()
                 val message = e.message ?: "取消收藏失败"
                 restoreFailedRemoval(message)
            }
        }
    }

    init {
        viewModelScope.launch {
            FavoriteSubscriptionBus.changes.collect { change ->
                if (change.identity != accountRequests.identity || !accountRequests.isCurrent(change.identity)) return@collect
                val key = resolveSubscribedFavoriteFolderKey(change.folder)
                val current = _subscribedFolderActions.value[key]
                val hasRow = _subscribedFolders.value.any { resolveSubscribedFavoriteFolderKey(it) == key }
                if ((current?.isSubscribed ?: true) != change.subscribed || !hasRow) {
                    applySubscribedFolderSubscription(
                        change.folder,
                        change.subscribed,
                        isLoading = current?.isLoading == true,
                    )
                }
            }
        }
    }

}
