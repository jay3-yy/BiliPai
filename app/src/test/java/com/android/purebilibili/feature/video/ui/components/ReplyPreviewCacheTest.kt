package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.data.model.response.ReplyItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplyPreviewCacheTest {
    @Test
    fun supplementsAreIsolatedByAccountAndSubjectAndInvalidatedByServerChanges() {
        val cache = ReplyPreviewCache { 0L }
        val account = AccountSessionIdentity(1L, 1L)
        val first = ReplyItem(rpid = 11L)
        val root = ReplyItem(oid = 5L, rpid = 10L, count = 3, replies = listOf(first))
        val supplemented = listOf(first, ReplyItem(rpid = 12L), ReplyItem(rpid = 13L))
        cache.put(account, root, supplemented)
        assertEquals(supplemented, cache.get(account, root))
        assertNull(cache.get(AccountSessionIdentity(2L, 1L), root))
        assertNull(cache.get(account.copy(generation = 2L), root))
        assertNull(cache.get(account, root.copy(oid = 6L)))
        assertNull(cache.get(account, root.copy(count = 4)))
        assertNull(cache.get(account, root.copy(replies = listOf(first.copy(like = 9)))))
    }

    @Test
    fun onlySupplementedResultsAreCachedAndOldRowsExpireOrAreEvicted() {
        var now = 0L
        val cache = ReplyPreviewCache { now }
        val account = AccountSessionIdentity(1L)
        val root = ReplyItem(oid = 5L, rpid = 10L, count = 3)
        cache.put(account, root, emptyList())
        assertNull(cache.get(account, root))
        val replies = listOf(ReplyItem(rpid = 11L))
        cache.put(account, root, replies)
        now = 60_000L
        assertNull(cache.get(account, root))
        cache.put(account, root, replies)
        repeat(128) { index -> cache.put(account, root.copy(rpid = 100L + index), replies) }
        assertNull(cache.get(account, root))
        assertEquals(replies, cache.get(account, root.copy(rpid = 227L)))
    }
}
