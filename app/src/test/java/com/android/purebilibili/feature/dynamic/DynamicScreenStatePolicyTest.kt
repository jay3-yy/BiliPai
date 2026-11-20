package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.data.model.response.DynamicAuthorModule
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicModules
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicScreenStatePolicyTest {

    @Test
    fun notInterestedIdsAreNormalizedAndBounded() {
        assertEquals(
            setOf("2", "3"),
            normalizeDynamicNotInterestedIds(listOf(" 1 ", "", "2", "2", "3"), maxSize = 2),
        )
    }


    @Test
    fun `selecting an author from pgc opens all dynamics with that author`() {
        val targetTab = resolveDynamicAuthorSelectionTab(2)
        val selectedUid = resolveDynamicSelectedUserIdAfterClick(null, 42L)
        assertEquals(0, targetTab)
        assertEquals(42L, selectedUid)
        assertTrue(shouldUseSelectedUserDynamicFeed(targetTab, selectedUid))
        assertFalse(shouldUseSelectedUserDynamicFeed(2, selectedUid))
    }

    @Test
    fun `author selection preserves all video and article categories`() {
        for (tab in listOf(0, 1, 3)) {
            assertEquals(tab, resolveDynamicAuthorSelectionTab(tab))
        }
    }

    @Test
    fun `horizontal user list should use compact vertical padding`() {
        assertEquals(4, resolveHorizontalUserListVerticalPaddingDp())
    }

    @Test
    fun `horizontal user list collapses once feed leaves top`() {
        assertFalse(
            shouldCollapseDynamicHorizontalUserList(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )
        assertTrue(
            shouldCollapseDynamicHorizontalUserList(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 1,
                topTolerancePx = DynamicHeaderCollapseTriggerPx,
            )
        )
        assertTrue(
            shouldCollapseDynamicHorizontalUserList(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0
            )
        )
        assertFalse(
            shouldCollapseDynamicHorizontalUserList(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 200,
                topTolerancePx = 200,
            )
        )
        assertTrue(
            shouldCollapseDynamicHorizontalUserList(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 201,
                topTolerancePx = 200,
            )
        )
    }

    @Test
    fun `horizontal user list height follows scroll like home header`() {
        assertEquals(
            96,
            resolveDynamicScrollCollapsedHeaderHeightPx(
                expandedHeightPx = 96,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            )
        )
        assertEquals(
            64,
            resolveDynamicScrollCollapsedHeaderHeightPx(
                expandedHeightPx = 96,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 32,
            )
        )
        assertEquals(
            -32,
            resolveDynamicScrollCollapsedHeaderOffsetYPx(
                expandedHeightPx = 96,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 32,
            )
        )
        assertEquals(
            0,
            resolveDynamicScrollCollapsedHeaderHeightPx(
                expandedHeightPx = 96,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
            )
        )
    }

    @Test
    fun `dynamic top bar collapse respects optional setting`() {
        assertFalse(
            shouldCollapseDynamicTopBar(
                collapseOnScrollEnabled = false,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 20
            )
        )
        assertTrue(
            shouldCollapseDynamicTopBar(
                collapseOnScrollEnabled = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 1,
                topTolerancePx = DynamicHeaderCollapseTriggerPx,
            )
        )
        assertTrue(
            shouldCollapseDynamicTopBar(
                collapseOnScrollEnabled = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 12
            )
        )
        assertFalse(
            shouldCollapseDynamicTopBar(
                collapseOnScrollEnabled = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `error overlay should show when active list is empty and error exists`() {
        assertTrue(
            shouldShowDynamicErrorOverlay(
                error = "加载失败",
                activeItemsCount = 0
            )
        )
    }

    @Test
    fun `error overlay should hide when active list has data`() {
        assertFalse(
            shouldShowDynamicErrorOverlay(
                error = "加载失败",
                activeItemsCount = 3
            )
        )
    }

    @Test
    fun `loading footer should follow active list size`() {
        assertTrue(shouldShowDynamicLoadingFooter(isLoading = true, activeItemsCount = 1))
        assertFalse(shouldShowDynamicLoadingFooter(isLoading = true, activeItemsCount = 0))
        assertFalse(shouldShowDynamicLoadingFooter(isLoading = false, activeItemsCount = 2))
    }

    @Test
    fun `dynamic feed loads more from furthest visible waterfall lane`() {
        val visibleLaneIndices = listOf(18, 22, 20)

        assertTrue(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = visibleLaneIndices.maxOrNull(),
                totalItemsCount = 25,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = true,
            )
        )
        assertFalse(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 20,
                totalItemsCount = 25,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = true,
            )
        )
    }

    @Test
    fun `author category searches the next page before any result is visible`() {
        assertTrue(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 1,
                totalItemsCount = 7,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = true,
                isSelectedUserFeedEmpty = true,
            )
        )
        assertFalse(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 1,
                totalItemsCount = 7,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = true,
                error = "请求超时",
                isSelectedUserFeedEmpty = true,
            )
        )
    }

    @Test
    fun `dynamic feed does not load more when pagination is unavailable`() {
        assertFalse(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 22,
                totalItemsCount = 25,
                allowAutomaticLoadMore = false,
                isLoading = false,
                hasMore = true,
            )
        )
        assertFalse(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 22,
                totalItemsCount = 25,
                allowAutomaticLoadMore = true,
                isLoading = true,
                hasMore = true,
            )
        )
        assertFalse(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 22,
                totalItemsCount = 25,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = false,
            )
        )
    }

    @Test
    fun `dynamic feed pauses automatic pagination after a request error`() {
        assertFalse(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 2,
                totalItemsCount = 3,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = true,
                error = "请求数据发生错误",
            )
        )
        assertTrue(
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = 2,
                totalItemsCount = 3,
                allowAutomaticLoadMore = true,
                isLoading = false,
                hasMore = true,
                error = null,
            )
        )
    }

    @Test
    fun `no more footer should follow active hasMore and list size`() {
        assertTrue(shouldShowDynamicNoMoreFooter(hasMore = false, activeItemsCount = 1))
        assertFalse(shouldShowDynamicNoMoreFooter(hasMore = true, activeItemsCount = 1))
        assertFalse(shouldShowDynamicNoMoreFooter(hasMore = false, activeItemsCount = 0))
    }

    @Test
    fun `static empty dynamic content should reveal bottom bar`() {
        assertTrue(
            shouldRevealDynamicBottomBarForStaticContent(
                activeItemsCount = 0,
                isLoading = false
            )
        )
        assertFalse(
            shouldRevealDynamicBottomBarForStaticContent(
                activeItemsCount = 0,
                isLoading = true
            )
        )
        assertFalse(
            shouldRevealDynamicBottomBarForStaticContent(
                activeItemsCount = 1,
                isLoading = false
            )
        )
    }

    @Test
    fun `comment sheet should only show when a dynamic is selected`() {
        assertTrue(shouldShowDynamicCommentSheet("dyn:123"))
        assertFalse(shouldShowDynamicCommentSheet(null))
        assertFalse(shouldShowDynamicCommentSheet(""))
    }

    @Test
    fun `comment sheet total count should prefer live comment payload`() {
        assertEquals(26, resolveDynamicCommentSheetTotalCount(liveCount = 26, fallbackCount = 12))
        assertEquals(12, resolveDynamicCommentSheetTotalCount(liveCount = 0, fallbackCount = 12))
        assertEquals(0, resolveDynamicCommentSheetTotalCount(liveCount = 0, fallbackCount = -3))
    }

    @Test
    fun `unfollowed author is removed from cached dynamic lists`() {
        val state = DynamicUiState(
            items = listOf(
                buildDynamicItem(id = "100", authorMid = 11L),
                buildDynamicItem(id = "101", authorMid = 12L)
            ).toImmutableList(),
            userItems = listOf(
                buildDynamicItem(id = "200", authorMid = 11L),
                buildDynamicItem(id = "201", authorMid = 13L)
            ).toImmutableList()
        )

        val updated = resolveDynamicStateAfterAuthorUnfollow(state, authorMid = 11L)

        assertEquals(listOf("101"), updated.items.map { it.id_str })
        assertEquals(listOf("201"), updated.userItems.map { it.id_str })
    }

    @Test
    fun `temp banned dynamic is filtered from all tab presentation`() {
        val state = DynamicUiState(
            items = listOf(
                buildDynamicItem(id = "100"),
                buildDynamicItem(id = "101"),
                buildDynamicItem(id = "102")
            ).toImmutableList(),
            tempBannedDynamicIds = persistentSetOf("101")
        )

        val presentation = resolveDynamicPagePresentation(
            state = state,
            logicalTab = 0,
            selectedUserId = null
        )

        assertEquals(listOf("100", "102"), presentation.items.map { it.id_str })
    }

    @Test
    fun `temp banned dynamic is filtered from selected user presentation`() {
        val state = DynamicUiState(
            userItems = listOf(
                buildDynamicItem(id = "200"),
                buildDynamicItem(id = "201")
            ).toImmutableList(),
            tempBannedDynamicIds = persistentSetOf("201")
        )

        val presentation = resolveDynamicPagePresentation(
            state = state,
            logicalTab = 0,
            selectedUserId = 1L
        )

        assertEquals(listOf("200"), presentation.items.map { it.id_str })
    }

    @Test
    fun `unfollowed author is removed from followed user sidebar`() {
        val users = listOf(
            SidebarUser(uid = 11L, name = "removed", face = ""),
            SidebarUser(uid = 12L, name = "kept", face = "")
        )

        val updated = resolveFollowedUsersAfterAuthorUnfollow(users, authorMid = 11L)

        assertEquals(listOf(12L), updated.map { it.uid })
    }

    @Test
    fun `up panel prepends all dynamics and self without duplicating shortcuts`() {
        val users = listOf(
            SidebarUser(uid = 11L, name = "A", face = "a"),
            SidebarUser(uid = 22L, name = "me", face = "old-me"),
            SidebarUser(uid = DYNAMIC_UP_PANEL_ALL_UID, name = "全部动态", face = "")
        )
        val panel = resolveDynamicUpPanelUsers(
            users = users,
            selfUid = 22L,
            selfFace = "https://i0.hdslb.com/bfs/face/current.jpg"
        )

        assertEquals(listOf(DYNAMIC_UP_PANEL_ALL_UID, 22L, 11L), panel.map { it.uid })
        assertTrue(isDynamicUpPanelAllShortcut(-1L))
        assertTrue(isDynamicUpPanelShortcut(-1L, selfUid = 22L))
        assertTrue(isDynamicUpPanelShortcut(22L, selfUid = 22L))
        assertFalse(isDynamicUpPanelShortcut(11L, selfUid = 22L))
        assertTrue(isDynamicUpPanelItemSelected(selectedUserId = null, itemUid = -1L))
        assertTrue(isDynamicUpPanelItemSelected(selectedUserId = 11L, itemUid = 11L))
        assertFalse(isDynamicUpPanelItemSelected(selectedUserId = 11L, itemUid = -1L))
    }

    @Test
    fun `clicking the all shortcut clears the selected user`() {
        assertNull(
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = 10001L,
                clickedUserId = DYNAMIC_UP_PANEL_ALL_UID
            )
        )
    }

    @Test
    fun `followed user list reset should trigger only for fresh prepended refresh while viewing all`() {
        assertTrue(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 3,
                selectedUserId = null,
                handledBoundaryKey = null
            )
        )
        assertFalse(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 0,
                selectedUserId = null,
                handledBoundaryKey = null
            )
        )
        assertFalse(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 2,
                selectedUserId = 10001L,
                handledBoundaryKey = null
            )
        )
        assertFalse(
            shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = "dyn:123",
                prependedCount = 2,
                selectedUserId = null,
                handledBoundaryKey = "dyn:123"
            )
        )
    }

    @Test
    fun `clicking the selected user again should keep the scoped feed selected`() {
        assertEquals(
            10001L,
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = 10001L,
                clickedUserId = 10001L
            )
        )
    }

    @Test
    fun `clicking a different user should switch the dynamic filter`() {
        assertEquals(
            10002L,
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = 10001L,
                clickedUserId = 10002L
            )
        )
        assertEquals(
            10003L,
            resolveDynamicSelectedUserIdAfterClick(
                selectedUserId = null,
                clickedUserId = 10003L
            )
        )
    }

    @Test
    fun `selected author scopes all video and article but never pgc`() {
        for (tab in listOf(0, 1, 3)) {
            assertTrue(shouldUseSelectedUserDynamicFeed(selectedTab = tab, selectedUserId = 10001L))
            assertFalse(shouldUseSelectedUserDynamicFeed(selectedTab = tab, selectedUserId = null))
        }
        assertFalse(shouldUseSelectedUserDynamicFeed(selectedTab = 2, selectedUserId = 10001L))
    }

    @Test
    fun `feed should reset scroll when tab or selected user source changes`() {
        assertTrue(
            shouldResetDynamicFeedScrollOnSourceChange(
                previousTab = 0,
                nextTab = 0,
                previousSelectedUserId = 10001L,
                nextSelectedUserId = null
            )
        )
        assertTrue(
            shouldResetDynamicFeedScrollOnSourceChange(
                previousTab = 0,
                nextTab = 0,
                previousSelectedUserId = 10001L,
                nextSelectedUserId = 10002L
            )
        )
        assertFalse(
            shouldResetDynamicFeedScrollOnSourceChange(
                previousTab = 0,
                nextTab = 0,
                previousSelectedUserId = null,
                nextSelectedUserId = null
            )
        )
    }

    @Test
    fun `saved dynamic tab restores when index is valid`() {
        assertEquals(3, resolveDynamicSelectedTab(savedTab = 3, tabCount = 4))
    }

    @Test
    fun `saved dynamic tab falls back to all when index is invalid`() {
        assertEquals(0, resolveDynamicSelectedTab(savedTab = null, tabCount = 4))
        assertEquals(0, resolveDynamicSelectedTab(savedTab = -1, tabCount = 4))
        assertEquals(0, resolveDynamicSelectedTab(savedTab = 4, tabCount = 4))
        assertEquals(0, resolveDynamicSelectedTab(savedTab = 1, tabCount = 0))
    }

    @Test
    fun `dynamic horizontal swipe switches to adjacent tab`() {
        assertEquals(
            1,
            resolveDynamicSwipeTargetTab(
                currentTab = 0,
                tabCount = 4,
                dragDistancePx = -120f
            )
        )
        assertEquals(
            2,
            resolveDynamicSwipeTargetTab(
                currentTab = 3,
                tabCount = 4,
                dragDistancePx = 120f
            )
        )
    }

    @Test
    fun `dynamic horizontal swipe ignores weak drag and clamps edges`() {
        assertNull(
            resolveDynamicSwipeTargetTab(
                currentTab = 2,
                tabCount = 4,
                dragDistancePx = -40f
            )
        )
        assertNull(
            resolveDynamicSwipeTargetTab(
                currentTab = 0,
                tabCount = 4,
                dragDistancePx = 120f
            )
        )
        assertNull(
            resolveDynamicSwipeTargetTab(
                currentTab = 3,
                tabCount = 4,
                dragDistancePx = -120f
            )
        )
    }

    @Test
    fun `dynamic request type aligns with visible tab mapping`() {
        assertEquals("all", resolveDynamicFeedRequestType(selectedTab = 0))
        assertEquals("video", resolveDynamicFeedRequestType(selectedTab = 1))
        assertEquals("pgc", resolveDynamicFeedRequestType(selectedTab = 2))
        assertEquals("article", resolveDynamicFeedRequestType(selectedTab = 3))
    }

    @Test
    fun `only content tabs use server filtered dynamic feed`() {
        assertTrue(shouldUseServerFilteredDynamicFeed(selectedTab = 1))
        assertTrue(shouldUseServerFilteredDynamicFeed(selectedTab = 2))
        assertTrue(shouldUseServerFilteredDynamicFeed(selectedTab = 3))
        assertFalse(shouldUseServerFilteredDynamicFeed(selectedTab = 0))
    }

    @Test
    fun `incremental refresh prepends new items without dropping current list when items overlap`() {
        val existing = listOf(buildDynamicItem("old_a"), buildDynamicItem("old_b")).toImmutableList()
        val result = resolveDynamicFeedStateAfterSuccess(
            currentState = DynamicUiState(items = existing),
            incomingItems = listOf(buildDynamicItem("new_1"), buildDynamicItem("old_a")),
            isRefresh = true,
            requestType = "all",
            incrementalRefreshEnabled = true,
            hasMore = true
        )

        assertEquals(
            listOf("new_1", "old_a", "old_b"),
            result.items.map { it.id_str }
        )
        assertEquals("old_a", result.incrementalRefreshBoundaryKey)
        assertEquals(1, result.incrementalPrependedCount)
        assertEquals(DynamicFeedErrorSource.NONE, result.errorSource)
        assertEquals("all", result.timelineRequestType)
    }

    @Test
    fun `incremental refresh falls back to full replacement when incoming items have no overlap with existing items`() {
        val existing = listOf(buildDynamicItem("old_a"), buildDynamicItem("old_b")).toImmutableList()
        val result = resolveDynamicFeedStateAfterSuccess(
            currentState = DynamicUiState(items = existing),
            incomingItems = listOf(buildDynamicItem("new_1"), buildDynamicItem("new_2")),
            isRefresh = true,
            requestType = "all",
            incrementalRefreshEnabled = true,
            hasMore = true
        )

        assertEquals(
            listOf("new_1", "new_2"),
            result.items.map { it.id_str }
        )
        assertEquals(null, result.incrementalRefreshBoundaryKey)
        assertEquals(0, result.incrementalPrependedCount)
        assertEquals(DynamicFeedErrorSource.NONE, result.errorSource)
    }

    @Test
    fun `incremental refresh on timeline page falls back to full replacement when page is cache placeholder`() {
        val cachedPage = DynamicTimelinePageState(
            items = listOf(buildDynamicItem("cached_old")).toImmutableList(),
            isCachePlaceholder = true
        )
        val result = resolveDynamicTimelinePageAfterSuccess(
            currentPage = cachedPage,
            incomingItems = listOf(buildDynamicItem("fresh_1"), buildDynamicItem("cached_old")),
            isRefresh = true,
            incrementalRefreshEnabled = true,
            hasMore = true
        )

        assertEquals(
            listOf("fresh_1", "cached_old"),
            result.items.map { it.id_str }
        )
        assertEquals(null, result.incrementalRefreshBoundaryKey)
        assertEquals(0, result.incrementalPrependedCount)
        assertFalse(result.isCachePlaceholder)
    }

    @Test
    fun `incremental refresh keeps merged timeline sorted by publish timestamp`() {
        val existing = listOf(
            buildDynamicItem(id = "today_0900", pubTs = 1_800L),
            buildDynamicItem(id = "yesterday_2300", pubTs = 900L)
        ).toImmutableList()
        val result = resolveDynamicFeedStateAfterSuccess(
            currentState = DynamicUiState(
                items = existing,
                timelineRequestType = "all"
            ),
            incomingItems = listOf(
                buildDynamicItem(id = "today_1000", pubTs = 2_000L),
                buildDynamicItem(id = "today_0900", pubTs = 1_800L)
            ),
            isRefresh = true,
            requestType = "all",
            incrementalRefreshEnabled = true,
            hasMore = true
        )

        assertEquals(
            listOf("today_1000", "today_0900", "yesterday_2300"),
            result.items.map { it.id_str }
        )
    }

    @Test
    fun `pagination append keeps server page order instead of resorting the entire list`() {
        val existing = listOf(
            buildDynamicItem(id = "newer", pubTs = 2_000L),
            buildDynamicItem(id = "older", pubTs = 1_000L)
        ).toImmutableList()
        val result = resolveDynamicFeedStateAfterSuccess(
            currentState = DynamicUiState(items = existing),
            incomingItems = listOf(
                buildDynamicItem(id = "page_second", pubTs = 1_200L),
                buildDynamicItem(id = "page_first", pubTs = 1_300L)
            ),
            isRefresh = false,
            requestType = "all",
            incrementalRefreshEnabled = true,
            hasMore = true
        )

        assertEquals(
            listOf("newer", "older", "page_second", "page_first"),
            result.items.map { it.id_str }
        )
    }

    @Test
    fun `incremental refresh does not merge items from a different dynamic feed type`() {
        val result = resolveDynamicFeedStateAfterSuccess(
            currentState = DynamicUiState(
                items = listOf(buildDynamicItem("pgc_old")).toImmutableList(),
                timelineRequestType = "pgc"
            ),
            incomingItems = listOf(buildDynamicItem("all_new")),
            isRefresh = true,
            requestType = "all",
            incrementalRefreshEnabled = true,
            hasMore = true
        )

        assertEquals(listOf("all_new"), result.items.map { it.id_str })
        assertEquals("all", result.timelineRequestType)
        assertEquals(null, result.incrementalRefreshBoundaryKey)
        assertEquals(0, result.incrementalPrependedCount)
    }

    @Test
    fun `pagination failure preserves existing items and marks append error`() {
        val existing = listOf(buildDynamicItem("keep_me")).toImmutableList()
        val result = resolveDynamicFeedStateAfterFailure(
            currentState = DynamicUiState(items = existing),
            errorMessage = "网络错误",
            refresh = false
        )

        assertEquals(existing, result.items)
        assertEquals("网络错误", result.error)
        assertEquals(DynamicFeedErrorSource.APPEND, result.errorSource)
    }

    @Test
    fun `first load error is recorded as initial load source`() {
        val result = resolveDynamicFeedStateAfterFailure(
            currentState = DynamicUiState(),
            errorMessage = "未登录",
            refresh = true
        )

        assertEquals(DynamicFeedErrorSource.INITIAL_LOAD, result.errorSource)
        assertEquals("未登录", result.error)
    }

    @Test
    fun `refresh failure with existing items is not treated as append failure`() {
        val result = resolveDynamicFeedStateAfterFailure(
            currentState = DynamicUiState(items = listOf(buildDynamicItem("keep_me")).toImmutableList()),
            errorMessage = "刷新失败",
            refresh = true
        )

        assertEquals(DynamicFeedErrorSource.REFRESH, result.errorSource)
        assertEquals("刷新失败", result.error)
    }

    @Test
    fun `selected user presentation state uses user scoped loading and error`() {
        val state = DynamicUiState(
            isLoading = false,
            error = "主时间线错误",
            userIsLoading = true,
            userError = "用户动态错误"
        )

        assertTrue(
            resolveDynamicActiveLoadingState(
                currentState = state,
                selectedUserId = 10001L
            )
        )
        assertEquals(
            "用户动态错误",
            resolveDynamicActiveError(
                currentState = state,
                selectedUserId = 10001L
            )
        )
        assertEquals(
            "主时间线错误",
            resolveDynamicActiveError(
                currentState = state,
                selectedUserId = null
            )
        )
    }

    @Test
    fun `isDynamicItemRealUser rejects UGC season and PGC dynamics`() {
        val ugcSeasonItem = DynamicItem(
            type = "DYNAMIC_TYPE_UGC_SEASON",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 1001L, name = "合集作者", following = true)
            )
        )
        val pgcItem = DynamicItem(
            type = "DYNAMIC_TYPE_PGC",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 1002L, name = "番剧", following = true)
            )
        )
        val regularItem = DynamicItem(
            type = "DYNAMIC_TYPE_AV",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 1003L, name = "普通UP", following = true)
            )
        )

        assertFalse(isDynamicItemRealUser(ugcSeasonItem))
        assertFalse(isDynamicItemRealUser(pgcItem))
        assertTrue(isDynamicItemRealUser(regularItem))
    }

    @Test
    fun `isDynamicItemRealUser rejects authors with following set to false`() {
        val unfollowedItem = DynamicItem(
            type = "DYNAMIC_TYPE_AV",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 2001L, name = "未关注UP", following = false)
            )
        )
        val followedItem = DynamicItem(
            type = "DYNAMIC_TYPE_AV",
            modules = DynamicModules(
                module_author = DynamicAuthorModule(mid = 2001L, name = "已关注UP", following = true)
            )
        )

        assertFalse(isDynamicItemRealUser(unfollowedItem))
        assertTrue(isDynamicItemRealUser(followedItem))
    }

    @Test
    fun `extractUsersFromDynamicItems ignores UGC season and deduplicates identical name and face`() {
        val items = listOf(
            DynamicItem(
                type = "DYNAMIC_TYPE_UGC_SEASON",
                modules = DynamicModules(
                    module_author = DynamicAuthorModule(mid = 9001L, name = "装机猿PC问答3", face = "face1")
                )
            ),
            DynamicItem(
                type = "DYNAMIC_TYPE_UGC_SEASON",
                modules = DynamicModules(
                    module_author = DynamicAuthorModule(mid = 9002L, name = "装机猿PC问答3", face = "face1")
                )
            ),
            DynamicItem(
                type = "DYNAMIC_TYPE_AV",
                modules = DynamicModules(
                    module_author = DynamicAuthorModule(mid = 260882L, name = "远古时代装机猿", face = "face2", pub_ts = 1000L, following = true)
                )
            ),
            DynamicItem(
                type = "DYNAMIC_TYPE_AV",
                modules = DynamicModules(
                    module_author = DynamicAuthorModule(mid = 260882L, name = "远古时代装机猿", face = "face2", pub_ts = 2000L, following = true)
                )
            )
        )

        val users = extractUsersFromDynamicItems(items)

        assertEquals(1, users.size)
        assertEquals(260882L, users[0].uid)
        assertEquals("远古时代装机猿", users[0].name)
        assertEquals(2000L, users[0].lastActiveTs)
    }

    @Test
    fun `resolveMergedFollowedUsers preserves followed whitelist and does not add unfollowed authors from dynamics`() {
        val followingUsers = listOf(
            SidebarUser(uid = 101L, name = "关注UP 1", face = "face1"),
            SidebarUser(uid = 102L, name = "关注UP 2", face = "face2")
        )
        val dynamicUsers = listOf(
            SidebarUser(uid = 101L, name = "关注UP 1", face = "face1", lastActiveTs = 5000L),
            SidebarUser(uid = 999L, name = "装机猿PC问答3", face = "face_fake", lastActiveTs = 9999L)
        )
        val liveUsers = listOf(
            SidebarUser(uid = 102L, name = "关注UP 2", face = "face2", isLive = true, lastActiveTs = 6000L)
        )

        val merged = resolveMergedFollowedUsers(
            followingUsers = followingUsers,
            liveUsers = liveUsers,
            dynamicUsers = dynamicUsers
        )

        val uids = merged.map { it.uid }.toSet()
        assertEquals(setOf(101L, 102L), uids)
        assertFalse(uids.contains(999L), "未关注的陌生人或合集虚拟号绝不能进入关注列表")

        val user101 = merged.first { it.uid == 101L }
        assertEquals(5000L, user101.lastActiveTs)

        val user102 = merged.first { it.uid == 102L }
        assertTrue(user102.isLive)
        assertEquals(6000L, user102.lastActiveTs)
    }

    @Test
    fun `resolveMergedFollowedUsers fallback deduplicates identical name and face when followingUsers is empty`() {
        val dynamicUsers = listOf(
            SidebarUser(uid = 8001L, name = "相同UP", face = "same_face", lastActiveTs = 100L),
            SidebarUser(uid = 8002L, name = "相同UP", face = "same_face", lastActiveTs = 200L),
            SidebarUser(uid = 8003L, name = "另一个UP", face = "other_face", lastActiveTs = 300L)
        )

        val merged = resolveMergedFollowedUsers(
            followingUsers = emptyList(),
            liveUsers = emptyList(),
            dynamicUsers = dynamicUsers
        )

        assertEquals(2, merged.size)
        val names = merged.map { it.name }
        assertEquals(listOf("相同UP", "另一个UP"), names)
    }
}

private fun buildDynamicItem(
    id: String,
    authorMid: Long = 0L,
    pubTs: Long = 0L
) = DynamicItem(
    id_str = id,
    modules = DynamicModules(
        module_author = DynamicAuthorModule(mid = authorMid, pub_ts = pubTs)
    )
)
