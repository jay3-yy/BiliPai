package com.android.purebilibili.feature.audio.library

import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.data.model.response.FavoriteResourceData
import com.android.purebilibili.data.repository.FavoriteRepository
import com.android.purebilibili.feature.list.isFavoriteRiskControlError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val LISTEN_VIDEO_PAGE_SIZE = 20
private const val LISTEN_VIDEO_REQUEST_INTERVAL_MILLIS = 600L
private const val LISTEN_VIDEO_PREVIEW_FOLDER_LIMIT = 8

internal data class ListenVideoCollectedFoldersPage(
    val folders: List<FavFolder>,
    val hasMore: Boolean
)

internal data class ListenVideoIndexResult(
    val resources: List<FavoriteData>,
    val failedFolderIds: Set<Long>,
    val haltedByRiskControl: Boolean = false
)

internal interface ListenVideoLibraryDataSource {
    suspend fun ownedFolders(mid: Long): Result<List<FavFolder>>

    suspend fun collectedFolders(mid: Long, page: Int): Result<ListenVideoCollectedFoldersPage>

    suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData>

    suspend fun albumPage(seasonId: Long, page: Int): Result<FavoriteResourceData>
}

internal class BilibiliListenVideoLibraryDataSource : ListenVideoLibraryDataSource {
    override suspend fun ownedFolders(mid: Long): Result<List<FavFolder>> {
        return FavoriteRepository.getFavFolders(mid)
    }

    override suspend fun collectedFolders(
        mid: Long,
        page: Int
    ): Result<ListenVideoCollectedFoldersPage> {
        return FavoriteRepository.getCollectedFavFolders(
            mid = mid,
            pn = page,
            ps = LISTEN_VIDEO_PAGE_SIZE,
            platform = "web"
        ).map { result ->
            ListenVideoCollectedFoldersPage(
                folders = result.folders,
                hasMore = if (result.totalCount > 0) {
                    page * LISTEN_VIDEO_PAGE_SIZE < result.totalCount
                } else {
                    result.folders.size >= LISTEN_VIDEO_PAGE_SIZE
                }
            )
        }
    }

    override suspend fun folderPage(
        mediaId: Long,
        page: Int
    ): Result<FavoriteResourceData> {
        return FavoriteRepository.getFavoriteList(
            mediaId = mediaId,
            pn = page,
            ps = LISTEN_VIDEO_PAGE_SIZE
        )
    }

    override suspend fun albumPage(
        seasonId: Long,
        page: Int
    ): Result<FavoriteResourceData> {
        return FavoriteRepository.getFavoriteSeasonList(seasonId = seasonId, pn = page)
    }
}

internal class ListenVideoLibraryLoader(
    private val source: ListenVideoLibraryDataSource,
    private val minimumRequestIntervalMillis: Long = LISTEN_VIDEO_REQUEST_INTERVAL_MILLIS,
    private val delayAction: suspend (Long) -> Unit = { delay(it) },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val previewCoverSelector: (List<String>) -> String? = { covers ->
        covers.randomOrNull()
    }
) {
    private val folderRequestMutex = Mutex()
    private val folderPages = mutableMapOf<Pair<Long, Int>, FavoriteResourceData>()
    private val albumPages = mutableMapOf<Pair<Long, Int>, FavoriteResourceData>()
    private var lastFolderRequestAtMillis: Long? = null

    suspend fun loadCollectedFolders(mid: Long): Result<List<FavFolder>> {
        return loadPages { page -> source.collectedFolders(mid, page) }
    }

    suspend fun loadFolder(
        mediaId: Long,
        onPageLoaded: suspend (List<FavoriteData>) -> Unit = {},
    ): Result<List<FavoriteData>> {
        var lastRequestedPage = 1
        return loadResourcePages(onPageLoaded) { page ->
            lastRequestedPage = page
            requestFolderPage(mediaId, page)
        }.also { result ->
            if (result.isFailure) folderPages.remove(mediaId to lastRequestedPage)
        }
    }

    suspend fun loadAlbum(
        seasonId: Long,
        onPageLoaded: suspend (List<FavoriteData>) -> Unit = {},
    ): Result<List<FavoriteData>> {
        var lastRequestedPage = 1
        return loadResourcePages(onPageLoaded) { page ->
            lastRequestedPage = page
            albumPages[seasonId to page]?.let { return@loadResourcePages Result.success(it) }
            source.albumPage(seasonId, page).also { result ->
                result.getOrNull()?.let { albumPages[seasonId to page] = it }
            }
        }.also { result ->
            if (result.isFailure) albumPages.remove(seasonId to lastRequestedPage)
        }
    }

    suspend fun loadPlaylistPreviewCovers(
        folders: List<FavFolder>
    ): Map<Long, String> {
        val coversByFolder = linkedMapOf<Long, String>()
        folders.asSequence()
            .filter { it.id > 0L && it.cover.isBlank() }
            .take(LISTEN_VIDEO_PREVIEW_FOLDER_LIMIT)
            .forEach { folder ->
                val result = requestFolderPage(folder.id, 1)
                if (result.isFailure && isFavoriteRiskControlError(result.exceptionOrNull()!!)) {
                    return coversByFolder
                }
                val covers = result.getOrNull()?.medias.orEmpty()
                    .map(FavoriteData::cover)
                    .filter(String::isNotBlank)
                previewCoverSelector(covers)?.let { cover -> coversByFolder[folder.id] = cover }
            }
        return coversByFolder
    }

    suspend fun indexFolders(
        folders: List<FavFolder>,
        onFolderIndexed: (completed: Int, total: Int) -> Unit = { _, _ -> },
        onResourcesIndexed: suspend (List<FavoriteData>) -> Unit = {},
    ): ListenVideoIndexResult {
        val validFolders = folders.filter { it.id > 0L }
        val deduplicated = linkedMapOf<String, FavoriteData>()
        val failedFolderIds = linkedSetOf<Long>()
        var haltedByRiskControl = false
        var emittedInitialBatch = false
        for ((index, folder) in validFolders.withIndex()) {
            val sizeBeforeFolder = deduplicated.size
            val result = loadFolder(folder.id) { folderResources ->
                val sizeBeforePage = deduplicated.size
                folderResources.forEach { resource ->
                    deduplicated[resource.listenVideoIndexKey()] = resource
                }
                if (!emittedInitialBatch && deduplicated.size > sizeBeforePage) {
                    emittedInitialBatch = true
                    onResourcesIndexed(deduplicated.values.toList())
                }
            }
            if (deduplicated.size > sizeBeforeFolder) {
                emittedInitialBatch = true
                onResourcesIndexed(deduplicated.values.toList())
            }
            if (result.isFailure) {
                failedFolderIds += folder.id
                if (isFavoriteRiskControlError(result.exceptionOrNull()!!)) {
                    haltedByRiskControl = true
                    failedFolderIds += validFolders.drop(index + 1).map(FavFolder::id)
                    onFolderIndexed(index + 1, validFolders.size)
                    break
                }
            }
            onFolderIndexed(index + 1, validFolders.size)
        }

        return ListenVideoIndexResult(
            resources = deduplicated.values.toList(),
            failedFolderIds = failedFolderIds,
            haltedByRiskControl = haltedByRiskControl
        )
    }

    private suspend fun loadPages(
        request: suspend (Int) -> Result<ListenVideoCollectedFoldersPage>
    ): Result<List<FavFolder>> {
        return try {
            val folders = mutableListOf<FavFolder>()
            var page = 1
            do {
                val response = request(page).getOrThrow()
                if (response.hasMore && response.folders.isEmpty()) {
                    error("收藏夹分页返回空数据，请稍后重试")
                }
                if (response.hasMore && response.folders.none { next -> folders.none { it.id == next.id } }) {
                    error("收藏夹分页没有新内容，请稍后重试")
                }
                folders += response.folders
                page += 1
            } while (response.hasMore)
            Result.success(folders.distinctBy { it.id })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun loadResourcePages(
        onPageLoaded: suspend (List<FavoriteData>) -> Unit = {},
        request: suspend (Int) -> Result<FavoriteResourceData>
    ): Result<List<FavoriteData>> {
        return try {
            val resources = linkedMapOf<String, FavoriteData>()
            var page = 1
            do {
                val response = request(page).getOrThrow()
                val previousSize = resources.size
                response.medias.orEmpty().forEach { resource ->
                    val key = "${resource.type}:${resource.bvid.ifBlank { resource.bv_id }}:${resource.season_id}:${resource.id}"
                    resources[key] = resource
                }
                onPageLoaded(resources.values.toList())
                if (response.has_more && resources.size == previousSize) {
                    error("列表分页没有新内容，已保留读取的曲目，请稍后重试")
                }
                page += 1
            } while (response.has_more)
            Result.success(resources.values.toList())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Serialize favorite-folder requests and keep a minimum gap between actual network calls. */
    private suspend fun requestFolderPage(
        mediaId: Long,
        page: Int
    ): Result<FavoriteResourceData> = folderRequestMutex.withLock {
        folderPages[mediaId to page]?.let { return@withLock Result.success(it) }
        val lastRequest = lastFolderRequestAtMillis
        if (lastRequest != null) {
            val elapsed = (nowMillis() - lastRequest).coerceAtLeast(0L)
            val remaining = minimumRequestIntervalMillis - elapsed
            if (remaining > 0L) delayAction(remaining)
        }
        val result = try {
            source.folderPage(mediaId, page)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            lastFolderRequestAtMillis = nowMillis()
        }
        result.getOrNull()?.let { response ->
            folderPages[mediaId to page] = response
        }
        result
    }
}

internal fun FavoriteData.listenVideoIndexKey(): String {
    return if (type == 21) {
        "album:${season_id.takeIf { it > 0L } ?: id}"
    } else {
        "track:${bvid.ifBlank { bv_id }}"
    }
}
