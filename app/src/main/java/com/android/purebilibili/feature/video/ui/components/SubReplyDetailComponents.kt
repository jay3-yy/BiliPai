package com.android.purebilibili.feature.video.ui.components

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AdaptivePullToRefreshBox
import com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.components.ImagePreviewSourceAnchor
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.SubReplySortMode
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.feature.video.viewmodel.resolveVideoCommentSkeletonSlotCount
import kotlinx.coroutines.delay

const val SUB_REPLY_DETAIL_HEADER_TAG = "subreply_detail_header"
const val SUB_REPLY_DETAIL_CLOSE_TAG = "subreply_detail_close"
const val SUB_REPLY_DETAIL_ROOT_TAG = "subreply_detail_root"
const val SUB_REPLY_DETAIL_LIST_TAG = "subreply_detail_reply_list"
const val SUB_REPLY_DETAIL_SECTION_TAG = "subreply_detail_section"
const val SUB_REPLY_DETAIL_SORT_TAG = "subreply_detail_sort"
const val SUB_REPLY_DETAIL_CONVERSATION_TAG_PREFIX = "subreply_detail_conversation_"
const val SUB_REPLY_DETAIL_ITEM_TAG_PREFIX = "subreply_detail_item_"

const val SUB_REPLY_DETAIL_PARTIAL_TAG = "subreply_detail_partial"

internal data class SubReplyDetailLayoutPolicy(
    val listBottomPaddingDp: Int,
    val headerVerticalPaddingDp: Int,
    val sectionVerticalPaddingDp: Int,
    val sectionDividerThicknessDp: Int,
)


internal data class SubReplyDetailListScrollResetKey(
    val rootReplyId: Long,
    val conversationMode: Boolean,
    val firstConversationReplyId: Long?
)

internal data class SubReplyDetailSavedScrollPosition(
    val index: Int,
    val scrollOffset: Int,
)

internal enum class SubReplyDetailScrollRestoreAction {
    KEEP_POSITION,
    SCROLL_TO_TOP,
    RESTORE_SAVED,
}

internal fun resolveSubReplyDetailScrollRestoreAction(
    previousConversationMode: Boolean?,
    currentConversationMode: Boolean,
    hasSavedPosition: Boolean,
    sortChanged: Boolean = false,
): SubReplyDetailScrollRestoreAction {
    if (sortChanged) return SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP
    if (previousConversationMode == null || previousConversationMode == currentConversationMode) {
        return SubReplyDetailScrollRestoreAction.KEEP_POSITION
    }
    if (previousConversationMode == true && !currentConversationMode && hasSavedPosition) {
        return SubReplyDetailScrollRestoreAction.RESTORE_SAVED
    }
    return SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP
}

internal fun shouldSaveSubReplyDetailScrollBeforeConversationEnter(
    previousConversationMode: Boolean?,
    currentConversationMode: Boolean,
): Boolean {
    return previousConversationMode == false && currentConversationMode
}

internal fun resolveSubReplyDetailLayoutPolicy(): SubReplyDetailLayoutPolicy {
    return SubReplyDetailLayoutPolicy(
        listBottomPaddingDp = 8,
        headerVerticalPaddingDp = 0,
        sectionVerticalPaddingDp = 0,
        sectionDividerThicknessDp = 6,
    )
}


internal fun resolveSubReplyDetailSectionTitle(
    replyCount: Int,
    loadedReplyCount: Int = replyCount,
    showLoadedReplyCount: Boolean = false,
): String {
    val total = replyCount.coerceAtLeast(0)
    val loaded = loadedReplyCount.coerceAtLeast(0)
    return if (showLoadedReplyCount && total > loaded) {
        "相关回复共${total}条（已加载${loaded}条）"
    } else {
        "相关回复共${total}条"
    }
}

internal fun resolveLazyListCanScrollForward(
    lastVisibleIndex: Int,
    lastVisibleEndOffset: Int,
    totalItemsCount: Int,
    viewportEndOffset: Int
): Boolean {
    if (totalItemsCount <= 0 || lastVisibleIndex < 0) return false
    if (lastVisibleIndex < totalItemsCount - 1) return true
    return lastVisibleEndOffset > viewportEndOffset
}

internal fun shouldLoadMoreSubReplyList(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    isLoading: Boolean,
    isEnd: Boolean,
    prefetchThreshold: Int = 2
): Boolean {
    if (isLoading || isEnd || totalItemsCount <= 0 || lastVisibleIndex < 0) return false
    val triggerIndex = (totalItemsCount - 1 - prefetchThreshold).coerceAtLeast(0)
    return lastVisibleIndex >= triggerIndex
}

internal fun shouldPrefetchSubRepliesWhenListNotScrollable(
    isLoading: Boolean,
    isEnd: Boolean,
    canScrollForward: Boolean
): Boolean {
    if (isLoading || isEnd) return false
    return !canScrollForward
}

internal fun shouldShowSubReplyManualLoadMore(
    isLoading: Boolean,
    isEnd: Boolean
): Boolean {
    if (isLoading || isEnd) return false
    return true
}

internal fun resolveSubReplyDetailDisplayCount(
    rootReply: ReplyItem?,
    loadedReplyCount: Int,
    remoteReplyCount: Int = 0
): Int {
    return maxOf(
        rootReply?.let(::resolveReplyThreadCount) ?: 0,
        remoteReplyCount,
        loadedReplyCount
    ).coerceAtLeast(0)
}

internal fun resolveSubReplyConversationSectionTitle(replyCount: Int): String {
    return "对话共${replyCount.coerceAtLeast(0)}条"
}


internal fun shouldShowSubReplyConversationAction(item: ReplyItem): Boolean {
    return item.root > 0L && item.dialog > 0L && item.parent != item.root
}

internal fun shouldRenderSubReplyConversationAction(
    item: ReplyItem,
    hasConversationHandler: Boolean
): Boolean {
    return hasConversationHandler && shouldShowSubReplyConversationAction(item)
}

internal fun resolveSubReplyConversationItems(
    anchorReply: ReplyItem,
    subReplies: List<ReplyItem>
): List<ReplyItem> {
    val dialogId = anchorReply.dialog
    val parentId = anchorReply.parent
    val anchorId = anchorReply.rpid
    val filtered = subReplies.filter { candidate ->
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
    }
    return filtered.ifEmpty { listOf(anchorReply) }.distinctBy { it.rpid }
}

internal fun resolveSubReplyDetailListScrollResetKey(
    rootReplyId: Long,
    effectiveConversationMode: Boolean,
    visibleReplies: List<ReplyItem>
): SubReplyDetailListScrollResetKey {
    return SubReplyDetailListScrollResetKey(
        rootReplyId = rootReplyId,
        conversationMode = effectiveConversationMode,
        firstConversationReplyId = if (effectiveConversationMode) {
            visibleReplies.firstOrNull()?.rpid
        } else {
            null
        }
    )
}

internal fun resolveSubReplyTargetListIndex(
    rootReplyId: Long,
    visibleReplies: List<ReplyItem>,
    targetReplyId: Long
): Int? {
    if (targetReplyId <= 0L) return null
    if (targetReplyId == rootReplyId) return 0
    val replyIndex = visibleReplies.indexOfFirst { it.rpid == targetReplyId }
    return replyIndex.takeIf { it >= 0 }?.plus(1)
}


@Composable
internal fun SubReplyInitialContent(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = rememberVideoCommentAppearance()
    Column(modifier.fillMaxSize().background(appearance.panelColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = "评论详情",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            AppIconButton(onClick = onDismiss, modifier = Modifier.testTag(SUB_REPLY_DETAIL_CLOSE_TAG)) {
                AppIcon(Icons.Outlined.Close, contentDescription = "关闭评论详情")
            }
        }
        AppHorizontalDivider(thickness = 0.5.dp, color = appearance.dividerColor)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (isLoading) {
                AdaptiveLoadingIndicator()
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = error ?: "回复加载失败",
                        textAlign = TextAlign.Center,
                        color = appearance.secondaryTextColor,
                    )
                    AppTextButton(onClick = onRetry) { AppText("重试") }
                }
            }
        }
    }
}

@Composable
internal fun VideoInlineSubReplyDetailContent(
    state: SubReplyUiState,
    commentState: CommentUiState,
    emoteMap: Map<String, String>,
    maxTimestampMs: Long?,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (SubReplySortMode) -> Unit,
    onDismiss: () -> Unit,
    onRootCommentClick: () -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    onImagePreview: ((List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit)?,
    onReplyClick: (ReplyItem) -> Unit,
    onConversationClick: (ReplyItem) -> Unit,
    onConversationBack: () -> Unit,
    onDissolveStart: (Long) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onCheckCommentFraud: (ReplyItem) -> Unit = {},
    onCommentLike: (Long) -> Unit,
    onCommentHate: (Long) -> Unit,
    onReportComment: (Long, Int) -> Unit,
    onUrlClick: (String) -> Unit,
    showIdentityDecorations: Boolean,
    onAvatarClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootReply = state.rootReply
    if (!state.visible) return
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = true) {
        onDismiss()
    }
    if (rootReply == null && !state.isTargetOnly) {
        SubReplyInitialContent(
            isLoading = state.isLoading,
            error = state.error,
            onRetry = onRefresh,
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    // Inline hosts replace the main list directly, unlike window sheets and the
    // portrait overlay host. Give this panel its own full-height bottom-up entry.
    val motion = com.android.purebilibili.core.ui.rememberAppBottomSheetMotion()
    val motionEnabled = !motion.reducedMotion &&
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.uiEntranceAnimationEnabled
    val entranceProgress = remember(state.threadRootId) {
        Animatable(if (motionEnabled) 1f else 0f)
    }
    LaunchedEffect(state.threadRootId, motionEnabled) {
        if (motionEnabled) {
            entranceProgress.animateTo(0f, animationSpec = motion.dragSettleSpec)
        } else {
            entranceProgress.snapTo(0f)
        }
    }


    SubReplyDetailContent(
        rootReply = rootReply,
        isTargetOnly = state.isTargetOnly,
        subReplies = state.items,
        sortMode = state.sortMode,
        error = state.error,
        onSortModeChange = onSortModeChange,
        remoteReplyCount = state.totalCount,
        isLoading = state.isLoading,
        isRefreshing = state.isRefreshing,
        isEnd = state.isEnd,
        paginationPage = state.page,
        emoteMap = emoteMap,
        onLoadMore = onLoadMore,
        onRefresh = onRefresh,
        onDismiss = onDismiss,
        applyStatusBarPadding = false,
        onRootCommentClick = onRootCommentClick,
        onTimestampClick = onTimestampClick,
        upMid = state.upMid.takeIf { it > 0L } ?: commentState.upMid,
        showUpFlag = commentState.showUpFlag,
        onImagePreview = onImagePreview,
        onReplyClick = onReplyClick,
        onConversationClick = onConversationClick,
        onConversationBack = onConversationBack,
        isConversationMode = state.conversationAnchor != null,
        conversationAnchorId = state.conversationAnchor?.rpid ?: 0L,
        dissolvingIds = state.dissolvingIds,
        currentMid = commentState.currentMid,
        onDissolveStart = onDissolveStart,
        onDeleteComment = onDeleteComment,
        onCheckCommentFraud = onCheckCommentFraud,
        onCommentLike = onCommentLike,
        onCommentHate = onCommentHate,
        onReportComment = onReportComment,
        likedComments = commentState.likedComments,
        hatedComments = commentState.hatedComments,
        onUrlClick = onUrlClick,
        showIdentityDecorations = showIdentityDecorations,
        onAvatarClick = onAvatarClick,
        maxTimestampMs = maxTimestampMs,
        targetReplyId = state.targetReplyId,
        modifier = modifier.graphicsLayer {
            translationY = size.height * entranceProgress.value.coerceIn(0f, 1f)
        },
    )
}

private data class SubReplyDetailPage(
    val key: Triple<Long, Boolean, Long>,
    val rootReply: ReplyItem?,
    val replies: List<ReplyItem>,
    val conversationMode: Boolean,
    val listState: LazyListState,
    val isRefreshing: Boolean,
    val isLoading: Boolean,
    val isEnd: Boolean,
    val isTargetOnly: Boolean,
    val error: String?,
    val showManualLoadMore: Boolean,
    val replyDisplayCount: Int,
    val sortMode: SubReplySortMode,
)

@Composable
internal fun SubReplyDetailContent(
    rootReply: ReplyItem?,
    subReplies: List<ReplyItem>,
    sortMode: SubReplySortMode,
    error: String?,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    isEnd: Boolean,
    emoteMap: Map<String, String>,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onSortModeChange: (SubReplySortMode) -> Unit,
    onDismiss: () -> Unit,
    applyStatusBarPadding: Boolean = false,
    onRootCommentClick: (() -> Unit)? = null,
    onTimestampClick: ((Long) -> Unit)? = null,
    upMid: Long = 0,
    showUpFlag: Boolean = false,
    onImagePreview: ((List<String>, Int, ImagePreviewSourceAnchor?, ImagePreviewTextContent?) -> Unit)? = null,
    onReplyClick: ((ReplyItem) -> Unit)? = null,
    onConversationClick: ((ReplyItem) -> Unit)? = null,
    onConversationBack: (() -> Unit)? = null,
    isConversationMode: Boolean = false,
    conversationAnchorId: Long = 0L,
    dissolvingIds: Set<Long> = emptySet(),
    currentMid: Long = 0,
    onDissolveStart: ((Long) -> Unit)? = null,
    onDeleteComment: ((Long) -> Unit)? = null,
    onCheckCommentFraud: ((ReplyItem) -> Unit)? = null,
    onCommentLike: ((Long) -> Unit)? = null,
    onCommentHate: ((Long) -> Unit)? = null,
    onReportComment: ((Long, Int) -> Unit)? = null,
    likedComments: Set<Long> = emptySet(),
    hatedComments: Set<Long> = emptySet(),
    onUrlClick: ((String) -> Unit)? = null,
    showIdentityDecorations: Boolean = true,
    onAvatarClick: ((String) -> Unit)? = null,
    maxTimestampMs: Long? = null,
    remoteReplyCount: Int = 0,
    targetReplyId: Long = 0,
    headerDragModifier: Modifier = Modifier,
    // 嵌入楼中楼的列表先还原播放器，再将剩余下拉交给刷新和拖拽关闭；窗口弹层保持默认。
    listModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    paginationPage: Int = 1,
    isTargetOnly: Boolean = false,
) {
    val layoutPolicy = remember {
        resolveSubReplyDetailLayoutPolicy()
    }
    val appearance = rememberVideoCommentAppearance()
    // 首屏骨架按视口高度铺够槽位：少铺会让列表在数据到达时被撑长、整体跳一下。
    val commentSkeletonSlotCount = resolveVideoCommentSkeletonSlotCount(
        LocalConfiguration.current.screenHeightDp
    )
    val context = LocalContext.current
    val threadRootId = rootReply?.rpid?.takeIf { it > 0L }
        ?: subReplies.firstOrNull { it.root > 0L }?.root ?: 0L
    val showLoadedReplyCount by com.android.purebilibili.core.store.SettingsManager
        .getSubReplyLoadedCountEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val avatarPushEnabled = rememberCommentAvatarPushEnabled()
    var wasRefreshing by remember(threadRootId) { mutableStateOf(false) }
    LaunchedEffect(isRefreshing, error) {
        if (wasRefreshing && !isRefreshing && error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
        wasRefreshing = isRefreshing
    }
    var highlightedTargetId by remember(threadRootId) { mutableLongStateOf(0L) }
    var targetReplyHandled by rememberSaveable(threadRootId, targetReplyId) { mutableStateOf(false) }
    var targetReplyMissing by rememberSaveable(threadRootId, targetReplyId) { mutableStateOf(false) }
    LaunchedEffect(highlightedTargetId) {
        if (highlightedTargetId <= 0L) return@LaunchedEffect
        delay(1_400)
        highlightedTargetId = 0L
    }
    var conversationAnchor by remember(threadRootId) { mutableStateOf<ReplyItem?>(null) }
    // 与 LazyListState 一起恢复，避免重建后用首次进场逻辑覆盖已保存的位置。
    var previousConversationMode by rememberSaveable(threadRootId) { mutableStateOf<Boolean?>(null) }
    var previousSortMode by rememberSaveable(threadRootId) { mutableIntStateOf(sortMode.apiMode) }
    var savedListIndex by rememberSaveable(threadRootId) { mutableIntStateOf(-1) }
    var savedListOffset by rememberSaveable(threadRootId) { mutableIntStateOf(0) }
    val savedListScroll = if (savedListIndex >= 0) {
        SubReplyDetailSavedScrollPosition(savedListIndex, savedListOffset)
    } else null
    val visibleReplies = remember(subReplies, conversationAnchor, isConversationMode) {
        val anchor = conversationAnchor
        if (anchor == null || isConversationMode) {
            subReplies
        } else {
            resolveSubReplyConversationItems(
                anchorReply = anchor,
                subReplies = subReplies
            )
        }
    }
    val localConversationMode = conversationAnchor != null
    val effectiveConversationMode = isConversationMode || localConversationMode
    val activeConversationId = conversationAnchor?.rpid ?: conversationAnchorId
    val allRepliesListState = key(threadRootId) { rememberLazyListState() }
    val conversationListState = key(threadRootId, activeConversationId) { rememberLazyListState() }
    // Keep separate states: the outgoing list must not jump when the next level resets.
    val listState = if (effectiveConversationMode) conversationListState else allRepliesListState
    val detailReplyDisplayCount = remember(rootReply, subReplies.size, remoteReplyCount) {
        resolveSubReplyDetailDisplayCount(
            rootReply = rootReply,
            loadedReplyCount = subReplies.size,
            remoteReplyCount = remoteReplyCount
        )
    }
    val listScrollResetKey = remember(
        threadRootId,
        effectiveConversationMode,
        visibleReplies.firstOrNull()?.rpid
    ) {
        resolveSubReplyDetailListScrollResetKey(
            rootReplyId = threadRootId,
            effectiveConversationMode = effectiveConversationMode,
            visibleReplies = visibleReplies
        )
    }
    val captureListScrollForConversation: () -> Unit = {
        savedListIndex = allRepliesListState.firstVisibleItemIndex
        savedListOffset = allRepliesListState.firstVisibleItemScrollOffset
    }
    val canScrollForwardState = remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            resolveLazyListCanScrollForward(
                lastVisibleIndex = lastVisibleItem?.index ?: -1,
                lastVisibleEndOffset = (lastVisibleItem?.offset ?: 0) + (lastVisibleItem?.size ?: 0),
                totalItemsCount = layoutInfo.totalItemsCount,
                viewportEndOffset = layoutInfo.viewportEndOffset
            )
        }
    }
    val shouldLoadMore by remember(listState, isLoading, isEnd, error, localConversationMode, isTargetOnly, effectiveConversationMode) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            error == null && !localConversationMode && (!isTargetOnly || isConversationMode) &&
                shouldLoadMoreSubReplyList(
                    lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                    totalItemsCount = layoutInfo.totalItemsCount,
                    isLoading = isLoading,
                    isEnd = isEnd
                )
        }
    }
    val shouldPrefetchShortList by remember(
        canScrollForwardState, isLoading, isEnd, error, localConversationMode, visibleReplies.size, detailReplyDisplayCount, isTargetOnly, isConversationMode
    ) {
        derivedStateOf {
            error == null && !localConversationMode && (!isTargetOnly || isConversationMode) &&
                shouldPrefetchSubRepliesWhenListNotScrollable(
                    isLoading = isLoading,
                    isEnd = isEnd,
                    canScrollForward = canScrollForwardState.value
                )
        }
    }
    val showManualLoadMore = remember(
        visibleReplies.size,
        detailReplyDisplayCount,
        isLoading,
        isEnd,
        localConversationMode, isTargetOnly, isConversationMode
    ) {
        !localConversationMode && (!isTargetOnly || isConversationMode) &&
            shouldShowSubReplyManualLoadMore(
                isLoading = isLoading,
                isEnd = isEnd
            )
    }
    LaunchedEffect(shouldLoadMore, paginationPage) {
        if (shouldLoadMore) onLoadMore()
    }
    LaunchedEffect(shouldPrefetchShortList, visibleReplies.size, detailReplyDisplayCount, paginationPage) {
        if (shouldPrefetchShortList) onLoadMore()
    }
    LaunchedEffect(threadRootId, effectiveConversationMode, activeConversationId, sortMode) {
        val previousMode = previousConversationMode
        val currentMode = listScrollResetKey.conversationMode
        when (
            resolveSubReplyDetailScrollRestoreAction(
                previousConversationMode = previousMode,
                currentConversationMode = currentMode,
                hasSavedPosition = savedListScroll != null,
                sortChanged = previousSortMode != sortMode.apiMode,
            )
        ) {
            SubReplyDetailScrollRestoreAction.KEEP_POSITION -> Unit
            SubReplyDetailScrollRestoreAction.RESTORE_SAVED -> {
                val saved = savedListScroll
                if (saved != null) {
                    listState.scrollToItem(
                        index = saved.index,
                        scrollOffset = saved.scrollOffset,
                    )
                } else {
                    listState.scrollToItem(0)
                }
            }
            SubReplyDetailScrollRestoreAction.SCROLL_TO_TOP -> {
                if (
                    shouldSaveSubReplyDetailScrollBeforeConversationEnter(
                        previousConversationMode = previousMode,
                        currentConversationMode = currentMode,
                    ) && savedListScroll == null
                ) {
                    captureListScrollForConversation()
                }
                listState.scrollToItem(0)
            }
        }
        previousConversationMode = currentMode
        previousSortMode = sortMode.apiMode
    }
    LaunchedEffect(threadRootId, targetReplyId, visibleReplies, isLoading, isEnd, effectiveConversationMode, error, paginationPage) {
        if (targetReplyId <= 0L) {
            highlightedTargetId = 0L
            return@LaunchedEffect
        }
        // This is an entry-time navigation request, not a persistent scroll anchor.
        // Loading/refreshing changes the effect keys but must not replay the jump.
        if (targetReplyHandled || effectiveConversationMode) return@LaunchedEffect
        val targetIndex = resolveSubReplyTargetListIndex(
            rootReplyId = threadRootId,
            visibleReplies = visibleReplies,
            targetReplyId = targetReplyId
        )
        when {
            targetIndex != null -> {
                // Commit the entry jump without a long animation that a page update
                // could cancel after the navigation request has already been consumed.
                listState.scrollToItem(targetIndex)
                targetReplyHandled = true
                highlightedTargetId = targetReplyId
            }
            error == null && !isLoading && !isEnd && !isTargetOnly -> onLoadMore()
            error == null && !isLoading && isEnd && !isTargetOnly -> {
                targetReplyHandled = true
                targetReplyMissing = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appearance.panelColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(
                    start = 16.dp,
                    end = 8.dp,
                    top = layoutPolicy.headerVerticalPaddingDp.dp,
                    bottom = layoutPolicy.headerVerticalPaddingDp.dp
                )
                .then(headerDragModifier)
                .testTag(SUB_REPLY_DETAIL_HEADER_TAG)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CommentCascadingContent(
                        targetState = effectiveConversationMode,
                        contentKey = { it },
                        isDeeper = { it },
                    ) { conversationMode ->
                        AppText(
                            text = if (conversationMode) "对话详情" else "评论详情",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = appearance.primaryTextColor,
                        )
                    }
                    if (isTargetOnly) {
                        AppText(
                            text = "部分结果",
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag(SUB_REPLY_DETAIL_PARTIAL_TAG)
                                .clearAndSetSemantics {
                                    contentDescription = if (effectiveConversationMode) {
                                        "完整楼层暂不可用，当前显示目标回复的可读取对话"
                                    } else {
                                        "完整楼层暂不可用，当前仅显示可读取的目标回复；下拉刷新可重试完整楼层"
                                    }
                                },
                            color = appearance.secondaryTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AppIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(SUB_REPLY_DETAIL_CLOSE_TAG)
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = appearance.primaryTextColor
                    )
                }
            }
        }
        AppHorizontalDivider(thickness = 0.5.dp, color = appearance.dividerColor)
        if (targetReplyMissing && !effectiveConversationMode) {
            AppText(
                text = "未找到目标回复，可能已删除或不可见",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = appearance.secondaryTextColor,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        CommentCascadingContent(
            targetState = SubReplyDetailPage(
                key = Triple(threadRootId, effectiveConversationMode, activeConversationId),
                rootReply = rootReply,
                replies = visibleReplies,
                conversationMode = effectiveConversationMode,
                listState = listState,
                isRefreshing = isRefreshing,
                isLoading = isLoading,
                isEnd = isEnd || localConversationMode,
                isTargetOnly = isTargetOnly,
                error = error,
                showManualLoadMore = showManualLoadMore,
                replyDisplayCount = detailReplyDisplayCount,
                sortMode = sortMode,
            ),
            contentKey = { it.key },
            isDeeper = { it.conversationMode },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            val pageActive = page.key == Triple(threadRootId, effectiveConversationMode, activeConversationId)
            val rootReply = page.rootReply
            val visibleReplies = page.replies
            val effectiveConversationMode = page.conversationMode
            val listState = page.listState
            val isRefreshing = page.isRefreshing
            val isLoading = page.isLoading
            val isEnd = page.isEnd
            val isTargetOnly = page.isTargetOnly
            val error = page.error
            val showManualLoadMore = page.showManualLoadMore
            val detailReplyDisplayCount = page.replyDisplayCount
            val sortMode = page.sortMode
            AdaptivePullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = listModifier
                        .fillMaxSize()
                        .testTag(SUB_REPLY_DETAIL_LIST_TAG),
                    contentPadding = PaddingValues(bottom = layoutPolicy.listBottomPaddingDp.dp)
                ) {
                    item(key = "root_reply") {
                        if (rootReply != null) {
                        // The page transition owns reveal; avoid resizing the lazy-list anchor again.
                        Box(modifier = Modifier.testTag(SUB_REPLY_DETAIL_ROOT_TAG)) {
                            ReplyItemView(
                                avatarListState = listState.takeIf { avatarPushEnabled },
                                avatarItemKey = "root_reply",
                                item = rootReply,
                                hideSubPreview = true,
                                boundAvatarToItem = true,
                                showIdentityDecorations = showIdentityDecorations,
                                onClick = {},
                                onSubClick = { _, _ -> onReplyClick?.invoke(rootReply) },
                                upMid = upMid,
                                emoteMap = emoteMap,
                                showUpFlag = showUpFlag,
                                onTimestampClick = onTimestampClick,
                                onImagePreview = onImagePreview,
                                onReplyClick = { onReplyClick?.invoke(rootReply) },
                                onDeleteClick = onDeleteComment?.takeIf {
                                    currentMid > 0 && rootReply.mid == currentMid
                                }?.let { delete -> { delete(rootReply.rpid) } },
                                onCheckFraudClick = if (onCheckCommentFraud != null && currentMid > 0 && rootReply.mid == currentMid) {
                                    { onCheckCommentFraud.invoke(rootReply) }
                                } else null,
                                onLikeClick = { onCommentLike?.invoke(rootReply.rpid) },
                                onHateClick = { onCommentHate?.invoke(rootReply.rpid) },
                                isLiked = rootReply.rpid in likedComments,
                                isHated = rootReply.rpid in hatedComments,
                                onUrlClick = onUrlClick,
                                maxTimestampMs = maxTimestampMs,
                                onReportClick = onReportComment?.let { report -> { reason -> report(rootReply.rpid, reason) } },
                                onAvatarClick = { onAvatarClick?.invoke(it) ?: Unit },
                            )
                        }
                        }
                        Column {
                            AppHorizontalDivider(
                                thickness = layoutPolicy.sectionDividerThicknessDp.dp,
                                color = appearance.sectionDividerColor
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = layoutPolicy.sectionVerticalPaddingDp.dp)
                                    .testTag(SUB_REPLY_DETAIL_SECTION_TAG),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppText(
                                    text = if (isTargetOnly) {
                                        "可读取的回复"
                                    } else if (effectiveConversationMode) {
                                        resolveSubReplyConversationSectionTitle(replyCount = visibleReplies.size)
                                    } else {
                                        resolveSubReplyDetailSectionTitle(
                                            replyCount = detailReplyDisplayCount,
                                            loadedReplyCount = visibleReplies.size,
                                            showLoadedReplyCount = showLoadedReplyCount,
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = appearance.primaryTextColor
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (effectiveConversationMode) {
                                    AppText(
                                        text = if (isTargetOnly) "返回目标回复" else "返回全部回复",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = appearance.sortTint,
                                        modifier = Modifier
                                            .clickable {
                                                if (isConversationMode) {
                                                    onConversationBack?.invoke()
                                                } else {
                                                    conversationAnchor = null
                                                }
                                            }
                                            .sizeIn(minHeight = 48.dp)
                                            .wrapContentHeight()
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                } else if (!isTargetOnly) {
                                    Row(
                                        modifier = Modifier
                                            .testTag(SUB_REPLY_DETAIL_SORT_TAG)
                                            .clickable(enabled = !isLoading) { onSortModeChange(sortMode.toggled()) }
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                                            contentDescription = "切换楼中楼排序",
                                            tint = appearance.sortTint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        AppText(
                                            text = sortMode.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = appearance.sortTint
                                        )
                                    }
                                }
                            }
                            AppHorizontalDivider(
                                thickness = 0.5.dp,
                                color = appearance.dividerColor
                            )
                        }
                    }

                    itemsIndexed(
                        items = visibleReplies,
                        key = { _, item -> item.rpid }
                    ) { _, item ->
                        MaybeDissolvableVideoCard(
                            isDissolving = pageActive && item.rpid in dissolvingIds,
                            onDissolveComplete = { if (pageActive) onDeleteComment?.invoke(item.rpid) },
                            cardId = "subreply_detail_${item.rpid}",
                            // 小间距防止裁剪；animateItem 放在最前，作用于整个槽位。
                            modifier = videoCommentItemModifier().padding(bottom = 1.dp)
                        ) {
                            val backgroundColor by animateColorAsState(
                                targetValue = if (item.rpid == highlightedTargetId) {
                                    appearance.accentColor.copy(alpha = 0.14f)
                                } else {
                                    appearance.panelColor
                                },
                                animationSpec = tween(durationMillis = 280),
                                label = "subReplyTargetHighlight",
                            )
                            ReplyItemView(
                                modifier = Modifier.testTag("$SUB_REPLY_DETAIL_ITEM_TAG_PREFIX${item.rpid}"),
                                avatarListState = listState.takeIf { avatarPushEnabled },
                                item = item,
                                containerColor = backgroundColor,
                                hideSubPreview = true,
                                showIdentityDecorations = showIdentityDecorations,
                                onClick = {},
                                onSubClick = { _, _ -> onReplyClick?.invoke(item) },
                                upMid = upMid,
                                emoteMap = emoteMap,
                                showUpFlag = showUpFlag,
                                onTimestampClick = onTimestampClick,
                                onImagePreview = onImagePreview,
                                onReplyClick = { onReplyClick?.invoke(item) },
                                onDeleteClick = (onDissolveStart ?: onDeleteComment)?.takeIf {
                                    currentMid > 0 && item.mid == currentMid
                                }?.let { delete -> { delete(item.rpid) } },
                                onCheckFraudClick = if (onCheckCommentFraud != null && currentMid > 0 && item.mid == currentMid) {
                                    { onCheckCommentFraud.invoke(item) }
                                } else null,
                                onLikeClick = { onCommentLike?.invoke(item.rpid) },
                                onHateClick = { onCommentHate?.invoke(item.rpid) },
                                isLiked = item.rpid in likedComments,
                                isHated = item.rpid in hatedComments,
                                onUrlClick = onUrlClick,
                                maxTimestampMs = maxTimestampMs,
                                onReportClick = onReportComment?.let { report -> { reason -> report(item.rpid, reason) } },
                                onAvatarClick = { onAvatarClick?.invoke(it) ?: Unit },
                                onConversationClick = if (shouldRenderSubReplyConversationAction(item, true)) {
                                    {
                                        captureListScrollForConversation()
                                        if (onConversationClick != null) {
                                            onConversationClick(item)
                                        } else {
                                            conversationAnchor = item
                                        }
                                    }
                                } else null,
                            )
                        }
                    }

                    if (isLoading && visibleReplies.isEmpty()) {
                        // 骨架拆成多个独立 lazy item（而不是 1 个 item 里塞 4 行）：数据到达时是
                        // 一个槽位换一个槽位，列表锚点不失效，不会整体跳一下。
                        videoCommentSkeletonItems(
                            slotCount = commentSkeletonSlotCount,
                            keyPrefix = "subreply_skeleton_",
                        )
                    }

                    item(key = "footer") {
                        when {
                            error != null && !isLoading -> {
                                AppText(
                                    text = "$error，点击重试",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = if (isTargetOnly && !isConversationMode) onRefresh else onLoadMore)
                                        .sizeIn(minHeight = 48.dp)
                                        .padding(16.dp),
                                    textAlign = TextAlign.Center,
                                    color = appearance.sortTint,
                                )
                            }
                            isLoading && !isRefreshing && visibleReplies.isNotEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdaptiveLoadingIndicator()
                                }
                            }
                            showManualLoadMore -> {
                                AppText(
                                    text = "加载更多回复",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onLoadMore)
                                        .padding(horizontal = 16.dp, vertical = 18.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = appearance.sortTint
                                )
                            }
                            isEnd && !isLoading && !isRefreshing && !isTargetOnly -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppText(
                                        text = "没有更多了",
                                        color = appearance.secondaryTextColor,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

