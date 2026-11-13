package com.android.purebilibili.core.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNestedPagerGestureTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun miuixCarouselKeepsTouchWhenHomePagerYields() = checkNestedPaging(AppUiStyle.MIUIX)

    @Test
    fun materialCarouselKeepsTouchWhenHomePagerYields() = checkNestedPaging(AppUiStyle.MATERIAL3)

    private fun checkNestedPaging(style: AppUiStyle) {
        lateinit var home: PagerState
        lateinit var hero: PagerState
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppUiStyle provides style) {
                home = rememberPagerState { 2 }
                hero = rememberPagerState { 3 }
                val heroPointerActive = remember { mutableStateOf(false) }
                AppHorizontalPager(
                    state = home,
                    shouldYieldGesture = { heroPointerActive.value },
                    useNativeGestures = true,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    if (page == 0) {
                        Column(Modifier.fillMaxSize()) {
                            Box(
                                Modifier.fillMaxWidth().height(160.dp).pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(false, PointerEventPass.Initial)
                                        heroPointerActive.value = true
                                        try {
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                            } while (event.changes.any { it.pressed })
                                        } finally {
                                            heroPointerActive.value = false
                                        }
                                    }
                                },
                            ) {
                                AppHorizontalPager(state = hero, modifier = Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxSize().testTag("hero_$it").clickable { clicks++ })
                                }
                            }
                            Box(Modifier.fillMaxSize().testTag("feed"))
                        }
                    } else Box(Modifier.fillMaxSize())
                }
            }
        }

        // Split down/up across recomposition: yielding must not cancel the child's pointer cycle.
        rule.onNodeWithTag("hero_0").performTouchInput { down(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("hero_0").performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, clicks) }

        rule.onNodeWithTag("hero_0").performTouchInput {
            down(Offset(width * 0.85f, height * 0.5f))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("hero_0").performTouchInput {
            moveTo(Offset(width * 0.15f, height * 0.5f), 300)
            up()
        }
        rule.runOnIdle {
            assertEquals(1, hero.currentPage)
            assertEquals(0, home.currentPage)
            assertEquals(1, clicks)
        }

        // Outside the carousel, the home categories must still accept horizontal swipes.
        rule.onNodeWithTag("feed").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(1, home.currentPage) }
    }
}
