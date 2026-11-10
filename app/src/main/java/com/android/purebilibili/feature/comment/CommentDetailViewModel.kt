package com.android.purebilibili.feature.comment

import android.net.Uri
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPagingSession
import com.android.purebilibili.feature.video.viewmodel.mergeSentSubReplies
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraft
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraftState
import com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey
import com.android.purebilibili.feature.video.viewmodel.hasUnavailableFloor
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyPageRequest
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.feature.video.viewmodel.resetForSort
import com.android.purebilibili.feature.video.viewmodel.resolveRoutedCommentRootReply
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyLoadedTotalCount
import com.android.purebilibili.feature.video.viewmodel.resolveSubReplyRemoteTotalCount
import com.android.purebilibili.feature.video.viewmodel.resolveSentSubReply
import com.android.purebilibili.feature.video.viewmodel.withSentSubReply
import com.android.purebilibili.feature.video.viewmodel.shouldApplySentSubReplyResult
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentDetailFraudResult(
    val rpid: Long,
    val status: CommentFraudStatus
)

class CommentDetailViewModel : ViewModel() {
    private val _subReplyState = MutableStateFlow(SubReplyUiState())
    val subReplyState = _subReplyState.asStateFlow()

    private val _likedComments = MutableStateFlow<Set<Long>>(emptySet())
    val likedComments = _likedComments.asStateFlow()

    private val _hatedComments = MutableStateFlow<Set<Long>>(emptySet())
    val hatedComments = _hatedComments.asStateFlow()

    private val _showCommentInput = MutableStateFlow(false)
    val showCommentInput = _showCommentInput.asStateFlow()

    private val _replyingTo = MutableStateFlow<ReplyItem?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    private val _composerDrafts = MutableStateFlow(CommentComposerDraftState())
    val composerDrafts = _composerDrafts.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _fraudResult = MutableStateFlow<CommentDetailFraudResult?>(null)
    val fraudResult = _fraudResult.asStateFlow()
    private var fraudCheckJob: Job? = null

    fun checkCommentFraud(reply: ReplyItem) {
        val oid = currentOid
        if (currentType != 1 || oid <= 0L || reply.rpid <= 0L) return
        fraudCheckJob?.cancel()
        _fraudResult.value = null
        fraudCheckJob = viewModelScope.launch {
            CommentRepository.checkCommentStatus(
                aid = oid,
                rpid = reply.rpid,
                rootId = reply.root,
                hasPictures = !reply.content.pictures.isNullOrEmpty(),
                sentAtSeconds = reply.ctime,
                waitMs = 0L
            ).onSuccess { status ->
                CommentFraudRepository.saveRecord(
                    rpid = reply.rpid,
                    oid = oid,
                    type = 1,
                    root = reply.root,
                    message = reply.content.message,
                    status = status,
                    initialStatus = null
                )
                _fraudResult.value = CommentDetailFraudResult(reply.rpid, status)
            }
        }
    }

    fun dismissFraudResult() {
        _fraudResult.value = null
    }

    private var currentOid: Long = 0L
    private var currentType: Int = 1
    private var currentRootId: Long = 0L
    private var currentTargetReplyId: Long = 0L
    private var subReplyLoadJob: Job? = null
    private var threadSession = 0L
    private var pagingSession: ForwardPagingSession<SubReplyPageRequest, SubReplyPageChunk>? = null
    private var dialogPagingSession: CommentPagingSession? = null
    private var dialogLoadJob: Job? = null
    private var sentReplyStore: SentCommentReplyStore? = null
    private val accountIdentity = TokenManager.accountIdentity
    private var currentAccount = accountIdentity.value

    init {
        viewModelScope.launch {
            accountIdentity.collect { account ->
                if (account != currentAccount) {
                    val oid = currentOid
                    val rootId = currentRootId
                    val type = currentType
                    val target = currentTargetReplyId
                    val wasVisible = _subReplyState.value.visible
                    close()
                    currentAccount = account
                    _subReplyState.value = SubReplyUiState()
                    if (wasVisible && oid > 0L && (rootId > 0L || target > 0L)) loadInitial(oid, rootId, type, target)
                }
            }
        }
    }

    fun close() {
        threadSession++
        subReplyLoadJob?.cancel()
        dialogLoadJob?.cancel()
        pagingSession = null
        dialogPagingSession = null
        sentReplyStore = null
        fraudCheckJob?.cancel()
        _fraudResult.value = null
        _composerDrafts.update { it.reset() }
        hideReplyInput()
        _isSending.value = false
        _likedComments.value = emptySet()
        _hatedComments.value = emptySet()
        // Successful sends and deletions remain in the subject store across UI sessions.
        _subReplyState.value = SubReplyUiState(totalCount = _subReplyState.value.totalCount)
    }

    fun loadInitial(
        oid: Long,
        rootId: Long,
        type: Int = 1,
        targetReplyId: Long = 0L
    ) {
        if (oid <= 0L || (rootId <= 0L && targetReplyId <= 0L)) {
            close()
            _subReplyState.value = SubReplyUiState(visible = true, error = "评论定位参数无效", isEnd = true)
            return
        }
        if (currentAccount == accountIdentity.value &&
            currentOid == oid && currentRootId == rootId && currentType == type &&
            currentTargetReplyId == targetReplyId &&
            _subReplyState.value.visible &&
            (_subReplyState.value.isLoading ||
                ((_subReplyState.value.rootReply != null || _subReplyState.value.items.isNotEmpty()) &&
                    _subReplyState.value.error == null))
        ) {
            return
        }

        if (currentAccount != accountIdentity.value ||
            currentOid != oid || currentRootId != rootId || currentType != type
        ) {
            _subReplyState.value = SubReplyUiState()
        }
        close()
        currentAccount = accountIdentity.value
        currentOid = oid
        currentType = type
        currentRootId = rootId
        currentTargetReplyId = targetReplyId
        val store = SentCommentReplyStores.forSubject(oid, type)
        sentReplyStore = store
        if (targetReplyId <= 0L && store.isRootRemoved(rootId)) {
            _subReplyState.value = SubReplyUiState(visible = true, error = "评论已删除", isEnd = true)
            return
        }

        val localItems = store.repliesFor(rootId).toImmutableList()
        val localRoot = store.rootFor(rootId)
        _subReplyState.value = SubReplyUiState(
            visible = true,
            rootReply = localRoot,
            items = localItems,
            baseItems = localItems,
            totalCount = maxOf(_subReplyState.value.totalCount, localRoot?.count ?: 0, localRoot?.rcount ?: 0, localItems.size),
            isLoading = true,
            error = null,
            targetReplyId = targetReplyId
        )

        startSubReplyPaging()
    }

    fun setSortMode(mode: SubReplySortMode) {
        val state = _subReplyState.value
        if (!state.visible || (state.rootReply == null && !state.isLoading) || state.sortMode == mode) return
        subReplyLoadJob?.cancel()
        val localItems = sentReplyStore?.repliesFor(currentRootId).orEmpty().toImmutableList()
        _subReplyState.update { it.resetForSort(mode).copy(items = localItems, baseItems = localItems) }
        startSubReplyPaging()
    }

    fun refresh() {
        val state = _subReplyState.value
        if (!state.visible || currentOid <= 0L || currentRootId <= 0L || state.isRefreshing) return
        // A conversation with a complete floor refreshes itself; without one, retry the full floor.
        val anchor = state.conversationAnchor.takeUnless { state.hasUnavailableFloor }
        dialogPagingSession = null
        dialogLoadJob?.cancel()
        _subReplyState.value = state.copy(
            isLoading = true, isRefreshing = true, error = null,
            conversationAnchor = anchor,
            targetReplyId = if (state.hasUnavailableFloor) state.targetReplyId else 0L,
        )
        if (anchor != null) {
            loadConversationReplies(anchor, page = 1)
        } else {
            startSubReplyPaging()
        }
    }

    fun loadMore() {
        val state = _subReplyState.value
        if (state.isLoading) return
        if (state.conversationAnchor != null) {
            if (!state.isEnd || state.error != null) dialogPagingSession?.loadMore()
            return
        }
        if (state.error != null) {
            if (pagingSession != null) pagingSession?.retry() else startSubReplyPaging()
            return
        }
        if (state.rootReply == null) return
        if (pagingSession == null) {
            // Returning from a conversation dropped the session; reload from the first page.
            _subReplyState.value = state.copy(isLoading = true, error = null)
            startSubReplyPaging()
            return
        }
        if (!state.isEnd) pagingSession?.append()
    }

    private fun startSubReplyPaging() {
        val sortMode = _subReplyState.value.sortMode
        val target = _subReplyState.value.targetReplyId
        val oid = currentOid
        val rootId = currentRootId
        var threadRootId = rootId
        val type = currentType
        val seedRoot = _subReplyState.value.rootReply
        val account = currentAccount
        val sessionId = threadSession
        val store = sentReplyStore ?: return
        subReplyLoadJob?.cancel()
        var appliedPages = 0
        lateinit var session: ForwardPagingSession<SubReplyPageRequest, SubReplyPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                var resolvedRoot = seedRoot
                subReplyPagingSource { request ->
                    val data = CommentRepository.getMobileSortedSubCommentsForSubject(
                        oid = oid, type = type, rootId = threadRootId, mode = sortMode.apiMode,
                        paginationOffset = request.offset,
                        targetReplyId = store.remoteTargetReplyId(rootId, target).takeIf { request.page == 1 } ?: 0L
                    ).getOrThrow()
                    currentCoroutineContext().ensureActive()
                    if (data.resolvedRootId > 0) threadRootId = data.resolvedRootId
                    check(data.root?.let { it.rpid == threadRootId } != false) { "回复楼层不匹配，请重试" }
                    resolvedRoot = resolveRoutedCommentRootReply(emptyList(), data, threadRootId) ?: resolvedRoot
                    check(resolvedRoot != null || data.isTargetOnly) { "评论可能已被删除或不可见" }
                    data
                }
            },
            onChanged = { pages, states ->
                if (pagingSession === session && threadSession == sessionId && accountIdentity.value == account &&
                    currentOid == oid && (currentRootId == rootId || currentRootId == threadRootId) && currentType == type && states != null &&
                    _subReplyState.value.conversationAnchor == null
                ) {
                    val failure = (states.refresh as? LoadState.Error)?.error ?: (states.append as? LoadState.Error)?.error
                    val ready = states.refresh is LoadState.NotLoading && pages.size > appliedPages
                    var recoveredConversationTarget: ReplyItem? = null
                    _subReplyState.update { current ->
                        var next = current
                        if (ready) {
                            val last = pages.last()
                            currentRootId = threadRootId
                            currentTargetReplyId = pages.first().data.resolvedTargetReplyId.takeIf { it > 0L }
                                ?: target.takeIf { it != threadRootId } ?: 0L
                            val remoteRoot = resolveRoutedCommentRootReply(emptyList(), last.data, threadRootId)
                                ?: current.rootReply?.takeIf { it.rpid == threadRootId } ?: seedRoot?.takeIf { it.rpid == threadRootId }
                            val root = remoteRoot?.let { store.merge(listOf(it)).firstOrNull { reply -> reply.rpid == threadRootId } }
                            if (root != null || last.data.isTargetOnly) {
                                // Rebuild from server pages, never from items carrying a local overlay.
                                val remoteItems = pages.flatMap { it.data.replies.orEmpty() }
                                    .filterNot { store.isReplyRemoved(it.rpid) }
                                val items = mergeSentSubReplies(
                                    remoteItems,
                                    store.repliesFor(threadRootId).filterNot { store.isReplyRemoved(it.rpid) }
                                )
                                val total = if (last.data.isTargetOnly) 0 else resolveSubReplyLoadedTotalCount(
                                    rootReply = root, loadedReplyCount = items.size,
                                    remoteReplyCount = resolveSubReplyRemoteTotalCount(last.data, root),
                                    previousTotalCount = if (current.rootReply == null || current.threadRootId != threadRootId) 0 else current.totalCount
                                )
                                next = current.copy(
                                    visible = true, rootReply = root,
                                    targetReplyId = pages.first().data.resolvedTargetReplyId.takeIf { it > 0L } ?: target.takeIf { it != threadRootId } ?: 0L,
                                    items = items.toImmutableList(), baseItems = items.toImmutableList(),
                                    totalCount = total, page = last.request.page, basePage = last.request.page,
                                    grpcNextOffset = last.data.grpcNextOffset, baseGrpcNextOffset = last.data.grpcNextOffset,
                                    retryRequest = null
                                )
                                // An unreadable floor falls back to the recovered target's conversation.
                                if (root == null) {
                                    recoveredConversationTarget = items.firstOrNull { it.rpid == next.targetReplyId }
                                }
                            }
                        }
                        next.copy(
                            isLoading = states.refresh is LoadState.Loading || states.append is LoadState.Loading,
                            isRefreshing = current.isRefreshing && states.refresh is LoadState.Loading,
                            isEnd = states.append.endOfPaginationReached,
                            baseIsEnd = states.append.endOfPaginationReached,
                            error = failure?.let { it.message ?: "回复加载失败" },
                        )
                    }
                    // An unreadable floor opens the recovered target's conversation instead of a bare target.
                    recoveredConversationTarget?.let(::openConversation)
                    if (ready) appliedPages = pages.size
                }
            }
        )
        pagingSession = session
        subReplyLoadJob = viewModelScope.launch { session.run() }
    }

    fun openConversation(reply: ReplyItem) {
        val current = _subReplyState.value
        if (!current.visible || reply.root <= 0L || reply.dialog <= 0L ||
            reply.parent == reply.root || reply.root != current.threadRootId
        ) return
        val baseItems = if (current.conversationAnchor == null) current.items else current.baseItems
        val dialogId = reply.dialog
        val parentId = reply.parent
        val anchorId = reply.rpid
        val localItems = baseItems.filter { candidate ->
            candidate.rpid == anchorId ||
                (dialogId > 0 && (
                    candidate.dialog == dialogId ||
                        candidate.rpid == dialogId ||
                        candidate.parent == dialogId
                    )) ||
                (parentId > 0 && (
                    candidate.rpid == parentId ||
                        candidate.parent == parentId
                    ))
        }.ifEmpty { listOf(reply) }.distinctBy { it.rpid }
        dialogPagingSession = null
        _subReplyState.value = current.copy(
            items = localItems.toImmutableList(),
            baseItems = baseItems.toImmutableList(),
            basePage = current.page,
            baseIsEnd = current.isEnd,
            baseGrpcNextOffset = current.grpcNextOffset,
            conversationAnchor = reply,
            page = 1,
            isEnd = false,
            isLoading = true,
            isRefreshing = false,
            retryRequest = null,
            error = null,
            grpcNextOffset = null,
        )
        loadConversationReplies(reply, page = 1)
    }

    fun closeConversation() {
        val current = _subReplyState.value
        if (current.conversationAnchor == null) return
        dialogPagingSession = null
        dialogLoadJob?.cancel()
        if (current.rootReply == null) {
            close()
            return
        }
        _subReplyState.value = current.copy(
            items = current.baseItems,
            page = current.basePage,
            isEnd = current.baseIsEnd,
            grpcNextOffset = current.baseGrpcNextOffset,
            conversationAnchor = null,
            isLoading = false,
            isRefreshing = false,
            retryRequest = null,
            error = null,
        )
    }

    private fun loadConversationReplies(anchorReply: ReplyItem, page: Int) {
        val rootId = anchorReply.root
        val dialogId = anchorReply.dialog
        if (rootId <= 0L || dialogId <= 0L || anchorReply.parent == rootId) return
        val oid = currentOid
        val type = currentType
        if (oid <= 0L || type <= 0 || currentRootId != rootId) return
        val sessionId = threadSession
        val account = currentAccount
        dialogLoadJob?.cancel()
        var remoteItems = if (page > 1) {
            _subReplyState.value.items.filter { it.rpid !in (sentReplyStore?.repliesFor(rootId)?.map { it.rpid }?.toSet() ?: emptySet()) }
        } else emptyList()
        lateinit var session: CommentPagingSession
        session = CommentPagingSession(
            initial = CommentPageRequest(page = page, offset = null), mainList = false,
            load = { request ->
                CommentRepository.getDialogCommentsForSubject(
                    oid = oid, type = type, rootId = rootId, dialogId = dialogId,
                    page = request.page, paginationOffset = request.offset
                ).getOrThrow()
            },
            onPage = { request, data ->
                if (dialogPagingSession === session && threadSession == sessionId &&
                    accountIdentity.value == account && _subReplyState.value.conversationAnchor?.let { it.rpid } == anchorReply.rpid
                ) {
                    val current = _subReplyState.value
                    val newItems = data.replies.orEmpty()
                    val nextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() }
                    remoteItems = if (request.page == 1) newItems else remoteItems + newItems
                    // DialogList can omit the known target from its first page.
                    val knownTarget = if (current.rootReply == null) anchorReply else null
                    val updatedItems = mergeSentSubReplies(remoteItems, listOfNotNull(knownTarget))
                        .filterNot { sentReplyStore?.isReplyRemoved(it.rpid) == true }
                    _subReplyState.value = current.copy(
                        items = updatedItems.ifEmpty { current.items }.toImmutableList(),
                        isLoading = false,
                        isRefreshing = false,
                        page = request.page,
                        isEnd = data.cursor.isEnd,
                        error = null,
                        retryRequest = null,
                        grpcNextOffset = nextOffset,
                    )
                }
            },
            onState = { loading, end, failure ->
                if (dialogPagingSession === session && threadSession == sessionId &&
                    accountIdentity.value == account && _subReplyState.value.conversationAnchor?.let { it.rpid } == anchorReply.rpid
                ) {
                    _subReplyState.update {
                        it.copy(
                            isLoading = loading,
                            isRefreshing = it.isRefreshing && loading,
                            isEnd = end,
                            error = failure?.let { error -> error.message ?: "对话加载失败" },
                        )
                    }
                }
            },
        )
        dialogPagingSession = session
        dialogLoadJob = viewModelScope.launch { session.run() }
    }

    fun showReplyInput(reply: ReplyItem? = null) {
        _composerDrafts.update { it.open() }
        _replyingTo.value = reply ?: _subReplyState.value.rootReply
        _showCommentInput.value = true
    }

    fun hideReplyInput() {
        _showCommentInput.value = false
        _replyingTo.value = null
    }

    fun updateCommentDraft(
        sessionId: Long,
        replyRpid: Long?,
        text: String,
        imageUris: List<Uri>,
        syncToDynamic: Boolean
    ) {
        _composerDrafts.update {
            it.update(sessionId, replyRpid, CommentComposerDraft(text, imageUris, syncToDynamic))
        }
    }

    fun sendReply(
        message: String,
        pictures: List<Uri> = emptyList(),
        syncToDynamic: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSending.value || currentAccount != accountIdentity.value) return
        val rootReply = _subReplyState.value.rootReply
        val rootId = currentRootId
        if (rootId <= 0L) return
        val replying = _replyingTo.value ?: rootReply ?: return
        val parentId = if (replying.rpid == rootId) 0L else replying.rpid
        val oid = currentOid
        val type = currentType
        val sendSession = threadSession
        val sendAccount = currentAccount
        val store = sentReplyStore ?: return
        val sendDraftSession = _composerDrafts.value.sessionId
        val sendDraftReplyRpid = replying.rpid
        val sentDraft = _composerDrafts.value.drafts[commentComposerDraftKey(sendDraftReplyRpid)]
        _isSending.value = true

        viewModelScope.launch {
            // Bind the result to the thread that opened this composer, not the current destination.
            CommentRepository.addCommentForSubject(
                oid = oid, type = type, message = message, root = rootId,
                parent = parentId, syncToDynamic = syncToDynamic
            ).onSuccess { newReply ->
                if (currentOid == oid && currentType == type && currentRootId == rootId &&
                    threadSession == sendSession && accountIdentity.value == sendAccount
                ) {
                    _isSending.value = false
                }
                if (currentOid != oid || currentType != type || accountIdentity.value != sendAccount ||
                    !shouldApplySentSubReplyResult(
                        rootId, sendSession, threadSession, _subReplyState.value
                    )
                ) return@onSuccess
                _composerDrafts.update {
                    it.complete(sendDraftSession, sendDraftReplyRpid, sentDraft)
                }
                if (_composerDrafts.value.sessionId == sendDraftSession) {
                    hideReplyInput()
                }
                if (newReply != null) {
                    if (store.isReplyRemoved(newReply.rpid) || store.isRootRemoved(rootId)) return@onSuccess
                    val resolvedReply = resolveSentSubReply(
                        newReply, rootId, parentId, replying, oid = oid, replyType = type
                    )
                    store.record(resolvedReply, listOfNotNull(rootReply))
                    _subReplyState.update {
                        it.withSentSubReply(resolvedReply, conversationItemsAreFiltered = false)
                    }
                } else {
                    startSubReplyPaging()
                }
                if (_composerDrafts.value.sessionId == sendDraftSession) {
                    onSuccess()
                }
            }.onFailure {
                if (currentOid == oid && currentType == type && currentRootId == rootId &&
                    threadSession == sendSession && accountIdentity.value == sendAccount
                ) {
                    _isSending.value = false
                }
            }
        }
    }

    fun likeComment(rpid: Long) {
        val isCurrentlyLiked = _likedComments.value.contains(rpid)
        val oid = currentOid
        val type = currentType
        _likedComments.update { if (isCurrentlyLiked) it - rpid else it + rpid }
        if (!isCurrentlyLiked) {
            _hatedComments.update { it - rpid }
        }
        viewModelScope.launch {
            CommentRepository.likeCommentForSubject(oid, type, rpid, !isCurrentlyLiked).onFailure {
                _likedComments.update { if (isCurrentlyLiked) it + rpid else it - rpid }
            }
        }
    }

    fun hateComment(rpid: Long) {
        val isCurrentlyHated = _hatedComments.value.contains(rpid)
        val oid = currentOid
        val type = currentType
        _hatedComments.update { if (isCurrentlyHated) it - rpid else it + rpid }
        if (!isCurrentlyHated) {
            _likedComments.update { it - rpid }
        }
        viewModelScope.launch {
            CommentRepository.hateCommentForSubject(oid, type, rpid, !isCurrentlyHated).onFailure {
                _hatedComments.update { if (isCurrentlyHated) it + rpid else it - rpid }
            }
        }
    }

    fun deleteComment(rpid: Long) {
        val oid = currentOid
        val type = currentType
        val session = threadSession
        val account = currentAccount
        val store = sentReplyStore ?: return
        viewModelScope.launch {
            CommentRepository.deleteCommentForSubject(oid, type, rpid).onSuccess {
                if (session != threadSession || currentOid != oid || currentType != type ||
                    accountIdentity.value != account
                ) return@onSuccess
                val state = _subReplyState.value
                if (rpid == currentRootId ||
                    (state.rootReply == null && state.items.isNotEmpty() && state.items.all { it.rpid == rpid })
                ) {
                    if (rpid == currentRootId) store.removeRoot(rpid) else store.removeReply(currentRootId, rpid)
                    close()
                    _subReplyState.value = SubReplyUiState(visible = true, error = "评论已删除", isEnd = true)
                    return@onSuccess
                }
                if (store.isReplyRemoved(rpid)) return@onSuccess
                store.removeReply(currentRootId, rpid)
                val currentItems = _subReplyState.value.items
                _subReplyState.value = _subReplyState.value.copy(
                    items = currentItems.filterNot { it.rpid == rpid }.toImmutableList(),
                    baseItems = _subReplyState.value.baseItems.filterNot { it.rpid == rpid }.toImmutableList(),
                    conversationAnchor = _subReplyState.value.conversationAnchor?.takeUnless { it.rpid == rpid },
                    totalCount = (_subReplyState.value.totalCount - 1).coerceAtLeast(0)
                )
            }
        }
    }

    fun startDissolve(rpid: Long) {
        _subReplyState.update { it.copy(dissolvingIds = (it.dissolvingIds + rpid).toPersistentSet()) }
    }
}
