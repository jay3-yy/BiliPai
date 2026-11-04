package com.android.purebilibili.feature.story

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.StoryItem
import com.android.purebilibili.data.repository.StoryRepository
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.videoMetadataAccountScope
import com.android.purebilibili.data.repository.recommendationAccountScope

data class StoryUiState(
    val items: List<StoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentIndex: Int = 0,
    val sessionId: Long = 0L,
)

/** Owns the Story queue and its cursor; the pager only requests more near the visible tail. */
class StoryViewModel(application: Application) : AndroidViewModel(application) {
    private val session = StoryFeedSession(
        scope = viewModelScope,
        loadStory = { seed, page -> StoryRepository.getStoryFeed(bvid = seed, displayId = page) },
        loadHome = { page -> VideoRepository.getHomeVideos(idx = page).map(::videoItemsToStoryItems) },
        store = StoryContinuationStore.shared,
        accountKey = { "${videoMetadataAccountScope()}:${recommendationAccountScope()}" }
    )

    val uiState = session.state

    fun startFeed(bvid: String = "") = session.startFeed(bvid)

    fun stopFeed() = session.stopFeed()

    fun refresh() = session.refresh()

    fun loadMoreStories() = session.loadMoreStories()

    fun updateCurrentVideo(bvid: String) = session.updateCurrentVideo(bvid)

    override fun onCleared() {
        session.stopFeed()
        super.onCleared()
    }
}
