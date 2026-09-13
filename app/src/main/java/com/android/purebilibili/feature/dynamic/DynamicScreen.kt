// 文件路径: feature/dynamic/DynamicScreen.kt
package com.android.purebilibili.feature.dynamic

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.FeedVerticalStaggeredGrid
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppTextField
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.navigation.animatePagerSelection

import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import kotlinx.coroutines.flow.distinctUntilChanged // [Fix] Missing import
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppListItem
import com.android.purebilibili.core.ui.components.AppRadioButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.dynamic.components.DynamicPublishComposer
import com.android.purebilibili.feature.dynamic.components.saveDynamicImageToGallery
import com.android.purebilibili.feature.dynamic.components.DynamicShareToMessageDialog
import com.android.purebilibili.feature.dynamic.components.DynamicAdaptiveSegmentedControl
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppSmallFloatingActionButton
import com.android.purebilibili.core.ui.components.AppLiquidGlassBackToTopButton
import top.yukonga.miuix.kmp.blur.Backdrop
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.rememberBackToTopButtonEnabled
import com.android.purebilibili.core.ui.rememberAppDynamicIcon
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.feature.dynamic.resolveDynamicHorizontalUserListHorizontalPadding
import com.android.purebilibili.feature.dynamic.resolveDynamicHorizontalUserListSpacing
import com.android.purebilibili.feature.dynamic.resolveDynamicTimelineHorizontalSpacing
import com.android.purebilibili.feature.dynamic.resolveDynamicTimelineMaxWidth
import com.android.purebilibili.feature.dynamic.resolveDynamicTimelineMinColumnWidth
import com.android.purebilibili.feature.dynamic.resolveDynamicTimelineVerticalSpacing

import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import com.android.purebilibili.feature.dynamic.components.DynamicCardActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardInteractionActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardNavigationActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardPresentation
import com.android.purebilibili.feature.dynamic.components.DynamicCommentOverlayHost
import com.android.purebilibili.feature.dynamic.components.DynamicSidebar
import com.android.purebilibili.feature.dynamic.components.DynamicUserLiveBadge
import com.android.purebilibili.feature.dynamic.components.DynamicTopBarWithTabs
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOnIcon
import com.android.purebilibili.feature.dynamic.components.DynamicDisplayMode
import com.android.purebilibili.feature.dynamic.components.isHorizontalUserList
import com.android.purebilibili.feature.dynamic.components.isRightAligned
import com.android.purebilibili.feature.dynamic.components.isDrawer
import com.android.purebilibili.feature.dynamic.components.resolveDynamicReportReasons
import com.android.purebilibili.feature.dynamic.components.DynamicCommentSheet
import com.android.purebilibili.feature.dynamic.components.RepostDialog
import com.android.purebilibili.feature.dynamic.components.DynamicSubReplyPreviewHost
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockEdge
import com.android.purebilibili.feature.home.components.BottomBarMatchedDockVisibility
import com.android.purebilibili.feature.home.policy.resolveBottomBarChromeScrollOffset
import com.android.purebilibili.core.util.resolveScrollToTopPlan
import kotlinx.coroutines.channels.Channel
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val LocalDynamicScrollChannel = compositionLocalOf<Channel<Unit>?> { null }

/**
 *  动态页面 - 支持两种布局模式
 *
 * 1. SIDEBAR 模式：UP 主列表在左侧边栏
 * 2. HORIZONTAL 模式：UP 主列表在顶部横向滚动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel = viewModel(),
    isCurrentPage: Boolean = true,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit = { _, _ -> },
    onArticleClick: ((Long, String) -> Unit)? = null,
    onDynamicDetailClick: (String) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> },
    onMusicClick: ((Long) -> Unit)? = null,
    onCollectionClick: ((Long, Long, String, String) -> Unit)? = null,
    onCourseClick: ((String, String) -> Unit)? = null,
    onSaveDynamicClick: ((com.android.purebilibili.data.model.response.DynamicItem) -> Unit)? = null,
    onShareToMessageClick: ((com.android.purebilibili.data.model.response.DynamicItem) -> Unit)? = null,
    onCheckDynamicClick: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    onLoginClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    publishSkinDecoration: com.android.purebilibili.feature.home.components.DynamicPublishSkinDecoration? = null,
    globalHazeState: dev.chrisbanes.haze.HazeState? = null  // [新增] 全局底栏模糊状态
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val allListState = rememberLazyStaggeredGridState()
    val videoListState = rememberLazyStaggeredGridState()
    val pgcListState = rememberLazyStaggeredGridState()
    val articleListState = rememberLazyStaggeredGridState()
    val userListState = rememberLazyStaggeredGridState()
    val listStates = remember(
        allListState,
        videoListState,
        pgcListState,
        articleListState,
        userListState
    ) {
        mapOf(
            0 to allListState,
            1 to videoListState,
            2 to pgcListState,
            3 to articleListState,
            4 to userListState
        )
    }
    val sidebarUserListState = rememberLazyListState()
    val horizontalUserListState = rememberLazyListState()
    val dynamicScrollChannel = LocalDynamicScrollChannel.current
    val context = LocalContext.current
    var pendingMessageShare by remember { mutableStateOf<com.android.purebilibili.data.model.response.DynamicItem?>(null) }
    val dynamicMenuScope = rememberCoroutineScope()
    val saveDynamicFallback: (com.android.purebilibili.data.model.response.DynamicItem) -> Unit = { item ->
        dynamicMenuScope.launch {
            val saved = saveDynamicImageToGallery(context, item)
            android.widget.Toast.makeText(
                context,
                if (saved) "已保存动态图片" else "保存动态失败",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val checkDynamicFallback: (String) -> Unit = { id ->
        viewModel.checkDynamic(id) { _, message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 侧边栏状态
    val followedUsers by viewModel.followedUsers.collectAsStateWithLifecycle()
    val selectedUserId by viewModel.selectedUserId.collectAsStateWithLifecycle()
    val selfUid = TokenManager.midCache ?: 0L
    val selfFace = remember(context, selfUid) {
        AccountSessionStore.getAccounts(context).firstOrNull { it.mid == selfUid }?.face.orEmpty()
    }
    val displayUsers = remember(followedUsers, selfUid, selfFace) {
        resolveDynamicUpPanelUsers(
            users = followedUsers,
            selfUid = selfUid,
            selfFace = selfFace
        )
    }
    val isSidebarExpanded by viewModel.isSidebarExpanded.collectAsStateWithLifecycle()
    val showHiddenUsers by viewModel.showHiddenUsers.collectAsStateWithLifecycle()
    val hiddenUserIds by viewModel.hiddenUserIds.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    var selectedUserContentFilterName by rememberSaveable(selectedUserId) {
        mutableStateOf(DynamicUserContentFilter.ALL.name)
    }
    val selectedUserContentFilter = remember(selectedUserContentFilterName) {
        runCatching { DynamicUserContentFilter.valueOf(selectedUserContentFilterName) }
            .getOrDefault(DynamicUserContentFilter.ALL)
    }
    val selectedUserName = remember(displayUsers, selectedUserId) {
        displayUsers.firstOrNull { it.uid == selectedUserId }?.name.orEmpty()
    }

    //  [新增] 点赞/转发状态
    val likedDynamics by viewModel.likedDynamics.collectAsStateWithLifecycle()
    val likeOverrides by viewModel.likeOverrides.collectAsStateWithLifecycle()
    var showRepostDialog by remember { mutableStateOf<String?>(null) }  // 存储要转发的动态ID
    var showPublishDialog by remember { mutableStateOf(false) }
    var dynamicTopActionsCollapsed by rememberSaveable { mutableStateOf(false) }
    var editingDynamicId by remember { mutableStateOf<String?>(null) }
    var editingDraft by remember {
        mutableStateOf(com.android.purebilibili.data.model.response.DynamicPublishDraft(text = ""))
    }
    var pendingReport by remember { mutableStateOf<com.android.purebilibili.feature.dynamic.components.DynamicManageAction.Report?>(null) }
    val manageActionCallback: (com.android.purebilibili.feature.dynamic.components.DynamicManageAction) -> Unit = { action ->
        com.android.purebilibili.feature.dynamic.components.dispatchDynamicManageAction(
            action = action,
            onReport = { pendingReport = it },
            onEdit = {
                editingDynamicId = it.dynamicId
                editingDraft = it.initialDraft
                showPublishDialog = true
            },
            onNotInterested = {
                viewModel.handleManageAction(it) { _, message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onOther = {
                viewModel.handleManageAction(it) { _, message ->
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    //  [新增] 动态 Feed 布局模式（瀑布流 / 列表）
    val dynamicFeedLayoutMode by SettingsManager.getDynamicFeedLayoutMode(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.DynamicFeedLayoutMode.WATERFALL)

    val dynamicVisibleTabIds by SettingsManager.getDynamicTabVisibleTabs(context)
        .collectAsStateWithLifecycle(initialValue = defaultDynamicTabVisibleIds)
    val dynamicAllTabHorizontalUserListVisible by SettingsManager
        .getDynamicAllTabHorizontalUserListVisible(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicTopBarCollapseOnScroll by SettingsManager
        .getDynamicTopBarCollapseOnScroll(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val visibleTabs = remember(dynamicVisibleTabIds) {
        resolveDynamicVisibleTabs(dynamicVisibleTabIds)
    }
    val isUserTabVisible = remember(visibleTabs) {
        isDynamicUserTabVisible(visibleTabs)
    }
    val activeSelectedTab = remember(selectedTab, visibleTabs) {
        resolveDynamicSelectedTabWithinVisibleTabs(
            selectedTab = selectedTab,
            visibleTabs = visibleTabs
        )
    }
    val selectedVisibleTabIndex = remember(activeSelectedTab, visibleTabs) {
        resolveDynamicSelectedVisibleTabIndex(
            selectedTab = activeSelectedTab,
            visibleTabs = visibleTabs
        )
    }
    val tabTitles = remember(visibleTabs) { visibleTabs.map { it.title } }
    val pagerState = rememberPagerState(
        pageCount = { visibleTabs.size },
        initialPage = selectedVisibleTabIndex
    )
    val dynamicTabIndicatorPositionProvider = remember(pagerState, visibleTabs) {
        {
            resolveDynamicPagerIndicatorPosition(
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                pageCount = visibleTabs.size
            )
        }
    }
    val dynamicTabScrollInProgressProvider = remember(pagerState) {
        { pagerState.isScrollInProgress }
    }
    val displayedTabIndex = pagerState.settledPage.coerceIn(0, visibleTabs.lastIndex.coerceAtLeast(0))
    val displayedLogicalTab = resolveDynamicSettledLogicalTab(displayedTabIndex, visibleTabs)
        ?: activeSelectedTab
    val activeListState = listStates[displayedLogicalTab]
    val pagerMotionSpec = AppMotionTokens.emphasizedSpec<Float>()

    LaunchedEffect(activeSelectedTab, pagerState.pageCount) {
        val targetIndex = visibleTabs.indexOfFirst { it.logicalIndex == activeSelectedTab }
        if (targetIndex in visibleTabs.indices && targetIndex != pagerState.settledPage) {
            animatePagerSelection(
                pagerState = pagerState,
                targetPage = targetIndex,
                animationSpec = pagerMotionSpec
            )
        }
    }

    LaunchedEffect(pagerState, visibleTabs) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                resolveDynamicSettledLogicalTab(settledPage, visibleTabs)
                    ?.let(viewModel::setSelectedTab)
            }
    }
    val isSelectedUserTabActive = remember(displayedLogicalTab, selectedUserId) {
        shouldUseSelectedUserDynamicFeed(
            selectedTab = displayedLogicalTab,
            selectedUserId = selectedUserId
        )
    }

    //  布局模式状态（侧边栏/横向）
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    val shouldShowHorizontalUserList = remember(
        displayMode,
        displayedLogicalTab,
        dynamicAllTabHorizontalUserListVisible
    ) {
        shouldShowDynamicHorizontalUserList(
            isHorizontalMode = displayMode.isHorizontalUserList(),
            selectedTab = displayedLogicalTab,
            allTabHorizontalUserListVisible = dynamicAllTabHorizontalUserListVisible
        )
    }

    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    // 顶部高斯模糊使用独立 Haze 源；液态玻璃的 Backdrop 渐进模糊仍单独由
    // DynamicTopBarWithTabs 根据安卓原生液态玻璃开关控制。
    val dynamicTopBarHazeState = if (
        appThemeConfig.liquidGlassEnabled || appThemeConfig.headerBlurEnabled
    ) {
        rememberRecoverableHazeState(initialBlurEnabled = true)
    } else {
        null
    }
    val scope = rememberCoroutineScope()
    val onDynamicTabSelected: (Int) -> Unit = { visibleIndex ->
        scope.launch {
            when (resolveDynamicTabReselectAction(displayedTabIndex, visibleIndex)) {
                DynamicTabReselectAction.SCROLL_TO_TOP -> {
                    activeListState?.animateScrollToItem(0)
                }
                DynamicTabReselectAction.SWITCH_TAB -> {
                    // Reuse the shared pager-follow deformation: tap switching now drives the
                    // same indicator stretch, scale and settle motion as other tab docks.
                    animatePagerSelection(pagerState, visibleIndex)
                }
            }
        }
    }

    LaunchedEffect(viewModel, isCurrentPage) {
        if (isCurrentPage) {
            viewModel.activateStartupLoads()
        }
    }

    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val dynamicListBottomPadding = LocalBottomBarContentPadding.current
    val pullRefreshState = rememberPullToRefreshState()

    // GIF 图片加载器
    val gifImageLoader = context.imageLoader
    val shouldShowBackToTop by remember(activeListState) {
        derivedStateOf {
            val state = activeListState ?: return@derivedStateOf false
            shouldShowDynamicBackToTop(
                firstVisibleItemIndex = state.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset
            )
        }
    }
    val shouldCollapseTopBar by remember(
        activeListState,
        dynamicTopBarCollapseOnScroll,
    ) {
        derivedStateOf {
            val state = activeListState ?: return@derivedStateOf false
            shouldCollapseDynamicTopBar(
                collapseOnScrollEnabled = dynamicTopBarCollapseOnScroll,
                firstVisibleItemIndex = state.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                topTolerancePx = DynamicHeaderCollapseTriggerPx,
            )
        }
    }
    LaunchedEffect(activeSelectedTab, selectedTab) {
        if (selectedTab != activeSelectedTab) {
            viewModel.setSelectedTab(activeSelectedTab)
        }
    }
    var previousFeedSelectedUserId by remember {
        mutableStateOf(selectedUserId.takeIf { isSelectedUserTabActive })
    }
    LaunchedEffect(selectedUserId, isSelectedUserTabActive) {
        val activeUserId = selectedUserId.takeIf { isSelectedUserTabActive }
        if (previousFeedSelectedUserId != activeUserId && isSelectedUserTabActive) {
            userListState.scrollToItem(0)
        }
        previousFeedSelectedUserId = activeUserId
    }
    val handleUserSelection = remember(selectedUserId, activeSelectedTab, isUserTabVisible, onUserClick) {
        { clickedUserId: Long? ->
            if (isDynamicUpPanelAllShortcut(clickedUserId)) {
                viewModel.selectUser(null)
                if (activeSelectedTab != 0) {
                    viewModel.setSelectedTab(0)
                }
            } else if (!isUserTabVisible) {
                if (clickedUserId != null) {
                    onUserClick(clickedUserId)
                }
            } else {
                val nextUserId = resolveDynamicSelectedUserIdAfterClick(
                    selectedUserId = selectedUserId,
                    clickedUserId = clickedUserId
                )
                val nextTab = resolveDynamicTabAfterUserSelection(
                    selectedUserId = selectedUserId,
                    clickedUserId = clickedUserId,
                    currentTab = activeSelectedTab
                )

                if (nextUserId == null && nextTab != activeSelectedTab) {
                    viewModel.setSelectedTab(nextTab)
                    viewModel.selectUser(null)
                } else {
                    viewModel.selectUser(nextUserId)
                    if (nextTab != activeSelectedTab) {
                        viewModel.setSelectedTab(nextTab)
                    }
                }
            }
        }
    }

    val activePresentation = remember(
        state,
        displayedLogicalTab,
        selectedUserId,
        selectedUserContentFilter,
    ) {
        resolveDynamicPagePresentation(state, displayedLogicalTab, selectedUserId)
            .withUserContentFilter(selectedUserContentFilter)
    }
    val filteredItems = activePresentation.items
    val oldContentDividerLabel = remember(displayedLogicalTab, visibleTabs) {
        if (displayedLogicalTab == 0) {
            "以下是之前的动态"
        } else {
            val tabTitle = visibleTabs.firstOrNull { it.logicalIndex == displayedLogicalTab }?.title ?: "内容"
            "以下是之前的${tabTitle}"
        }
    }
    val oldContentDividerIndex = remember(
        filteredItems,
        selectedUserId,
        activePresentation.incrementalRefreshBoundaryKey,
        activePresentation.incrementalPrependedCount
    ) {
        if (isSelectedUserTabActive) {
            -1
        } else {
            resolveOldContentDividerIndex(
                displayKeys = filteredItems.map(::dynamicFeedItemKey),
                boundaryKey = activePresentation.incrementalRefreshBoundaryKey,
                showDivider = activePresentation.incrementalPrependedCount > 0
            )
        }
    }
    val currentHasMore = activePresentation.hasMore
    val activeLoading = activePresentation.isLoading
    val activeError = activePresentation.error
    val allowAutomaticLoadMore = remember(
        isSelectedUserTabActive,
        selectedUserContentFilter,
        filteredItems.size,
    ) {
        shouldAutoLoadMoreForUserContentFilter(
            isSelectedUserFeed = isSelectedUserTabActive,
            filter = selectedUserContentFilter,
            visibleItemCount = filteredItems.size,
        )
    }

    var handledUserListRefreshBoundary by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        state.timelinePages,
        selectedUserId
    ) {
        val allPage = state.timelinePage("all")
        val boundaryKey = allPage.incrementalRefreshBoundaryKey
        if (!shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = boundaryKey,
                prependedCount = allPage.incrementalPrependedCount,
                selectedUserId = selectedUserId,
                handledBoundaryKey = handledUserListRefreshBoundary
            )
        ) {
            return@LaunchedEffect
        }
        handledUserListRefreshBoundary = boundaryKey
        sidebarUserListState.scrollToItem(0)
        horizontalUserListState.scrollToItem(0)
    }

    // 加载更多
    val shouldLoadMore by remember(
        activeListState,
        activeLoading,
        currentHasMore,
        allowAutomaticLoadMore,
    ) {
        derivedStateOf {
            val state = activeListState ?: return@derivedStateOf false
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            // Staggered-grid visible items are lane-oriented; their list order is not a
            // pagination contract. Use the furthest adapter index across every visible lane.
            val furthestVisibleItemIndex = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index }
            shouldLoadMoreDynamicFeed(
                furthestVisibleItemIndex = furthestVisibleItemIndex,
                totalItemsCount = totalItems,
                allowAutomaticLoadMore = allowAutomaticLoadMore,
                isLoading = activeLoading,
                hasMore = currentHasMore,
            )
        }
    }
    //  [埋点] 页面浏览追踪
    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("DynamicScreen")
    }

    //  [修改] 加载更多 - 区分全部动态和用户动态
    LaunchedEffect(
        shouldLoadMore,
        selectedUserId,
        isSelectedUserTabActive,
        displayedLogicalTab
    ) {
        if (shouldLoadMore) {
            if (isSelectedUserTabActive) {
                viewModel.loadMoreUserDynamics()
            } else {
                viewModel.loadMore(displayedLogicalTab)
            }
        }
    }

    // [Feature] BottomBar Scroll Hiding for Dynamic Screen
    val setBottomBarVisible = com.android.purebilibili.core.ui.LocalSetBottomBarVisible.current
    val bottomBarChromeScrollOffset = LocalHomeScrollOffset.current

    suspend fun scrollDynamicFeedToTop(refreshWhenAlreadyAtTop: Boolean) {
        val state = activeListState ?: return
        val isAtTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 50
        if (isAtTop) {
            if (refreshWhenAlreadyAtTop) {
                viewModel.refresh(displayedLogicalTab)
            }
            return
        }

        val currentIndex = state.firstVisibleItemIndex
        val plan = resolveScrollToTopPlan(currentIndex)
        plan.preJumpIndex?.let { preJump ->
            if (currentIndex > preJump) {
                state.scrollToItem(preJump)
            }
        }
        state.animateScrollToItem(plan.animateTargetIndex)
    }

    LaunchedEffect(dynamicScrollChannel) {
        dynamicScrollChannel?.receiveAsFlow()?.collect {
            scrollDynamicFeedToTop(refreshWhenAlreadyAtTop = true)
        }
    }

    // 瀑布流中首个可见 item 会在不同 lane 间切换，不能用 index 推断滚动方向。
    // 底栏显隐还会改变 scaffold 的 bottom contentPadding，触发不等高卡片重新分配，
    // 造成平板端上下滑动时动态位置抽搐。因此瀑布流保持底栏稳定，仅普通列表自动隐藏。
    val shouldAutoCollapseBottomBar =
        dynamicFeedLayoutMode != SettingsManager.DynamicFeedLayoutMode.WATERFALL

    // 监听列表滚动实现底栏自动隐藏/显示（仅普通列表）
    var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(filteredItems.size, activeLoading, displayedLogicalTab, isSelectedUserTabActive) {
        if (shouldRevealDynamicBottomBarForStaticContent(
                activeItemsCount = filteredItems.size,
                isLoading = activeLoading
            )
        ) {
            setBottomBarVisible(true)
            bottomBarChromeScrollOffset.value = 0f
            // 数据刷新/分页后从真实布局位置重新建立基线，避免下一帧被误判为大幅下滑。
            activeListState?.let { listState ->
                lastFirstVisibleItem = listState.firstVisibleItemIndex
                lastScrollOffset = listState.firstVisibleItemScrollOffset
            }
        }
    }

    LaunchedEffect(activeListState, shouldAutoCollapseBottomBar) {
        val state = activeListState ?: return@LaunchedEffect
        if (!shouldAutoCollapseBottomBar) {
            setBottomBarVisible(true)
            bottomBarChromeScrollOffset.value = 0f
        }
        snapshotFlow {
            Pair(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
        .distinctUntilChanged()
        .collect { (firstVisibleItem, scrollOffset) ->
             // 顶部始终显示
             if (shouldAutoCollapseBottomBar) {
                 if (firstVisibleItem == 0 && scrollOffset < 100) {
                     setBottomBarVisible(true)
                 } else {
                 val isScrollingDown = when {
                     firstVisibleItem > lastFirstVisibleItem -> true
                     firstVisibleItem < lastFirstVisibleItem -> false
                     else -> scrollOffset > lastScrollOffset + 50 // 较小的阈值
                 }
                 val isScrollingUp = when {
                     firstVisibleItem < lastFirstVisibleItem -> true
                     firstVisibleItem > lastFirstVisibleItem -> false
                     else -> scrollOffset < lastScrollOffset - 50
                 }

                 if (isScrollingDown) setBottomBarVisible(false)
                 if (isScrollingUp) setBottomBarVisible(true)
                 }
             } else {
                 // Waterfall keeps the navigation bar mounted, but the linked playback
                 // strip still follows the same scroll position and can merge globally.
                 setBottomBarVisible(true)
             }
             lastFirstVisibleItem = firstVisibleItem
             lastScrollOffset = scrollOffset
             bottomBarChromeScrollOffset.value = resolveBottomBarChromeScrollOffset(
                 firstVisibleItem = firstVisibleItem,
                 scrollOffset = scrollOffset
             )
        }
    }

    // 离开页面时恢复底栏显示 (特别是进入详情页或其他 Tab)
    DisposableEffect(Unit) {
        onDispose {
            setBottomBarVisible(true)
            bottomBarChromeScrollOffset.value = 0f
        }
    }

    AppScaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent // 透明背景以显示渐变
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景层 - 自适应 MaterialTheme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .globalWallpaperAwareBackground()
            ) {
                 // 移除光晕 Canvas，保持纯净背景
            }

            //  [新增] 模式切换动画
            val modeEnterFadeSpec = AppMotionTokens.emphasizedSpec<Float>()
            val modeExitFadeSpec = AppMotionTokens.standardSpec<Float>()
            var activeDynamicBackdrop by remember { mutableStateOf<Backdrop?>(null) }
            AnimatedContent(
                targetState = displayMode,
                transitionSpec = {
                    //  根据切换方向使用不同动画
                    val slideDirection = if (targetState.isHorizontalUserList()) {
                        // 从侧边栏切换到横向：向左滑出+淡出，向左滑入+淡入
                        (slideInHorizontally { -it / 4 } + fadeIn(animationSpec = modeEnterFadeSpec)) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut(animationSpec = modeExitFadeSpec))
                    } else {
                        // 从横向切换到侧边栏：向右滑出+淡出，向右滑入+淡入
                        (slideInHorizontally { it / 4 } + fadeIn(animationSpec = modeEnterFadeSpec)) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut(animationSpec = modeExitFadeSpec))
                    }
                    slideDirection.using(SizeTransform(clip = false))
                },
                label = "displayModeTransition"
            ) { targetMode ->
                // Each animated layout owns one source; outgoing/incoming trees must not share it.
                val dynamicDockSource = if (
                    appThemeConfig.progressiveTopBlurEnabled ||
                    appThemeConfig.headerBlurEnabled ||
                    appThemeConfig.liquidGlassEnabled
                ) {
                    com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
                } else {
                    null
                }
                val dynamicDockBackdrop = dynamicDockSource?.takeIf {
                    !(activePresentation.isLoading && activePresentation.items.isEmpty()) && it.isReady
                }?.backdrop
                SideEffect {
                    if (activeDynamicBackdrop != dynamicDockBackdrop) {
                        activeDynamicBackdrop = dynamicDockBackdrop
                    }
                }
                //  根据布局模式选择不同布局
                when (targetMode) {
                    DynamicDisplayMode.SIDEBAR,
                    DynamicDisplayMode.SIDEBAR_RIGHT,
                    DynamicDisplayMode.DRAWER_LEFT,
                    DynamicDisplayMode.DRAWER_RIGHT -> {
                        val sidebarOnRight = targetMode.isRightAligned()
                        @Composable
                        fun UpPanelSidebar() {
                            DynamicSidebar(
                                users = displayUsers,
                                selectedUserId = selectedUserId,
                                selfUid = selfUid,
                                isExpanded = isSidebarExpanded,
                                userListState = sidebarUserListState,
                                onUserClick = { userId ->
                                    handleUserSelection(userId)
                                    if (targetMode.isDrawer() && isSidebarExpanded) {
                                        viewModel.toggleSidebar()
                                    }
                                },
                                showHiddenUsers = showHiddenUsers,
                                hiddenCount = hiddenUserIds.size,
                                uplistUpdateMids = state.uplistUpdateMids,
                                onToggleShowHidden = { viewModel.toggleShowHiddenUsers() },
                                onTogglePin = { viewModel.togglePinUser(it) },
                                onToggleHidden = { viewModel.toggleHiddenUser(it) },
                                onToggleExpand = { viewModel.toggleSidebar() },
                                topPadding = statusBarHeight,
                                onBackClick = {
                                    if (targetMode.isDrawer()) {
                                        viewModel.toggleSidebar()
                                    } else {
                                        onBack()
                                    }
                                }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                        if (!sidebarOnRight && (!targetMode.isDrawer() || isSidebarExpanded)) {
                            UpPanelSidebar()
                        }

                        // 内容区
                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        dynamicDockSource?.modifier ?: Modifier
                                    )
                                    .then(
                                        if (dynamicTopBarHazeState != null) {
                                            Modifier.hazeSourceCompat(state = dynamicTopBarHazeState)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .globalWallpaperAwareBackground(AppSurfaceTokens.background())
                            ) {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalPriorityHorizontalPagerSwipe(
                                        state = pagerState,
                                        enabled = true,
                                    ),
                                key = { page -> visibleTabs[page].logicalIndex }
                            ) { page ->
                                val tab = visibleTabs[page]
                                val pageListState = requireNotNull(listStates[tab.logicalIndex])
                                val pagePresentation = remember(
                                    state,
                                    tab.logicalIndex,
                                    selectedUserId,
                                    selectedUserContentFilter,
                                ) {
                                    resolveDynamicPagePresentation(state, tab.logicalIndex, selectedUserId)
                                        .withUserContentFilter(selectedUserContentFilter)
                                }
                                val pageDividerIndex = remember(pagePresentation) {
                                    if (pagePresentation.isSelectedUserFeed) {
                                        -1
                                    } else {
                                        resolveOldContentDividerIndex(
                                            displayKeys = pagePresentation.items.map(::dynamicFeedItemKey),
                                            boundaryKey = pagePresentation.incrementalRefreshBoundaryKey,
                                            showDivider = pagePresentation.incrementalPrependedCount > 0
                                        )
                                    }
                                }
                                val pageDividerLabel = if (tab.logicalIndex == 0) {
                                    "以下是之前的动态"
                                } else {
                                    "以下是之前的${tab.title}"
                                }
                                val pageListTopExtra = resolveDynamicListTopPaddingExtraDp(
                                    isHorizontalMode = false,
                                ).dp
                                // Overlay top bar (not Scaffold-padded) — anchor indicator under chrome.
                                val dynamicRefreshIndicatorTopInset =
                                    statusBarHeight + pageListTopExtra
                                AdaptivePullToRefreshBox(
                                    isRefreshing = isRefreshing,
                                    onRefresh = { viewModel.refresh(tab.logicalIndex) },
                                    state = pullRefreshState,
                                    indicatorTopInset = dynamicRefreshIndicatorTopInset,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    DynamicList(
                                        state = state,
                                        activeLoading = pagePresentation.isLoading,
                                        activeError = pagePresentation.error,
                                        hasMore = pagePresentation.hasMore,
                                        selectedTab = tab.logicalIndex,
                                        isSelectedUserTabActive = pagePresentation.isSelectedUserFeed,
                                        selectedUserName = selectedUserName,
                                        selectedUserContentFilter = selectedUserContentFilter,
                                        onSelectedUserContentFilterChange = { filter ->
                                            selectedUserContentFilterName = filter.name
                                        },
                                        onOpenSelectedUser = {
                                            selectedUserId?.takeIf { it > 0L }?.let(onUserClick)
                                        },
                                        filteredItems = pagePresentation.items,
                                        listState = pageListState,
                                        statusBarHeight = statusBarHeight,
                                        topPaddingExtra = pageListTopExtra,
                                        bottomPadding = dynamicListBottomPadding,
                                        oldContentDividerIndex = pageDividerIndex,
                                        oldContentDividerLabel = pageDividerLabel,
                                        onVideoClick = onVideoClick,
                                        onBangumiClick = onBangumiClick,
                                        onArticleClick = onArticleClick,
                                        onDynamicDetailClick = onDynamicDetailClick,
                                        onUnfoldRelatedClick = viewModel::unfoldRelatedDynamics,
                                        onUserClick = onUserClick,
                                        onTopicClick = onTopicClick,
                                        onTopicKeywordClick = onTopicKeywordClick,
                                        onLiveClick = onLiveClick,
                                        onMusicClick = onMusicClick,
                                        onCollectionClick = onCollectionClick,
                                        onCourseClick = onCourseClick,
                                        onSaveDynamicClick = onSaveDynamicClick ?: saveDynamicFallback,
                                        onShareToMessageClick = onShareToMessageClick ?: { pendingMessageShare = it },
                                        onCheckDynamicClick = onCheckDynamicClick ?: checkDynamicFallback,
                                        onReserveClick = viewModel::toggleDynamicReserve,
                                        onLoginClick = onLoginClick,
                                        gifImageLoader = gifImageLoader,
                                        onCommentClick = onDynamicDetailClick,
                                        onRepostClick = { showRepostDialog = it },
                                        onLikeClick = { dynamicId ->
                                            viewModel.likeDynamic(dynamicId) { _, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onWatchLaterClick = { aid ->
                                            viewModel.addToWatchLater(aid) { _, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDeleteClick = { action ->
                                            viewModel.deleteDynamic(action) { _, msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onManageAction = manageActionCallback,
                                        likedDynamics = likedDynamics,
                                        likeOverrides = likeOverrides,
                                        feedLayoutMode = dynamicFeedLayoutMode,
                                        modifier = Modifier
                                    )
                                }
                            }
                            }

                            // 顶栏（下滑折叠，回顶复现）
                            BottomBarMatchedDockVisibility(
                                visible = !shouldCollapseTopBar,
                                edge = BottomBarMatchedDockEdge.TOP,
                                modifier = Modifier.align(Alignment.TopCenter),
                                animateScale = false,
                            ) {
                                DynamicTopBarWithTabs(
                                    selectedTab = displayedTabIndex,
                                    tabs = tabTitles,
                                    onTabSelected = onDynamicTabSelected,
                                    displayMode = displayMode,
                                    onDisplayModeChange = { viewModel.setDisplayMode(it) },
                                    onPublishClick = { showPublishDialog = true },
                                    actionDockCollapsed = dynamicTopActionsCollapsed,
                                    onActionDockCollapsedChange = { dynamicTopActionsCollapsed = it },
                                    publishSkinDecoration = publishSkinDecoration,
                                    dockBackdrop = dynamicDockBackdrop,
                                    hazeState = dynamicTopBarHazeState,
                                    indicatorPositionProvider = dynamicTabIndicatorPositionProvider,
                                    isScrollInProgressProvider = dynamicTabScrollInProgressProvider,
                                )
                            }

                            // 错误提示
                            ErrorOverlay(
                                error = activeError,
                                activeItemsCount = filteredItems.size,
                                onLoginClick = onLoginClick,
                                onRetry = {
                                    if (isSelectedUserTabActive) {
                                        selectedUserId?.let(viewModel::selectUser)
                                    } else {
                                        viewModel.refresh(displayedLogicalTab)
                                    }
                                },
                                modifier = Modifier.align(Alignment.Center)
                            )

                            if (targetMode.isDrawer() && !isSidebarExpanded) {
                                AppSmallFloatingActionButton(
                                    onClick = { viewModel.toggleSidebar() },
                                    modifier = Modifier
                                        .align(if (sidebarOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                                        .padding(8.dp)
                                ) {
                                    AppText(
                                        text = if (sidebarOnRight) "‹" else "›",
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                        if (sidebarOnRight && (!targetMode.isDrawer() || isSidebarExpanded)) {
                            UpPanelSidebar()
                        }
                    }
                }

                DynamicDisplayMode.HORIZONTAL -> {
                    // 横向模式（UP 主列表在顶部）
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    dynamicDockSource?.modifier ?: Modifier
                                )
                                .then(
                                    if (dynamicTopBarHazeState != null) {
                                        Modifier.hazeSourceCompat(state = dynamicTopBarHazeState)
                                    } else {
                                        Modifier
                                    }
                                )
                                .globalWallpaperAwareBackground(AppSurfaceTokens.background())
                        ) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalPriorityHorizontalPagerSwipe(
                                    state = pagerState,
                                    enabled = true,
                                ),
                            key = { page -> visibleTabs[page].logicalIndex }
                        ) { page ->
                            val tab = visibleTabs[page]
                            val pageListState = requireNotNull(listStates[tab.logicalIndex])
                            val pagePresentation = remember(
                                state,
                                tab.logicalIndex,
                                selectedUserId,
                                selectedUserContentFilter,
                            ) {
                                resolveDynamicPagePresentation(state, tab.logicalIndex, selectedUserId)
                                    .withUserContentFilter(selectedUserContentFilter)
                            }
                            val pageDividerIndex = remember(pagePresentation) {
                                if (pagePresentation.isSelectedUserFeed) {
                                    -1
                                } else {
                                    resolveOldContentDividerIndex(
                                        displayKeys = pagePresentation.items.map(::dynamicFeedItemKey),
                                        boundaryKey = pagePresentation.incrementalRefreshBoundaryKey,
                                        showDivider = pagePresentation.incrementalPrependedCount > 0
                                    )
                                }
                            }
                            val pageDividerLabel = if (tab.logicalIndex == 0) {
                                "以下是之前的动态"
                            } else {
                                "以下是之前的${tab.title}"
                            }
                            val pageListTopExtra = resolveDynamicListTopPaddingExtraDp(
                                isHorizontalMode = true,
                                shouldShowHorizontalUserList = shouldShowHorizontalUserList,
                            ).dp
                            val dynamicRefreshIndicatorTopInset =
                                statusBarHeight + pageListTopExtra
                            AdaptivePullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = { viewModel.refresh(tab.logicalIndex) },
                                state = pullRefreshState,
                                indicatorTopInset = dynamicRefreshIndicatorTopInset,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                DynamicList(
                                    state = state,
                                    activeLoading = pagePresentation.isLoading,
                                    activeError = pagePresentation.error,
                                    hasMore = pagePresentation.hasMore,
                                    selectedTab = tab.logicalIndex,
                                    isSelectedUserTabActive = pagePresentation.isSelectedUserFeed,
                                    selectedUserName = selectedUserName,
                                    selectedUserContentFilter = selectedUserContentFilter,
                                    onSelectedUserContentFilterChange = { filter ->
                                        selectedUserContentFilterName = filter.name
                                    },
                                    onOpenSelectedUser = {
                                        selectedUserId?.takeIf { it > 0L }?.let(onUserClick)
                                    },
                                    filteredItems = pagePresentation.items,
                                    listState = pageListState,
                                    statusBarHeight = statusBarHeight,
                                    topPaddingExtra = pageListTopExtra,
                                    bottomPadding = dynamicListBottomPadding,
                                    oldContentDividerIndex = pageDividerIndex,
                                    oldContentDividerLabel = pageDividerLabel,
                                    onVideoClick = onVideoClick,
                                    onBangumiClick = onBangumiClick,
                                    onArticleClick = onArticleClick,
                                    onDynamicDetailClick = onDynamicDetailClick,
                                    onUnfoldRelatedClick = viewModel::unfoldRelatedDynamics,
                                    onUserClick = onUserClick,
                                    onTopicClick = onTopicClick,
                                    onTopicKeywordClick = onTopicKeywordClick,
                                    onLiveClick = onLiveClick,
                                    onMusicClick = onMusicClick,
                                    onCollectionClick = onCollectionClick,
                                    onCourseClick = onCourseClick,
                                    onSaveDynamicClick = onSaveDynamicClick ?: saveDynamicFallback,
                                    onShareToMessageClick = onShareToMessageClick ?: { pendingMessageShare = it },
                                    onCheckDynamicClick = onCheckDynamicClick ?: checkDynamicFallback,
                                    onReserveClick = viewModel::toggleDynamicReserve,
                                    onLoginClick = onLoginClick,
                                    gifImageLoader = gifImageLoader,
                                    onCommentClick = onDynamicDetailClick,
                                    onRepostClick = { showRepostDialog = it },
                                    onLikeClick = { dynamicId ->
                                        viewModel.likeDynamic(dynamicId) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onWatchLaterClick = { aid ->
                                        viewModel.addToWatchLater(aid) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDeleteClick = { action ->
                                        viewModel.deleteDynamic(action) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onManageAction = manageActionCallback,
                                    onLoadReplyInteractionStatus = { oid, type, onLoaded ->
                                        viewModel.loadReplyInteractionStatus(oid, type, onLoaded)
                                    },
                                    likedDynamics = likedDynamics,
                                    likeOverrides = likeOverrides,
                                    feedLayoutMode = dynamicFeedLayoutMode,
                                    modifier = Modifier
                                )
                            }
                        }
                        }

                        // 顶部区域：顶栏 + 横向用户列表
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                        ) {
                            // 顶栏（下滑折叠，回顶复现）
                            BottomBarMatchedDockVisibility(
                                visible = !shouldCollapseTopBar,
                                edge = BottomBarMatchedDockEdge.TOP,
                                modifier = Modifier.zIndex(1f),
                                animateScale = false,
                            ) {
                                DynamicTopBarWithTabs(
                                    selectedTab = displayedTabIndex,
                                    tabs = tabTitles,
                                    onTabSelected = onDynamicTabSelected,
                                    displayMode = displayMode,
                                    onDisplayModeChange = { viewModel.setDisplayMode(it) },
                                    onPublishClick = { showPublishDialog = true },
                                    actionDockCollapsed = dynamicTopActionsCollapsed,
                                    onActionDockCollapsedChange = { dynamicTopActionsCollapsed = it },
                                    publishSkinDecoration = publishSkinDecoration,
                                    dockBackdrop = dynamicDockBackdrop,
                                    hazeState = dynamicTopBarHazeState,
                                    indicatorPositionProvider = dynamicTabIndicatorPositionProvider,
                                    isScrollInProgressProvider = dynamicTabScrollInProgressProvider,
                                    shouldShowHorizontalUserList = shouldShowHorizontalUserList,
                                )
                            }

                            if (shouldShowHorizontalUserList) {
                                val expandedUserListHeightPx = with(density) {
                                    DynamicHorizontalUserListReservedHeightDp.dp.roundToPx()
                                }
                                HorizontalUserList(
                                    users = displayUsers,
                                    selectedUserId = selectedUserId,
                                    selfUid = selfUid,
                                    listState = horizontalUserListState,
                                    showHiddenUsers = showHiddenUsers,
                                    hiddenCount = hiddenUserIds.size,
                                    uplistUpdateMids = state.uplistUpdateMids,
                                    onUserClick = handleUserSelection,
                                    onToggleShowHidden = { viewModel.toggleShowHiddenUsers() },
                                    onTogglePin = { viewModel.togglePinUser(it) },
                                    onToggleHidden = { viewModel.toggleHiddenUser(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // 与内容滚动逐像素联动：下滑时自然上收并裁切，回顶时完整恢复。
                                        .dynamicScrollCollapseLayout(
                                            expandedHeightPx = expandedUserListHeightPx,
                                            listStateProvider = { activeListState },
                                        )
                                )
                            }
                        }

                        ErrorOverlay(
                            error = activeError,
                            activeItemsCount = filteredItems.size,
                            onLoginClick = onLoginClick,
                            onRetry = {
                                if (isSelectedUserTabActive) {
                                    selectedUserId?.let(viewModel::selectUser)
                                } else {
                                    viewModel.refresh(displayedLogicalTab)
                                }
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    }
                }
            }

            AppLiquidGlassBackToTopButton(
                visible = rememberBackToTopButtonEnabled() && shouldShowBackToTop,
                onClick = {
                    scope.launch {
                        scrollDynamicFeedToTop(refreshWhenAlreadyAtTop = false)
                    }
                },
                backdrop = activeDynamicBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, bottom = dynamicListBottomPadding + AppSpacingTokens.Medium),
            )
        }
    }

    DynamicCommentOverlayHost(
        viewModel = viewModel,
        primaryItems = filteredItems,
        secondaryItems = state.userItems,
        toastContext = context,
        onUserClick = onUserClick,
    )

    //  [新增] 转发弹窗
    showRepostDialog?.let { dynamicId ->
        RepostDialog(
            onDismiss = { showRepostDialog = null },
            onRepost = { content: String, onComplete: (Boolean) -> Unit ->
                viewModel.repostDynamic(dynamicId, content) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (success) showRepostDialog = null
                    onComplete(success)
                }
            }
        )
    }

    //  发布动态：图片 / 投票 / 预约走原生选择器和对话框
    if (showPublishDialog) {
        val isEditing = !editingDynamicId.isNullOrBlank()
        var submitting by remember { mutableStateOf(false) }
        var publishError by remember { mutableStateOf<String?>(null) }
        DynamicPublishComposer(
            initialDraft = editingDraft,
            isEditing = isEditing,
            submitting = submitting,
            errorMessage = publishError,
            onDismiss = {
                showPublishDialog = false
                editingDynamicId = null
                editingDraft = com.android.purebilibili.data.model.response.DynamicPublishDraft(text = "")
            },
            onSubmit = { draft ->
                val editId = editingDynamicId
                submitting = true
                publishError = null
                if (editId.isNullOrBlank()) {
                    viewModel.publishDynamic(draft = draft, context = context) { success, msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        submitting = false
                        if (success) {
                            showPublishDialog = false
                            editingDraft = com.android.purebilibili.data.model.response.DynamicPublishDraft(text = "")
                        } else {
                            publishError = msg
                        }
                    }
                } else {
                    viewModel.editDynamic(context, editId, draft) { success, msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        submitting = false
                        if (success) {
                            showPublishDialog = false
                            editingDynamicId = null
                            editingDraft = com.android.purebilibili.data.model.response.DynamicPublishDraft(text = "")
                        } else {
                            publishError = msg
                        }
                    }
                }
            }
        )
    }

    pendingReport?.let { reportAction ->
        var selectedReason by remember { mutableStateOf(resolveDynamicReportReasons().first()) }
        var otherDesc by remember { mutableStateOf("") }
        AppAlertDialog(
            onDismissRequest = { pendingReport = null },
            title = { AppText("举报动态") },
            text = {
                Column {
                    resolveDynamicReportReasons().forEach { reason ->
                        AppListItem(
                            headlineContent = { AppText(reason.label) },
                            trailingContent = {
                                AppRadioButton(
                                    selected = reason.type == selectedReason.type,
                                    onClick = { selectedReason = reason }
                                )
                            },
                            modifier = Modifier.clickable { selectedReason = reason }
                        )
                    }
                    if (selectedReason.type == 0) {
                        AppTextField(
                            value = otherDesc,
                            onValueChange = { otherDesc = it },
                            placeholder = "补充详细说明",
                            singleLine = false,
                            minLines = 2
                        )
                    }
                }
            },
            confirmButton = {
                AppDialogAction(
                    onClick = {
                        viewModel.reportDynamic(
                            action = reportAction,
                            reasonType = selectedReason.type,
                            reasonDesc = otherDesc
                        ) { _, msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            pendingReport = null
                        }
                    }
                ) {
                    AppText("提交")
                }
            },
            dismissButton = {
                AppDialogAction(onClick = { pendingReport = null }) {
                    AppText("取消")
                }
            }
        )
    }

    pendingMessageShare?.let { shareItem ->
        DynamicShareToMessageDialog(
            item = shareItem,
            onDismiss = { pendingMessageShare = null },
            onResult = { _, message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/**
 *  动态列表内容
 */
@Composable
private fun DynamicList(
    state: DynamicUiState,
    activeLoading: Boolean,
    activeError: String?,
    hasMore: Boolean,
    selectedTab: Int,
    isSelectedUserTabActive: Boolean,
    selectedUserName: String,
    selectedUserContentFilter: DynamicUserContentFilter,
    onSelectedUserContentFilterChange: (DynamicUserContentFilter) -> Unit,
    onOpenSelectedUser: () -> Unit,
    filteredItems: List<com.android.purebilibili.data.model.response.DynamicItem>,
    listState: LazyStaggeredGridState,
    statusBarHeight: androidx.compose.ui.unit.Dp,
    topPaddingExtra: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    oldContentDividerIndex: Int,
    oldContentDividerLabel: String,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onArticleClick: ((Long, String) -> Unit)?,
    onDynamicDetailClick: (String) -> Unit,
    onUnfoldRelatedClick: (String) -> Unit = {},
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit,
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onLiveClick: (Long, String, String) -> Unit,
    onMusicClick: ((Long) -> Unit)?,
    onCollectionClick: ((Long, Long, String, String) -> Unit)?,
    onCourseClick: ((String, String) -> Unit)?,
    onSaveDynamicClick: ((com.android.purebilibili.data.model.response.DynamicItem) -> Unit)?,
    onShareToMessageClick: ((com.android.purebilibili.data.model.response.DynamicItem) -> Unit)?,
    onCheckDynamicClick: ((String) -> Unit)?,
    onReserveClick: (
        com.android.purebilibili.feature.dynamic.components.DynamicReserveAction,
        (Result<com.android.purebilibili.feature.dynamic.components.DynamicReserveResult>) -> Unit,
    ) -> Unit,
    onLoginClick: () -> Unit,
    gifImageLoader: ImageLoader,
    //  [新增] 动态操作回调
    onCommentClick: (String) -> Unit = {},
    onRepostClick: (String) -> Unit = {},
    onLikeClick: (String) -> Unit = {},
    onWatchLaterClick: (Long) -> Unit = {},
    onDeleteClick: (DynamicDeleteAction) -> Unit = {},
    onManageAction: (com.android.purebilibili.feature.dynamic.components.DynamicManageAction) -> Unit = {},
    onLoadReplyInteractionStatus: ((oid: Long, type: Int, onLoaded: (com.android.purebilibili.data.model.response.ReplyInteractionData?) -> Unit) -> Unit)? = null,
    likedDynamics: Set<String> = emptySet(),
    likeOverrides: Map<String, Boolean> = emptyMap(),
    feedLayoutMode: SettingsManager.DynamicFeedLayoutMode = SettingsManager.DynamicFeedLayoutMode.WATERFALL,
    modifier: Modifier = Modifier
) {
    val dynamicCard: @Composable (com.android.purebilibili.data.model.response.DynamicItem) -> Unit = { item ->
        DynamicCardV2(
            item = item,
            gifImageLoader = gifImageLoader,
            actions = DynamicCardActions(
                navigation = DynamicCardNavigationActions(
                    onVideoClick = onVideoClick,
                    onBangumiClick = onBangumiClick,
                    onArticleClick = onArticleClick,
                    onDynamicDetailClick = onDynamicDetailClick,
                    onUnfoldRelatedClick = onUnfoldRelatedClick,
                    onUserClick = onUserClick,
                    onTopicClick = onTopicClick,
                    onTopicKeywordClick = onTopicKeywordClick,
                    onLiveClick = onLiveClick,
                    onMusicClick = onMusicClick,
                    onCollectionClick = onCollectionClick,
                    onCourseClick = onCourseClick,
                ),
                interaction = DynamicCardInteractionActions(
                    onSaveDynamicClick = { onSaveDynamicClick?.invoke(item) },
                    onShareToMessageClick = { onShareToMessageClick?.invoke(item) },
                    onCheckDynamicClick = { onCheckDynamicClick?.invoke(item.id_str) },
                    onReserveClick = onReserveClick,
                    onCommentClick = onCommentClick,
                    onRepostClick = onRepostClick,
                    onLikeClick = onLikeClick,
                    onWatchLaterClick = onWatchLaterClick,
                    onDeleteClick = onDeleteClick,
                    onManageAction = onManageAction,
                    onLoadReplyInteractionStatus = onLoadReplyInteractionStatus,
                ),
            ),
            presentation = DynamicCardPresentation(
                isLiked = likedDynamics.contains(item.id_str),
                likeOverride = likeOverrides[item.id_str],
            ),
        )
    }
    val showSkeleton = filteredItems.isEmpty() && activeLoading
    val dynamicGridKeys = remember(filteredItems) {
        filteredItems.map { "dynamic_${dynamicFeedItemKey(it)}" }
    }
    val useManualPrependAnchor = remember(feedLayoutMode) {
        shouldUseDynamicManualPrependAnchor(feedLayoutMode)
    }
    val skeletonPulse = if (showSkeleton) {
        com.android.purebilibili.feature.dynamic.components.rememberDynamicFeedSkeletonPulse()
    } else {
        0f
    }

    FeedVerticalStaggeredGrid(
        columns = if (feedLayoutMode == SettingsManager.DynamicFeedLayoutMode.LIST) {
            //  [新增] 列表模式：单列居中（对齐 BiliPai dynamicsWaterfallFlow 的列表布局）
            StaggeredGridCells.Fixed(1)
        } else {
            StaggeredGridCells.Adaptive(resolveDynamicTimelineMinColumnWidth())
        },
        state = listState,
        // Keyed masonry lanes retain their visible content across prepends. Re-anchoring with
        // scrollToItem after lane balancing can rebuild a tablet viewport from another lane.
        prependItemKeys = if (useManualPrependAnchor) dynamicGridKeys else emptyList(),
        prependDividerIndex = if (useManualPrependAnchor && !isSelectedUserTabActive) {
            oldContentDividerIndex
        } else {
            -1
        },
        contentPadding = PaddingValues(
            top = statusBarHeight + topPaddingExtra,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(resolveDynamicTimelineHorizontalSpacing()),
        verticalItemSpacing = resolveDynamicTimelineVerticalSpacing(),
        modifier = modifier
            .responsiveContentWidth(maxWidth = resolveDynamicTimelineMaxWidth())
            .fillMaxSize()
    ) {
        if (isSelectedUserTabActive) {
            item(
                key = "dynamic_selected_user_header",
                contentType = "dynamic_selected_user_header",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                DynamicSelectedUserFeedHeader(
                    userName = selectedUserName,
                    selectedFilter = selectedUserContentFilter,
                    onFilterSelected = onSelectedUserContentFilterChange,
                    onOpenUser = onOpenSelectedUser,
                )
            }
        }

        // 首屏骨架屏（列表为空且加载中时显示，对齐 BiliPai dynSkeleton）
        if (showSkeleton) {
            items(
                count = com.android.purebilibili.feature.dynamic.components.DYNAMIC_FEED_SKELETON_ITEM_COUNT,
                key = { index -> "dynamic_skeleton_$index" },
                contentType = { "dynamic_skeleton" }
            ) { _ ->
                com.android.purebilibili.feature.dynamic.components.DynamicFeedSkeletonCard(
                    pulse = skeletonPulse
                )
            }
        }

        // 空状态
        if (filteredItems.isEmpty() && !activeLoading && activeError == null) {
            item(
                key = "dynamic_empty_state",
                contentType = "dynamic_empty_state",
                span = StaggeredGridItemSpan.FullLine
            ) {
                DynamicEmptyState(
                    title = when {
                        selectedTab == 4 && !isSelectedUserTabActive -> "选择一个 UP 查看动态"
                        isSelectedUserTabActive &&
                            selectedUserContentFilter != DynamicUserContentFilter.ALL &&
                            hasMore -> "当前已加载内容中暂无${selectedUserContentFilter.label}"
                        isSelectedUserTabActive && selectedUserContentFilter != DynamicUserContentFilter.ALL ->
                            "该 UP 暂无${selectedUserContentFilter.label}"
                        isSelectedUserTabActive -> "该 UP 暂无动态"
                        else -> "暂无动态"
                    },
                    subtitle = when {
                        selectedTab == 4 && !isSelectedUserTabActive ->
                            "从左侧或顶部的 UP 列表中选择一个用户"
                        isSelectedUserTabActive &&
                            selectedUserContentFilter != DynamicUserContentFilter.ALL &&
                            hasMore -> "已停止自动翻页，可切换到“全部”继续查看"
                        isSelectedUserTabActive && selectedUserContentFilter != DynamicUserContentFilter.ALL ->
                            "可以切换到“全部”继续查看"
                        isSelectedUserTabActive -> "该用户暂时没有可显示的公开动态"
                        else -> "登录后即可查看关注 UP 主的最新动态"
                    },
                    modifier = Modifier.height(AppSpacingTokens.TripleExtraLarge * 6 + AppSpacingTokens.Medium)
                )
            }
        }

        // 动态卡片列表
        if (oldContentDividerIndex in 0..filteredItems.size) {
            items(
                count = oldContentDividerIndex,
                key = { index -> dynamicGridKeys[index] },
                contentType = { "dynamic_card" }
            ) { index ->
                dynamicCard(filteredItems[index])
            }
            item(
                span = StaggeredGridItemSpan.FullLine,
                key = "old_content_divider",
                contentType = "dynamic_old_content_divider"
            ) {
                OldContentDivider(label = oldContentDividerLabel)
            }
            items(
                count = filteredItems.size - oldContentDividerIndex,
                key = { offset ->
                    val index = oldContentDividerIndex + offset
                    dynamicGridKeys[index]
                },
                contentType = { "dynamic_card" }
            ) { offset ->
                dynamicCard(filteredItems[oldContentDividerIndex + offset])
            }
        } else {
            items(
                count = filteredItems.size,
                key = { index -> dynamicGridKeys[index] },
                contentType = { "dynamic_card" }
            ) { index ->
                dynamicCard(filteredItems[index])
            }
        }

        // 加载中
        if (shouldShowDynamicLoadingFooter(isLoading = activeLoading, activeItemsCount = filteredItems.size)) {
            item(
                key = "dynamic_loading_footer",
                contentType = "dynamic_loading_footer",
                span = StaggeredGridItemSpan.FullLine
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(AppSpacingTokens.Large),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingAnimation(size = AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small)
                }
            }
        }

        // 没有更多
        if (shouldShowDynamicNoMoreFooter(hasMore = hasMore, activeItemsCount = filteredItems.size)) {
            item(
                key = "dynamic_no_more_footer",
                contentType = "dynamic_no_more_footer",
                span = StaggeredGridItemSpan.FullLine
            ) {
                AppText(
                    "没有更多了",
                    modifier = Modifier.fillMaxWidth().padding(AppSpacingTokens.Large),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                )
            }
        }
    }
}

@Composable
private fun DynamicSelectedUserFeedHeader(
    userName: String,
    selectedFilter: DynamicUserContentFilter,
    onFilterSelected: (DynamicUserContentFilter) -> Unit,
    onOpenUser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = when {
                    userName == "我" -> "我的动态"
                    userName.isNotBlank() -> "$userName 的动态"
                    else -> "UP 动态"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            com.android.purebilibili.core.ui.components.AppTextButton(onClick = onOpenUser) {
                AppText("查看主页")
            }
        }
        val filters = DynamicUserContentFilter.entries
        DynamicAdaptiveSegmentedControl(
            items = filters.map(DynamicUserContentFilter::label),
            selectedIndex = filters.indexOf(selectedFilter).coerceAtLeast(0),
            onSelected = { index -> filters.getOrNull(index)?.let(onFilterSelected) },
            itemWidth = 96.dp,
            height = AppChromeSizeTokens.MinimumTouchTarget,
            indicatorHeight = 42.dp,
            labelFontSize = MaterialTheme.typography.labelLarge.fontSize,
            modifier = Modifier.width(304.dp),
        )
    }
}

@Composable
private fun DynamicEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large)
                .clip(CircleShape)
                .background(AppSurfaceTokens.surfaceContainerHigh()),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                imageVector = rememberAppDynamicIcon(),
                contentDescription = null,
                modifier = Modifier.size(AppSpacingTokens.DoubleExtraLarge),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(AppSpacingTokens.Large))
        AppText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
        AppText(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = AppSurfaceTokens.onSurfaceVariantActions(),
        )
    }
}

@Composable
private fun OldContentDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small + AppSpacingTokens.Micro),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppHorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        AppText(
            text = label,
            modifier = Modifier.padding(horizontal = AppSpacingTokens.Small + AppSpacingTokens.Micro),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
        AppHorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 *  横向 UP 主列表（Telegram 风格）
 */
@Composable
private fun HorizontalUserList(
    users: List<SidebarUser>,
    selectedUserId: Long?,
    selfUid: Long = 0L,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showHiddenUsers: Boolean,
    hiddenCount: Int,
    uplistUpdateMids: Set<Long> = emptySet(),
    onUserClick: (Long?) -> Unit,
    onToggleShowHidden: () -> Unit,
    onTogglePin: (Long) -> Unit,
    onToggleHidden: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 移除 Surface，直接使用 LazyRow 配合传入的 modifier，实现背景透明
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(
            horizontal = resolveDynamicHorizontalUserListHorizontalPadding(),
            vertical = resolveHorizontalUserListVerticalPaddingDp().dp
        ),
        horizontalArrangement = Arrangement.spacedBy(resolveDynamicHorizontalUserListSpacing()),
        modifier = modifier
    ) {
            if (hiddenCount > 0 || showHiddenUsers) {
                item(key = "hidden_toggle") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(AppSpacingTokens.ExtraSmall)
                            .combinedClickable(
                                onClick = onToggleShowHidden,
                                onLongClick = onToggleShowHidden
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(AppSpacingTokens.TripleExtraLarge)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(
                                imageVector = if (showHiddenUsers) {
                                    rememberAppVisibilityOnIcon()
                                } else {
                                    rememberAppVisibilityOffIcon()
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = if (showHiddenUsers) "隐藏中" else "显示隐藏",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // UP 主头像列表
            items(users, key = { it.uid }) { user ->
                val isSelected = isDynamicUpPanelItemSelected(selectedUserId, user.uid)
                val isShortcut = isDynamicUpPanelShortcut(user.uid, selfUid)
                var showMenu by remember { mutableStateOf(false) }
                val displayName = if (user.isHidden) {
                    "${user.name}(隐)"
                } else {
                    user.name
                }

                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onUserClick(user.uid) },
                                onLongClick = { if (!isShortcut) showMenu = true }
                            )
                            .padding(AppSpacingTokens.ExtraSmall)
                            .alpha(if (user.isHidden) 0.5f else 1f)
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(AppSpacingTokens.TripleExtraLarge)
                                    .clip(CircleShape)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(AppSpacingTokens.Micro, MaterialTheme.colorScheme.primary, CircleShape)
                                        else
                                            Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                        .data(user.face.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            //  [新增] UP 未读红点（对齐 BiliPai up_panel 8px 红点）
                            if (user.uid in uplistUpdateMids) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(AppSpacingTokens.Small)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        if (shouldShowDynamicUserLiveBadge(user.isLive)) {
                            DynamicUserLiveBadge(modifier = Modifier.padding(top = AppSpacingTokens.Micro))
                        }
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        AppText(
                            displayName,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            // 预留高度已覆盖名称基线；此处再放宽名字宽度上限，
                            // 避免较长昵称在窄视口下被过早省略号截断。
                            // LazyRow 仍会在屏幕边缘自然裁切超出视口的内容。
                            modifier = Modifier.widthIn(
                                min = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large,
                                max = 128.dp,
                            )
                        )
                    }

                    AppDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        AppDropdownMenuItem(
                            text = { AppText(if (user.isPinned) "取消置顶" else "置顶") },
                            onClick = {
                                showMenu = false
                                onTogglePin(user.uid)
                            }
                        )
                        AppDropdownMenuItem(
                            text = { AppText(if (user.isHidden) "取消隐藏" else "隐藏") },
                            onClick = {
                                showMenu = false
                                onToggleHidden(user.uid)
                            }
                        )
                    }
                }
            }
        }
    }

private fun Modifier.dynamicScrollCollapseLayout(
    expandedHeightPx: Int,
    listStateProvider: () -> LazyStaggeredGridState?,
): Modifier = clipToBounds().layout { measurable, constraints ->
    val fixedHeightPx = expandedHeightPx.coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(
        constraints.copy(minHeight = fixedHeightPx, maxHeight = fixedHeightPx)
    )
    val state = listStateProvider()
    val contentOffsetYPx = resolveDynamicScrollCollapsedHeaderOffsetYPx(
        expandedHeightPx = fixedHeightPx,
        firstVisibleItemIndex = state?.firstVisibleItemIndex ?: 0,
        firstVisibleItemScrollOffset = state?.firstVisibleItemScrollOffset ?: 0,
    )
    val visibleHeightPx = (fixedHeightPx + contentOffsetYPx).coerceAtLeast(0)
    layout(placeable.width, visibleHeightPx) {
        placeable.placeRelative(0, contentOffsetYPx)
    }
}

/**
 * 错误提示覆盖层
 */
@Composable
private fun ErrorOverlay(
    error: String?,
    activeItemsCount: Int,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (shouldShowDynamicErrorOverlay(error = error, activeItemsCount = activeItemsCount)) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(AppSpacingTokens.Large))
            if (error?.contains("未登录") == true) {
                AppPrimaryButton(text = "去登录", onClick = onLoginClick)
            } else {
                AppPrimaryButton(text = "重试", onClick = onRetry)
            }
        }
    }
}
