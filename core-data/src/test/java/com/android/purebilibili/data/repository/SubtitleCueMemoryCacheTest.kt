package com.android.purebilibili.data.repository

import com.android.purebilibili.core.subtitle.SubtitleCue
import com.android.purebilibili.core.subtitle.SubtitleWordSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubtitleCueMemoryCacheTest {
    private fun cues(text: String = "字幕") = listOf(SubtitleCue(0, 1000, text))
    private fun SubtitleCueMemoryCache.store(key: String, cues: List<SubtitleCue>) =
        put(key, cues, generation())

    @Test
    fun entryLimitEvictsLeastRecentlyUsedWithoutClearingRecentTracks() {
        val cache = SubtitleCueMemoryCache(maxEntries = 2, maxBytes = 10_000)
        val playing = cues("当前字幕")
        cache.store("playing", playing)
        cache.store("old", cues())
        assertSame(playing, cache["playing"])
        cache.store("new", cues())
        assertNull(cache["old"])
        assertSame(playing, cache["playing"])
        assertEquals(2, cache.stats().entryCount)
        // 缓存清除不会修改播放会话仍持有的列表。
        cache.clear()
        assertEquals("当前字幕", playing.single().content)
    }

    @Test
    fun byteBudgetEvictsOldTracksEvenBelowEntryLimit() {
        val cache = SubtitleCueMemoryCache(maxEntries = 48, maxBytes = 2000)
        cache.store("a", cues("x".repeat(10)))
        cache.store("b", cues("x".repeat(10)))
        cache.store("c", cues("x".repeat(10)))
        cache["a"]
        cache.store("d", cues("x".repeat(10)))
        assertNull(cache["b"])
        assertEquals(3, cache.stats().entryCount)
        assertTrue(cache.stats().estimatedBytes <= 2000)
    }

    @Test
    fun oversizedTextAndWordTimingTracksAreUsableWithoutDisplacingSmallTracks() {
        val cache = SubtitleCueMemoryCache(maxBytes = 2000)
        val small = cues()
        cache.store("small", small)
        val largeText = cues("x".repeat(2000))
        val wordTimed = listOf(SubtitleCue(0, 1000, "AI 字幕", List(30) {
            SubtitleWordSpan("逐字时间戳", it * 10L, it * 10L + 10)
        }))
        for ((key, track) in listOf("large" to largeText, "words" to wordTimed)) {
            cache.store(key, track)
            assertNull(cache[key])
            assertSame(small, cache["small"])
        }
        assertEquals(1, cache.stats().entryCount)
    }

    @Test
    fun replacingTrackUpdatesAccountingAndClearingResetsIt() {
        val cache = SubtitleCueMemoryCache(maxBytes = 10_000)
        cache.store("same", cues())
        val initial = cache.stats()
        cache.store("same", cues("longer".repeat(100)))
        assertEquals(1, cache.stats().entryCount)
        assertEquals(1, cache.stats().totalCueCount)
        assertTrue(cache.stats().estimatedBytes > initial.estimatedBytes)
        cache.store("same", cues())
        assertEquals(initial, cache.stats())
        cache.clear()
        assertEquals(SubtitleCueMemoryCacheStats(0, 0, 0), cache.stats())
    }

    @Test
    fun pressureClearRejectsOldRequestRefillButAllowsNewRequests() {
        val cache = SubtitleCueMemoryCache()
        val startedBeforeClear = cache.generation()
        cache.clear()
        cache.put("old-request", cues(), startedBeforeClear)
        assertNull(cache["old-request"])
        cache.store("new-request", cues())
        assertEquals(1, cache.stats().entryCount)
    }
}
