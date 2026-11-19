package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.core.filter.contentFiltered
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.repository.AiReplyDetector
import com.android.purebilibili.data.repository.ReplyContentFilter
import com.android.purebilibili.data.repository.RepeatedReplyDetector
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class ReplyListProjection(private val foldDetectedReplies: Boolean = false) {
    private val detector = AiReplyDetector()
    private val repeatedDetector = RepeatedReplyDetector()

    data class Content(
        val items: List<ReplyItem>,
        val baseItems: List<ReplyItem> = emptyList(),
        val root: ReplyItem? = null,
        val anchor: ReplyItem? = null,
    )

    private var source: Content? = null
    private var rules: ContentFilterSnapshot? = null
    private var author = 0L
    private var viewer = 0L
    private var keepsThreadContext = false
    private var output = Content(emptyList())

    fun project(
        items: List<ReplyItem>,
        snapshot: ContentFilterSnapshot,
        upMid: Long = 0L,
    ): List<ReplyItem> = project(Content(items), snapshot, upMid).items

    fun project(
        content: Content,
        snapshot: ContentFilterSnapshot,
        upMid: Long = 0L,
        currentMid: Long = TokenManager.accountIdentity.value.mid ?: 0L,
        keepThreadContext: Boolean = false,
    ): Content {
        val previous = source
        if (previous != null && previous.items === content.items && previous.baseItems === content.baseItems &&
            previous.root === content.root && previous.anchor === content.anchor &&
            rules == snapshot && author == upMid && viewer == currentMid && keepsThreadContext == keepThreadContext
        ) return output

        val evidence = content.items.asSequence() + content.baseItems.asSequence() +
            sequenceOf(content.root, content.anchor).filterNotNull()
        val filter = ReplyContentFilter(
            snapshot, evidence, upMid, currentMid, foldDetectedReplies, detector, repeatedDetector, recordHits = true,
        )
        output = Content(
            items = filter.filter(content.items),
            baseItems = filter.filter(content.baseItems),
            root = content.root?.let { filter.filter(it, keepThreadContext, isThreadRoot = keepThreadContext) },
            anchor = content.anchor?.let { filter.filter(it, keepThreadContext) },
        )
        source = content
        rules = snapshot
        author = upMid
        viewer = currentMid
        keepsThreadContext = keepThreadContext
        return output
    }
}

internal fun StateFlow<CommentUiState>.filteredComments(scope: CoroutineScope): StateFlow<CommentUiState> {
    val replies = ReplyListProjection(foldDetectedReplies = true)
    return contentFiltered(scope) { state, rules ->
        val content = replies.project(ReplyListProjection.Content(state.replies), rules, state.upMid, state.currentMid)
        state.copy(replies = content.items.toImmutableList())
    }
}

internal fun StateFlow<SubReplyUiState>.filteredSubReplies(scope: CoroutineScope): StateFlow<SubReplyUiState> {
    val replies = ReplyListProjection(foldDetectedReplies = true)
    return contentFiltered(scope) { state, rules ->
        val content = replies.project(
            ReplyListProjection.Content(state.items, state.baseItems, state.rootReply, state.conversationAnchor),
            rules,
            state.upMid,
            keepThreadContext = true,
        )
        state.copy(
            items = content.items.toImmutableList(),
            baseItems = content.baseItems.toImmutableList(),
            rootReply = content.root,
            conversationAnchor = content.anchor,
        )
    }
}
