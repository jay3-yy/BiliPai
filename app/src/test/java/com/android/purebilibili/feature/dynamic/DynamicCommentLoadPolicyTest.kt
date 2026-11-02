package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.data.model.response.ReplyCursor
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyPage
import com.android.purebilibili.data.model.response.ReplyTop
import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.feature.dynamic.components.DynamicCardPrimaryAction
import com.android.purebilibili.feature.comment.SentCommentReplyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DynamicCommentLoadPolicyTest {

    @Test
    fun `dynamic comment payload merges top hot and main replies with stable count`() {
        val data = ReplyData(
            cursor = ReplyCursor(allCount = 0, isEnd = false, next = 2),
            page = ReplyPage(count = 0, acount = 0),
            top = ReplyTop(upper = ReplyItem(rpid = 100L)),
            hots = listOf(ReplyItem(rpid = 101L)),
            replies = listOf(ReplyItem(rpid = 102L), ReplyItem(rpid = 101L))
        )

        val resolved = resolveDynamicCommentPayload(
            data = data,
            fallbackCount = 29,
            includeHotReplies = true
        )

        assertEquals(listOf(100L, 101L, 102L), resolved.replies.map { it.rpid })
        assertEquals(29, resolved.totalCount)
    }

    @Test
    fun `dynamic newest comment payload skips hot replies to preserve time order`() {
        val data = ReplyData(
            top = ReplyTop(upper = ReplyItem(rpid = 100L)),
            hots = listOf(ReplyItem(rpid = 101L)),
            replies = listOf(ReplyItem(rpid = 102L), ReplyItem(rpid = 103L))
        )

        val resolved = resolveDynamicCommentPayload(
            data = data,
            fallbackCount = 0,
            includeHotReplies = false
        )

        assertEquals(listOf(100L, 102L, 103L), resolved.replies.map { it.rpid })
        assertEquals(3, resolved.totalCount)
    }

    @Test
    fun `dynamic comment selection prefers candidate closer to expected count`() {
        val attempts = listOf(
            DynamicCommentLoadAttempt(
                target = DynamicCommentTarget(oid = 326122895L, type = 11),
                replies = listOf(ReplyItem(rpid = 1L)),
                totalCount = 9,
                candidateIndex = 1
            ),
            DynamicCommentLoadAttempt(
                target = DynamicCommentTarget(oid = 967717348014293017L, type = 17),
                replies = listOf(ReplyItem(rpid = 2L)),
                totalCount = 3,
                candidateIndex = 0
            )
        )

        val selected = selectPreferredDynamicCommentAttempt(
            attempts = attempts,
            expectedCount = 9
        )

        assertEquals(DynamicCommentTarget(oid = 326122895L, type = 11), selected?.target)
    }

    @Test
    fun `detail comments load more only when near end and not already loading`() {
        assertEquals(
            true,
            shouldLoadMoreDynamicDetailComments(
                lastVisibleIndex = 18,
                itemCount = 20,
                hasMore = true,
                isLoading = false,
                isLoadingMore = false,
            )
        )
        assertEquals(
            false,
            shouldLoadMoreDynamicDetailComments(
                lastVisibleIndex = 18,
                itemCount = 20,
                hasMore = true,
                isLoading = false,
                isLoadingMore = true,
            )
        )
        assertEquals(
            false,
            shouldLoadMoreDynamicDetailComments(
                lastVisibleIndex = 10,
                itemCount = 20,
                hasMore = true,
                isLoading = false,
                isLoadingMore = false,
            )
        )
        assertEquals(
            false,
            shouldLoadMoreDynamicDetailComments(
                lastVisibleIndex = 18,
                itemCount = 20,
                hasMore = false,
                isLoading = false,
                isLoadingMore = false,
            )
        )
    }

    @Test
    fun `unknown or underestimated count cannot block an available next page`() {
        for (displayCount in listOf(0, 1, 20)) {
            val state = DynamicCommentUiState(
                comments = List(20) { ReplyItem(rpid = it.toLong() + 1) },
                totalCount = displayCount,
                hasMore = true,
                nextPage = 2,
            )
            assertEquals(true, shouldLoadMoreDynamicDetailComments(
                lastVisibleIndex = 19, itemCount = 21, hasMore = state.hasMore,
                isLoading = state.isLoading, isLoadingMore = state.isLoadingMore,
            ))
        }
        val ended = DynamicCommentUiState(totalCount = 100, hasMore = false)
        assertEquals(false, shouldLoadMoreDynamicDetailComments(
            lastVisibleIndex = 19, itemCount = 21, hasMore = ended.hasMore,
            isLoading = false, isLoadingMore = false,
        ))
    }

    @Test
    fun `detail interaction model reuses feed primary action and comment targets`() {
        val item = DynamicItem(
            id_str = "966281785469042740",
            type = "DYNAMIC_TYPE_AV",
            basic = com.android.purebilibili.data.model.response.DynamicBasic(
                comment_id_str = "1129813966",
                comment_type = 1
            ),
            modules = DynamicModules(
                module_dynamic = DynamicContentModule(
                    major = DynamicMajor(
                        archive = ArchiveMajor(bvid = "BV1xx411c7mD")
                    )
                )
            )
        )

        val model = resolveDynamicDetailInteractionModel(item)

        val primaryAction = assertIs<DynamicCardPrimaryAction.OpenVideo>(model.primaryAction)
        assertEquals("BV1xx411c7mD", primaryAction.bvid)
        assertEquals(
            listOf(DynamicCommentTarget(oid = 1129813966L, type = 1)),
            model.commentTargets
        )
    }

    @Test
    fun `comment skeleton slot count fills the viewport within sane bounds`() {
        assertEquals(DYNAMIC_COMMENT_SKELETON_MIN_SLOTS, resolveDynamicCommentSkeletonSlotCount(0))
        assertEquals(DYNAMIC_COMMENT_SKELETON_MIN_SLOTS, resolveDynamicCommentSkeletonSlotCount(200))
        assertEquals(5, resolveDynamicCommentSkeletonSlotCount(600))
        assertEquals(DYNAMIC_COMMENT_SKELETON_MAX_SLOTS, resolveDynamicCommentSkeletonSlotCount(4_000))
    }

    @Test
    fun `dynamic comment cache serves the last page and evicts least recently used`() {
        val cache = DynamicCommentCache(maxEntries = 2)
        cache.put("a", 3, commentSnapshot(rpid = 1L))
        cache.put("b", 3, commentSnapshot(rpid = 2L))

        // 读取 a 使其成为最近使用，随后写入 c 时最久未用的 b 应被淘汰。
        assertEquals(listOf(1L), cache.get("a", 3)?.replies?.map { it.rpid })
        cache.put("c", 3, commentSnapshot(rpid = 3L))

        assertEquals(2, cache.size())
        assertNull(cache.get("b", 3))
        assertEquals(listOf(1L), cache.get("a", 3)?.replies?.map { it.rpid })
        assertEquals(listOf(3L), cache.get("c", 3)?.replies?.map { it.rpid })
    }

    @Test
    fun `dynamic comment cache separates sort modes and ignores empty payloads`() {
        val cache = DynamicCommentCache()
        cache.put("a", 3, commentSnapshot(rpid = 1L))
        cache.put("a", 2, commentSnapshot(rpid = 2L, oid = 2L))
        cache.put("b", 3, commentSnapshot(rpid = 3L).copy(replies = emptyList()))

        assertEquals(listOf(1L), cache.get("a", 3)?.replies?.map { it.rpid })
        assertEquals(listOf(2L), cache.get("a", 2)?.replies?.map { it.rpid })
        // 空列表与空 dynamicId 都不进缓存，避免「重进秒出一个空骨架」。
        assertNull(cache.get("b", 3))
        assertNull(cache.get("", 3))
    }

    @Test
    fun `forwarded video replies use selected video subject rather than first forward candidate`() {
        val forward = DynamicItem(
            id_str = "1000", type = "DYNAMIC_TYPE_FORWARD",
            orig = DynamicItem(
                id_str = "2000", type = "DYNAMIC_TYPE_AV",
                basic = com.android.purebilibili.data.model.response.DynamicBasic(
                    comment_id_str = "3000", comment_type = 1,
                ),
            ),
        )
        val candidates = resolveDynamicCommentTargets(forward)
        assertEquals(DynamicCommentTarget(1000L, 17), candidates.first())
        val videoTarget = candidates.first { it.type == 1 }
        val selected = requireNotNull(selectPreferredDynamicCommentAttempt(
            attempts = candidates.mapIndexed { index, target ->
                DynamicCommentLoadAttempt(
                    target = target,
                    replies = if (target == videoTarget) listOf(ReplyItem(rpid = 10L)) else emptyList(),
                    totalCount = if (target == videoTarget) 1 else 0,
                    candidateIndex = index,
                )
            },
        ))
        val sent = resolveDynamicSentComment(
            ReplyItem(rpid = 20L), selected.target, DynamicCommentComposerTarget(10L, 10L, "root"), null,
        )
        assertEquals(3000L, sent.oid)
        assertEquals(1, sent.replyType)
        assertEquals(10L, sent.root)
        assertEquals(10L, sent.parent)
    }

    @Test
    fun `cached main preview and pure remote snapshot remain separate across reopening`() {
        val root = ReplyItem(rpid = 10L)
        val localRoot = ReplyItem(rpid = 50L)
        val store = SentCommentReplyStore()
        store.record(localRoot, listOf(root))
        store.record(ReplyItem(rpid = 40L, root = 10L), listOf(root))
        val cache = DynamicCommentCache()
        cache.put("a", 3, commentSnapshot(10L).copy(
            replies = store.merge(listOf(root)), remoteReplies = listOf(root),
        ))
        val cached = requireNotNull(cache.get("a", 3))
        assertEquals(listOf(50L, 10L), cached.replies.map { it.rpid })
        assertEquals(listOf(40L), cached.replies.last().replies.orEmpty().map { it.rpid })
        assertEquals(listOf(10L), cached.remoteReplies.map { it.rpid })
        val remoteNextPage = cached.remoteReplies + ReplyItem(rpid = 30L) + localRoot.copy(like = 7)
        val appended = store.merge(remoteNextPage)
        assertEquals(listOf(10L, 30L, 50L), appended.map { it.rpid })
        assertEquals(7, appended.last().like)
        // Account activation clears both the rendered cache and subject-local send state.
        cache.clear()
        store.clear()
        assertNull(cache.get("a", 3))
        assertEquals(listOf(root), store.merge(listOf(root)))
        assertEquals(emptyList(), store.repliesFor(10L))
    }

    @Test
    fun `route pin is displayed first once without mutating the server order or top flag`() {
        val first = ReplyItem(rpid = 10L)
        val selected = ReplyItem(rpid = 20L)
        val last = ReplyItem(rpid = 30L)
        val remote = listOf(first, selected, last)
        val state = DynamicCommentUiState(
            comments = remote,
            pinnedRootReplyId = selected.rpid,
            pinnedReply = selected,
        )

        val displayed = resolveDynamicDetailDisplayComments(state)

        assertEquals(listOf(20L, 10L, 30L), displayed.map { it.rpid })
        assertEquals(listOf(10L, 20L, 30L), remote.map { it.rpid })
        assertEquals(selected.replyControl, displayed.first().replyControl)
        assertEquals(remote, resolveDynamicDetailDisplayComments(state.copy(pinnedRootReplyId = 0L)))
    }

    private fun commentSnapshot(rpid: Long, oid: Long = 1L) = DynamicCommentCacheSnapshot(
        replies = listOf(ReplyItem(rpid = rpid)),
        totalCount = 1,
        nextPage = 2,
        isEnd = false,
        grpcNextOffset = null,
        target = DynamicCommentTarget(oid = oid, type = 17),
        cachedAtMs = 0L,
    )
}
