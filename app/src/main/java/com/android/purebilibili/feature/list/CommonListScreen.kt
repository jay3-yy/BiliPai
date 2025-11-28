package com.android.purebilibili.feature.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.VideoGridItemSkeleton
import com.android.purebilibili.feature.home.VideoGridItem // 导入别名函数

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonListScreen(
    viewModel: BaseListViewModel,
    onBack: () -> Unit,
    onVideoClick: (String, Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF1F2F3))
        ) {
            if (state.isLoading) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(8) { VideoGridItemSkeleton() }
                }
            } else if (state.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = state.error ?: "未知错误", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadData() }, colors = ButtonDefaults.buttonColors(containerColor = BiliPink)) {
                        Text("重试")
                    }
                }
            } else if (state.items.isEmpty()) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Text("暂无数据", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.items) { index, video ->
                        // 修复：传入 index
                        VideoGridItem(
                            video = video,
                            index = index,
                            onClick = onVideoClick
                        )
                    }
                }
            }
        }
    }
}