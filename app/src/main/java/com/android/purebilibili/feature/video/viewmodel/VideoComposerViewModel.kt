package com.android.purebilibili.feature.video.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class VideoComposerUiState(
    val subject: VideoSubjectSnapshot? = null,
    val commentDraft: String = "",
    val danmakuDraft: String = "",
    val isSendingComment: Boolean = false,
    val isSendingDanmaku: Boolean = false,
    val commentDialogVisible: Boolean = false,
    val danmakuDialogVisible: Boolean = false
)

sealed interface VideoComposerEvent {
    data object CommentSent : VideoComposerEvent
    data object DanmakuSent : VideoComposerEvent
}

class VideoComposerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VideoComposerUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<VideoComposerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var mentionSearchJob: Job? = null

    fun bindSubject(subject: VideoSubjectSnapshot) {
        if (!shouldRebindVideoDomain(_uiState.value.subject, subject)) return
        mentionSearchJob?.cancel()
        _uiState.value = VideoComposerUiState(subject = subject)
    }

    fun updateCommentDraft(text: String) {
        _uiState.update { it.copy(commentDraft = text) }
    }

    fun updateDanmakuDraft(text: String) {
        _uiState.update { it.copy(danmakuDraft = text) }
    }

    internal suspend fun notifyCommentSent() {
        _events.send(VideoComposerEvent.CommentSent)
    }

    internal suspend fun notifyDanmakuSent() {
        _events.send(VideoComposerEvent.DanmakuSent)
    }

    override fun onCleared() {
        mentionSearchJob?.cancel()
        super.onCleared()
    }
}
