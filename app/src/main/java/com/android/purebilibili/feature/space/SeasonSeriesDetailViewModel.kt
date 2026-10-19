package com.android.purebilibili.feature.space

import android.app.Application
import android.net.Uri
import android.widget.Toast
import com.android.purebilibili.core.refresh.FavoriteFolderUpdateBus
import com.android.purebilibili.core.store.FavoriteFolderCoverCache
import com.android.purebilibili.data.model.response.FavoriteInfo
import com.android.purebilibili.data.model.response.FavoriteResourceData
import com.android.purebilibili.data.repository.CommentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.feature.list.BaseListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

class SeasonSeriesDetailViewModel(application: Application) : BaseListViewModel(application, "") {

    private val spaceApi = NetworkModule.spaceApi
    
    // Parameters
    private var type: String = "" // "season" or "series"
    private var id: Long = 0
    private var mid: Long = 0
    private var ownerName: String = ""
    private var pageTitle: String = ""

    val isFavoriteDetail: Boolean
        get() = type == SpaceCollectionDetailType.FAVORITE.raw ||
            type == SpaceCollectionDetailType.FAVORITE_SEASON.raw

    val canCopyFavoriteResources: Boolean
        get() = id > 0L && mid > 0L && canCopySpaceFavoriteResources(type, TokenManager.midCache)

    val canManageFavoriteResources: Boolean
        get() = id > 0L && canManageSpaceFavoriteResources(type, favoriteFolderOwnerMid, TokenManager.midCache)

    private val _favoriteFolderInfo = MutableStateFlow<FavoriteInfo?>(null)
    val favoriteFolderInfo = _favoriteFolderInfo.asStateFlow()
    private val _favoriteDetailSortOrder = MutableStateFlow(FavoriteDetailSortOrder.NEWEST_FIRST)
    internal val favoriteDetailSortOrder = _favoriteDetailSortOrder.asStateFlow()
    val canSortFavoriteFolder: Boolean get() = type == "favorite"
    private var favoriteFolderOwnerMid = 0L
    val canEditFavoriteFolder: Boolean
        get() = canManageSpaceFavoriteResources(type, favoriteFolderOwnerMid, TokenManager.midCache)
    val canShareFavoriteFolder: Boolean
        get() = type == "favorite" && id > 0L && (TokenManager.midCache ?: 0L) > 0L &&
            _favoriteFolderInfo.value?.let { (it.attr and 1) == 0 } == true

    private var refreshJob: Job? = null
    private var pagingJob: Job? = null
    private var requestGeneration = 0L

    override fun loadData(showLoading: Boolean) {
        val generation = ++requestGeneration
        refreshJob?.cancel()
        pagingJob?.cancel()
        isLoadingMore = false
        _isLoadingMoreState.value = false
        hasMore = false
        _hasMoreState.value = false
        _uiState.value = _uiState.value.copy(
            isLoading = showLoading || _uiState.value.items.isEmpty(),
            error = null,
            loadMoreError = null,
        )
        refreshJob = accountRequests.launch {
            try {
                val items = fetchItems()
                currentCoroutineContext().ensureActive()
                if (generation == requestGeneration) {
                    _uiState.value = _uiState.value.copy(isLoading = false, items = items)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == requestGeneration) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    internal fun changeFavoriteDetailSortOrder(order: FavoriteDetailSortOrder): Boolean {
        if (!canSortFavoriteFolder || order == _favoriteDetailSortOrder.value ||
            _uiState.value.isLoading || isLoadingMore || _isManagingState.value) return false
        _favoriteDetailSortOrder.value = order
        loadData()
        return true
    }

    // Pagination
    private var currentPage = 1
    private var hasMore = true
    private var isLoadingMore = false
    
    private val _isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMoreState = _isLoadingMoreState.asStateFlow()
    
    private val _hasMoreState = MutableStateFlow(true)
    val hasMoreState = _hasMoreState.asStateFlow()
    override fun onAccountChanged() {
        ++requestGeneration
        refreshJob?.cancel()
        pagingJob?.cancel()
        favoriteFolderOwnerMid = 0L
        _favoriteFolderInfo.value = null
        currentPage = 1
        hasMore = true
        isLoadingMore = false
        _isLoadingMoreState.value = false
        _hasMoreState.value = true
        _favoriteDetailProgressState.value = FavoriteDetailProgressState()
        _transferFolders.value = emptyList()
        _isManagingState.value = false
        _uiState.value = _uiState.value.copy(
            title = pageTitle,
            canRemoveItems = canManageFavoriteResources,
        )
        if (id > 0L) loadData()
    }


    data class FavoriteDetailProgressState(
        val loadedCount: Int = 0,
        val expectedCount: Int = 0,
        val currentPage: Int = 1,
        val lastAddedCount: Int = 0,
        val invalidCount: Int = 0,
        val hasMore: Boolean = false
    )

    private val _favoriteDetailProgressState = MutableStateFlow(FavoriteDetailProgressState())
    val favoriteDetailProgressState = _favoriteDetailProgressState.asStateFlow()

    fun init(type: String, id: Long, mid: Long, title: String, ownerName: String = "") {
        ++requestGeneration
        refreshJob?.cancel()
        pagingJob?.cancel()
        _favoriteFolderInfo.value = null
        favoriteFolderOwnerMid = 0L
        isLoadingMore = false
        _isLoadingMoreState.value = false
        val request = resolveSpaceCollectionDetailRequest(type, id, mid, title)
        if (request == null) {
            this.type = ""
            this.id = 0L
            this.mid = 0L
            this.ownerName = ownerName
            this.pageTitle = title
            hasMore = false
            _hasMoreState.value = false
            _uiState.value = _uiState.value.copy(
                title = title,
                items = emptyList(),
                isLoading = false,
                canRemoveItems = false,
                error = null,
                loadMoreError = null,
            )
            return
        }
        this.type = request.type.raw
        this.id = request.id
        this.mid = request.mid
        this.ownerName = ownerName
        this.pageTitle = request.title
        _favoriteDetailProgressState.value = FavoriteDetailProgressState()
        
        _uiState.value = _uiState.value.copy(
            title = request.title,
            canRemoveItems = canManageFavoriteResources,
        )
        loadData()
    }

    override suspend fun fetchItems(): List<VideoItem> {
        if (id == 0L) return emptyList()

        if (type == "season") {
            return fetchSeasonArchives()
        } else if (type == "series") {
            return fetchSeriesArchives()
        } else if (isFavoriteDetail) {
            return fetchFavoriteResources()
        }
        return emptyList()
    }

    private suspend fun fetchSeasonArchives(): List<VideoItem> {
        currentPage = 1
        try {
            val response = spaceApi.getSeasonArchives(mid, id, currentPage)
            currentCoroutineContext().ensureActive()
            if (response.code == 0) {
                val data = response.data
                if (data != null) {
                    val archives = data.archives
                    hasMore = archives.size >= 30 // Assumption based on page size
                     _hasMoreState.value = hasMore
                     
                    return archives.map { item -> mapSeasonArchiveToVideoItem(item, mid, ownerName) }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
        return emptyList()
    }

    private suspend fun fetchSeriesArchives(): List<VideoItem> {
        currentPage = 1
         try {
            val response = spaceApi.getSeriesArchives(mid, id, currentPage)
            currentCoroutineContext().ensureActive()
            if (response.code == 0) {
                val data = response.data
                if (data != null) {
                    val archives = data.archives
                    hasMore = archives.size >= 30
                    _hasMoreState.value = hasMore

                    return archives.map { item -> mapSeriesArchiveToVideoItem(item, mid, ownerName) }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
        return emptyList()
    }

    private suspend fun requestFavoritePage(page: Int): FavoriteResourceData {
        val response = FavoriteRepository.getFavoriteList(mediaId = id, pn = page).getOrThrow()
        currentCoroutineContext().ensureActive()
        check(response.medias?.isNotEmpty() == true || response.info?.media_count == 0) {
            "收藏夹分页数据为空，请重试"
        }
        return response
    }

    private fun updateFavoriteResourceInfo(info: FavoriteInfo) {
        _favoriteFolderInfo.value = info
        favoriteFolderOwnerMid = info.upper?.mid?.takeIf { it > 0L } ?: info.mid
        mid = favoriteFolderOwnerMid.takeIf { it > 0L } ?: mid
        if (info.title.isNotBlank()) pageTitle = info.title
        _uiState.value = _uiState.value.copy(
            title = pageTitle,
            canRemoveItems = canManageFavoriteResources,
        )
    }


    private suspend fun fetchFavoriteResources(
        page: Int = 1,
        append: Boolean = false,
    ): List<VideoItem> {
        val ordinaryFolder = type == SpaceCollectionDetailType.FAVORITE.raw
        val oldestFirst = ordinaryFolder &&
            _favoriteDetailSortOrder.value == FavoriteDetailSortOrder.OLDEST_FIRST
        var serverPage = page
        var expectedCount: Int? = null
        val response = if (ordinaryFolder) {
            val requested = requestFavoritePage(page)
            if (!append) {
                val info = requireNotNull(requested.info) { "无法获取收藏夹信息" }
                updateFavoriteResourceInfo(info)
                expectedCount = info.media_count
                if (oldestFirst) serverPage = favoriteLastServerPage(info.media_count)
            }
            if (serverPage == page) requested else requestFavoritePage(serverPage)
        } else {
            FavoriteRepository.getFavoriteSeasonList(seasonId = id, pn = page).getOrThrow()
        }
        currentCoroutineContext().ensureActive()
        if (ordinaryFolder) response.info?.let(::updateFavoriteResourceInfo)
        val mediaItems = response.medias.orEmpty()
        val ordered = if (oldestFirst) mediaItems.asReversed() else mediaItems
        val items = ordered.map {
            it.toVideoItem(ownerFallbackMid = mid, ownerFallbackName = ownerName)
        }
        val previousProgress = if (append) _favoriteDetailProgressState.value else FavoriteDetailProgressState()
        hasMore = if (oldestFirst) serverPage > 1 else
            resolveSpaceCollectionHasMore(type, items.size, response.has_more)
        _hasMoreState.value = hasMore
        currentPage = serverPage
        _favoriteDetailProgressState.value = FavoriteDetailProgressState(
            loadedCount = if (append) _uiState.value.items.size + items.size else items.size,
            expectedCount = expectedCount ?: response.info?.media_count ?: previousProgress.expectedCount,
            currentPage = serverPage,
            lastAddedCount = items.size,
            invalidCount = previousProgress.invalidCount + mediaItems.count { it.attr != 0 },
            hasMore = hasMore,
        )
        return items
    }
    
    // ---- 收藏夹详情批量管理（对齐 PiliPlus fav_detail）----

    private val _transferFolders = MutableStateFlow<List<FavFolder>>(emptyList())
    val transferFolders = _transferFolders.asStateFlow()

    private val _isManagingState = MutableStateFlow(false)
    val isManagingState = _isManagingState.asStateFlow()

    /** 加载复制/移动的目标收藏夹列表（排除当前收藏夹）。 */
    fun loadTransferFolders() {
        if (!canCopyFavoriteResources) return
        accountRequests.launch {
            val accountMid = TokenManager.midCache?.takeIf { it > 0L } ?: return@launch
            val folders = FavoriteRepository.getFavFolders(accountMid).getOrNull().orEmpty()
            _transferFolders.value = folders.filter { it.id != id }
        }
    }

    /** 批量取消收藏（移除所选内容），成功后刷新列表。 */
    fun removeFavoriteResources(resourceIds: Set<Long>) {
        if (!canManageFavoriteResources || resourceIds.isEmpty() || _isManagingState.value) return
        _isManagingState.value = true
        accountRequests.launch {
            try {
                if (!canManageFavoriteResources) return@launch
                FavoriteRepository.removeResources(id, resourceIds).getOrThrow()
                loadData()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.widget.Toast.makeText(
                    getApplication(), e.message ?: "移除失败，请稍后重试", android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                _isManagingState.value = false
            }
        }
    }

    /** 批量复制/移动所选内容到目标收藏夹，移动成功后刷新列表。 */
    fun copyOrMoveFavoriteResources(resourceIds: Set<Long>, targetMediaId: Long, copy: Boolean) {
        if (
            !canCopyFavoriteResources || (!copy && !canManageFavoriteResources) ||
            resourceIds.isEmpty() || targetMediaId <= 0L || _isManagingState.value
        ) return
        _isManagingState.value = true
        accountRequests.launch {
            try {
                if (!canCopyFavoriteResources || (!copy && !canManageFavoriteResources)) return@launch
                FavoriteRepository.copyOrMoveResources(
                    sourceMediaId = id,
                    targetMediaId = targetMediaId,
                    mid = mid,
                    resourceIds = resourceIds,
                    copy = copy,
                ).getOrThrow()
                if (!copy) loadData()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.widget.Toast.makeText(
                    getApplication(), e.message ?: "操作失败，请稍后重试", android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                _isManagingState.value = false
            }
        }
    }

    fun retryLoadMore() {
        _uiState.value = _uiState.value.copy(loadMoreError = null)
        loadMore()
    }

    internal fun shareFavoriteFolderToDynamic() {
        if (!canShareFavoriteFolder || _isManagingState.value ||
            _uiState.value.isLoading || isLoadingMore) return
        val mediaId = id
        val generation = requestGeneration
        _isManagingState.value = true
        accountRequests.launch {
            try {
                val info = FavoriteRepository.getFolderInfo(mediaId).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (generation != requestGeneration || id != mediaId) return@launch
                updateFavoriteResourceInfo(info)
                check(canShareFavoriteFolder) { "私密收藏夹不能分享至动态" }
                FavoriteRepository.shareFolderToDynamic(
                    mediaId = mediaId,
                    content = "分享收藏夹：${info.title}",
                ).getOrThrow()
                Toast.makeText(getApplication(), "已分享至动态", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(getApplication(), e.message ?: "分享失败，请稍后重试", Toast.LENGTH_SHORT).show()
            } finally {
                _isManagingState.value = false
            }
        }
    }

    internal fun loadFavoriteFolderForEditing(onSuccess: () -> Unit) {
        if (!canEditFavoriteFolder || _isManagingState.value || _uiState.value.isLoading) return
        val mediaId = id
        val generation = requestGeneration
        _favoriteFolderInfo.value = null
        _isManagingState.value = true
        accountRequests.launch {
            try {
                val info = FavoriteRepository.getFolderInfo(mediaId).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (generation != requestGeneration || id != mediaId) return@launch
                updateFavoriteResourceInfo(info)
                check(canEditFavoriteFolder) { "无法编辑此收藏夹" }
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == requestGeneration) {
                    Toast.makeText(getApplication(), e.message ?: "获取收藏夹信息失败，请重试", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isManagingState.value = false
            }
        }
    }

    internal fun saveFavoriteFolder(
        title: String,
        intro: String,
        isPrivate: Boolean,
        coverUri: Uri?,
        onSuccess: () -> Unit,
    ) {
        if (!canEditFavoriteFolder || _isManagingState.value || title.isBlank()) return
        val original = _favoriteFolderInfo.value ?: return
        val mediaId = id
        val accountMid = TokenManager.midCache ?: return
        val generation = requestGeneration
        _isManagingState.value = true
        accountRequests.launch {
            try {
                val app = getApplication<Application>()
                val cover = if (coverUri == null) original.cover else {
                    CommentRepository.uploadCommentImage(
                        fileName = coverUri.lastPathSegment?.substringAfterLast('/') ?: "favorite_cover.jpg",
                        mimeType = app.contentResolver.getType(coverUri) ?: "image/jpeg",
                        resolver = app.contentResolver,
                        uri = coverUri,
                    ).getOrThrow().imgSrc
                }
                currentCoroutineContext().ensureActive()
                check(canEditFavoriteFolder && id == mediaId && requestGeneration == generation &&
                    TokenManager.midCache == accountMid) { "无法编辑此收藏夹" }
                FavoriteRepository.editFolder(
                    mediaId = mediaId,
                    title = title.trim(),
                    intro = intro.trim(),
                    isPrivate = isPrivate,
                    cover = cover,
                ).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (generation != requestGeneration || id != mediaId || TokenManager.midCache != accountMid) return@launch
                val updated = original.copy(
                    title = title.trim(),
                    intro = intro.trim(),
                    cover = cover,
                    attr = if (isPrivate) original.attr or 1 else original.attr and 1.inv(),
                )
                updateFavoriteResourceInfo(updated)
                try {
                    FavoriteFolderCoverCache.store(
                        app,
                        listOf(FavoriteFolderCoverCache.Resolved(mediaId, updated.media_count, cover)),
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Preview persistence must not turn a successful remote edit into a failure.
                    e.printStackTrace()
                }
                FavoriteFolderUpdateBus.notifyChanged(mediaId, accountMid, updated)
                Toast.makeText(app, "已更新收藏夹", Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(getApplication(), e.message ?: "编辑收藏夹失败", Toast.LENGTH_SHORT).show()
            } finally {
                _isManagingState.value = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMore || _uiState.value.isLoading ||
            _uiState.value.loadMoreError != null || _isManagingState.value) return
        isLoadingMore = true
        _isLoadingMoreState.value = true
        val generation = requestGeneration
        val oldestFirst = type == "favorite" &&
            _favoriteDetailSortOrder.value == FavoriteDetailSortOrder.OLDEST_FIRST
        val nextPage = currentPage + if (oldestFirst) -1 else 1
        pagingJob = accountRequests.launch {
            try {
                val newItems = when (type) {
                    "season" -> {
                        val response = spaceApi.getSeasonArchives(mid, id, nextPage)
                        check(response.code == 0) { "加载更多失败：${response.code}" }
                        val data = checkNotNull(response.data) { "合集内容响应不完整，请重试" }
                        data.archives.map { mapSeasonArchiveToVideoItem(it, mid, ownerName) }
                    }
                    "series" -> {
                        val response = spaceApi.getSeriesArchives(mid, id, nextPage)
                        check(response.code == 0) { "加载更多失败：${response.code}" }
                        val data = checkNotNull(response.data) { "系列内容响应不完整，请重试" }
                        data.archives.map { mapSeriesArchiveToVideoItem(it, mid, ownerName) }
                    }
                    "favorite", "favorite_season" -> fetchFavoriteResources(nextPage, append = true)
                    else -> return@launch
                }
                currentCoroutineContext().ensureActive()
                if (generation != requestGeneration) return@launch
                if (!isFavoriteDetail) {
                    hasMore = resolveSpaceCollectionHasMore(type, newItems.size)
                    _hasMoreState.value = hasMore
                }
                currentPage = nextPage
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items + newItems,
                    loadMoreError = null,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 失败不推进页码、不关闭 hasMore，保留已加载内容供同页重试。
                if (generation == requestGeneration) {
                    _uiState.value = _uiState.value.copy(
                        loadMoreError = e.message ?: "加载更多失败，请稍后重试"
                    )
                }
            } finally {
                if (generation == requestGeneration) {
                    isLoadingMore = false
                    _isLoadingMoreState.value = false
                }
            }
        }
    }

    init {
        loadData()
    }
}
