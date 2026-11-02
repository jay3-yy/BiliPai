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
    fun `shop selection survives the saved main tab index`() {
        assertEquals(SpaceMainTab.SHOP, tabIndexToMainTab(mainTabToTabIndex(SpaceMainTab.SHOP)))
    }

    @Test
    fun `article opus and opus filters survive collection tab enrichment`() {
        val tabs = resolveSpaceContributionTabs(listOf(SpaceAggregateTab(param = "contribute", items = listOf(
            SpaceAggregateTabItem(param = "video", title = "视频"),
            SpaceAggregateTabItem(param = "article", title = "专栏"),
            SpaceAggregateTabItem(param = "opus", title = "图文", filter = listOf(
                com.android.purebilibili.data.model.response.SpaceOpusFilter(text = "全部图文", meta = "all"),
                com.android.purebilibili.data.model.response.SpaceOpusFilter(text = "专栏", meta = "article"),
            )),
        ))))
        val merged = mergeSpaceContributionTabsWithCollections(tabs, emptyList(), emptyList())
        assertEquals(listOf(SpaceSubTab.VIDEO, SpaceSubTab.ARTICLE, SpaceSubTab.OPUS), merged.map { it.subTab })
        assertEquals(listOf("all", "article"), merged.last().filters.map { it.meta })
    }


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
        assertFalse(shell.tabStates[SpaceMainTab.SHOP]?.hasLoaded ?: true)
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
    fun `resolveSpaceDisplayedMainTabs keeps only PiliPlus primary destinations and cheese when present`() {
        val tabs = listOf(
            SpaceMainTabItem(SpaceMainTab.HOME, "主页"),
            SpaceMainTabItem(SpaceMainTab.DYNAMIC, "动态"),
            SpaceMainTabItem(SpaceMainTab.CONTRIBUTION, "投稿"),
            SpaceMainTabItem(SpaceMainTab.COLLECTIONS, "合集"),
            SpaceMainTabItem(SpaceMainTab.FAVORITE, "收藏"),
            SpaceMainTabItem(SpaceMainTab.BANGUMI, "追番"),
            SpaceMainTabItem(SpaceMainTab.CHEESE, "课堂")
        )
        val expected = listOf(
            SpaceMainTab.HOME,
            SpaceMainTab.DYNAMIC,
            SpaceMainTab.CONTRIBUTION,
            SpaceMainTab.CHEESE,
        )

        assertEquals(
            expected,
            resolveSpaceDisplayedMainTabs(tabs, selectedTab = SpaceMainTab.HOME).map { it.tab }
        )
        assertEquals(
            expected,
            resolveSpaceDisplayedMainTabs(tabs, selectedTab = SpaceMainTab.COLLECTIONS).map { it.tab }
        )
        assertEquals(
            expected,
            resolveSpaceDisplayedMainTabs(tabs, selectedTab = SpaceMainTab.CHEESE).map { it.tab }
        )
    }

    @Test
    fun `resolveSpacePrimaryTab maps library tabs back to contribution but keeps cheese primary`() {
        assertEquals(SpaceMainTab.CONTRIBUTION, resolveSpacePrimaryTab(SpaceMainTab.FAVORITE))
        assertEquals(SpaceMainTab.CONTRIBUTION, resolveSpacePrimaryTab(SpaceMainTab.BANGUMI))
        assertEquals(SpaceMainTab.CONTRIBUTION, resolveSpacePrimaryTab(SpaceMainTab.COLLECTIONS))
        assertEquals(SpaceMainTab.CHEESE, resolveSpacePrimaryTab(SpaceMainTab.CHEESE))
        assertEquals(SpaceMainTab.DYNAMIC, resolveSpacePrimaryTab(SpaceMainTab.DYNAMIC))
    }

    @Test
    fun `resolveSpaceSecondarySwitchItems appends library destinations after contribution tabs`() {
        val items = resolveSpaceSecondarySwitchItems(buildDefaultSpaceContributionTabs())

        assertEquals(
            listOf("video", "article", "audio", SPACE_SECONDARY_COLLECTIONS_ID, SPACE_SECONDARY_FAVORITE_ID, SPACE_SECONDARY_BANGUMI_ID),
            items.map { it.id }
        )
        assertEquals(SpaceMainTab.CONTRIBUTION, items.first().targetTab)
        assertEquals(SpaceMainTab.COLLECTIONS, items.first { it.id == SPACE_SECONDARY_COLLECTIONS_ID }.targetTab)
        assertEquals(SpaceMainTab.FAVORITE, items.first { it.id == SPACE_SECONDARY_FAVORITE_ID }.targetTab)
        assertEquals(SpaceMainTab.BANGUMI, items.first { it.id == SPACE_SECONDARY_BANGUMI_ID }.targetTab)
    }

    @Test
    fun `resolveSpaceSecondarySwitchItems includes cheese when available`() {
        val items = resolveSpaceSecondarySwitchItems(
            contributionTabs = buildDefaultSpaceContributionTabs(),
            hasCheese = true
        )

        assertTrue(items.any { it.id == SPACE_SECONDARY_CHEESE_ID && it.targetTab == SpaceMainTab.CHEESE && it.title == "课堂" })
        assertEquals(
            listOf("video", "article", "audio", SPACE_SECONDARY_COLLECTIONS_ID, SPACE_SECONDARY_FAVORITE_ID, SPACE_SECONDARY_BANGUMI_ID, SPACE_SECONDARY_CHEESE_ID),
            items.map { it.id }
        )
    }

    @Test
    fun `resolveSelectedSpaceSecondarySwitchId tracks library tabs`() {
        assertEquals(
            SPACE_SECONDARY_FAVORITE_ID,
            resolveSelectedSpaceSecondarySwitchId(
                selectedTab = SpaceMainTab.FAVORITE,
                selectedContributionTabId = "video",
            )
        )
        assertEquals(
            SPACE_SECONDARY_CHEESE_ID,
            resolveSelectedSpaceSecondarySwitchId(
                selectedTab = SpaceMainTab.CHEESE,
                selectedContributionTabId = "video",
            )
        )
        assertEquals(
            "article",
            resolveSelectedSpaceSecondarySwitchId(
                selectedTab = SpaceMainTab.CONTRIBUTION,
                selectedContributionTabId = "article",
            )
        )
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
