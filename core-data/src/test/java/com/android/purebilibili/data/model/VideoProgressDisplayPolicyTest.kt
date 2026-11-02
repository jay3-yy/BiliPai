package com.android.purebilibili.data.model

import com.android.purebilibili.data.model.response.VideoItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoProgressDisplayPolicyTest {
    @Test
    fun `default negative progress requires history metadata to mean completed`() {
        val unknown = resolveVideoDisplayProgressState(-1, 100, viewAt = 0)
        assertEquals(0, unknown.progressSec)
        assertFalse(unknown.showProgressBar)
        val completed = resolveVideoDisplayProgressState(-1, 100, viewAt = 1)
        assertEquals(-1, completed.progressSec)
        assertEquals(1f, completed.progressFraction)
        assertTrue(completed.showProgressBar)

        val later = resolveWatchLaterDisplayProgressState(VideoItem(duration = 100, progress = -1, view_at = 1))
        assertEquals(0, later.progressSec)
        assertFalse(later.showProgressBar)
    }

    @Test
    fun `history and watch later retain their different completion boundaries`() {
        assertEquals(94, resolveVideoDisplayProgressState(94, 100, viewAt = 1).progressSec)
        assertEquals(-1, resolveVideoDisplayProgressState(95, 100, viewAt = 1).progressSec)
        assertEquals(95, resolveWatchLaterDisplayProgressState(VideoItem(duration = 100, progress = 95)).progressSec)
        val completed = resolveWatchLaterDisplayProgressState(VideoItem(duration = 100, progress = 105))
        assertEquals(-1, completed.progressSec)
        assertEquals(1f, completed.progressFraction)
        assertTrue(completed.showProgressBar)
    }

    @Test
    fun `missing duration keeps known position without fabricating a progress bar`() {
        val history = resolveVideoDisplayProgressState(30, 0, viewAt = 1)
        val later = resolveWatchLaterDisplayProgressState(VideoItem(duration = 0, progress = 30))
        for (state in listOf(history, later)) {
            assertEquals(30, state.progressSec)
            assertEquals(0f, state.progressFraction)
            assertFalse(state.showProgressBar)
        }
        assertFalse(isWatchLaterViewed(VideoItem(duration = 0, progress = 30)))
    }

    @Test
    fun `cloud history retains backward seeks zero and completion over stale local progress`() {
        val local = resolveVideoDisplayProgressState(20, 100, localPositionMs = 30_000, viewAt = 1)
        assertEquals(20, local.progressSec)
        assertEquals(0.2f, local.progressFraction)
        assertEquals(-1, resolveVideoDisplayProgressState(-1, 100, localPositionMs = 30_000, viewAt = 1).progressSec)
        assertEquals(20, resolveVideoDisplayProgressState(20, 100, localPositionMs = 95_000, viewAt = 1).progressSec)
        assertEquals(0, resolveVideoDisplayProgressState(0, 100, localPositionMs = 95_000, viewAt = 1).progressSec)
        assertEquals(30, resolveVideoDisplayProgressState(-1, 100, localPositionMs = 30_000, viewAt = 0).progressSec)
    }
}
