package com.android.purebilibili.data.model.response

import com.android.purebilibili.data.repository.asVideoItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoStatusLabelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun mobileFeedPlaybackDimensionsProduceVerticalBadge() {
        val portrait = json.decodeFromString<MobileFeedItem>(
            """{"goto":"av","uri":"bilibili://video/123?player_width=1080&player_height=1920"}""",
        ).toVideoItem()
        assertTrue(portrait.isVertical)
        assertTrue("竖屏" in portrait.statusLabels)
        val landscape = json.decodeFromString<MobileFeedItem>(
            """{"goto":"av","uri":"bilibili://video/123?player_width=1920&player_height=1080"}""",
        ).toVideoItem()
        assertTrue("竖屏" !in landscape.statusLabels)
    }

    @Test
    fun popularChargingAndServerLabelsSurviveConversionWithoutDuplicates() {
        val item = json.decodeFromString<PopularItem>(
            """{"charging_pay":{"level":0},"rights":{"is_cooperation":1},
                "pgc_label":"独家","badges":[{"text":"独家"},{"text":"合作"}]}""",
        ).toVideoItem()
        assertEquals(listOf("充电专属", "合作", "独家"), item.statusLabels)
    }

    @Test
    fun courseSearchUsesGlobalLabelAndRetainsServerBadges() {
        val item = json.decodeFromString<SearchVideoItem>(
            """{"type":"ketang","badges":[{"text":"课程"},{"text":"限时免费"}]}""",
        ).toVideoItem()
        assertEquals(listOf("课堂", "限时免费"), item.statusLabels)
    }

    @Test
    fun spaceInteractiveAndCourseFlagsSurviveMapping() {
        val item = json.decodeFromString<SpaceVideoItem>(
            """{"is_steins":true,"is_lesson_video":1,"is_charging_arc":true,
                "badges":[{"text":"互动"},{"text":"限免"}]}""",
        )
        assertEquals(listOf("充电专属", "课堂", "互动", "限免"), item.statusLabels)
        assertEquals(item.statusLabels, item.asVideoItem(1L).statusLabels)
    }

    @Test
    fun rightsSupportInteractiveAndPaidVideoWithoutChargingMisclassification() {
        val video = json.decodeFromString<VideoItem>(
            """{"rights":{"is_stein_gate":1,"pay":1}}""",
        )
        assertEquals(listOf("付费", "互动"), video.statusLabels)
    }

    @Test
    fun ordinaryVideosHaveNoStatusBadgesAndBlankServerLabelsAreIgnored() {
        assertTrue(json.decodeFromString<VideoItem>("{}").statusLabels.isEmpty())
        assertTrue(resolveVideoStatusLabels(serverLabels = listOf("", "  ")).isEmpty())
    }

    @Test
    fun mobileFeedDurationIsNotMistakenForAnInterfaceBadge() {
        val video = json.decodeFromString<MobileFeedItem>(
            """{"goto":"av","cover_right_text":"03:25"}""",
        ).toVideoItem()
        assertTrue(video.statusLabels.isEmpty())
        val pgc = json.decodeFromString<MobileFeedItem>(
            """{"goto":"bangumi","cover_right_text":"独家"}""",
        ).toVideoItem()
        assertEquals(listOf("独家"), pgc.statusLabels)
    }
}
