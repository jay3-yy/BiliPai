package com.android.purebilibili.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.android.purebilibili.data.repository.VideoCatalogRepository.resolveRegionRankingRid
import com.android.purebilibili.data.repository.VideoCatalogRepository.shouldFallbackRegionLatestToRanking

class RegionVideoFallbackPolicyTest {

    @Test
    fun `main region first page falls back when latest api fails`() {
        assertTrue(
            shouldFallbackRegionLatestToRanking(
                tid = 3,
                page = 1,
                latestVideoCount = 0,
                latestResponseCode = -404
            )
        )
    }

    @Test
    fun `main region later page does not fall back to repeated ranking list`() {
        assertFalse(
            shouldFallbackRegionLatestToRanking(
                tid = 3,
                page = 2,
                latestVideoCount = 0,
                latestResponseCode = -404
            )
        )
    }

    @Test
    fun `sub region keeps latest api result semantics`() {
        assertFalse(
            shouldFallbackRegionLatestToRanking(
                tid = 28,
                page = 1,
                latestVideoCount = 0,
                latestResponseCode = -404
            )
        )
    }

    @Test
    fun `pgc region does not fall back to ugc ranking`() {
        assertFalse(
            shouldFallbackRegionLatestToRanking(
                tid = 13,
                page = 1,
                latestVideoCount = 0,
                latestResponseCode = -404
            )
        )
        assertFalse(
            shouldFallbackRegionLatestToRanking(
                tid = 167,
                page = 1,
                latestVideoCount = 0,
                latestResponseCode = -404
            )
        )
    }

    @Test
    fun `main region fallback translates legacy tid to ranking v2 rid`() {
        assertEquals(1009, resolveRegionRankingRid(tid = 202))
        assertEquals(1015, resolveRegionRankingRid(tid = 160))
        assertEquals(1001, resolveRegionRankingRid(tid = 181))
    }

}
