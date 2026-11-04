package com.android.purebilibili.feature.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class VideoTagFilterTest {
    private data class Video(val bvid: String, val title: String)

    @Test
    fun matchesActualTagsNotTheTitleAndPreservesSurvivorOrder() = runTest {
        val first = Video("hidden", "普通日常")
        val second = Video("visible", "广告标题但标签无关")
        val third = Video("also-visible", "普通视频")
        val filter = VideoTagFilter(loadTags = { id ->
            when (id) {
                "hidden" -> listOf("生活", "Sponsored Content")
                else -> listOf("摄影")
            }
        })
        val blocked = mutableListOf<Pair<Video, String>>()
        val result = filter.filter(listOf(first, second, third), listOf("  sponsored  "), Video::bvid) { video, word ->
            blocked += video to word
        }
        assertEquals(listOf(second, third), result)
        assertEquals(listOf(first to "sponsored"), blocked)
    }

    @Test
    fun emptyRulesDoNotFetchAndChangingRulesUsesCachedTags() = runTest {
        var calls = 0
        val filter = VideoTagFilter(loadTags = { calls++; listOf("猫咪", "宠物") })
        val items = listOf(Video("cat", "今天"))
        assertSame(items, filter.filter(items, listOf(" "), Video::bvid))
        assertEquals(0, calls)
        assertEquals(emptyList(), filter.filter(items, listOf("猫"), Video::bvid))
        assertEquals(items, filter.filter(items, listOf("游戏"), Video::bvid))
        assertSame(items, filter.filter(items, emptyList(), Video::bvid))
        assertEquals(1, calls)
    }

    @Test
    fun failedLookupKeepsVideoAndDoesNotCacheFailure() = runTest {
        var calls = 0
        val failures = mutableListOf<String>()
        val filter = VideoTagFilter(
            loadTags = { if (++calls == 1) error("offline") else listOf("剧透") },
            onFailure = { id, _ -> failures += id },
        )
        val items = listOf(Video("movie", "无关标题"))
        assertEquals(items, filter.filter(items, listOf("剧透"), Video::bvid))
        assertEquals(emptyList(), filter.filter(items, listOf("剧透"), Video::bvid))
        assertEquals(listOf("movie"), failures)
        assertEquals(2, calls)
    }

    @Test
    fun cachesSuccessfulEmptyTagsButNeverRequestsMissingIdentity() = runTest {
        val calls = mutableListOf<String>()
        val filter = VideoTagFilter(loadTags = { calls += it; emptyList() })
        val items = listOf(Video("empty", "无标签"), Video("", "不是视频"))
        repeat(2) { assertEquals(items, filter.filter(items, listOf("游戏"), Video::bvid)) }
        assertEquals(listOf("empty"), calls)
    }

    @Test
    fun expiresTagsAndEvictsLeastRecentlyUsedVideo() = runTest {
        var clock = 0L
        val calls = mutableListOf<String>()
        val filter = VideoTagFilter(
            loadTags = { calls += it; listOf("旧标签") },
            nowMs = { clock },
            maxCacheEntries = 2,
            cacheTtlMs = 100,
        )
        suspend fun visit(id: String) {
            filter.filter(listOf(Video(id, id)), listOf("不匹配"), Video::bvid)
        }
        visit("a"); visit("b"); visit("a"); visit("c"); visit("b")
        assertEquals(listOf("a", "b", "c", "b"), calls)
        clock = 100
        visit("b")
        assertEquals(listOf("a", "b", "c", "b", "b"), calls)
    }

    @Test
    fun cancellationPropagatesWithoutCachingAnEmptySuccess() = runTest {
        var cancelled = true
        val filter = VideoTagFilter(loadTags = {
            if (cancelled) throw CancellationException("left screen") else listOf("剧透")
        })
        val items = listOf(Video("movie", "片段"))
        try {
            filter.filter(items, listOf("剧透"), Video::bvid)
            fail("Cancellation must reach the feed owner")
        } catch (_: CancellationException) {
            cancelled = false
        }
        assertEquals(emptyList(), filter.filter(items, listOf("剧透"), Video::bvid))
    }

    @Test
    fun boundsConcurrentRequestsAndKeepsOrderWhenTheyFinishOutOfOrder() = runTest {
        var active = 0
        var peak = 0
        val filter = VideoTagFilter(loadTags = { id ->
            active++
            peak = maxOf(peak, active)
            try {
                delay(20L - id.toInt())
                if (id.toInt() % 2 == 0) listOf("屏蔽") else emptyList()
            } finally {
                active--
            }
        })
        val items = (1..12).map { Video(it.toString(), "标题") }
        assertEquals(items.filter { it.bvid.toInt() % 2 != 0 }, filter.filter(items, listOf("屏蔽"), Video::bvid))
        assertTrue(peak in 2..4)
        assertEquals(0, active)
    }

    @Test
    fun batchDeadlineKeepsUnresolvedVideosButStillHidesCompletedMatches() = runTest {
        val filter = VideoTagFilter(
            loadTags = { id ->
                if (id == "slow") delay(10_000L) else delay(10L)
                listOf("剧透")
            },
            batchTimeoutMs = 50L,
        )
        val videos = listOf(Video("fast", "普通标题"), Video("slow", "另一条"))
        assertEquals(listOf(videos[1]), filter.filter(videos, listOf("剧透"), Video::bvid))
        assertEquals(50L, testScheduler.currentTime)
    }
}
