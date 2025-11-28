// 文件路径: feature/list/ListViewModel.kt
package com.android.purebilibili.feature.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 通用的 UI 状态
data class ListUiState(
    val title: String = "",
    val items: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// 基类 ViewModel
abstract class BaseListViewModel(application: Application, private val pageTitle: String) : AndroidViewModel(application) {
    protected val _uiState = MutableStateFlow(ListUiState(title = pageTitle, isLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val items = fetchItems()
                _uiState.value = _uiState.value.copy(isLoading = false, items = items)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "加载失败")
            }
        }
    }

    // 子类必须实现此方法来提供数据
    abstract suspend fun fetchItems(): List<VideoItem>
}

// --- 历史记录 ViewModel ---
class HistoryViewModel(application: Application) : BaseListViewModel(application, "历史记录") {
    override suspend fun fetchItems(): List<VideoItem> {
        val response = NetworkModule.api.getHistoryList()
        // 过滤并映射数据
        return response.data?.list?.map { it.toVideoItem() } ?: emptyList()
    }
}

// --- 收藏 ViewModel (智能版) ---
class FavoriteViewModel(application: Application) : BaseListViewModel(application, "我的收藏") {
    override suspend fun fetchItems(): List<VideoItem> {
        val api = NetworkModule.api

        // 1. 先获取用户信息，拿到 mid (用户ID)
        val navResp = api.getNavInfo()
        val mid = navResp.data?.mid
        if (mid == null || mid == 0L) {
            throw Exception("无法获取用户信息，请先登录")
        }

        // 2. 获取该用户的收藏夹列表
        val foldersResp = api.getFavFolders(mid)
        val folders = foldersResp.data?.list
        if (folders.isNullOrEmpty()) {
            return emptyList()
        }

        // 3. 取第一个收藏夹 (通常是“默认收藏夹”) 的 ID
        val defaultFolderId = folders[0].id

        // 4. 获取该收藏夹内的视频
        val listResp = api.getFavoriteListStub(mediaId = defaultFolderId)
        return listResp.data?.medias?.map { it.toVideoItem() } ?: emptyList()
    }
}