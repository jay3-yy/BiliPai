package com.android.purebilibili.feature.search

import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchScreenPolicyTest {


    @Test
    fun resetSearchScroll_onlyWhenShowingNonBlankResults() {
        assertTrue(
            shouldResetSearchResultScroll(
                searchSessionId = 1L,
                showResults = true,
                lastResetSessionId = 0L
            )
        )
        assertFalse(
            shouldResetSearchResultScroll(
                searchSessionId = 0L,
                showResults = true,
                lastResetSessionId = 0L
            )
        )
        assertFalse(
            shouldResetSearchResultScroll(
                searchSessionId = 2L,
                showResults = false,
                lastResetSessionId = 1L
            )
        )
    }

    @Test
    fun resetSearchScroll_preservesPositionWhenReturningFromVideoDetail() {
        assertFalse(
            shouldResetSearchResultScroll(
                searchSessionId = 2L,
                showResults = true,
                lastResetSessionId = 0L,
                isReturningFromVideoDetail = true,
            )
        )
    }

    @Test
    fun backToTopButton_onlyShowsAfterResultListScrollsPastThreshold() {
        assertFalse(
            shouldShowSearchBackToTop(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 180
            )
        )
        assertTrue(
            shouldShowSearchBackToTop(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 320
            )
        )
        assertTrue(
            shouldShowSearchBackToTop(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun submitKeyword_prefersTypedQuery_thenFallsBackToSuggestedKeyword() {
        assertEquals(
            "黑神话悟空",
            resolveSearchSubmitKeyword(
                query = "  黑神话悟空 ",
                suggestedKeyword = "睡羊妹妹m"
            )
        )
        assertEquals(
            "睡羊妹妹m",
            resolveSearchSubmitKeyword(
                query = " ",
                suggestedKeyword = " 睡羊妹妹m "
            )
        )
        assertEquals(
            "",
            resolveSearchSubmitKeyword(
                query = "",
                suggestedKeyword = " "
            )
        )
    }


    @Test
    fun `searchUpUserTypeFilterLabel avoids repeating tab name`() {
        assertEquals("用户类型", resolveSearchUpUserTypeFilterLabel(SearchUserType.ALL))
        assertEquals("仅UP主", resolveSearchUpUserTypeFilterLabel(SearchUserType.UP))
    }

    @Test
    fun searchFilterTabs_followBiliPaiPrimaryOrder() {
        assertEquals(
            listOf(
                SearchType.VIDEO,
                SearchType.BANGUMI,
                SearchType.MEDIA_FT,
                SearchType.LIVE,
                SearchType.LIVE_USER,
                SearchType.UP,
                SearchType.ARTICLE,
                SearchType.TOPIC,
                SearchType.PHOTO
            ),
            resolveSearchFilterTabs()
        )
    }

    @Test
    fun searchFilterTabs_exposeEverySupportedApiType() {
        val visibleTabs = resolveSearchFilterTabs()

        assertEquals(SearchType.entries.toSet(), visibleTabs.toSet())
    }

    @Test
    fun searchFilterTabs_applyCustomSavedOrder() {
        val reordered = resolveSearchFilterTabs(
            listOf(
                SearchType.VIDEO.value,
                SearchType.UP.value,
                SearchType.LIVE.value,
            )
        )

        assertEquals(
            listOf(
                SearchType.VIDEO,
                SearchType.UP,
                SearchType.LIVE,
                SearchType.BANGUMI,
                SearchType.MEDIA_FT,
                SearchType.LIVE_USER,
                SearchType.ARTICLE,
                SearchType.TOPIC,
                SearchType.PHOTO,
            ),
            reordered
        )
    }

    @Test
    fun searchFilterTabs_ignoreUnknownAndDuplicateSavedValues() {
        val reordered = resolveSearchFilterTabs(
            listOf(
                "unknown",
                SearchType.UP.value,
                SearchType.UP.value,
                SearchType.VIDEO.value,
            )
        )

        assertEquals(
            listOf(
                SearchType.UP,
                SearchType.VIDEO,
                SearchType.BANGUMI,
                SearchType.MEDIA_FT,
                SearchType.LIVE,
                SearchType.LIVE_USER,
                SearchType.ARTICLE,
                SearchType.TOPIC,
                SearchType.PHOTO,
            ),
            reordered
        )
    }

    @Test
    fun searchFilterControls_matchCurrentSearchType() {
        assertEquals(
            listOf(
                SearchFilterControl.VIDEO_ORDER,
                SearchFilterControl.VIDEO_DURATION,
                SearchFilterControl.VIDEO_TID
            ),
            resolveSearchFilterControls(
                currentType = SearchType.VIDEO,
                currentUpOrder = SearchUpOrder.DEFAULT
            )
        )
        assertEquals(
            listOf(
                SearchFilterControl.UP_ORDER,
                SearchFilterControl.UP_ORDER_SORT,
                SearchFilterControl.UP_USER_TYPE
            ),
            resolveSearchFilterControls(
                currentType = SearchType.UP,
                currentUpOrder = SearchUpOrder.FANS
            )
        )
        assertEquals(
            listOf(SearchFilterControl.LIVE_ORDER),
            resolveSearchFilterControls(
                currentType = SearchType.LIVE,
                currentUpOrder = SearchUpOrder.DEFAULT
            )
        )
        assertEquals(
            listOf(
                SearchFilterControl.PHOTO_ORDER,
                SearchFilterControl.PHOTO_CATEGORY
            ),
            resolveSearchFilterControls(
                currentType = SearchType.PHOTO,
                currentUpOrder = SearchUpOrder.DEFAULT
            )
        )
        assertEquals(
            listOf(
                SearchFilterControl.ARTICLE_ORDER,
                SearchFilterControl.ARTICLE_CATEGORY
            ),
            resolveSearchFilterControls(
                currentType = SearchType.ARTICLE,
                currentUpOrder = SearchUpOrder.DEFAULT
            )
        )
    }



    @Test
    fun searchResultLazyItemKey_prefersStableBusinessKeys() {
        assertEquals(
            "video:0:text:BV1xx411c7mD",
            resolveSearchResultLazyItemKey(
                searchType = SearchType.VIDEO,
                index = 0,
                textKey = " BV1xx411c7mD ",
                numericKey = 123L
            )
        )
        assertEquals(
            "video:0:id:123",
            resolveSearchResultLazyItemKey(
                searchType = SearchType.VIDEO,
                index = 0,
                textKey = "",
                numericKey = 123L
            )
        )
        assertEquals(
            "media_bangumi:0:secondary:456",
            resolveSearchResultLazyItemKey(
                searchType = SearchType.BANGUMI,
                index = 0,
                numericKey = 0L,
                secondaryNumericKey = 456L
            )
        )
    }

    @Test
    fun searchResultLazyItemKey_usesIndexedFallbackForMissingIds() {
        val first = resolveSearchResultLazyItemKey(
            searchType = SearchType.VIDEO,
            index = 0,
            textKey = "",
            numericKey = 0L
        )
        val second = resolveSearchResultLazyItemKey(
            searchType = SearchType.VIDEO,
            index = 1,
            textKey = "",
            numericKey = 0L
        )

        assertEquals("video:local:0", first)
        assertEquals("video:local:1", second)
        assertTrue(first != second)
    }

    @Test
    fun searchResultLazyItemKey_disambiguatesDuplicateBusinessKeys() {
        val first = resolveSearchResultLazyItemKey(
            searchType = SearchType.VIDEO,
            index = 0,
            textKey = "BV_DUPLICATE"
        )
        val second = resolveSearchResultLazyItemKey(
            searchType = SearchType.VIDEO,
            index = 1,
            textKey = "BV_DUPLICATE"
        )

        assertEquals("video:0:text:BV_DUPLICATE", first)
        assertEquals("video:1:text:BV_DUPLICATE", second)
        assertTrue(first != second)
    }

    @Test
    fun searchResultLazyItemKey_preventsBlankAndDuplicateVideoGridKeys() {
        val keys = listOf(
            resolveSearchResultLazyItemKey(SearchType.VIDEO, index = 0, textKey = "", numericKey = 0L),
            resolveSearchResultLazyItemKey(SearchType.VIDEO, index = 1, textKey = "", numericKey = 0L),
            resolveSearchResultLazyItemKey(SearchType.VIDEO, index = 2, textKey = "BV_DUPLICATE", numericKey = 100L),
            resolveSearchResultLazyItemKey(SearchType.VIDEO, index = 3, textKey = "BV_DUPLICATE", numericKey = 100L)
        )

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.none { it.isBlank() })
    }

    @Test
    fun searchHighlightedTextSegments_preserveEmphasisAndDecodeEntities() {
        assertEquals(
            listOf(
                SearchHighlightedTextSegment("这是", highlighted = false),
                SearchHighlightedTextSegment("关键词", highlighted = true),
                SearchHighlightedTextSegment("&结尾", highlighted = false)
            ),
            resolveSearchHighlightedTextSegments("这是<em class=\"keyword\">关键词</em>&amp;结尾")
        )
    }

    @Test
    fun searchTypeTabs_useCompactDensityOnNarrowScreens() {
        val compact = resolveSearchTypeTabLayoutSpec(widthDp = 360)
        val regular = resolveSearchTypeTabLayoutSpec(widthDp = 412)

        assertEquals(6, compact.horizontalSpacingDp)
        assertEquals(10, compact.horizontalPaddingDp)
        assertEquals(13, compact.fontSizeSp)
        assertEquals(36, compact.minHeightDp)

        assertEquals(8, regular.horizontalSpacingDp)
        assertEquals(16, regular.horizontalPaddingDp)
        assertEquals(14, regular.fontSizeSp)
        assertEquals(40, regular.minHeightDp)
    }

    @Test
    fun searchResultPager_mapsPageAndTypeUsingVisibleTabs() {
        assertEquals(
            SearchType.MEDIA_FT,
            resolveSearchTypeForPagerPage(2)
        )
        assertEquals(
            3,
            resolveSearchPagerPageForType(SearchType.LIVE)
        )
        assertEquals(
            0,
            resolveSearchPagerPageForType(SearchType.PHOTO)
        )
    }

    @Test
    fun searchResultPageState_usesCurrentMirrorForActiveType() {
        val state = SearchUiState(
            query = "动画",
            showResults = true,
            searchType = SearchType.LIVE,
            isSearching = false,
            currentPage = 2,
            totalPages = 5,
            hasMoreResults = true
        )

        assertEquals(
            2,
            resolveSearchResultPageState(state, SearchType.LIVE).currentPage
        )
        assertTrue(resolveSearchResultPageState(state, SearchType.LIVE).hasMoreResults)
    }

    @Test
    fun searchResultPageState_restoresCachedInactiveType() {
        val cached = SearchResultPageUiState(
            query = "动画",
            currentPage = 1,
            totalPages = 3,
            hasMoreResults = true
        )
        val state = SearchUiState(
            query = "动画",
            showResults = true,
            searchType = SearchType.VIDEO,
            resultPages = mapOf(SearchType.UP to cached)
        )

        assertEquals(
            cached,
            resolveSearchResultPageState(state, SearchType.UP)
        )
    }




    @Test
    fun searchEntryMotion_onlyRunsForBottomBarSourceAndRespectsReducedBudget() {
        assertEquals(
            null,
            resolveSearchEntryMotionSpec(
                source = SearchEntryMotionSource.NONE,
                reducedMotionBudget = false
            )
        )

        val bottomBarSpec = requireNotNull(
            resolveSearchEntryMotionSpec(
                source = SearchEntryMotionSource.BOTTOM_BAR,
                reducedMotionBudget = false
            )
        )
        assertEquals(320, bottomBarSpec.durationMillis)
        assertEquals(0.58f, bottomBarSpec.initialAlpha)
        assertEquals(0.88f, bottomBarSpec.initialScale)
        assertEquals(360f, bottomBarSpec.initialTranslationYDp)
        assertEquals(0.5f, bottomBarSpec.transformOriginPivotX)
        assertEquals(1f, bottomBarSpec.transformOriginPivotY)

        val reducedSpec = requireNotNull(
            resolveSearchEntryMotionSpec(
                source = SearchEntryMotionSource.BOTTOM_BAR,
                reducedMotionBudget = true
            )
        )
        assertEquals(0, reducedSpec.durationMillis)
        assertEquals(1f, reducedSpec.initialAlpha)
        assertEquals(1f, reducedSpec.initialScale)
        assertEquals(0f, reducedSpec.initialTranslationYDp)
    }

    @Test
    fun searchExitMotion_returnsControlTowardBottomSearchSlot() {
        val entry = resolveSearchEntryMotionSpec(
            source = SearchEntryMotionSource.BOTTOM_BAR,
            reducedMotionBudget = false,
        )

        val exit = requireNotNull(
            resolveSearchExitMotionSpec(
                entrySpec = entry,
                screenHeightDp = 900,
            )
        )

        assertEquals(300, exit.durationMillis)
        assertEquals(764f, exit.initialTranslationYDp)
        assertEquals(0.74f, exit.initialScale)
        assertEquals(0.88f, exit.transformOriginPivotX)
    }

}
