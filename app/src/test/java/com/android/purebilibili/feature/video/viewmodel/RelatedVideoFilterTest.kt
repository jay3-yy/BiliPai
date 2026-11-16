package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.feature.plugin.BiliPaiFeedFilterConfig
import com.android.purebilibili.feature.plugin.shouldShowFeedItem
import com.android.purebilibili.plugin.sdk.FeedKind
import org.junit.Assert.*
import org.junit.Test

class RelatedVideoFilterTest {
    @Test
    fun relatedVideosUseExistingTitleAndBlockedUserRulesWithoutLosingSourceMetadata() {
        val keyword = RelatedVideo(aid = 1, bvid = "BV1", title = "剧情 SPOILER")
        val blockedUp = RelatedVideo(aid = 2, bvid = "BV2", owner = Owner(mid = 42))
        val retained = RelatedVideo(aid = 3, bvid = "BV3", title = "正常内容", isVertical = null)
        val config = BiliPaiFeedFilterConfig(recommendBlockedMids = mapOf(42L to "屏蔽用户"))
        val filtered = filterRelatedVideoCandidates(listOf(keyword, blockedUp, retained)) { videos ->
            videos.filter { shouldShowFeedItem(config, it, FeedKind.GENERIC, Regex("spoiler", RegexOption.IGNORE_CASE), null) }
        }
        assertEquals(listOf(retained), filtered)
        assertSame(retained, filtered.single())
        assertNull(filtered.single().isVertical)
    }

    @Test
    fun disabledFiltersRetainAllCandidatesAndTransformsCanReorderThem() {
        val first = RelatedVideo(aid = 1, bvid = "BV1", isVertical = true)
        val second = RelatedVideo(aid = 2, bvid = "BV2", isVertical = false)
        val items = listOf(first, second)
        assertEquals(items, filterRelatedVideoCandidates(items) { it })
        assertEquals(listOf(second, first), filterRelatedVideoCandidates(items) { it.reversed() })
    }
}
