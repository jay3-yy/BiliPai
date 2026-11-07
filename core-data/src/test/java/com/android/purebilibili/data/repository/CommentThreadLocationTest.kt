package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcException
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommentThreadLocationTest {
    private val oid = 117556090309934L
    private val rootId = 319212191617L
    private val childId = 319251319265L
    private val root = ReplyItem(rpid = rootId, oid = oid)
    private val child = ReplyItem(rpid = childId, oid = oid, root = rootId, parent = 322989678896L)
    private val unavailable = BiliGrpcException(200, "2", "12006")
    private val firstPage = ReplyData(root = root, replies = emptyList(), grpcNextOffset = "ranked-next")

    @Test
    fun `missing wrong and child root hints resolve from the target even outside the first page`() = runTest {
        for (hint in listOf(0L, -1L, childId, 99L, rootId)) {
            val threadReads = mutableListOf<Long>()
            val result = readLocatedSubReplyPage(oid, 1, hint, childId, null,
                readReply = { id ->
                    assertEquals(childId, id)
                    ReplyResponse(data = ReplyData(root = child))
                },
                readThread = { id -> threadReads += id; Result.success(firstPage) },
            ).getOrThrow()
            assertEquals(listOf(rootId), threadReads)
            assertEquals(rootId, result.root?.rpid)
            assertEquals(rootId, result.resolvedRootId)
            assertEquals(childId, result.resolvedTargetReplyId)
            assertEquals("ranked-next", result.grpcNextOffset)
            assertTrue(result.replies.orEmpty().isEmpty()) // No injected target changes the server ranking.
            assertFalse(result.isTargetOnly)
        }
    }

    @Test
    fun `child passed as sole root is repaired before recovering unavailable thread`() = runTest {
        val roots = mutableListOf<Long>()
        val result = readLocatedSubReplyPage(oid, 1, childId, 0, null,
            readReply = { id -> assertEquals(childId, id); ReplyResponse(data = ReplyData(root = child)) },
            readThread = { id ->
                roots += id
                if (id == childId) Result.failure(unavailable) else Result.success(firstPage)
            },
        ).getOrThrow()
        assertEquals(listOf(childId, rootId), roots)
        assertEquals(root, result.root)
        assertEquals(childId, result.resolvedTargetReplyId)
        assertFalse(result.isTargetOnly)
    }

    @Test
    fun `successful child detail loads the actual root instead of rejecting the readable child`() = runTest {
        val reads = mutableListOf<Long>()
        val result = readLocatedSubReplyPage(oid, 1, childId, 0, null,
            readReply = { error("The child response already carries its identity") },
            readThread = { id ->
                reads += id
                Result.success(if (id == childId) ReplyData(root = child) else firstPage)
            },
        ).getOrThrow()
        assertEquals(listOf(childId, rootId), reads)
        assertEquals(root, result.root)
        assertEquals(rootId, result.resolvedRootId)
        assertEquals(childId, result.resolvedTargetReplyId)
        assertEquals("ranked-next", result.grpcNextOffset)
    }

    @Test
    fun `automatically returned root starts from the floor beginning rather than the child seek window`() = runTest {
        val earlier = child.copy(rpid = childId - 1)
        val result = readLocatedSubReplyPage(oid, 1, childId, 0, null,
            readReply = { id -> assertEquals(childId, id); ReplyResponse(data = ReplyData(root = child)) },
            readThread = { id ->
                Result.success(if (id == childId) {
                    ReplyData(root = root, replies = listOf(child), cursor = ReplyCursor(isEnd = true))
                } else {
                    firstPage.copy(replies = listOf(earlier))
                })
            },
        ).getOrThrow()
        assertEquals(root, result.root)
        assertEquals(rootId, result.resolvedRootId)
        assertEquals(childId, result.resolvedTargetReplyId)
        assertEquals(listOf(earlier), result.replies)
        assertEquals("ranked-next", result.grpcNextOffset)
    }

    @Test
    fun `genuinely unavailable root retains readable child and real root identity without fabricating root`() = runTest {
        var lookups = 0
        val roots = mutableListOf<Long>()
        val result = readLocatedSubReplyPage(oid, 1, 99, childId, null,
            readReply = { lookups++; ReplyResponse(data = ReplyData(root = child)) },
            readThread = { roots += it; Result.failure(unavailable) },
        ).getOrThrow()
        assertEquals(1, lookups)
        assertEquals(listOf(rootId), roots)
        assertTrue(result.isTargetOnly)
        assertNull(result.root)
        assertEquals(listOf(child), result.replies)
        assertEquals(rootId, result.resolvedRootId)
        assertEquals(childId, result.resolvedTargetReplyId)
        assertTrue(result.cursor.isEnd)
    }

    @Test
    fun `root as target opens root without inventing a child`() = runTest {
        val result = readLocatedSubReplyPage(oid, 1, 0, rootId, null,
            readReply = { ReplyResponse(data = ReplyData(root = root)) },
            readThread = { assertEquals(rootId, it); Result.success(firstPage) },
        ).getOrThrow()
        assertEquals(rootId, result.resolvedRootId)
        assertEquals(0L, result.resolvedTargetReplyId)
    }

    @Test
    fun `known root success avoids unnecessary lookup and cursor pages never relocate`() = runTest {
        for (offset in listOf(null, "ranked-next", " ")) {
            val result = readLocatedSubReplyPage(oid, 1, rootId, 0, offset,
                readReply = { error("No lookup needed") },
                readThread = { assertEquals(rootId, it); Result.success(firstPage) },
            ).getOrThrow()
            assertEquals(firstPage.grpcNextOffset, result.grpcNextOffset)
            assertEquals(firstPage.replies, result.replies)
        }
    }

    @Test
    fun `deleted denied or rate limited target does not fall back to an unrelated supplied root`() = runTest {
        for (code in listOf(12006, -403, -352, 12002)) {
            val result = readLocatedSubReplyPage(oid, 1, rootId, childId, null,
                readReply = { ReplyResponse(code = code) },
                readThread = { error("Unreadable target must not open unrelated root") },
            )
            assertEquals(code, assertIs<CommentReadException>(result.exceptionOrNull()).code)
        }
    }

    @Test
    fun `missing or mismatched lookup identities do not resolve`() = runTest {
        for (reply in listOf(null, child.copy(rpid = 1), child.copy(oid = 1),
            child.copy(replyType = 17), child.copy(root = 0), child.copy(root = -1))) {
            val result = readLocatedSubReplyPage(oid, 1, rootId, childId, null,
                readReply = { ReplyResponse(data = ReplyData(root = reply)) },
                readThread = { error("Invalid lookup must not load thread") },
            )
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `wrong returned root is a protocol mismatch rather than unreadable target recovery`() = runTest {
        val result = readLocatedSubReplyPage(oid, 1, rootId, childId, null,
            readReply = { ReplyResponse(data = ReplyData(root = child)) },
            readThread = { Result.success(ReplyData(root = root.copy(rpid = 99))) },
        )
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun `transport failures remain intact and cancellation stops before thread lookup`() = runTest {
        val offline = IllegalStateException("offline")
        val failed = readLocatedSubReplyPage(oid, 1, rootId, 0, null,
            readReply = { error("Transport failure is not a wrong root") },
            readThread = { Result.failure(offline) },
        )
        assertSame(offline, failed.exceptionOrNull())
        val cancelled = CancellationException("account changed")
        try {
            readLocatedSubReplyPage(oid, 1, rootId, childId, null,
                readReply = { throw cancelled }, readThread = { error("Cancelled lookup continued") },
            )
            error("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
    @Test
    fun `missing identity and continuation without root never issue a request`() = runTest {
        for ((hint, target, offset) in listOf(
            Triple(0L, 0L, null), Triple(-1L, -1L, null), Triple(0L, childId, "next"),
        )) {
            val result = readLocatedSubReplyPage(oid, 1, hint, target, offset,
                readReply = { error("Invalid request") }, readThread = { error("Invalid request") },
            )
            assertIs<IllegalArgumentException>(result.exceptionOrNull())
        }
    }

    @Test
    fun `cancellation returned by thread reader is not treated as unavailability`() = runTest {
        val cancelled = CancellationException("session ended")
        try {
            readLocatedSubReplyPage(oid, 1, rootId, 0, null,
                readReply = { error("Cancelled thread must not locate") },
                readThread = { Result.failure(cancelled) },
            )
            error("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
