package com.android.purebilibili.feature.list

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavFolderSource
import com.android.purebilibili.data.repository.ActionRepository
import com.android.purebilibili.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteSubscribedFoldersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val identities = MutableStateFlow(AccountSessionIdentity(101L, 1L))
    private val store = ViewModelStore()
    private lateinit var viewModel: FavoriteViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(TokenManager, FavoriteRepository, ActionRepository, SettingsManager)
        every { TokenManager.accountIdentity } returns identities
        coEvery { SettingsManager.setCollectionSubscription(any(), any(), any()) } returns Unit
        viewModel = FavoriteViewModel(mockk<Application>(relaxed = true))
        store.put("favorites", viewModel)
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `subscriptions initialize even when owned folder catalog fails`() = runTest(dispatcher) {
        val folder = subscribedFolder()
        coEvery { FavoriteRepository.getFavFolders(101L) } returns
            Result.failure(IllegalStateException("自建目录失败"))
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(listOf(folder), 1))

        viewModel.setPageActive(true)
        runCurrent()

        assertEquals(listOf(folder), viewModel.subscribedFolders.value)
        assertEquals("自建目录失败", viewModel.uiState.value.error)
        assertNull(viewModel.subscribedFolderProgressState.value.error)
        coVerify(exactly = 1) { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") }
    }

    @Test
    fun `initial failure stays observable until explicit same page retry`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<FavoriteRepository.CollectedFavFoldersPage>>()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } coAnswers {
            response.await()
        }
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        assertTrue(viewModel.subscribedFolderProgressState.value.isLoadingMore)

        response.complete(Result.failure(IllegalStateException("首屏失败")))
        runCurrent()
        val failed = viewModel.subscribedFolderProgressState.value
        assertEquals("首屏失败", failed.error)
        assertEquals(0, failed.loadedCount)
        assertEquals(1, failed.currentPage)
        assertFalse(failed.isLoadingMore)
        assertTrue(viewModel.subscribedFolders.value.isEmpty())

        viewModel.ensureSubscribedFoldersLoaded()
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        coVerify(exactly = 1) { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") }

        val folder = subscribedFolder()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(listOf(folder), 1))
        viewModel.retrySubscribedFolders()
        runCurrent()
        assertEquals(listOf(folder), viewModel.subscribedFolders.value)
        assertNull(viewModel.subscribedFolderProgressState.value.error)
        coVerify(exactly = 2) { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") }
    }

    @Test
    fun `pagination failure retains items page and hasMore and retries page two`() = runTest(dispatcher) {
        val first = subscribedFolder()
        val second = subscribedFolder().copy(id = 13L, fid = 13L)
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(listOf(first), 2))
        val response = CompletableDeferred<Result<FavoriteRepository.CollectedFavFoldersPage>>()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") } coAnswers {
            response.await()
        }
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        val before = viewModel.subscribedFolderProgressState.value

        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        assertTrue(viewModel.subscribedFolderProgressState.value.isLoadingMore)
        response.complete(Result.failure(IllegalStateException("第二页失败")))
        runCurrent()
        assertEquals(listOf(first), viewModel.subscribedFolders.value)
        assertEquals(before.copy(error = "第二页失败"), viewModel.subscribedFolderProgressState.value)
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        coVerify(exactly = 1) { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") }

        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") } returns
            Result.success(page(listOf(second), 2))
        viewModel.retrySubscribedFolders()
        runCurrent()
        assertEquals(listOf(first, second), viewModel.subscribedFolders.value)
        assertEquals(2, viewModel.subscribedFolderProgressState.value.currentPage)
        assertFalse(viewModel.subscribedFolderProgressState.value.hasMore)
        assertNull(viewModel.subscribedFolderProgressState.value.error)
        coVerify(exactly = 2) { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") }
    }

    @Test
    fun `full overlapping page without total does not truncate later pages`() = runTest(dispatcher) {
        val folders = (1L..20L).map { subscribedFolder().copy(id = it, fid = it) }
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(folders, 0))
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") } returns
            Result.success(page(folders.drop(1) + subscribedFolder().copy(id = 21L, fid = 21L), 0))
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 3, 20, "web") } returns
            Result.success(page(listOf(subscribedFolder().copy(id = 22L, fid = 22L)), 0))

        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        assertEquals(21, viewModel.subscribedFolders.value.size)
        assertTrue(viewModel.subscribedFolderProgressState.value.hasMore)
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        assertEquals(22, viewModel.subscribedFolders.value.size)
        assertFalse(viewModel.subscribedFolderProgressState.value.hasMore)
    }

    @Test
    fun `cancel retains row and changes state only after success then resubscribes`() = runTest(dispatcher) {
        val folder = subscribedFolder().copy(fav_state = 0)
        val key = resolveSubscribedFavoriteFolderKey(folder)
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(listOf(folder), 1))
        val cancelled = CompletableDeferred<Result<Boolean>>()
        coEvery { FavoriteRepository.setFolderSubscription(1234L, false) } coAnswers { cancelled.await() }
        coEvery { FavoriteRepository.setFolderSubscription(1234L, true) } returns Result.success(true)
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()

        viewModel.toggleSubscribedFolderSubscription(folder)
        viewModel.toggleSubscribedFolderSubscription(folder)
        assertTrue(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertTrue(viewModel.subscribedFolderActions.value.getValue(key).isLoading)
        runCurrent()
        coVerify(exactly = 1) { FavoriteRepository.setFolderSubscription(1234L, false) }
        cancelled.complete(Result.success(false))
        runCurrent()
        assertFalse(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertEquals(listOf(folder), viewModel.subscribedFolders.value)

        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        assertTrue(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertEquals(listOf(folder), viewModel.subscribedFolders.value)
        coVerify(exactly = 1) { FavoriteRepository.setFolderSubscription(1234L, true) }
    }

    @Test
    fun `failed cancellation and failed resubscription keep prior successful state`() = runTest(dispatcher) {
        val folder = subscribedFolder()
        val key = resolveSubscribedFavoriteFolderKey(folder)
        coEvery { FavoriteRepository.setFolderSubscription(1234L, false) } returns
            Result.failure(IllegalStateException("取消失败"))
        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        assertTrue(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertEquals("取消失败", viewModel.subscribedFolderActions.value.getValue(key).error)
        assertFalse(viewModel.subscribedFolderActions.value.getValue(key).isLoading)

        coEvery { FavoriteRepository.setFolderSubscription(1234L, false) } returns Result.success(false)
        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        coEvery { FavoriteRepository.setFolderSubscription(1234L, true) } returns
            Result.failure(IllegalStateException("恢复失败"))
        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        assertFalse(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertEquals("恢复失败", viewModel.subscribedFolderActions.value.getValue(key).error)
    }

    @Test
    fun `type 21 actions use season id and persist each successful state`() = runTest(dispatcher) {
        val folder = subscribedFolder().copy(type = 21)
        coEvery { ActionRepository.setCollectionSubscription(folder.id, false) } returns Result.success(false)
        coEvery { ActionRepository.setCollectionSubscription(folder.id, true) } returns Result.success(true)

        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        coVerify(exactly = 1) { SettingsManager.setCollectionSubscription(any(), folder.id, false) }
        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        coVerify(exactly = 1) { SettingsManager.setCollectionSubscription(any(), folder.id, true) }
        coVerify(exactly = 0) { FavoriteRepository.setFolderSubscription(any(), any()) }
    }

    @Test
    fun `account switch clears action state and rejects a late old account response`() = runTest(dispatcher) {
        val folder = subscribedFolder().copy(type = 21)
        val response = CompletableDeferred<Result<Boolean>>()
        coEvery { ActionRepository.setCollectionSubscription(folder.id, false) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        identities.value = AccountSessionIdentity(202L, 2L)
        runCurrent()
        response.complete(Result.success(false))
        runCurrent()

        assertTrue(viewModel.subscribedFolderActions.value.isEmpty())
        assertTrue(viewModel.subscribedFolders.value.isEmpty())
        assertEquals(FavoriteViewModel.SubscribedFolderProgressState(), viewModel.subscribedFolderProgressState.value)
        coVerify(exactly = 0) { SettingsManager.setCollectionSubscription(any(), any(), any()) }
    }

    @Test
    fun `account switch rejects a late old catalog response and new account can load`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<FavoriteRepository.CollectedFavFoldersPage>>()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        identities.value = AccountSessionIdentity(202L, 2L)
        runCurrent()
        response.complete(Result.success(page(listOf(subscribedFolder()), 1)))
        runCurrent()
        assertTrue(viewModel.subscribedFolders.value.isEmpty())

        val newFolder = subscribedFolder().copy(id = 99L)
        coEvery { FavoriteRepository.getCollectedFavFolders(202L, 1, 20, "web") } returns
            Result.success(page(listOf(newFolder), 1))
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        assertEquals(listOf(newFolder), viewModel.subscribedFolders.value)
    }

    @Test
    fun `cancellation recalibrates offset pages and discards an older in flight page`() = runTest(dispatcher) {
        fun folders(ids: LongRange) = ids.map {
            subscribedFolder().copy(id = it, fid = it, type = 21)
        }
        val firstPage = folders(1L..20L)
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(firstPage, 40))
        val oldPage = CompletableDeferred<Result<FavoriteRepository.CollectedFavFoldersPage>>()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") } coAnswers {
            oldPage.await()
        }
        coEvery { ActionRepository.setCollectionSubscription(1L, false) } returns Result.success(false)
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        viewModel.loadMoreSubscribedFolders()
        runCurrent()

        viewModel.toggleSubscribedFolderSubscription(firstPage.first())
        runCurrent()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(folders(2L..21L), 39))
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        oldPage.complete(Result.success(page(folders(21L..40L), 40)))
        runCurrent()
        assertEquals((1L..21L).toList(), viewModel.subscribedFolders.value.map { it.id })
        assertEquals(1, viewModel.subscribedFolderProgressState.value.currentPage)
        assertTrue(viewModel.subscribedFolderProgressState.value.hasMore)

        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 2, 20, "web") } returns
            Result.success(page(folders(22L..40L), 39))
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        assertEquals((1L..40L).toList(), viewModel.subscribedFolders.value.map { it.id })
        assertFalse(viewModel.subscribedFolderProgressState.value.hasMore)
        assertFalse(viewModel.subscribedFolderActions.value.getValue(
            resolveSubscribedFavoriteFolderKey(firstPage.first())
        ).isSubscribed)
    }

    @Test
    fun `retrying recalibration page one retains the cancelled undo row`() = runTest(dispatcher) {
        val firstPage = (1L..20L).map {
            subscribedFolder().copy(id = it, fid = it, type = 21)
        }
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(firstPage, 40))
        coEvery { ActionRepository.setCollectionSubscription(1L, false) } returns Result.success(false)
        viewModel.ensureSubscribedFoldersLoaded()
        runCurrent()
        viewModel.toggleSubscribedFolderSubscription(firstPage.first())
        runCurrent()
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.failure(IllegalStateException("重新校准失败"))
        viewModel.loadMoreSubscribedFolders()
        runCurrent()
        assertEquals("重新校准失败", viewModel.subscribedFolderProgressState.value.error)

        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page((2L..21L).map {
                subscribedFolder().copy(id = it, fid = it, type = 21)
            }, 39))
        viewModel.retrySubscribedFolders()
        runCurrent()
        assertEquals((1L..21L).toList(), viewModel.subscribedFolders.value.map { it.id })
        assertNull(viewModel.subscribedFolderProgressState.value.error)
        assertFalse(viewModel.subscribedFolderActions.value.getValue(
            resolveSubscribedFavoriteFolderKey(firstPage.first())
        ).isSubscribed)
    }

    @Test
    fun `search cancellation and source page resubscription stay synchronized`() = runTest(dispatcher) {
        val folder = subscribedFolder()
        val key = resolveSubscribedFavoriteFolderKey(folder)
        val searchViewModel = FavoriteViewModel(mockk<Application>(relaxed = true))
        store.put("search", searchViewModel)
        coEvery { FavoriteRepository.getCollectedFavFolders(101L, 1, 20, "web") } returns
            Result.success(page(listOf(folder), 1))
        coEvery { FavoriteRepository.setFolderSubscription(1234L, false) } returns Result.success(false)
        coEvery { FavoriteRepository.setFolderSubscription(1234L, true) } returns Result.success(true)
        viewModel.ensureSubscribedFoldersLoaded()
        searchViewModel.ensureSubscribedFoldersLoaded()
        runCurrent()

        searchViewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        assertFalse(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertEquals(listOf(folder), viewModel.subscribedFolders.value)
        assertFalse(searchViewModel.subscribedFolderActions.value.getValue(key).isSubscribed)

        viewModel.toggleSubscribedFolderSubscription(folder)
        runCurrent()
        assertTrue(viewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertTrue(searchViewModel.subscribedFolderActions.value.getValue(key).isSubscribed)
        assertEquals(listOf(folder), searchViewModel.subscribedFolders.value)
        coVerify(exactly = 1) { FavoriteRepository.setFolderSubscription(1234L, true) }
    }

    private fun subscribedFolder() = FavFolder(
        id = 12L,
        fid = 12L,
        mid = 34L,
        title = "订阅目录",
        type = 11,
        source = FavFolderSource.SUBSCRIBED,
    )

    private fun page(folders: List<FavFolder>, total: Int) =
        FavoriteRepository.CollectedFavFoldersPage(folders, total)
}
