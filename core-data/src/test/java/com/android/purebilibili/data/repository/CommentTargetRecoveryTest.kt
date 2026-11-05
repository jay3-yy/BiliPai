package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcException
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommentTargetRecoveryTest {
    private val oid = 117556090309934L
    private val rootId = 322992857728L
    private val targetId = 323002515200L
    private val target = ReplyItem(
        rpid = targetId,
        oid = oid,
        root = rootId,
        replyType = 1,
        parent = 323002148192L,
        dialog = 323001363376L,
        content = ReplyContent(message = "可读取的目标回复"),
    )
    private val unavailable = BiliGrpcException(200, "2", "12006")

    @Test
    fun `unavailable first thread recovers exact target without inventing a root or total`() = runTest {
        for (offset in listOf(null, "")) {
            var reads = 0
            val result = recoverUnavailableSubReplyTarget(
                oid, 1, rootId, offset, targetId, Result.failure(unavailable),
            ) {
                reads++
                ReplyResponse(data = ReplyData(
                    root = target,
                    replies = listOf(ReplyItem(rpid = 999L)),
                    cursor = ReplyCursor(allCount = 123, isEnd = false),
                    grpcNextOffset = "not-a-grpc-continuation",
                ))
            }.getOrThrow()
            assertEquals(1, reads)
            assertTrue(result.isTargetOnly)
            assertNull(result.root)
            assertEquals(listOf(target), result.replies)
            assertEquals(0, result.getAllCount())
            assertTrue(result.cursor.isEnd)
            assertEquals("", result.grpcNextOffset)
            assertNull(result.mainListContinuation)
        }
    }

    @Test
    fun `ordinary success remains complete and never reads target`() = runTest {
        val loaded = ReplyData(
            root = ReplyItem(rpid = rootId),
            replies = listOf(target),
            grpcNextOffset = "original-order",
        )
        val result = recoverUnavailableSubReplyTarget(
            oid, 1, rootId, null, targetId, Result.success(loaded),
        ) { error("A successful thread must not read REST") }.getOrThrow()
        assertSame(loaded, result)
        assertFalse(result.isTargetOnly)
    }

    @Test
    fun `unknown permission risk and text-only errors never recover`() = runTest {
        for (failure in listOf(
            BiliGrpcException(200, "2", "12345"),
            BiliGrpcException(200, "2", "-101"),
            BiliGrpcException(200, "2", "-403"),
            BiliGrpcException(200, "2", "-352"),
            BiliGrpcException(200, "2", "-412"),
            BiliGrpcException(200, "2", "12002"),
            BiliGrpcException(503, "2", "12006"),
            BiliGrpcException(200, "7", "12006"),
            BiliGrpcException(200, "2", "error 12006"),
            IllegalStateException("12006"),
            CommentReadException(12006),
        )) {
            val result = recoverUnavailableSubReplyTarget(
                oid, 1, rootId, null, targetId, Result.failure(failure),
            ) { error("Only structured gRPC business 12006 may recover") }
            assertSame(failure, result.exceptionOrNull())
        }
    }

    @Test
    fun `later cursor never switches grpc pagination to rest`() = runTest {
        for (offset in listOf("grpc-next-page", " ")) {
            val result = recoverUnavailableSubReplyTarget(
                oid, 1, rootId, offset, targetId, Result.failure(unavailable),
            ) { error("A continuation must never switch sources") }
            val failure = assertIs<CommentReadException>(result.exceptionOrNull())
            assertEquals(12006, failure.code)
            assertSame(unavailable, failure.cause)
        }
    }

    @Test
    fun `missing root same-root target and invalid identities do not recover`() = runTest {
        for ((requestOid, requestType, requestRoot, requestTarget) in listOf(
            listOf(oid, 1L, rootId, 0L),
            listOf(oid, 1L, rootId, -1L),
            listOf(oid, 1L, rootId, rootId),
            listOf(oid, 1L, 0L, targetId),
            listOf(0L, 1L, rootId, targetId),
            listOf(oid, 0L, rootId, targetId),
        )) {
            val result = recoverUnavailableSubReplyTarget(
                requestOid, requestType.toInt(), requestRoot, null, requestTarget,
                Result.failure(unavailable),
            ) { error("An invalid request cannot recover a target") }
            assertEquals(12006, assertIs<CommentReadException>(result.exceptionOrNull()).code)
        }
    }

    @Test
    fun `every target identity field must match and another reply cannot substitute`() = runTest {
        for (wrong in listOf(
            target.copy(rpid = targetId + 1),
            target.copy(oid = oid + 1),
            target.copy(replyType = 17),
            target.copy(root = rootId + 1),
            target.copy(root = 0L),
        )) {
            var reads = 0
            val result = recoverUnavailableSubReplyTarget(
                oid, 1, rootId, null, targetId, Result.failure(unavailable),
            ) {
                reads++
                ReplyResponse(data = ReplyData(root = wrong, replies = listOf(target)))
            }
            assertEquals(1, reads)
            assertIs<IllegalArgumentException>(result.exceptionOrNull())
        }
    }

    @Test
    fun `unreadable target missing payload and transport failures remain failures without retry`() = runTest {
        for (response in listOf(
            ReplyResponse(code = 12006, message = "已删除"),
            ReplyResponse(code = -403, message = "禁止访问"),
            ReplyResponse(data = null),
            ReplyResponse(data = ReplyData(replies = listOf(target))),
        )) {
            var reads = 0
            val result = recoverUnavailableSubReplyTarget(
                oid, 1, rootId, null, targetId, Result.failure(unavailable),
            ) { reads++; response }
            assertEquals(1, reads)
            assertTrue(result.isFailure)
            if (response.code != 0) {
                val failure = assertIs<CommentReadException>(result.exceptionOrNull())
                assertEquals(response.code, failure.code)
            }
        }
        val offline = IllegalStateException("offline")
        var reads = 0
        val result = recoverUnavailableSubReplyTarget(
            oid, 1, rootId, null, targetId, Result.failure(unavailable),
        ) { reads++; throw offline }
        assertEquals(1, reads)
        assertSame(offline, result.exceptionOrNull())
    }

    @Test
    fun `cancellation propagates from both thread and target reads`() = runTest {
        val cancellation = CancellationException("thread changed")
        var caught: CancellationException? = null
        try {
            recoverUnavailableSubReplyTarget(
                oid, 1, rootId, null, targetId, Result.failure(cancellation),
            ) { error("Cancelled threads must not start REST") }
        } catch (error: CancellationException) {
            caught = error
        }
        assertSame(cancellation, caught)
        caught = null
        try {
            recoverUnavailableSubReplyTarget(
                oid, 1, rootId, null, targetId, Result.failure(unavailable),
            ) { throw cancellation }
        } catch (error: CancellationException) {
            caught = error
        }
        assertSame(cancellation, caught)
    }

    @Test
    fun `target-only marker is local state and cannot be supplied by serialized data`() {
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.parseToJsonElement(json.encodeToString(ReplyData(isTargetOnly = true))).jsonObject
        assertFalse(encoded.containsKey("isTargetOnly"))
        assertFalse(json.decodeFromString<ReplyData>("""{"isTargetOnly":true}""").isTargetOnly)
    }
}
