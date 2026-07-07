package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyCardLabel
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyContentUrl
import com.android.purebilibili.data.model.response.ReplyControl
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyRichText
import com.android.purebilibili.data.model.response.ReplyRichTextNote
import com.android.purebilibili.data.model.response.ReplyRichTextOpus
import com.android.purebilibili.data.model.response.ReplyVote
import com.android.purebilibili.data.model.response.ReplySailingCardBg
import com.android.purebilibili.data.model.response.ReplySailingFan
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplyUpAction
import com.android.purebilibili.data.model.response.ReplyUserSailing
import java.io.File
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
    fun `sub reply preview opens its own floor while root opens thread top`() {
        assertEquals(22L, resolveSubReplyOpenTargetId(rootReplyId = 11L, clickedReplyId = 22L))
        assertEquals(0L, resolveSubReplyOpenTargetId(rootReplyId = 11L, clickedReplyId = 11L))
        assertEquals(0L, resolveSubReplyOpenTargetId(rootReplyId = 11L, clickedReplyId = 0L))
    }

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
    fun `shouldShowReplyTopBadge uses explicit pinned and reply control flags`() {
        assertTrue(
            shouldShowReplyTopBadge(
                item = ReplyItem(),
                isPinned = true
            )
        )
        assertTrue(
            shouldShowReplyTopBadge(
                item = ReplyItem(replyControl = ReplyControl(isUpTop = true)),
                isPinned = false
            )
        )
        assertFalse(
            shouldShowReplyTopBadge(
                item = ReplyItem(replyControl = ReplyControl(isUpTop = false)),
                isPinned = false
            )
        )
    }

    @Test
    fun `shouldShowReplyTopAction only allows owner on root comments`() {
        assertTrue(
            shouldShowReplyTopAction(
                currentMid = 42L,
                upMid = 42L,
                item = ReplyItem(root = 0L)
            )
        )
        assertFalse(
            shouldShowReplyTopAction(
                currentMid = 7L,
                upMid = 42L,
                item = ReplyItem(root = 0L)
            )
        )
        assertFalse(
            shouldShowReplyTopAction(
                currentMid = 42L,
                upMid = 42L,
                item = ReplyItem(root = 100L)
            )
        )
    }

    @Test
    fun `resolveReplyTopActionLabel reflects current top state`() {
        assertEquals("置顶", resolveReplyTopActionLabel(isCurrentlyTop = false))
        assertEquals("取消置顶", resolveReplyTopActionLabel(isCurrentlyTop = true))
    }

    @Test
    fun `resolveSubReplyPreviewSummaryLabel follows pili plus wording`() {
        assertEquals(
            "UP主等人 共4条回复",
            resolveSubReplyPreviewSummaryLabel(replyCount = 4, hasUpReply = true)
        )
        assertEquals(
            "共2条回复",
            resolveSubReplyPreviewSummaryLabel(replyCount = 2, hasUpReply = false)
        )
    }

    @Test
    fun `resolveReplyThreadCount uses the strongest available count`() {
        assertEquals(
            5,
            resolveReplyThreadCount(
                ReplyItem(
                    count = 2,
                    rcount = 5,
                    replies = listOf(
                        ReplyItem(rpid = 11),
                        ReplyItem(rpid = 12)
                    )
                )
            )
        )
    }

    @Test
    fun `root click opens thread only when reply has nested replies`() {
        assertTrue(
            shouldOpenReplyThreadFromRootClick(
                ReplyItem(
                    rcount = 1,
                    content = ReplyContent(message = "has remote thread")
                )
            )
        )
        assertTrue(
            shouldOpenReplyThreadFromRootClick(
                ReplyItem(
                    replies = listOf(ReplyItem(rpid = 11L)),
                    content = ReplyContent(message = "has preview thread")
                )
            )
        )
        assertFalse(
            shouldOpenReplyThreadFromRootClick(
                ReplyItem(content = ReplyContent(message = "plain comment"))
            )
        )
    }

    @Test
    fun `buildReplyCommentShareText includes author message and comment url`() {
        val text = buildReplyCommentShareText(
            ReplyItem(
                oid = 100L,
                rpid = 777L,
                member = ReplyMember(uname = "评论者"),
                content = ReplyContent(message = "保存这条评论")
            )
        )

        assertEquals(
            "评论者: 保存这条评论\nhttps://www.bilibili.com/video/av100?comment_on=1&comment_root_id=777",
            text
        )
    }

    @Test
    fun `resolveReplyCommentShareUrl includes secondary id for sub replies`() {
        assertEquals(
            "https://www.bilibili.com/video/av100?comment_on=1&comment_root_id=777&comment_secondary_id=888",
            resolveReplyCommentShareUrl(
                ReplyItem(
                    oid = 100L,
                    root = 777L,
                    rpid = 888L
                )
            )
        )
    }

    @Test
    fun `reply action sheet policy includes share and block actions when supported`() {
        assertContentEquals(
            listOf(
                ReplyActionSheetAction.COPY_ALL,
                ReplyActionSheetAction.FREE_COPY,
                ReplyActionSheetAction.SAVE,
                ReplyActionSheetAction.SHARE,
                ReplyActionSheetAction.REPLY,
                ReplyActionSheetAction.BLOCK_USER,
                ReplyActionSheetAction.REPORT,
                ReplyActionSheetAction.TOGGLE_TOP,
                ReplyActionSheetAction.DELETE
            ),
            buildReplyActionSheetActions(
                canDelete = true,
                canReport = true,
                canShare = true,
                canBlockUser = true,
                topActionLabel = "置顶"
            )
        )
    }

    @Test
    fun `reply action sheet policy hides share when api disables support share`() {
        assertFalse(
            buildReplyActionSheetActions(
                canDelete = false,
                canReport = false,
                canShare = false,
                canBlockUser = false
            ).contains(ReplyActionSheetAction.SHARE)
        )
    }

    @Test
    fun `buildReplyCommentImageSpec carries author message and qr url`() {
        val spec = buildReplyCommentImageSpec(
            ReplyItem(
                oid = 100L,
                rpid = 777L,
                member = ReplyMember(uname = "评论者"),
                content = ReplyContent(message = "保存这条评论"),
                like = 12,
                ctime = 1_700_000_000L
            ),
            generatedAtMillis = 1_700_000_100_000L
        )

        assertEquals("评论者", spec.authorName)
        assertEquals("保存这条评论", spec.message)
        assertEquals("https://www.bilibili.com/video/av100?comment_on=1&comment_root_id=777", spec.qrUrl)
        assertTrue(spec.footerText.contains("识别二维码"))
        assertTrue(spec.metadataText.contains("12赞"))
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
    fun `buildRichCommentAnnotatedString links mentions and topics from content metadata`() {
        val annotated = buildRichCommentAnnotatedString(
            text = "你好 @测试用户 来看 #动画#",
            atNameToMid = mapOf("测试用户" to 42L),
            topics = setOf("动画")
        )

        assertEquals(
            "42",
            annotated
                .getStringAnnotations(COMMENT_USER_TAG, 3, 8)
                .firstOrNull()
                ?.item
        )
        assertEquals(
            "动画",
            annotated
                .getStringAnnotations(COMMENT_TOPIC_TAG, 11, 15)
                .firstOrNull()
                ?.item
        )
    }

    @Test
    fun `buildRichCommentAnnotatedString uses server url title and app schema`() {
        val annotated = buildRichCommentAnnotatedString(
            text = "看看 https://b23.tv/demo",
            richUrls = mapOf(
                "https://b23.tv/demo" to ReplyContentUrl(
                    title = "视频标题",
                    appUrlSchema = "bilibili://video/BV1testtest"
                )
            )
        )

        assertEquals("看看 视频标题", annotated.text)
        assertEquals(
            "bilibili://video/BV1testtest",
            annotated
                .getStringAnnotations(COMMENT_URL_TAG, 3, annotated.length)
                .firstOrNull()
                ?.item
        )
    }

    @Test
    fun `resolveReplyContentUrlNavigationUrl prefers dynamic web url over misleading video schema`() {
        val url = ReplyContentUrl(
            title = "动态",
            url = "https://t.bilibili.com/1199344045210468386",
            appUrlSchema = "bilibili://video/1199344045210468386"
        )

        assertEquals(
            "https://t.bilibili.com/1199344045210468386",
            resolveReplyContentUrlNavigationUrl(
                rawToken = "https://t.bilibili.com/1199344045210468386",
                url = url
            )
        )
    }

    @Test
    fun `buildRichCommentAnnotatedString accepts server url prefix icon`() {
        val annotated = buildRichCommentAnnotatedString(
            text = "看看 https://b23.tv/demo",
            richUrls = mapOf(
                "https://b23.tv/demo" to ReplyContentUrl(
                    title = "视频标题",
                    appUrlSchema = "bilibili://video/BV1testtest",
                    prefixIcon = "https://example.com/icon.png"
                )
            )
        )

        assertTrue(annotated.text.contains("视频标题"))
        assertEquals(
            "bilibili://video/BV1testtest",
            annotated
                .getStringAnnotations(COMMENT_URL_TAG, 3, annotated.length)
                .firstOrNull()
                ?.item
        )
    }

    @Test
    fun `resolveReplyTopicNavigationUrl builds search deep link`() {
        assertEquals(
            "bilibili://search?keyword=%E5%8A%A8%E7%94%BB",
            resolveReplyTopicNavigationUrl("动画")
        )
    }

    @Test
    fun `resolveReplyContentUrlPrefixInlineId is stable for token`() {
        val token = "https://b23.tv/demo"
        assertEquals(
            resolveReplyContentUrlPrefixInlineId(token),
            resolveReplyContentUrlPrefixInlineId(token)
        )
    }

    @Test
    fun `buildRichCommentAnnotatedString renders vote token with title`() {
        val annotated = buildRichCommentAnnotatedString(
            text = "参加 {vote:987}",
            voteTitle = "投票标题"
        )

        assertEquals("参加 投票: 投票标题", annotated.text)
        assertEquals(
            "987",
            annotated
                .getStringAnnotations(COMMENT_VOTE_TAG, 3, annotated.length)
                .firstOrNull()
                ?.item
        )
    }

    @Test
    fun `buildRichCommentAnnotatedString skips invalid timestamps above max duration`() {
        val annotated = buildRichCommentAnnotatedString(
            text = "看到 99:59 和 01:00",
            maxTimestampSeconds = 120
        )

        assertTrue(
            annotated.getStringAnnotations(COMMENT_TIMESTAMP_TAG, 0, annotated.length)
                .none { it.item == (99 * 60L + 59L).toString() }
        )
        assertTrue(
            annotated.getStringAnnotations(COMMENT_TIMESTAMP_TAG, 0, annotated.length)
                .any { it.item == "60" }
        )
    }

    @Test
    fun `resolveReplyLeadingRichTextReferences prefers note click url then opus url`() {
        assertEquals(
            listOf(
                ReplyLeadingRichTextReference(
                    label = "笔记 ",
                    navigationUrl = "https://example.com/note"
                )
            ),
            resolveReplyLeadingRichTextReferences(
                ReplyContent(
                    richText = ReplyRichText(
                        note = ReplyRichTextNote(clickUrl = "https://example.com/note"),
                        opus = ReplyRichTextOpus(opusId = 112233L)
                    )
                )
            )
        )
        assertEquals(
            listOf(
                ReplyLeadingRichTextReference(
                    label = "笔记 ",
                    navigationUrl = "https://www.bilibili.com/opus/112233"
                )
            ),
            resolveReplyLeadingRichTextReferences(
                ReplyContent(
                    richText = ReplyRichText(
                        opus = ReplyRichTextOpus(opusId = 112233L)
                    )
                )
            )
        )
    }

    @Test
    fun `resolveReplyVoteDisplayText falls back to id`() {
        assertEquals("投票: 标题", resolveReplyVoteDisplayText(1L, "标题"))
        assertEquals("投票: 987", resolveReplyVoteDisplayText(987L, " "))
    }

    @Test
    fun `resolveReplyLeadingRichTextReferences ignores placeholder note cvid`() {
        assertEquals(
            emptyList(),
            resolveReplyLeadingRichTextReferences(
                content = ReplyContent(),
                noteCvidStr = "0"
            )
        )
        assertEquals(
            emptyList(),
            resolveReplyLeadingRichTextReferences(
                content = ReplyContent(),
                noteCvidStr = ""
            )
        )
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
    fun `lightweight reply mode keeps special labels visible while preserving identity and sub preview toggles`() {
        assertTrue(shouldShowReplySpecialLabel(lightweightMode = true))
        assertTrue(shouldShowReplySpecialLabel(lightweightMode = false))
        assertEquals(
            "UP主觉得很赞",
            resolveReplySpecialLabelText(
                cardLabels = emptyList(),
                showUpFlag = true,
                upAction = ReplyUpAction(like = true, reply = false)
            ).takeIf { shouldShowReplySpecialLabel(lightweightMode = true) }
        )
        assertFalse(shouldShowReplyIdentityDecorations(enabled = false))
        assertTrue(shouldShowReplyIdentityDecorations(enabled = true))
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
    fun `reply item layout gives comment text a wider reading column`() {
        val policy = resolveReplyItemLayoutPolicy()

        assertEquals(12, policy.horizontalPaddingDp)
        assertEquals(36, policy.avatarSizeDp)
        assertEquals(8, policy.avatarContentSpacingDp)
        assertEquals(40, policy.actionButtonSizeDp)
        assertEquals(78, policy.decorationWidthReserveDp)
        assertEquals(56, policy.dividerStartPaddingDp)
        assertEquals(
            292,
            resolveReplyItemTextColumnWidthDp(containerWidthDp = 360, policy = policy)
        )
        assertEquals(
            40,
            resolveReplyItemHeaderEndPaddingDp(hasPiliPlusDecoration = false, policy = policy)
        )
        assertEquals(
            118,
            resolveReplyItemHeaderEndPaddingDp(hasPiliPlusDecoration = true, policy = policy)
        )
        assertEquals(
            12,
            resolveReplyItemContentStartPaddingDp(containerWidth = 279.dp, policy = policy)
        )
        assertEquals(
            44,
            resolveReplyItemContentStartPaddingDp(containerWidth = 280.dp, policy = policy)
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
    fun `fan group label uses uppercase co prefix and preserves visible number`() {
        assertEquals("CO.008502", resolveFanGroupLabelText("008502"))
        assertEquals("CO.008502", resolveFanGroupLabelText("8502"))
        assertEquals("", resolveFanGroupLabelText("abc"))
    }

    @Test
    fun `fan group label color falls back when server color lacks contrast`() {
        val resolved = resolveFanGroupLabelTextColor(
            fanColorHex = "#FFFFFF",
            backgroundColor = Color.White,
            fallbackColor = Color(0xFF1B1C1F)
        )

        assertEquals(Color(0xFF1B1C1F), resolved)
    }

    @Test
    fun `fan group label color keeps server color when contrast is readable`() {
        val resolved = resolveFanGroupLabelTextColor(
            fanColorHex = "#1B1C1F",
            backgroundColor = Color.White,
            fallbackColor = Color(0xFF666666)
        )

        assertEquals(Color(0xFF1B1C1F), resolved)
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
    fun `resolveFanGroupDecorationCardBgs prefers focused images before plain card backgrounds`() {
        val member = ReplyMember(
            userSailing = ReplyUserSailing(
                cardBg = ReplySailingCardBg(image = "https://example.com/legacy_plain.png"),
                cardBgWithFocus = ReplySailingCardBg(image = "https://example.com/legacy_focus.png")
            ),
            userSailingV2 = ReplyUserSailing(
                cardBg = ReplySailingCardBg(image = "https://example.com/v2_plain.png"),
                cardBgWithFocus = ReplySailingCardBg(image = "https://example.com/v2_focus.png")
            )
        )

        val images = resolveFanGroupDecorationCardBgs(member).map { it.image }
        assertEquals(
            listOf(
                "https://example.com/v2_focus.png",
                "https://example.com/legacy_focus.png",
                "https://example.com/v2_plain.png",
                "https://example.com/legacy_plain.png"
            ),
            images
        )
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
    fun `resolveFanGroupVisualFromMemberAndSailing prefers sailing fan number and focused garb image`() {
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
        assertEquals("000011", visual.fanNumber)
        assertEquals("https://example.com/garb_card_focus.png", visual.cardBgImageUrl)
        assertEquals("#f76a6b", visual.fanColorHex)
    }

    @Test
    fun `resolveFanGroupVisualFromMemberAndSailing keeps sailing card image when legacy image is missing`() {
        val member = ReplyMember()
        val cards = listOf(
            ReplySailingCardBg(
                image = "https://example.com/sailing_card.png",
                fan = ReplySailingFan(number = 8502, numDesc = "008502", color = "#576690", name = "", isFan = 1)
            )
        )

        val visual = resolveFanGroupVisualFromMemberAndSailing(member, cards)
        assertNotNull(visual)
        assertEquals("008502", visual.fanNumber)
        assertEquals("https://example.com/sailing_card.png", visual.cardBgImageUrl)
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
    fun `resolveDecorationImageUrl keeps normalized original image urls visible`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item.png",
            resolveDecorationImageUrl("//i0.hdslb.com/bfs/garb/item.png")
        )
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item.png",
            resolveDecorationImageUrl("i0.hdslb.com/bfs/garb/item.png")
        )
    }

    @Test
    fun `resolveDecorationImageUrl keeps existing thumbnail suffix unchanged`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/garb/item@240w.webp",
            resolveDecorationImageUrl("https://i0.hdslb.com/bfs/garb/item@240w.webp")
        )
    }

    @Test
    fun `fan group decoration image uses large cropped presentation for transparent garb assets`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/ui/components/ReplyComponents.kt")
            .readText()
        val decorationSource = source
            .substringAfter("@Composable\ninternal fun FanGroupDecorationBadge(")
            .substringBefore("@Composable\nprivate fun PiliPlusGarbCardDecoration(")

        assertTrue(decorationSource.contains("contentScale = ContentScale.Crop"))
        assertTrue(decorationSource.contains("layoutPolicy.decorationImageWidthDp.dp"))
        assertTrue(decorationSource.contains("layoutPolicy.decorationImageHeightDp.dp"))
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
            listOf(1L, 2L),
            resolveVisibleSubReplies(
                replies = replies,
                expanded = false,
                collapsedLimit = 2
            ).map { it.rpid }
        )
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            resolveVisibleSubReplies(replies = replies, expanded = true).map { it.rpid }
        )
    }

    @Test
    fun `sub reply preview expands by default when replies are already returned`() {
        assertTrue(resolveInitialSubReplyPreviewExpanded(previewReplyCount = 2))
        assertFalse(resolveInitialSubReplyPreviewExpanded(previewReplyCount = 0))
    }

    @Test
    fun `inline sub reply toggle only appears when preview count exceeds collapsed limit`() {
        assertFalse(shouldShowInlineSubReplyToggle(previewReplyCount = 3))
        assertTrue(shouldShowInlineSubReplyToggle(previewReplyCount = 4))
        assertEquals("展开回复", resolveInlineSubReplyToggleLabel(expanded = false))
        assertEquals("收起回复", resolveInlineSubReplyToggleLabel(expanded = true))
    }

    @Test
    fun `normalizeCollapsedSubReplyPreviewLimit clamps supported range`() {
        assertEquals(1, normalizeCollapsedSubReplyPreviewLimit(0))
        assertEquals(3, normalizeCollapsedSubReplyPreviewLimit(3))
        assertEquals(10, normalizeCollapsedSubReplyPreviewLimit(99))
    }

    @Test
    fun `sub reply prefix includes compact official verify token`() {
        assertContentEquals(
            listOf("测试用户", " ", "[VERIFY_PERSONAL]", " ", "[UP]", ": "),
            buildSubReplyPreviewPrefix(
                userName = "测试用户",
                isUpComment = true,
                officialVerifyTone = com.android.purebilibili.core.ui.OfficialVerifyBadgeTone.PERSONAL
            )
        )
        assertContentEquals(
            listOf("机构号", " ", "[VERIFY_ORGANIZATION]", ": "),
            buildSubReplyPreviewPrefix(
                userName = "机构号",
                isUpComment = false,
                officialVerifyTone = com.android.purebilibili.core.ui.OfficialVerifyBadgeTone.ORGANIZATION
            )
        )
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
