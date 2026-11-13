package com.android.purebilibili.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.util.animateEnter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnterAnimationUiRegressionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun completedItemStaysVisibleAcrossPolicyChangesButNewIdentityEntersAgain() {
        var identity by mutableStateOf("first")
        var tier by mutableStateOf(MotionTier.Normal)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.size(80.dp).background(Color.White).testTag("frame")) {
                Box(Modifier.size(80.dp).animateEnter(
                    key = identity,
                    initialOffsetY = 0f,
                    motionTier = tier,
                ).background(Color.Black))
            }
        }
        assertFaded()
        rule.mainClock.advanceTimeBy(2_000)
        assertVisible()
        rule.runOnIdle { tier = MotionTier.Reduced }
        rule.mainClock.advanceTimeByFrame()
        assertVisible()
        rule.runOnIdle { identity = "second" }
        rule.mainClock.advanceTimeByFrame()
        assertFaded()
        rule.mainClock.advanceTimeBy(2_000)
        assertVisible()
    }

    @Test
    fun disablingEntranceShowsContentAndReenablingStartsAFreshEntrance() {
        var enabled by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.size(80.dp).background(Color.White).testTag("frame")) {
                Box(Modifier.size(80.dp).animateEnter(
                    animationEnabled = enabled,
                    initialOffsetY = 0f,
                ).background(Color.Black))
            }
        }
        rule.runOnIdle { enabled = false }
        rule.mainClock.advanceTimeByFrame()
        assertVisible()
        rule.runOnIdle { enabled = true }
        rule.mainClock.advanceTimeByFrame()
        assertFaded()
        rule.mainClock.advanceTimeBy(2_000)
        assertVisible()
    }

    private fun centerRed(): Float {
        val pixels = rule.onNodeWithTag("frame").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2].red
    }

    private fun assertFaded() = assertTrue("New content must start faded", centerRed() > 0.5f)
    private fun assertVisible() = assertTrue("Content must finish visible", centerRed() < 0.05f)
}
