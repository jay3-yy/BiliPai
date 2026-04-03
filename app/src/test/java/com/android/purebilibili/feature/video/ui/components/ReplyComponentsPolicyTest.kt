package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyCardLabel
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplySailingCardBg
import com.android.purebilibili.data.model.response.ReplySailingFan
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplyUpAction
import kotlinx.coroutines.runBlocking
import kotlin.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyComponentsPolicyTest {

    @Test
    fun `resolveReplyLevelBadgeAsset keeps pili plus mapping for normal levels`() {
        assertEquals(
            ReplyLevelBadgeAsset.LEVEL_4,
            resolveReplyLevelBadgeAsset(level = 4, isSeniorMember = false)
        )
    }

    @Test
    fun `resolveReplyLevelBadgeAsset prefers senior level six badge when available`() {
        assertEquals(
            ReplyLevelBadgeAsset.LEVEL_6_SENIOR,
            resolveReplyLevelBadgeAsset(level = 6, isSeniorMember = true)
        )
    }

    @Test
    fun `resolveReplyLevelBadgeAsset rejects invalid levels`() {
        assertNull(resolveReplyLevelBadgeAsset(level = -1, isSeniorMember = false))
        assertNull(resolveReplyLevelBadgeAsset(level = 7, isSeniorMember = false))
    }

    @Test
    fun `resolveReplySpecialLabelText prefers server label over fallback`() {
        val text = resolveReplySpecialLabelText(
            cardLabels = listOf(
                ReplyCardLabel(
                    textContent = "UP主觉得很赞",
                    labelColor = "#FB7299",
                    jumpUrl = ""
                )
            ),
            showUpFlag = true,
            upAction = ReplyUpAction(like = true, reply = false)
        )

        assertEquals("UP主觉得很赞", text)
    }

    @Test
    fun `resolveReplySpecialLabelText falls back only when config allows it`() {
        assertEquals(
            "UP主觉得很赞",
            resolveReplySpecialLabelText(
                cardLabels = emptyList(),
                showUpFlag = true,
                upAction = ReplyUpAction(like = true, reply = false)
            )
        )
        assertNull(
            resolveReplySpecialLabelText(
                cardLabels = emptyList(),
                showUpFlag = false,
                upAction = ReplyUpAction(like = true, reply = false)
            )
        )
    }

    @Test
    fun `resolveReplySpecialLabelText ignores blank server labels`() {
        assertNull(
            resolveReplySpecialLabelText(
                cardLabels = listOf(
                    ReplyCardLabel(
                        textContent = "   ",
                        labelColor = "",
                        jumpUrl = ""
                    )
                ),
                showUpFlag = false,
                upAction = ReplyUpAction(like = false, reply = false)
            )
        )
    }

    @Test
    fun `resolveReplyDisplayLikeCount applies optimistic state only when needed`() {
        assertEquals(8, resolveReplyDisplayLikeCount(baseLikeCount = 7, initialAction = 0, isLiked = true))
        assertEquals(6, resolveReplyDisplayLikeCount(baseLikeCount = 7, initialAction = 1, isLiked = false))
        assertEquals(7, resolveReplyDisplayLikeCount(baseLikeCount = 7, initialAction = 1, isLiked = true))
    }

    @Test
    fun `resolveReplyLocationText normalizes non empty values`() {
        assertEquals("IP归属地：上海", resolveReplyLocationText("IP属地：上海"))
        assertEquals("IP归属地：北京", resolveReplyLocationText("北京"))
        assertNull(resolveReplyLocationText(""))
    }

    @Test
    fun `buildSubReplyPreviewPrefix includes separator and optional up tag`() {
        assertContentEquals(
            listOf("测试用户", " ", "[UP]", ": "),
            buildSubReplyPreviewPrefix(
                userName = "测试用户",
                isUpComment = true
            )
        )
        assertContentEquals(
            listOf("路人", ": "),
            buildSubReplyPreviewPrefix(
                userName = "路人",
                isUpComment = false
            )
        )
    }

    @Test
    fun `resolveReplyItemContentType distinguishes media label and thread variants`() {
        assertEquals(
            "reply_labeled",
            resolveReplyItemContentType(
                ReplyItem(
                    cardLabels = listOf(ReplyCardLabel(textContent = "UP主觉得很赞")),
                    content = ReplyContent(message = "special")
                )
            )
        )
        assertEquals(
            "reply_media",
            resolveReplyItemContentType(
                ReplyItem(
                    content = ReplyContent(
                        message = "media",
                        pictures = listOf(ReplyPicture(imgSrc = "https://example.com/1.jpg"))
                    )
                )
            )
        )
        assertEquals(
            "reply_thread",
            resolveReplyItemContentType(
                ReplyItem(
                    rcount = 2,
                    content = ReplyContent(message = "thread")
                )
            )
        )
        assertEquals(
            "reply_plain",
            resolveReplyItemContentType(
                ReplyItem(
                    content = ReplyContent(message = "plain")
                )
            )
        )
    }

    @Test
    fun `collectRenderableEmoteKeys only keeps used and mapped tokens`() {
        val emoteMap = mapOf(
            "[doge]" to "url_doge",
            "[笑哭]" to "url_laugh",
            "[不存在]" to "url_none"
        )

        val keys = collectRenderableEmoteKeys(
            text = "测试 [doge] 还有 [笑哭] 以及 [未收录]",
            emoteMap = emoteMap
        )

        assertEquals(setOf("[doge]", "[笑哭]"), keys)
    }

    @Test
    fun `shouldEnableRichCommentSelection disables expensive mixed mode`() {
        assertFalse(
            shouldEnableRichCommentSelection(
                hasRenderableEmotes = true,
                hasInteractiveAnnotations = true
            )
        )
        assertFalse(
            shouldEnableRichCommentSelection(
                hasRenderableEmotes = true,
                hasInteractiveAnnotations = false
            )
        )
        assertFalse(
            shouldEnableRichCommentSelection(
                hasRenderableEmotes = false,
                hasInteractiveAnnotations = true
            )
        )
        assertTrue(
            shouldEnableRichCommentSelection(
                hasRenderableEmotes = false,
                hasInteractiveAnnotations = false
            )
        )
    }

    @Test
    fun `lightweight reply mode keeps sub previews while hiding ancillary decorations`() {
        assertFalse(shouldShowReplyAncillaryDecorations(lightweightMode = true))
        assertTrue(shouldShowReplyAncillaryDecorations(lightweightMode = false))
        assertTrue(
            shouldShowReplySubPreview(
                hideSubPreview = false,
                lightweightMode = true
            )
        )
        assertFalse(
            shouldShowReplySubPreview(
                hideSubPreview = true,
                lightweightMode = false
            )
        )
        assertTrue(
            shouldShowReplySubPreview(
                hideSubPreview = false,
                lightweightMode = false
            )
        )
    }

    @Test
    fun `timestamp parser supports spaces and full-width colon`() {
        val text = "自用18: 07\n19：30"
        val matches = COMMENT_TIMESTAMP_PATTERN.findAll(text).toList()
        assertEquals(2, matches.size)

        val firstSeconds = parseCommentTimestampSeconds(matches[0])
        val secondSeconds = parseCommentTimestampSeconds(matches[1])
        assertEquals(18 * 60L + 7L, firstSeconds)
        assertEquals(19 * 60L + 30L, secondSeconds)
    }

    @Test
    fun `timestamp parser keeps hour format and rejects invalid second width`() {
        val match = COMMENT_TIMESTAMP_PATTERN.find("1:02:03")
        assertNotNull(match)
        assertEquals(3723L, parseCommentTimestampSeconds(match))

        val invalid = COMMENT_TIMESTAMP_PATTERN.find("3:5")
        assertNull(invalid)
    }

    @Test
    fun `resolveFanGroupTagVisual keeps num_desc and cardbg image`() {
        val fan = ReplySailingFan(
            isFan = 1,
            number = 11,
            color = "#f76a6b",
            name = "测试粉丝团",
            numDesc = "000011"
        )

        val visual = resolveFanGroupTagVisual(
            fan = fan,
            cardBgImage = "https://example.com/card3.png"
        )

        assertNotNull(visual)
        assertEquals("000011", visual.fanNumber)
        assertEquals("https://example.com/card3.png", visual.cardBgImageUrl)
    }

    @Test
    fun `resolveFanGroupTagVisual pads number when num_desc is blank`() {
        val fan = ReplySailingFan(
            isFan = 1,
            number = 11,
            color = "#f76a6b",
            name = "测试粉丝团",
            numDesc = ""
        )

        val visual = resolveFanGroupTagVisual(
            fan = fan,
            cardBgImage = "   "
        )

        assertNotNull(visual)
        assertEquals("000011", visual.fanNumber)
        assertNull(visual.cardBgImageUrl)
    }

    @Test
    fun `resolveSailingDecorationImage picks first non blank card image`() {
        val cards = listOf(
            ReplySailingCardBg(image = "", fan = null),
            ReplySailingCardBg(image = "https://example.com/fan_card.png", fan = null),
            ReplySailingCardBg(image = "https://example.com/other.png", fan = null)
        )

        val image = resolveSailingDecorationImage(cards)
        assertEquals("https://example.com/fan_card.png", image)
    }

    @Test
    fun `resolveSailingFan finds first fan with visible number`() {
        val cards = listOf(
            ReplySailingCardBg(
                image = "",
                fan = ReplySailingFan(number = 0, numDesc = "", color = "", name = "", isFan = 0)
            ),
            ReplySailingCardBg(
                image = "",
                fan = ReplySailingFan(number = 11, numDesc = "", color = "", name = "", isFan = 1)
            )
        )

        val fan = resolveSailingFan(cards)
        assertNotNull(fan)
        assertEquals(11L, fan.number)
    }

    @Test
    fun `resolveFanGroupVisualFromMemberAndSailing prefers pili plus garb card image over focus image`() {
        val member = ReplyMember(
            garbCardImage = "https://example.com/garb_card.png",
            garbCardImageWithFocus = "https://example.com/garb_card_focus.png",
            garbCardNumber = "021288",
            garbCardFanColor = "#f76a6b"
        )
        val cards = listOf(
            ReplySailingCardBg(
                image = "https://example.com/sailing_card.png",
                fan = ReplySailingFan(number = 11, numDesc = "000011", color = "#112233", name = "", isFan = 1)
            )
        )

        val visual = resolveFanGroupVisualFromMemberAndSailing(member, cards)
        assertNotNull(visual)
        assertEquals("021288", visual.fanNumber)
        assertEquals("https://example.com/garb_card.png", visual.cardBgImageUrl)
        assertEquals("#f76a6b", visual.fanColorHex)
    }

    @Test
    fun `normalizeHttpImageUrl upgrades protocol relative and bare host urls`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item.png",
            normalizeHttpImageUrl("//i0.hdslb.com/bfs/garb/item.png")
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item.png",
            normalizeHttpImageUrl("i0.hdslb.com/bfs/garb/item.png")
        )
    }

    @Test
    fun `resolveDecorationImageUrl appends low quality suffix for plain image urls`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item.png@1q.webp",
            resolveDecorationImageUrl("//i0.hdslb.com/bfs/garb/item.png")
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item.png@1q.webp",
            resolveDecorationImageUrl("i0.hdslb.com/bfs/garb/item.png")
        )
    }

    @Test
    fun `resolveDecorationImageUrl upgrades existing thumbnail suffix to include quality`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item@240w_1q.webp",
            resolveDecorationImageUrl("https://i0.hdslb.com/bfs/garb/item@240w.webp")
        )
    }

    @Test
    fun `resolveReplyVideoReference extracts standalone bvid references`() {
        val reference = resolveReplyVideoReference(" BV1ecNuzGEPB ")

        assertNotNull(reference)
        assertEquals("BV1ecNuzGEPB", reference.bvid)
        assertEquals("https://www.bilibili.com/video/BV1ecNuzGEPB", reference.navigationUrl)
    }

    @Test
    fun `resolveReplyVideoReference ignores embedded video ids in regular sentences`() {
        val reference = resolveReplyVideoReference("我觉得 BV1ecNuzGEPB 这个也不错")

        assertNull(reference)
    }

    @Test
    fun `buildRichCommentAnnotatedString marks inline bvid as clickable video url`() {
        val text = "我觉得 BV1ecNuzGEPB 这个也不错"
        val annotated = buildRichCommentAnnotatedString(
            text = text,
            renderableEmoteKeys = emptySet(),
            color = Color.Black,
            timestampColor = Color.Blue,
            urlColor = Color.Red
        )

        val start = text.indexOf("BV1ecNuzGEPB")
        val annotations = annotated.getStringAnnotations(
            tag = "URL",
            start = start,
            end = start + "BV1ecNuzGEPB".length
        )

        assertEquals(1, annotations.size)
        assertEquals(
            "https://www.bilibili.com/video/BV1ecNuzGEPB",
            annotations.single().item
        )
    }

    @Test
    fun `buildRichCommentAnnotatedString ignores bvid-like fragments inside longer tokens`() {
        val text = "前缀xBV1ecNuzGEPBy后缀"
        val annotated = buildRichCommentAnnotatedString(
            text = text,
            renderableEmoteKeys = emptySet(),
            color = Color.Black,
            timestampColor = Color.Blue,
            urlColor = Color.Red
        )

        val annotations = annotated.getStringAnnotations(
            tag = "URL",
            start = 0,
            end = text.length
        )

        assertTrue(annotations.isEmpty())
    }

    @Test
    fun `resolveReplyVideoDisplayText prefers resolved title over fallback id`() {
        assertEquals(
            "真实视频标题",
            resolveReplyVideoDisplayText(
                resolvedTitle = "真实视频标题",
                fallbackText = "BV1ecNuzGEPB"
            )
        )
        assertEquals(
            "BV1ecNuzGEPB",
            resolveReplyVideoDisplayText(
                resolvedTitle = "   ",
                fallbackText = "BV1ecNuzGEPB"
            )
        )
    }

    @Test
    fun `resolveVisibleSubReplies applies collapsed preview limit`() {
        val replies = listOf(
            ReplyItem(rpid = 1L),
            ReplyItem(rpid = 2L),
            ReplyItem(rpid = 3L),
            ReplyItem(rpid = 4L)
        )

        assertEquals(
            listOf(1L, 2L, 3L),
            resolveVisibleSubReplies(replies = replies, expanded = false).map { it.rpid }
        )
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            resolveVisibleSubReplies(replies = replies, expanded = true).map { it.rpid }
        )
    }

    @Test
    fun `inline sub reply toggle only appears when preview count exceeds collapsed limit`() {
        assertFalse(shouldShowInlineSubReplyToggle(previewReplyCount = 3))
        assertTrue(shouldShowInlineSubReplyToggle(previewReplyCount = 4))
        assertEquals("展开回复", resolveInlineSubReplyToggleLabel(expanded = false))
        assertEquals("收起回复", resolveInlineSubReplyToggleLabel(expanded = true))
    }

    @Test
    fun `resolveReplyVideoTitle caches lightweight provider result`() = runBlocking {
        val cache = mutableMapOf<String, String>()
        var providerCalls = 0

        val title = resolveReplyVideoTitle(
            reference = ReplyVideoReference(
                bvid = "BV1ecNuzGEPB",
                navigationUrl = "https://www.bilibili.com/video/BV1ecNuzGEPB"
            ),
            cache = cache,
            titleProvider = { bvid ->
                providerCalls += 1
                "标题:$bvid"
            }
        )

        assertEquals("标题:BV1ecNuzGEPB", title)
        assertEquals(1, providerCalls)
        assertEquals("标题:BV1ecNuzGEPB", cache["BV1ecNuzGEPB"])
    }

    @Test
    fun `resolveReplyVideoTitle reuses cache before provider`() = runBlocking {
        val title = resolveReplyVideoTitle(
            reference = ReplyVideoReference(
                bvid = "BV1ecNuzGEPB",
                navigationUrl = "https://www.bilibili.com/video/BV1ecNuzGEPB"
            ),
            cache = mutableMapOf("BV1ecNuzGEPB" to "缓存标题"),
            titleProvider = {
                error("provider should not be called when cache is warm")
            }
        )

        assertEquals("缓存标题", title)
    }
}
