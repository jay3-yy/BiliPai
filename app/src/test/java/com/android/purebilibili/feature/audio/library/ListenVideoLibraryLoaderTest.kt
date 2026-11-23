package com.android.purebilibili.feature.audio.library

import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.data.model.response.FavoriteResourceData
import com.android.purebilibili.data.model.response.Upper
import com.android.purebilibili.feature.audio.viewmodel.ListenVideoViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ListenVideoLibraryLoaderTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()

    private fun createViewModel(source: ListenVideoLibraryDataSource, mid: Long) =
        ListenVideoViewModel(source, currentMid = { mid }).also {
            viewModelStore.put("library", it)
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        viewModelStore.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `folder loader follows pagination until has more is false`() = runTest {
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(
                    1 to Result.success(page("BV1", hasMore = true)),
                    2 to Result.success(page("BV2", hasMore = false))
                )
            )
        )

        val result = ListenVideoLibraryLoader(source).loadFolder(1L)

        assertEquals(listOf("BV1", "BV2"), result.getOrThrow().map { it.bvid })
        assertEquals(listOf(1, 2), source.requestedFolderPages)
    }

    @Test
    fun `default loader serializes favorite folder requests`() = runTest {
        val source = SerialFavoriteRequestDataSource()
        val loader = ListenVideoLibraryLoader(source)

        val first = async { loader.loadFolder(1L) }
        source.firstStarted.await()
        val second = async { loader.loadFolder(2L) }
        runCurrent()

        assertEquals(listOf(1L), source.startedFolderIds)
        source.releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf(1L, 2L), source.startedFolderIds)
    }

    @Test
    fun `index keeps successful folders when one folder fails`() = runTest {
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(1 to Result.success(page("BV1", hasMore = false))),
                2L to mapOf(1 to Result.failure(IOException("offline")))
            )
        )

        val result = ListenVideoLibraryLoader(source, minimumRequestIntervalMillis = 0L).indexFolders(
            listOf(FavFolder(id = 1L, title = "A"), FavFolder(id = 2L, title = "B"))
        )

        assertEquals(listOf("BV1"), result.resources.map { it.bvid })
        assertEquals(setOf(2L), result.failedFolderIds)
    }

    @Test
    fun `index stops after risk control and marks untouched folders for retry`() = runTest {
        val riskError = com.android.purebilibili.data.repository.FavoriteRequestException(
            httpCode = 412,
            message = "HTTP 412",
        )
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(1 to Result.success(page("BV1", hasMore = false))),
                2L to mapOf(1 to Result.failure(riskError)),
                3L to mapOf(1 to Result.success(page("BV3", hasMore = false))),
            )
        )

        val result = ListenVideoLibraryLoader(
            source = source,
            minimumRequestIntervalMillis = 0L,
        ).indexFolders(
            listOf(
                FavFolder(id = 1L, title = "A"),
                FavFolder(id = 2L, title = "B"),
                FavFolder(id = 3L, title = "C"),
            )
        )

        assertEquals(listOf(1L, 2L), source.requestedFolderIds)
        assertEquals(listOf("BV1"), result.resources.map { it.bvid })
        assertEquals(setOf(2L, 3L), result.failedFolderIds)
        assertTrue(result.haltedByRiskControl)
    }

    @Test
    fun `folder pagination applies minimum spacing between requests`() = runTest {
        val delays = mutableListOf<Long>()
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(
                    1 to Result.success(page("BV1", hasMore = true)),
                    2 to Result.success(page("BV2", hasMore = false)),
                )
            )
        )
        val loader = ListenVideoLibraryLoader(
            source = source,
            minimumRequestIntervalMillis = 350L,
            delayAction = { delays += it },
            nowMillis = { 0L },
        )

        loader.loadFolder(1L).getOrThrow()

        assertEquals(listOf(350L), delays)
    }

    @Test
    fun `playlist previews use a video cover from each folder first page`() = runTest {
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(1 to Result.success(coverPage("cover-a", "cover-b"))),
                2L to mapOf(1 to Result.failure(IOException("offline")))
            )
        )
        val loader = ListenVideoLibraryLoader(
            source = source,
            minimumRequestIntervalMillis = 0L,
            previewCoverSelector = { covers -> covers.lastOrNull() }
        )

        val covers = loader.loadPlaylistPreviewCovers(
            listOf(FavFolder(id = 1L), FavFolder(id = 2L))
        )

        assertEquals(mapOf(1L to "cover-b"), covers)
        assertEquals(listOf(1, 1), source.requestedFolderPages)
    }

    @Test
    fun `index reuses preview first page instead of requesting it twice`() = runTest {
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(1 to Result.success(coverPage("cover-a")))
            )
        )
        val loader = ListenVideoLibraryLoader(
            source = source,
            minimumRequestIntervalMillis = 0L,
        )

        loader.loadPlaylistPreviewCovers(listOf(FavFolder(id = 1L)))
        val result = loader.indexFolders(listOf(FavFolder(id = 1L)))

        assertEquals(listOf(1L), source.requestedFolderIds)
        assertEquals(listOf("BV0"), result.resources.map { it.bvid })
    }

    @Test
    fun `new refresh cancels old generation and only latest result is published`() = runTest {
        val source = RestartingListenVideoDataSource()
        val viewModel = createViewModel(source, mid = 42L)

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.generation)
        assertEquals(listOf("Latest"), viewModel.uiState.value.playlists.map { it.title })
        assertTrue(source.firstRequestCancelled)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `logged out refresh skips remote requests`() = runTest {
        val source = FakeListenVideoDataSource()
        val viewModel = createViewModel(source, mid = 0L)

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals(0, source.ownedFolderRequests)
    }

    @Test
    fun `detail retry repeats the last playlist request`() = runTest {
        val source = RetryDetailListenVideoDataSource()
        val viewModel = createViewModel(source, mid = 42L)
        viewModel.refresh()
        advanceUntilIdle()
        val playlist = viewModel.uiState.value.playlists.single()

        viewModel.openPlaylist(playlist)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.detailError != null)

        viewModel.retryDetail()
        advanceUntilIdle()

        assertEquals(listOf("BV-RETRY"), viewModel.uiState.value.selectedTracks.map { it.bvid })
        assertEquals(3, source.detailRequests)
    }

    @Test
    fun `first page is published while next page is still waiting`() = runTest {
        val nextPageStarted = CompletableDeferred<Unit>()
        val releaseNextPage = CompletableDeferred<Unit>()
        val source = object : FakeListenVideoDataSource() {
            override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
                if (page == 2) {
                    nextPageStarted.complete(Unit)
                    releaseNextPage.await()
                }
                return Result.success(this@ListenVideoLibraryLoaderTest.page(if (page == 1) "BV1" else "BV2", hasMore = page == 1))
            }
        }
        val published = mutableListOf<List<String>>()
        val loading = async {
            ListenVideoLibraryLoader(source, minimumRequestIntervalMillis = 0L).loadFolder(1L) {
                published += it.map(FavoriteData::bvid)
            }
        }
        nextPageStarted.await()
        assertEquals(listOf(listOf("BV1")), published)
        assertFalse(loading.isCompleted)
        releaseNextPage.complete(Unit)
        loading.await().getOrThrow()
        assertEquals(listOf("BV1", "BV2"), published.last())
    }

    @Test
    fun `retry retains successful pages and only repeats failed page network request`() = runTest {
        val requested = mutableListOf<Int>()
        var secondPageAttempts = 0
        val source = object : FakeListenVideoDataSource() {
            override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
                requested += page
                if (page == 2 && ++secondPageAttempts == 1) return Result.failure(IOException("offline"))
                return Result.success(this@ListenVideoLibraryLoaderTest.page(if (page == 1) "BV1" else "BV2", hasMore = page == 1))
            }
        }
        val loader = ListenVideoLibraryLoader(source, minimumRequestIntervalMillis = 0L)
        val published = mutableListOf<List<String>>()
        assertTrue(loader.loadFolder(1L) { published += it.map(FavoriteData::bvid) }.isFailure)
        assertEquals(listOf("BV1"), published.last())
        assertEquals(listOf("BV1", "BV2"), loader.loadFolder(1L).getOrThrow().map { it.bvid })
        assertEquals(listOf(1, 2, 2), requested)
    }

    @Test
    fun `index keeps first page when a later page of same folder fails`() = runTest {
        val source = FakeListenVideoDataSource(folderPages = mapOf(1L to mapOf(
            1 to Result.success(page("BV1", hasMore = true)),
            2 to Result.failure(IOException("offline")),
        )))
        val result = ListenVideoLibraryLoader(source, minimumRequestIntervalMillis = 0L)
            .indexFolders(listOf(FavFolder(id = 1L)))
        assertEquals(listOf("BV1"), result.resources.map { it.bvid })
        assertEquals(setOf(1L), result.failedFolderIds)
    }

    @Test
    fun `repeated pagination stops instead of loading indefinitely`() = runTest {
        val source = FakeListenVideoDataSource(folderPages = mapOf(1L to mapOf(
            1 to Result.success(page("BV1", hasMore = true)),
            2 to Result.success(page("BV1", hasMore = true)),
        )))
        val result = ListenVideoLibraryLoader(source, minimumRequestIntervalMillis = 0L).loadFolder(1L)
        assertTrue(result.isFailure)
        assertEquals(listOf(1, 2), source.requestedFolderPages)
    }

    @Test
    fun `switching between albums and artists does not restart active index`() = runTest {
        var folderRequests = 0
        val source = object : FakeListenVideoDataSource() {
            override suspend fun ownedFolders(mid: Long) = Result.success(
                listOf(FavFolder(id = 1L, title = "Large folder", cover = "cover")),
            )
            override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
                folderRequests++
                awaitCancellation()
            }
        }
        val viewModel = createViewModel(source, mid = 42L)
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.selectSection(ListenVideoSection.ALBUMS)
        runCurrent()
        viewModel.selectSection(ListenVideoSection.ARTISTS)
        runCurrent()
        assertEquals(1, folderRequests)
        assertTrue(viewModel.uiState.value.isIndexing)
        viewModel.pauseBackgroundLoading()
        runCurrent()
        assertFalse(viewModel.uiState.value.isIndexing)
    }

    @Test
    fun `index emits initial batch promptly then batches remaining pages per folder`() = runTest {
        val source = FakeListenVideoDataSource(
            folderPages = mapOf(
                1L to mapOf(
                    1 to Result.success(page("BV1", hasMore = true)),
                    2 to Result.success(page("BV2", hasMore = true)),
                    3 to Result.success(page("BV3", hasMore = false)),
                )
            )
        )
        val emittedSizes = mutableListOf<Int>()
        val result = ListenVideoLibraryLoader(source, minimumRequestIntervalMillis = 0L).indexFolders(
            folders = listOf(FavFolder(id = 1L, title = "MultiPage")),
            onResourcesIndexed = { emittedSizes += it.size },
        )

        assertEquals(listOf(1, 3), emittedSizes)
        assertEquals(listOf("BV1", "BV2", "BV3"), result.resources.map { it.bvid })
    }

    private fun page(bvid: String, hasMore: Boolean): FavoriteResourceData {
        return FavoriteResourceData(
            medias = listOf(
                FavoriteData(
                    id = bvid.hashCode().toLong(),
                    bvid = bvid,
                    title = bvid,
                    upper = Upper(mid = 7L, name = "Artist")
                )
            ),
            has_more = hasMore
        )
    }

    private fun coverPage(vararg covers: String): FavoriteResourceData {
        return FavoriteResourceData(
            medias = covers.mapIndexed { index, cover ->
                FavoriteData(
                    id = index.toLong(),
                    bvid = "BV$index",
                    title = "Track $index",
                    cover = cover,
                    upper = Upper(mid = 7L, name = "Artist")
                )
            },
            has_more = false
        )
    }
}

private open class FakeListenVideoDataSource(
    private val folderPages: Map<Long, Map<Int, Result<FavoriteResourceData>>> = emptyMap()
) : ListenVideoLibraryDataSource {
    var ownedFolderRequests = 0
        private set
    val requestedFolderPages = mutableListOf<Int>()
    val requestedFolderIds = mutableListOf<Long>()

    override suspend fun ownedFolders(mid: Long): Result<List<FavFolder>> {
        ownedFolderRequests += 1
        return Result.success(emptyList())
    }

    override suspend fun collectedFolders(mid: Long, page: Int): Result<ListenVideoCollectedFoldersPage> {
        return Result.success(ListenVideoCollectedFoldersPage(emptyList(), hasMore = false))
    }

    override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
        requestedFolderIds += mediaId
        requestedFolderPages += page
        return folderPages[mediaId]?.get(page)
            ?: Result.failure(IllegalStateException("missing folder page $mediaId/$page"))
    }

    override suspend fun albumPage(seasonId: Long, page: Int): Result<FavoriteResourceData> {
        return Result.failure(IllegalStateException("unused"))
    }
}

private class RestartingListenVideoDataSource : ListenVideoLibraryDataSource {
    private var ownedCalls = 0
    var firstRequestCancelled = false
        private set

    override suspend fun ownedFolders(mid: Long): Result<List<FavFolder>> {
        ownedCalls += 1
        if (ownedCalls == 1) {
            try {
                awaitCancellation()
            } finally {
                firstRequestCancelled = true
            }
        }
        return Result.success(listOf(FavFolder(id = 1L, title = "Latest")))
    }

    override suspend fun collectedFolders(mid: Long, page: Int): Result<ListenVideoCollectedFoldersPage> {
        return Result.success(ListenVideoCollectedFoldersPage(emptyList(), hasMore = false))
    }

    override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
        return Result.success(FavoriteResourceData())
    }

    override suspend fun albumPage(seasonId: Long, page: Int): Result<FavoriteResourceData> {
        return Result.success(FavoriteResourceData())
    }
}

private class SerialFavoriteRequestDataSource : ListenVideoLibraryDataSource {
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val startedFolderIds = mutableListOf<Long>()

    override suspend fun ownedFolders(mid: Long): Result<List<FavFolder>> = Result.success(emptyList())

    override suspend fun collectedFolders(mid: Long, page: Int): Result<ListenVideoCollectedFoldersPage> {
        return Result.success(ListenVideoCollectedFoldersPage(emptyList(), hasMore = false))
    }

    override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
        startedFolderIds += mediaId
        if (mediaId == 1L) {
            firstStarted.complete(Unit)
            releaseFirst.await()
        }
        return Result.success(FavoriteResourceData(has_more = false))
    }

    override suspend fun albumPage(seasonId: Long, page: Int): Result<FavoriteResourceData> {
        return Result.failure(IllegalStateException("unused"))
    }
}

private class RetryDetailListenVideoDataSource : ListenVideoLibraryDataSource {
    var detailRequests = 0
        private set

    override suspend fun ownedFolders(mid: Long): Result<List<FavFolder>> {
        return Result.success(listOf(FavFolder(id = 9L, title = "Retry playlist")))
    }

    override suspend fun collectedFolders(mid: Long, page: Int): Result<ListenVideoCollectedFoldersPage> {
        return Result.success(ListenVideoCollectedFoldersPage(emptyList(), hasMore = false))
    }

    override suspend fun folderPage(mediaId: Long, page: Int): Result<FavoriteResourceData> {
        detailRequests += 1
        if (detailRequests <= 2) return Result.failure(IOException("preview and first detail failure"))
        return Result.success(
            FavoriteResourceData(
                medias = listOf(
                    FavoriteData(
                        bvid = "BV-RETRY",
                        title = "Recovered",
                        upper = Upper(mid = 7L, name = "Artist")
                    )
                )
            )
        )
    }

    override suspend fun albumPage(seasonId: Long, page: Int): Result<FavoriteResourceData> {
        return Result.failure(IllegalStateException("unused"))
    }
}
