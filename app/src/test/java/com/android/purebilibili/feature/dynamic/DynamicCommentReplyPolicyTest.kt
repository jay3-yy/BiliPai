package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.comment.SentCommentReplyStore
import com.android.purebilibili.feature.video.viewmodel.withSentSubReply
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicCommentReplyPolicyTest {

    @Test
    fun `dynamic comment exposes sub reply entry when server says replies exist`() {
        val reply = ReplyItem(rpid = 1L, rcount = 4)

        assertTrue(canOpenDynamicSubReplies(reply))
        assertEquals(4, resolveDynamicSubReplyCount(reply))
    }

    @Test
    fun `dynamic comment falls back to inline reply preview count`() {
        val reply = ReplyItem(
            rpid = 1L,
            replies = listOf(
                ReplyItem(rpid = 2L),
                ReplyItem(rpid = 3L)
            )
        )

        assertTrue(canOpenDynamicSubReplies(reply))
        assertEquals(2, resolveDynamicSubReplyCount(reply))
    }

    @Test
    fun `dynamic comment hides sub reply entry when no replies exist`() {
        val reply = ReplyItem(rpid = 1L)

        assertFalse(canOpenDynamicSubReplies(reply))
        assertEquals(0, resolveDynamicSubReplyCount(reply))
    }

    @Test
    fun `sub reply append failure preserves existing items`() {
        val currentState = SubReplyUiState(
            items = listOf(ReplyItem(rpid = 1L), ReplyItem(rpid = 2L)).toImmutableList(),
            isLoading = true,
            page = 2
        )

        val result = resolveDynamicSubReplyStateAfterFailure(
            currentState = currentState,
            errorMessage = "加载失败"
        )

        assertEquals(listOf(1L, 2L), result.items.map { it.rpid })
        assertFalse(result.isLoading)
        assertEquals("加载失败", result.error)
    }

    @Test
    fun `sub reply success uses the full remote snapshot without previous overlays`() {
        val currentState = SubReplyUiState(
            items = listOf(ReplyItem(rpid = 1L), ReplyItem(rpid = 2L)).toImmutableList(),
            isLoading = true,
            page = 1
        )

        val result = resolveDynamicSubReplyStateAfterSuccess(
            currentState = currentState,
            remoteItems = listOf(ReplyItem(rpid = 1L), ReplyItem(rpid = 2L), ReplyItem(rpid = 3L)),
            page = 2,
            isEnd = false
        )

        assertEquals(listOf(1L, 2L, 3L), result.items.map { it.rpid })
        assertEquals(2, result.page)
        assertFalse(result.isLoading)
        assertFalse(result.isEnd)
    }

    @Test
    fun `comment header and empty copy use compact reply chrome`() {
        assertEquals("0条回复", resolveDynamicCommentCountLabel(-3))
        assertEquals("12条回复", resolveDynamicCommentCountLabel(12))
        assertEquals("1.2万条回复", resolveDynamicCommentCountLabel(12_000))
        assertEquals("还没有评论", resolveDynamicCommentEmptyLabel())
        assertEquals("发一条友善的评论", DYNAMIC_COMMENT_ROOT_INPUT_HINT)
    }

    @Test
    fun `feed comment opens the detail page instead of a bottom sheet`() {
        assertTrue(shouldOpenDynamicCommentsInDetailPage())
    }

    @Test
    fun `tapping a comment always opens the thread`() {
        val reply = ReplyItem(rpid = 8L)
        assertTrue(shouldOpenDynamicCommentThreadOnTap(reply))
        assertFalse(canOpenDynamicSubReplies(reply))
        assertFalse(shouldOpenDynamicCommentThreadOnTap(ReplyItem(rpid = 0L)))
        assertEquals(
            DynamicCommentSheetHostContent.THREAD_DETAIL,
            resolveDynamicCommentSheetHostContent(subReplyVisible = true),
        )
        assertEquals(
            DynamicCommentSheetHostContent.MAIN_LIST,
            resolveDynamicCommentSheetHostContent(subReplyVisible = false),
        )
    }

    @Test
    fun `reply target uses root when present and otherwise the comment itself`() {
        val nested = ReplyItem(rpid = 9L, root = 3L, member = com.android.purebilibili.data.model.response.ReplyMember(uname = "小红"))
        val root = resolveDynamicCommentReplyTarget(ReplyItem(rpid = 3L, member = com.android.purebilibili.data.model.response.ReplyMember(uname = "小明")))

        assertEquals(DynamicCommentComposerTarget(rootRpid = 3L, parentRpid = 3L, uname = "小明"), root)
        assertEquals(DynamicCommentComposerTarget(rootRpid = 3L, parentRpid = 9L, uname = "小红"), resolveDynamicCommentReplyTarget(nested))
    }

    @Test
    fun `liking a comment updates action and count without touching siblings`() {
        val comments = listOf(
            ReplyItem(
                rpid = 1L,
                like = 3,
                action = 0,
                replies = listOf(ReplyItem(rpid = 2L, like = 1, action = 0))
            ),
            ReplyItem(rpid = 3L, like = 8, action = 1)
        )

        val child = applyDynamicCommentLike(comments[0].replies.orEmpty().first(), toLiked = true)
        val liked = comments.map { updateDynamicCommentReaction(it, child.rpid, child.action, child.like) }
        val sibling = applyDynamicCommentLike(liked[1], toLiked = false)
        val unliked = liked.map { updateDynamicCommentReaction(it, sibling.rpid, sibling.action, sibling.like) }

        assertEquals(1, liked[0].replies?.first()?.action)
        assertEquals(2, liked[0].replies?.first()?.like)
        assertEquals(0, unliked[1].action)
        assertEquals(7, unliked[1].like)
        assertEquals(3, unliked[0].like)
    }

    @Test
    fun `all dynamic subject types normalize omitted fields and immediately show reply first`() {
        for (type in listOf(1, 11, 17)) {
            val subject = DynamicCommentTarget(oid = 100L + type, type = type)
            val root = ReplyItem(rpid = 10L, rcount = 4, count = 4,
                replies = listOf(ReplyItem(rpid = 20L, root = 10L)))
            val store = SentCommentReplyStore()
            val parent = ReplyItem(rpid = 20L, root = 10L, dialog = 30L)
            val reply = resolveDynamicSentComment(
                ReplyItem(rpid = 40L), subject, resolveDynamicCommentReplyTarget(parent), parent,
            )
            assertEquals(subject.oid, reply.oid)
            assertEquals(type, reply.replyType)
            assertEquals(10L, reply.root)
            assertEquals(20L, reply.parent)
            assertEquals(30L, reply.dialog)
            assertTrue(store.record(reply, listOf(root)))
            val visible = store.merge(listOf(root)).single()
            assertEquals(listOf(40L, 20L), visible.replies.orEmpty().map { it.rpid })
            assertEquals(5, visible.rcount)
            assertTrue(canOpenDynamicSubReplies(visible))
            assertFalse(store.record(reply, listOf(visible)))
            assertEquals(5, store.merge(listOf(visible)).single().rcount)
        }
    }

    @Test
    fun `root reply and nested reply use captured composer ownership`() {
        val subject = DynamicCommentTarget(oid = 200L, type = 11)
        val rootReply = resolveDynamicSentComment(
            ReplyItem(rpid = 30L, root = 999L, parent = 999L), subject,
            DynamicCommentComposerTarget(10L, 10L, "root"), ReplyItem(rpid = 10L),
        )
        assertEquals(10L, rootReply.root)
        assertEquals(10L, rootReply.parent)
        val topLevel = resolveDynamicSentComment(ReplyItem(rpid = 50L), subject, null, null)
        assertEquals(0L, topLevel.root)
        assertEquals(0L, topLevel.parent)
    }

    @Test
    fun `time thread sends remain at tail through pagination and take server echo position`() {
        val root = ReplyItem(rpid = 10L)
        val first = ReplyItem(rpid = 20L, root = 10L)
        val second = ReplyItem(rpid = 30L, root = 10L)
        val sent = ReplyItem(rpid = 40L, root = 10L, parent = 20L)
        val laterSent = ReplyItem(rpid = 50L, root = 10L, parent = 20L)
        val store = SentCommentReplyStore()
        store.record(sent, listOf(root))
        store.record(laterSent, store.merge(listOf(root)))
        val initial = SubReplyUiState(visible = true, rootReply = root,
            items = listOf(first).toImmutableList()).withSentSubReply(sent).withSentSubReply(laterSent)
        assertEquals(listOf(20L, 40L, 50L), initial.items.map { it.rpid })
        val appended = resolveDynamicSubReplyStateAfterSuccess(
            currentState = initial, remoteItems = listOf(first, second), page = 2, isEnd = false,
            sentReplies = store.repliesFor(10L),
        )
        assertEquals(listOf(20L, 30L, 40L, 50L), appended.items.map { it.rpid })
        val serverReply = sent.copy(like = 7)
        val echoed = resolveDynamicSubReplyStateAfterSuccess(
            currentState = appended, remoteItems = listOf(first, second, serverReply), page = 3,
            isEnd = true, sentReplies = store.repliesFor(10L),
        )
        assertEquals(listOf(20L, 30L, 40L, 50L), echoed.items.map { it.rpid })
        assertEquals(7, echoed.items.first { it.rpid == 40L }.like)
        assertEquals(echoed.items, echoed.baseItems)
        // Reopening starts with pure remote data, not the newest-first main preview.
        val reopened = resolveDynamicSubReplyStateAfterSuccess(
            currentState = SubReplyUiState(rootReply = root),
            remoteItems = listOf(first, second), page = 1, isEnd = false,
            sentReplies = store.repliesFor(10L),
        )
        assertEquals(listOf(20L, 30L, 40L, 50L), reopened.items.map { it.rpid })
        assertEquals(listOf(50L, 40L), store.merge(listOf(root)).single().replies.orEmpty().map { it.rpid })
    }

    @Test
    fun `hot refresh keeps echoed send in remote rank and deduplicates remote objects`() {
        val sent = ReplyItem(rpid = 40L, root = 10L)
        val remote = sent.copy(like = 100)
        val refreshed = resolveDynamicSubReplyStateAfterSuccess(
            currentState = SubReplyUiState(
                items = listOf(ReplyItem(rpid = 20L), sent).toImmutableList(),
                isRefreshing = true,
            ),
            remoteItems = listOf(remote, ReplyItem(rpid = 30L), remote),
            page = 1, isEnd = false, sentReplies = listOf(sent, ReplyItem(rpid = 50L)),
        )
        assertEquals(listOf(40L, 30L, 50L), refreshed.items.map { it.rpid })
        assertEquals(remote, refreshed.items.first())
        assertFalse(refreshed.isRefreshing)
    }

    @Test
    fun `conversation send updates its tail and base thread without preview ordering`() {
        val parent = ReplyItem(rpid = 20L, root = 10L, dialog = 20L)
        val sent = resolveDynamicSentComment(
            ReplyItem(rpid = 40L), DynamicCommentTarget(100L, 11),
            resolveDynamicCommentReplyTarget(parent), parent,
        )
        val state = SubReplyUiState(
            visible = true, rootReply = ReplyItem(rpid = 10L), conversationAnchor = parent,
            items = listOf(parent).toImmutableList(),
            baseItems = listOf(ReplyItem(rpid = 15L), parent, ReplyItem(rpid = 30L)).toImmutableList(),
        ).withSentSubReply(sent)
        assertEquals(listOf(20L, 40L), state.items.map { it.rpid })
        assertEquals(listOf(15L, 20L, 30L, 40L), state.baseItems.map { it.rpid })
    }

    @Test
    fun `delete removes local preview and overlay and stale responses cannot resurrect it`() {
        val root = ReplyItem(rpid = 10L, rcount = 5, count = 5)
        val sent = ReplyItem(rpid = 40L, root = 10L)
        val store = SentCommentReplyStore()
        store.record(sent, listOf(root))
        val visible = store.merge(listOf(root))
        store.removeReply(10L, 40L)
        val deleted = store.merge(removeDynamicCommentFromList(visible, 40L, 10L)).single()
        assertTrue(deleted.replies.orEmpty().isEmpty())
        assertEquals(5, deleted.rcount)
        assertTrue(store.repliesFor(10L).isEmpty())
        assertFalse(store.record(sent, listOf(deleted)))
        val stale = root.copy(rcount = 6, count = 6, replies = listOf(sent))
        assertEquals(5, store.merge(listOf(stale)).single().rcount)
        store.removeRoot(10L)
        assertTrue(store.merge(listOf(stale)).isEmpty())
        store.clear()
        assertTrue(store.record(sent, listOf(root)))
    }

    @Test
    fun `subject oid type dynamic and reopened session gate delayed send results`() {
        val target = DynamicCommentTarget(100L, 11)
        assertTrue(shouldApplyDynamicSentComment("a", target, 1L, "a", target, 1L))
        assertFalse(shouldApplyDynamicSentComment("a", target, 1L, "b", target, 1L))
        assertFalse(shouldApplyDynamicSentComment("a", target, 1L, "a", target.copy(oid = 200L), 1L))
        assertFalse(shouldApplyDynamicSentComment("a", target, 1L, "a", target.copy(type = 17), 1L))
        assertFalse(shouldApplyDynamicSentComment("a", target, 1L, "a", null, 1L))
        assertFalse(shouldApplyDynamicSentComment("a", target, 1L, "a", target, 2L))
    }
}
