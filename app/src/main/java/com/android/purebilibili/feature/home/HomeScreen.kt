// 文件路径: feature/home/HomeScreen.kt
package com.android.purebilibili.feature.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.feature.settings.GITHUB_URL

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (String, Long, String) -> Unit,
    onAvatarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()

    // 滚动状态检测
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 20
        }
    }

    // 🔥 状态栏优化：基于背景亮度智能适配（优化防闪烁）
    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface

    // 使用 remember 缓存亮度判断，避免频繁重组
    val isLightBackground = remember(backgroundColor) {
        backgroundColor.luminance() > 0.5f
    }

    // 使用 DisposableEffect 确保只在必要时更新
    if (!view.isInEditMode) {
        DisposableEffect(isLightBackground) {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            // 核心逻辑：根据背景亮度自动选择状态栏图标颜色
            insetsController.isAppearanceLightStatusBars = isLightBackground

            // 保持透明背景以支持沉浸式体验
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            onDispose { }
        }
    }

    // 计算内边距
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val navBarHeight = WindowInsets.navigationBars.getBottom(density).let { with(density) { it.toDp() } }

    // 🔥 优化后的内容区域间距
    // TopBar 高度 = 状态栏 + 头像行(48dp) + 间距(8dp) + 搜索栏(48dp) + 底部间距(8dp)
    val topContentPadding = statusBarHeight + 48.dp + 8.dp + 48.dp + 8.dp + 12.dp
    val bottomContentPadding = navBarHeight + 16.dp

    // 显示模式
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var displayMode by remember { mutableIntStateOf(prefs.getInt("display_mode", 0)) }
    var showWelcomeDialog by remember { mutableStateOf(false) }

    SideEffect { displayMode = prefs.getInt("display_mode", 0) }

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("is_first_run", true)) showWelcomeDialog = true
    }

    // 自动加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4 && !state.isLoading && !isRefreshing
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    // 下拉刷新逻辑
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) { viewModel.refresh() }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) pullRefreshState.startRefresh() else pullRefreshState.endRefresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            // 底层：视频列表
            if (state.isLoading && state.videos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliPink)
                }
            } else if (state.error != null && state.videos.isEmpty()) {
                ErrorState(state.error!!) { viewModel.refresh() }
            } else {
                val columnsCount = if (displayMode == 1) 1 else 2
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topContentPadding,
                        bottom = bottomContentPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.videos) { index, video ->
                        if (displayMode == 1) {
                            ImmersiveVideoCard(video, index) { bvid, cid ->
                                onVideoClick(bvid, cid, video.pic)
                            }
                        } else {
                            ElegantVideoCard(video, index) { bvid, cid ->
                                onVideoClick(bvid, cid, video.pic)
                            }
                        }
                    }
                    if (state.videos.isNotEmpty() && state.isLoading) {
                        item(span = { GridItemSpan(columnsCount) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.Gray,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }

            // 下拉刷新指示器
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = BiliPink,
                contentColor = Color.White
            )

            // 顶层：集成搜索的 TopBar
            HomeTopBar(
                user = state.user,
                isScrolled = isScrolled,
                onAvatarClick = {
                    if (state.user.isLogin) onProfileClick() else onAvatarClick()
                },
                onSettingsClick = onSettingsClick,
                onSearchClick = onSearchClick
            )
        }
    }

    if (showWelcomeDialog) {
        WelcomeDialog(GITHUB_URL) {
            prefs.edit().putBoolean("is_first_run", false).apply()
            showWelcomeDialog = false
        }
    }
}