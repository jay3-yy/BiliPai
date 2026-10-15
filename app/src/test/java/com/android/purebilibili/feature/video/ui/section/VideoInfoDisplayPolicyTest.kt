package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.data.model.response.AiModelResult
import com.android.purebilibili.data.model.response.AiOutline
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.data.model.response.VideoDetailRights
import com.android.purebilibili.data.model.response.VideoStaff
import com.android.purebilibili.data.model.response.ViewInfo
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoInfoDisplayPolicyTest {

    @Test
    fun ownerStats_placesCountsBeforeLabels() {
        assertEquals(listOf("9粉丝", "2视频"), resolveVideoDetailOwnerStatsLabels(9, 2))
    }

    @Test
    fun ownerStats_preservesZeroAndHidesUnknownCounts() {
        assertEquals(listOf("0粉丝", "0视频"), resolveVideoDetailOwnerStatsLabels(0, 0))
        assertEquals(listOf("2视频"), resolveVideoDetailOwnerStatsLabels(null, 2))
        assertEquals(listOf("9粉丝"), resolveVideoDetailOwnerStatsLabels(9, -1))
        assertEquals(emptyList(), resolveVideoDetailOwnerStatsLabels(null, null))
        assertEquals(emptyList(), resolveVideoDetailOwnerStatsLabels(-1, -1))
    }

    @Test
    fun videoInfoInitialExpanded_whenDescriptionOrTagsExist() {
        assertTrue(
            resolveVideoInfoInitialExpandedState(
                hasDescription = true,
                hasTags = false,
                defaultExpanded = true
            )
        )
        assertTrue(
            resolveVideoInfoInitialExpandedState(
                hasDescription = false,
                hasTags = true,
                defaultExpanded = true
            )
        )
        assertFalse(resolveVideoInfoInitialExpandedState(hasDescription = false, hasTags = false))
    }

    @Test
    fun videoInfoInitialExpanded_defaultsToCollapsedWhenPreferenceIsUnset() {
        assertFalse(resolveVideoInfoInitialExpandedState(hasDescription = true, hasTags = true))
    }

    @Test
    fun videoInfoInitialExpanded_respectsDefaultExpandedSwitch() {
        assertFalse(
            resolveVideoInfoInitialExpandedState(
                hasDescription = true,
                hasTags = true,
                defaultExpanded = false
            )
        )
        assertTrue(
            resolveVideoInfoInitialExpandedState(
                hasDescription = true,
                hasTags = false,
                defaultExpanded = true
            )
        )
    }

    @Test
    fun videoDescriptionAnnotatedString_marksPlainUrlsClickable() {
        val annotated = buildVideoDescriptionAnnotatedString(
            desc = "简介 https://www.bilibili.com/video/BV1xx411c7mD 和 https://example.com/demo",
            urlColor = androidx.compose.ui.graphics.Color.Blue
        )

        val links = annotated.getStringAnnotations(
            tag = VIDEO_DESCRIPTION_URL_TAG,
            start = 0,
            end = annotated.length
        )

        assertEquals(
            listOf(
                "https://www.bilibili.com/video/BV1xx411c7mD",
                "https://example.com/demo"
            ),
            links.map { it.item }
        )
    }

    @Test
    fun videoDescriptionAnnotatedString_marksInlineBvidClickable() {
        val annotated = buildVideoDescriptionAnnotatedString(
            desc = "相关视频 BV1xx411c7mD",
            urlColor = androidx.compose.ui.graphics.Color.Blue
        )

        val link = annotated.getStringAnnotations(
            tag = VIDEO_DESCRIPTION_URL_TAG,
            start = 0,
            end = annotated.length
        ).single()

        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", link.item)
    }

    @Test
    fun aiSummaryEntryShownOnlyWhenEnabledAndContentExists() {
        val aiSummary = AiSummaryData(
            code = 0,
            modelResult = AiModelResult(
                summary = "这是一段 AI 摘要",
                outline = listOf(AiOutline(title = "开场", timestamp = 12))
            )
        )

        assertTrue(shouldShowAiSummaryEntry(aiSummary, isAiSummaryEntryEnabled = true))
        assertFalse(shouldShowAiSummaryEntry(aiSummary, isAiSummaryEntryEnabled = false))
    }

    @Test
    fun aiSummaryEntryHiddenWhenPayloadMissingOrEmpty() {
        val emptySummary = AiSummaryData(
            code = 0,
            modelResult = AiModelResult(summary = "", outline = emptyList())
        )

        assertFalse(shouldShowAiSummaryEntry(aiSummary = null, isAiSummaryEntryEnabled = true))
        assertFalse(shouldShowAiSummaryEntry(emptySummary, isAiSummaryEntryEnabled = true))
    }

    @Test
    fun videoNoteEntryShownOnlyForLoadedVideoWithAid() {
        assertTrue(shouldShowVideoNoteEntry(isVideoLoaded = true, aid = 123L))
        assertFalse(shouldShowVideoNoteEntry(isVideoLoaded = false, aid = 123L))
        assertFalse(shouldShowVideoNoteEntry(isVideoLoaded = true, aid = 0L))
    }

    @Test
    fun inlineOwnerIdentityShownOnlyWhenLeadingAvatarHidden() {
        assertTrue(shouldShowInlineOwnerIdentity(showOwnerAvatar = false))
        assertFalse(shouldShowInlineOwnerIdentity(showOwnerAvatar = true))
    }

    @Test
    fun videoBadgesPrioritizeUpowerExclusiveAndIncludePreviewState() {
        val info = ViewInfo(
            isUpowerExclusive = true,
            isUpowerPreview = true,
            rights = VideoDetailRights(isCooperation = 1)
        )

        assertEquals(
            listOf("充电专属 · 可试看", "联合投稿"),
            resolveVideoDetailBadges(info)
        )
    }

    @Test
    fun creatorTeamShownOnlyWhenStaffListExists() {
        assertTrue(
            shouldShowCreatorTeamSection(
                ViewInfo(staff = listOf(VideoStaff(mid = 101L, name = "Lucky-101")))
            )
        )
        assertFalse(
            shouldShowCreatorTeamSection(
                ViewInfo(rights = VideoDetailRights(isCooperation = 1))
            )
        )
    }

    @Test
    fun publishTimeRow_emphasizesCurrentAffairsVideosFromPartitionKeywords() {
        assertTrue(
            shouldEmphasizePrecisePublishTime(
                partitionName = "资讯",
                title = "今晚新闻速递"
            )
        )
    }

    @Test
    fun publishTimeRow_emphasizesCurrentAffairsVideosFromTitleKeywords() {
        assertTrue(
            shouldEmphasizePrecisePublishTime(
                partitionName = "科技",
                title = "联合国发布会最新回应"
            )
        )
    }

    @Test
    fun publishTimeRow_keepsOrdinaryVideosLightweight() {
        assertFalse(
            shouldEmphasizePrecisePublishTime(
                partitionName = "动画",
                title = "今天继续补番"
            )
        )
    }

    @Test
    fun publishTimeRow_usesRelativeAndPreciseTextForCurrentAffairsVideos() {
        assertEquals(
            "发布时间 2小时前  ·  2024-03-01 02:00",
            resolvePublishTimeRowText(
                pubdate = 1_709_258_400L,
                partitionName = "资讯",
                title = "例行发布会速报",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun publishTimeRow_usesRelativeOnlyTextForOrdinaryVideos() {
        assertEquals(
            "发布于 2小时前",
            resolvePublishTimeRowText(
                pubdate = 1_709_258_400L,
                partitionName = "动画",
                title = "新番混剪",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun publishTimeRow_allowsHomepageCardsToEmphasizeFromTitleOnly() {
        assertEquals(
            "发布时间 2小时前  ·  2024-03-01 02:00",
            resolvePublishTimeRowText(
                pubdate = 1_709_258_400L,
                partitionName = "",
                title = "联合国发布会速报",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun compactPublishTimeRow_neverAppendsPreciseTimeForCards() {
        assertEquals(
            "发布于 3天前",
            resolveCompactPublishTimeRowText(
                pubdate = 1_709_006_400L,
                nowMs = 1_709_265_600_000L
            )
        )
    }

    @Test
    fun videoDetailOnlineCountTextRequiresSettingAndLoadedValue() {
        assertEquals(
            "123人正在看",
            resolveVideoDetailOnlineCountText(
                showOnlineCount = true,
                onlineCount = " 123人正在看 "
            )
        )
        assertEquals(
            "",
            resolveVideoDetailOnlineCountText(
                showOnlineCount = false,
                onlineCount = "123人正在看"
            )
        )
        assertEquals(
            "",
            resolveVideoDetailOnlineCountText(
                showOnlineCount = true,
                onlineCount = " "
            )
        )
    }

    @Test
    fun dynamicPublishTimeRow_usesDynamicLabelForOrdinaryVideos() {
        assertEquals(
            "动态发布 2小时前",
            resolveDynamicPublishTimeRowText(
                publishTs = 1_709_258_400L,
                title = "新番混剪",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun dynamicPublishTimeRow_usesRelativeAndPreciseTextForCurrentAffairsVideos() {
        assertEquals(
            "动态发布 2小时前  ·  2024-03-01 02:00",
            resolveDynamicPublishTimeRowText(
                publishTs = 1_709_258_400L,
                title = "外交部最新回应",
                nowMs = 1_709_265_600_000L,
                locale = Locale.US,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }

    @Test
    fun shouldUseCompactUpInfoLayout_whenWidthBelowThreshold() {
        assertTrue(shouldUseCompactUpInfoLayout(180))
        assertTrue(shouldUseCompactUpInfoLayout(240))
        assertTrue(shouldUseCompactUpInfoLayout(319))
        assertFalse(shouldUseCompactUpInfoLayout(320))
        assertFalse(shouldUseCompactUpInfoLayout(412))
        assertFalse(shouldUseCompactUpInfoLayout(0))
        assertFalse(shouldUseCompactUpInfoLayout(-1))
    }

    @Test
    fun sponsorLabel_stacksAboveTitleOnlyWhenLong() {
        assertFalse(shouldStackSponsorLabelAboveTitle("恰饭"))
        assertFalse(shouldStackSponsorLabelAboveTitle("赞助/恰饭"))
        assertFalse(shouldStackSponsorLabelAboveTitle(""))

        val longLabel = "过场/开场动画/音乐:非音乐部分/赞助/恰饭/离题闲聊/玩笑"
        assertTrue(longLabel.length > 10)
        assertTrue(shouldStackSponsorLabelAboveTitle(longLabel))
        assertTrue(shouldStackSponsorLabelAboveTitle("12345678901"))
        assertFalse(shouldStackSponsorLabelAboveTitle("1234567890"))
    }
}
