package com.android.purebilibili.feature.video.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyPage
import com.android.purebilibili.data.repository.CommentRepository
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.data.repository.shouldStartCommentFraudDetection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val isRepliesLoading: Boolean = false,
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
    val likedComments: ImmutableSet<Long> = persistentSetOf(),  // 已点赞的评论 rpid 集合
    val hatedComments: ImmutableSet<Long> = persistentSetOf(),   // 已点踩的评论 rpid 集合
    // [新增] 删除与动画状态
    val dissolvingIds: ImmutableSet<Long> = persistentSetOf(), // 正在播放消散动画的评论 ID
    // [新增] 当前登录用户 Mid (直接暴露以便 UI 判断是否显示删除按钮)
    val currentMid: Long = 0,
    // [新增] 评论输入控制
    val rootInputHint: String = "进来唠会嗑呗~",
    val childInputHint: String = "回复一下吧~",
    val canUploadImage: Boolean = true,
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
    val visible: Boolean = false,
    val rootReply: ReplyItem? = null,
    val items: ImmutableList<ReplyItem> = persistentListOf(),
    val baseItems: ImmutableList<ReplyItem> = persistentListOf(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val page: Int = 1,
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

internal fun resolveSubReplyRemoteTotalCount(
    data: ReplyData,
    rootReply: ReplyItem? = null
): Int {
    // 不同接口会把分页窗口大小也写进 page.count；不能把单页数量当总数。
    // 取所有可用声明中的最大值，避免“显示还有 N 条，详情却在首屏结束”。
    return listOf(
        data.page.count,
        data.root?.rcount ?: 0,
        data.root?.count ?: 0,
        data.cursor.allCount,
        rootReply?.rcount ?: 0,
        rootReply?.count ?: 0,
        data.page.acount
    ).filter { it > 0 }.maxOrNull() ?: 0
}

internal fun resolveSubReplyLoadedTotalCount(
    rootReply: ReplyItem?,
    loadedReplyCount: Int,
    remoteReplyCount: Int,
    previousTotalCount: Int = 0
): Int {
    val rootDeclaredCount = maxOf(
        rootReply?.count ?: 0,
        rootReply?.rcount ?: 0,
        rootReply?.replies.orEmpty().size
    )
    return maxOf(
        previousTotalCount,
        rootDeclaredCount,
        remoteReplyCount,
        loadedReplyCount
    ).coerceAtLeast(0)
}

internal fun resolveSubReplyPageEnd(
    cursorIsEnd: Boolean,
    fetchedReplyCount: Int,
    loadedReplyCount: Int,
    remoteReplyCount: Int,
    requestedPage: Int = 1,
    pageSize: Int = SUB_REPLY_PAGE_SIZE,
    restPage: ReplyPage = ReplyPage()
): Boolean {
    val safeLoadedCount = loadedReplyCount.coerceAtLeast(0)
    val declaredTotal = maxOf(restPage.count, remoteReplyCount).coerceAtLeast(0)
    if (declaredTotal > 0 && safeLoadedCount >= declaredTotal) {
        return true
    }
    // x/v2/reply/reply 的 page.count 可能只是窗口上限；分页进度必须以已解析总数为准。
    if (restPage.count > 0 && restPage.num > 0 && restPage.size > 0) {
        if (restPage.num * restPage.size < declaredTotal) {
            return false
        }
        return fetchedReplyCount <= 0 || safeLoadedCount >= declaredTotal
    }
    if (declaredTotal > safeLoadedCount) {
        // 楼中楼接口可能因审核或折叠导致中间页很稀疏，不能因单页为空提前结束。
        // 最多探测到外层声明总数对应的理论末页，避免异常计数导致无限请求。
        val safePageSize = pageSize.coerceAtLeast(1)
        val expectedLastPage = (declaredTotal + safePageSize - 1) / safePageSize
        return requestedPage.coerceAtLeast(1) >= expectedLastPage
    }
    return cursorIsEnd || fetchedReplyCount <= 0
}

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

class VideoCommentViewModel : ViewModel() {
    private val _commentState = MutableStateFlow(CommentUiState())
    val commentState = _commentState.asStateFlow()

    private val _subReplyState = MutableStateFlow(SubReplyUiState())
    val subReplyState = _subReplyState.asStateFlow()

    // [新增] 评论反诈检测事件流（one-shot event）
    private val _fraudEvent = MutableSharedFlow<CommentFraudStatus>(extraBufferCapacity = 1)
    val fraudEvent = _fraudEvent.asSharedFlow()

    private var currentAid: Long = 0
    private var currentSubject: CommentSubjectKey = CommentSubjectKey(0L)
    
    //  存储原始评论列表（未经筛选），用于筛选切换
    private var allReplies: List<ReplyItem> = emptyList()

    /**
     * 切换视频时立即废弃旧评论主体和未完成请求，等新页面真正打开评论区后再加载。
     */
    fun clearForVideoChange() {
        currentAid = 0L
        currentSubject = CommentSubjectKey(0L)
        allReplies = emptyList()
        _commentState.value = CommentUiState(
            currentMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
        )
        _subReplyState.value = SubReplyUiState()
    }

    // 初始化/重置
    fun init(
        aid: Long,
        upMid: Long = 0,
        preferredSortMode: CommentSortMode = CommentSortMode.HOT,
        expectedReplyCount: Int = 0
    ) {
        android.util.Log.d("CommentVM", " init called with aid=$aid, upMid=$upMid, currentAid=$currentAid")
        if (currentAid == aid && _commentState.value.upMid == upMid) {
            // [修复] 即使视频相同，也刷新 currentMid（防止登录状态变化后不更新）
            refreshCurrentMid()
            if (expectedReplyCount > _commentState.value.replyCount) {
                _commentState.value = _commentState.value.copy(replyCount = expectedReplyCount)
            }
            return
        }
        currentAid = aid
        currentSubject = CommentSubjectKey(oid = aid)
        allReplies = emptyList()
        // 获取当前登录用户 mid
        val myMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
        android.util.Log.d("CommentVM", " init: myMid=$myMid")
        _commentState.value = CommentUiState(
            sortMode = preferredSortMode,
            upMid = upMid,
            currentMid = myMid,
            replyCount = expectedReplyCount.coerceAtLeast(0)
        )
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
        
        allReplies = emptyList()
        _commentState.value = CommentUiState(
            sortMode = mode,
            upMid = currentState.upMid,
            currentMid = currentState.currentMid,
            replyCount = currentState.replyCount
        )
        loadComments()
    }

    fun loadComments() {
        val currentState = _commentState.value
        if (currentState.isRepliesEnd || currentState.isRepliesLoading) return
        val requestSubject = currentSubject
        if (!requestSubject.isValid) return

        _commentState.value = currentState.copy(isRepliesLoading = true, repliesError = null)

        viewModelScope.launch {
            val pageToLoad = currentState.nextPage
            //  使用当前排序模式
            val result = CommentRepository.getComments(
                aid = requestSubject.oid,
                page = pageToLoad, 
                ps = 20,
                mode = currentState.sortMode.apiMode,
                paginationOffset = currentState.grpcNextOffset
            )

            result.onSuccess { data ->
                if (!shouldApplyCommentSubjectResult(requestSubject, currentSubject)) {
                    return@onSuccess
                }
                val current = _commentState.value
                val newReplies = data.replies ?: emptyList()
                val previousRepliesSize = allReplies.size
                
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
                    likedComments = (current.likedComments + combinedReplies.flatMap { root ->
                        (listOf(root) + root.replies.orEmpty()).filter { it.action == 1 }.map { it.rpid }
                    }).toImmutableSet(),
                    hatedComments = (current.hatedComments + combinedReplies.flatMap { root ->
                        (listOf(root) + root.replies.orEmpty()).filter { it.action == 2 }.map { it.rpid }
                    }).toImmutableSet(),
                    replyCount = totalCount,
                    isRepliesLoading = false,
                    repliesError = null,
                    isRepliesEnd = isEnd,
                    nextPage = pageToLoad + 1,
                    rootInputHint = data.control?.rootInputText?.takeIf { it.isNotBlank() } ?: current.rootInputHint,
                    childInputHint = data.control?.childInputText?.takeIf { it.isNotBlank() } ?: current.childInputHint,
                    canUploadImage = data.control?.canUploadPicture ?: current.canUploadImage,
                    canInputComment = data.control?.inputDisable?.not() ?: current.canInputComment,
                    showUpFlag = data.config?.showUpFlag ?: current.showUpFlag,
                    pinnedReplyIds = pinnedReplyIds.toImmutableSet(),
                    grpcNextOffset = data.grpcNextOffset.takeIf { it.isNotBlank() }
                )
            }.onFailure { e ->
                if (!shouldApplyCommentSubjectResult(requestSubject, currentSubject)) {
                    return@onFailure
                }
                android.util.Log.e("CommentVM", " loadComments error: ${e.message}")
                _commentState.value = _commentState.value.copy(
                    isRepliesLoading = false,
                    repliesError = e.message ?: "加载评论失败",
                    isRepliesEnd = true  //  [修复] 出错时也标记为结束，防止无限重试
                )
            }
        }
    }

    // --- 二级评论逻辑 ---

    fun openSubReply(rootReply: ReplyItem, targetReplyId: Long = 0L) {
        val requestSubject = currentSubject
        if (!requestSubject.isValid) return
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

        val routeSubject = currentSubject
        _subReplyState.value = _subReplyState.value.copy(
            visible = false,
            isLoading = true,
            error = null,
            targetReplyId = targetReplyId.takeIf { it != rootReplyId } ?: 0L
        )

        viewModelScope.launch {
            CommentRepository.getSubCommentsForSubject(
                oid = routeSubject.oid,
                type = routeSubject.type,
                rootId = rootReplyId,
                page = 1,
                ps = SUB_REPLY_PAGE_SIZE
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
                val isEnd = resolveSubReplyPageEnd(
                    cursorIsEnd = data.cursor.isEnd,
                    fetchedReplyCount = items.size,
                    loadedReplyCount = items.size,
                    remoteReplyCount = totalCount,
                    requestedPage = 1,
                    restPage = data.page
                )
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
        _subReplyState.update { current ->
            current.copy(
                visible = false,
                conversationAnchor = null,
                isLoading = false,
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
                reloadCommentsFromStart()
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
                    val updatedPreviewReplies = (listOf(newReply) + rootReply.replies.orEmpty())
                        .distinctBy { it.rpid }
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
            val updatedItems = (listOf(newReply) + subState.items).distinctBy { it.rpid }
            _subReplyState.value = subState.copy(
                items = updatedItems.toImmutableList(),
                totalCount = resolveSubReplyLoadedTotalCount(
                    rootReply = activeRootReply,
                    loadedReplyCount = updatedItems.size,
                    remoteReplyCount = subState.totalCount + 1
                )
            )
        }
    }

    fun loadMoreSubReplies() {
        val state = _subReplyState.value
        if (state.isLoading || state.isEnd || state.rootReply == null) return
        val nextPage = state.page + 1
        _subReplyState.value = state.copy(isLoading = true)
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
            error = null,
            grpcNextOffset = null
        )
        loadConversationReplies(anchorReply, page = 1, rootReply = rootReply)
    }

    fun closeSubReplyConversation() {
        val current = _subReplyState.value
        if (current.conversationAnchor == null) return
        _subReplyState.value = current.copy(
            items = current.baseItems,
            page = current.basePage,
            isEnd = current.baseIsEnd,
            grpcNextOffset = current.baseGrpcNextOffset,
            conversationAnchor = null,
            isLoading = false,
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
        viewModelScope.launch {
            val result = CommentRepository.getSubCommentsForSubject(
                oid = subject.oid,
                type = subject.type,
                rootId = rootId,
                page = page,
                ps = SUB_REPLY_PAGE_SIZE,
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
                val isEnd = resolveSubReplyPageEnd(
                    cursorIsEnd = data.cursor.isEnd,
                    fetchedReplyCount = newItems.size,
                    loadedReplyCount = updatedItems.size,
                    remoteReplyCount = totalCount,
                    requestedPage = page,
                    restPage = data.page
                )

                _subReplyState.value = current.copy(
                    items = updatedItems.toImmutableList(),
                    baseItems = updatedItems.toImmutableList(),
                    totalCount = totalCount,
                    isLoading = false,
                    page = page,
                    basePage = page,
                    isEnd = isEnd,
                    baseIsEnd = isEnd,
                    error = null,
                    grpcNextOffset = null,
                    baseGrpcNextOffset = null
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
                    error = it.message
                )
            }
        }
    }

    private fun loadConversationReplies(
        anchorReply: ReplyItem,
        page: Int,
        rootReply: ReplyItem? = _subReplyState.value.rootReply
    ) {
        val root = rootReply ?: return
        val dialogId = resolveConversationDialogId(anchorReply)
        if (dialogId <= 0L) {
            _subReplyState.value = _subReplyState.value.copy(isLoading = false, isEnd = true)
            return
        }
        val subject = currentSubject
        if (!subject.isValid) return
        val rootId = root.rpid
        val paginationOffset = _subReplyState.value.grpcNextOffset
        viewModelScope.launch {
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
                    isEnd = true,
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
        fraudDetectionEnabled: Boolean = true
    ) {
        if (message.isBlank()) return
        val currentState = _commentState.value
        if (currentState.isSending) return
        
        _commentState.value = currentState.copy(isSending = true, sendError = null)
        
        viewModelScope.launch {
            val replyTarget = currentState.replyTarget
            // [修复] 正确计算 root ID
            // 如果是在二级评论页回复，root 为当前二级评论页的根评论 ID
            // 如果是一级评论页回复某评论，root 为该评论 ID
            // 如果是直接发表评论，root 为 0
            val subReplyState = _subReplyState.value
            val isSubReplyContext = subReplyState.visible && subReplyState.rootReply != null
            
            val root = if (isSubReplyContext) {
                subReplyState.rootReply.rpid
            } else {
                replyTarget?.rpid ?: 0
            }
            // parent 总是回复目标的 ID (如果没有回复目标，则是 0)
            val parent = replyTarget?.rpid ?: 0
            
            val result = CommentRepository.addComment(currentAid, message, root, parent)
            
            result.onSuccess { newReply ->
                android.util.Log.d("CommentVM", " sendComment success: newReply=${newReply?.rpid}, root=$root, parent=$parent")
                val current = _commentState.value

                // [新增] 启动评论反诈检测（后台协程，不阻塞 UI）
                val rpidToCheck = newReply?.rpid ?: 0L
                if (rpidToCheck > 0L) {
                    // 💾 发评成功瞬间立即在本地数据库记一笔
                    viewModelScope.launch {
                        com.android.purebilibili.data.repository.CommentFraudRepository.saveRecord(
                            rpid = rpidToCheck,
                            oid = currentAid,
                            type = 1,
                            root = root,
                            message = message,
                            status = com.android.purebilibili.data.model.CommentFraudStatus.NORMAL
                        )
                    }
                    
                    val sentAtSeconds = newReply?.ctime?.takeIf { it > 0L } ?: (System.currentTimeMillis() / 1000L)
                    launchFraudDetection(
                        aid = currentAid,
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
                            replyTarget = null,
                            replyCount = current.replyCount + 1
                        )
                    } else {
                        // [修复] newReply 为 null 时，重新加载评论列表以显示新评论
                        android.util.Log.d("CommentVM", " sendComment: newReply is null, reloading comments...")
                        _commentState.value = current.copy(
                            isSending = false,
                            sendError = null,
                            replyTarget = null
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
                    if (newReply != null) {
                        // 有返回评论对象，直接添加到列表顶部
                        val currentSub = _subReplyState.value
                        _subReplyState.value = currentSub.copy(
                            items = (listOf(newReply) + currentSub.items).toImmutableList(),
                        )
                    } else {
                        // [修复] newReply 为 null 时，重新加载二级评论列表
                        android.util.Log.d("CommentVM", " sendComment: sub-reply newReply is null, reloading...")
                        val currentSub = _subReplyState.value
                        currentSub.rootReply?.let { root ->
                            _subReplyState.value = currentSub.copy(
                                items = emptyList<ReplyItem>().toImmutableList(),
                                page = 1,
                                isEnd = false,
                                isLoading = true,
                                grpcNextOffset = null
                            )
                            loadSubReplies(
                                subject = currentSubject,
                                rootId = root.rpid,
                                page = 1,
                                paginationOffset = null
                            )
                        }
                    }
                    
                    _commentState.value = current.copy(
                        isSending = false,
                        sendError = null,
                        replyTarget = null
                    )
                }
                // 3. 一级列表回复 (展开二级或直接回复)
                else {
                    _commentState.value = current.copy(
                        isSending = false,
                        sendError = null,
                        replyTarget = null,
                        replyCount = current.replyCount + 1
                    )
                }
            }.onFailure { e ->
                android.util.Log.e("CommentVM", " sendComment failed: ${e.message}")
                _commentState.value = _commentState.value.copy(isSending = false, sendError = e.message)
            }
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
            CommentRepository.likeComment(currentAid, rpid, !isCurrentlyLiked).onFailure {
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
            CommentRepository.hateComment(currentAid, rpid, !isCurrentlyHated).onFailure {
                _commentState.value = _commentState.value.copy(likedComments = currentState.likedComments, hatedComments = currentState.hatedComments)
            }
        }
    }
    

    
    fun reportComment(rpid: Long, reason: Int, content: String = "") {
        viewModelScope.launch { CommentRepository.reportComment(currentAid, rpid, reason, content) }
    }

    fun toggleTopComment(reply: ReplyItem) {
        if (currentAid <= 0L || reply.rpid <= 0L) return
        val current = _commentState.value
        val isCurrentlyTop = reply.rpid in current.pinnedReplyIds || reply.replyControl?.isUpTop == true
        viewModelScope.launch {
            CommentRepository.setCommentTop(
                aid = currentAid,
                rpid = reply.rpid,
                isCurrentlyTop = isCurrentlyTop
            ).onSuccess {
                reloadCommentsFromStart()
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
        sentAtSeconds: Long = 0
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
                sentAtSeconds = sentAtSeconds
            )
            result.onSuccess { status ->
                android.util.Log.d("CommentVM", "评论反诈检测结果: $status (rpid=$rpid)")
                // [存储初检] 初检完成，同步将真实状态写入 initial_status 与 status
                CommentFraudRepository.saveRecord(
                    rpid = rpid,
                    oid = aid,
                    type = 1,
                    root = rootId,
                    message = message,
                    status = status,
                    initialStatus = status // 该评论发布 T+5s 的“初始出生状态”！
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
            CommentRepository.deleteComment(currentAid, rpid).onFailure { e ->
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
            CommentRepository.deleteComment(oid, rpid).onFailure { e ->
                android.util.Log.e("CommentVM", "Delete sub-comment failed for $rpid: ${e.message}")
            }
        }
    }

    private fun reloadCommentsFromStart() {
        allReplies = emptyList()
        _commentState.value = _commentState.value.copy(
            replies = emptyList<ReplyItem>().toImmutableList(),
            nextPage = 1,
            isRepliesEnd = false,
            isRepliesLoading = false,
            repliesError = null
        )
        loadComments()
    }
}
