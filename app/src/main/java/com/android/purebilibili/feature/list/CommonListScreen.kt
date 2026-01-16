package com.android.purebilibili.feature.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.VideoGridItemSkeleton
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import com.android.purebilibili.core.util.rememberAdaptiveGridColumns
import com.android.purebilibili.core.util.rememberResponsiveSpacing
import com.android.purebilibili.core.util.rememberResponsiveValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonListScreen(
    viewModel: BaseListViewModel,
    onBack: () -> Unit,
    onVideoClick: (String, Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    
    // 📱 响应式布局参数
    // Fix: 手机端(Compact)使用较小的最小宽度以保证2列显示 (360dp / 170dp = 2.1 -> 2列)
    // 平板端(Expanded)使用较大的最小宽度以避免卡片过小
    val minColWidth = rememberResponsiveValue(compact = 170.dp, medium = 170.dp, expanded = 240.dp)
    val columns = rememberAdaptiveGridColumns(minColumnWidth = minColWidth)
    val spacing = rememberResponsiveSpacing()
    
    //  [修复] 分页支持：收藏 + 历史记录
    val favoriteViewModel = viewModel as? FavoriteViewModel
    val historyViewModel = viewModel as? HistoryViewModel
    
    // 收藏分页状态
    val isLoadingMoreFav by favoriteViewModel?.isLoadingMoreState?.collectAsState() 
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val hasMoreFav by favoriteViewModel?.hasMoreState?.collectAsState() 
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    //  历史记录分页状态
    val isLoadingMoreHis by historyViewModel?.isLoadingMoreState?.collectAsState() 
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val hasMoreHis by historyViewModel?.hasMoreState?.collectAsState() 
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    //  统一分页状态
    val isLoadingMore = isLoadingMoreFav || isLoadingMoreHis
    val hasMore = hasMoreFav || hasMoreHis
    
    //  使用 derivedStateOf 来高效检测滚动位置
    val shouldLoadMore = androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4  // 提前4个item开始加载
        }
    }
    
    //  滚动到底部时加载更多
    LaunchedEffect(shouldLoadMore.value, hasMore, isLoadingMore) {
        if (shouldLoadMore.value && hasMore && !isLoadingMore) {
            favoriteViewModel?.loadMore()
            historyViewModel?.loadMore()  //  历史记录加载更多
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.isLoading) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(columns * 4) { VideoGridItemSkeleton() } // 根据列数生成骨架屏数量
                }
            } else if (state.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = state.error ?: "未知错误", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadData() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("重试")
                    }
                }
            } else if (state.items.isEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text("暂无数据", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.items) { index, video ->
                        ElegantVideoCard(
                            video = video,
                            index = index,
                            onClick = { bvid, cid -> onVideoClick(bvid, cid) }
                        )
                    }
                    
                    //  加载更多指示器
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CupertinoActivityIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}