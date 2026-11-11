package com.android.purebilibili.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

class NavSwipeCancellationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun consumedReleaseCancelsReturnAndNextSwipeStillWorks() {
        val stack = navBackStackOf(BiliPaiNavKey.Settings, BiliPaiNavKey.AnimationSettings)
        var consumeRelease = true
        compose.setContent {
            NavDisplay(
                backStack = stack,
                effects = NavDisplayEffects.None,
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (consumeRelease) event.changes.filter { !it.pressed }.forEach { it.consume() }
                        }
                    }
                },
            ) {
                entry<BiliPaiNavKey.Settings> { Box(Modifier.fillMaxSize()) }
                entry<BiliPaiNavKey.AnimationSettings>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                    Box(Modifier.fillMaxSize())
                }
            }
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
}
