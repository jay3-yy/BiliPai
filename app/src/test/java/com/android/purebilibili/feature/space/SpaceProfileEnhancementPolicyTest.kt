package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.SpaceAggregateTab
import com.android.purebilibili.data.model.response.SpaceAggregateTabItem
import com.android.purebilibili.feature.space.SpaceMainTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaceProfileEnhancementPolicyTest {

    @Test
    fun `discovered opus content adds the missing article contribution tab`() {
        val tabs = ensureSpaceContributionTabsForAvailableContent(
            tabs = listOf(
                SpaceContributionTab(
                    id = "video",
                    title = "视频",
                    subTab = SpaceSubTab.VIDEO,
                    param = "video"
                )
            ),
            hasArticles = true
        )

        assertEquals(listOf(SpaceSubTab.VIDEO, SpaceSubTab.ARTICLE), tabs.map { it.subTab })
    }

    @Test
    fun `shouldEnableSpaceTopPhotoPreview only when url is not blank`() {
        assertTrue(shouldEnableSpaceTopPhotoPreview("https://i0.hdslb.com/bfs/space/demo.jpg"))
        assertFalse(shouldEnableSpaceTopPhotoPreview(""))
        assertFalse(shouldEnableSpaceTopPhotoPreview("   "))
    }

    @Test
    fun `resolveSpaceFavoriteFoldersForDisplay filters invalid and deduplicates by id`() {
        val folders = listOf(
            FavFolder(id = 1L, title = "默认收藏夹", media_count = 8),
            FavFolder(id = 0L, title = "无效", media_count = 2),
            FavFolder(id = 2L, title = "", media_count = 2),
            FavFolder(id = 3L, title = "空夹", media_count = 0),
            FavFolder(id = 1L, title = "重复id", media_count = 99),
            FavFolder(id = 4L, title = "公开收藏", media_count = 3)
        )

        val result = resolveSpaceFavoriteFoldersForDisplay(folders)

        assertEquals(listOf(1L, 4L), result.map { it.id })
        assertEquals(listOf("默认收藏夹", "公开收藏"), result.map { it.title })
    }

    @Test
    fun `resolveSpaceCollectionTabCount includes season series and favorite folders`() {
        val count = resolveSpaceCollectionTabCount(
            seasonCount = 2,
            seriesCount = 1,
            createdFavoriteCount = 3,
            collectedFavoriteCount = 4
        )
        assertEquals(10, count)
    }

    @Test
    fun `resolveSpaceTopPhoto picks first non blank source`() {
        assertEquals(
            "top_primary",
            resolveSpaceTopPhoto(
                topPhoto = "top_primary",
                cardLargePhoto = "card_large",
                cardSmallPhoto = "card_small"
            )
        )
        assertEquals(
            "card_large",
            resolveSpaceTopPhoto(
                topPhoto = " ",
                cardLargePhoto = "card_large",
                cardSmallPhoto = "card_small"
            )
        )
        assertEquals(
            "card_small",
            resolveSpaceTopPhoto(
                topPhoto = "",
                cardLargePhoto = " ",
                cardSmallPhoto = "card_small"
            )
        )
        assertEquals(
            "",
            resolveSpaceTopPhoto(
                topPhoto = "",
                cardLargePhoto = "",
                cardSmallPhoto = " "
            )
        )
    }

    @Test
    fun `resolveSpaceTopPhoto skips invalid placeholders and falls back to card photos`() {
        assertEquals(
            "https://i0.hdslb.com/bfs/space/cover_large.jpg",
            resolveSpaceTopPhoto(
                topPhoto = "null",
                cardLargePhoto = "https://i0.hdslb.com/bfs/space/cover_large.jpg",
                cardSmallPhoto = "https://i0.hdslb.com/bfs/space/cover_small.jpg"
            )
        )
    }

    @Test
    fun `normalizeSpaceTopPhotoUrl rejects extra placeholder payloads`() {
        assertEquals("", normalizeSpaceTopPhotoUrl("[]"))
        assertEquals("", normalizeSpaceTopPhotoUrl("{}"))
        assertEquals("", normalizeSpaceTopPhotoUrl("N/A"))
        assertEquals("", normalizeSpaceTopPhotoUrl("about:blank"))
    }

    @Test
    fun `buildInitialTabShellState populates each tab`() {
        val shell = buildInitialTabShellState(selectedTab = SpaceMainTab.DYNAMIC)

        assertEquals(SpaceMainTab.DYNAMIC, shell.selectedTab)
        assertEquals(7, shell.tabStates.size)
        assertFalse(shell.tabStates[SpaceMainTab.HOME]?.hasLoaded ?: true)
        assertFalse(shell.tabStates[SpaceMainTab.CHEESE]?.hasLoaded ?: true)
    }

    @Test
    fun `buildHeaderState collects favorite folders`() {
        val header = buildHeaderState(
            userInfo = null,
            relationStat = null,
            upStat = null,
            topVideo = null,
            notice = "notice",
            createdFavorites = listOf(FavFolder(id = 1, title = "c", media_count = 1)),
            collectedFavorites = listOf(FavFolder(id = 2, title = "col", media_count = 2))
        )

        assertEquals("notice", header.notice)
        assertEquals(1, header.createdFavorites.size)
        assertEquals(2, header.collectedFavorites.first().id)
    }

    @Test
    fun `resolveSpaceMainTabs and contribution tabs follow tab2 payload`() {
        val tab2 = listOf(
            SpaceAggregateTab(title = "主页", param = "home"),
            SpaceAggregateTab(title = "动态", param = "dynamic"),
            SpaceAggregateTab(
                title = "投稿",
                param = "contribute",
                items = listOf(
                    SpaceAggregateTabItem(title = "视频", param = "video"),
                    SpaceAggregateTabItem(title = "图文", param = "article"),
                    SpaceAggregateTabItem(title = "赛季", param = "season_video", seasonId = 12L),
                    SpaceAggregateTabItem(title = "系列", param = "series", seriesId = 34L)
                )
            ),
            SpaceAggregateTab(title = "收藏", param = "favorite")
        )

        val mainTabs = resolveSpaceMainTabs(tab2)
        val contributionTabs = resolveSpaceContributionTabs(tab2)

        assertEquals(
            listOf(
                SpaceMainTab.HOME,
                SpaceMainTab.DYNAMIC,
                SpaceMainTab.CONTRIBUTION,
                SpaceMainTab.COLLECTIONS,
                SpaceMainTab.FAVORITE,
                SpaceMainTab.BANGUMI,
            ),
            mainTabs.map { it.tab }
        )
        assertEquals(listOf("视频", "图文", "赛季", "系列"), contributionTabs.map { it.title })
        assertEquals(SpaceSubTab.SEASON_VIDEO, contributionTabs[2].subTab)
        assertEquals(12L, contributionTabs[2].seasonId)
        assertEquals(SpaceSubTab.SERIES, contributionTabs[3].subTab)
        assertEquals(34L, contributionTabs[3].seriesId)
    }

    @Test
    fun `library destinations stay primary before and after remote tabs arrive`() {
        val expected = listOf(
            SpaceMainTab.HOME,
            SpaceMainTab.DYNAMIC,
            SpaceMainTab.CONTRIBUTION,
            SpaceMainTab.COLLECTIONS,
            SpaceMainTab.FAVORITE,
            SpaceMainTab.BANGUMI,
        )
        val remoteTabs = listOf(
            SpaceMainTabItem(SpaceMainTab.BANGUMI, "订阅番剧"),
            SpaceMainTabItem(SpaceMainTab.CONTRIBUTION, "作品"),
            SpaceMainTabItem(SpaceMainTab.FAVORITE, "公开收藏"),
        )

        for (selected in expected) {
            val loadingTabs = resolveSpaceDisplayedMainTabs(emptyList(), selectedTab = selected)
            val loadedTabs = resolveSpaceDisplayedMainTabs(
                resolveSpaceMainTabs(remoteTabs.map { item ->
                    SpaceAggregateTab(
                        title = item.title,
                        param = when (item.tab) {
                            SpaceMainTab.BANGUMI -> "bangumi"
                            SpaceMainTab.FAVORITE -> "favorite"
                            else -> "contribute"
                        },
                    )
                }),
                selectedTab = selected,
            )
            assertEquals(expected, loadingTabs.map { it.tab })
            assertEquals(expected, loadedTabs.map { it.tab })
            assertEquals(expected.indexOf(selected), resolveSpaceMainTabChromeSpec(loadedTabs, selected).selectedIndex)
            assertEquals("公开收藏", loadedTabs.first { it.tab == SpaceMainTab.FAVORITE }.title)
        }
    }


    @Test
    fun `optional classroom does not displace library destinations`() {
        val selected = SpaceMainTab.CHEESE
        val tabs = resolveSpaceDisplayedMainTabs(
            tabs = listOf(SpaceMainTabItem(selected, "课程")),
            selectedTab = selected,
        )
        assertEquals(
            listOf("主页", "动态", "投稿", "合集", "收藏", "追番", "课程"),
            tabs.map { it.title },
        )
        assertEquals(
            SpaceMainTab.CHEESE,
            resolveSpaceDisplayedMainTabs(emptyList(), selectedTab = selected).last().tab,
        )
    }

    @Test
    fun `saved library tab ids still select their own primary destination`() {
        val savedTabs = listOf(
            3 to SpaceMainTab.FAVORITE,
            4 to SpaceMainTab.BANGUMI,
            5 to SpaceMainTab.COLLECTIONS,
        )
        for ((savedId, destination) in savedTabs) {
            val selected = tabIndexToMainTab(savedId)
            val displayed = resolveSpaceDisplayedMainTabs(emptyList(), selectedTab = selected)
            val chrome = resolveSpaceMainTabChromeSpec(displayed, selected)
            assertEquals(destination, displayed[chrome.selectedIndex].tab)
            assertEquals(savedId, mainTabToTabIndex(selected))
        }
    }

    @Test
    fun `resolveDisplayedSpaceContributionTabs hides empty audio tabs`() {
        val tabs = listOf(
            SpaceContributionTab(id = "video", title = "视频", subTab = SpaceSubTab.VIDEO, param = "video"),
            SpaceContributionTab(id = "article", title = "图文", subTab = SpaceSubTab.ARTICLE, param = "article"),
            SpaceContributionTab(id = "audio", title = "音频", subTab = SpaceSubTab.AUDIO, param = "audio")
        )

        assertEquals(
            listOf("视频", "图文"),
            resolveDisplayedSpaceContributionTabs(tabs, totalAudios = 0).map { it.title }
        )
        assertEquals(
            listOf("视频", "图文", "音频"),
            resolveDisplayedSpaceContributionTabs(tabs, totalAudios = 3).map { it.title }
        )
    }
}
