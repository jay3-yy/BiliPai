package com.android.purebilibili.feature.video.danmaku

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceOcclusionPolicyTest {

    @Test
    fun maskRects_areExpandedAndClamped() {
        val masks = resolveFaceOcclusionMasks(
            rawRects = listOf(
                FaceOcclusionMaskRect(
                    leftRatio = -0.05f,
                    topRatio = 0.05f,
                    rightRatio = 0.25f,
                    bottomRatio = 0.35f
                )
            ),
            expansionRatio = 0.05f
        )

        val first = masks.first()
        assertTrue(first.leftRatio >= 0f)
        assertTrue(first.topRatio >= 0f)
        assertTrue(first.rightRatio <= 1f)
        assertTrue(first.bottomRatio <= 1f)
        assertTrue(first.bottomRatio - first.topRatio > 0.3f)
    }

    @Test
    fun overlappingMaskRects_areMerged() {
        val masks = resolveFaceOcclusionMasks(
            rawRects = listOf(
                FaceOcclusionMaskRect(0.2f, 0.2f, 0.42f, 0.55f),
                FaceOcclusionMaskRect(0.35f, 0.3f, 0.6f, 0.62f)
            ),
            expansionRatio = 0f
        )

        assertEquals(1, masks.size)
        val merged = masks.first()
        assertTrue(merged.leftRatio <= 0.2f)
        assertTrue(merged.rightRatio >= 0.6f)
        assertTrue(merged.bottomRatio >= 0.62f)
    }

    @Test
    fun polygonExpansion_keepsShapeAndClampsIntoViewport() {
        val polygon = listOf(
            NormalizedPoint(0.02f, 0.10f),
            NormalizedPoint(0.22f, 0.08f),
            NormalizedPoint(0.25f, 0.28f),
            NormalizedPoint(0.05f, 0.30f)
        )

        val expanded = expandNormalizedPolygon(polygon, expansionRatio = 0.08f)

        assertEquals(polygon.size, expanded.size)
        assertTrue(expanded.all { it.xRatio in 0f..1f && it.yRatio in 0f..1f })
        assertTrue(expanded.first().xRatio <= polygon.first().xRatio)
    }

    @Test
    fun visualMaskPrefersPolygon_whenEnoughPoints() {
        val polygon = listOf(
            NormalizedPoint(0.3f, 0.2f),
            NormalizedPoint(0.4f, 0.18f),
            NormalizedPoint(0.5f, 0.22f),
            NormalizedPoint(0.52f, 0.32f),
            NormalizedPoint(0.45f, 0.4f),
            NormalizedPoint(0.34f, 0.36f)
        )

        val mask = buildVisualMask(
            rect = FaceOcclusionMaskRect(0.3f, 0.18f, 0.52f, 0.4f),
            polygon = polygon
        )

        assertTrue(mask.polygonPoints.isNotEmpty())
    }
}
