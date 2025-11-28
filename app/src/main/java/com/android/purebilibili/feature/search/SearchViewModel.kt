package com.android.purebilibili.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// 搜索页面的 UI 状态
data class SearchUiState(
    val query: String = "",           // 搜索框文字
    val isSearching: Boolean = false, // 是否正在请求网络
    val showResults: Boolean = false, // true=显示结果列表, false=显示历史/热搜

    val historyList: List<SearchHistory> = emptyList(), // 历史记录
    val hotList: List<HotItem> = emptyList(),           // 热搜列表
    val searchResults: List<VideoItem> = emptyList(),   // 搜索结果

    val error: String? = null
)

class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // 1. 监听本地历史记录变化 (数据库变 -> 自动更新 UI)
        viewModelScope.launch {
            SearchRepository.getHistory().collectLatest { history ->
                _uiState.value = _uiState.value.copy(historyList = history)
            }
        }

        // 2. 加载热搜
        loadHotSearch()
    }

    private fun loadHotSearch() {
        viewModelScope.launch {
            val hots = SearchRepository.getHotSearch()
            _uiState.value = _uiState.value.copy(hotList = hots)
        }
    }

    // 输入框文字变化
    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(
            query = newQuery,
            // 如果清空了文字，这就切回历史/热搜模式
            showResults = if (newQuery.isEmpty()) false else _uiState.value.showResults
        )
    }

    // 执行搜索
    fun search(keyword: String) {
        if (keyword.isBlank()) return

        viewModelScope.launch {
            // 1. 更新 UI 状态：显示 Loading，更新文字
            _uiState.value = _uiState.value.copy(
                query = keyword,
                isSearching = true,
                showResults = true,
                error = null
            )

            // 2. 保存到历史记录 (异步)
            SearchRepository.addHistory(keyword)

            // 3. 请求网络
            val result = SearchRepository.search(keyword)

            result.onSuccess { videos ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = videos,
                    error = if (videos.isEmpty()) "未找到相关视频" else null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "搜索失败: ${it.message}"
                )
            }
        }
    }

    // 删除单条历史
    fun deleteHistory(item: SearchHistory) {
        viewModelScope.launch { SearchRepository.deleteHistory(item) }
    }

    // 清空历史
    fun clearHistory() {
        viewModelScope.launch { SearchRepository.clearHistory() }
    }

    // 退出搜索结果模式 (返回历史页)
    fun clearResults() {
        _uiState.value = _uiState.value.copy(showResults = false, query = "")
    }
}