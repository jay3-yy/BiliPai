package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.data.model.response.ReplyItem

internal const val MAX_SUB_REPLY_THREAD_INDENT = 2

internal data class SubReplyThreadLink(val replyId: Long, val parentId: Long)

internal fun List<ReplyItem>.subReplyThreadLinks(): List<SubReplyThreadLink> =
    map { SubReplyThreadLink(it.rpid, it.parent) }

internal data class SubReplyThreadRow(
    val replyId: Long,
    val sourceIndex: Int,
    val depth: Int,
    val continuingRails: Int,
    val hasNextSibling: Boolean,
    val descendantCount: Int = 0,
)

private data class SubReplyThreadLevel(
    val parentIndex: Int,
    val replyIndices: Iterator<Int>,
    val depth: Int,
    val continuingRails: Int,
)

internal fun buildSubReplyThreadRows(
    rootId: Long,
    links: List<SubReplyThreadLink>,
): List<SubReplyThreadRow> {
    val loadedIds = links.mapTo(HashSet()) { it.replyId }
    val children = links.indices.groupBy { index ->
        val link = links[index]
        // A parent can be on a later page or hidden by comment filters.
        link.parentId.takeIf { it != rootId && it != link.replyId && it in loadedIds }
    }
    val rows = ArrayList<SubReplyThreadRow>(links.size)
    val levels = ArrayDeque<SubReplyThreadLevel>()
    // The root comment is rendered above the list and occupies depth zero.
    levels.addLast(SubReplyThreadLevel(-1, children[null].orEmpty().iterator(), depth = 1, continuingRails = 0))
    while (levels.isNotEmpty()) {
        val level = levels.last()
        if (!level.replyIndices.hasNext()) {
            levels.removeLast()
            if (level.parentIndex >= 0) {
                val parent = rows[level.parentIndex]
                rows[level.parentIndex] = parent.copy(descendantCount = rows.lastIndex - level.parentIndex)
            }
            continue
        }

        val index = level.replyIndices.next()
        val replyId = links[index].replyId
        val depth = level.depth
        val hasNextSibling = level.replyIndices.hasNext()
        rows.add(SubReplyThreadRow(replyId, index, depth, level.continuingRails, hasNextSibling))
        val directReplies = children[replyId] ?: continue
        val continuingRails = if (hasNextSibling && depth <= MAX_SUB_REPLY_THREAD_INDENT) {
            level.continuingRails or (1 shl (depth - 1))
        } else {
            level.continuingRails
        }
        levels.addLast(SubReplyThreadLevel(rows.lastIndex, directReplies.iterator(), depth + 1, continuingRails))
    }
    return rows
}

internal fun List<SubReplyThreadRow>.collapsedBranchesHiding(
    replyId: Long,
    collapsedBranches: Set<Long>,
): Set<Long> {
    if (collapsedBranches.isEmpty() || replyId <= 0L) return emptySet()
    val targetIndex = indexOfFirst { it.replyId == replyId }
    if (targetIndex < 0) return emptySet()
    val hidingBranches = mutableSetOf<Long>()
    for (index in 0 until targetIndex) {
        val row = this[index]
        if (index + row.descendantCount >= targetIndex && row.replyId in collapsedBranches) {
            hidingBranches.add(row.replyId)
        }
    }
    return hidingBranches
}

internal fun List<SubReplyThreadRow>.visibleThreadRows(
    collapsedBranches: Set<Long>,
): List<SubReplyThreadRow> {
    if (collapsedBranches.isEmpty()) return this
    return buildList {
        var index = 0
        while (index < this@visibleThreadRows.size) {
            val row = this@visibleThreadRows[index]
            add(row)
            index += if (row.replyId in collapsedBranches) row.descendantCount + 1 else 1
        }
    }
}
