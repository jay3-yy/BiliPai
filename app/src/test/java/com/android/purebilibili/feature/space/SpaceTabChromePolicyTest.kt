package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.VideoSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaceTabChromePolicyTest {

    @Test
    fun `main tab chrome resolves selected index and keeps touch target height`() {
        val tabs = listOf(
            SpaceMainTabItem(SpaceMainTab.HOME, "主页"),
            SpaceMainTabItem(SpaceMainTab.DYNAMIC, "动态"),
            SpaceMainTabItem(SpaceMainTab.CONTRIBUTION, "投稿")
        )

        val spec = resolveSpaceMainTabChromeSpec(
            tabs = tabs,
            selectedTab = SpaceMainTab.DYNAMIC
        )

        assertEquals(1, spec.selectedIndex)
        assertFalse(spec.scrollable)
        assertEquals(40, spec.heightDp)
        assertTrue(spec.indicatorHeightDp < spec.heightDp)
        assertFalse(spec.liquidGlassEffectsEnabled)
        assertTrue(spec.dragSelectionEnabled)
    }

    @Test
    fun `main tab chrome falls back to first item when selected tab is hidden`() {
        val spec = resolveSpaceMainTabChromeSpec(
            tabs = listOf(SpaceMainTabItem(SpaceMainTab.HOME, "主页")),
            selectedTab = SpaceMainTab.COLLECTIONS
        )

        assertEquals(0, spec.selectedIndex)
    }

    @Test
    fun `contribution tab chrome prefers selected id and uses compact secondary chrome`() {
        val tabs = listOf(
            SpaceContributionTab(
                id = "video",
                title = "视频",
                subTab = SpaceSubTab.VIDEO,
                param = "video"
            ),
            SpaceContributionTab(
                id = "article",
                title = "图文",
                subTab = SpaceSubTab.ARTICLE,
                param = "article"
            ),
            SpaceContributionTab(
                id = "season_video:season:1",
                title = "很长的合集标题",
                subTab = SpaceSubTab.SEASON_VIDEO,
                param = "season_video",
                seasonId = 1L
            ),
            SpaceContributionTab(
                id = "series:series:2",
                title = "系列",
                subTab = SpaceSubTab.SERIES,
                param = "series",
                seriesId = 2L
            )
        )
        val mainSpec = resolveSpaceMainTabChromeSpec(
            tabs = buildDefaultSpaceMainTabs().take(3),
            selectedTab = SpaceMainTab.DYNAMIC
        )

        val spec = resolveSpaceContributionTabChromeSpec(
            tabs = tabs,
            selectedTabId = "season_video:season:1",
            selectedSubTab = SpaceSubTab.VIDEO
        )

        assertEquals(2, spec.selectedIndex)
        assertTrue(spec.scrollable)
        assertTrue((spec.itemWidthDp ?: 0) > 104)
        assertTrue(spec.indicatorHeightDp < spec.heightDp)
        assertEquals(mainSpec.horizontalPaddingDp, spec.horizontalPaddingDp)
        assertTrue(spec.liquidGlassEffectsEnabled)
        assertFalse(spec.dragSelectionEnabled)
    }

    @Test
    fun `single contribution video tab uses compact item width instead of full row`() {
        val spec = resolveSpaceContributionTabChromeSpec(
            tabs = listOf(
                SpaceContributionTab(
                    id = "video",
                    title = "视频",
                    subTab = SpaceSubTab.VIDEO,
                    param = "video"
                )
            ),
            selectedTabId = "video",
            selectedSubTab = SpaceSubTab.VIDEO
        )

        assertFalse(spec.scrollable)
        assertEquals(104, spec.itemWidthDp)
        assertFalse(spec.dragSelectionEnabled)
    }

    @Test
    fun `contribution tab chrome falls back to selected sub tab`() {
        val tabs = buildDefaultSpaceContributionTabs()

        val spec = resolveSpaceContributionTabChromeSpec(
            tabs = tabs,
            selectedTabId = "missing",
            selectedSubTab = SpaceSubTab.AUDIO
        )

        assertEquals(tabs.indexOfFirst { it.subTab == SpaceSubTab.AUDIO }, spec.selectedIndex)
    }

    @Test
    fun `contribution tab chrome keeps drag selection for three contribution entries`() {
        val tabs = listOf(
            SpaceContributionTab(
                id = "video",
                title = "视频",
                subTab = SpaceSubTab.VIDEO,
                param = "video"
            ),
            SpaceContributionTab(
                id = "article",
                title = "图文",
                subTab = SpaceSubTab.ARTICLE,
                param = "article"
            ),
            SpaceContributionTab(
                id = "season_video:season:1",
                title = "合集 · 美食对决合集视频",
                subTab = SpaceSubTab.SEASON_VIDEO,
                param = "season_video",
                seasonId = 1L
            )
        )

        val spec = resolveSpaceContributionTabChromeSpec(
            tabs = tabs,
            selectedTabId = "season_video:season:1",
            selectedSubTab = SpaceSubTab.VIDEO
        )

        assertFalse(spec.scrollable)
        assertTrue((spec.itemWidthDp ?: 0) > 104)
        assertTrue(spec.dragSelectionEnabled)
    }


    @Test
    fun `dragged secondary indicator asks rail to follow only near viewport edges`() {
        assertEquals(
            0f,
            resolveSpaceSecondarySwitchDragScrollDeltaPx(1f, 104f, 360f, 0f, 4f, 12f)
        )
        assertEquals(
            72f,
            resolveSpaceSecondarySwitchDragScrollDeltaPx(3f, 104f, 360f, 0f, 4f, 12f)
        )
        assertEquals(
            -8f,
            resolveSpaceSecondarySwitchDragScrollDeltaPx(0f, 104f, 360f, 0f, 4f, 12f)
        )
    }

    @Test
    fun `contribution toolbar uses compact chrome and icon first actions on narrow phones`() {
        val spec = resolveSpaceContributionToolbarSpec(
            widthDp = 412,
            selectedSubTab = SpaceSubTab.VIDEO,
            tabCount = 2,
            selectedTitle = "视频"
        )

        assertEquals(40, spec.tabHeightDp)
        assertEquals(34, spec.tabIndicatorHeightDp)
        assertEquals(40, spec.expandedTabRailHeightDp)
        assertEquals(88, spec.collapsedTabWidthDp)
        assertTrue(spec.showVideoActions)
        assertFalse(spec.showTotalText)
        assertFalse(spec.showPlayAllText)
        assertFalse(spec.showSortText)
        // Keep categories expanded so 视频/图文/音频 stay discoverable (BiliPai-like).
        assertFalse(spec.collapseAfterTabSelection)
    }

    @Test
    fun `contribution toolbar hides video actions for article tabs`() {
        val spec = resolveSpaceContributionToolbarSpec(
            widthDp = 412,
            selectedSubTab = SpaceSubTab.ARTICLE,
            tabCount = 2
        )

        assertFalse(spec.showVideoActions)
        assertFalse(spec.showPlayAllText)
        assertFalse(spec.showSortText)
    }

    @Test
    fun `collapsed contribution tab width stays compact for short and long titles`() {
        assertEquals(88, resolveSpaceContributionCollapsedTabWidthDp("视频", widthDp = 412))
        assertEquals(104, resolveSpaceContributionCollapsedTabWidthDp("season_video", widthDp = 412))
        assertEquals(156, resolveSpaceContributionCollapsedTabWidthDp("合集 · 美食对决合集视频", widthDp = 412))
    }

    @Test
    fun `space video sort compact labels stay short`() {
        VideoSortOrder.entries.forEach { order ->
            val label = resolveSpaceVideoSortCompactLabel(order)

            assertTrue(label.length <= 2)
        }
        assertEquals("最新", resolveSpaceVideoSortCompactLabel(VideoSortOrder.PUBDATE))
        assertEquals("最早", resolveSpaceVideoSortCompactLabel(VideoSortOrder.OLDEST_PUBDATE))
        assertEquals("播放", resolveSpaceVideoSortCompactLabel(VideoSortOrder.CLICK))
        assertEquals("收藏", resolveSpaceVideoSortCompactLabel(VideoSortOrder.STOW))
    }
}
