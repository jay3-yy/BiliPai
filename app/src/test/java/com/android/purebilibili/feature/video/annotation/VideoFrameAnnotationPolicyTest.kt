package com.android.purebilibili.feature.video.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameAnnotationPolicyTest {

    @Test
    fun pointerHoverPreviewActivatesOnlyForStylusOrMouseWhenIdle() {
        assertTrue(shouldActivatePointerHoverPreview(isStylusOrMouse = true, isSeekScrubbing = false, durationMs = 60_000L))
        assertFalse(shouldActivatePointerHoverPreview(isStylusOrMouse = false, isSeekScrubbing = false, durationMs = 60_000L))
        assertFalse(shouldActivatePointerHoverPreview(isStylusOrMouse = true, isSeekScrubbing = true, durationMs = 60_000L))
        assertFalse(shouldActivatePointerHoverPreview(isStylusOrMouse = true, isSeekScrubbing = false, durationMs = 0L))
    }

    @Test
    fun stylusPressureScaleClampsAndFallsBackToUnity() {
        assertEquals(1f, resolveStylusStrokeWidthScale(null), 0.001f)
        assertEquals(1f, resolveStylusStrokeWidthScale(0f), 0.001f)
        assertEquals(1f, resolveStylusStrokeWidthScale(Float.NaN), 0.001f)
        assertTrue(resolveStylusStrokeWidthScale(0.1f) < resolveStylusStrokeWidthScale(0.9f))
        assertEquals(1.55f, resolveStylusStrokeWidthScale(2.5f), 0.001f)
    }
}
