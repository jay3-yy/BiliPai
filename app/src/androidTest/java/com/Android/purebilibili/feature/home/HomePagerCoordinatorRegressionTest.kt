package com.android.purebilibili.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class HomePagerCoordinatorRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newerPageSelectionRemainsBusyWhenThePreviousAnimationIsCancelled() {
        composeRule.mainClock.autoAdvance = false
        lateinit var coordinator: HomePagerCoordinator
        lateinit var scope: CoroutineScope
        lateinit var firstSelection: Job
        val entries = listOf(HomeCategory.RECOMMEND, HomeCategory.FOLLOW, HomeCategory.POPULAR)
            .map { HomeTopTabEntry.Category(it) }
        composeRule.setContent {
            val pager = rememberPagerState { entries.size }
            coordinator = rememberHomePagerCoordinator(pager, entries, HomeCategory.RECOMMEND, 0)
            scope = rememberCoroutineScope()
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Text("page $page")
            }
        }
        composeRule.runOnIdle {
            firstSelection = coordinator.selectPage(1, tween(600), scope)
            assertTrue(coordinator.isSwitching)
        }
        composeRule.mainClock.advanceTimeBy(48)
        composeRule.runOnIdle {
            assertTrue(coordinator.isSwitching)
            coordinator.selectPage(2, tween(600), scope)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitUntil(timeoutMillis = 5_000) { firstSelection.isCancelled }
        composeRule.runOnIdle { assertTrue(coordinator.isSwitching) }

        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.runOnIdle {
            assertEquals(2, coordinator.pagerState.currentPage)
            assertFalse(coordinator.pagerState.isScrollInProgress)
            assertFalse(coordinator.isSwitching)
        }
    }
}
