package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.HistoryCursor
import com.android.purebilibili.data.model.response.HistoryData
import com.android.purebilibili.data.model.response.HistoryPage
import com.android.purebilibili.data.repository.HistoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodayWatchHistorySourceTest {
    private fun video(bvid: String) = HistoryData(title = bvid, history = HistoryPage(bvid = bvid))

    @Test
    fun completeHistoryReusesCacheUntilExpiryAndClearDropsThePreviousAccount() = runTest {
        var now = 1_000L
        var calls = 0
        val source = TodayWatchHistorySource(
            loadPage = { _, _, _ -> calls++; Result.success(HistoryResult(listOf(video("BV$calls")), null)) },
            nowMillis = { now },
        )
        suspend fun load() = source.load(false, 60) { }
        assertEquals(listOf("BV1"), load().videos.map { it.bvid })
        assertEquals(listOf("BV1"), load().videos.map { it.bvid })
        assertEquals(1, calls)
        now += TODAY_WATCH_HISTORY_CACHE_TTL_MS
        assertEquals(listOf("BV2"), load().videos.map { it.bvid })
        source.clear()
        assertEquals(listOf("BV3"), load().videos.map { it.bvid })
    }

    @Test
    fun pagingUsesServerCursorAndDeduplicatesBeforeCaching() = runTest {
        val cursors = mutableListOf<Triple<Long, Long, String>>()
        val source = TodayWatchHistorySource(loadPage = { max, viewAt, business ->
            cursors += Triple(max, viewAt, business)
            Result.success(if (max == 0L) {
                HistoryResult(listOf(video("BV1")), HistoryCursor(max = 51, view_at = 70, business = "archive"))
            } else {
                HistoryResult(listOf(video("BV1"), video("BV2")), null)
            })
        })
        val result = source.load(false, 20) { }
        assertEquals(listOf(Triple(0L, 0L, ""), Triple(51L, 70L, "archive")), cursors)
        assertEquals(listOf("BV1", "BV2"), result.videos.map { it.bvid })
        assertFalse(result.unavailable)
    }

    @Test
    fun failedOrCancelledHistoryDoesNotBecomeACompletedCache() = runTest {
        var calls = 0
        val source = TodayWatchHistorySource(loadPage = { _, _, _ ->
            calls++
            when (calls) {
                1 -> Result.failure(IllegalStateException("request failed"))
                2 -> Result.failure(CancellationException("account changed"))
                else -> Result.success(HistoryResult(listOf(video("BV_NEW")), null))
            }
        })
        assertTrue(source.load(false, 20) { }.unavailable)
        assertFailsWith<CancellationException> { source.load(false, 20) { } }
        assertEquals(listOf("BV_NEW"), source.load(false, 20) { }.videos.map { it.bvid })
        assertEquals(3, calls)
    }

    @Test
    fun accountValidationRejectsAnOldResponseBeforeItCanPopulateTheCache() = runTest {
        var current = true
        var calls = 0
        val source = TodayWatchHistorySource(loadPage = { _, _, _ ->
            calls++
            if (calls == 1) current = false
            Result.success(HistoryResult(listOf(video("BV$calls")), null))
        })
        assertFailsWith<CancellationException> {
            source.load(false, 20) { if (!current) throw CancellationException("account changed") }
        }
        current = true
        assertEquals(listOf("BV2"), source.load(false, 20) { }.videos.map { it.bvid })
    }
}
