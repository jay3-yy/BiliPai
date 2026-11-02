package com.android.purebilibili.feature.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoProgressDisplayPolicyTest {

    @Test
    fun `resolveVideoDisplayProgressState prefers cloud history over stale local progress`() {
        val state = resolveVideoDisplayProgressState(
            serverProgressSec = 120,
            durationSec = 600,
            localPositionMs = 180_000L,
            viewAt = 1L
        )

        assertEquals(120, state.progressSec)
        assertEquals(0.2f, state.progressFraction, 0.0001f)
        assertTrue(state.showProgressBar)
    }

    @Test
    fun `resolveVideoDisplayProgressState keeps cloud zero over completed local progress`() {
        val state = resolveVideoDisplayProgressState(
            serverProgressSec = 0,
            durationSec = 1000,
            localPositionMs = 980_000L,
            viewAt = 1L
        )

        assertEquals(0, state.progressSec)
        assertEquals(0f, state.progressFraction, 0.0001f)
        assertFalse(state.showProgressBar)
    }

    @Test
    fun `resolveVideoDisplayProgressState hides bar for zero progress`() {
        val state = resolveVideoDisplayProgressState(
            serverProgressSec = 0,
            durationSec = 600,
            localPositionMs = 0L,
            viewAt = 1L
        )

        assertEquals(0, state.progressSec)
        assertEquals(0f, state.progressFraction, 0.0001f)
        assertFalse(state.showProgressBar)
    }

    @Test
    fun `resolveVideoDisplayProgressState shows bar for non history lists when progress exists`() {
        val state = resolveVideoDisplayProgressState(
            serverProgressSec = 90,
            durationSec = 600,
            localPositionMs = 0L,
            viewAt = 0L
        )

        assertEquals(90, state.progressSec)
        assertEquals(0.15f, state.progressFraction, 0.0001f)
        assertTrue(state.showProgressBar)
    }

    @Test
    fun `resolveVideoDisplayProgressState treats default negative progress without history metadata as unknown`() {
        val state = resolveVideoDisplayProgressState(
            serverProgressSec = -1,
            durationSec = 600,
            localPositionMs = 0L,
            viewAt = 0L
        )

        assertEquals(0, state.progressSec)
        assertEquals(0f, state.progressFraction, 0.0001f)
        assertFalse(state.showProgressBar)
    }
}
