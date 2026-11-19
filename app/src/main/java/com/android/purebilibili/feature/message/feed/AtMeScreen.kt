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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.TokenManager

data class AtMeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<MessageFeedAtItem> = emptyList(),
    val cursor: Long? = null,
    val cursorTime: Long? = null,
    val hasMore: Boolean = false,
    val error: String? = null,
    val operationError: String? = null,
    val operationBusy: Boolean = false,
    val loadMoreError: String? = null,
)

class AtMeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AtMeUiState())
    val uiState: StateFlow<AtMeUiState> = _uiState.asStateFlow()
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
        requestVersion++
        listJob = accountRequests.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isLoadingMore = false, error = null, loadMoreError = null)
            MessageRepository.getAtFeed().fold(
                onSuccess = { data ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = data.items.orEmpty(),
                        cursor = data.cursor?.id,
                        cursorTime = data.cursor?.time,
                        hasMore = data.cursor?.isEnd != true && !data.items.isNullOrEmpty()
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "加载失败")
                }
            )
        }
    }

    fun refresh() {
        listJob?.cancel()
        moreJob?.cancel()
        requestVersion++
        listJob = accountRequests.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, isLoadingMore = false, error = null, loadMoreError = null)
            MessageRepository.getAtFeed().fold(
                onSuccess = { data ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        items = data.items.orEmpty(),
                        cursor = data.cursor?.id,
                        cursorTime = data.cursor?.time,
                        hasMore = data.cursor?.isEnd != true && !data.items.isNullOrEmpty()
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isRefreshing = false, error = error.message ?: "刷新失败")
                }
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isLoading || current.isRefreshing || !current.hasMore) return
        val version = requestVersion
        moreJob = accountRequests.launch {
            _uiState.value = current.copy(isLoadingMore = true, loadMoreError = null)
            MessageRepository.getAtFeed(cursor = current.cursor, cursorTime = current.cursorTime).fold(
                onSuccess = { data ->
                    if (version != requestVersion) return@fold
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        items = (_uiState.value.items + data.items.orEmpty()).distinctBy { it.id },
                        cursor = data.cursor?.id,
                        cursorTime = data.cursor?.time,
                        hasMore = data.cursor?.isEnd != true && !data.items.isNullOrEmpty() && (data.cursor?.id != current.cursor || data.cursor?.time != current.cursorTime)
                    )
                },
                onFailure = { error ->
                    if (version != requestVersion) return@fold
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, loadMoreError = error.message ?: "加载更多失败")
                }
            )
        }
    }

    fun remove(id: Long) {
        if (_uiState.value.operationBusy) return
        _uiState.value = _uiState.value.copy(operationBusy = true, operationError = null)
        accountRequests.launch {
            MessageRepository.deleteFeedItem(type = 2, id = id).onSuccess {
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.filterNot { it.id == id }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(operationError = error.message ?: "操作失败")
            }
            _uiState.value = _uiState.value.copy(operationBusy = false)
        }
    }
    fun clearOperationError() { _uiState.value = _uiState.value.copy(operationError = null) }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtMeScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    viewModel: AtMeViewModel = viewModel(key = "message_AtMe_" + TokenManager.accountIdentity.collectAsStateWithLifecycle().value)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    uiState.operationError?.let { error -> MessageConfirmDialog("操作失败", error, onDismiss = viewModel::clearOperationError, onConfirm = viewModel::clearOperationError) }

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
