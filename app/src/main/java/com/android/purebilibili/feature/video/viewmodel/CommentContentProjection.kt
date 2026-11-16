package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.core.filter.contentFiltered
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.filterReplyForContent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class ReplyListProjection {
    private var input: List<ReplyItem>? = null
    private var rules: ContentFilterSnapshot? = null
    private var output: List<ReplyItem> = emptyList()

    fun project(items: List<ReplyItem>, snapshot: ContentFilterSnapshot): List<ReplyItem> {
        if (input === items && rules == snapshot) return output
        val filtered = items.mapNotNull { filterReplyForContent(it, snapshot) }
        output = if (filtered == items) items else filtered
        input = items
        rules = snapshot
        return output
    }
}

internal fun StateFlow<CommentUiState>.filteredComments(scope: CoroutineScope): StateFlow<CommentUiState> {
    val replies = ReplyListProjection()
    return contentFiltered(scope) { state, rules ->
        state.copy(replies = replies.project(state.replies, rules).toImmutableList())
    }
}

internal fun StateFlow<SubReplyUiState>.filteredSubReplies(scope: CoroutineScope): StateFlow<SubReplyUiState> {
    val items = ReplyListProjection()
    val base = ReplyListProjection()
    return contentFiltered(scope) { state, rules ->
        state.copy(
            items = items.project(state.items, rules).toImmutableList(),
            baseItems = base.project(state.baseItems, rules).toImmutableList(),
            rootReply = state.rootReply?.let { filterReplyForContent(it, rules) },
            conversationAnchor = state.conversationAnchor?.let { filterReplyForContent(it, rules) },
        )
    }
}
