package com.android.purebilibili.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.core.util.responsiveContentWidth

private const val SEARCH_HIGHLIGHT_START_TOKEN = "§hl§"
private const val SEARCH_HIGHLIGHT_END_TOKEN = "§/hl§"

internal fun resolveSearchKeywordSectionToggleLabel(enabled: Boolean): String {
    return if (enabled) "隐藏" else "显示"
}

internal fun resolveSearchKeywordSectionHiddenText(title: String): String {
    return "已隐藏$title"
}

internal fun shouldUseOriginalSearchDiscoverStyle(
    showTrendingAction: Boolean
): Boolean = !showTrendingAction

internal fun resolveSearchKeywordSectionColumns(
    requestedColumns: Int,
    showTrendingAction: Boolean
): Int {
    val safeColumns = requestedColumns.coerceAtLeast(1)
    return if (shouldUseOriginalSearchDiscoverStyle(showTrendingAction)) 2 else safeColumns
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

internal fun resolveSearchDiscoverOriginalCellColors(
    colorScheme: androidx.compose.material3.ColorScheme
): SearchDiscoverOriginalCellColors {
    return if (colorScheme.background.luminance() > 0.5f) {
        SearchDiscoverOriginalCellColors(
            containerColor = colorScheme.primary.copy(alpha = 0.08f),
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.primary.copy(alpha = 0.58f),
            borderColor = colorScheme.primary.copy(alpha = 0.12f)
        )
    } else {
        SearchDiscoverOriginalCellColors(
            containerColor = colorScheme.primary.copy(alpha = 0.18f),
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.primary.copy(alpha = 0.72f),
            borderColor = colorScheme.primary.copy(alpha = 0.22f)
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
    discoverTitle: String,
    discoverList: List<SearchKeywordUiModel>,
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
    if (useSplitLayout) {
        Row(
            modifier = modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(layoutPolicy.leftPaneWeight)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopPadding + 16.dp,
                    bottom = bottomPadding,
                    start = layoutPolicy.splitOuterPaddingDp.dp,
                    end = layoutPolicy.splitInnerGapDp.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    SearchKeywordSection(
                        title = "大家都在搜",
                        items = hotList,
                        columns = layoutPolicy.hotSearchColumns,
                        enabled = hotSearchEnabled,
                        showTrendingAction = true,
                        onToggleEnabled = onToggleHotSearch,
                        onOpenTrending = onOpenTrending,
                        onRefresh = onRefreshHot,
                        onKeywordClick = onKeywordClick
                    )
                }
                item {
                    SearchKeywordSection(
                        title = discoverTitle,
                        items = discoverList,
                        columns = layoutPolicy.hotSearchColumns,
                        enabled = discoverSectionEnabled,
                        showTrendingAction = false,
                        onToggleEnabled = onToggleDiscoverSection,
                        onRefresh = onRefreshDiscover,
                        onKeywordClick = onKeywordClick
                    )
                }
            }

            LazyColumn(
                state = historyListState,
                modifier = Modifier
                    .weight(layoutPolicy.rightPaneWeight)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentTopPadding + 16.dp,
                    bottom = bottomPadding,
                    start = layoutPolicy.splitInnerGapDp.dp,
                    end = layoutPolicy.splitOuterPaddingDp.dp
                )
            ) {
                item {
                    SearchHistorySectionModern(
                        historyList = historyList,
                        onItemClick = onKeywordClick,
                        onClear = onClearHistory,
                        onDelete = onDeleteHistory
                    )
                }
            }
        }
    } else {
        LazyColumn(
            state = historyListState,
            modifier = modifier
                .fillMaxSize()
                .responsiveContentWidth(),
            contentPadding = PaddingValues(
                top = contentTopPadding + 16.dp,
                bottom = bottomPadding,
                start = layoutPolicy.resultHorizontalPaddingDp.dp,
                end = layoutPolicy.resultHorizontalPaddingDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SearchKeywordSection(
                    title = "大家都在搜",
                    items = hotList,
                    columns = layoutPolicy.hotSearchColumns,
                    enabled = hotSearchEnabled,
                    showTrendingAction = true,
                    onToggleEnabled = onToggleHotSearch,
                    onOpenTrending = onOpenTrending,
                    onRefresh = onRefreshHot,
                    onKeywordClick = onKeywordClick
                )
            }
            item {
                SearchKeywordSection(
                    title = discoverTitle,
                    items = discoverList,
                    columns = layoutPolicy.hotSearchColumns,
                    enabled = discoverSectionEnabled,
                    showTrendingAction = false,
                    onToggleEnabled = onToggleDiscoverSection,
                    onRefresh = onRefreshDiscover,
                    onKeywordClick = onKeywordClick
                )
            }
            item {
                SearchHistorySectionModern(
                    historyList = historyList,
                    onItemClick = onKeywordClick,
                    onClear = onClearHistory,
                    onDelete = onDeleteHistory
                )
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

    Surface(
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Pill),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        color = AppSurfaceTokens.cardContainer()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion.keyword) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = rememberSuggestionAnnotatedText(
                            richText = suggestion.richText,
                            fallback = suggestion.keyword
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index != suggestions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 46.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
        if (enabled && items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(if (useOriginalDiscoverStyle) 12.dp else 10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(if (useOriginalDiscoverStyle) 12.dp else 6.dp)
            ) {
                items.chunked(safeColumns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (useOriginalDiscoverStyle) 12.dp else 12.dp)
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
        } else if (!enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = resolveSearchKeywordSectionHiddenText(title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    if (useOriginalDiscoverStyle) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (enabled) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新搜索发现",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (onToggleEnabled != null) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                    )
                    IconButton(onClick = onToggleEnabled, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (enabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (enabled) "隐藏搜索发现" else "显示搜索发现",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showTrendingAction && enabled && onOpenTrending != null) {
                TextButton(onClick = onOpenTrending) {
                    Text(
                        text = "完整榜单",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (enabled) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新搜索发现",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (onToggleEnabled != null) {
                IconButton(onClick = onToggleEnabled, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (enabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (enabled) "隐藏搜索发现" else "显示搜索发现",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    SuggestionChip(
        onClick = onClick,
        label = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (!displaySubtitle.isNullOrBlank()) {
                    Text(
                        text = displaySubtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    )
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        when {
            item.iconUrl != null -> AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 20.dp, height = 16.dp)
            )
            item.showLiveBadge -> SearchKeywordBadge(
                text = "直播中",
                containerColor = Color(0xFFFF6B97),
                contentColor = Color.White
            )
            !item.subtitle.isNullOrBlank() -> Text(
                text = item.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
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
                shape = AppShapes.container(ContainerLevel.Chip)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySectionModern(
    historyList: List<SearchHistory>,
    onItemClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: (SearchHistory) -> Unit
) {
    if (historyList.isEmpty()) return

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索历史",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            TextButton(onClick = onClear) {
                Text("清空")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            historyList.forEach { history ->
                HistoryChip(
                    keyword = history.keyword,
                    onClick = { onItemClick(history.keyword) },
                    onDelete = { onDelete(history) }
                )
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
