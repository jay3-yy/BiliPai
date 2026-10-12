package com.android.purebilibili.core.ui.components

import kotlin.test.assertEquals
import kotlin.test.Test

class TabVisibilityPolicyTest {
    @Test
    fun visibleUnequalWidthItemDoesNotMoveItsNeighbors() {
        assertEquals(0, resolveMeasuredTabVisibilityDelta(86, 194, 0, 224))
    }

    @Test
    fun partialItemsRevealOnlyTheirClippedPart() {
        assertEquals(-26, resolveMeasuredTabVisibilityDelta(-26, 68, 0, 224))
        assertEquals(53, resolveMeasuredTabVisibilityDelta(168, 277, 0, 224))
    }

    @Test
    fun viewportInsetsAndOversizedLabelsUseActualEdges() {
        assertEquals(-12, resolveMeasuredTabVisibilityDelta(-20, 64, -8, 216))
        assertEquals(40, resolveMeasuredTabVisibilityDelta(40, 360, 0, 224))
    }
}
