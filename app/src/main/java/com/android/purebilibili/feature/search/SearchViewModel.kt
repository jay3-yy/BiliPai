package com.android.purebilibili.feature.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.database.AppDatabase
import com.android.purebilibili.core.database.entity.SearchHistory
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val showResults: Boolean = false,
    val searchResults: List<VideoItem> = emptyList(),
    val hotList: List<HotItem> = emptyList(),
    val historyList: List<SearchHistory> = emptyList(),
    val error: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val searchDao = AppDatabase.getDatabase(application).searchHistoryDao()

    init {
        loadHotSearch()
        loadHistory()
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        if (newQuery.isEmpty()) {
            _uiState.update { it.copy(showResults = false, error = null) }
        }
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) return

        _uiState.update { it.copy(query = keyword, isSearching = true, showResults = true, error = null) }
        saveHistory(keyword)

        viewModelScope.launch {
            val result = SearchRepository.search(keyword)
            result.onSuccess { videos ->
                _uiState.update { it.copy(isSearching = false, searchResults = videos) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSearching = false, error = e.message ?: "搜索失败") }
            }
        }
    }

    private fun loadHotSearch() {
        viewModelScope.launch {
            val result = SearchRepository.getHotSearch()
            result.onSuccess { items ->
                _uiState.update { it.copy(hotList = items) }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            // 🔥🔥🔥 核心修复：这里改成了 getAll() 以匹配 SearchHistoryDao 🔥🔥🔥
            searchDao.getAll().collect { history ->
                _uiState.update { it.copy(historyList = history) }
            }
        }
    }

    private fun saveHistory(keyword: String) {
        viewModelScope.launch {
            searchDao.insert(SearchHistory(keyword = keyword, timestamp = System.currentTimeMillis()))
        }
    }

    fun deleteHistory(history: SearchHistory) {
        viewModelScope.launch {
            searchDao.delete(history)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchDao.clearAll()
        }
    }
}