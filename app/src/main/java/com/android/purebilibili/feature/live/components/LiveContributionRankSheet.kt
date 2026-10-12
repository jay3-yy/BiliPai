package com.android.purebilibili.feature.live.components
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.feature.live.resolveLiveSheetVisualSpec
import com.android.purebilibili.feature.live.AnchorInfo
import com.android.purebilibili.feature.live.RoomInfo
import com.android.purebilibili.feature.live.formatLiveDuration
import com.android.purebilibili.feature.live.formatLiveViewerCount
import com.android.purebilibili.data.model.response.LiveContributionRankItem
import com.android.purebilibili.data.repository.LiveContributionRankType
import com.android.purebilibili.data.repository.LiveRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveContributionRankSheet(
    roomTitle: String,
    anchorInfo: AnchorInfo,
    roomInfo: RoomInfo,
    onDismiss: () -> Unit,
    onUserClick: (Long) -> Unit = {},
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rankTypes = remember { LiveContributionRankType.entries.toList() }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { rankTypes.size })
    val pageStates = remember(roomInfo.roomId) { rankTypes.associateWith { LiveRankPageState() } }
    val listStates = remember(roomInfo.roomId) { rankTypes.map { androidx.compose.foundation.lazy.LazyListState() } }
    val panelHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.65f).dp
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().height(panelHeight)) {
            AppText("高能榜", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp))
            AppThemeAdaptiveTabRow(
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                options = rankTypes.mapIndexed { index, type -> AppSegmentOption(index, type.title) },
                selectedValue = pager.currentPage,
                onSelectionChange = { index -> scope.launch {
                    if (index == pager.currentPage) listStates[index].animateScrollToItem(0)
                    else com.android.purebilibili.navigation.animatePagerSelection(pager, index)
                } },
                indicatorPositionProvider = { pager.currentPage + pager.currentPageOffsetFraction },
                isScrollInProgressProvider = { pager.isScrollInProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            com.android.purebilibili.core.ui.components.AppHorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                LiveRankPage(roomInfo.roomId, anchorInfo.uid, rankTypes[page], pageStates.getValue(rankTypes[page]), listStates[page], onUserClick)
            }
        }
    }
}

private class LiveRankPageState {
    var items by mutableStateOf<List<LiveContributionRankItem>>(emptyList())
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var initialized = false
    val requests = com.android.purebilibili.feature.live.LiveBrowseRequestPolicy()
}

@Composable
private fun LiveRankPage(
    roomId: Long,
    anchorId: Long,
    type: LiveContributionRankType,
    state: LiveRankPageState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Long) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun load(reset: Boolean) {
        val request = if (reset) state.requests.refresh(replace = true) else state.requests.loadMore(state.hasMore)
        if (request == null) return
        state.loading = reset
        state.loadingMore = !reset
        state.error = null
        scope.launch {
            try {
                LiveRepository.getLiveContributionRank(roomId, anchorId, type, request.page).onSuccess { next ->
                    if (!state.requests.succeed(request)) return@onSuccess
                    val previous = if (reset) emptyList() else state.items
                    state.hasMore = next.isNotEmpty() && next.any { candidate -> previous.none { it.uid == candidate.uid } }
                    state.items = (previous + next).distinctBy { it.uid }
                    state.initialized = true
                    state.loading = false
                    state.loadingMore = false
                }.onFailure {
                    if (!state.requests.fail(request)) return@onFailure
                    state.error = it.message ?: "获取高能榜失败"
                    state.loading = false
                    state.loadingMore = false
                }
            } finally {
                if (state.requests.owns(request)) {
                    state.requests.fail(request)
                    state.loading = false
                    state.loadingMore = false
                }
            }
        }
    }
    LaunchedEffect(roomId, type) { if (!state.initialized) load(true) }
    com.android.purebilibili.core.ui.AdaptivePullToRefreshBox(isRefreshing = state.loading, onRefresh = { load(true) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            if (state.items.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    AppText(if (state.loading) "榜单加载中…" else state.error ?: "暂无榜单数据")
                    if (!state.loading) com.android.purebilibili.core.ui.components.AppTextButton(onClick = { load(true) }) { AppText("重新加载") }
                }
            }
            items(state.items, key = { it.uid }) { item -> RankItemRow(item) { onUserClick(item.uid) } }
            if (state.items.isNotEmpty()) item {
                com.android.purebilibili.feature.live.LiveHomeLoadMoreFooter(state.loadingMore, state.items.size, state.hasMore, state.error) { load(false) }
            }
        }
    }
}

@Composable
private fun RankItemRow(
    item: LiveContributionRankItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppSpacingTokens.Large,
                vertical = AppSpacingTokens.Small
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)
        ) {
            AppText(
                text = item.rank.takeIf { it > 0 }?.toString() ?: "-",
                color = AppSurfaceTokens.primary(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = item.name.ifBlank { "用户" },
                    color = AppSurfaceTokens.onSurface(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val medal = item.medalInfo
                if (medal != null && medal.medalName.isNotBlank()) {
                    AppText(
                        text = "${medal.medalName} Lv.${medal.level}",
                        color = AppSurfaceTokens.onSurfaceVariantSummary(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        AppText(
            text = item.score.takeIf { it > 0 }?.toString() ?: "-",
            color = AppSurfaceTokens.onSurface(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = AppSpacingTokens.Small)
        )
    }
}
