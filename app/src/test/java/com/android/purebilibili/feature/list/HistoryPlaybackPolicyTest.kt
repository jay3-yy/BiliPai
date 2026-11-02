package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryItem
import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryPlaybackPolicyTest {

    @Test
    fun `resolveHistoryPlaybackCid prefers cid from history item`() {
        val historyItem = HistoryItem(
            videoItem = VideoItem(bvid = "BV1"),
            business = HistoryBusiness.ARCHIVE,
            cid = 9527L
        )

        val resolved = resolveHistoryPlaybackCid(
            clickedCid = 0L,
            historyItem = historyItem
        )

        assertEquals(9527L, resolved)
    }

    @Test
    fun `resolveHistoryPlaybackCid falls back to clicked cid when history cid missing`() {
        val historyItem = HistoryItem(
            videoItem = VideoItem(bvid = "BV1"),
            business = HistoryBusiness.ARCHIVE,
            cid = 0L
        )

        val resolved = resolveHistoryPlaybackCid(
            clickedCid = 114L,
            historyItem = historyItem
        )

        assertEquals(114L, resolved)
    }

    @Test
    fun `resolveHistoryResumePosition converts positive server progress to milliseconds`() {
        val historyItem = HistoryItem(
            videoItem = VideoItem(bvid = "BV1"),
            business = HistoryBusiness.ARCHIVE,
            progress = 123
        )

        val resolved = resolveHistoryResumePositionMs(historyItem)

        assertEquals(123_000L, resolved)
    }

    @Test
    fun `resolveHistoryResumePosition ignores missing or non-positive progress`() {
        assertEquals(0L, resolveHistoryResumePositionMs(null))
        assertEquals(
            0L,
            resolveHistoryResumePositionMs(
                HistoryItem(
                    videoItem = VideoItem(bvid = "BV1"),
                    business = HistoryBusiness.ARCHIVE,
                    progress = 0
                )
            )
        )
    }

    @Test
    fun `history displays cloud progress even when local progress is ahead or complete`() {
        listOf(180_000L, 590_000L).forEach { localMs ->
            assertEquals(120, resolveHistoryDisplayProgress(120, 600, localMs))
            assertEquals(0, resolveHistoryDisplayProgress(0, 600, localMs))
            assertEquals(-1, resolveHistoryDisplayProgress(-1, 600, localMs))
        }
    }
}
