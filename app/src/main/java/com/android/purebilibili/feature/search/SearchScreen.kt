package com.android.purebilibili.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.feature.home.VideoGridItem

// 🔥 确保导入了正确的 SearchViewModel
// 如果你的 SearchViewModel 在 com.android.purebilibili.feature.search 包下，上面已经声明了 package，不需要额外 import
// 如果报错依然存在，请检查 SearchViewModel.kt 的 package 声明是否也是 com.android.purebilibili.feature.search

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    // 🔥 这里的 SearchViewModel 引用是报错的核心
    // 如果 AS 提示红色，请尝试按 Alt+Enter 导入，或者检查文件名拼写
    viewModel: SearchViewModel = viewModel(),
    onBack: () -> Unit,
    onVideoClick: (String, Long) -> Unit
) {
    // 🔥 收集 UI 状态
    val state by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SearchTopBar(
                query = state.query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = {
                    viewModel.search(it)
                    keyboardController?.hide()
                },
                onBack = onBack,
                onClearQuery = { viewModel.onQueryChange("") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            if (state.showResults) {
                if (state.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = BiliPink
                    )
                } else if (state.error != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.error ?: "未知错误", color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 使用 itemsIndexed 确保渲染正确
                        itemsIndexed(state.searchResults) { index, video ->
                            VideoGridItem(
                                video = video,
                                index = index,
                                onClick = onVideoClick
                            )
                        }
                    }
                }
            } else {
                if (state.hotList.isEmpty() && state.historyList.isEmpty()) {
                    // 如果什么数据都没有，可能还没加载完成，或者真的空
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 可以选择显示 Loading 或者 空白
                        // CircularProgressIndicator(color = BiliPink)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (state.hotList.isNotEmpty()) {
                            item {
                                Text(
                                    "大家都在搜",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    color = Color.Black
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    state.hotList.forEach { hotItem ->
                                        Surface(
                                            color = Color(0xFFF6F7F8),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable {
                                                viewModel.search(hotItem.keyword)
                                                keyboardController?.hide()
                                            }
                                        ) {
                                            Text(
                                                text = hotItem.show_name,
                                                fontSize = 13.sp,
                                                color = Color.DarkGray,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        if (state.historyList.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("历史记录", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                                    TextButton(onClick = { viewModel.clearHistory() }) {
                                        Text("清空", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }

                            items(state.historyList) { history ->
                                HistoryItem(
                                    history = history,
                                    onClick = {
                                        viewModel.search(history.keyword)
                                        keyboardController?.hide()
                                    },
                                    onDelete = { viewModel.deleteHistory(history) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onClearQuery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Gray)
            }

            // 搜索条容器
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF1F2F3)), // 浅灰背景
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .size(18.dp)
                )

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 0.dp),
                    textStyle = TextStyle(
                        color = Color.Black,
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(BiliPink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索视频、UP主...",
                                    style = TextStyle(color = Color.Gray, fontSize = 14.sp)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            TextButton(onClick = { onSearch(query) }) {
                Text("搜索", color = BiliPink, fontSize = 15.sp)
            }
        }
    }
}

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
        Icon(Icons.Default.History, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = history.keyword, color = Color.Black, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
    }
    Divider(color = Color.LightGray.copy(alpha = 0.2f), thickness = 0.5.dp)
}