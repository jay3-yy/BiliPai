package com.android.purebilibili.feature.home

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.feature.settings.GITHUB_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (String, Long) -> Unit,
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

    // 动态计算顶部 Padding
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val topContentPadding = statusBarHeight + 56.dp + 12.dp // Header高度 + 间距

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var displayMode by remember { mutableIntStateOf(prefs.getInt("display_mode", 0)) }
    var showWelcomeDialog by remember { mutableStateOf(false) }

    SideEffect { displayMode = prefs.getInt("display_mode", 0) }

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("is_first_run", true)) showWelcomeDialog = true
    }

    // 无限加载逻辑
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4 && !state.isLoading && !isRefreshing
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) { viewModel.refresh() }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) pullRefreshState.startRefresh() else pullRefreshState.endRefresh()
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FA), // 使用更高级的淡灰底色
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
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
                        start = 12.dp, end = 12.dp, // 略微收窄边距，让卡片更大
                        top = topContentPadding,
                        bottom = 100.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), // 间距调小一点点，更紧凑
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.videos) { index, video ->
                        if (displayMode == 1) ImmersiveVideoCard(video, index, onVideoClick)
                        else ElegantVideoCard(video, index, onVideoClick)
                    }
                    if (state.videos.isNotEmpty() && state.isLoading) {
                        item(span = { GridItemSpan(columnsCount) }) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Gray, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topContentPadding - 20.dp),
                containerColor = BiliPink,
                contentColor = Color.White
            )

            FloatingHomeHeader(
                user = state.user,
                onAvatarClick = { if (state.user.isLogin) onProfileClick() else onAvatarClick() },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick
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