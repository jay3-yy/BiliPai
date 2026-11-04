package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyPage
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyLoadedTotalCount
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyRemoteTotalCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubReplyDetailPresentationPolicyTest {


    @Test
    fun `recreated thread keeps its restored position while sort changes reset it`() {
        assertEquals(
            SubReplyDetailScrollRestoreAction.KEEP_POSITION,
            resolveSubReplyDetailScrollRestoreAction(null, false, false),
        )
        assertEquals(
            SubReplyDetailScrollRestoreAction.KEEP_POSITION,
            resolveSubReplyDetailScrollRestoreAction(true, true, true),
        )
        assertEquals(
            SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP,
            resolveSubReplyDetailScrollRestoreAction(false, false, true, sortChanged = true),
        )
        assertEquals(
            SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP,
            resolveSubReplyDetailScrollRestoreAction(true, false, true, sortChanged = true),
        )
    }

    @Test
    fun `sub reply prefetch continues when list cannot scroll but more replies remain`() {
        assertTrue(
            shouldPrefetchSubRepliesWhenListNotScrollable(
                isLoading = false,
                isEnd = false,
                canScrollForward = false
            )
        )
        assertFalse(
            shouldPrefetchSubRepliesWhenListNotScrollable(
                isLoading = false,
                isEnd = false,
                canScrollForward = true
            )
        )
    }

    @Test
    fun `sub reply manual load more follows server end independently of display totals`() {
        assertTrue(
            shouldShowSubReplyManualLoadMore(
                isLoading = false,
                isEnd = false
            )
        )
        assertFalse(
            shouldShowSubReplyManualLoadMore(
                isLoading = false,
                isEnd = true
            )
        )
    }


    @Test
    fun `sub reply target list index resolves root and child positions`() {
        val replies = listOf(
            ReplyItem(rpid = 20L),
            ReplyItem(rpid = 30L)
        )

        assertEquals(
            0,
            resolveSubReplyTargetListIndex(
                rootReplyId = 10L,
                visibleReplies = replies,
                targetReplyId = 10L
            )
        )
        assertEquals(
            2,
            resolveSubReplyTargetListIndex(
                rootReplyId = 10L,
                visibleReplies = replies,
                targetReplyId = 30L
            )
        )
        assertEquals(
            null,
            resolveSubReplyTargetListIndex(
                rootReplyId = 10L,
                visibleReplies = replies,
                targetReplyId = 99L
            )
        )
    }


    @Test
    fun `sub reply detail display count keeps root declared total when page only loaded partially`() {
        assertEquals(
            8,
            resolveSubReplyDetailDisplayCount(
                rootReply = ReplyItem(rcount = 8, replies = listOf(ReplyItem(rpid = 1))),
                loadedReplyCount = 3
            )
        )
        assertEquals(
            5,
            resolveSubReplyDetailDisplayCount(
                rootReply = ReplyItem(rcount = 2),
                loadedReplyCount = 5
            )
        )
    }

    @Test
    fun `sub reply detail display count uses remote total from loaded detail response`() {
        assertEquals(
            12,
            resolveSubReplyDetailDisplayCount(
                rootReply = ReplyItem(rcount = 2),
                loadedReplyCount = 4,
                remoteReplyCount = 12
            )
        )
    }

    @Test
    fun `sub reply detail display count preserves larger root declared count`() {
        assertEquals(
            2,
            resolveSubReplyDetailDisplayCount(
                rootReply = ReplyItem(count = 2, rcount = 2),
                loadedReplyCount = 1,
                remoteReplyCount = 1
            )
        )
    }

    @Test
    fun `sub reply loaded total count prefers remote detail count over stale root preview count`() {
        assertEquals(
            12,
            resolveSubReplyLoadedTotalCount(
                rootReply = ReplyItem(count = 2, rcount = 2),
                loadedReplyCount = 4,
                remoteReplyCount = 12
            )
        )
    }

    @Test
    fun `sub reply loaded total count preserves larger root declared count`() {
        assertEquals(
            2,
            resolveSubReplyLoadedTotalCount(
                rootReply = ReplyItem(count = 2, rcount = 2),
                loadedReplyCount = 1,
                remoteReplyCount = 1
            )
        )
    }

    @Test
    fun `sub reply remote total count uses max of declared counts to avoid window-size undercount`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 2),
            page = ReplyPage(count = 1),
            root = ReplyItem(count = 2, rcount = 1)
        )

        // 修复二级评论数量口径后：page.count 可能是分页窗口大小而非总数，
        // 取所有可用声明的最大值，避免“显示还有 N 条，详情却在首屏结束”。
        assertEquals(2, resolveSubReplyRemoteTotalCount(data))
    }

    @Test
    fun `sub reply loaded total count keeps previous remote total on sparse page`() {
        assertEquals(
            120,
            resolveSubReplyLoadedTotalCount(
                rootReply = ReplyItem(count = 120, rcount = 120),
                loadedReplyCount = 60,
                remoteReplyCount = 0,
                previousTotalCount = 120
            )
        )
    }


    @Test
    fun `conversation action should only show for directed reply text`() {
        assertTrue(
            shouldShowSubReplyConversationAction(
                buildReply(message = "回复 @前进四放映室：没错")
            )
        )
        assertFalse(
            shouldShowSubReplyConversationAction(
                buildReply(message = "又又又又更新？？？？")
            )
        )
    }

    @Test
    fun `conversation action should not reuse reply composer when handler is missing`() {
        val directedReply = buildReply(message = "回复 @前进四放映室：没错")

        assertFalse(
            shouldRenderSubReplyConversationAction(
                item = directedReply,
                hasConversationHandler = false
            )
        )
        assertTrue(
            shouldRenderSubReplyConversationAction(
                item = directedReply,
                hasConversationHandler = true
            )
        )
    }

    @Test
    fun `conversation items should filter by dialog id`() {
        val first = buildReply(
            rpid = 10,
            message = "回复 @甲：第一条",
            dialog = 100
        )
        val second = buildReply(
            rpid = 11,
            message = "回复 @乙：第二条",
            dialog = 100
        )
        val other = buildReply(
            rpid = 12,
            message = "回复 @丙：无关",
            dialog = 200
        )

        assertEquals(
            listOf(10L, 11L),
            resolveSubReplyConversationItems(
                anchorReply = first,
                subReplies = listOf(first, second, other)
            ).map { it.rpid }
        )
    }

    @Test
    fun `conversation items should fallback to clicked reply when dialog is unavailable`() {
        val clicked = buildReply(
            rpid = 10,
            message = "回复 @甲：第一条"
        )

        assertEquals(
            listOf(10L),
            resolveSubReplyConversationItems(
                anchorReply = clicked,
                subReplies = emptyList()
            ).map { it.rpid }
        )
    }

    @Test
    fun `list scroll reset key changes when entering conversation mode`() {
        assertEquals(
            SubReplyDetailListScrollResetKey(
                rootReplyId = 1L,
                conversationMode = false,
                firstConversationReplyId = null
            ),
            resolveSubReplyDetailListScrollResetKey(
                rootReplyId = 1L,
                effectiveConversationMode = false,
                visibleReplies = listOf(buildReply(rpid = 10, message = "回复 @甲：第一条"))
            )
        )
        assertEquals(
            SubReplyDetailListScrollResetKey(
                rootReplyId = 1L,
                conversationMode = true,
                firstConversationReplyId = 10L
            ),
            resolveSubReplyDetailListScrollResetKey(
                rootReplyId = 1L,
                effectiveConversationMode = true,
                visibleReplies = listOf(buildReply(rpid = 10, message = "回复 @甲：第一条"))
            )
        )
    }

    @Test
    fun `leaving conversation restores saved scroll instead of jumping to top`() {
        assertEquals(
            SubReplyDetailScrollRestoreAction.RESTORE_SAVED,
            resolveSubReplyDetailScrollRestoreAction(
                previousConversationMode = true,
                currentConversationMode = false,
                hasSavedPosition = true,
            )
        )
        assertEquals(
            SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP,
            resolveSubReplyDetailScrollRestoreAction(
                previousConversationMode = true,
                currentConversationMode = false,
                hasSavedPosition = false,
            )
        )
        assertEquals(
            SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP,
            resolveSubReplyDetailScrollRestoreAction(
                previousConversationMode = false,
                currentConversationMode = true,
                hasSavedPosition = true,
            )
        )
        assertTrue(
            shouldSaveSubReplyDetailScrollBeforeConversationEnter(
                previousConversationMode = false,
                currentConversationMode = true,
            )
        )
        assertFalse(
            shouldSaveSubReplyDetailScrollBeforeConversationEnter(
                previousConversationMode = true,
                currentConversationMode = false,
            )
        )
    }


    private fun buildReply(
        rpid: Long = 200L,
        message: String,
        parent: Long = 0L,
        dialog: Long = 0L
    ): ReplyItem {
        return ReplyItem(
            rpid = rpid,
            parent = parent,
            dialog = dialog,
            member = ReplyMember(
                mid = "12",
                uname = "ReplyUser",
            ),
            content = ReplyContent(message = message)
        )
    }
}
