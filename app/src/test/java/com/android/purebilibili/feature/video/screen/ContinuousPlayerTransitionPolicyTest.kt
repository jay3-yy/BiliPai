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
    fun exitingFullscreenWaitsForPortraitBeforeCollapsing() {
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

        val collapsing = reduceContinuousPlayerTransition(
            phase = awaitingPortrait.phase,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
        )
        assertEquals(ContinuousPlayerTransitionPhase.Collapsing, collapsing.phase)
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
    fun backDuringPendingEnterCancelsAndConsumesRepeatedBackUntilInline() {
        val entering = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Inline,
            event = ContinuousPlayerTransitionEvent.Toggle,
        )
        assertEquals(
            VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = false,
                isPortraitFullscreen = false,
                continuousPlayerPhase = entering.phase,
            ),
        )

        val cancelled = reduceContinuousPlayerTransition(
            phase = entering.phase,
            event = ContinuousPlayerTransitionEvent.ExitFullscreen,
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, cancelled.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Portrait, cancelled.orientationRequest)
        assertEquals(
            cancelled,
            reduceContinuousPlayerTransition(
                phase = cancelled.phase,
                event = ContinuousPlayerTransitionEvent.ExitFullscreen,
            ),
        )
        val lateLandscape = reduceContinuousPlayerTransition(
            phase = cancelled.phase,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = true),
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, lateLandscape.phase)

        val collapsing = reduceContinuousPlayerTransition(
            phase = lateLandscape.phase,
            event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
        )
        assertEquals(ContinuousPlayerTransitionPhase.Collapsing, collapsing.phase)
        val repeatedBack = reduceContinuousPlayerTransition(
            phase = collapsing.phase,
            event = ContinuousPlayerTransitionEvent.ExitFullscreen,
        )
        assertEquals(ContinuousPlayerTransitionPhase.Collapsing, repeatedBack.phase)
        assertEquals(ContinuousPlayerOrientationRequest.None, repeatedBack.orientationRequest)

        val inline = reduceContinuousPlayerTransition(
            phase = repeatedBack.phase,
            event = ContinuousPlayerTransitionEvent.CollapseFinished,
        )
        assertEquals(ContinuousPlayerTransitionPhase.Inline, inline.phase)
        assertEquals(
            VideoDetailLocalBackTarget.NAVIGATE_BACK,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = false,
                isPortraitFullscreen = false,
                continuousPlayerPhase = inline.phase,
            ),
        )
    }

    @Test
    fun backFromButtonOrAutoRotatedFullscreenRequestsPortraitAndFinishesCollapse() {
        for (phaseBeforeLandscape in listOf(
            ContinuousPlayerTransitionPhase.AwaitingLandscape,
            ContinuousPlayerTransitionPhase.Inline,
        )) {
            val fullscreen = reduceContinuousPlayerTransition(
                phase = phaseBeforeLandscape,
                event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = true),
            )
            assertEquals(ContinuousPlayerTransitionPhase.Fullscreen, fullscreen.phase)
            assertEquals(
                VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN,
                resolveVideoDetailLocalBackTarget(
                    isLandscapeFullscreen = true,
                    isPortraitFullscreen = false,
                    continuousPlayerPhase = fullscreen.phase,
                ),
            )
            val exiting = reduceContinuousPlayerTransition(
                phase = fullscreen.phase,
                event = ContinuousPlayerTransitionEvent.ExitFullscreen,
            )
            assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, exiting.phase)
            assertEquals(ContinuousPlayerOrientationRequest.Portrait, exiting.orientationRequest)
            val collapsing = reduceContinuousPlayerTransition(
                phase = exiting.phase,
                event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
            )
            assertEquals(ContinuousPlayerTransitionPhase.Collapsing, collapsing.phase)
            assertEquals(
                ContinuousPlayerTransitionPhase.Inline,
                reduceContinuousPlayerTransition(
                    phase = collapsing.phase,
                    event = ContinuousPlayerTransitionEvent.CollapseFinished,
                ).phase,
            )
        }
    }

    @Test
    fun backBeforeFullscreenOrientationObservationStillRequestsPortrait() {
        val decision = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Inline,
            event = ContinuousPlayerTransitionEvent.ExitFullscreen,
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, decision.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Portrait, decision.orientationRequest)
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
    fun systemPortraitFromFullscreenOrStuckPhasesStartsCollapse() {
        assertEquals(
            ContinuousPlayerTransitionPhase.Collapsing,
            reduceContinuousPlayerTransition(
                phase = ContinuousPlayerTransitionPhase.Fullscreen,
                event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
            ).phase,
        )
        assertEquals(
            ContinuousPlayerTransitionPhase.Collapsing,
            reduceContinuousPlayerTransition(
                phase = ContinuousPlayerTransitionPhase.AwaitingLandscape,
                event = ContinuousPlayerTransitionEvent.OrientationChanged(isLandscape = false),
            ).phase,
        )
    }

    @Test
    fun reenteringDuringCollapseRequestsLandscapeWithoutWaitingForAnimation() {
        val result = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Collapsing,
            event = ContinuousPlayerTransitionEvent.Toggle,
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingLandscape, result.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Landscape, result.orientationRequest)

        val lateCollapse = reduceContinuousPlayerTransition(
            phase = result.phase,
            event = ContinuousPlayerTransitionEvent.CollapseFinished,
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingLandscape, lateCollapse.phase)
        assertEquals(ContinuousPlayerOrientationRequest.None, lateCollapse.orientationRequest)
    }

    @Test
    fun landscapeToPortraitThenBackReturnsInlineAndCanEnterAgain() {
        val portrait = reduceContinuousPlayerTransition(
            phase = ContinuousPlayerTransitionPhase.Fullscreen,
            event = ContinuousPlayerTransitionEvent.EnterPortraitFullscreen,
        )
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingPortrait, portrait.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Portrait, portrait.orientationRequest)
        // The old landscape Configuration may arrive after the portrait button click.
        val staleLandscape = reduceContinuousPlayerTransition(
            portrait.phase,
            ContinuousPlayerTransitionEvent.OrientationChanged(true),
        )
        assertEquals(portrait.phase, staleLandscape.phase)
        val collapsed = reduceContinuousPlayerTransition(
            staleLandscape.phase,
            ContinuousPlayerTransitionEvent.OrientationChanged(false),
        )
        val inline = reduceContinuousPlayerTransition(
            collapsed.phase,
            ContinuousPlayerTransitionEvent.CollapseFinished,
        )
        assertEquals(ContinuousPlayerTransitionPhase.Inline, inline.phase)
        val reenter = reduceContinuousPlayerTransition(inline.phase, ContinuousPlayerTransitionEvent.Toggle)
        assertEquals(ContinuousPlayerTransitionPhase.AwaitingLandscape, reenter.phase)
        assertEquals(ContinuousPlayerOrientationRequest.Landscape, reenter.orientationRequest)
    }

    @Test
    fun portraitLandscapeButtonEntersInsteadOfTogglingThePreviousLandscapePhase() {
        for (oldPhase in ContinuousPlayerTransitionPhase.entries) {
            val enter = reduceContinuousPlayerTransition(
                oldPhase,
                ContinuousPlayerTransitionEvent.EnterFullscreen,
            )
            assertEquals(ContinuousPlayerTransitionPhase.AwaitingLandscape, enter.phase)
            assertEquals(ContinuousPlayerOrientationRequest.Landscape, enter.orientationRequest)
            val landscape = reduceContinuousPlayerTransition(
                enter.phase,
                ContinuousPlayerTransitionEvent.OrientationChanged(true),
            )
            assertEquals(ContinuousPlayerTransitionPhase.Fullscreen, landscape.phase)
            val exit = reduceContinuousPlayerTransition(
                landscape.phase,
                ContinuousPlayerTransitionEvent.Toggle,
            )
            assertEquals(ContinuousPlayerOrientationRequest.Portrait, exit.orientationRequest)
        }
    }
}
