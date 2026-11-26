package com.android.purebilibili.feature.personal

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionKeepsakeCardPolicyTest {

    @Test
    fun `buildCompletionKeepsakeCardPayload preserves title reflection and formatted date`() {
        val record = CompletionKeepsakeRecord(
            id = "bangumi:100",
            kind = CompletionKeepsakeKind.BANGUMI,
            title = "  葬送的芙莉莲  ",
            episodeCount = 28,
            coverUrl = "https://example.com/cover.jpg",
            reflection = "  安静又绵长的告别。  ",
            completedAtMs = 1_767_225_600_000L, // 2026-01-01T00:00:00Z
            seasonId = 100L,
        )

        val payload = buildCompletionKeepsakeCardPayload(record, ZoneOffset.UTC)

        assertEquals("追番完结", payload.badgeLabel)
        assertEquals("葬送的芙莉莲", payload.title)
        assertEquals("共 28 集", payload.episodeSummary)
        assertEquals("2026.01.01", payload.completedDate)
        assertEquals("安静又绵长的告别。", payload.reflectionText)
        assertEquals("https://example.com/cover.jpg", payload.coverUrl)
    }

    @Test
    fun `buildCompletionKeepsakeCardPayload supplies calm defaults for blank fields`() {
        val record = CompletionKeepsakeRecord(
            id = "collection:2",
            kind = CompletionKeepsakeKind.COLLECTION,
            title = "   ",
            episodeCount = 0,
            coverUrl = "  ",
            reflection = "",
            completedAtMs = 1_767_225_600_000L,
        )

        val payload = buildCompletionKeepsakeCardPayload(record, ZoneOffset.UTC)

        assertEquals("合集完结", payload.badgeLabel)
        assertEquals("完结纪念", payload.title)
        assertEquals("共 1 集", payload.episodeSummary)
        assertEquals("这一程已经认真看完，留下一枚完结印记。", payload.reflectionText)
        assertEquals("", payload.coverUrl)
    }
}
