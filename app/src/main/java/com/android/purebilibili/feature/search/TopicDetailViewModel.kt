package com.android.purebilibili.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DynamicPublishDraft
import com.android.purebilibili.data.model.response.TopicSortOption
import com.android.purebilibili.data.model.response.TopicTopDetails
import com.android.purebilibili.data.repository.DynamicCreateRepository
import com.android.purebilibili.data.repository.TopicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopicDetailUiState(
    val isLoading: Boolean = false,
    val isSwitchingSort: Boolean = false,
    val isLoadingMore: Boolean = false,
    val details: TopicTopDetails? = null,
    val items: List<DynamicItem> = emptyList(),
    val offset: String = "",
    val hasMore: Boolean = false,
    val sortOptions: List<TopicSortOption> = emptyList(),
    val selectedSortBy: Int = 0,
    val isPublishing: Boolean = false,
    val publishError: String? = null,
    val error: String? = null
)

class TopicDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val publishingRequests = com.android.purebilibili.core.coroutines.AccountScopedRequests(viewModelScope)
    init {
        publishingRequests.observe {
            _uiState.update { it.copy(isPublishing = false, publishError = null) }
        }
    }

    private var loadedTopicId: Long = 0L

    fun load(topicId: Long) {
        if (topicId <= 0L) {
            _uiState.update { it.copy(error = "话题不存在", isLoading = false) }
            return
        }
        if (loadedTopicId == topicId && (_uiState.value.details != null || _uiState.value.isLoading)) return
        loadedTopicId = topicId
        _uiState.update {
            TopicDetailUiState(isLoading = true)
        }
        viewModelScope.launch {
            val detailResult = TopicRepository.getTopicDetail(topicId)
            val feedResult = TopicRepository.getTopicFeed(topicId)
            val details = detailResult.getOrNull()
            val page = feedResult.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    details = details,
                    items = page?.items.orEmpty(),
                    offset = page?.offset.orEmpty(),
                    hasMore = page?.hasMore == true,
                    sortOptions = page?.sortOptions.orEmpty(),
                    selectedSortBy = page?.selectedSortBy ?: 0,
                    error = detailResult.exceptionOrNull()?.message
                        ?: feedResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val topicId = loadedTopicId
        if (
            topicId <= 0L ||
            !state.hasMore ||
            state.isLoading ||
            state.isSwitchingSort ||
            state.isLoadingMore
        ) return
        val requestedSortBy = state.selectedSortBy
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            TopicRepository.getTopicFeed(
                topicId = topicId,
                offset = state.offset,
                sortBy = requestedSortBy,
            )
                .onSuccess { page ->
                    _uiState.update { current ->
                        if (
                            current.selectedSortBy != requestedSortBy ||
                            current.isSwitchingSort
                        ) {
                            current
                        } else current.copy(
                            isLoadingMore = false,
                            items = mergeDynamicItems(current.items, page.items),
                            offset = page.offset,
                            hasMore = page.hasMore,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { current ->
                        if (current.selectedSortBy != requestedSortBy) {
                            current
                        } else current.copy(
                            isLoadingMore = false,
                            error = error.message ?: "加载更多失败"
                        )
                    }
                }
        }
    }

    fun selectSort(sortBy: Int) {
        val state = _uiState.value
        val topicId = loadedTopicId
        if (
            topicId <= 0L ||
            sortBy == state.selectedSortBy ||
            state.isLoading ||
            state.isSwitchingSort
        ) return
        val previousSortBy = state.selectedSortBy
        val previousOffset = state.offset
        val previousHasMore = state.hasMore
        _uiState.update {
            it.copy(
                offset = "",
                hasMore = false,
                selectedSortBy = sortBy,
                isSwitchingSort = true,
                isLoadingMore = false,
                error = null,
            )
        }
        viewModelScope.launch {
            TopicRepository.getTopicFeed(topicId = topicId, sortBy = sortBy)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isSwitchingSort = false,
                            items = page.items,
                            offset = page.offset,
                            hasMore = page.hasMore,
                            sortOptions = page.sortOptions.ifEmpty { it.sortOptions },
                            selectedSortBy = page.selectedSortBy,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSwitchingSort = false,
                            selectedSortBy = previousSortBy,
                            offset = previousOffset,
                            hasMore = previousHasMore,
                            error = error.message ?: "话题动态加载失败",
                        )
                    }
                }
        }
    }

    fun publish(
        context: Context,
        draft: DynamicPublishDraft,
        onResult: (Boolean, String) -> Unit,
    ) {
        if (_uiState.value.isPublishing) return
        _uiState.update { it.copy(isPublishing = true, publishError = null) }
        val identity = publishingRequests.identity
        publishingRequests.launch {
            DynamicCreateRepository.publish(context, draft)
                .onSuccess { createdId ->
                    if (!publishingRequests.isCurrent(identity)) return@onSuccess
                    _uiState.update { it.copy(isPublishing = false, publishError = null) }
                    onResult(true, if (draft.publishAtSeconds != null) "定时发布已创建" else "发布成功")
                    if (draft.publishAtSeconds == null) refreshSelectedFeed()
                    com.android.purebilibili.feature.dynamic.visibility.DynamicVisibilityCheck.afterPublication(
                        context.applicationContext, createdId, identity,
                        privatePublication = draft.private,
                        scheduledPublication = draft.publishAtSeconds != null,
                    )
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (!publishingRequests.isCurrent(identity)) return@onFailure
                    val message = error.message ?: "发布失败"
                    _uiState.update { it.copy(isPublishing = false, publishError = message) }
                    onResult(false, message)
                }
        }
    }

    private suspend fun refreshSelectedFeed() {
        val topicId = loadedTopicId
        if (topicId <= 0L) return
        val selectedSortBy = _uiState.value.selectedSortBy
        TopicRepository.getTopicFeed(topicId = topicId, sortBy = selectedSortBy)
            .onSuccess { page ->
                _uiState.update {
                    it.copy(
                        items = page.items,
                        offset = page.offset,
                        hasMore = page.hasMore,
                        sortOptions = page.sortOptions.ifEmpty { it.sortOptions },
                        selectedSortBy = page.selectedSortBy,
                    )
                }
            }
    }

    private fun mergeDynamicItems(
        existing: List<DynamicItem>,
        incoming: List<DynamicItem>
    ): List<DynamicItem> {
        val seen = LinkedHashSet<String>()
        val merged = ArrayList<DynamicItem>(existing.size + incoming.size)
        (existing + incoming).forEach { item ->
            val key = item.id_str.ifBlank { item.hashCode().toString() }
            if (seen.add(key)) merged += item
        }
        return merged
    }
}
