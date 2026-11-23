package com.android.purebilibili.feature.audio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.feature.audio.library.BilibiliListenVideoLibraryDataSource
import com.android.purebilibili.feature.audio.library.ListenVideoAlbum
import com.android.purebilibili.feature.audio.library.ListenVideoArtist
import com.android.purebilibili.feature.audio.library.ListenVideoLibraryDataSource
import com.android.purebilibili.feature.audio.library.ListenVideoLibraryLoader
import com.android.purebilibili.feature.audio.library.ListenVideoPlaylist
import com.android.purebilibili.feature.audio.library.ListenVideoSection
import com.android.purebilibili.feature.audio.library.ListenVideoTrack
import com.android.purebilibili.feature.audio.library.listenVideoIndexKey
import com.android.purebilibili.feature.audio.library.mapListenVideoAlbums
import com.android.purebilibili.feature.audio.library.mapListenVideoArtists
import com.android.purebilibili.feature.audio.library.mapListenVideoPlaylists
import com.android.purebilibili.feature.audio.library.toListenVideoTrackOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ListenVideoUiState(
    val generation: Long = 0L,
    val isLoggedIn: Boolean = true,
    val isLoading: Boolean = false,
    val isIndexing: Boolean = false,
    val indexedFolderCount: Int = 0,
    val totalFolderCount: Int = 0,
    val section: ListenVideoSection = ListenVideoSection.PLAYLISTS,
    val playlists: List<ListenVideoPlaylist> = emptyList(),
    val albums: List<ListenVideoAlbum> = emptyList(),
    val artists: List<ListenVideoArtist> = emptyList(),
    val selectedTitle: String = "",
    val selectedTracks: List<ListenVideoTrack> = emptyList(),
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    val failedFolderIds: Set<Long> = emptySet(),
    val error: String? = null
)

internal class ListenVideoViewModel(
    private val dataSource: ListenVideoLibraryDataSource = BilibiliListenVideoLibraryDataSource(),
    private val currentMid: () -> Long = { TokenManager.midCache ?: 0L }
) : ViewModel() {
    private var loader = ListenVideoLibraryLoader(dataSource)
    private val _uiState = MutableStateFlow(ListenVideoUiState())
    val uiState = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var previewJob: Job? = null
    private var backgroundPaused = false
    private var loadedMid = 0L
    private var isFoldersLoading = false
    private var indexJob: Job? = null
    private var detailJob: Job? = null
    private var ownedFolders: List<FavFolder> = emptyList()
    private var collectedFolders: List<FavFolder> = emptyList()
    private var indexedResources: List<FavoriteData> = emptyList()
    private val indexedFolderIds = linkedSetOf<Long>()
    private var indexCompleted = false
    private var lastDetailTitle = ""
    private var lastDetailRequest: (suspend () -> Result<List<FavoriteData>>)? = null
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var isPageActive = false
    private var needsAccountReload = false

    init {
        accountRequests.observe {
            refresh(load = isPageActive)
        }
    }

    fun refresh() = refresh(load = true)

    private fun refresh(load: Boolean) {
        needsAccountReload = !load
        refreshJob?.cancel()
        previewJob?.cancel()
        indexJob?.cancel()
        detailJob?.cancel()
        backgroundPaused = false
        loader = ListenVideoLibraryLoader(dataSource)
        ownedFolders = emptyList()
        collectedFolders = emptyList()
        indexedResources = emptyList()
        indexedFolderIds.clear()
        indexCompleted = false
        lastDetailTitle = ""
        lastDetailRequest = null
        val generation = _uiState.value.generation + 1L
        val mid = currentMid()
        loadedMid = mid
        isFoldersLoading = mid > 0L
        if (mid <= 0L) {
            ownedFolders = emptyList()
            collectedFolders = emptyList()
            indexedResources = emptyList()
            indexCompleted = false
            lastDetailTitle = ""
            lastDetailRequest = null
            _uiState.value = ListenVideoUiState(
                generation = generation,
                isLoggedIn = false
            )
            return
        }

        _uiState.value = ListenVideoUiState(
            generation = generation,
            section = _uiState.value.section,
            isLoggedIn = true,
            isLoading = true
        )
        if (!load) {
            isFoldersLoading = false
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val identity = accountRequests.identity
        refreshJob = accountRequests.launch {
            try {
                val foldersResult = dataSource.ownedFolders(mid)
                if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
                val folders = foldersResult.getOrElse { error ->
                    isFoldersLoading = false
                    _uiState.update {
                        it.copy(isLoading = false, error = error.userMessage("收藏夹加载失败"))
                    }
                    return@launch
                }
                ownedFolders = folders
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlists = mapListenVideoPlaylists(folders),
                        totalFolderCount = folders.size,
                    )
                }
                val collectedResult = loader.loadCollectedFolders(mid)
                if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
                ownedFolders = folders
                collectedFolders = collectedResult.getOrDefault(emptyList())
                isFoldersLoading = false
                indexedResources = emptyList()
                indexedFolderIds.clear()
                indexCompleted = false
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlists = mapListenVideoPlaylists(ownedFolders),
                        albums = mapListenVideoAlbums(collectedFolders, emptyList()),
                        artists = emptyList(),
                        totalFolderCount = ownedFolders.size,
                        error = collectedResult.exceptionOrNull()?.userMessage("部分合集加载失败")
                    )
                }
                if (_uiState.value.section != ListenVideoSection.PLAYLISTS) indexLibrary()
                if (!backgroundPaused) {
                    previewJob = accountRequests.launch {
                        val previewCovers = loader.loadPlaylistPreviewCovers(ownedFolders)
                        if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
                        if (previewCovers.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    playlists = state.playlists.map { playlist ->
                                        previewCovers[playlist.mediaId]?.let { cover ->
                                            playlist.copy(coverUrl = cover)
                                        } ?: playlist
                                    }
                                )
                            }
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (accountRequests.isCurrent(identity) && _uiState.value.generation == generation) {
                    isFoldersLoading = false
                    _uiState.update { it.copy(isLoading = false, error = error.userMessage("收藏夹加载失败")) }
                }
            }
        }
    }

    fun pauseBackgroundLoading() {
        isPageActive = false
        pauseIndexing()
    }

    private fun pauseIndexing() {
        backgroundPaused = true
        previewJob?.cancel()
        indexJob?.cancel()
        _uiState.update { it.copy(isIndexing = false) }
    }

    fun resumeBackgroundLoading() {
        isPageActive = true
        backgroundPaused = false
        if (needsAccountReload || !accountRequests.isCurrent() ||
            _uiState.value.generation == 0L || loadedMid != currentMid()
        ) {
            refresh()
        } else if (_uiState.value.section != ListenVideoSection.PLAYLISTS && !indexCompleted) {
            indexLibrary()
        }
    }

    fun selectSection(section: ListenVideoSection) {
        _uiState.update { it.copy(section = section) }
        if (section != ListenVideoSection.PLAYLISTS && !indexCompleted) {
            indexLibrary()
        }
    }

    fun openPlaylist(playlist: ListenVideoPlaylist) {
        // Playlist covers are best-effort. Stop their background requests before loading
        // the user-selected folder so the favorite endpoint does not trip HTTP 412.
        pauseIndexing()
        loadDetail(playlist.title) { loader.loadFolder(playlist.mediaId) }
    }

    fun openAlbum(album: ListenVideoAlbum) {
        loadDetail(album.title) { loader.loadAlbum(album.seasonId) }
    }

    fun openArtist(artist: ListenVideoArtist) {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTitle = artist.name,
                selectedTracks = artist.tracks,
                isDetailLoading = false,
                detailError = null
            )
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTitle = "",
                selectedTracks = emptyList(),
                isDetailLoading = false,
                detailError = null
            )
        }
    }

    fun retryDetail() {
        val request = lastDetailRequest ?: return
        loadDetail(lastDetailTitle, request)
    }

    fun retryFailedIndex() {
        if (_uiState.value.failedFolderIds.isEmpty()) return
        indexLibrary(onlyFailed = true)
    }

    private fun indexLibrary(onlyFailed: Boolean = false) {
        if (backgroundPaused || isFoldersLoading || indexJob?.isActive == true || _uiState.value.isLoading) return
        val generation = _uiState.value.generation
        val identity = accountRequests.identity
        val validOwnedFolders = ownedFolders.filter { it.id > 0L }
        val targets = if (onlyFailed) {
            validOwnedFolders.filter { it.id in _uiState.value.failedFolderIds }
        } else {
            validOwnedFolders.filter { it.id !in indexedFolderIds }
        }
        if (targets.isEmpty()) {
            indexCompleted = true
            return
        }
        val baseCompletedCount = if (onlyFailed) 0 else indexedFolderIds.size
        val progressTotalCount = if (onlyFailed) targets.size else validOwnedFolders.size
        _uiState.update {
            it.copy(
                isIndexing = true,
                indexedFolderCount = baseCompletedCount,
                totalFolderCount = progressTotalCount
            )
        }
        indexJob = accountRequests.launch {
            val previousResources = indexedResources
            val result = loader.indexFolders(
                folders = targets,
                onFolderIndexed = { completed, total ->
                    if (accountRequests.isCurrent(identity) && _uiState.value.generation == generation) {
                        val resolvedCompleted = if (onlyFailed) completed else (baseCompletedCount + completed).coerceAtMost(progressTotalCount)
                        val resolvedTotal = if (onlyFailed) total else progressTotalCount
                        _uiState.update {
                            it.copy(indexedFolderCount = resolvedCompleted, totalFolderCount = resolvedTotal)
                        }
                    }
                },
                onResourcesIndexed = { resources ->
                    val snapshot = withContext(Dispatchers.Default) {
                        val allResources = (previousResources + resources).distinctBy(FavoriteData::listenVideoIndexKey)
                        Triple(
                            allResources,
                            mapListenVideoArtists(allResources),
                            mapListenVideoAlbums(collectedFolders, allResources)
                        )
                    }
                    if (accountRequests.isCurrent(identity) && _uiState.value.generation == generation) {
                        indexedResources = snapshot.first
                        _uiState.update { it.copy(artists = snapshot.second, albums = snapshot.third) }
                    }
                },
            )
            if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
            val snapshot = withContext(Dispatchers.Default) {
                val resources = (indexedResources + result.resources).distinctBy(FavoriteData::listenVideoIndexKey)
                Triple(
                    resources,
                    mapListenVideoAlbums(collectedFolders, resources),
                    mapListenVideoArtists(resources)
                )
            }
            if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
            indexedResources = snapshot.first
            targets.forEach { folder ->
                if (folder.id !in result.failedFolderIds) {
                    indexedFolderIds += folder.id
                }
            }
            val remainingFailedIds = if (onlyFailed) {
                result.failedFolderIds
            } else {
                (_uiState.value.failedFolderIds - indexedFolderIds) + result.failedFolderIds
            }
            indexCompleted = remainingFailedIds.isEmpty() && validOwnedFolders.all { it.id in indexedFolderIds }
            _uiState.update {
                it.copy(
                    isIndexing = false,
                    albums = snapshot.second,
                    artists = snapshot.third,
                    failedFolderIds = remainingFailedIds,
                    error = remainingFailedIds.takeIf(Set<Long>::isNotEmpty)?.let {
                        if (result.haltedByRiskControl) {
                            "已触发收藏夹风控，整理已暂停；${it.size} 个收藏夹可稍后重试"
                        } else {
                            "${it.size} 个收藏夹暂时无法索引"
                        }
                    }
                )
            }
        }
    }

    private fun loadDetail(
        title: String,
        request: suspend () -> Result<List<FavoriteData>>
    ) {
        lastDetailTitle = title
        lastDetailRequest = request
        detailJob?.cancel()
        val generation = _uiState.value.generation
        val identity = accountRequests.identity
        _uiState.update {
            it.copy(
                selectedTitle = title,
                selectedTracks = emptyList(),
                isDetailLoading = true,
                detailError = null
            )
        }
        detailJob = accountRequests.launch {
            val result = request()
            if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
            val resources = result.getOrNull().orEmpty()
            val detailSnapshot = withContext(Dispatchers.Default) {
                val mergedResources = (indexedResources + resources).distinctBy(FavoriteData::listenVideoIndexKey)
                val tracks = resources
                    .mapNotNull(FavoriteData::toListenVideoTrackOrNull)
                    .distinctBy(ListenVideoTrack::bvid)
                Triple(
                    mergedResources,
                    tracks,
                    Pair(
                        mapListenVideoAlbums(collectedFolders, mergedResources),
                        mapListenVideoArtists(mergedResources)
                    )
                )
            }
            if (!accountRequests.isCurrent(identity) || _uiState.value.generation != generation) return@launch
            indexedResources = detailSnapshot.first
            _uiState.update {
                it.copy(
                    selectedTracks = detailSnapshot.second,
                    isDetailLoading = false,
                    detailError = result.exceptionOrNull()?.userMessage("内容加载失败"),
                    albums = detailSnapshot.third.first,
                    artists = detailSnapshot.third.second
                )
            }
        }
    }
}

private fun Throwable.userMessage(fallback: String): String {
    return message?.trim()?.takeIf(String::isNotEmpty) ?: fallback
}
