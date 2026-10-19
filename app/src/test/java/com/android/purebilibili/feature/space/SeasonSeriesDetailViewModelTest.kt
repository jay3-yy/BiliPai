package com.android.purebilibili.feature.space

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.data.model.response.FavoriteInfo
import com.android.purebilibili.data.model.response.FavoriteResourceData
import com.android.purebilibili.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonSeriesDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var viewModel: SeasonSeriesDetailViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(TokenManager, NetworkModule, FavoriteRepository)
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
        every { TokenManager.accountIdentity } returns MutableStateFlow(AccountSessionIdentity(101L, 1L))
        every { TokenManager.midCache } returns 101L
        every { NetworkModule.spaceApi } returns mockk(relaxed = true)
        viewModel = SeasonSeriesDetailViewModel(mockk<Application>(relaxed = true))
        store.put("detail", viewModel)
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `subscribed season continues beyond forty videos until server end`() = runTest(dispatcher) {
        runCurrent()
        for (page in 1..4) {
            val first = (page - 1) * 20 + 1
            val last = minOf(page * 20, 65)
            coEvery { FavoriteRepository.getFavoriteSeasonList(900L, page) } returns
                Result.success(resources(first..last, hasMore = page < 4))
        }
        viewModel.init("favorite_season", 900L, 202L, "教程")
        runCurrent()
        repeat(3) {
            assertTrue(viewModel.hasMoreState.value)
            viewModel.loadMore()
            runCurrent()
        }

        assertEquals((1L..65L).toList(), viewModel.uiState.value.items.map { it.aid })
        assertFalse(viewModel.hasMoreState.value)
        assertEquals(4, viewModel.favoriteDetailProgressState.value.currentPage)
        assertNull(viewModel.uiState.value.loadMoreError)
    }

    @Test
    fun `failed next page preserves contents and retries the same page explicitly`() = runTest(dispatcher) {
        runCurrent()
        coEvery { FavoriteRepository.getFavoriteSeasonList(900L, 1) } returns
            Result.success(resources(1..20, hasMore = true))
        coEvery { FavoriteRepository.getFavoriteSeasonList(900L, 2) } returns
            Result.failure(IllegalStateException("第二页失败"))
        viewModel.init("favorite_season", 900L, 202L, "教程")
        runCurrent()
        val firstPage = viewModel.uiState.value.items
        viewModel.loadMore()
        runCurrent()

        assertEquals(firstPage, viewModel.uiState.value.items)
        assertEquals("第二页失败", viewModel.uiState.value.loadMoreError)
        assertTrue(viewModel.hasMoreState.value)
        assertEquals(1, viewModel.favoriteDetailProgressState.value.currentPage)
        viewModel.loadMore()
        runCurrent()
        coVerify(exactly = 1) { FavoriteRepository.getFavoriteSeasonList(900L, 2) }

        coEvery { FavoriteRepository.getFavoriteSeasonList(900L, 2) } returns
            Result.success(resources(21..25, hasMore = false))
        viewModel.retryLoadMore()
        runCurrent()
        assertEquals((1L..25L).toList(), viewModel.uiState.value.items.map { it.aid })
        assertNull(viewModel.uiState.value.loadMoreError)
        assertEquals(2, viewModel.favoriteDetailProgressState.value.currentPage)
        assertFalse(viewModel.hasMoreState.value)
        coVerify(exactly = 2) { FavoriteRepository.getFavoriteSeasonList(900L, 2) }
    }

    @Test
    fun `first page failure remains an error rather than a successful empty collection`() = runTest(dispatcher) {
        runCurrent()
        coEvery { FavoriteRepository.getFavoriteSeasonList(900L, 1) } returns
            Result.failure(IllegalStateException("合集请求失败"))
        viewModel.init("favorite_season", 900L, 202L, "教程")
        runCurrent()
        assertEquals("合集请求失败", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)

        coEvery { FavoriteRepository.getFavoriteSeasonList(900L, 1) } returns
            Result.success(resources(1..2, hasMore = false))
        viewModel.loadData()
        runCurrent()
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.items.map { it.aid })
    }

    @Test
    fun `season ids never reach favorite folder mutation endpoints`() = runTest(dispatcher) {
        runCurrent()
        coEvery { FavoriteRepository.getFavoriteSeasonList(900L, 1) } returns
            Result.success(resources(1..2, hasMore = false))
        viewModel.init("favorite_season", 900L, 101L, "自己的合集")
        runCurrent()
        assertFalse(viewModel.uiState.value.canRemoveItems)
        assertFalse(viewModel.canCopyFavoriteResources)
        viewModel.removeFavoriteResources(setOf(1L))
        viewModel.copyOrMoveFavoriteResources(setOf(1L), targetMediaId = 800L, copy = false)
        viewModel.copyOrMoveFavoriteResources(setOf(1L), targetMediaId = 800L, copy = true)
        runCurrent()
        coVerify(exactly = 0) { FavoriteRepository.removeResources(any(), any()) }
        coVerify(exactly = 0) { FavoriteRepository.copyOrMoveResources(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `server owner prevents a foreign favorite folder from being removed or moved`() = runTest(dispatcher) {
        runCurrent()
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 1) } returns
            Result.success(resources(1..2, hasMore = false).copy(info = FavoriteInfo(mid = 202L)))
        viewModel.init("favorite", 900L, 101L, "他人的收藏夹")
        runCurrent()
        assertTrue(viewModel.canCopyFavoriteResources)
        assertFalse(viewModel.uiState.value.canRemoveItems)
        viewModel.removeFavoriteResources(setOf(1L))
        viewModel.copyOrMoveFavoriteResources(setOf(1L), targetMediaId = 800L, copy = false)
        runCurrent()
        coVerify(exactly = 0) { FavoriteRepository.removeResources(any(), any()) }
        coVerify(exactly = 0) { FavoriteRepository.copyOrMoveResources(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `oldest first traverses forty one resources from last page and explicitly retries failures`() = runTest(dispatcher) {
        runCurrent()
        val info = FavoriteInfo(id = 900L, mid = 101L, title = "收藏夹", media_count = 41)
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 1) } returns
            Result.success(resources(41 downTo 22, hasMore = true).copy(info = info))
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 3) } returns
            Result.success(resources(1..1, hasMore = false).copy(info = info))
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 2) } returns
            Result.failure(IllegalStateException("中间页失败"))
        viewModel.init("favorite", 900L, 101L, "收藏夹")
        runCurrent()
        assertFalse(viewModel.changeFavoriteDetailSortOrder(FavoriteDetailSortOrder.NEWEST_FIRST))
        assertTrue(viewModel.changeFavoriteDetailSortOrder(FavoriteDetailSortOrder.OLDEST_FIRST))
        assertFalse(viewModel.changeFavoriteDetailSortOrder(FavoriteDetailSortOrder.NEWEST_FIRST))
        runCurrent()
        assertFalse(viewModel.changeFavoriteDetailSortOrder(FavoriteDetailSortOrder.OLDEST_FIRST))

        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.aid })
        assertEquals(3, viewModel.favoriteDetailProgressState.value.currentPage)
        assertEquals(41, viewModel.favoriteDetailProgressState.value.expectedCount)
        assertTrue(viewModel.hasMoreState.value)
        coVerify(exactly = 0) { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 2) }

        viewModel.loadMore()
        runCurrent()
        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.aid })
        assertEquals("中间页失败", viewModel.uiState.value.loadMoreError)
        assertEquals(3, viewModel.favoriteDetailProgressState.value.currentPage)
        viewModel.loadMore()
        runCurrent()
        coVerify(exactly = 1) { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 2) }

        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 2) } returns
            Result.success(resources(21 downTo 2, hasMore = true).copy(info = info))
        viewModel.retryLoadMore()
        runCurrent()
        assertEquals((1L..21L).toList(), viewModel.uiState.value.items.map { it.aid })
        assertNull(viewModel.uiState.value.loadMoreError)
        assertEquals(2, viewModel.favoriteDetailProgressState.value.currentPage)
        viewModel.loadMore()
        runCurrent()

        assertEquals((1L..41L).toList(), viewModel.uiState.value.items.map { it.aid })
        assertEquals(41, viewModel.favoriteDetailProgressState.value.loadedCount)
        assertEquals(1, viewModel.favoriteDetailProgressState.value.currentPage)
        assertFalse(viewModel.hasMoreState.value)
        coVerify(exactly = 2) { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 2) }
        coVerify(exactly = 1) { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 3) }
    }

    @Test
    fun `editor loads complete folder metadata instead of resource list summary`() = runTest(dispatcher) {
        runCurrent()
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 1) } returns
            Result.success(resources(1..2, hasMore = false).copy(info = FavoriteInfo(mid = 101L)))
        val complete = FavoriteInfo(
            id = 900L,
            mid = 101L,
            title = "完整标题",
            cover = "https://example.com/cover.jpg",
            intro = "完整简介",
            media_count = 2,
            attr = 3,
        )
        coEvery { FavoriteRepository.getFolderInfo(900L) } returns Result.success(complete)
        viewModel.init("favorite", 900L, 202L, "路由标题")
        runCurrent()
        assertTrue(viewModel.canEditFavoriteFolder)
        var opened = false
        viewModel.loadFavoriteFolderForEditing { opened = true }
        assertTrue(viewModel.isManagingState.value)
        assertFalse(opened)
        runCurrent()

        assertTrue(opened)
        assertFalse(viewModel.isManagingState.value)
        assertEquals(complete, viewModel.favoriteFolderInfo.value)
        assertEquals("完整标题", viewModel.uiState.value.title)
        coVerify(exactly = 1) { FavoriteRepository.getFolderInfo(900L) }
    }

    @Test
    fun `private folder cannot be posted to dynamics`() = runTest(dispatcher) {
        runCurrent()
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 1) } returns
            Result.success(resources(1..2, false).copy(info = FavoriteInfo(id = 900L, mid = 101L, attr = 1)))
        viewModel.init("favorite", 900L, 101L, "私密收藏")
        runCurrent()
        assertFalse(viewModel.canShareFavoriteFolder)
        viewModel.shareFavoriteFolderToDynamic()
        runCurrent()
        coVerify(exactly = 0) { FavoriteRepository.shareFolderToDynamic(any(), any()) }
        assertFalse(viewModel.isManagingState.value)
    }

    @Test
    fun `sharing rechecks remote privacy before posting and releases busy state on refusal`() = runTest(dispatcher) {
        runCurrent()
        val info = FavoriteInfo(id = 900L, mid = 101L, attr = 0)
        coEvery { FavoriteRepository.getFavoriteList(mediaId = 900L, pn = 1) } returns
            Result.success(resources(1..2, false).copy(info = info))
        coEvery { FavoriteRepository.getFolderInfo(900L) } returns Result.success(info.copy(attr = 1))
        viewModel.init("favorite", 900L, 101L, "公开收藏")
        runCurrent()
        assertTrue(viewModel.canShareFavoriteFolder)
        viewModel.shareFavoriteFolderToDynamic()
        assertTrue(viewModel.isManagingState.value)
        runCurrent()
        assertFalse(viewModel.canShareFavoriteFolder)
        assertFalse(viewModel.isManagingState.value)
        coVerify(exactly = 0) { FavoriteRepository.shareFolderToDynamic(any(), any()) }
    }

    private fun resources(ids: IntProgression, hasMore: Boolean) = FavoriteResourceData(
        medias = ids.map { FavoriteData(id = it.toLong(), bvid = "BV$it", title = "视频$it", type = 2) },
        has_more = hasMore,
    )
}
