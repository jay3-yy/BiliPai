package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyPageControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentFirstPageRecoveryTest {
    @Test
    fun `empty first page with detail count recovers without changing order`() = runTest {
        var calls = 0
        val loaded = ReplyData(replies = listOf(ReplyItem(rpid = 4), ReplyItem(rpid = 2)))
        val result = recoverEmptyCommentFirstPage(1, 41) {
            Result.success(if (++calls == 1) ReplyData() else loaded)
        }
        assertEquals(2, calls)
        assertEquals(loaded, result.getOrThrow())
    }

    @Test
    fun `persistent empty page is a retryable failure with bounded requests`() = runTest {
        var calls = 0
        val result = recoverEmptyCommentFirstPage(1, 0) {
            calls++
            Result.success(ReplyData(cursor = ReplyCursor(allCount = 41)))
        }
        assertEquals(2, calls)
        assertTrue(result.isFailure)
    }

    @Test
    fun `real zero explicit empty state and later pages do not retry`() = runTest {
        for ((page, count, data) in listOf(
            Triple(1, 0, ReplyData()),
            Triple(1, 41, ReplyData(control = ReplyPageControl(emptyText = "暂时没有可见评论"))),
            Triple(2, 41, ReplyData()),
            Triple(1, 41, ReplyData(hots = listOf(ReplyItem(rpid = 1)))),
        )) {
            var calls = 0
            val result = recoverEmptyCommentFirstPage(page, count) { calls++; Result.success(data) }
            assertEquals(1, calls)
            assertEquals(data, result.getOrThrow())
        }
    }

    @Test
    fun `server failure is preserved and cancellation propagates`() = runTest {
        val closed = CommentReadException(12002)
        var calls = 0
        val result = recoverEmptyCommentFirstPage(1, 41) { calls++; Result.failure(closed) }
        assertEquals(1, calls)
        assertEquals(closed, result.exceptionOrNull())
        val cancellation = CancellationException("content changed")
        var caught: CancellationException? = null
        try {
            recoverEmptyCommentFirstPage(1, 41) { throw cancellation }
        } catch (error: CancellationException) {
            caught = error
        }
        assertEquals(cancellation, caught)
    }
}
