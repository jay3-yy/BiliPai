package com.android.purebilibili.feature.live

import com.android.purebilibili.core.util.animateScrollToTop

import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.animation.LocalLiquidIndicatorDynamicLengthEnabled

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.skeleton.ContentMediaListSkeleton
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeletonFixedColumns
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.data.model.response.LiveAppSearchRoom
import com.android.purebilibili.data.model.response.LiveAppSearchUser
import com.android.purebilibili.data.model.response.cleanSearchText
import com.android.purebilibili.data.model.response.normalizeSearchImageUrl
import com.android.purebilibili.data.repository.LiveRepository
import com.android.purebilibili.data.repository.LiveSearchType
import com.android.purebilibili.navigation.animatePagerSelection
import kotlinx.coroutines.launch

@Composable
fun LiveSearchScreen(
    onBack: () -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onUserClick: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val pager = rememberPagerState(pageCount = { 2 })
    val liveGrid = rememberLazyGridState()
    val userList = rememberLazyGridState()
    val chrome = rememberAppTopChromePolicy()
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    val spec = resolveLiveVisualSpec(chrome.tabPresentation)
    val window = LocalWindowSizeClass.current
    val columns = resolveLiveBiliPaiGridColumns(window.widthDp.value.toInt(), window.isTablet)
    var coverToSave by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    val submitted = keyword.isNotEmpty()
    val roomResults = remember { LiveSearchResults<LiveAppSearchRoom>() }
    val userResults = remember { LiveSearchResults<LiveAppSearchUser>() }

    fun loadRooms(reset: Boolean) = roomResults.load(scope, keyword, reset, LiveAppSearchRoom::roomid) { term, page ->
        LiveRepository.searchLive(term, page, LiveSearchType.ROOM).map {
            it.room?.list.orEmpty() to it.room?.total
        }
    }

    fun loadUsers(reset: Boolean) = userResults.load(scope, keyword, reset, LiveAppSearchUser::roomid) { term, page ->
        LiveRepository.searchLive(term, page, LiveSearchType.USER).map {
            it.user?.list.orEmpty() to it.user?.total
        }
    }

    fun clear() {
        query = ""
        keyword = ""
        roomResults.clear()
        userResults.clear()
    }

    fun submit() {
        val value = query.trim()
        if (value.isEmpty()) return
        keyboard?.hide()
        value.takeIf { it.all(Char::isDigit) }?.toLongOrNull()?.takeIf { it > 0 }?.let {
            onLiveClick(it, "", "")
            return
        }
        roomResults.clear()
        userResults.clear()
        keyword = value
        loadRooms(true)
        loadUsers(true)
        scope.launch { liveGrid.scrollToItem(0) }
        scope.launch { userList.scrollToItem(0) }
    }
    LaunchedEffect(Unit) {
        if (submitted) {
            loadRooms(true)
            loadUsers(true)
        } else {
            focusRequester.requestFocus()
        }
    }
    val tabs = listOf(
        liveSearchTab(0, "正在直播", roomResults.total),
        liveSearchTab(1, "主播", userResults.total),
    )
    AppScaffold(
        blurContentReady = !roomResults.loading || !userResults.loading,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spec.homeMetrics.safeSpaceDp.dp, vertical = AppSpacingTokens.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = onBack) { AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
                        BasicTextField(
                            value = query,
                            onValueChange = { if (it.isEmpty()) clear() else query = it },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppContentColors.primaryText()),
                            cursorBrush = SolidColor(AppContentColors.accent()),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit() }),
                            decorationBox = { input ->
                                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) AppText("搜索房间或主播", color = AppContentColors.secondaryText())
                                    input()
                                }
                            },
                        )
                    }
                    AppIconButton(onClick = {
                        if (query.isEmpty()) onBack() else {
                            clear()
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    }) {
                        AppIcon(Icons.Outlined.Close, "清空")
                    }
                    AppIconButton(onClick = { submit() }, enabled = query.isNotBlank()) {
                        AppIcon(Icons.Outlined.Search, "搜索")
                    }
                }
                if (submitted) {
                    CompositionLocalProvider(LocalLiquidIndicatorDynamicLengthEnabled provides false) {
                        AppThemeAdaptiveTabRow(
                            // 固定槽位保证加载前、实际为零和长计数下的指示器几何一致。
                            scrollable = glass,
                            forceEqualWidth = !glass,
                            liquidGlassAdaptIndicatorToLabel = false,
                            role = AppTabRowRole.SECONDARY,
                            options = tabs,
                            selectedValue = pager.tabSelectedPage,
                            onSelectionChange = { target ->
                                scope.launch {
                                    if (pager.settledPage == target && !pager.isScrollInProgress) {
                                        if (target == 0) liveGrid.animateScrollToTop() else userList.animateScrollToTop()
                                    } else animatePagerSelection(pager, target)
                                }
                            },
                            indicatorPositionProvider = { pager.tabIndicatorPosition },
                            isScrollInProgressProvider = { pager.isScrollInProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (submitted) {
            AppHorizontalPager(
                state = pager,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalPriorityHorizontalPagerSwipe(pager),
            ) { page ->
                val results = if (page == 0) roomResults else userResults
                val active = pager.settledPage == page
                val refresh = { if (page == 0) loadRooms(true) else loadUsers(true) }
                val loadMore = { if (page == 0) loadRooms(false) else loadUsers(false) }
                val grid = if (page == 0) liveGrid else userList
                LiveGridPagination(
                    grid = grid,
                    enabled = active && results.hasMore && !results.loading && !results.loadingMore &&
                        results.error == null && results.moreError == null && results.items.isNotEmpty(),
                    nextPage = results.nextPage,
                    onLoadMore = loadMore,
                )
                AdaptivePullToRefreshBox(
                    isRefreshing = results.loading,
                    onRefresh = refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        results.loading && results.items.isEmpty() -> if (page == 0) {
                            ContentVideoGridSkeletonFixedColumns(columns = columns)
                        } else {
                            ContentMediaListSkeleton(useUserRow = true)
                        }
                        results.error != null -> LiveSearchState(results.error.orEmpty(), onRetry = refresh)
                        results.items.isEmpty() -> LiveSearchState(
                            if (page == 0) "暂无相关直播间" else "暂无相关主播",
                        )
                        else -> LazyVerticalGrid(
                            columns = if (page == 0) LiveRoomGridCells() else LiveRoomGridCells(480.dp),
                            state = grid,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spec.homeMetrics.safeSpaceDp.dp,
                                end = spec.homeMetrics.safeSpaceDp.dp,
                                top = AppSpacingTokens.Small,
                                bottom = maxOf(LocalBottomBarContentPadding.current, AppSpacingTokens.Large),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
                            verticalArrangement = Arrangement.spacedBy(if (page == 0) spec.homeMetrics.cardSpaceDp.dp else 0.dp),
                        ) {
                            if (page == 0) {
                                gridItems(roomResults.items, key = { it.roomid }) { room ->
                                    val card = LiveRoomCardUiModel(
                                        roomId = room.roomid,
                                        title = cleanSearchText(room.title),
                                        coverUrl = normalizeSearchImageUrl(room.cover.ifBlank { room.face }),
                                        hostName = cleanSearchText(room.name),
                                        viewerCount = null,
                                        areaName = "",
                                        viewerText = room.watchedShow?.textLarge.orEmpty(),
                                    )
                                    LiveRoomCard(
                                        model = card,
                                        onClick = { onLiveClick(card.roomId, card.title, card.hostName) },
                                        onLongPress = { coverToSave = card },
                                        searchResult = true,
                                    )
                                }
                            } else {
                                gridItems(userResults.items, key = { it.roomid }) { user ->
                                    LiveSearchUserCard(user, pageActive = active) {
                                        onLiveClick(user.roomid, "", cleanSearchText(user.name))
                                    }
                                }
                            }
                            if (results.hasMore || results.moreError != null) {
                                item(key = LIVE_GRID_LOAD_MORE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                                    LiveHomeLoadMoreFooter(results.loadingMore, results.items.size, results.hasMore, results.moreError, autoLoad = false, onLoadMore = loadMore)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    coverToSave?.let { card -> LiveCoverSaveDialog(card) { coverToSave = null } }
}

private fun liveSearchTab(page: Int, title: String, total: Int?): AppSegmentOption<Int> =
    com.android.purebilibili.core.ui.components.appCountedTabOption(
        value = page,
        label = title,
        count = total,
    )

@Composable
private fun LiveSearchState(message: String, modifier: Modifier = Modifier.fillMaxSize(), onRetry: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(message, color = AppContentColors.secondaryText())
        if (onRetry != null) AppTextButton(onClick = onRetry) { AppText("重新加载") }
    }
}

@Composable
private fun LiveSearchUserCard(item: LiveAppSearchUser, pageActive: Boolean, onClick: () -> Unit) {
    AppSurface(onClick = onClick, enabled = item.roomid > 0, color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = normalizeSearchImageUrl(item.face),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(cleanSearchText(item.name), modifier = Modifier.weight(1f, fill = false), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.liveStatus == 1) {
                        PlaybackBarsIndicator(
                            isPlaying = pageActive,
                            contentDescription = "直播中",
                            color = AppContentColors.accent(),
                            modifier = Modifier.padding(start = 10.dp).size(16.dp),
                        )
                    }
                }
                AppText(
                    buildList {
                        if (item.areaName.isNotBlank()) add("分区：${cleanSearchText(item.areaName)}")
                        item.fansNum?.let { add("关注数：${formatLiveViewerCount(it)}") }
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
