package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

class PageTransitionDrawingRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun circularRevealReusesContentDrawingAndKeepsLivePixels() {
        assertClippingReusesContent(CircularRevealNavTransition)
    }

    @Test
    fun sizeRevealReusesContentDrawingAndKeepsLivePixels() {
        assertClippingReusesContent(SizePageNavTransition)
    }

    private fun assertClippingReusesContent(transition: NavTransition) {
        val scope = TestTransitionScope(-0.75f)
        val contentColor = mutableStateOf(Color.Green)
        val contentDraws = AtomicInteger()
        composeRule.setContent {
            Box(Modifier.size(80.dp).background(Color.Red).testTag("reveal")) {
                Box(
                    with(transition) { Modifier.matchParentSize().transformEntry(scope) }
                        .drawBehind {
                            contentDraws.incrementAndGet()
                            drawRect(contentColor.value)
                        },
                )
            }
        }
        fun assertPixels(edgeColor: Color, centerColor: Color) {
            val pixels = composeRule.onNodeWithTag("reveal").captureToImage().toPixelMap()
            for ((heightFraction, expected) in listOf(0.2f to edgeColor, 0.5f to centerColor)) {
                val actual = pixels[pixels.width / 2, (pixels.height * heightFraction).toInt()]
                assertEquals(expected.red, actual.red, 0.03f)
                assertEquals(expected.green, actual.green, 0.03f)
                assertEquals(expected.blue, actual.blue, 0.03f)
            }
        }
        assertPixels(Color.Red, Color.Green)
        composeRule.runOnIdle { contentDraws.set(0) }
        // 覆盖展开、收回与落位；只改变裁剪时复用内容绘制记录。
        for (depth in listOf(-0.25f, -0.75f, 0f)) {
            composeRule.runOnIdle { scope.depth.floatValue = depth }
            assertPixels(if (depth == -0.75f) Color.Red else Color.Green, Color.Green)
            composeRule.runOnIdle { assertEquals(0, contentDraws.get()) }
        }
        composeRule.runOnIdle { contentColor.value = Color.Yellow }
        assertPixels(Color.Yellow, Color.Yellow)
        composeRule.runOnIdle { assertTrue(contentDraws.get() > 0) }
        composeRule.runOnIdle { scope.depth.floatValue = -0.75f }
        assertPixels(Color.Red, Color.Yellow)
    }

    @Test
    fun classicReturnDoesNotRecomposeEntryWithinTheSameRole() {
        val scope = TestTransitionScope(0.8f)
        val compositions = AtomicInteger()
        composeRule.setContent {
            val modifier = with(ClassicNavTransition) { Modifier.transformEntry(scope) }
            SideEffect { compositions.incrementAndGet() }
            Box(modifier.size(80.dp).background(Color.Green).testTag("classic"))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { compositions.set(0) }
        for (depth in listOf(0.6f, 0.4f, 0.2f)) {
            composeRule.runOnIdle { scope.depth.floatValue = depth }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertEquals(0, compositions.get()) }
        }
        // 到达角色边界仍更新层级，避免用冻结层级换取不重组。
        composeRule.runOnIdle { scope.depth.floatValue = 0f }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(compositions.get() > 0) }
    }

    /** 提供与导航宿主相同的连续深度及按边界发布的角色信号。 */
    private class TestTransitionScope(initialDepth: Float) : NavTransitionScope {
        val depth = mutableFloatStateOf(initialDepth)
        override val relativeDepth: Float get() = depth.floatValue
        override val role: NavRole by derivedStateOf {
            when {
                relativeDepth > 0.001f -> NavRole.Covered
                relativeDepth < -0.001f -> NavRole.Outgoing
                else -> NavRole.Top
            }
        }
        override val change = NavChange.Pop
        override val gesture: NavGesture? = null
        override val layoutSize = IntSize(240, 240)
        override val layoutDirection = LayoutDirection.Ltr
        override val density = Density(1f)
    }
}
