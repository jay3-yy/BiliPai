package com.android.purebilibili.feature.list

import com.android.purebilibili.feature.personal.CompletionKeepsakeKind
import com.android.purebilibili.feature.personal.CompletionKeepsakeRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalRecapCardPolicyTest {

    @Test
    fun `buildPersonalRecapCardPayload summarizes metrics top ups and keepsake quote`() {
        val videoStats = PersonalVideoRecapStats(
            videoCount = 20,
            totalDurationSec = 7_500L,
            finishedCount = 15,
            topUps = listOf(
                PersonalUpRecap(1L, "UP甲", "", 8, 3000L),
                PersonalUpRecap(2L, "UP乙", "", 5, 2000L),
                PersonalUpRecap(3L, "UP丙", "", 4, 1500L),
                PersonalUpRecap(4L, "UP丁", "", 3, 1000L),
            ),
        )
        val keepsakes = listOf(
            CompletionKeepsakeRecord(
                id = "bangumi:100",
                kind = CompletionKeepsakeKind.BANGUMI,
                title = "葬送的芙莉莲",
                episodeCount = 28,
                coverUrl = "",
                reflection = "温柔又有力量的旅途",
                completedAtMs = 1_000L,
            )
        )
        val payload = buildPersonalRecapCardPayload(
            window = PersonalRecapWindow.LAST_SEVEN_DAYS,
            videoStats = videoStats,
            rssStats = PersonalRssRecapStats(readCount = 6),
            keepsakes = keepsakes,
            nowMs = 123_456L,
        )

        assertEquals("近七天", payload.windowLabel)
        assertEquals(20, payload.videoCount)
        assertEquals("约 2 小时", payload.totalDurationText)
        assertEquals(75, payload.completionPercent)
        assertEquals(6, payload.rssReadCount)
        assertEquals(listOf("UP甲", "UP乙", "UP丙"), payload.topUps)
        assertTrue(payload.keepsakeQuote.orEmpty().contains("温柔又有力量的旅途"))
    }

    @Test
    fun `buildPersonalRecapCardPayload handles empty stats gracefully`() {
        val payload = buildPersonalRecapCardPayload(
            window = PersonalRecapWindow.TODAY,
            videoStats = null,
            rssStats = PersonalRssRecapStats(),
            keepsakes = emptyList(),
        )
        assertEquals(0, payload.videoCount)
        assertEquals("0 分钟", payload.totalDurationText)
        assertNull(payload.completionPercent)
        assertNull(payload.keepsakeQuote)
    }
}
