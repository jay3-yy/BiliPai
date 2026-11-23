package com.android.purebilibili.feature.video.ui.overlay

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PiliPlayerSleepTimerPolicyTest {

    @Test
    fun `natural end finishes wait`() {
        assertTrue(
            shouldFinishWaitingForCurrentVideoEnd(
                playbackState = Player.STATE_ENDED,
                hasError = false,
            )
        )
    }

    @Test
    fun `playback error finishes wait`() {
        assertTrue(
            shouldFinishWaitingForCurrentVideoEnd(
                playbackState = Player.STATE_READY,
                hasError = true,
            )
        )
    }

    @Test
    fun `paused or playing does not finish wait`() {
        assertFalse(
            shouldFinishWaitingForCurrentVideoEnd(
                playbackState = Player.STATE_READY,
                hasError = false,
            )
        )
        assertFalse(
            shouldFinishWaitingForCurrentVideoEnd(
                playbackState = Player.STATE_BUFFERING,
                hasError = false,
            )
        )
        assertFalse(
            shouldFinishWaitingForCurrentVideoEnd(
                playbackState = Player.STATE_IDLE,
                hasError = false,
            )
        )
    }
}
