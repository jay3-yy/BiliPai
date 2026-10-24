package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.transition.VideoCardTransitionExposure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailSettledPlayerBoundsTest {
    @Test
    fun cardMotionFreezesBoundsUntilEntryOrCancelledGestureHasSettled() {
        for (exposure in VideoCardTransitionExposure.entries) {
            assertEquals(
                exposure != VideoCardTransitionExposure.Idle &&
                    exposure != VideoCardTransitionExposure.SettledHidden,
                shouldFreezeVideoDetailPlayerBounds(true, false, exposure),
                "exposure=$exposure",
            )
        }
    }

    @Test
    fun committedOutgoingEntryCannotPublishCardCoordinatesAtIdleEndpoint() {
        for (exposure in VideoCardTransitionExposure.entries) {
            assertTrue(shouldFreezeVideoDetailPlayerBounds(true, true, exposure))
            // Session cleanup may revoke ownership before the outgoing entry is disposed.
            assertTrue(shouldFreezeVideoDetailPlayerBounds(false, true, exposure))
        }
    }

    @Test
    fun unrelatedParentAndNonCardPlaybackKeepTheirNormalBoundsUpdates() {
        for (exposure in VideoCardTransitionExposure.entries) {
            assertFalse(shouldFreezeVideoDetailPlayerBounds(false, false, exposure))
        }
    }
}
