package com.android.purebilibili.feature.plugin

import com.android.purebilibili.data.model.response.SponsorActionType
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SponsorFeedBadgePolicyTest {

    @Test
    fun sponsorFeedBadge_sponsorSegmentShowsBadge() {
        val label = resolveSponsorFeedBadgeLabel(
            listOf(feedSegment(category = SponsorCategory.SPONSOR))
        )

        assertEquals(SPONSOR_FEED_BADGE_LABEL, label)
    }

    @Test
    fun sponsorFeedBadge_nonSponsorSegmentsShowNoBadge() {
        // 自我推广、开场片尾等类别不属于「恰饭」，不应误标。
        val label = resolveSponsorFeedBadgeLabel(
            listOf(
                feedSegment(category = SponsorCategory.SELFPROMO),
                feedSegment(category = SponsorCategory.INTRO),
                feedSegment(category = SponsorCategory.OUTRO),
            )
        )

        assertNull(label)
    }

    @Test
    fun sponsorFeedBadge_emptySegmentsShowNoBadge() {
        assertNull(resolveSponsorFeedBadgeLabel(emptyList()))
    }

    private fun feedSegment(category: String): SponsorSegment {
        return SponsorSegment(
            segment = listOf(10f, 20f),
            UUID = "uuid-$category",
            category = category,
            actionType = SponsorActionType.SKIP,
        )
    }
}
