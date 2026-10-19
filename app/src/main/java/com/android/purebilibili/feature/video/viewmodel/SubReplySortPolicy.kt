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
    baseIsEnd = false,
    grpcNextOffset = null,
    baseGrpcNextOffset = null,
    conversationAnchor = null,
    targetReplyId = 0L,
    isLoading = true,
    isRefreshing = false,
    error = null,
)

internal fun isSortedSubReplyPageEnd(cursorIsEnd: Boolean, nextOffset: String?): Boolean =
    com.android.purebilibili.data.repository.isSortedSubReplyPageEnd(cursorIsEnd, nextOffset)

/** Keep the server's relative order; an unloaded parent (or a root reply) falls back to the end. */
internal fun insertSentSubReply(
    items: List<ReplyItem>,
    reply: ReplyItem
): List<ReplyItem> {
    val existingIndex = items.indexOfFirst { it.rpid == reply.rpid }
    val insertionIndex = if (existingIndex >= 0) existingIndex else {
        val parentIndex = items.indexOfFirst { it.rpid == reply.parent }
        if (parentIndex >= 0) parentIndex + 1 else items.size
    }
    return buildList(items.size + if (existingIndex < 0) 1 else 0) {
        items.forEachIndexed { index, item ->
            if (index == insertionIndex) add(reply)
            if (item.rpid != reply.rpid) add(item)
        }
        if (insertionIndex == items.size) add(reply)
    }
}

/** Add responses can omit threading fields; the captured composer target is authoritative. */
internal fun resolveSentSubReply(
    reply: ReplyItem,
    rootId: Long,
    parentId: Long,
    target: ReplyItem?
): ReplyItem = reply.copy(
    root = rootId,
    parent = parentId,
    dialog = reply.dialog.takeIf { it > 0L } ?: target?.takeIf { it.rpid != rootId }?.let {
        it.dialog.takeIf { id -> id > 0L }
            ?: it.parent.takeIf { id -> id > 0L && id != rootId }
            ?: it.rpid
    } ?: 0L
)

internal fun shouldApplySentSubReplyResult(
    expectedRootId: Long,
    expectedSession: Long,
    currentSession: Long,
    state: SubReplyUiState
): Boolean = expectedRootId > 0L && expectedSession == currentSession &&
    state.visible && state.rootReply?.rpid == expectedRootId

internal fun SubReplyUiState.withSentSubReply(
    reply: ReplyItem,
    conversationItemsAreFiltered: Boolean = true
): SubReplyUiState {
    if (!visible || rootReply?.rpid != reply.root) return this
    val allItems = if (conversationAnchor == null) items else baseItems
    val alreadyLoaded = allItems.any { it.rpid == reply.rpid } || items.any { it.rpid == reply.rpid }
    val updatedBase = insertSentSubReply(allItems, reply).toImmutableList()
    val anchor = conversationAnchor
    val belongsToConversation = anchor == null || !conversationItemsAreFiltered ||
        reply.parent == anchor.rpid ||
        (reply.parent != reply.root && items.any { it.rpid == reply.parent }) ||
        (reply.dialog > 0L && reply.dialog == (
            anchor.dialog.takeIf { it > 0L }
                ?: anchor.parent.takeIf { it > 0L && it != reply.root }
                ?: anchor.rpid
            ))
    return copy(
        items = if (anchor == null) updatedBase else if (belongsToConversation) {
            insertSentSubReply(items, reply).toImmutableList()
        } else items,
        baseItems = updatedBase,
        totalCount = totalCount + if (alreadyLoaded) 0 else 1
    )
}
