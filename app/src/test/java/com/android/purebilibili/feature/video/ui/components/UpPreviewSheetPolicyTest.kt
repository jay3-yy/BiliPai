package com.android.purebilibili.feature.video.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpPreviewSheetPolicyTest {

    @Test
    fun surfaceColors_lightAndDarkRemainReadable() {
        val light = resolveUpPreviewSheetSurfaceColors(lightColorScheme())
        val dark = resolveUpPreviewSheetSurfaceColors(darkColorScheme())

        assertTrue(light.sheetColor.luminance() > 0.5f)
        assertTrue(dark.sheetColor.luminance() < 0.5f)
        assertTrue(light.titleColor.luminance() < light.sheetColor.luminance())
        assertTrue(dark.titleColor.luminance() > dark.sheetColor.luminance())
        assertNotNull(light.followFillColor)
        assertNotNull(dark.followFillColor)
    }

    @Test
    fun statValue_formatsCountsAndDistinguishesUnknownFromZero() {
        assertEquals("1.5万", formatUpPreviewStat(15_291))
        assertEquals("128", formatUpPreviewStat(128))
        assertEquals("0", formatUpPreviewStat(0))
        assertEquals("—", formatUpPreviewStat(null))
    }

    @Test
    fun videoClickTarget_rejectsBlankBvid() {
        assertNull(resolveUpPreviewVideoClickTarget("  "))
        assertEquals("BV1xx" to 0L, resolveUpPreviewVideoClickTarget("BV1xx"))
        assertEquals("BV1yy" to 12L, resolveUpPreviewVideoClickTarget(" BV1yy ", 12L))
    }

    @Test
    fun dismissGesture_acceptsDistanceOrDownwardVelocity() {
        assertTrue(shouldDismissUpPreviewSheet(96f, 0f, 96f))
        assertTrue(shouldDismissUpPreviewSheet(24f, 1_200f, 96f))
        assertFalse(shouldDismissUpPreviewSheet(24f, 600f, 96f))
        assertFalse(shouldDismissUpPreviewSheet(0f, 2_000f, 96f))
    }

    @Test
    fun visibilityProgress_tracksHostAndDragLikeComments() {
        assertEquals(
            0f,
            resolveUpPreviewSheetVisibilityProgress(
                hostVisible = false,
                hostVisibilityProgress = 0f,
                sheetDragOffsetPx = 0f,
                sheetHeightPx = 1000f,
            ),
        )
        assertEquals(
            1f,
            resolveUpPreviewSheetVisibilityProgress(
                hostVisible = true,
                hostVisibilityProgress = 1f,
                sheetDragOffsetPx = 0f,
                sheetHeightPx = 1000f,
            ),
        )
        assertEquals(
            0.5f,
            resolveUpPreviewSheetVisibilityProgress(
                hostVisible = true,
                hostVisibilityProgress = 1f,
                sheetDragOffsetPx = 500f,
                sheetHeightPx = 1000f,
            ),
            0.001f,
        )
    }

    @Test
    fun overlaySheetExpansion_prefersDominantSheetFraction() {
        val upDominant = resolvePortraitOverlaySheetExpansion(
            commentVisibilityProgress = 0.2f,
            upPreviewVisibilityProgress = 0.8f,
            commentSheetHeightFraction = 0.60f,
            upPreviewSheetHeightFraction = UP_PREVIEW_SHEET_HEIGHT_FRACTION,
        )
        assertEquals(0.8f, upDominant.progress, 0.001f)
        assertEquals(UP_PREVIEW_SHEET_HEIGHT_FRACTION, upDominant.sheetHeightFraction, 0.001f)

        val commentDominant = resolvePortraitOverlaySheetExpansion(
            commentVisibilityProgress = 0.9f,
            upPreviewVisibilityProgress = 0.1f,
            commentSheetHeightFraction = 0.60f,
        )
        assertEquals(0.9f, commentDominant.progress, 0.001f)
        assertEquals(0.60f, commentDominant.sheetHeightFraction, 0.001f)
    }
}
