package com.android.purebilibili.data.model.response

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDimensionPolicyTest {

    @Test
    fun isVertical_true_whenHeightGreaterThanWidthWithoutRotation() {
        val dimension = Dimension(width = 720, height = 1280, rotate = 0)
        assertTrue(dimension.isVertical)
    }

    @Test
    fun isVertical_true_whenRotate90SwapsLandscapeSource() {
        val dimension = Dimension(width = 1920, height = 1080, rotate = 90)
        assertTrue(dimension.isVertical)
    }

    @Test
    fun isVertical_true_whenRotate270SwapsLandscapeSource() {
        val dimension = Dimension(width = 1920, height = 1080, rotate = 270)
        assertTrue(dimension.isVertical)
    }

    @Test
    fun isVertical_false_whenRotate180KeepsLandscape() {
        val dimension = Dimension(width = 1920, height = 1080, rotate = 180)
        assertFalse(dimension.isVertical)
    }

    @Test
    fun isVertical_true_whenRotate1SwapsLandscapeSource() {
        val dimension = Dimension(width = 1920, height = 1080, rotate = 1)
        assertTrue(dimension.isVertical)
    }

    @Test
    fun isVertical_false_whenRotate1SwapsPortraitSourceToLandscape() {
        val dimension = Dimension(width = 1080, height = 1920, rotate = 1)
        assertFalse(dimension.isVertical)
    }

    @Test
    fun isVerticalAtRatio_respectsHigherThresholdAndRotation() {
        val nearSquarePortrait = Dimension(width = 1000, height = 1100, rotate = 0)
        assertTrue(nearSquarePortrait.isVerticalAtRatio(1.0f))
        assertFalse(nearSquarePortrait.isVerticalAtRatio(1.2f))

        val rotatedTallPortrait = Dimension(width = 1920, height = 1080, rotate = 1)
        assertTrue(rotatedTallPortrait.isVerticalAtRatio(1.5f))
    }
}
