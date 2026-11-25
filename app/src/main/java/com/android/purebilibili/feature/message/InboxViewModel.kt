package com.android.purebilibili.feature.message

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.coroutines.AccountScopedRequests
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.MessageFeedUnreadData
import com.android.purebilibili.data.model.response.SessionItem
import com.android.purebilibili.data.repository.ImMenuItem
import com.android.purebilibili.data.repository.ImSession
import com.android.purebilibili.data.repository.MessageImRepository
import com.android.purebilibili.data.repository.MessageRepository
import com.android.purebilibili.data.repository.MessageReadEvent
import java.util.concurrent.atomic.AtomicLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class UiTextEvent(
    val id: Long,
    val message: String,
)

@Immutable
data class InboxUiState(
    val accountIdentity: AccountSessionIdentity = TokenManager.accountIdentity.value,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sessions: ImmutableList<SessionItem> = persistentListOf(),
    val entries: ImmutableMap<String, ImSession> = persistentMapOf(),
    val feedUnreadData: MessageFeedUnreadData? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isBatchOperating: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val operationError: UiTextEvent? = null,
    val page: Int = 1,
    val endTs: Long = 0,
    val userInfoMap: ImmutableMap<Long, UserBasicInfo> = persistentMapOf(),
    val pageType: Int = 1,
    val title: String = "消息",
    val menu: ImmutableList<ImMenuItem> = persistentListOf(),
    val outside: ImmutableList<ImMenuItem> = persistentListOf(),
)

class InboxViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState = _uiState.asStateFlow()
    private val requests = AccountScopedRequests(viewModelScope)
    private val eventIds = AtomicLong(0L)
    private var generation = 0L
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var readStateJob: Job? = null
    private var pagination = byteArrayOf()
    private val parents = mutableListOf<Pair<Int, String>>()

    init {
        requests.observe {
            generation++
            parents.clear()
            pagination = byteArrayOf()
            _uiState.value = InboxUiState(accountIdentity = requests.identity)
            loadSessions()
        }
        loadSessions()
        viewModelScope.launch {
            MessageRepository.readEvents.collect { event ->
                if (event is MessageReadEvent.PrivateSession) applyReadAck(event)
                readStateJob?.cancel()
                readStateJob = requests.launch {
                    val result = MessageRepository.getFeedUnread()
                    if (requests.isCurrent()) {
                        result.onSuccess { unread ->
                            _uiState.update { it.copy(feedUnreadData = unread) }
                        }
                    }
                }
            }
        }
    }

    fun entry(session: SessionItem): ImSession? = _uiState.value.entries[InboxSessionPaginationPolicy.resolveSessionKey(session)]

    private fun applyReadAck(event: MessageReadEvent.PrivateSession) {
        _uiState.update { state ->
            val entries = state.entries.mapValues { (_, entry) ->
                if (entry.item.talker_id == event.talkerId && entry.item.session_type == event.sessionType) {
                    mergeReadAck(entry, event.ackSeqno)
                } else entry
            }.toImmutableMap()
            state.copy(
                entries = entries,
                sessions = state.sessions.map { session ->
                    entries[InboxSessionPaginationPolicy.resolveSessionKey(session)]?.item ?: session
                }.toImmutableList(),
            )
        }
    }

    private fun mergeReadAck(entry: ImSession, ackSeqno: Long): ImSession {
        val item = InboxSessionPaginationPolicy.applyReadAck(
            entry.item, entry.item.talker_id, entry.item.session_type, ackSeqno,
        )
        return if (item === entry.item) entry else entry.copy(
            item = item,
            unreadLabel = if (ackSeqno >= item.max_seqno) "" else entry.unreadLabel,
            unreadDot = entry.unreadDot && ackSeqno < item.max_seqno,
        )
    }

    private fun retainReadAck(entry: ImSession): ImSession {
        val key = InboxSessionPaginationPolicy.resolveSessionKey(entry.item)
        return mergeReadAck(entry, _uiState.value.entries[key]?.item?.ack_seqno ?: 0L)
    }

    private fun rememberSessionUsers(items: List<SessionItem>): ImmutableMap<Long, UserBasicInfo> =
        items.mapNotNull { item ->
            val user = InboxUserInfoResolver.sessionUserInfo(item) ?: return@mapNotNull null
            MessageUserInfoLoader.remember(user)?.let { item.talker_id to it }
        }.toMap().toImmutableMap()

    fun openFolder(session: SessionItem): Boolean {
        val folder = entry(session)?.folderPage ?: return false
        parents += _uiState.value.pageType to _uiState.value.title
        navigatePage(folder, session.account_info?.name.orEmpty().ifBlank { "消息" })
        return true
    }

    fun back(): Boolean {
        val parent = parents.removeLastOrNull() ?: return false
        navigatePage(parent.first, parent.second)
        return true
    }

    private fun navigatePage(pageType: Int, title: String) {
        listJob?.cancel()
        moreJob?.cancel()
        generation++
        pagination = byteArrayOf()
        _uiState.update { InboxUiState(accountIdentity = it.accountIdentity, pageType = pageType, title = title, feedUnreadData = it.feedUnreadData) }
        loadSessions()
    }

    fun loadSessions() = load(refresh = false)
    fun refresh() = load(refresh = true)
    internal fun refreshFromForeground(snapshot: ForegroundMessageSnapshot) {
        if (snapshot.identity == requests.identity && requests.isCurrent() &&
            listJob?.isActive != true && moreJob?.isActive != true
        ) {
            load(refresh = true, unread = snapshot.unread)
        }
    }

    private fun load(refresh: Boolean, unread: com.android.purebilibili.data.repository.MessageCenterUnread? = null) {
        listJob?.cancel()
        moreJob?.cancel()
        val version = ++generation
        val pageType = _uiState.value.pageType
        _uiState.update { it.copy(isLoading = it.sessions.isEmpty(), isRefreshing = refresh, isLoadingMore = false, error = null, loadMoreError = null) }
        listJob = requests.launch {
            coroutineScope {
                // 未读摘要独立补齐，会话列表不等待统计或统计备用接口。
                launch {
                    val centerUnread = unread ?: MessageRepository.getMessageCenterUnread().getOrNull()
                    val centerFeed = centerUnread?.let {
                        MessageFeedUnreadData(reply = it.reply, at = it.at, like = it.like, sysMsg = it.sysMsg)
                    }
                    val feedData = centerFeed ?: MessageRepository.getFeedUnread().getOrNull()
                    if (version != generation || !requests.isCurrent()) return@launch
                    feedData?.let { data -> _uiState.update { it.copy(feedUnreadData = data) } }
                }
                val result = MessageImRepository.sessions(pageType)
                if (version != generation || !requests.isCurrent()) return@coroutineScope
                result.fold(onSuccess = { data ->
                    pagination = data.pagination
                    val entries = data.sessions.distinctBy { it.key }.map(::retainReadAck)
                    val items = entries.map { it.item }.toImmutableList()
                    val entryMap = entries.associateBy { InboxSessionPaginationPolicy.resolveSessionKey(it.item) }.toImmutableMap()
                    val userMap = rememberSessionUsers(items)
                    val menuItems = data.menu.toImmutableList()
                    val outsideItems = data.outside.toImmutableList()
                    _uiState.update { it.copy(
                        isLoading = false, isRefreshing = false, isBatchOperating = false,
                        sessions = items, entries = entryMap,
                        userInfoMap = userMap,
                        menu = menuItems, outside = outsideItems, hasMore = data.hasMore,
                        page = 1, endTs = it.endTs + 1,
                    ) }
                }, onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, isBatchOperating = false, error = error.message ?: "加载失败") }
                })
            }
        }
    }

    fun loadMoreSessions() {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) return
        val version = generation
        val cursor = pagination
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        moreJob = requests.launch {
            MessageImRepository.sessions(current.pageType, cursor).fold(onSuccess = { data ->
                if (version != generation || !requests.isCurrent()) return@fold
                val advanced = !cursor.contentEquals(data.pagination)
                pagination = data.pagination
                val incoming = data.sessions.associate { entry ->
                    val retained = retainReadAck(entry)
                    InboxSessionPaginationPolicy.resolveSessionKey(retained.item) to retained
                }
                _uiState.update { state ->
                    val entries = (state.entries + incoming).toImmutableMap()
                    val orderedKeys = (state.sessions.map(InboxSessionPaginationPolicy::resolveSessionKey) + incoming.keys).distinct()
                    val items = orderedKeys.mapNotNull { entries[it]?.item }.toImmutableList()
                    state.copy(
                        isLoadingMore = false, sessions = items, entries = entries,
                        userInfoMap = rememberSessionUsers(items),
                        hasMore = data.hasMore && advanced, page = state.page + 1, endTs = state.endTs + 1,
                    )
                }
            }, onFailure = { error ->
                if (version == generation) _uiState.update { it.copy(isLoadingMore = false, loadMoreError = error.message ?: "加载更多失败") }
            })
        }
    }

    private fun operate(action: suspend () -> Result<Unit>) {
        if (_uiState.value.isBatchOperating) return
        val version = generation
        _uiState.update { it.copy(isBatchOperating = true, operationError = null) }
        requests.launch {
            action().fold(onSuccess = { if (version == generation) refresh() }, onFailure = { error ->
                if (version == generation) {
                    val errorEvent = UiTextEvent(
                        id = eventIds.incrementAndGet(),
                        message = error.message ?: "操作失败",
                    )
                    _uiState.update { it.copy(isBatchOperating = false, operationError = errorEvent) }
                }
            })
        }
    }
    fun markRead(session: SessionItem) {
        val entry = entry(session) ?: return
        val page = _uiState.value.pageType
        operate { MessageImRepository.clearUnread(page, entry) }
    }
    fun markAllRead() { val page = _uiState.value.pageType; operate { MessageImRepository.clearUnread(page) } }
    fun clearSessions() { val page = _uiState.value.pageType; operate { MessageImRepository.clearList(page) } }
    fun removeSession(session: SessionItem) = operate { MessageRepository.removeSession(session.talker_id, session.session_type) }
    fun toggleTop(session: SessionItem) { val entry = entry(session) ?: return; operate { MessageImRepository.pin(entry) } }
    fun toggleDnd(session: SessionItem) = operate { MessageRepository.setSessionDnd(session.talker_id, session.session_type, session.is_dnd != 1) }
    fun toggleIntercept(session: SessionItem) = operate { MessageRepository.setSessionIntercept(session.talker_id, session.is_intercept != 1) }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun consumeOperationError(id: Long) {
        _uiState.update { if (it.operationError?.id == id) it.copy(operationError = null) else it }
    }
}
