package com.android.purebilibili.feature.video.viewmodel

import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.app.PureApplication
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.feature.comment.SentCommentReplyStores
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoCommentThreadRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val identities = MutableStateFlow(AccountSessionIdentity())
    private lateinit var model: VideoCommentViewModel
    private val root = ReplyItem(rpid = 10L, oid = 1001L, count = 8, rcount = 8)
    private val target = ReplyItem(
        rpid = 50L, oid = 1001L, root = root.rpid, parent = 30L, dialog = 20L,
        content = ReplyContent(message = "readable target"),
    )
    // This is the repository's successful target recovery after DetailList business 12006.
    private val recovered = ReplyData(
        replies = listOf(target), isTargetOnly = true, cursor = ReplyCursor(isEnd = true),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sharedVideoCommentCache.clear()
        SentCommentReplyStores.clear()
        mockkObject(CommentRepository, TokenManager, PureApplication.Companion)
        every { TokenManager.accountIdentity } returns identities
        every { TokenManager.midCache } answers { identities.value.mid }
        every { PureApplication.instance } returns mockk(relaxed = true)
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(cursor = ReplyCursor(isEnd = true)))
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(recovered)
        coEvery {
            CommentRepository.getDialogCommentsForSubject(any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("dialog offline"))
        coEvery { CommentRepository.checkCommentStatus(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("Detection disabled in this fixture"))
        model = VideoCommentViewModel()
        store.put("comments", model)
        model.init(1001L)
        dispatcher.scheduler.runCurrent()
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        sharedVideoCommentCache.clear()
        SentCommentReplyStores.clear()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `replying from rootless recovery inserts the real response and retains the target`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        coEvery { CommentRepository.addCommentForSubject(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(ReplyItem(rpid = 70L, content = ReplyContent(message = "sent reply")))
        model.replyTo(target)
        model.sendComment("sent reply", fraudDetectionEnabled = false)
        runCurrent()
        val state = model.subReplyState.value
        assertNull(state.rootReply)
        assertTrue(state.isTargetOnly)
        assertEquals(listOf(50L, 70L), state.items.map { it.rpid })
        assertEquals(target.root, state.items.last().root)
        assertEquals(target.rpid, state.items.last().parent)
        assertEquals(target.dialog, state.items.last().dialog)
        assertEquals(0, state.totalCount)
        assertFalse(model.commentState.value.isSending)
    }

    @Test
    fun `deleting the final real target closes a rootless recovery panel`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns Result.success(Unit)
        model.deleteSubComment(target.rpid)
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertTrue(model.subReplyState.value.items.isEmpty())
    }

    @Test
    fun `returning from dialog discards a late response and keeps the original recovered target`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        val late = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getDialogCommentsForSubject(any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { late.await() } }
        model.openSubReplyConversation(target)
        runCurrent()
        model.closeSubReplyConversation()
        late.complete(Result.success(ReplyData(replies = listOf(target.copy(rpid = 99L)))))
        runCurrent()
        assertTrue(model.subReplyState.value.isTargetOnly)
        assertNull(model.subReplyState.value.conversationAnchor)
        assertEquals(listOf(target), model.subReplyState.value.items)
    }

    @Test
    fun `switching subjects discards a late target recovery`() = runTest(dispatcher) {
        val late = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { late.await() } }
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        model.init(2002L)
        runCurrent()
        late.complete(Result.success(recovered))
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertTrue(model.subReplyState.value.items.isEmpty())
    }

    @Test
    fun `routed recovery displays the real target without requiring or fabricating a root`() = runTest(dispatcher) {
        assertTrue(model.openSubReplyFromRoute(root.rpid, target.rpid))
        runCurrent()
        val partial = model.subReplyState.value
        assertTrue(partial.visible)
        assertTrue(partial.isTargetOnly)
        assertNull(partial.rootReply)
        assertEquals(listOf(target), partial.items)
        assertEquals(root.rpid, partial.threadRootId)
        assertEquals(target.rpid, partial.targetReplyId)
        assertEquals(0, partial.totalCount)
        assertFalse(partial.isLoading)
        assertNull(partial.error)
        model.loadMoreSubReplies()
        model.setSubReplySortMode(partial.sortMode.toggled())
        runCurrent()
        assertEquals(partial, model.subReplyState.value)
    }

    @Test
    fun `cached root remains real but recovered target does not claim a full count`() = runTest(dispatcher) {
        model.openSubReply(root, target.rpid)
        runCurrent()
        assertEquals(root, model.subReplyState.value.rootReply)
        assertTrue(model.subReplyState.value.isTargetOnly)
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertEquals(0, model.subReplyState.value.totalCount)
    }

    @Test
    fun `refresh retains the target while pending then clears partial status on full recovery`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        val refresh = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(1001L, 1, root.rpid, any(), any(), target.rpid, any())
        } coAnswers { refresh.await() }
        model.refreshSubReplies()
        runCurrent()
        assertTrue(model.subReplyState.value.isRefreshing)
        assertTrue(model.subReplyState.value.isTargetOnly)
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertEquals(target.rpid, model.subReplyState.value.targetReplyId)
        refresh.complete(Result.success(ReplyData(
            root = root, replies = listOf(target, target.copy(rpid = 60L)),
            cursor = ReplyCursor(isEnd = true),
        )))
        runCurrent()
        val full = model.subReplyState.value
        assertFalse(full.isTargetOnly)
        assertFalse(full.isRefreshing)
        assertEquals(root, full.rootReply)
        assertEquals(listOf(50L, 60L), full.items.map { it.rpid })
        assertEquals(8, full.totalCount)
    }

    @Test
    fun `refresh error leaves recovered content readable and retry can recover again`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("refresh offline"))
        model.refreshSubReplies()
        runCurrent()
        assertTrue(model.subReplyState.value.isTargetOnly)
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertEquals("refresh offline", model.subReplyState.value.error)
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(recovered)
        model.refreshSubReplies()
        runCurrent()
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertNull(model.subReplyState.value.error)
    }

    @Test
    fun `full thread append failure keeps already loaded replies`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(
            root = root, replies = listOf(target), grpcNextOffset = "next",
            cursor = ReplyCursor(isEnd = false),
        ))
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), "next", any(), any())
        } returns Result.failure(IllegalStateException("append offline"))
        model.loadMoreSubReplies()
        runCurrent()
        assertFalse(model.subReplyState.value.isTargetOnly)
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertEquals("append offline", model.subReplyState.value.error)
        assertFalse(model.subReplyState.value.isLoading)
    }

    @Test
    fun `rootless recovery loads structured dialog and returns to the original target after failure`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        val partial = model.subReplyState.value
        // Neither a missing dialog nor another thread is permitted to replace this view.
        model.openSubReplyConversation(target.copy(dialog = 0L))
        model.openSubReplyConversation(target.copy(root = 99L))
        assertEquals(partial, model.subReplyState.value)
        val dialogReply = target.copy(rpid = 40L)
        coEvery {
            CommentRepository.getDialogCommentsForSubject(1001L, 1, target.root, target.dialog, any(), any())
        } returns Result.success(ReplyData(replies = listOf(dialogReply, target), cursor = ReplyCursor(isEnd = false), grpcNextOffset = "dialog-next"))
        model.openSubReplyConversation(target)
        runCurrent()
        assertEquals(target, model.subReplyState.value.conversationAnchor)
        assertEquals(listOf(dialogReply, target), model.subReplyState.value.items)
        coEvery {
            CommentRepository.getDialogCommentsForSubject(1001L, 1, target.root, target.dialog, any(), "dialog-next")
        } returns Result.failure(IllegalStateException("dialog append offline"))
        model.loadMoreSubReplies()
        runCurrent()
        assertEquals(listOf(dialogReply, target), model.subReplyState.value.items)
        assertEquals("dialog append offline", model.subReplyState.value.error)
        model.closeSubReplyConversation()
        assertNull(model.subReplyState.value.conversationAnchor)
        assertTrue(model.subReplyState.value.isTargetOnly)
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertEquals(target.rpid, model.subReplyState.value.targetReplyId)
    }

    @Test
    fun `failed first dialog request keeps the readable anchor`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        model.openSubReplyConversation(target)
        runCurrent()
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertEquals("dialog offline", model.subReplyState.value.error)
        model.closeSubReplyConversation()
        assertEquals(listOf(target), model.subReplyState.value.items)
        assertNull(model.subReplyState.value.error)
    }

    @Test
    fun `closing thread drops a noncancellable late recovery response`() = runTest(dispatcher) {
        val late = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { late.await() } }
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        model.closeSubReply()
        late.complete(Result.success(recovered))
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertTrue(model.subReplyState.value.items.isEmpty())
        assertFalse(model.subReplyState.value.isTargetOnly)
    }

    @Test
    fun `account switch drops old recovery and dialog responses`() = runTest(dispatcher) {
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        val late = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getDialogCommentsForSubject(any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { late.await() } }
        model.openSubReplyConversation(target)
        runCurrent()
        identities.value = AccountSessionIdentity(mid = 99L)
        runCurrent()
        late.complete(Result.success(ReplyData(replies = listOf(target.copy(rpid = 99L)))))
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertTrue(model.subReplyState.value.items.isEmpty())
        assertFalse(model.subReplyState.value.isTargetOnly)
    }

    @Test
    fun `account switch while root recovery is pending cannot reopen the previous account thread`() = runTest(dispatcher) {
        val late = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { late.await() } }
        model.openSubReplyFromRoute(root.rpid, target.rpid)
        runCurrent()
        identities.value = AccountSessionIdentity(mid = 99L)
        runCurrent()
        late.complete(Result.success(recovered))
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertTrue(model.subReplyState.value.items.isEmpty())
        assertFalse(model.subReplyState.value.isTargetOnly)
    }
}
