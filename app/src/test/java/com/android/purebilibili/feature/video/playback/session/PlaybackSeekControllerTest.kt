package com.android.purebilibili.feature.video.playback.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackSeekControllerTest {

    @Test
    fun syncFromPlayback_initializesSliderPositionWhenIdle() {
        val state = syncPlaybackSeekSession(
            state = PlaybackSeekSessionState(),
            playbackPositionMs = 12_000L
        )

        assertEquals(12_000L, state.playbackPositionMs)
        assertEquals(12_000L, state.sliderPositionMs)
        assertEquals(12_000L, state.sliderTempPositionMs)
        assertFalse(state.isSliderMoving)
    }

    @Test
    fun syncFromPlayback_doesNotOverrideSliderWhileUserIsDragging() {
        val draggingState = updatePlaybackSeekInteraction(
            state = startPlaybackSeekInteraction(
                state = syncPlaybackSeekSession(
                    state = PlaybackSeekSessionState(),
                    playbackPositionMs = 10_000L
                )
            ),
            positionMs = 24_000L
        )

        val synced = syncPlaybackSeekSession(
            state = draggingState,
            playbackPositionMs = 11_000L
        )

        assertEquals(11_000L, synced.playbackPositionMs)
        assertEquals(24_000L, synced.sliderPositionMs)
        assertEquals(24_000L, synced.sliderTempPositionMs)
        assertTrue(synced.isSliderMoving)
    }

    @Test
    fun finishSeek_keepsCommittedSliderUntilPlaybackCatchesUp() {
        val draggingState = updatePlaybackSeekInteraction(
            state = startPlaybackSeekInteraction(
                state = syncPlaybackSeekSession(
                    state = PlaybackSeekSessionState(),
                    playbackPositionMs = 10_000L
                )
            ),
            positionMs = 25_000L
        )

        val result = finishPlaybackSeekInteraction(draggingState)
        val staleSync = syncPlaybackSeekSession(
            state = result.state,
            playbackPositionMs = 1_000L
        )
        val settledSync = syncPlaybackSeekSession(
            state = staleSync,
            playbackPositionMs = 24_700L
        )

        assertEquals(25_000L, result.committedPositionMs)
        assertEquals(25_000L, staleSync.sliderPositionMs)
        assertEquals(25_000L, staleSync.pendingSeekPositionMs)
        assertEquals(24_700L, settledSync.sliderPositionMs)
        assertNull(settledSync.pendingSeekPositionMs)
    }

    @Test
    fun cancelSeek_restoresLastPlaybackPosition() {
        val draggingState = updatePlaybackSeekInteraction(
            state = startPlaybackSeekInteraction(
                state = syncPlaybackSeekSession(
                    state = PlaybackSeekSessionState(),
                    playbackPositionMs = 8_000L
                )
            ),
            positionMs = 30_000L
        )

        val cancelled = cancelPlaybackSeekInteraction(draggingState)

        assertFalse(cancelled.isSliderMoving)
        assertEquals(8_000L, cancelled.playbackPositionMs)
        assertEquals(8_000L, cancelled.sliderPositionMs)
        assertEquals(8_000L, cancelled.sliderTempPositionMs)
        assertNull(cancelled.pendingSeekPositionMs)
    }

    @Test
    fun finishSeek_preservesResumeIntentCapturedAtInteractionStart() {
        val draggingState = updatePlaybackSeekInteraction(
            state = startPlaybackSeekInteraction(
                state = syncPlaybackSeekSession(
                    state = PlaybackSeekSessionState(),
                    playbackPositionMs = 8_000L
                ),
                shouldResumePlayback = true
            ),
            positionMs = 30_000L
        )

        val result = finishPlaybackSeekInteraction(draggingState)

        assertTrue(result.shouldResumePlayback)
        assertEquals(true, result.state.shouldResumePlayback)
    }

    @Test
    fun startScrubbing_entersScrubbingStateWithClampedProgress() {
        val state = startPlaybackSeekSession(progress = 1.4f)

        assertTrue(state.isScrubbing)
        assertEquals(1f, state.dragProgress)
        assertNull(state.pendingSettledProgress)
    }

    @Test
    fun finishScrubbing_commitsCurrentDragProgressAndKeepsSettledTarget() {
        val state = updatePlaybackSeekSession(
            state = startPlaybackSeekSession(progress = 0.2f),
            progress = 0.72f
        )

        val result = finishPlaybackSeekSession(state)

        assertFalse(result.state.isScrubbing)
        assertEquals(0.72f, result.committedProgress)
        assertEquals(0.72f, result.state.pendingSettledProgress)
    }

    @Test
    fun cancelScrubbing_clearsPendingAndReturnsToIdle() {
        val state = cancelPlaybackSeekSession(
            state = PlaybackSeekUiState(
                isScrubbing = true,
                dragProgress = 0.55f,
                pendingSettledProgress = 0.8f
            )
        )

        assertFalse(state.isScrubbing)
        assertNull(state.pendingSettledProgress)
        assertEquals(0.55f, state.dragProgress)
    }

    @Test
    fun settleAgainstPlaybackProgress_clearsPendingWhenPlaybackCatchesUp() {
        val state = settlePlaybackSeekSession(
            state = PlaybackSeekUiState(
                isScrubbing = false,
                dragProgress = 0.72f,
                pendingSettledProgress = 0.72f
            ),
            playbackProgress = 0.719f
        )

        assertNull(state.pendingSettledProgress)
    }

    @Test
    fun resolveDisplayProgress_prefersSettledProgressUntilPlaybackCatchesUp() {
        val display = resolvePlaybackSeekDisplayProgress(
            playbackProgress = 0.15f,
            state = PlaybackSeekUiState(
                isScrubbing = false,
                dragProgress = 0.72f,
                pendingSettledProgress = 0.72f
            )
        )

        assertEquals(0.72f, display)
    }
}
