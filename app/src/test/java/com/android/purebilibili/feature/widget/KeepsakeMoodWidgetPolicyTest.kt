package com.android.purebilibili.feature.widget

import com.android.purebilibili.feature.personal.CompletionKeepsakeKind
import com.android.purebilibili.feature.personal.CompletionKeepsakeRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeepsakeMoodWidgetPolicyTest {

    @Test
    fun `greeting adapts cleanly across day and late night`() {
        assertEquals("早安", resolveKeepsakeWidgetGreeting(7))
        assertEquals("午安", resolveKeepsakeWidgetGreeting(12))
        assertEquals("下午好", resolveKeepsakeWidgetGreeting(15))
        assertEquals("晚上好", resolveKeepsakeWidgetGreeting(20))
        assertEquals("夜深了，早点休息", resolveKeepsakeWidgetGreeting(23))
        assertEquals("夜深了，早点休息", resolveKeepsakeWidgetGreeting(2))
    }

    @Test
    fun `empty keepsake list falls back to calm daily quote and rotates with manual offset`() {
        val first = pickKeepsakeWidgetDisplayModel(
            records = emptyList(),
            epochDay = 20_000L,
            hourOfDay = 21,
            manualOffset = 0,
        )
        val next = pickKeepsakeWidgetDisplayModel(
            records = emptyList(),
            epochDay = 20_000L,
            hourOfDay = 21,
            manualOffset = 1,
        )
        assertFalse(first.isKeepsake)
        assertEquals("晚上好", first.greeting)
        assertEquals("每日一句", first.badge)
        assertTrue(first.bodyText.isNotBlank())
        assertTrue(first.bodyText != next.bodyText)
    }

    @Test
    fun `prefers records with non-blank reflection over empty reflection records`() {
        val withoutNote = CompletionKeepsakeRecord(
            id = "collection:1",
            kind = CompletionKeepsakeKind.COLLECTION,
            title = "无短评合集",
            episodeCount = 6,
            coverUrl = "",
            reflection = "",
            completedAtMs = 1000L,
            bvid = "BV111",
        )
        val withNote = CompletionKeepsakeRecord(
            id = "bangumi:2",
            kind = CompletionKeepsakeKind.BANGUMI,
            title = "葬送的芙莉莲",
            episodeCount = 28,
            coverUrl = "",
            reflection = "平淡却让人一直记住的旅途。",
            completedAtMs = 2000L,
            seasonId = 2L,
        )

        val picked = pickKeepsakeWidgetDisplayModel(
            records = listOf(withoutNote, withNote),
            epochDay = 20_001L,
            hourOfDay = 9,
            manualOffset = 0,
        )
        assertTrue(picked.isKeepsake)
        assertEquals("早安", picked.greeting)
        assertEquals("追番完结", picked.badge)
        assertEquals("“平淡却让人一直记住的旅途。”", picked.bodyText)
        assertEquals("葬送的芙莉莲 · 共 28 集", picked.footerText)
    }

    @Test
    fun `falls back to episode summary when all keepsakes have blank reflection`() {
        val record = CompletionKeepsakeRecord(
            id = "multipart:BV222",
            kind = CompletionKeepsakeKind.MULTI_PART,
            title = "架构拆解系列",
            episodeCount = 8,
            coverUrl = "",
            reflection = "   ",
            completedAtMs = 3000L,
            bvid = "BV222",
        )

        val picked = pickKeepsakeWidgetDisplayModel(
            records = listOf(record),
            epochDay = 20_002L,
            hourOfDay = 14,
            manualOffset = 3,
        )
        assertTrue(picked.isKeepsake)
        assertEquals("全集看完", picked.badge)
        assertEquals("共看完 8 集，这一程已经收进纪念册。", picked.bodyText)
        assertEquals("BV222", picked.targetBvid)
        assertEquals(null, resolveKeepsakeWidgetDeepLink(picked))
    }

    @Test
    fun `bangumi or empty keepsake routes widget click to personal recap deep link`() {
        val fallback = pickKeepsakeWidgetDisplayModel(
            records = emptyList(),
            epochDay = 20_003L,
            hourOfDay = 19,
        )
        assertEquals(KEEPSAKE_WIDGET_RECAP_DEEP_LINK, resolveKeepsakeWidgetDeepLink(fallback))

        val bangumi = pickKeepsakeWidgetDisplayModel(
            records = listOf(
                CompletionKeepsakeRecord(
                    id = "bangumi:9",
                    kind = CompletionKeepsakeKind.BANGUMI,
                    title = "迷宫饭",
                    episodeCount = 24,
                    coverUrl = "",
                    reflection = "看饿了。",
                    completedAtMs = 4000L,
                    seasonId = 9L,
                )
            ),
            epochDay = 20_003L,
            hourOfDay = 19,
        )
        assertEquals(KEEPSAKE_WIDGET_RECAP_DEEP_LINK, resolveKeepsakeWidgetDeepLink(bangumi))
    }
}
