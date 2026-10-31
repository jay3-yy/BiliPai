package com.android.purebilibili.core.ui.components

import com.android.purebilibili.core.store.ScrollFeedbackPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollFeedbackPolicyTest {
    @Test
    fun `standard keeps each caller original spring and presets only change damping`() {
        listOf(0.78f to 420f, 0.74f to 420f, 0.96f to 520f).forEach { (damping, stiffness) ->
            val standard = scrollFeedbackSpring(ScrollFeedbackPreset.STANDARD, damping, stiffness)
            val gentle = scrollFeedbackSpring(ScrollFeedbackPreset.GENTLE, damping, stiffness)
            val lively = scrollFeedbackSpring(ScrollFeedbackPreset.LIVELY, damping, stiffness)
            assertEquals(damping, standard.dampingRatio)
            assertEquals(stiffness, standard.stiffness)
            assertEquals(1f, gentle.dampingRatio)
            assertEquals(stiffness, gentle.stiffness)
            assertTrue(lively.dampingRatio < standard.dampingRatio)
            assertEquals(stiffness, lively.stiffness)
        }
    }
}
