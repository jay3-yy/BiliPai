package com.android.purebilibili.feature.comment

import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.CommentMainListRoute
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentPagingSessionTest {
    @Test fun pendingFirstPageStaysLoadingUntilRowsAreAppliedAndRealFailureCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = kotlinx.coroutines.CompletableDeferred<ReplyData>()
            val retryResponse = kotlinx.coroutines.CompletableDeferred<ReplyData>()
            val states = mutableListOf<Triple<Boolean, Boolean, Throwable?>>()
            var applied = false
            var attempts = 0
            val failure = IllegalStateException("请求失败")
            val session = CommentPagingSession(mainList = true,
                load = { if (++attempts == 1) response.await() else retryResponse.await() },
                onPage = { _, _ -> applied = true },
                onState = { loading, end, error ->
                    // 没有明确失败时，不能先结束加载再发布首包（真实零评论也算首包）。
                    if (!applied && error == null) assertTrue(loading)
                    states += Triple(loading, end, error)
                },
            )
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            assertTrue(states.isNotEmpty())
            assertTrue(states.all { it.first && it.third == null })
            response.completeExceptionally(failure)
            runCurrent()
            assertFalse(states.last().first)
            assertSame(failure, states.last().third)

            session.loadMore()
            runCurrent()
            assertTrue(states.last().first)
            assertNull(states.last().third)
            retryResponse.complete(ReplyData(cursor = ReplyCursor(isEnd = true)))
            runCurrent()
            assertTrue(applied)
            assertFalse(states.last().first)
            assertTrue(states.last().second)
            assertNull(states.last().third)
            job.cancelAndJoin()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun mainCommentRetryRetainsRouteAndCursorAndPublishesEachPageOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val continuation = CommentMainListContinuation(CommentMainListRoute.GRPC, offset = "opaque")
            val requested = mutableListOf<CommentPageRequest>()
            val applied = mutableListOf<Int>()
            var fail = true
            var error: Throwable? = null
            var end = false
            val session = CommentPagingSession(mainList = true, load = { request ->
                requested += request
                if (request.page == 2 && fail) { fail = false; throw IllegalStateException("暂时失败") }
                ReplyData(cursor = ReplyCursor(isEnd = request.page == 2), mainListContinuation = continuation)
            }, onPage = { request, _ -> applied += request.page }, onState = { _, done, failure -> end = done; error = failure })
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            session.loadMore()
            runCurrent()
            assertNotNull(error)
            assertEquals(listOf(1), applied)
            session.loadMore()
            runCurrent()
            assertNull(error)
            assertTrue(end)
            assertEquals(listOf(1, 2), applied)
            assertEquals(listOf(CommentPageRequest(), CommentPageRequest(2, continuation), CommentPageRequest(2, continuation)), requested)
            job.cancelAndJoin()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun dialogResumesAtSavedCursorAndStopsOnRepeatedOffset() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requested = mutableListOf<CommentPageRequest>()
            var end = false
            val initial = CommentPageRequest(page = 4, offset = "saved")
            val session = CommentPagingSession(initial, mainList = false, load = { request ->
                requested += request
                ReplyData(grpcNextOffset = "saved")
            }, onPage = { _, _ -> }, onState = { _, done, _ -> end = done })
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            session.loadMore()
            runCurrent()
            assertTrue(end)
            assertEquals(listOf(initial), requested)
            job.cancelAndJoin()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cancelledCollectionCannotPublishDelayedReply() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = kotlinx.coroutines.CompletableDeferred<ReplyData>()
            var applied = 0
            val session = CommentPagingSession(mainList = false, load = { response.await() },
                onPage = { _, _ -> applied++ }, onState = { _, _, _ -> })
            val job = backgroundScope.launch { session.run() }
            runCurrent()
            job.cancelAndJoin()
            response.complete(ReplyData())
            runCurrent()
            assertEquals(0, applied)
        } finally { Dispatchers.resetMain() }
    }
}
