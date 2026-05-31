// 文件路径: feature/home/HomeScreen.kt
package com.android.purebilibili.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.ExperimentalFoundationApi //  Added
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*  // 🌊 瀑布流布局
import com.kyant.backdrop.backdrops.layerBackdrop // [Fix] Import for modifier
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.android.purebilibili.feature.home.components.MineSideDrawer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.ComfortablePullToRefreshBox
import com.android.purebilibili.core.ui.AdaptiveScaffold
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.store.SettingsManager //  引入 SettingsManager
import com.android.purebilibili.core.store.HomeTopTabSettings
import com.android.purebilibili.core.store.AppNavigationSettings
import com.android.purebilibili.core.store.resolveEffectiveHomeSettings
import com.android.purebilibili.core.store.resolveEffectiveLiquidGlassEnabled
import com.android.purebilibili.core.store.resolveHomeHeaderBlurEnabled
import com.android.purebilibili.core.plugin.skin.rememberUiSkinState
//  从 components 包导入拆分后的组件
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.FrostedSideBar
import com.android.purebilibili.feature.home.components.iOSHomeHeader  //  iOS 大标题头部
import com.android.purebilibili.feature.home.components.iOSRefreshIndicator  //  iOS 下拉刷新指示器
import com.android.purebilibili.feature.home.components.Md3ScreenshotRefreshIndicator
import com.android.purebilibili.feature.home.components.HomeInteractionMotionBudget
import com.android.purebilibili.feature.home.components.rememberHomeUiSkinDecoration
import com.android.purebilibili.feature.home.components.resolveHomeInteractionMotionBudget
import com.android.purebilibili.feature.home.components.resolveHomeDrawerScrimAlpha
import com.android.purebilibili.feature.home.components.resolveTopTabStyle
import com.android.purebilibili.feature.home.components.resolveHomeTopChromeMaterialMode
import com.android.purebilibili.feature.home.components.resolveHomeTopSearchBarHeight
import com.android.purebilibili.feature.home.components.resolveHomeTopReservedListPadding
import com.android.purebilibili.feature.home.components.resolveHomeTopTabRowHeight
import com.android.purebilibili.feature.home.policy.BottomBarVisibilityIntent
import com.android.purebilibili.feature.home.policy.HomeBottomBarScrollState
import com.android.purebilibili.feature.home.policy.reduceHomePreScroll
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderTransitionRunning
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderSettleTransition
import com.android.purebilibili.feature.home.policy.shouldHandleHomeVerticalPreScroll
import com.android.purebilibili.feature.home.policy.reduceHomeBottomBarListScroll
import com.android.purebilibili.feature.home.policy.resolveHomeBottomBarBaseVisibility
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderOffsetForSettledPage
import com.android.purebilibili.feature.home.policy.resolveHomePagerSettledAction
import com.android.purebilibili.feature.home.policy.shouldAnimateHomePagerToCategory
import com.android.purebilibili.feature.home.policy.HomePagerSettledAction
import com.android.purebilibili.feature.home.policy.shouldUseInitialHomePagerSnap
//  从 cards 子包导入卡片组件
import com.android.purebilibili.core.ui.VideoCardSkeleton
import com.android.purebilibili.core.ui.ErrorState as ModernErrorState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.adaptive.resolveEffectiveMotionTier
import com.android.purebilibili.core.ui.motion.pullRefreshReleaseSpring
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.performance.TrackJankStateFlag
import com.android.purebilibili.core.ui.performance.TrackJankStateValue
import com.android.purebilibili.core.util.resolveScrollToTopPlan
import coil.imageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged  //  性能优化：防止重复触发
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import androidx.compose.animation.ExperimentalSharedTransitionApi  //  共享过渡实验API
import com.android.purebilibili.core.ui.LocalSetBottomBarVisible
import com.android.purebilibili.core.ui.LocalBottomBarVisible

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.android.purebilibili.data.model.response.VideoItem // [Fix] Import VideoItem
import com.android.purebilibili.feature.home.components.resolveHomeHeaderCollapseDistance

// [新增] 全局回顶事件通道
val LocalHomeScrollChannel = compositionLocalOf<Channel<Unit>?> { null }

// [New] Global Scroll Offset for Liquid Glass Effect
// Used to pass scroll position from HomeScreen to BottomBar without causing recomposition
val LocalHomeScrollOffset = compositionLocalOf { androidx.compose.runtime.mutableFloatStateOf(0f) }
val LocalHomeFeedScrollInProgress = compositionLocalOf { mutableStateOf(false) }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onAvatarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    //  新增：动态页面回调
    onDynamicClick: () -> Unit = {},
    //  新增：历史记录回调
    onHistoryClick: () -> Unit = {},
    //  新增：分区回调
    onPartitionClick: () -> Unit = {},
    //  新增：直播点击回调
    onLiveClick: (Long, String, String) -> Unit = { _, _, _ -> },  // roomId, title, uname
    //  [修复] 番剧/影视回调，接受类型参数 (1=番剧 2=电影 等)
    onBangumiClick: (Int) -> Unit = {},
    //  新增：分类点击回调（用于游戏、知识、科技等分类，传入 tid 和 name）
    onCategoryClick: (Int, String) -> Unit = { _, _ -> },
    //  [新增] 底栏扩展项目导航回调
    onFavoriteClick: () -> Unit = {},  // 收藏页面
    onLiveListClick: () -> Unit = {},  // 直播列表页面
    onWatchLaterClick: () -> Unit = {},  // 稍后再看页面
    onDownloadClick: () -> Unit = {},  // 离线缓存页面
    onInboxClick: () -> Unit = {},  // 私信页面
    onStoryClick: () -> Unit = {},  //  [新增] 竖屏短视频
    onSpaceClick: (Long) -> Unit = {},
    globalHazeState: dev.chrisbanes.haze.HazeState? = null,  //  [新增] 全局底栏模糊状态
    isReturningFromVideoDetail: Boolean = false,
    isQuickReturningFromVideoDetail: Boolean = false,
    onVideoDetailReturnAnimationConsumed: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState(context = kotlin.coroutines.EmptyCoroutineContext)
    val isRefreshing by viewModel.isRefreshing.collectAsState(context = kotlin.coroutines.EmptyCoroutineContext)
// val pullRefreshState = rememberPullToRefreshState() // [Removed] Moved inside HorizontalPager
    val context = LocalContext.current
    val uiSkinState by rememberUiSkinState(context)
    val homeUiSkinDecoration = rememberHomeUiSkinDecoration(uiSkinState)
    val overlayMotionSpec = remember { resolveHomeOverlayMotionSpec() }
    //  [Refactor] Use a map of grid states for each category to support HorizontalPager
    // [Refactor] Use a map of grid states for each category to support HorizontalPager
    val gridStates = remember { mutableMapOf<HomeCategory, LazyGridState>() }
    HomeCategory.entries.forEach { category ->
        gridStates[category] = rememberSaveable(
            category.name,
            saver = LazyGridState.Saver
        ) {
            LazyGridState()
        }
    }
    val staggeredGridState = rememberLazyStaggeredGridState() // 🌊 瀑布流状态
    val localHazeState = rememberRecoverableHazeState(initialBlurEnabled = true)
    // 首页使用独立 HazeState，避免命中外层全局 source 的祖先过滤规则导致无模糊。
    val hazeState = localHazeState


    // [Feature] Video Preview State (Global Scope)
    val targetVideoItemState = remember { mutableStateOf<VideoItem?>(null) }
    val homeBackdrop = rememberLayerBackdrop()

    val coroutineScope = rememberCoroutineScope() // 用于双击回顶动画
    val globalScrollOffset = LocalHomeScrollOffset.current
    val globalFeedScrollInProgress = LocalHomeFeedScrollInProgress.current
    // [Header] 首页重选/双击回顶时需要强制恢复顶部，避免自动收缩后残留空白区域
    var headerOffsetHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var headerSettleAnimationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var delayTopTabsUntilCardSettled by remember { mutableStateOf(false) }
    var hideTopTabsForForwardDetailNav by remember { mutableStateOf(false) }
    var returnAnimationStartElapsedMs by remember { mutableLongStateOf(0L) }
    var topTabsRevealJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun setHeaderOffsetImmediate(value: Float) {
        headerSettleAnimationJob?.cancel()
        headerSettleAnimationJob = null
        headerOffsetHeightPx = value
    }

    fun animateHeaderOffsetTo(targetValue: Float) {
        val transition = resolveHomeHeaderSettleTransition(
            currentHeaderOffsetPx = headerOffsetHeightPx,
            targetHeaderOffsetPx = targetValue
        )
        if (!transition.shouldAnimate) {
            setHeaderOffsetImmediate(transition.targetOffsetPx)
            return
        }
        headerSettleAnimationJob?.cancel()
        headerSettleAnimationJob = coroutineScope.launch {
            animate(
                initialValue = headerOffsetHeightPx,
                targetValue = transition.targetOffsetPx,
                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)
            ) { value, _ ->
                headerOffsetHeightPx = value
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (headerSettleAnimationJob === job) {
                    headerSettleAnimationJob = null
                }
            }
        }
    }

    // [新增] 监听全局回顶事件
    val scrollChannel = LocalHomeScrollChannel.current
    LaunchedEffect(scrollChannel) {
        scrollChannel?.receiveAsFlow()?.collect {
            launch {
                // 双击首页回顶时强制展开顶部，避免收缩头部与回顶状态错位导致空白
                setHeaderOffsetImmediate(0f)
                val gridState = gridStates[state.currentCategory]
                val isAtTop = gridState == null || (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50)

                if (isAtTop) {
                    viewModel.refresh()
                } else {
                    val listState = requireNotNull(gridState)
                    val currentIndex = listState.firstVisibleItemIndex
                    val plan = resolveScrollToTopPlan(currentIndex)
                    plan.preJumpIndex?.let { preJump ->
                        if (currentIndex > preJump) {
                            listState.scrollToItem(preJump)
                        }
                    }
                    listState.animateScrollToItem(plan.animateTargetIndex)
                }
                setHeaderOffsetImmediate(0f)
                globalScrollOffset.floatValue = 0f
            }
        }
    }

    // [P2] 顶栏自定义：顺序与可见项从设置读取
    val defaultTopTabIds = remember { resolveDefaultHomeTopTabIds() }
    val topTabSettings by SettingsManager.getHomeTopTabSettings(context).collectAsState(
        initial = HomeTopTabSettings(
            orderIds = defaultTopTabIds,
            visibleIds = defaultTopTabIds.toSet()
        ),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    // [Refactor] Hoist PagerState to be available for both Content and Header
    // 确保 pagerState 在所有作用域均可见，以便传给 iOSHomeHeader
    val topCategories = remember(topTabSettings) {
        resolveHomeTopCategories(
            customOrderIds = topTabSettings.orderIds,
            visibleIds = topTabSettings.visibleIds
        )
    }
    val localizedTopCategoryLabels = topCategories.map { category ->
        stringResource(resolveHomeCategoryLabelRes(category))
    }
    val initialPage = resolveHomeTopTabIndex(state.currentCategory, topCategories)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage) { topCategories.size }
    var hasSyncedPagerWithState by remember(topCategories) { mutableStateOf(false) }
    var programmaticPageSwitchInProgress by remember { mutableStateOf(false) }
    TrackJankStateFlag(
        stateName = "home:pager_swipe",
        isActive = pagerState.isScrollInProgress
    )
    TrackJankStateValue(
        stateName = "home:current_category",
        stateValue = state.currentCategory.name
    )

    // [修复] 仅在完成首次“状态->Pager”对齐后，才允许“Pager->状态”反向同步，避免返回首页时误跳分类。
    LaunchedEffect(pagerState, topCategories, hasSyncedPagerWithState, state.currentCategory) {
        if (!hasSyncedPagerWithState) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, scrolling) ->
                val currentCategoryIndex = resolveHomeTopTabIndex(state.currentCategory, topCategories)
                when (resolveHomePagerSettledAction(
                        hasSyncedPagerWithState = hasSyncedPagerWithState,
                        pagerCurrentPage = page,
                        pagerScrolling = scrolling,
                        currentCategoryIndex = currentCategoryIndex,
                        settledCategory = resolveHomeCategoryForTopTab(
                            index = page,
                            topCategories = topCategories
                        ),
                        programmaticPageSwitchInProgress = programmaticPageSwitchInProgress
                    )
                ) {
                    HomePagerSettledAction.NONE -> return@collect
                    HomePagerSettledAction.SWITCH_CATEGORY -> {
                        viewModel.switchCategory(
                            resolveHomeCategoryForTopTab(
                                index = page,
                                topCategories = topCategories
                            )
                        )
                    }
                }
            }
    }

    // [P2] 当前分类被隐藏时，自动落到首个可见分类
    LaunchedEffect(topCategories) {
        val firstVisible = topCategories.firstOrNull() ?: return@LaunchedEffect
        if (state.currentCategory !in topCategories) {
            viewModel.updateDisplayedTabIndex(0)
            viewModel.switchCategory(firstVisible)
        }
    }

    // [CrashFix] 顶栏配置变化导致页数收缩时，先钳制 pager 当前页，避免越界
    LaunchedEffect(topCategories.size) {
        if (topCategories.isEmpty()) return@LaunchedEffect
        val lastIndex = topCategories.lastIndex
        if (pagerState.currentPage > lastIndex) {
            pagerState.scrollToPage(lastIndex)
        }
    }

    // [修复] 状态变化时驱动 Pager：首次使用无动画对齐，后续用动画跟随
    LaunchedEffect(state.currentCategory, topCategories) {
        val targetPage = topCategories.indexOf(state.currentCategory)
        if (targetPage < 0) return@LaunchedEffect
        if (shouldUseInitialHomePagerSnap(
                hasSyncedPagerWithState = hasSyncedPagerWithState,
                targetPage = targetPage
            )
        ) {
            pagerState.scrollToPage(targetPage)
            hasSyncedPagerWithState = true
            return@LaunchedEffect
        }
        if (shouldAnimateHomePagerToCategory(
                hasSyncedPagerWithState = hasSyncedPagerWithState,
                targetPage = targetPage,
                pagerCurrentPage = pagerState.currentPage,
                pagerScrolling = pagerState.isScrollInProgress,
                programmaticPageSwitchInProgress = programmaticPageSwitchInProgress
            )
        ) {
            programmaticPageSwitchInProgress = true
            try {
                pagerState.animateScrollToPage(targetPage)
            } finally {
                programmaticPageSwitchInProgress = false
            }
        }
    }

    // [修复] 刷新时仅在列表不在顶部时回顶，避免与下拉手势状态冲突导致“卡一下”
    LaunchedEffect(isRefreshing, state.currentCategory) {
        if (!isRefreshing) return@LaunchedEffect
        val gridState = gridStates[state.currentCategory] ?: return@LaunchedEffect
        if (!shouldResetToTopOnRefreshStart(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset
            )
        ) {
            return@LaunchedEffect
        }
        gridState.animateScrollToItem(0)
    }

    //  [新增] JSON 插件过滤提示
    val snackbarHostState = remember { SnackbarHostState() }
    val lastFilteredCount by com.android.purebilibili.core.plugin.json.JsonPluginManager.lastFilteredCount.collectAsState(context = kotlin.coroutines.EmptyCoroutineContext)
    
    //  当有视频被过滤时显示提示
    LaunchedEffect(lastFilteredCount) {
        if (lastFilteredCount > 0) {
            snackbarHostState.showSnackbar(
                message = " 已过滤 $lastFilteredCount 个视频",
                duration = SnackbarDuration.Short
            )
        }
    }
    
    //  [埋点] 页面浏览追踪
    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("HomeScreen")
    }
    
    //  [埋点] 分类切换追踪
    LaunchedEffect(state.currentCategory) {
        com.android.purebilibili.core.util.AnalyticsHelper.logCategoryView(
            categoryName = state.currentCategory.label,
            categoryId = state.currentCategory.tid
        )
    }

    // [New] Broadcast Scroll Offset for Liquid Glass Effect & Parallax
    // Create the state here and provide it

    //  [性能优化] 合并首页设置为单一 Flow，减少 6 个 collectAsState → 1 个
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsState(
        initial = com.android.purebilibili.core.store.HomeSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val pullRefreshMotionStyle = remember(uiPreset, androidNativeVariant) {
        resolveHomePullRefreshMotionStyle(
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }
    val pullRefreshIndicatorStyle = remember(uiPreset, androidNativeVariant) {
        resolveHomePullRefreshIndicatorStyle(
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }

    
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var refreshDeltaTipText by remember { mutableStateOf<String?>(null) }
    
    //  [彩蛋] 下拉刷新成功后显示趣味提示（仅在开关开启时）
    LaunchedEffect(state.refreshKey, homeSettings.easterEggEnabled) {
        val message = state.refreshMessage
        if (message != null && state.refreshKey > 0 && homeSettings.easterEggEnabled) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "关闭彩蛋",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                showEasterEggDialog = true
            }
        }
    }

    LaunchedEffect(state.refreshNewItemsKey, isRefreshing, state.currentCategory) {
        val refreshKey = state.refreshNewItemsKey
        if (!shouldHandleRefreshNewItemsEvent(refreshKey, state.refreshNewItemsHandledKey)) {
            return@LaunchedEffect
        }
        val count = state.refreshNewItemsCount ?: return@LaunchedEffect
        if (state.currentCategory == HomeCategory.RECOMMEND && count > 0) {
            val recommendGridState = gridStates[HomeCategory.RECOMMEND]
            if (recommendGridState != null && shouldResetToTopAfterIncrementalRefresh(
                    currentCategory = state.currentCategory,
                    newItemsCount = count,
                    isRefreshing = isRefreshing,
                    firstVisibleItemIndex = recommendGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = recommendGridState.firstVisibleItemScrollOffset
                )
            ) {
                // 等 PullToRefresh 释放手势后再回顶，避免在手势临界点抢占滚动。
                yield()
                recommendGridState.scrollToItem(0)
            }
        }
        refreshDeltaTipText = if (count > 0) "新增 $count 条内容" else "暂无新内容"
        delay(2200)
        refreshDeltaTipText = null
        viewModel.markRefreshNewItemsHandled(refreshKey)
    }

    // 仅在推荐页检测“刷新后是否已下滑”，用于激活旧内容分割线
    LaunchedEffect(
        state.currentCategory,
        state.refreshNewItemsKey,
        state.recommendOldContentAnchorBvid,
        state.categoryStates[HomeCategory.RECOMMEND]?.videos
    ) {
        if (state.currentCategory != HomeCategory.RECOMMEND) return@LaunchedEffect
        if ((state.refreshNewItemsCount ?: 0) <= 0) return@LaunchedEffect
        val targetKey = state.refreshNewItemsKey
        if (targetKey <= 0L || state.recommendOldContentRevealKey == targetKey) return@LaunchedEffect

        val anchorBvid = state.recommendOldContentAnchorBvid ?: return@LaunchedEffect
        val recommendVideos = state.categoryStates[HomeCategory.RECOMMEND]?.videos ?: return@LaunchedEffect
        val anchorIndex = recommendVideos.indexOfFirst { it.bvid == anchorBvid }
        if (anchorIndex <= 0) return@LaunchedEffect

        val recommendState = gridStates[HomeCategory.RECOMMEND] ?: return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = recommendState.layoutInfo
            val reachedByVisible = layoutInfo.visibleItemsInfo.any { it.index == anchorIndex }
            val reachedByIndex = recommendState.firstVisibleItemIndex >= anchorIndex
            reachedByVisible || reachedByIndex
        }.first { it }
        viewModel.markRecommendOldContentDividerRevealed(targetKey)
    }
    
    //  [彩蛋] 关闭确认对话框
    if (showEasterEggDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEasterEggDialog = false },
            title = { 
                Text(
                    "关闭趣味提示？", 
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = { 
                Text(
                    "关闭后下拉刷新将不再显示趣味消息。\n\n你可以在「设置」中随时重新开启。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        coroutineScope.launch {
                            SettingsManager.setEasterEggEnabled(context, false)
                        }
                        showEasterEggDialog = false
                    }
                ) { Text("关闭彩蛋", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showEasterEggDialog = false }
                ) { Text("保留彩蛋", color = MaterialTheme.colorScheme.primary) }
            },
            containerColor = AppSurfaceTokens.cardContainer()
        )
    }
    
    //  [修复] 确保首页显示时 WindowInsets 配置正确，防止从视频页返回时布局跳动
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        // 保持边到边显示（与 VideoDetailScreen 一致）
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    // 解构设置值（避免每次访问都触发重组）
    val effectiveHomeSettings = remember(homeSettings, uiPreset) {
        resolveEffectiveHomeSettings(
            homeSettings = homeSettings,
            uiPreset = uiPreset
        )
    }
    val displayMode = homeSettings.displayMode
    val isBottomBarFloating = homeSettings.isBottomBarFloating
    val bottomBarLabelMode = homeSettings.bottomBarLabelMode
    val baseIsHeaderBlurEnabled = remember(homeSettings.headerBlurMode, uiPreset) {
        resolveHomeHeaderBlurEnabled(
            mode = homeSettings.headerBlurMode,
            uiPreset = uiPreset
        )
    }
    val baseIsBottomBarBlurEnabled = homeSettings.isBottomBarBlurEnabled
    val crashTrackingConsentShown = homeSettings.crashTrackingConsentShown
    val baseCardAnimationEnabled = homeSettings.cardAnimationEnabled      //  卡片进场动画开关
    val baseCardTransitionEnabled = homeSettings.cardTransitionEnabled
    val baseBottomBarLiquidGlassEnabled = remember(
        homeSettings.isBottomBarLiquidGlassEnabled,
        homeSettings.androidNativeLiquidGlassEnabled,
        uiPreset
    ) {
        resolveEffectiveLiquidGlassEnabled(
            requestedEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
            uiPreset = uiPreset,
            androidNativeLiquidGlassEnabled = homeSettings.androidNativeLiquidGlassEnabled
        )
    }
    val baseIsDataSaverActive = remember(context) {
        com.android.purebilibili.core.store.SettingsManager.isDataSaverActive(context)
    }
    val homePerformanceConfig = remember(
        baseIsHeaderBlurEnabled,
        baseIsBottomBarBlurEnabled,
        baseBottomBarLiquidGlassEnabled,
        baseCardAnimationEnabled,
        baseCardTransitionEnabled,
        baseIsDataSaverActive,
        homeSettings.androidNativeLiquidGlassEnabled
    ) {
        resolveHomePerformanceConfig(
            uiPreset = uiPreset,
            headerBlurEnabled = baseIsHeaderBlurEnabled,
            bottomBarBlurEnabled = baseIsBottomBarBlurEnabled,
            topBarLiquidGlassEnabled = false,
            bottomBarLiquidGlassEnabled = baseBottomBarLiquidGlassEnabled,
            androidNativeLiquidGlassEnabled = homeSettings.androidNativeLiquidGlassEnabled,
            cardAnimationEnabled = baseCardAnimationEnabled,
            cardTransitionEnabled = baseCardTransitionEnabled,
            isDataSaverActive = baseIsDataSaverActive,
            smartVisualGuardEnabled = false
        )
    }
    val isHeaderBlurEnabled = homePerformanceConfig.headerBlurEnabled
    val isBottomBarBlurEnabled = homePerformanceConfig.bottomBarBlurEnabled
    // [统一门控] 系统「减弱动效」是所有界面动效的通用开关:开启时关闭卡片进场/消散等所有卡片动效,
    // 各功能面自身的开关(此处为卡片动画开关)仍各自独立。与设置页入场动画共用同一 reduce-motion 判定。
    val systemReduceMotion = rememberSystemReduceMotion()
    val cardAnimationEnabled = homePerformanceConfig.cardAnimationEnabled && !systemReduceMotion
    val cardTransitionEnabled = homePerformanceConfig.cardTransitionEnabled
    val isBottomBarLiquidGlassEnabled = homePerformanceConfig.bottomBarLiquidGlassEnabled
    val isLiquidGlassEnabled = homePerformanceConfig.isAnyLiquidGlassEnabled
    val isDataSaverActive = homePerformanceConfig.isDataSaverActive
    val preloadAheadCount = homePerformanceConfig.preloadAheadCount
    val configuredHomeWallpaperUri by SettingsManager.getHomeWallpaperUri(context).collectAsState(
        initial = "",
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val splashWallpaperUri by SettingsManager.getSplashWallpaperUri(context).collectAsState(
        initial = "",
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    val homeWallpaperUri = remember(configuredHomeWallpaperUri, splashWallpaperUri) {
        resolveHomeWallpaperUri(
            homeWallpaperUri = configuredHomeWallpaperUri,
            splashWallpaperUri = splashWallpaperUri
        )
    }

    val appNavigationSettings by SettingsManager.getAppNavigationSettings(context).collectAsState(
        initial = AppNavigationSettings(),
        context = kotlin.coroutines.EmptyCoroutineContext
    )
    // 将字符串 ID 转换为 BottomNavItem 枚举
    val visibleBottomBarItems = remember(appNavigationSettings.orderedVisibleTabIds) {
        appNavigationSettings.orderedVisibleTabIds.mapNotNull { id ->
            try { BottomNavItem.valueOf(id) } catch (e: Exception) { null }
        }
    }
    val bottomBarItemColors = appNavigationSettings.bottomBarItemColors

    
    //  📐 [平板适配] 根据屏幕尺寸和展示模式动态设置网格列数
    // 故事卡片(1)和沉浸模式(2)需要单列全宽，网格(0)使用双列
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val cardMotionTier = resolveEffectiveMotionTier(
        baseTier = deviceUiProfile.motionTier,
        animationEnabled = cardAnimationEnabled
    )
    val returnAnimationSuppressionDurationMs = resolveReturnAnimationSuppressionDurationMs(
        isTabletLayout = windowSizeClass.isTablet,
        cardAnimationEnabled = cardAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled,
        isQuickReturnFromDetail = isQuickReturningFromVideoDetail
    )
    // Navigation 返回不一定触发首页 Lifecycle.ON_START，顶栏恢复必须直接跟随返回态。
    LaunchedEffect(isReturningFromVideoDetail, cardTransitionEnabled, isQuickReturningFromVideoDetail) {
        if (!isReturningFromVideoDetail) return@LaunchedEffect
        topTabsRevealJob?.cancel()
        returnAnimationStartElapsedMs = SystemClock.elapsedRealtime()
        hideTopTabsForForwardDetailNav = false
        val revealDelayMs = resolveHomeTopTabsRevealDelayMs(
            isReturningFromDetail = true,
            cardTransitionEnabled = cardTransitionEnabled,
            isQuickReturnFromDetail = isQuickReturningFromVideoDetail
        )
        if (revealDelayMs > 0L) {
            delayTopTabsUntilCardSettled = true
            topTabsRevealJob = coroutineScope.launch {
                delay(revealDelayMs)
                delayTopTabsUntilCardSettled = false
            }
        } else {
            delayTopTabsUntilCardSettled = false
        }
    }

    // 从详情页返回时延后清理“返回中”状态，避免卡片进场动画在共享转场期间抢跑造成闪屏。
    LaunchedEffect(returnAnimationSuppressionDurationMs, isReturningFromVideoDetail) {
        if (isReturningFromVideoDetail) {
            val startElapsedMs = if (returnAnimationStartElapsedMs > 0L) {
                returnAnimationStartElapsedMs
            } else {
                SystemClock.elapsedRealtime()
            }
            delay(returnAnimationSuppressionDurationMs)
            val actualDurationMs = (SystemClock.elapsedRealtime() - startElapsedMs).coerceAtLeast(0L)
            val isQuickReturn = isQuickReturningFromVideoDetail
            val sharedTransitionReady = cardTransitionEnabled &&
                CardPositionManager.lastClickedCardBounds != null &&
                CardPositionManager.isCardFullyVisible

            // 先清除"返回中"状态，让后续 LaunchedEffect 恢复底栏
            returnAnimationStartElapsedMs = 0L
            onVideoDetailReturnAnimationConsumed()

            val builtinPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledCount()
            val playerPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledPlayerPlugins().size
            val feedPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledFeedPlugins().size
            val danmakuPluginEnabledCount = com.android.purebilibili.core.plugin.PluginManager.getEnabledDanmakuPlugins().size
            val jsonEnabledPlugins = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled }
            val jsonFeedPluginEnabledCount = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled && it.plugin.type == "feed" }
            val jsonDanmakuPluginEnabledCount = com.android.purebilibili.core.plugin.json.JsonPluginManager.plugins.value
                .count { it.enabled && it.plugin.type == "danmaku" }
            com.android.purebilibili.core.util.AnalyticsHelper.logHomeReturnAnimationPerformance(
                actualDurationMs = actualDurationMs,
                plannedSuppressionMs = returnAnimationSuppressionDurationMs,
                sharedTransitionEnabled = cardTransitionEnabled,
                sharedTransitionReady = sharedTransitionReady,
                isQuickReturn = isQuickReturn,
                isTabletLayout = windowSizeClass.isTablet,
                cardAnimationEnabled = cardAnimationEnabled,
                builtinPluginEnabledCount = builtinPluginEnabledCount,
                playerPluginEnabledCount = playerPluginEnabledCount,
                feedPluginEnabledCount = feedPluginEnabledCount,
                danmakuPluginEnabledCount = danmakuPluginEnabledCount,
                jsonPluginEnabledCount = jsonEnabledPlugins,
                jsonFeedPluginEnabledCount = jsonFeedPluginEnabledCount,
                jsonDanmakuPluginEnabledCount = jsonDanmakuPluginEnabledCount
            )
        }
        if (CardPositionManager.isSwitchingCategory) {
            delay(300)
            CardPositionManager.isSwitchingCategory = false
        }
    }

    val contentWidth = if (windowSizeClass.isExpandedScreen) {
        minOf(windowSizeClass.widthDp, 1280.dp)
    } else {
        windowSizeClass.widthDp
    }
    
    // 是否为单列模式 (Story or Cinematic)
    val isSingleColumnMode = displayMode == 1
    
    val gridColumns = remember(
        contentWidth,
        displayMode,
        homeSettings.gridColumnCount,
        homeSettings.homeFeedCardWidthPreset
    ) {
        resolveHomeFeedGridColumns(
            contentWidthDp = contentWidth.value.toInt(),
            displayMode = displayMode,
            fixedColumnCount = homeSettings.gridColumnCount,
            cardWidthPreset = homeSettings.homeFeedCardWidthPreset
        )
    }
    
    
    val tabletUseSidebar = appNavigationSettings.tabletUseSidebar
    
    //  📐 [大屏适配] 平板导航模式：根据用户偏好决定
    // 仅在平板且用户选择了侧边栏时使用侧边导航
    val useSideNavigation = com.android.purebilibili.core.util.shouldUseSidebarNavigationForLayout(
        windowSizeClass = windowSizeClass,
        tabletUseSidebar = tabletUseSidebar
    )
    val isHomeDrawerEnabled = com.android.purebilibili.core.util.shouldEnableHomeDrawer(
        useSideNavigation = useSideNavigation
    )
    
    //  📱 [切换导航模式] 处理函数
    val onToggleNavigationMode: () -> Unit = {
        coroutineScope.launch {
            SettingsManager.setTabletUseSidebar(context, !tabletUseSidebar)
        }
    }

    //  [修复] 恢复状态栏样式：确保从视频详情页返回后状态栏正确
    // 当使用滑动动画时，Theme.kt 的 SideEffect 可能不会重新执行
    val backgroundColor = AppSurfaceTokens.chromeBackground()
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }
    val homeWallpaperBackdropAppearance = remember(
        homeWallpaperUri,
        homeSettings.homeWallpaperEffectMode,
        isLightBackground,
        isDataSaverActive
    ) {
        resolveHomeWallpaperBackdropAppearance(
            hasWallpaper = homeWallpaperUri.isNotBlank(),
            effectMode = homeSettings.homeWallpaperEffectMode,
            isDarkTheme = !isLightBackground,
            isDataSaverActive = isDataSaverActive
        )
    }
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? android.app.Activity)?.window ?: return@SideEffect
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            //  根据背景亮度设置状态栏图标颜色
            insetsController.isAppearanceLightStatusBars = isLightBackground
            //  [修复] 导航栏也需要根据背景亮度设置图标颜色
            insetsController.isAppearanceLightNavigationBars = isLightBackground
            //  确保状态栏可见且透明
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            //  [修复] 导航栏也设为透明，确保底栏隐藏时手势区域沉浸
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    val density = LocalDensity.current
    val navBarHeight = WindowInsets.navigationBars.getBottom(density).let { with(density) { it.toDp() } }

    //  [修复] 动态计算内容顶部边距，防止被头部遮挡
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val homeStartupElapsedAt = remember { SystemClock.elapsedRealtime() }
    var todayWatchStartupRevealHandled by rememberSaveable { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    //  当前选中的导航项
    var currentNavItem by remember { mutableStateOf(BottomNavItem.HOME) }

    // 统一导航点击逻辑（底栏/侧栏复用）
    val handleNavItemClick: (BottomNavItem) -> Unit = { item ->
        currentNavItem = item
        when (item) {
            BottomNavItem.HOME -> {
                coroutineScope.launch { 
                    setHeaderOffsetImmediate(0f)
                    val gridState = gridStates[state.currentCategory]
                    val isAtTop = gridState == null || (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 50)
                    
                    if (isAtTop) {
                        viewModel.refresh()
                    } else {
                        val listState = requireNotNull(gridState)
                        // [性能优化] 逻辑同上，如果太远先瞬移回来
                        if (listState.firstVisibleItemIndex > 12) {
                            listState.scrollToItem(12)
                        }
                        listState.animateScrollToItem(0)
                    } 
                    setHeaderOffsetImmediate(0f)
                    globalScrollOffset.floatValue = 0f
                }
            }
            BottomNavItem.DYNAMIC -> onDynamicClick()
            BottomNavItem.HISTORY -> onHistoryClick()
            BottomNavItem.PROFILE -> onProfileClick()
            BottomNavItem.FAVORITE -> onFavoriteClick()
            BottomNavItem.LIVE -> onLiveListClick()
            BottomNavItem.WATCHLATER -> onWatchLaterClick()
            BottomNavItem.STORY -> onStoryClick()
            BottomNavItem.SETTINGS -> onSettingsClick()
        }
    }
    
    val bottomBarVisibilityMode = appNavigationSettings.bottomBarVisibilityMode
    
    //  [Refactor] 使用全局 CompositionLocal 控制底栏可见性
    val setBottomBarVisible = LocalSetBottomBarVisible.current
    val isGlobalBottomBarVisible = LocalBottomBarVisible.current
    // 兼容代码：为了最小化改动，将 bottomBarVisible 指向全局状态
    // 注意：这里的 bottomBarVisible 现在是只读的，修改必须通过 setBottomBarVisible
    val bottomBarVisible = isGlobalBottomBarVisible
    val bottomBarBodyHeight = when (bottomBarLabelMode) {
        0 -> if (windowSizeClass.isTablet) 76.dp else 70.dp
        2 -> if (windowSizeClass.isTablet) 56.dp else 54.dp
        else -> if (windowSizeClass.isTablet) 68.dp else 62.dp
    }
    val dockedBarBodyHeight = when (bottomBarLabelMode) {
        0 -> 72.dp
        2 -> if (windowSizeClass.isTablet) 52.dp else 56.dp
        else -> 64.dp
    }
    val bottomBarVerticalInset = if (isBottomBarFloating) {
        if (windowSizeClass.isTablet) 20.dp else 16.dp
    } else {
        0.dp
    }
    val homeListBottomPadding = when {
        useSideNavigation -> navBarHeight + 8.dp
        !bottomBarVisible -> navBarHeight + 8.dp
        isBottomBarFloating -> bottomBarBodyHeight + bottomBarVerticalInset + navBarHeight + 12.dp
        else -> dockedBarBodyHeight + navBarHeight + 12.dp
    }
    
    //  [修复] 跟踪是否正在导航到/从视频页 - 必须在 LaunchedEffect 之前声明
    var isVideoNavigating by remember { mutableStateOf(false) }
    var isHomeContentInteractionRestored by remember { mutableStateOf(true) }

    LaunchedEffect(isReturningFromVideoDetail, cardTransitionEnabled, isQuickReturningFromVideoDetail) {
        if (!isReturningFromVideoDetail) return@LaunchedEffect
        val restoreDelayMs = resolveHomeContentInteractionRestoreDelayMs(
            cardTransitionEnabled = cardTransitionEnabled,
            isQuickReturnFromDetail = isQuickReturningFromVideoDetail
        )
        if (restoreDelayMs > 0L) {
            delay(restoreDelayMs)
        }
        isHomeContentInteractionRestored = true
        isVideoNavigating = false
    }
    
    //  [新增] 滚动方向检测状态（用于上滑隐藏模式）
    var bottomBarScrollState by remember(state.currentCategory) {
        mutableStateOf(
            HomeBottomBarScrollState(
                firstVisibleItem = gridStates[state.currentCategory]?.firstVisibleItemIndex ?: 0,
                scrollOffset = gridStates[state.currentCategory]?.firstVisibleItemScrollOffset ?: 0
            )
        )
    }
    
    //  [新增] 滚动方向检测逻辑
    LaunchedEffect(state.currentCategory, bottomBarVisibilityMode, useSideNavigation) {
        resolveHomeBottomBarBaseVisibility(
            useSideNavigation = useSideNavigation,
            mode = bottomBarVisibilityMode
        )?.let { isVisible ->
            setBottomBarVisible(isVisible)
            return@LaunchedEffect
        }
        
        // 上滑隐藏模式：监听滚动方向
        val currentGridState = gridStates[state.currentCategory] ?: return@LaunchedEffect
        snapshotFlow {
            Pair(currentGridState.firstVisibleItemIndex, currentGridState.firstVisibleItemScrollOffset)
        }
        .distinctUntilChanged()
        .collect { (firstVisibleItem, scrollOffset) ->
            val scrollUpdate = reduceHomeBottomBarListScroll(
                previousState = bottomBarScrollState,
                firstVisibleItem = firstVisibleItem,
                scrollOffset = scrollOffset,
                isVideoNavigating = isVideoNavigating,
                contentInteractionRestored = isHomeContentInteractionRestored
            )

            bottomBarScrollState = scrollUpdate.state
            when (scrollUpdate.visibilityIntent) {
                BottomBarVisibilityIntent.SHOW -> setBottomBarVisible(true)
                BottomBarVisibilityIntent.HIDE -> setBottomBarVisible(false)
                null -> Unit
            }
        }
    }

    // [New] State for side drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var bottomBarVisibleBeforeDrawer by remember { mutableStateOf<Boolean?>(null) }
    
    // 抽屉打开时隐藏全局底栏，避免覆盖侧边栏底部内容
    val isDrawerOpenOrOpening = drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
    LaunchedEffect(isDrawerOpenOrOpening, isGlobalBottomBarVisible, useSideNavigation) {
        if (useSideNavigation) return@LaunchedEffect
        
        if (isDrawerOpenOrOpening) {
            if (bottomBarVisibleBeforeDrawer == null) {
                bottomBarVisibleBeforeDrawer = isGlobalBottomBarVisible
            }
            if (isGlobalBottomBarVisible) {
                setBottomBarVisible(false)
            }
        } else {
            bottomBarVisibleBeforeDrawer?.let { previousVisible ->
                setBottomBarVisible(previousVisible)
            }
            bottomBarVisibleBeforeDrawer = null
        }
    }
    
    // [P2] 优先按当前可见顶栏计算索引，避免自定义排序后高亮错位
    val currentCategoryIndex = topCategories.indexOf(state.currentCategory)
    val displayedTabIndex = if (currentCategoryIndex >= 0) {
        currentCategoryIndex
    } else {
        state.displayedTabIndex.coerceIn(0, (topCategories.size - 1).coerceAtLeast(0))
    }

    //  根据滚动距离动态调整 BottomBar 可见性
    //  逻辑优化：使用 nestedScrollConnection 监听滚动
    var areTopTabsManuallyCollapsed by rememberSaveable { mutableStateOf(false) }
    
    // Constants
    val topTabStyle = remember(isBottomBarFloating, isHeaderBlurEnabled) {
        resolveTopTabStyle(
            isBottomBarFloating = isBottomBarFloating,
            isBottomBarBlurEnabled = isHeaderBlurEnabled,
            isLiquidGlassEnabled = false
        )
    }
    val topChromeMaterialMode = remember(isHeaderBlurEnabled) {
        resolveHomeTopChromeMaterialMode(
            isHeaderBlurEnabled = isHeaderBlurEnabled,
            isBottomBarBlurEnabled = false,
            isLiquidGlassEnabled = false
        )
    }
    val searchBarHeightDp = resolveHomeTopSearchBarHeight(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    val tabRowHeightDp = resolveHomeTopTabRowHeight(
        isTabFloating = topTabStyle.floating,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        labelMode = homeSettings.topTabLabelMode
    )
    val headerCollapseDistanceDp = resolveHomeHeaderCollapseDistance(
        searchBarHeight = searchBarHeightDp,
        tabRowHeight = tabRowHeightDp,
        uiPreset = uiPreset
        androidNativeVariant = androidNativeVariant
    )
    val listTopPadding = resolveHomeTopReservedListPadding(
        statusBarHeight = statusBarHeight,
        searchBarHeight = searchBarHeightDp,
        tabRowHeight = tabRowHeightDp,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    
    // Pixels
    val headerCollapseDistancePx = with(density) { headerCollapseDistanceDp.toPx() }

    LaunchedEffect(pagerState, topCategories, headerCollapseDistancePx) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, scrolling) ->
                if (scrolling) return@collect
                val settledCategory = resolveHomeTopCategoryOrNull(topCategories, page) ?: return@collect
                val settledGridState = gridStates[settledCategory] ?: return@collect
                val settledHeaderOffsetPx = resolveHomeHeaderOffsetForSettledPage(
                    firstVisibleItemIndex = settledGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = settledGridState.firstVisibleItemScrollOffset,
                    maxHeaderCollapsePx = headerCollapseDistancePx
                )
                if (kotlin.math.abs(headerOffsetHeightPx - settledHeaderOffsetPx) > 0.5f) {
                    animateHeaderOffsetTo(settledHeaderOffsetPx)
                }
            }
    }
    
    // [Feature] Sticky Header Options
    // If true, header will shrink but stay visible. If false, it scrolls away.
    val isHeaderCollapseEnabled = homeSettings.isHeaderCollapseEnabled // Enable shrinking based on settings
    val areTopTabsAutoCollapsed by remember(headerOffsetHeightPx, isHeaderCollapseEnabled) {
        derivedStateOf {
            resolveHomeTopTabsAutoCollapsed(
                currentHeaderOffsetPx = headerOffsetHeightPx,
                isHeaderCollapseEnabled = isHeaderCollapseEnabled
            )
        }
    }
    
    // [Feature] Bottom Bar Auto-Hide (based on scroll hide mode)
    val isBottomBarAutoHideEnabled = bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE
    val bottomBarVisibleState = LocalSetBottomBarVisible.current
    
    // [Feature] Global Scroll Offset for Liquid Glass
    val activeGridState = gridStates[state.currentCategory]
    val canRevealHeader by remember(activeGridState) {
        derivedStateOf {
            activeGridState != null &&
                activeGridState.firstVisibleItemIndex == 0 &&
                activeGridState.firstVisibleItemScrollOffset.toFloat() <= headerCollapseDistancePx
        }
    }

    val nestedScrollConnection = remember(
        isHeaderCollapseEnabled,
        isBottomBarAutoHideEnabled,
        useSideNavigation,
        isLiquidGlassEnabled,
        canRevealHeader
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!shouldHandleHomeVerticalPreScroll(deltaX = available.x, deltaY = available.y)) {
                    return Offset.Zero
                }
                headerSettleAnimationJob?.cancel()
                headerSettleAnimationJob = null
                val scrollUpdate = reduceHomePreScroll(
                    currentHeaderOffsetPx = headerOffsetHeightPx,
                    deltaY = available.y,
                    minHeaderOffsetPx = -headerCollapseDistancePx,
                    canRevealHeader = canRevealHeader,
                    isHeaderCollapseEnabled = isHeaderCollapseEnabled,
                    isBottomBarAutoHideEnabled = isBottomBarAutoHideEnabled,
                    useSideNavigation = useSideNavigation,
                    liquidGlassEnabled = isLiquidGlassEnabled,
                    currentGlobalScrollOffset = globalScrollOffset.floatValue
                )

                headerOffsetHeightPx = scrollUpdate.headerOffsetPx
                scrollUpdate.globalScrollOffset?.let { nextOffset ->
                    globalScrollOffset.floatValue = nextOffset
                }
                when (scrollUpdate.bottomBarVisibilityIntent) {
                    BottomBarVisibilityIntent.SHOW -> bottomBarVisibleState(true)
                    BottomBarVisibilityIntent.HIDE -> bottomBarVisibleState(false)
                    null -> Unit
                }

                return Offset.Zero
            }
        }
    }
    //  包装 onVideoClick：点击视频时先隐藏底栏再导航
    val wrappedOnVideoClick: (HomeVideoClickRequest) -> Unit = remember(
        onVideoClick,
        setBottomBarVisible
    ) {
        { request ->
            hideTopTabsForForwardDetailNav = true
            delayTopTabsUntilCardSettled = false
            setBottomBarVisible(false)
            isVideoNavigating = true
            isHomeContentInteractionRestored = false
            onVideoClick(request)
        }
    }
    val onTodayWatchVideoClick: (VideoItem) -> Unit = remember(viewModel, wrappedOnVideoClick) {
        { video ->
            viewModel.markTodayWatchVideoOpened(video)
            wrappedOnVideoClick(
                HomeVideoClickRequest(
                    bvid = video.bvid,
                    cid = video.cid,
                    coverUrl = video.pic,
                    isVerticalVideo = video.isVertical,
                    source = HomeVideoClickSource.TODAY_WATCH
                )
            )
        }
    }

    // [TodayWatch首曝] 冷启动启动窗口内自动回顶一次，确保用户能看到今日推荐单卡片。
    LaunchedEffect(
        state.todayWatchPluginEnabled,
        state.todayWatchPlan?.generatedAt,
        state.currentCategory
    ) {
        if (todayWatchStartupRevealHandled) return@LaunchedEffect

        val recommendGridState = gridStates[HomeCategory.RECOMMEND] ?: return@LaunchedEffect
        val decision = decideTodayWatchStartupReveal(
            startupElapsedMs = SystemClock.elapsedRealtime() - homeStartupElapsedAt,
            isPluginEnabled = state.todayWatchPluginEnabled,
            currentCategory = state.currentCategory,
            hasTodayPlan = state.todayWatchPlan != null && !state.todayWatchCollapsed,
            firstVisibleItemIndex = recommendGridState.firstVisibleItemIndex,
            firstVisibleItemOffset = recommendGridState.firstVisibleItemScrollOffset
        )

        when (decision) {
            TodayWatchStartupRevealDecision.REVEAL -> {
                setHeaderOffsetImmediate(0f)
                if (recommendGridState.firstVisibleItemIndex > 12) {
                    recommendGridState.scrollToItem(12)
                }
                recommendGridState.animateScrollToItem(0)
                setHeaderOffsetImmediate(0f)
                globalScrollOffset.floatValue = 0f
                todayWatchStartupRevealHandled = true
            }
            TodayWatchStartupRevealDecision.SKIP -> {
                todayWatchStartupRevealHandled = true
            }
            TodayWatchStartupRevealDecision.WAIT -> Unit
        }
    }

    //  Scaffold 内容封装 (用于 Panel 左右布局复用)
    val scaffoldLayout: @Composable () -> Unit = {
        AdaptiveScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                containerColor = AppSurfaceTokens.chromeBackground(),
                bottomBar = {
                   // BottomBar logic handled by parent
                },
                contentWindowInsets = WindowInsets(0.dp)
            ) { padding ->
                   // [Refactor] Use Box to allow overlay and proper blur nesting
                   // [新增] Video Preview State (Long Press)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(homeBackdrop)
                            // 首页使用 Pager + Lazy 子层，source 挂在外层容器更稳定。
                            .hazeSource(state = hazeState)
                    ) {
                    HomeWallpaperBackdrop(
                        wallpaperUri = homeWallpaperUri,
                        appearance = homeWallpaperBackdropAppearance,
                        baseColor = AppSurfaceTokens.chromeBackground()
                    )
                    // [Fix] Re-enabled default overscroll for better feedback
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1, // [Optimization] Preload adjacent pages to prevent swipe lag
                            modifier = Modifier.fillMaxSize(),
                            key = { index -> resolveHomeTopCategoryKey(topCategories, index) }
                        ) { page ->
                        val category = resolveHomeTopCategoryOrNull(topCategories, page) ?: return@HorizontalPager
                        val categoryState = state.categoryStates[category] ?: com.android.purebilibili.feature.home.CategoryContent()
                        
                        //  独立的 PullToRefreshState，避免所有页面共享一个状态导致冲突
                        val pullRefreshState = rememberPullToRefreshState()
                        val pullDistanceFraction = pullRefreshState.distanceFraction
                        val isPageRefreshing = isRefreshing && state.currentCategory == category
                        var stablePullOffsetFraction by remember { mutableFloatStateOf(0f) }

                        //  下拉物理由策略区分：MD3 截图式跟随当前手指距离回收，旧 iOS 弹性保留防抖滞后。
                        val resolvedStablePullOffsetFraction = resolveStablePullContentOffsetFraction(
                            distanceFraction = pullDistanceFraction,
                            isRefreshing = isPageRefreshing,
                            isStateAnimating = pullRefreshState.isAnimating,
                            previousOffsetFraction = stablePullOffsetFraction,
                            motionStyle = pullRefreshMotionStyle,
                            indicatorStyle = pullRefreshIndicatorStyle
                        )
                        SideEffect {
                            stablePullOffsetFraction = resolvedStablePullOffsetFraction
                        }
                        
                        //  使用 animateFloatAsState 包装偏移量
                        val animatedDragOffsetFraction by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = resolvedStablePullOffsetFraction,
                            animationSpec = if (
                                shouldSnapPullOffsetToFinger(
                                    distanceFraction = pullDistanceFraction,
                                    isRefreshing = isPageRefreshing,
                                    isStateAnimating = pullRefreshState.isAnimating
                                )
                            ) {
                                androidx.compose.animation.core.snap()
                            } else {
                                pullRefreshReleaseSpring()
                            },
                            label = "pull_bounce"
                        )

                        //  Defers calculation to graphicsLayer
                        val calculateDragOffset: androidx.compose.ui.unit.Density.() -> Float = remember(
                            animatedDragOffsetFraction,
                            pullRefreshIndicatorStyle
                        ) {
                            {
                                val maxPx = resolvePullContentMaxOffsetDp(pullRefreshIndicatorStyle).dp.toPx()
                                maxPx * animatedDragOffsetFraction
                            }
                        }
                        
                        //  每个页面独立的 GridState
                        //  使用 saveable 记住滚动位置
                        val pageGridState = gridStates[category] ?: rememberLazyGridState()
                        
                        //  把 GridState 提升给父级用于控制 Header? 
                        
                        ComfortablePullToRefreshBox(
                            isRefreshing = isRefreshing && state.currentCategory == category,
                            onRefresh = {
                                if (category == HomeCategory.FOLLOW) {
                                    viewModel.refresh(category)
                                } else {
                                    viewModel.refresh()
                                }
                            },
                            state = pullRefreshState,
                            modifier = Modifier.fillMaxSize(),
                             //  不同原生外观使用不同下拉刷新指示器，位移策略仍由 policy 统一控制。
                             indicator = {
                                when (pullRefreshIndicatorStyle) {
                                    HomePullRefreshIndicatorStyle.MATERIAL_DEFAULT -> {
                                        PullToRefreshDefaults.Indicator(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = listTopPadding),
                                            isRefreshing = isPageRefreshing,
                                            state = pullRefreshState
                                        )
                                    }
                                    HomePullRefreshIndicatorStyle.MD3_SCREENSHOT_HANDLE -> {
                                        val indicatorHeight = resolveMd3ScreenshotRefreshIndicatorHeightDp(
                                            progress = pullDistanceFraction,
                                            isRefreshing = isPageRefreshing
                                        ).dp
                                        Md3ScreenshotRefreshIndicator(
                                            state = pullRefreshState,
                                            isRefreshing = isPageRefreshing,
                                            indicatorHeight = indicatorHeight,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = listTopPadding)
                                                .graphicsLayer {
                                                    val currentDragOffset = calculateDragOffset()
                                                    val totalHeight = indicatorHeight.toPx() + 36.dp.toPx()
                                                    translationY = resolveMd3ScreenshotRefreshIndicatorTranslationY(
                                                        dragOffsetPx = currentDragOffset,
                                                        indicatorTotalHeightPx = totalHeight,
                                                        minGapPx = 8.dp.toPx()
                                                    )
                                                }
                                                .fillMaxWidth()
                                        )
                                    }
                                    HomePullRefreshIndicatorStyle.IOS -> {
                                        iOSRefreshIndicator(
                                            state = pullRefreshState,
                                            isRefreshing = isPageRefreshing,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(top = listTopPadding)
                                                .graphicsLayer {
                                                    val currentDragOffset = calculateDragOffset()
                                                    val indicatorHeight = 40.dp.toPx()
                                                    val minGap = 8.dp.toPx()
                                                    translationY = resolvePullIndicatorTranslationY(
                                                        dragOffsetPx = currentDragOffset,
                                                        indicatorHeightPx = indicatorHeight,
                                                        minGapPx = minGap,
                                                        isRefreshing = isPageRefreshing
                                                    )
                                                }
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                             }
                        ) {
                             // [物理优化] 内容容器应用下沉效果
                             Box(
                                 modifier = Modifier
                                     .fillMaxSize()
                                     .graphicsLayer {
                                         translationY = calculateDragOffset()
                                     }
                             ) {
                             if (categoryState.isLoading && categoryState.videos.isEmpty() && categoryState.liveRooms.isEmpty()) {
                                 // Loading Skeleton per page
                                 LazyVerticalGrid(
                                     columns = GridCells.Fixed(gridColumns),
                                     contentPadding = PaddingValues(
                                         bottom = homeListBottomPadding,
                                         start = 8.dp, end = 8.dp, top = listTopPadding // [Fix] Apply top padding to skeleton grid too
                                     ),
                                     horizontalArrangement = Arrangement.spacedBy(10.dp),
                                     verticalArrangement = Arrangement.spacedBy(10.dp),
                                     modifier = Modifier.fillMaxSize()
                                 ) {
                                     // [Fix] Dynamic skeleton count to fill tablet screens (at least 5 rows)
                                     val skeletonItemCount = gridColumns * 5
                                     // [Fix] Use modulo to prevent excessive delay for large item counts on tablet
                                     // Cap the animation wave to ~10 items (approx 800ms max delay) to ensure visibility
                                     items(skeletonItemCount) { index -> VideoCardSkeleton(index = index % 10) }
                                 }
                             } else if (categoryState.error != null && categoryState.videos.isEmpty()) {
                                 // Error State per page
                                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                     ModernErrorState(
                                         message = categoryState.error,
                                         onRetry = { viewModel.refresh() }
                                     )
                                 }
                             } else {
                                 // Data Content
                                 // [性能优化] Stabilize event callbacks to prevent recomposition on scroll
                                 val onLoadMoreCallback = remember(viewModel) { { viewModel.loadMore() } }
                                 val onDismissVideoCallback = remember(viewModel, cardAnimationEnabled) {
                                     { bvid: String ->
                                         val transition = resolveHomeDismissVisualTransition(
                                             isFeedbackRecorded = true,
                                             cardAnimationEnabled = cardAnimationEnabled
                                         )
                                         if (transition.shouldStartDissolve) {
                                             viewModel.startVideoDissolve(bvid)
                                         } else if (transition.shouldRemoveImmediately) {
                                             viewModel.completeVideoDissolve(bvid)
                                         }
                                     }
                                 }
                                 val onWatchLaterCallback = remember(viewModel) { { bvid: String, aid: Long -> viewModel.addToWatchLater(bvid, aid) } }
                                 val onDissolveCompleteCallback = remember(viewModel) { { bvid: String -> viewModel.completeVideoDissolve(bvid) } }
                                 val onLongPressCallback = remember(targetVideoItemState) { { item: VideoItem -> targetVideoItemState.value = item } }
                                 val onLiveClickCallback = remember(onLiveClick) { onLiveClick }
                                 val onTodayWatchModeChange = remember(viewModel) { { mode: TodayWatchMode -> viewModel.switchTodayWatchMode(mode) } }
                                 val onTodayWatchCollapsedChange = remember(viewModel) { { collapsed: Boolean -> viewModel.setTodayWatchCollapsed(collapsed) } }
                                 val onTodayWatchRefresh = remember(viewModel) { { viewModel.refreshTodayWatchOnly() } }
                                 val onTodayWatchUpClick = remember(onSpaceClick) { { mid: Long -> onSpaceClick(mid) } }
                                 val onHomeFeedUpClick = remember(onSpaceClick) { { mid: Long -> onSpaceClick(mid) } }
                                 val onPopularSubCategoryChange = remember(viewModel) {
                                     { subCategory: PopularSubCategory -> viewModel.switchPopularSubCategory(subCategory) }
                                 }

                                 HomeCategoryPageContent(
                                     category = category,
                                     categoryState = categoryState,
                                     gridState = pageGridState,
                                     gridColumns = gridColumns,
                                     contentPadding = PaddingValues(
                                         bottom = homeListBottomPadding,
                                         start = 8.dp, end = 8.dp, top = listTopPadding 
                                     ),
                                     dissolvingVideos = state.dissolvingVideos,
                                     followingMids = state.followingMids,
                                     onVideoClick = wrappedOnVideoClick,
                                     onUpClick = onHomeFeedUpClick,
                                     onLiveClick = onLiveClickCallback,
                                     onLoadMore = onLoadMoreCallback,
                                     onDismissVideo = onDismissVideoCallback,
                                     onWatchLater = onWatchLaterCallback,
                                     onDissolveComplete = onDissolveCompleteCallback,
                                     longPressCallback = onLongPressCallback, // [Feature] Pass callback
                                     displayMode = displayMode,
                                     cardAnimationEnabled = cardAnimationEnabled,
                                     cardMotionTier = cardMotionTier,
                                     cardTransitionEnabled = cardTransitionEnabled,
                                     isReturningFromVideoDetail = isReturningFromVideoDetail,
                                     isQuickReturningFromVideoDetail = isQuickReturningFromVideoDetail,
                                     smartVisualGuardEnabled = false,
                                     isDataSaverActive = isDataSaverActive,
                                     preferLowQualityCover = homeSettings.lowQualityHomeCoverInDataSaver,
                                     compactStatsOnCover = homeSettings.compactVideoStatsOnCover,
                                     showCoverGlassBadges = homeSettings.showHomeCoverGlassBadges,
                                     showInfoGlassBadges = homeSettings.showHomeInfoGlassBadges,
                                     wallpaperTintEnabled = homeWallpaperBackdropAppearance.visible,
                                     wallpaperEffectMode = homeSettings.homeWallpaperEffectMode,
                                     showUpBadges = homeSettings.showHomeUpBadges,
                                     showDurationBadges = homeSettings.showHomeVideoDurationBadges,
                                     oldContentAnchorBvid = if (shouldShowRecommendOldContentDivider(
                                             currentCategory = category,
                                             refreshNewItemsKey = state.refreshNewItemsKey,
                                             revealedRefreshKey = state.recommendOldContentRevealKey,
                                             anchorBvid = state.recommendOldContentAnchorBvid,
                                             oldContentStartIndex = state.recommendOldContentStartIndex
                                         )
                                     ) {
                                         state.recommendOldContentAnchorBvid
                                     } else {
                                         null
                                     },
                                     oldContentStartIndex = if (shouldShowRecommendOldContentDivider(
                                             currentCategory = category,
                                             refreshNewItemsKey = state.refreshNewItemsKey,
                                             revealedRefreshKey = state.recommendOldContentRevealKey,
                                             anchorBvid = state.recommendOldContentAnchorBvid,
                                             oldContentStartIndex = state.recommendOldContentStartIndex
                                         )
                                     ) {
                                         state.recommendOldContentStartIndex
                                     } else {
                                         null
                                     },
                                     todayWatchEnabled = category == HomeCategory.RECOMMEND && state.todayWatchPluginEnabled,
                                     todayWatchMode = state.todayWatchMode,
                                     todayWatchPlan = if (category == HomeCategory.RECOMMEND) state.todayWatchPlan else null,
                                     todayWatchLoading = category == HomeCategory.RECOMMEND && state.todayWatchLoading,
                                     todayWatchError = if (category == HomeCategory.RECOMMEND) state.todayWatchError else null,
                                     todayWatchCollapsed = category == HomeCategory.RECOMMEND && state.todayWatchCollapsed,
                                     todayWatchCardConfig = state.todayWatchCardConfig,
                                     onTodayWatchModeChange = onTodayWatchModeChange,
                                     onTodayWatchCollapsedChange = onTodayWatchCollapsedChange,
                                     onTodayWatchRefresh = onTodayWatchRefresh,
                                     onTodayWatchUpClick = onTodayWatchUpClick,
                                     popularSubCategory = state.popularSubCategory,
                                     onPopularSubCategoryChange = onPopularSubCategoryChange,
                                     onTodayWatchVideoClick = onTodayWatchVideoClick,
                                     uiSkinDecoration = homeUiSkinDecoration,
                                     firstGridItemModifier = Modifier
                                 )
                             }
                             } // Close Box wrapper
                        }
                } // Close HorizontalPager lambda
            } // Close Box wrapper
        } // Close Scaffold lambda
        
        //  ===== Header Overlay (毛玻璃效果) =====
        //  Header 现在在外层 Box 内、hazeSource 外部，可以正确模糊内层内容
        val isSkeletonState = state.isLoading && state.videos.isEmpty() && state.liveRooms.isEmpty()
        val isErrorState = state.error != null && 
            ((state.currentCategory == HomeCategory.LIVE && state.liveRooms.isEmpty()) ||
             (state.currentCategory != HomeCategory.LIVE && state.videos.isEmpty()))

        //  [Restored] Header 始终显示，不再随 Loading/Error 状态隐藏
        //  这保证了 Tab 指示器状态的连续性，防止消失或重置
        val isFeedScrollInProgress by remember(activeGridState) {
            derivedStateOf { activeGridState?.isScrollInProgress == true }
        }
        SideEffect {
            globalFeedScrollInProgress.value = isFeedScrollInProgress
        }
        DisposableEffect(Unit) {
            onDispose {
                globalFeedScrollInProgress.value = false
            }
        }
        val homeInteractionMotionBudget = resolveHomeInteractionMotionBudget(
            isPagerScrolling = pagerState.isScrollInProgress,
            isProgrammaticPageSwitchInProgress = programmaticPageSwitchInProgress,
            isFeedScrolling = isFeedScrollInProgress
        )
        val isHeaderTransitionRunning by remember(pagerState, headerSettleAnimationJob, isFeedScrollInProgress) {
            derivedStateOf {
                resolveHomeHeaderTransitionRunning(
                    isFeedScrolling = isFeedScrollInProgress,
                    isPagerScrolling = pagerState.isScrollInProgress,
                    isHeaderSettleAnimating = headerSettleAnimationJob != null
                )
            }
        }
        TrackJankStateFlag(
            stateName = "home:header_transition",
            isActive = isHeaderTransitionRunning
        )
        val forceLowBlurBudget = false
        val overlayChromeColors = rememberHomeGlassChromeColors(
            glassEnabled = isLiquidGlassEnabled,
            blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled
        )
        val refreshTipAppearance = remember(isLiquidGlassEnabled, isHeaderBlurEnabled, isBottomBarBlurEnabled) {
            resolveHomeRefreshTipAppearance(
                liquidGlassEnabled = isLiquidGlassEnabled,
                blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled
            )
        }
        val overlayPillColors = rememberHomeGlassPillColors(
            glassEnabled = isLiquidGlassEnabled,
            blurEnabled = isHeaderBlurEnabled || isBottomBarBlurEnabled,
            emphasized = true,
            baseColor = AppSurfaceTokens.cardContainer()
        )
        
        // Calculate parameters based on scroll
        // 1. Search Bar Collapse (First phase)
        val topTabsCollapsedForHeader = if (isHeaderCollapseEnabled) {
            areTopTabsAutoCollapsed
        } else {
            areTopTabsManuallyCollapsed
        }
        iOSHomeHeader(
            headerOffsetProvider = { headerOffsetHeightPx }, // [Optimization] Pass lambda to defer state read
            isHeaderCollapseEnabled = isHeaderCollapseEnabled,
            user = state.user,
            onAvatarClick = {
                when (
                    resolveHomeAvatarAction(
                        isLoggedIn = state.user.isLogin,
                        isHomeDrawerEnabled = isHomeDrawerEnabled
                    )
                ) {
                    HomeAvatarAction.OPEN_DRAWER -> coroutineScope.launch { drawerState.open() }
                    HomeAvatarAction.OPEN_PROFILE -> onProfileClick()
                    HomeAvatarAction.OPEN_LOGIN -> onAvatarClick()
                }
            },
            onSettingsClick = onSettingsClick,
            onInboxClick = onInboxClick,
            topRightUnreadCount = state.messageUnreadCount,
            onSearchClick = onSearchClick,
            topCategories = localizedTopCategoryLabels,
            topCategoryKeys = topCategories.map { it.name },
            categoryIndex = displayedTabIndex,
            onCategorySelected = { index ->
                viewModel.updateDisplayedTabIndex(index)
                topCategories.getOrNull(index)?.let { selectedCategory ->
                    if (pagerState.currentPage != index) {
                        programmaticPageSwitchInProgress = true
                        coroutineScope.launch {
                            try {
                                pagerState.animateScrollToPage(
                                    page = index,
                                    animationSpec = tween(
                                        durationMillis = 240,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            } finally {
                                programmaticPageSwitchInProgress = false
                            }
                        }
                    }
                    viewModel.switchCategory(selectedCategory)
                }
            },
            onPartitionClick = onPartitionClick,
            // isScrollingUp = isHeaderVisible, // [Removed] logic moved to offset
            hazeState = if (topChromeMaterialMode != com.android.purebilibili.feature.home.components.TopTabMaterialMode.PLAIN) {
                hazeState
            } else {
                null
            },
            onStatusBarDoubleTap = {
                coroutineScope.launch {
                    gridStates[state.currentCategory]?.animateScrollToItem(0)
                    setHeaderOffsetImmediate(0f) // [Refinement] Reset header on double tap
                    globalScrollOffset.floatValue = 0f
                }
            },
            isRefreshing = isRefreshing,
            pullProgress = 0f, // [Fix] Outer header doesn't track inner pull state
            pagerState = pagerState,
            backdrop = homeBackdrop,
            homeSettings = effectiveHomeSettings,
            topTabsVisible = resolveHomeTopTabsVisible(
                isDelayedForCardSettle = delayTopTabsUntilCardSettled,
                isForwardNavigatingToDetail = hideTopTabsForForwardDetailNav,
                isReturningFromDetail = isReturningFromVideoDetail,
                topTabsCollapsed = topTabsCollapsedForHeader
            ),
            topTabsCollapsed = topTabsCollapsedForHeader,
            onTopTabsCollapsedChange = { collapsed ->
                if (!isHeaderCollapseEnabled) {
                    areTopTabsManuallyCollapsed = collapsed
                }
            },
            motionTier = deviceUiProfile.motionTier,
            isScrolling = isFeedScrollInProgress,
            isTransitionRunning = isHeaderTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            interactionBudget = homeInteractionMotionBudget,
            uiSkinDecoration = homeUiSkinDecoration
        )

        AnimatedVisibility(
            visible = refreshDeltaTipText != null,
            enter = fadeIn(animationSpec = tween(overlayMotionSpec.refreshTipEnterFadeDurationMillis)) + slideInVertically(
                animationSpec = tween(overlayMotionSpec.refreshTipSlideDurationMillis),
                initialOffsetY = { -it / 2 }
            ),
            exit = fadeOut(animationSpec = tween(overlayMotionSpec.refreshTipExitFadeDurationMillis)) + slideOutVertically(
                animationSpec = tween(overlayMotionSpec.refreshTipSlideDurationMillis),
                targetOffsetY = { -it / 2 }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = listTopPadding + 8.dp)
                .zIndex(90f)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = AppShapes.container(ContainerLevel.Pill),
                    color = if (refreshTipAppearance.surfaceStyle == HomeRefreshTipSurfaceStyle.PLAIN) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        overlayChromeColors.containerColor
                    },
                    border = if (refreshTipAppearance.borderWidthDp > 0f) {
                        BorderStroke(
                            refreshTipAppearance.borderWidthDp.dp,
                            overlayChromeColors.borderColor
                        )
                    } else {
                        null
                    },
                    tonalElevation = refreshTipAppearance.tonalElevationDp.dp,
                    shadowElevation = refreshTipAppearance.shadowElevationDp.dp
                ) {
                    Text(
                        text = refreshDeltaTipText.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        //  [新增] 刷新撤销悬浮按钮（右下角，5秒后自动消失）
        val undoVisible = state.undoAvailable && state.currentCategory == HomeCategory.RECOMMEND
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(95f),
            contentAlignment = Alignment.BottomEnd
        ) {
            AnimatedVisibility(
                visible = undoVisible,
                enter = fadeIn(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideInVertically(
                    animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                    initialOffsetY = { it }
                ),
                exit = fadeOut(animationSpec = tween(overlayMotionSpec.undoFabFadeDurationMillis)) + slideOutVertically(
                    animationSpec = tween(overlayMotionSpec.undoFabSlideDurationMillis),
                    targetOffsetY = { it }
                ),
                modifier = Modifier.padding(end = 16.dp, bottom = homeListBottomPadding + 8.dp)
            ) {
            androidx.compose.material3.Button(
                onClick = { viewModel.undoRefresh() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = overlayPillColors.containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(0.8.dp, overlayPillColors.borderColor),
                shape = AppShapes.container(ContainerLevel.Pill),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "⟲",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "撤销刷新",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            }
        }

        // [Feature] Video Preview Overlay with Animation
        androidx.compose.animation.AnimatedVisibility(
            visible = targetVideoItemState.value != null,
            enter = fadeIn(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(
                    overlayMotionSpec.previewOverlayScaleDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ),
            exit = fadeOut(tween(overlayMotionSpec.previewOverlayFadeDurationMillis)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(
                    overlayMotionSpec.previewOverlayScaleDurationMillis,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ),
            modifier = Modifier.fillMaxSize().zIndex(100f) // Ensure on top
        ) {
            val item = targetVideoItemState.value
            if (item != null) {
                com.android.purebilibili.feature.home.components.VideoPreviewDialog(
                    video = item,
                    onDismiss = { targetVideoItemState.value = null },
                    onPlay = {
                     // 1. Log click
                     wrappedOnVideoClick(
                         HomeVideoClickRequest(
                             bvid = item.bvid,
                             cid = item.cid,
                             coverUrl = item.pic,
                             isVerticalVideo = item.isVertical,
                             source = HomeVideoClickSource.PREVIEW
                         )
                     )
                     // 2. Clear preview state
                     targetVideoItemState.value = null
                },
                onWatchLater = {
                    viewModel.addToWatchLater(item.bvid, item.aid)
                    targetVideoItemState.value = null
                },
                onSaveCover = {
                    val coverUrl = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(item.pic)
                    if (coverUrl.isBlank()) {
                        android.widget.Toast.makeText(context, "无法获取封面地址", android.widget.Toast.LENGTH_SHORT).show()
                        targetVideoItemState.value = null
                    } else {
                        coroutineScope.launch {
                            val success = com.android.purebilibili.feature.download.DownloadManager
                                .saveImageToGallery(context, coverUrl, item.title)
                            val message = if (success) "封面已保存到相册" else "保存失败"
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        targetVideoItemState.value = null
                    }
                },
                onShare = {
                   val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "【${item.title}】 https://www.bilibili.com/video/${item.bvid}")
                    }
                    val chooser = android.content.Intent.createChooser(shareIntent, "分享视频")
                    if (context !is android.app.Activity) {
                        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                    targetVideoItemState.value = null
                },
                onNotInterested = {
                    viewModel.markNotInterested(item.bvid, cardAnimationEnabled = cardAnimationEnabled)
                    targetVideoItemState.value = null
                },
                onBlockCreator = {
                    viewModel.blockCreator(item)
                    targetVideoItemState.value = null
                },
                onGetPreviewUrl = { bvid, cid ->
                    viewModel.getPreviewVideoUrl(bvid, cid)
                },
                hazeState = hazeState
            )
            }
        }
    }

    val scaffoldContent: @Composable () -> Unit = {
        if (isHomeDrawerEnabled) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                scrimColor = Color.Black.copy(alpha = resolveHomeDrawerScrimAlpha(isHeaderBlurEnabled)),
                drawerContent = {
                    MineSideDrawer(
                        drawerState = drawerState,
                        user = state.user,
                        onLogout = resolveHomeDrawerLogoutAction(
                            onLogout = onLogout,
                            onProfileClick = onProfileClick
                        ),
                        onHistoryClick = onHistoryClick,
                        onFavoriteClick = onFavoriteClick,
                        onBangumiClick = { onBangumiClick(1) },
                        onDownloadClick = onDownloadClick,
                        onWatchLaterClick = onWatchLaterClick,
                        onInboxClick = onInboxClick,
                        onSettingsClick = onSettingsClick,
                        onProfileClick = onProfileClick,
                        hazeState = hazeState,
                        isBlurEnabled = isHeaderBlurEnabled
                    )
                }
            ) {
                scaffoldLayout()
            }
        } else {
            scaffoldLayout()
        }
    }

    
    //  使用生命周期事件：
    // ON_START: 非视频返回底栏立即恢复
    // ON_STOP: 清理定时器
    // 视频返回的顶栏/底栏恢复统一由导航返回态 LaunchedEffect 处理，避免依赖不稳定的页面生命周期。
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val currentBottomBarVisible by rememberUpdatedState(bottomBarVisible)
    DisposableEffect(lifecycleOwner, useSideNavigation) {
        if (useSideNavigation) {
            return@DisposableEffect onDispose { }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    //  底栏由动画完成 LaunchedEffect 统一恢复，此处不再独立计时
                    if (!currentBottomBarVisible && !isVideoNavigating) {
                        //  从设置等非视频页面返回时，立即显示底栏（无延迟）
                        setBottomBarVisible(true)
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    topTabsRevealJob?.cancel()
                    // setBottomBarVisible(false) // REMOVED
                }
                else -> { /* 其他事件不处理 */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            topTabsRevealJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    //  [修复] 使用 rememberSaveable 记住本次会话中是否已处理过弹窗（防止导航后重新显示）
    var consentDialogHandled by rememberSaveable { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    
    //  检查欢迎弹窗是否已显示过（确保弹窗顺序显示，不会同时出现）
    val welcomePrefs = remember { context.getSharedPreferences("app_welcome", Context.MODE_PRIVATE) }
    val welcomeAlreadyShown = welcomePrefs.getBoolean("first_launch_shown", false)
    
    // 检查是否需要显示弹窗（欢迎弹窗已显示过 且 同意弹窗尚未显示过 且 本次会话未处理过）
    LaunchedEffect(crashTrackingConsentShown) {
        if (welcomeAlreadyShown && !crashTrackingConsentShown && !consentDialogHandled) {
            showConsentDialog = true
        }
    }
    
    // 显示弹窗
    if (showConsentDialog) {
        com.android.purebilibili.feature.home.components.CrashTrackingConsentDialog(
            onDismiss = { 
                showConsentDialog = false
                consentDialogHandled = true  // 标记为已处理
            }
        )
    }
    
    //  计算滚动偏移量用于头部动画 -  优化：量化减少重组
    //  计算滚动偏移量用于头部动画 -  优化：量化减少重组
    val scrollOffset by remember {
        derivedStateOf {
            val currentGridState = gridStates[state.currentCategory]
            if (currentGridState == null) return@derivedStateOf 0f
            
            val firstVisibleItem = currentGridState.firstVisibleItemIndex
            if (firstVisibleItem == 0) {
                //  直接使用原始偏移量，避免量化导致的跳变
                currentGridState.firstVisibleItemScrollOffset.toFloat()
            } else 1000f
        }
    }
    
    //  滚动方向（简化版 - 不再需要复杂检测，因为标签页只在顶部显示）
    val isScrollingUp = true  // 保留参数兼容性

    //  [性能优化] 图片预加载 - 提前加载即将显示的视频封面
    // 📉 [省流量] 省流量模式下禁用预加载
    LaunchedEffect(state.currentCategory, isDataSaverActive, preloadAheadCount) {
        // 📉 省流量模式下跳过预加载
        if (isDataSaverActive) return@LaunchedEffect
        if (preloadAheadCount <= 0) return@LaunchedEffect
        
        val currentGridState = gridStates[state.currentCategory] ?: return@LaunchedEffect
        
        snapshotFlow {
            val lastVisibleIndex = currentGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex to currentGridState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, isScrollInProgress) ->
                val videos = state.categoryStates[state.currentCategory]?.videos ?: state.videos
                val preloadRange = resolveHomeCoverPreloadRange(
                    isDataSaverActive = isDataSaverActive,
                    isScrollInProgress = isScrollInProgress,
                    lastVisibleIndex = lastVisibleIndex,
                    totalItemCount = videos.size,
                    preloadAheadCount = preloadAheadCount
                ) ?: return@collect
                val imageUrls = preloadRange.mapNotNull { index -> videos.getOrNull(index)?.pic }
                if (imageUrls.isEmpty()) return@collect

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    for (imageUrl in imageUrls) {
                        val fixedUrl = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(imageUrl)

                        val request = coil.request.ImageRequest.Builder(context)
                            .data(fixedUrl)
                            .size(360, 225)  //  预加载也使用限制尺寸
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                        context.imageLoader.enqueue(request)
                    }
                }
            }
    }


    //  PullToRefreshBox 自动处理下拉刷新逻辑
    
    //  [已移除] 特殊分类（ANIME, MOVIE等）不再在首页切换，直接导航到独立页面
    
    //  [修复] 如果当前在直播-关注分类且列表为空，返回时先切换到热门，再切换到推荐
    val isEmptyLiveFollowed = state.currentCategory == HomeCategory.LIVE && 
                               state.liveSubCategory == LiveSubCategory.FOLLOWED &&
                               state.liveRooms.isEmpty() && 
                               !state.isLoading
    androidx.activity.compose.BackHandler(enabled = isEmptyLiveFollowed) {
        // 切换到热门直播
        viewModel.switchLiveSubCategory(LiveSubCategory.POPULAR)
    }

    //  [修复] 如果当前在直播分类（非关注空列表情况），返回时切换到推荐
    val isLiveCategoryNotHome = state.currentCategory == HomeCategory.LIVE && !isEmptyLiveFollowed
    androidx.activity.compose.BackHandler(enabled = isLiveCategoryNotHome) {
        viewModel.switchCategory(HomeCategory.RECOMMEND)
    }
    
// [Removed] Animation logic moved inside HorizontalPager where the active state exists
    
    // 指示器位置逻辑也移入 graphicsLayer
    Box(modifier = Modifier.fillMaxSize()) {
        scaffoldContent()
    }
}

internal data class HomeOverlayMotionSpec(
    val refreshTipEnterFadeDurationMillis: Int,
    val refreshTipExitFadeDurationMillis: Int,
    val refreshTipSlideDurationMillis: Int,
    val undoFabFadeDurationMillis: Int,
    val undoFabSlideDurationMillis: Int,
    val previewOverlayFadeDurationMillis: Int,
    val previewOverlayScaleDurationMillis: Int,
    val sideNavEnterSlideDurationMillis: Int,
    val sideNavExitSlideDurationMillis: Int,
    val sideNavFadeDurationMillis: Int
)

internal fun resolveHomeOverlayMotionSpec(): HomeOverlayMotionSpec {
    return HomeOverlayMotionSpec(
        refreshTipEnterFadeDurationMillis = 180,
        refreshTipExitFadeDurationMillis = 220,
        refreshTipSlideDurationMillis = 220,
        undoFabFadeDurationMillis = 200,
        undoFabSlideDurationMillis = 250,
        previewOverlayFadeDurationMillis = 200,
        previewOverlayScaleDurationMillis = 200,
        sideNavEnterSlideDurationMillis = 300,
        sideNavExitSlideDurationMillis = 250,
        sideNavFadeDurationMillis = 200
    )
}

internal fun resolveReturnAnimationSuppressionDurationMs(
    isTabletLayout: Boolean,
    cardAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean
): Long {
    if (cardTransitionEnabled && isQuickReturnFromDetail) {
        return if (isTabletLayout) 540L else 420L
    }
    if (cardTransitionEnabled) {
        if (!cardAnimationEnabled) return if (isTabletLayout) 460L else 400L
        return if (isTabletLayout) 460L else 400L
    }
    if (!cardAnimationEnabled) return 220L
    return if (isTabletLayout) 220L else 240L
}

internal fun resolveHomeContentInteractionRestoreDelayMs(
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean
): Long {
    // 视觉返场保护仍由 suppression / 底栏恢复窗口负责；
    // 首页列表手势应在页面重新可见时立即恢复，避免第一下滑动被导航态吞掉。
    return 0L
}
