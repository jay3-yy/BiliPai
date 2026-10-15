package com.android.purebilibili.feature.video.screen

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoDetailCoverContentScaleTest {
    @Test
    fun wideLoadingCoverFillsSharedSixteenByNineSlotWithoutBlackBands() {
        val source = Size(160f, 90f)
        val slot = Size(160f, 90f)
        val result = VideoDetailCoverContentScale.computeScaleFactor(source, slot)
        assertEquals(ContentScale.Crop.computeScaleFactor(source, slot), result)
        assertTrue(source.width * result.scaleX >= slot.width)
        assertTrue(source.height * result.scaleY >= slot.height)
        assertEquals(result.scaleX, result.scaleY)
    }

    @Test
    fun matchingSourceRatioFillsWithoutAdditionalCropping() {
        val source = Size(320f, 180f)
        val slot = Size(160f, 90f)
        val result = VideoDetailCoverContentScale.computeScaleFactor(source, slot)
        assertEquals(0.5f, result.scaleX)
        assertEquals(result.scaleX, result.scaleY)
    }
}
