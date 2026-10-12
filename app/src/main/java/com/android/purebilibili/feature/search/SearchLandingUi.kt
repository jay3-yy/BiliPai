package com.android.purebilibili.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppSpacingTokens
import coil3.compose.AsyncImage
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

private const val SEARCH_HIGHLIGHT_START_TOKEN = "§hl§"
private const val SEARCH_HIGHLIGHT_END_TOKEN = "§/hl§"

internal fun resolveSearchKeywordSectionToggleLabel(enabled: Boolean): String {
    return if (enabled) "隐藏" else "显示"
}

internal fun shouldShowSearchKeywordSectionVisibilityToggle(
    hasToggleHandler: Boolean,
): Boolean = hasToggleHandler

internal fun resolveSearchKeywordSectionToggleContentDescription(
    enabled: Boolean,
    title: String,
): String = resolveSearchKeywordSectionToggleLabel(enabled) + title

internal fun resolveSearchKeywordSectionHiddenText(title: String): String {
    return "已隐藏$title"
}

internal fun shouldUseCompactSearchSectionActions(widthDp: Int, fontScale: Float): Boolean =
    widthDp < 360 || fontScale > 1.3f

internal fun shouldUseOriginalSearchDiscoverStyle(
    showTrendingAction: Boolean
): Boolean = !showTrendingAction

internal fun resolveSearchKeywordSectionColumns(
    requestedColumns: Int,
    showTrendingAction: Boolean
): Int {
    // BiliPai / official search use a fixed 2-column keyword grid for both
    // trending and discover sections.
    return 2
}

internal fun resolveSearchDiscoverOriginalSubtitle(
    subtitle: String?
): String? {
    val normalized = subtitle?.trim().orEmpty()
    return normalized.takeIf { it.isNotBlank() }
}

internal data class SearchDiscoverOriginalCellColors(
    val containerColor: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    val borderColor: Color
)

/**
 * Discover chips stay neutral (surfaceVariant), not brand/theme primary —
 * matches official search / BiliPai “搜索发现” look under all presets.
 */
internal fun resolveSearchDiscoverOriginalCellColors(
    colorScheme: androidx.compose.material3.ColorScheme
): SearchDiscoverOriginalCellColors {
        return if (colorScheme.background.luminance() > 0.5f) {
        SearchDiscoverOriginalCellColors(
            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f),
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            borderColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    } else {
        SearchDiscoverOriginalCellColors(
            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f),
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            borderColor = colorScheme.outline.copy(alpha = 0.28f)
        )
    }
}

@Composable
fun SearchLandingContent(
    historyListState: LazyListState,
    useSplitLayout: Boolean,
    layoutPolicy: SearchLayoutPolicy,
    contentTopPadding: Dp,
    bottomPadding: Dp,
    hotList: List<SearchKeywordUiModel>,
    hotListError: String? = null,
    isRefreshingHotList: Boolean = false,
    discoverTitle: String,
    discoverList: List<SearchKeywordUiModel>,
    discoverListError: String? = null,
    isRefreshingDiscoverList: Boolean = false,
    historyList: List<SearchHistory>,
    hotSearchEnabled: Boolean,
    discoverSectionEnabled: Boolean,
    onToggleHotSearch: () -> Unit,
    onToggleDiscoverSection: () -> Unit,
    onRefreshHot: () -> Unit,
    onOpenTrending: () -> Unit,
    onRefreshDiscover: () -> Unit,
    onKeywordClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionOrder = remember { resolveSearchLandingSectionOrder() }

    @Composable
    fun TrendingSection() {
        SearchKeywordSection(
            title = "大家都在搜",
            items = hotList,
            columns = layoutPolicy.hotSearchColumns,
            enabled = hotSearchEnabled,
            showTrendingAction = true,
            onToggleEnabled = onToggleHotSearch,
            onOpenTrending = onOpenTrending,
            onRefresh = onRefreshHot,
            error = hotListError,
            isRefreshing = isRefreshingHotList,
            onKeywordClick = onKeywordClick
        )
    }

    @Composable
    fun DiscoverSection() {
        SearchKeywordSection(
            title = discoverTitle,
            items = discoverList,
            columns = layoutPolicy.hotSearchColumns,
            enabled = discoverSectionEnabled,
            showTrendingAction = false,
            onToggleEnabled = onToggleDiscoverSection,
            onRefresh = onRefreshDiscover,
            error = discoverListError,
            isRefreshing = isRefreshingDiscoverList,
            onKeywordClick = onKeywordClick
        )
    }

    @Composable
    fun HistorySection() {
        SearchHistorySectionModern(
            historyList = historyList,
            columns = layoutPolicy.hotSearchColumns,
            onItemClick = onKeywordClick,
            onClear = onClearHistory,
            onDelete = onDeleteHistory
        )
    }

    if (useSplitLayout) {
        Row(
            modifier = modifier
                .responsiveContentWidth(maxWidth = resolveSearchMaxContentWidth())
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(layoutPolicy.leftPaneWeight)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopPadding,
                    bottom = bottomPadding,
                    start = layoutPolicy.splitOuterPaddingDp.dp,
                    end = layoutPolicy.splitInnerGapDp.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { TrendingSection() }
                item { DiscoverSection() }
            }
            LazyColumn(
                state = historyListState,
                modifier = Modifier
                    .weight(layoutPolicy.rightPaneWeight)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopPadding,
                    bottom = bottomPadding,
                    start = layoutPolicy.splitInnerGapDp.dp,
                    end = layoutPolicy.splitOuterPaddingDp.dp
                )
            ) {
                item { HistorySection() }
            }
        }
    } else {
        LazyColumn(
            state = historyListState,
            modifier = modifier
                .fillMaxSize()
                .responsiveContentWidth(),
            contentPadding = PaddingValues(
                top = contentTopPadding,
                bottom = bottomPadding,
                start = layoutPolicy.resultHorizontalPaddingDp.dp,
                end = layoutPolicy.resultHorizontalPaddingDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sectionOrder.forEach { section ->
                item(key = section.name) {
                    when (section) {
                        SearchLandingSection.TRENDING -> TrendingSection()
                        SearchLandingSection.HISTORY -> HistorySection()
                        SearchLandingSection.DISCOVER -> DiscoverSection()
                    }
                }
            }
        }
    }
}

@Composable
fun SearchSuggestionDropdown(
    suggestions: List<SearchSuggestionUiModel>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    AppSurface(
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Card),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        color = AppSurfaceTokens.surface()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            suggestions.forEachIndexed { index, suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion.keyword) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                    AppText(
                        text = rememberSuggestionAnnotatedText(
                            richText = suggestion.richText,
                            fallback = suggestion.keyword
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index != suggestions.lastIndex) {
                    AppHorizontalDivider(
                        modifier = Modifier.padding(start = 46.dp),
                        color = outline
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchKeywordSection(
    title: String,
    items: List<SearchKeywordUiModel>,
    columns: Int,
    enabled: Boolean,
    showTrendingAction: Boolean,
    onRefresh: () -> Unit,
    onKeywordClick: (String) -> Unit,
    error: String? = null,
    isRefreshing: Boolean = false,
    onToggleEnabled: (() -> Unit)? = null,
    onOpenTrending: (() -> Unit)? = null
) {
    val useOriginalDiscoverStyle = shouldUseOriginalSearchDiscoverStyle(showTrendingAction)
    val safeColumns = resolveSearchKeywordSectionColumns(columns, showTrendingAction)
    Column {
        SearchKeywordSectionHeader(
            title = title,
            enabled = enabled,
            useOriginalDiscoverStyle = useOriginalDiscoverStyle,
            showTrendingAction = showTrendingAction,
            onToggleEnabled = onToggleEnabled,
            onOpenTrending = onOpenTrending,
            onRefresh = onRefresh
        )
        val sectionMode = resolveSearchLandingSectionMode(
            enabled = enabled,
            itemCount = items.size,
            isRefreshing = isRefreshing,
            error = error
        )
        when (sectionMode) {
            SearchLandingSectionMode.CONTENT -> {
                Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall)
                ) {
                    items.chunked(safeColumns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
                        ) {
                            rowItems.forEach { item ->
                                if (useOriginalDiscoverStyle) {
                                    SearchDiscoverOriginalCell(
                                        item = item,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onKeywordClick(item.keyword) }
                                    )
                                } else {
                                    SearchKeywordCell(
                                        item = item,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onKeywordClick(item.keyword) }
                                    )
                                }
                            }
                            repeat(safeColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (error != null) {
                    SearchInlineMessage(
                        title = "刷新失败",
                        message = error,
                        actionLabel = "重试",
                        onAction = onRefresh
                    )
                }
            }
            SearchLandingSectionMode.LOADING -> {
                SearchInlineMessage(title = "正在加载")
            }
            SearchLandingSectionMode.ERROR -> {
                SearchInlineMessage(
                    title = "加载失败",
                    message = error,
                    actionLabel = "重试",
                    onAction = onRefresh
                )
            }
            SearchLandingSectionMode.EMPTY -> {
                SearchInlineMessage(
                    title = "暂无内容",
                    message = "稍后再试或直接输入关键词",
                    actionLabel = "刷新",
                    onAction = onRefresh
                )
            }
            SearchLandingSectionMode.HIDDEN -> {
                Spacer(modifier = Modifier.height(12.dp))
                AppText(
                    text = resolveSearchKeywordSectionHiddenText(title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchInlineMessage(
    title: String,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppText(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!message.isNullOrBlank()) {
            AppText(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
        if (actionLabel != null && onAction != null) {
            AppTextButton(onClick = onAction) {
                AppText(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SearchKeywordSectionHeader(
    title: String,
    enabled: Boolean,
    useOriginalDiscoverStyle: Boolean,
    showTrendingAction: Boolean,
    onRefresh: () -> Unit,
    onToggleEnabled: (() -> Unit)?,
    onOpenTrending: (() -> Unit)?
) {
    val outline = MaterialTheme.colorScheme.outline
    val secondary = MaterialTheme.colorScheme.secondary
    val useCompactActions = shouldUseCompactSearchSectionActions(
        widthDp = LocalConfiguration.current.screenWidthDp,
        fontScale = LocalDensity.current.fontScale,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!useCompactActions && showTrendingAction && enabled && onOpenTrending != null) {
                Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                AppTextButton(onClick = onOpenTrending) {
                    AppText(
                        text = "完整榜单",
                        color = outline,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (useCompactActions && showTrendingAction && enabled && onOpenTrending != null) {
                AppIconButton(onClick = onOpenTrending, modifier = Modifier.size(48.dp)) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "完整榜单",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (enabled) {
                if (useCompactActions) {
                    AppIconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                        AppIcon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    AppTextButton(onClick = onRefresh) {
                        AppIcon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        AppText(
                            text = "刷新",
                            color = secondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            val showVisibilityToggle = shouldShowSearchKeywordSectionVisibilityToggle(
                hasToggleHandler = onToggleEnabled != null,
            )
            if (showVisibilityToggle && onToggleEnabled != null) {
                AppIconButton(onClick = onToggleEnabled, modifier = Modifier.size(48.dp)) {
                    AppIcon(
                        imageVector = if (enabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = resolveSearchKeywordSectionToggleContentDescription(
                            enabled = enabled,
                            title = title,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDiscoverOriginalCell(
    item: SearchKeywordUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displaySubtitle = remember(item.subtitle) {
        resolveSearchDiscoverOriginalSubtitle(item.subtitle)
    }
    val colors = resolveSearchDiscoverOriginalCellColors(MaterialTheme.colorScheme)
    AppSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = AppShapes.container(ContainerLevel.Field),
        color = colors.containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.ExtraSmall)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = item.title,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = colors.titleColor
                    )
                )
                when {
                    item.iconUrl != null -> {
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(width = 20.dp, height = 15.dp)
                        )
                    }
                    item.showLiveBadge -> {
                        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                        SearchKeywordBadge(
                            text = "直播中",
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
            if (item.iconUrl == null && !item.showLiveBadge && !displaySubtitle.isNullOrBlank()) {
                AppText(
                    text = displaySubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.subtitleColor
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchKeywordCell(
    item: SearchKeywordUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppText(
            text = item.title,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
        when {
            item.iconUrl != null -> AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 20.dp, height = 15.dp)
            )
            item.showLiveBadge -> SearchKeywordBadge(
                text = "直播中",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
            !item.subtitle.isNullOrBlank() -> AppText(
                text = item.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun SearchKeywordBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = containerColor,
                shape = AppShapes.container(ContainerLevel.Tag)
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SearchHistorySectionModern(
    historyList: List<SearchHistory>,
    columns: Int,
    onItemClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: (SearchHistory) -> Unit
) {
    if (historyList.isEmpty()) return
    val secondary = MaterialTheme.colorScheme.secondary
    val safeColumns = resolveSearchKeywordSectionColumns(columns, showTrendingAction = false)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "搜索历史",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            AppTextButton(onClick = onClear) {
                AppIcon(
                    imageVector = Icons.Outlined.ClearAll,
                    contentDescription = "清空",
                    tint = secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                AppText(
                    text = "清空",
                    color = secondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
        // 与「搜索发现」同构的紧凑网格：历史项 14sp 文字行 + 删除角标，
        // 行间距 4dp，替代此前间距过大的气泡 FlowRow。
        historyList.chunked(safeColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
            ) {
                rowItems.forEach { history ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .clickable { onItemClick(history.keyword) }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = history.keyword,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(AppShapes.container(ContainerLevel.Chip))
                                .clickable { onDelete(history) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                if (rowItems.size < safeColumns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun rememberSuggestionAnnotatedText(
    richText: String,
    fallback: String
): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return remember(richText, fallback, highlightColor) {
        buildSuggestionAnnotatedString(
            richText = richText,
            fallback = fallback,
            highlightColor = highlightColor
        )
    }
}

internal fun buildSuggestionAnnotatedString(
    richText: String,
    fallback: String,
    highlightColor: Color
): AnnotatedString {
    val source = richText.ifBlank { fallback }
    val normalized = source
        .replace("<suggest_high_light>", SEARCH_HIGHLIGHT_START_TOKEN)
        .replace("</suggest_high_light>", SEARCH_HIGHLIGHT_END_TOKEN)
        .replace(Regex("<em[^>]*>"), SEARCH_HIGHLIGHT_START_TOKEN)
        .replace("</em>", SEARCH_HIGHLIGHT_END_TOKEN)
        .replace(Regex("<.*?>"), "")

    if (!normalized.contains(SEARCH_HIGHLIGHT_START_TOKEN)) {
        return AnnotatedString(normalized.ifBlank { fallback })
    }

    val builder = AnnotatedString.Builder()
    var remaining = normalized
    while (remaining.isNotEmpty()) {
        val start = remaining.indexOf(SEARCH_HIGHLIGHT_START_TOKEN)
        if (start < 0) {
            builder.append(remaining)
            break
        }
        if (start > 0) {
            builder.append(remaining.substring(0, start))
        }
        val contentStart = start + SEARCH_HIGHLIGHT_START_TOKEN.length
        val end = remaining.indexOf(SEARCH_HIGHLIGHT_END_TOKEN, startIndex = contentStart)
        if (end < 0) {
            builder.append(remaining.substring(contentStart))
            break
        }
        val highlightText = remaining.substring(contentStart, end)
        builder.pushStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold))
        builder.append(highlightText)
        builder.pop()
        remaining = remaining.substring(end + SEARCH_HIGHLIGHT_END_TOKEN.length)
    }
    return builder.toAnnotatedString()
}
