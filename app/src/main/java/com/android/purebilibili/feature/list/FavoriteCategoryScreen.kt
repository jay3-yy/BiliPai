package com.android.purebilibili.feature.list

import com.android.purebilibili.core.ui.components.appActionWithFeedback

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.CompatibleStaggeredGridStateSaver
import com.android.purebilibili.core.ui.createCompatibleStaggeredGridState

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalStaggeredGrid as LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import com.android.purebilibili.core.ui.components.AppFloatingToolbar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppGlassMenuAnchor
import com.android.purebilibili.core.ui.components.AppSnackbarHost
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.FavoritePgcStatus
import com.android.purebilibili.data.model.response.FavoriteSection
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.data.repository.FavoriteCategoryItem
import com.android.purebilibili.data.repository.FavoriteCategoryPage
import com.android.purebilibili.data.repository.PersonalFavoriteRepository
import com.android.purebilibili.feature.personal.PERSONAL_LIST_POSTER_ASPECT_RATIO
import com.android.purebilibili.feature.personal.PersonalMediaCardFrame
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FavoriteCategoryUiState(
    val section: FavoriteSection = FavoriteSection.BANGUMI,
    val items: List<FavoriteCategoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val pgcStatus: FavoritePgcStatus = FavoritePgcStatus.WATCHING,
    val publishedNotes: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectingPgc: Boolean = false,
    val isUpdatingPgc: Boolean = false,
) {
    val isPgc: Boolean get() = section == FavoriteSection.BANGUMI || section == FavoriteSection.CINEMA
    val isSelecting: Boolean get() = isSelectingPgc || selectedIds.isNotEmpty()
}

class FavoriteCategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FavoriteCategoryUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var hasSelectedSection = false
    private var loadGeneration = 0L
    private var loadJob: Job? = null
    private val pgcLists = mutableMapOf<FavoritePgcStatus, FavoriteCategoryUiState>()
    // Moving or removing entries shifts server page boundaries, even when the loaded cards stay visible.
    private val pgcListsWithShiftedPages = mutableSetOf<FavoritePgcStatus>()

    init {
        accountRequests.observe {
            loadGeneration += 1L
            pgcLists.clear()
            pgcListsWithShiftedPages.clear()
            _uiState.value = FavoriteCategoryUiState(section = _uiState.value.section)
            if (hasSelectedSection) load(reset = true)
        }
    }

    fun selectSection(section: FavoriteSection) {
        val current = _uiState.value
        if (hasSelectedSection && current.section == section && (current.isPgc || current.items.isNotEmpty())) return
        hasSelectedSection = true
        pgcLists.clear()
        pgcListsWithShiftedPages.clear()
        _uiState.value = FavoriteCategoryUiState(
            section = section,
            pgcStatus = _uiState.value.pgcStatus,
            publishedNotes = _uiState.value.publishedNotes,
            isLoading = true,
        )
        load(reset = true)
    }

    fun selectPgcStatus(status: FavoritePgcStatus) {
        val state = _uiState.value
        if (state.pgcStatus == status || state.isUpdatingPgc) return
        loadJob?.cancel()
        loadGeneration++
        if (state.page > 0) {
            pgcLists[state.pgcStatus] = state.copy(
                isLoadingMore = false,
                isRefreshing = false,
                selectedIds = emptySet(),
                isSelectingPgc = false,
            )
        }
        val cached = pgcLists.remove(status)
        _uiState.value = cached ?: FavoriteCategoryUiState(section = state.section, pgcStatus = status)
        when {
            cached == null -> load(reset = true)
            cached.items.isEmpty() && cached.hasMore -> loadMore()
        }
    }

    fun selectPublishedNotes(published: Boolean) {
        if (_uiState.value.publishedNotes == published) return
        _uiState.update { it.copy(publishedNotes = published, selectedIds = emptySet()) }
        load(reset = true)
    }

    fun retry() {
        if (!_uiState.value.isUpdatingPgc) load(reset = true)
    }

    fun refreshPgc() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isUpdatingPgc) return
        load(reset = true, refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore || state.isRefreshing || state.isUpdatingPgc) return
        load(reset = false)
    }

    fun toggleSelection(id: Long) {
        if (id <= 0L || _uiState.value.isUpdatingPgc) return
        _uiState.update { state ->
            state.copy(
                selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id,
                isSelectingPgc = state.isPgc,
            )
        }
    }

    fun toggleAllPgcSelection(visibleIds: Set<Long>) {
        if (_uiState.value.isUpdatingPgc) return
        _uiState.update { state ->
            state.copy(
                selectedIds = if (state.selectedIds.containsAll(visibleIds)) {
                    state.selectedIds - visibleIds
                } else state.selectedIds + visibleIds,
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectingPgc = false) }
    }

    fun remove(item: FavoriteCategoryItem) {
        if (_uiState.value.isPgc) {
            val shortLabel = if (item.section == FavoriteSection.CINEMA) "追剧" else "追番"
            updatePgc(
                ids = setOf(item.id),
                successMessage = "已取消$shortLabel",
                request = { BangumiRepository.unfollowBangumi(item.id) },
            )
            return
        }
        val generation = loadGeneration
        val identity = accountRequests.identity
        accountRequests.launch {
            val state = _uiState.value
            val result = PersonalFavoriteRepository.remove(item, state.publishedNotes)
            ensureActive()
            if (!accountRequests.isCurrent(identity) || generation != loadGeneration) return@launch
            if (result.isSuccess) {
                _uiState.update { it.copy(items = it.items.filterNot { candidate -> candidate.id == item.id }) }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message ?: "移除失败") }
            }
        }
    }

    fun removeSelected() {
        val state = _uiState.value
        if (state.selectedIds.isEmpty()) return
        val generation = loadGeneration
        val identity = accountRequests.identity
        accountRequests.launch {
            val selected = state.items.filter { it.id in state.selectedIds }
            val failures = selected.map { item ->
                ensureActive()
                if (!accountRequests.isCurrent(identity)) return@launch
                when (item.section) {
                    FavoriteSection.BANGUMI, FavoriteSection.CINEMA ->
                        BangumiRepository.unfollowBangumi(item.id).map { Unit }
                    else -> PersonalFavoriteRepository.remove(item, state.publishedNotes)
                }
            }.count { it.isFailure }
            ensureActive()
            if (!accountRequests.isCurrent(identity) || generation != loadGeneration) return@launch
            if (failures == 0) {
                _uiState.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.id in state.selectedIds },
                        selectedIds = emptySet(),
                    )
                }
            } else {
                _uiState.update { it.copy(error = "部分内容移除失败：${selected.size - failures}/${selected.size}") }
            }
        }
    }

    fun updateSelectedPgcStatus(status: FavoritePgcStatus) {
        val state = _uiState.value
        if (status == state.pgcStatus) return
        updatePgc(state.selectedIds, "已标记为${status.label}", targetStatus = status) {
            BangumiRepository.updateBangumiFollowStatuses(state.selectedIds, status.value)
        }
    }

    fun updatePgcStatus(item: FavoriteCategoryItem, status: FavoritePgcStatus) {
        if (status == _uiState.value.pgcStatus) return
        updatePgc(setOf(item.id), "已标记为${status.label}", targetStatus = status) {
            BangumiRepository.updateBangumiFollowStatus(item.id, status.value)
        }
    }

    private fun updatePgc(
        ids: Set<Long>,
        successMessage: String,
        targetStatus: FavoritePgcStatus? = null,
        request: suspend () -> Result<*>,
    ) {
        val state = _uiState.value
        if (ids.isEmpty() || !state.isPgc || state.isUpdatingPgc) return
        val identity = accountRequests.identity
        _uiState.update { it.copy(isUpdatingPgc = true) }
        accountRequests.launch {
            val result = request()
            ensureActive()
            if (!accountRequests.isCurrent(identity)) return@launch
            result.fold(
                onSuccess = {
                    loadJob?.cancel()
                    loadGeneration++
                    val current = _uiState.value
                    val movedItems = state.items.filter { it.id in ids }
                    val target = targetStatus?.let(pgcLists::get)
                    if (target != null) {
                        pgcLists[target.pgcStatus] = target.copy(
                            items = movedItems + target.items.filterNot { it.id in ids },
                            error = null,
                        )
                        if (target.hasMore) pgcListsWithShiftedPages.add(target.pgcStatus)
                    }
                    if (current.hasMore) pgcListsWithShiftedPages.add(current.pgcStatus)
                    val remaining = current.items.filterNot { it.id in ids }
                    _uiState.value = current.copy(
                        items = remaining,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        error = null,
                        selectedIds = emptySet(),
                        isSelectingPgc = false,
                        isUpdatingPgc = false,
                    )
                    if (remaining.isEmpty() && current.hasMore) loadMore()
                    _messages.emit(successMessage)
                },
                onFailure = {
                    _uiState.update { current -> current.copy(isUpdatingPgc = false) }
                    _messages.emit(it.message ?: "更新状态失败")
                },
            )
        }
    }

    private fun load(reset: Boolean, refresh: Boolean = false) {
        if (TokenManager.sessDataCache.isNullOrEmpty()) {
            _uiState.value = FavoriteCategoryUiState(section = _uiState.value.section)
            return
        }
        loadJob?.cancel()
        val generation = ++loadGeneration
        val identity = accountRequests.identity
        val requestState = _uiState.value
        val requestPage = if (reset) 1 else requestState.page + 1
        val rebase = !reset && requestState.isPgc && requestState.pgcStatus in pgcListsWithShiftedPages
        _uiState.update {
            if (reset) it.copy(
                items = if (refresh) it.items else emptyList(),
                selectedIds = emptySet(),
                isSelectingPgc = false,
                isLoading = !refresh,
                isLoadingMore = false,
                isRefreshing = refresh,
                error = null,
                page = if (refresh) it.page else 0,
                hasMore = if (refresh) it.hasMore else false,
            )
            else it.copy(isLoadingMore = true, error = null)
        }
        loadJob = accountRequests.launch {
            val result = when (requestState.section) {
                FavoriteSection.BANGUMI, FavoriteSection.CINEMA -> if (rebase) {
                    reloadPgcPages(requestState.section, requestState.pgcStatus, requestPage)
                } else {
                    loadPgc(requestState.section, requestState.pgcStatus, requestPage)
                }
                FavoriteSection.ARTICLE -> PersonalFavoriteRepository.getArticles(requestPage)
                FavoriteSection.NOTE -> PersonalFavoriteRepository.getNotes(requestPage, requestState.publishedNotes)
                FavoriteSection.TOPIC -> PersonalFavoriteRepository.getTopics(requestPage)
                FavoriteSection.COURSE -> PersonalFavoriteRepository.getCourses(requestPage)
                FavoriteSection.VIDEO -> Result.success(
                    FavoriteCategoryPage(emptyList(), false)
                )
            }
            ensureActive()
            if (!accountRequests.isCurrent(identity) || generation != loadGeneration) return@launch
            result.fold(
                onSuccess = { page ->
                    if (reset || rebase) pgcListsWithShiftedPages.remove(requestState.pgcStatus)
                    _uiState.update { current ->
                        current.copy(
                            items = if (reset || rebase) page.items
                            else (current.items + page.items).distinctBy { it.id },
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            page = requestPage,
                            hasMore = page.hasMore,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            error = if (refresh && it.items.isNotEmpty()) null else error.message ?: "加载失败",
                        )
                    }
                    if (refresh && _uiState.value.items.isNotEmpty()) {
                        _messages.emit(error.message ?: "刷新失败")
                    }
                },
            )
        }
    }

    private suspend fun reloadPgcPages(
        section: FavoriteSection,
        status: FavoritePgcStatus,
        lastPage: Int,
    ): Result<FavoriteCategoryPage> {
        val items = mutableListOf<FavoriteCategoryItem>()
        var hasMore = true
        for (page in 1..lastPage) {
            val response = loadPgc(section, status, page).getOrElse { return Result.failure(it) }
            items += response.items
            hasMore = response.hasMore
            if (!hasMore) break
        }
        return Result.success(FavoriteCategoryPage(items.distinctBy { it.id }, hasMore))
    }

    private suspend fun loadPgc(
        section: FavoriteSection,
        status: FavoritePgcStatus,
        page: Int,
    ): Result<FavoriteCategoryPage> {
        val pageSize = 30
        return BangumiRepository.getMyFollowBangumi(
            type = if (section == FavoriteSection.CINEMA) 2 else 1,
            followStatus = status.value,
            page = page,
            pageSize = pageSize,
        ).map { data ->
            val items = data.list.orEmpty()
                .map { pgc ->
                    FavoriteCategoryItem(
                        id = pgc.seasonId,
                        title = pgc.title,
                        subtitle = pgc.progress.ifBlank { pgc.evaluate },
                        cover = pgc.cover,
                        url = pgc.url,
                        badge = listOf(pgc.badge, pgc.newEp?.indexShow.orEmpty())
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        section = section,
                    )
                }
            FavoriteCategoryPage(
                items = items,
                hasMore = data.hasNextPage(page),
            )
        }
    }
}

private val FavoritePgcGridStatesSaver: Saver<MutableMap<String, LazyStaggeredGridState>, Any> = mapSaver(
    save = { states ->
        states.mapValues { (_, state) ->
            with(CompatibleStaggeredGridStateSaver) { save(state) }
        }
    },
    restore = { saved ->
        saved.mapNotNull { (name, value) ->
            value?.let(CompatibleStaggeredGridStateSaver::restore)?.let { name to it }
        }.toMap(mutableMapOf())
    },
)

@Composable
internal fun rememberFavoritePgcGridState(
    section: FavoriteSection,
    status: FavoritePgcStatus,
    accountMid: Long?,
    accountGeneration: Long,
): LazyStaggeredGridState? {
    val states = key(accountMid, accountGeneration) {
        rememberSaveable(saver = FavoritePgcGridStatesSaver) { mutableMapOf() }
    }
    return when (section) {
        FavoriteSection.BANGUMI, FavoriteSection.CINEMA ->
            states.getOrPut("${section.name}:${status.name}") { createCompatibleStaggeredGridState() }
        else -> null
    }
}

@Composable
internal fun FavoriteCategoryRoute(
    section: FavoriteSection,
    state: FavoriteCategoryUiState,
    viewModel: FavoriteCategoryViewModel,
    pageActive: Boolean,
    query: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onBangumiClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
    onCheeseClick: ((Long) -> Unit)? = null,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState =
        com.android.purebilibili.core.ui.rememberCompatibleStaggeredGridState(),
) {
    val snackbarHostState = remember(viewModel) { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel, section) { viewModel.selectSection(section) }
    LaunchedEffect(viewModel, pageActive) {
        if (pageActive) viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    val contentState = if (state.section == section) state else {
        FavoriteCategoryUiState(section = section, isLoading = true)
    }
    FavoriteCategoryContent(
        state = contentState,
        snackbarHostState = snackbarHostState,
        pageActive = pageActive,
        query = query,
        contentPadding = contentPadding,
        gridState = gridState,
        onPgcStatusSelected = { status ->
            if (status != contentState.pgcStatus) {
                viewModel.selectPgcStatus(status)
            } else if (contentState.items.isNotEmpty()) {
                scope.launch { gridState.animateScrollToTop() }
            }
        },
        onPublishedNotesSelected = viewModel::selectPublishedNotes,
        onRetry = viewModel::retry,
        onRefreshPgc = viewModel::refreshPgc,
        onLoadMore = viewModel::loadMore,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onToggleAllPgcSelection = viewModel::toggleAllPgcSelection,
        onRemoveSelected = viewModel::removeSelected,
        onUpdateSelectedPgcStatus = viewModel::updateSelectedPgcStatus,
        onRemove = viewModel::remove,
        onUpdatePgcStatus = viewModel::updatePgcStatus,
        onBangumiClick = onBangumiClick,
        onArticleClick = onArticleClick,
        onTopicClick = onTopicClick,
        onWebClick = onWebClick,
        onCheeseClick = onCheeseClick,
    )
}

@Composable
private fun FavoriteCategoryContent(
    state: FavoriteCategoryUiState,
    snackbarHostState: SnackbarHostState,
    pageActive: Boolean,
    query: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    onPgcStatusSelected: (FavoritePgcStatus) -> Unit,
    onPublishedNotesSelected: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onRefreshPgc: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onToggleAllPgcSelection: (Set<Long>) -> Unit,
    onRemoveSelected: () -> Unit,
    onUpdateSelectedPgcStatus: (FavoritePgcStatus) -> Unit,
    onRemove: (FavoriteCategoryItem) -> Unit,
    onUpdatePgcStatus: (FavoriteCategoryItem, FavoritePgcStatus) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
    onCheeseClick: ((Long) -> Unit)? = null,
) {
    val visibleItems = state.items.filter { item ->
        query.isBlank() || item.title.contains(query, ignoreCase = true) ||
            item.subtitle.contains(query, ignoreCase = true)
    }
    val headerInset = contentPadding.calculateTopPadding()
    val hasFilterRow = state.isPgc || state.section == FavoriteSection.NOTE
    val stickyChromeReserve = headerInset +
        (if (hasFilterRow) 48.dp else AppSpacingTokens.None)
    Box(modifier = Modifier.fillMaxSize()) {
        AdaptivePullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefreshPgc,
            enabled = pageActive && state.isPgc && !state.isUpdatingPgc,
            indicatorTopInset = stickyChromeReserve,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                (state.isLoading || state.isLoadingMore) && state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(top = headerInset),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("正在加载…", color = AppContentColors.secondaryText())
                }
                state.error != null && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(top = headerInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppText(state.error, color = AppContentColors.error())
                    AppButton(onClick = onRetry) { AppText("重试") }
                }
                visibleItems.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(top = headerInset),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        if (query.isBlank()) "暂无${state.section.label}收藏" else "未找到相关内容",
                        color = AppContentColors.secondaryText(),
                    )
                }
                else -> FavoriteCategoryGrid(
                    state = state.copy(items = visibleItems),
                    gridState = gridState,
                    topPadding = stickyChromeReserve,
                    bottomPadding = contentPadding.calculateBottomPadding() +
                        if (state.isSelecting) 80.dp else 0.dp,
                    pageActive = pageActive,
                    onLoadMore = onLoadMore,
                    onToggleSelection = onToggleSelection,
                    onRemove = onRemove,
                    onUpdatePgcStatus = onUpdatePgcStatus,
                    onBangumiClick = onBangumiClick,
                    onArticleClick = onArticleClick,
                    onTopicClick = onTopicClick,
                    onWebClick = onWebClick,
                    onCheeseClick = onCheeseClick,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = headerInset),
        ) {
            if (state.isPgc) {
                FavoriteCategoryFilterRow(
                    labels = FavoritePgcStatus.entries.map { it.label },
                    selectedIndex = FavoritePgcStatus.entries.indexOf(state.pgcStatus),
                    enabled = !state.isUpdatingPgc,
                    onSelected = { FavoritePgcStatus.entries.getOrNull(it)?.let(onPgcStatusSelected) },
                )
            } else if (state.section == FavoriteSection.NOTE) {
                FavoriteCategoryFilterRow(
                    labels = listOf("未发布笔记", "公开笔记"),
                    selectedIndex = if (state.publishedNotes) 1 else 0,
                    onSelected = { onPublishedNotesSelected(it == 1) },
                )
            }
        }
        if (state.isSelecting) {
            AppFloatingToolbar(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp),
            ) {
                if (state.isPgc) {
                    val visibleIds = visibleItems.mapTo(mutableSetOf()) { it.id }
                    val allSelected = visibleIds.isNotEmpty() && state.selectedIds.containsAll(visibleIds)
                    AppTextButton(
                        onClick = { onToggleAllPgcSelection(visibleIds) },
                        enabled = !state.isUpdatingPgc && visibleIds.isNotEmpty(),
                    ) {
                        AppText(if (allSelected) "取消全选" else "全选")
                    }
                    FavoritePgcStatus.entries.filter { it != state.pgcStatus }.forEach { status ->
                        AppTextButton(
                            onClick = { onUpdateSelectedPgcStatus(status) },
                            enabled = !state.isUpdatingPgc && state.selectedIds.isNotEmpty(),
                        ) {
                            AppText("标记为${status.label}")
                        }
                    }
                } else {
                    AppTextButton(onClick = onRemoveSelected) {
                        AppText("删除(${state.selectedIds.size})")
                    }
                }
                AppTextButton(onClick = onClearSelection, enabled = !state.isUpdatingPgc) { AppText("取消选择") }
            }
        }
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(
                bottom = contentPadding.calculateBottomPadding() + if (state.isSelecting) 80.dp else 0.dp,
            ),
        )
    }
}

@Composable
private fun FavoriteCategoryFilterRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val options = remember(labels) {
        labels.mapIndexed { index, label -> AppSegmentOption(index, label) }
    }
    val uiStyle = LocalAppUiStyle.current
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val isScrollable = labels.size > 4
    val horizontalPadding = if (uiStyle == AppUiStyle.MATERIAL3 && isScrollable) 0.dp else AppSpacingTokens.Medium
    val minTabWidth = if (labels.size <= 3) {
        // Only tighten the glass PGC status dock. Native MD3/Miuix and the two-option
        // notes filter retain their original slots; measured text still supports large fonts.
        if (liquidGlassEnabled && labels.size == 3) 64.dp else 84.dp
    } else if (uiStyle == AppUiStyle.MATERIAL3) {
        AppChromeSizeTokens.MinimumTouchTarget
    } else {
        Dp.Unspecified
    }
    AppThemeAdaptiveTabRow(
        // Glass indicator fills the measured slot; native geometry keeps its defaults.
        liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
        options = options,
        selectedValue = selectedIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0)),
        onSelectionChange = onSelected,
        enabled = enabled,
        scrollable = isScrollable,
        minTabWidth = minTabWidth,
        dragSelectionEnabled = labels.size > 1,
        tapPressRefractionEnabled = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .then(
                if (isScrollable) Modifier else Modifier.wrapContentWidth(Alignment.CenterHorizontally)
            ),
    )
}

@Composable
private fun FavoriteCategoryGrid(
    state: FavoriteCategoryUiState,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    topPadding: Dp,
    bottomPadding: Dp,
    pageActive: Boolean,
    onLoadMore: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onRemove: (FavoriteCategoryItem) -> Unit,
    onUpdatePgcStatus: (FavoriteCategoryItem, FavoritePgcStatus) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
    onCheeseClick: ((Long) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = resolveFavoriteCategoryColumnCount(state.section, maxWidth.value)
        val alignRows = columns > 1 && !com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible.current
        LazyVerticalStaggeredGrid(
            state = gridState,
            columns = StaggeredGridCells.Fixed(columns),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AppSpacingTokens.Medium,
                end = AppSpacingTokens.Medium,
                top = topPadding,
                bottom = bottomPadding + AppSpacingTokens.Medium,
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
            verticalItemSpacing = AppSpacingTokens.Medium,
        ) {
            val itemKeys = state.items.map { "${it.section}_${it.id}" }
            val renderCard: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                val item = state.items[index]
                if (
                    index == state.items.lastIndex && pageActive && state.hasMore &&
                    !state.isLoadingMore && !state.isRefreshing && !state.isUpdatingPgc &&
                    (!state.isPgc || state.error == null)
                ) {
                    LaunchedEffect(gridState, state.page, state.items.size) { onLoadMore() }
                }
                when (state.section) {
                    FavoriteSection.BANGUMI, FavoriteSection.CINEMA -> FavoritePgcCard(
                        item = item,
                        modifier = cardModifier,
                        selected = item.id in state.selectedIds,
                        batchMode = state.isSelectingPgc,
                        selectedStatus = state.pgcStatus,
                        updating = state.isUpdatingPgc,
                        onClick = {
                            if (state.isSelectingPgc) onToggleSelection(item.id)
                            else onBangumiClick(item.id)
                        },
                        onLongClick = if (state.isSelectingPgc) null else {
                            { onToggleSelection(item.id) }
                        },
                        onStatusSelected = { onUpdatePgcStatus(item, it) },
                        onRemove = { onRemove(item) },
                    )
                    FavoriteSection.TOPIC -> AppAssistChip(
                        onClick = { onTopicClick(item.id) },
                        label = { AppText("# ${item.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = null,
                        modifier = cardModifier.fillMaxWidth(),
                    )
                    else -> FavoriteGenericCategoryCard(
                        item = item,
                        modifier = cardModifier,
                        selected = item.id in state.selectedIds,
                        batchMode = state.selectedIds.isNotEmpty(),
                        onClick = {
                            when (item.section) {
                                FavoriteSection.ARTICLE -> onArticleClick(item.id, item.title)
                                FavoriteSection.COURSE -> {
                                    if (onCheeseClick != null) {
                                        onCheeseClick(item.id)
                                    } else {
                                        onBangumiClick(item.id)
                                    }
                                }
                                FavoriteSection.NOTE -> onWebClick(item.url, item.title)
                                else -> Unit
                            }
                        },
                        onLongClick = if (item.section == FavoriteSection.NOTE) {
                            { onToggleSelection(item.id) }
                        } else null,
                        onRemove = { onRemove(item) },
                    )
                }
            }
            if (!alignRows) {
                itemsIndexed(state.items, key = { index, _ -> itemKeys[index] }) { index, _ ->
                    renderCard(index, Modifier)
                }
            } else {
                com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(state.items.size, columns)
                    .forEach { row ->
                        item(key = "favorite_category_row_${itemKeys[row.first]}", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                indices = row,
                                itemKeys = itemKeys,
                                columns = columns,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                                modifier = Modifier.fillMaxWidth(),
                                content = renderCard,
                            )
                        }
                    }
            }
            if (state.isPgc && state.error != null) {
                item(key = "favorite_category_error", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppText(state.error, color = AppContentColors.error())
                        AppTextButton(onClick = onLoadMore) { AppText("重试") }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FavoritePgcCard(
    item: FavoriteCategoryItem,
    modifier: Modifier = Modifier,
    selected: Boolean,
    batchMode: Boolean,
    selectedStatus: FavoritePgcStatus,
    updating: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onStatusSelected: (FavoritePgcStatus) -> Unit,
    onRemove: () -> Unit,
) {
    val container = if (!batchMode) {
        com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(
            com.android.purebilibili.navigation3.BiliPaiNavKey.BangumiPlayer(seasonId = item.id, epId = 0L), onClick,
        )
    } else null
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(container?.modifier ?: Modifier)
            .appCombinedClickable(onClick = appActionWithFeedback(container?.onClick ?: onClick), onLongClick = onLongClick),
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(item.cover),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(PERSONAL_LIST_POSTER_ASPECT_RATIO),
                )
                if (item.badge.isNotBlank()) {
                    AppText(
                        text = item.badge,
                        modifier = Modifier.align(Alignment.TopStart).padding(AppSpacingTokens.Small),
                        color = com.android.purebilibili.core.ui.MediaContrastPalette.Foreground,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (!batchMode) {
                    var expanded by remember { mutableStateOf(false) }
                    AppGlassMenuAnchor(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onOpen = { expanded = true },
                        enabled = !updating,
                    ) {
                        AppIconButton(onClick = { expanded = true }, enabled = !updating) {
                            AppIcon(
                                rememberAppMoreIcon(),
                                contentDescription = if (item.section == FavoriteSection.CINEMA) "追剧状态" else "追番状态",
                            )
                        }
                        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            FavoritePgcStatus.entries.asReversed().forEach { status ->
                                AppDropdownMenuItem(
                                    text = { AppText(status.label) },
                                    selected = status == selectedStatus,
                                    enabled = status != selectedStatus,
                                    onClick = {
                                        expanded = false
                                        onStatusSelected(status)
                                    },
                                )
                            }
                            AppDropdownMenuItem(
                                text = { AppText(if (item.section == FavoriteSection.CINEMA) "取消追剧" else "取消追番") },
                                onClick = {
                                    expanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                } else {
                    AppCheckbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(AppSpacingTokens.Small),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(AppSpacingTokens.Medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
            ) {
                AppText(item.title, style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AppText(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FavoriteGenericCategoryCard(
    item: FavoriteCategoryItem,
    modifier: Modifier = Modifier,
    selected: Boolean,
    batchMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val container = if (item.section == FavoriteSection.ARTICLE && !batchMode && item.id > 0L) {
        com.android.purebilibili.core.ui.rememberNativeCardContainerTransition(
            com.android.purebilibili.navigation3.BiliPaiNavKey.DynamicDetail(item.id.toString()), onClick,
        )
    } else null
    PersonalMediaCardFrame(
        modifier = modifier.then(container?.modifier ?: Modifier),
        horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
        selected = selected,
        headlineContent = {
            AppText(
                item.title,
                style = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography().title,
                maxLines = com.android.purebilibili.core.ui.videoCardTitleMaxLines(),
                overflow = com.android.purebilibili.core.ui.videoCardTitleOverflow(),
            )
        },
        overlineContent = item.badge.takeIf(String::isNotBlank)?.let { badge ->
            { AppText(badge, color = AppContentColors.accent(), style = MaterialTheme.typography.labelSmall) }
        },
        supportingContent = item.subtitle.takeIf(String::isNotBlank)?.let { subtitle ->
            {
                AppText(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        coverContent = {
            AsyncImage(
                model = FormatUtils.fixImageUrl(item.cover),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        trailingContent = if (batchMode) null else {
            {
                AppIconButton(onClick = onRemove) {
                    AppIcon(Icons.Rounded.Close, contentDescription = "移除收藏")
                }
            }
        },
        onClick = if (batchMode) ({ onLongClick?.invoke(); Unit }) else container?.onClick ?: onClick,
        onLongClick = onLongClick,
    )
}
