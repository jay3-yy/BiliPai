package com.android.purebilibili.feature.live

import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.animation.LocalLiquidIndicatorDynamicLengthEnabled

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeletonFixedColumns
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.data.model.response.LiveRoom
import com.android.purebilibili.data.model.response.LiveAreaNavigationTab
import com.android.purebilibili.data.model.response.LiveSecondSortTag
import com.android.purebilibili.data.repository.LiveRepository
import com.android.purebilibili.navigation.animatePagerSelection
import kotlinx.coroutines.launch

private data class LiveAreaTab(val id: Int, val parentId: Int, val title: String, val cover: String = "")

private class LiveAreaRoomPageState {
    val grid = LazyGridState()
    var rooms by mutableStateOf<List<LiveRoom>>(emptyList())
    var tags by mutableStateOf<List<LiveSecondSortTag>>(emptyList())
    var sortType by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(false)
    var nextPage by mutableIntStateOf(1)
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
    var navigation by remember(parentAreaId) { mutableStateOf<Result<List<LiveAreaNavigationTab>>?>(null) }
    var reload by remember(parentAreaId) { mutableIntStateOf(0) }
    val entries = navigation?.getOrNull().orEmpty()
    val parentTitle = entries.firstNotNullOfOrNull { it.parentName.takeIf(String::isNotBlank) } ?: title
    val tabs = remember(parentAreaId, entries) {
        entries.distinctBy { it.id }.map { entry ->
            LiveAreaTab(
                id = entry.id,
                parentId = entry.parentId.takeIf { it > 0 } ?: parentAreaId,
                title = entry.name,
                cover = entry.pic,
            )
        }
    }
    var showFirstFrame by rememberSaveable(parentAreaId) { mutableStateOf(false) }
    var showMenu by remember(parentAreaId, areaId) { mutableStateOf(false) }
    val hasTabs = tabs.isNotEmpty()
    // Mount the ready pager at its actual destination; never expose page zero first.
    val pager = key(parentAreaId, areaId, hasTabs) {
        rememberPagerState(
            initialPage = tabs.indexOfFirst { it.id == areaId }.coerceAtLeast(0),
            pageCount = { tabs.size.coerceAtLeast(1) },
        )
    }
    val pageStates = remember(parentAreaId) { mutableMapOf<Int, LiveAreaRoomPageState>() }
    val spec = resolveLiveVisualSpec(rememberAppTopChromePolicy().tabPresentation)
    LaunchedEffect(parentAreaId, reload) {
        navigation = LiveRepository.getLiveAreaNavigation(parentAreaId)
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
                if (hasTabs) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalLiquidIndicatorDynamicLengthEnabled provides false) {
                            AppThemeAdaptiveTabRow(
                                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                                role = AppTabRowRole.SECONDARY,
                                options = tabs.mapIndexed { index, tab -> AppSegmentOption(index, tab.title) },
                                selectedValue = pager.tabSelectedPage,
                                scrollable = true,
                                centerContent = false,
                                onSelectionChange = { target ->
                                    scope.launch {
                                        if (target == pager.settledPage && !pager.isScrollInProgress) {
                                            pageStates[tabs[target].id]?.grid?.animateScrollToTop()
                                        } else {
                                            animatePagerSelection(pager, target)
                                        }
                                    }
                                },
                                indicatorPositionProvider = { pager.tabIndicatorPosition },
                                isScrollInProgressProvider = { pager.isScrollInProgress },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        AppIconButton(onClick = { showFirstFrame = !showFirstFrame }) {
                            AppIcon(
                                if (showFirstFrame) LiveFirstFrameIcon else Icons.Outlined.Image,
                                if (showFirstFrame) "显示封面" else "显示首帧",
                            )
                        }
                        AppIconButton(onClick = { showMenu = true }) { AppIcon(Icons.Outlined.Menu, "显示分区菜单") }
                    }
                }
            }
        },
    ) { padding ->
        if (hasTabs) {
            AppHorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalPriorityHorizontalPagerSwipe(pager),
            ) { page ->
                tabs.getOrNull(page)?.let { tab ->
                    LiveAreaRoomPage(
                        tab = tab,
                        state = pageStates.getOrPut(tab.id) { LiveAreaRoomPageState() },
                        showFirstFrame = showFirstFrame,
                        active = page == pager.settledPage,
                        onLiveClick = onLiveClick,
                    )
                }
            }
        } else if (navigation != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(navigation?.exceptionOrNull()?.message ?: "该分区暂无标签")
                AppTextButton(onClick = {
                    navigation = null
                    reload++
                }) { AppText("重新加载") }
            }
        }
    }
    if (showMenu) {
        LiveAreaMenuSheet(
            title = parentTitle,
            tabs = tabs,
            selectedIndex = pager.tabSelectedPage,
            onSelect = { target -> scope.launch { animatePagerSelection(pager, target) } },
            onDismiss = { showMenu = false },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LiveAreaMenuSheet(
    title: String,
    tabs: List<LiveAreaTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    val latestSelect by rememberUpdatedState(onSelect)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, tabs.lastIndex.coerceAtLeast(0)))
    AppModalBottomSheet(
        visible = visible,
        presentationOverride = AppModalPresentation.BottomSheet,
        windowInsets = WindowInsets.safeDrawing,
        onDismissRequest = {
            latestDismiss()
            pendingIndex?.let(latestSelect)
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(40.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AppText(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AppIconButton(onClick = { visible = false }, modifier = Modifier.size(40.dp)) {
                    AppIcon(Icons.Outlined.Close, "关闭分区菜单")
                }
            }
            LazyVerticalGrid(
                columns = LiveRoomGridCells(100.dp),
                state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                    Column(
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                            .semantics { selected = index == selectedIndex }
                            .clickable(role = Role.Button) { pendingIndex = index; visible = false },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        coil3.compose.AsyncImage(
                            model = tab.cover, contentDescription = null,
                            modifier = Modifier.size(45.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )
                        Spacer(Modifier.height(4.dp))
                        AppText(tab.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    active: Boolean,
    onLiveClick: (Long, String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val grid = state.grid
    val spec = resolveLiveVisualSpec(rememberAppTopChromePolicy().tabPresentation)
    val window = LocalWindowSizeClass.current
    val columns = resolveLiveBiliPaiGridColumns(window.widthDp.value.toInt(), window.isTablet)
    var saveCover by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }
    fun load(reset: Boolean) {
        val request = if (reset) state.requests.refresh() else state.requests.loadMore(state.hasMore)
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
                        state.nextPage = request.page + 1
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
    LaunchedEffect(tab, active) {
        if (active && !state.initialized && state.error == null) load(true)
    }
    LiveGridPagination(
        grid = grid,
        enabled = active && state.hasMore && !state.loading && !state.loadingMore &&
            state.error == null && state.moreError == null,
        nextPage = state.nextPage,
        onLoadMore = { load(false) },
    )
    AdaptivePullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { load(true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = LiveRoomGridCells(),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spec.homeMetrics.safeSpaceDp.dp,
                end = spec.homeMetrics.safeSpaceDp.dp,
                top = AppSpacingTokens.Small,
                bottom = maxOf(LocalBottomBarContentPadding.current, AppSpacingTokens.Large),
            ),
            horizontalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
            verticalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
        ) {
            if (state.tags.isNotEmpty()) {
                stickyHeader(key = "area_sort_tags") {
                    Box(Modifier.fillMaxWidth().background(rememberLiveChromePalette().backgroundTop)) {
                        LiveSortTagChipRow(state.tags, state.sortType) { sort ->
                            if (!state.loading) {
                                state.sortType = sort
                                state.rooms = emptyList()
                                load(true)
                                grid.requestScrollToItem(0)
                            }
                        }
                    }
                }
            }
            when {
                state.loading && state.rooms.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ContentVideoGridSkeletonFixedColumns(columns, modifier = Modifier.fillMaxWidth().height(560.dp))
                }
                state.error != null || state.rooms.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(state.error ?: "暂无该分区直播", color = AppContentColors.secondaryText())
                        AppTextButton(onClick = { load(true) }) { AppText("重新加载") }
                    }
                }
                else -> items(state.rooms, key = { it.roomid }) { room ->
                    val card = LiveRoomCardUiModel(room.roomid, room.title, room.displayCover(showFirstFrame), room.uname, room.viewerCount(), room.areaName, room.watchedShow?.textLarge.orEmpty())
                    LiveRoomCard(card, onClick = { onLiveClick(room.roomid, room.title, room.uname) }, onLongPress = { saveCover = card })
                }
            }
            if (state.error == null && (state.hasMore || state.moreError != null)) {
                item(key = LIVE_GRID_LOAD_MORE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    LiveHomeLoadMoreFooter(state.loadingMore, state.rooms.size, state.hasMore, state.moreError, autoLoad = false) { load(false) }
                }
            }
        }
    }
    saveCover?.let { card -> LiveCoverSaveDialog(card) { saveCover = null } }
}
