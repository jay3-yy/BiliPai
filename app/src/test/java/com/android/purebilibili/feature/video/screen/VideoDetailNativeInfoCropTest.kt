package com.android.purebilibili.feature.video.screen

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoDetailNativeInfoCropTest {
    @Test
    fun scaledCardDoesNotCopyTheLastCoverRowsIntoItsInfoBand() {
        // Original layer: cover [0, 560), info [560, 900). The screen-space
        // card can be compressed by landing feedback or enlarged by hover.
        for (scale in listOf(0.97f, 1f, 1.025f)) {
            val crop = resolveVideoDetailNativeInfoCrop(
                sourceInfoRect = Rect(0f, 560f * scale, 1000f * scale, 900f * scale),
                sourceCardSize = Size(1000f * scale, 900f * scale),
                layerSize = IntSize(1000, 900),
                recordedInfoBounds = null,
            )
            assertEquals(0f, crop.left, 0.001f)
            assertEquals(560f, crop.top, 0.001f)
            assertEquals(1000f, crop.right, 0.001f)
            assertEquals(900f, crop.bottom, 0.001f)
        }
    }

    @Test
    fun localMeasurementWinsWhenCoverAndOuterCardHaveDifferentTransforms() {
        val localInfo = Rect(0f, 560f, 1000f, 900f)
        val crop = resolveVideoDetailNativeInfoCrop(
            // The outer card anchor is outside the landing-feedback transform.
            sourceInfoRect = Rect(15f, 556.7f, 985f, 886.5f),
            sourceCardSize = Size(1000f, 900f),
            layerSize = IntSize(1000, 900),
            recordedInfoBounds = localInfo,
        )
        assertEquals(localInfo, crop)
    }
}
