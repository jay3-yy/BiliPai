package com.android.purebilibili.feature.message

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val accountIdentity: AccountSessionIdentity = TokenManager.accountIdentity.value,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sessions: List<SessionItem> = emptyList(),
    val entries: Map<String, ImSession> = emptyMap(),
    val feedUnreadData: MessageFeedUnreadData? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isBatchOperating: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val operationError: String? = null,
    val page: Int = 1,
    val endTs: Long = 0,
    val userInfoMap: Map<Long, UserBasicInfo> = emptyMap(),
    val pageType: Int = 1,
    val title: String = "消息",
    val menu: List<ImMenuItem> = emptyList(),
    val outside: List<ImMenuItem> = emptyList(),
)

class InboxViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState = _uiState.asStateFlow()
    private val requests = AccountScopedRequests(viewModelScope)
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
            }
            state.copy(
                entries = entries,
                sessions = state.sessions.map { session ->
                    entries[InboxSessionPaginationPolicy.resolveSessionKey(session)]?.item ?: session
                },
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

    private fun rememberSessionUsers(items: List<SessionItem>): Map<Long, UserBasicInfo> =
        items.mapNotNull { item ->
            val user = InboxUserInfoResolver.sessionUserInfo(item) ?: return@mapNotNull null
            MessageUserInfoLoader.remember(user)?.let { item.talker_id to it }
        }.toMap()

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
    private fun load(refresh: Boolean) {
        listJob?.cancel()
        moreJob?.cancel()
        val version = ++generation
        val pageType = _uiState.value.pageType
        _uiState.update { it.copy(isLoading = it.sessions.isEmpty(), isRefreshing = refresh, isLoadingMore = false, error = null, loadMoreError = null) }
        listJob = requests.launch {
            coroutineScope {
                val center = async { MessageRepository.getMessageCenterUnread() }
                val sessions = async { MessageImRepository.sessions(pageType) }
                val result = sessions.await()
                val centerResult = center.await()
                if (version != generation || !requests.isCurrent()) return@coroutineScope
                // 四项优先用官方 GetTotalUnread；失败回退 REST。
                val centerFeed = centerResult.getOrNull()?.let {
                    MessageFeedUnreadData(reply = it.reply, at = it.at, like = it.like, sysMsg = it.sysMsg)
                }
                val feedData = centerFeed ?: MessageRepository.getFeedUnread().getOrNull()
                feedData?.let { data -> _uiState.update { it.copy(feedUnreadData = data) } }
                result.fold(onSuccess = { data ->
                    pagination = data.pagination
                    val entries = data.sessions.distinctBy { it.key }.map(::retainReadAck)
                    val items = entries.map { it.item }
                    _uiState.update { it.copy(
                        isLoading = false, isRefreshing = false, isBatchOperating = false,
                        sessions = items, entries = entries.associateBy { InboxSessionPaginationPolicy.resolveSessionKey(it.item) },
                        userInfoMap = rememberSessionUsers(items),
                        menu = data.menu, outside = data.outside, hasMore = data.hasMore,
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
                    val entries = state.entries + incoming
                    val orderedKeys = (state.sessions.map(InboxSessionPaginationPolicy::resolveSessionKey) + incoming.keys).distinct()
                    val items = orderedKeys.mapNotNull { entries[it]?.item }
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
                if (version == generation) _uiState.update { it.copy(isBatchOperating = false, operationError = error.message ?: "操作失败") }
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
    fun clearOperationError() { _uiState.update { it.copy(operationError = null) } }
}
