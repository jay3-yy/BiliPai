package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailProgressiveReturnTest {
    private fun frame(depth: Float, restoring: Boolean = false) =
        resolveVideoDetailReturnMediaFrame(
            transitionProgress = depth,
            isCommittedCardReturn = !restoring,
            isReturnGestureInProgress = restoring,
            hasResidentCover = true,
            progressiveResidentCover = true,
        )

    @Test
    fun coverTakesOverWithoutAnEmptyOrTranslucentPlayerSlot() {
        assertEquals(VideoDetailReturnMediaFrame(0f, 1f), frame(1f))
        var previousCoverAlpha = 0f
        for (step in 0..100) {
            val current = frame(1f - step / 100f)
            assertTrue(current.coverAlpha >= previousCoverAlpha)
            // Never fade both layers at once, even on the SurfaceView path.
            assertTrue(current.coverAlpha == 1f || current.playerAlpha == 1f)
            previousCoverAlpha = current.coverAlpha
        }
        assertEquals(VideoDetailReturnMediaFrame(1f, 0f), frame(0f))
    }

    @Test
    fun commitAndCancelUseTheSameDepthWithoutRestartingTheReveal() {
        for (depth in listOf(1f, 0.9f, 0.6f, 0.2f, 0f)) {
            assertEquals(frame(depth), frame(depth, restoring = true))
        }
        assertEquals(VideoDetailReturnMediaFrame(0f, 1f), frame(1f, restoring = true))
    }

    @Test
    fun missingFirstFrameKeepsTheResidentCoverOpaque() {
        assertEquals(
            VideoDetailReturnMediaFrame(1f, 0f),
            resolveVideoDetailReturnMediaFrame(
                transitionProgress = 0.9f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                progressiveResidentCover = true,
                showResidentCoverUntilFirstFrame = true,
            ),
        )
    }

    @Test
    fun compatibilityGateRequiresReadyMediaAndAnInlineHost() {
        fun enabled(
            sdk: Int = 35,
            ownsInline: Boolean = true,
            firstFrame: Boolean = true,
            decodedCover: Boolean = true,
            live: Boolean = false,
            forced: Boolean = false,
            reduced: Boolean = false,
        ) = shouldUseProgressiveResidentCoverReturn(
            sdk, ownsInline, firstFrame, decodedCover, live, forced, reduced,
        )
        assertTrue(enabled())
        assertFalse(enabled(sdk = 34))
        assertFalse(enabled(ownsInline = false))
        assertFalse(enabled(firstFrame = false))
        assertFalse(enabled(decodedCover = false))
        assertFalse(enabled(live = true))
        assertFalse(enabled(forced = true))
        assertFalse(enabled(reduced = true))
    }

    @Test
    fun playerTransformCropsUniformlyAndKeepsTheTargetCentered() {
        val target = VideoDetailReturnMediaLayoutFrame(80, 40, 300, 240)
        val transform = resolveVideoDetailReturnPlayerTransform(1000, 600, target)
        assertEquals(0.4f, transform.scale, 0.0001f)
        assertEquals(230f, transform.translationXPx + 1000 * transform.scale / 2f, 0.0001f)
        assertEquals(160f, transform.translationYPx + 600 * transform.scale / 2f, 0.0001f)
        assertTrue(1000 * transform.scale >= target.widthPx)
        assertTrue(600 * transform.scale >= target.heightPx)
    }

    @Test
    fun playerTransformReturnsExactlyToTheDetailViewport() {
        val original = VideoDetailReturnMediaLayoutFrame(0, 0, 1088, 684)
        assertEquals(
            VideoDetailReturnPlayerTransform(0f, 0f, 1f),
            resolveVideoDetailReturnPlayerTransform(1088, 684, original),
        )
    }
}
