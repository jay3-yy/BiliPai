package com.android.purebilibili.feature.live

import androidx.compose.foundation.border

import com.android.purebilibili.core.ui.AppContentColors

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Widgets
import com.android.purebilibili.core.ui.components.AppFilledIconButton
import com.android.purebilibili.core.ui.components.AppIconButtonDefaults
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.flow.distinctUntilChanged
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
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
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.LiveAreaParent
import com.android.purebilibili.data.repository.LiveRepository
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

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
    val feedback: List<com.android.purebilibili.data.model.response.LiveFeedbackGroup> = emptyList(),
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
    val accountIdentity: com.android.purebilibili.core.store.AccountSessionIdentity = com.android.purebilibili.core.store.AccountSessionIdentity(),
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
    /** 账号首轮模块结束前，整页只显示骨架。 */
    val initialLoadComplete: Boolean = false,
    /** 切分区时重新置为 false；普通刷新保留已有内容。 */
    val contentLoadComplete: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val showFirstFrame: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val livingCount: Int? = null,
    val followError: String? = null,
    val refreshCompletion: Long = 0L,
)

class LiveListViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LiveListUiState(isLoading = true, accountIdentity = com.android.purebilibili.core.store.TokenManager.accountIdentity.value))
    val uiState = _uiState.asStateFlow()
    private val requests = LiveBrowseRequestPolicy()
    private var refreshEpoch = 0L
    private var pageLoadJob: kotlinx.coroutines.Job? = null
    private var topRefreshJob: kotlinx.coroutines.Job? = null
    private var accountIdentity = com.android.purebilibili.core.store.TokenManager.accountIdentity.value

    init {
        refresh()
    }

    fun onAccountChanged() {
        val current = com.android.purebilibili.core.store.TokenManager.accountIdentity.value
        if (current == accountIdentity) return
        accountIdentity = current
        topRefreshJob?.cancel()
        pageLoadJob?.cancel()
        requests.invalidate()
        _uiState.value = _uiState.value.copy(accountIdentity = current, initialLoadComplete = false, contentLoadComplete = false, contentItems = emptyList(), followItems = emptyList(), livingCount = null, followError = null, areaEntries = emptyList())
        refresh()
    }

    fun refresh() {
        val request = requests.refresh() ?: return
        topRefreshJob?.cancel()
        pageLoadJob?.cancel()
        val state = _uiState.value
        val epoch = ++refreshEpoch
        val identity = com.android.purebilibili.core.store.TokenManager.accountIdentity.value
        _uiState.value = state.copy(isLoading = true, isLoadingMore = false, error = null, loadMoreError = null)
        pageLoadJob = viewModelScope.launch {
            val modules = if (state.selectedAreaIndex != LIVE_HOME_RECOMMEND_INDEX) async {
                LiveRepository.getLiveFeedHome(page = 1, moduleSelect = true)
            } else null
            loadPage(request, state)
            modules?.await()?.onSuccess { snapshot ->
                if (epoch == refreshEpoch && identity == com.android.purebilibili.core.store.TokenManager.accountIdentity.value) {
                    val current = _uiState.value
                    val entries = snapshot.areaEntries.ifEmpty { current.areaEntries }
                    // 解析失败（-1，如快照缺少该分区）视为未知，保留当前选中，
                    // 不能用 -1 覆盖导致指示器退回推荐。
                    val resolvedIndex = resolveRefreshedLiveHomeAreaIndex(current.selectedAreaIndex, current.selectedParentAreaId, current.selectedAreaId, entries)
                    _uiState.value = current.copy(
                        selectedAreaIndex = if (resolvedIndex >= 0) resolvedIndex else current.selectedAreaIndex,
                        followItems = if (snapshot.hasFollowModule && snapshot.followError == null) snapshot.followRooms.map { it.toLiveRoomItem() } else current.followItems,
                        livingCount = if (snapshot.hasFollowModule && snapshot.followError == null) snapshot.totalCount else current.livingCount,
                        followError = snapshot.followError,
                        areaEntries = entries,
                    )
                }
            }
            if (epoch == refreshEpoch && _uiState.value.areaList.isEmpty()) {
                LiveRepository.getLiveAreaIndex().onSuccess { areas ->
                    if (epoch == refreshEpoch && identity == com.android.purebilibili.core.store.TokenManager.accountIdentity.value) {
                        _uiState.value = _uiState.value.copy(areaList = areas)
                    }
                }
            }
            if (epoch == refreshEpoch && identity == com.android.purebilibili.core.store.TokenManager.accountIdentity.value) {
                // 首次进入时，列表、关注模块与分区目录准备完成后统一揭示内容。
                _uiState.value = _uiState.value.copy(isLoading = false, initialLoadComplete = true, contentLoadComplete = true, refreshCompletion = epoch)
            }
        }
    }

    fun refreshTop() {
        if (_uiState.value.isLoading || !_uiState.value.initialLoadComplete) return
        topRefreshJob?.cancel()
        val identity = com.android.purebilibili.core.store.TokenManager.accountIdentity.value
        val epoch = refreshEpoch
        topRefreshJob = viewModelScope.launch {
            LiveRepository.getLiveFeedHome(page = 1, moduleSelect = true).onSuccess { snapshot ->
                if (epoch != refreshEpoch || identity != com.android.purebilibili.core.store.TokenManager.accountIdentity.value) return@onSuccess
                val current = _uiState.value
                val entries = snapshot.areaEntries
                // 同 refresh()：解析失败视为未知，保留当前选中，避免指示器退回推荐。
                val resolvedIndex = resolveRefreshedLiveHomeAreaIndex(current.selectedAreaIndex, current.selectedParentAreaId, current.selectedAreaId, entries)
                _uiState.value = current.copy(
                    followItems = if (snapshot.hasFollowModule && snapshot.followError == null) snapshot.followRooms.map { it.toLiveRoomItem() } else current.followItems,
                    livingCount = if (snapshot.hasFollowModule && snapshot.followError == null) snapshot.totalCount else current.livingCount,
                    followError = snapshot.followError,
                    areaEntries = entries,
                    selectedAreaIndex = if (resolvedIndex >= 0) resolvedIndex else current.selectedAreaIndex,
                )
            }
        }
    }

    suspend fun submitFeedback(
        roomId: Long,
        reason: com.android.purebilibili.data.model.response.LiveFeedbackReason,
    ): Result<Unit> {
        val identity = com.android.purebilibili.core.store.TokenManager.accountIdentity.value
        return LiveRepository.submitLiveFeedFeedback(roomId, reason).onSuccess {
            if (identity == com.android.purebilibili.core.store.TokenManager.accountIdentity.value) {
                _uiState.value = _uiState.value.copy(contentItems = _uiState.value.contentItems.filterNot { it.roomId == roomId })
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        val request = requests.loadMore(state.hasMore) ?: return
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        pageLoadJob = viewModelScope.launch { loadPage(request, state) }
    }

    private fun reloadSelection() {
        refreshEpoch += 1L
        requests.invalidate()
        _uiState.value = _uiState.value.copy(
            contentItems = emptyList(), contentLoadComplete = false, isLoadingMore = false, loadMoreError = null,
        )
        refresh()
    }

    // 首页结果只提交数据；整轮 refresh 完成顶部模块后才结束加载与骨架。
    private suspend fun loadPage(request: LiveBrowseRequestPolicy.Request, state: LiveListUiState) {
        when {
            state.selectedAreaIndex == LIVE_HOME_RECOMMEND_INDEX -> loadRecommendPage(request)
            isLiveHomeFollowedTab(state.selectedAreaIndex) -> loadFollowedPage(request)
            else -> loadAreaPage(request, state)
        }
    }

    fun selectHomeArea(index: Int) {
        val state = _uiState.value
        // 加载中也允许改选：新的 reloadSelection 递增 refreshEpoch，旧加载结果按 epoch 丢弃。
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
        // 加载中也允许改选：reloadSelection 的 refreshEpoch 会让旧结果失效。
        if (state.selectedParentAreaId <= 0) return
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
                    followItems = if (!append && snapshot.hasFollowModule && snapshot.followError == null) followMapped else current.followItems,
                    livingCount = if (!append && snapshot.hasFollowModule && snapshot.followError == null) snapshot.totalCount else current.livingCount,
                    followError = if (!append) snapshot.followError else current.followError,
                    areaEntries = areaEntries,
                    page = page,
                    hasMore = snapshot.hasMore,
                    isLoading = !append,
                    isLoadingMore = false,
                    error = if (mergedRooms.isEmpty() && !append) "暂无直播" else null,
                )
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!requests.fail(request)) return@fold
                _uiState.value = _uiState.value.copy(
                    isLoading = !append,
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
                    followError = if (!append) null else current.followError,
                    page = page,
                    hasMore = snapshot.hasMore && (!append || mapped.isEmpty() || mergedRooms.size > current.contentItems.size),
                    isLoading = !append,
                    isLoadingMore = false,
                    // 空关注列表是正常内容状态，仍需保留顶部分类，方便切回推荐直播。
                    error = null,
                )
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!requests.fail(request)) return@fold
                _uiState.value = _uiState.value.copy(
                    isLoading = !append,
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
                isLoading = !append,
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
                    isLoading = !append,
                    isLoadingMore = false,
                    error = if (merged.isEmpty() && !append) "暂无直播内容" else null,
                )
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!requests.fail(request)) return@fold
                _uiState.value = _uiState.value.copy(
                    isLoading = !append,
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
        feedback = feedback,
    )

@Composable
fun LiveListScreen(
    onBack: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onSearchClick: () -> Unit,
    onAreaListClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onAreaDetailClick: (Int, Int, String) -> Unit,
    onMatchClick: () -> Unit,
    onUserClick: (Long) -> Unit = {},
    /** 底栏主入口时隐藏返回，更接近 BiliPai 主 tab 形态。 */
    showNavigationBack: Boolean = true,
    user: com.android.purebilibili.feature.home.UserState = com.android.purebilibili.feature.home.UserState(),
    onAvatarClick: () -> Unit = {},
    /** 嵌入首页时共用首页顶栏和采样层，顶部避让通过列表内容内边距提供。 */
    embeddedInHome: Boolean = false,
    isCurrentPage: Boolean = true,
    contentTopPadding: androidx.compose.ui.unit.Dp = AppSpacingTokens.None,
    scrollToTopRequestId: Int = 0,
    scrollToTopChannel: Channel<Unit>? = null,
    viewModel: LiveListViewModel = viewModel(),
    globalHazeState: HazeState? = null
) {
    val collectedState by viewModel.uiState.collectAsStateWithLifecycle()
    val accountIdentity by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val state = if (collectedState.accountIdentity == accountIdentity) collectedState
        else LiveListUiState(isLoading = true, accountIdentity = accountIdentity)
    LaunchedEffect(accountIdentity) { viewModel.onAccountChanged() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (isCurrentPage) viewModel.refreshTop()
    }
    val context = LocalContext.current
    val palette = rememberLiveChromePalette()
    val coroutineScope = rememberCoroutineScope()
    var coverToSave by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }
    var feedbackCard by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }
    var feedbackSource by remember { mutableStateOf<com.android.purebilibili.core.ui.components.AppGlassMenuSource?>(null) }

    val topChromePolicy = rememberAppTopChromePolicy()
    val visualSpec = remember(topChromePolicy.tabPresentation) {
        resolveLiveVisualSpec(topChromePolicy.tabPresentation)
    }
    val metrics = visualSpec.homeMetrics
    val gridBottomPadding = LocalBottomBarContentPadding.current
    val liveGridState = rememberLazyGridState()
    val followRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 与动态列表共用底栏滚动增量，未激活的 Pager 页面不写入全局状态。
    val navigationSettings by remember(context) {
        com.android.purebilibili.core.store.SettingsManager.getAppNavigationSettings(context)
    }.collectAsStateWithLifecycle(initialValue = com.android.purebilibili.core.store.AppNavigationSettings())
    val chromeScrollOffset = com.android.purebilibili.feature.home.LocalHomeScrollOffset.current
    val feedScrolling = com.android.purebilibili.feature.home.LocalHomeFeedScrollInProgress.current
    val setBottomBarVisible = com.android.purebilibili.core.ui.LocalSetBottomBarVisible.current
    val scrollWorkActive = isCurrentPage && com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val bottomBarScrollConnection = com.android.purebilibili.core.ui.rememberBottomBarScrollHideConnection(
        chromeScrollOffset = chromeScrollOffset,
        autoHideEnabled = com.android.purebilibili.core.ui.shouldAutoHideBottomBarOnScroll(navigationSettings.bottomBarVisibilityMode),
        isAtTop = { liveGridState.firstVisibleItemIndex == 0 &&
            liveGridState.firstVisibleItemScrollOffset < com.android.purebilibili.core.ui.BottomBarScrollHideTopRevealPx },
        isActivePage = scrollWorkActive,
        onVisibilityIntent = { intent ->
            setBottomBarVisible(intent == com.android.purebilibili.core.ui.BottomBarScrollHideIntent.SHOW)
        },
    )
    LaunchedEffect(scrollWorkActive, liveGridState) {
        if (!scrollWorkActive) return@LaunchedEffect
        snapshotFlow { liveGridState.isScrollInProgress }
            .distinctUntilChanged().collect { feedScrolling.value = it }
    }
    DisposableEffect(scrollWorkActive) {
        onDispose { if (scrollWorkActive) feedScrolling.value = false }
    }
    LaunchedEffect(scrollWorkActive) {
        if (scrollWorkActive) {
            chromeScrollOffset.floatValue = com.android.purebilibili.feature.home.policy.resolveBottomBarChromeScrollOffset(
                liveGridState.firstVisibleItemIndex, liveGridState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(state.refreshCompletion) {
        if (state.refreshCompletion > 0L) followRowState.scrollToItem(0)
    }
    suspend fun scrollLiveHomeToTop(refreshAtTop: Boolean = true) {
        val atTop = liveGridState.firstVisibleItemIndex == 0 &&
            liveGridState.firstVisibleItemScrollOffset < 50
        if (!refreshAtTop) {
            liveGridState.scrollToItem(0)
        } else if (!atTop) {
            liveGridState.animateScrollToTop()
        } else {
            viewModel.refresh()
        }
    }
    LaunchedEffect(scrollToTopRequestId) {
        if (scrollToTopRequestId <= 0) return@LaunchedEffect
        scrollLiveHomeToTop(refreshAtTop = false)
    }
    LaunchedEffect(scrollToTopChannel) {
        scrollToTopChannel?.receiveAsFlow()?.collect {
            scrollLiveHomeToTop()
        }
    }

    // 顶部避让属于可滚动内容；视口保持全高，让卡片经过顶栏下方供模糊采样。
    val liveContent: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { topInset ->
        AdaptivePullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().then(if (embeddedInHome) Modifier else Modifier.nestedScroll(bottomBarScrollConnection)),
            indicatorTopInset = topInset,
        ) {
            LiveHomeContent(
                gridState = liveGridState,
                followRowState = followRowState,
                contentItems = state.contentItems,
                followItems = state.followItems,
                areaEntries = state.areaEntries,
                areaList = state.areaList,
                selectedAreaIndex = state.selectedAreaIndex,
                selectedParentAreaId = state.selectedParentAreaId,
                sortTags = state.sortTags,
                selectedSortType = state.selectedSortType,
                livingCount = state.livingCount,
                showFollowSection = accountIdentity.mid != null || state.followItems.isNotEmpty() || state.livingCount != null,
                followError = state.followError,
                isLoading = state.isLoading,
                showInitialSkeleton = !state.initialLoadComplete,
                showContentSkeleton = !state.contentLoadComplete,
                error = state.error,
                onRetry = viewModel::refresh,
                isLoadingMore = state.isLoadingMore,
                hasMore = state.hasMore,
                loadMoreError = state.loadMoreError,
                showFirstFrame = state.showFirstFrame,
                bottomPadding = gridBottomPadding,
                topPadding = topInset,
                metrics = metrics,
                visualSpec = visualSpec,
                onLiveClick = onLiveClick,
                onUserClick = onUserClick,
                onAreaSelected = { index ->
                    // 加载中允许改选：reloadSelection 用 epoch 让旧结果失效，旧请求不会写回。
                    // 静默丢弃会让拖动松手后的选中被吞掉，指示器弹回旧分 Tab。
                    if (index != state.selectedAreaIndex) {
                        viewModel.selectHomeArea(index)
                        coroutineScope.launch { liveGridState.scrollToItem(0) }
                    }
                },
                onToggleFirstFrame = viewModel::toggleShowFirstFrame,
                onAreaListClick = onAreaListClick,
                onMatchClick = onMatchClick,
                onSortTagSelected = { sort ->
                    if (!state.isLoading) {
                        viewModel.selectSortTag(sort)
                        coroutineScope.launch { liveGridState.scrollToItem(0) }
                    }
                },
                onLoadMore = viewModel::loadMore,
                onAreaDetailClick = onAreaDetailClick,
                onFollowingClick = onFollowingClick,
                onLongPressCard = { coverToSave = it },
                onFeedback = { card, source -> feedbackSource = source; feedbackCard = card },
            )
        }
    }
    if (embeddedInHome) {
        // 首页负责顶栏及背景采样，嵌入页只提供内容内边距，不再缩短滚动视口。
        Box(Modifier.fillMaxSize().background(palette.backgroundBrush())) {
            liveContent(contentTopPadding)
        }
    } else {
        // 独立直播页的搜索胶囊只采样列表兄弟层，不能把顶栏录入自己的玻璃背景。
        val searchBackdrop = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
            com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
        } else null
        ImmersiveAppScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = palette.backgroundTop,
            topBarSurfaceColor = palette.backgroundTop,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            blurContentReady = state.initialLoadComplete && state.contentLoadComplete,
            chromeBackdropSource = searchBackdrop,
            topBar = {
                Box(
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
                ) {
                    LiveListHeader(
                        metrics = metrics,
                        user = user,
                        onAvatarClick = onAvatarClick,
                        showNavigationBack = showNavigationBack,
                        onBack = onBack,
                        onSearchClick = onSearchClick,
                        onAreaListClick = onAreaListClick,
                        searchBackdrop = searchBackdrop?.takeIf { it.isReady }?.backdrop,
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize()
                    .then(searchBackdrop?.modifier ?: Modifier)
                    .background(palette.backgroundBrush())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                liveContent(padding.calculateTopPadding())
            }
        }
    }
    feedbackCard?.let { card ->
        LiveFeedbackDialog(
            card = card,
            source = feedbackSource,
            onSubmit = { reason -> viewModel.submitFeedback(card.roomId, reason) },
            onDismiss = { feedbackCard = null },
        )
    }
    coverToSave?.let { card -> LiveCoverSaveDialog(card) { coverToSave = null } }

}

@Composable
internal fun LiveHomeContent(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    followRowState: androidx.compose.foundation.lazy.LazyListState,
    contentItems: List<LiveRoomItem>,
    followItems: List<LiveRoomItem>,
    areaEntries: List<com.android.purebilibili.data.model.response.LiveFeedAreaEntry>,
    areaList: List<LiveAreaParent>,
    selectedAreaIndex: Int,
    selectedParentAreaId: Int,
    sortTags: List<com.android.purebilibili.data.model.response.LiveSecondSortTag>,
    selectedSortType: String?,
    livingCount: Int?,
    showFollowSection: Boolean,
    followError: String?,
    isLoading: Boolean,
    showInitialSkeleton: Boolean,
    showContentSkeleton: Boolean,
    error: String?,
    onRetry: () -> Unit,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    showFirstFrame: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    topPadding: androidx.compose.ui.unit.Dp,
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
    onLongPressCard: (LiveRoomCardUiModel) -> Unit = {},
    onFeedback: (LiveRoomCardUiModel, com.android.purebilibili.core.ui.components.AppGlassMenuSource) -> Unit = { _, _ -> },
) {
    val selectedParent = areaList.firstOrNull { it.id == selectedParentAreaId }
        ?: areaList.firstOrNull {
            areaEntries.getOrNull(resolveLiveHomeAreaListIndex(selectedAreaIndex).coerceAtLeast(0))?.parentAreaId == it.id
        }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val skeletonLayout = rememberLiveSkeletonLayout(maxWidth, maxHeight, metrics)
        val skeletonColor = com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor(
            com.android.purebilibili.core.ui.skeleton.rememberSkeletonSweepState(
                animated = showInitialSkeleton || showContentSkeleton,
            ),
        )
        LazyVerticalGrid(
            columns = LiveRoomGridCells(),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = metrics.safeSpaceDp.dp,
                end = metrics.safeSpaceDp.dp,
                top = topPadding + metrics.cardSpaceDp.dp,
                bottom = maxOf(bottomPadding, 100.dp)
            ),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpaceDp.dp),
            verticalArrangement = Arrangement.spacedBy(metrics.cardSpaceDp.dp)
        ) {
            if (showInitialSkeleton) {
                liveHomeHeaderSkeletonItems(showFollowSection, metrics, skeletonColor)
                liveRoomSkeletonItems(skeletonLayout.cardCount, metrics, skeletonColor)
                return@LazyVerticalGrid
            }
            if (showFollowSection) {
                item(key = "live_follow_header", span = { GridItemSpan(maxLineSpan) }) {
                    LiveFollowHeader(
                        livingCount = livingCount,
                        onActionClick = onFollowingClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (followItems.isNotEmpty()) item(key = "live_follow_avatars", span = { GridItemSpan(maxLineSpan) }) {
                    LiveFollowAvatarRow(
                        items = followItems,
                        listState = followRowState,
                        metrics = metrics,
                        onLiveClick = onLiveClick,
                        onUserClick = onUserClick,
                        onFollowingClick = onFollowingClick,
                        showMore = (livingCount ?: 0) > followItems.size,
                    )
                }
            }
            if (showFollowSection && followError != null) item(key = "live_follow_error", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(followError, modifier = Modifier.weight(1f), color = rememberLiveChromePalette().secondaryText)
                    AppTextButton(onClick = onRetry) { AppText("重试") }
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
            if (selectedParentAreaId > 0 && sortTags.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LiveSortTagChipRow(
                            tags = sortTags,
                            selectedSortType = selectedSortType,
                            onSortTagSelected = onSortTagSelected,
                        )
                    }
                }
            when {
                isLoading && contentItems.isEmpty() -> {
                    liveRoomSkeletonItems(skeletonLayout.cardCount, metrics, skeletonColor)
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
                            onLongPress = { onLongPressCard(model) },
                            onFeedback = { source -> onFeedback(model, source) },
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
}

@Composable
private fun LiveListHeader(
    metrics: LiveBiliPaiHomeMetrics,
    user: com.android.purebilibili.feature.home.UserState,
    onAvatarClick: () -> Unit,
    showNavigationBack: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onAreaListClick: () -> Unit,
    searchBackdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
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
                // 头像在玻璃与原生主题中共用账号来源和尺寸，材质由顶栏容器负责。
                AppSurface(
                    onClick = onAvatarClick,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    com.android.purebilibili.feature.home.components.HomeTopAvatarContent(
                        user = user,
                        shape = CircleShape,
                        fallbackBackgroundColor = palette.surface,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            // 中间槽位弹性占位，胶囊本身限制宽度；手机、窄分屏会按可用宽度收缩。
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock(
                    shape = AppShapes.container(ContainerLevel.Pill),
                    modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth(),
                    backdrop = searchBackdrop,
                    reuseEnabled = true,
                    elasticFeedbackEnabled = true,
                    useNeutralLiquidContainer = true,
                    shellLensIntensity = com.android.purebilibili.feature.home.components
                        .resolveFloatingDockGeometryScale(compactChrome.primaryHeightDp.toFloat()),
                ) { liquidChromeActive ->
                    // 玻璃外壳拥有材质；关闭时保留原生 Surface 的颜色、形状与点击行为。
                    AppSurface(
                        onClick = onSearchClick,
                        color = if (liquidChromeActive) Color.Transparent else palette.searchField,
                        shape = AppShapes.container(ContainerLevel.Pill),
                        modifier = Modifier.fillMaxWidth().height(compactChrome.primaryHeightDp.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = compactChrome.inputHorizontalPaddingDp.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = palette.secondaryText,
                            )
                            Spacer(Modifier.width(AppSpacingTokens.Medium))
                            AppText(
                                text = "搜索直播间 / 主播",
                                color = palette.secondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            // 关注直播入口收敛到页内「已关注」分类与关注主播行，头部不再重复；
            // 分区入口使用诚实的分区图标，不再用关注主播头像伪装。
            AppSurface(
                onClick = onAreaListClick,
                color = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .size(AppSpacingTokens.TripleExtraLarge)
                    .semantics { contentDescription = "全部直播分区" }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = "全部直播分区",
                        tint = palette.primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveFollowHeader(
    livingCount: Int?,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberLiveChromePalette()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = androidx.compose.ui.text.buildAnnotatedString {
                append("我的关注  ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = palette.accentStrong))
                append(livingCount?.toString() ?: "—")
                pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = palette.secondaryText))
                append(" 人正在直播")
                pop()
            },
            modifier = Modifier.weight(1f),
            color = palette.primaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    metrics: LiveBiliPaiHomeMetrics,
    onLiveClick: (Long, String, String) -> Unit,
    onUserClick: (Long) -> Unit,
    onFollowingClick: () -> Unit,
    showMore: Boolean,
) {
    val palette = rememberLiveChromePalette()
    LazyRow(state = listState) {
        items(items, key = { it.roomId }) { item ->
            Column(
                modifier = Modifier
                    .width(metrics.followItemExtentDp.dp)
                    .liveSecondaryClick { if (item.uid > 0) onUserClick(item.uid) }
                    .combinedClickable(
                        onClick = { onLiveClick(item.roomId, item.title, item.uname) },
                        onLongClick = { if (item.uid > 0) onUserClick(item.uid) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size((metrics.followAvatarSizeDp + 4).dp)
                            .then(if (item.liveStatus == 1) Modifier.border(1.5.dp, palette.accentStrong, CircleShape) else Modifier)
                            .padding(2.dp)
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
                    if (item.liveStatus == 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .background(palette.accentStrong, CircleShape)
                                .border(1.5.dp, palette.backgroundTop, CircleShape)
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.android.purebilibili.core.ui.components.PlaybackBarsIndicator(
                                isPlaying = true,
                                color = Color.White,
                                contentDescription = "正在直播",
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                AppText(
                    text = item.uname,
                    color = palette.primaryText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
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
    val selectedCategory = selectedAreaIndex
    val quickActionColors = AppIconButtonDefaults.colors(
        containerColor = com.android.purebilibili.core.ui.AppSurfaceTokens.secondaryContainer(),
        contentColor = com.android.purebilibili.core.ui.AppSurfaceTokens.onSecondaryContainer(),
    )
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (liquidGlassEnabled) {
            AppThemeAdaptiveTabRow(
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                centerContent = false,
                options = categoryOptions,
                selectedValue = selectedCategory,
                onSelectionChange = onAreaSelected,
                scrollable = true,
                minTabWidth = 40.dp,
                liquidGlassLabelHorizontalPadding = 4.dp,
                // 直播分类长短差异明显，玻璃指示器显式随标签宽度变化；
                // 标签集合异步更新时仍平滑调整槽宽，关闭玻璃后沿用原生标签布局。
                liquidGlassAdaptIndicatorToLabel = true,
                liquidGlassAnimateLabelWidthChanges = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(categoryOptions, key = { it.value }) { option ->
                    LiveHomeSelectableChip(
                        label = option.label,
                        selected = option.value == selectedCategory,
                        onClick = { onAreaSelected(option.value) },
                    )
                }
            }
        }
        AppFilledIconButton(
            onClick = onToggleFirstFrame,
            modifier = Modifier.size(26.dp),
            colors = quickActionColors,
        ) {
            AppIcon(
                imageVector = if (showFirstFrame) {
                    LiveFirstFrameIcon
                } else {
                    Icons.Filled.Image
                },
                contentDescription = if (showFirstFrame) "显示封面" else "显示首帧",
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        AppFilledIconButton(
            onClick = onMatchClick,
            modifier = Modifier.size(26.dp),
            colors = quickActionColors,
        ) {
            AppIcon(
                imageVector = Icons.Filled.Gamepad,
                contentDescription = "游戏赛事",
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        AppFilledIconButton(
            onClick = onAreaListClick,
            modifier = Modifier.size(26.dp),
            colors = quickActionColors,
        ) {
            AppIcon(
                imageVector = Icons.Filled.Widgets,
                contentDescription = "全部标签",
                modifier = Modifier.size(16.dp),
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
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    if (liquidGlassEnabled) {
        AppThemeAdaptiveTabRow(
            // 排序标签与分类共用玻璃动态宽度策略，原生分支保留自身布局。
            liquidGlassAdaptIndicatorToLabel = true,
            liquidGlassAnimateLabelWidthChanges = true,
            role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
            centerContent = false,
            options = options,
            selectedValue = selectedValue,
            onSelectionChange = { onSortTagSelected(it.takeIf(String::isNotBlank)) },
            scrollable = true,
            minTabWidth = 40.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(options, key = { it.value }) { option ->
                LiveHomeSelectableChip(
                    label = option.label,
                    selected = selectedValue == option.value,
                    onClick = { onSortTagSelected(option.value.takeIf(String::isNotBlank)) },
                    compact = true,
                )
            }
        }
    }
}

@Composable
internal fun LiveHomeLoadMoreFooter(
    isLoadingMore: Boolean,
    contentCount: Int,
    hasMore: Boolean,
    error: String?,
    autoLoad: Boolean = true,
    onLoadMore: () -> Unit,
) {
    // 自行观察视口的列表关闭 autoLoad，避免预组合页脚时提前触发请求。
    LaunchedEffect(contentCount, isLoadingMore, hasMore, error, autoLoad) {
        if (autoLoad && hasMore && !isLoadingMore && error == null) {
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
            // 空闲时保留单行占位，加载状态切换不改变列表底部高度。
            AppText(
                text = if (isLoadingMore) "加载更多…" else " ",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
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
    LazyRow {
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
    feedback = feedback,
)
