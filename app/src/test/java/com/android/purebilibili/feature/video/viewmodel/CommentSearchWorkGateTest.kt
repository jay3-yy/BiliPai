package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentSearchWorkGateTest {
    @Test
    fun hidingSearchCancelsOnlyThePendingPageAndResumesWithoutReloadingCompletedRoots() = runTest {
        val gate = CommentSearchWorkGate()
        val owner = Any()
        var mainRequests = 0
        var threadRequests = 0
        var cancelledRequests = 0
        val progressCounts = mutableListOf<Int>()
        gate.updateConsumer(owner, true)
        val search = async {
            loadCommentSearchReplies(
                loadMain = { _, _ -> gate.runRequest {
                    mainRequests++
                    Result.success(ReplyData(
                        cursor = ReplyCursor(isEnd = true),
                        replies = listOf(ReplyItem(rpid = 1L, count = 1)),
                    ))
                } },
                loadThread = { root, offset -> gate.runRequest {
                    assertEquals(1L, root)
                    assertEquals(null, offset)
                    threadRequests++
                    if (threadRequests == 1) {
                        try { awaitCancellation() } finally { cancelledRequests++ }
                    }
                    Result.success(ReplyData(
                        cursor = ReplyCursor(isEnd = true),
                        replies = listOf(ReplyItem(rpid = 2L)),
                    ))
                } },
                onProgress = { progressCounts += it.loadedCount },
            )
        }
        runCurrent()
        assertEquals(listOf(1), progressCounts)
        assertEquals(1, threadRequests)
        gate.updateConsumer(owner, false)
        runCurrent()
        assertEquals(1, cancelledRequests)
        assertFalse(search.isCompleted)
        assertEquals(listOf(1), progressCounts)
        gate.updateConsumer(owner, true)
        val result = search.await()
        assertEquals(1, mainRequests)
        assertEquals(2, threadRequests)
        assertEquals(2, result.loadedCount)
        assertTrue(result.isComplete)
    }

    @Test
    fun hiddenSearchDoesNotStartRequestsAndAnotherVisibleOwnerKeepsWorkAlive() = runTest {
        val gate = CommentSearchWorkGate()
        val first = Any()
        val second = Any()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        var cancellations = 0
        val request = async { gate.runRequest {
            requests++
            try { release.await() } finally { if (!release.isCompleted) cancellations++ }
            42
        } }
        runCurrent()
        assertEquals(0, requests)
        gate.updateConsumer(first, true)
        runCurrent()
        gate.updateConsumer(second, true)
        gate.updateConsumer(first, false)
        runCurrent()
        assertEquals(1, requests)
        assertEquals(0, cancellations)
        release.complete(Unit)
        assertEquals(42, request.await())
        gate.updateConsumer(second, false)
        assertFalse(gate.hasConsumers)
    }

    @Test
    fun changingSubjectCanCancelAPausedSearchWithoutWaitingForVisibility() = runTest {
        val gate = CommentSearchWorkGate()
        val request = async { gate.runRequest { error("Hidden search must not request") } }
        runCurrent()
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }
}
