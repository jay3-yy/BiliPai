package com.android.purebilibili.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.LocalNavEntryContentVisible
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

class NavCoveredContentUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun changingNavigationStyleKeepsCoveredWorkPausedAndModalBackgroundVisible() {
        val stack = NavBackStack<BiliPaiNavKey>(BiliPaiNavKey.Settings, BiliPaiNavKey.AnimationSettings)
        val transition = mutableStateOf<NavTransition>(NavTransitions.MiuixDefault)
        val sourceColor = mutableStateOf(Color.Red)
        val sourceDraws = AtomicInteger()
        var sourceVisible = true
        lateinit var sourceLifecycle: Lifecycle
        composeRule.setContent {
            BiliPaiAndroidXNavDisplay(
                backStack = stack,
                onBack = { stack.removeLastOrNull() },
                transition = transition.value,
                effects = NavDisplayEffects.None,
                motion = {
                    entry<BiliPaiNavKey.Settings> {}
                    entry<BiliPaiNavKey.AnimationSettings> {}
                },
            ) { route ->
                if (route == BiliPaiNavKey.Settings) {
                    val visible = LocalNavEntryContentVisible.current
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    SideEffect {
                        sourceVisible = visible
                        sourceLifecycle = lifecycle
                    }
                    Box(Modifier.fillMaxSize().drawWithContent {
                        sourceDraws.incrementAndGet()
                        drawRect(sourceColor.value)
                        drawContent()
                    })
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        for (style in listOf(NavTransitions.MiuixDefault, NavTransitions.None)) {
            composeRule.runOnIdle { transition.value = style }
            composeRule.runOnIdle {
                assertFalse(sourceVisible)
                assertEquals(Lifecycle.State.CREATED, sourceLifecycle.currentState)
            }
            val before = sourceDraws.get()
            composeRule.runOnIdle { sourceColor.value = if (sourceColor.value == Color.Red) Color.Blue else Color.Red }
            composeRule.runOnIdle { assertEquals(before, sourceDraws.get()) }
        }

        // 弹层的背景仍露出，切换到这种方式时必须重新启用来源页。
        composeRule.runOnIdle { transition.value = NavTransitions.Modal }
        composeRule.runOnIdle {
            assertTrue(sourceVisible)
            assertEquals(Lifecycle.State.STARTED, sourceLifecycle.currentState)
        }
        val modalDraws = sourceDraws.get()
        composeRule.runOnIdle { sourceColor.value = Color.Green }
        composeRule.runOnIdle { assertTrue(sourceDraws.get() > modalDraws) }

        composeRule.runOnIdle { transition.value = NavTransitions.MiuixDefault }
        composeRule.runOnIdle {
            assertFalse(sourceVisible)
            assertEquals(Lifecycle.State.CREATED, sourceLifecycle.currentState)
        }
        composeRule.runOnIdle { stack.removeLastOrNull() }
        composeRule.runOnIdle {
            assertTrue(sourceVisible)
            assertEquals(Lifecycle.State.RESUMED, sourceLifecycle.currentState)
        }
    }
}
