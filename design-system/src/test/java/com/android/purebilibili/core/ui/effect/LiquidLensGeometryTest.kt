package com.android.purebilibili.core.ui.effect

import kotlin.test.Test
import kotlin.test.assertEquals

class LiquidLensGeometryTest {
    @Test
    fun smallConfiguredRefractionIsPreservedAndVerticalCapsulesUseTheirWidth() {
        assertEquals(8f, resolveLiquidLensRefractionHeight(8f, 260f, 46f))
        assertEquals(23f, resolveLiquidLensRefractionHeight(24f, 46f, 260f))
    }

    @Test
    fun refractionStaysOutsideCenterThroughoutCapsuleCollapse() {
        val collapsedSizes = listOf(46f to 23f, 42f to 21f, 32f to 16f, 16f to 8f, 1f to 0.5f, 0f to 0f)
        for ((shortSide, expectedBand) in collapsedSizes) {
            assertEquals(expectedBand, resolveLiquidLensRefractionHeight(24f, 260f, shortSide))
        }
    }
}
