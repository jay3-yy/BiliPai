// 文件路径: feature/bangumi/BangumiScreen.kt
package com.android.purebilibili.feature.bangumi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
//  已改用 MaterialTheme.colorScheme.primary
import com.android.purebilibili.core.theme.iOSYellow
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.BangumiFilter
import com.android.purebilibili.data.model.response.BangumiItem
import com.android.purebilibili.data.model.response.BangumiSearchItem
import com.android.purebilibili.data.model.response.BangumiType

/**
 * 番剧主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiScreen(
    onBack: () -> Unit,
    onBangumiClick: (Long) -> Unit,  // 点击番剧 -> seasonId
    initialType: Int = 1,  // 初始类型：1=番剧 2=电影 等
    viewModel: BangumiViewModel = viewModel()
) {
    val displayMode by viewModel.displayMode.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val listState by viewModel.listState.collectAsState()
    val timelineState by viewModel.timelineState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val myFollowState by viewModel.myFollowState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchKeyword by viewModel.searchKeyword.collectAsState()
    
    // 搜索状态
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 筛选器展开状态
    var showFilter by remember { mutableStateOf(false) }
    
    // 初始类型切换
    LaunchedEffect(initialType) {
        if (initialType != 1) {
            viewModel.selectType(initialType)
        }
    }
    
    // 番剧类型列表
    val types = listOf(
        BangumiType.ANIME,
        BangumiType.GUOCHUANG,
        BangumiType.MOVIE,
        BangumiType.TV_SHOW,
        BangumiType.DOCUMENTARY,
        BangumiType.VARIETY
    )
    
    //  [修复] 设置导航栏透明，确保底部手势栏沉浸式效果
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val originalNavBarColor = window?.navigationBarColor ?: android.graphics.Color.TRANSPARENT
        
        if (window != null) {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        onDispose {
            if (window != null) {
                window.navigationBarColor = originalNavBarColor
            }
        }
    }
    
    Scaffold(
        topBar = {
            if (showSearchBar) {
                // 搜索模式顶栏
                BangumiSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (it.isNotBlank()) {
                            viewModel.searchBangumi(it)
                            keyboardController?.hide()
                        }
                    },
                    onBack = {
                        showSearchBar = false
                        searchQuery = ""
                        viewModel.clearSearch()
                    }
                )
            } else {
                // 默认顶栏
                TopAppBar(
                    title = { Text("番剧影视") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 搜索按钮
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(CupertinoIcons.Default.MagnifyingGlass, contentDescription = "搜索")
                        }
                        // 筛选按钮
                        IconButton(onClick = { showFilter = !showFilter }) {
                            Icon(
                                CupertinoIcons.Default.ListBullet,
                                contentDescription = "筛选",
                                tint = if (filter != BangumiFilter()) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        //  [修复] 禁用 Scaffold 默认的 WindowInsets 消耗，让内容区域自行处理
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .responsiveContentWidth()
                //  [修复] 移除这里的底部内边距，让内容区域自己处理（如 LazyVerticalGrid 的 contentPadding）
        ) {
            // 模式切换 Tabs (时间表/索引/追番)
            BangumiModeTabs(
                currentMode = displayMode,
                onModeChange = { viewModel.setDisplayMode(it) }
            )
            
            // 类型选择 Tabs (仅列表模式显示)
            if (displayMode == BangumiDisplayMode.LIST) {
                ScrollableTabRow(
                    selectedTabIndex = types.indexOfFirst { it.value == selectedType }.coerceAtLeast(0),
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    types.forEach { type ->
                        Tab(
                            selected = type.value == selectedType,
                            onClick = { viewModel.selectType(type.value) },
                            text = {
                                Text(
                                    text = type.label,
                                    fontWeight = if (type.value == selectedType) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                
                // 筛选器
                if (showFilter) {
                    BangumiFilterPanel(
                        filter = filter,
                        onFilterChange = { viewModel.updateFilter(it) },
                        onDismiss = { showFilter = false }
                    )
                }
            }
            
            // 内容区域
            when (displayMode) {
                BangumiDisplayMode.LIST -> {
                    BangumiListContent(
                        listState = listState,
                        onRetry = { viewModel.loadBangumiList() },
                        onLoadMore = { viewModel.loadMore() },
                        onItemClick = onBangumiClick
                    )
                }
                BangumiDisplayMode.TIMELINE -> {
                    BangumiTimelineContent(
                        timelineState = timelineState,
                        onRetry = { viewModel.loadTimeline() },
                        onBangumiClick = onBangumiClick
                    )
                }
                BangumiDisplayMode.MY_FOLLOW -> {
                    MyBangumiContent(
                        myFollowState = myFollowState,
                        onRetry = { viewModel.loadMyFollowBangumi() },
                        onLoadMore = { viewModel.loadMoreMyFollow() },
                        onBangumiClick = onBangumiClick
                    )
                }
                BangumiDisplayMode.SEARCH -> {
                    BangumiSearchContent(
                        searchState = searchState,
                        onRetry = { viewModel.searchBangumi(searchKeyword) },
                        onLoadMore = { viewModel.loadMoreSearchResults() },
                        onItemClick = onBangumiClick
                    )
                }
            }
        }
    }
}

/**
 * 模式切换 Tabs
 */
@Composable
private fun BangumiModeTabs(
    currentMode: BangumiDisplayMode,
    onModeChange: (BangumiDisplayMode) -> Unit
) {
    val modes = listOf(
        BangumiDisplayMode.LIST to "索引",
        BangumiDisplayMode.TIMELINE to "时间表",
        BangumiDisplayMode.MY_FOLLOW to "我的追番"
    )
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(modes) { (mode, label) ->
            val isSelected = currentMode == mode
            Surface(
                onClick = { onModeChange(mode) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 搜索顶栏
 */
@Composable
private fun BangumiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().responsiveContentWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(CupertinoIcons.Default.ChevronBackward, contentDescription = "返回")
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // 搜索框
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        CupertinoIcons.Default.MagnifyingGlass,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        "搜索番剧名称、声优...",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                            fontSize = 15.sp
                                        )
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                CupertinoIcons.Default.XmarkCircle,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                TextButton(
                    onClick = { onSearch(query) },
                    enabled = query.isNotEmpty()
                ) {
                    Text(
                        "搜索",
                        color = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                    )
                }
            }
        }
    }
}

/**
 * 筛选器面板
 */
@Composable
private fun BangumiFilterPanel(
    filter: BangumiFilter,
    onFilterChange: (BangumiFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var showOrderMenu by remember { mutableStateOf(false) }
    var showAreaMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showYearMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 排序
                FilterChip(
                    label = BangumiFilter.ORDER_OPTIONS.find { it.first == filter.order }?.second ?: "排序",
                    isActive = filter.order != 2,
                    onClick = { showOrderMenu = true },
                    expanded = showOrderMenu,
                    options = BangumiFilter.ORDER_OPTIONS.map { it.second },
                    onOptionSelected = { index ->
                        onFilterChange(filter.copy(order = BangumiFilter.ORDER_OPTIONS[index].first))
                        showOrderMenu = false
                    },
                    onDismiss = { showOrderMenu = false }
                )
                
                // 地区
                FilterChip(
                    label = BangumiFilter.AREA_OPTIONS.find { it.first == filter.area }?.second ?: "地区",
                    isActive = filter.area != -1,
                    onClick = { showAreaMenu = true },
                    expanded = showAreaMenu,
                    options = BangumiFilter.AREA_OPTIONS.map { it.second },
                    onOptionSelected = { index ->
                        onFilterChange(filter.copy(area = BangumiFilter.AREA_OPTIONS[index].first))
                        showAreaMenu = false
                    },
                    onDismiss = { showAreaMenu = false }
                )
                
                // 状态
                FilterChip(
                    label = BangumiFilter.STATUS_OPTIONS.find { it.first == filter.isFinish }?.second ?: "状态",
                    isActive = filter.isFinish != -1,
                    onClick = { showStatusMenu = true },
                    expanded = showStatusMenu,
                    options = BangumiFilter.STATUS_OPTIONS.map { it.second },
                    onOptionSelected = { index ->
                        onFilterChange(filter.copy(isFinish = BangumiFilter.STATUS_OPTIONS[index].first))
                        showStatusMenu = false
                    },
                    onDismiss = { showStatusMenu = false }
                )
                
                // 年份
                FilterChip(
                    label = BangumiFilter.YEAR_OPTIONS.find { it.first == filter.year }?.second ?: "年份",
                    isActive = filter.year != "-1",
                    onClick = { showYearMenu = true },
                    expanded = showYearMenu,
                    options = BangumiFilter.YEAR_OPTIONS.map { it.second },
                    onOptionSelected = { index ->
                        onFilterChange(filter.copy(year = BangumiFilter.YEAR_OPTIONS[index].first))
                        showYearMenu = false
                    },
                    onDismiss = { showYearMenu = false }
                )
            }
            
            // 重置按钮
            if (filter != BangumiFilter()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onFilterChange(BangumiFilter()) }
                ) {
                    Text("重置筛选", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    expanded: Boolean,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box {
        Surface(
            onClick = onClick,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    CupertinoIcons.Default.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    onClick = { onOptionSelected(index) }
                )
            }
        }
    }
}

/**
 * 番剧列表内容
 */
@Composable
private fun BangumiListContent(
    listState: BangumiListState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (Long) -> Unit
) {
    when (listState) {
        is BangumiListState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is BangumiListState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = listState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
        is BangumiListState.Success -> {
            BangumiGrid(
                items = listState.items,
                hasMore = listState.hasMore,
                onLoadMore = onLoadMore,
                onItemClick = { onItemClick(it.seasonId) }
            )
        }
    }
}

/**
 * 番剧网格列表
 */
@Composable
private fun BangumiGrid(
    items: List<BangumiItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onItemClick: (BangumiItem) -> Unit
) {
    val gridState = rememberLazyGridState()
    
    // 加载更多检测
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= layoutInfo.totalItemsCount - 6
        }.collect { shouldLoad ->
            if (shouldLoad && hasMore) {
                onLoadMore()
            }
        }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            BangumiCard(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
        
        // 加载更多指示器
        if (hasMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

/**
 * 番剧卡片
 */
@Composable
private fun BangumiCard(
    item: BangumiItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)  // 3:4 比例
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(item.cover),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )
            
            // 角标（会员专享等）
            if (item.badge.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    color = when {
                        item.badge.contains("会员") -> MaterialTheme.colorScheme.primary
                        item.badge.contains("独家") -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            
            // 底部信息
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                // 评分
                if (item.score.isNotEmpty() && item.score != "0") {
                    Text(
                        text = item.score,
                        color = iOSYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 更新状态
                item.newEp?.indexShow?.let { indexShow ->
                    Text(
                        text = indexShow,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // 标题
        Text(
            text = item.title,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

/**
 * 番剧搜索结果内容
 */
@Composable
private fun BangumiSearchContent(
    searchState: BangumiSearchState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (Long) -> Unit
) {
    when (searchState) {
        is BangumiSearchState.Idle -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "输入关键词搜索番剧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is BangumiSearchState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is BangumiSearchState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = searchState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
        is BangumiSearchState.Success -> {
            if (searchState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到相关番剧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BangumiSearchGrid(
                    items = searchState.items,
                    hasMore = searchState.hasMore,
                    onLoadMore = onLoadMore,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
private fun BangumiSearchGrid(
    items: List<BangumiSearchItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onItemClick: (Long) -> Unit
) {
    val gridState = rememberLazyGridState()
    
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= layoutInfo.totalItemsCount - 4
        }.collect { shouldLoad ->
            if (shouldLoad && hasMore) {
                onLoadMore()
            }
        }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = items,
            key = { it.seasonId }
        ) { item ->
            BangumiSearchCard(
                item = item,
                onClick = { onItemClick(item.seasonId) }
            )
        }
        
        if (hasMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun BangumiSearchCard(
    item: BangumiSearchItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(item.cover),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )
            
            // 角标
            item.badges?.firstOrNull()?.let { badge ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge.text,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                // 评分
                item.mediaScore?.let { score ->
                    if (score.score > 0) {
                        Text(
                            text = String.format("%.1f", score.score),
                            color = iOSYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // 集数
                if (item.indexShow.isNotEmpty()) {
                    Text(
                        text = item.indexShow,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // 标题 (移除高亮标签)
        Text(
            text = item.title.replace(Regex("<[^>]+>"), ""),
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
