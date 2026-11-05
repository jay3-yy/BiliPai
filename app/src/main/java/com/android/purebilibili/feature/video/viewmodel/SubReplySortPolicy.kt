package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.ReplyItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// 排序档位与楼中楼末页判定已下沉 core-data；原包入口保留，手机各调用方不感知迁移。
typealias SubReplySortMode = com.android.purebilibili.data.repository.SubReplySortMode

internal fun SubReplyUiState.resetForSort(mode: SubReplySortMode): SubReplyUiState = copy(
    sortMode = mode,
    items = persistentListOf(),
    baseItems = persistentListOf(),
    page = 1,
    basePage = 1,
    isEnd = false,
    isTargetOnly = false,
    baseIsEnd = false,
    grpcNextOffset = null,
    baseGrpcNextOffset = null,
    conversationAnchor = null,
    targetReplyId = 0L,
    isLoading = true,
    isRefreshing = false,
    error = null,
    retryRequest = null,
)

internal fun isSortedSubReplyPageEnd(cursorIsEnd: Boolean, nextOffset: String?): Boolean =
    com.android.purebilibili.data.repository.isSortedSubReplyPageEnd(cursorIsEnd, nextOffset)

/** A new local reply belongs at the tail, not beside its parent in an earlier time page. */
internal fun insertSentSubReply(
    items: List<ReplyItem>,
    reply: ReplyItem
): List<ReplyItem> {
    val existingIndex = items.indexOfFirst { it.rpid == reply.rpid }
    val insertionIndex = if (existingIndex >= 0) existingIndex else items.size
    return buildList(items.size + if (existingIndex < 0) 1 else 0) {
        items.forEachIndexed { index, item ->
            if (index == insertionIndex) add(reply)
            if (item.rpid != reply.rpid) add(item)
        }
        if (insertionIndex == items.size) add(reply)
    }
}

/** Rebuild from pure remote pages, then append only unacknowledged sends in send order. */
internal fun mergeSentSubReplies(
    remoteItems: List<ReplyItem>,
    sentReplies: Collection<ReplyItem>
): List<ReplyItem> = buildList(remoteItems.size + sentReplies.size) {
    val ids = HashSet<Long>(remoteItems.size + sentReplies.size)
    remoteItems.forEach { if (ids.add(it.rpid)) add(it) }
    sentReplies.forEach { if (ids.add(it.rpid)) add(it) }
}

/** Add responses can omit threading fields; the captured composer target is authoritative. */
internal fun resolveSentSubReply(
    reply: ReplyItem,
    rootId: Long,
    parentId: Long,
    target: ReplyItem?,
    oid: Long = reply.oid,
    replyType: Int = reply.replyType
): ReplyItem = reply.copy(
    oid = oid,
    replyType = replyType,
    root = rootId,
    parent = parentId,
    dialog = reply.dialog.takeIf { it > 0L }
        ?: target?.takeIf { it.root == rootId && it.parent != rootId }?.dialog?.takeIf { it > 0L }
        ?: 0L
)

internal fun shouldApplySentSubReplyResult(
    expectedRootId: Long,
    expectedSession: Long,
    currentSession: Long,
    state: SubReplyUiState
): Boolean = expectedRootId > 0L && expectedSession == currentSession &&
    state.visible && state.threadRootId == expectedRootId

internal fun SubReplyUiState.withSentSubReply(
    reply: ReplyItem,
    conversationItemsAreFiltered: Boolean = true
): SubReplyUiState {
    if (!visible || threadRootId != reply.root) return this
    val allItems = if (conversationAnchor == null) items else baseItems
    val alreadyLoaded = allItems.any { it.rpid == reply.rpid } || items.any { it.rpid == reply.rpid }
    val updatedBase = insertSentSubReply(allItems, reply).toImmutableList()
    val anchor = conversationAnchor
    val belongsToConversation = anchor == null || !conversationItemsAreFiltered ||
        reply.parent == anchor.rpid ||
        (reply.parent != reply.root && items.any { it.rpid == reply.parent }) ||
        (reply.dialog > 0L && reply.dialog == anchor.dialog)
    return copy(
        items = if (anchor == null) updatedBase else if (belongsToConversation) {
            insertSentSubReply(items, reply).toImmutableList()
        } else items,
        baseItems = updatedBase,
        totalCount = if (isTargetOnly) totalCount else totalCount + if (alreadyLoaded) 0 else 1
    )
}
