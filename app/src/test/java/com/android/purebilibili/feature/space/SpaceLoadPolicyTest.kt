package com.android.purebilibili.feature.space

import com.android.purebilibili.core.store.HomeFeedCardWidthPreset
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.SeasonArchiveItem
import com.android.purebilibili.data.model.response.SeasonArchiveStat
import com.android.purebilibili.data.model.response.SeasonItem
import com.android.purebilibili.data.model.response.SeasonMeta
import com.android.purebilibili.data.model.response.SeriesArchiveItem
import com.android.purebilibili.data.model.response.SeriesArchiveStat
import com.android.purebilibili.data.model.response.SeriesItem
import com.android.purebilibili.data.model.response.SeriesMeta
import com.android.purebilibili.data.model.response.SpaceAggregateCard
import com.android.purebilibili.data.model.response.SpaceAggregateData
import com.android.purebilibili.data.model.response.SpaceAggregateImages
import com.android.purebilibili.data.model.response.SpaceAggregateRelation
import com.android.purebilibili.data.model.response.SpaceAggregateTab
import com.android.purebilibili.data.model.response.SpaceAggregateArchiveItem
import com.android.purebilibili.data.model.response.SpaceTagItem
import com.android.purebilibili.data.model.response.SpaceTopArcData
import com.android.purebilibili.data.model.response.SpaceUserInfo
import com.android.purebilibili.data.model.response.SpaceVideoItem
import com.android.purebilibili.data.model.response.VideoSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpaceLoadPolicyTest {

    @Test
    fun `aggregate tab metadata does not mark unrequested page content as loaded`() {
        val seed = requireNotNull(resolveSpaceInitialSeedFromAggregate(SpaceAggregateData(
            card = SpaceAggregateCard(mid = "42", name = "UP", face = "face"),
        )))
        for (tab in listOf(SpaceMainTab.HOME, SpaceMainTab.BANGUMI, SpaceMainTab.CHEESE)) {
            val state = buildInitialSpaceSuccessState(seed, selectedMainTab = tab)
            assertFalse(state.tabShellState.tabStates.getValue(tab).hasLoaded)
            assertFalse(isSpacePrimaryContentReady(state))
        }
    }

    @Test
    fun `late user card fills missing visuals without replacing current identity or relation`() {
        val user = SpaceUserInfo(mid = 2, name = "当前UP", isFollowed = true, relationStatus = -10)
        val merged = mergeSpaceUserCardVisuals(user, SpaceUserCardVisuals(
            largePhoto = "https://example.com/banner.jpg", ipLocation = "上海",
        ))
        assertEquals(user.mid, merged.mid)
        assertEquals(user.name, merged.name)
        assertTrue(merged.isFollowed)
        assertEquals(-10, merged.relationStatus)
        assertEquals("https://example.com/banner.jpg", merged.topPhoto)
        assertEquals("上海", merged.ipLocation)
        assertEquals(listOf(SpaceTagItem(type = "location", title = "IP属地：上海")), merged.spaceTags)
    }

    @Test
    fun `late user card preserves existing banner location and tags`() {
        val user = SpaceUserInfo(
            mid = 1, topPhoto = "https://example.com/current.jpg", ipLocation = "北京",
            spaceTags = listOf(SpaceTagItem(type = "location", title = "IP属地：北京"),
                SpaceTagItem(type = "real_name", title = "已实名认证")),
        )
        assertEquals(user, mergeSpaceUserCardVisuals(user, SpaceUserCardVisuals(
            largePhoto = "https://example.com/old.jpg", ipLocation = "上海",
        )))
        assertEquals(user, mergeSpaceUserCardVisuals(user, SpaceUserCardVisuals()))
    }

    @Test
    fun `aggregate video target falls back to bilibili video deep link when bvid is absent`() {
        assertEquals(
            "av2001",
            resolveSpaceAggregateVideoId(
                SpaceAggregateArchiveItem(
                    aid = 2001L,
                    uri = "bilibili://video/2001",
                    goto = "av",
                )
            )
        )
    }

    @Test
    fun `aggregate video target falls back to numeric archive param`() {
        assertEquals(
            "av2002",
            resolveSpaceAggregateVideoId(
                SpaceAggregateArchiveItem(
                    aid = 2002L,
                    param = "2002",
                    goto = "av",
                )
            )
        )
    }

    @Test
    fun `aggregate pgc item is not mistaken for a regular video`() {
        assertNull(
            resolveSpaceAggregateVideoId(
                SpaceAggregateArchiveItem(
                    aid = 3001L,
                    param = "3001",
                    goto = "bangumi",
                    uri = "bilibili://bangumi/play/ep3001",
                    isPgc = true,
                )
            )
        )
    }

    @Test
    fun `aggregate lazy keys stay unique when api omits archive ids`() {
        val emptyIdentityItem = SpaceAggregateArchiveItem()

        val firstKey = resolveSpaceAggregateLazyItemKey("coin", 0, emptyIdentityItem)
        val secondKey = resolveSpaceAggregateLazyItemKey("coin", 1, emptyIdentityItem)

        assertEquals("coin_0__0", firstKey)
        assertEquals("coin_0__1", secondKey)
        assertTrue(firstKey != secondKey)
    }

    @Test
    fun `oldest publish sort keeps pubdate api key but exposes dedicated label`() {
        assertEquals("pubdate", VideoSortOrder.OLDEST_PUBDATE.apiValue)
        assertEquals("最早发布", VideoSortOrder.OLDEST_PUBDATE.displayName)
    }

    @Test
    fun `resolveInitialSpaceVideoPage starts from last page for oldest publish sort`() {
        assertEquals(
            4,
            resolveInitialSpaceVideoPage(
                order = VideoSortOrder.OLDEST_PUBDATE,
                totalCount = 95,
                pageSize = 30
            )
        )
        assertEquals(
            1,
            resolveInitialSpaceVideoPage(
                order = VideoSortOrder.PUBDATE,
                totalCount = 95,
                pageSize = 30
            )
        )
    }

    @Test
    fun `resolveNextSpaceVideoPage walks backward for oldest publish sort`() {
        assertEquals(
            3,
            resolveNextSpaceVideoPage(
                order = VideoSortOrder.OLDEST_PUBDATE,
                currentPage = 4,
                totalCount = 95,
                pageSize = 30
            )
        )
        assertNull(
            resolveNextSpaceVideoPage(
                order = VideoSortOrder.OLDEST_PUBDATE,
                currentPage = 1,
                totalCount = 95,
                pageSize = 30
            )
        )
        assertEquals(
            2,
            resolveNextSpaceVideoPage(
                order = VideoSortOrder.PUBDATE,
                currentPage = 1,
                totalCount = 95,
                pageSize = 30
            )
        )
    }

    @Test
    fun `normalizeSpaceVideoPage reverses page items for oldest publish sort`() {
        val videos = listOf(
            SpaceVideoItem(bvid = "BV1", title = "first"),
            SpaceVideoItem(bvid = "BV2", title = "second"),
            SpaceVideoItem(bvid = "BV3", title = "third")
        )

        assertEquals(
            listOf("BV3", "BV2", "BV1"),
            normalizeSpaceVideoPage(
                order = VideoSortOrder.OLDEST_PUBDATE,
                videos = videos
            ).map { it.bvid }
        )
        assertEquals(
            listOf("BV1", "BV2", "BV3"),
            normalizeSpaceVideoPage(
                order = VideoSortOrder.PUBDATE,
                videos = videos
            ).map { it.bvid }
        )
    }

    @Test
    fun resolveSpaceSearchScope_supportsDynamicAndVideoContributionOnly() {
        assertEquals(
            SpaceSearchScope.DYNAMIC,
            resolveSpaceSearchScope(
                selectedMainTab = SpaceMainTab.DYNAMIC,
                selectedSubTab = SpaceSubTab.VIDEO
            )
        )
        assertEquals(
            SpaceSearchScope.VIDEO,
            resolveSpaceSearchScope(
                selectedMainTab = SpaceMainTab.CONTRIBUTION,
                selectedSubTab = SpaceSubTab.VIDEO
            )
        )
        assertEquals(
            SpaceSearchScope.NONE,
            resolveSpaceSearchScope(
                selectedMainTab = SpaceMainTab.CONTRIBUTION,
                selectedSubTab = SpaceSubTab.AUDIO
            )
        )
    }

    @Test
    fun resolveSpaceSearchPlaceholder_matchesSearchScope() {
        assertEquals("搜索 TA 的动态", resolveSpaceSearchPlaceholder(SpaceSearchScope.DYNAMIC))
        assertEquals("搜索 TA 的视频", resolveSpaceSearchPlaceholder(SpaceSearchScope.VIDEO))
        assertEquals("", resolveSpaceSearchPlaceholder(SpaceSearchScope.NONE))
    }

    @Test
    fun resolveSpaceSearchEntryLabel_andVisibility() {
        assertEquals("搜索 TA 的动态", resolveSpaceSearchEntryLabel(SpaceSearchScope.DYNAMIC))
        assertEquals("搜索 TA 的视频", resolveSpaceSearchEntryLabel(SpaceSearchScope.VIDEO))
        assertTrue(shouldShowSpaceSearchEntry(SpaceSearchScope.DYNAMIC, isSearchMode = false))
        assertFalse(shouldShowSpaceSearchEntry(SpaceSearchScope.DYNAMIC, isSearchMode = true))
        assertFalse(shouldShowSpaceSearchEntry(SpaceSearchScope.NONE, isSearchMode = false))
    }

    @Test
    fun resolveSpaceSearchBarGridItemIndex_keepsSearchBarVisibleAfterTopBarClick() {
        assertEquals(
            1,
            resolveSpaceSearchBarGridItemIndex(
                scope = SpaceSearchScope.DYNAMIC,
                hasContributionToolbar = false
            )
        )
        assertEquals(
            1,
            resolveSpaceSearchBarGridItemIndex(
                scope = SpaceSearchScope.VIDEO,
                hasContributionToolbar = true
            )
        )
        assertEquals(
            1,
            resolveSpaceSearchBarGridItemIndex(
                scope = SpaceSearchScope.VIDEO,
                hasContributionToolbar = false
            )
        )
        assertEquals(
            null,
            resolveSpaceSearchBarGridItemIndex(
                scope = SpaceSearchScope.NONE,
                hasContributionToolbar = true
            )
        )
    }

    @Test
    fun resolveSpaceSearchBarRevealScrollOffsetPx_placesSearchBelowTopBar() {
        assertEquals(
            -72,
            resolveSpaceSearchBarRevealScrollOffsetPx(
                topBarHeightPx = 64,
                extraVisibleMarginPx = 8
            )
        )
        assertEquals(
            -8,
            resolveSpaceSearchBarRevealScrollOffsetPx(
                topBarHeightPx = -1,
                extraVisibleMarginPx = 8
            )
        )
        assertEquals(
            -64,
            resolveSpaceSearchBarRevealScrollOffsetPx(
                topBarHeightPx = 64,
                extraVisibleMarginPx = -1
            )
        )
    }

    @Test
    fun shouldEnableSpaceLazyGridSharedTransition_requiresBothScopes() {
        assertTrue(
            shouldEnableSpaceLazyGridSharedTransition(
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldEnableSpaceLazyGridSharedTransition(
                transitionEnabled = true,
                hasSharedTransitionScope = false,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldEnableSpaceLazyGridSharedTransition(
                transitionEnabled = false,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun shouldApplySpaceLoadResult_requires_matching_generation_and_mid() {
        assertTrue(
            shouldApplySpaceLoadResult(
                requestMid = 1001L,
                activeMid = 1001L,
                requestGeneration = 4L,
                activeGeneration = 4L
            )
        )
        assertFalse(
            shouldApplySpaceLoadResult(
                requestMid = 1001L,
                activeMid = 1002L,
                requestGeneration = 4L,
                activeGeneration = 4L
            )
        )
        assertFalse(
            shouldApplySpaceLoadResult(
                requestMid = 1001L,
                activeMid = 1001L,
                requestGeneration = 4L,
                activeGeneration = 5L
            )
        )
    }

    @Test
    fun applySpaceSupplementalData_merges_collection_content_without_losing_core_state() {
        val initial = SpaceUiState.Success(
            userInfo = SpaceUserInfo(mid = 42L, name = "UP"),
            videos = listOf(com.android.purebilibili.data.model.response.SpaceVideoItem(bvid = "BV1", title = "core")),
            topVideo = SpaceTopArcData(bvid = "BVTOP", title = "置顶"),
            notice = "已有公告",
            headerState = buildHeaderState(
                userInfo = SpaceUserInfo(mid = 42L, name = "UP"),
                relationStat = null,
                upStat = null,
                topVideo = SpaceTopArcData(bvid = "BVTOP", title = "置顶"),
                notice = "已有公告",
                createdFavorites = emptyList(),
                collectedFavorites = emptyList()
            ),
            tabShellState = buildInitialTabShellState(selectedTab = SpaceMainTab.CONTRIBUTION)
        )

        val updated = applySpaceSupplementalData(
            state = initial,
            seasons = listOf(SeasonItem(meta = SeasonMeta(season_id = 1L, name = "合集"))),
            series = listOf(SeriesItem(meta = SeriesMeta(series_id = 2L, name = "系列"))),
            createdFavoriteFolders = listOf(FavFolder(id = 3L, title = "创建收藏", media_count = 4)),
            collectedFavoriteFolders = listOf(FavFolder(id = 4L, title = "收藏合集", media_count = 5)),
            seasonArchives = mapOf(1L to listOf(SeasonArchiveItem(bvid = "BVSEASON", title = "合集预览"))),
            seriesArchives = mapOf(2L to listOf(SeriesArchiveItem(bvid = "BVSERIES", title = "系列预览")))
        )

        assertEquals("UP", updated.userInfo.name)
        assertEquals(listOf("BV1"), updated.videos.map { it.bvid })
        assertEquals("BVTOP", updated.topVideo?.bvid)
        assertEquals("已有公告", updated.notice)
        assertEquals(listOf(1L), updated.seasons.map { it.meta.season_id })
        assertEquals(listOf(2L), updated.series.map { it.meta.series_id })
        assertEquals(listOf(3L), updated.createdFavoriteFolders.map { it.id })
        assertEquals(listOf(4L), updated.collectedFavoriteFolders.map { it.id })
        assertEquals("BVSEASON", updated.seasonArchives.getValue(1L).single().bvid)
        assertEquals("BVSERIES", updated.seriesArchives.getValue(2L).single().bvid)
        assertEquals(
            listOf("视频", "图文", "合集", "系列", "音频"),
            updated.contributionTabs.map { it.title }
        )
    }

    @Test
    fun resolveEmbeddedCollectionArchives_usesArchivesFromSeasonsSeriesList() {
        val seasons = listOf(
            SeasonItem(
                meta = SeasonMeta(season_id = 11L, name = "合集", total = 2),
                archives = listOf(
                    SeasonArchiveItem(bvid = "BVSEASON", title = "合集单集")
                )
            ),
            SeasonItem(meta = SeasonMeta(season_id = 12L, name = "空合集", total = 0))
        )
        val series = listOf(
            SeriesItem(
                meta = SeriesMeta(series_id = 21L, name = "系列", total = 1),
                archives = listOf(
                    SeriesArchiveItem(bvid = "BVSERIES", title = "系列单集")
                )
            )
        )

        val seasonArchives = resolveEmbeddedSeasonArchives(seasons)
        val seriesArchives = resolveEmbeddedSeriesArchives(series)

        assertEquals(setOf(11L), seasonArchives.keys)
        assertEquals("BVSEASON", seasonArchives.getValue(11L).single().bvid)
        assertEquals(setOf(21L), seriesArchives.keys)
        assertEquals("BVSERIES", seriesArchives.getValue(21L).single().bvid)
    }

    @Test
    fun applySpaceSupplementalData_keepsLongerAlreadyLoadedCollectionArchives() {
        val initial = SpaceUiState.Success(
            userInfo = SpaceUserInfo(mid = 42L, name = "UP"),
            seasonArchives = mapOf(
                11L to listOf(
                    SeasonArchiveItem(bvid = "BVFULL1"),
                    SeasonArchiveItem(bvid = "BVFULL2")
                )
            ),
            seriesArchives = mapOf(
                21L to listOf(
                    SeriesArchiveItem(bvid = "BVSERIES_FULL1"),
                    SeriesArchiveItem(bvid = "BVSERIES_FULL2")
                )
            )
        )

        val updated = applySpaceSupplementalData(
            state = initial,
            seasons = listOf(SeasonItem(meta = SeasonMeta(season_id = 11L, name = "合集"))),
            series = listOf(SeriesItem(meta = SeriesMeta(series_id = 21L, name = "系列"))),
            createdFavoriteFolders = emptyList(),
            collectedFavoriteFolders = emptyList(),
            seasonArchives = mapOf(11L to listOf(SeasonArchiveItem(bvid = "BVPREVIEW"))),
            seriesArchives = mapOf(21L to listOf(SeriesArchiveItem(bvid = "BVSERIES_PREVIEW")))
        )

        assertEquals(listOf("BVFULL1", "BVFULL2"), updated.seasonArchives.getValue(11L).map { it.bvid })
        assertEquals(
            listOf("BVSERIES_FULL1", "BVSERIES_FULL2"),
            updated.seriesArchives.getValue(21L).map { it.bvid }
        )
    }

    @Test
    fun `mapSeasonArchiveToVideoItem preserves danmaku count`() {
        val item = mapSeasonArchiveToVideoItem(
            item = SeasonArchiveItem(
                bvid = "BVSEASON",
                title = "合集视频",
                stat = SeasonArchiveStat(view = 100, danmaku = 23, reply = 7)
            ),
            mid = 42L
        )

        assertEquals(100, item.stat.view)
        assertEquals(23, item.stat.danmaku)
        assertEquals(7, item.stat.reply)
    }

    @Test
    fun `mapSeasonArchiveToVideoItem uses space owner name when archive author is blank`() {
        val item = mapSeasonArchiveToVideoItem(
            item = SeasonArchiveItem(
                bvid = "BVSEASON",
                title = "合集视频"
            ),
            mid = 42L,
            ownerName = "影视飓风"
        )

        assertEquals(42L, item.owner.mid)
        assertEquals("影视飓风", item.owner.name)
    }

    @Test
    fun `mapSeasonArchiveToVideoItem keeps archive author for collaborative videos`() {
        val item = mapSeasonArchiveToVideoItem(
            item = SeasonArchiveItem(
                bvid = "BVCOOP",
                title = "联合投稿",
                author = "联合投稿UP"
            ),
            mid = 42L,
            ownerName = "影视飓风"
        )

        assertEquals("联合投稿UP", item.owner.name)
    }

    @Test
    fun `mapSeriesArchiveToVideoItem preserves danmaku count`() {
        val item = mapSeriesArchiveToVideoItem(
            item = SeriesArchiveItem(
                bvid = "BVSERIES",
                title = "系列视频",
                stat = SeriesArchiveStat(view = 200, danmaku = 34, reply = 8)
            ),
            mid = 42L
        )

        assertEquals(200, item.stat.view)
        assertEquals(34, item.stat.danmaku)
        assertEquals(8, item.stat.reply)
    }

    @Test
    fun `mapSeriesArchiveToVideoItem uses space owner name when archive author is blank`() {
        val item = mapSeriesArchiveToVideoItem(
            item = SeriesArchiveItem(
                bvid = "BVSERIES",
                title = "系列视频"
            ),
            mid = 42L,
            ownerName = "影视飓风"
        )

        assertEquals(42L, item.owner.mid)
        assertEquals("影视飓风", item.owner.name)
    }

    @Test
    fun `resolveSpaceContentGridColumnCount keeps at least two columns on phones`() {
        assertEquals(2, resolveSpaceContentGridColumnCount(widthDp = 360))
        assertEquals(2, resolveSpaceContentGridColumnCount(widthDp = 412))
        assertEquals(3, resolveSpaceContentGridColumnCount(widthDp = 700))
        // 与首页信息流共用自适应列数策略：980dp 内容上限内按 180dp 最小卡宽自适应。
        assertEquals(5, resolveSpaceContentGridColumnCount(widthDp = 960))
    }

    @Test
    fun `space adaptive layout follows window width for tablets and unfolded foldables`() {
        val compact = resolveSpaceAdaptiveLayoutSpec(widthDp = 412)
        val medium = resolveSpaceAdaptiveLayoutSpec(widthDp = 700)
        val expanded = resolveSpaceAdaptiveLayoutSpec(widthDp = 1000)
        val large = resolveSpaceAdaptiveLayoutSpec(widthDp = 1280)

        assertEquals(980, compact.contentMaxWidthDp)
        assertEquals(1, compact.dynamicColumns)
        assertFalse(compact.useExpandedHeader)
        assertEquals(980, medium.contentMaxWidthDp)
        assertEquals(1, medium.dynamicColumns)
        assertTrue(medium.useExpandedHeader)
        assertEquals(1280, expanded.contentMaxWidthDp)
        assertEquals(2, expanded.dynamicColumns)
        assertTrue(expanded.useExpandedHeader)
        assertEquals(1280, large.contentMaxWidthDp)
        assertEquals(3, large.dynamicColumns)
        assertTrue(large.useExpandedHeader)
        assertEquals(720, large.listContentMaxWidthDp)
        val phoneBanner = resolveSpaceBannerMetrics(
            renderedBannerWidthDp = 393f,
            windowWidthDp = 393f,
            windowHeightDp = 851f,
        )
        assertFalse(phoneBanner.cropToFill)
        assertEquals(393f / SPACE_BANNER_ASPECT_RATIO, phoneBanner.heightDp, 0.01f)
        assertEquals(393f / SPACE_BANNER_ASPECT_RATIO, phoneBanner.heroHeightDp, 0.01f)
        val phoneBannerWithInset = resolveSpaceBannerMetrics(
            renderedBannerWidthDp = 393f,
            windowWidthDp = 393f,
            windowHeightDp = 851f,
            topInsetDp = 104f,
        )
        assertFalse(phoneBannerWithInset.cropToFill)
        assertEquals(393f / SPACE_BANNER_ASPECT_RATIO + 104f, phoneBannerWithInset.heightDp, 0.01f)
        assertEquals(393f / SPACE_BANNER_ASPECT_RATIO, phoneBannerWithInset.heroHeightDp, 0.01f)
        val landscapeTabletBanner = resolveSpaceBannerMetrics(
            renderedBannerWidthDp = 1280f,
            windowWidthDp = 1280f,
            windowHeightDp = 800f,
        )
        assertTrue(landscapeTabletBanner.cropToFill)
        assertEquals(SPACE_WIDE_BANNER_MAX_HEIGHT_DP, landscapeTabletBanner.heightDp, 0.01f)
        val compactCover = resolveSpaceBannerMetrics(
            renderedBannerWidthDp = 421f,
            windowWidthDp = 421f,
            windowHeightDp = 616f,
        )
        assertFalse(compactCover.cropToFill)
        assertEquals(421f / SPACE_BANNER_ASPECT_RATIO, compactCover.heightDp, 0.01f)
        assertEquals(
            7,
            resolveSpaceContentGridColumnCount(
                widthDp = 1280,
                contentMaxWidthDp = large.contentMaxWidthDp,
                widthSizeClass = com.android.purebilibili.core.util.WindowWidthSizeClass.Large,
            ),
        )
    }

    @Test
    fun `resolveSpaceContentGridColumnCount honors home feed fixed column and width presets`() {
        assertEquals(
            3,
            resolveSpaceContentGridColumnCount(
                widthDp = 412,
                fixedColumnCount = 3
            )
        )
        assertEquals(
            2,
            resolveSpaceContentGridColumnCount(
                widthDp = 412,
                cardWidthPreset = HomeFeedCardWidthPreset.WIDE
            )
        )
        // 与首页一致：即使超宽卡预设，窄屏仍保底 2 列。
        assertEquals(
            2,
            resolveSpaceContentGridColumnCount(
                widthDp = 412,
                cardWidthPreset = HomeFeedCardWidthPreset.ULTRA_WIDE
            )
        )
        // 大屏按空间页 980dp 内容宽度上限截断，而不是屏幕全宽。
        assertEquals(
            5,
            resolveSpaceContentGridColumnCount(widthDp = 1600)
        )
    }

    @Test
    fun `contribution video layout defaults to grid and toggles to single column`() {
        assertEquals(SpaceContributionVideoLayoutMode.GRID, defaultSpaceContributionVideoLayoutMode())
        assertEquals(
            SpaceContributionVideoLayoutMode.SINGLE_COLUMN,
            toggleSpaceContributionVideoLayoutMode(SpaceContributionVideoLayoutMode.GRID)
        )
        assertEquals(
            SpaceContributionVideoLayoutMode.GRID,
            toggleSpaceContributionVideoLayoutMode(SpaceContributionVideoLayoutMode.SINGLE_COLUMN)
        )
    }

    @Test
    fun `single column contribution video layout spans the full content row`() {
        assertEquals(
            1,
            resolveSpaceContributionVideoGridSpan(
                layoutMode = SpaceContributionVideoLayoutMode.GRID,
                maxLineSpan = 4
            )
        )
        assertEquals(
            4,
            resolveSpaceContributionVideoGridSpan(
                layoutMode = SpaceContributionVideoLayoutMode.SINGLE_COLUMN,
                maxLineSpan = 4
            )
        )
    }

    @Test
    fun `contribution video item key survives layout changes for continuous reflow`() {
        val gridKey = resolveSpaceContributionVideoItemKey(
            layoutMode = SpaceContributionVideoLayoutMode.GRID,
            bvid = "BV1xx",
            aid = 123
        )
        val singleColumnKey = resolveSpaceContributionVideoItemKey(
            layoutMode = SpaceContributionVideoLayoutMode.SINGLE_COLUMN,
            bvid = "BV1xx",
            aid = 123
        )

        assertEquals("space_video_BV1xx_123", gridKey)
        assertEquals(gridKey, singleColumnKey)
    }

    @Test
    fun `space archive shared transition key uses non blank bvid`() {
        assertEquals("BVSEASON", resolveSpaceArchiveSharedTransitionKey("BVSEASON"))
        assertNull(resolveSpaceArchiveSharedTransitionKey(""))
        assertNull(resolveSpaceArchiveSharedTransitionKey("   "))
    }

    @Test
    fun `shouldHydrateSpaceContributionVideos hydrates when seeded videos are missing`() {
        assertTrue(
            shouldHydrateSpaceContributionVideos(
                totalVideos = 133,
                seededVideoCount = 0,
                pageSize = 30,
                selectedSubTab = SpaceSubTab.VIDEO,
                selectedTid = 0,
                currentOrder = VideoSortOrder.PUBDATE,
                currentKeyword = ""
            )
        )
        assertTrue(
            shouldHydrateSpaceContributionVideos(
                totalVideos = 133,
                seededVideoCount = 20,
                pageSize = 30,
                selectedSubTab = SpaceSubTab.VIDEO,
                selectedTid = 0,
                currentOrder = VideoSortOrder.PUBDATE,
                currentKeyword = ""
            )
        )
        assertFalse(
            shouldHydrateSpaceContributionVideos(
                totalVideos = 133,
                seededVideoCount = 30,
                pageSize = 30,
                selectedSubTab = SpaceSubTab.VIDEO,
                selectedTid = 0,
                currentOrder = VideoSortOrder.PUBDATE,
                currentKeyword = ""
            )
        )
        assertFalse(
            shouldHydrateSpaceContributionVideos(
                totalVideos = 133,
                seededVideoCount = 0,
                pageSize = 30,
                selectedSubTab = SpaceSubTab.AUDIO,
                selectedTid = 0,
                currentOrder = VideoSortOrder.PUBDATE,
                currentKeyword = ""
            )
        )
        assertTrue(
            shouldHydrateSpaceContributionVideos(
                totalVideos = 133,
                seededVideoCount = 0,
                pageSize = 30,
                selectedSubTab = SpaceSubTab.CHARGING_VIDEO,
                selectedTid = 0,
                currentOrder = VideoSortOrder.PUBDATE,
                currentKeyword = ""
            )
        )
        assertFalse(
            shouldHydrateSpaceContributionVideos(
                totalVideos = 0,
                seededVideoCount = 0,
                pageSize = 30,
                selectedSubTab = SpaceSubTab.VIDEO,
                selectedTid = 0,
                currentOrder = VideoSortOrder.PUBDATE,
                currentKeyword = ""
            )
        )
    }

    @Test
    fun `buildInitialSpaceSuccessState keeps first video paint in loading state when hydration is needed`() {
        val state = buildInitialSpaceSuccessState(
            seed = SpaceInitialSeed(
                userInfo = SpaceUserInfo(mid = 42L, name = "UP"),
                relationStat = null,
                upStat = null,
                videos = emptyList(),
                totalVideos = 133,
                audios = emptyList(),
                totalAudios = 0,
                articles = emptyList(),
                totalArticles = 0,
                homeFavoriteFolders = emptyList(),
                homeFavoriteFolderCount = 0,
                homeCoinVideos = emptyList(),
                homeCoinVideoCount = 0,
                homeLikeVideos = emptyList(),
                homeLikeVideoCount = 0,
                homeBangumiItems = emptyList(),
                homeBangumiCount = 0,
                homeComicItems = emptyList(),
                homeComicCount = 0,
                mainTabs = buildDefaultSpaceMainTabs(),
                contributionTabs = buildDefaultSpaceContributionTabs(),
                defaultMainTab = SpaceMainTab.CONTRIBUTION,
                defaultSubTab = SpaceSubTab.VIDEO,
                defaultContributionTabId = buildDefaultSpaceContributionTabs().first().id
            ),
            selectedMainTab = SpaceMainTab.CONTRIBUTION,
            selectedSubTab = SpaceSubTab.VIDEO
        )

        assertTrue(state.isLoadingMore)
        assertEquals(133, state.totalVideos)
        assertTrue(state.videos.isEmpty())
    }

    @Test
    fun `resolveSpaceInitialSeedFromAggregate falls back to night header image`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                card = SpaceAggregateCard(
                    mid = "42",
                    name = "UP",
                    face = "https://i0.hdslb.com/bfs/face/demo.jpg"
                ),
                images = SpaceAggregateImages(
                    imgUrl = "",
                    nightImgUrl = "https://i0.hdslb.com/bfs/space/night-cover.jpg"
                )
            )
        )

        assertEquals(
            "https://i0.hdslb.com/bfs/space/night-cover.jpg",
            seed?.userInfo?.topPhoto
        )
    }

    @Test
    fun `resolveSpaceInitialSeedFromAggregate keeps PiliPlus space tag contract`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                card = SpaceAggregateCard(
                    mid = "42",
                    spaceTag = listOf(
                        SpaceTagItem(type = "location", title = "IP属地：广东"),
                        SpaceTagItem(type = "real_name", title = "已实名认证"),
                        SpaceTagItem(type = "other", title = "不应展示")
                    )
                )
            )
        )

        assertEquals(
            listOf("IP属地：广东", "已实名认证"),
            seed?.userInfo?.spaceTags?.map { it.title }
        )
        assertEquals("IP属地：广东", seed?.userInfo?.ipLocation)
    }

    @Test
    fun `resolveSpaceAggregateTopPhoto prefers collection top simple image`() {
        val images = SpaceAggregateImages(
            imgUrl = "https://i0.hdslb.com/bfs/space/fallback.jpg",
            collectionTopSimple = com.android.purebilibili.data.model.response.SpaceCollectionTopSimple(
                top = com.android.purebilibili.data.model.response.SpaceCollectionTop(
                    result = listOf(
                        com.android.purebilibili.data.model.response.SpaceCollectionTopItem(
                            item = com.android.purebilibili.data.model.response.SpaceCollectionTopItemDetail(
                                image = com.android.purebilibili.data.model.response.SpaceCollectionTopImage(
                                    defaultImage = "https://i0.hdslb.com/bfs/space/collection-default.png"
                                )
                            ),
                            cover = "https://i0.hdslb.com/bfs/space/collection-cover.png"
                        )
                    )
                )
            )
        )

        val resolved = resolveSpaceAggregateTopPhoto(images)
        assertEquals("https://i0.hdslb.com/bfs/space/collection-default.png", resolved)
    }

    @Test
    fun `resolveSpaceAggregateTopPhoto falls back to collection cover then imgUrl`() {
        val coverOnly = SpaceAggregateImages(
            imgUrl = "https://i0.hdslb.com/bfs/space/fallback.jpg",
            collectionTopSimple = com.android.purebilibili.data.model.response.SpaceCollectionTopSimple(
                top = com.android.purebilibili.data.model.response.SpaceCollectionTop(
                    result = listOf(
                        com.android.purebilibili.data.model.response.SpaceCollectionTopItem(
                            cover = "https://i0.hdslb.com/bfs/space/collection-cover.png"
                        )
                    )
                )
            )
        )
        assertEquals("https://i0.hdslb.com/bfs/space/collection-cover.png", resolveSpaceAggregateTopPhoto(coverOnly))

        val imgUrlOnly = SpaceAggregateImages(
            imgUrl = "https://i0.hdslb.com/bfs/space/img-url.jpg"
        )
        assertEquals("https://i0.hdslb.com/bfs/space/img-url.jpg", resolveSpaceAggregateTopPhoto(imgUrlOnly))
    }

    @Test
    fun `shouldApplySpaceVideoResult requires matching list generation and filters`() {
        assertTrue(
            shouldApplySpaceVideoResult(
                requestMid = 42L,
                activeMid = 42L,
                requestGeneration = 3L,
                activeGeneration = 3L,
                requestTid = 0,
                activeTid = 0,
                requestOrder = VideoSortOrder.PUBDATE,
                activeOrder = VideoSortOrder.PUBDATE,
                requestKeyword = "test",
                activeKeyword = "test"
            )
        )
        assertFalse(
            shouldApplySpaceVideoResult(
                requestMid = 42L,
                activeMid = 42L,
                requestGeneration = 3L,
                activeGeneration = 4L,
                requestTid = 0,
                activeTid = 0,
                requestOrder = VideoSortOrder.PUBDATE,
                activeOrder = VideoSortOrder.PUBDATE,
                requestKeyword = "test",
                activeKeyword = "test"
            )
        )
        assertFalse(
            shouldApplySpaceVideoResult(
                requestMid = 42L,
                activeMid = 42L,
                requestGeneration = 3L,
                activeGeneration = 3L,
                requestTid = 1,
                activeTid = 0,
                requestOrder = VideoSortOrder.PUBDATE,
                activeOrder = VideoSortOrder.PUBDATE,
                requestKeyword = "test",
                activeKeyword = "test"
            )
        )
    }

    @Test
    fun `mergeSpaceVideoPages preserves order and removes duplicates`() {
        val merged = mergeSpaceVideoPages(
            existing = listOf(
                SpaceVideoItem(aid = 1L, bvid = "BV1"),
                SpaceVideoItem(aid = 2L, bvid = "BV2")
            ),
            incoming = listOf(
                SpaceVideoItem(aid = 2L, bvid = "BV2"),
                SpaceVideoItem(aid = 3L, bvid = "BV3"),
                SpaceVideoItem(aid = 1L, bvid = "BV1")
            )
        )

        assertEquals(listOf("BV1", "BV2", "BV3"), merged.map { it.bvid })
    }

    @Test
    fun `bangumi pagination stops when api page adds no unique items`() {
        assertFalse(
            shouldContinueSpaceBangumiPagination(
                previousItemCount = 30,
                mergedItemCount = 30,
                incomingItemCount = 30,
                responsePage = 2,
                pageSize = 30,
                total = 91,
            ),
        )
        assertTrue(
            shouldContinueSpaceBangumiPagination(
                previousItemCount = 30,
                mergedItemCount = 60,
                incomingItemCount = 30,
                responsePage = 2,
                pageSize = 30,
                total = 91,
            ),
        )
        assertFalse(
            shouldContinueSpaceBangumiPagination(
                previousItemCount = 60,
                mergedItemCount = 91,
                incomingItemCount = 31,
                responsePage = 3,
                pageSize = 30,
                total = 91,
            ),
        )
    }

    @Test
    fun `parseTopImageDy calculates vertical bias matching PiliPlus dy formula`() {
        assertEquals(0f, parseTopImageDy("0-0-396", 396.0), 0.001f)
        assertEquals(-0.5f, parseTopImageDy("0-0-198", 396.0), 0.001f)
        assertEquals(0.5f, parseTopImageDy("0-198-396", 396.0), 0.001f)
        assertEquals(0f, parseTopImageDy("", 396.0))
        assertEquals(0f, parseTopImageDy("invalid", 396.0))
    }

    @Test
    fun `resolveSpaceRelationState aligns with PiliPlus relation determination`() {
        // Blacklisted
        assertEquals(
            Pair(false, 128),
            resolveSpaceRelationState(aggregateRelation = -1)
        )
        // Followed with special relation
        assertEquals(
            Pair(true, -10),
            resolveSpaceRelationState(
                aggregateRelation = 0,
                relSpecial = 1,
                cardRelation = SpaceAggregateRelation(status = 2, isFollow = 1)
            )
        )
        // Followed mutual
        assertEquals(
            Pair(true, 6),
            resolveSpaceRelationState(
                aggregateRelation = 0,
                relSpecial = 0,
                cardRelation = SpaceAggregateRelation(status = 6, isFollow = 1)
            )
        )
        // Followed standard
        assertEquals(
            Pair(true, 2),
            resolveSpaceRelationState(
                aggregateRelation = 0,
                relSpecial = 0,
                cardRelation = SpaceAggregateRelation(status = 2, isFollow = 1)
            )
        )
        // Not followed even if status has residual value 2
        assertEquals(
            Pair(false, 0),
            resolveSpaceRelationState(
                aggregateRelation = 0,
                relSpecial = 0,
                cardRelation = SpaceAggregateRelation(status = 2, isFollow = 0)
            )
        )
        // Null card relation
        assertEquals(
            Pair(false, 0),
            resolveSpaceRelationState()
        )
    }

    @Test
    fun `resolveSpaceInitialSeedFromAggregate detects cheese tab in tab2`() {
        val seed = resolveSpaceInitialSeedFromAggregate(
            data = SpaceAggregateData(
                card = SpaceAggregateCard(mid = "123", name = "Teacher"),
                tab2 = listOf(
                    SpaceAggregateTab(title = "主页", param = "home"),
                    SpaceAggregateTab(title = "课堂", param = "cheese")
                )
            )
        )

        assertTrue(seed?.hasCheeseTab == true)
    }
}
