package com.android.purebilibili.feature.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeletonFixedColumns
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.LiveRoom
import com.android.purebilibili.data.model.response.LiveSecondSortTag
import com.android.purebilibili.data.repository.LiveRepository
import kotlinx.coroutines.launch

private data class LiveAreaTab(val id: Int, val parentId: Int, val title: String, val cover: String = "")

private class LiveAreaRoomPageState {
    var rooms by mutableStateOf<List<LiveRoom>>(emptyList())
    var tags by mutableStateOf<List<LiveSecondSortTag>>(emptyList())
    var sortType by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var moreError by mutableStateOf<String?>(null)
    var initialized = false
    val requests = LiveBrowseRequestPolicy()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LiveAreaDetailScreen(
    parentAreaId: Int,
    areaId: Int,
    title: String,
    onBack: () -> Unit,
    onAreaClick: (Int, Int, String) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onSearchClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var parentTitle by remember(parentAreaId) { mutableStateOf(title) }
    var tabs by remember(parentAreaId) { mutableStateOf<List<LiveAreaTab>>(emptyList()) }
    var showFirstFrame by rememberSaveable(parentAreaId) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var metadataLoaded by remember(parentAreaId) { mutableStateOf(false) }
    // Mount the ready pager at its actual destination; never expose page zero first.
    val pager = key(parentAreaId, areaId, metadataLoaded) {
        rememberPagerState(
            initialPage = tabs.indexOfFirst { it.id == areaId }.coerceAtLeast(0),
            pageCount = { tabs.size.coerceAtLeast(1) },
        )
    }
    val pageStates = remember(parentAreaId) { mutableMapOf<Int, LiveAreaRoomPageState>() }
    val spec = resolveLiveVisualSpec(rememberAppTopChromePolicy().tabPresentation)
    LaunchedEffect(parentAreaId, areaId) {
        val parent = LiveRepository.getLiveAreaIndex().getOrNull()?.firstOrNull { it.id == parentAreaId }
        parentTitle = parent?.name ?: title
        tabs = parent?.list.orEmpty().map {
            LiveAreaTab(it.id.toIntOrNull() ?: 0, it.parent_id.toIntOrNull() ?: parentAreaId, it.name, it.pic)
        }.ifEmpty { listOf(LiveAreaTab(areaId, parentAreaId, title)) }
        metadataLoaded = true
    }
    AppScaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                AppTopBar(
                    title = parentTitle,
                    navigationIcon = { AppIconButton(onClick = onBack) { AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                    actions = { AppIconButton(onClick = onSearchClick) { AppIcon(Icons.Outlined.Search, "搜索直播") } },
                )
                if (metadataLoaded) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppThemeAdaptiveTabRow(
                            role = AppTabRowRole.SECONDARY,
                            options = tabs.mapIndexed { index, tab -> AppSegmentOption(index, tab.title) },
                            selectedValue = pager.currentPage,
                            scrollable = true,
                            centerContent = false,
                            onSelectionChange = { target -> scope.launch { pager.scrollToPage(target) } },
                            indicatorPositionProvider = { pager.currentPage + pager.currentPageOffsetFraction },
                            isScrollInProgressProvider = { pager.isScrollInProgress },
                            modifier = Modifier.weight(1f),
                        )
                        AppIconButton(onClick = { showFirstFrame = !showFirstFrame }) {
                            AppIcon(if (showFirstFrame) Icons.Outlined.Photo else Icons.Outlined.Image, if (showFirstFrame) "显示封面" else "显示首帧")
                        }
                        AppIconButton(onClick = { showMenu = true }) { AppIcon(Icons.Outlined.Menu, "显示分区菜单") }
                    }
                }
            }
        },
    ) { padding ->
        if (metadataLoaded) {
            AppHorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
                tabs.getOrNull(page)?.let { tab ->
                    LiveAreaRoomPage(
                        tab = tab,
                        state = pageStates.getOrPut(tab.id) { LiveAreaRoomPageState() },
                        showFirstFrame = showFirstFrame,
                        onLiveClick = onLiveClick,
                    )
                }
            }
        }
    }
    if (showMenu) {
        AppModalBottomSheet(onDismissRequest = { showMenu = false }) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(AppSpacingTokens.Large),
            ) {
                itemsIndexed(tabs) { index, tab ->
                    AppTextButton(onClick = {
                        showMenu = false
                        scope.launch { pager.scrollToPage(index) }
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            coil3.compose.AsyncImage(model = tab.cover, contentDescription = null, modifier = Modifier.size(48.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            AppText(tab.title)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAreaRoomPage(
    tab: LiveAreaTab,
    state: LiveAreaRoomPageState,
    showFirstFrame: Boolean,
    onLiveClick: (Long, String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val spec = resolveLiveVisualSpec(rememberAppTopChromePolicy().tabPresentation)
    val window = LocalWindowSizeClass.current
    val columns = resolveLiveBiliPaiGridColumns(minOf(window.widthDp.value.toInt(), spec.maxContentWidthDp), window.isTablet)
    var saveCover by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }
    fun load(reset: Boolean) {
        val request = if (reset) state.requests.refresh(replace = true) else state.requests.loadMore(state.hasMore)
        if (request == null) return
        state.loading = reset
        state.loadingMore = !reset
        state.error = null
        state.moreError = null
        val sort = state.sortType
        scope.launch {
            try {
                LiveRepository.getLiveSecondHome(tab.parentId, tab.id, request.page, sort)
                    .onSuccess { result ->
                        if (!state.requests.succeed(request)) return@onSuccess
                        state.rooms = (if (reset) result.rooms else state.rooms + result.rooms).distinctBy { it.roomid }
                        if (result.sortTags.isNotEmpty()) state.tags = result.sortTags
                        state.hasMore = result.hasMore
                        state.loading = false
                        state.loadingMore = false
                        state.initialized = true
                    }.onFailure {
                        if (!state.requests.fail(request)) return@onFailure
                        if (reset) state.error = it.message ?: "加载失败" else state.moreError = it.message ?: "加载更多失败"
                        state.loading = false
                        state.loadingMore = false
                    }
            } finally {
                // Pager can dispose offscreen pages; release the request so returning can retry.
                if (state.requests.owns(request)) {
                    state.requests.fail(request)
                    state.loading = false
                    state.loadingMore = false
                }
            }
        }
    }
    LaunchedEffect(tab) { if (!state.initialized || state.rooms.isEmpty()) load(true) }
    AdaptivePullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { load(true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.responsiveContentWidth(maxWidth = spec.maxContentWidthDp.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = spec.homeMetrics.safeSpaceDp.dp,
                end = spec.homeMetrics.safeSpaceDp.dp,
                top = AppSpacingTokens.Small,
                bottom = maxOf(LocalBottomBarContentPadding.current, AppSpacingTokens.Large),
            ),
            horizontalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
            verticalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
        ) {
            if (state.tags.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                LiveSortTagChipRow(state.tags, state.sortType) { sort ->
                    if (state.sortType != sort) {
                        state.sortType = sort
                        state.rooms = emptyList()
                        load(true)
                        scope.launch { grid.scrollToItem(0) }
                    }
                }
            }
            when {
                state.loading && state.rooms.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ContentVideoGridSkeletonFixedColumns(columns, modifier = Modifier.fillMaxWidth().height(560.dp))
                }
                state.rooms.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(state.error ?: "暂无该分区直播", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AppTextButton(onClick = { load(true) }) { AppText("重新加载") }
                    }
                }
                else -> items(state.rooms, key = { it.roomid }) { room ->
                    val card = LiveRoomCardUiModel(room.roomid, room.title, room.displayCover(showFirstFrame), room.uname, room.viewerCount(), room.areaName, room.watchedShow?.textLarge.orEmpty())
                    LiveRoomCard(card, onClick = { onLiveClick(room.roomid, room.title, room.uname) }, onLongPress = { saveCover = card })
                }
            }
            if (state.rooms.isNotEmpty() || state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                LiveHomeLoadMoreFooter(state.loadingMore, state.rooms.size, state.hasMore, state.moreError) { load(false) }
            }
        }
    }
    saveCover?.let { card -> LiveCoverSaveDialog(card) { saveCover = null } }
}
