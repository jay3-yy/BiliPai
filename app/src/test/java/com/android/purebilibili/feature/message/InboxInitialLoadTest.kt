package com.android.purebilibili.feature.message

import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.repository.ImSession
import com.android.purebilibili.data.repository.ImSessionPage
import com.android.purebilibili.data.repository.MessageCenterUnread
import com.android.purebilibili.data.repository.MessageImRepository
import com.android.purebilibili.data.repository.MessageRepository
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class InboxInitialLoadTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(NetworkModule)
        every { NetworkModule.api } returns mockk(relaxed = true)
        every { NetworkModule.messageApi } returns mockk(relaxed = true)
        mockkObject(TokenManager, MessageRepository, MessageImRepository)
        every { TokenManager.accountIdentity } returns MutableStateFlow(AccountSessionIdentity(1, 1))
        coEvery { MessageImRepository.sessions(any(), any()) } returns Result.success(page(10))
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `sessions appear while unread summary is pending`() = runTest(dispatcher) {
        val summary = CompletableDeferred<Result<MessageCenterUnread>>()
        coEvery { MessageRepository.getMessageCenterUnread() } coAnswers { summary.await() }
        val model = InboxViewModel().also { store.put("inbox", it) }
        runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(10L, model.uiState.value.sessions.single().talker_id)
        assertNull(model.uiState.value.feedUnreadData)

        summary.complete(Result.success(MessageCenterUnread(6, 1, 2, 3, 0)))
        runCurrent()
        assertEquals(2, model.uiState.value.feedUnreadData?.at)
        assertEquals(10L, model.uiState.value.sessions.single().talker_id)
    }

    @Test
    fun `old unread summary cannot overwrite a refreshed list`() = runTest(dispatcher) {
        val old = CompletableDeferred<Result<MessageCenterUnread>>()
        coEvery { MessageRepository.getMessageCenterUnread() } coAnswers {
            withContext(NonCancellable) { old.await() }
        }
        val model = InboxViewModel().also { store.put("inbox", it) }
        runCurrent()
        coEvery { MessageRepository.getMessageCenterUnread() } returns Result.success(MessageCenterUnread(2, 2, 0, 0, 0))
        coEvery { MessageImRepository.sessions(any(), any()) } returns Result.success(page(20))
        model.refresh()
        runCurrent()
        old.complete(Result.success(MessageCenterUnread(99, 99, 0, 0, 0)))
        runCurrent()

        assertEquals(20L, model.uiState.value.sessions.single().talker_id)
        assertEquals(2, model.uiState.value.feedUnreadData?.reply)
        assertFalse(model.uiState.value.isRefreshing)
    }

    private fun page(mid: Long) = ImSessionPage(
        sessions = listOf(ImSession(key = mid.toString(), id = byteArrayOf(1), item = SessionItem(talker_id = mid))),
        pagination = byteArrayOf(), hasMore = false, menu = emptyList(), outside = emptyList(),
    )
}
