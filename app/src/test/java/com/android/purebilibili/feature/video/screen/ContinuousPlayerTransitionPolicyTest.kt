package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuousPlayerTransitionPolicyTest {

    @Test
    fun enteringFullscreenRequestsLandscapeImmediately() {
        val awaitingLandscape = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Inline,
            event = ContinuousPlayerTransitionEvent.Toggle,
        )
        assertEquals(
            ContinuousPlayerTransitionPhase.AwaitingLandscape,
            awaitingLandscape.phase,
        )
        assertEquals(
            ContinuousPlayerOrientationRequest.Landscape,
            awaitingLandscape.orientationRequest,
        )
    }

    @Test
    fun portraitObservationDoesNotCancelButtonEnterBeforeLandscapeRequestCompletes() {
        assertTrue(
            shouldKeepContinuousPlayerEnterPhaseWhilePortrait(
                phase = ContinuousPlayerTransitionPhase.AwaitingLandscape,
                isLandscape = false,
            )
        )

        assertFalse(
            shouldKeepContinuousPlayerEnterPhaseWhilePortrait(
                phase = ContinuousPlayerTransitionPhase.Fullscreen,
                isLandscape = false,
            )
        )
        assertFalse(
            shouldKeepContinuousPlayerEnterPhaseWhilePortrait(
                phase = ContinuousPlayerTransitionPhase.AwaitingLandscape,
                isLandscape = true,
            )
        )
    }

    @Test
    fun systemLandscapeCompletesFullscreenWithoutRestartingAnimation() {
        val result = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.AwaitingLandscape,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = true),
        )

        assertEquals(ContinuousPlayerTransitionPhase.Fullscreen, result.phase)
        assertEquals(ContinuousPlayerOrientationRequest.None, result.orientationRequest)
    }

    @Test
    fun exitingKeepsLandscapeHostUntilFirstPortraitLayoutThenReturnsInline() {
        val awaitingPortrait = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Fullscreen,
            event = ContinuousPlayerTransitionEvent.Toggle,
        )
        assertEquals(
            ContinuousPlayerTransitionPhase.AwaitingPortrait,
            awaitingPortrait.phase,
        )
        assertEquals(
            ContinuousPlayerOrientationRequest.Portrait,
            awaitingPortrait.orientationRequest,
        )
        assertTrue(
            resolveContinuousPlayerFullscreenPresentation(
                orientationFullscreen = false,
                phase = awaitingPortrait.phase,
                isLandscape = true,
            )
        )
        // Configuration can update before the reducer effect. Do not render a portrait
        // fullscreen shell for even that first frame.
        assertFalse(
            resolveContinuousPlayerFullscreenPresentation(
                orientationFullscreen = false,
                phase = awaitingPortrait.phase,
                isLandscape = false,
            )
        )

        val inline = reduceContinuousPlayerTransition(
            phase = awaitingPortrait.phase,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
        )
        assertEquals(ContinuousPlayerTransitionPhase.Inline, inline.phase)
    }

    @Test
    fun togglingBeforeLandscapeArrivesCancelsTheOrientationRequest() {
        val result = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.AwaitingLandscape,
            event = ContinuousPlayerTransitionEvent.Toggle,
        )

        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, result.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Portrait, result.orientationRequest)

        val lateLandscape = reduceContinuousPlayerTransition(
            phase = result.phase,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = true),
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, lateLandscape.phase)
    }

    @Test
    fun systemLandscapeFromInlineSettlesFullscreen() {
        val result = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Inline,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = true),
        )
        assertEquals(ContinuousPlayerTransitionPhase.Fullscreen, result.phase)
    }

    @Test
    fun systemPortraitFromFullscreenOrStuckPhasesReturnsInline() {
        assertEquals(
            ContinuousPlayerTransitionPhase.Inline,
            reduceContinuousPlayerTransition(
                phase = ContinuousPlayerTransitionPhase.Fullscreen,
                event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
            ).phase,
        )
        assertEquals(
            ContinuousPlayerTransitionPhase.Inline,
            reduceContinuousPlayerTransition(
                phase = ContinuousPlayerTransitionPhase.AwaitingLandscape,
                event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
            ).phase,
        )
    }

}
