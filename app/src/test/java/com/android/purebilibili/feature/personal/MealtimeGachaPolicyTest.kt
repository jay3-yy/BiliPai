package com.android.purebilibili.feature.personal

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MealtimeGachaPolicyTest {

    private val sampleCandidates = listOf(
        MealtimeGachaCandidate("BV_SHORT_1", 1L, "5分钟短片", "", "UP1", durationSec = 300),
        MealtimeGachaCandidate("BV_MEAL_1", 2L, "15分钟下饭", "", "UP2", durationSec = 900),
        MealtimeGachaCandidate("BV_MEAL_2", 3L, "25分钟解析", "", "UP3", durationSec = 1500),
        MealtimeGachaCandidate("BV_LONG_1", 4L, "1小时纪录片", "", "UP4", durationSec = 3600),
        MealtimeGachaCandidate("BV_MEAL_1", 2L, "重复项", "", "UP2", durationSec = 900),
    )

    @Test
    fun `filterMealtimeCandidates buckets durations and deduplicates by bvid`() {
        assertEquals(
            listOf("BV_MEAL_1", "BV_MEAL_2"),
            filterMealtimeCandidates(sampleCandidates, MealtimeDurationFilter.MEALTIME).map { it.bvid },
        )
        assertEquals(
            listOf("BV_SHORT_1"),
            filterMealtimeCandidates(sampleCandidates, MealtimeDurationFilter.QUICK_BITE).map { it.bvid },
        )
        assertEquals(
            4,
            filterMealtimeCandidates(sampleCandidates, MealtimeDurationFilter.ALL).size,
        )
    }

    @Test
    fun `drawNextMealtimeCandidate exhausts round without repeats and avoids immediate repeat after reshuffle`() {
        val random = Random(42)
        val initial = startMealtimeGachaSession(sampleCandidates, MealtimeDurationFilter.MEALTIME, random)
        val first = assertNotNull(initial.current)
        assertEquals(1, initial.drawnCount)
        assertEquals(2, initial.poolSize)

        val secondSession = drawNextMealtimeCandidate(initial, sampleCandidates, MealtimeDurationFilter.MEALTIME, random)
        val second = assertNotNull(secondSession.current)
        assertNotEquals(first.bvid, second.bvid)
        assertEquals(2, secondSession.drawnCount)

        val reshuffledSession = drawNextMealtimeCandidate(secondSession, sampleCandidates, MealtimeDurationFilter.MEALTIME, random)
        val afterReshuffle = assertNotNull(reshuffledSession.current)
        assertNotEquals(second.bvid, afterReshuffle.bvid)
        assertEquals(1, reshuffledSession.drawnCount)
    }

    @Test
    fun `startMealtimeGachaSession returns empty state when filter bucket has no items`() {
        val longOnly = listOf(MealtimeGachaCandidate("BV_LONG", 1L, "长视频", "", "UP", durationSec = 4200))
        val session = startMealtimeGachaSession(longOnly, MealtimeDurationFilter.MEALTIME)
        assertNull(session.current)
        assertEquals(0, session.poolSize)
    }
}
