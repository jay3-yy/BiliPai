package com.android.purebilibili.feature.dynamic.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePreviewThumbnailScrubberTest {
    @Test
    fun resolvesDirectHitGapAndClampedEdgesFromMeasuredBounds() {
        val bounds = listOf(
            0f to 30f,
            36f to 66f,
            72f to 112f,
            118f to 148f,
        )
        assertEquals(0, resolveImagePreviewThumbnailScrubIndex(-10f, bounds, 148f, 4))
        assertEquals(0, resolveImagePreviewThumbnailScrubIndex(15f, bounds, 148f, 4))
        assertEquals(1, resolveImagePreviewThumbnailScrubIndex(50f, bounds, 148f, 4))
        assertEquals(2, resolveImagePreviewThumbnailScrubIndex(70f, bounds, 148f, 4))
        assertEquals(3, resolveImagePreviewThumbnailScrubIndex(200f, bounds, 148f, 4))
    }

    @Test
    fun fallsBackToProportionalMappingWhenBoundsUnavailable() {
        assertEquals(0, resolveImagePreviewThumbnailScrubIndex(0f, emptyList(), 300f, 3))
        assertEquals(1, resolveImagePreviewThumbnailScrubIndex(150f, emptyList(), 300f, 3))
        assertEquals(2, resolveImagePreviewThumbnailScrubIndex(299f, emptyList(), 300f, 3))
        assertEquals(0, resolveImagePreviewThumbnailScrubIndex(50f, emptyList(), 300f, 1))
    }
}
