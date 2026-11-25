package com.android.purebilibili.feature.message.feed

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.runtime.*
import com.android.purebilibili.feature.message.*
import com.android.purebilibili.data.repository.MessageManagementRepository
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.feature.message.MessageAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.data.model.response.SystemNoticeItem
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
data class SystemNoticeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: ImmutableList<SystemNoticeItem> = persistentListOf(),
    val cursor: Long? = null,
    val hasMore: Boolean = false,
    val error: String? = null,
    val operationBusy: Boolean = false,
    val loadMoreError: String? = null,
)

class SystemNoticeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SystemNoticeUiState())
    val uiState: StateFlow<SystemNoticeUiState> = _uiState.asStateFlow()
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()
    private val accountRequests = AccountScopedRequests(viewModelScope)
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var requestVersion = 0L

    init {
        accountRequests.observe {
            requestVersion++
            _uiState.value = SystemNoticeUiState()
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
            MessageRepository.getSystemNotices().fold(
                onSuccess = { items ->
                    if (version != requestVersion) return@fold
                    items.firstOrNull()?.cursor?.takeIf { it > 0 }?.let {
                        MessageRepository.updateSystemNoticeCursor(it)
                    }
                    val immutableItems = items.toImmutableList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = immutableItems,
                            cursor = immutableItems.lastOrNull()?.cursor,
                            hasMore = immutableItems.isNotEmpty()
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
            MessageRepository.getSystemNotices().fold(
                onSuccess = { items ->
                    if (version != requestVersion) return@fold
                    items.firstOrNull()?.cursor?.takeIf { it > 0 }?.let {
                        MessageRepository.updateSystemNoticeCursor(it)
                    }
                    val immutableItems = items.toImmutableList()
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            items = immutableItems,
                            cursor = immutableItems.lastOrNull()?.cursor,
                            hasMore = immutableItems.isNotEmpty()
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

    fun remove(id: Int) {
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
                MessageManagementRepository.deleteSystemNotice(id.toLong()).fold(
                    onSuccess = {
                        _uiState.update { state ->
                            state.copy(items = state.items.filterNot { it.id == id }.toImmutableList())
                        }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _events.trySend(error.message ?: "删除通知失败")
                    },
                )
            } finally {
                _uiState.update { it.copy(operationBusy = false) }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoadingMore || current.isLoading || current.isRefreshing || !current.hasMore) return
        val version = requestVersion
        val requestCursor = current.cursor
        moreJob = accountRequests.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            MessageRepository.getSystemNotices(cursor = requestCursor).fold(
                onSuccess = { items ->
                    if (version != requestVersion) return@fold
                    _uiState.update { state ->
                        state.copy(
                            isLoadingMore = false,
                            items = (state.items + items).distinctBy { it.id }.toImmutableList(),
                            cursor = items.lastOrNull()?.cursor ?: requestCursor,
                            hasMore = items.isNotEmpty() && items.lastOrNull()?.cursor != requestCursor
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemNoticeScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    viewModel: SystemNoticeViewModel = viewModel(key = "message_SystemNotice_" + TokenManager.accountIdentity.collectAsStateWithLifecycle().value)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember(TokenManager.accountIdentity.collectAsStateWithLifecycle().value) { mutableStateOf<Int?>(null) }
    pendingDelete?.let { id -> MessageConfirmDialog("删除通知", "确定删除该通知？", onDismiss = { pendingDelete = null }) { pendingDelete = null; viewModel.remove(id) } }
    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    AppScaffold(
        blurContentReady = !uiState.isLoading,
        topBar = {
            AppTopBar(
                title = "系统通知",
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
            // Scaffold body already below topBar.
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
                                key = { index -> "system_notice_skeleton_$index" },
                                contentType = { MESSAGE_FEED_CARD_CONTENT_TYPE }
                            ) { index ->
                                MessageFeedNoticeSkeleton(
                                    slotIndex = index,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        uiState.error != null -> {
                            item(key = "system_notice_error") {
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
                            item(key = "system_notice_empty") {
                                MessageFeedEmpty(
                                    text = "暂无系统通知",
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
                                val annotatedContent = rememberSystemNoticeAnnotatedContent(item.content)
                                MessageFeedCard(modifier = Modifier.fillMaxWidth().animateItem()
                                    .appCombinedClickable(onClick = {}, onLongClick = { pendingDelete = item.id })
                                    .messageSecondaryClick { pendingDelete = item.id }) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppText(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        ClickableText(
                                            text = annotatedContent,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = AppContentColors.secondaryText()
                                            ),
                                            onClick = { offset ->
                                                annotatedContent
                                                    .getStringAnnotations(tag = "link", start = offset, end = offset)
                                                    .firstOrNull()
                                                    ?.item
                                                    ?.let(onOpenLink)
                                            }
                                        )
                                        AppText(
                                            text = item.timeAt,
                                            modifier = Modifier
                                                .align(Alignment.End),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.hasMore || uiState.isLoadingMore) {
                        item(key = "system_notice_load_more") {
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
private fun rememberSystemNoticeAnnotatedContent(content: String): AnnotatedString {
    val primaryColor = AppContentColors.accent()
    return remember(content, primaryColor) {
        buildAnnotatedString {
            parseSystemNoticeContentSegments(content).forEach { segment ->
                val link = segment.link
                if (link == null) {
                    append(segment.text)
                } else {
                    pushStringAnnotation(tag = "link", annotation = link)
                    withStyle(SpanStyle(color = primaryColor)) {
                        append(segment.text)
                    }
                    pop()
                }
            }
        }
    }
}
