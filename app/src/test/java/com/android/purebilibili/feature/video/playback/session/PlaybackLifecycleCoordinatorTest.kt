package com.android.purebilibili.feature.video.playback.session

import androidx.lifecycle.Lifecycle.Event
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackLifecycleCoordinatorTest {

    @Test
    fun splitScreenPlaybackFollowsVisibilityWithoutRequiringFocus() {
        val lifecycle = PlaybackWindowLifecycle()

        assertNull(lifecycle.playbackEvent(Event.ON_PAUSE, isInMultiWindow = true))
        assertEquals(Event.ON_PAUSE, lifecycle.playbackEvent(Event.ON_STOP, isInMultiWindow = false))
        assertEquals(Event.ON_RESUME, lifecycle.playbackEvent(Event.ON_START, isInMultiWindow = true))
        assertEquals(Event.ON_PAUSE, lifecycle.playbackEvent(Event.ON_STOP, isInMultiWindow = false))
    }

    @Test
    fun splitScreenWindowCanStopBeforeItsFirstResume() {
        val lifecycle = PlaybackWindowLifecycle()

        assertEquals(Event.ON_START, lifecycle.playbackEvent(Event.ON_START, isInMultiWindow = true))
        assertEquals(Event.ON_PAUSE, lifecycle.playbackEvent(Event.ON_STOP, isInMultiWindow = true))
        assertEquals(Event.ON_RESUME, lifecycle.playbackEvent(Event.ON_START, isInMultiWindow = true))
    }

    @Test
    fun regainingSplitScreenFocusClearsDeferredPause() {
        val lifecycle = PlaybackWindowLifecycle()

        assertNull(lifecycle.playbackEvent(Event.ON_PAUSE, isInMultiWindow = true))
        assertEquals(Event.ON_RESUME, lifecycle.playbackEvent(Event.ON_RESUME, isInMultiWindow = true))
        assertEquals(Event.ON_PAUSE, lifecycle.playbackEvent(Event.ON_PAUSE, isInMultiWindow = false))
        assertEquals(Event.ON_STOP, lifecycle.playbackEvent(Event.ON_STOP, isInMultiWindow = false))
    }

    @Test
    fun returnIntent_keepsManualPauseAcrossSuspensionAndPlayerRecreation() {
        val state = PlaybackReturnState()
        assertFalse(state.suspend("BVpaused", 1L, false, PlaybackPauseOwner.NAVIGATION))
        state.recordPlaybackIntent("BVpaused", 1L, false)
        state.suspend("BVpaused", 1L, false, PlaybackPauseOwner.LIFECYCLE)
        assertFalse(state.resolveAutoPlay("BVpaused", 1L, initialPreference = true))
        state.resume(PlaybackPauseOwner.NAVIGATION)
        state.resume(PlaybackPauseOwner.LIFECYCLE)
        assertFalse(state.resolveAutoPlay("BVpaused", 0L, initialPreference = true))
    }

    @Test
    fun returnIntent_doesNotConfuseAutomaticPauseWithUserPause() {
        val state = PlaybackReturnState()
        assertTrue(state.suspend("BVplaying", 1L, true, PlaybackPauseOwner.LIFECYCLE))
        state.recordPlaybackIntent("BVplaying", 1L, false)
        assertTrue(state.suspend("BVplaying", 1L, false, PlaybackPauseOwner.NAVIGATION))
        assertTrue(state.resolveAutoPlay("BVplaying", 1L, initialPreference = false))
        state.resume(PlaybackPauseOwner.LIFECYCLE)
        state.resume(PlaybackPauseOwner.NAVIGATION)
        state.recordPlaybackIntent("BVplaying", 1L, false)
        assertFalse(state.suspend("BVplaying", 1L, false, PlaybackPauseOwner.NAVIGATION))
        assertFalse(state.resolveAutoPlay("BVplaying", 1L, initialPreference = true))
    }

    @Test
    fun returnIntent_refreshesAfterEachResumeInsteadOfReusingOriginalEntryIntent() {
        val state = PlaybackReturnState()
        state.suspend("BVplaying", 1L, false, PlaybackPauseOwner.NAVIGATION)
        state.resume(PlaybackPauseOwner.NAVIGATION)
        state.recordPlaybackIntent("BVplaying", 1L, true)
        state.suspend("BVplaying", 1L, true, PlaybackPauseOwner.NAVIGATION)
        state.recordPlaybackIntent("BVplaying", 1L, false)
        state.resume(PlaybackPauseOwner.NAVIGATION)
        assertTrue(state.resolveAutoPlay("BVplaying", 1L, initialPreference = false))
    }

    @Test
    fun returnIntent_doesNotLeakIntoAnotherVideoOrPart() {
        val state = PlaybackReturnState()
        state.suspend("BVparent", 1L, false, PlaybackPauseOwner.NAVIGATION)
        assertTrue(state.resolveAutoPlay("BVchild", 1L, initialPreference = true))
        assertTrue(state.resolveAutoPlay("BVparent", 2L, initialPreference = true))
        state.suspend("BVchild", 2L, true, PlaybackPauseOwner.NAVIGATION)
        state.recordPlaybackIntent("BVparent", 1L, false)
        assertTrue(state.resolveAutoPlay("BVchild", 2L, initialPreference = false))
    }

    @Test
    fun returnIntent_lifecycleResumeDoesNotReleaseNavigationPause() {
        val state = PlaybackReturnState()
        state.suspend("BVplaying", 1L, true, PlaybackPauseOwner.NAVIGATION)
        state.suspend("BVplaying", 1L, false, PlaybackPauseOwner.LIFECYCLE)
        state.resume(PlaybackPauseOwner.LIFECYCLE)
        state.recordPlaybackIntent("BVplaying", 1L, false)
        assertTrue(state.suspend("BVplaying", 1L, false, PlaybackPauseOwner.LIFECYCLE))
        state.resume(PlaybackPauseOwner.NAVIGATION)
        state.recordPlaybackIntent("BVplaying", 1L, false)
        assertTrue(state.resolveAutoPlay("BVplaying", 1L, false))
        state.resume(PlaybackPauseOwner.LIFECYCLE)
        state.recordPlaybackIntent("BVplaying", 1L, false)
        assertFalse(state.resolveAutoPlay("BVplaying", 1L, true))
    }

    @Test
    fun pauseDecision_keepsPlayingForPip() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = true,
            isBackgroundAudio = false,
            wasPlaybackActive = false,
            hasRecentUserLeaveHint = true
        )

        assertTrue(decision.shouldContinuePlayback)
        assertFalse(decision.shouldPausePlayback)
    }

    @Test
    fun pauseDecision_keepsSharedPlayerRunningWhileAudioModeCoversVideoDetail() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = false,
            isInAudioMode = true,
            isBackgroundAudio = false,
            wasPlaybackActive = true,
            hasRecentUserLeaveHint = false,
        )

        assertTrue(decision.shouldContinuePlayback)
        assertFalse(decision.shouldPausePlayback)
        assertFalse(decision.shouldPersistTransientResumeIntent)
    }

    @Test
    fun pauseDecision_marksBackgroundAudioOnlyWhenUserActuallyLeftApp() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = false,
            isBackgroundAudio = true,
            wasPlaybackActive = true,
            hasRecentUserLeaveHint = true
        )

        assertTrue(decision.shouldContinuePlayback)
        assertTrue(decision.shouldMarkBackgroundAudioSession)
    }

    @Test
    fun pauseDecision_keepsBackgroundAudioEvenWhenPlaybackSnapshotTemporarilyLooksInactive() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = false,
            isBackgroundAudio = true,
            wasPlaybackActive = false,
            hasRecentUserLeaveHint = true
        )

        assertTrue(decision.shouldContinuePlayback)
        assertFalse(decision.shouldPausePlayback)
        assertTrue(decision.shouldMarkBackgroundAudioSession)
    }

    @Test
    fun pauseDecision_marksTransientResumeIntent_whenActivePlaybackWillBePaused() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = false,
            isBackgroundAudio = false,
            wasPlaybackActive = true,
            hasRecentUserLeaveHint = true
        )

        assertTrue(decision.shouldPausePlayback)
        assertTrue(decision.shouldPersistTransientResumeIntent)
    }

    @Test
    fun pauseDecision_doesNotMarkTransientResumeIntent_whenPlaybackContinuesInPip() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = true,
            isBackgroundAudio = false,
            wasPlaybackActive = true,
            hasRecentUserLeaveHint = true
        )

        assertFalse(decision.shouldPausePlayback)
        assertFalse(decision.shouldPersistTransientResumeIntent)
    }

    @Test
    fun pauseDecision_doesNotMarkTransientResumeIntent_whenLeavingByNavigation() {
        val decision = resolvePlaybackPauseDecision(
            isMiniMode = false,
            isPip = false,
            isBackgroundAudio = false,
            wasPlaybackActive = true,
            hasRecentUserLeaveHint = false,
            isLeavingByNavigation = true
        )

        assertTrue(decision.shouldPausePlayback)
        assertFalse(decision.shouldPersistTransientResumeIntent)
    }

    @Test
    fun resumeDecision_doesNotResumeWhenNavigationLeaveWasMarked() {
        val decision = resolvePlaybackResumeDecision(
            wasPlaybackActive = true,
            hasTransientResumeIntent = true,
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            currentVolume = 0f,
            shouldEnsureAudibleOnForeground = true,
            isLeavingByNavigation = true
        )

        assertFalse(decision.shouldResumePlayback)
        assertFalse(decision.shouldRestoreVolume)
    }

    @Test
    fun resumeDecision_restoresVolumeWhenForegroundShouldBeAudible() {
        val decision = resolvePlaybackResumeDecision(
            wasPlaybackActive = false,
            hasTransientResumeIntent = false,
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            currentVolume = 0f,
            shouldEnsureAudibleOnForeground = true,
            isLeavingByNavigation = false
        )

        assertFalse(decision.shouldResumePlayback)
        assertTrue(decision.shouldRestoreVolume)
    }

    @Test
    fun resumeDecision_resumesWhenTransientIntentExistsEvenIfPlayerLooksPaused() {
        val decision = resolvePlaybackResumeDecision(
            wasPlaybackActive = false,
            hasTransientResumeIntent = true,
            hasForegroundResumeIntent = false,
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            currentVolume = 1f,
            shouldEnsureAudibleOnForeground = true,
            isLeavingByNavigation = false
        )

        assertTrue(decision.shouldResumePlayback)
        assertFalse(decision.shouldRestoreVolume)
    }

    @Test
    fun resumeDecision_resumesWhenForegroundIntentSurvivesAndroid16PlayWhenReadyReset() {
        val decision = resolvePlaybackResumeDecision(
            wasPlaybackActive = false,
            hasTransientResumeIntent = false,
            hasForegroundResumeIntent = true,
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            currentVolume = 1f,
            shouldEnsureAudibleOnForeground = true,
            isLeavingByNavigation = false
        )

        assertTrue(decision.shouldResumePlayback)
        assertFalse(decision.shouldRestoreVolume)
    }

    @Test
    fun resumeDecision_kicksForegroundPlaybackWhenBufferingButNotActuallyRunning() {
        val decision = resolvePlaybackResumeDecision(
            wasPlaybackActive = false,
            hasTransientResumeIntent = false,
            hasForegroundResumeIntent = false,
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_BUFFERING,
            currentVolume = 1f,
            shouldEnsureAudibleOnForeground = true,
            isLeavingByNavigation = false
        )

        assertTrue(decision.shouldResumePlayback)
        assertFalse(decision.shouldRestoreVolume)
    }

    @Test
    fun resumeDecision_kicksForegroundPlaybackWhenReadyButNotActuallyRunning() {
        val decision = resolvePlaybackResumeDecision(
            wasPlaybackActive = false,
            hasTransientResumeIntent = false,
            hasForegroundResumeIntent = false,
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            currentVolume = 1f,
            shouldEnsureAudibleOnForeground = true,
            isLeavingByNavigation = false
        )

        assertTrue(decision.shouldResumePlayback)
        assertFalse(decision.shouldRestoreVolume)
    }
}
