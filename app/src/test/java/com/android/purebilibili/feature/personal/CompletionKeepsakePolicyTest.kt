package com.android.purebilibili.feature.personal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CompletionKeepsakePolicyTest {

    @Test
    fun `resolveBangumiCompletionKeepsake triggers only on final episode of multi-episode series`() {
        assertNull(resolveBangumiCompletionKeepsake(100L, "单集OVA", "", episodeCount = 1, currentEpisodeIndex = 0))
        assertNull(resolveBangumiCompletionKeepsake(100L, "追番", "", episodeCount = 12, currentEpisodeIndex = 10))

        val prompt = assertNotNull(
            resolveBangumiCompletionKeepsake(100L, "葬送的芙莉莲", "//img.test/cover.jpg", episodeCount = 28, currentEpisodeIndex = 27)
        )
        assertEquals("bangumi:100", prompt.id)
        assertEquals(CompletionKeepsakeKind.BANGUMI, prompt.kind)
        assertEquals(28, prompt.episodeCount)
        assertEquals("https://img.test/cover.jpg", prompt.coverUrl)
    }

    @Test
    fun `resolveVideoSeriesCompletionKeepsake detects collection finale and multi-part finale`() {
        val collectionPrompt = assertNotNull(
            resolveVideoSeriesCompletionKeepsake(
                bvid = "BV3",
                title = "第三集",
                coverUrl = "https://img.test/c.jpg",
                cid = 30L,
                pageCids = listOf(30L),
                seasonId = 777L,
                seasonTitle = "完整合集",
                seasonEpisodeBvids = listOf("BV1", "BV2", "BV3"),
            )
        )
        assertEquals("collection:777", collectionPrompt.id)
        assertEquals(CompletionKeepsakeKind.COLLECTION, collectionPrompt.kind)
        assertEquals("完整合集", collectionPrompt.title)
        assertEquals(3, collectionPrompt.episodeCount)

        val multiPartPrompt = assertNotNull(
            resolveVideoSeriesCompletionKeepsake(
                bvid = "BV_MP",
                title = "多P教程",
                coverUrl = "",
                cid = 200L,
                pageCids = listOf(100L, 200L),
            )
        )
        assertEquals("multipart:BV_MP", multiPartPrompt.id)
        assertEquals(CompletionKeepsakeKind.MULTI_PART, multiPartPrompt.kind)
        assertEquals(2, multiPartPrompt.episodeCount)

        assertNull(
            resolveVideoSeriesCompletionKeepsake(
                bvid = "BV_SINGLE",
                title = "单P普通视频",
                coverUrl = "",
                cid = 100L,
                pageCids = listOf(100L),
            )
        )
    }

    @Test
    fun `filterCompletionKeepsakes filters by kind and matches title or reflection`() {
        val records = listOf(
            CompletionKeepsakeRecord(
                id = "bangumi:1",
                kind = CompletionKeepsakeKind.BANGUMI,
                title = "葬送的芙莉莲",
                episodeCount = 28,
                coverUrl = "",
                reflection = "温柔又绵长的旅途。",
                completedAtMs = 1000L,
            ),
            CompletionKeepsakeRecord(
                id = "collection:2",
                kind = CompletionKeepsakeKind.COLLECTION,
                title = "Kotlin 架构实战",
                episodeCount = 12,
                coverUrl = "",
                reflection = "非常扎实的一套课。",
                completedAtMs = 2000L,
            ),
            CompletionKeepsakeRecord(
                id = "multipart:3",
                kind = CompletionKeepsakeKind.MULTI_PART,
                title = "城市漫步 Vlog",
                episodeCount = 4,
                coverUrl = "",
                reflection = "夜晚散步很治愈。",
                completedAtMs = 3000L,
            ),
        )

        assertEquals(listOf("bangumi:1"), filterCompletionKeepsakes(records, kindFilter = CompletionKeepsakeKind.BANGUMI).map { it.id })
        assertEquals(listOf("collection:2"), filterCompletionKeepsakes(records, query = "kotlin").map { it.id })
        assertEquals(listOf("multipart:3"), filterCompletionKeepsakes(records, query = "治愈").map { it.id })
        assertEquals(emptyList(), filterCompletionKeepsakes(records, kindFilter = CompletionKeepsakeKind.BANGUMI, query = "治愈"))
    }

    @Test
    fun `groupCompletionKeepsakesByMonth groups newest months first`() {
        val records = listOf(
            CompletionKeepsakeRecord(
                id = "jan",
                kind = CompletionKeepsakeKind.BANGUMI,
                title = "一月番",
                episodeCount = 12,
                coverUrl = "",
                reflection = "",
                completedAtMs = 1_768_000_000_000L, // 2026-01
            ),
            CompletionKeepsakeRecord(
                id = "mar",
                kind = CompletionKeepsakeKind.COLLECTION,
                title = "三月合集",
                episodeCount = 6,
                coverUrl = "",
                reflection = "",
                completedAtMs = 1_773_000_000_000L, // 2026-03
            ),
        )
        val groups = groupCompletionKeepsakesByMonth(records, java.time.ZoneOffset.UTC)
        assertEquals(listOf("2026-03", "2026-01"), groups.map { it.monthKey })
        assertEquals(listOf("mar"), groups[0].records.map { it.id })
        assertEquals(listOf("jan"), groups[1].records.map { it.id })
    }
}
