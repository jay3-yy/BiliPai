// 文件路径: feature/search/SearchScreen.kt
package com.android.purebilibili.feature.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.feature.home.VideoGridItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    userFace: String = "", // 🔥 新增：从外部传入头像 URL
    onBack: () -> Unit,
    onVideoClick: (String, Long) -> Unit,
    onAvatarClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 1. 滚动状态监听
    val historyListState = rememberLazyListState()
    val resultGridState = rememberLazyGridState()

    // 2. 核心：计算是否滚动 (决定顶部栏背景)
    val isScrolled by remember(state.showResults) {
        derivedStateOf {
            if (state.showResults) {
                resultGridState.firstVisibleItemIndex > 0 || resultGridState.firstVisibleItemScrollOffset > 10
            } else {
                historyListState.firstVisibleItemIndex > 0 || historyListState.firstVisibleItemScrollOffset > 10
            }
        }
    }

    // 3. 顶部栏颜色动画 (透明 -> Surface)
    val topBarContainerColor by animateColorAsState(
        targetValue = if (isScrolled) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "TopBarBg"
    )

    // 4. 顶部避让高度 (状态栏 + 顶部栏高度)
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val topBarHeight = 56.dp
    val contentTopPadding = statusBarHeight + topBarHeight

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // 手动处理 Insets
        containerColor = MaterialTheme.colorScheme.background,
        // 🔥 核心改变：搜索栏放到底部
        bottomBar = {
            BottomSearchBar(
                query = state.query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = {
                    viewModel.search(it)
                    keyboardController?.hide()
                },
                onClearQuery = { viewModel.onQueryChange("") }
            )
        }
    ) { padding -> // padding 包含了 bottomBar 的高度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()) // 底部避让搜索栏
        ) {
            // --- 列表内容层 ---
            if (state.showResults) {
                if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = BiliPink)
                } else if (state.error != null) {
                    Text(
                        text = state.error ?: "未知错误",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = resultGridState,
                        // 顶部避让 TopBar，底部留白防止遮挡
                        contentPadding = PaddingValues(top = contentTopPadding + 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.searchResults) { index, video ->
                            VideoGridItem(video, index, onVideoClick)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = historyListState,
                    contentPadding = PaddingValues(top = contentTopPadding + 16.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    if (state.hotList.isNotEmpty()) {
                        item {
                            Text("大家都在搜", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                state.hotList.forEach { hotItem ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.clickable { viewModel.search(hotItem.keyword); keyboardController?.hide() }
                                    ) {
                                        Text(hotItem.show_name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    if (state.historyList.isNotEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("历史记录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                TextButton(onClick = { viewModel.clearHistory() }) { Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                            }
                        }
                        items(state.historyList) { history ->
                            HistoryItem(history, { viewModel.search(history.keyword); keyboardController?.hide() }, { viewModel.deleteHistory(history) })
                        }
                    }
                }
            }

            // --- 顶部导航层 (Back + Avatar) ---
            // 放置在最上层，实现沉浸遮挡
            SimpleTopBar(
                userFace = userFace,
                containerColor = topBarContainerColor,
                onBack = onBack,
                onAvatarClick = onAvatarClick,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// 🔥 新设计：顶部简易栏 (只有返回和头像)
@Composable
fun SimpleTopBar(
    userFace: String,
    containerColor: Color,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor) // 动态背景色
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：返回按钮
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }

            // 右侧：头像
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.3f), CircleShape)
                    .clickable { onAvatarClick() }
            ) {
                if (userFace.isNotEmpty()) {
                    AsyncImage(
                        model = userFace,
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search, // 没头像显示一个默认图标
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// 🔥 新设计：底部悬浮搜索栏
@Composable
fun BottomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime) // 🔥 关键：随键盘顶起
            .windowInsetsPadding(WindowInsets.navigationBars) // 避让底部导航条
            .shadow(16.dp, spotColor = Color.Black.copy(0.1f)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, end = 12.dp)
                    .size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(BiliPink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索视频、UP主...",
                                style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), fontSize = 16.sp)
                            )
                        }
                        inner()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            } else {
                // 如果为空，可以显示一个占位或者什么都不显示
                Spacer(modifier = Modifier.width(12.dp))
            }

            // 搜索按钮 (胶囊型)
            if (query.isNotEmpty()) {
                Button(
                    onClick = { onSearch(query) },
                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .height(36.dp)
                ) {
                    Text("搜索", fontSize = 14.sp)
                }
            }
        }
    }
}

// HistoryItem 保持不变...
@Composable
fun HistoryItem(
    history: SearchHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = history.keyword, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.size(16.dp))
        }
    }
    Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
}