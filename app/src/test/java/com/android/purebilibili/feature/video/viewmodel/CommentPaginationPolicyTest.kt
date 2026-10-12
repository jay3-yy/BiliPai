package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentPaginationPolicyTest {

    @Test
    fun `guest hot comments should not show zero count when visible comments exist`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 0, isEnd = false, next = 2),
            replies = emptyList(),
            hots = listOf(ReplyItem(rpid = 1L))
        )

        val resolution = resolveCommentPageResolution(
            data = data,
            pageToLoad = 1,
            previousRepliesSize = 0,
            combinedRepliesSize = 1,
            newRepliesSize = 0,
            fallbackCount = 0
        )

        assertEquals(1, resolution.totalCount)
        assertFalse(resolution.isEnd)
    }

    @Test
    fun `cursor is_end should terminate pagination`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 0, isEnd = true, next = 0),
            replies = listOf(ReplyItem(rpid = 1L))
        )

        val resolution = resolveCommentPageResolution(
            data = data,
            pageToLoad = 2,
            previousRepliesSize = 1,
            combinedRepliesSize = 1,
            newRepliesSize = 0,
            fallbackCount = 0
        )

        assertTrue(resolution.isEnd)
    }

    @Test
    fun `legacy page count should remain preferred when available`() {
        val data = ReplyData(
            replies = listOf(ReplyItem(rpid = 1L)),
            page = com.android.purebilibili.data.model.response.ReplyPage(count = 56)
        )

        val resolution = resolveCommentPageResolution(
            data = data,
            pageToLoad = 1,
            previousRepliesSize = 0,
            combinedRepliesSize = 1,
            newRepliesSize = 1,
            fallbackCount = 0
        )

        assertEquals(56, resolution.totalCount)
        assertFalse(resolution.isEnd)
    }

    @Test
    fun `pagination should end when fetched page adds no unique replies`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 100, isEnd = false, next = 3),
            replies = listOf(ReplyItem(rpid = 1L), ReplyItem(rpid = 2L))
        )

        val resolution = resolveCommentPageResolution(
            data = data,
            pageToLoad = 2,
            previousRepliesSize = 2,
            combinedRepliesSize = 2,
            newRepliesSize = 2,
            fallbackCount = 0
        )

        assertTrue(resolution.isEnd)
    }

    @Test
    fun `pagination should keep detail reply count when guest api returns zero total`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 0, isEnd = false, next = 2),
            replies = listOf(ReplyItem(rpid = 1L))
        )

        val resolution = resolveCommentPageResolution(
            data = data,
            pageToLoad = 1,
            previousRepliesSize = 0,
            combinedRepliesSize = 1,
            newRepliesSize = 1,
            fallbackCount = 128
        )

        assertEquals(128, resolution.totalCount)
        assertFalse(resolution.isEnd)
    }

    @Test
    fun `reply data prefers legacy acount over root count when available`() {
        val data = ReplyData(
            page = com.android.purebilibili.data.model.response.ReplyPage(
                count = 12,
                acount = 34
            )
        )

        assertEquals(34, data.getAllCount())
    }

    @Test
    fun `sub reply page should continue when rest page metadata has more pages`() {
        assertFalse(
            resolveSubReplyPageEnd(
                cursorIsEnd = true,
                fetchedReplyCount = 20,
                loadedReplyCount = 20,
                remoteReplyCount = 20,
                requestedPage = 1,
                restPage = com.android.purebilibili.data.model.response.ReplyPage(
                    num = 1,
                    size = 20,
                    count = 200
                )
            )
        )
        assertTrue(
            resolveSubReplyPageEnd(
                cursorIsEnd = false,
                fetchedReplyCount = 20,
                loadedReplyCount = 200,
                remoteReplyCount = 200,
                requestedPage = 10,
                restPage = com.android.purebilibili.data.model.response.ReplyPage(
                    num = 10,
                    size = 20,
                    count = 200
                )
            )
        )
    }

    @Test
    fun `sub reply page should end when loaded count reaches declared total`() {
        assertTrue(
            resolveSubReplyPageEnd(
                cursorIsEnd = false,
                fetchedReplyCount = 10,
                loadedReplyCount = 200,
                remoteReplyCount = 200,
                requestedPage = 10
            )
        )
    }

    @Test
    fun `sub reply loaded total count should not shrink across sparse pages`() {
        assertEquals(
            200,
            resolveSubReplyLoadedTotalCount(
                rootReply = ReplyItem(count = 200, rcount = 200),
                loadedReplyCount = 80,
                remoteReplyCount = 0,
                previousTotalCount = 200
            )
        )
    }

    @Test
    fun `sub reply remote total count prefers reply detail page count`() {
        val data = ReplyData(
            page = com.android.purebilibili.data.model.response.ReplyPage(count = 230),
            root = ReplyItem(count = 12, rcount = 12)
        )

        assertEquals(230, resolveSubReplyRemoteTotalCount(data))
    }

    @Test
    fun `sub reply remote total count ignores a smaller page window`() {
        val data = ReplyData(
            page = com.android.purebilibili.data.model.response.ReplyPage(count = 20),
            root = ReplyItem(count = 80, rcount = 80)
        )

        assertEquals(80, resolveSubReplyRemoteTotalCount(data))
    }

    @Test
    fun `sub reply remote total count falls back to root reply declared count`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 0),
            root = ReplyItem(count = 0, rcount = 0)
        )

        assertEquals(
            180,
            resolveSubReplyRemoteTotalCount(
                data = data,
                rootReply = ReplyItem(count = 180, rcount = 180)
            )
        )
    }

    @Test
    fun `sub reply page should keep pagination open when detail count exceeds loaded items`() {
        assertFalse(
            resolveSubReplyPageEnd(
                cursorIsEnd = true,
                fetchedReplyCount = 2,
                loadedReplyCount = 2,
                remoteReplyCount = 80,
                requestedPage = 1
            )
        )
        assertTrue(
            resolveSubReplyPageEnd(
                cursorIsEnd = true,
                fetchedReplyCount = 8,
                loadedReplyCount = 8,
                remoteReplyCount = 8,
                requestedPage = 1
            )
        )
    }

    @Test
    fun `sub reply page does not treat a smaller page count as the total`() {
        assertFalse(
            resolveSubReplyPageEnd(
                cursorIsEnd = true,
                fetchedReplyCount = 20,
                loadedReplyCount = 20,
                remoteReplyCount = 80,
                requestedPage = 1,
                restPage = com.android.purebilibili.data.model.response.ReplyPage(
                    num = 1,
                    size = 20,
                    count = 20
                )
            )
        )
    }

    @Test
    fun `sub reply sparse pages continue until declared final page`() {
        assertFalse(
            resolveSubReplyPageEnd(
                cursorIsEnd = false,
                fetchedReplyCount = 0,
                loadedReplyCount = 4,
                remoteReplyCount = 200,
                requestedPage = 2
            )
        )
        assertTrue(
            resolveSubReplyPageEnd(
                cursorIsEnd = false,
                fetchedReplyCount = 0,
                loadedReplyCount = 4,
                remoteReplyCount = 200,
                requestedPage = 10
            )
        )
    }

    @Test
    fun `routed comment root prefers loaded root reply`() {
        val loaded = ReplyItem(rpid = 11L)
        val remote = ReplyData(root = ReplyItem(rpid = 11L, content = com.android.purebilibili.data.model.response.ReplyContent(message = "remote")))

        assertEquals(
            loaded,
            resolveRoutedCommentRootReply(
                loadedReplies = listOf(loaded),
                remoteData = remote,
                rootReplyId = 11L
            )
        )
    }

    @Test
    fun `routed comment root falls back to remote root reply`() {
        val remoteRoot = ReplyItem(rpid = 22L)

        assertEquals(
            remoteRoot,
            resolveRoutedCommentRootReply(
                loadedReplies = emptyList(),
                remoteData = ReplyData(root = remoteRoot),
                rootReplyId = 22L
            )
        )
    }

    @Test
    fun `routed comment root ignores unrelated remote reply`() {
        assertEquals(
            null,
            resolveRoutedCommentRootReply(
                loadedReplies = emptyList(),
                remoteData = ReplyData(root = ReplyItem(rpid = 33L)),
                rootReplyId = 44L
            )
        )
    }

    @Test
    fun `routed sub reply open only starts after aid is ready`() {
        assertFalse(
            shouldStartRoutedSubReplyOpen(
                rootReplyId = 11L,
                currentAid = 0L
            )
        )
        assertFalse(
            shouldStartRoutedSubReplyOpen(
                rootReplyId = 0L,
                currentAid = 100L
            )
        )
        assertTrue(
            shouldStartRoutedSubReplyOpen(
                rootReplyId = 11L,
                currentAid = 100L
            )
        )
    }

}
