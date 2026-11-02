package com.android.purebilibili.feature.dynamic

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.database.AppDatabase
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.data.model.response.DynamicBasic
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.feature.comment.SentCommentReplyStores
import com.android.purebilibili.feature.video.viewmodel.CommentSortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the real send callback, subject lifecycle and paging consumers, not policy forwarding. */
@OptIn(ExperimentalCoroutinesApi::class)
class DynamicCommentSendViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val identities = MutableStateFlow(AccountSessionIdentity(101L, 1L))
    private val root = ReplyItem(
        rpid = 10L, count = 2, rcount = 2,
        replies = listOf(ReplyItem(rpid = 20L, root = 10L, parent = 10L)),
    )
    private val otherRoot = ReplyItem(rpid = 110L)
    private val subject = dynamic("1000", 3000L)
    private val otherSubject = dynamic("2000", 4000L)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(SettingsManager, TokenManager, CommentRepository, NetworkModule, AppDatabase.Companion, Logger)
        every { TokenManager.accountIdentity } returns identities
        every { TokenManager.csrfCache } returns "csrf"
        SentCommentReplyStores.clear()
        every { SettingsManager.getIncrementalTimelineRefresh(any()) } returns flowOf(false)
        every { NetworkModule.api } returns mockk(relaxed = true)
        every { AppDatabase.getDatabase(any()) } returns mockk(relaxed = true)
        every { Logger.d(any(), any<String>()) } just Runs
        every { Logger.w(any(), any<String>(), any()) } just Runs
        every { Logger.e(any(), any<String>(), any()) } just Runs
        coEvery { CommentRepository.getCommentCountForSubject(any(), any()) } returns Result.success(3)
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            Result.success(ReplyData(
                replies = listOf(if (firstArg<Long>() == 3000L) root else otherRoot),
                cursor = ReplyCursor(allCount = 3, isEnd = true),
            ))
        }
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(threadPage(listOf(ReplyItem(rpid = 20L, root = 10L)), "page2"))
        coEvery {
            CommentRepository.addCommentForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyItem(rpid = 40L))
    }

    @AfterTest
    fun tearDown() {
        SentCommentReplyStores.clear()
        unmockkObject(SettingsManager, TokenManager, CommentRepository, NetworkModule, AppDatabase.Companion, Logger)
        Dispatchers.resetMain()
    }

    @Test
    fun `main list reply callback fills omitted subject fields and immediately updates collapsed preview`() = runTest(dispatcher) {
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.startCommentReply(owner.commentUiState.value.comments.single())
            var success: Boolean? = null
            owner.postComment(subject.id_str, "sent from main") { result, _ -> success = result }
            runCurrent()

            assertEquals(true, success)
            val visibleRoot = owner.commentUiState.value.comments.single()
            val sent = visibleRoot.replies.orEmpty().first()
            assertEquals(listOf(40L, 20L), visibleRoot.replies.orEmpty().map { it.rpid })
            assertEquals(3, visibleRoot.rcount)
            assertEquals(4, owner.commentUiState.value.totalCount)
            assertEquals(3000L, sent.oid)
            assertEquals(11, sent.replyType)
            assertEquals(10L, sent.root)
            assertEquals(10L, sent.parent)
            assertFalse(owner.subReplyState.value.visible)
            coVerify(exactly = 1) {
                CommentRepository.addCommentForSubject(3000L, 11, "sent from main", 10L, 10L, emptyList(), false)
            }
            // No successful-send reload is needed to make the item visible.
            coVerify(exactly = 1) {
                CommentRepository.getMobileCommentsForSubject(3000L, 11, any(), any(), any(), any(), any())
            }
            owner.refreshComments()
            runCurrent()
            assertEquals(40L, owner.commentUiState.value.comments.single().replies.orEmpty().first().rpid)
            owner.closeCommentSheet()
            owner.openCommentSheet(subject)
            runCurrent()
            assertEquals(40L, owner.commentUiState.value.comments.single().replies.orEmpty().first().rpid)
        }
    }

    @Test
    fun `opening a newly sent dynamic reply does not require its unpropagated server target`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            if (arg<Long>(5) == 40L) Result.failure(IllegalStateException("Target not propagated"))
            else Result.success(threadPage(listOf(ReplyItem(rpid = 20L, root = 10L)), ""))
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.startCommentReply(root)
            owner.postComment(subject.id_str, "new reply") { _, _ -> }
            runCurrent()
            owner.openSubReply(owner.commentUiState.value.comments.single(), 40L)
            assertEquals(listOf(40L), owner.subReplyState.value.items.map { it.rpid })
            runCurrent()
            assertNull(owner.subReplyState.value.error)
            assertEquals(listOf(20L, 40L), owner.subReplyState.value.items.map { it.rpid })
        }
    }

    @Test
    fun `real thread send follows every subsequent page and echoed reply takes server hot rank`() = runTest(dispatcher) {
        val first = ReplyItem(rpid = 20L, root = 10L, parent = 10L)
        val second = ReplyItem(rpid = 30L, root = 10L, parent = 10L)
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val data = when (arg<String?>(4)) {
                "page2" -> threadPage(listOf(second), "page3")
                "page3" -> threadPage(listOf(ReplyItem(rpid = 40L, root = 10L, like = 7)), "")
                else -> threadPage(listOf(first), "page2")
            }
            Result.success(data)
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.openSubReply(owner.commentUiState.value.comments.single())
            runCurrent()
            assertEquals(listOf(20L), owner.subReplyState.value.items.map { it.rpid })
            owner.startCommentReply(first)
            owner.postComment(subject.id_str, "thread send") { _, _ -> }
            runCurrent()
            assertEquals(listOf(20L, 40L), owner.subReplyState.value.items.map { it.rpid })
            assertEquals(20L, owner.subReplyState.value.items.last().parent)
            owner.loadMoreSubReplies()
            runCurrent()
            assertEquals(listOf(20L, 30L, 40L), owner.subReplyState.value.items.map { it.rpid })
            owner.loadMoreSubReplies()
            runCurrent()
            assertEquals(listOf(20L, 30L, 40L), owner.subReplyState.value.items.map { it.rpid })
            assertEquals(7, owner.subReplyState.value.items.last().like)
            assertTrue(owner.subReplyState.value.isEnd)

            val echoed = ReplyItem(rpid = 40L, root = 10L, like = 100)
            coEvery {
                CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
            } returns Result.success(threadPage(listOf(echoed, first, second), ""))
            owner.setSubReplySortMode(SubReplySortMode.HOT)
            runCurrent()
            assertEquals(listOf(40L, 20L, 30L), owner.subReplyState.value.items.map { it.rpid })
            assertEquals(100, owner.subReplyState.value.items.first().like)
            assertEquals(owner.subReplyState.value.items, owner.subReplyState.value.baseItems)
            owner.closeSubReply()
            owner.openSubReply(owner.commentUiState.value.comments.single())
            runCurrent()
            assertEquals(listOf(40L, 20L, 30L), owner.subReplyState.value.items.map { it.rpid })
        }
    }

    @Test
    fun `delayed callback cannot move a reply into a different dynamic or its composer`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<ReplyItem?>>()
        coEvery {
            CommentRepository.addCommentForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { pending.await() }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.startCommentReply(root)
            var callbackCount = 0
            owner.postComment(subject.id_str, "old subject") { _, _ -> callbackCount++ }
            runCurrent()
            owner.openCommentSheet(otherSubject)
            runCurrent()
            owner.startCommentReply(otherRoot)
            val activeSession = owner.commentComposerDrafts.value.sessionId
            pending.complete(Result.success(ReplyItem(rpid = 40L)))
            runCurrent()

            assertEquals(0, callbackCount)
            assertEquals(otherSubject.id_str, owner.commentUiState.value.dynamicId)
            assertEquals(listOf(110L), owner.commentUiState.value.comments.map { it.rpid })
            assertTrue(owner.commentUiState.value.comments.single().replies.orEmpty().isEmpty())
            assertEquals(110L, owner.commentReplyTarget.value?.rootRpid)
            assertEquals(activeSession, owner.commentComposerDrafts.value.sessionId)
            assertTrue(owner.commentComposerVisible.value)
            owner.openCommentSheet(subject)
            runCurrent()
            assertEquals(listOf(20L), owner.commentUiState.value.comments.single().replies.orEmpty().map { it.rpid })
        }
    }

    @Test
    fun `account observer rejects cancellation resistant old send and clears same subject overlay`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<ReplyItem?>>()
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.startCommentReply(root)
            owner.postComment(subject.id_str, "previous successful send") { _, _ -> }
            runCurrent()
            assertEquals(40L, owner.commentUiState.value.comments.single().replies.orEmpty().first().rpid)

            coEvery {
                CommentRepository.addCommentForSubject(any(), any(), any(), any(), any(), any(), any())
            } coAnswers { withContext(NonCancellable) { pending.await() } }
            owner.startCommentReply(root)
            var callbackCount = 0
            owner.postComment(subject.id_str, "old account in flight") { _, _ -> callbackCount++ }
            runCurrent()
            identities.value = AccountSessionIdentity(202L, 2L)
            runCurrent()
            assertNull(owner.commentUiState.value.dynamicId)
            assertTrue(owner.commentUiState.value.comments.isEmpty())
            pending.complete(Result.success(ReplyItem(rpid = 50L)))
            runCurrent()
            owner.openCommentSheet(subject)
            runCurrent()

            assertEquals(0, callbackCount)
            assertEquals(listOf(20L), owner.commentUiState.value.comments.single().replies.orEmpty().map { it.rpid })
            assertEquals(3, owner.commentUiState.value.totalCount)
            assertFalse(owner.isSendingComment.value)
            owner.openSubReply(owner.commentUiState.value.comments.single())
            runCurrent()
            assertEquals(listOf(20L), owner.subReplyState.value.items.map { it.rpid })
        }
    }

    @Test
    fun `another dynamic ViewModel restores subject sends after owner closes and changes target`() = runTest(dispatcher) {
        withViewModel { firstOwner ->
            firstOwner.openCommentSheet(subject)
            runCurrent()
            firstOwner.startCommentReply(root)
            firstOwner.postComment(subject.id_str, "cross owner send") { _, _ -> }
            runCurrent()
            firstOwner.closeCommentSheet()
            firstOwner.openCommentSheet(otherSubject)
            runCurrent()
            assertTrue(firstOwner.commentUiState.value.comments.single().replies.orEmpty().isEmpty())
            withViewModel { nextOwner ->
                nextOwner.openCommentSheet(subject)
                runCurrent()
                assertEquals(listOf(40L, 20L),
                    nextOwner.commentUiState.value.comments.single().replies.orEmpty().map { it.rpid })
                nextOwner.openSubReply(nextOwner.commentUiState.value.comments.single())
                runCurrent()
                assertEquals(listOf(20L, 40L), nextOwner.subReplyState.value.items.map { it.rpid })
            }
        }
    }

    @Test
    fun `shared deletion tombstones filter stale detail pages and routed roots in another ViewModel`() = runTest(dispatcher) {
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns Result.success(Unit)
        withViewModel { firstOwner ->
            firstOwner.openCommentSheet(subject)
            runCurrent()
            firstOwner.startCommentReply(root)
            firstOwner.postComment(subject.id_str, "delete across owners") { _, _ -> }
            runCurrent()
            firstOwner.deleteDynamicComment(40L) { _, _ -> }
            runCurrent()
            coEvery {
                CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
            } returns Result.success(threadPage(
                listOf(ReplyItem(rpid = 20L, root = 10L), ReplyItem(rpid = 40L, root = 10L)), "",
            ))
            withViewModel { nextOwner ->
                nextOwner.openCommentSheet(subject)
                runCurrent()
                nextOwner.openSubReply(nextOwner.commentUiState.value.comments.single())
                runCurrent()
                assertEquals(listOf(20L), nextOwner.subReplyState.value.items.map { it.rpid })
                assertEquals(listOf(20L),
                    nextOwner.commentUiState.value.comments.single().replies.orEmpty().map { it.rpid })
                firstOwner.deleteDynamicComment(10L) { _, _ -> }
                runCurrent()
                nextOwner.refreshSubReplies()
                runCurrent()
                assertFalse(nextOwner.subReplyState.value.visible)
                nextOwner.refreshComments()
                runCurrent()
                assertTrue(nextOwner.commentUiState.value.comments.isEmpty())
                assertFalse(nextOwner.openSubReplyFromRoute(10L))
            }
        }
    }

    @Test
    fun `rootless dynamic route failure is visible and retries its target before ordinary cursor pages`() = runTest(dispatcher) {
        var fail = true
        val requests = mutableListOf<Pair<String?, Long>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val offset = arg<String?>(4)
            requests += offset to arg<Long>(5)
            if (fail) Result.failure(IllegalStateException("target unavailable"))
            else Result.success(threadPage(
                listOf(ReplyItem(rpid = if (offset == null) 91L else 92L, root = 90L)),
                if (offset == null) "next" else "",
            ).copy(root = root.copy(rpid = 90L)))
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.openSubReply(root)
            runCurrent()
            owner.openSubReplyFromRoute(90L, 91L)
            assertTrue(owner.subReplyState.value.visible)
            assertNull(owner.subReplyState.value.rootReply)
            assertTrue(owner.subReplyState.value.items.isEmpty())
            runCurrent()
            assertEquals("target unavailable", owner.subReplyState.value.error)
            fail = false
            owner.loadMoreSubReplies()
            runCurrent()
            assertEquals(90L, owner.subReplyState.value.rootReply?.rpid)
            assertEquals(listOf(91L), owner.subReplyState.value.items.map { it.rpid })
            owner.loadMoreSubReplies()
            runCurrent()
            assertEquals(listOf(null to 91L, null to 91L, "next" to 0L), requests.drop(1))
            assertEquals(listOf(91L, 92L), owner.subReplyState.value.items.map { it.rpid })
            assertNull(owner.subReplyState.value.error)
        }
    }

    @Test
    fun `new dynamic route and close discard cancellation resistant route outcomes`() = runTest(dispatcher) {
        val firstResponse = CompletableDeferred<Result<ReplyData>>()
        val secondResponse = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            withContext(NonCancellable) {
                if (arg<Long>(2) == 90L) firstResponse.await() else secondResponse.await()
            }
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.openSubReplyFromRoute(90L, 91L)
            runCurrent()
            owner.openSubReplyFromRoute(100L, 101L)
            runCurrent()
            firstResponse.complete(Result.success(threadPage(emptyList(), "").copy(root = root.copy(rpid = 90L))))
            runCurrent()
            assertTrue(owner.subReplyState.value.isLoading)
            assertNull(owner.subReplyState.value.rootReply)
            assertEquals(101L, owner.subReplyState.value.targetReplyId)
            owner.closeSubReply()
            secondResponse.complete(Result.failure(IllegalStateException("late failure")))
            runCurrent()
            assertFalse(owner.subReplyState.value.visible)
            assertFalse(owner.subReplyState.value.isLoading)
            assertNull(owner.subReplyState.value.rootReply)
            assertNull(owner.subReplyState.value.error)
        }
    }

    @Test
    fun `account switch closes pending dynamic route and does not leak its root into the next account`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { response.await() } }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.openSubReplyFromRoute(90L, 91L)
            runCurrent()
            identities.value = AccountSessionIdentity(202L, 2L)
            runCurrent()
            response.complete(Result.success(threadPage(listOf(ReplyItem(rpid = 91L)), "").copy(root = root.copy(rpid = 90L))))
            runCurrent()
            assertFalse(owner.subReplyState.value.visible)
            assertNull(owner.subReplyState.value.rootReply)
            assertTrue(owner.subReplyState.value.items.isEmpty())
            owner.openCommentSheet(subject)
            runCurrent()
            assertFalse(owner.subReplyState.value.visible)
            assertEquals(10L, owner.commentUiState.value.comments.single().rpid)
        }
    }

    @Test
    fun `dynamic root targeted empty route normalizes target and rejects a wrong server root until retry`() = runTest(dispatcher) {
        var correctRoot = false
        val targets = mutableListOf<Long>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            targets += arg<Long>(5)
            Result.success(threadPage(emptyList(), "").copy(root = root.copy(
                rpid = if (correctRoot) 90L else 10L, count = 0, rcount = 0,
            )))
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            assertTrue(owner.openSubReplyFromRoute(90L, 90L))
            runCurrent()
            assertTrue(owner.subReplyState.value.visible)
            assertNull(owner.subReplyState.value.rootReply)
            assertEquals("回复楼层不匹配，请重试", owner.subReplyState.value.error)
            correctRoot = true
            owner.loadMoreSubReplies()
            runCurrent()
            assertEquals(listOf(0L, 0L), targets)
            assertEquals(90L, owner.subReplyState.value.rootReply?.rpid)
            assertTrue(owner.subReplyState.value.items.isEmpty())
            assertNull(owner.subReplyState.value.error)
        }
    }

    @Test
    fun `dynamic refresh and sorting cancel an in flight routed target instead of resuming its seek`() = runTest(dispatcher) {
        val pending = CompletableDeferred<Result<ReplyData>>()
        val targets = mutableListOf<Long>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            targets += arg<Long>(5)
            if (arg<Long>(5) > 0L) withContext(NonCancellable) { pending.await() }
            else Result.success(threadPage(emptyList(), "").copy(root = root.copy(rpid = 90L)))
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.openSubReplyFromRoute(90L, 91L)
            runCurrent()
            owner.refreshSubReplies()
            runCurrent()
            assertEquals(0L, owner.subReplyState.value.targetReplyId)
            assertEquals(90L, owner.subReplyState.value.rootReply?.rpid)
            owner.openSubReplyFromRoute(90L, 92L)
            runCurrent()
            owner.setSubReplySortMode(SubReplySortMode.HOT)
            runCurrent()
            pending.complete(Result.failure(IllegalStateException("abandoned target")))
            runCurrent()
            assertEquals(listOf(91L, 0L, 92L, 0L), targets)
            assertEquals(SubReplySortMode.HOT, owner.subReplyState.value.sortMode)
            assertEquals(0L, owner.subReplyState.value.targetReplyId)
            assertNull(owner.subReplyState.value.error)
        }
    }

    @Test
    fun `accepted dynamic replies remain visible when routed read and sorted retry both fail`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("offline"))
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.startCommentReply(root)
            owner.postComment(subject.id_str, "accepted reply") { _, _ -> }
            runCurrent()
            owner.openSubReplyFromRoute(10L, 40L)
            runCurrent()
            assertEquals(listOf(40L), owner.subReplyState.value.items.map { it.rpid })
            assertEquals("offline", owner.subReplyState.value.error)
            owner.setSubReplySortMode(SubReplySortMode.HOT)
            runCurrent()
            assertEquals(listOf(40L), owner.subReplyState.value.items.map { it.rpid })
            assertEquals("offline", owner.subReplyState.value.error)
        }
    }

    @Test
    fun `routed thread opens before pending main list and a late main success cannot reopen it after close`() = runTest(dispatcher) {
        val mainResponse = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { mainResponse.await() }
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(threadPage(listOf(ReplyItem(rpid = 91L, root = 90L)), "").copy(root = root.copy(rpid = 90L)))
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, targetReplyId = 91L)
            runCurrent()
            assertTrue(owner.commentUiState.value.isLoading)
            assertTrue(owner.subReplyState.value.visible)
            assertEquals(90L, owner.subReplyState.value.rootReply?.rpid)
            assertEquals(listOf(91L), owner.subReplyState.value.items.map { it.rpid })
            owner.closeSubReply()
            mainResponse.complete(Result.success(ReplyData(replies = listOf(root), cursor = ReplyCursor(isEnd = true))))
            runCurrent()
            assertFalse(owner.commentUiState.value.isLoading)
            assertEquals(10L, owner.commentUiState.value.comments.single().rpid)
            assertFalse(owner.subReplyState.value.visible)
            coVerify(exactly = 1) {
                CommentRepository.getMobileSortedSubCommentsForSubject(3000L, 11, 90L, any(), any(), 91L, any())
            }
        }
    }

    @Test
    fun `main list failure does not prevent routed thread failure feedback or same target retry`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("main offline"))
        var failThread = true
        val targets = mutableListOf<Long>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            targets += arg<Long>(5)
            if (failThread) Result.failure(IllegalStateException("target unavailable"))
            else Result.success(threadPage(emptyList(), "").copy(root = root.copy(rpid = 90L)))
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, targetReplyId = 91L)
            runCurrent()
            assertEquals("main offline", owner.commentUiState.value.refreshError)
            assertTrue(owner.subReplyState.value.visible)
            assertEquals("target unavailable", owner.subReplyState.value.error)
            failThread = false
            owner.loadMoreSubReplies()
            runCurrent()
            assertEquals(listOf(91L, 91L), targets)
            assertEquals(90L, owner.subReplyState.value.rootReply?.rpid)
            assertNull(owner.subReplyState.value.error)
            coVerify(exactly = 2) {
                CommentRepository.getMobileSortedSubCommentsForSubject(3000L, 11, 90L, any(), any(), 91L, any())
            }
        }
    }

    @Test
    fun `closing routed intent before delayed subject resolution prevents a late thread open`() = runTest(dispatcher) {
        val detailResponse = CompletableDeferred<Result<DynamicItem>>()
        val unresolved = DynamicItem(id_str = "needs-detail", type = "DYNAMIC_TYPE_ARTICLE")
        mockkObject(DynamicRepository)
        try {
            coEvery { DynamicRepository.getDynamicDetail("needs-detail") } coAnswers { detailResponse.await() }
            withViewModel { owner ->
                owner.openCommentSheet(unresolved, rootReplyId = 90L, targetReplyId = 91L)
                runCurrent()
                owner.closeSubReply()
                detailResponse.complete(Result.success(subject.copy(id_str = "needs-detail")))
                runCurrent()
                assertFalse(owner.subReplyState.value.visible)
                assertNull(owner.subReplyState.value.rootReply)
                coVerify(exactly = 0) {
                    CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), 90L, any(), any(), any(), any())
                }
                assertEquals(10L, owner.commentUiState.value.comments.single().rpid)
            }
        } finally {
            unmockkObject(DynamicRepository)
        }
    }

    @Test
    fun `detail route pins an off page root while the main list is pending or fails without opening a thread`() = runTest(dispatcher) {
        val main = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { main.await() }
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(root = ReplyItem(rpid = 90L)))
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, targetReplyId = 91L, pinRootReply = true)
            runCurrent()
            assertTrue(owner.commentUiState.value.isLoading)
            assertEquals(91L, owner.commentUiState.value.pinnedTargetReplyId)
            assertEquals(listOf(90L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertFalse(owner.subReplyState.value.visible)

            main.complete(Result.failure(IllegalStateException("main offline")))
            runCurrent()
            assertEquals("main offline", owner.commentUiState.value.refreshError)
            assertEquals(90L, owner.commentUiState.value.pinnedReply?.rpid)
            assertFalse(owner.subReplyState.value.visible)
        }
    }

    @Test
    fun `detail pin moves a cached main root first without duplicating it or changing the normal cache order`() = runTest(dispatcher) {
        val selected = ReplyItem(rpid = 90L)
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(replies = listOf(root, selected), cursor = ReplyCursor(isEnd = true)))
        withViewModel { owner ->
            owner.openCommentSheet(subject)
            runCurrent()
            owner.openCommentSheet(subject, rootReplyId = 90L, pinRootReply = true)
            runCurrent()
            assertEquals(listOf(90L, 10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertEquals(listOf(10L, 90L), owner.commentUiState.value.comments.map { it.rpid })
            assertFalse(owner.subReplyState.value.visible)

            owner.closeCommentSheet()
            owner.openCommentSheet(subject)
            runCurrent()
            assertEquals(listOf(10L, 90L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertNull(owner.commentUiState.value.pinnedReply)
        }
    }

    @Test
    fun `off page detail pin is not injected into an ordinary reopening and root targeted routes normalize child selection`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(root = ReplyItem(rpid = 90L)))
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, targetReplyId = 90L, pinRootReply = true)
            runCurrent()
            assertEquals(0L, owner.commentUiState.value.pinnedTargetReplyId)
            assertEquals(listOf(90L, 10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertEquals(3, owner.commentUiState.value.totalCount)

            owner.closeCommentSheet()
            owner.openCommentSheet(subject)
            runCurrent()
            assertEquals(listOf(10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertEquals(0L, owner.commentUiState.value.pinnedRootReplyId)
            assertNull(owner.commentUiState.value.pinnedReply)
        }
    }

    @Test
    fun `detail pin survives main paging refresh and sort changes without changing the server total`() = runTest(dispatcher) {
        var rootLikes = 4
        coEvery { CommentRepository.getCommentCountForSubject(any(), any()) } returns Result.success(13)
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { Result.success(ReplyData(root = ReplyItem(rpid = 90L, like = rootLikes))) }
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val newest = arg<Int>(4) == CommentSortMode.NEWEST.apiMode
            val nextPage = arg<Int>(2) > 1
            Result.success(ReplyData(
                replies = listOf(if (newest) ReplyItem(rpid = 50L) else if (nextPage) ReplyItem(rpid = 30L) else root),
                cursor = ReplyCursor(allCount = 13, isEnd = newest || nextPage),
                grpcNextOffset = if (newest || nextPage) "" else "next",
            ))
        }
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, pinRootReply = true)
            runCurrent()
            owner.loadMoreComments()
            runCurrent()
            assertEquals(listOf(90L, 10L, 30L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })

            rootLikes = 9
            owner.refreshComments()
            runCurrent()
            assertEquals(9, owner.commentUiState.value.pinnedReply?.like)
            assertEquals(listOf(90L, 10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })

            owner.setDynamicCommentSortMode(CommentSortMode.NEWEST)
            runCurrent()
            assertEquals(listOf(90L, 50L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertEquals(13, owner.commentUiState.value.totalCount)
            assertFalse(owner.subReplyState.value.visible)
        }
    }

    @Test
    fun `late cancellation resistant pin data cannot enter another dynamic or a closed comment page`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { response.await() } }
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, pinRootReply = true)
            runCurrent()
            owner.openCommentSheet(otherSubject)
            runCurrent()
            response.complete(Result.success(ReplyData(root = ReplyItem(rpid = 90L))))
            runCurrent()
            assertEquals(listOf(110L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertNull(owner.commentUiState.value.pinnedReply)
            assertNull(owner.commentUiState.value.pinnedReplyError)

            owner.closeCommentSheet()
            assertEquals(emptyList(), resolveDynamicDetailDisplayComments(owner.commentUiState.value))
        }
    }

    @Test
    fun `account changes discard an unresolved detail pin rather than carrying it into the next login`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { response.await() } }
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, pinRootReply = true)
            runCurrent()
            identities.value = AccountSessionIdentity(202L, 2L)
            runCurrent()
            response.complete(Result.success(ReplyData(root = ReplyItem(rpid = 90L))))
            runCurrent()
            assertNull(owner.commentUiState.value.pinnedReply)
            assertEquals(0L, owner.commentUiState.value.pinnedRootReplyId)
            owner.openCommentSheet(subject)
            runCurrent()
            assertEquals(listOf(10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
        }
    }

    @Test
    fun `wrong point lookup root is never pinned and a normal comment refresh can resolve the intended root`() = runTest(dispatcher) {
        var correct = false
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { Result.success(ReplyData(root = ReplyItem(rpid = if (correct) 90L else 99L))) }
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, pinRootReply = true)
            runCurrent()
            assertNull(owner.commentUiState.value.pinnedReply)
            assertTrue(owner.commentUiState.value.pinnedReplyError.orEmpty().contains("楼层不匹配"))
            assertEquals(listOf(10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertFalse(owner.subReplyState.value.visible)

            correct = true
            owner.refreshComments()
            runCurrent()
            assertEquals(listOf(90L, 10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertNull(owner.commentUiState.value.pinnedReplyError)
        }
    }

    @Test
    fun `off page pinned roots support optimistic actions rollback and deletion without changing the main list`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(root = ReplyItem(rpid = 90L, like = 4)))
        coEvery { CommentRepository.likeCommentForSubject(any(), any(), any(), any()) } returns Result.failure(IllegalStateException("offline"))
        coEvery { CommentRepository.hateCommentForSubject(any(), any(), any(), any()) } returns Result.failure(IllegalStateException("offline"))
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns Result.success(Unit)
        withViewModel { owner ->
            owner.openCommentSheet(subject, rootReplyId = 90L, pinRootReply = true)
            runCurrent()
            owner.likeComment(90L)
            assertEquals(5, owner.commentUiState.value.pinnedReply?.like)
            runCurrent()
            assertEquals(4, owner.commentUiState.value.pinnedReply?.like)
            owner.hateComment(90L)
            assertTrue(owner.commentUiState.value.pinnedReply?.let(::isDynamicCommentHated) == true)
            runCurrent()
            assertFalse(owner.commentUiState.value.pinnedReply?.let(::isDynamicCommentHated) == true)

            owner.deleteDynamicComment(90L) { _, _ -> }
            runCurrent()
            assertNull(owner.commentUiState.value.pinnedReply)
            assertEquals(0L, owner.commentUiState.value.pinnedRootReplyId)
            assertEquals(listOf(10L), resolveDynamicDetailDisplayComments(owner.commentUiState.value).map { it.rpid })
            assertEquals(2, owner.commentUiState.value.totalCount)
        }
    }

    private suspend fun TestScope.withViewModel(block: suspend TestScope.(DynamicViewModel) -> Unit) {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        every { preferences.getString(any(), any()) } answers { secondArg<String?>() }
        every { preferences.getStringSet(any(), any()) } answers { secondArg<Set<String>?>() }
        val application = mockk<Application>(relaxed = true)
        every { application.applicationContext } returns application
        every { application.getSharedPreferences(any(), any()) } returns preferences
        val owner = DynamicViewModel(application)
        try {
            runCurrent()
            block(owner)
        } finally {
            owner.viewModelScope.cancel()
            runCurrent()
        }
    }

    private fun dynamic(id: String, oid: Long) = DynamicItem(
        id_str = id, type = "DYNAMIC_TYPE_DRAW",
        basic = DynamicBasic(comment_id_str = oid.toString(), comment_type = 11),
    )

    private fun threadPage(items: List<ReplyItem>, offset: String) = ReplyData(
        replies = items,
        cursor = ReplyCursor(allCount = 3, isEnd = offset.isEmpty()),
        grpcNextOffset = offset,
    )
}
