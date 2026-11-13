package com.android.purebilibili.feature.search

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.android.purebilibili.core.ui.skeleton.TrendingListSkeleton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor
import androidx.compose.ui.graphics.RectangleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTrendingScreen(
    onBack: () -> Unit,
    onKeywordClick: (String) -> Unit,
    viewModel: SearchTrendingViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    AppScaffold(
        blurContentReady = !state.isLoading,
        topBar = {
            AppTopBar(
                title = "bilibili 热搜",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    AppIconButton(onClick = viewModel::refresh) {
                        AppIcon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.background),
                    scrolledContainerColor = globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.background)
                )
            )
        },
        containerColor = globalWallpaperAwareChromeColor(MaterialTheme.colorScheme.background)
    ) { paddingValues ->
        when {
            state.isLoading -> TrendingListSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                itemCount = 10,
            )

            state.error != null && state.items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .appActionClickable(onClick = viewModel::refresh),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = state.error ?: "加载失败，点击重试",
                    color = AppContentColors.secondaryText()
                )
            }

            // Scaffold padding applied on the box — indicator at content top.
            else -> AdaptivePullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                state = pullRefreshState,
                indicatorTopInset = paddingValues.calculateTopPadding(),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = paddingValues.calculateBottomPadding() + 24.dp)
                ) {
                    item {
                        SearchTrendingHero()
                    }
                    itemsIndexed(state.items, key = { index, item -> "${index}_${item.keyword}" }) { index, item ->
                        SearchTrendingRow(
                            index = index,
                            item = item,
                            pinnedCount = state.pinnedCount,
                            onClick = { onKeywordClick(item.keyword) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrendingHero() {
    coil3.compose.AsyncImage(
        model = com.android.purebilibili.R.drawable.bili_trending_banner,
        contentDescription = "bilibili 热搜",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().aspectRatio(1125f / 528f),
    )
}

@Composable
private fun SearchTrendingRow(
    index: Int,
    item: SearchKeywordUiModel,
    pinnedCount: Int,
    onClick: () -> Unit
) {
    AppSurface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RectangleShape
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchTrendingRank(
                    index = index,
                    pinnedCount = pinnedCount
                )
                Spacer(modifier = Modifier.width(18.dp))
                AppText(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                when {
                    item.iconUrl != null -> androidx.compose.foundation.layout.Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        coil3.compose.AsyncImage(
                            model = item.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(width = 24.dp, height = 18.dp)
                        )
                    }

                    item.showLiveBadge -> SearchKeywordBadge(
                        text = "直播中",
                        containerColor = AppContentColors.error(),
                        contentColor = MaterialTheme.colorScheme.onError
                    )

                    !item.subtitle.isNullOrBlank() -> AppText(
                        text = item.subtitle,
                        color = AppContentColors.secondaryText(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            AppHorizontalDivider(
                modifier = Modifier.padding(start = 78.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun SearchTrendingRank(
    index: Int,
    pinnedCount: Int
) {
    if (index < pinnedCount) {
        AppIcon(
            imageVector = Icons.Rounded.North,
            contentDescription = null,
            tint = AppContentColors.error(),
            modifier = Modifier.size(20.dp)
        )
        return
    }

    val rank = index + 1 - pinnedCount
    AppText(
        text = rank.toString(),
        color = when (rank) {
            1 -> MaterialTheme.colorScheme.tertiary
            2 -> MaterialTheme.colorScheme.secondary
            3 -> AppContentColors.accent()
            else -> AppContentColors.secondaryText().copy(alpha = 0.72f)
        },
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic
        )
    )
}
