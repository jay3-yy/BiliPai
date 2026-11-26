package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryData
import com.android.purebilibili.data.model.response.HistoryPage
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HistoryMemoryPolicyTest {

    private val utc = ZoneOffset.UTC
    private val nowMs = LocalDate.of(2026, 11, 26).atTime(12, 0).toInstant(utc).toEpochMilli()

    private fun sampleVideo(
        bvid: String,
        date: LocalDate,
        progress: Int = -1,
        duration: Int = 600,
        business: String = "archive",
    ): HistoryData = HistoryData(
        title = "视频 $bvid",
        cover = "https://example.com/$bvid.jpg",
        author_name = "UP测试",
        duration = duration,
        progress = progress,
        view_at = date.atTime(20, 0).toInstant(utc).epochSecond,
        history = HistoryPage(bvid = bvid, cid = 101L, business = business),
    )

    @Test
    fun `pickHistoryMemoryFromData prioritizes on-this-day memory from previous years`() {
        val items = listOf(
            sampleVideo("BV_RECENT", LocalDate.of(2026, 11, 25)),
            sampleVideo("BV_OLD", LocalDate.of(2026, 9, 10)),
            sampleVideo("BV_ANNIV", LocalDate.of(2024, 11, 26), progress = -1),
        )
        val pick = assertNotNull(pickHistoryMemoryFromData(items, nowMs = nowMs, zone = utc))
        assertEquals(HistoryMemoryKind.ON_THIS_DAY, pick.kind)
        assertEquals("BV_ANNIV", pick.bvid)
        assertEquals("2 年前的今天", pick.badgeText)
    }

    @Test
    fun `pickHistoryMemoryFromData falls back to rewatch gem older than 14 days`() {
        val items = listOf(
            sampleVideo("BV_TOO_NEW", LocalDate.of(2026, 11, 20), progress = -1),
            sampleVideo("BV_UNFINISHED", LocalDate.of(2026, 10, 1), progress = 30, duration = 600),
            sampleVideo("BV_ARTICLE", LocalDate.of(2026, 10, 1), progress = -1, business = "article"),
            sampleVideo("BV_GEM", LocalDate.of(2026, 10, 5), progress = -1),
        )
        val pick = assertNotNull(pickHistoryMemoryFromData(items, nowMs = nowMs, zone = utc))
        assertEquals(HistoryMemoryKind.REWATCH_GEM, pick.kind)
        assertEquals("BV_GEM", pick.bvid)
        assertEquals("往日重温", pick.badgeText)
    }

    @Test
    fun `pickHistoryMemoryFromData returns null when no eligible old history exists`() {
        val items = listOf(
            sampleVideo("BV_TODAY", LocalDate.of(2026, 11, 26)),
            sampleVideo("BV_YESTERDAY", LocalDate.of(2026, 11, 25)),
        )
        assertNull(pickHistoryMemoryFromData(items, nowMs = nowMs, zone = utc))
    }
}
