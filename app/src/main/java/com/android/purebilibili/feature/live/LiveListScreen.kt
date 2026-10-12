package com.android.purebilibili.feature.live

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import com.android.purebilibili.core.ui.components.AppBadge
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.LiveAreaParent
import com.android.purebilibili.data.repository.LiveRepository
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveRoomItem(
    val roomId: Long,
    val title: String,
    val cover: String,
    val systemCover: String = "",
    val uname: String,
    val face: String,
    val online: Int,
    val areaName: String,
    val liveStatus: Int = 1,
    val uid: Long = 0,
    val viewerText: String = "",
) {
    fun resolvedCover(preferFirstFrame: Boolean): String {
        return if (preferFirstFrame) {
            listOf(systemCover, cover, face).firstOrNull { it.isNotBlank() }.orEmpty()
        } else {
            listOf(cover, systemCover, face).firstOrNull { it.isNotBlank() }.orEmpty()
        }
    }
}

data class LiveListUiState(
    val contentItems: List<LiveRoomItem> = emptyList(),
    val followItems: List<LiveRoomItem> = emptyList(),
    val areaEntries: List<com.android.purebilibili.data.model.response.LiveFeedAreaEntry> = emptyList(),
    val areaList: List<LiveAreaParent> = emptyList(),
    /** 0 = 推荐；1 = 已关注；其余对应 [areaEntries] 下标 + 2 */
    val selectedAreaIndex: Int = 0,
    val selectedParentAreaId: Int = 0,
    val selectedAreaId: Int = 0,
    val sortTags: List<com.android.purebilibili.data.model.response.LiveSecondSortTag> = emptyList(),
    val selectedSortType: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val showFirstFrame: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val livingCount: Int = 0,
)

class LiveListViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LiveListUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private val requests = LiveBrowseRequestPolicy()

    init {
        refresh()
    }

    fun refresh() {
        val request = requests.refresh() ?: return
        val state = _uiState.value
        _uiState.value = state.copy(
            isLoading = true, isLoadingMore = false, error = null, loadMoreError = null,
        )
        viewModelScope.launch {
            loadPage(request, state)
            // 分区详情子标签仍用 web area list 兜底；元数据不依赖当前分类。
            if (_uiState.value.areaList.isEmpty()) {
                runCatching {
                    val response = NetworkModule.api.getLiveAreaList()
                    if (response.code == 0 && response.data != null) {
                        _uiState.value = _uiState.value.copy(areaList = requireNotNull(response.data))
                    }
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        val request = requests.loadMore(state.hasMore) ?: return
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        viewModelScope.launch { loadPage(request, state) }
    }

    private fun reloadSelection() {
        requests.invalidate()
        _uiState.value = _uiState.value.copy(
            contentItems = emptyList(), isLoadingMore = false, loadMoreError = null,
        )
        refresh()
    }

    private suspend fun loadPage(request: LiveBrowseRequestPolicy.Request, state: LiveListUiState) {
        when {
            state.selectedAreaIndex == LIVE_HOME_RECOMMEND_INDEX -> loadRecommendPage(request)
            isLiveHomeFollowedTab(state.selectedAreaIndex) -> loadFollowedPage(request)
            else -> loadAreaPage(request, state)
        }
    }

    fun selectHomeArea(index: Int) {
        val state = _uiState.value
        if (index == state.selectedAreaIndex) return
        if (index <= LIVE_HOME_RECOMMEND_INDEX) {
            _uiState.value = state.copy(
                selectedAreaIndex = LIVE_HOME_RECOMMEND_INDEX,
                selectedParentAreaId = 0,
                selectedAreaId = 0,
                selectedSortType = null,
                sortTags = emptyList(),
                page = 1,
                hasMore = true,
                isLoading = true,
                error = null,
            )
            reloadSelection()
            return
        }
        if (isLiveHomeFollowedTab(index)) {
            _uiState.value = state.copy(
                selectedAreaIndex = LIVE_HOME_FOLLOWED_INDEX,
                selectedParentAreaId = 0,
                selectedAreaId = 0,
                selectedSortType = null,
                sortTags = emptyList(),
                page = 1,
                hasMore = true,
                isLoading = true,
                error = null,
            )
            reloadSelection()
            return
        }
        val entry = resolveLiveHomeAreaEntries(
            feedEntries = state.areaEntries,
            areaParents = state.areaList
        ).getOrNull(resolveLiveHomeAreaListIndex(index)) ?: return
        _uiState.value = state.copy(
            selectedAreaIndex = index,
            selectedParentAreaId = entry.parentAreaId,
            selectedAreaId = entry.areaId,
            selectedSortType = null,
            sortTags = emptyList(),
            page = 1,
            hasMore = true,
            isLoading = true,
            error = null,
        )
        reloadSelection()
    }

    fun selectSortTag(sortType: String?) {
        val state = _uiState.value
        if (state.selectedAreaIndex <= LIVE_HOME_FOLLOWED_INDEX) return
        if (state.selectedSortType == sortType) return
        _uiState.value = state.copy(
            selectedSortType = sortType,
            page = 1,
            hasMore = true,
            isLoading = true,
            error = null,
        )
        reloadSelection()
    }

    fun toggleShowFirstFrame() {
        _uiState.value = _uiState.value.copy(showFirstFrame = !_uiState.value.showFirstFrame)
    }

    private suspend fun loadRecommendPage(request: LiveBrowseRequestPolicy.Request) {
        val page = request.page
        val append = request.append
        LiveRepository.getLiveFeedHome(page = page).fold(
            onSuccess = { snapshot ->
                if (!requests.succeed(request)) return@fold
                val mapped = snapshot.rooms.map { it.toLiveRoomItem() }
                val followMapped = snapshot.followRooms.map { it.toLiveRoomItem() }
                val current = _uiState.value
                val mergedRooms = if (append) {
                    (current.contentItems + mapped).distinctBy { it.roomId }
                } else {
                    mapped.distinctBy { it.roomId }
                }
                val areaEntries = when {
                    snapshot.areaEntries.isNotEmpty() -> snapshot.areaEntries
                    !append -> current.areaEntries
                    else -> current.areaEntries
                }.ifEmpty {
                    current.areaList.map {
                        com.android.purebilibili.data.model.response.LiveFeedAreaEntry(
                            title = it.name,
                            areaId = 0,
                            parentAreaId = it.id,
                        )
                    }
                }
                _uiState.value = current.copy(
                    contentItems = mergedRooms,
                    followItems = if (followMapped.isNotEmpty()) followMapped else if (!append) emptyList() else current.followItems,
                    livingCount = if (!append) snapshot.totalCount else current.livingCount,
                    areaEntries = areaEntries,
                    page = page,
                    hasMore = snapshot.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (mergedRooms.isEmpty() && !append) "暂无直播" else null,
                )
            },
            onFailure = { error ->
                if (!requests.fail(request)) return@fold
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (!append) error.message ?: "加载失败" else _uiState.value.error,
                    loadMoreError = if (append) error.message ?: "加载更多失败" else null,
                )
            }
        )
    }

    private suspend fun loadFollowedPage(request: LiveBrowseRequestPolicy.Request) {
        val page = request.page
        val append = request.append
        LiveRepository.getFollowedLivePage(page = page).fold(
            onSuccess = { snapshot ->
                if (!requests.succeed(request)) return@fold
                val mapped = snapshot.items.map { it.toLiveRoomItem() }
                val current = _uiState.value
                val mergedRooms = if (append) {
                    (current.contentItems + mapped).distinctBy { it.roomId }
                } else {
                    mapped.distinctBy { it.roomId }
                }
                _uiState.value = current.copy(
                    contentItems = mergedRooms,
                    followItems = if (!append) mapped else current.followItems,
                    livingCount = if (!append) snapshot.totalCount else current.livingCount,
                    page = page,
                    hasMore = snapshot.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    // 空关注列表是正常内容状态，仍需保留顶部分类，方便切回推荐直播。
                    error = null,
                )
            },
            onFailure = { error ->
                if (!requests.fail(request)) return@fold
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (!append) error.message ?: "加载关注直播失败" else _uiState.value.error,
                    loadMoreError = if (append) error.message ?: "加载更多失败" else null,
                )
            }
        )
    }

    private suspend fun loadAreaPage(
        request: LiveBrowseRequestPolicy.Request,
        state: LiveListUiState,
    ) {
        val page = request.page
        val append = request.append
        val query = resolveLiveAreaRoomQuery(
            parentAreaId = state.selectedParentAreaId,
            areaId = state.selectedAreaId
        ) ?: run {
            if (!requests.fail(request)) return
            _uiState.value = state.copy(
                isLoading = false,
                isLoadingMore = false,
                error = "无效的直播分区"
            )
            return
        }
        LiveRepository.getLiveSecondHome(
            parentAreaId = query.parentAreaId,
            areaId = query.areaId,
            page = page,
            sortType = state.selectedSortType,
        ).fold(
            onSuccess = { snapshot ->
                if (!requests.succeed(request)) return@fold
                val mapped = snapshot.rooms.map { it.toLiveRoomItem() }
                val current = _uiState.value
                val merged = if (append) {
                    (current.contentItems + mapped).distinctBy { it.roomId }
                } else {
                    mapped.distinctBy { it.roomId }
                }
                _uiState.value = current.copy(
                    contentItems = merged,
                    sortTags = if (snapshot.sortTags.isNotEmpty()) snapshot.sortTags else current.sortTags,
                    page = page,
                    hasMore = snapshot.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (merged.isEmpty() && !append) "暂无直播内容" else null,
                )
            },
            onFailure = { error ->
                if (!requests.fail(request)) return@fold
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (!append) error.message ?: "加载失败" else _uiState.value.error,
                    loadMoreError = if (append) error.message ?: "加载更多失败" else null,
                )
            }
        )
    }
}

private fun com.android.purebilibili.data.model.response.LiveRoom.toLiveRoomItem(): LiveRoomItem =
    LiveRoomItem(
        roomId = roomid,
        uid = uid,
        title = title,
        cover = displayCover(preferFirstFrame = false),
        systemCover = systemCover.ifBlank { keyframe },
        uname = uname,
        face = face,
        online = viewerCount(),
        viewerText = watchedShow?.textLarge.orEmpty(),
        areaName = areaName,
    )

@Composable
fun LiveListScreen(
    onBack: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onSearchClick: () -> Unit,
    onAreaListClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onAreaDetailClick: (Int, Int, String) -> Unit,
    onMatchClick: () -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    /** 底栏主入口时隐藏返回，更接近 BiliPai 主 tab 形态。 */
    showNavigationBack: Boolean = true,
    /** 首页顶栏独立页：去掉自有顶栏和状态栏垫高，避免和首页搜索/标签叠两层。 */
    embeddedInHome: Boolean = false,
    contentTopPadding: androidx.compose.ui.unit.Dp = AppSpacingTokens.None,
    scrollToTopRequestId: Int = 0,
    scrollToTopChannel: Channel<Unit>? = null,
    viewModel: LiveListViewModel = viewModel(),
    globalHazeState: HazeState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val palette = rememberLiveChromePalette()
    val coroutineScope = rememberCoroutineScope()
    var coverToSave by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }

    val windowSizeClass = LocalWindowSizeClass.current
    val topChromePolicy = rememberAppTopChromePolicy()
    val visualSpec = remember(topChromePolicy.tabPresentation) {
        resolveLiveVisualSpec(topChromePolicy.tabPresentation)
    }
    val metrics = visualSpec.homeMetrics
    val contentWidth = if (windowSizeClass.isExpandedScreen) {
        minOf(windowSizeClass.widthDp, visualSpec.maxContentWidthDp.dp)
    } else {
        windowSizeClass.widthDp
    }
    val gridColumns = remember(contentWidth, windowSizeClass.isTablet) {
        resolveLiveBiliPaiGridColumns(contentWidth.value.toInt(), windowSizeClass.isTablet)
    }
    val gridBottomPadding = LocalBottomBarContentPadding.current
    val liveGridState = rememberLazyGridState()
    suspend fun scrollLiveHomeToTop() {
        val atTop = liveGridState.firstVisibleItemIndex == 0 &&
            liveGridState.firstVisibleItemScrollOffset < 50
        if (!atTop) {
            liveGridState.animateScrollToTop()
        } else {
            viewModel.refresh()
        }
    }
    LaunchedEffect(scrollToTopRequestId) {
        if (scrollToTopRequestId <= 0) return@LaunchedEffect
        scrollLiveHomeToTop()
    }
    LaunchedEffect(scrollToTopChannel) {
        scrollToTopChannel?.receiveAsFlow()?.collect {
            scrollLiveHomeToTop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundBrush())
    ) {
        Column(
            modifier = Modifier
                .responsiveContentWidth(maxWidth = visualSpec.maxContentWidthDp.dp)
                .fillMaxSize()
                .then(if (embeddedInHome) Modifier.padding(top = contentTopPadding) else Modifier.statusBarsPadding())
        ) {
            if (!embeddedInHome) {
                LiveListHeader(
                    metrics = metrics,
                    livingCount = state.livingCount,
                    primaryFace = state.followItems.firstOrNull()?.face.orEmpty(),
                    showNavigationBack = showNavigationBack,
                    onBack = onBack,
                    onSearchClick = onSearchClick,
                    onInboxClick = onFollowingClick,
                    onAvatarClick = onAreaListClick
                )
            }
            AdaptivePullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LiveHomeContent(
                    gridState = liveGridState,
                    contentItems = state.contentItems,
                    followItems = state.followItems,
                    areaEntries = state.areaEntries,
                    areaList = state.areaList,
                    selectedAreaIndex = state.selectedAreaIndex,
                    selectedParentAreaId = state.selectedParentAreaId,
                    sortTags = state.sortTags,
                    selectedSortType = state.selectedSortType,
                    livingCount = state.livingCount,
                    isLoading = state.isLoading,
                    error = state.error,
                    onRetry = viewModel::refresh,
                    isLoadingMore = state.isLoadingMore,
                    hasMore = state.hasMore,
                    loadMoreError = state.loadMoreError,
                    showFirstFrame = state.showFirstFrame,
                    gridColumns = gridColumns,
                    bottomPadding = gridBottomPadding,
                    metrics = metrics,
                    visualSpec = visualSpec,
                    onLiveClick = onLiveClick,
                    onUserClick = onUserClick,
                    onAreaSelected = { index ->
                        viewModel.selectHomeArea(index)
                        coroutineScope.launch { liveGridState.scrollToItem(0) }
                    },
                    onToggleFirstFrame = viewModel::toggleShowFirstFrame,
                    onAreaListClick = onAreaListClick,
                    onMatchClick = onMatchClick,
                    onSortTagSelected = viewModel::selectSortTag,
                    onLoadMore = viewModel::loadMore,
                    onAreaDetailClick = onAreaDetailClick,
                    onFollowingClick = onFollowingClick,
                    onLongPressCard = { coverToSave = it },
                )
            }
        }
    }
    coverToSave?.let { card -> LiveCoverSaveDialog(card) { coverToSave = null } }

}

@Composable
private fun LiveHomeContent(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    contentItems: List<LiveRoomItem>,
    followItems: List<LiveRoomItem>,
    areaEntries: List<com.android.purebilibili.data.model.response.LiveFeedAreaEntry>,
    areaList: List<LiveAreaParent>,
    selectedAreaIndex: Int,
    selectedParentAreaId: Int,
    sortTags: List<com.android.purebilibili.data.model.response.LiveSecondSortTag>,
    selectedSortType: String?,
    livingCount: Int,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    showFirstFrame: Boolean,
    gridColumns: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    metrics: LiveBiliPaiHomeMetrics,
    visualSpec: LiveVisualSpec,
    onLiveClick: (Long, String, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onAreaSelected: (Int) -> Unit,
    onToggleFirstFrame: () -> Unit,
    onAreaListClick: () -> Unit,
    onMatchClick: () -> Unit,
    onSortTagSelected: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onAreaDetailClick: (Int, Int, String) -> Unit,
    onFollowingClick: () -> Unit,
    onLongPressCard: (LiveRoomCardUiModel) -> Unit = {}
) {
    val selectedParent = areaList.firstOrNull { it.id == selectedParentAreaId }
        ?: areaList.firstOrNull {
            areaEntries.getOrNull(resolveLiveHomeAreaListIndex(selectedAreaIndex).coerceAtLeast(0))?.parentAreaId == it.id
        }

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.safeSpaceDp.dp,
            end = metrics.safeSpaceDp.dp,
            top = metrics.cardSpaceDp.dp,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(metrics.cardSpaceDp.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.cardSpaceDp.dp)
    ) {
        if (followItems.isNotEmpty() || livingCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiveFollowHeader(
                    livingCount = livingCount,
                    onActionClick = onFollowingClick
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiveFollowAvatarRow(
                    items = followItems,
                    metrics = metrics,
                    onLiveClick = onLiveClick,
                    onUserClick = onUserClick,
                    onFollowingClick = onFollowingClick,
                    showMore = livingCount > followItems.size,
                )
            }
        }
        item(key = "live_categories", span = { GridItemSpan(maxLineSpan) }) {
            LiveAreaHomeChipRow(
                areaEntries = resolveLiveHomeAreaEntries(areaEntries, areaList),
                selectedAreaIndex = selectedAreaIndex,
                showFirstFrame = showFirstFrame,
                onAreaSelected = onAreaSelected,
                onToggleFirstFrame = onToggleFirstFrame,
                onAreaListClick = onAreaListClick,
                onMatchClick = onMatchClick,
            )
        }
        if (selectedAreaIndex > LIVE_HOME_FOLLOWED_INDEX && sortTags.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LiveSortTagChipRow(
                        tags = sortTags,
                        selectedSortType = selectedSortType,
                        onSortTagSelected = onSortTagSelected,
                    )
                }
            }
        when {
            isLoading && contentItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeletonFixedColumns(
                    columns = gridColumns,
                    modifier = Modifier.fillMaxWidth().height(560.dp),
                )
            }
            error != null && contentItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                LiveListErrorState(error, onRetry)
            }
            contentItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    message = if (isLiveHomeFollowedTab(selectedAreaIndex)) {
                        "关注的主播暂时都未开播"
                    } else {
                        "暂无直播内容"
                    },
                    visualSpec = visualSpec
                )
            }
            else -> {
                items(contentItems, key = { it.roomId }) { item ->
                    val model = item.toLiveRoomCardUiModel(showFirstFrame)
                    LiveRoomCard(
                        model = model,
                        enableSharedCoverTransition = true,
                        onClick = { onLiveClick(item.roomId, item.title, item.uname) },
                        onLongPress = { onLongPressCard(model) }
                    )
                }
            }
        }
        if (hasMore && !isLoading && error == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiveHomeLoadMoreFooter(
                    isLoadingMore = isLoadingMore,
                    contentCount = contentItems.size,
                    hasMore = hasMore,
                    error = loadMoreError,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
}

@Composable
private fun LiveListHeader(
    metrics: LiveBiliPaiHomeMetrics,
    livingCount: Int,
    primaryFace: String,
    showNavigationBack: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onInboxClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val palette = rememberLiveChromePalette()
    val compactChrome = rememberAppTopChromePolicy().compactChromeSpec
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = metrics.safeSpaceDp.dp,
                vertical = AppSpacingTokens.Small,
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
        ) {
            if (showNavigationBack) {
                AppSurface(
                    onClick = onBack,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = palette.primaryText
                        )
                    }
                }
            } else {
                AppText(
                    text = "直播",
                    color = palette.primaryText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = AppSpacingTokens.ExtraSmall),
                )
            }
            AppSurface(
                onClick = onSearchClick,
                color = palette.searchField,
                shape = AppShapes.container(ContainerLevel.Pill),
                modifier = Modifier
                    .weight(1f)
                    .height(compactChrome.primaryHeightDp.dp)
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = compactChrome.inputHorizontalPaddingDp.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = palette.secondaryText
                    )
                    Spacer(Modifier.width(AppSpacingTokens.Medium))
                    AppText(
                        text = "搜索直播间 / 主播",
                        color = palette.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Box {
                AppSurface(
                    onClick = onInboxClick,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(AppSpacingTokens.TripleExtraLarge)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(
                            imageVector = Icons.Outlined.Subscriptions,
                            contentDescription = "关注直播",
                            tint = palette.primaryText
                        )
                    }
                }
                if (livingCount > 0) {
                    AppBadge(
                        containerColor = palette.accentStrong,
                        contentColor = palette.onAccent,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        AppText(
                            text = if (livingCount > 99) "99+" else livingCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(AppSpacingTokens.TripleExtraLarge)
                    .clickable(onClick = onAvatarClick)
                    .semantics { contentDescription = "全部直播分区" },
                contentAlignment = Alignment.Center,
            ) {
                AppSurface(
                    color = palette.surfaceMuted,
                    shape = CircleShape,
                    modifier = Modifier.size(compactChrome.secondaryButtonSizeDp.dp)
                ) {
                    if (primaryFace.isNotBlank()) {
                        AsyncImage(
                            model = primaryFace,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            AppText(
                                text = "LIVE",
                                color = palette.primaryText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveFollowHeader(
    livingCount: Int,
    onActionClick: () -> Unit
) {
    val palette = rememberLiveChromePalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            AppText(
                text = "我的关注  ",
                color = palette.primaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            AppText(
                text = livingCount.toString(),
                color = palette.accentStrong,
                style = MaterialTheme.typography.bodySmall,
            )
            AppText(
                text = " 人正在直播",
                color = palette.secondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.clickable(onClick = onActionClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "查看更多",
                color = palette.secondaryText,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(AppSpacingTokens.ExtraSmall))
            AppText(
                text = ">",
                color = palette.secondaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LiveFollowAvatarRow(
    items: List<LiveRoomItem>,
    metrics: LiveBiliPaiHomeMetrics,
    onLiveClick: (Long, String, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onFollowingClick: () -> Unit,
    showMore: Boolean,
) {
    val palette = rememberLiveChromePalette()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
        items(items, key = { it.roomId }) { item ->
            Column(
                modifier = Modifier
                    .width(metrics.followItemExtentDp.dp)
                    .combinedClickable(
                        onClick = { onLiveClick(item.roomId, item.title, item.uname) },
                        onLongClick = { if (item.uid > 0) onUserClick(item.uid) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size((metrics.followAvatarSizeDp + 5).dp)
                            .clip(CircleShape)
                            .background(palette.accentStrong)
                            .padding(AppSpacingTokens.Micro)
                    ) {
                        AsyncImage(
                            model = item.face.ifBlank { item.cover },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(palette.surface)
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacingTokens.Small))
                AppText(
                    text = item.uname,
                    color = palette.primaryText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (showMore) {
            item(key = "more_following") {
                AppIconButton(onClick = onFollowingClick, modifier = Modifier.size(48.dp)) {
                    AppText(">", modifier = Modifier.semantics { contentDescription = "更多关注直播" })
                }
            }
        }
    }
}

@Composable
private fun LiveAreaHomeChipRow(
    areaEntries: List<com.android.purebilibili.data.model.response.LiveFeedAreaEntry>,
    selectedAreaIndex: Int,
    showFirstFrame: Boolean,
    onAreaSelected: (Int) -> Unit,
    onToggleFirstFrame: () -> Unit,
    onAreaListClick: () -> Unit,
    onMatchClick: () -> Unit,
) {
    val categoryOptions = remember(areaEntries) {
        buildList {
            add(AppSegmentOption(LIVE_HOME_RECOMMEND_INDEX, "推荐"))
            areaEntries.forEachIndexed { index, entry ->
                add(
                    AppSegmentOption(
                        value = resolveLiveHomeSelectedIndexForArea(index),
                        label = entry.title,
                    ),
                )
            }
        }
    }
    val selectedCategory = categoryOptions
        .firstOrNull { it.value == selectedAreaIndex }
        ?.value
        ?: LIVE_HOME_RECOMMEND_INDEX
    val uiStyle = LocalAppUiStyle.current
    val categoryMinWidth = if (uiStyle == AppUiStyle.MATERIAL3) {
        AppChromeSizeTokens.MinimumTouchTarget
    } else {
        rememberAppTopChromePolicy()
            .compactChromeSpec
            .let(::resolveLiveHomeCategorySegmentedControlSpec)
            .itemWidthDp
            ?.dp
            ?: 82.dp
    }

    // MD3 follows the app-wide animated underline. Miuix uses the shared moving
    // capsule, which automatically opts into global liquid-glass reuse when enabled.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppThemeAdaptiveTabRow(
            role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
            centerContent = false,
            options = categoryOptions,
            selectedValue = selectedCategory,
            onSelectionChange = onAreaSelected,
            scrollable = true,
            minTabWidth = categoryMinWidth,
            modifier = Modifier.weight(1f),
        )
        AppIconButton(
            onClick = onToggleFirstFrame,
            modifier = Modifier.size(40.dp),
        ) {
            AppIcon(
                imageVector = if (showFirstFrame) {
                    Icons.Outlined.Photo
                } else {
                    Icons.Outlined.Image
                },
                contentDescription = if (showFirstFrame) "显示封面" else "显示首帧",
                modifier = Modifier.size(18.dp),
            )
        }
        AppIconButton(
            onClick = onMatchClick,
            modifier = Modifier.size(40.dp),
        ) {
            AppIcon(
                imageVector = Icons.Outlined.SportsEsports,
                contentDescription = "游戏赛事",
                modifier = Modifier.size(18.dp),
            )
        }
        AppIconButton(
            onClick = onAreaListClick,
            modifier = Modifier.size(40.dp),
        ) {
            AppIcon(
                imageVector = Icons.Outlined.Widgets,
                contentDescription = "全部标签",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun LiveSortTagChipRow(
    tags: List<com.android.purebilibili.data.model.response.LiveSecondSortTag>,
    selectedSortType: String?,
    onSortTagSelected: (String?) -> Unit,
) {
    if (tags.isEmpty()) return
    val options = remember(tags) {
        tags.map { tag ->
            AppSegmentOption(
                value = tag.sortType,
                label = tag.name.ifBlank { tag.sortType },
            )
        }
    }
    val selectedValue = selectedSortType
        ?.takeIf { selected -> options.any { it.value == selected } }
        ?: options.first().value
    val uiStyle = LocalAppUiStyle.current
    val sortTagMinWidth = if (uiStyle == AppUiStyle.MATERIAL3) {
        AppChromeSizeTokens.MinimumTouchTarget
    } else {
        72.dp
    }
    AppThemeAdaptiveTabRow(
        role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
        centerContent = false,
        options = options,
        selectedValue = selectedValue,
        onSelectionChange = { value ->
            onSortTagSelected(value.takeIf { it.isNotBlank() })
        },
        scrollable = true,
        minTabWidth = sortTagMinWidth,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun LiveHomeLoadMoreFooter(
    isLoadingMore: Boolean,
    contentCount: Int,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
) {
    // 列表末项进入可见区域时自动请求下一页。
    LaunchedEffect(contentCount, isLoadingMore, hasMore, error) {
        if (hasMore && !isLoadingMore && error == null) {
            onLoadMore()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacingTokens.Medium),
        contentAlignment = Alignment.Center,
    ) {
        if (error != null) {
            AppOutlinedButton(onClick = onLoadMore) {
                AppText("$error · 重试")
            }
        } else {
            AppText(
                text = if (isLoadingMore) "加载更多…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveAreaChildChipRow(
    items: List<com.android.purebilibili.data.model.response.LiveAreaChild>,
    parentAreaId: Int,
    onAreaDetailClick: (Int, Int, String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
        items(items, key = { it.id }) { child ->
            LiveHomeSelectableChip(
                label = child.name,
                selected = false,
                compact = true,
                onClick = {
                    onAreaDetailClick(
                        parentAreaId,
                        child.id.toIntOrNull() ?: 0,
                        child.name
                    )
                },
            )
        }
    }
}

@Composable
private fun LiveListLoadingState() {
    val palette = rememberLiveChromePalette()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppSpacingTokens.ExtraLarge),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = "直播内容加载中…",
            color = palette.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LiveListErrorState(
    message: String,
    onRetry: () -> Unit
) {
    val palette = rememberLiveChromePalette()
    Column(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppText(
            text = message,
            color = palette.primaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(AppSpacingTokens.Medium))
        AppOutlinedButton(onClick = onRetry) {
            AppText("重试")
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    visualSpec: LiveVisualSpec,
) {
    val palette = rememberLiveChromePalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacingTokens.DoubleExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
    ) {
        Box(
            modifier = Modifier
                .size(visualSpec.emptyStateContainerSizeDp.dp)
                .clip(CircleShape)
                .background(palette.surfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = palette.secondaryText,
                modifier = Modifier.size(visualSpec.emptyStateIconSizeDp.dp)
            )
        }
        AppText(
            text = message,
            color = palette.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun LiveRoomItem.toLiveRoomCardUiModel(preferFirstFrame: Boolean) = LiveRoomCardUiModel(
    roomId = roomId,
    title = title,
    coverUrl = resolvedCover(preferFirstFrame).ifBlank { face },
    hostName = uname,
    viewerCount = online,
    viewerText = viewerText,
    areaName = areaName,
)
