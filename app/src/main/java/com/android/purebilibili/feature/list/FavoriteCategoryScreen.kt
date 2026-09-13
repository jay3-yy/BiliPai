package com.android.purebilibili.feature.list

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.FavoritePgcStatus
import com.android.purebilibili.data.model.response.FavoriteSection
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.data.repository.FavoriteCategoryItem
import com.android.purebilibili.data.repository.PersonalFavoriteRepository
import com.android.purebilibili.feature.personal.PERSONAL_LIST_POSTER_ASPECT_RATIO
import com.android.purebilibili.feature.personal.PersonalMediaCardFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteCategoryUiState(
    val section: FavoriteSection = FavoriteSection.BANGUMI,
    val items: List<FavoriteCategoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val pgcStatus: FavoritePgcStatus = FavoritePgcStatus.WANT,
    val publishedNotes: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)

class FavoriteCategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FavoriteCategoryUiState())
    val uiState = _uiState.asStateFlow()

    fun selectSection(section: FavoriteSection) {
        if (_uiState.value.section == section && _uiState.value.items.isNotEmpty()) return
        _uiState.value = FavoriteCategoryUiState(
            section = section,
            pgcStatus = _uiState.value.pgcStatus,
            publishedNotes = _uiState.value.publishedNotes,
            isLoading = true,
        )
        load(reset = true)
    }

    fun selectPgcStatus(status: FavoritePgcStatus) {
        if (_uiState.value.pgcStatus == status) return
        _uiState.update { it.copy(pgcStatus = status, selectedIds = emptySet()) }
        load(reset = true)
    }

    fun selectPublishedNotes(published: Boolean) {
        if (_uiState.value.publishedNotes == published) return
        _uiState.update { it.copy(publishedNotes = published, selectedIds = emptySet()) }
        load(reset = true)
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        load(reset = false)
    }

    fun toggleSelection(id: Long) {
        if (id <= 0L) return
        _uiState.update { state ->
            state.copy(selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun remove(item: FavoriteCategoryItem) {
        viewModelScope.launch {
            val state = _uiState.value
            val result = when (item.section) {
                FavoriteSection.BANGUMI, FavoriteSection.CINEMA ->
                    BangumiRepository.unfollowBangumi(item.id).map { Unit }
                else -> PersonalFavoriteRepository.remove(item, state.publishedNotes)
            }
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
        viewModelScope.launch {
            val selected = state.items.filter { it.id in state.selectedIds }
            val failures = selected.map { item ->
                when (item.section) {
                    FavoriteSection.BANGUMI, FavoriteSection.CINEMA ->
                        BangumiRepository.unfollowBangumi(item.id).map { Unit }
                    else -> PersonalFavoriteRepository.remove(item, state.publishedNotes)
                }
            }.count { it.isFailure }
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
        if (
            state.section !in setOf(FavoriteSection.BANGUMI, FavoriteSection.CINEMA) ||
            state.selectedIds.isEmpty()
        ) return
        viewModelScope.launch {
            val results = state.selectedIds.map { seasonId ->
                BangumiRepository.updateBangumiFollowStatus(seasonId, status.value)
            }
            if (results.all { it.isSuccess }) {
                _uiState.update { it.copy(selectedIds = emptySet()) }
                load(reset = true)
            } else {
                _uiState.update { it.copy(error = "部分状态迁移失败，请重试") }
            }
        }
    }

    fun updatePgcStatus(item: FavoriteCategoryItem, status: FavoritePgcStatus) {
        viewModelScope.launch {
            val result = BangumiRepository.updateBangumiFollowStatus(item.id, status.value)
            if (result.isSuccess) load(reset = true)
            else _uiState.update { it.copy(error = result.exceptionOrNull()?.message ?: "更新状态失败") }
        }
    }

    private fun load(reset: Boolean) {
        val requestState = _uiState.value
        val requestPage = if (reset) 1 else requestState.page + 1
        _uiState.update {
            if (reset) it.copy(isLoading = true, error = null, page = 1, hasMore = false)
            else it.copy(isLoadingMore = true, error = null)
        }
        viewModelScope.launch {
            val result = when (requestState.section) {
                FavoriteSection.BANGUMI, FavoriteSection.CINEMA -> loadPgc(
                    section = requestState.section,
                    status = requestState.pgcStatus,
                    page = requestPage,
                )
                FavoriteSection.ARTICLE -> PersonalFavoriteRepository.getArticles(requestPage)
                FavoriteSection.NOTE -> PersonalFavoriteRepository.getNotes(requestPage, requestState.publishedNotes)
                FavoriteSection.TOPIC -> PersonalFavoriteRepository.getTopics(requestPage)
                FavoriteSection.COURSE -> PersonalFavoriteRepository.getCourses(requestPage)
                FavoriteSection.VIDEO -> Result.success(
                    com.android.purebilibili.data.repository.FavoriteCategoryPage(emptyList(), false)
                )
            }
            if (_uiState.value.section != requestState.section) return@launch
            result.fold(
                onSuccess = { page ->
                    _uiState.update { current ->
                        current.copy(
                            items = if (reset) page.items else (current.items + page.items).distinctBy { it.id },
                            isLoading = false,
                            isLoadingMore = false,
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
                            error = error.message ?: "加载失败",
                        )
                    }
                },
            )
        }
    }

    private suspend fun loadPgc(
        section: FavoriteSection,
        status: FavoritePgcStatus,
        page: Int,
    ): Result<com.android.purebilibili.data.repository.FavoriteCategoryPage> {
        val pageSize = 30
        return BangumiRepository.getMyFollowBangumi(
            type = if (section == FavoriteSection.CINEMA) 2 else 1,
            page = page,
            pageSize = pageSize,
        ).map { data ->
            val items = data.list.orEmpty()
                .filter { it.followStatus == status.value || it.followStatus == 0 }
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
            com.android.purebilibili.data.repository.FavoriteCategoryPage(
                items = items,
                hasMore = page * pageSize < data.total,
            )
        }
    }
}

@Composable
fun FavoriteCategoryRoute(
    section: FavoriteSection,
    query: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onBangumiClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
    viewModel: FavoriteCategoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(section) { viewModel.selectSection(section) }
    FavoriteCategoryContent(
        state = state,
        query = query,
        contentPadding = contentPadding,
        onPgcStatusSelected = viewModel::selectPgcStatus,
        onPublishedNotesSelected = viewModel::selectPublishedNotes,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onRemoveSelected = viewModel::removeSelected,
        onUpdateSelectedPgcStatus = viewModel::updateSelectedPgcStatus,
        onRemove = viewModel::remove,
        onUpdatePgcStatus = viewModel::updatePgcStatus,
        onBangumiClick = onBangumiClick,
        onArticleClick = onArticleClick,
        onTopicClick = onTopicClick,
        onWebClick = onWebClick,
    )
}

@Composable
private fun FavoriteCategoryContent(
    state: FavoriteCategoryUiState,
    query: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onPgcStatusSelected: (FavoritePgcStatus) -> Unit,
    onPublishedNotesSelected: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onUpdateSelectedPgcStatus: (FavoritePgcStatus) -> Unit,
    onRemove: (FavoriteCategoryItem) -> Unit,
    onUpdatePgcStatus: (FavoriteCategoryItem, FavoritePgcStatus) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
) {
    val visibleItems = state.items.filter { item ->
        query.isBlank() || item.title.contains(query, ignoreCase = true) ||
            item.subtitle.contains(query, ignoreCase = true)
    }
    val headerInset = contentPadding.calculateTopPadding()
    val hasFilterRow = state.section == FavoriteSection.BANGUMI ||
        state.section == FavoriteSection.CINEMA ||
        state.section == FavoriteSection.NOTE
    val stickyChromeReserve = headerInset +
        (if (hasFilterRow) 48.dp else AppSpacingTokens.None) +
        (if (state.selectedIds.isNotEmpty()) 48.dp else AppSpacingTokens.None)
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(top = headerInset),
                contentAlignment = Alignment.Center,
            ) {
                AppText("正在加载…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error != null && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(top = headerInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(state.error, color = MaterialTheme.colorScheme.error)
                AppButton(onClick = onRetry) { AppText("重试") }
            }
            visibleItems.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(top = headerInset),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    if (query.isBlank()) "暂无${state.section.label}收藏" else "未找到相关内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> FavoriteCategoryGrid(
                state = state.copy(items = visibleItems),
                topPadding = stickyChromeReserve,
                bottomPadding = contentPadding.calculateBottomPadding(),
                onLoadMore = onLoadMore,
                onToggleSelection = onToggleSelection,
                onRemove = onRemove,
                onUpdatePgcStatus = onUpdatePgcStatus,
                onBangumiClick = onBangumiClick,
                onArticleClick = onArticleClick,
                onTopicClick = onTopicClick,
                onWebClick = onWebClick,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = headerInset),
        ) {
            if (state.section == FavoriteSection.BANGUMI || state.section == FavoriteSection.CINEMA) {
                FavoriteCategoryFilterRow(
                    labels = FavoritePgcStatus.entries.map { it.label },
                    selectedIndex = FavoritePgcStatus.entries.indexOf(state.pgcStatus),
                    onSelected = { FavoritePgcStatus.entries.getOrNull(it)?.let(onPgcStatusSelected) },
                )
            } else if (state.section == FavoriteSection.NOTE) {
                FavoriteCategoryFilterRow(
                    labels = listOf("未发布笔记", "公开笔记"),
                    selectedIndex = if (state.publishedNotes) 1 else 0,
                    onSelected = { onPublishedNotesSelected(it == 1) },
                )
            }

            if (state.selectedIds.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacingTokens.Medium),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                ) {
                    if (state.section == FavoriteSection.BANGUMI || state.section == FavoriteSection.CINEMA) {
                        FavoritePgcStatus.entries.forEach { status ->
                            AppTextButton(onClick = { onUpdateSelectedPgcStatus(status) }) {
                                AppText("移至${status.label}")
                            }
                        }
                    } else {
                        AppTextButton(onClick = onRemoveSelected) {
                            AppText("删除(${state.selectedIds.size})")
                        }
                    }
                    AppTextButton(onClick = onClearSelection) {
                        AppText("取消选择")
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCategoryFilterRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val options = remember(labels) {
        labels.mapIndexed { index, label -> AppSegmentOption(index, label) }
    }
    AppThemeAdaptiveTabRow(
        options = options,
        selectedValue = selectedIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0)),
        onSelectionChange = onSelected,
        scrollable = labels.size > 4,
        minTabWidth = if (labels.size <= 3) 84.dp else Dp.Unspecified,
        dragSelectionEnabled = labels.size > 1,
        tapPressRefractionEnabled = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacingTokens.Medium)
            .wrapContentWidth(Alignment.CenterHorizontally),
    )
}

@Composable
private fun FavoriteCategoryGrid(
    state: FavoriteCategoryUiState,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onLoadMore: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onRemove: (FavoriteCategoryItem) -> Unit,
    onUpdatePgcStatus: (FavoriteCategoryItem, FavoritePgcStatus) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onArticleClick: (Long, String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onWebClick: (String, String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = resolveFavoriteCategoryColumnCount(state.section, maxWidth.value)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AppSpacingTokens.Medium,
                end = AppSpacingTokens.Medium,
                top = topPadding,
                bottom = bottomPadding + AppSpacingTokens.Medium,
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
        ) {
            itemsIndexed(state.items, key = { _, item -> "${item.section}_${item.id}" }) { index, item ->
                if (index == state.items.lastIndex && state.hasMore && !state.isLoadingMore) {
                    LaunchedEffect(state.page, state.items.size) { onLoadMore() }
                }
                when (state.section) {
                    FavoriteSection.BANGUMI, FavoriteSection.CINEMA -> FavoritePgcCard(
                        item = item,
                        selected = item.id in state.selectedIds,
                        batchMode = state.selectedIds.isNotEmpty(),
                        selectedStatus = state.pgcStatus,
                        onClick = {
                            if (state.selectedIds.isNotEmpty()) onToggleSelection(item.id)
                            else onBangumiClick(item.id)
                        },
                        onLongClick = { onToggleSelection(item.id) },
                        onStatusSelected = { onUpdatePgcStatus(item, it) },
                        onRemove = { onRemove(item) },
                    )
                    FavoriteSection.TOPIC -> AppAssistChip(
                        onClick = { onTopicClick(item.id) },
                        label = { AppText("# ${item.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> FavoriteGenericCategoryCard(
                        item = item,
                        selected = item.id in state.selectedIds,
                        batchMode = state.selectedIds.isNotEmpty(),
                        onClick = {
                            when (item.section) {
                                FavoriteSection.ARTICLE -> onArticleClick(item.id, item.title)
                                FavoriteSection.NOTE, FavoriteSection.COURSE -> onWebClick(item.url, item.title)
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
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FavoritePgcCard(
    item: FavoriteCategoryItem,
    selected: Boolean,
    batchMode: Boolean,
    selectedStatus: FavoritePgcStatus,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStatusSelected: (FavoritePgcStatus) -> Unit,
    onRemove: () -> Unit,
) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                    AppIconButton(
                        onClick = onRemove,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        AppIcon(Icons.Rounded.Close, contentDescription = "取消收藏")
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
                AppText(item.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AppText(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                ) {
                    FavoritePgcStatus.entries.forEach { status ->
                        AppTextButton(onClick = { onStatusSelected(status) }) {
                            AppText(
                                status.label,
                                color = if (status == selectedStatus) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteGenericCategoryCard(
    item: FavoriteCategoryItem,
    selected: Boolean,
    batchMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    PersonalMediaCardFrame(
        selected = selected,
        headlineContent = {
            AppText(item.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        overlineContent = item.badge.takeIf(String::isNotBlank)?.let { badge ->
            { AppText(badge, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
        },
        supportingContent = item.subtitle.takeIf(String::isNotBlank)?.let { subtitle ->
            {
                AppText(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        onClick = if (batchMode) ({ onLongClick?.invoke(); Unit }) else onClick,
        onLongClick = onLongClick,
    )
}
