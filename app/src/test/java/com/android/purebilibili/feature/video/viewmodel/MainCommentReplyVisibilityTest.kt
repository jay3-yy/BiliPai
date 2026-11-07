package com.android.purebilibili.feature.video.viewmodel

import androidx.lifecycle.ViewModelStore
import com.android.purebilibili.app.PureApplication
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.feature.comment.SentCommentReplyStores
import com.android.purebilibili.data.repository.CommentRepository
import io.mockk.coVerify
import io.mockk.mockkStatic
import android.widget.Toast
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
class MainCommentReplyVisibilityTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var model: VideoCommentViewModel
    private val identities = MutableStateFlow(AccountSessionIdentity())
    private val root = ReplyItem(
        rpid = 10L,
        oid = 1001L,
        count = 8,
        rcount = 8,
        replies = (20L..24L).map { ReplyItem(rpid = it, root = 10L, parent = 10L) }
    )
    private val sent = ReplyItem(
        rpid = 50L,
        root = 10L,
        parent = 10L,
        content = ReplyContent(message = "new reply")
    )
    private var remoteRoot = root

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sharedVideoCommentCache.clear()
        SentCommentReplyStores.clear()
        mockkObject(CommentRepository, CommentFraudRepository, PureApplication.Companion, TokenManager)
        every { TokenManager.accountIdentity } returns identities
        every { TokenManager.midCache } answers { identities.value.mid }
        every { PureApplication.instance } returns mockk(relaxed = true)
        coEvery {
            CommentRepository.getMobileCommentsForSubject(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            Result.success(ReplyData(replies = listOf(remoteRoot), cursor = ReplyCursor(allCount = 9, isEnd = true)))
        }
        coEvery { CommentRepository.addCommentForSubject(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(sent.copy(root = 0L, parent = 0L))
        coEvery { CommentRepository.checkCommentStatus(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("Detection disabled in this fixture"))
        coEvery {
            CommentFraudRepository.saveRecord(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Unit
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
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
    fun `canceling a server like survives a stale refresh`() = runTest(dispatcher) {
        remoteRoot = root.copy(action = 1)
        model.refreshComments()
        runCurrent()
        coEvery { CommentRepository.likeCommentForSubject(any(), any(), any(), any()) } returns Result.success(Unit)
        model.likeComment(root.rpid)
        runCurrent()
        assertFalse(root.rpid in model.commentState.value.likedComments)
        coVerify { CommentRepository.likeCommentForSubject(1001L, 1, root.rpid, false) }
        model.refreshComments()
        runCurrent()
        assertFalse(root.rpid in model.commentState.value.likedComments)
    }

    @Test
    fun `failed like preserves another comment successful like`() = runTest(dispatcher) {
        val first = CompletableDeferred<Result<Unit>>()
        coEvery { CommentRepository.likeCommentForSubject(any(), any(), 10L, any()) } coAnswers { first.await() }
        coEvery { CommentRepository.likeCommentForSubject(any(), any(), 20L, any()) } returns Result.success(Unit)
        model.likeComment(10L)
        model.likeComment(20L)
        runCurrent()
        first.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertFalse(10L in model.commentState.value.likedComments)
        assertTrue(20L in model.commentState.value.likedComments)
    }

    @Test
    fun `old like failure cannot update a reopened subject`() = runTest(dispatcher) {
        remoteRoot = root.copy(action = 1)
        model.refreshComments()
        runCurrent()
        val pending = CompletableDeferred<Result<Unit>>()
        coEvery { CommentRepository.likeCommentForSubject(any(), any(), any(), any()) } coAnswers { pending.await() }
        model.likeComment(root.rpid)
        runCurrent()
        model.clearForVideoChange()
        sharedVideoCommentCache.clear()
        remoteRoot = root
        model.init(1001L)
        runCurrent()
        pending.complete(Result.failure(IllegalStateException("old response")))
        runCurrent()
        assertTrue(model.commentState.value.likedComments.isEmpty())
    }

    @Test
    fun `failed deletion retains comment count and permits retry`() = runTest(dispatcher) {
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))
        val count = model.commentState.value.replyCount
        model.startDissolve(root.rpid)
        model.deleteComment(root.rpid)
        runCurrent()
        assertTrue(model.commentState.value.replies.any { it.rpid == root.rpid })
        assertEquals(count, model.commentState.value.replyCount)
        assertFalse(root.rpid in model.commentState.value.dissolvingIds)
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns Result.success(Unit)
        model.deleteComment(root.rpid)
        runCurrent()
        assertFalse(model.commentState.value.replies.any { it.rpid == root.rpid })
        assertEquals(count - 1, model.commentState.value.replyCount)
    }

    @Test
    fun `failed thread deletion preserves preview and counts`() = runTest(dispatcher) {
        coEvery { CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(ReplyData(root = root, replies = root.replies, cursor = ReplyCursor(isEnd = true)))
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))
        model.openSubReply(root)
        runCurrent()
        val count = model.subReplyState.value.totalCount
        model.startSubDissolve(20L)
        model.deleteSubComment(20L)
        runCurrent()
        assertTrue(model.subReplyState.value.items.any { it.rpid == 20L })
        assertEquals(count, model.subReplyState.value.totalCount)
        assertEquals(root.replies, model.commentState.value.replies.single().replies)
        assertFalse(20L in model.subReplyState.value.dissolvingIds)
    }

    @Test
    fun `main list composer immediately inserts a reply even when response omits threading fields`() = runTest(dispatcher) {
        model.replyTo(root)
        model.sendComment("new reply", fraudDetectionEnabled = false)
        runCurrent()

        val updated = model.commentState.value.replies.single()
        assertEquals(sent.rpid, updated.replies.orEmpty().first().rpid)
        assertEquals(root.rpid, updated.replies.orEmpty().first().root)
        assertEquals(root.rpid, updated.replies.orEmpty().first().parent)
        assertEquals(9, updated.count)
        assertEquals(9, updated.rcount)
        assertFalse(model.commentState.value.isSending)
    }

    @Test
    fun `external reply is visible ahead of a full collapsed preview`() {
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)

        val updated = model.commentState.value.replies.single()
        assertEquals(listOf(50L, 20L, 21L, 22L, 23L, 24L), updated.replies.orEmpty().map { it.rpid })
        assertEquals(9, updated.count)
        assertEquals(9, updated.rcount)
    }

    @Test
    fun `stale refresh preserves local reply and repeated events do not inflate thread count`() = runTest(dispatcher) {
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.refreshComments()
        runCurrent()

        val updated = model.commentState.value.replies.single()
        assertEquals(listOf(50L, 20L, 21L, 22L, 23L, 24L), updated.replies.orEmpty().map { it.rpid })
        assertEquals(9, updated.count)
        assertEquals(9, updated.rcount)
    }

    @Test
    fun `server acknowledgement deduplicates reply without incrementing acknowledged count`() = runTest(dispatcher) {
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        remoteRoot = root.copy(count = 9, rcount = 9, replies = root.replies.orEmpty() + sent)
        model.refreshComments()
        runCurrent()

        val updated = model.commentState.value.replies.single()
        assertEquals(listOf(50L, 20L, 21L, 22L, 23L, 24L), updated.replies.orEmpty().map { it.rpid })
        assertEquals(9, updated.count)
        assertEquals(9, updated.rcount)
    }

    @Test
    fun `opening and reopening reply details keeps locally sent replies before server propagation`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(root = root, replies = root.replies, cursor = ReplyCursor(isEnd = true)))
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)

        repeat(2) {
            model.openSubReply(model.commentState.value.replies.single())
            runCurrent()
            assertEquals(listOf(20L, 21L, 22L, 23L, 24L, 50L), model.subReplyState.value.items.map { it.rpid })
            assertEquals(9, model.subReplyState.value.totalCount)
            model.closeSubReply()
        }
    }

    @Test
    fun `deleting a locally sent reply removes its preview and refresh cannot resurrect it`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(root = root, replies = root.replies, cursor = ReplyCursor(isEnd = true)))
        coEvery { CommentRepository.deleteCommentForSubject(any(), any(), any()) } returns Result.success(Unit)
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.openSubReply(model.commentState.value.replies.single())
        runCurrent()

        model.deleteSubComment(sent.rpid)
        runCurrent()
        assertEquals(root.replies, model.commentState.value.replies.single().replies)
        assertEquals(8, model.commentState.value.replies.single().count)
        assertFalse(model.subReplyState.value.items.any { it.rpid == sent.rpid })

        model.refreshComments()
        runCurrent()
        assertEquals(root.replies, model.commentState.value.replies.single().replies)
        assertEquals(8, model.commentState.value.replies.single().count)
    }

    @Test
    fun `opening a sent reply keeps it visible even when thread reads fail before propagation`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("offline"))
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.openSubReply(model.commentState.value.replies.single(), targetReplyId = sent.rpid)
        assertEquals(listOf(50L), model.subReplyState.value.items.map { it.rpid })
        runCurrent()
        assertEquals(listOf(50L), model.subReplyState.value.items.map { it.rpid })
        assertEquals("offline", model.subReplyState.value.error)
        model.setSubReplySortMode(SubReplySortMode.HOT)
        runCurrent()
        assertEquals(listOf(50L), model.subReplyState.value.items.map { it.rpid })
        assertEquals("offline", model.subReplyState.value.error)
    }

    @Test
    fun `returning from a conversation retains an acknowledged hot reply at its server position`() = runTest(dispatcher) {
        val anchor = ReplyItem(rpid = 20L, root = 10L, parent = 10L, dialog = 20L)
        val confirmed = sent.copy(like = 7)
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val firstPage = arg<String?>(4) == null
            Result.success(ReplyData(
                root = root,
                replies = if (firstPage) listOf(confirmed, anchor) else listOf(ReplyItem(rpid = 30L, root = 10L)),
                grpcNextOffset = if (firstPage) "next" else "",
                cursor = ReplyCursor(isEnd = !firstPage)
            ))
        }
        coEvery { CommentRepository.getDialogCommentsForSubject(any(), any(), any(), any(), any(), any()) } returns
            Result.success(ReplyData(replies = listOf(anchor), cursor = ReplyCursor(isEnd = true)))
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.openSubReply(model.commentState.value.replies.single())
        runCurrent()
        model.setSubReplySortMode(SubReplySortMode.HOT)
        runCurrent()
        model.openSubReplyConversation(anchor)
        runCurrent()
        model.closeSubReplyConversation()
        model.loadMoreSubReplies()
        runCurrent()
        assertEquals(listOf(50L, 20L, 30L), model.subReplyState.value.items.map { it.rpid })
        assertEquals(7, model.subReplyState.value.items.first().like)
    }

    @Test
    fun `new pages stay before the pending reply instead of leaving it at the first page boundary`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val firstPage = arg<String?>(4) == null
            Result.success(ReplyData(
                root = root,
                replies = if (firstPage) listOf(ReplyItem(rpid = 20L), ReplyItem(rpid = 21L))
                    else listOf(ReplyItem(rpid = 30L), ReplyItem(rpid = 31L)),
                grpcNextOffset = if (firstPage) "next" else "",
                cursor = ReplyCursor(isEnd = !firstPage)
            ))
        }
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.openSubReply(model.commentState.value.replies.single())
        runCurrent()
        assertEquals(listOf(20L, 21L, 50L), model.subReplyState.value.items.map { it.rpid })
        model.loadMoreSubReplies()
        runCurrent()
        assertEquals(listOf(20L, 21L, 30L, 31L, 50L), model.subReplyState.value.items.map { it.rpid })
    }

    @Test
    fun `preview newest first does not reverse multiple sends in detail`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(ReplyData(root = root, replies = root.replies, cursor = ReplyCursor(isEnd = true)))
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.onExternalCommentSent(1001L, sent.copy(rpid = 60L), fraudDetectionEnabled = false)
        assertEquals(listOf(60L, 50L), model.commentState.value.replies.single().replies.orEmpty().take(2).map { it.rpid })
        model.openSubReply(model.commentState.value.replies.single())
        runCurrent()
        assertEquals(listOf(20L, 21L, 22L, 23L, 24L, 50L, 60L), model.subReplyState.value.items.map { it.rpid })
    }

    @Test
    fun `direct composers bind video course and BGM subject types even when add response omits them`() = runTest(dispatcher) {
        for (type in listOf(1, 33, 47)) {
            model.init(1001L, commentType = type)
            runCurrent()
            model.replyTo(root.copy(replyType = type))
            model.sendComment("new reply", fraudDetectionEnabled = false)
            runCurrent()
            val reply = model.commentState.value.replies.single().replies.orEmpty().first()
            assertEquals(50L, reply.rpid)
            assertEquals(1001L, reply.oid)
            assertEquals(type, reply.replyType)
            assertEquals(10L, reply.root)
        }
    }

    @Test
    fun `account switch clears previews and rejects an old account send completion`() = runTest(dispatcher) {
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        val response = CompletableDeferred<Result<ReplyItem?>>()
        coEvery { CommentRepository.addCommentForSubject(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            response.await()
        }
        model.replyTo(root)
        model.sendComment("old account reply", fraudDetectionEnabled = false)
        runCurrent()
        identities.value = AccountSessionIdentity(202L, 1L)
        runCurrent()
        response.complete(Result.success(sent.copy(rpid = 60L)))
        runCurrent()
        assertEquals(root.replies, model.commentState.value.replies.single().replies)
        assertFalse(model.commentState.value.isSending)
    }

    @Test
    fun `switching comment subject drops locally sent previews`() = runTest(dispatcher) {
        model.onExternalCommentSent(1001L, sent, fraudDetectionEnabled = false)
        model.init(2002L)
        runCurrent()

        assertEquals(root.replies, model.commentState.value.replies.single().replies)
        assertEquals(8, model.commentState.value.replies.single().count)
    }

    @Test
    fun `rootless routed failure stays visible and retries the same target before cursor pagination`() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<String?, Long>>()
        var fail = true
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val offset = arg<String?>(4)
            requests += offset to arg<Long>(5)
            if (fail) Result.failure(IllegalStateException("target unavailable"))
            else Result.success(ReplyData(
                root = root.copy(rpid = 90L),
                replies = listOf(ReplyItem(rpid = if (offset == null) 91L else 92L, root = 90L)),
                grpcNextOffset = if (offset == null) "next" else "",
                cursor = ReplyCursor(isEnd = offset != null),
            ))
        }
        model.openSubReply(root)
        runCurrent()
        model.openSubReplyFromRoute(90L, 91L)
        assertTrue(model.subReplyState.value.visible)
        assertNull(model.subReplyState.value.rootReply)
        assertTrue(model.subReplyState.value.items.isEmpty())
        runCurrent()
        assertEquals("target unavailable", model.subReplyState.value.error)
        fail = false
        model.loadMoreSubReplies()
        runCurrent()
        assertEquals(90L, model.subReplyState.value.rootReply?.rpid)
        assertEquals(listOf(91L), model.subReplyState.value.items.map { it.rpid })
        assertNull(model.subReplyState.value.error)
        model.loadMoreSubReplies()
        runCurrent()
        assertEquals(listOf(null to 91L, null to 91L, "next" to 0L), requests.drop(1))
        assertEquals(listOf(91L, 92L), model.subReplyState.value.items.map { it.rpid })
    }

    @Test
    fun `new routed thread and close reject cancellation resistant earlier route outcomes`() = runTest(dispatcher) {
        val firstResponse = CompletableDeferred<Result<ReplyData>>()
        val secondResponse = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            withContext(NonCancellable) {
                if (arg<Long>(2) == 90L) firstResponse.await() else secondResponse.await()
            }
        }
        model.openSubReplyFromRoute(90L, 91L)
        runCurrent()
        model.openSubReplyFromRoute(100L, 101L)
        runCurrent()
        firstResponse.complete(Result.success(ReplyData(root = root.copy(rpid = 90L))))
        runCurrent()
        assertTrue(model.subReplyState.value.isLoading)
        assertNull(model.subReplyState.value.rootReply)
        assertEquals(101L, model.subReplyState.value.targetReplyId)
        model.closeSubReply()
        secondResponse.complete(Result.failure(IllegalStateException("late failure")))
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertFalse(model.subReplyState.value.isLoading)
        assertNull(model.subReplyState.value.error)
        assertNull(model.subReplyState.value.rootReply)
    }

    @Test
    fun `routed response cannot restore an old account thread after switching back`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ReplyData>>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers { withContext(NonCancellable) { response.await() } }
        model.openSubReplyFromRoute(90L, 91L)
        runCurrent()
        identities.value = AccountSessionIdentity(202L, 1L)
        runCurrent()
        identities.value = AccountSessionIdentity(0L, 2L)
        runCurrent()
        response.complete(Result.success(ReplyData(root = root.copy(rpid = 90L))))
        runCurrent()
        assertFalse(model.subReplyState.value.visible)
        assertNull(model.subReplyState.value.rootReply)
        assertTrue(model.subReplyState.value.items.isEmpty())
    }

    @Test
    fun `root targeted route opens an empty thread with no selected child`() = runTest(dispatcher) {
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            Result.success(ReplyData(root = root.copy(rpid = 90L, count = 0, rcount = 0), cursor = ReplyCursor(isEnd = true)))
        }
        assertTrue(model.openSubReplyFromRoute(90L, 90L))
        runCurrent()
        assertEquals(0L, model.subReplyState.value.targetReplyId)
        assertTrue(model.subReplyState.value.visible)
        assertEquals(90L, model.subReplyState.value.rootReply?.rpid)
        assertTrue(model.subReplyState.value.items.isEmpty())
        assertNull(model.subReplyState.value.error)
    }

    @Test
    fun `refresh and sorting replace in flight route lookup with untargeted first pages`() = runTest(dispatcher) {
        val response = CompletableDeferred<Result<ReplyData>>()
        val targets = mutableListOf<Long>()
        coEvery {
            CommentRepository.getMobileSortedSubCommentsForSubject(any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            targets += arg<Long>(5)
            if (arg<Long>(5) > 0L) withContext(NonCancellable) { response.await() }
            else Result.success(ReplyData(root = root.copy(rpid = 90L), cursor = ReplyCursor(isEnd = true)))
        }
        model.openSubReplyFromRoute(90L, 91L)
        runCurrent()
        model.refreshSubReplies()
        runCurrent()
        assertEquals(0L, model.subReplyState.value.targetReplyId)
        assertEquals(90L, model.subReplyState.value.rootReply?.rpid)
        model.openSubReplyFromRoute(90L, 92L)
        runCurrent()
        model.setSubReplySortMode(SubReplySortMode.HOT)
        runCurrent()
        response.complete(Result.failure(IllegalStateException("abandoned target")))
        runCurrent()
        assertEquals(listOf(91L, 0L, 92L, 0L), targets)
        assertEquals(SubReplySortMode.HOT, model.subReplyState.value.sortMode)
        assertEquals(0L, model.subReplyState.value.targetReplyId)
        assertNull(model.subReplyState.value.error)
    }
}
