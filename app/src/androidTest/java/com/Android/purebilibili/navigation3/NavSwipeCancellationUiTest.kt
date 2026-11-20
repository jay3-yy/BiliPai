package com.android.purebilibili.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.NavBackStack
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

class NavSwipeCancellationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun consumedReleaseCancelsReturnAndNextSwipeStillWorks() {
        val stack = NavBackStack<BiliPaiNavKey>(BiliPaiNavKey.Settings, BiliPaiNavKey.AnimationSettings)
        var consumeRelease = true
        compose.setContent {
            BiliPaiAndroidXNavDisplay(
                backStack = stack,
                onBack = { stack.removeLastOrNull() },
                effects = NavDisplayEffects.None,
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (consumeRelease) event.changes.filter { !it.pressed }.forEach { it.consume() }
                        }
                    }
                },
                motion = {
                    entry<BiliPaiNavKey.Settings> {}
                    entry<BiliPaiNavKey.AnimationSettings>(swipeDismiss = NavSwipeDirection.LeftToRight) {}
                },
            ) { Box(Modifier.fillMaxSize()) }
        }
        fun swipe() = compose.onRoot().performTouchInput {
            down(Offset(width * 0.05f, centerY))
            repeat(6) { step -> moveTo(Offset(width * (0.05f + 0.12f * (step + 1)), centerY)) }
            up()
        }
        swipe()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, stack.size)
            consumeRelease = false
        }
        swipe()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(BiliPaiNavKey.Settings), stack.toList()) }
    }

    @Test
    fun replacingTopPageDuringDragReleasesTheOldSwipe() {
        val stack = NavBackStack<BiliPaiNavKey>(BiliPaiNavKey.Settings, BiliPaiNavKey.AnimationSettings)
        lateinit var depth: () -> Float
        compose.setContent {
            BiliPaiAndroidXNavDisplay(
                backStack = stack,
                onBack = { stack.removeLastOrNull() },
                effects = NavDisplayEffects.None,
                motion = {
                    entry<BiliPaiNavKey.Settings> {}
                    entry<BiliPaiNavKey.AnimationSettings>(swipeDismiss = NavSwipeDirection.LeftToRight) {}
                    entry<BiliPaiNavKey.HomeSettings>(swipeDismiss = NavSwipeDirection.LeftToRight) {}
                },
            ) { route ->
                if (route != BiliPaiNavKey.Settings) {
                    val transition = LocalNavTransitionScope.current
                    SideEffect { depth = { transition.relativeDepth } }
                }
                Box(Modifier.fillMaxSize())
            }
        }

        val root = compose.onRoot()
        root.performTouchInput {
            down(Offset(width * 0.05f, centerY))
            moveTo(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.45f, centerY))
        }
        compose.waitUntil { depth() < -0.2f }
        compose.runOnIdle { stack[stack.lastIndex] = BiliPaiNavKey.HomeSettings }
        compose.waitUntil { depth() >= -0.001f }
        root.performTouchInput {
            moveTo(Offset(width * 0.85f, centerY))
            up()
        }
        compose.runOnIdle {
            assertEquals(BiliPaiNavKey.HomeSettings, stack.last())
            assertEquals(0f, depth(), 0.001f)
        }
    }
}
