package com.android.purebilibili.feature.video.viewmodel

import android.net.Uri

data class DanmakuComposerDraft(
    val text: String = "",
    val attentionCommand: Boolean = false
)

data class CommentComposerDraft(
    val text: String = "",
    val imageUris: List<Uri> = emptyList(),
    val syncToDynamic: Boolean = false
)

data class CommentComposerDraftState(
    val sessionId: Long = 0L,
    val completedSessionId: Long = -1L,
    val drafts: Map<Long, CommentComposerDraft> = emptyMap(),
    val revision: Long = 0L
) {
    internal fun open(): CommentComposerDraftState = copy(sessionId = sessionId + 1L)

    internal fun reset(): CommentComposerDraftState = copy(
        sessionId = sessionId + 1L,
        drafts = emptyMap()
    )

    internal fun update(
        sessionId: Long,
        replyRpid: Long?,
        draft: CommentComposerDraft
    ): CommentComposerDraftState {
        if (sessionId != this.sessionId || sessionId <= completedSessionId) return this
        val key = commentComposerDraftKey(replyRpid)
        val updatedDrafts = if (
            draft.text.isEmpty() && draft.imageUris.isEmpty() && !draft.syncToDynamic
        ) {
            if (key in drafts) drafts - key else drafts
        } else {
            drafts + (key to draft)
        }
        // StateFlow must retain a newer draft instance even when its contents are equal.
        return copy(drafts = updatedDrafts, revision = revision + 1L)
    }

    internal fun complete(
        sessionId: Long,
        replyRpid: Long?,
        sentDraft: CommentComposerDraft?
    ): CommentComposerDraftState {
        if (sessionId > this.sessionId) return this
        val key = commentComposerDraftKey(replyRpid)
        return copy(
            completedSessionId = maxOf(completedSessionId, sessionId),
            drafts = if (sentDraft != null && drafts[key] === sentDraft) drafts - key else drafts
        )
    }
}

data class VideoComposerDraftState(
    val videoId: String = "",
    val danmaku: DanmakuComposerDraft = DanmakuComposerDraft(),
    val comments: CommentComposerDraftState = CommentComposerDraftState()
) {
    internal fun forVideo(videoId: String): VideoComposerDraftState {
        if (videoId.isBlank() || this.videoId == videoId) return this
        return VideoComposerDraftState(videoId = videoId, comments = comments.reset())
    }
}

internal fun commentComposerDraftKey(replyRpid: Long?): Long = replyRpid ?: 0L
