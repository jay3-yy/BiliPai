package com.android.purebilibili.feature.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalRecapUpSortPolicyTest {
    @Test
    fun countsAndDurationProduceDifferentRanksWithDeterministicTies() {
        val short = up(mid = 3L, count = 5, duration = 30L)
        val long = up(mid = 2L, count = 2, duration = 600L)
        val tied = up(mid = 1L, count = 2, duration = 600L)
        val sameCount = up(mid = 4L, count = 5, duration = 100L)
        val sameDuration = up(mid = 5L, count = 1, duration = 600L)
        val entries = listOf(short, long, tied, sameCount, sameDuration)

        assertEquals(
            listOf(4L, 3L, 1L, 2L, 5L),
            sortPersonalRecapUps(entries, PersonalRecapUpSortOrder.VIDEO_COUNT).map { it.mid },
        )
        assertEquals(
            listOf(1L, 2L, 5L, 4L, 3L),
            sortPersonalRecapUps(entries.reversed(), PersonalRecapUpSortOrder.ESTIMATED_DURATION).map { it.mid },
        )
    }

    @Test
    fun sharesUseAllVideosIncludingThoseWithoutAnUp() {
        val creator = up(mid = 1L, count = 2, duration = 60L)
        val stats = PersonalVideoRecapStats(videoCount = 8, totalDurationSec = 600L, topUps = listOf(creator))

        assertEquals(0.25f, personalRecapUpShare(creator, stats, PersonalRecapUpSortOrder.VIDEO_COUNT)!!, 0.0001f)
        assertEquals(0.1f, personalRecapUpShare(creator, stats, PersonalRecapUpSortOrder.ESTIMATED_DURATION)!!, 0.0001f)
    }

    @Test
    fun missingTotalHasNoShareWhileZeroProgressHasRealZeroShare() {
        val creator = up(mid = 1L, count = 1, duration = 0L)
        val stats = PersonalVideoRecapStats(videoCount = 1, topUps = listOf(creator))

        assertNull(personalRecapUpShare(creator, stats, PersonalRecapUpSortOrder.ESTIMATED_DURATION))
        assertEquals(0f, personalRecapUpShare(creator, stats.copy(totalDurationSec = 60L), PersonalRecapUpSortOrder.ESTIMATED_DURATION)!!, 0f)
        assertNull(personalRecapUpShare(creator, PersonalVideoRecapStats(), PersonalRecapUpSortOrder.VIDEO_COUNT))
    }

    private fun up(mid: Long, count: Int, duration: Long) = PersonalUpRecap(
        mid = mid,
        name = "UP $mid",
        face = "",
        watchCount = count,
        totalDurationSec = duration,
    )
}
