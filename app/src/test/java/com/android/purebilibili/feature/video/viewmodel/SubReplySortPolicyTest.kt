package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.ReplyItem
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubReplySortPolicyTest {
    @Test
    fun `sort modes match PiliPlus DetailList protocol`() {
        assertEquals(2, SubReplySortMode.TIME.apiMode)
        assertEquals(3, SubReplySortMode.HOT.apiMode)
        assertEquals(SubReplySortMode.TIME, SubReplyUiState().sortMode)
        assertEquals(SubReplySortMode.HOT, SubReplySortMode.TIME.toggled())
        assertEquals(SubReplySortMode.TIME, SubReplySortMode.HOT.toggled())
    }

    @Test
    fun `changing sort discards old pages cursor and deep link target`() {
        val root = ReplyItem(rpid = 10L)
        val reply = ReplyItem(rpid = 11L)
        val current = SubReplyUiState(
            visible = true,
            rootReply = root,
            items = persistentListOf(reply),
            baseItems = persistentListOf(reply),
            page = 4,
            basePage = 4,
            totalCount = 200,
            isEnd = true,
            baseIsEnd = true,
            grpcNextOffset = "old-time-cursor",
            baseGrpcNextOffset = "old-time-cursor",
            targetReplyId = 11L,
            error = "old error",
        )
        for (mode in SubReplySortMode.entries) {
            val reset = current.resetForSort(mode)
            assertEquals(mode, reset.sortMode)
            assertEquals(root, reset.rootReply)
            assertEquals(200, reset.totalCount)
            assertTrue(reset.visible)
            assertTrue(reset.isLoading)
            assertTrue(reset.items.isEmpty())
            assertTrue(reset.baseItems.isEmpty())
            assertEquals(1, reset.page)
            assertEquals(1, reset.basePage)
            assertFalse(reset.isEnd)
            assertFalse(reset.baseIsEnd)
            assertNull(reset.grpcNextOffset)
            assertNull(reset.baseGrpcNextOffset)
            assertNull(reset.error)
            assertEquals(0L, reset.targetReplyId)
        }
    }

    @Test
    fun `sorted pagination follows cursor instead of declared reply count`() {
        assertFalse(isSortedSubReplyPageEnd(false, "next-page"))
        assertTrue(isSortedSubReplyPageEnd(true, "next-page"))
        assertTrue(isSortedSubReplyPageEnd(false, null))
        assertTrue(isSortedSubReplyPageEnd(false, ""))
    }

    @Test
    fun `sent reply follows its loaded parent without reordering server replies`() {
        val first = ReplyItem(rpid = 30L, root = 10L)
        val parent = ReplyItem(rpid = 20L, root = 10L)
        val last = ReplyItem(rpid = 40L, root = 10L)
        val sent = resolveSentSubReply(ReplyItem(rpid = 50L), 10L, parent.rpid, parent)
        val state = SubReplyUiState(
            visible = true,
            rootReply = ReplyItem(rpid = 10L),
            items = persistentListOf(first, parent, last),
            baseItems = persistentListOf(first, parent, last),
            totalCount = 3
        ).withSentSubReply(sent)

        assertEquals(listOf(30L, 20L, 50L, 40L), state.items.map { it.rpid })
        assertEquals(state.items, state.baseItems)
        assertEquals(4, state.totalCount)
        assertEquals(10L, state.items[2].root)
        assertEquals(20L, state.items[2].parent)
        assertEquals(20L, state.items[2].dialog)
    }

    @Test
    fun `root replies and missing parents append instead of taking the first server position`() {
        val items = listOf(ReplyItem(rpid = 30L), ReplyItem(rpid = 20L))
        for (parentId in listOf(0L, 10L, 99L)) {
            val sent = resolveSentSubReply(ReplyItem(rpid = 50L), 10L, parentId, null)
            assertEquals(listOf(30L, 20L, 50L), insertSentSubReply(items, sent).map { it.rpid })
        }
    }

    @Test
    fun `repeated send result replaces the existing rpid in place without increasing count`() {
        val parent = ReplyItem(rpid = 20L, root = 10L)
        val oldReply = ReplyItem(rpid = 50L, root = 10L, parent = 20L)
        val sent = oldReply.copy(like = 7)
        val state = SubReplyUiState(
            visible = true,
            rootReply = ReplyItem(rpid = 10L),
            items = persistentListOf(oldReply, parent, oldReply),
            baseItems = persistentListOf(oldReply, parent, oldReply),
            totalCount = 2
        ).withSentSubReply(sent).withSentSubReply(sent)

        assertEquals(listOf(50L, 20L), state.items.map { it.rpid })
        assertEquals(7, state.items.first().like)
        assertEquals(state.items, state.baseItems)
        assertEquals(2, state.totalCount)
    }

    @Test
    fun `sending inside a conversation updates both conversation and restored all replies`() {
        val parent = ReplyItem(rpid = 20L, root = 10L, dialog = 19L)
        val unrelated = ReplyItem(rpid = 30L, root = 10L)
        val sent = resolveSentSubReply(ReplyItem(rpid = 50L), 10L, 20L, parent)
        val state = SubReplyUiState(
            visible = true,
            rootReply = ReplyItem(rpid = 10L),
            items = persistentListOf(parent),
            baseItems = persistentListOf(unrelated, parent),
            conversationAnchor = parent,
            totalCount = 2
        ).withSentSubReply(sent)

        assertEquals(listOf(20L, 50L), state.items.map { it.rpid })
        assertEquals(listOf(30L, 20L, 50L), state.baseItems.map { it.rpid })
        assertEquals(19L, state.items.last().dialog)
        assertEquals(3, state.totalCount)
    }

    @Test
    fun `switching conversation while sending does not insert into the unrelated dialog`() {
        val target = ReplyItem(rpid = 20L, root = 10L, dialog = 19L)
        val anchor = ReplyItem(rpid = 30L, root = 10L, dialog = 29L)
        val sent = resolveSentSubReply(ReplyItem(rpid = 50L), 10L, 20L, target)
        val state = SubReplyUiState(
            visible = true,
            rootReply = ReplyItem(rpid = 10L),
            items = persistentListOf(anchor),
            baseItems = persistentListOf(target, anchor),
            conversationAnchor = anchor,
            totalCount = 2
        ).withSentSubReply(sent)

        assertEquals(listOf(anchor), state.items)
        assertEquals(listOf(20L, 50L, 30L), state.baseItems.map { it.rpid })
        assertEquals(3, state.totalCount)
    }

    @Test
    fun `detail screen keeps its unfiltered items and base synchronized in conversation mode`() {
        val target = ReplyItem(rpid = 20L, root = 10L)
        val anchor = ReplyItem(rpid = 30L, root = 10L)
        val sent = resolveSentSubReply(ReplyItem(rpid = 50L), 10L, 20L, target)
        val state = SubReplyUiState(
            visible = true,
            rootReply = ReplyItem(rpid = 10L),
            items = persistentListOf(target, anchor),
            baseItems = persistentListOf(target, anchor),
            conversationAnchor = anchor,
        ).withSentSubReply(sent, conversationItemsAreFiltered = false)

        assertEquals(listOf(20L, 50L, 30L), state.items.map { it.rpid })
        assertEquals(state.items, state.baseItems)
    }

    @Test
    fun `late send results cannot mutate a closed switched or reopened thread`() {
        val open = SubReplyUiState(visible = true, rootReply = ReplyItem(rpid = 10L))
        val sent = ReplyItem(rpid = 50L, root = 10L, parent = 20L)
        assertTrue(shouldApplySentSubReplyResult(10L, 1L, 1L, open))
        assertFalse(shouldApplySentSubReplyResult(10L, 1L, 1L, open.copy(visible = false)))
        assertFalse(shouldApplySentSubReplyResult(10L, 1L, 1L, open.copy(rootReply = ReplyItem(rpid = 11L))))
        assertFalse(shouldApplySentSubReplyResult(10L, 1L, 2L, open))
        val closed = open.copy(visible = false)
        val switched = open.copy(rootReply = ReplyItem(rpid = 11L))
        assertEquals(closed, closed.withSentSubReply(sent))
        assertEquals(switched, switched.withSentSubReply(sent))
    }

    @Test
    fun `local reply context inherits nonroot parent dialog but keeps an explicit server dialog`() {
        val target = ReplyItem(rpid = 20L, root = 10L, parent = 19L)
        assertEquals(19L, resolveSentSubReply(ReplyItem(rpid = 50L), 10L, 20L, target).dialog)
        assertEquals(
            18L,
            resolveSentSubReply(ReplyItem(rpid = 50L, dialog = 18L), 10L, 20L, target).dialog
        )
        assertEquals(
            20L,
            resolveSentSubReply(ReplyItem(rpid = 50L), 10L, 20L, target.copy(parent = 10L)).dialog
        )
    }
}
