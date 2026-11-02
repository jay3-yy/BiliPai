package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.data.model.response.CommentMainListContinuation
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.ReplyData
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.feature.dynamic.components.DynamicCardPrimaryAction
import com.android.purebilibili.feature.dynamic.components.resolveDynamicCardPrimaryAction
import com.android.purebilibili.feature.video.viewmodel.SubReplyPageRequest
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.feature.video.viewmodel.mergeSentSubReplies
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.ceil

/**
 * 动态评论首页每页条数。
 * 从 20 提到 30：首屏一次拿到的评论更多，减少触底加载次数，也让首屏骨架与真实内容的
 * 高度差更小。
 */
internal const val DYNAMIC_COMMENT_PAGE_SIZE = 30

internal data class DynamicCommentPayload(
    val replies: List<ReplyItem>,
    val totalCount: Int
)

internal data class DynamicCommentLoadAttempt(
    val target: DynamicCommentTarget,
    val replies: List<ReplyItem>,
    val totalCount: Int,
    val candidateIndex: Int,
    val nextPage: Int = 2,
    val isEnd: Boolean = true,
    val grpcNextOffset: String? = null,
    val mainListContinuation: CommentMainListContinuation? = null,
)

internal data class DynamicDetailInteractionModel(
    val primaryAction: DynamicCardPrimaryAction,
    val commentTargets: List<DynamicCommentTarget>
)

internal fun resolveDynamicCommentPayload(
    data: ReplyData,
    fallbackCount: Int,
    includeHotReplies: Boolean = true
): DynamicCommentPayload {
    val replies = buildList {
        addAll(data.collectTopReplies())
        if (includeHotReplies) {
            addAll(data.hots.orEmpty())
        }
        addAll(data.replies.orEmpty())
    }.distinctBy { it.rpid }

    return DynamicCommentPayload(
        replies = replies,
        totalCount = maxOf(
            data.getAllCount(),
            fallbackCount,
            replies.size
        )
    )
}

internal fun selectPreferredDynamicCommentAttempt(
    attempts: List<DynamicCommentLoadAttempt>,
    expectedCount: Int = 0
): DynamicCommentLoadAttempt? {
    return attempts.minWithOrNull(
        compareBy<DynamicCommentLoadAttempt> {
            when {
                it.replies.isNotEmpty() -> 0
                it.totalCount > 0 -> 1
                else -> 2
            }
        }.thenBy {
            if (expectedCount > 0 && it.totalCount > 0) {
                kotlin.math.abs(it.totalCount - expectedCount)
            } else {
                0
            }
        }.thenBy { it.candidateIndex }
            .thenByDescending { it.totalCount }
            .thenByDescending { it.replies.size }
    )
}

internal fun shouldLoadMoreDynamicDetailComments(
    lastVisibleIndex: Int,
    itemCount: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
): Boolean {
    if (isLoading || isLoadingMore) return false
    if (itemCount <= 0 || lastVisibleIndex < 0) return false
    // 显示总数可能未知、滞后或包含楼中楼，不能充当分页边界。
    if (!hasMore) return false
    return lastVisibleIndex >= itemCount - 4
}

internal fun resolveDynamicDetailInteractionModel(
    item: DynamicItem
): DynamicDetailInteractionModel {
    return DynamicDetailInteractionModel(
        primaryAction = resolveDynamicCardPrimaryAction(item),
        commentTargets = resolveDynamicCommentTargets(item)
    )
}

internal fun resolveDynamicSubReplyStateAfterSuccess(
    currentState: SubReplyUiState,
    remoteItems: List<ReplyItem>,
    page: Int,
    isEnd: Boolean,
    totalCount: Int = currentState.totalCount,
    grpcNextOffset: String? = currentState.grpcNextOffset,
    sentReplies: Collection<ReplyItem> = emptyList(),
): SubReplyUiState {
    val mergedItems = mergeSentSubReplies(remoteItems, sentReplies)
    return currentState.copy(
        items = mergedItems.toImmutableList(),
        totalCount = totalCount,
        isLoading = false,
        isRefreshing = false,
        page = page,
        isEnd = isEnd,
        error = null,
        retryRequest = null,
        baseItems = mergedItems.toImmutableList(),
        basePage = page,
        baseIsEnd = isEnd,
        grpcNextOffset = grpcNextOffset,
        baseGrpcNextOffset = grpcNextOffset
    )
}

internal fun resolveDynamicSubReplyStateAfterFailure(
    currentState: SubReplyUiState,
    errorMessage: String?,
    failedRequest: SubReplyPageRequest? = currentState.retryRequest,
): SubReplyUiState {
    return currentState.copy(
        isLoading = false,
        error = errorMessage,
        retryRequest = failedRequest,
    )
}

/**
 * 动态评论区单一 UI 状态。
 *
 * 以前 comments / totalCount / loading / refreshing / error 是 5 个独立 StateFlow，
 * 加载完成时会依次发射，中间帧会出现「评论已经渲染但 loading 仍为 true」这类不一致状态，
 * 表现就是列表先跳一下再稳定。收敛成一个对象后，一次发射即一个完整画面。
 */
internal data class DynamicCommentUiState(
    val comments: List<ReplyItem> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val refreshError: String? = null,
    val dynamicId: String? = null,
    val hasMore: Boolean = false,
    val nextPage: Int = 1,
    val pinnedRootReplyId: Long = 0L,
    val pinnedTargetReplyId: Long = 0L,
    val pinnedReply: ReplyItem? = null,
    val pinnedReplyError: String? = null,
)

/** Route-local placement does not change the server order or the cached first page. */
internal fun resolveDynamicDetailDisplayComments(state: DynamicCommentUiState): List<ReplyItem> {
    val pinned = state.pinnedReply?.takeIf {
        it.rpid > 0L && it.rpid == state.pinnedRootReplyId
    } ?: return state.comments
    if (state.comments.firstOrNull() === pinned) return state.comments
    val existingIndex = state.comments.indexOfFirst { it.rpid == pinned.rpid }
    return buildList(state.comments.size + if (existingIndex < 0) 1 else 0) {
        add(pinned)
        state.comments.forEach { if (it.rpid != pinned.rpid) add(it) }
    }
}

/**
 * 评论骨架槽位估算。
 *
 * 骨架行高对齐真实 [com.android.purebilibili.feature.video.ui.components.ReplyItemView]
 * 之后，用可视高度除以单槽高度得到需要的槽位数量：宁可多铺一点（多余的在屏幕外，无副作用），
 * 也不要少铺——少铺会让列表在数据到达时突然被撑长，产生位移。
 */
internal const val DYNAMIC_COMMENT_SKELETON_SLOT_HEIGHT_DP = 130f
const val DYNAMIC_COMMENT_SKELETON_MIN_SLOTS = 3
const val DYNAMIC_COMMENT_SKELETON_MAX_SLOTS = 8

internal fun resolveDynamicCommentSkeletonSlotCount(viewportHeightDp: Int): Int {
    if (viewportHeightDp <= 0) return DYNAMIC_COMMENT_SKELETON_MIN_SLOTS
    val estimated = ceil(viewportHeightDp / DYNAMIC_COMMENT_SKELETON_SLOT_HEIGHT_DP).toInt()
    return estimated.coerceIn(DYNAMIC_COMMENT_SKELETON_MIN_SLOTS, DYNAMIC_COMMENT_SKELETON_MAX_SLOTS)
}

/**
 * 评论首页缓存快照。
 *
 * 只缓存「首页」：重进同一条动态时先秒出旧内容，再后台静默刷新，既避免重复转圈，
 * 也不会让缓存无限膨胀。
 */
internal data class DynamicCommentCacheSnapshot(
    val replies: List<ReplyItem>,
    val totalCount: Int,
    val nextPage: Int,
    val isEnd: Boolean,
    val grpcNextOffset: String?,
    val target: DynamicCommentTarget,
    val cachedAtMs: Long,
    val mainListContinuation: CommentMainListContinuation? = null,
    val remoteReplies: List<ReplyItem> = replies,
)

/**
 * 进程内 LRU 评论缓存（按 dynamicId + 排序模式分桶）。
 *
 * 生命周期与 ViewModel 一致，不落盘；退出动态详情不清空，这样「返回再进」仍能秒出。
 */
internal class DynamicCommentCache(private val maxEntries: Int = 12) {

    private val entries = LinkedHashMap<String, DynamicCommentCacheSnapshot>()

    internal fun keyOf(dynamicId: String, sortModeApiMode: Int): String = "$dynamicId#$sortModeApiMode"

    internal fun get(dynamicId: String, sortModeApiMode: Int): DynamicCommentCacheSnapshot? {
        val key = keyOf(dynamicId, sortModeApiMode)
        val snapshot = entries.remove(key) ?: return null
        // 重新插入到队尾，保持「最近使用」语义
        entries[key] = snapshot
        return snapshot
    }

    internal fun put(
        dynamicId: String,
        sortModeApiMode: Int,
        snapshot: DynamicCommentCacheSnapshot,
    ) {
        if (dynamicId.isBlank() || snapshot.replies.isEmpty()) return
        val key = keyOf(dynamicId, sortModeApiMode)
        entries.remove(key)
        entries[key] = snapshot
        while (entries.size > maxEntries) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            entries.remove(oldestKey)
        }
    }

    internal fun size(): Int = entries.size

    internal fun clear() {
        entries.clear()
    }
}
