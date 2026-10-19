package com.android.purebilibili.feature.list

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavoriteInfo
import com.android.purebilibili.data.model.response.FavoriteResourceData
import com.android.purebilibili.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteFolderBatchManagementTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var viewModel: FavoriteViewModel
    private var catalog = listOf(1L, 2L, 3L).map { FavFolder(id = it, mid = 101, title = "收藏$it") }

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(TokenManager, FavoriteRepository)
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
        every { TokenManager.accountIdentity } returns MutableStateFlow(AccountSessionIdentity(101, 1))
        every { TokenManager.midCache } returns 101
        coEvery { FavoriteRepository.getFavFolders(101) } coAnswers { Result.success(catalog) }
        coEvery { FavoriteRepository.getCollectedFavFolders(101, 1, 20, "web") } returns
            Result.success(FavoriteRepository.CollectedFavFoldersPage(emptyList(), 0))
        coEvery { FavoriteRepository.getFavoriteList(mediaId = any(), pn = any(), order = any()) } returns
            Result.success(FavoriteResourceData(info = FavoriteInfo(media_count = 0), medias = emptyList()))
        viewModel = FavoriteViewModel(mockk<Application>(relaxed = true))
        store.put("favorites", viewModel)
    }

    @AfterEach fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test fun `default folder in selection prevents every delete request`() = runTest(dispatcher) {
        viewModel.setPageActive(true)
        runCurrent()
        assertEquals(listOf(1L, 2L, 3L), viewModel.folders.value.map { it.id })
        viewModel.deleteFavoriteFolders(setOf(1, 2)) {}
        runCurrent()
        assertFalse(viewModel.isFavoriteManagingState.value)
        assertEquals(listOf(1L, 2L, 3L), viewModel.folders.value.map { it.id })
        coVerify(exactly = 0) { FavoriteRepository.deleteFolder(any()) }
    }

    @Test fun `partial batch refreshes successful deletion while rejecting changed server ownership`() = runTest(dispatcher) {
        viewModel.setPageActive(true)
        runCurrent()
        coEvery { FavoriteRepository.getFolderInfo(2) } returns Result.success(FavoriteInfo(id = 2, mid = 101))
        coEvery { FavoriteRepository.getFolderInfo(3) } returns Result.success(FavoriteInfo(id = 3, mid = 202))
        coEvery { FavoriteRepository.deleteFolder(2) } coAnswers {
            catalog = catalog.filterNot { it.id == 2L }
            Result.success(Unit)
        }
        viewModel.deleteFavoriteFolders(setOf(2, 3)) {}
        assertTrue(viewModel.isFavoriteManagingState.value)
        runCurrent()
        assertFalse(viewModel.isFavoriteManagingState.value)
        assertEquals(listOf(1L, 3L), viewModel.folders.value.map { it.id })
        coVerify(exactly = 0) { FavoriteRepository.deleteFolder(3) }
    }
}
