package com.android.purebilibili.feature.video.playback.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.UgcSection
import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcEpisodeArc
import com.android.purebilibili.data.model.response.Page
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWatchSuggestionTest {
    @Test fun countdownFollowsMediaPositionAndRoundsUp() {
        assertEquals(5, nextWatchRemainingSeconds(60_000, 55_000))
        assertEquals(5, nextWatchRemainingSeconds(60_000, 55_999))
        assertEquals(4, nextWatchRemainingSeconds(60_000, 56_000))
        assertEquals(2, nextWatchRemainingSeconds(60_000, 58_000))
        // Seeking backwards within the window updates the displayed number too.
        assertEquals(4, nextWatchRemainingSeconds(60_000, 56_000))
        assertEquals(1, nextWatchRemainingSeconds(60_000, 59_999))
        assertNull(nextWatchRemainingSeconds(60_000, 60_000))
        assertNull(nextWatchRemainingSeconds(60_000, 54_999))
        assertNull(nextWatchRemainingSeconds(-9223372036854775807L, 0))
    }
    @Test fun windowDoesNotAcceptUnknownDurationOrEndedPlayback() {
        assertFalse(isNextWatchWindow(-9223372036854775807L, 10_000))
        assertFalse(isNextWatchWindow(0, 0))
        assertFalse(isNextWatchWindow(60_000, 60_000))
        assertFalse(isNextWatchWindow(60_000, 60_001))
        assertFalse(isNextWatchWindow(60_000, -1))
    }

    @Test fun windowHasExactFiveSecondBoundaryAndSkipsVeryShortClips() {
        assertFalse(isNextWatchWindow(60_000, 54_999))
        assertTrue(isNextWatchWindow(60_000, 55_000))
        assertTrue(isNextWatchWindow(60_000, 59_999))
        assertFalse(isNextWatchWindow(5_000, 1))
    }
    @Test fun partLookaheadDoesNotChangeOrGuessTheCurrentPart() {
        val info = ViewInfo(bvid = "BV-current", cid = 10L,
            pages = listOf(Page(cid = 10L, part = "第一 P"), Page(cid = 20L, part = "第二 P")))
        val next = resolveCollectionNextWatch(info)
        assertEquals(1, next?.pageIndex)
        assertEquals(20L, next?.cid)
        assertEquals(10L, next?.sourceCid)
        assertNull(resolveCollectionNextWatch(info.copy(cid = 99L)))
        assertNull(resolveCollectionNextWatch(info.copy(cid = 20L)))
    }

    @Test fun collectionLookaheadCrossesSectionsAndPrefersRemainingParts() {
        val info = ViewInfo(bvid = "BV-one", cid = 10L, ugc_season = UgcSeason(sections = listOf(
            UgcSection(episodes = listOf(UgcEpisode(bvid = "BV-one", cid = 10L))),
            UgcSection(episodes = listOf(UgcEpisode(bvid = "BV-two", cid = 30L, title = "下一集")))
        )))
        assertEquals("BV-two", resolveCollectionNextWatch(info)?.bvid)
        assertNull(resolveCollectionNextWatch(info.copy(bvid = "BV-missing")))
        val withParts = info.copy(pages = listOf(Page(cid = 10L), Page(cid = 20L)))
        assertEquals(20L, resolveCollectionNextWatch(withParts)?.cid)
        assertEquals("BV-one", resolveCollectionNextWatch(withParts)?.bvid)
        assertEquals("BV-one", resolvePageWatchTarget(withParts)?.bvid)
        assertEquals(20L, resolvePageWatchTarget(withParts)?.cid)
        assertEquals("BV-two", resolveSeasonWatchTarget(withParts)?.bvid)
        assertEquals(30L, resolveSeasonWatchTarget(withParts)?.cid)
    }

    @Test fun collectionUsesPublishOrderWhenServerReturnsNewestFirst() {
        val first = UgcEpisode(bvid = "BV-first", cid = 10L, arc = UgcEpisodeArc(pubdate = 100L))
        val second = UgcEpisode(bvid = "BV-second", cid = 20L, arc = UgcEpisodeArc(ctime = 200L))
        val third = UgcEpisode(
            bvid = "BV-third", cid = 30L, arc = UgcEpisodeArc(pubdate = 300L, ctime = 50L)
        )
        val info = ViewInfo(
            bvid = second.bvid,
            cid = second.cid,
            ugc_season = UgcSeason(sections = listOf(
                UgcSection(episodes = listOf(third)),
                UgcSection(episodes = listOf(second, first)),
            )),
        )

        assertEquals(third.bvid, resolveCollectionNextWatch(info)?.bvid)
        assertEquals(first.bvid, resolveSeasonWatchTarget(info, forward = false)?.bvid)
        val finalEpisode = info.copy(bvid = third.bvid, cid = third.cid)
        assertNull(resolveCollectionNextWatch(finalEpisode))
        assertEquals(first.bvid, resolveCollectionNextWatch(finalEpisode, loopAtEnd = true)?.bvid)
    }

    @Test fun reversedPartsChangeActualLookaheadAndRemainIndependentOfSeasonOrder() {
        val info = ViewInfo(bvid = "BV-reverse-parts", cid = 20L,
            pages = listOf(Page(cid = 10L), Page(cid = 20L)),
            ugc_season = UgcSeason(id = 77L, sections = listOf(UgcSection(episodes = listOf(
                UgcEpisode(bvid = "BV-reverse-parts", cid = 10L),
                UgcEpisode(bvid = "BV-next-episode", cid = 30L),
            )))))
        PiliPlaybackOrder.setReversed(77L, info.bvid, false)
        PiliPlaybackOrder.setReversed(0L, info.bvid, true)
        try {
            assertEquals(10L, resolveCollectionNextWatch(info)?.cid)
            assertEquals(0, resolveCollectionNextWatch(info)?.pageIndex)
            assertEquals("BV-next-episode", resolveCollectionNextWatch(info.copy(cid = 10L))?.bvid)
        } finally {
            PiliPlaybackOrder.setReversed(0L, info.bvid, false)
        }
    }

    @Test fun reversedCollectionMovesToPreviousEpisodeAcrossSections() {
        val info = ViewInfo(bvid = "BV-reverse-two", cid = 20L,
            ugc_season = UgcSeason(id = 88L, sections = listOf(
                UgcSection(episodes = listOf(UgcEpisode(bvid = "BV-reverse-one", cid = 10L))),
                UgcSection(episodes = listOf(UgcEpisode(bvid = "BV-reverse-two", cid = 20L)))
            )))
        PiliPlaybackOrder.setReversed(88L, info.bvid, true)
        try {
            assertEquals("BV-reverse-one", resolveCollectionNextWatch(info)?.bvid)
            assertEquals(10L, resolveCollectionNextWatch(info)?.cid)
        } finally {
            PiliPlaybackOrder.setReversed(88L, info.bvid, false)
        }
    }

    @Test fun previousPartUsesTheOppositeDirectionWithoutWrappingAtPartBoundaries() {
        val info = ViewInfo(bvid = "BV-previous-parts", cid = 20L,
            pages = listOf(Page(cid = 10L), Page(cid = 20L), Page(cid = 30L)))
        assertEquals(10L, resolvePageWatchTarget(info, forward = false)?.cid)
        assertNull(resolvePageWatchTarget(info.copy(cid = 10L), forward = false))
        PiliPlaybackOrder.setReversed(0L, info.bvid, true)
        try {
            assertEquals(30L, resolvePageWatchTarget(info, forward = false)?.cid)
            assertEquals(10L, resolvePageWatchTarget(info)?.cid)
            assertNull(resolvePageWatchTarget(info.copy(cid = 10L)))
            assertNull(resolvePageWatchTarget(info.copy(cid = 30L), forward = false))
        } finally {
            PiliPlaybackOrder.setReversed(0L, info.bvid, false)
        }
    }

}
