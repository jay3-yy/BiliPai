package com.android.purebilibili.feature.space

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPagingSession
import kotlinx.coroutines.flow.update
import com.android.purebilibili.core.refresh.FavoriteFolderUpdateBus
import com.android.purebilibili.core.store.FavoriteFolderCoverCache
import com.android.purebilibili.data.model.response.FavoriteInfo
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

    internal val favoriteOrderFolderId: Long get() = id
    internal val favoriteOrderOwnerMid: Long get() = favoriteFolderOwnerMid

    internal fun onFavoriteResourceOrderSaved() {
        if (!canManageFavoriteResources) return
        _favoriteDetailSortOrder.value = FavoriteDetailSortOrder.NEWEST_FIRST
        loadData()
    }

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

    private var pagingSession: ForwardPagingSession<CollectionDetailPageKey, CollectionDetailPageChunk>? = null
    private var pagingJob: Job? = null
    private var requestGeneration = 0L

    override fun loadData(showLoading: Boolean) {
        val generation = ++requestGeneration
        pagingJob?.cancel()
        pagingSession = null
        _isLoadingMoreState.value = false
        _hasMoreState.value = false
        if (id <= 0L) {
            _uiState.update { it.copy(isLoading = false, items = emptyList()) }
            return
        }
        val identity = accountRequests.identity
        val requestType = type
        val requestId = id
        val requestMid = mid
        val requestOwner = ownerName
        val ordinaryFavorite = requestType == SpaceCollectionDetailType.FAVORITE.raw
        val favoriteDetail = isFavoriteDetail
        val favoriteSort = _favoriteDetailSortOrder.value
        val oldestFirst = ordinaryFavorite && favoriteSort == FavoriteDetailSortOrder.OLDEST_FIRST
        _uiState.update { it.copy(isLoading = showLoading || it.items.isEmpty(), error = null, loadMoreError = null) }
        var appliedPages = 0
        lateinit var session: ForwardPagingSession<CollectionDetailPageKey, CollectionDetailPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                var resolvedOwnerMid = requestMid
                collectionDetailPagingSource(ordinaryFavorite, oldestFirst) { number ->
                    when (requestType) {
                        "season" -> {
                            val response = spaceApi.getSeasonArchives(requestMid, requestId, number)
                            check(response.code == 0) { "加载合集失败：${response.code}" }
                            val data = checkNotNull(response.data) { "合集内容响应不完整，请重试" }
                            CollectionDetailServerPage(
                                data.archives.map { mapSeasonArchiveToVideoItem(it, requestMid, requestOwner) },
                                resolveSpaceCollectionHasMore(requestType, data.archives.size)
                            )
                        }
                        "series" -> {
                            val response = spaceApi.getSeriesArchives(requestMid, requestId, number)
                            check(response.code == 0) { "加载系列失败：${response.code}" }
                            val data = checkNotNull(response.data) { "系列内容响应不完整，请重试" }
                            CollectionDetailServerPage(
                                data.archives.map { mapSeriesArchiveToVideoItem(it, requestMid, requestOwner) },
                                resolveSpaceCollectionHasMore(requestType, data.archives.size)
                            )
                        }
                        else -> {
                            val response = if (ordinaryFavorite) {
                                FavoriteRepository.getFavoriteList(mediaId = requestId, pn = number, order = favoriteSort.serverOrder)
                            } else FavoriteRepository.getFavoriteSeasonList(seasonId = requestId, pn = number)
                            val data = response.getOrThrow()
                            currentCoroutineContext().ensureActive()
                            data.info?.let { info ->
                                resolvedOwnerMid = info.upper?.mid?.takeIf { it > 0L }
                                    ?: info.mid.takeIf { it > 0L } ?: resolvedOwnerMid
                            }
                            if (ordinaryFavorite) check(data.medias?.isNotEmpty() == true || data.info?.media_count == 0) {
                                "收藏夹分页数据为空，请重试"
                            }
                            val items = data.medias.orEmpty().map {
                                it.toVideoItem(ownerFallbackMid = resolvedOwnerMid, ownerFallbackName = requestOwner)
                            }
                            CollectionDetailServerPage(
                                items = items,
                                hasMore = resolveSpaceCollectionHasMore(requestType, items.size, data.has_more),
                                info = data.info,
                                invalidCount = data.medias.orEmpty().count { it.attr != 0 },
                            )
                        }
                    }
                }
            },
            onChanged = { pages, states ->
                if (pagingSession === session && generation == requestGeneration && accountRequests.isCurrent(identity) && states != null) {
                    val refreshError = (states.refresh as? LoadState.Error)?.error
                    val appendError = (states.append as? LoadState.Error)?.error
                    val ready = states.refresh is LoadState.NotLoading && pages.isNotEmpty()
                    if (ready && pages.size > appliedPages && ordinaryFavorite) {
                        pages.lastOrNull()?.page?.info?.let(::updateFavoriteResourceInfo)
                    }
                    val items = if (ready) pages.flatMap { it.page.items } else _uiState.value.items
                    _uiState.update { it.copy(
                        items = items,
                        isLoading = states.refresh is LoadState.Loading && (showLoading || it.items.isEmpty()),
                        error = refreshError?.let { error -> error.message ?: "加载失败" },
                        loadMoreError = appendError?.let { error -> error.message ?: "加载更多失败，请稍后重试" },
                    ) }
                    _isLoadingMoreState.value = states.append is LoadState.Loading
                    _hasMoreState.value = !states.append.endOfPaginationReached
                    if (ready && favoriteDetail) {
                        val last = pages.last()
                        _favoriteDetailProgressState.value = FavoriteDetailProgressState(
                            loadedCount = items.size,
                            expectedCount = pages.lastOrNull { it.page.info != null }?.page?.info?.media_count
                                ?: _favoriteDetailProgressState.value.expectedCount,
                            currentPage = last.number,
                            lastAddedCount = last.page.items.size,
                            invalidCount = pages.sumOf { it.page.invalidCount },
                            hasMore = _hasMoreState.value,
                        )
                    }
                    if (ready) appliedPages = pages.size
                }
            }
        )
        pagingSession = session
        pagingJob = accountRequests.launch { session.run() }
    }

    internal fun changeFavoriteDetailSortOrder(order: FavoriteDetailSortOrder): Boolean {
        if (!canSortFavoriteFolder || order == _favoriteDetailSortOrder.value ||
            _uiState.value.isLoading || isLoadingMore || _isManagingState.value) return false
        _favoriteDetailSortOrder.value = order
        loadData()
        return true
    }

    private val isLoadingMore: Boolean get() = _isLoadingMoreState.value
    
    private val _isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMoreState = _isLoadingMoreState.asStateFlow()
    
    private val _hasMoreState = MutableStateFlow(true)
    val hasMoreState = _hasMoreState.asStateFlow()
    override fun onAccountChanged() {
        ++requestGeneration
        pagingJob?.cancel()
        favoriteFolderOwnerMid = 0L
        _favoriteFolderInfo.value = null
        pagingSession = null
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
        if (this.type != type || this.id != id || this.mid != mid) {
            _favoriteDetailSortOrder.value = FavoriteDetailSortOrder.NEWEST_FIRST
        }
        ++requestGeneration
        pagingJob?.cancel()
        _favoriteFolderInfo.value = null
        favoriteFolderOwnerMid = 0L
        pagingSession = null
        _isLoadingMoreState.value = false
        val request = resolveSpaceCollectionDetailRequest(type, id, mid, title)
        if (request == null) {
            this.type = ""
            this.id = 0L
            this.mid = 0L
            this.ownerName = ownerName
            this.pageTitle = title
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

    override suspend fun fetchItems(): List<VideoItem> = _uiState.value.items

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


    // ---- 收藏夹详情批量管理 ----

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
        if (_isManagingState.value || !accountRequests.isCurrent()) return
        pagingSession?.retry()
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
        if (_isManagingState.value || !accountRequests.isCurrent()) return
        pagingSession?.append()
    }

    init {
        loadData()
    }
}
