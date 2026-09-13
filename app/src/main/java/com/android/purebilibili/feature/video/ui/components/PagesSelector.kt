package com.android.purebilibili.feature.video.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.data.model.response.Page
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesSelector(
    pages: List<Page>,
    currentPageIndex: Int,
    onPageSelect: (Int) -> Unit,
    forceGridMode: Boolean = false,
    blockParentVerticalScroll: Boolean = false,
    onDismissRequest: (() -> Unit)? = null
) {
    if (pages.isEmpty()) return

    if (forceGridMode && onDismissRequest != null) {
        PlayerMiuixListPopup(
            title = "分集（${pages.size}）",
            onDismissRequest = onDismissRequest,
            placement = PlayerListPopupPlacement.END,
        ) {
            pages.forEachIndexed { index, page ->
                val selected = index == currentPageIndex
                DropdownImpl(
                    item = DropdownItem(
                        text = "P${page.page}",
                        summary = page.part.takeIf { it.isNotEmpty() },
                    ),
                    optionSize = pages.size,
                    isSelected = selected,
                    index = index,
                    enabled = !selected,
                    onSelectedIndexChange = {
                        onPageSelect(index)
                        onDismissRequest()
                    },
                )
            }
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = remember(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp
    }
    val layoutPolicy = remember(
        configuration.screenWidthDp,
        isLandscape,
        pages.size,
        forceGridMode
    ) {
        resolvePagesSelectorLayoutPolicy(
            widthDp = configuration.screenWidthDp,
            isLandscape = isLandscape,
            pagesCount = pages.size,
            forceGridMode = forceGridMode
        )
    }
    val showExpandAction = shouldShowPagesExpandAction(
        policy = layoutPolicy,
        pagesCount = pages.size
    )
    val navigationBarBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val gridBottomPaddingDp = resolvePagesSelectorBottomContentPaddingDp(
        navigationBarBottomDp = navigationBarBottomPadding.value.toInt()
    )
    val groups = remember(pages) { resolvePageSelectorGroups(pages) }

    var inlineQuery by rememberSaveable(pages.size, forceGridMode) { mutableStateOf("") }
    var inlineGroupKey by rememberSaveable(pages.size, forceGridMode) { mutableStateOf<String?>(null) }
    val inlineVisibleIndices = remember(pages, inlineGroupKey, inlineQuery) {
        filterPageIndicesForSelector(
            pages = pages,
            selectedGroupKey = inlineGroupKey,
            query = inlineQuery
        )
    }

    var showExpandedSheet by rememberSaveable(pages.size, forceGridMode) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "选集",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = "(${pages.size})",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showExpandAction) {
                Row(
                    modifier = Modifier
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .clickable { showExpandedSheet = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "展开",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AppIcon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "展开选集",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(16.dp)
                    )
                }
            }
            if (onDismissRequest != null) {
                AppIconButton(onClick = onDismissRequest) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭选集面板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (layoutPolicy.presentation == PagesSelectorPresentation.InlineGrid) {
            if (pages.size >= 10) {
                PagesSelectorFilterBar(
                    groups = groups,
                    selectedGroupKey = inlineGroupKey,
                    query = inlineQuery,
                    totalCount = pages.size,
                    onGroupSelect = { inlineGroupKey = it },
                    onQueryChange = { inlineQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layoutPolicy.horizontalPaddingDp.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            PagesGrid(
                pages = pages,
                visiblePageIndices = inlineVisibleIndices,
                currentPageIndex = currentPageIndex,
                gridColumns = layoutPolicy.gridColumns,
                horizontalPaddingDp = layoutPolicy.horizontalPaddingDp,
                maxGridHeightDp = layoutPolicy.maxGridHeightDp,
                bottomContentPaddingDp = gridBottomPaddingDp,
                gridItemMinHeightDp = layoutPolicy.gridItemMinHeightDp,
                emptyMessage = "没有匹配的分集",
                onPageSelect = onPageSelect,
                blockParentVerticalScroll = blockParentVerticalScroll,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = layoutPolicy.horizontalPaddingDp.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pages.size, key = { index -> pages[index].cid }) { index ->
                    val page = pages[index]
                    PageSelectorItem(
                        page = page,
                        index = index,
                        isSelected = index == currentPageIndex,
                        modifier = Modifier.width(layoutPolicy.previewItemWidthDp.dp),
                        onClick = onPageSelect
                    )
                }
            }
        }
    }

    if (showExpandedSheet) {
        var expandedQuery by rememberSaveable(pages.size) { mutableStateOf("") }
        var expandedGroupKey by rememberSaveable(pages.size) { mutableStateOf<String?>(null) }
        val expandedVisibleIndices = remember(pages, expandedGroupKey, expandedQuery) {
            filterPageIndicesForSelector(
                pages = pages,
                selectedGroupKey = expandedGroupKey,
                query = expandedQuery
            )
        }

        AppModalBottomSheet(
            onDismissRequest = { showExpandedSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
            windowInsets = WindowInsets(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "分集(${pages.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppIconButton(onClick = { showExpandedSheet = false }) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭选集",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PagesSelectorFilterBar(
                    groups = groups,
                    selectedGroupKey = expandedGroupKey,
                    query = expandedQuery,
                    totalCount = pages.size,
                    onGroupSelect = { expandedGroupKey = it },
                    onQueryChange = { expandedQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                PagesGrid(
                    pages = pages,
                    visiblePageIndices = expandedVisibleIndices,
                    currentPageIndex = currentPageIndex,
                    gridColumns = resolvePagesSelectorLayoutPolicy(
                        widthDp = configuration.screenWidthDp,
                        isLandscape = isLandscape,
                        pagesCount = pages.size,
                        forceGridMode = true
                    ).gridColumns,
                    horizontalPaddingDp = 16,
                    maxGridHeightDp = null,
                    bottomContentPaddingDp = gridBottomPaddingDp,
                    gridItemMinHeightDp = 68,
                    emptyMessage = "没有找到匹配分集",
                    onPageSelect = { index ->
                        onPageSelect(index)
                        showExpandedSheet = false
                    },
                    blockParentVerticalScroll = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PagesSelectorFilterBar(
    groups: List<PageSelectorGroup>,
    selectedGroupKey: String?,
    query: String,
    totalCount: Int,
    onGroupSelect: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AppOutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                AppText(text = "搜索 P号 / 标题")
            },
            leadingIcon = {
                AppIcon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (query.isNotBlank()) {
                {
                    AppIconButton(onClick = { onQueryChange("") }) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "清空搜索"
                        )
                    }
                }
            } else {
                null
            }
        )

        if (groups.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AppFilterChip(
                        selected = selectedGroupKey == null,
                        onClick = { onGroupSelect(null) },
                        label = { AppText("全部 $totalCount") }
                    )
                }
                items(groups, key = { it.key }) { group ->
                    AppFilterChip(
                        selected = selectedGroupKey == group.key,
                        onClick = { onGroupSelect(group.key) },
                        label = { AppText("${group.label} ${group.count}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PagesGrid(
    pages: List<Page>,
    visiblePageIndices: List<Int>,
    currentPageIndex: Int,
    gridColumns: Int,
    horizontalPaddingDp: Int,
    maxGridHeightDp: Int?,
    bottomContentPaddingDp: Int,
    gridItemMinHeightDp: Int,
    emptyMessage: String,
    onPageSelect: (Int) -> Unit,
    blockParentVerticalScroll: Boolean,
    modifier: Modifier = Modifier
) {
    if (visiblePageIndices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val gridModifier = if (maxGridHeightDp != null) {
        modifier.heightIn(max = maxGridHeightDp.dp)
    } else {
        modifier.fillMaxSize()
    }
    val gridState = rememberLazyGridState()
    val nestedScrollConnection = rememberModalChildScrollConnection()

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        state = gridState,
        modifier = if (blockParentVerticalScroll) {
            gridModifier.nestedScroll(nestedScrollConnection)
        } else {
            gridModifier
        },
        contentPadding = PaddingValues(
            start = horizontalPaddingDp.dp,
            end = horizontalPaddingDp.dp,
            bottom = bottomContentPaddingDp.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(visiblePageIndices.size, key = { itemIndex -> pages[visiblePageIndices[itemIndex]].cid }) { itemIndex ->
            val index = visiblePageIndices[itemIndex]
            val page = pages[index]
            PageSelectorItem(
                page = page,
                index = index,
                isSelected = index == currentPageIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = gridItemMinHeightDp.dp),
                onClick = onPageSelect
            )
        }
    }
}

@Composable
private fun PageSelectorItem(
    page: Page,
    index: Int,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: (Int) -> Unit
) {
    AppSingleChoiceRow(
        selected = isSelected,
        onClick = { onClick(index) },
        shape = AppShapes.container(ContainerLevel.Card),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            AppText(
                text = "P${page.page}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            AppText(
                text = page.part.ifEmpty { "第${page.page}P" },
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppSurfaceTokens.onSurfaceContainerHigh()
            )
        }
    }
}
