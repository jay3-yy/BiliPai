package com.android.purebilibili.feature.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.util.toUserFriendlyMessage
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.repository.DynamicRepository
import com.android.purebilibili.data.repository.shouldFetchOpusDetailForDynamicDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface DynamicDetailUiState {
    data object Loading : DynamicDetailUiState
    data class Success(
        val item: DynamicItem,
        val isPreview: Boolean = false,
        val isUpdatingFavorite: Boolean = false,
    ) : DynamicDetailUiState
    data class Error(val message: String) : DynamicDetailUiState
}

/** Owned by the navigation entry, so a child space does not restart detail/comment loading. */
internal class DynamicDetailViewModel(
    private val loadDetail: suspend (String) -> Result<DynamicItem> = DynamicRepository::getDynamicDetail,
    private val readSeed: (String) -> DynamicItem? = DynamicRepository::peekDynamicDetailSeed,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DynamicDetailUiState>(DynamicDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _favoriteFeedback = Channel<String>(Channel.BUFFERED)
    val favoriteFeedback = _favoriteFeedback.receiveAsFlow()
    private var loadedId: String? = null
    private var loadedAccount: AccountSessionIdentity? = null
    private var loadJob: Job? = null
    private var favoriteJob: Job? = null
    private var initializedComments: Triple<String, Long, Long>? = null

    fun load(dynamicId: String, fallbackError: String, force: Boolean = false) {
        val account = TokenManager.accountIdentity.value
        val sameDetail = loadedId == dynamicId && loadedAccount == account
        if (!force && sameDetail) return
        loadedId = dynamicId
        loadedAccount = account
        loadJob?.cancel()
        if (!sameDetail) {
            favoriteJob?.cancel()
            favoriteJob = null
            initializedComments = null
        }
        val retained = _uiState.value.takeIf { sameDetail && it is DynamicDetailUiState.Success }
        // 列表里的图文可能只有摘要和九宫格，等详情解析完成再确定段落与图片布局。
        _uiState.value = retained ?: readSeed(dynamicId)
            ?.takeUnless(::shouldFetchOpusDetailForDynamicDetail)
            ?.let { DynamicDetailUiState.Success(it, isPreview = true) }
            ?: DynamicDetailUiState.Loading
        loadJob = viewModelScope.launch {
            favoriteJob?.join()
            val result = loadDetail(dynamicId)
            currentCoroutineContext().ensureActive()
            _uiState.value = result.fold(
                onSuccess = { DynamicDetailUiState.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    DynamicDetailUiState.Error(it.message ?: fallbackError)
                },
            )
        }
    }

    fun toggleFavorite(opusId: Long) {
        val account = TokenManager.accountIdentity.value
        val state = _uiState.value as? DynamicDetailUiState.Success ?: return
        if (loadedAccount != account || state.isPreview || state.item.id_str.toLongOrNull() != opusId) return
        if (loadJob?.isActive == true || favoriteJob?.isActive == true) return
        val favorite = state.item.modules.module_stat?.favorite?.takeUnless { it.hidden } ?: return
        val next = !favorite.status
        _uiState.update { state.copy(isUpdatingFavorite = true) }
        favoriteJob = viewModelScope.launch {
            val result = DynamicRepository.setOpusFavorite(opusId, next, account)
            if (TokenManager.accountIdentity.value != account) return@launch
            _uiState.update { current ->
                if (current !is DynamicDetailUiState.Success || current.item.id_str != state.item.id_str) {
                    return@update current
                }
                val item = current.item
                val stats = item.modules.module_stat
                val updatedItem = if (result.isSuccess && stats != null) {
                    val updatedStats = stats.copy(favorite = favorite.copy(status = next))
                    item.copy(modules = item.modules.copy(module_stat = updatedStats))
                } else item
                current.copy(item = updatedItem, isUpdatingFavorite = false)
            }
            val message = result.fold(
                onSuccess = { if (next) "已收藏" else "已取消收藏" },
                onFailure = { it.toUserFriendlyMessage(if (next) "收藏失败" else "取消收藏失败") },
            )
            _favoriteFeedback.send(message)
        }
    }

    fun initializeCommentsOnce(
        targetKey: String,
        rootReplyId: Long,
        targetReplyId: Long,
        initialize: () -> Unit,
    ) {
        val target = Triple(targetKey, rootReplyId, targetReplyId)
        if (initializedComments == target) return
        initialize()
        initializedComments = target
    }
}
