package com.android.purebilibili.feature.video.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyPage
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplyVoteCard
import com.android.purebilibili.data.repository.CommentGrpcRepository
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.data.repository.shouldStartCommentFraudDetection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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

internal data class CommentSubjectKey(
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
    val isRepliesLoading: Boolean = false,
    val isRepliesRefreshing: Boolean = false,
    val replyCount: Int = 0,
    val repliesError: String? = null,
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
    val baseIsEnd: Boolean = false,
    val error: String? = null,
    val upMid: Long = 0,
    val grpcNextOffset: String? = null,
    val baseGrpcNextOffset: String? = null,
    val conversationAnchor: ReplyItem? = null,
    val targetReplyId: Long = 0,
    // [新增] 消散动画状态
    val dissolvingIds: ImmutableSet<Long> = persistentSetOf()
)

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
    val error: String? = null
)

class VideoCommentViewModel : ViewModel() {
    private val _commentState = MutableStateFlow(CommentUiState())
    val commentState = _commentState.asStateFlow()

    private var commentLoadJob: Job? = null
    private var commentLoadRequestId = 0L

    // 进程内评论首页缓存（跨 ViewModel 实例）：重进同一个视频的评论区先秒出旧内容，
    // 再后台静默刷新，避免每次都从白屏骨架开始。
    private val commentCache = sharedVideoCommentCache
    private var subReplyLoadJob: Job? = null
    private var subReplySession = 0L
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
                upMid = state.upMid,
                pinnedReplyIds = state.pinnedReplyIds,
                subject = subject,
                cachedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * 切换视频时立即废弃旧评论主体和未完成请求，等新页面真正打开评论区后再加载。
     */
    fun clearForVideoChange() {
        commentLoadRequestId++
        commentLoadJob?.cancel()
        currentAid = 0L
        currentSubject = CommentSubjectKey(0L)
        allReplies = emptyList()
        _commentState.value = CommentUiState(
            currentMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L,
            composerDrafts = _commentState.value.composerDrafts.reset()
        )
        subReplyLoadJob?.cancel()
        _subReplyState.value = SubReplyUiState()
        resetFullCommentSearch()
    }

    // 初始化/重置
    fun init(
        aid: Long,
        upMid: Long = 0,
        preferredSortMode: CommentSortMode = CommentSortMode.HOT,
        expectedReplyCount: Int = 0,
        commentType: Int = VIDEO_COMMENT_TYPE
    ) {
        android.util.Log.d("CommentVM", " init called with aid=$aid, upMid=$upMid, currentAid=$currentAid, type=$commentType")
        if (currentAid == aid && _commentState.value.upMid == upMid && currentSubject.type == commentType) {
            // [修复] 即使视频相同，也刷新 currentMid（防止登录状态变化后不更新）
            refreshCurrentMid()
            if (expectedReplyCount > _commentState.value.replyCount) {
                _commentState.value = _commentState.value.copy(replyCount = expectedReplyCount)
            }
            return
        }
        val currentStateSubjectChanged = currentSubject != CommentSubjectKey(aid, commentType)
        commentLoadRequestId++
        commentLoadJob?.cancel()
        currentAid = aid
        currentSubject = CommentSubjectKey(oid = aid, type = commentType)
        allReplies = emptyList()
        resetFullCommentSearch()
        // 获取当前登录用户 mid
        val myMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
        android.util.Log.d("CommentVM", " init: myMid=$myMid")
        _commentState.value = CommentUiState(
            sortMode = preferredSortMode,
            upMid = upMid,
            currentMid = myMid,
            replyCount = expectedReplyCount.coerceAtLeast(0),
            replyTarget = if (currentStateSubjectChanged) null else _commentState.value.replyTarget,
            isSending = !currentStateSubjectChanged && _commentState.value.isSending,
            sendError = if (currentStateSubjectChanged) null else _commentState.value.sendError,
            composerDrafts = if (currentStateSubjectChanged) {
                _commentState.value.composerDrafts.reset()
            } else {
                _commentState.value.composerDrafts
            }
        )
        subReplyLoadJob?.cancel()
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
        
        android.util.Log.d("CommentVM", " setSortMode")
        
        commentLoadRequestId++
        commentLoadJob?.cancel()
        allReplies = emptyList()
        _commentState.value = CommentUiState(
            sortMode = mode,
            upMid = currentState.upMid,
            currentMid = currentState.currentMid,
            replyCount = currentState.replyCount,
            isSending = currentState.isSending,
            sendError = currentState.sendError,
            replyTarget = currentState.replyTarget,
            composerDrafts = currentState.composerDrafts
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
        val requestSubject = currentSubject
        if (!requestSubject.isValid) return
        val requestId = ++commentLoadRequestId
        commentLoadJob?.cancel()
        _commentState.value = currentState.copy(
            isRepliesLoading = true,
            isRepliesRefreshing = refresh,
            repliesError = null,
        )

        commentLoadJob = viewModelScope.launch {
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
                allReplies = cached.replies
                _commentState.value = _commentState.value.copy(
                    replies = cached.replies.toImmutableList(),
                    replyCount = maxOf(cached.replyCount, currentState.replyCount),
                    isRepliesEnd = cached.isEnd,
                    nextPage = cached.nextPage,
                    grpcNextOffset = cached.grpcNextOffset,
                    pinnedReplyIds = cached.pinnedReplyIds.toImmutableSet(),
                    upMid = if (cached.upMid > 0L) cached.upMid else currentState.upMid,
                )
            }
            //  使用当前排序模式
            val result = CommentRepository.getCommentsForSubject(
                oid = requestSubject.oid,
                type = requestSubject.type,
                page = pageToLoad, 
                ps = VIDEO_COMMENT_PAGE_SIZE,
                mode = currentState.sortMode.apiMode,
                paginationOffset = if (refresh) null else currentState.grpcNextOffset,
                fallbackOnMissingLocation = requestSubject.type == 1,
            )

            result.onSuccess { data ->
                if (requestId != commentLoadRequestId ||
                    !shouldApplyCommentSubjectResult(requestSubject, currentSubject)
                ) {
                    return@onSuccess
                }
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
                val combinedReplies = if (pageToLoad == 1) {
                    (topReplies + hotReplies + newReplies).distinctBy { it.rpid }
                } else {
                    (allReplies + newReplies).distinctBy { it.rpid }
                }
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
                val isEnd = pageResolution.isEnd
                
                android.util.Log.d(
                    "CommentVM",
                    " loadComments result: page=$pageToLoad, new=${newReplies.size}, hot=${hotReplies.size}, top=${topReplies.size}, total=${allReplies.size}, allCount=$totalCount, isEnd=$isEnd"
                )
                
                _commentState.value = current.copy(
                    replies = combinedReplies.toImmutableList(),
                    voteCard = if (pageToLoad == 1) data.voteCard else current.voteCard,
                    likedComments = (current.likedComments + combinedReplies.flatMap { root ->
                        (listOf(root) + root.replies.orEmpty()).filter { it.action == 1 }.map { it.rpid }
                    }).toImmutableSet(),
                    hatedComments = (current.hatedComments + combinedReplies.flatMap { root ->
                        (listOf(root) + root.replies.orEmpty()).filter { it.action == 2 }.map { it.rpid }
                    }).toImmutableSet(),
                    replyCount = totalCount,
                    isRepliesLoading = false,
                    isRepliesRefreshing = false,
                    repliesError = null,
                    isRepliesEnd = isEnd,
                    nextPage = pageToLoad + 1,
                    rootInputHint = data.control?.rootInputText?.takeIf { it.isNotBlank() } ?: current.rootInputHint,
                    childInputHint = data.control?.childInputText?.takeIf { it.isNotBlank() } ?: current.childInputHint,
                    canInputComment = data.control?.inputDisable?.not() ?: current.canInputComment,
                    showUpFlag = data.config?.showUpFlag ?: current.showUpFlag,
                    pinnedReplyIds = pinnedReplyIds.toImmutableSet(),
                    grpcNextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() }
                )
                // 只缓存首页：后续页没有缓存时也能从首页继续翻，缓存整段会让内存迅速膨胀。
                if (pageToLoad == 1) {
                    cacheCurrentComments(combinedReplies)
                }
            }.onFailure { e ->
                if (requestId != commentLoadRequestId ||
                    !shouldApplyCommentSubjectResult(requestSubject, currentSubject)
                ) {
                    return@onFailure
                }
                android.util.Log.e("CommentVM", " loadComments error: ${e.message}")
                // 命中缓存时这次只是「静默刷新」失败：内容还在，就不弹错误条；也不要把
                // 「还有下一页」错判成已到底，否则用户既看不到错误、又再也拉不到后续评论。
                _commentState.value = _commentState.value.copy(
                    isRepliesLoading = false,
                    isRepliesRefreshing = false,
                    repliesError = if (cached != null) null else (e.message ?: "加载评论失败"),
                    isRepliesEnd = if (refresh || cached != null) {
                        _commentState.value.isRepliesEnd
                    } else {
                        true
                    }
                )
            }
        }
    }

    // --- [新增] 全量评论加载（供评论区搜索/充电评论过滤） ---

    fun loadAllCommentsForSearch() {
        val state = _fullSearchState.value
        if (state.isLoading || state.isReady) return
        val subject = currentSubject
        if (!subject.isValid) return
        fullSearchJob?.cancel()
        _fullSearchState.value = FullCommentSearchUiState(isLoading = true)
        fullSearchJob = viewModelScope.launch {
            val collected = mutableListOf<ReplyItem>()
            var offset: String? = null
            var page = 1
            var totalCount = 0
            var failure: String? = null
            while (isActive) {
                val data = CommentRepository.getCommentsForSubject(
                    oid = subject.oid,
                    type = subject.type,
                    page = page,
                    ps = VIDEO_COMMENT_PAGE_SIZE,
                    mode = CommentGrpcRepository.MODE_TIME,
                    paginationOffset = offset,
                ).getOrNull()
                if (data == null) {
                    failure = "评论加载失败，请稍后重试"
                    break
                }
                if (page == 1) {
                    totalCount = data.getAllCount()
                    collected += data.collectTopReplies()
                }
                val batch = data.replies.orEmpty()
                collected += batch
                _fullSearchState.value = _fullSearchState.value.copy(
                    loadedCount = collected.size,
                    totalCount = totalCount
                )
                val next = data.grpcNextOffset.takeIf { it.isNotBlank() }
                if (data.cursor.isEnd || batch.isEmpty() || next == null) break
                offset = next
                page++
                // 安全上限：极端大评论区最多拉 300 页（约 6000 条根评论）
                if (page >= 300) break
            }
            val deduped = collected.distinctBy { it.rpid }
            _fullSearchReplies.value = deduped
            _fullSearchState.value = FullCommentSearchUiState(
                isReady = failure == null,
                loadedCount = deduped.size,
                totalCount = totalCount,
                error = failure
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
        com.android.purebilibili.core.util.Logger.d("VideoInputTrace") {
            "subreply_click subjectValid=${requestSubject.isValid} visible=${_subReplyState.value.visible}"
        }
        if (!requestSubject.isValid) return
        subReplySession++
        subReplyLoadJob?.cancel()
        _subReplyState.value = SubReplyUiState(
            visible = true,
            rootReply = rootReply,
            targetReplyId = targetReplyId.takeIf { it != rootReply.rpid } ?: 0L,
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
        if (!shouldStartRoutedSubReplyOpen(rootReplyId, currentAid)) return false

        resolveRoutedCommentRootReply(
            loadedReplies = allReplies.ifEmpty { _commentState.value.replies },
            remoteData = null,
            rootReplyId = rootReplyId
        )?.let { rootReply ->
            openSubReply(rootReply, targetReplyId)
            return true
        }

        subReplySession++
        subReplyLoadJob?.cancel()
        val routeSubject = currentSubject
        _subReplyState.value = _subReplyState.value.copy(
            visible = false,
            isLoading = true,
            error = null,
            targetReplyId = targetReplyId.takeIf { it != rootReplyId } ?: 0L
        )

        subReplyLoadJob = viewModelScope.launch {
            CommentRepository.getSortedSubCommentsForSubject(
                oid = routeSubject.oid,
                type = routeSubject.type,
                rootId = rootReplyId,
                mode = SubReplySortMode.TIME.apiMode,
                targetReplyId = targetReplyId
            ).onSuccess { data ->
                if (!shouldApplyCommentSubjectResult(routeSubject, currentSubject)) {
                    return@onSuccess
                }
                val rootReply = resolveRoutedCommentRootReply(
                    loadedReplies = emptyList(),
                    remoteData = data,
                    rootReplyId = rootReplyId
                )
                if (rootReply == null) {
                    _subReplyState.value = _subReplyState.value.copy(
                        isLoading = false,
                        error = "回复可能已被删除或不可见"
                    )
                    return@onSuccess
                }

                val items = data.replies.orEmpty()
                val remoteTotalCount = resolveSubReplyRemoteTotalCount(
                    data = data,
                    rootReply = rootReply
                )
                val totalCount = resolveSubReplyLoadedTotalCount(
                    rootReply = rootReply,
                    loadedReplyCount = items.size,
                    remoteReplyCount = remoteTotalCount
                )
                val isEnd = isSortedSubReplyPageEnd(data.cursor.isEnd, data.grpcNextOffset)
                _subReplyState.value = SubReplyUiState(
                    visible = true,
                    rootReply = rootReply,
                    items = items.toImmutableList(),
                    baseItems = items.toImmutableList(),
                    totalCount = totalCount,
                    isLoading = false,
                    page = 1,
                    basePage = 1,
                    isEnd = isEnd,
                    baseIsEnd = isEnd,
                    grpcNextOffset = data.grpcNextOffset,
                    baseGrpcNextOffset = data.grpcNextOffset,
                    upMid = _commentState.value.upMid,
                    targetReplyId = targetReplyId.takeIf { it != rootReplyId } ?: 0L
                )
            }.onFailure { error ->
                if (!shouldApplyCommentSubjectResult(routeSubject, currentSubject)) {
                    return@onFailure
                }
                _subReplyState.value = _subReplyState.value.copy(
                    isLoading = false,
                    error = error.message ?: "回复加载失败"
                )
            }
        }
        return true
    }

    fun closeSubReply() {
        subReplySession++
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

    fun onExternalCommentSent(
        aid: Long,
        newReply: ReplyItem?,
        fraudDetectionEnabled: Boolean = true
    ) {
        if (currentAid != aid || aid <= 0L) return

        val current = _commentState.value
        val subState = _subReplyState.value
        val activeRootReply = subState.rootReply

        if (newReply == null) {
            if (subState.visible && activeRootReply != null) {
                _subReplyState.value = subState.copy(
                    items = emptyList<ReplyItem>().toImmutableList(),
                    page = 1,
                    isEnd = false,
                    isLoading = true,
                    error = null
                )
                loadSubReplies(
                    subject = currentSubject,
                    rootId = activeRootReply.rpid,
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

        val updatedAllReplies = when {
            newReply.root == 0L -> {
                (listOf(newReply) + allReplies).distinctBy { it.rpid }
            }
            else -> {
                allReplies.map { rootReply ->
                    if (rootReply.rpid != newReply.root) return@map rootReply
                    val updatedPreviewReplies = insertSentSubReply(rootReply.replies.orEmpty(), newReply)
                    rootReply.copy(
                        count = rootReply.count + 1,
                        rcount = rootReply.rcount + 1,
                        replies = updatedPreviewReplies
                    )
                }
            }
        }
        allReplies = updatedAllReplies

        _commentState.value = current.copy(
            replies = updatedAllReplies.toImmutableList(),
            replyCount = current.replyCount + 1
        )

        if (subState.visible && activeRootReply != null && newReply.root == activeRootReply.rpid) {
            _subReplyState.value = subState.withSentSubReply(newReply)
        }
    }

    fun setSubReplySortMode(mode: SubReplySortMode) {
        val state = _subReplyState.value
        val root = state.rootReply ?: return
        if (!state.visible || state.conversationAnchor != null || state.sortMode == mode) return
        subReplyLoadJob?.cancel()
        _subReplyState.update { it.resetForSort(mode) }
        loadSubReplies(currentSubject, root.rpid, page = 1, paginationOffset = null)
    }

    fun refreshSubReplies() {
        val state = _subReplyState.value
        val root = state.rootReply ?: return
        if (!state.visible || state.isRefreshing ||
            (state.isLoading && state.items.isEmpty()) || !currentSubject.isValid
        ) return
        subReplyLoadJob?.cancel()
        _subReplyState.update { current ->
            current.copy(
                isLoading = true,
                isRefreshing = true,
                error = null,
                // A user refresh starts a normal first page, not another deep-link lookup.
                targetReplyId = 0L,
            )
        }
        val anchor = state.conversationAnchor
        if (anchor != null) {
            loadConversationReplies(anchor, page = 1, paginationOffset = null)
        } else {
            loadSubReplies(currentSubject, root.rpid, page = 1, paginationOffset = null)
        }
    }

    fun loadMoreSubReplies() {
        val state = _subReplyState.value
        if (state.isLoading || state.isEnd || state.rootReply == null) return
        val nextPage = if (state.error != null && state.items.isEmpty()) 1 else state.page + 1
        _subReplyState.value = state.copy(isLoading = true, error = null)
        val anchor = state.conversationAnchor
        if (anchor != null) {
            loadConversationReplies(anchor, nextPage)
        } else {
            loadSubReplies(
                subject = currentSubject,
                rootId = state.rootReply.rpid,
                page = nextPage,
                paginationOffset = state.grpcNextOffset
            )
        }
    }

    fun openSubReplyConversation(anchorReply: ReplyItem) {
        subReplyLoadJob?.cancel()
        val current = _subReplyState.value
        val rootReply = current.rootReply ?: return
        val baseItems = current.baseItems.ifEmpty { current.items }
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
            error = null,
            grpcNextOffset = null
        )
        loadConversationReplies(anchorReply, page = 1, rootReply = rootReply)
    }

    fun closeSubReplyConversation() {
        val current = _subReplyState.value
        if (current.conversationAnchor == null) return
        subReplyLoadJob?.cancel()
        _subReplyState.value = current.copy(
            items = current.baseItems,
            page = current.basePage,
            isEnd = current.baseIsEnd,
            grpcNextOffset = current.baseGrpcNextOffset,
            conversationAnchor = null,
            isLoading = false,
            isRefreshing = false,
            error = null
        )
    }

    private fun loadSubReplies(
        subject: CommentSubjectKey,
        rootId: Long,
        page: Int,
        paginationOffset: String? = _subReplyState.value.grpcNextOffset
    ) {
        if (!subject.isValid || rootId <= 0L) return
        val sortMode = _subReplyState.value.sortMode
        val targetReplyId = _subReplyState.value.targetReplyId.takeIf { page == 1 } ?: 0L
        subReplyLoadJob?.cancel()
        subReplyLoadJob = viewModelScope.launch {
            val result = CommentRepository.getSortedSubCommentsForSubject(
                oid = subject.oid,
                type = subject.type,
                rootId = rootId,
                mode = sortMode.apiMode,
                targetReplyId = targetReplyId,
                paginationOffset = paginationOffset
            )
            result.onSuccess { data ->
                val current = _subReplyState.value
                if (!shouldApplySubReplyResult(
                        expectedSubject = subject,
                        expectedRootId = rootId,
                        currentSubject = currentSubject,
                        activeRootId = current.rootReply?.rpid,
                        conversationActive = current.conversationAnchor != null
                    )
                ) {
                    return@onSuccess
                }
                val newItems = data.replies ?: emptyList()
                val updatedItems = if (page == 1) newItems else (current.items + newItems).distinctBy { it.rpid }
                val remoteTotalCount = resolveSubReplyRemoteTotalCount(
                    data = data,
                    rootReply = current.rootReply
                )
                val totalCount = resolveSubReplyLoadedTotalCount(
                    rootReply = current.rootReply,
                    loadedReplyCount = updatedItems.size,
                    remoteReplyCount = remoteTotalCount,
                    previousTotalCount = current.totalCount
                )
                val isEnd = isSortedSubReplyPageEnd(data.cursor.isEnd, data.grpcNextOffset)

                _subReplyState.value = current.copy(
                    items = updatedItems.toImmutableList(),
                    baseItems = updatedItems.toImmutableList(),
                    totalCount = totalCount,
                    isLoading = false,
                    isRefreshing = false,
                    page = page,
                    basePage = page,
                    isEnd = isEnd,
                    baseIsEnd = isEnd,
                    error = null,
                    grpcNextOffset = data.grpcNextOffset,
                    baseGrpcNextOffset = data.grpcNextOffset
                )
                val commentState = _commentState.value
                _commentState.value = commentState.copy(
                    likedComments = (commentState.likedComments + updatedItems.filter { it.action == 1 }.map { it.rpid }).toImmutableSet(),
                    hatedComments = (commentState.hatedComments + updatedItems.filter { it.action == 2 }.map { it.rpid }).toImmutableSet()
                )
            }.onFailure {
                val current = _subReplyState.value
                if (!shouldApplySubReplyResult(
                        expectedSubject = subject,
                        expectedRootId = rootId,
                        currentSubject = currentSubject,
                        activeRootId = current.rootReply?.rpid,
                        conversationActive = current.conversationAnchor != null
                    )
                ) {
                    return@onFailure
                }
                _subReplyState.value = _subReplyState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = it.message ?: "回复加载失败"
                )
            }
        }
    }

    private fun loadConversationReplies(
        anchorReply: ReplyItem,
        page: Int,
        rootReply: ReplyItem? = _subReplyState.value.rootReply,
        paginationOffset: String? = _subReplyState.value.grpcNextOffset,
    ) {
        val root = rootReply ?: return
        val dialogId = resolveConversationDialogId(anchorReply)
        if (dialogId <= 0L) {
            _subReplyState.value = _subReplyState.value.copy(
                isLoading = false, isRefreshing = false, isEnd = true
            )
            return
        }
        val subject = currentSubject
        if (!subject.isValid) return
        val rootId = root.rpid
        subReplyLoadJob?.cancel()
        subReplyLoadJob = viewModelScope.launch {
            val result = CommentRepository.getDialogCommentsForSubject(
                oid = subject.oid,
                type = subject.type,
                rootId = rootId,
                dialogId = dialogId,
                page = page,
                paginationOffset = paginationOffset
            )
            result.onSuccess { data ->
                val current = _subReplyState.value
                if (!shouldApplyConversationReplyResult(
                        expectedSubject = subject,
                        expectedRootId = rootId,
                        expectedDialogId = dialogId,
                        currentSubject = currentSubject,
                        activeRootId = current.rootReply?.rpid,
                        activeDialogId = current.conversationAnchor?.let(::resolveConversationDialogId)
                    )
                ) {
                    return@onSuccess
                }
                val newItems = data.replies.orEmpty()
                val nextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() }
                val updatedItems = if (page == 1) {
                    newItems
                } else {
                    (current.items + newItems).distinctBy { it.rpid }
                }
                _subReplyState.value = current.copy(
                    items = updatedItems.ifEmpty {
                        resolveLocalConversationItems(anchorReply, current.baseItems)
                    }.toImmutableList(),
                    isLoading = false,
                    isRefreshing = false,
                    page = page,
                    isEnd = data.cursor.isEnd || newItems.isEmpty() || nextOffset == null,
                    error = null,
                    grpcNextOffset = nextOffset
                )
            }.onFailure { error ->
                val current = _subReplyState.value
                if (!shouldApplyConversationReplyResult(
                        expectedSubject = subject,
                        expectedRootId = rootId,
                        expectedDialogId = dialogId,
                        currentSubject = currentSubject,
                        activeRootId = current.rootReply?.rpid,
                        activeDialogId = current.conversationAnchor?.let(::resolveConversationDialogId)
                    )
                ) {
                    return@onFailure
                }
                _subReplyState.value = current.copy(
                    items = current.items.ifEmpty {
                        resolveLocalConversationItems(anchorReply, current.baseItems)
                    }.toImmutableList(),
                    isLoading = false,
                    isRefreshing = false,
                    isEnd = if (current.isRefreshing) current.isEnd else true,
                    error = error.message
                )
            }
        }
    }

    private fun resolveConversationDialogId(anchorReply: ReplyItem): Long {
        return when {
            anchorReply.dialog > 0L -> anchorReply.dialog
            anchorReply.parent > 0L -> anchorReply.parent
            else -> anchorReply.rpid
        }
    }

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
        
        _commentState.value = currentState.copy(isSending = true, sendError = null)

        // Keep the request bound to the episode that opened the composer. A quick episode
        // switch must not let a late response update the newly selected comment thread.
        val sendSubject = currentSubject
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
            val isSubReplyContext = sendSubReplyState.visible && sendSubReplyState.rootReply != null
            
            val root = if (isSubReplyContext) {
                sendSubReplyState.rootReply!!.rpid
            } else {
                sendReplyTarget?.let { it.root.takeIf { id -> id > 0L } ?: it.rpid } ?: 0L
            }
            // parent 总是回复目标的 ID (如果没有回复目标，则是 0)
            val parent = sendReplyTarget?.rpid ?: 0
            
            val picturesResult = uploadCommentPictures(imageUris)
            val pictures = picturesResult.getOrElse { error ->
                if (shouldApplyCommentSubjectResult(sendSubject, currentSubject)) {
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
                if (!shouldApplyCommentSubjectResult(sendSubject, currentSubject)) return@onSuccess
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
                    // 💾 发评成功瞬间立即在本地数据库记一笔
                    viewModelScope.launch {
                        com.android.purebilibili.data.repository.CommentFraudRepository.saveRecord(
                            rpid = rpidToCheck,
                            oid = sendSubject.oid,
                            type = sendSubject.type,
                            root = root,
                            message = message,
                            status = com.android.purebilibili.data.model.CommentFraudStatus.NORMAL
                        )
                    }
                    
                    val sentAtSeconds = newReply?.ctime?.takeIf { it > 0L } ?: (System.currentTimeMillis() / 1000L)
                    launchFraudDetection(
                        aid = sendCurrentAid,
                        rpid = rpidToCheck,
                        rootId = root,
                        message = message,
                        sentAtSeconds = sentAtSeconds
                    )
                }
                
                // 1. 如果是主层级评论 (root=0)
                if (root == 0L) {
                    if (newReply != null) {
                        // 有返回评论对象，直接添加到列表顶部
                        val updatedReplies = listOf(newReply) + allReplies
                        allReplies = updatedReplies
                        _commentState.value = current.copy(
                            replies = updatedReplies.toImmutableList(),
                            isSending = false,
                            sendError = null,
                            replyTarget = completedReplyTarget,
                            replyCount = current.replyCount + 1
                        )
                    } else {
                        // [修复] newReply 为 null 时，重新加载评论列表以显示新评论
                        android.util.Log.d("CommentVM", " sendComment: newReply is null, reloading comments...")
                        _commentState.value = current.copy(
                            isSending = false,
                            sendError = null,
                            replyTarget = completedReplyTarget
                        )
                        // 重置并重新加载
                        allReplies = emptyList()
                        _commentState.value = _commentState.value.copy(
                            replies = emptyList<ReplyItem>().toImmutableList(),
                            nextPage = 1,
                            isRepliesEnd = false
                        )
                        loadComments()
                    }
                } 
                // 2. 如果是二级评论，刷新二级评论列表
                else if (isSubReplyContext) {
                    val currentSub = _subReplyState.value
                    val canApplyToThread = shouldApplySentSubReplyResult(
                        root, sendSubReplySession, subReplySession, currentSub
                    )
                    if (newReply != null && canApplyToThread) {
                        _subReplyState.value = currentSub.withSentSubReply(
                            resolveSentSubReply(newReply, root, parent, sendReplyTarget)
                        )
                    } else if (newReply == null && canApplyToThread) {
                        // [修复] newReply 为 null 时，重新加载二级评论列表
                        android.util.Log.d("CommentVM", " sendComment: sub-reply newReply is null, reloading...")
                        subReplyLoadJob?.cancel()
                        currentSub.rootReply?.let { activeRoot ->
                            _subReplyState.value = currentSub.copy(
                                items = emptyList<ReplyItem>().toImmutableList(),
                                baseItems = emptyList<ReplyItem>().toImmutableList(),
                                conversationAnchor = null,
                                page = 1,
                                isEnd = false,
                                isLoading = true,
                                grpcNextOffset = null
                            )
                            loadSubReplies(
                                subject = sendSubject,
                                rootId = activeRoot.rpid,
                                page = 1,
                                paginationOffset = null
                            )
                        }
                    }
                    
                    _commentState.value = current.copy(
                        isSending = false,
                        sendError = null,
                        replyTarget = completedReplyTarget
                    )
                }
                // 3. 一级列表回复 (展开二级或直接回复)
                else {
                    _commentState.value = current.copy(
                        isSending = false,
                        sendError = null,
                        replyTarget = completedReplyTarget,
                        replyCount = current.replyCount + 1
                    )
                }
            }.onFailure { e ->
                if (!shouldApplyCommentSubjectResult(sendSubject, currentSubject)) return@onFailure
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
    
    fun likeComment(rpid: Long) {
        val currentState = _commentState.value
        val isCurrentlyLiked = rpid in currentState.likedComments
        val newLikedComments = if (isCurrentlyLiked) currentState.likedComments - rpid else currentState.likedComments + rpid
        val newHatedComments = currentState.hatedComments - rpid
        _commentState.value = currentState.copy(
            likedComments = newLikedComments.toImmutableSet(),
            hatedComments = newHatedComments.toImmutableSet()
        )
        
        viewModelScope.launch {
            CommentRepository.likeCommentForSubject(
                oid = currentSubject.oid,
                type = currentSubject.type,
                rpid = rpid,
                like = !isCurrentlyLiked
            ).onFailure {
                _commentState.value = _commentState.value.copy(likedComments = currentState.likedComments, hatedComments = currentState.hatedComments)
            }
        }
    }
    
    fun hateComment(rpid: Long) {
        val currentState = _commentState.value
        val isCurrentlyHated = rpid in currentState.hatedComments
        val newHatedComments = if (isCurrentlyHated) currentState.hatedComments - rpid else currentState.hatedComments + rpid
        val newLikedComments = currentState.likedComments - rpid
        _commentState.value = currentState.copy(
            likedComments = newLikedComments.toImmutableSet(),
            hatedComments = newHatedComments.toImmutableSet()
        )
        
        viewModelScope.launch {
            CommentRepository.hateCommentForSubject(
                oid = currentSubject.oid,
                type = currentSubject.type,
                rpid = rpid,
                hate = !isCurrentlyHated
            ).onFailure {
                _commentState.value = _commentState.value.copy(likedComments = currentState.likedComments, hatedComments = currentState.hatedComments)
            }
        }
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
    fun deleteComment(rpid: Long) {
        // 先从 UI 移除 (乐观更新)
        val current = _commentState.value
        val updatedReplies = allReplies.filter { it.rpid != rpid }
        allReplies = updatedReplies
        
        // 移除 dissolvingId
        _commentState.value = current.copy(
            replies = current.replies.filter { it.rpid != rpid }.toImmutableList(),
            replyCount = maxOf(0, current.replyCount - 1),
            dissolvingIds = (current.dissolvingIds - rpid).toImmutableSet()
        )

        // 发起网络请求
        viewModelScope.launch {
            CommentRepository.deleteCommentForSubject(
                oid = currentSubject.oid,
                type = currentSubject.type,
                rpid = rpid
            ).onFailure { e ->
                // 如果删除失败，可能需要恢复? 暂时只需提示
                // 实际场景中很少失败，除非网络极差
                // 若要严格一致性，可以在这里重新加载评论列表
                android.util.Log.e("CommentVM", "Delete failed for $rpid: ${e.message}")
            }
        }
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
    fun deleteSubComment(rpid: Long) {
        val current = _subReplyState.value
        // 从二级评论列表中移除
        val updatedItems = current.items.filter { it.rpid != rpid }
        _subReplyState.value = current.copy(
            items = updatedItems.toImmutableList(),
            dissolvingIds = (current.dissolvingIds - rpid).toImmutableSet()
        )
        
        android.util.Log.d("CommentVM", " deleteSubComment: rpid=$rpid, remaining=${updatedItems.size}")
        
        // 发起网络删除请求（使用 rootReply 的 oid）
        val oid = currentSubject.oid.takeIf { it > 0L } ?: return
        viewModelScope.launch {
            CommentRepository.deleteCommentForSubject(
                oid = oid,
                type = currentSubject.type,
                rpid = rpid
            ).onFailure { e ->
                android.util.Log.e("CommentVM", "Delete sub-comment failed for $rpid: ${e.message}")
            }
        }
    }

}
