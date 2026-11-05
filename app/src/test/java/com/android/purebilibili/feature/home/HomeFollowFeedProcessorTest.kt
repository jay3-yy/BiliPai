package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.ArchiveMajor
import com.android.purebilibili.data.model.response.ArchiveStat
import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicContentModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicMajor
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFollowFeedProcessorTest {
    @Test
    fun mappingKeepsVideoIdentityAndStatsWhileExcludingBlockedAndNonVideoItems() {
        val archive = ArchiveMajor(aid = "42", bvid = "BV42", duration_text = "01:02:03", stat = ArchiveStat(play = "1.2万", danmaku = "23"))
        val item = DynamicItem(
            id_str = " dynamic-42 ",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 7, name = "author"),
                module_dynamic = DynamicContentModule(major = DynamicMajor(archive = archive)),
            ),
        )
        val blockedItem = item.copy(modules = item.modules.copy(module_author = DynamicAuthorModule(mid = 8)))
        val videos = mapHomeFollowDynamicItemsToVideos(listOf(item, blockedItem, DynamicItem()), setOf(8L))
        assertEquals(1, videos.size)
        with(videos.single()) {
            assertEquals("dynamic-42", dynamicId)
            assertEquals(42L, aid)
            assertEquals("BV42", bvid)
            assertEquals(7L, owner.mid)
            assertEquals(3723, duration)
            assertEquals(12000, stat.view)
            assertEquals(23, stat.danmaku)
        }
    }

    @Test
    fun incrementalRefreshPrependsAndDeduplicatesByDynamicIdentity() {
        val old = video(1)
        val new = video(2)
        val merge = merge(listOf(old), listOf(new, old, new))
        assertTrue(merge.canPrepend)
        assertEquals(listOf(new, old), merge.videos)
        assertEquals(1, merge.insertedCount)
    }

    @Test
    fun disconnectedTimelineAndForcedReplacementDiscardOldContent() {
        val old = video(1)
        val new = video(2)
        val disconnected = merge(listOf(old), listOf(new))
        assertFalse(disconnected.canPrepend)
        assertEquals(listOf(new), disconnected.videos)
        val replacement = merge(listOf(old, new), listOf(old), forceReplace = true)
        assertFalse(replacement.canPrepend)
        assertEquals(listOf(old), replacement.videos)
    }

    @Test
    fun paginationAppendsWithoutDroppingRetainedItems() {
        val old = video(1)
        val new = video(2)
        assertEquals(listOf(old, new), merge(listOf(old), listOf(old, new), isLoadMore = true).videos)
    }

    @Test
    fun aDismissedCardDoesNotReturnWhenTheRequestFinishes() {
        val dismissed = video(1)
        val retained = video(2)
        val result = mergeHomeFollowFeed(
            currentVideos = listOf(retained), incomingVideos = listOf(dismissed, retained),
            isLoadMore = false, incrementalRefreshEnabled = false,
            usedUpdateBaseline = false, forceReplace = true,
            requestVideos = listOf(dismissed, retained),
        )
        assertEquals(listOf(retained), result.videos)
    }

    @Test
    fun differentDynamicsSharingAVideoRemainSeparateCards() {
        val first = video(1)
        val second = first.copy(dynamicId = "another-dynamic")
        assertEquals(listOf(first, second), merge(listOf(first), listOf(second), isLoadMore = true).videos)
    }

    private fun video(id: Long) = VideoItem(id = id, bvid = "BV$id", dynamicId = "dynamic-$id")

    private fun merge(
        current: List<VideoItem>, incoming: List<VideoItem>,
        forceReplace: Boolean = false, isLoadMore: Boolean = false,
    ) = mergeHomeFollowFeed(current, incoming, isLoadMore, true, false, forceReplace)
}
