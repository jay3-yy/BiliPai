package com.android.purebilibili.feature.video.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.data.model.response.VideoTag
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class VideoSupplementSeed(
    val aiSummary: AiSummaryData? = null,
    val videoNoteState: VideoNoteUiState = VideoNoteUiState(),
    val videoTags: ImmutableList<VideoTag> = persistentListOf(),
    val onlineCount: String = "",
    val ownerFollowerCount: Int? = null,
    val ownerVideoCount: Int? = null
)

@Immutable
data class VideoSupplementUiState(
    val subject: VideoSubjectSnapshot? = null,
    val visible: Boolean = true,
    val aiSummary: AiSummaryData? = null,
    val videoNoteState: VideoNoteUiState = VideoNoteUiState(),
    val videoTags: ImmutableList<VideoTag> = persistentListOf(),
    val onlineCount: String = "",
    val ownerFollowerCount: Int? = null,
    val ownerVideoCount: Int? = null
)

fun interface VideoSupplementLoader {
    suspend fun load(subject: VideoSubjectSnapshot): VideoSupplementSeed?
}

private val EmptyVideoSupplementLoader = VideoSupplementLoader { null }

class VideoSupplementViewModel(
    private val loader: VideoSupplementLoader = EmptyVideoSupplementLoader,
    private val startDelayMs: Long = 300L
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoSupplementUiState())
    val uiState = _uiState.asStateFlow()
    private var subjectJob: Job? = null

    fun bindSubject(subject: VideoSubjectSnapshot, seed: VideoSupplementSeed) {
        if (!shouldRebindVideoDomain(_uiState.value.subject, subject)) {
            sync(seed)
            return
        }
        subjectJob?.cancel()
        _uiState.update { prev ->
            VideoSupplementUiState(
                subject = subject,
                visible = prev.visible,
                aiSummary = seed.aiSummary,
                videoNoteState = seed.videoNoteState,
                videoTags = seed.videoTags,
                onlineCount = seed.onlineCount,
                ownerFollowerCount = seed.ownerFollowerCount,
                ownerVideoCount = seed.ownerVideoCount
            )
        }
        startDeferredLoad(subject)
    }

    fun sync(seed: VideoSupplementSeed) {
        _uiState.update { current ->
            current.copy(
                aiSummary = seed.aiSummary,
                videoNoteState = seed.videoNoteState,
                videoTags = seed.videoTags,
                onlineCount = seed.onlineCount,
                ownerFollowerCount = seed.ownerFollowerCount,
                ownerVideoCount = seed.ownerVideoCount
            )
        }
    }

    fun setVisible(visible: Boolean) {
        var changed = false
        var currentSubject: VideoSubjectSnapshot? = null
        _uiState.update { current ->
            if (current.visible == visible) current
            else {
                changed = true
                currentSubject = current.subject
                current.copy(visible = visible)
            }
        }
        if (!changed) return
        if (!visible) subjectJob?.cancel()
        else currentSubject?.let(::startDeferredLoad)
    }

    private fun startDeferredLoad(subject: VideoSubjectSnapshot) {
        if (!_uiState.value.visible) return
        subjectJob?.cancel()
        subjectJob = viewModelScope.launch {
            delay(startDelayMs)
            val loaded = loader.load(subject) ?: return@launch
            val current = _uiState.value
            if (current.visible && current.subject?.generation == subject.generation) {
                sync(loaded)
            }
        }
    }

    override fun onCleared() {
        subjectJob?.cancel()
        super.onCleared()
    }
}

internal fun VideoPlaybackUiState.Success.toSupplementSeed(): VideoSupplementSeed =
    VideoSupplementSeed(
        aiSummary = aiSummary,
        videoNoteState = videoNoteState,
        videoTags = videoTags.toImmutableList(),
        onlineCount = onlineCount,
        ownerFollowerCount = ownerFollowerCount,
        ownerVideoCount = ownerVideoCount
    )
