package com.android.purebilibili.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

class ContainerEnterFrameUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun incomingContainerLaysOutAtSourceBeforeItsOpeningClockAdvances() {
        val stack = mutableStateListOf<NavKey>(BiliPaiNavKey.Settings)
        var firstLayoutDepth: Float? = null
        val drawnDepths = mutableListOf<Float>()
        val transition = object : NavTransition {
            override val preparesEnterFrame = true
            override fun Modifier.transformEntry(scope: NavTransitionScope): Modifier = drawWithContent {
                drawnDepths.add(scope.relativeDepth)
                drawContent()
            }
        }
        rule.setContent {
            NavDisplay(backStack = stack) {
                entry<BiliPaiNavKey.Settings> { Box(Modifier.fillMaxSize()) }
                entry<BiliPaiNavKey.AnimationSettings>(transition = transition) {
                    val scope = LocalNavTransitionScope.current
                    Box(Modifier.fillMaxSize().onGloballyPositioned {
                        if (firstLayoutDepth == null) firstLayoutDepth = scope.relativeDepth
                    })
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { stack.add(BiliPaiNavKey.AnimationSettings) }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(-1f, requireNotNull(firstLayoutDepth), 0.0001f) }
        repeat(6) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        rule.runOnIdle { assertTrue(drawnDepths.any { it > -1f && it < 0f }) }
    }
}
