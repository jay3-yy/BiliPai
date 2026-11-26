package com.android.purebilibili.feature.video.postcard

import com.android.purebilibili.feature.video.note.VideoNoteBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoPostcardPolicyTest {

    @Test
    fun `resolvePostcardInitialQuote prioritizes subtitle then falls back to nearby danmaku`() {
        assertEquals(
            "愿你历尽千帆\n归来仍是少年",
            resolvePostcardInitialQuote(
                primarySubtitle = "  愿你历尽千帆  ",
                secondarySubtitle = "归来仍是少年",
                nearbyDanmaku = listOf("泪目了"),
            ),
        )
        assertEquals(
            "这一幕封神了",
            resolvePostcardInitialQuote(
                primarySubtitle = null,
                secondarySubtitle = null,
                nearbyDanmaku = listOf("这一幕封神了", "前排"),
            ),
        )
        assertEquals(
            "",
            resolvePostcardInitialQuote(null, null, emptyList()),
        )
    }

    @Test
    fun `selectNearbyPostcardDanmaku filters by window sorts by proximity and deduplicates`() {
        val candidates = listOf(
            20_000L to "太远了",
            10_800L to "神回预定",
            10_100L to "泪目了",
            10_200L to "泪目了",
            10_050L to "1",
            9_600L to "这段配乐绝了",
        )
        val picked = selectNearbyPostcardDanmaku(
            candidates = candidates,
            positionMs = 10_000L,
            windowMs = 5_000L,
            limit = 4,
        )
        assertEquals(listOf("泪目了", "这段配乐绝了", "神回预定"), picked)
    }

    @Test
    fun `formatPostcardTimestamp handles sub-hour and multi-hour positions`() {
        assertEquals("00:00", formatPostcardTimestamp(-500L))
        assertEquals("04:21", formatPostcardTimestamp(261_000L))
        assertEquals("1:02:05", formatPostcardTimestamp(3_725_000L))
    }

    @Test
    fun `buildPostcardNoteBlocks emits timestamp quote and optional mood`() {
        val blocks = buildPostcardNoteBlocks(
            quote = "生活不是等待暴风雨过去",
            moodNote = "晚上看到这里突然被治愈",
            positionMs = 125_000L,
            cid = 999L,
            pageIndex = 1,
            cidCount = 3,
        )
        assertEquals(3, blocks.size)
        val ts = blocks[0] as VideoNoteBlock.Timestamp
        assertEquals(125L, ts.seconds)
        assertEquals(999L, ts.cid)
        assertEquals(1, ts.index)
        assertEquals(3, ts.cidCount)
        assertEquals("02:05", ts.label)
        assertEquals(VideoNoteBlock.Quote("生活不是等待暴风雨过去"), blocks[1])
        assertEquals(VideoNoteBlock.Text("晚上看到这里突然被治愈"), blocks[2])

        val withoutMood = buildPostcardNoteBlocks(
            quote = "",
            moodNote = "   ",
            positionMs = 0L,
            cid = 1L,
            pageIndex = 0,
            cidCount = 1,
        )
        assertTrue(withoutMood.size == 1 && withoutMood.first() is VideoNoteBlock.Timestamp)
    }
}
