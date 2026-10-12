package com.android.purebilibili.feature.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
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
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.skeleton.ContentMediaListSkeleton
import com.android.purebilibili.core.ui.skeleton.ContentVideoGridSkeletonFixedColumns
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.LiveRoomSearchItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.SearchRepository
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
    val userList = androidx.compose.foundation.lazy.rememberLazyListState()
    val chrome = rememberAppTopChromePolicy()
    val spec = resolveLiveVisualSpec(chrome.tabPresentation)
    val window = LocalWindowSizeClass.current
    val columns = resolveLiveBiliPaiGridColumns(minOf(window.widthDp.value.toInt(), spec.maxContentWidthDp), window.isTablet)
    var coverToSave by remember { mutableStateOf<LiveRoomCardUiModel?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var liveLoading by remember { mutableStateOf(false) }
    var userLoading by remember { mutableStateOf(false) }
    var liveMoreLoading by remember { mutableStateOf(false) }
    var userMoreLoading by remember { mutableStateOf(false) }
    var liveHasMore by remember { mutableStateOf(false) }
    var userHasMore by remember { mutableStateOf(false) }
    var liveTotal by remember { mutableStateOf<Int?>(null) }
    var userTotal by remember { mutableStateOf<Int?>(null) }
    var liveError by remember { mutableStateOf<String?>(null) }
    var userError by remember { mutableStateOf<String?>(null) }
    val rooms = remember { mutableStateListOf<LiveRoomSearchItem>() }
    val users = remember { mutableStateListOf<SearchLiveUserItem>() }
    val liveRequests = remember { LiveBrowseRequestPolicy() }
    val userRequests = remember { LiveBrowseRequestPolicy() }

    fun loadRooms(reset: Boolean) {
        if (keyword.isBlank()) return
        val request = if (reset) liveRequests.refresh(replace = true) else liveRequests.loadMore(liveHasMore)
        if (request == null) return
        val requestedKeyword = keyword
        liveError = null
        liveLoading = reset
        liveMoreLoading = !reset
        scope.launch {
            SearchRepository.searchLive(requestedKeyword, request.page, SearchLiveOrder.ONLINE)
                .onSuccess { (items, info) ->
                    if (!liveRequests.succeed(request, info.currentPage + 1)) return@onSuccess
                    if (reset) rooms.clear()
                    val ids = rooms.map { it.roomid }.toSet()
                    rooms.addAll(items.distinctBy { it.roomid }.filterNot { it.roomid in ids })
                    liveTotal = info.totalResults
                    liveHasMore = info.hasMore
                    liveLoading = false
                    liveMoreLoading = false
                }.onFailure {
                    if (!liveRequests.fail(request)) return@onFailure
                    liveError = it.message ?: "直播搜索失败"
                    liveLoading = false
                    liveMoreLoading = false
                }
        }
    }
    fun loadUsers(reset: Boolean) {
        if (keyword.isBlank()) return
        val request = if (reset) userRequests.refresh(replace = true) else userRequests.loadMore(userHasMore)
        if (request == null) return
        val requestedKeyword = keyword
        userError = null
        userLoading = reset
        userMoreLoading = !reset
        scope.launch {
            SearchRepository.searchLiveUser(requestedKeyword, request.page)
                .onSuccess { (items, info) ->
                    if (!userRequests.succeed(request, info.currentPage + 1)) return@onSuccess
                    if (reset) users.clear()
                    val ids = users.map { it.uid }.toSet()
                    users.addAll(items.distinctBy { it.uid }.filterNot { it.uid in ids })
                    userTotal = info.totalResults
                    userHasMore = info.hasMore
                    userLoading = false
                    userMoreLoading = false
                }.onFailure {
                    if (!userRequests.fail(request)) return@onFailure
                    userError = it.message ?: "主播搜索失败"
                    userLoading = false
                    userMoreLoading = false
                }
        }
    }
    fun clear() {
        query = ""
        keyword = ""
        submitted = false
        liveRequests.invalidate()
        userRequests.invalidate()
        rooms.clear()
        users.clear()
        liveTotal = null
        userTotal = null
        liveLoading = false
        userLoading = false
        liveMoreLoading = false
        userMoreLoading = false
        liveHasMore = false
        userHasMore = false
        liveError = null
        userError = null
        focusRequester.requestFocus()
        keyboard?.show()
    }
    fun submit() {
        val value = query.trim()
        if (value.isEmpty()) return
        keyboard?.hide()
        value.takeIf { it.all(Char::isDigit) }?.toLongOrNull()?.takeIf { it > 0 }?.let {
            onLiveClick(it, "", "")
            return
        }
        keyword = value
        submitted = true
        rooms.clear()
        users.clear()
        liveTotal = null
        userTotal = null
        liveHasMore = false
        userHasMore = false
        loadRooms(true)
        loadUsers(true)
        scope.launch { liveGrid.scrollToItem(0) }
        scope.launch { userList.scrollToItem(0) }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (submitted && keyword.isNotBlank()) {
            loadRooms(true)
            loadUsers(true)
        }
    }
    val tabs = listOf(
        AppSegmentOption(0, "正在直播" + (liveTotal?.let { " $it" } ?: "")),
        AppSegmentOption(1, "主播" + (userTotal?.let { " $it" } ?: "")),
    )
    AppScaffold(
        blurContentReady = !liveLoading || !userLoading,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .responsiveContentWidth(maxWidth = spec.maxContentWidthDp.dp)
                        .fillMaxWidth()
                        .padding(horizontal = spec.homeMetrics.safeSpaceDp.dp, vertical = AppSpacingTokens.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(onClick = onBack) { AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    AppOutlinedTextField(
                        value = query,
                        onValueChange = { if (it.isEmpty()) clear() else query = it },
                        modifier = Modifier.weight(1f),
                        placeholderText = "搜索房间或主播",
                        singleLine = true,
                        focusRequester = focusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        trailingIcon = {
                            AppIconButton(onClick = { if (query.isEmpty()) onBack() else clear() }) {
                                AppIcon(Icons.Outlined.Close, "清空")
                            }
                        },
                    )
                    AppIconButton(onClick = { submit() }, enabled = query.isNotBlank()) {
                        AppIcon(Icons.Outlined.Search, "搜索")
                    }
                }
                if (submitted) {
                    AppThemeAdaptiveTabRow(
                        role = AppTabRowRole.SECONDARY,
                        options = tabs,
                        selectedValue = pager.currentPage,
                        onSelectionChange = { target ->
                            scope.launch {
                                if (pager.currentPage == target) {
                                    if (target == 0) liveGrid.animateScrollToItem(0) else userList.animateScrollToItem(0)
                                } else animatePagerSelection(pager, target)
                            }
                        },
                        indicatorPositionProvider = { pager.currentPage + pager.currentPageOffsetFraction },
                        isScrollInProgressProvider = { pager.isScrollInProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        if (!submitted) {
            LiveSearchState("输入关键词后搜索直播间或主播", modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            AppHorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
                AdaptivePullToRefreshBox(
                    isRefreshing = if (page == 0) liveLoading else userLoading,
                    onRefresh = { if (page == 0) loadRooms(true) else loadUsers(true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        page == 0 && liveLoading && rooms.isEmpty() -> ContentVideoGridSkeletonFixedColumns(columns = columns)
                        page == 1 && userLoading && users.isEmpty() -> ContentMediaListSkeleton(useUserRow = true)
                        page == 0 && rooms.isEmpty() -> LiveSearchState(liveError ?: "暂无相关直播间", onRetry = { loadRooms(true) })
                        page == 1 && users.isEmpty() -> LiveSearchState(userError ?: "暂无相关主播", onRetry = { loadUsers(true) })
                        page == 0 -> LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = liveGrid,
                            modifier = Modifier.responsiveContentWidth(maxWidth = spec.maxContentWidthDp.dp).fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = spec.homeMetrics.safeSpaceDp.dp, vertical = AppSpacingTokens.Small),
                            horizontalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
                            verticalArrangement = Arrangement.spacedBy(spec.homeMetrics.cardSpaceDp.dp),
                        ) {
                            gridItems(rooms, key = { it.roomid }) { room ->
                                LiveRoomCard(
                                    model = LiveRoomCardUiModel(room.roomid, room.title, room.cover.ifBlank { room.uface }, room.uname, room.online, room.area_v2_name),
                                    onClick = { onLiveClick(room.roomid, room.title, room.uname) },
                                    onLongPress = { coverToSave = LiveRoomCardUiModel(room.roomid, room.title, room.cover, room.uname, room.online, room.area_v2_name) },
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LiveHomeLoadMoreFooter(liveMoreLoading, rooms.size, liveHasMore, liveError) { loadRooms(false) }
                            }
                        }
                        else -> LazyColumn(
                            state = userList,
                            modifier = Modifier.responsiveContentWidth(maxWidth = spec.maxContentWidthDp.dp).fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = spec.homeMetrics.safeSpaceDp.dp, vertical = AppSpacingTokens.Small),
                            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
                        ) {
                            items(users, key = { it.uid }) { user ->
                                LiveSearchUserCard(
                                    user,
                                    onClick = { if (user.roomid > 0) onLiveClick(user.roomid, "", user.uname) else onUserClick(user.uid) },
                                    onAvatarClick = { onUserClick(user.uid) },
                                )
                            }
                            item { LiveHomeLoadMoreFooter(userMoreLoading, users.size, userHasMore, userError) { loadUsers(false) } }
                        }
                    }
                }
            }
        }
    }
    coverToSave?.let { card -> LiveCoverSaveDialog(card) { coverToSave = null } }
}

@Composable
private fun LiveSearchState(message: String, modifier: Modifier = Modifier.fillMaxSize(), onRetry: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) AppTextButton(onClick = onRetry) { AppText("重新加载") }
    }
}

@Composable
private fun LiveSearchUserCard(item: SearchLiveUserItem, onClick: () -> Unit, onAvatarClick: () -> Unit) {
    AppSurface(onClick = onClick, color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(AppSpacingTokens.Medium), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.uface,
                contentDescription = "查看${item.uname}的空间",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onAvatarClick),
            )
            Spacer(Modifier.width(AppSpacingTokens.Medium))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(item.uname, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.liveStatus == 1 || item.isLive) AppText("直播中", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                AppText(
                    "分区：${item.areaName.ifBlank { item.tags.ifBlank { "未分类" } }} · 关注数：${item.attentions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
