package com.android.purebilibili.feature.space

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.common.contentTabPagerGestures
import com.android.purebilibili.core.ui.common.rememberContentTabPager
import com.android.purebilibili.core.ui.common.stationaryPagerHeader
import com.android.purebilibili.core.ui.components.AppLazyVerticalStaggeredGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 用真实分页和瀑布流验证非玻璃标签的跨页位置，目标投稿页预留深层滚动历史。 */
@RunWith(Parameterized::class)
class SpacePageHeaderAlignmentUiTest(private val gridColumns: Int) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "columns={0}")
        fun columns(): List<Array<Int>> = listOf(arrayOf(2), arrayOf(4))
    }

    @get:Rule val rule = createComposeRule()
    private lateinit var pager: PagerState
    private lateinit var grids: List<LazyStaggeredGridState>

    private fun showSpacePages(initialPage: Int, targetInitialIndex: Int) {
        rule.setContent {
            val selectedPage = remember { mutableIntStateOf(initialPage) }
            grids = remember {
                List(3) { page ->
                    LazyStaggeredGridState(
                        initialFirstVisibleItemIndex = if (page == 2) targetInitialIndex else 1,
                        initialFirstVisibleItemScrollOffset = if (page == initialPage) 12 else 0,
                    )
                }
            }
            pager = rememberContentTabPager(
                pageCount = grids.size,
                selectedPage = selectedPage.intValue,
                onPageSelected = { selectedPage.intValue = it },
                prepareNeighbors = { source ->
                    grids.forEachIndexed { index, target ->
                        if (index != source) alignSpacePageHeader(grids[source], target)
                    }
                },
            )
            // 非玻璃路径中每页持有自己的主标签，并抵消横向分页位移。
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier
                    .size(300.dp, 400.dp)
                    .testTag("space-pager")
                    .contentTabPagerGestures(
                        pager = pager,
                        enabled = true,
                        contentTopPx = {
                            val info = grids[pager.currentPage].layoutInfo
                            val tabs = info.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }
                            tabs?.let { (it.offset.y + info.beforeContentPadding + it.size.height).toFloat() }
                                ?: 0f
                        },
                    ),
            ) { page ->
                AppLazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(gridColumns),
                    state = grids[page],
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                        Box(Modifier.fillMaxWidth().height(200.dp))
                    }
                    item(key = "space_tabs", span = StaggeredGridItemSpan.FullLine) {
                        Box(Modifier.fillMaxWidth()) {
                            BasicText(
                                text = "投稿入口",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .stationaryPagerHeader(pager, page)
                                    .testTag("tabs_$page")
                                    .clickable { selectedPage.intValue = 2 },
                            )
                        }
                    }
                    items(count = 80, key = { "video_$it" }, contentType = { "video" }) {
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
    }

    @Test
    fun clickingFreshContributionKeepsPartiallyScrolledTabsAtTheSameOffset() {
        showSpacePages(initialPage = 0, targetInitialIndex = 0)
        val originalTop = rule.onNodeWithTag("tabs_0").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("tabs_0").assertIsDisplayed().performClick()
        assertContributionTabsAligned(originalTop)
    }

    @Test
    fun swipingToContributionKeepsTheSameTabOffsetOverOldContentHistory() {
        showSpacePages(initialPage = 1, targetInitialIndex = 12)
        val originalTop = rule.onNodeWithTag("tabs_1").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("space-pager").performTouchInput {
            down(Offset(width * 0.8f, height * 0.75f))
            moveBy(Offset(-width * 0.65f, 0f), delayMillis = 200)
            advanceEventTime(500)
            up()
        }
        assertContributionTabsAligned(originalTop)
    }

    private fun assertContributionTabsAligned(originalTop: Float) {
        rule.onNodeWithTag("tabs_2").assertIsDisplayed()
        val currentTop = rule.onNodeWithTag("tabs_2").fetchSemanticsNode().boundsInRoot.top
        assertEquals(originalTop, currentTop, 1f)
        rule.runOnIdle {
            assertEquals(2, pager.settledPage)
            assertEquals(1, grids[2].firstVisibleItemIndex)
            assertEquals(12, grids[2].firstVisibleItemScrollOffset)
            assertTrue(grids[2].layoutInfo.visibleItemsInfo.any { it.key == "space_tabs" })
        }
    }
}
