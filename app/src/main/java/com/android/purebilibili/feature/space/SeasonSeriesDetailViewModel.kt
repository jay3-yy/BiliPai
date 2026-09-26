package com.android.purebilibili.feature.space

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.feature.list.BaseListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // Pagination
    private var currentPage = 1
    private var hasMore = true
    private var isLoadingMore = false
    
    private val _isLoadingMoreState = MutableStateFlow(false)
    val isLoadingMoreState = _isLoadingMoreState.asStateFlow()
    
    private val _hasMoreState = MutableStateFlow(true)
    val hasMoreState = _hasMoreState.asStateFlow()

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
        val request = resolveSpaceCollectionDetailRequest(type, id, mid, title)
        if (request == null) {
            this.type = ""
            this.id = 0L
            this.mid = 0L
            this.ownerName = ownerName
            this.pageTitle = title
            _uiState.value = _uiState.value.copy(title = title, items = emptyList())
            return
        }
        this.type = request.type.raw
        this.id = request.id
        this.mid = request.mid
        this.ownerName = ownerName
        this.pageTitle = request.title
        _favoriteDetailProgressState.value = FavoriteDetailProgressState()
        
        // Update Title via UI State update (a bit tricky since BaseListViewModel sets it in init)
        // We trigger a reload which resets title in loadData() -> fetchItems() ? No.
        // BaseListViewModel's init { loadData() } runs before we set params.
        // So loadData() might fail or do nothing first. 
        // We should manually call loadData() after init.
        
        // Update title in state
        _uiState.value = _uiState.value.copy(title = request.title)
        loadData()
    }

    override suspend fun fetchItems(): List<VideoItem> {
        if (id == 0L) return emptyList()

        if (type == "season") {
            return fetchSeasonArchives()
        } else if (type == "series") {
            return fetchSeriesArchives()
        } else if (type == "favorite") {
            return fetchFavoriteResources()
        } else if (type == "favorite_season") {
            return fetchFavoriteSeasonResources()
        }
        return emptyList()
    }

    private suspend fun fetchSeasonArchives(): List<VideoItem> {
        currentPage = 1
        try {
            val response = spaceApi.getSeasonArchives(mid, id, currentPage)
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
            e.printStackTrace()
        }
        return emptyList()
    }

    private suspend fun fetchSeriesArchives(): List<VideoItem> {
        currentPage = 1
         try {
            val response = spaceApi.getSeriesArchives(mid, id, currentPage)
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
            e.printStackTrace()
        }
        return emptyList()
    }

    private suspend fun fetchFavoriteResources(): List<VideoItem> {
        currentPage = 1
        return try {
            val response = FavoriteRepository.getFavoriteList(mediaId = id, pn = currentPage).getOrNull()
            hasMore = response?.has_more == true
            _hasMoreState.value = hasMore
            val mediaItems = response?.medias.orEmpty()
            _favoriteDetailProgressState.value = FavoriteDetailProgressState(
                loadedCount = mediaItems.size,
                expectedCount = response?.info?.media_count ?: 0,
                currentPage = currentPage,
                lastAddedCount = mediaItems.size,
                invalidCount = mediaItems.count { it.attr != 0 },
                hasMore = hasMore
            )
            mediaItems
                .orEmpty()
                .map {
                    it.toVideoItem(
                        ownerFallbackMid = mid,
                        ownerFallbackName = ownerName
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchFavoriteSeasonResources(): List<VideoItem> {
        currentPage = 1
        return try {
            val response = FavoriteRepository.getFavoriteSeasonList(seasonId = id, pn = currentPage).getOrNull()
            hasMore = response?.has_more == true
            _hasMoreState.value = hasMore
            val mediaItems = response?.medias.orEmpty()
            _favoriteDetailProgressState.value = FavoriteDetailProgressState(
                loadedCount = mediaItems.size,
                expectedCount = response?.info?.media_count ?: 0,
                currentPage = currentPage,
                lastAddedCount = mediaItems.size,
                invalidCount = mediaItems.count { it.attr != 0 },
                hasMore = hasMore
            )
            mediaItems.map {
                it.toVideoItem(
                    ownerFallbackMid = mid,
                    ownerFallbackName = ownerName
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    // ---- 收藏夹详情批量管理（对齐 PiliPlus fav_detail）----

    private val _transferFolders = MutableStateFlow<List<FavFolder>>(emptyList())
    val transferFolders = _transferFolders.asStateFlow()

    private val _isManagingState = MutableStateFlow(false)
    val isManagingState = _isManagingState.asStateFlow()

    /** 加载复制/移动的目标收藏夹列表（排除当前收藏夹）。 */
    fun loadTransferFolders() {
        if (!isFavoriteDetail || id == 0L) return
        viewModelScope.launch {
            val folders = FavoriteRepository.getFavFolders(mid).getOrNull().orEmpty()
            _transferFolders.value = folders.filter { it.id != id }
        }
    }

    /** 批量取消收藏（移除所选内容），成功后刷新列表。 */
    fun removeFavoriteResources(resourceIds: Set<Long>) {
        if (!isFavoriteDetail || id == 0L || resourceIds.isEmpty()) return
        viewModelScope.launch {
            _isManagingState.value = true
            try {
                FavoriteRepository.removeResources(id, resourceIds).onSuccess { loadData() }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isManagingState.value = false
            }
        }
    }

    /** 批量复制/移动所选内容到目标收藏夹，移动成功后刷新列表。 */
    fun copyOrMoveFavoriteResources(resourceIds: Set<Long>, targetMediaId: Long, copy: Boolean) {
        if (!isFavoriteDetail || id == 0L || resourceIds.isEmpty()) return
        viewModelScope.launch {
            _isManagingState.value = true
            try {
                FavoriteRepository.copyOrMoveResources(
                    sourceMediaId = id,
                    targetMediaId = targetMediaId,
                    mid = mid,
                    resourceIds = resourceIds,
                    copy = copy,
                ).onSuccess {
                    if (!copy) loadData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isManagingState.value = false
            }
        }
    }

    // Supports loading more pages
    fun loadMore() {        if (isLoadingMore || !hasMore) return
        
        viewModelScope.launch {
            isLoadingMore = true
            _isLoadingMoreState.value = true
            currentPage++
            
            try {
                var newItems: List<VideoItem> = emptyList()
                if (type == "season") {
                     val response = spaceApi.getSeasonArchives(mid, id, currentPage)
                     if (response.code == 0 && response.data != null) {
                         val archives = response.data.archives
                         newItems = archives.map { item -> mapSeasonArchiveToVideoItem(item, mid, ownerName) }
                     }
                } else if (type == "series") {
                     val response = spaceApi.getSeriesArchives(mid, id, currentPage)
                     if (response.code == 0 && response.data != null) {
                         val archives = response.data.archives
                         newItems = archives.map { item -> mapSeriesArchiveToVideoItem(item, mid, ownerName) }
                     }
                } else if (type == "favorite") {
                    val response = FavoriteRepository.getFavoriteList(mediaId = id, pn = currentPage).getOrNull()
                    hasMore = response?.has_more == true
                    _hasMoreState.value = hasMore
                    val mediaItems = response?.medias.orEmpty()
                    newItems = mediaItems.map {
                        it.toVideoItem(
                            ownerFallbackMid = mid,
                            ownerFallbackName = ownerName
                        )
                    }
                    val currentLoadedCount = _uiState.value.items.size + newItems.size
                    _favoriteDetailProgressState.value = _favoriteDetailProgressState.value.copy(
                        loadedCount = currentLoadedCount,
                        expectedCount = response?.info?.media_count
                            ?: _favoriteDetailProgressState.value.expectedCount,
                        currentPage = currentPage,
                        lastAddedCount = mediaItems.size,
                        invalidCount = _favoriteDetailProgressState.value.invalidCount +
                            mediaItems.count { it.attr != 0 },
                        hasMore = hasMore
                    )
                } else if (type == "favorite_season") {
                    val response = FavoriteRepository.getFavoriteSeasonList(seasonId = id, pn = currentPage).getOrNull()
                    hasMore = response?.has_more == true
                    _hasMoreState.value = hasMore
                    val mediaItems = response?.medias.orEmpty()
                    newItems = mediaItems.map {
                        it.toVideoItem(
                            ownerFallbackMid = mid,
                            ownerFallbackName = ownerName
                        )
                    }
                    val currentLoadedCount = _uiState.value.items.size + newItems.size
                    _favoriteDetailProgressState.value = _favoriteDetailProgressState.value.copy(
                        loadedCount = currentLoadedCount,
                        expectedCount = response?.info?.media_count
                            ?: _favoriteDetailProgressState.value.expectedCount,
                        currentPage = currentPage,
                        lastAddedCount = mediaItems.size,
                        invalidCount = _favoriteDetailProgressState.value.invalidCount +
                            mediaItems.count { it.attr != 0 },
                        hasMore = hasMore
                    )
                }
                
                if (newItems.isNotEmpty()) {
                    val currentItems = _uiState.value.items
                    _uiState.value = _uiState.value.copy(items = currentItems + newItems)

                    if (type != "favorite") {
                        hasMore = newItems.size >= 30
                        _hasMoreState.value = hasMore
                    }
                } else {
                    hasMore = false
                    _hasMoreState.value = false
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
                 _isLoadingMoreState.value = false
            }
        }
    }

    init {
        loadData()
    }
}
