package com.android.purebilibili.feature.bangumi

import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.repository.BangumiRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BangumiDetailEntryLoadTest {
    @Test
    fun `detail entry requests the season without loading index timeline or following pages`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        mockkObject(NetworkModule)
        every { NetworkModule.bangumiApi } returns mockk(relaxed = true)
        mockkObject(TokenManager, BangumiRepository)
        every { TokenManager.midCache } returns 1L
        every { TokenManager.sessDataCache } returns "session"
        coEvery { BangumiRepository.getSeasonDetail(any(), any()) } coAnswers { awaitCancellation() }
        coEvery { BangumiRepository.getBangumiIndexWithFilter(any(), any(), any()) } coAnswers { awaitCancellation() }
        coEvery { BangumiRepository.getTimeline(any(), any(), any()) } coAnswers { awaitCancellation() }
        coEvery { BangumiRepository.getMyFollowBangumi(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        val store = ViewModelStore()
        try {
            val model = BangumiViewModel().also { store.put("detail", it) }
            model.loadSeasonDetail(42)
            runCurrent()
            coVerify(exactly = 1) { BangumiRepository.getSeasonDetail(42, 0) }
            coVerify(exactly = 0) { BangumiRepository.getBangumiIndexWithFilter(any(), any(), any()) }
            coVerify(exactly = 0) { BangumiRepository.getTimeline(any(), any(), any()) }
            coVerify(exactly = 0) { BangumiRepository.getMyFollowBangumi(any(), any(), any(), any(), any()) }
        } finally {
            store.clear()
            runCurrent()
            unmockkAll()
            Dispatchers.resetMain()
        }
    }
}
