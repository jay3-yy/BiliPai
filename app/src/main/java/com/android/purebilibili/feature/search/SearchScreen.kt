package com.android.purebilibili.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed // 导入这个
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.feature.home.VideoGridItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    onBack: () -> Unit,
    onVideoClick: (String, Long) -> Unit
) {
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
                        // 修复：改用 itemsIndexed 并传入 index
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
                // ... 历史记录部分保持不变 ...
                if (state.hotList.isEmpty() && state.historyList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BiliPink)
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

// ... SearchTopBar 和 HistoryItem 保持不变 ...
// (请保留原文件中的这两个函数，无需修改)
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

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF1F2F3)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.padding(start = 12.dp).size(18.dp)
                )

                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("搜索视频、UP主...", fontSize = 14.sp, color = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = BiliPink
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    modifier = Modifier.weight(1f)
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