package com.android.purebilibili.feature.home

import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.store.HomeBarHideType
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScrollLayoutUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    private var hostCompositions = 0

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun refreshInsetUpdatesIndicatorWithoutRecomposingFeedOrPageHost() {
        val inset = mutableStateOf(120.dp)
        var pageCompositions = 0
        var feedCompositions = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppUiStyle provides AppUiStyle.MATERIAL3) {
                MaterialTheme {
                    SideEffect { pageCompositions++ }
                    HomeFeedPullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = {},
                        state = rememberPullToRefreshState(),
                        indicatorTopInset = inset,
                        indicator = { topInset -> Text("刷新起点：${topInset.value.toInt()}") },
                    ) {
                        SideEffect { feedCompositions++ }
                        Text("首页列表")
                    }
                }
            }
        }
        rule.onNodeWithText("刷新起点：120").assertIsDisplayed()
        var initialPageCompositions = 0
        var initialFeedCompositions = 0
        rule.runOnIdle {
            initialPageCompositions = pageCompositions
            initialFeedCompositions = feedCompositions
            inset.value = 64.dp
        }
        rule.onNodeWithText("刷新起点：64").assertIsDisplayed()
        rule.onNodeWithText("首页列表").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(initialPageCompositions, pageCompositions)
            assertEquals(initialFeedCompositions, feedCompositions)
        }
    }

    @Test
    fun embeddedPaddingUpdatesKeepPageHostStableAndRespectStatusBar() {
        val headerOffset = mutableFloatStateOf(0f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme { EmbeddedPageHost(headerOffset) }
            }
        }
        rule.onNodeWithText("顶部留白：120").assertIsDisplayed()
        var initialHostCompositions = 0
        rule.runOnIdle { initialHostCompositions = hostCompositions }
        assertTrue(initialHostCompositions > 0)

        // 连续偏移保留 -3px，不再按 4dp 阶梯取整。
        rule.runOnIdle { headerOffset.floatValue = -3f }
        rule.onNodeWithText("顶部留白：117").assertIsDisplayed()
        rule.runOnIdle { assertEquals(initialHostCompositions, hostCompositions) }

        rule.runOnIdle { headerOffset.floatValue = -200f }
        rule.onNodeWithText("顶部留白：24").assertIsDisplayed()
        rule.runOnIdle { assertEquals(initialHostCompositions, hostCompositions) }
    }

    @Composable
    private fun EmbeddedPageHost(headerOffset: MutableFloatState) {
        SideEffect { hostCompositions += 1 }
        val offsetProvider = remember(headerOffset) { { headerOffset.floatValue } }
        val padding = rememberHomeEmbeddedPageTopPadding(
            expandedTopPadding = 120.dp,
            statusBarHeight = 24.dp,
            tabRowHeight = 40.dp,
            tabsCollapsed = false,
            headerOffsetProvider = offsetProvider,
        )
        HomeEmbeddedPageContent(padding) { topPadding ->
            Text("顶部留白：${topPadding.value.toInt()}")
        }
    }

    @Test
    fun fixedNativeBarsKeepDockOffsetWithoutChromeUpdatesAndHonorRefreshAndRevealLock() {
        val globalOffset = mutableFloatStateOf(0f)
        val locked = mutableStateOf(false)
        val refreshing = mutableStateOf(false)
        val chromeUpdates = mutableListOf<String>()
        lateinit var connection: NestedScrollConnection
        rule.setContent {
            val pager = rememberPagerState(pageCount = { 1 })
            val subscription = rememberLazyStaggeredGridState()
            val feed = rememberHomeFeedGridState(regularLayout = true)
            connection = rememberHomeHeaderScrollConnection(
                configuration = HomeHeaderScrollConfiguration(
                    collapseEnabled = false,
                    collapseDistancePx = 0f,
                    collapseTabs = false,
                    bottomBarAutoHideEnabled = false,
                    useSideNavigation = false,
                    liquidGlassEnabled = false,
                    hideType = HomeBarHideType.SYNC,
                ),
                pagerState = pager,
                topTabEntries = listOf(HomeTopTabEntry.Category(HomeCategory.RECOMMEND)),
                activeGridStateProvider = { feed },
                subscriptionListState = subscription,
                revealLocked = locked.value,
                refreshInteractionActiveProvider = { refreshing.value },
                headerOffsetProvider = { 0f },
                globalScrollOffset = globalOffset,
                onHeaderOffsetChanged = { _, _ -> chromeUpdates.add("header") },
                onTabsCollapsedChanged = { chromeUpdates.add("tabs") },
                onBottomBarVisibleChanged = { chromeUpdates.add("bottom") },
            )
        }
        rule.runOnIdle {
            assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -18f), NestedScrollSource.UserInput))
            connection.onPreScroll(Offset(0f, -12f), NestedScrollSource.SideEffect)
            assertEquals(30f, globalOffset.floatValue, 0f)
            assertTrue(chromeUpdates.isEmpty())
            connection.onPreScroll(Offset(20f, -2f), NestedScrollSource.UserInput)
            assertEquals(30f, globalOffset.floatValue, 0f)
            locked.value = true
        }
        rule.runOnIdle {
            connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
            assertEquals(30f, globalOffset.floatValue, 0f)
            assertTrue(chromeUpdates.isEmpty())
            locked.value = false
            refreshing.value = true
        }
        rule.runOnIdle {
            connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
            assertEquals(30f, globalOffset.floatValue, 0f)
            assertEquals(listOf("header", "tabs"), chromeUpdates)
            refreshing.value = false
        }
        rule.runOnIdle {
            connection.onPreScroll(Offset(0f, 8f), NestedScrollSource.UserInput)
            assertEquals(22f, globalOffset.floatValue, 0f)
        }
    }

    @Test
    fun headerScrollKeepsFeedScrollUnconsumedAndHonorsRevealLock() {
        val headerOffset = mutableFloatStateOf(0f)
        val globalOffset = mutableFloatStateOf(0f)
        val revealLocked = mutableStateOf(false)
        var tabsCollapsed = false
        val bottomBarVisibility = mutableListOf<Boolean>()
        lateinit var connection: NestedScrollConnection
        rule.setContent {
            val pager = rememberPagerState(pageCount = { 1 })
            val grid = rememberLazyStaggeredGridState()
            val feedGrid = rememberHomeFeedGridState(regularLayout = true)
            connection = rememberHomeHeaderScrollConnection(
                configuration = HomeHeaderScrollConfiguration(
                    collapseEnabled = true,
                    collapseDistancePx = 100f,
                    collapseTabs = true,
                    bottomBarAutoHideEnabled = true,
                    useSideNavigation = false,
                    liquidGlassEnabled = false,
                    hideType = HomeBarHideType.SYNC,
                ),
                pagerState = pager,
                topTabEntries = listOf(HomeTopTabEntry.Category(HomeCategory.RECOMMEND)),
                activeGridStateProvider = { feedGrid },
                subscriptionListState = grid,
                revealLocked = revealLocked.value,
                headerOffsetProvider = { headerOffset.floatValue },
                globalScrollOffset = globalOffset,
                onHeaderOffsetChanged = { value, _ -> headerOffset.floatValue = value },
                onTabsCollapsedChanged = { tabsCollapsed = it },
                onBottomBarVisibleChanged = { bottomBarVisibility.add(it) },
            )
        }
        rule.runOnIdle {
            assertEquals(
                Offset.Zero,
                connection.onPreScroll(Offset(0f, -15f), NestedScrollSource.UserInput),
            )
            assertEquals(-15f, headerOffset.floatValue, 0f)
            assertTrue(tabsCollapsed)
            assertEquals(listOf(false), bottomBarVisibility)

            connection.onPreScroll(Offset(20f, -5f), NestedScrollSource.UserInput)
            assertEquals(-15f, headerOffset.floatValue, 0f)
            assertEquals(1, bottomBarVisibility.size)
            // 列表已贴顶后，尾帧剩余位移也能展开，覆盖手势与惯性两条路径。
            assertEquals(
                Offset(0f, 6f),
                connection.onPostScroll(Offset.Zero, Offset(0f, 6f), NestedScrollSource.SideEffect),
            )
            assertEquals(-9f, headerOffset.floatValue, 0f)
            assertEquals(
                Offset(0f, 9f),
                connection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput),
            )
            assertEquals(0f, headerOffset.floatValue, 0f)
            assertEquals(false, tabsCollapsed)
            headerOffset.floatValue = -15f
            revealLocked.value = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            connection.onPreScroll(Offset(0f, 30f), NestedScrollSource.UserInput)
            assertEquals(-15f, headerOffset.floatValue, 0f)
            assertEquals(1, bottomBarVisibility.size)
        }
    }
    @Test
    fun switchingPagesPreservesHeaderAndUsesOnlyVisiblePageRefreshState() {
        val headerOffset = mutableFloatStateOf(-40f)
        val globalOffset = mutableFloatStateOf(0f)
        val refreshByPage = listOf(mutableStateOf(true), mutableStateOf(false))
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        lateinit var connection: NestedScrollConnection
        var switchChecked = false
        rule.setContent {
            pager = rememberPagerState(pageCount = { 2 })
            scope = rememberCoroutineScope()
            val grid = rememberLazyStaggeredGridState()
            val feedGrid = rememberHomeFeedGridState(regularLayout = true)
            connection = rememberHomeHeaderScrollConnection(
                configuration = HomeHeaderScrollConfiguration(
                    collapseEnabled = true,
                    collapseDistancePx = 100f,
                    collapseTabs = false,
                    bottomBarAutoHideEnabled = false,
                    useSideNavigation = false,
                    liquidGlassEnabled = false,
                    hideType = HomeBarHideType.SYNC,
                ),
                pagerState = pager,
                topTabEntries = listOf(
                    HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
                    HomeTopTabEntry.Category(HomeCategory.POPULAR),
                ),
                activeGridStateProvider = { feedGrid },
                subscriptionListState = grid,
                revealLocked = false,
                refreshInteractionActiveProvider = { refreshByPage[pager.currentPage].value },
                headerOffsetProvider = { headerOffset.floatValue },
                globalScrollOffset = globalOffset,
                onHeaderOffsetChanged = { value, _ -> headerOffset.floatValue = value },
                onTabsCollapsedChanged = {},
                onBottomBarVisibleChanged = {},
            )
        }
        rule.runOnIdle {
            scope.launch {
                pager.scroll {
                    connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput)
                    connection.onPostScroll(Offset.Zero, Offset(0f, 20f), NestedScrollSource.SideEffect)
                    assertEquals(-40f, headerOffset.floatValue, 0f)
                }
                pager.scrollToPage(1)
                assertEquals(-40f, headerOffset.floatValue, 0f)
                // 推荐页仍在刷新，热门页的一次拖动不能被旧页刷新强制展开。
                connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
                assertEquals(-50f, headerOffset.floatValue, 0f)
                refreshByPage[1].value = true
                connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.UserInput)
                assertEquals(0f, headerOffset.floatValue, 0f)
                switchChecked = true
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(switchChecked) }
    }

}
