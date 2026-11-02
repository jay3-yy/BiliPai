package com.android.purebilibili.feature.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.HomeStyleSingleColumnVideoCard
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun SpaceWebVideosScreen(
    mid: Long,
    ownerName: String,
    collectionType: String,
    collectionId: Long,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    viewModel: SpaceWebVideosViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    LaunchedEffect(mid, collectionType, collectionId, account) {
        viewModel.initialize(mid, ownerName, collectionType, collectionId)
    }
    val grid = rememberLazyGridState()
    var displayedRevision by rememberSaveable(mid, collectionType, collectionId) { mutableIntStateOf(0) }
    LaunchedEffect(state.listRevision) {
        if (state.listRevision != displayedRevision) {
            if (state.listRevision > 0) grid.scrollToItem(0)
            displayedRevision = state.listRevision
        }
    }
    LaunchedEffect(grid, viewModel) {
        snapshotFlow {
            val layout = grid.layoutInfo
            layout.totalItemsCount to (layout.totalItemsCount > 1 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 3)
        }.distinctUntilChanged().collect { (_, nearEnd) -> if (nearEnd) viewModel.append() }
    }
    var showJump by rememberSaveable { mutableStateOf(false) }
    var pageInput by rememberSaveable { mutableStateOf("") }
    val orders = if (collectionType.isEmpty()) listOf(
        "pubdate" to "最新发布", "click" to "最多播放", "stow" to "最多收藏",
    ) else listOf("desc" to "默认", "asc" to "倒序")

    AppScaffold(
        topBar = {
            AppTopBar(
                title = ownerName,
                subtitle = "网页投稿",
                navigationIcon = {
                    AppIconButton(onClick = onBack) { AppIcon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    AppWindowActionMenu(groups = listOf(orders.map { (order, label) ->
                        AppWindowAction(label = label, selected = state.order == order, onClick = { viewModel.sort(order) })
                    })) { AppIcon(Icons.Outlined.Sort, "排序") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(state.count?.let { "共 $it 视频" } ?: "视频数量待加载", Modifier.weight(1f))
                if (state.tags.isNotEmpty()) {
                    AppWindowActionMenu(groups = listOf(state.tags.map { tag ->
                        AppWindowAction(
                            label = tag.name,
                            summary = tag.count?.let { "$it 视频" },
                            selected = tag.tid == state.tag.tid && tag.specialType == state.tag.specialType,
                            onClick = { viewModel.filter(tag) },
                        )
                    })) { AppText(state.tag.name, Modifier.padding(8.dp)) }
                }
            }
            // 翻页栏按可用宽度分配文本空间，手机窄分屏也不挤占卡片宽度。
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppTextButton(onClick = { viewModel.jump(state.page - 1) }, enabled = !state.isLoading && state.page > 1) { AppText("上一页") }
                AppTextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { pageInput = state.page.toString(); showJump = true },
                    enabled = !state.isLoading && (state.totalPages ?: 0) > 0,
                ) { AppText(state.totalPages?.let { if (it == 0) "0 / 0" else "${state.page} / $it" } ?: "— / —") }
                AppTextButton(onClick = { viewModel.jump(state.page + 1) }, enabled = !state.isLoading && state.hasMore) { AppText("下一页") }
            }
            AdaptivePullToRefreshBox(
                isRefreshing = state.isLoading && state.items.isEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(360.dp),
                    state = grid,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.bvid }) { video ->
                        HomeStyleSingleColumnVideoCard(
                            video = video, sourceRoute = "space_web_videos/$mid", coverAspectRatio = 16f / 9f,
                            transitionEnabled = false, showPublishTime = true,
                            onClick = { onVideoClick(video) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                state.isLoading -> AppText("正在加载…")
                                state.error != null -> {
                                    AppText(state.error.orEmpty())
                                    AppTextButton(onClick = viewModel::retry) { AppText("重试") }
                                }
                                state.count == 0 -> AppText("暂无投稿")
                                state.hasMore -> AppTextButton(onClick = viewModel::append) { AppText("加载更多") }
                                state.count != null -> AppText("已到最后一页")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showJump) {
        val page = pageInput.toIntOrNull()
        val valid = page != null && page in 1..(state.totalPages ?: 0)
        AppAlertDialog(
            onDismissRequest = { showJump = false },
            title = { AppText("跳转到页码") },
            text = {
                AppOutlinedTextField(
                    value = pageInput, onValueChange = { pageInput = it }, singleLine = true,
                    labelText = "1–${state.totalPages}", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = pageInput.isNotEmpty() && !valid,
                )
            },
            confirmButton = {
                AppTextButton(onClick = { viewModel.jump(checkNotNull(page)); showJump = false }, enabled = valid) { AppText("跳转") }
            },
            dismissButton = { AppDialogAction(onClick = { showJump = false }) { AppText("取消") } },
        )
    }
}
