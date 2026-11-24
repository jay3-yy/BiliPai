package com.android.purebilibili.feature.article

import com.android.purebilibili.data.repository.articleContentBlocksToOpusBlocks
import com.android.purebilibili.data.model.response.OpusContentBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ArticleMediaCardPolicyTest {
    @Test
    fun `legacy video card keeps full preview and opens video instead of image gallery`() {
        val blocks = parseArticleContentBlocks(emptyList(), """{"ops":[
          {"insert":{"video-card":{"id":"av170001","url":"//i0.hdslb.com/card.jpg","width":1200,"height":240}}},
          {"insert":{"image":"//i0.hdslb.com/body.jpg"}}
        ]}""")
        val media = assertIs<ArticleContentBlock.MediaCard>(blocks.first())
        assertEquals("https://www.bilibili.com/video/av170001", media.card.jumpUrl)
        assertEquals(1200, media.preview?.width)
        assertEquals(240, media.preview?.height)
        assertEquals(listOf("https://i0.hdslb.com/body.jpg"), collectArticleBodyImageUrls(blocks))
        assertNull(resolveArticleImagePreviewPayload(blocks, 0))
    }

    @Test
    fun `legacy article html card retains article target between text paragraphs`() {
        val blocks = parseArticleContentBlocks(emptyList(), """<p>前文</p><p><img class="article-card card" aid="cv123" data-src="//i0.hdslb.com/article.jpg" /></p><p>后文</p>""")
        assertEquals(3, blocks.size)
        val media = assertIs<ArticleContentBlock.MediaCard>(blocks[1])
        assertEquals("https://www.bilibili.com/read/cv123", media.card.jumpUrl)
        assertEquals("前文", (blocks.first() as ArticleContentBlock.Paragraph).text)
        assertEquals("后文", (blocks.last() as ArticleContentBlock.Paragraph).text)
    }

    @Test
    fun `missing legacy target preserves the original image`() {
        val blocks = parseArticleContentBlocks(emptyList(), """{"ops":[{"insert":{"article-card":{"url":"//i0.hdslb.com/card.jpg","id":""}}}]}""")
        assertIs<ArticleContentBlock.Image>(blocks.single())
    }

    @Test
    fun `structured video card keeps metadata and survives opus conversion`() {
        val paragraph = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_UGC","oid":"170001","ugc":{
            "title":"视频标题","cover":"//i0.hdslb.com/cover.jpg","desc_second":"视频介绍",
            "jump_url":"https://www.bilibili.com/video/av170001"
          }
        }}}""").jsonObject
        val media = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(paragraph), null).single())
        assertEquals("视频标题", media.card.title)
        assertEquals("视频介绍", media.card.description)
        val converted = assertIs<OpusContentBlock.LinkCard>(articleContentBlocksToOpusBlocks(listOf(media)).single())
        assertEquals(media.card, converted.card)
    }

    @Test
    fun `vote preview retains vote id without a web target`() {
        val insert = Json.parseToJsonElement("""{"vote-card":{"id":"123","url":"//i0.hdslb.com/vote.jpg"}}""").jsonObject
        val card = parseArticleOpsMediaCard(insert)!!
        assertEquals("LINK_CARD_TYPE_VOTE", card.card.type)
        assertEquals("123", card.card.oid)
        assertEquals("", card.card.jumpUrl)
    }

    @Test
    fun `new video and opus card envelopes preserve targets and covers`() {
        listOf(
            "eva3_video" to "LINK_CARD_TYPE_EVA3_VIDEO",
            "eva3_opus" to "LINK_CARD_TYPE_EVA3_OPUS",
        ).forEach { (key, type) ->
            val paragraph = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
              "type":"$type","$key":{"info":{"title":"相关内容","jump_url":"https://www.bilibili.com/opus/123456","pics":[{"src":"//i0.hdslb.com/cover.jpg"}]}}
            }}}""").jsonObject
            val media = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(paragraph), null).single())
            assertEquals("相关内容", media.card.title)
            assertEquals("https://www.bilibili.com/opus/123456", media.card.jumpUrl)
            assertEquals("https://i0.hdslb.com/cover.jpg", media.card.cover)
        }
    }
    @Test
    fun `blank opus and ugc jump urls are filled from a positive oid`() {
        val opus = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_OPUS","oid":"8905947","opus":{"title":"后续专栏标题","jump_url":""}
        }}}""").jsonObject
        val opusCard = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(opus), null).single())
        assertEquals("https://www.bilibili.com/read/cv8905947", opusCard.card.jumpUrl)

        val ugc = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_UGC","oid":"170001","ugc":{"title":"视频标题","jump_url":""}
        }}}""").jsonObject
        val ugcCard = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(ugc), null).single())
        assertEquals("https://www.bilibili.com/video/av170001", ugcCard.card.jumpUrl)

        val kept = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_UGC","oid":"170001","ugc":{
            "title":"视频标题","jump_url":"https://www.bilibili.com/video/BV1xx411c7mD"
          }
        }}}""").jsonObject
        val keptCard = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(kept), null).single())
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", keptCard.card.jumpUrl)

        val large = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_OPUS","oid":"1199344045210468386","opus":{"title":"长图文","cover":"//i0.hdslb.com/cover.jpg","jump_url":""}
        }}}""").jsonObject
        val largeCard = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(large), null).single())
        assertEquals("https://www.bilibili.com/opus/1199344045210468386", largeCard.card.jumpUrl)

        val vote = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_VOTE","oid":"8905947","vote":{"title":"投票标题","vote_id":"55","jump_url":""}
        }}}""").jsonObject
        val voteCard = assertIs<ArticleContentBlock.MediaCard>(parseArticleContentBlocks(listOf(vote), null).single())
        assertEquals("LINK_CARD_TYPE_VOTE", voteCard.card.type)
        assertEquals("", voteCard.card.jumpUrl)

        val missingOid = Json.parseToJsonElement("""{"para_type":6,"link_card":{"card":{
          "type":"LINK_CARD_TYPE_OPUS","opus":{"title":"没有编号","jump_url":""}
        }}}""").jsonObject
        val missingOidCard = assertIs<ArticleContentBlock.MediaCard>(
            parseArticleContentBlocks(listOf(missingOid), null).single()
        )
        assertEquals("", missingOidCard.card.jumpUrl)
    }

    @Test
    fun `mismatched identifier prefix does not open another kind of resource`() {
        val insert = Json.parseToJsonElement("""{"video-card":{"id":"cv123","url":"//i0.hdslb.com/card.jpg"}}""").jsonObject
        assertNull(parseArticleOpsMediaCard(insert))
    }

}
