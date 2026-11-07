package com.android.purebilibili.feature.video.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.paging.LoadState
import androidx.paging.PagingConfig
import com.android.purebilibili.core.paging.ForwardPagingSession
import com.android.purebilibili.feature.comment.CommentPagingSession
import com.android.purebilibili.feature.comment.CommentPageRequest
import com.android.purebilibili.feature.comment.SubReplyPageChunk
import com.android.purebilibili.feature.comment.subReplyPagingSource
import com.android.purebilibili.feature.comment.SentCommentReplyStore
import com.android.purebilibili.feature.comment.SentCommentReplyStores
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyPage
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplyVoteCard
import com.android.purebilibili.data.repository.CommentGrpcRepository
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.CommentReadException
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.data.repository.shouldStartCommentFraudDetection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

internal const val VIDEO_COMMENT_TYPE = 1

// 评论排序模式：只保留最热和最新。
enum class CommentSortMode(val apiMode: Int, val label: String) {
    HOT(3, "最热"),
    NEWEST(2, "最新");

    companion object {
        fun fromApiMode(mode: Int): CommentSortMode = entries.find { it.apiMode == mode } ?: HOT
    }
}

private const val SUB_REPLY_PAGE_SIZE = 20

data class CommentSubjectKey(
    val oid: Long,
    val type: Int = VIDEO_COMMENT_TYPE
) {
    val isValid: Boolean get() = oid > 0L && type > 0
}

internal fun shouldApplyCommentSubjectResult(
    expectedSubject: CommentSubjectKey,
    currentSubject: CommentSubjectKey
): Boolean {
    return expectedSubject.isValid && expectedSubject == currentSubject
}

internal fun shouldApplySubReplyResult(
    expectedSubject: CommentSubjectKey,
    expectedRootId: Long,
    currentSubject: CommentSubjectKey,
    activeRootId: Long?,
    conversationActive: Boolean
): Boolean {
    return shouldApplyCommentSubjectResult(expectedSubject, currentSubject) &&
        expectedRootId > 0L &&
        activeRootId == expectedRootId &&
        !conversationActive
}

internal fun shouldApplyConversationReplyResult(
    expectedSubject: CommentSubjectKey,
    expectedRootId: Long,
    expectedDialogId: Long,
    currentSubject: CommentSubjectKey,
    activeRootId: Long?,
    activeDialogId: Long?
): Boolean {
    return shouldApplyCommentSubjectResult(expectedSubject, currentSubject) &&
        expectedRootId > 0L &&
        expectedDialogId > 0L &&
        activeRootId == expectedRootId &&
        activeDialogId == expectedDialogId
}

// 评论状态
data class CommentUiState(
    val replies: ImmutableList<ReplyItem> = persistentListOf(),
    val voteCard: ReplyVoteCard? = null,
    val pageControl: com.android.purebilibili.data.model.response.ReplyPageControl? = null,
    val isRepliesLoading: Boolean = false,
    val isRepliesRefreshing: Boolean = false,
    val replyCount: Int = 0,
    // Null means unknown; a known zero is valid and must not fall back to stale detail metadata.
    val replyCountSubject: CommentSubjectKey? = null,
    val replyCountIsAuthoritative: Boolean = false,
    val repliesError: String? = null,
    val commentReadBlocked: Boolean = false,
    val isRepliesEnd: Boolean = false,
    val nextPage: Int = 1,
    // 排序状态
    val sortMode: CommentSortMode = CommentSortMode.HOT,
    val upMid: Long = 0,  // UP 主 mid，用于标识评论身份
    // [新增] 评论交互状态
    val isSending: Boolean = false,
    val sendError: String? = null,
    val replyTarget: ReplyItem? = null,  // 回复目标评论（为空则是一级评论）
    val composerDrafts: CommentComposerDraftState = CommentComposerDraftState(),
    val likedComments: ImmutableSet<Long> = persistentSetOf(),  // 已点赞的评论 rpid 集合
    val hatedComments: ImmutableSet<Long> = persistentSetOf(),   // 已点踩的评论 rpid 集合
    // [新增] 删除与动画状态
    val dissolvingIds: ImmutableSet<Long> = persistentSetOf(), // 正在播放消散动画的评论 ID
    // [新增] 当前登录用户 Mid (直接暴露以便 UI 判断是否显示删除按钮)
    val currentMid: Long = 0,
    // [新增] 评论输入控制
    val rootInputHint: String = "进来唠会嗑呗~",
    val childInputHint: String = "回复一下吧~",
    val canInputComment: Boolean = true,
    val showUpFlag: Boolean = false,
    val pinnedReplyIds: ImmutableSet<Long> = persistentSetOf(),
    val grpcNextOffset: String? = null,
    val mainListContinuation: CommentMainListContinuation? = null,
    // [新增] 评论反诈检测状态
    val isDetectingFraud: Boolean = false,
    val fraudDetectResult: CommentFraudStatus? = null,
    val fraudDetectRpid: Long = 0  // 被检测的评论 rpid
)

// 二级评论状态 (从 VideoPlaybackViewModel 移过来)
data class SubReplyUiState(
    val sortMode: SubReplySortMode = SubReplySortMode.TIME,
    val visible: Boolean = false,
    val rootReply: ReplyItem? = null,
    val items: ImmutableList<ReplyItem> = persistentListOf(),
    val baseItems: ImmutableList<ReplyItem> = persistentListOf(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val page: Int = 1,
    val isRefreshing: Boolean = false,
    val basePage: Int = 1,
    val isEnd: Boolean = false,
    val isTargetOnly: Boolean = false,
    val baseIsEnd: Boolean = false,
    val error: String? = null,
    val upMid: Long = 0,
    val grpcNextOffset: String? = null,
    val baseGrpcNextOffset: String? = null,
    val retryRequest: SubReplyPageRequest? = null,
    val conversationAnchor: ReplyItem? = null,
    val targetReplyId: Long = 0,
    // [新增] 消散动画状态
    val dissolvingIds: ImmutableSet<Long> = persistentSetOf()
) {
    val threadRootId: Long
        get() = rootReply?.rpid?.takeIf { it > 0L }
            ?: items.firstOrNull { it.root > 0L }?.root
            ?: baseItems.firstOrNull { it.root > 0L }?.root
            ?: 0L
}

// 楼中楼计数与末页判定规则已下沉 core-data；原包入口保留为同签名委托。
internal fun resolveSubReplyRemoteTotalCount(
    data: ReplyData,
    rootReply: ReplyItem? = null
): Int = com.android.purebilibili.data.repository.resolveSubReplyRemoteTotalCount(data, rootReply)

internal fun resolveSubReplyLoadedTotalCount(
    rootReply: ReplyItem?,
    loadedReplyCount: Int,
    remoteReplyCount: Int,
    previousTotalCount: Int = 0
): Int = com.android.purebilibili.data.repository.resolveSubReplyLoadedTotalCount(
    rootReply, loadedReplyCount, remoteReplyCount, previousTotalCount
)

internal fun resolveSubReplyPageEnd(
    cursorIsEnd: Boolean,
    fetchedReplyCount: Int,
    loadedReplyCount: Int,
    remoteReplyCount: Int,
    requestedPage: Int = 1,
    pageSize: Int = SUB_REPLY_PAGE_SIZE,
    restPage: ReplyPage = ReplyPage()
): Boolean = com.android.purebilibili.data.repository.resolveSubReplyPageEnd(
    cursorIsEnd, fetchedReplyCount, loadedReplyCount, remoteReplyCount, requestedPage, pageSize, restPage
)

internal fun resolveRoutedCommentRootReply(
    loadedReplies: List<ReplyItem>,
    remoteData: ReplyData?,
    rootReplyId: Long
): ReplyItem? {
    if (rootReplyId <= 0L) return null
    return loadedReplies.firstOrNull { it.rpid == rootReplyId }
        ?: remoteData?.root?.takeIf { it.rpid == rootReplyId }
}

internal fun shouldStartRoutedSubReplyOpen(
    rootReplyId: Long,
    currentAid: Long
): Boolean {
    return rootReplyId > 0L && currentAid > 0L
}

// [新增] 全量评论搜索加载状态
data class FullCommentSearchUiState(
    val isLoading: Boolean = false,
    val loadedCount: Int = 0,
    val totalCount: Int = 0,
    val isReady: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
)

class VideoCommentViewModel : ViewModel() {
    // 竖屏全屏面板可卸载重建，位置跟随评论 ViewModel 存活，不与详情/横屏共用滚动对象。
    internal val fullscreenCommentScrollMemory = CommentScrollMemory()
    private val _commentState = MutableStateFlow(CommentUiState())
    val commentState = _commentState.asStateFlow()

    private val pendingReactions = mutableSetOf<Pair<Long, Long>>()
    private val pendingDeletes = mutableSetOf<Pair<Long, Long>>()
    private var commentSessionId = 0L
    private val reactionOverrides = mutableMapOf<Long, Int>()

    private var commentLoadJob: Job? = null
    private var mainPagingSession: CommentPagingSession? = null
    private val removedMainReplyIds = mutableSetOf<Long>()
    private var sentReplies = SentCommentReplyStore()
    private var dialogPagingSession: CommentPagingSession? = null
    private var commentLoadRequestId = 0L

    // 进程内评论首页缓存（跨 ViewModel 实例）：重进同一个视频的评论区先秒出旧内容，
    // 再后台静默刷新，避免每次都从白屏骨架开始。
    private val commentCache = sharedVideoCommentCache
    private var subReplyLoadJob: Job? = null
    private var subReplyPagingSession: ForwardPagingSession<SubReplyPageRequest, SubReplyPageChunk>? = null
    private val localSubReplies = linkedMapOf<Long, ReplyItem>()
    private var remoteSubReplies: List<ReplyItem> = emptyList()
    private val removedSubReplyIds = mutableSetOf<Long>()
    private var subReplySession = 0L
    private var activeSubReplyRootId = 0L
    private val _subReplyState = MutableStateFlow(SubReplyUiState())
    val subReplyState = _subReplyState.asStateFlow()

    // [新增] 评论反诈检测事件流（one-shot event）
    private val _fraudEvent = MutableSharedFlow<CommentFraudStatus>(extraBufferCapacity = 1)
    val fraudEvent = _fraudEvent.asSharedFlow()

    private var currentAid: Long = 0
    private var currentSubject: CommentSubjectKey = CommentSubjectKey(0L)
    
    //  存储原始评论列表（未经筛选），用于筛选切换
    private var allReplies: List<ReplyItem> = emptyList()

    // [新增] 全量评论搜索：把整个评论区分页拉完后供搜索面板本地过滤
    private val _fullSearchState = MutableStateFlow(FullCommentSearchUiState())
    val fullSearchState = _fullSearchState.asStateFlow()
    private val _fullSearchReplies = MutableStateFlow<List<ReplyItem>>(emptyList())
    val fullSearchReplies = _fullSearchReplies.asStateFlow()
    private var fullSearchJob: Job? = null

    private var commentAccount = TokenManager.accountIdentity.value

    init {
        viewModelScope.launch {
            TokenManager.accountIdentity.collect { account ->
                if (account != commentAccount) {
                    commentAccount = account
                    val subject = currentSubject
                    val state = _commentState.value
                    clearForVideoChange()
                    if (subject.isValid) {
                        init(subject.oid, state.upMid, state.sortMode, commentType = subject.type)
                    }
                }
            }
        }
    }

    /**
     * 把当前评论首页写进进程内缓存，让「退出再进」「切走再回来」不必重新拉取。
     * 只有在评论主体（oid/type）已确定且列表非空时才写，避免把不完整状态缓存下来。
     */
    private fun cacheCurrentComments(
        replies: List<ReplyItem>,
        sortModeApiMode: Int = _commentState.value.sortMode.apiMode,
    ) {
        val subject = currentSubject
        if (!subject.isValid || replies.isEmpty()) return
        val state = _commentState.value
        commentCache.put(
            key = videoCommentCacheKey(
                oid = subject.oid,
                type = subject.type,
                sortModeApiMode = sortModeApiMode,
                viewerMid = state.currentMid,
            ),
            snapshot = VideoCommentCacheSnapshot(
                replies = replies,
                replyCount = state.replyCount,
                nextPage = state.nextPage,
                isEnd = state.isRepliesEnd,
                grpcNextOffset = state.grpcNextOffset,
                mainListContinuation = state.mainListContinuation,
                upMid = state.upMid,
                pinnedReplyIds = state.pinnedReplyIds,
                subject = subject,
                pageControl = state.pageControl,
                cachedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * 切换视频时立即废弃旧评论主体和未完成请求，等新页面真正打开评论区后再加载。
     */
    fun clearForVideoChange() {
        fullscreenCommentScrollMemory.clear()
        commentLoadRequestId++
        mainPagingSession = null
        commentLoadJob?.cancel()
        currentAid = 0L
        currentSubject = CommentSubjectKey(0L)
        allReplies = emptyList()
        commentSessionId++
        reactionOverrides.clear()
        _commentState.value = CommentUiState(
            currentMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L,
            composerDrafts = _commentState.value.composerDrafts.reset()
        )
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        localSubReplies.clear()
        remoteSubReplies = emptyList()
        removedSubReplyIds.clear()
        removedMainReplyIds.clear()
        sentReplies = SentCommentReplyStore()
        _subReplyState.value = SubReplyUiState()
        resetFullCommentSearch()
    }

    // 初始化/重置
    fun init(
        aid: Long,
        upMid: Long = 0,
        preferredSortMode: CommentSortMode = CommentSortMode.HOT,
        expectedReplyCount: Int? = null,
        commentType: Int = VIDEO_COMMENT_TYPE
    ) {
        android.util.Log.d("CommentVM", " init called with aid=$aid, upMid=$upMid, currentAid=$currentAid, type=$commentType")
        if (currentAid == aid && _commentState.value.upMid == upMid && currentSubject.type == commentType) {
            // [修复] 即使视频相同，也刷新 currentMid（防止登录状态变化后不更新）
            refreshCurrentMid()
            allReplies = sentReplies.merge(allReplies)
            _commentState.update { it.copy(replies = allReplies.toImmutableList()) }
            if (expectedReplyCount != null && !_commentState.value.replyCountIsAuthoritative &&
                (_commentState.value.replyCountSubject == null || expectedReplyCount > _commentState.value.replyCount)
            ) {
                _commentState.value = _commentState.value.copy(
                    replyCount = expectedReplyCount.coerceAtLeast(0),
                    replyCountSubject = currentSubject,
                )
            }
            return
        }
        val currentStateSubjectChanged = currentSubject != CommentSubjectKey(aid, commentType)
        commentLoadRequestId++
        mainPagingSession = null
        commentLoadJob?.cancel()
        currentAid = aid
        currentSubject = CommentSubjectKey(oid = aid, type = commentType)
        allReplies = emptyList()
        commentSessionId++
        reactionOverrides.clear()
        resetFullCommentSearch()
        // 获取当前登录用户 mid
        val myMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
        android.util.Log.d("CommentVM", " init: myMid=$myMid")
        _commentState.value = CommentUiState(
            sortMode = preferredSortMode,
            upMid = upMid,
            currentMid = myMid,
            replyCount = expectedReplyCount?.coerceAtLeast(0) ?: 0,
            replyCountSubject = currentSubject.takeIf { expectedReplyCount != null },
            replyTarget = if (currentStateSubjectChanged) null else _commentState.value.replyTarget,
            isSending = !currentStateSubjectChanged && _commentState.value.isSending,
            sendError = if (currentStateSubjectChanged) null else _commentState.value.sendError,
            composerDrafts = if (currentStateSubjectChanged) {
                _commentState.value.composerDrafts.reset()
            } else {
                _commentState.value.composerDrafts
            }
        )
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        localSubReplies.clear()
        remoteSubReplies = emptyList()
        removedSubReplyIds.clear()
        removedMainReplyIds.clear()
        sentReplies = SentCommentReplyStores.forSubject(aid, commentType)
        _subReplyState.value = SubReplyUiState()
        loadComments()
    }
    
    // [新增] 刷新当前登录用户 mid
    fun refreshCurrentMid() {
        val myMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
        val current = _commentState.value
        if (current.currentMid != myMid) {
            android.util.Log.d("CommentVM", " refreshCurrentMid: ${current.currentMid} -> $myMid")
            _commentState.value = current.copy(currentMid = myMid)
        }
    }
    
    // 切换排序模式
    fun setSortMode(mode: CommentSortMode) {
        val currentState = _commentState.value
        if (currentState.sortMode == mode) return
        fullscreenCommentScrollMemory.clear()
        
        android.util.Log.d("CommentVM", " setSortMode")
        
        commentLoadRequestId++
        mainPagingSession = null
        commentLoadJob?.cancel()
        allReplies = emptyList()
        _commentState.value = CommentUiState(
            sortMode = mode,
            upMid = currentState.upMid,
            currentMid = currentState.currentMid,
            replyCount = currentState.replyCount,
            replyCountSubject = currentState.replyCountSubject,
            replyCountIsAuthoritative = currentState.replyCountIsAuthoritative,
            isSending = currentState.isSending,
            sendError = currentState.sendError,
            replyTarget = currentState.replyTarget,
            composerDrafts = currentState.composerDrafts,
            commentReadBlocked = currentState.commentReadBlocked,
            canInputComment = currentState.canInputComment,
            rootInputHint = currentState.rootInputHint,
        )
        loadComments()
    }

    fun loadComments() = requestComments(refresh = false)

    fun refreshComments() = requestComments(refresh = true)

    private fun requestComments(refresh: Boolean, forceRefresh: Boolean = false) {
        val currentState = _commentState.value
        if (refresh) {
            if (!forceRefresh && (currentState.isRepliesRefreshing ||
                (currentState.isRepliesLoading && currentState.replies.isEmpty()))
            ) return
        } else if (currentState.isRepliesEnd || currentState.isRepliesLoading) return
        if (!refresh && mainPagingSession != null) {
            mainPagingSession?.loadMore()
            return
        }
        val requestSubject = currentSubject
        if (!requestSubject.isValid) return
        val requestId = ++commentLoadRequestId
        mainPagingSession = null
        commentLoadJob?.cancel()
        _commentState.value = currentState.copy(
            isRepliesLoading = true,
            isRepliesRefreshing = refresh,
            repliesError = null,
        )

        val pageToLoad = if (refresh) 1 else currentState.nextPage
        val cacheKey = videoCommentCacheKey(
            oid = requestSubject.oid,
            type = requestSubject.type,
            sortModeApiMode = currentState.sortMode.apiMode,
            viewerMid = currentState.currentMid,
        )
        // stale-while-revalidate：首页命中缓存就先把旧评论铺出来（列表立刻有内容、
        // 不再白屏），请求照常发出，回来后再整体覆盖。这里刻意保持 isRepliesLoading
        // 为 true，否则列表底部的自动翻页会在刷新途中把这次请求取消掉。
        val cached = if (pageToLoad == 1 && !refresh) commentCache.get(cacheKey) else null
        if (cached != null && shouldApplyCommentSubjectResult(requestSubject, currentSubject)) {
            allReplies = sentReplies.merge(cached.replies)
            _commentState.value = _commentState.value.copy(
                replies = allReplies.toImmutableList(),
                likedComments = reactionIds(emptySet(), allReplies.flatMap { listOf(it) + it.replies.orEmpty() }, 1),
                hatedComments = reactionIds(emptySet(), allReplies.flatMap { listOf(it) + it.replies.orEmpty() }, 2),
                pageControl = cached.pageControl,
                replyCount = maxOf(cached.replyCount, currentState.replyCount),
                replyCountSubject = requestSubject,
                isRepliesEnd = cached.isEnd,
                nextPage = cached.nextPage,
                grpcNextOffset = cached.grpcNextOffset,
                mainListContinuation = cached.mainListContinuation,
                pinnedReplyIds = cached.pinnedReplyIds.toImmutableSet(),
                upMid = if (cached.upMid > 0L) cached.upMid else currentState.upMid,
            )
        }
        var initialPageApplied = false
        lateinit var session: CommentPagingSession
        session = CommentPagingSession(
            initial = CommentPageRequest(pageToLoad, if (pageToLoad == 1) null else currentState.mainListContinuation),
            mainList = true,
            load = { request ->
                CommentRepository.getMobileCommentsForSubject(
                    oid = requestSubject.oid, type = requestSubject.type, page = request.page,
                    ps = VIDEO_COMMENT_PAGE_SIZE, mode = currentState.sortMode.apiMode,
                    continuation = request.continuation, fallbackOnMissingLocation = requestSubject.type == 1,
                    expectedCount = currentState.replyCount.takeIf { currentState.replyCountSubject == requestSubject } ?: 0,
                ).getOrThrow()
            },
            onPage = success@ { request, data ->
                if (mainPagingSession !== session || requestId != commentLoadRequestId ||
                    !shouldApplyCommentSubjectResult(requestSubject, currentSubject)) return@success
                val pageToLoad = request.page
                initialPageApplied = true
                val current = _commentState.value
                val newReplies = data.replies ?: emptyList()
                val previousRepliesSize = if (refresh) 0 else allReplies.size
                
                // 第一页时先合并置顶和热评
                val topReplies = if (pageToLoad == 1) data.collectTopReplies() else emptyList()
                val hotReplies = if (pageToLoad == 1) data.hots ?: emptyList() else emptyList()
                val pinnedReplyIds = if (pageToLoad == 1) {
                    topReplies.mapTo(mutableSetOf()) { it.rpid }
                } else {
                    current.pinnedReplyIds
                }
                
                // 合并到原始列表（置顶 -> 热评 -> 普通评论）
                val remoteReplies = if (pageToLoad == 1) {
                    (topReplies + hotReplies + newReplies).distinctBy { it.rpid }
                } else {
                    (allReplies + newReplies).distinctBy { it.rpid }
                }
                val combinedReplies = sentReplies.merge(remoteReplies)
                allReplies = combinedReplies
                
                // 统一获取评论总数和结束标志 (兼容 WBI 游客 all_count=0 场景)
                val pageResolution = resolveCommentPageResolution(
                    data = data,
                    pageToLoad = pageToLoad,
                    previousRepliesSize = previousRepliesSize,
                    combinedRepliesSize = combinedReplies.size,
                    newRepliesSize = newReplies.size,
                    fallbackCount = current.replyCount
                )
                val totalCount = pageResolution.totalCount
                // 手机入口已按所选接口解析末页；不因本页去重后没有新增而截断有效游标。
                val isEnd = data.cursor.isEnd
                
                android.util.Log.d(
                    "CommentVM",
                    " loadComments result: page=$pageToLoad, new=${newReplies.size}, hot=${hotReplies.size}, top=${topReplies.size}, total=${allReplies.size}, allCount=$totalCount, isEnd=$isEnd"
                )
                
                _commentState.value = current.copy(
                    replies = combinedReplies.toImmutableList(),
                    voteCard = if (pageToLoad == 1) data.voteCard else current.voteCard,
                    pageControl = if (pageToLoad == 1) data.control else current.pageControl,
                    likedComments = reactionIds(current.likedComments, combinedReplies.flatMap { listOf(it) + it.replies.orEmpty() }, 1),
                    hatedComments = reactionIds(current.hatedComments, combinedReplies.flatMap { listOf(it) + it.replies.orEmpty() }, 2),
                    replyCount = totalCount,
                    replyCountSubject = requestSubject,
                    replyCountIsAuthoritative = true,
                    isRepliesLoading = false,
                    isRepliesRefreshing = false,
                    repliesError = null,
                    isRepliesEnd = isEnd,
                    nextPage = pageToLoad + 1,
                    rootInputHint = data.control?.rootInputText?.takeIf { it.isNotBlank() }
                        ?: if (current.commentReadBlocked) CommentUiState().rootInputHint else current.rootInputHint,
                    childInputHint = data.control?.childInputText?.takeIf { it.isNotBlank() } ?: current.childInputHint,
                    canInputComment = data.control?.inputDisable?.not() ?: (current.commentReadBlocked || current.canInputComment),
                    commentReadBlocked = false,
                    showUpFlag = data.config?.showUpFlag ?: current.showUpFlag,
                    pinnedReplyIds = pinnedReplyIds.toImmutableSet(),
                    grpcNextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() },
                    mainListContinuation = data.mainListContinuation,
                )
                // 只缓存首页：后续页没有缓存时也能从首页继续翻，缓存整段会让内存迅速膨胀。
                if (pageToLoad == 1) {
                    cacheCurrentComments(combinedReplies)
                }
            },
            onState = { loading, end, failure ->
                if (mainPagingSession === session && requestId == commentLoadRequestId &&
                    shouldApplyCommentSubjectResult(requestSubject, currentSubject)) {
                    val blocked = (failure as? CommentReadException)?.commentInputBlocked == true
                    _commentState.update { it.copy(
                        isRepliesLoading = loading, isRepliesRefreshing = refresh && !initialPageApplied && loading,
                        isRepliesEnd = end,
                        repliesError = if (failure != null && (blocked || cached == null || initialPageApplied)) failure.message ?: "加载评论失败" else null,
                        commentReadBlocked = blocked || it.commentReadBlocked,
                        canInputComment = if (blocked) false else it.canInputComment,
                        rootInputHint = if (blocked) failure?.message ?: "评论区暂不可用" else it.rootInputHint,
                    ) }
                }
            }
        )
        mainPagingSession = session
        commentLoadJob = viewModelScope.launch { session.run() }
    }

    // --- [新增] 全量评论加载（供评论区搜索/充电评论过滤） ---

    fun loadAllCommentsForSearch() {
        val state = _fullSearchState.value
        if (state.isLoading || state.isReady && state.error == null) return
        val subject = currentSubject
        if (!subject.isValid) return
        fullSearchJob?.cancel()
        _fullSearchState.value = FullCommentSearchUiState(isLoading = true)
        fullSearchJob = viewModelScope.launch {
            val result = loadCommentSearchReplies(
                loadMain = { page, continuation ->
                    CommentRepository.getMobileCommentsForSubject(
                        oid = subject.oid, type = subject.type, page = page,
                        ps = VIDEO_COMMENT_PAGE_SIZE, mode = CommentGrpcRepository.MODE_TIME,
                        continuation = continuation,
                    )
                },
                loadThread = { rootId, offset ->
                    CommentRepository.getMobileSortedSubCommentsForSubject(
                        oid = subject.oid, type = subject.type, rootId = rootId,
                        mode = CommentGrpcRepository.MODE_TIME, paginationOffset = offset,
                        supplementLocations = false,
                    )
                },
                onProgress = { progress ->
                    if (currentSubject == subject) {
                        _fullSearchReplies.value = progress.replies
                        _fullSearchState.value = FullCommentSearchUiState(
                            isLoading = true, loadedCount = progress.loadedCount,
                            totalCount = progress.totalCount,
                        )
                    }
                },
            )
            if (currentSubject != subject) return@launch
            _fullSearchReplies.value = result.replies
            _fullSearchState.value = FullCommentSearchUiState(
                isReady = true,
                isComplete = result.isComplete,
                loadedCount = result.loadedCount,
                totalCount = result.totalCount,
                error = result.error,
            )
        }
    }

    fun resetFullCommentSearch() {
        fullSearchJob?.cancel()
        _fullSearchState.value = FullCommentSearchUiState()
        _fullSearchReplies.value = emptyList()
    }

    // --- 二级评论逻辑 ---

    fun openSubReply(rootReply: ReplyItem, targetReplyId: Long = 0L) {
        val requestSubject = currentSubject
        if (sentReplies.isRootRemoved(rootReply.rpid)) return
        com.android.purebilibili.core.util.Logger.d("VideoInputTrace") {
            "subreply_click subjectValid=${requestSubject.isValid} visible=${_subReplyState.value.visible}"
        }
        if (!requestSubject.isValid || commentAccount != TokenManager.accountIdentity.value) return
        subReplySession++
        activeSubReplyRootId = rootReply.rpid
        localSubReplies.clear()
        remoteSubReplies = emptyList()
        sentReplies.repliesFor(rootReply.rpid).forEach { localSubReplies[it.rpid] = it }
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.value = SubReplyUiState(
            visible = true,
            rootReply = rootReply,
            targetReplyId = targetReplyId.takeIf { it != rootReply.rpid } ?: 0L,
            items = localSubReplies.values.toImmutableList(),
            baseItems = localSubReplies.values.toImmutableList(),
            totalCount = resolveSubReplyLoadedTotalCount(
                rootReply = rootReply,
                loadedReplyCount = rootReply.replies.orEmpty().size,
                remoteReplyCount = 0
            ),
            isLoading = true,
            page = 1,
            upMid = _commentState.value.upMid  // 保留 UP 主身份标识，供回复详情展示
        )
        loadSubReplies(
            subject = requestSubject,
            rootId = rootReply.rpid,
            page = 1,
            paginationOffset = null
        )
    }

    fun openSubReplyFromRoute(rootReplyId: Long, targetReplyId: Long = 0L): Boolean {
        if (!shouldStartRoutedSubReplyOpen(rootReplyId.takeIf { it > 0L } ?: targetReplyId, currentAid)) return false
        if (commentAccount != TokenManager.accountIdentity.value) return false
        if (targetReplyId <= 0L && sentReplies.isRootRemoved(rootReplyId)) return false

        (resolveRoutedCommentRootReply(
            loadedReplies = allReplies.ifEmpty { _commentState.value.replies },
            remoteData = null,
            rootReplyId = rootReplyId
        ) ?: sentReplies.rootFor(rootReplyId))?.let { rootReply ->
            openSubReply(rootReply, targetReplyId)
            return true
        }

        subReplySession++
        activeSubReplyRootId = rootReplyId
        localSubReplies.clear()
        remoteSubReplies = emptyList()
        sentReplies.repliesFor(rootReplyId).forEach { localSubReplies[it.rpid] = it }
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.value = SubReplyUiState(
            visible = true,
            items = localSubReplies.values.toImmutableList(),
            baseItems = localSubReplies.values.toImmutableList(),
            upMid = _commentState.value.upMid,
            isLoading = true,
            targetReplyId = targetReplyId
        )
        loadSubReplies(currentSubject, rootReplyId, page = 1, paginationOffset = null)
        return true
    }

    fun closeSubReply() {
        subReplySession++
        activeSubReplyRootId = 0L
        localSubReplies.clear()
        remoteSubReplies = emptyList()
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.update { current ->
            current.copy(
                visible = false,
                conversationAnchor = null,
                isLoading = false,
                isRefreshing = false,
                error = null
            )
        }
    }

    private fun applySentMainListReply(reply: ReplyItem) {
        val isNew = sentReplies.record(reply, allReplies)
        allReplies = sentReplies.merge(allReplies)
        _commentState.update {
            it.copy(replies = allReplies.toImmutableList(), replyCount = it.replyCount + if (isNew) 1 else 0)
        }
    }

    fun onExternalCommentSent(
        aid: Long,
        newReply: ReplyItem?,
        fraudDetectionEnabled: Boolean = true,
        senderAccount: AccountSessionIdentity = TokenManager.accountIdentity.value
    ) {
        if (currentAid != aid || aid <= 0L || senderAccount != TokenManager.accountIdentity.value) return
        if (newReply != null && (sentReplies.isReplyRemoved(newReply.rpid) ||
                sentReplies.isRootRemoved(newReply.rpid) || sentReplies.isRootRemoved(newReply.root))) return

        val subState = _subReplyState.value
        val activeRootId = subState.threadRootId

        if (newReply == null) {
            if (subState.visible && activeRootId > 0L) {
                _subReplyState.value = subState.copy(
                    items = if (subState.isTargetOnly) subState.items else persistentListOf(),
                    page = 1,
                    isEnd = false,
                    isLoading = true,
                    error = null
                )
                loadSubReplies(
                    subject = currentSubject,
                    rootId = activeRootId,
                    page = 1,
                    paginationOffset = null
                )
            } else {
                requestComments(refresh = true, forceRefresh = true)
            }
            return
        }

        if (shouldStartCommentFraudDetection(fraudDetectionEnabled, newReply.rpid)) {
            val sentAtSeconds = newReply.ctime
                .takeIf { it > 0L }
                ?: (System.currentTimeMillis() / 1000L)
            launchFraudDetection(
                aid = aid,
                rpid = newReply.rpid,
                rootId = newReply.root,
                message = newReply.content.message,
                hasPictures = !newReply.content.pictures.isNullOrEmpty(),
                sentAtSeconds = sentAtSeconds
            )
        }

        applySentMainListReply(newReply)

        if (subState.visible && activeRootId > 0L && newReply.root == activeRootId) {
            localSubReplies[newReply.rpid] = newReply
            _subReplyState.value = subState.withSentSubReply(newReply)
        }
    }

    fun setSubReplySortMode(mode: SubReplySortMode) {
        val state = _subReplyState.value
        val rootId = state.rootReply?.rpid ?: activeSubReplyRootId
        if (!state.visible || state.isTargetOnly || state.conversationAnchor != null || state.sortMode == mode || rootId <= 0L) return
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        val localItems = localSubReplies.values.toImmutableList()
        _subReplyState.update { it.resetForSort(mode).copy(items = localItems, baseItems = localItems) }
        loadSubReplies(currentSubject, rootId, page = 1, paginationOffset = null)
    }

    fun refreshSubReplies() {
        val state = _subReplyState.value
        val rootId = state.rootReply?.rpid ?: activeSubReplyRootId
        if (!state.visible || state.isRefreshing ||
            !currentSubject.isValid || rootId <= 0L
        ) return
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.update { current ->
            current.copy(
                isLoading = true,
                isRefreshing = true,
                error = null,
                targetReplyId = if (current.isTargetOnly) current.targetReplyId else 0L,
            )
        }
        val anchor = state.conversationAnchor
        if (anchor != null) {
            loadConversationReplies(anchor, page = 1, paginationOffset = null)
        } else {
            loadSubReplies(currentSubject, rootId, page = 1, paginationOffset = null)
        }
    }

    fun loadMoreSubReplies() {
        val state = _subReplyState.value
        if (!state.visible || state.isLoading ||
            (state.isTargetOnly && state.conversationAnchor == null) ||
            (state.isEnd && state.error == null)) return
        if (state.conversationAnchor != null && dialogPagingSession != null) {
            dialogPagingSession?.loadMore()
            return
        }
        if (state.conversationAnchor == null && subReplyPagingSession != null) {
            subReplyPagingSession?.appendOrRetry()
            return
        }
        if (state.rootReply == null) return
        val request = resolveSubReplyNextRequest(state)
        _subReplyState.value = state.copy(isLoading = true, error = null)
        val anchor = state.conversationAnchor
        if (anchor != null) {
            loadConversationReplies(anchor, request.page, paginationOffset = request.offset)
        } else {
            loadSubReplies(
                subject = currentSubject,
                rootId = state.rootReply.rpid,
                page = request.page,
                paginationOffset = request.offset
            )
        }
    }

    fun openSubReplyConversation(anchorReply: ReplyItem) {
        val current = _subReplyState.value
        if (!current.visible || anchorReply.root <= 0L || anchorReply.dialog <= 0L ||
            anchorReply.parent == anchorReply.root || anchorReply.root != current.threadRootId ||
            anchorReply.root != activeSubReplyRootId) return
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        val baseItems = if (current.conversationAnchor == null) current.items else current.baseItems
        val localConversationItems = resolveLocalConversationItems(
            anchorReply = anchorReply,
            subReplies = baseItems
        )
        _subReplyState.value = current.copy(
            items = localConversationItems.toImmutableList(),
            baseItems = baseItems.toImmutableList(),
            basePage = current.page,
            baseIsEnd = current.isEnd,
            baseGrpcNextOffset = current.grpcNextOffset,
            conversationAnchor = anchorReply,
            page = 1,
            isEnd = false,
            isLoading = true,
            isRefreshing = false,
            retryRequest = null,
            error = null,
            grpcNextOffset = null
        )
        loadConversationReplies(anchorReply, page = 1)
    }

    fun closeSubReplyConversation() {
        val current = _subReplyState.value
        if (current.conversationAnchor == null) return
        dialogPagingSession = null
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        _subReplyState.value = current.copy(
            items = current.baseItems,
            page = current.basePage,
            isEnd = current.baseIsEnd,
            grpcNextOffset = current.baseGrpcNextOffset,
            conversationAnchor = null,
            isLoading = false,
            isRefreshing = false,
            retryRequest = null,
            error = null
        )
    }

    private fun loadSubReplies(
        subject: CommentSubjectKey,
        rootId: Long,
        page: Int,
        paginationOffset: String? = _subReplyState.value.grpcNextOffset
    ) {
        if (!subject.isValid || (rootId <= 0L && _subReplyState.value.targetReplyId <= 0L)) return
        if (page > 1 && subReplyPagingSession != null) {
            subReplyPagingSession?.appendOrRetry()
            return
        }
        val sortMode = _subReplyState.value.sortMode
        val threadSession = subReplySession
        val account = commentAccount
        val store = sentReplies
        val seedRoot = _subReplyState.value.rootReply
        val targetReplyId = sentReplies.remoteTargetReplyId(
            rootId, _subReplyState.value.targetReplyId
        ).takeIf { page == 1 } ?: 0L
        subReplyLoadJob?.cancel()
        val initialRemoteItems = if (page > 1) remoteSubReplies else emptyList()
        var appliedPages = 0
        var threadRootId = rootId
        lateinit var session: ForwardPagingSession<SubReplyPageRequest, SubReplyPageChunk>
        session = ForwardPagingSession(
            config = PagingConfig(pageSize = 1, initialLoadSize = 1, prefetchDistance = 1, enablePlaceholders = false),
            sourceFactory = {
                var resolvedRoot = seedRoot
                subReplyPagingSource(initialRequest = SubReplyPageRequest(page, paginationOffset)) { request ->
                    val data = CommentRepository.getMobileSortedSubCommentsForSubject(
                        oid = subject.oid, type = subject.type, rootId = threadRootId, mode = sortMode.apiMode,
                        targetReplyId = targetReplyId.takeIf { request.page == 1 } ?: 0L,
                        paginationOffset = request.offset
                    ).getOrThrow()
                    if (data.resolvedRootId > 0L) threadRootId = data.resolvedRootId
                    check(data.root?.let { it.rpid == threadRootId } != false) { "回复楼层不匹配，请重试" }
                    resolvedRoot = resolveRoutedCommentRootReply(emptyList(), data, threadRootId) ?: resolvedRoot
                    check(resolvedRoot != null || data.isTargetOnly) { "回复可能已被删除或不可见" }
                    data
                }
            },
            onChanged = changed@ { pages, states ->
                val current = _subReplyState.value
                if (subReplyPagingSession === session && states != null &&
                    subReplySession == threadSession && TokenManager.accountIdentity.value == account &&
                    current.visible && shouldApplyCommentSubjectResult(subject, currentSubject) &&
                    (activeSubReplyRootId == rootId || activeSubReplyRootId == threadRootId) &&
                    current.conversationAnchor == null
                ) {
                    val ready = states.refresh is LoadState.NotLoading && pages.size > appliedPages
                    val failure = (states.refresh as? LoadState.Error)?.error ?: (states.append as? LoadState.Error)?.error
                    var next = current
                    if (ready) {
                        val last = pages.last()
                        activeSubReplyRootId = threadRootId
                        val root = resolveRoutedCommentRootReply(emptyList(), last.data, threadRootId)
                            ?: current.rootReply?.takeIf { it.rpid == threadRootId } ?: seedRoot?.takeIf { it.rpid == threadRootId }
                        if (store.isRootRemoved(threadRootId)) {
                            closeSubReply()
                            return@changed
                        }
                        if (threadRootId != rootId) {
                            localSubReplies.clear()
                            store.repliesFor(threadRootId).forEach { localSubReplies[it.rpid] = it }
                        }
                        remoteSubReplies = initialRemoteItems + pages.flatMap { it.data.replies.orEmpty() }
                        val items = mergeSentSubReplies(remoteSubReplies, localSubReplies.values)
                            .filterNot { it.rpid in removedSubReplyIds || sentReplies.isReplyRemoved(it.rpid) }
                        val mergedRoot = root?.let { store.merge(listOf(it)).firstOrNull { reply -> reply.rpid == threadRootId } }
                        next = current.copy(
                            rootReply = mergedRoot,
                            targetReplyId = pages.first().data.resolvedTargetReplyId.takeIf { it > 0L }
                                ?: current.targetReplyId.takeIf { it != threadRootId } ?: 0L,
                            isTargetOnly = last.data.isTargetOnly,
                            items = items.toImmutableList(), baseItems = items.toImmutableList(),
                            totalCount = if (last.data.isTargetOnly) 0 else resolveSubReplyLoadedTotalCount(
                                rootReply = mergedRoot, loadedReplyCount = items.size,
                                remoteReplyCount = resolveSubReplyRemoteTotalCount(last.data, mergedRoot),
                                previousTotalCount = if (current.isTargetOnly || current.threadRootId != threadRootId) 0 else current.totalCount
                            ),
                            page = last.request.page, basePage = last.request.page,
                            grpcNextOffset = last.data.grpcNextOffset, baseGrpcNextOffset = last.data.grpcNextOffset,
                            retryRequest = null
                        )
                        _commentState.update { comments -> comments.copy(
                            likedComments = reactionIds(comments.likedComments, listOfNotNull(mergedRoot) + items, 1),
                            hatedComments = reactionIds(comments.hatedComments, listOfNotNull(mergedRoot) + items, 2)
                        ) }
                        appliedPages = pages.size
                    }
                    _subReplyState.value = next.copy(
                        isLoading = states.refresh is LoadState.Loading || states.append is LoadState.Loading,
                        isRefreshing = current.isRefreshing && states.refresh is LoadState.Loading,
                        isEnd = states.append.endOfPaginationReached, baseIsEnd = states.append.endOfPaginationReached,
                        error = failure?.let { it.message ?: "回复加载失败" }
                    )
                }
            }
        )
        subReplyPagingSession = session
        subReplyLoadJob = viewModelScope.launch { session.run() }
    }

    private fun loadConversationReplies(
        anchorReply: ReplyItem,
        page: Int,
        paginationOffset: String? = _subReplyState.value.grpcNextOffset,
    ) {
        if (page > 1 && dialogPagingSession != null) { dialogPagingSession?.loadMore(); return }
        val rootId = anchorReply.root
        val dialogId = anchorReply.dialog
        if (rootId <= 0L || dialogId <= 0L || anchorReply.parent == rootId ||
            rootId != activeSubReplyRootId || rootId != _subReplyState.value.threadRootId) return
        val subject = currentSubject
        if (!subject.isValid) return
        val threadSession = subReplySession
        val account = commentAccount
        subReplyPagingSession = null
        subReplyLoadJob?.cancel()
        var remoteItems = if (page > 1) {
            _subReplyState.value.items.filterNot { it.rpid in localSubReplies }
        } else emptyList()
        lateinit var session: CommentPagingSession
        session = CommentPagingSession(
            initial = CommentPageRequest(page = page, offset = paginationOffset), mainList = false,
            load = { request -> CommentRepository.getDialogCommentsForSubject(
                oid = subject.oid, type = subject.type, rootId = rootId, dialogId = dialogId,
                page = request.page, paginationOffset = request.offset
            ).getOrThrow() },
            onPage = success@ { request, data ->
                if (dialogPagingSession !== session || subReplySession != threadSession ||
                    TokenManager.accountIdentity.value != account
                ) return@success
                val page = request.page
                val current = _subReplyState.value
                if (!shouldApplyConversationReplyResult(
                        expectedSubject = subject,
                        expectedRootId = rootId,
                        expectedDialogId = dialogId,
                        currentSubject = currentSubject,
                        activeRootId = current.threadRootId,
                        activeDialogId = current.conversationAnchor?.let(::resolveConversationDialogId)
                    )
                ) {
                    return@success
                }
                val newItems = data.replies.orEmpty()
                val nextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() }
                remoteItems = if (page == 1) newItems else remoteItems + newItems
                val updatedItems = mergeSentSubReplies(
                    remoteItems,
                    localSubReplies.values.filter {
                        it.dialog == dialogId || it.parent == anchorReply.rpid || it.rpid == anchorReply.rpid
                    }
                ).filterNot { it.rpid in removedSubReplyIds || sentReplies.isReplyRemoved(it.rpid) }
                _subReplyState.value = current.copy(
                    items = updatedItems.ifEmpty {
                        resolveLocalConversationItems(anchorReply, current.baseItems)
                    }.toImmutableList(),
                    isLoading = false,
                    isRefreshing = false,
                    page = page,
                    isEnd = data.cursor.isEnd,
                    error = null,
                    retryRequest = null,
                    grpcNextOffset = nextOffset
                )
            },
            onState = { loading, end, failure ->
                if (dialogPagingSession === session && subReplySession == threadSession &&
                    TokenManager.accountIdentity.value == account && shouldApplyConversationReplyResult(
                        expectedSubject = subject, expectedRootId = rootId, expectedDialogId = dialogId,
                        currentSubject = currentSubject, activeRootId = _subReplyState.value.threadRootId,
                        activeDialogId = _subReplyState.value.conversationAnchor?.let(::resolveConversationDialogId)
                    )) {
                    _subReplyState.update { it.copy(
                        isLoading = loading, isRefreshing = it.isRefreshing && loading, isEnd = end,
                        error = failure?.let { error -> error.message ?: "对话加载失败" }
                    ) }
                }
            }
        )
        dialogPagingSession = session
        subReplyLoadJob = viewModelScope.launch { session.run() }
    }

    private fun resolveConversationDialogId(anchorReply: ReplyItem): Long = anchorReply.dialog

    private fun resolveLocalConversationItems(
        anchorReply: ReplyItem,
        subReplies: List<ReplyItem>
    ): List<ReplyItem> {
        val dialogId = anchorReply.dialog
        val parentId = anchorReply.parent
        val anchorId = anchorReply.rpid
        return subReplies.filter { candidate ->
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
        }.ifEmpty { listOf(anchorReply) }.distinctBy { it.rpid }
    }
    
    // --- [新增] 评论交互逻辑 ---
    
    fun sendComment(
        message: String,
        imageUris: List<Uri> = emptyList(),
        syncToDynamic: Boolean = false,
        fraudDetectionEnabled: Boolean = true
    ) {
        if (message.isBlank() && imageUris.isEmpty()) return
        val currentState = _commentState.value
        if (currentState.isSending) return
        if (!currentState.canInputComment) {
            _commentState.update { it.copy(sendError = it.rootInputHint.ifBlank { "当前评论区暂不可发言" }) }
            return
        }
        
        _commentState.value = currentState.copy(isSending = true, sendError = null)

        // Keep the request bound to the episode that opened the composer. A quick episode
        // switch must not let a late response update the newly selected comment thread.
        val sendSubject = currentSubject
        val sendAccount = TokenManager.accountIdentity.value
        val sendCurrentAid = currentAid
        val sendReplyTarget = currentState.replyTarget
        val sendSubReplyState = _subReplyState.value
        val sendSubReplySession = subReplySession
        val sendDraftSession = currentState.composerDrafts.sessionId
        val sendDraftReplyRpid = sendReplyTarget?.rpid
        val sentDraft = currentState.composerDrafts.drafts[commentComposerDraftKey(sendDraftReplyRpid)]
        
        viewModelScope.launch {
            // [修复] 正确计算 root ID
            // 如果是在二级评论页回复，root 为当前二级评论页的根评论 ID
            // 如果是一级评论页回复某评论，root 为该评论 ID
            // 如果是直接发表评论，root 为 0
            val isSubReplyContext = sendSubReplyState.visible && sendSubReplyState.threadRootId > 0L
            
            val root = if (isSubReplyContext) {
                sendSubReplyState.threadRootId
            } else {
                sendReplyTarget?.let { it.root.takeIf { id -> id > 0L } ?: it.rpid } ?: 0L
            }
            // parent 总是回复目标的 ID (如果没有回复目标，则是 0)
            val parent = sendReplyTarget?.rpid ?: 0
            
            val picturesResult = uploadCommentPictures(imageUris)
            val pictures = picturesResult.getOrElse { error ->
                if (shouldApplyCommentSubjectResult(sendSubject, currentSubject) &&
                    sendAccount == TokenManager.accountIdentity.value) {
                    _commentState.value = _commentState.value.copy(
                        isSending = false,
                        sendError = error.message ?: "图片上传失败"
                    )
                }
                return@launch
            }
            val result = CommentRepository.addCommentForSubject(
                oid = sendSubject.oid,
                type = sendSubject.type,
                message = message,
                root = root,
                parent = parent,
                pictures = pictures,
                syncToDynamic = syncToDynamic
            )
            
            result.onSuccess { newReply ->
                if (!shouldApplyCommentSubjectResult(sendSubject, currentSubject) ||
                    sendAccount != TokenManager.accountIdentity.value) return@onSuccess
                android.util.Log.d("CommentVM", " sendComment success: newReply=${newReply?.rpid}, root=$root, parent=$parent")
                val latest = _commentState.value
                val current = latest.copy(
                    composerDrafts = latest.composerDrafts.complete(
                        sendDraftSession, sendDraftReplyRpid, sentDraft
                    )
                )
                val completedReplyTarget = if (current.composerDrafts.sessionId == sendDraftSession) {
                    null
                } else {
                    current.replyTarget
                }

                // [新增] 启动评论反诈检测（后台协程，不阻塞 UI）
                val rpidToCheck = newReply?.rpid ?: 0L
                if (rpidToCheck > 0L) {
                    // 成功发布的元数据由宿主回调统一保存，这里只发起可见性检测。
                    val sentAtSeconds = newReply?.ctime?.takeIf { it > 0L } ?: (System.currentTimeMillis() / 1000L)
                    if (sendSubject.type == 1) launchFraudDetection(
                        aid = sendCurrentAid,
                        rpid = rpidToCheck,
                        rootId = root,
                        message = message,
                        sentAtSeconds = sentAtSeconds
                    )
                }
                
                val resolvedReply = newReply?.let {
                    if (root > 0L) resolveSentSubReply(
                        it, root, parent, sendReplyTarget, oid = sendSubject.oid, replyType = sendSubject.type
                    ) else it.copy(oid = sendSubject.oid, replyType = sendSubject.type)
                }
                _commentState.value = current.copy(
                    isSending = false,
                    sendError = null,
                    replyTarget = completedReplyTarget,
                    replyCount = current.replyCount + if (resolvedReply == null) 1 else 0
                )
                if (resolvedReply != null) {
                    applySentMainListReply(resolvedReply)
                }

                if (root == 0L) {
                    if (resolvedReply == null) {
                        requestComments(refresh = true, forceRefresh = true)
                    }
                } else if (isSubReplyContext) {
                    val currentSub = _subReplyState.value
                    val canApplyToThread = shouldApplySentSubReplyResult(
                        root, sendSubReplySession, subReplySession, currentSub
                    )
                    if (resolvedReply != null && canApplyToThread) {
                        localSubReplies[resolvedReply.rpid] = resolvedReply
                        _subReplyState.value = currentSub.withSentSubReply(resolvedReply)
                    } else if (resolvedReply == null && canApplyToThread) {
                        dialogPagingSession = null
                        subReplyPagingSession = null
                        subReplyLoadJob?.cancel()
                        _subReplyState.value = currentSub.copy(
                            items = if (currentSub.isTargetOnly) {
                                if (currentSub.conversationAnchor == null) currentSub.items else currentSub.baseItems
                            } else persistentListOf(),
                            baseItems = if (currentSub.isTargetOnly) currentSub.baseItems else persistentListOf(),
                            conversationAnchor = null,
                            page = 1,
                            isEnd = false,
                            isLoading = true,
                            grpcNextOffset = null
                        )
                        loadSubReplies(
                            subject = sendSubject,
                            rootId = currentSub.threadRootId,
                            page = 1,
                            paginationOffset = null
                        )
                    }
                } else if (resolvedReply == null) {
                    requestComments(refresh = true, forceRefresh = true)
                }
            }.onFailure { e ->
                if (!shouldApplyCommentSubjectResult(sendSubject, currentSubject) ||
                    sendAccount != TokenManager.accountIdentity.value) return@onFailure
                android.util.Log.e("CommentVM", " sendComment failed: ${e.message}")
                _commentState.value = _commentState.value.copy(isSending = false, sendError = e.message)
            }
        }
    }

    private suspend fun uploadCommentPictures(imageUris: List<Uri>): Result<List<ReplyPicture>> {
        if (imageUris.isEmpty()) return Result.success(emptyList())
        val context = NetworkModule.appContext ?: return Result.failure(Exception("应用上下文不可用"))
        return withContext(Dispatchers.IO) {
            runCatching {
                imageUris.take(9).mapIndexed { index, uri ->
                    val fileName = queryDisplayName(context, uri)
                        ?: "comment_${System.currentTimeMillis()}_${index + 1}.jpg"
                    // 流式上传:空/15MB 校验在 CommentRepository 内完成,不再整文件读入内存。
                    CommentRepository.uploadCommentImage(
                        fileName = fileName,
                        mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                        resolver = context.contentResolver,
                        uri = uri
                    ).getOrElse { throw it }
                }
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }
    
    fun beginCommentDraft() {
        _commentState.update { it.copy(composerDrafts = it.composerDrafts.open(), sendError = null) }
    }

    fun updateCommentDraft(
        sessionId: Long,
        replyRpid: Long?,
        text: String,
        imageUris: List<Uri>,
        syncToDynamic: Boolean
    ) {
        _commentState.update {
            it.copy(
                composerDrafts = it.composerDrafts.update(
                    sessionId, replyRpid, CommentComposerDraft(text, imageUris, syncToDynamic)
                )
            )
        }
    }

    fun replyTo(reply: ReplyItem) {
        _commentState.value = _commentState.value.copy(replyTarget = reply)
    }
    
    fun cancelReply() {
        _commentState.value = _commentState.value.copy(replyTarget = null)
    }
    
    fun likeComment(rpid: Long) = reactToComment(rpid, liked = true)

    fun hateComment(rpid: Long) = reactToComment(rpid, liked = false)

    private fun reactToComment(rpid: Long, liked: Boolean) {
        val subject = currentSubject
        val account = TokenManager.accountIdentity.value
        val store = sentReplies
        val sessionId = commentSessionId
        val operation = sessionId to rpid
        // 同一评论提交期间保持当前反馈，其他评论仍可独立操作。
        if (!pendingReactions.add(operation)) return
        val before = _commentState.value
        val previousAction = when (rpid) {
            in before.likedComments -> 1
            in before.hatedComments -> 2
            else -> 0
        }
        val action = if (liked) {
            if (previousAction == 1) 0 else 1
        } else {
            if (previousAction == 2) 0 else 2
        }
        fun applyAction(value: Int) {
            reactionOverrides[rpid] = value
            _commentState.update { state -> state.copy(
                likedComments = ((state.likedComments - rpid) + if (value == 1) setOf(rpid) else emptySet()).toImmutableSet(),
                hatedComments = ((state.hatedComments - rpid) + if (value == 2) setOf(rpid) else emptySet()).toImmutableSet(),
            ) }
        }
        applyAction(action)
        viewModelScope.launch {
            try {
                val result = if (liked) CommentRepository.likeCommentForSubject(
                    subject.oid, subject.type, rpid, action == 1
                ) else CommentRepository.hateCommentForSubject(
                    subject.oid, subject.type, rpid, action == 2
                )
                if (commentSessionId == sessionId && sentReplies === store && currentSubject == subject &&
                    TokenManager.accountIdentity.value == account) {
                    result.onFailure { error ->
                        applyAction(previousAction)
                        showCommentActionError(error.message ?: "操作失败，请重试")
                    }
                }
            } finally {
                pendingReactions.remove(operation)
            }
        }
    }

    private fun showCommentActionError(message: String) {
        android.widget.Toast.makeText(NetworkModule.appContext, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** 加载只更新未被本次会话操作覆盖的服务器状态。 */
    private fun reactionIds(existing: Set<Long>, replies: List<ReplyItem>, action: Int): ImmutableSet<Long> {
        val loadedIds = replies.mapTo(mutableSetOf()) { it.rpid }
        val server = existing - loadedIds + replies.filter { it.action == action }.map { it.rpid }
        return (server - reactionOverrides.keys + reactionOverrides.filterValues { it == action }.keys).toImmutableSet()
    }

    fun reportComment(rpid: Long, reason: Int, content: String = "") {
        viewModelScope.launch {
            CommentRepository.reportCommentForSubject(
                oid = currentSubject.oid,
                type = currentSubject.type,
                rpid = rpid,
                reason = reason,
                content = content
            )
        }
    }

    fun toggleTopComment(reply: ReplyItem) {
        if (currentSubject.oid <= 0L || reply.rpid <= 0L) return
        val current = _commentState.value
        val isCurrentlyTop = reply.rpid in current.pinnedReplyIds || reply.replyControl?.isUpTop == true
        viewModelScope.launch {
            CommentRepository.setCommentTopForSubject(
                oid = currentSubject.oid,
                type = currentSubject.type,
                rpid = reply.rpid,
                isCurrentlyTop = isCurrentlyTop
            ).onSuccess {
                requestComments(refresh = true, forceRefresh = true)
            }.onFailure { error ->
                android.util.Log.e("CommentVM", "toggleTopComment failed: ${error.message}")
            }
        }
    }

    // --- [新增] 评论反诈检测 ---

    /**
     * 启动评论反诈后台检测
     */
    private fun launchFraudDetection(
        aid: Long,
        rpid: Long,
        rootId: Long,
        message: String = "",
        hasPictures: Boolean = false,
        sentAtSeconds: Long = 0,
        waitMs: Long = -1L,
        preserveInitialStatus: Boolean = false
    ) {
        _commentState.value = _commentState.value.copy(
            isDetectingFraud = true,
            fraudDetectResult = null,
            fraudDetectRpid = rpid
        )
        viewModelScope.launch {
            val result = CommentRepository.checkCommentStatus(
                aid = aid,
                rpid = rpid,
                rootId = rootId,
                hasPictures = hasPictures,
                sentAtSeconds = sentAtSeconds,
                waitMs = waitMs
            )
            result.onSuccess { status ->
                android.util.Log.d("CommentVM", "评论反诈检测结果: $status (rpid=$rpid)")
                CommentFraudRepository.saveRecord(
                    rpid = rpid,
                    oid = aid,
                    type = 1,
                    root = rootId,
                    message = message,
                    status = status,
                    // 发评自动检测写入初始出生状态；手动复检保留历史 initial_status
                    initialStatus = if (preserveInitialStatus) null else status
                )
                _commentState.value = _commentState.value.copy(
                    isDetectingFraud = false,
                    fraudDetectResult = status,
                    fraudDetectRpid = rpid
                )
                // 发射 one-shot event 给 UI 弹窗
                _fraudEvent.tryEmit(status)
            }.onFailure { e ->
                android.util.Log.e("CommentVM", "评论反诈检测失败: ${e.message}")
                _commentState.value = _commentState.value.copy(
                    isDetectingFraud = false
                )
            }
        }
    }
    /**
     * 手动触发某条自己评论的反诈检测（评论长按菜单「检测评论状态」入口）。
     *
     * 与发评自动检测的区别：
     * - 不受 [fraudDetectionEnabled] 设置门控（用户主动触发）；
     * - waitMs=0 立即检测（不是刚发的评论，无需等待主从同步缓冲）；
     * - 仅更新 status，initialStatus 传 null 以保留历史记录中的初始出生状态。
     */
    fun checkCommentFraud(reply: ReplyItem) {
        val aid = currentAid
        if (aid <= 0L || reply.rpid <= 0L) return
        launchFraudDetection(
            aid = aid,
            rpid = reply.rpid,
            rootId = reply.root,
            message = reply.content.message,
            hasPictures = !reply.content.pictures.isNullOrEmpty(),
            sentAtSeconds = reply.ctime.takeIf { it > 0L } ?: 0L,
            waitMs = 0L,
            preserveInitialStatus = true
        )
    }

    /** 清除检测结果（用户关闭弹窗后调用） */
    fun dismissFraudResult() {
        _commentState.value = _commentState.value.copy(
            fraudDetectResult = null,
            fraudDetectRpid = 0
        )
    }

    // --- [新增] 删除动画逻辑 ---

    /**
     * 开始删除动画
     * UI 调用此方法触发消散动画，动画结束后 UI 回调 deleteComment
     */
    fun startDissolve(rpid: Long) {
        val current = _commentState.value
        _commentState.value = current.copy(dissolvingIds = (current.dissolvingIds + rpid).toImmutableSet())
    }

    /**
     * 实际删除操作 (动画完成后调用)
     */
    fun deleteComment(rpid: Long) = requestDelete(rpid, subReply = false)

    private fun requestDelete(rpid: Long, subReply: Boolean) {
        val subject = currentSubject
        val account = TokenManager.accountIdentity.value
        val store = sentReplies
        val sessionId = commentSessionId
        val operation = sessionId to rpid
        val rootId = _subReplyState.value.threadRootId.takeIf { it > 0L }
        if (!pendingDeletes.add(operation)) return
        viewModelScope.launch {
            try {
                val result = CommentRepository.deleteCommentForSubject(subject.oid, subject.type, rpid)
                if (commentSessionId != sessionId || sentReplies !== store || currentSubject != subject ||
                    TokenManager.accountIdentity.value != account) return@launch
                result.onSuccess {
                    if (subReply) applyDeletedSubComment(rpid, rootId) else applyDeletedComment(rpid)
                }.onFailure { error ->
                    _commentState.update { it.copy(dissolvingIds = (it.dissolvingIds - rpid).toImmutableSet()) }
                    _subReplyState.update { it.copy(dissolvingIds = (it.dissolvingIds - rpid).toImmutableSet()) }
                    showCommentActionError(error.message ?: "删除失败，请重试")
                }
            } finally {
                pendingDeletes.remove(operation)
            }
        }
    }

    private fun applyDeletedComment(rpid: Long) {
        if (!removedMainReplyIds.add(rpid)) return
        sentReplies.removeRoot(rpid)
        // 服务端确认后同步列表和数量。
        val current = _commentState.value
        val updatedReplies = allReplies.filter { it.rpid != rpid }
        allReplies = updatedReplies
        
        // 移除 dissolvingId
        _commentState.value = current.copy(
            replies = current.replies.filter { it.rpid != rpid }.toImmutableList(),
            replyCount = maxOf(0, current.replyCount - 1),
            dissolvingIds = (current.dissolvingIds - rpid).toImmutableSet()
        )
    }
    
    /**
     * [新增] 开始二级评论消散动画
     */
    fun startSubDissolve(rpid: Long) {
        val current = _subReplyState.value
        _subReplyState.value = current.copy(dissolvingIds = (current.dissolvingIds + rpid).toImmutableSet())
    }
    
    /**
     * [新增] 删除二级评论（动画完成后调用）
     */
    fun deleteSubComment(rpid: Long) = requestDelete(rpid, subReply = true)

    private fun applyDeletedSubComment(rpid: Long, rootId: Long?) {
        val newlyRemoved = removedSubReplyIds.add(rpid)
        val current = _subReplyState.value
        val matchesThread = current.threadRootId == rootId
        if (matchesThread) localSubReplies.remove(rpid)
        if (newlyRemoved) {
            rootId?.let { sentReplies.removeReply(it, rpid) }
            allReplies = allReplies.map { root ->
                if (root.rpid != rootId) root else root.copy(
                    replies = root.replies?.filterNot { it.rpid == rpid },
                    count = (root.count - 1).coerceAtLeast(0),
                    rcount = (root.rcount - 1).coerceAtLeast(0)
                )
            }
            _commentState.update { it.copy(replies = allReplies.toImmutableList()) }
        }
        // 从二级评论列表中移除
        val updatedItems = current.items.filter { it.rpid != rpid }
        if (matchesThread) _subReplyState.value = current.copy(
            rootReply = current.rootReply?.let { root ->
                if (!newlyRemoved) root else root.copy(
                    replies = root.replies?.filterNot { it.rpid == rpid },
                    count = (root.count - 1).coerceAtLeast(0),
                    rcount = (root.rcount - 1).coerceAtLeast(0)
                )
            },
            totalCount = (current.totalCount - if (newlyRemoved) 1 else 0).coerceAtLeast(0),
            items = updatedItems.toImmutableList(),
            baseItems = current.baseItems.filterNot { it.rpid == rpid }.toImmutableList(),
            dissolvingIds = (current.dissolvingIds - rpid).toImmutableSet()
        )
        if (matchesThread && current.isTargetOnly && current.rootReply == null &&
            updatedItems.isEmpty() && current.baseItems.none { it.rpid != rpid }) {
            closeSubReply()
        }
        
        // 楼中楼删除与主评论删除共用总数，重复回调不重复扣减。
        if (newlyRemoved) {
            _commentState.update { state ->
                if (state.replyCountSubject == currentSubject) {
                    state.copy(replyCount = (state.replyCount - 1).coerceAtLeast(0))
                } else state
            }
        }

        android.util.Log.d("CommentVM", " deleteSubComment: rpid=$rpid, remaining=${updatedItems.size}")

    }

}
