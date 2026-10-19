package com.android.purebilibili.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAnalyticsTrackerTest {
    @Test
    fun loadingAndPausedPreviewDoNotCountAsPlayback() {
        val tracker = PlaybackAnalyticsTracker()
        assertFalse(tracker.sample("p1", 0, 100_000, false, 0).started)
        assertTrue(tracker.sample("p1", 0, 100_000, true, 1000).started)
        assertFalse(tracker.sample("p1", 0, 100_000, true, 2000).started)
    }

    @Test
    fun crossingMilestoneDoesNotRequireExactPercentageAndNeverRepeats() {
        val tracker = PlaybackAnalyticsTracker()
        tracker.sample("p1", 24_900, 100_000, true, 0)
        assertEquals(listOf(25), tracker.sample("p1", 25_650, 100_000, true, 750).checkpoints)
        tracker.sample("p1", 24_900, 100_000, true, 1000, discontinuity = true)
        assertTrue(tracker.sample("p1", 26_000, 100_000, true, 2000).checkpoints.isEmpty())
    }

    @Test
    fun seekDoesNotBackfillSkippedMilestonesOrCompleteImmediately() {
        val tracker = PlaybackAnalyticsTracker()
        tracker.sample("p1", 0, 100_000, true, 0)
        tracker.sample("p1", 1000, 100_000, true, 1000)
        val seek = tracker.sample("p1", 100_000, 100_000, false, 1100, discontinuity = true)
        assertTrue(seek.checkpoints.isEmpty())
        assertFalse(tracker.sample("p1", 100_000, 100_000, false, 1200, ended = true).completed)
    }

    @Test
    fun shortVideoCompletesOnceEvenBeforeNextPollingTick() {
        val tracker = PlaybackAnalyticsTracker()
        tracker.sample("short", 0, 2000, true, 0)
        val end = tracker.sample("short", 2000, 2000, false, 2000, ended = true)
        assertEquals(listOf(25, 50, 75, 100), end.checkpoints)
        assertTrue(end.completed)
        assertEquals(2L, end.watchTimeSeconds)
        assertFalse(tracker.sample("short", 2000, 2000, false, 2100, ended = true).completed)
    }

    @Test
    fun pauseAndBufferingAreExcludedFromActualWatchTime() {
        val tracker = PlaybackAnalyticsTracker()
        tracker.sample("p1", 0, 100_000, true, 0)
        tracker.sample("p1", 2000, 100_000, false, 2000)
        tracker.sample("p1", 2000, 100_000, true, 12_000)
        val result = tracker.sample("p1", 3000, 100_000, false, 13_000)
        assertEquals(3L, result.watchTimeSeconds)
    }

    @Test
    fun switchingPartResetsWatchTimeAndMilestones() {
        val tracker = PlaybackAnalyticsTracker()
        tracker.sample("video:p1", 0, 100_000, true, 0)
        tracker.sample("video:p1", 26_000, 100_000, true, 26_000)
        val next = tracker.sample("video:p2", 0, 100_000, true, 30_000)
        assertTrue(next.started)
        assertEquals(0L, next.watchTimeSeconds)
        assertEquals(listOf(25), tracker.sample("video:p2", 26_000, 100_000, true, 56_000).checkpoints)
    }

    @Test
    fun repeatTransitionCanCompleteOldPositionWithoutCountingAgain() {
        val tracker = PlaybackAnalyticsTracker()
        tracker.sample("p1", 0, 2000, true, 0)
        assertTrue(tracker.sample("p1", 2000, 2000, true, 2000, ended = true).completed)
        tracker.sample("p1", 0, 2000, true, 2000, discontinuity = true)
        val repeatEnd = tracker.sample("p1", 2000, 2000, true, 4000, ended = true)
        assertFalse(repeatEnd.started)
        assertFalse(repeatEnd.completed)
        assertTrue(repeatEnd.checkpoints.isEmpty())
    }

    @Test
    fun resetAndUnknownDurationDoNotInventCompletion() {
        val tracker = PlaybackAnalyticsTracker()
        assertFalse(tracker.sample("p1", 0, -1, true, 0).started)
        tracker.reset()
        assertFalse(tracker.sample("p1", 100_000, 100_000, false, 1000, ended = true).completed)
        assertTrue(tracker.sample("p1", 0, 100_000, true, 2000).started)
    }
}
