package com.android.purebilibili.feature.message.feed

import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.runtime.*
import com.android.purebilibili.feature.message.*
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
import com.android.purebilibili.data.model.response.MessageFeedAtItem
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
data class AtMeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: ImmutableList<MessageFeedAtItem> = persistentListOf(),
    val cursor: Long? = null,
    val cursorTime: Long? = null,
    val hasMore: Boolean = false,
    val error: String? = null,
    val operationBusy: Boolean = false,
    val loadMoreError: String? = null,
)

class AtMeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AtMeUiState())
    val uiState: StateFlow<AtMeUiState> = _uiState.asStateFlow()
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var requestVersion = 0L

    init {
        accountRequests.observe {
            requestVersion++
            _uiState.value = AtMeUiState()
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
            MessageRepository.getAtFeed().fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    val loaded = data.items.orEmpty().toImmutableList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = loaded,
                            cursor = data.cursor?.id,
                            cursorTime = data.cursor?.time,
                            hasMore = data.cursor?.isEnd != true && loaded.isNotEmpty()
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
            MessageRepository.getAtFeed().fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    val loaded = data.items.orEmpty().toImmutableList()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            items = loaded,
                            cursor = data.cursor?.id,
                            cursorTime = data.cursor?.time,
                            hasMore = data.cursor?.isEnd != true && loaded.isNotEmpty()
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
            MessageRepository.getAtFeed(cursor = requestCursor, cursorTime = requestCursorTime).fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    _uiState.update { state ->
                        state.copy(
                            isLoadingMore = false,
                            items = (state.items + data.items.orEmpty()).distinctBy { it.id }.toImmutableList(),
                            cursor = data.cursor?.id,
                            cursorTime = data.cursor?.time,
                            hasMore = data.cursor?.isEnd != true && !data.items.isNullOrEmpty() && (data.cursor?.id != requestCursor || data.cursor?.time != requestCursorTime)
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

    fun remove(id: Long) {
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
                MessageRepository.deleteFeedItem(type = 2, id = id).onSuccess {
                    _uiState.update { state ->
                        state.copy(items = state.items.filterNot { it.id == id }.toImmutableList())
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
fun AtMeScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    viewModel: AtMeViewModel = viewModel(key = "message_AtMe_" + TokenManager.accountIdentity.collectAsStateWithLifecycle().value)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    AppScaffold(
        blurContentReady = !uiState.isLoading,
        topBar = {
            AppTopBar(
                title = "@我",
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
            AdaptivePullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                indicatorTopInset = paddingValues.calculateTopPadding(),
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = paddingValues.calculateTopPadding() + 12.dp, bottom = paddingValues.calculateBottomPadding() + 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        uiState.isLoading -> {
                            items(
                                count = MESSAGE_FEED_SKELETON_ITEM_COUNT,
                                key = { index -> "at_me_skeleton_$index" },
                                contentType = { MESSAGE_FEED_CARD_CONTENT_TYPE }
                            ) { index ->
                                MessageFeedCardSkeleton(
                                    slotIndex = index,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        uiState.error != null -> {
                            item(key = "at_me_error") {
                                MessageFeedError(
                                    text = uiState.error ?: "加载失败",
                                    onRetry = viewModel::loadInitial,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.6f)
                                )
                            }
                        }

                        uiState.items.isEmpty() -> {
                            item(key = "at_me_empty") {
                                MessageFeedEmpty(
                                    text = "暂无@我消息",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(0.6f)
                                )
                            }
                        }

                        else -> {
                            items(
                                items = uiState.items,
                                key = { it.id },
                                contentType = { MESSAGE_FEED_CARD_CONTENT_TYPE }
                            ) { item ->
                                AtMeCard(
                                    modifier = Modifier.animateItem(),
                                    item = item,
                                    onClick = {
                                        item.item?.let { content ->
                                            buildMessageFeedCommentNavigationLink(
                                                nativeUri = content.nativeUri,
                                                uri = content.uri,
                                                businessId = content.businessId.toInt(),
                                                subjectId = content.subjectId,
                                                rootId = content.rootId,
                                                sourceId = content.sourceId,
                                                targetId = content.targetId,
                                                business = content.business
                                            )?.let(onOpenLink)
                                        }
                                    },
                                    onUserClick = {
                                        item.user?.mid?.takeIf { it > 0 }?.let(onOpenSpace)
                                    },
                                    onRemove = { viewModel.remove(item.id) }
                                )
                            }
                        }
                    }

                    if (uiState.hasMore || uiState.isLoadingMore) {
                        item(key = "at_me_load_more") {
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
private fun AtMeCard(
    item: MessageFeedAtItem,
    onClick: () -> Unit,
    onUserClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    if (confirmDelete) MessageConfirmDialog("删除通知", "确定删除该通知？", onDismiss = { confirmDelete = false }) { confirmDelete = false; onRemove() }
    MessageFeedCard(
        modifier = modifier
            .fillMaxWidth()
            .appCombinedClickable(onClick = onClick, onLongClick = { confirmDelete = true })
            .messageSecondaryClick { confirmDelete = true }
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            MessageFeedUserAvatar(
                avatarUrl = item.user?.avatar.orEmpty(),
                mid = item.user?.mid,
                onClick = onUserClick,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "${item.user?.nickname.orEmpty().ifBlank { "用户" }} 在${item.item?.business.orEmpty().ifBlank { "内容" }}中@了我",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                item.item?.sourceContent?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        text = formatMessageFeedTime(item.atTime.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )

                }
            }
        }
    }
}
