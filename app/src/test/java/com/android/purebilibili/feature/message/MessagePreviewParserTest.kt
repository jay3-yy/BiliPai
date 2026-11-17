package com.android.purebilibili.feature.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessagePreviewParserTest {

    @Test
    fun parseSessionPreview_returnsVideoTitleForVideoCard() {
        val preview = MessagePreviewParser.parseSessionPreview(
            content = """{"title":"春游小片段","bvid":"BV1xx411c7mD","cover":"https://i0.hdslb.com/test.jpg","times":96}""",
            msgType = 11
        )

        assertEquals("视频：春游小片段", preview)
    }

    @Test
    fun parseVideoCard_extractsStructuredFields() {
        val card = MessagePreviewParser.parseVideoCard(
            """{"title":"春游小片段","bvid":"BV1xx411c7mD","cover":"https://i0.hdslb.com/test.jpg","times":96}"""
        )

        assertNotNull(card)
        assertEquals("春游小片段", card.title)
        assertEquals("BV1xx411c7mD", card.bvid)
        assertEquals("https://i0.hdslb.com/test.jpg", card.cover)
        assertEquals(96L, card.duration)
    }

    @Test
    fun videoCardsWithoutBvid_resolveNumericIdsAndVideoLinks() {
        val identities = listOf(
            "\"id\":170001",
            "\"aid\":\"170001\"",
            "\"jump_uri\":\"bilibili://video/170001\"",
            "\"url\":\"https://www.bilibili.com/video/BV17x411w7KC\"",
        )
        for (msgType in listOf(7, 11, 14)) {
            for (identity in identities) {
                val card = MessagePreviewParser.parseMessageCard(
                    content = """{"source":5,"title":"视频",$identity}""",
                    msgType = msgType,
                )
                assertNotNull(card)
                assertEquals(MessageCardKind.Video, card.kind)
                assertEquals("BV17x411w7KC", card.bvid, "msgType=$msgType, $identity")
            }
        }
    }

    @Test
    fun videoCard_preservesShortLinkForNavigationWhenIdIsAbsent() {
        val card = MessagePreviewParser.parseMessageCard(
            content = """{"title":"视频","jump_url":"https://b23.tv/example"}""",
            msgType = 11,
        )
        assertNotNull(card)
        assertEquals("", card.bvid)
        assertEquals("https://b23.tv/example", card.targetUrl)
    }

    @Test
    fun videoCard_prefersBvidOverNumericId() {
        val card = MessagePreviewParser.parseVideoCard(
            """{"bvid":"BV1xx411c7mD","id":170001,"title":"视频"}"""
        )
        assertNotNull(card)
        assertEquals("BV1xx411c7mD", card.bvid)
    }

    @Test
    fun nonVideoShare_doesNotTreatContentIdAsVideoId() {
        val card = MessagePreviewParser.parseMessageCard(
            content = """{"source":6,"id":170001,"title":"专栏","url":"https://www.bilibili.com/read/cv170001"}""",
            msgType = 7,
        )
        assertNotNull(card)
        assertEquals(MessageCardKind.Article, card.kind)
        assertEquals("", card.bvid)
        assertEquals("https://www.bilibili.com/read/cv170001", card.targetUrl)
    }

    @Test
    fun parseMessageCard_returnsArticleShareCard() {
        val card = MessagePreviewParser.parseMessageCard(
            content = """{"source":6,"title":"专栏标题","author":"作者","thumb":"https://i0.hdslb.com/article.jpg","url":"https://www.bilibili.com/read/cv123"}""",
            msgType = 7
        )

        assertNotNull(card)
        assertEquals(MessageCardKind.Article, card.kind)
        assertEquals("专栏标题", card.title)
        assertEquals("作者", card.subtitle)
        assertEquals("https://www.bilibili.com/read/cv123", card.targetUrl)
    }

    @Test
    fun parseMessageCard_returnsLiveShareCardFromOtherContent() {
        val card = MessagePreviewParser.parseMessageCard(
            content = """{"source":4,"title":"直播间","desc":"主播：测试","cover":"https://i0.hdslb.com/live.jpg","url":"https://live.bilibili.com/123"}""",
            msgType = 14
        )

        assertNotNull(card)
        assertEquals(MessageCardKind.Live, card.kind)
        assertEquals("直播间", card.title)
        assertEquals("主播：测试", card.subtitle)
        assertEquals("https://live.bilibili.com/123", card.targetUrl)
    }

    @Test
    fun parseSessionPreview_usesCardTitleForArticlePush() {
        val preview = MessagePreviewParser.parseSessionPreview(
            content = """{"title":"文章推送","summary":"摘要","image_urls":["https://i0.hdslb.com/a.jpg"]}""",
            msgType = 12
        )

        assertEquals("专栏：文章推送", preview)
    }
}
