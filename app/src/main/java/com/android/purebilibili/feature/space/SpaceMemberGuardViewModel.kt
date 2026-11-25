package com.android.purebilibili.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.SpaceGuardMemberItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UP主大航海（舰队）列表页（对齐 PiliPlus member_guard）。 */
class SpaceMemberGuardViewModel(
    private val ruid: Long,
    upName: String,
    initialCount: Long = 0L,
) : ViewModel() {

    data class UiState(
        val upName: String = "",
        val totalCount: Long = 0L,
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
        val items: List<SpaceGuardMemberItem> = emptyList(),
        // 顶部领奖台（总督/提督/舰长前三名），刷新时从列表中摘出
        val tops: List<SpaceGuardMemberItem> = emptyList(),
        val hasMore: Boolean = false,
    )

    private val spaceApi = NetworkModule.spaceApi
    private val _uiState = MutableStateFlow(UiState(upName = upName, totalCount = initialCount))
    val uiState = _uiState.asStateFlow()

    private var page = 1
    private var fetchJob: Job? = null

    init {
        refresh()
    }

    fun retry() {
        refresh()
    }

    fun loadMore() {
        var shouldFetch = false
        _uiState.update { state ->
            if (state.isLoading || state.isLoadingMore || !state.hasMore || state.error != null) {
                shouldFetch = false
                state
            } else {
                shouldFetch = true
                state.copy(isLoadingMore = true)
            }
        }
        if (shouldFetch) {
            fetch(page = page + 1, isLoadMore = true)
        }
    }

    private fun refresh() {
        fetchJob?.cancel()
        page = 1
        _uiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        fetch(page = 1, isLoadMore = false)
    }

    private fun fetch(page: Int, isLoadMore: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val response = spaceApi.getMemberGuard(
                    mapOf(
                        "ruid" to ruid.toString(),
                        "page" to page.toString(),
                        "page_size" to PAGE_SIZE.toString(),
                    )
                )
                val data = response.data
                if (response.code != 0 || data == null) {
                    val fallbackErr = response.message.ifBlank { "获取舰队信息失败" }
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = if (isLoadMore) current.error else fallbackErr,
                        )
                    }
                    return@launch
                }
                val rawList = data.guardTopList
                val resolvedTops = if (!isLoadMore) rawList.take(3) else null
                val incomingItems = if (!isLoadMore) rawList.drop(3) else rawList
                val hasMore = data.hasMore == 1
                this@SpaceMemberGuardViewModel.page = page
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        items = if (isLoadMore) current.items + incomingItems else incomingItems,
                        tops = resolvedTops ?: current.tops,
                        hasMore = hasMore,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SpaceMemberGuard", "load failed: ${e.message}")
                val fallbackErr = e.message ?: "网络请求失败"
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (isLoadMore) current.error else fallbackErr,
                    )
                }
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
