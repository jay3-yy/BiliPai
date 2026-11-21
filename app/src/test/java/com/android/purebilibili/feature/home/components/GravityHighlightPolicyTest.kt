package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class GravityHighlightPolicyTest {

    @Test
    fun `gravity samples on opposite sides of the angle wrap share one highlight direction`() {
        val above = quantizeGravityHighlightSector(gravityX = -0.95f, gravityY = 0.01f)
        val below = quantizeGravityHighlightSector(gravityX = -0.95f, gravityY = -0.01f)
        assertEquals(above, below)
    }

    @Test
    fun `missing gravity falls back to straight up`() {
        assertEquals(
            quantizeGravityHighlightSector(gravityX = 0f, gravityY = -1f),
            quantizeGravityHighlightSector(gravityX = 0f, gravityY = 0f),
        )
    }
}
