package com.android.purebilibili.feature.comment

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraft
import com.android.purebilibili.feature.video.viewmodel.CommentComposerDraftState
import com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.feature.video.viewmodel.isSortedSubReplyPageEnd
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun loadInitial(
        oid: Long,
        rootId: Long,
        type: Int = 1,
        targetReplyId: Long = 0L
    ) {
        if (oid <= 0L || rootId <= 0L) return
        if (currentOid == oid && currentRootId == rootId && currentType == type &&
            _subReplyState.value.visible && _subReplyState.value.rootReply != null
        ) {
            return
        }

        if (currentOid != oid || currentRootId != rootId || currentType != type) {
            _composerDrafts.update { it.reset() }
            hideReplyInput()
            _isSending.value = false
        }
        threadSession++
        currentOid = oid
        currentType = type
        currentRootId = rootId
        currentTargetReplyId = targetReplyId

        subReplyLoadJob?.cancel()
        _subReplyState.value = SubReplyUiState(
            visible = true,
            isLoading = true,
            error = null,
            targetReplyId = targetReplyId.takeIf { it != rootId } ?: 0L
        )

        loadSubReplies(page = 1, paginationOffset = null)
    }

    fun setSortMode(mode: SubReplySortMode) {
        val state = _subReplyState.value
        if (state.sortMode == mode || state.isLoading) return
        subReplyLoadJob?.cancel()
        _subReplyState.update { it.resetForSort(mode) }
        loadSubReplies(page = 1, paginationOffset = null)
    }

    fun refresh() {
        val state = _subReplyState.value
        if (currentOid <= 0L || currentRootId <= 0L || state.isRefreshing ||
            (state.isLoading && state.items.isEmpty())
        ) return
        _subReplyState.value = state.copy(isLoading = true, isRefreshing = true, error = null)
        loadSubReplies(page = 1, paginationOffset = null)
    }

    fun loadMore() {
        val state = _subReplyState.value
        if (state.isLoading || state.isEnd || state.rootReply == null) return
        val nextPage = if (state.error != null && state.items.isEmpty()) 1 else state.page + 1
        _subReplyState.value = state.copy(isLoading = true, error = null)
        loadSubReplies(page = nextPage, paginationOffset = state.grpcNextOffset)
    }

    private fun loadSubReplies(page: Int, paginationOffset: String?) {
        val sortMode = _subReplyState.value.sortMode
        val targetReplyId = _subReplyState.value.targetReplyId.takeIf { page == 1 } ?: 0L
        val oid = currentOid
        val rootId = currentRootId
        val type = currentType

        subReplyLoadJob?.cancel()
        subReplyLoadJob = viewModelScope.launch {
            CommentRepository.getSortedSubCommentsForSubject(
                oid = oid,
                type = type,
                rootId = rootId,
                mode = sortMode.apiMode,
                paginationOffset = paginationOffset,
                targetReplyId = targetReplyId
            ).onSuccess { data ->
                if (currentOid != oid || currentRootId != rootId || currentType != type) return@onSuccess
                val rootReply = resolveRoutedCommentRootReply(
                    loadedReplies = emptyList(),
                    remoteData = data,
                    rootReplyId = rootId
                ) ?: _subReplyState.value.rootReply
                if (rootReply == null) {
                    _subReplyState.value = _subReplyState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "评论可能已被删除或不可见"
                    )
                    return@onSuccess
                }

                val newItems = data.replies.orEmpty()
                val currentItems = _subReplyState.value.items
                val updatedItems = if (page == 1) {
                    newItems
                } else {
                    (currentItems + newItems).distinctBy { it.rpid }
                }

                val remoteTotalCount = resolveSubReplyRemoteTotalCount(
                    data = data,
                    rootReply = rootReply
                )
                val totalCount = resolveSubReplyLoadedTotalCount(
                    rootReply = rootReply,
                    loadedReplyCount = updatedItems.size,
                    remoteReplyCount = remoteTotalCount,
                    previousTotalCount = _subReplyState.value.totalCount
                )
                val isEnd = isSortedSubReplyPageEnd(data.cursor.isEnd, data.grpcNextOffset)

                _subReplyState.value = _subReplyState.value.copy(
                    visible = true,
                    rootReply = rootReply,
                    items = updatedItems.toImmutableList(),
                    baseItems = updatedItems.toImmutableList(),
                    totalCount = totalCount,
                    isLoading = false,
                    isRefreshing = false,
                    page = page,
                    basePage = page,
                    isEnd = isEnd,
                    baseIsEnd = isEnd,
                    grpcNextOffset = data.grpcNextOffset,
                    baseGrpcNextOffset = data.grpcNextOffset,
                    error = null
                )
            }.onFailure { error ->
                if (currentOid != oid || currentRootId != rootId || currentType != type) return@onFailure
                _subReplyState.value = _subReplyState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = error.message ?: "回复加载失败"
                )
            }
        }
    }

    fun openConversation(reply: ReplyItem) {
        _subReplyState.update { it.copy(conversationAnchor = reply) }
    }

    fun closeConversation() {
        _subReplyState.update { it.copy(conversationAnchor = null) }
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
        if (_isSending.value) return
        val rootReply = _subReplyState.value.rootReply ?: return
        val replying = _replyingTo.value ?: rootReply
        val parentId = if (replying.rpid == rootReply.rpid) 0L else replying.rpid
        val oid = currentOid
        val type = currentType
        val sendSession = threadSession
        val sendDraftSession = _composerDrafts.value.sessionId
        val sendDraftReplyRpid = replying.rpid
        val sentDraft = _composerDrafts.value.drafts[commentComposerDraftKey(sendDraftReplyRpid)]
        _isSending.value = true

        viewModelScope.launch {
            // Bind the result to the thread that opened this composer, not the current destination.
            CommentRepository.addCommentForSubject(
                oid = oid,
                type = type,
                message = message,
                root = rootReply.rpid,
                parent = parentId,
                syncToDynamic = syncToDynamic
            ).onSuccess { newReply ->
                if (currentOid == oid && currentType == type && currentRootId == rootReply.rpid &&
                    threadSession == sendSession
                ) {
                    _isSending.value = false
                }
                if (currentOid != oid || currentType != type || !shouldApplySentSubReplyResult(
                        rootReply.rpid, sendSession, threadSession, _subReplyState.value
                    )
                ) return@onSuccess
                _composerDrafts.update {
                    it.complete(sendDraftSession, sendDraftReplyRpid, sentDraft)
                }
                if (_composerDrafts.value.sessionId == sendDraftSession) {
                    hideReplyInput()
                }
                if (newReply != null) {
                    _subReplyState.update {
                        it.withSentSubReply(
                            resolveSentSubReply(newReply, rootReply.rpid, parentId, replying),
                            conversationItemsAreFiltered = false
                        )
                    }
                } else {
                    loadSubReplies(page = 1, paginationOffset = null)
                }
                if (_composerDrafts.value.sessionId == sendDraftSession) {
                    onSuccess()
                }
            }.onFailure {
                if (currentOid == oid && currentType == type && currentRootId == rootReply.rpid &&
                    threadSession == sendSession
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
        viewModelScope.launch {
            CommentRepository.deleteCommentForSubject(oid, type, rpid).onSuccess {
                val currentItems = _subReplyState.value.items
                _subReplyState.value = _subReplyState.value.copy(
                    items = currentItems.filterNot { it.rpid == rpid }.toImmutableList(),
                    totalCount = (_subReplyState.value.totalCount - 1).coerceAtLeast(0)
                )
            }
        }
    }

    fun startDissolve(rpid: Long) {
        _subReplyState.update { it.copy(dissolvingIds = (it.dissolvingIds + rpid).toPersistentSet()) }
    }
}
