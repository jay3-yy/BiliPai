package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.core.ui.components.wallpaperMediaCardSurface
import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import com.android.purebilibili.core.ui.components.AppHorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppContentStateAction
import com.android.purebilibili.core.ui.components.AppContentStatePresentation
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppEmptyState
import com.android.purebilibili.core.ui.components.AppErrorState
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLiquidAwareTabRow
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.rememberAppCheckCircleIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.ui.rememberAppRefreshIcon
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.data.model.response.BangumiItem
import com.android.purebilibili.data.model.response.BangumiSearchItem
import com.android.purebilibili.data.model.response.FollowBangumiItem
import com.android.purebilibili.data.model.response.TimelineDay
import com.android.purebilibili.data.model.response.TimelineEpisode
import com.android.purebilibili.feature.bangumi.ui.list.BangumiBadge
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop

internal const val BANGUMI_POSTER_ASPECT_RATIO = 0.75f

private val BangumiGridStatesSaver = Saver<MutableMap<String, LazyGridState>, List<Any>>(
    save = { states ->
        states.flatMap { (key, state) ->
            listOf(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    },
    restore = { values ->
        values.chunked(3).associate { (key, index, offset) ->
            (key as String) to LazyGridState(index as Int, offset as Int)
        }.toMutableMap()
    },
)


@Composable
internal fun rememberBangumiChannelPagerState(
    channel: BangumiChannel,
    enabled: Boolean,
    onChannelSelected: (BangumiChannel) -> Unit,
): PagerState {
    val channels = BangumiChannel.entries
    val pager = rememberPagerState(initialPage = channel.ordinal) { channels.size }
    val latestChannelSelected by rememberUpdatedState(onChannelSelected)
    LaunchedEffect(channel, enabled) {
        if (enabled && pager.currentPage != channel.ordinal) com.android.purebilibili.navigation.animatePagerSelection(pager, channel.ordinal)
    }
    LaunchedEffect(pager, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { if (pager.isScrollInProgress) null else pager.settledPage }
            .collect { page -> if (page != null) latestChannelSelected(channels[page]) }
    }
    return pager
}

@Composable
internal fun BangumiHubContent(
    state: BangumiHubUiState,
    onBangumiClick: (Long) -> Unit,
    onEpisodeClick: (Long, Long) -> Unit,
    onPlayFollow: (Long) -> Unit,
    channelPagerState: PagerState,
    onPrepareChannel: (BangumiChannel) -> Unit,
    onRefreshHome: () -> Unit,
    onLoadMoreHomeRecommendations: () -> Unit,
    onLoadMoreHomeFollows: () -> Unit,
    onRetryTimeline: () -> Unit,
    onTimelineRangeSelected: (BangumiTimelineRange) -> Unit,
    onOpenIndex: () -> Unit,
    onOpenFollow: () -> Unit,
    onIndexCategorySelected: (BangumiIndexCategory) -> Unit,
    onIndexFilterSelected: (BangumiIndexFilterGroupUi, BangumiIndexChoice) -> Unit,
    onToggleFiltersExpanded: () -> Unit,
    onRetryIndexConditions: () -> Unit,
    onRetryIndexResults: () -> Unit,
    onLoadMoreIndexResults: () -> Unit,
    onFollowStatusSelected: (BangumiFollowStatus) -> Unit,
    onRefreshFollow: () -> Unit,
    onLoadMoreFollow: () -> Unit,
    onToggleFollowSelection: (Long) -> Unit,
    onSelectAllFollow: () -> Unit,
    onClearFollowSelection: () -> Unit,
    onMoveSelectedFollow: (BangumiFollowStatus) -> Unit,
    onMoveSingleFollow: (Long, BangumiFollowStatus) -> Unit,
    onUnfollowSingle: (Long) -> Unit,
    onSearchCategorySelected: (BangumiIndexCategory) -> Unit,
    onLoadMoreSearch: () -> Unit,
    onSaveCover: (String, String) -> Unit,
    onHomeScrollChanged: (firstVisibleIndex: Int, scrollOffset: Int) -> Unit = { _, _ -> },
    scrollToTopRequestId: Int = 0,
    listBottomPadding: Dp = 24.dp,
    listTopPadding: Dp = 0.dp,
    tabBackdrop: Backdrop? = null,
    showFollowStatusTabs: Boolean = true,
) {
    val pageWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val homeGridStates = rememberSaveable(saver = BangumiGridStatesSaver) { mutableMapOf<String, LazyGridState>() }
    val indexGridStates = rememberSaveable(saver = BangumiGridStatesSaver) { mutableMapOf<String, LazyGridState>() }
    val followGridStates = rememberSaveable(saver = BangumiGridStatesSaver) { mutableMapOf<String, LazyGridState>() }
    val searchGridState = rememberLazyGridState()
    var handledScrollToTopRequestId by rememberSaveable { mutableIntStateOf(0) }
    val activeHomeGrid = homeGridStates.getOrPut(state.channel.name) { LazyGridState() }
    LaunchedEffect(activeHomeGrid, state.page, state.channel, pageWorkActive) {
        if (!pageWorkActive) return@LaunchedEffect
        if (state.page != BangumiHubPage.HOME) return@LaunchedEffect
        snapshotFlow {
            activeHomeGrid.firstVisibleItemIndex to activeHomeGrid.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            onHomeScrollChanged(index, offset)
        }
    }
    LaunchedEffect(scrollToTopRequestId, state.page, state.channel, state.followStatus, state.indexCategory, pageWorkActive) {
        if (!pageWorkActive) return@LaunchedEffect
        if (scrollToTopRequestId <= handledScrollToTopRequestId) return@LaunchedEffect
        handledScrollToTopRequestId = scrollToTopRequestId
        val target = when (state.page) {
            BangumiHubPage.HOME -> activeHomeGrid
            BangumiHubPage.INDEX -> indexGridStates.getOrPut(state.indexCategory.name) { LazyGridState() }
            BangumiHubPage.FOLLOW -> followGridStates.getOrPut("${state.channel.name}:${state.followStatus.name}") { LazyGridState() }
            BangumiHubPage.SEARCH -> searchGridState
        }
        if (target.firstVisibleItemIndex > 0 || target.firstVisibleItemScrollOffset > 0) {
            target.animateScrollToTop()
        }
    }
    when (state.page) {
        BangumiHubPage.HOME -> {
            val channels = BangumiChannel.entries
            val pager = channelPagerState
            // 频道滑动复用主题手势策略；各频道的列表状态仍按频道身份独立保存。
            AppHorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { channels[it].name },
            ) { page ->
                val channel = channels[page]
                LaunchedEffect(channel, state.accountGeneration, state.channel, pageWorkActive) {
                    if (pageWorkActive && channel == state.channel) onPrepareChannel(channel)
                }
                BangumiHomeContent(
                    channel = channel,
                    state = state.homeStates[channel] ?: BangumiHomeState(),
                    gridState = homeGridStates.getOrPut(channel.name) { LazyGridState() },
                    isLoggedIn = state.isLoggedIn,
                    showPgcTimeline = state.showPgcTimeline,
                    onBangumiClick = onBangumiClick,
                    onEpisodeClick = onEpisodeClick,
                    onPlayFollow = onPlayFollow,
                    onRefresh = { if (channel == state.channel) onRefreshHome() },
                    onLoadMoreRecommendations = { if (channel == state.channel) onLoadMoreHomeRecommendations() },
                    onLoadMoreFollows = { if (channel == state.channel) onLoadMoreHomeFollows() },
                    onRetryTimeline = { if (channel == state.channel) onRetryTimeline() },
                    onTimelineRangeSelected = { if (channel == state.channel) onTimelineRangeSelected(it) },
                    onOpenIndex = { if (channel == state.channel) onOpenIndex() },
                    onOpenFollow = { if (channel == state.channel) onOpenFollow() },
                    onSaveCover = onSaveCover,
                    listBottomPadding = listBottomPadding,
                    listTopPadding = listTopPadding,
                    tabBackdrop = tabBackdrop,
                )
            }
        }

        BangumiHubPage.INDEX -> BangumiIndexContent(
            channel = state.channel,
            category = state.indexCategory,
            state = state.indexStates[state.indexCategory] ?: BangumiIndexState(),
            gridState = indexGridStates.getOrPut(state.indexCategory.name) { LazyGridState() },
            onCategorySelected = onIndexCategorySelected,
            onFilterSelected = onIndexFilterSelected,
            onToggleExpanded = onToggleFiltersExpanded,
            onRetryConditions = onRetryIndexConditions,
            onRetryResults = onRetryIndexResults,
            onLoadMore = onLoadMoreIndexResults,
            onBangumiClick = onBangumiClick,
            onSaveCover = onSaveCover,
            listBottomPadding = listBottomPadding,
            listTopPadding = listTopPadding,
            tabBackdrop = tabBackdrop,
        )

        BangumiHubPage.FOLLOW -> BangumiFollowContent(
            channel = state.channel,
            status = state.followStatus,
            state = state.followStates[state.channel to state.followStatus] ?: BangumiFollowManagerState(),
            gridState = followGridStates.getOrPut("${state.channel.name}:${state.followStatus.name}") { LazyGridState() },
            onStatusSelected = onFollowStatusSelected,
            onRefresh = onRefreshFollow,
            onLoadMore = onLoadMoreFollow,
            onBangumiClick = onBangumiClick,
            onPlayFollow = onPlayFollow,
            onToggleSelection = onToggleFollowSelection,
            onSelectAll = onSelectAllFollow,
            onClearSelection = onClearFollowSelection,
            onMoveSelected = onMoveSelectedFollow,
            onMoveSingle = onMoveSingleFollow,
            onUnfollowSingle = onUnfollowSingle,
            listBottomPadding = listBottomPadding,
            listTopPadding = listTopPadding,
            tabBackdrop = tabBackdrop,
            showStatusTabs = showFollowStatusTabs,
        )

        BangumiHubPage.SEARCH -> BangumiSearchContent(
            channel = state.channel,
            state = state.search,
            gridState = searchGridState,
            onCategorySelected = onSearchCategorySelected,
            onBangumiClick = onBangumiClick,
            onLoadMore = onLoadMoreSearch,
            onSaveCover = onSaveCover,
            listBottomPadding = listBottomPadding,
            listTopPadding = listTopPadding,
            tabBackdrop = tabBackdrop,
        )
    }
}

@Composable
private fun BangumiHomeContent(
    channel: BangumiChannel,
    state: BangumiHomeState,
    gridState: LazyGridState,
    isLoggedIn: Boolean,
    showPgcTimeline: Boolean,
    onBangumiClick: (Long) -> Unit,
    onEpisodeClick: (Long, Long) -> Unit,
    onPlayFollow: (Long) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreRecommendations: () -> Unit,
    onLoadMoreFollows: () -> Unit,
    onRetryTimeline: () -> Unit,
    onTimelineRangeSelected: (BangumiTimelineRange) -> Unit,
    onOpenIndex: () -> Unit,
    onOpenFollow: () -> Unit,
    onSaveCover: (String, String) -> Unit,
    listBottomPadding: Dp,
    listTopPadding: Dp,
    tabBackdrop: Backdrop?,
) {
    val pageWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val isRefreshing = state.recommendations.isRefreshing ||
        state.follows.isRefreshing || state.timeline.isRefreshing
    AdaptivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, top = listTopPadding + 8.dp, end = 12.dp, bottom = listBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "follow_header") {
                val followLabel = if (channel == BangumiChannel.BANGUMI) "追番" else "追剧"
                val followCount = state.followTotal.takeIf { it >= 0 }?.let { " $it" }.orEmpty()
                SectionHeader(
                    title = "最近$followLabel$followCount",
                    actionLabel = if (isLoggedIn) "查看全部" else null,
                    onAction = onOpenFollow,
                    onRefresh = if (isLoggedIn) onRefresh else null,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "follows") {
                when {
                    !isLoggedIn -> InlineNotice("登录后可同步追番/追剧")
                    state.follows.isLoading && state.follows.items.isEmpty() -> BangumiPosterSkeletonRow()
                    state.follows.error != null && state.follows.items.isEmpty() -> InlineError(
                        message = state.follows.error,
                        onRetry = onRefresh,
                    )
                    state.follows.items.isEmpty() -> InlineNotice("这里还没有内容")
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            state.follows.items,
                            key = { index, item -> resolveMyFollowItemLazyKey(index, item) },
                        ) { index, item ->
                            FollowPosterCard(
                                item = item,
                                onClick = { onBangumiClick(item.seasonId) },
                                onLongClick = { onSaveCover(item.cover, item.title) },
                                onPlay = { onPlayFollow(item.seasonId) },
                                modifier = Modifier.width(104.dp),
                            )
                            if (index == state.follows.items.lastIndex && state.follows.hasMore) {
                                LaunchedEffect(item.seasonId, state.follows.page, pageWorkActive) {
                                    if (pageWorkActive) onLoadMoreFollows()
                                }
                            }
                        }
                        if (state.follows.isLoadingMore) item { InlineLoading(modifier = Modifier.width(64.dp)) }
                    }
                }
            }

            if (showPgcTimeline) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "timeline") {
                    TimelineSection(
                        state = state.timeline,
                        onEpisodeClick = onEpisodeClick,
                        onRetry = onRetryTimeline,
                        onRangeSelected = onTimelineRangeSelected,
                        tabBackdrop = tabBackdrop,
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "recommend_header") {
                SectionHeader(title = "推荐", actionLabel = "更多", onAction = onOpenIndex)
            }
            when {
                state.recommendations.isLoading && state.recommendations.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "recommend_skeleton",
                ) { BangumiPosterGridSkeleton() }
                state.recommendations.error != null && state.recommendations.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "recommend_error",
                ) { InlineError(state.recommendations.error, onRefresh) }
                state.recommendations.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "recommend_empty",
                ) { InlineNotice("暂无推荐") }
            }
            itemsIndexed(
                items = state.recommendations.items,
                key = { index, item -> "recommend_${resolveBangumiIndexItemLazyKey(index, item)}" },
            ) { index, item ->
                PosterGridCard(
                    item = item,
                    onClick = { onBangumiClick(item.seasonId) },
                    onLongClick = { onSaveCover(item.cover, item.title) },
                )
                if (index == state.recommendations.items.lastIndex && state.recommendations.hasMore) {
                    LaunchedEffect(item.seasonId, state.recommendations.page, pageWorkActive) {
                        if (pageWorkActive) onLoadMoreRecommendations()
                    }
                }
            }
            if (state.recommendations.isLoadingMore) item(
                span = { GridItemSpan(maxLineSpan) },
                key = "recommend_more",
            ) { InlineLoading() }
            if (state.recommendations.error != null && state.recommendations.items.isNotEmpty()) item(
                span = { GridItemSpan(maxLineSpan) },
                key = "recommend_footer_error",
            ) { InlineError(state.recommendations.error, onLoadMoreRecommendations) }
        }
    }
}

@Composable
private fun TimelineSection(
    state: BangumiTimelineHubState,
    onEpisodeClick: (Long, Long) -> Unit,
    onRetry: () -> Unit,
    onRangeSelected: (BangumiTimelineRange) -> Unit,
    tabBackdrop: Backdrop?,
) {
    val today = state.days.indexOfFirst { it.isToday == 1 }.coerceAtLeast(0)
    var selectedDay by remember(state.days) { mutableIntStateOf(today) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppText(
            text = "追番时间表",
            style = MaterialTheme.typography.titleMedium,
        )
        val uiStyle = LocalAppUiStyle.current
        val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
        val timelineRangeMinWidth = if (liquidGlassEnabled || uiStyle == AppUiStyle.MATERIAL3) {
            AppChromeSizeTokens.MinimumTouchTarget
        } else {
            104.dp
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AppThemeAdaptiveTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                options = BangumiTimelineRange.entries.map { range ->
                    AppSegmentOption(range, range.label)
                },
                selectedValue = state.range,
                onSelectionChange = onRangeSelected,
                minTabWidth = timelineRangeMinWidth,
                scrollable = false,
                miuixBackdrop = tabBackdrop,
            )
        }
        when {
            state.isLoading && state.days.isEmpty() -> BangumiTimelineSkeleton()
            state.error != null && state.days.isEmpty() -> InlineError(state.error, onRetry)
            state.days.isEmpty() -> InlineNotice("当前范围暂无更新时间表")
            else -> {
                val timelineDayMinWidth = if (liquidGlassEnabled || uiStyle == AppUiStyle.MATERIAL3) {
                    AppChromeSizeTokens.MinimumTouchTarget
                } else {
                    112.dp
                }
                AppThemeAdaptiveTabRow(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                    role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                    options = state.days.mapIndexed { index, item ->
                        AppSegmentOption(index, resolveBangumiTimelineDayLabel(item))
                    },
                    selectedValue = selectedDay,
                    onSelectionChange = { selectedDay = it },
                    // Glass drags on the selected capsule choose a date; other slots remain scrollable.
                    dragSelectionEnabled = liquidGlassEnabled,
                    scrollable = true,
                    minTabWidth = timelineDayMinWidth,
                    miuixBackdrop = tabBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedContent(
                    targetState = selectedDay,
                    transitionSpec = {
                        val direction = if (targetState >= initialState) 1 else -1
                        (
                            slideInHorizontally(animationSpec = tween(220)) { width ->
                                direction * width / 4
                            } + fadeIn(animationSpec = tween(180))
                        ).togetherWith(
                            slideOutHorizontally(animationSpec = tween(180)) { width ->
                                -direction * width / 4
                            } + fadeOut(animationSpec = tween(140))
                        )
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "timelineDayContent",
                ) { targetDay ->
                    val day = state.days.getOrNull(targetDay) ?: state.days.first()
                    if (day.episodes.isNullOrEmpty()) {
                        InlineNotice("当天暂无更新")
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                day.episodes.orEmpty(),
                                key = { index, episode -> resolveTimelineEpisodeLazyKey(index, episode) },
                            ) { _, episode ->
                                TimelineEpisodeCard(
                                    episode = episode,
                                    onClick = { onEpisodeClick(episode.seasonId, episode.episodeId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiIndexContent(
    channel: BangumiChannel,
    category: BangumiIndexCategory,
    state: BangumiIndexState,
    gridState: LazyGridState,
    onCategorySelected: (BangumiIndexCategory) -> Unit,
    onFilterSelected: (BangumiIndexFilterGroupUi, BangumiIndexChoice) -> Unit,
    onToggleExpanded: () -> Unit,
    onRetryConditions: () -> Unit,
    onRetryResults: () -> Unit,
    onLoadMore: () -> Unit,
    onBangumiClick: (Long) -> Unit,
    onSaveCover: (String, String) -> Unit,
    listBottomPadding: Dp,
    listTopPadding: Dp,
    tabBackdrop: Backdrop?,
) {
    val pageWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val scope = rememberCoroutineScope()
    val categories = bangumiIndexCategoriesForChannel(channel)
    AdaptivePullToRefreshBox(
        isRefreshing = state.results.isRefreshing,
        onRefresh = onRetryResults,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, top = listTopPadding + 8.dp, end = 12.dp, bottom = listBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (categories.size > 1) item(
                span = { GridItemSpan(maxLineSpan) },
                key = "index_categories",
            ) {
                val uiStyle = LocalAppUiStyle.current
                // Native Miuix widths do not participate in glass label measurement.
                val categoryMinWidth = if (
                    com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled ||
                    uiStyle == AppUiStyle.MATERIAL3
                ) {
                    AppChromeSizeTokens.MinimumTouchTarget
                } else {
                    108.dp
                }
                AppThemeAdaptiveTabRow(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                    role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                    options = categories.map { AppSegmentOption(it, it.label) },
                    selectedValue = category,
                    scrollable = true,
                    minTabWidth = categoryMinWidth,
                    miuixBackdrop = tabBackdrop,
                    onSelectionChange = {
                        if (it == category) {
                            scope.launch { gridState.animateScrollToItem(0) }
                        } else {
                            onCategorySelected(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                when {
                    state.isConditionLoading -> BangumiFilterSkeleton()
                    state.conditionError != null -> InlineError(state.conditionError, onRetryConditions)
                    else -> IndexFilterPanel(
                        state = state,
                        onFilterSelected = onFilterSelected,
                        onToggleExpanded = onToggleExpanded,
                    )
                }
            }

            when {
                state.results.isLoading && state.results.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "index_skeleton",
                ) { BangumiPosterGridSkeleton() }
                state.results.error != null && state.results.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) }, key = "index_error",
                ) { InlineError(state.results.error, onRetryResults) }
                !state.isConditionLoading && state.results.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) }, key = "index_empty",
                ) { InlineNotice("没有找到符合条件的内容") }
            }
            itemsIndexed(
                state.results.items,
                key = { index, item -> resolveBangumiIndexItemLazyKey(index, item) },
            ) { index, item ->
                PosterGridCard(
                    item = item,
                    onClick = { onBangumiClick(item.seasonId) },
                    onLongClick = { onSaveCover(item.cover, item.title) },
                )
                if (index == state.results.items.lastIndex && state.results.hasMore) {
                    LaunchedEffect(item.seasonId, state.results.page, pageWorkActive) {
                        if (pageWorkActive) onLoadMore()
                    }
                }
            }
            if (state.results.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }, key = "index_more") {
                InlineLoading()
            }
            if (state.results.error != null && state.results.items.isNotEmpty()) item(
                span = { GridItemSpan(maxLineSpan) }, key = "index_footer_error",
            ) { InlineError(state.results.error, onLoadMore) }
        }
    }
}

@Composable
private fun IndexFilterPanel(
    state: BangumiIndexState,
    onFilterSelected: (BangumiIndexFilterGroupUi, BangumiIndexChoice) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val motionEnabled = LocalComponentMotionEnabled.current
    val arrowRotation = animateFloatAsState(
        targetValue = if (state.isExpanded) 180f else 0f,
        animationSpec = if (motionEnabled) spring(dampingRatio = 0.75f, stiffness = 450f) else snap(),
        label = "indexFilterExpandArrow",
    )
    Column {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.conditions.take(5).forEach { group ->
                IndexFilterRow(group, state.selectedParams[group.field], onFilterSelected)
            }
        }
        if (state.conditions.size > 5) {
            AnimatedVisibility(
                visible = state.isExpanded,
                enter = if (motionEnabled) {
                    expandVertically(
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                        expandFrom = Alignment.Top,
                    ) +
                        fadeIn(tween(160))
                } else EnterTransition.None,
                exit = if (motionEnabled) {
                    shrinkVertically(
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                        shrinkTowards = Alignment.Top,
                    ) +
                        fadeOut(tween(120))
                } else ExitTransition.None,
            ) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.conditions.drop(5).forEach { group ->
                        IndexFilterRow(group, state.selectedParams[group.field], onFilterSelected)
                    }
                }
            }
            AppTextButton(
                onClick = onToggleExpanded,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
                    .heightIn(min = 48.dp),
            ) {
                AppIcon(
                    imageVector = rememberAppChevronDownIcon(),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation.value },
                )
                AppText(if (state.isExpanded) "收起" else "展开全部筛选")
            }
        }
    }
}

@Composable
private fun IndexFilterRow(
    group: BangumiIndexFilterGroupUi,
    selectedKeyword: String?,
    onFilterSelected: (BangumiIndexFilterGroupUi, BangumiIndexChoice) -> Unit,
) {
    val selectedChoice = group.choices.firstOrNull { it.keyword == selectedKeyword }
        ?: group.choices.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = group.label,
            modifier = Modifier.width(56.dp),
            color = AppContentColors.secondaryText(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        if (selectedChoice != null) {
            val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
            val filterChoiceMinWidth = if (
                liquidGlassEnabled ||
                LocalAppUiStyle.current == AppUiStyle.MATERIAL3
            ) {
                AppChromeSizeTokens.MinimumTouchTarget
            } else 88.dp
            AppThemeAdaptiveTabRow(
                // Glass indicator fills the measured slot; native geometry keeps its defaults.
                liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                options = group.choices.map {
                    AppSegmentOption(it, resolveBangumiIndexChoiceDisplayLabel(group, it, liquidGlassEnabled))
                },
                selectedValue = selectedChoice,
                scrollable = true,
                centerContent = false,
                minTabWidth = filterChoiceMinWidth,
                onSelectionChange = { onFilterSelected(group, it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BangumiFollowContent(
    channel: BangumiChannel,
    status: BangumiFollowStatus,
    state: BangumiFollowManagerState,
    gridState: LazyGridState,
    onStatusSelected: (BangumiFollowStatus) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBangumiClick: (Long) -> Unit,
    onPlayFollow: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelected: (BangumiFollowStatus) -> Unit,
    onMoveSingle: (Long, BangumiFollowStatus) -> Unit,
    onUnfollowSingle: (Long) -> Unit,
    listBottomPadding: Dp,
    listTopPadding: Dp,
    tabBackdrop: Backdrop?,
    showStatusTabs: Boolean,
) {
    val pageWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val selectionMode = state.selectedIds.isNotEmpty()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().then(
                if (showStatusTabs) Modifier.padding(top = listTopPadding) else Modifier
            ),
        ) {
            if (showStatusTabs) {
                AppLiquidAwareTabRow(
                    // Glass indicator fills the measured slot; native geometry keeps its defaults.
                    liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                    role = com.android.purebilibili.core.ui.components.AppTabRowRole.FILTER,
                    options = BangumiFollowStatus.entries.map { AppSegmentOption(it, it.label) },
                    selectedValue = status,
                    enabled = !state.isMutating,
                    onSelectionChange = onStatusSelected,
                    dragSelectionEnabled = BangumiFollowStatus.entries.size > 1,
                    tapPressRefractionEnabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    miuixBackdrop = tabBackdrop,
                )
            }
            AdaptivePullToRefreshBox(
                isRefreshing = state.content.isRefreshing,
                onRefresh = onRefresh,
                indicatorTopInset = if (showStatusTabs) 0.dp else listTopPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.content.isLoading && state.content.items.isEmpty()) {
                    Box(Modifier.padding(top = if (showStatusTabs) 0.dp else listTopPadding)) {
                        BangumiFollowManagerSkeleton()
                    }
                } else if (state.content.error != null && state.content.items.isEmpty()) {
                    AppErrorState(
                        title = "加载失败",
                        message = state.content.error,
                        primaryAction = AppContentStateAction("重试", onRefresh),
                    )
                } else if (state.content.items.isEmpty()) {
                    AppEmptyState(
                        title = "暂无${status.label}内容",
                        message = "长按条目可进入多选管理",
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val (columns, posterWidth) = with(density) {
                            val contentWidthPx = (maxWidth.roundToPx() - 24.dp.roundToPx()).coerceAtLeast(0)
                            val spacingPx = 12.dp.roundToPx()
                            val count = ((contentWidthPx + spacingPx) / (310.dp.roundToPx() + spacingPx)).coerceAtLeast(1)
                            val cardWidth = ((contentWidthPx - spacingPx * (count - 1)).coerceAtLeast(0) / count).toDp()
                            count to (cardWidth * 0.30f).coerceIn(88.dp, 112.dp)
                        }
                        val alignRows = columns > 1 && !com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible.current
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = if (showStatusTabs) 4.dp else listTopPadding + 4.dp,
                                end = 12.dp,
                                bottom = if (selectionMode) {
                                    listBottomPadding + 88.dp
                                } else {
                                    listBottomPadding
                                },
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val followKeys = state.content.items.mapIndexed { index, item -> resolveMyFollowItemLazyKey(index, item) }
                            val renderFollow: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                                val item = state.content.items[index]
                                FollowInfoCard(
                                    item = item,
                                    modifier = cardModifier,
                                    posterWidth = posterWidth,
                                    matchRowHeight = alignRows,
                                    selected = item.seasonId in state.selectedIds,
                                    selectionMode = selectionMode,
                                    currentStatus = status,
                                    channel = channel,
                                    onClick = {
                                        if (selectionMode) onToggleSelection(item.seasonId)
                                        else onBangumiClick(item.seasonId)
                                    },
                                    onLongClick = { onToggleSelection(item.seasonId) },
                                    onPlay = { onPlayFollow(item.seasonId) },
                                    onMove = { onMoveSingle(item.seasonId, it) },
                                    onUnfollow = { onUnfollowSingle(item.seasonId) },
                                )
                                if (index == state.content.items.lastIndex && state.content.hasMore) {
                                    LaunchedEffect(item.seasonId, state.content.page, pageWorkActive) {
                                        if (pageWorkActive) onLoadMore()
                                    }
                                }
                            }
                            if (!alignRows) {
                                itemsIndexed(state.content.items, key = { index, _ -> followKeys[index] }) { index, _ ->
                                    renderFollow(index, Modifier)
                                }
                            } else {
                                com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(state.content.items.size, columns)
                                    .forEach { row ->
                                        item(key = "follow_row_${followKeys[row.first]}", span = { GridItemSpan(maxLineSpan) }) {
                                            com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                                indices = row,
                                                itemKeys = followKeys,
                                                columns = columns,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                content = renderFollow,
                                            )
                                        }
                                    }
                            }
                            if (state.content.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { InlineLoading() }
                            if (state.content.error != null && state.content.items.isNotEmpty()) item(
                                span = { GridItemSpan(maxLineSpan) },
                            ) { InlineError(state.content.error, onLoadMore) }
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            FollowBatchBar(
                state = state,
                currentStatus = status,
                onSelectAll = onSelectAll,
                onClear = onClearSelection,
                onMove = onMoveSelected,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun FollowBatchBar(
    state: BangumiFollowManagerState,
    currentStatus: BangumiFollowStatus,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onMove: (BangumiFollowStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        color = AppSurfaceTokens.surfaceContainerHigh(),
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppCheckbox(
                    checked = state.selectedIds.size == state.content.items.size && state.content.items.isNotEmpty(),
                    onCheckedChange = { onSelectAll() },
                    enabled = !state.isMutating,
                )
                AppText("已选择 ${state.selectedIds.size} 项", modifier = Modifier.weight(1f))
                AppTextButton(onClick = onClear, enabled = !state.isMutating) { AppText("取消") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BangumiFollowStatus.entries.filter { it != currentStatus }.forEach { target ->
                    AppTextButton(
                        onClick = { onMove(target) },
                        enabled = !state.isMutating,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { AppText("移到${target.label}") }
                }
                if (state.isMutating) AdaptiveLoadingIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun BangumiSearchContent(
    channel: BangumiChannel,
    state: BangumiSearchHubState,
    gridState: LazyGridState,
    onCategorySelected: (BangumiIndexCategory) -> Unit,
    onBangumiClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onSaveCover: (String, String) -> Unit,
    listBottomPadding: Dp,
    listTopPadding: Dp,
    tabBackdrop: Backdrop?,
) {
    val pageWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val results = state.results
    Column(modifier = Modifier.fillMaxSize().padding(top = listTopPadding)) {
        val categories = resolveBangumiSearchCategories(channel)
        val uiStyle = LocalAppUiStyle.current
        val searchCategoryMinWidth = if (uiStyle == AppUiStyle.MATERIAL3) {
            AppChromeSizeTokens.MinimumTouchTarget
        } else {
            104.dp
        }
        val searchTabHorizontalPadding = if (uiStyle == AppUiStyle.MATERIAL3) 0.dp else 12.dp
        AppThemeAdaptiveTabRow(
            // Glass indicator fills the measured slot; native geometry keeps its defaults.
            liquidGlassAdaptIndicatorToLabel = !com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
            role = com.android.purebilibili.core.ui.components.AppTabRowRole.PRIMARY,
            options = categories.map { category -> AppSegmentOption(category, category.label) },
            selectedValue = state.category,
            onSelectionChange = onCategorySelected,
            scrollable = true,
            minTabWidth = searchCategoryMinWidth,
            miuixBackdrop = tabBackdrop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = searchTabHorizontalPadding, vertical = 8.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.query.isBlank() -> AppEmptyState(
                    title = "搜索${state.category.label}",
                    message = "结果会限定在当前分类",
                )
                (results.isLoading || results.isLoadingMore) && results.items.isEmpty() ->
                    com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeleton(
                        minItemWidth = 112.dp,
                        coverAspectRatio = BANGUMI_POSTER_ASPECT_RATIO,
                        horizontalSpacing = 10.dp,
                        verticalSpacing = 14.dp,
                    )
                results.error != null && results.items.isEmpty() ->
                    AppErrorState(
                        title = "搜索失败",
                        message = results.error,
                        primaryAction = AppContentStateAction("重试", onLoadMore),
                    )
                results.items.isEmpty() ->
                    AppEmptyState(
                        title = "当前页没有${state.category.label}结果",
                        message = if (results.hasMore) "可以继续查找后续结果" else "换个关键词试试",
                        primaryAction = if (results.hasMore) {
                            AppContentStateAction("继续查找", onLoadMore)
                        } else {
                            null
                        },
                    )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = listBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(
                        results.items,
                        key = { index, item -> resolveBangumiSearchItemLazyKey(index, item) },
                    ) { index, item ->
                        SearchPosterCard(
                            item = item,
                            onClick = { onBangumiClick(item.seasonId.takeIf { it > 0 } ?: item.pgcSeasonId) },
                            onLongClick = { onSaveCover(item.cover, plainSearchTitle(item)) },
                        )
                        if (index == results.items.lastIndex && results.hasMore) {
                            LaunchedEffect(item.seasonId, results.page, pageWorkActive) {
                                if (pageWorkActive) onLoadMore()
                            }
                        }
                    }
                    if (results.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) { InlineLoading() }
                    if (results.error != null && results.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        InlineError(results.error, onLoadMore)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGridCard(
    item: BangumiItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val transition = rememberBangumiCardTransition(item.seasonId, onClick = onClick)
    // Only the poster is rounded; keep the title block square for cleaner media rows.
    Column(
        modifier = transition.modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = if (transition.enabled) null else LocalIndication.current,
            onClick = transition.onClick,
            onLongClick = onLongClick,
        ),
    ) {
        PosterImage(
            cover = item.cover,
            title = item.title,
            badge = item.badge,
        )
        AppText(
            text = item.title,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = item.newEp?.indexShow?.ifBlank { item.indexShow }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPosterCard(
    item: BangumiSearchItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val transition = rememberBangumiCardTransition(item.seasonId, onClick = onClick)
    Column(
        modifier = transition.modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = if (transition.enabled) null else LocalIndication.current,
            onClick = transition.onClick,
            onLongClick = onLongClick,
        ),
    ) {
        PosterImage(
            cover = item.cover,
            title = plainSearchTitle(item),
            badge = item.badges?.firstOrNull()?.text.orEmpty(),
        )
        AppText(
            text = plainSearchTitle(item),
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = item.indexShow,
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun plainSearchTitle(item: BangumiSearchItem): String = item.orgTitle.ifBlank {
    item.title.replace(Regex("<[^>]*>"), "")
}

@Composable
private fun PosterImage(
    cover: String,
    title: String,
    badge: String = "",
    bottomBadge: String = "",
    modifier: Modifier = Modifier,
    fillCardHeight: Boolean = false,
) {
    Box(
        modifier = modifier
            .then(if (fillCardHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(BANGUMI_POSTER_ASPECT_RATIO))
            .clip(AppShapes.container(if (fillCardHeight) ContainerLevel.Card else ContainerLevel.Field)),
    ) {
        AsyncImage(
            model = FormatUtils.fixImageUrl(cover),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (badge.isNotBlank()) {
            BangumiBadge(text = badge, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        if (bottomBadge.isNotBlank()) {
            BangumiBadge(
                text = bottomBadge,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}

@Composable
private fun FollowPosterCard(
    item: FollowBangumiItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberBangumiCardTransition(item.seasonId, onClick = onClick)
    Column(
        modifier = modifier.then(transition.modifier).combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = if (transition.enabled) null else LocalIndication.current,
            onClick = transition.onClick,
            onLongClick = onLongClick,
        ),
    ) {
        PosterImage(
            cover = item.cover,
            title = item.title,
            badge = item.badge,
        )
        AppText(
            item.title,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.progress.ifBlank { item.newEp?.indexShow.orEmpty() }.takeIf { it.isNotBlank() }?.let { subtitle ->
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.seasonId > 0L) {
            AppTextButton(onClick = onPlay) {
                AppText(if (item.progress.isNotBlank()) "继续观看" else "开始观看", maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineEpisodeCard(episode: TimelineEpisode, onClick: () -> Unit) {
    val transition = rememberBangumiCardTransition(episode.seasonId, episode.episodeId, onClick)
    Column(
        modifier = Modifier
            .then(transition.modifier)
            .width(104.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (transition.enabled) null else LocalIndication.current,
                onClick = transition.onClick,
            ),
    ) {
        PosterImage(
            cover = resolveTimelineEpisodeCover(episode, preferEpisodeCover = false),
            title = episode.title,
            badge = if (episode.follow == 1) "已追番" else "",
            bottomBadge = resolveTimelineEpisodeScheduleLabel(episode),
        )
        AppText(
            episode.title,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        resolveTimelineEpisodeUpdateLabel(episode).takeIf(String::isNotBlank)?.let { updateLabel ->
            AppText(
                text = updateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FollowInfoCard(
    item: FollowBangumiItem,
    posterWidth: Dp,
    modifier: Modifier = Modifier,
    matchRowHeight: Boolean = false,
    selected: Boolean,
    selectionMode: Boolean,
    currentStatus: BangumiFollowStatus,
    channel: BangumiChannel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onMove: (BangumiFollowStatus) -> Unit,
    onUnfollow: () -> Unit,
) {
    val transition = rememberBangumiCardTransition(item.seasonId, onClick = onClick)
    val cardSurface = wallpaperMediaCardSurface(
        containerColor = if (selected) AppSurfaceTokens.secondaryContainer() else AppSurfaceTokens.cardContainer(),
        shape = AppShapes.container(ContainerLevel.Card),
        enabled = !selected,
    )
    AppSurface(
        color = cardSurface.containerColor,
        shape = AppShapes.container(ContainerLevel.Card),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .then(transition.modifier)
            .then(cardSurface.modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (transition.enabled) null else LocalIndication.current,
                onClick = if (selectionMode) onClick else transition.onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (matchRowHeight) Modifier.fillMaxHeight() else Modifier)
                .clip(AppShapes.container(ContainerLevel.Card))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (matchRowHeight) Modifier.fillMaxHeight() else Modifier)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.width(posterWidth).fillMaxHeight()) {
                    PosterImage(
                        cover = item.cover,
                        title = item.title,
                        badge = item.badge,
                        modifier = Modifier.matchParentSize(),
                        fillCardHeight = true,
                    )
                    if (selected) {
                        AppSurface(
                            shape = CircleShape,
                            color = AppContentColors.accent(),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                        ) {
                            AppIcon(
                                rememberAppCheckCircleIcon(),
                                contentDescription = "已选择",
                                tint = AppContentColors.onAccent(),
                                modifier = Modifier.padding(3.dp).size(20.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).padding(top = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppText(item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!selectionMode) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { menuExpanded = true }) {
                                AppIconButton(onClick = { menuExpanded = true }) {
                                    AppIcon(rememberAppMoreIcon(), contentDescription = "更多")
                                }
                                AppDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    BangumiFollowStatus.entries.filter { it != currentStatus }.forEach { target ->
                                        AppDropdownMenuItem(
                                            text = { AppText("移到${target.label}") },
                                            onClick = { menuExpanded = false; onMove(target) },
                                        )
                                    }
                                    AppDropdownMenuItem(
                                        text = { AppText(if (channel == BangumiChannel.BANGUMI) "取消追番" else "取消追剧") },
                                        onClick = { menuExpanded = false; onUnfollow() },
                                    )
                                }
                            }
                        }
                    }
                    item.newEp?.indexShow?.takeIf { it.isNotBlank() }?.let {
                        AppText(it, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                    }
                    item.progress.takeIf { it.isNotBlank() }?.let {
                        AppText(it, style = MaterialTheme.typography.bodySmall, color = AppContentColors.accent())
                    }
                    item.evaluate.takeIf { it.isNotBlank() }?.let {
                        AppText(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!selectionMode && item.seasonId > 0L) {
                        AppTextButton(onClick = onPlay) {
                            AppText(if (item.progress.isNotBlank()) "继续观看" else "开始观看", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { AppText(it, color = AppContentColors.secondaryText(), style = MaterialTheme.typography.bodySmall) }
        }
        onRefresh?.let {
            AppIconButton(onClick = it) {
                AppIcon(rememberAppRefreshIcon(), contentDescription = "刷新")
            }
        }
        actionLabel?.let {
            AppTextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { AppText(it) }
        }
    }
}

@Composable
private fun InlineLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
        AdaptiveLoadingIndicator()
    }
}

@Composable
private fun InlineNotice(message: String) {
    AppEmptyState(
        title = message,
        showIcon = false,
        presentation = AppContentStatePresentation.INLINE,
    )
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    AppErrorState(
        title = "加载失败",
        message = message,
        showIcon = false,
        presentation = AppContentStatePresentation.INLINE,
        primaryAction = AppContentStateAction("重试", onRetry),
    )
}
