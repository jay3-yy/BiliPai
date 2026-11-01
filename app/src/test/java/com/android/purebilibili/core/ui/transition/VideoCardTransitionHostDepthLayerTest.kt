package com.android.purebilibili.core.ui.transition

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardTransitionHostDepthLayerTest {

    @Test
    fun preparedSourceExcludesHostDrawingInEveryHeldOrReturnPhase() {
        for (exposure in listOf(
            VideoCardTransitionExposure.SettledHidden,
            VideoCardTransitionExposure.BackPreview,
            VideoCardTransitionExposure.Returning,
            VideoCardTransitionExposure.Restoring,
        )) {
            assertFalse(
                shouldPaintHostOwnedDepthLayer(
                    exposure = exposure,
                    hasRecordedContent = true,
                    motionTier = MotionTier.Normal,
                    realtimeBlurEnabled = true,
                    sdkInt = 35,
                    sourceRendererReady = true,
                ),
            )
        }
    }

    @Test
    fun remountedSourceTransfersDepthOwnershipAfterPreparation() {
        val state = VideoCardTransitionSnapshotLayerState()
        val source = Any()
        fun hostPaints() = shouldPaintHostOwnedDepthLayer(
            exposure = VideoCardTransitionExposure.BackPreview,
            hasRecordedContent = true,
            motionTier = MotionTier.Normal,
            realtimeBlurEnabled = true,
            sdkInt = 35,
            sourceRendererReady = state.hasReadySourceRenderer,
        )
        state.attachSourceRenderer(source)
        assertTrue(state.hasAttachedSourceRenderer)
        assertTrue(hostPaints())
        state.markSourceRendererReady(source, true)
        assertFalse(hostPaints())
        state.markSourceRendererReady(source, false)
        assertTrue(hostPaints())
        state.markSourceRendererReady(source, true)
        state.detachSourceRenderer(source)
        assertFalse(state.hasAttachedSourceRenderer)
        assertTrue(hostPaints())
        // A cancelled old effect cannot revive a detached renderer.
        state.markSourceRendererReady(source, true)
        assertFalse(state.hasReadySourceRenderer)
    }

    @Test
    fun disposingOneSourceCannotRevokeAnotherPreparedSource() {
        val state = VideoCardTransitionSnapshotLayerState()
        val oldSource = Any()
        val newSource = Any()
        state.attachSourceRenderer(oldSource)
        state.markSourceRendererReady(oldSource, true)
        state.attachSourceRenderer(newSource)
        state.markSourceRendererReady(newSource, true)
        state.detachSourceRenderer(oldSource)
        assertTrue(state.hasReadySourceRenderer)
        assertTrue(state.hasAttachedSourceRenderer)
        state.detachSourceRenderer(newSource)
        assertFalse(state.hasReadySourceRenderer)
        assertFalse(state.hasAttachedSourceRenderer)
    }

    @Test
    fun unpreparedSourceNeverYieldsToHostWithoutDrawableSnapshot() {
        for (recorded in listOf(false, true)) {
            assertFalse(
                shouldPaintHostOwnedDepthLayer(
                    exposure = VideoCardTransitionExposure.Returning,
                    hasRecordedContent = recorded,
                    displayListStale = true,
                    motionTier = MotionTier.Normal,
                    realtimeBlurEnabled = true,
                    sdkInt = 35,
                    sourceRendererReady = false,
                ),
            )
        }
    }

    @Test
    fun hostLayerPaintsSettledBackPreviewRestoringAndReturning() {
        assertTrue(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.SettledHidden,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertTrue(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.Restoring,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        // BackPreview / Returning：drawable 时 Host 可垫景深；stale 时不画。
        assertTrue(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.BackPreview,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertTrue(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.Returning,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.BackPreview,
                hasRecordedContent = true,
                displayListStale = true,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.Opening,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
    }

    @Test
    fun hostLayerNeverPaintsStaleOrMissingDisplayList() {
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.SettledHidden,
                hasRecordedContent = true,
                displayListStale = true,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.SettledHidden,
                hasRecordedContent = false,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.SettledHidden,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Reduced,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            ),
        )
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.SettledHidden,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = false,
                sdkInt = 35,
            ),
        )
        assertFalse(
            shouldPaintHostOwnedDepthLayer(
                exposure = VideoCardTransitionExposure.SettledHidden,
                hasRecordedContent = true,
                displayListStale = false,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 30,
            ),
        )
    }

    @Test
    fun snapshotDrawableRequiresFreshDisplayList() {
        assertTrue(isVideoCardTransitionSnapshotDrawable(hasRecordedContent = true, displayListStale = false))
        assertFalse(isVideoCardTransitionSnapshotDrawable(hasRecordedContent = true, displayListStale = true))
        assertFalse(isVideoCardTransitionSnapshotDrawable(hasRecordedContent = false, displayListStale = false))
    }

    @Test
    fun settledHiddenForcesFullDepthForPredictiveReadyState() {
        assertEquals(
            1f,
            resolveHostOwnedDepthProgress(
                exposure = VideoCardTransitionExposure.SettledHidden,
                liveProgress = 0.2f,
            ),
        )
        assertEquals(
            0.35f,
            resolveHostOwnedDepthProgress(
                exposure = VideoCardTransitionExposure.BackPreview,
                liveProgress = 0.35f,
            ),
        )
    }

    @Test
    fun hostSnapshotSurvivesSourceDisposeAndOnlyReleasesOnIdle() {
        assertFalse(shouldInvalidateSnapshotOnSourceDispose(isHostOwnedSnapshot = true))
        assertTrue(shouldInvalidateSnapshotOnSourceDispose(isHostOwnedSnapshot = false))
        assertTrue(shouldReleaseHostOwnedDepthLayer(VideoCardTransitionExposure.Idle))
        assertFalse(shouldReleaseHostOwnedDepthLayer(VideoCardTransitionExposure.SettledHidden))
        assertFalse(shouldReleaseHostOwnedDepthLayer(VideoCardTransitionExposure.BackPreview))
    }

    @Test
    fun hostOwnedDisposeDoesNotMarkStaleSoHeldBlurSurvives() {
        assertFalse(shouldMarkDisplayListStaleOnHostOwnedSourceDispose())
    }
}
