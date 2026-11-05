package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyControl
import com.android.purebilibili.data.model.response.ReplyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentReadAccessPolicyTest {

    @Test
    fun `closed comment codes preserve server explanation and prevent input`() {
        for (code in listOf(12061, 12002)) {
            val failure = CommentReadException(code, "评论区已关闭，暂时无法查看")
            assertEquals(code, failure.code)
            assertEquals("评论区已关闭，暂时无法查看", failure.message)
            assertTrue(failure.commentInputBlocked)
            assertFalse(shouldFallbackCommentRead(code))
        }
        assertEquals("评论区暂不可用", CommentReadException(12061, " ").message)
        assertEquals("评论区已关闭", CommentReadException(12002).message)
        assertFalse(CommentReadException(-412).commentInputBlocked)
        assertFalse(CommentReadException(-101).commentInputBlocked)
    }


    @Test
    fun `unavailable reply preserves business code without blocking input`() {
        val failure = CommentReadException(12006, "该评论已被删除")
        assertEquals(12006, failure.code)
        assertFalse(failure.commentInputBlocked)
        assertFalse(shouldFallbackCommentRead(12006))
    }

    @Test
    fun `sorted thread location supplement preserves ranking cursor and existing controls`() {
        val root = ReplyItem(rpid = 100L, replyControl = ReplyControl(location = "IP属地：上海"))
        val data = ReplyData(
            root = root,
            replies = listOf(
                ReplyItem(rpid = 3L, like = 99, replyControl = ReplyControl(translationSwitch = 2)),
                ReplyItem(rpid = 1L, replyControl = ReplyControl(location = "IP属地：北京")),
                ReplyItem(rpid = 2L),
            ),
            cursor = ReplyCursor(isEnd = false, next = 42),
            grpcNextOffset = "hot-ranking-cursor",
        )
        val merged = mergeCommentReplyLocations(data, listOf(
            ReplyItem(rpid = 1L, replyControl = ReplyControl(location = "IP属地：广东")),
            ReplyItem(rpid = 3L, like = 0, replyControl = ReplyControl(location = "IP属地：江苏")),
            ReplyItem(rpid = 999L, replyControl = ReplyControl(location = "IP属地：浙江")),
        ))
        assertEquals(listOf(3L, 1L, 2L), merged.replies.orEmpty().map { it.rpid })
        assertEquals("IP属地：江苏", merged.replies.orEmpty()[0].replyControl?.location)
        assertEquals(2, merged.replies.orEmpty()[0].replyControl?.translationSwitch)
        assertEquals(99, merged.replies.orEmpty()[0].like)
        assertEquals("IP属地：北京", merged.replies.orEmpty()[1].replyControl?.location)
        assertEquals(data.replies.orEmpty()[2], merged.replies.orEmpty()[2])
        assertEquals(root, merged.root)
        assertEquals(data.cursor, merged.cursor)
        assertEquals(data.grpcNextOffset, merged.grpcNextOffset)
    }

    @Test
    fun `location supplement finds nested target without borrowing root location`() {
        val data = ReplyData(replies = listOf(ReplyItem(rpid = 2L)))
        val root = ReplyItem(
            rpid = 1L,
            replyControl = ReplyControl(location = "IP属地：上海"),
            replies = listOf(ReplyItem(rpid = 2L, replyControl = ReplyControl(location = "IP属地：北京"))),
        )
        val candidates = collectReplyLocationCandidates(ReplyData(replies = listOf(root)))
        val merged = mergeCommentReplyLocations(data, candidates)
        assertEquals("IP属地：北京", merged.replies.orEmpty().single().replyControl?.location)
        assertEquals(data, mergeCommentReplyLocations(data, listOf(root.copy(replies = null))))
        assertEquals(data, mergeCommentReplyLocations(data, listOf(ReplyItem(rpid = 2L))))
    }

    @Test
    fun `resolveCommentReadPlan prefers auth for logged user`() {
        val plan = resolveCommentReadPlan(hasSession = true)
        assertEquals(CommentReadApiMode.AUTH, plan.primary)
        assertEquals(CommentReadApiMode.GUEST, plan.fallback)
    }

    @Test
    fun `resolveCommentReadPlan prefers guest for anonymous user`() {
        val plan = resolveCommentReadPlan(hasSession = false)
        assertEquals(CommentReadApiMode.GUEST, plan.primary)
        assertEquals(CommentReadApiMode.AUTH, plan.fallback)
    }

    @Test
    fun `shouldFallbackCommentRead covers unstable api errors`() {
        assertTrue(shouldFallbackCommentRead(-101))
        assertTrue(shouldFallbackCommentRead(-111))
        assertTrue(shouldFallbackCommentRead(-352))
        assertTrue(shouldFallbackCommentRead(-403))
        assertTrue(shouldFallbackCommentRead(-412))
        assertFalse(shouldFallbackCommentRead(12002))
    }

    @Test
    fun `resolveCommentReadErrorMessage avoids forcing login for anonymous read`() {
        assertEquals(
            "评论加载失败，请稍后重试或切换排序",
            resolveCommentReadErrorMessage(-101)
        )
    }

    @Test
    fun `guest hot comment read falls back when success payload is empty but count exists`() {
        assertTrue(
            shouldFallbackGuestHotCommentReadOnEmptySuccess(
                primaryMode = CommentReadApiMode.GUEST,
                page = 1,
                mode = 3,
                responseCode = 0,
                data = ReplyData(
                    cursor = ReplyCursor(allCount = 6118, isEnd = true, next = 0),
                    replies = emptyList(),
                    hots = emptyList()
                )
            )
        )
    }

    @Test
    fun `hot first page falls back when successful endpoint omits comments and count`() {
        assertTrue(
            shouldFallbackHotCommentReadOnEmptySuccess(
                page = 1,
                mode = 3,
                responseCode = 0,
                data = ReplyData(
                    cursor = ReplyCursor(allCount = 0, isEnd = true, next = 0),
                    replies = emptyList(),
                    hots = emptyList(),
                ),
            )
        )
        assertTrue(
            shouldFallbackHotCommentReadOnEmptySuccess(
                page = 1,
                mode = 3,
                responseCode = 0,
                data = null,
            )
        )
    }

    @Test
    fun `hot empty fallback is limited to first page and hot sort`() {
        val emptyData = ReplyData(replies = emptyList(), hots = emptyList())
        assertFalse(shouldFallbackHotCommentReadOnEmptySuccess(2, 3, 0, emptyData))
        assertFalse(shouldFallbackHotCommentReadOnEmptySuccess(1, 2, 0, emptyData))
        assertFalse(shouldFallbackHotCommentReadOnEmptySuccess(1, 3, -1, emptyData))
    }

    @Test
    fun `empty renderable success falls back when total count exists`() {
        assertTrue(
            shouldFallbackCommentReadOnEmptyRenderableSuccess(
                responseCode = 0,
                data = ReplyData(
                    cursor = ReplyCursor(allCount = 6118, isEnd = true, next = 0),
                    replies = emptyList(),
                    hots = emptyList()
                )
            )
        )
    }

    @Test
    fun `empty renderable success does not fall back for truly empty comment area`() {
        assertFalse(
            shouldFallbackCommentReadOnEmptyRenderableSuccess(
                responseCode = 0,
                data = ReplyData(
                    cursor = ReplyCursor(allCount = 0, isEnd = true, next = 0),
                    replies = emptyList(),
                    hots = emptyList()
                )
            )
        )
    }

    @Test
    fun `guest hot comment read keeps payload when renderable comments exist`() {
        assertFalse(
            shouldFallbackGuestHotCommentReadOnEmptySuccess(
                primaryMode = CommentReadApiMode.GUEST,
                page = 1,
                mode = 3,
                responseCode = 0,
                data = ReplyData(
                    cursor = ReplyCursor(allCount = 6118, isEnd = false, next = 2),
                    replies = listOf(ReplyItem(rpid = 1L))
                )
            )
        )
    }

    @Test
    fun `hasRenderableCommentPayload counts top or hot comments as visible content`() {
        assertTrue(
            hasRenderableCommentPayload(
                ReplyData(
                    hots = listOf(ReplyItem(rpid = 1L))
                )
            )
        )
    }

    @Test
    fun `vote card alone remains a renderable grpc response`() {
        val data = ReplyData(voteCard = com.android.purebilibili.data.model.response.ReplyVoteCard(
            voteId = 123L,
            title = "投票",
        ))

        assertTrue(hasRenderableCommentPayload(data))
        assertFalse(shouldFallbackGrpcCommentReadOnMissingLocation(data))
    }

    @Test
    fun `grpc comment read falls back when rendered comments all miss ip location`() {
        assertTrue(
            shouldFallbackGrpcCommentReadOnMissingLocation(
                ReplyData(
                    replies = listOf(
                        ReplyItem(rpid = 1L),
                        ReplyItem(rpid = 2L, replyControl = ReplyControl(location = ""))
                    )
                )
            )
        )
    }

    @Test
    fun `grpc comment read keeps payload when any rendered comment has ip location`() {
        assertFalse(
            shouldFallbackGrpcCommentReadOnMissingLocation(
                ReplyData(
                    hots = listOf(
                        ReplyItem(
                            rpid = 1L,
                            replyControl = ReplyControl(location = "IP属地：上海")
                        )
                    ),
                    replies = listOf(ReplyItem(rpid = 2L))
                )
            )
        )
        assertTrue(
            hasAnyReplyLocation(
                ReplyData(
                    replies = listOf(
                        ReplyItem(
                            rpid = 3L,
                            replyControl = ReplyControl(location = "IP属地：北京")
                        )
                    )
                )
            )
        )
    }
}
