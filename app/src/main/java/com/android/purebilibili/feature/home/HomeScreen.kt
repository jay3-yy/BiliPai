// 文件路径: feature/home/HomeScreen.kt
package com.android.purebilibili.feature.home

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.ExperimentalFoundationApi //  Added
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*  // 🌊 瀑布流布局
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.feature.settings.GITHUB_URL
import com.android.purebilibili.core.store.SettingsManager //  引入 SettingsManager
//  从 components 包导入拆分后的组件
import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.feature.home.components.FluidHomeTopBar
import com.android.purebilibili.feature.home.components.FrostedBottomBar
import com.android.purebilibili.feature.home.components.FrostedSideBar
import com.android.purebilibili.feature.home.components.CategoryTabRow
import com.android.purebilibili.feature.home.components.iOSHomeHeader  //  iOS 大标题头部
import com.android.purebilibili.feature.home.components.iOSRefreshIndicator  //  iOS 下拉刷新指示器
//  从 cards 子包导入卡片组件
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.LiveRoomCard
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard   //  故事卡片
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.ui.VideoCardSkeleton
import com.android.purebilibili.core.ui.ErrorState as ModernErrorState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.shimmer
import com.android.purebilibili.core.ui.LocalSharedTransitionScope  //  共享过渡
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard  //  粒子消散动画
import com.android.purebilibili.core.ui.animation.jiggleOnDissolve      // 📳 iOS 风格抖动效果
import com.android.purebilibili.core.util.responsiveContentWidth
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import coil.imageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged  //  性能优化：防止重复触发
import androidx.compose.animation.ExperimentalSharedTransitionApi  //  共享过渡实验API

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (String, Long, String) -> Unit,
    onAvatarClick: () -> Unit,
    onProfileClick: () -> Unit,
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
    onStoryClick: () -> Unit = {}  //  [新增] 竖屏短视频
) {
    val state by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val staggeredGridState = rememberLazyStaggeredGridState()  // 🌊 瀑布流状态
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()  //  用于双击回顶动画
    
    // [修复] 刷新时自动滚回顶部，防止下拉用力过猛导致内容偏移
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            gridState.animateScrollToItem(0)
        }
    }
    
    //  [新增] JSON 插件过滤提示
    val snackbarHostState = remember { SnackbarHostState() }
    val lastFilteredCount by com.android.purebilibili.core.plugin.json.JsonPluginManager.lastFilteredCount.collectAsState()
    
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
    
    //  [彩蛋] 彩蛋开关设置
    val easterEggEnabled by SettingsManager.getEasterEggEnabled(context).collectAsState(initial = true)
    var showEasterEggDialog by remember { mutableStateOf(false) }
    
    //  [彩蛋] 下拉刷新成功后显示趣味提示（仅在开关开启时）
    LaunchedEffect(state.refreshKey, easterEggEnabled) {
        val message = state.refreshMessage
        if (message != null && state.refreshKey > 0 && easterEggEnabled) {
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
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    //  [修复] 确保首页显示时 WindowInsets 配置正确，防止从视频页返回时布局跳动
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        // 保持边到边显示（与 VideoDetailScreen 一致）
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    //  [性能优化] 合并首页设置为单一 Flow，减少 6 个 collectAsState → 1 个
    val homeSettings by SettingsManager.getHomeSettings(context).collectAsState(
        initial = com.android.purebilibili.core.store.HomeSettings()
    )
    
    // 解构设置值（避免每次访问都触发重组）
    val displayMode = homeSettings.displayMode
    val isBottomBarFloating = homeSettings.isBottomBarFloating
    val bottomBarLabelMode = homeSettings.bottomBarLabelMode
    val isHeaderBlurEnabled = homeSettings.isHeaderBlurEnabled
    val isBottomBarBlurEnabled = homeSettings.isBottomBarBlurEnabled
    val crashTrackingConsentShown = homeSettings.crashTrackingConsentShown
    val cardAnimationEnabled = homeSettings.cardAnimationEnabled      //  卡片进场动画开关
    val cardTransitionEnabled = homeSettings.cardTransitionEnabled    //  卡片过渡动画开关
    
    //  [新增] 底栏可见项目配置
    val orderedVisibleTabIds by SettingsManager.getOrderedVisibleTabs(context).collectAsState(
        initial = listOf("HOME", "DYNAMIC", "HISTORY", "PROFILE")
    )
    // 将字符串 ID 转换为 BottomNavItem 枚举
    val visibleBottomBarItems = remember(orderedVisibleTabIds) {
        orderedVisibleTabIds.mapNotNull { id ->
            try { BottomNavItem.valueOf(id) } catch (e: Exception) { null }
        }
    }
    
    //  [新增] 底栏项目颜色配置
    val bottomBarItemColors by SettingsManager.getBottomBarItemColors(context).collectAsState(initial = emptyMap())
    
    //  📐 [平板适配] 根据屏幕尺寸和展示模式动态设置网格列数
    // 故事卡片需要单列全宽，网格和玻璃使用双列，平板端使用多列
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val contentWidth = if (windowSizeClass.isExpandedScreen) {
        minOf(windowSizeClass.widthDp, 1000.dp)
    } else {
        windowSizeClass.widthDp
    }
    val adaptiveColumns = remember(contentWidth, displayMode) {
        val minColumnWidth = if (displayMode == 1) 240.dp else 180.dp
        val maxColumns = if (displayMode == 1) 2 else 6
        val columns = (contentWidth / minColumnWidth).toInt()
        columns.coerceIn(1, maxColumns)
    }
    val gridColumns = if (windowSizeClass.isExpandedScreen) {
        adaptiveColumns
    } else {
        com.android.purebilibili.core.util.rememberResponsiveValue(
            compact = if (displayMode == 1) 1 else 2,  // 手机：故事1列，其他2列
            medium = if (displayMode == 1) 2 else 3    // 中等宽度：故事2列，其它3列
        )
    }
    
    
    //  � [平板导航切换] 用户偏好设置
    val tabletUseSidebar by SettingsManager.getTabletUseSidebar(context).collectAsState(initial = false)
    
    //  �📐 [大屏适配] 平板导航模式：根据用户偏好决定
    // 仅在平板且用户选择了侧边栏时使用侧边导航
    val useSideNavigation = windowSizeClass.isExpandedScreen && tabletUseSidebar
    
    //  📱 [切换导航模式] 处理函数
    val onToggleNavigationMode: () -> Unit = {
        coroutineScope.launch {
            SettingsManager.setTabletUseSidebar(context, !tabletUseSidebar)
        }
    }

    //  [修复] 恢复状态栏样式：确保从视频详情页返回后状态栏正确
    // 当使用滑动动画时，Theme.kt 的 SideEffect 可能不会重新执行
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }
    
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
    
    val bottomBarHeight = if (isBottomBarFloating) {
        84.dp + navBarHeight  // 72dp(栏高度) + 12dp(底部边距)
    } else {
        64.dp + navBarHeight  // 64dp(Docked模式)
    }

    //  [修复] 动态计算内容顶部边距，防止被头部遮挡
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listTopPadding = statusBarHeight + 110.dp  // 状态栏 + 52dp搜索栏 + 48dp标签栏 + 10dp间距

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    //  当前选中的导航项
    var currentNavItem by remember { mutableStateOf(BottomNavItem.HOME) }

    // 统一导航点击逻辑（底栏/侧栏复用）
    val handleNavItemClick: (BottomNavItem) -> Unit = { item ->
        currentNavItem = item
        when (item) {
            BottomNavItem.HOME -> {
                coroutineScope.launch { gridState.animateScrollToItem(0) }
            }
            BottomNavItem.DYNAMIC -> onDynamicClick()
            BottomNavItem.HISTORY -> onHistoryClick()
            BottomNavItem.PROFILE -> onProfileClick()
            BottomNavItem.FAVORITE -> onFavoriteClick()
            BottomNavItem.LIVE -> onLiveListClick()
            BottomNavItem.WATCHLATER -> onWatchLaterClick()
            BottomNavItem.STORY -> onStoryClick()
        }
    }
    
    //  [新增] 底栏显示模式设置
    val bottomBarVisibilityMode by SettingsManager.getBottomBarVisibilityMode(context).collectAsState(
        initial = SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE
    )
    
    //  [新增] 底栏可见性状态（根据模式初始化）
    var bottomBarVisible by remember { mutableStateOf(true) }
    
    //  [修复] 跟踪是否正在导航到/从视频页 - 必须在 LaunchedEffect 之前声明
    var isVideoNavigating by remember { mutableStateOf(false) }
    
    //  [新增] 滚动方向检测状态（用于上滑隐藏模式）
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
    
    //  [新增] 滚动方向检测逻辑
    LaunchedEffect(gridState, bottomBarVisibilityMode, useSideNavigation) {
        if (useSideNavigation) {
            bottomBarVisible = false
            return@LaunchedEffect
        }
        if (bottomBarVisibilityMode != SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE) {
            // 非滚动隐藏模式时，根据设置决定底栏可见性
            bottomBarVisible = bottomBarVisibilityMode == SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE
            return@LaunchedEffect
        }
        
        // 上滑隐藏模式：监听滚动方向
        snapshotFlow {
            Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }
        .distinctUntilChanged()
        .collect { (firstVisibleItem, scrollOffset) ->
            // 视频导航期间不处理滚动隐藏
            if (isVideoNavigating) return@collect
            
            // 滚动到顶部时始终显示
            if (firstVisibleItem == 0 && scrollOffset < 100) {
                bottomBarVisible = true
            } else {
                // 计算滚动方向
                val isScrollingDown = when {
                    firstVisibleItem > lastFirstVisibleItem -> true
                    firstVisibleItem < lastFirstVisibleItem -> false
                    else -> scrollOffset > lastScrollOffset + 30 // 阈值30px
                }
                val isScrollingUp = when {
                    firstVisibleItem < lastFirstVisibleItem -> true
                    firstVisibleItem > lastFirstVisibleItem -> false
                    else -> scrollOffset < lastScrollOffset - 30
                }
                
                if (isScrollingDown) bottomBarVisible = false
                else if (isScrollingUp) bottomBarVisible = true
            }
            
            lastFirstVisibleItem = firstVisibleItem
            lastScrollOffset = scrollOffset
        }
    }
    
    //  [修复] 用于取消延迟协程的 Job 引用
    var bottomBarRestoreJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    //  包装 onVideoClick：点击视频时先隐藏底栏再导航
    val wrappedOnVideoClick: (String, Long, String) -> Unit = remember(onVideoClick) {
        { bvid, cid, cover ->
            //  取消之前的恢复协程，防止竞态条件
            bottomBarRestoreJob?.cancel()
            bottomBarRestoreJob = null
            
            bottomBarVisible = false  //  触发底栏下滑动画
            isVideoNavigating = true  //  标记正在导航到视频
            onVideoClick(bvid, cid, cover)
        }
    }
    
    //  [修复] 使用生命周期事件控制底栏可见性
    // ON_START: 恢复底栏（仅在从视频页返回时）
    // ON_STOP: 隐藏底栏（导航到其他页面时，避免影响导航栏区域）
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, useSideNavigation) {
        if (useSideNavigation) {
            return@DisposableEffect onDispose { }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    //  关键修复：只在底栏当前隐藏时才恢复可见
                    if (!bottomBarVisible && isVideoNavigating) {
                        //  [同步动画] 延迟后再显示底栏，让进入动画与卡片返回动画同步
                        bottomBarRestoreJob = kotlinx.coroutines.MainScope().launch {
                            kotlinx.coroutines.delay(100)  // 等待返回动画开始
                            bottomBarVisible = true
                            // 延迟重置导航状态，确保进入动画完成
                            kotlinx.coroutines.delay(400)
                            isVideoNavigating = false
                        }
                    } else if (!bottomBarVisible && !isVideoNavigating) {
                        //  [新增] 从设置等非视频页面返回时，立即显示底栏（无延迟）
                        bottomBarVisible = true
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    //  [新增] 导航离开首页时隐藏底栏，避免影响其他页面的导航栏区域
                    bottomBarRestoreJob?.cancel()
                    bottomBarRestoreJob = null
                    bottomBarVisible = false
                }
                else -> { /* 其他事件不处理 */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            bottomBarRestoreJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    //  [修复] 使用 ViewModel 中的标签页显示索引（跨导航保持）
    // 当用户滑动到特殊分类时，标签页位置更新，但内容分类保持不变
    val displayedTabIndex = state.displayedTabIndex
    
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
    val scrollOffset by remember {
        derivedStateOf {
            val firstVisibleItem = gridState.firstVisibleItemIndex
            if (firstVisibleItem == 0) {
                //  直接使用原始偏移量，避免量化导致的跳变
                gridState.firstVisibleItemScrollOffset.toFloat()
            } else 1000f
        }
    }
    
    //  滚动方向（简化版 - 不再需要复杂检测，因为标签页只在顶部显示）
    val isScrollingUp = true  // 保留参数兼容性

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4 && !state.isLoading && !isRefreshing
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }
    
    //  [性能优化] 图片预加载 - 提前加载即将显示的视频封面
    // 📉 [省流量] 省流量模式下禁用预加载
    val isDataSaverActive = remember {
        com.android.purebilibili.core.store.SettingsManager.isDataSaverActive(context)
    }
    
    LaunchedEffect(gridState, isDataSaverActive) {
        // 📉 省流量模式下跳过预加载
        if (isDataSaverActive) return@LaunchedEffect
        
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()  //  只在索引变化时触发
            .collect { lastVisibleIndex ->
                val videos = state.videos
                val preloadStart = (lastVisibleIndex + 1).coerceAtMost(videos.size)
                val preloadEnd = (lastVisibleIndex + 6).coerceAtMost(videos.size)  //  减少预加载数量
                
                if (preloadStart < preloadEnd) {
                    for (i in preloadStart until preloadEnd) {
                        val imageUrl = videos.getOrNull(i)?.pic ?: continue
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(com.android.purebilibili.core.util.FormatUtils.fixImageUrl(imageUrl))
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
    
    //  记录滑动方向用于动画 (true = 向右/上一个分类, false = 向左/下一个分类)
    var swipeDirection by remember { mutableStateOf(true) }
    
    //  [改进] 水平滑动过渡动画状态 - 使用动画实现平滑过渡
    var targetDragOffset by remember { mutableFloatStateOf(0f) }  // 目标偏移量
    var isDragging by remember { mutableStateOf(false) }  // 是否正在拖拽
    
    //  [优化] 使用 spring 动画实现平滑跟手效果
    // 拖拽时使用较高刚度实现即时响应，释放时使用较低刚度实现柔和弹回
    val animatedDragOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetDragOffset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = if (isDragging) 1f else 0.8f,    // 拖拽时无弹性，释放时轻微弹性
            stiffness = if (isDragging) 1500f else 350f    // [优化] 拖拽响应更平滑(1500)，释放更柔和(350)
        ),
        label = "dragOffset"
    )
    
    var isAnimatingTransition by remember { mutableStateOf(false) }  // 是否正在动画过渡
    var transitionDirection by remember { mutableIntStateOf(0) }  // -1=左滑进入, 1=右滑进入, 0=无
    
    //  [新增] 下拉回弹物理动画状态
    val targetPullOffset = if (pullRefreshState.distanceFraction > 0) {
        with(density) { (pullRefreshState.distanceFraction * 130.dp.toPx()).coerceAtMost(250.dp.toPx()) }
    } else 0f
    
    val animatedPullOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetPullOffset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.5f,  // 0.5 = 明显的弹性回弹 (Bouncy)
            stiffness = 300f      // 300 = 中等刚度
        ),
        label = "pull_physics"
    )
    
    //  [修复] 特殊分类列表（有独立页面，不在首页显示内容）
    val specialCategories = listOf(
        HomeCategory.ANIME, 
        HomeCategory.MOVIE, 
        HomeCategory.GAME, 
        HomeCategory.KNOWLEDGE, 
        HomeCategory.TECH
    )
    
    //  水平滑动切换分类的回调
    val switchToPreviousCategory: () -> Unit = remember(displayedTabIndex) {
        {
            swipeDirection = true  // 右滑
            //  [修复] 使用 ViewModel 中的标签页索引
            if (displayedTabIndex > 0) {
                val prevIndex = displayedTabIndex - 1
                val prevCategory = HomeCategory.entries[prevIndex]
                // Update label position and switch category content
                viewModel.updateDisplayedTabIndex(prevIndex)
                // [Modified] Special handling: Only Anime(13) and Movie(181) go to separate pages
                when (prevCategory) {
                    HomeCategory.ANIME -> onBangumiClick(1)
                    HomeCategory.MOVIE -> onBangumiClick(2)
                    else -> viewModel.switchCategory(prevCategory)
                }
            }
        }
    }
    
    val switchToNextCategory: () -> Unit = remember(displayedTabIndex) {
        {
            swipeDirection = false  // 左滑
            //  [修复] 使用 ViewModel 中的标签页索引
            if (displayedTabIndex < HomeCategory.entries.size - 1) {
                val nextIndex = displayedTabIndex + 1
                val nextCategory = HomeCategory.entries[nextIndex]
                // Update label position and switch category content
                viewModel.updateDisplayedTabIndex(nextIndex)
                // [Modified] Special handling: Only Anime(13) and Movie(181) go to separate pages
                when (nextCategory) {
                    HomeCategory.ANIME -> onBangumiClick(1)
                    HomeCategory.MOVIE -> onBangumiClick(2)
                    else -> viewModel.switchCategory(nextCategory)
                }
            }
        }
    }

    val scaffoldContent: @Composable () -> Unit = {
    Scaffold(
        bottomBar = {
            if (!useSideNavigation) {
            //  尝试获取共享过渡作用域
            val sharedTransitionScope = LocalSharedTransitionScope.current
            
            //  [修复] 只在导航到/从视频页时使用 overlay
            // isVideoNavigating 在点击视频时设为 true，动画完成后重置为 false
            val bottomBarModifier = if (sharedTransitionScope != null && isVideoNavigating) {
                with(sharedTransitionScope) {
                    Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                }
            } else {
                Modifier
            }
            
            AnimatedVisibility(
                visible = bottomBarVisible,  //  受状态控制
                modifier = bottomBarModifier,
                enter = slideInVertically(
                    initialOffsetY = { it },  // 从底部滑入
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(250)),
                exit = slideOutVertically(
                    targetOffsetY = { it },   // 向底部滑出
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                if (isBottomBarFloating) {
                    // 悬浮式底栏
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp), // 悬浮距离
                        contentAlignment = Alignment.Center
                    ) {
                        FrostedBottomBar(
                            currentItem = currentNavItem,
                            onItemClick = handleNavItemClick,
                            onHomeDoubleTap = {
                                coroutineScope.launch { gridState.animateScrollToItem(0) }
                            },
                            hazeState = if (isBottomBarBlurEnabled) hazeState else null,
                            isFloating = true,
                            labelMode = bottomBarLabelMode,
                            visibleItems = visibleBottomBarItems,
                            itemColorIndices = bottomBarItemColors,  //  [新增] 传入颜色配置
                            onToggleSidebar = if (windowSizeClass.isExpandedScreen) onToggleNavigationMode else null  // 📱 平板切换
                        )
                    }
                } else {
                    // 贴底式底栏
                    FrostedBottomBar(
                        currentItem = currentNavItem,
                        onItemClick = handleNavItemClick,
                        onHomeDoubleTap = {
                            coroutineScope.launch { gridState.animateScrollToItem(0) }
                        },
                        hazeState = if (isBottomBarBlurEnabled) hazeState else null,
                        isFloating = false,
                        labelMode = bottomBarLabelMode,
                        visibleItems = visibleBottomBarItems,
                        itemColorIndices = bottomBarItemColors,  //  [新增] 传入颜色配置
                        onToggleSidebar = if (windowSizeClass.isExpandedScreen) onToggleNavigationMode else null  // 📱 平板切换
                    )
                }
            }
            }
        },
        //  [新增] JSON 插件过滤提示
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(
                    bottom = when {
                        useSideNavigation -> navBarHeight + 8.dp
                        isBottomBarFloating -> 100.dp
                        else -> 80.dp
                    }
                )
            )
        },
        //  [修复] 禁用 Scaffold 默认的 contentWindowInsets，防止底部出现白色填充
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        //  [重构] 实现真正的毛玻璃效果
        // 外层 Box 包含：1) hazeSource 内容层  2) Header overlay 层
        // Header 在 hazeSource 外部，可以正确模糊内层内容
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // ===== 内容层 (hazeSource) =====
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)  //  使用正确的 Haze API
            ) {
            if (state.isLoading && state.videos.isEmpty() && state.liveRooms.isEmpty()) {
                //  首次加载改为骨架屏
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(
                        top = 0.dp,  //  [修改] Header 作为 item，顶部由 Header 自身处理
                        //  [修复] 动态底部 padding
                        bottom = when {
                            useSideNavigation -> navBarHeight + 8.dp
                            isBottomBarFloating -> 100.dp
                            bottomBarVisible -> 64.dp + navBarHeight + 20.dp
                            else -> navBarHeight + 8.dp
                        },
                        start = 8.dp,
                        end = 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (useSideNavigation) {
                                Modifier.responsiveContentWidth(maxWidth = 1000.dp)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // [新增] 骨架屏状态下的 Header
                    item(span = { GridItemSpan(gridColumns) }) {
                        iOSHomeHeader(
                            scrollOffset = 0f,
                            user = state.user,
                            onAvatarClick = { if (state.user.isLogin) onProfileClick() else onAvatarClick() },
                            onSettingsClick = onSettingsClick,
                            onSearchClick = onSearchClick,
                            categoryIndex = displayedTabIndex,
                            onCategorySelected = { index ->
                                viewModel.updateDisplayedTabIndex(index)
                                val category = HomeCategory.entries[index]
                                when (category) {
                                    HomeCategory.ANIME -> onBangumiClick(1)
                                    HomeCategory.MOVIE -> onBangumiClick(2)
                                    else -> viewModel.switchCategory(category)
                                }
                            },
                            onPartitionClick = onPartitionClick,
                            isScrollingUp = true,
                            hazeState = if (isHeaderBlurEnabled) hazeState else null,
                            isRefreshing = isRefreshing,
                            pullProgress = pullRefreshState.distanceFraction
                        )
                    }

                    // 📱 [平板适配] 根据列数动态生成骨架屏数量
                    items(gridColumns * 4) { index ->
                        VideoCardSkeleton(index = index)
                    }
                }
            } else if (state.error != null && 
                ((state.currentCategory == HomeCategory.LIVE && state.liveRooms.isEmpty()) ||
                 (state.currentCategory != HomeCategory.LIVE && state.videos.isEmpty()))) {
                
                // [修改] 错误状态改为 Grid 布局，包含 Header
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(top = 0.dp), // Header 自带 Padding
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Header Item
                    item(span = { GridItemSpan(gridColumns) }) {
                        iOSHomeHeader(
                            scrollOffset = 0f,
                            user = state.user,
                            onAvatarClick = { if (state.user.isLogin) onProfileClick() else onAvatarClick() },
                            onSettingsClick = onSettingsClick,
                            onSearchClick = onSearchClick,
                            categoryIndex = displayedTabIndex,
                            onCategorySelected = { index ->
                                viewModel.updateDisplayedTabIndex(index)
                                val category = HomeCategory.entries[index]
                                when (category) {
                                    HomeCategory.ANIME -> onBangumiClick(1)
                                    HomeCategory.MOVIE -> onBangumiClick(2)
                                    else -> viewModel.switchCategory(category)
                                }
                            },
                            onPartitionClick = onPartitionClick,
                            isScrollingUp = true,
                            hazeState = if (isHeaderBlurEnabled) hazeState else null,
                            isRefreshing = isRefreshing,
                            pullProgress = pullRefreshState.distanceFraction
                        )
                    }

                    // 2. Error Message Item
                    item(span = { GridItemSpan(gridColumns) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp), // 给定高度确保居中
                            contentAlignment = Alignment.Center
                        ) {
                            ModernErrorState(
                                message = state.error ?: "未知错误",
                                onRetry = { viewModel.refresh() }
                            )
                        }
                    }
                }
            } else {
                //  [性能优化] 移除 AnimatedContent 包裹，减少分类切换时的重组开销
                // 原：AnimatedContent 对整个 Grid 做动画，成本很高
                // 新：直接渲染，分类切换瞬间完成
                val targetCategory = state.currentCategory
                
                //  使用 PullToRefreshBox 包裹内容
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    state = pullRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    //  iOS 风格下拉刷新指示器
                    indicator = {
                        iOSRefreshIndicator(
                            state = pullRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)  //  [修复] 确保指示器水平居中
                                .fillMaxWidth()
                                .padding(top = statusBarHeight + 110.dp)  //  [优化] 刷新提示位于 Header(约110dp) 下方
                        )
                    }
                ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(
                        top = listTopPadding,  //  [优化] 确保卡片圆角完全显示
                        //  [修复] 底栏隐藏时减少底部 padding，避免白色填充
                        bottom = when {
                            useSideNavigation -> navBarHeight + 8.dp
                            isBottomBarFloating -> 100.dp
                            bottomBarVisible -> 64.dp + navBarHeight + 20.dp  // 底栏可见：底栏高度 + 导航栏 + 间距
                            else -> navBarHeight + 8.dp  // 底栏隐藏：只需导航栏安全区 + 少量间距
                        },
                        start = 8.dp, 
                        end = 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (useSideNavigation) {
                                Modifier.responsiveContentWidth(maxWidth = 1000.dp)
                            } else {
                                Modifier
                            }
                        )
                        //  [修复] 底栏隐藏时不需要额外的导航栏 padding
                        .padding(bottom = if (useSideNavigation || isBottomBarFloating || !bottomBarVisible) 0.dp else navBarHeight)
                        //  [改进] 水平滑动手势 + 平滑动画偏移
                        .graphicsLayer {
                            // 使用动画值实现平滑过渡
                            translationX = animatedDragOffset
                            

                            
                            //  使用 AnimateAsState 手动管理偏移，实现物理回弹
                            //  注意：由于是在 graphicsLayer 内部，我们需要使用 state 提升到外部或在此处直接使用 value (但 graphicsLayer 是 lambda)
                            //  更优解：将 animatedPullOffset 定义在 graphicsLayer 外部
                            translationY = animatedPullOffset
                        }
                        .pointerInput(targetCategory) {
                            detectHorizontalDragGestures(
                                onDragStart = { 
                                    //  开始拖拽
                                    isDragging = true
                                    isAnimatingTransition = false
                                    transitionDirection = 0
                                },
                                onDragEnd = {
                                    //  释放手指，开启动画
                                    isDragging = false
                                    val threshold = 100f
                                    val currentOffset = targetDragOffset
                                    
                                    when {
                                        currentOffset > threshold && displayedTabIndex > 0 -> {
                                            // 右滑：切换到上一个分类
                                            transitionDirection = 1
                                            isAnimatingTransition = true
                                            switchToPreviousCategory()
                                        }
                                        currentOffset < -threshold && displayedTabIndex < HomeCategory.entries.size - 1 -> {
                                            // 左滑：切换到下一个分类
                                            transitionDirection = -1
                                            isAnimatingTransition = true
                                            switchToNextCategory()
                                        }
                                        else -> {
                                            // 未达阈值，不切换
                                            transitionDirection = 0
                                        }
                                    }
                                    //  使用动画平滑弹回原位
                                    targetDragOffset = 0f
                                },
                                onDragCancel = { 
                                    isDragging = false
                                    targetDragOffset = 0f
                                    transitionDirection = 0
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    //  实时更新目标偏移量（带阻尼效果）
                                    val newOffset = targetDragOffset + dragAmount
                                    val dampedOffset = when {
                                        displayedTabIndex == 0 && newOffset > 0 -> 
                                            newOffset * 0.3f  // 第一个分类，右滑阻尼
                                        displayedTabIndex == HomeCategory.entries.size - 1 && newOffset < 0 ->
                                            newOffset * 0.3f  // 最后一个分类，左滑阻尼
                                        else -> newOffset
                                    }
                                    targetDragOffset = dampedOffset.coerceIn(-size.width * 0.5f, size.width * 0.5f)
                                }
                            )
                        }
                ) {
                    if (targetCategory == HomeCategory.LIVE) {
                        // 🔴 [改进] 合并显示关注和热门直播（不分开切换）
                        
                        // 1. 关注的主播直播（如果有）
                        if (state.followedLiveRooms.isNotEmpty()) {
                            item(span = { GridItemSpan(gridColumns) }) {
                                Text(
                                    text = "关注",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                            
                            itemsIndexed(
                                items = state.followedLiveRooms,
                                key = { _, room -> "followed_${room.roomid}" },
                                contentType = { _, _ -> "live_room" }
                            ) { index, room ->
                                LiveRoomCard(
                                    room = room,
                                    index = index,
                                    onClick = { onLiveClick(room.roomid, room.title, room.uname) } 
                                )
                            }
                        }
                        
                        // 2. 热门直播
                        if (state.liveRooms.isNotEmpty()) {
                            item(span = { GridItemSpan(gridColumns) }) {
                                Text(
                                    text = "热门",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                            
                            itemsIndexed(
                                items = state.liveRooms,
                                key = { _, room -> "popular_${room.roomid}" },
                                contentType = { _, _ -> "live_room" }
                            ) { index, room ->
                                LiveRoomCard(
                                    room = room,
                                    index = index,
                                    onClick = { onLiveClick(room.roomid, room.title, room.uname) } 
                                )
                            }
                        }
                    } else {
                        if (state.videos.isNotEmpty()) {
                            itemsIndexed(
                                items = state.videos,
                                key = { _, video -> video.bvid },
                                contentType = { _, _ -> "video" }
                            ) { index, video ->
                                // �️ [新增] 检查是否正在消散
                                val isDissolving = video.bvid in state.dissolvingVideos
                                
                                //  使用可消散卡片容器包装
                                DissolvableVideoCard(
                                    isDissolving = isDissolving,
                                    onDissolveComplete = { viewModel.completeVideoDissolve(video.bvid) },
                                    cardId = video.bvid,  //  用于识别卡片，触发邻近卡片抖动
                                    modifier = Modifier
                                        .jiggleOnDissolve(video.bvid)  // 📳 iOS 风格抖动
                                ) {
                                    //  根据展示模式选择卡片样式 (0=网格, 1=故事卡片)
                                    when (displayMode) {
                                        1 -> {
                                            //  故事卡片 (Apple TV+ 风格)
                                            StoryVideoCard(
                                                video = video,
                                                index = index,  //  动画索引
                                                animationEnabled = cardAnimationEnabled,  //  动画开关
                                                transitionEnabled = cardTransitionEnabled, //  过渡动画开关
                                                onDismiss = { viewModel.startVideoDissolve(video.bvid) },
                                                onClick = { bvid, cid -> wrappedOnVideoClick(bvid, cid, video.pic) }
                                            )
                                        }
                                        else -> {
                                            //  默认网格卡片
                                            ElegantVideoCard(
                                                video = video,
                                                index = index,
                                                isFollowing = video.owner.mid in state.followingMids,  //  判断是否已关注
                                                animationEnabled = cardAnimationEnabled,    //  进场动画开关
                                                transitionEnabled = cardTransitionEnabled,  //  过渡动画开关
                                                isDataSaverActive = isDataSaverActive,      // 🚀 [性能优化] 从列表级别传入
                                                onDismiss = { viewModel.startVideoDissolve(video.bvid) },
                                                onClick = { bvid, cid -> wrappedOnVideoClick(bvid, cid, video.pic) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!state.isLoading && state.error == null) {
                        item(span = { GridItemSpan(gridColumns) }) {
                            LaunchedEffect(Unit) {
                                viewModel.loadMore()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.isLoading) {
                                    CupertinoActivityIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                    
                    item(span = { GridItemSpan(gridColumns) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(20.dp))
                    }
                }
            } // 关闭 PullToRefreshBox
            //  ===== Header Overlay (毛玻璃效果) =====
            //  Header 现在在 hazeSource 外部，可以正确模糊内层内容
            val isSkeletonState = state.isLoading && state.videos.isEmpty() && state.liveRooms.isEmpty()
            val isErrorState = state.error != null && 
                ((state.currentCategory == HomeCategory.LIVE && state.liveRooms.isEmpty()) ||
                 (state.currentCategory != HomeCategory.LIVE && state.videos.isEmpty()))

            if (!isSkeletonState && !isErrorState) {
                iOSHomeHeader(
                    scrollOffset = scrollOffset,
                    user = state.user,
                    onAvatarClick = { if (state.user.isLogin) onProfileClick() else onAvatarClick() },
                    onSettingsClick = onSettingsClick,
                    onSearchClick = onSearchClick,
                    categoryIndex = displayedTabIndex,
                    onCategorySelected = { index ->
                        viewModel.updateDisplayedTabIndex(index)
                        val category = HomeCategory.entries[index]
                        when (category) {
                            HomeCategory.ANIME -> onBangumiClick(1)
                            HomeCategory.MOVIE -> onBangumiClick(2)
                            // All others (Game, Knowledge, Tech, etc.) are handled by state switch
                            else -> viewModel.switchCategory(category)
                        }
                    },
                    onPartitionClick = onPartitionClick,
                    isScrollingUp = isScrollingUp,
                    hazeState = if (isHeaderBlurEnabled) hazeState else null,
                    onStatusBarDoubleTap = {
                        coroutineScope.launch {
                            gridState.animateScrollToItem(0)
                        }
                    },
                    isRefreshing = isRefreshing,
                    pullProgress = pullRefreshState.distanceFraction
                )
            }
            }  // 关闭 else 分支
        }  // 关闭 hazeSource Box
        
        //  ===== Header Overlay (毛玻璃效果) =====
        //  Header 现在在外层 Box 内、hazeSource 外部，可以正确模糊内层内容
        val isSkeletonState = state.isLoading && state.videos.isEmpty() && state.liveRooms.isEmpty()
        val isErrorState = state.error != null && 
            ((state.currentCategory == HomeCategory.LIVE && state.liveRooms.isEmpty()) ||
             (state.currentCategory != HomeCategory.LIVE && state.videos.isEmpty()))

        if (!isSkeletonState && !isErrorState) {
            iOSHomeHeader(
                scrollOffset = scrollOffset,
                user = state.user,
                onAvatarClick = { if (state.user.isLogin) onProfileClick() else onAvatarClick() },
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick,
                categoryIndex = displayedTabIndex,
                onCategorySelected = { index ->
                    viewModel.updateDisplayedTabIndex(index)
                    val category = HomeCategory.entries[index]
                    when (category) {
                        HomeCategory.ANIME -> onBangumiClick(1)
                        HomeCategory.MOVIE -> onBangumiClick(2)
                        else -> viewModel.switchCategory(category)
                    }
                },
                onPartitionClick = onPartitionClick,
                isScrollingUp = isScrollingUp,
                hazeState = if (isHeaderBlurEnabled) hazeState else null,
                onStatusBarDoubleTap = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                isRefreshing = isRefreshing,
                pullProgress = pullRefreshState.distanceFraction
            )
        }
    }  // 关闭外层 Box
    }  // 关闭 Scaffold content
    }  //  关闭 scaffoldContent lambda
    // 📱 [平板适配] 导航模式切换动画
    // 始终使用 Row 布局，通过动画控制侧边栏的显示/隐藏
    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = useSideNavigation,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(250, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            FrostedSideBar(
                currentItem = currentNavItem,
                onItemClick = handleNavItemClick,
                onHomeDoubleTap = {
                    coroutineScope.launch { gridState.animateScrollToItem(0) }
                },
                hazeState = if (isBottomBarBlurEnabled) hazeState else null,
                visibleItems = visibleBottomBarItems,
                itemColorIndices = bottomBarItemColors,
                onToggleSidebar = onToggleNavigationMode
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            scaffoldContent()
        }
    }
}
