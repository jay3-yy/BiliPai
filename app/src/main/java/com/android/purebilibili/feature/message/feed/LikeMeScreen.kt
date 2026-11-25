package com.android.purebilibili.feature.message.feed

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.android.purebilibili.feature.message.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.message.MessageAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.data.model.response.MessageFeedLikeItem
import com.android.purebilibili.data.repository.MessageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.TokenManager

@Immutable
data class LikeMeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val latestItems: ImmutableList<MessageFeedLikeItem> = persistentListOf(),
    val totalItems: ImmutableList<MessageFeedLikeItem> = persistentListOf(),
    val cursor: Long? = null,
    val cursorTime: Long? = null,
    val hasMore: Boolean = false,
    val error: String? = null,
    val operationBusy: Boolean = false,
    val loadMoreError: String? = null,
)

private fun mergeLikeItems(
    existing: List<MessageFeedLikeItem>,
    incoming: List<MessageFeedLikeItem>
): ImmutableList<MessageFeedLikeItem> {
    val seen = linkedSetOf<Long>()
    return buildList {
        (existing + incoming).forEach { item ->
            if (seen.add(item.id)) add(item)
        }
    }.toImmutableList()
}

class LikeMeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LikeMeUiState())
    val uiState: StateFlow<LikeMeUiState> = _uiState.asStateFlow()
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var requestVersion = 0L

    init {
        accountRequests.observe {
            requestVersion++
            _uiState.value = LikeMeUiState()
            loadInitial()
        }
        loadInitial()
    }

    fun loadInitial() {
        listJob?.cancel()
        moreJob?.cancel()
        val version = ++requestVersion
        listJob = accountRequests.launch {
            _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null, loadMoreError = null) }
            MessageRepository.getLikeFeed().fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    val latest = data.latest?.items.orEmpty().toImmutableList()
                    val total = data.total?.items.orEmpty().toImmutableList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            latestItems = latest,
                            totalItems = total,
                            cursor = data.total?.cursor?.id,
                            cursorTime = data.total?.cursor?.time,
                            hasMore = data.total?.cursor?.isEnd != true && total.isNotEmpty()
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@fold
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "加载失败") }
                }
            )
        }
    }

    fun refresh() {
        listJob?.cancel()
        moreJob?.cancel()
        val version = ++requestVersion
        listJob = accountRequests.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, error = null, loadMoreError = null) }
            MessageRepository.getLikeFeed().fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    val latest = data.latest?.items.orEmpty().toImmutableList()
                    val total = data.total?.items.orEmpty().toImmutableList()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            latestItems = latest,
                            totalItems = total,
                            cursor = data.total?.cursor?.id,
                            cursorTime = data.total?.cursor?.time,
                            hasMore = data.total?.cursor?.isEnd != true && total.isNotEmpty()
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@fold
                    _uiState.update { it.copy(isRefreshing = false, error = error.message ?: "刷新失败") }
                }
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isLoading || current.isRefreshing || !current.hasMore) return
        val version = requestVersion
        val requestCursor = current.cursor
        val requestCursorTime = current.cursorTime
        moreJob = accountRequests.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            MessageRepository.getLikeFeed(cursor = requestCursor, cursorTime = requestCursorTime).fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    _uiState.update { state ->
                        state.copy(
                            isLoadingMore = false,
                            latestItems = mergeLikeItems(state.latestItems, data.latest?.items.orEmpty()),
                            totalItems = mergeLikeItems(state.totalItems, data.total?.items.orEmpty()),
                            cursor = data.total?.cursor?.id,
                            cursorTime = data.total?.cursor?.time,
                            hasMore = data.total?.cursor?.isEnd != true && !data.total?.items.isNullOrEmpty() && (data.total?.cursor?.id != requestCursor || data.total?.cursor?.time != requestCursorTime)
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (version != requestVersion) return@fold
                    _uiState.update { it.copy(isLoadingMore = false, loadMoreError = error.message ?: "加载更多失败") }
                }
            )
        }
    }

    fun remove(id: Long, latest: Boolean) {
        var started = false
        _uiState.update { current ->
            if (current.operationBusy) current
            else {
                started = true
                current.copy(operationBusy = true)
            }
        }
        if (!started) return
        accountRequests.launch {
            try {
                MessageRepository.deleteFeedItem(type = 0, id = id).onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            latestItems = if (latest) state.latestItems.filterNot { it.id == id }.toImmutableList() else state.latestItems,
                            totalItems = if (latest) state.totalItems else state.totalItems.filterNot { it.id == id }.toImmutableList()
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _events.trySend(error.message ?: "操作失败")
                }
            } finally {
                _uiState.update { it.copy(operationBusy = false) }
            }
        }
    }

    fun toggleNotice(item: MessageFeedLikeItem) {
        var started = false
        _uiState.update { current ->
            if (current.operationBusy) current
            else {
                started = true
                current.copy(operationBusy = true)
            }
        }
        if (!started) return
        accountRequests.launch {
            try {
                val enableNotifications = item.noticeState == 1
                MessageRepository.setLikeFeedNotice(id = item.id, enabled = enableNotifications).onSuccess {
                    fun updateList(list: List<MessageFeedLikeItem>): ImmutableList<MessageFeedLikeItem> {
                        return list.map {
                            if (it.id == item.id) {
                                it.copy(noticeState = if (enableNotifications) 0 else 1)
                            } else {
                                it
                            }
                        }.toImmutableList()
                    }

                    _uiState.update { state ->
                        state.copy(
                            latestItems = updateList(state.latestItems),
                            totalItems = updateList(state.totalItems)
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    _events.trySend(error.message ?: "操作失败")
                }
            } finally {
                _uiState.update { it.copy(operationBusy = false) }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikeMeScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    viewModel: LikeMeViewModel = viewModel(key = "message_LikeMe_" + TokenManager.accountIdentity.collectAsStateWithLifecycle().value)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val account by com.android.purebilibili.core.store.TokenManager.accountIdentity.collectAsStateWithLifecycle()
    var detailId by rememberSaveable(account) { mutableLongStateOf(0L) }
    var detailLink by rememberSaveable(account) { mutableStateOf("") }
    if (detailId > 0L) {
        LikeDetailScreen(cardId = detailId, contentLink = detailLink,
            onBack = { detailId = 0L }, onOpenLink = onOpenLink, onOpenSpace = onOpenSpace)
        return
    }

    AppScaffold(
        blurContentReady = !uiState.isLoading,
        topBar = {
            AppTopBar(
                title = "收到的赞",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 骨架与真实卡片共用同一个 LazyColumn 与同一个 contentType：数据到达时
            // LazyList 在原地替换 item（配合 animateItem 交叉淡入），不再整棵子树重建。
            // Scaffold already pads out the top bar — indicator at box top (inset 0).
            AdaptivePullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                indicatorTopInset = paddingValues.calculateTopPadding(),
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding() + 10.dp, bottom = paddingValues.calculateBottomPadding() + 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        uiState.isLoading -> {
                            items(
                                count = MESSAGE_FEED_SKELETON_ITEM_COUNT,
                                key = { index -> "like_me_skeleton_$index" },
                                contentType = { MESSAGE_FEED_CARD_CONTENT_TYPE }
                            ) { index ->
                                MessageFeedCardSkeleton(
                                    slotIndex = index,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem(),
                                )
                            }
                        }

                        uiState.error != null -> {
                            item(key = "like_me_error") {
                                MessageFeedError(
                                    text = uiState.error ?: "加载失败",
                                    onRetry = viewModel::loadInitial,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.6f)
                                )
                            }
                        }

                        uiState.latestItems.isEmpty() && uiState.totalItems.isEmpty() -> {
                            item(key = "like_me_empty") {
                                MessageFeedEmpty(
                                    text = "暂无点赞消息",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.6f)
                                )
                            }
                        }

                        else -> {
                            if (uiState.latestItems.isNotEmpty()) {
                                item(key = "like_me_latest_header") { MessageFeedSectionHeader("最新") }
                                items(
                                    items = uiState.latestItems,
                                    key = { "latest_${it.id}" },
                                    contentType = { MESSAGE_FEED_CARD_CONTENT_TYPE }
                                ) { item ->
                                    LikeMeCard(
                                        item = item,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .animateItem(),
                                        onClick = {
                                            if (item.counts > 1) { detailId = item.id; detailLink = item.item?.nativeUri?.takeIf { it.contains("://") } ?: item.item?.uri.orEmpty() }
                                            else item.item?.let { content ->
                                                buildMessageFeedCommentNavigationLink(
                                                    nativeUri = content.nativeUri,
                                                    uri = content.uri,
                                                    businessId = content.businessId,
                                                    subjectId = content.subjectId,
                                                    rootId = content.rootId,
                                                    sourceId = content.sourceId,
                                                    targetId = content.targetId,
                                                    business = content.business
                                                )?.let(onOpenLink)
                                            }
                                        },
                                        onUserClick = {
                                            item.users?.firstOrNull()?.mid?.takeIf { it > 0 }?.let(onOpenSpace)
                                        },
                                        onToggleNotice = { viewModel.toggleNotice(item) },
                                        onRemove = { viewModel.remove(item.id, latest = true) }
                                    )
                                }
                            }

                            if (uiState.totalItems.isNotEmpty()) {
                                item(key = "like_me_total_header") { MessageFeedSectionHeader("累计") }
                                items(
                                    items = uiState.totalItems,
                                    key = { "total_${it.id}" },
                                    contentType = { MESSAGE_FEED_CARD_CONTENT_TYPE }
                                ) { item ->
                                    LikeMeCard(
                                        item = item,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .animateItem(),
                                        onClick = {
                                            if (item.counts > 1) { detailId = item.id; detailLink = item.item?.nativeUri?.takeIf { it.contains("://") } ?: item.item?.uri.orEmpty() }
                                            else item.item?.let { content ->
                                                buildMessageFeedCommentNavigationLink(
                                                    nativeUri = content.nativeUri,
                                                    uri = content.uri,
                                                    businessId = content.businessId,
                                                    subjectId = content.subjectId,
                                                    rootId = content.rootId,
                                                    sourceId = content.sourceId,
                                                    targetId = content.targetId,
                                                    business = content.business
                                                )?.let(onOpenLink)
                                            }
                                        },
                                        onUserClick = {
                                            item.users?.firstOrNull()?.mid?.takeIf { it > 0 }?.let(onOpenSpace)
                                        },
                                        onToggleNotice = { viewModel.toggleNotice(item) },
                                        onRemove = { viewModel.remove(item.id, latest = false) }
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.hasMore || uiState.isLoadingMore) {
                        item(key = "like_me_load_more") {
                            LaunchedEffect(uiState.cursor, uiState.loadMoreError) {
                                if (uiState.loadMoreError == null) viewModel.loadMore()
                            }
                            uiState.loadMoreError?.let { AppText(it) }
                            MessageFeedLoadMore(
                                isLoadingMore = uiState.isLoadingMore,
                                hasMore = uiState.hasMore,
                                onLoadMore = viewModel::loadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikeMeCard(
    item: MessageFeedLikeItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onUserClick: () -> Unit,
    onToggleNotice: () -> Unit,
    onRemove: () -> Unit
) {
    val firstUser = item.users?.firstOrNull()
    var showMenu by remember(item.id) { mutableStateOf(false) }
    var confirmation by remember(item.id) { mutableStateOf<String?>(null) }
    if (showMenu) MessageActionDialog(
        actions = listOf(
            MessageAction("删除") { confirmation = "delete" },
            MessageAction(if (item.noticeState == 1) "接收通知" else "不再通知") { if (item.noticeState == 1) onToggleNotice() else confirmation = "notice" },
        ), onDismiss = { showMenu = false },
    )
    confirmation?.let { action -> MessageConfirmDialog(
        if (action == "delete") "删除" else "不再通知",
        if (action == "delete") "该条通知删除后，当有新点赞时会重新出现在列表，是否继续？" else "这条内容的点赞将不再通知，但仍可在列表内查看，是否继续？",
        onDismiss = { confirmation = null },
    ) { confirmation = null; if (action == "delete") onRemove() else onToggleNotice() } }
    MessageFeedCard(
        modifier = modifier
            .fillMaxWidth()
            .appCombinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .messageSecondaryClick { showMenu = true }
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            MessageFeedUserAvatar(
                avatarUrl = firstUser?.avatar.orEmpty(),
                mid = firstUser?.mid,
                onClick = onUserClick,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = buildString {
                        append(firstUser?.nickname.orEmpty().ifBlank { "用户" })
                        if (item.counts > 1) append(" 等${item.counts}人")
                        append(" 赞了我的")
                        append(item.item?.business.orEmpty().ifBlank { "内容" })
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                item.item?.title?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(text = it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = formatMessageFeedTime(item.likeTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )

                }
            }
        }
    }
}
