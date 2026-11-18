package com.android.purebilibili.core.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimatedCountTextUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun initialRevealContinuesFromTheVisibleCountWhenTheTargetChanges() {
        val target = mutableLongStateOf(254L)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalComponentMotionEnabled provides true,
                LocalSystemReduceMotion provides false,
            ) {
                AnimatedCountText(count = target.longValue, prefix = "×", animateFromZero = true)
            }
        }

        assertEquals(0L, displayedCount())
        rule.mainClock.advanceTimeBy(224L)
        val beforeUpdate = displayedCount()
        assertTrue(beforeUpdate in 1L..127L)

        rule.runOnIdle { target.longValue = 255L }
        rule.mainClock.advanceTimeBy(240L)
        assertTrue(displayedCount() in (beforeUpdate + 1L)..254L)
        rule.mainClock.advanceTimeBy(1_000L)
        assertEquals(255L, displayedCount())
    }

    @Test
    fun initialRevealAlsoRunsForCountsOutsideTheIntRange() {
        val target = Int.MAX_VALUE.toLong() + 10L
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalComponentMotionEnabled provides true,
                LocalSystemReduceMotion provides false,
            ) {
                AnimatedCountText(count = target, prefix = "×", animateFromZero = true)
            }
        }

        assertEquals(0L, displayedCount())
        rule.mainClock.advanceTimeBy(320L)
        assertTrue(displayedCount() in 1L until target)
        rule.mainClock.advanceTimeBy(1_000L)
        assertEquals(target, displayedCount())
    }

    private fun displayedCount(): Long =
        rule.onNode(hasText("×", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text.removePrefix("×").toLong()
}
